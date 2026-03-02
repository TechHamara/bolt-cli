import 'dart:io';

import 'package:collection/collection.dart';
import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/config/config.dart';

import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/services/lib_service.dart';

import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/utils/process_runner.dart';

class Executor {
  static final _fs = GetIt.I<FileService>();
  static final _libService = GetIt.I<LibService>();
  static final _processRunner = ProcessRunner();

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

  static Future<void> execD8(Config config, String artJarPath) async {
    final args = <String>[
      ...['-cp', await _libService.r8Jar()],
      'com.android.tools.r8.D8',
      ...['--min-api', '${config.minSdk}'],
      ...['--lib', _androidJar(config)],
      '--release',
      '--no-desugaring',
      '--output',
      p.join(_fs.buildRawDir.path, 'classes.jar'),
      artJarPath
    ];

    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(),
          args.map((el) => el.replaceAll('\\', '/')).toList());
    } catch (e) {
      rethrow;
    }
  }

  static Future<void> execProGuard(
    Config config,
    String artJarPath,
    Set<String> aarProguardRules, {
    bool deannotateOnly = false,
  }) async {
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
      if (deannotateOnly) ...[
        '-dontshrink',
        '-dontoptimize',
        '-dontobfuscate',
        '-keepattributes',
        'Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable',
      ] else ...[
        ...[for (final el in aarProguardRules) '-include $el'],
        '@${rulesFile.path}',
      ],
    ];

    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(),
          args.map((el) => el.replaceAll('\\', '/')).toList());
    } catch (e) {
      rethrow;
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
    final outputJar = p
        .join(_fs.buildRawDir.path, 'files', 'AndroidRuntime.dsgr.jar')
        .asFile();

    final bootclasspath = await () async {
      final javaHome = await BuildUtils.javaHomeDir();
      final forJdk8AndBelow = p.join(javaHome, 'jre', 'lib', 'rt.jar').asFile();
      if (await forJdk8AndBelow.exists()) {
        return forJdk8AndBelow;
      }
      return p.join(javaHome, 'jmods', 'java.base.jmod').asFile();
    }();

    final providedDeps = await _libService.providedDependencies(config);
    final classpathJars = providedDeps
        .map((el) => el.classpathJars(providedDeps))
        .flattened
        .toSet();

    final desugarerArgs = <String>[
      '--desugar_try_with_resources_if_needed',
      '--copy_bridges_from_classpath',
      ...['--bootclasspath_entry', '\'${bootclasspath.path}\''],
      ...['--input', '\'$artJarPath\''],
      ...['--output', '\'${outputJar.path}\''],
      ...classpathJars.map((dep) => '--classpath_entry' '\n' '\'$dep\''),
      ...['--min_sdk_version', '${config.minSdk}'],
    ];
    final argsFile =
        p.join(_fs.buildFilesDir.path, 'desugar.args').asFile(true);
    await argsFile.writeAsString(desugarerArgs.join('\n'));

    final tempDir = await p.join(_fs.buildFilesDir.path).asDir().createTemp();
    final args = <String>[
      // Required on JDK 11 (>11.0.9.1)
      // https://github.com/bazelbuild/bazel/commit/cecb3f1650d642dc626d6f418282bd802c29f6d7
      '-Djdk.internal.lambda.dumpProxyClasses=${tempDir.path}',
      ...['-cp', await _libService.desugarJar()],
      'com.google.devtools.build.android.desugar.Desugar',
      '@${argsFile.path}',
    ];

    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(), args);
    } catch (_) {
      rethrow;
    } finally {
      await tempDir.delete(recursive: true);
    }

    await outputJar.copy(artJarPath);
    await outputJar.delete();
  }
}
