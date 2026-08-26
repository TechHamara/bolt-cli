import 'dart:io';

import 'package:collection/collection.dart';
import 'package:get_it/get_it.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/config/config.dart';

import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/services/logger.dart';

import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/utils/process_runner.dart';

class Executor {
  static final _fs = GetIt.I<FileService>();
  static final _libService = GetIt.I<LibService>();
  static final _processRunner = ProcessRunner();
  static final _lgr = GetIt.I<Logger>();

  /// Returns the path to android.jar, falling back to ANDROID_HOME if the
  /// Bolt-bundled copy is missing.
  static String _androidJar(Config config) {
    final boltCopy =
        p.join(_fs.libsDir.path, 'android-${config.androidSdk}.jar');
    if (boltCopy.asFile().existsSync()) {
      return boltCopy;
    }
    final androidHome = Platform.environment['ANDROID_HOME'] ??
        Platform.environment['ANDROID_SDK_ROOT'];
    if (androidHome != null) {
      final sdkCopy = p.join(androidHome, 'platforms',
          'android-${config.androidSdk}', 'android.jar');
      if (sdkCopy.asFile().existsSync()) {
        return sdkCopy;
      }
    }
    // Return the Bolt path anyway (will fail with a clear error)
    return boltCopy;
  }

  static Future<void> execD8(Config config, String artJarPath, LazyBox<DateTime> timestampBox) async {
    _lgr.dbg('Running the dexing task.');
    _lgr.dbg('Generating DEX bytecode.');

    final outputClassesJar = p.join(_fs.buildRawDir.path, 'classes.jar').asFile();
    final inputArtJar = File(artJarPath);

    final lastD8Compile = await timestampBox.get('d8TimestampKey');
    bool needsD8 = lastD8Compile == null || !outputClassesJar.existsSync();

    if (!needsD8 && inputArtJar.existsSync()) {
      if (inputArtJar.lastModifiedSync().isAfter(lastD8Compile!)) {
        needsD8 = true;
      }
    }

    if (!needsD8) {
      _lgr.info('Skipping DEX generation (cached)', console: false);
      return;
    }

    final desugarEnabled = config.desugar || config.desugarSources || config.desugarDeps || config.coreLibraryDesugaring;

    final providedDeps = await _libService.providedDependencies(config);
    final libraryJars = providedDeps
        .map((el) => el.classpathJars(providedDeps))
        .flattened
        .toSet();
    libraryJars.add(_androidJar(config));

    final args = <String>[
      ...['-cp', await _libService.r8Jar()],
      'com.android.tools.r8.D8',
      ...['--min-api', '${config.minSdk}'],
      for (final libJar in libraryJars) ...['--lib', libJar],
      if (config.coreLibraryDesugaring) ...[
        '--lib',
        await _libService.desugarJdkLibsJar(),
        '--desugared-lib',
        await _libService.desugarJdkLibsConfig(),
      ],
      '--release',
      if (!desugarEnabled) '--no-desugaring',
      '--output',
      outputClassesJar.path,
      artJarPath,
    ];

    _lgr.dbg('Successfully created dexing arguments');
    _lgr.dbg('Calling dexing commands.');

    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(),
          args.map((el) => el.replaceAll('\\', '/')).toList());
      _lgr.dbg('D8 is successfully executed.');
      await timestampBox.put('d8TimestampKey', DateTime.now());
    } catch (e) {
      rethrow;
    }
  }

  static Future<void> execProGuard(
    Config config,
    String artJarPath,
    Set<String> aarProguardRules, {
    bool deannotateOnly = false,
    bool dontObfuscate = false,
  }) async {
    if (deannotateOnly) {
      _lgr.dbg('Removing annotations from sources.');
    } else {
      _lgr.dbg('Will run the proguard task if -r passed.');
      _lgr.dbg('Running the proguard task.');
      _lgr.dbg('temporary proguard-rules.pro is created.');
      _lgr.dbg('calling proguard commands.');
    }

    final rulesFile = p.join(_fs.srcDir.path, 'proguard-rules.pro').asFile();
    final optimizedJar =
        p.join(p.dirname(artJarPath), 'AndroidRuntime.optimized.jar').asFile();

    final pgJars = await _libService.pgJars(config.proguardVersion);

    // Take only provided deps since compile and runtime scoped deps have already
    // been added to the art jar
    final providedDeps = await _libService.providedDependencies(config);
    final libraryJars = providedDeps
        .map((el) => el.classpathJars(providedDeps))
        .flattened
        .toSet();
    libraryJars.add(_androidJar(config));

    final args = <String>[
      ...['-cp', pgJars.join(BuildUtils.cpSeparator)],
      'proguard.ProGuard',
      // Suppress non-fatal warnings like duplicate classes to keep build output clean
      '-ignorewarnings',
      ...['-injars', artJarPath],
      ...['-outjars', optimizedJar.path],
      ...['-libraryjars', libraryJars.join(BuildUtils.cpSeparator)],
      // Always suppress warnings coming from the AI2 runtime (and related
      // helper classes) so that shrinking never fails due to resolvers being
      // intentionally omitted.  Users can still add their own -dontwarn rules
      // in the project-level `proguard-rules.pro` if they need something more
      // specific.
      '-dontwarn',
      'com.lid.lib.**',
      if (config.deannonate) ...[
        '-keepattributes',
        '!*Annotation*,Exceptions,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable',
      ] else ...[
        '-keepattributes',
        'Exceptions,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable,*Annotation*',
      ],
      if (deannotateOnly) ...[
        '-dontshrink',
        '-dontoptimize',
        '-dontobfuscate',
      ] else ...[
        ...[for (final el in aarProguardRules) '-include $el'],
        '@${rulesFile.path}',
        if (dontObfuscate) '-dontobfuscate',
      ],
    ];

    if (config.minimize != null && config.minimize!.excludeDependency.isNotEmpty) {
      for (final dep in config.minimize!.excludeDependency) {
        // e.g. org.slf4j:slf4j-simple:.* -> org.slf4j.**
        final parts = dep.split(':');
        if (parts.isNotEmpty) {
          final groupId = parts[0];
          args.addAll([
            '-keep',
            'class $groupId.** { *; }',
            '-dontwarn',
            '$groupId.**'
          ]);
        }
      }
    }

    if (config.minimize != null && config.minimize!.excludeProject.isNotEmpty) {
      for (final project in config.minimize!.excludeProject) {
        args.addAll([
          '-keep',
          'class ** { *; }', // we can't easily map project to package without more info, keeping it broad or skip
        ]);
        _lgr.warn('exclude_project is currently mapped to keep all classes. Please use exclude_dependency with precise coordinates if possible.');
      }
    }

    try {
      await _processRunner.runExecutable(
        BuildUtils.javaExe(),
        args.map((el) => el.replaceAll('\\', '/')).toList(),
        logToConsole: false,
      );
      if (!deannotateOnly) {
        _lgr.dbg('proguard is successfully executed.');
      }
    } catch (e) {
      rethrow;
    }

    _lgr.dbg('Moving the runtime jar to the desired dir of raw..');
    if (!optimizedJar.existsSync()) {
      throw Exception(
        'ProGuard execution did not produce an optimized JAR file at ${optimizedJar.path}. '
        'This usually means all classes were shrunk away. Check your proguard-rules.pro and keep rules.'
      );
    }
    await optimizedJar.copy(artJarPath);
    await optimizedJar.delete();
  }

  static Future<void> execManifMerger(
    Config config,
    String mainManifest,
    Set<String> depManifests,
  ) async {
    final classpath = <String>[
      ...await _libService.manifMergerJars(),
      _androidJar(config),
    ].join(BuildUtils.cpSeparator);

    final output = p.join(_fs.buildFilesDir.path, 'AndroidManifest.xml');
    final args = <String>[
      ...['-cp', classpath],
      'com.android.manifmerger.Merger',
      ...['--main', mainManifest],
      ...['--libs', depManifests.join(BuildUtils.cpSeparator)],
      ...['--property', 'MIN_SDK_VERSION=${config.minSdk.toString()}'],
      ...['--out', output],
      ...['--log', 'INFO'],
    ];

    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(),
          args.map((el) => el.replaceAll('\\', '/')).toList());
    } catch (e) {
      rethrow;
    }
  }

  static Future<void> execDesugarer(String artJarPath, Config config) async {
    _lgr.dbg('Desugaring is now handled natively during the DEX phase by D8/R8.');
    _lgr.dbg('Skipping standalone desugar.jar execution.');
    // We keep this method for API compatibility in build.dart but it is now a no-op,
    // as desugaring happens in execD8.
    return;
  }

  static Future<void> execJarJar({
    required Config config,
    required String inputJar,
    required String outputJar,
  }) async {
    final relocation = config.relocation;
    if (relocation == null || !relocation.enableAutoRelocation) return;

    _lgr.dbg('Running JarJar for Package Relocation (Shading).');

    final jarjarJar = await _libService.jarjarJar();
    final rulesFile = p.join(_fs.buildFilesDir.path, 'jarjar_rules.txt').asFile(true);
    
    final rules = StringBuffer();
    final orgName = config.author.isNotEmpty ? config.author : 'com.example';
    final repackedPrefix = '$orgName.repacked';

    if (relocation.include.isNotEmpty) {
      for (final inc in relocation.include) {
        final pattern = inc.replaceAll('**', '@1');
        rules.writeln('rule $inc $repackedPrefix.$pattern');
      }
    } else {
      rules.writeln('rule ** $repackedPrefix.@1');
    }

    await rulesFile.writeAsString(rules.toString());

    final args = <String>[
      '-jar',
      jarjarJar,
      'process',
      rulesFile.path,
      inputJar,
      outputJar,
    ];

    try {
      await _processRunner.runExecutable(
        BuildUtils.javaExe(),
        args.map((el) => el.replaceAll('\\', '/')).toList(),
        logToConsole: false,
      );
      _lgr.dbg('JarJar is successfully executed.');
    } catch (e) {
      rethrow;
    }
  }

  static Future<void> runStrGuard({
    required String inputDir,
    required String outputDir,
    String? key,
    required List<String> packages,
  }) async {
    _lgr.dbg('Running StrGuard.');
    final strGuardJar = await _libService.strguardJar();

    final args = <String>[
      '-jar',
      strGuardJar,
      '--input',
      inputDir,
      '--output',
      outputDir,
    ];

    if (key != null && key.isNotEmpty) {
      args.addAll(['--key', key]);
    }

    if (packages.isNotEmpty) {
      args.addAll(['--packages', packages.join(',')]);
    }

    try {
      await _processRunner.runExecutable(
        BuildUtils.javaExe(),
        args,
        logToConsole: false,
      );
      _lgr.dbg('StrGuard successfully executed.');
    } catch (e) {
      _lgr.err('An error occurred during StrGuard execution.\n$e');
      rethrow;
    }
  }
}
