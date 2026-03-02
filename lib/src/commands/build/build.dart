import 'dart:convert' show jsonDecode;
import 'dart:io' show File, Platform;
import 'package:bolt/src/version.dart';

import 'package:archive/archive_io.dart';
import 'package:args/command_runner.dart';
import 'package:collection/collection.dart';
import 'package:get_it/get_it.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/build/block_renderer.dart';
import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/commands/deps/sync.dart';
import 'package:bolt/src/commands/build/tools/compiler.dart';
import 'package:bolt/src/commands/build/tools/executor.dart';
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/resolver/artifact.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/utils/file_extension.dart';

/// Result of the shrink configuration calculation.
///
/// The values are derived from command‑line flags plus the configuration
/// file.  We expose this logic in a helper so tests can verify it without
/// executing the entire build command.
class ShrinkInfo {
  ShrinkInfo({
    required this.runProguard,
    required this.runR8,
    required this.shrink,
    required this.shouldRunProguard,
    required this.performingShrink,
  });

  final bool runProguard;
  final bool runR8;
  final bool shrink;
  final bool shouldRunProguard;
  final bool performingShrink;
}

ShrinkInfo computeShrinkInfo({
  bool? requestedProguard,
  bool? requestedR8,
  required bool configProguard,
  required bool configR8,
}) {
  final runProguard = requestedProguard ?? configProguard;
  final runR8 = requestedR8 ?? configR8;
  final shrink = runProguard || runR8;

  // If the user explicitly asked for ProGuard we run it even if R8 is also
  // enabled.  previously we would suppress ProGuard whenever R8 was true, so
  // a command such as `bolt build -r` would silently do nothing when
  // `config.r8` was true.  this behaviour is the basis of the bug reported by
  // users.
  final shouldRunProguard =
      runProguard && (requestedProguard == true || !runR8);
  final performingShrink = shouldRunProguard || runR8;

  return ShrinkInfo(
    runProguard: runProguard,
    runR8: runR8,
    shrink: shrink,
    shouldRunProguard: shouldRunProguard,
    performingShrink: performingShrink,
  );
}

class BuildCommand extends Command<int> {
  final Logger _lgr = GetIt.I<Logger>();
  final FileService _fs = GetIt.I<FileService>();
  late final LibService _libService;

  BuildCommand() {
    argParser.addFlag(
      'optimize',
      abbr: 'o',
      help:
          'Indicates to optimize the extension size even there is no ProGuard.',
    );
    argParser.addFlag(
      'proguard',
      abbr: 'r',
      help: '''Indicates the execution of the ProGuard task. Defaults off unless
config enables it; use `-r` to force ProGuard on for this build.''',
    );
    argParser.addFlag(
      'r8',
      abbr: 's',
      help:
          'Indicates the execution of the R8 shriker task. Pass it with the build command.',
    );
    argParser.addFlag(
      'dex',
      help:
          'Indicates to generate the DEX Bytecode by the R8 dexer. Pass it with the build command.',
    );
    argParser.addFlag(
      'build-blocks',
      abbr: 'b',
      help: 'Generates PNG blocks for each builder.',
    );
    argParser.addFlag(
      'keep-manifest',
      abbr: 'm',
      help:
          'Keeps all classes declared in AndroidManifest.xml from being obfuscated by ProGuard.',
    );
  }

  @override
  String get description =>
      'Builds the extension project in current working directory.';

  @override
  String get name => 'build';

  final _stopwatch = Stopwatch();

  /// Builds the extension in the current directory
  @override
  Future<int> run() async {
    _stopwatch.start();
    _lgr.startTask('Initializing build');

    await GetIt.I.isReady<LibService>();
    _libService = GetIt.I<LibService>();

    // load configuration; we may later need to rewrite it if the user forces
    // ProGuard while R8 is enabled.  use a non-nullable local once we've
    // verified the file exists.
    late Config config;
    {
      final loaded = await Config.load(_fs.configFile, _lgr);
      if (loaded == null) {
        _lgr.stopTask(false);
        return 1;
      }
      config = loaded;
    }

    // Check if user explicitly provided -r flag to enable ProGuard
    bool? earlyRequestedProguard;
    if (argResults?.wasParsed('proguard') == true) {
      earlyRequestedProguard = argResults?['proguard'] as bool? ?? false;
    }
    // Only update config if user explicitly requested ProGuard (-r flag) while R8 is enabled
    if (earlyRequestedProguard == true &&
        config.proguard == false &&
        config.r8) {
      try {
        await _fixupConfigProguard();
        // reload configuration so in-memory object reflects the update
        config = (await Config.load(_fs.configFile, _lgr))!;
      } catch (_) {
        // non-fatal
      }
    }

    // create .bolt/logs.txt and point the logger at it so every message is
    // captured for post‑mortem inspection.  The file is overwritten each run.
    final boltDir = _fs.dotBoltDir;
    final logFile = p.join(boltDir.path, 'logs.txt').asFile(true);

    // gather environment info similar to the provided by the user
    final env = Platform.environment;
    final username = env['USER'] ?? env['USERNAME'] ?? 'unknown';
    final userHome = env['HOME'] ?? env['USERPROFILE'] ?? 'unknown';
    final javaHome = env['JAVA_HOME'] ?? '';

    bool debugBuild = false;
    assert(() {
      debugBuild = true;
      return true;
    }());

    final header = StringBuffer();
    header.writeln('Bolt CLI version: $packageVersion');
    header.writeln('OS Name: ${Platform.operatingSystem}');
    header.writeln('OS Version: ${Platform.operatingSystemVersion}');
    header.writeln('Architecture: ${Platform.version}');
    header.writeln('Username: $username');
    header.writeln('User Home: $userHome');
    header.writeln('Debug Build: $debugBuild');
    header.writeln('Working Directory: ${_fs.cwd}');
    header.writeln('Config file: ${_fs.configFile.path}');
    if (javaHome.isNotEmpty) {
      header.writeln('JAVA_HOME from environment variable.');
      header.writeln('JAVA_HOME: $javaHome');
    }
    header.writeln('Path Separator: ${Platform.pathSeparator}');
    header.writeln('File Separator: ${Platform.isWindows ? '\\' : '/'}');
    header.writeln('Line Separator: ${Platform.isWindows ? '\r\n' : '\n'}');
    header.writeln();

    await logFile.writeAsString(header.toString());
    _lgr.setOutputFile(logFile);

    // mimic FAST's opening message
    _lgr.info('bolt build initialized.');

    // configuration/debug info should not normally clutter the console; keep
    // the environment dump inside the log file only.  promote to debug level
    // so that it can still be enabled with `--debug`.
    _lgr.dbg('Loaded configuration');
    _lgr.dbg('  android_sdk: ${config.androidSdk}');
    _lgr.dbg('  min_sdk: ${config.minSdk}');
    _lgr.dbg('  desugar: ${config.desugar}');
    _lgr.dbg('  desugar_sources: ${config.desugarSources}');
    _lgr.dbg('  desugar_deps: ${config.desugarDeps}');
    _lgr.dbg('  desugar_dex: ${config.desugarDex}');
    _lgr.dbg('  proguard: ${config.proguard}');
    _lgr.dbg('  proguard_version: ${config.proguardVersion}');
    _lgr.dbg('  R8: ${config.r8}');
    _lgr.dbg('  kotlin_version: ${config.kotlin.compilerVersion}');

    _lgr.stopTask();

    // Ensure project dependencies are up-to-date before starting the build.  A
    // missing kotlin-stdlib was the root cause of many confusing errors, and
    // users shouldn't have to remember to invoke `bolt sync` separately.
    final syncRes = await SyncCommand().run(title: 'Syncing dependencies');
    if (syncRes != 0) {
      // SyncCommand already reports failure internally.
      return syncRes;
    }

    _lgr.info('Cleaning build caches');
    _lgr.info('Increasing Components version');
    _lgr.startTask('Compiling Java classes');
    try {
      final timestampBox = await Hive.openLazyBox<DateTime>(timestampBoxName);
      await _mergeManifests(config, timestampBox);
      final buildBlocks = argResults?['build-blocks'] as bool? ?? false;
      await _compile(config, timestampBox, buildBlocks);
    } catch (e, s) {
      _catchAndStop(e, s);
      return 1;
    }
    _lgr.stopTask();

    _lgr.startTask('Processing');
    final componentsJson =
        p.join(_fs.buildRawDir.path, 'components.json').asFile();
    final buildInfosJson = p
        .join(_fs.buildRawDir.path, 'files', 'component_build_infos.json')
        .asFile();
    if (!await componentsJson.exists() || !await buildInfosJson.exists()) {
      _lgr
        ..err('Unable to find components.json or component_build_infos.json')
        ..log(
          '${'help '.green()} Make sure you have annotated your extension with @Extension annotation',
        )
        ..stopTask(false);
      return 1;
    }

    final String artJarPath;
    try {
      _lgr.info('Copying extension assets');
      await BuildUtils.copyAssets(config);
      await BuildUtils.copyLicense(config);
      _lgr.info('Generating AndroidRuntime.jar');
      artJarPath = await _createArtJar(config);
    } catch (e, s) {
      _catchAndStop(e, s);
      return 1;
    }
    _lgr.stopTask();

    if (config.desugar || config.desugarSources || config.desugarDeps) {
      _lgr.startTask('Desugaring Java 8 language features');
      try {
        await Executor.execDesugarer(artJarPath, config);
      } catch (e, s) {
        _catchAndStop(e, s);
        return 1;
      }
      _lgr.stopTask();
    }

    // Decide whether to shrink/obfuscate based on command‑line flags and
    // configuration file.  The `-r` flag takes precedence over bolt.yml;
    // when neither specifies anything the default is *false* (i.e. the user
    // must opt in).
    // use wasParsed() because ArgParser returns `false` for an absent flag
    // and we need to know whether the user explicitly provided it.
    bool? requestedProguard;
    if (argResults?.wasParsed('proguard') == true) {
      requestedProguard = argResults?['proguard'] as bool? ?? false;
    }
    bool? requestedR8;
    if (argResults?.wasParsed('r8') == true) {
      requestedR8 = argResults?['r8'] as bool? ?? false;
    }

    // compute shrink details via shared helper so tests can verify behaviour
    final shrinkInfo = computeShrinkInfo(
      requestedProguard: requestedProguard,
      requestedR8: requestedR8,
      configProguard: config.proguard,
      configR8: config.r8,
    );
    final runProguard = shrinkInfo.runProguard;
    final runR8 = shrinkInfo.runR8;
    final shrink = shrinkInfo.shrink;

    _lgr.dbg(
      'requestedProguard=$requestedProguard requestedR8=$requestedR8 '
      'config.proguard=${config.proguard} config.r8=${config.r8}',
    );

    // decide whether dexing should occur (controlled via --dex or
    // desugar_dex config value).
    bool? requestedDex;
    if (argResults?.wasParsed('dex') == true) {
      requestedDex = argResults?['dex'] as bool;
    }
    final runDex = requestedDex ?? config.desugarDex;

    // echo effective decisions so users know what actually happened
    _lgr.info('Effective build options:');
    _lgr.info('  shrink (proguard|R8): $shrink');
    _lgr.info('    proguard flag: $runProguard');
    _lgr.info('    R8 flag: $runR8');
    _lgr.info('  dex generation: $runDex');

    // determine which shrinker to drive.  When the user explicitly asks for
    // ProGuard we honour that request even if R8 is also enabled; the
    // `computeShrinkInfo` helper encodes that logic.
    final shouldRunProguard = shrinkInfo.shouldRunProguard;
    final performingShrink = shrinkInfo.performingShrink;

    if (performingShrink || config.deannonate) {
      if (performingShrink) {
        _lgr.startTask('Optimizing and obfuscating the bytecode');
      } else {
        _lgr.startTask('Deannotating bytecode');
      }

      final deps = await _libService.extensionDependencies(
        config,
        includeAi2ProvidedDeps: true,
        includeProjectProvidedDeps: true,
      );
      final aars = deps.where((el) => el.packaging == 'aar');

      final proguardRules = aars
          .map(
            (el) => BuildUtils.resourceFromExtractedAar(
              el.artifactFile,
              'proguard.txt',
            ),
          )
          .where((el) => el.existsSync())
          .map((el) => el.path)
          .toSet();

      final keepManifest = argResults?['keep-manifest'] as bool? ?? false;
      if (keepManifest) {
        final manifestFile =
            p.join(_fs.buildFilesDir.path, 'AndroidManifest.xml').asFile();
        if (manifestFile.existsSync()) {
          final content = await manifestFile.readAsString();
          final keepClasses = <String>{};
          for (final match in RegExp(
            '<(?:activity|service|receiver|provider)[^>]*>',
          ).allMatches(content)) {
            final tagContent = match.group(0)!;
            final nameMatch = RegExp(
              'android:name="([^"]+)"',
            ).firstMatch(tagContent);
            if (nameMatch != null) {
              keepClasses.add(nameMatch.group(1)!);
            }
          }
          if (keepClasses.isNotEmpty) {
            final rulesContent = keepClasses
                .map((el) => '-keep public class $el { *; }')
                .join('\n');
            final manifestRulesFile =
                p.join(_fs.buildFilesDir.path, 'manifest-rules.pro').asFile();
            await manifestRulesFile.writeAsString(rulesContent);
            proguardRules.add(manifestRulesFile.path);
          }
        }
      }

      if (shouldRunProguard) {
        try {
          await Executor.execProGuard(
            config,
            artJarPath,
            proguardRules,
            deannotateOnly: !runProguard,
          );
        } catch (e, s) {
          _catchAndStop(e, s);
          return 1;
        }
      } else if (runR8) {
        _lgr.info('R8 enabled; ProGuard step skipped.');
      }
      _lgr.stopTask();
    }

    if (runDex) {
      _lgr.startTask('Generating DEX bytecode');
      try {
        await Executor.execD8(config, artJarPath);
      } catch (e, s) {
        _catchAndStop(e, s);
        return 1;
      }
      _lgr.stopTask();
    } else {
      _lgr.info('Skipping dex generation (dex disabled)');
    }

    _lgr.startTask('Packaging the extension');
    try {
      await _assemble(config, argResults?['build-blocks'] as bool? ?? false);
    } catch (e, s) {
      _catchAndStop(e, s);
      return 1;
    }
    _lgr.stopTask();

    _logFinalLine(true);
    return 0;
  }

  void _catchAndStop(Object e, StackTrace s) {
    if (e.toString().isNotEmpty) {
      _lgr.dbg(e.toString());
    }
    _lgr
      ..dbg(s.toString())
      ..stopTask(false);

    _logFinalLine(false);
  }

  /// If the user forced a ProGuard run while R8 support was enabled we
  /// persist the decision by altering the `bolt.yml` file.  This avoids
  /// surprising behaviour on subsequent invocations and matches the user's
  /// intent.
  Future<void> _fixupConfigProguard() async {
    final file = _fs.configFile;
    final contents = await file.readAsString();
    _lgr.dbg('persisting proguard flag into ${file.path}');

    // Split into lines so we can safely remove any existing proguard entries
    // (whether true or false, commented or not).  We treat any line that
    // begins with "proguard:" after optional whitespace as an existing
    // declaration and drop it.  This avoids duplicate keys which YAML parsers
    // complain about.
    final lines = contents.split('\n');
    final filtered = <String>[];
    for (final line in lines) {
      final trimmed = line.trimRight();
      if (RegExp(r'^\s*proguard\s*:').hasMatch(trimmed)) {
        // skip this line entirely
        continue;
      }
      filtered.add(line);
    }

    filtered.add('proguard: true');
    final newContents = filtered.join('\n');
    await file.writeAsString(newContents);
    _lgr.dbg('updated bolt.yml to enable proguard');
  }

  void _logFinalLine(bool success) {
    var line = '\n';
    line += '> ';
    line += success ? 'BUILD SUCCESSFUL '.green() : 'BUILD FAILED '.red();
    final timeMs = _stopwatch.elapsedMilliseconds;
    final sec = (timeMs / 1000).floor();
    final ms = timeMs % 1000;
    line += 'in ${sec}s ${ms}ms'.grey();
    _lgr.log(line);
  }

  Future<void> _mergeManifests(
    Config config,
    LazyBox<DateTime> timestampBox,
  ) async {
    final deps = await _libService.extensionDependencies(config);
    final requiredAars = deps.where(
      (el) =>
          el.scope == Scope.runtime &&
          el.scope == Scope.compile &&
          el.packaging == 'aar',
    );

    final manifests = requiredAars
        .map(
          (el) => BuildUtils.resourceFromExtractedAar(
            el.artifactFile,
            'AndroidManifest.xml',
          ),
        )
        .where((el) => el.existsSync())
        .map((el) => el.path);

    final mainManifest =
        p.join(_fs.srcDir.path, 'AndroidManifest.xml').asFile();

    if (mainManifest.existsSync()) {
      _lgr.info('Reading AndroidManifest.xml');
      var content = await mainManifest.readAsString();
      final packageMatch = RegExp('package="([^"]+)"').firstMatch(content);
      if (packageMatch != null) {
        final pkg = packageMatch.group(1)!;
        // Expand android:name=".MyClass" or android:name="...MyClass"
        content = content.replaceAllMapped(
          RegExp(r'android:name="\.+([^"]+)"'),
          (match) {
            final name = match.group(1)!;
            return 'android:name="$pkg.$name"';
          },
        );
        await mainManifest.writeAsString(content);
      }
    }

    final outputManifest =
        p.join(_fs.buildFilesDir.path, 'AndroidManifest.xml').asFile(true);

    if (manifests.isEmpty) {
      _lgr.dbg('No manifests found in dependencies; skipping manifest merge');
      await outputManifest.delete();
      return;
    }

    final lastMergeTime = await timestampBox.get(androidManifestTimestampKey);
    final needMerge = !await outputManifest.exists() ||
        (lastMergeTime?.isBefore(await mainManifest.lastModified()) ?? true);
    if (!needMerge) {
      return;
    }

    _lgr.info('Merging Android manifests...');
    await Executor.execManifMerger(
      config,
      mainManifest.path,
      manifests.toSet(),
    );

    await timestampBox.put(androidManifestTimestampKey, DateTime.now());
  }

  /// Compiles extension's source files.
  Future<void> _compile(
    Config config,
    LazyBox<DateTime> timestampBox,
    bool buildBlocks,
  ) async {
    final srcFiles =
        _fs.srcDir.path.asDir().listSync(recursive: true).whereType<File>();
    final javaFiles = srcFiles
        .whereType<File>()
        .where((file) => p.extension(file.path) == '.java')
        .map((f) => f.path)
        .toSet();
    final ktFiles = srcFiles.whereType<File>().where(
          (file) => p.extension(file.path) == '.kt',
        );

    final fileCount = javaFiles.length + ktFiles.length;
    _lgr.info('Picked $fileCount source file${fileCount > 1 ? 's' : ''}');

    final dependencies = await _libService.extensionDependencies(
      config,
      includeAi2ProvidedDeps: true,
      includeProjectProvidedDeps: true,
    );
    final compileClasspathJars = dependencies
        .where((el) => el.scope == Scope.compile || el.scope == Scope.provided)
        .map((el) => el.classpathJars(dependencies))
        .flattened
        .toSet();

    // make sure the annotations jar actually contains the DesignerComponent
    // class; older remote releases omitted it which led to compilation errors.
    // if it's missing we generate a tiny supplemental JAR and add it to the
    // classpath before invoking javac.
    await Compiler.patchAnnotationJarIfNecessary(compileClasspathJars);

    // Users occasionally drop a local JAR or AAR directly inside `src` (for
    // example when they don't bother creating a `deps` directory).  Treat any
    // such archive as a compile‑time library by adding it to the classpath.
    final srcArchives = _fs.srcDir
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.jar') || f.path.endsWith('.aar'))
        .map((f) => f.path);
    compileClasspathJars.addAll(srcArchives);

    // auto-detect java8 usage: check for lambda arrows in source files so we
    // don't accidentally compile modern library code with -source 1.7.  This
    // catches the common case without requiring the user to flip a flag.
    // java8 support may be needed either because the user explicitly
    // requested it in the config or because source files contain language
    // features (lambdas/method refs) that require a 1.8 compiler.  We also
    // keep the existing desugar flags for compatibility.
    bool supportJava8 = config.java8 ||
        config.desugar ||
        config.desugarSources ||
        config.desugarDeps;
    if (!supportJava8 && javaFiles.isNotEmpty) {
      supportJava8 = await Compiler.hasJava8Features(javaFiles);
      if (supportJava8) {
        ///  _lgr.info(
        ///      'Detected Java 8 language features; enabling java8 compilation');
      }
    }

    // If Kotlin compilation is involved, make sure the standard library JAR is
    // always present on the classpath.  The user may not explicitly declare it
    // in bolt.yml or the sync might not have fetched it yet; the compiler
    // distribution already contains a copy.
    if (ktFiles.isNotEmpty) {
      final stdlibJars = (await _libService.kotlincJars(
        config.kotlin.compilerVersion,
      ))
          .where((p) => p.contains('kotlin-stdlib'))
          .toSet();
      compileClasspathJars.addAll(stdlibJars);
    }

    try {
      if (ktFiles.isNotEmpty) {
        await Compiler.compileKtFiles(
          compileClasspathJars,
          config.kotlin.compilerVersion,
          timestampBox,
          config,
          buildBlocks,
        );
      }

      if (javaFiles.isNotEmpty) {
        // pass the previously computed set so that we compile exactly what was
        // announced above; avoids weird discrepancies that crept in when the
        // directory contents changed mid-build.
        await Compiler.compileJavaFiles(
          compileClasspathJars,
          supportJava8,
          timestampBox,
          config,
          buildBlocks,
          javaFiles: javaFiles,
        );
      }
    } catch (e, s) {
      _lgr
        ..dbg(e.toString())
        ..dbg(s.toString());
      rethrow;
    }
  }

  Future<String> _createArtJar(Config config) async {
    final artJarPath = p.join(
      _fs.buildRawDir.path,
      'files',
      'AndroidRuntime.jar',
    );
    final zipEncoder = ZipFileEncoder()..create(artJarPath);

    final deps = await _libService.extensionDependencies(config);
    final requiredDeps = deps
        .where((el) => el.scope == Scope.compile || el.scope == Scope.runtime)
        .toList(growable: true);

    // If the project doesn't compile any classes, we don't need the AI2
    // runtime dependency. Removing it makes empty extensions much smaller.
    final hasUserClasses = _fs.buildClassesDir
        .listSync(recursive: true)
        .whereType<File>()
        .any((f) => p.extension(f.path) == '.class');
    if (!hasUserClasses) {
      requiredDeps.removeWhere((el) => el.coordinate == ai2RuntimeCoord);
      if (!requiredDeps.any((el) => el.coordinate == ai2RuntimeCoord)) {
        _lgr.dbg('No compiled classes – excluding AI2 runtime from ART.jar');
      }
    }

    final requiredJars =
        requiredDeps.map((el) => el.classesJar).nonNulls.toSet();

    // Add class files from all required deps into the ART.jar
    if (requiredJars.isNotEmpty) {
      _lgr.info('Merging dependencies into a single JAR...');

      final addedPaths = <String>{};
      for (final jarPath in requiredJars) {
        final jar = jarPath.asFile();
        if (!await jar.exists()) {
          _lgr.err('Unable to find required JAR: $jarPath');
        }

        final decodedJar = ZipDecoder()
            .decodeBytes(await jar.readAsBytes())
            .files
            .whereNot(
              (el) =>
                  addedPaths.contains(el.name) ||
                  el.name.startsWith('META-INF'),
            )
            // Do not include files other than .class files.
            .where((el) {
          if (!el.isFile) {
            return true;
          }
          return p.extension(el.name) == '.class';
        });
        for (final file in decodedJar) {
          zipEncoder.addArchiveFile(file);
          addedPaths.add(file.name);
        }
      }
    }

    // Add extension classes to ART.jar
    final classFiles = _fs.buildClassesDir.listSync(recursive: true);
    for (final file in classFiles) {
      if (file is File &&
          !file.path.contains('META-INF') &&
          p.extension(file.path) == '.class') {
        final path = p.relative(file.path, from: _fs.buildClassesDir.path);
        await zipEncoder.addFile(file, path);
      }
    }

    await zipEncoder.close();
    return artJarPath;
  }

  Future<void> _assemble(Config config, bool buildBlocks) async {
    final componentsJsonFile =
        p.join(_fs.buildDir.path, 'raw', 'components.json').asFile();

    final org = () async {
      final json = jsonDecode(await componentsJsonFile.readAsString());
      final type = json[0]['type'] as String;

      final split = type.split('.')..removeLast();
      return split.join('.');
    }();

    final outputDir = p.join(_fs.cwd, 'out').asDir(true);
    final aix = p.join(outputDir.path, '${await org}.aix');
    final zipEncoder = ZipFileEncoder()..create(aix);

    try {
      final jsonList =
          jsonDecode(await componentsJsonFile.readAsString()) as List;

      for (final file in _fs.buildRawDir.listSync(recursive: true)) {
        if (file is File) {
          final name = p.relative(file.path, from: _fs.buildRawDir.path);
          await zipEncoder.addFile(file, p.join(await org, name));
        }
      }
      // Generate Documentation
      _lgr.info('Generating docs in Markdown');
      final buffer = StringBuffer();
      final extensionVersion = config.version;
      final author = config.authorName;
      // Minimum API level is known at configuration time; size will be added
      // once the AIX has been written.

      for (final compDataDynamic in jsonList) {
        final compData = compDataDynamic as Map<String, dynamic>;
        final String componentName = compData['name'] ?? 'Unknown Component';
        final String dateStr = DateTime.now().toString().split(' ')[0];

        buffer.writeln('<div align="center">');
        buffer.writeln('<h1><kbd>🧩 $componentName</kbd></h1>');
        buffer.writeln('An extension for MIT App Inventor 2.<br>');
        buffer.writeln('${compData['description'] ?? ''}');
        buffer.writeln('</div>\n');

        buffer.writeln('## 📝 Specifications');
        final type = compData['type'] as String;
        final package = type.contains('.')
            ? (type.split('.')..removeLast()).join('.')
            : type;
        buffer.writeln('📦 **Package:** $package');
        buffer.writeln('⚙️ **Version:** $extensionVersion');
        buffer.writeln('📅 **Updated On:** $dateStr');
        if (author.isNotEmpty) {
          buffer.writeln('👤 **Author:** $author');
        }
        buffer.writeln(
          '💻 **Built & documented using:** [Bolt CLI](https://github.com/TechHamara/bolt-cli)\n',
        );

        final events = compData['events'] as List? ?? [];
        if (events.isNotEmpty) {
          buffer.writeln('## <kbd>Events:</kbd>');
          buffer.writeln(
            '**$componentName** has total ${events.length} events.\n',
          );
          var blockIndex = 1;
          for (final e in events) {
            buffer.writeln('### $blockIndex ${e['name']}');
            buffer.writeln('${e['description']}\n');
            final params = e['params'] as List? ?? [];
            if (params.isNotEmpty) {
              buffer.writeln('| Parameter | Type |');
              buffer.writeln('| - | - |');
              for (final p in params) {
                buffer.writeln('| ${p['name']} | ${p['type']} |');
              }
              buffer.writeln();
            }
            blockIndex++;
          }
        }

        final methods = compData['methods'] as List? ?? [];
        if (methods.isNotEmpty) {
          buffer.writeln('## <kbd>Methods:</kbd>');
          buffer.writeln(
            '**$componentName** has total ${methods.length} methods.\n',
          );
          var blockIndex = 1;
          for (final m in methods) {
            buffer.writeln('### $blockIndex ${m['name']}');
            buffer.writeln('${m['description']}\n');
            final params = m['params'] as List? ?? [];
            if (params.isNotEmpty) {
              buffer.writeln('| Parameter | Type |');
              buffer.writeln('| - | - |');
              for (final p in params) {
                buffer.writeln('| ${p['name']} | ${p['type']} |');
              }
              buffer.writeln();
            }
            if (m['returnType'] != null) {
              buffer.writeln('**Returns:** ${m['returnType']}\n');
            }
            blockIndex++;
          }
        }

        final properties = compData['properties'] as List? ?? [];
        final setters = properties
            .where((p) => p['rw'] == 'write' || p['rw'] == 'read-write')
            .toList();
        final getters = properties
            .where((p) => p['rw'] == 'read' || p['rw'] == 'read-write')
            .toList();

        if (setters.isNotEmpty) {
          buffer.writeln('## <kbd>Setters:</kbd>');
          buffer.writeln(
            '**$componentName** has total ${setters.length} setter properties.\n',
          );
          var blockIndex = 1;
          for (final p in setters) {
            buffer.writeln('### $blockIndex ${p['name']}');
            buffer.writeln('${p['description']}\n');
            buffer.writeln('* Input type: `${p['type']}`\n');
            blockIndex++;
          }
        }

        if (getters.isNotEmpty) {
          buffer.writeln('## <kbd>Getters:</kbd>');
          buffer.writeln(
            '**$componentName** has total ${getters.length} getter properties.\n',
          );
          var blockIndex = 1;
          for (final p in getters) {
            buffer.writeln('### $blockIndex ${p['name']}');
            buffer.writeln('${p['description']}\n');
            buffer.writeln('* Return type: `${p['type']}`\n');
            blockIndex++;
          }
        }

        // Generate documentation for block properties with helper functions
        final blockProperties = compData['blockProperties'] as List? ?? [];
        if (blockProperties.isNotEmpty) {
          buffer.writeln('## <kbd>Block Properties:</kbd>');
          buffer.writeln(
            '**$componentName** has total ${blockProperties.length} block properties.\n',
          );
          var blockIndex = 1;
          for (final bp in blockProperties) {
            buffer.writeln('### $blockIndex ${bp['name']}');
            buffer.writeln('${bp['description']}\n');
            buffer.writeln('* Input type: `${bp['type']}`');

            // Check if this block property has a helper function
            final helper = bp['helper'] as Map? ?? {};
            if (helper.isNotEmpty) {
              final helperData = helper['data'] as Map? ?? {};
              final helperType = helperData['tag'] as String?;
              final options = helperData['options'] as List? ?? [];

              if (helperType != null && helperType.isNotEmpty) {
                buffer.writeln('* Helper type: `$helperType`');
              }

              if (options.isNotEmpty) {
                final optionNames = options
                    .map((opt) => opt['name'] as String? ?? '')
                    .where((name) => name.isNotEmpty)
                    .join('`, `');
                buffer.writeln('* Helper enums: `$optionNames`');
              }
            }

            buffer.writeln();
            blockIndex++;
          }
        }

        if (compDataDynamic != jsonList.last) {
          buffer.writeln('\n---\n');
        }
      }

      final extTxt = p.join(outputDir.path, 'extension.txt').asFile();
      await extTxt.writeAsString(buffer.toString());

      // Optionally generate block PNGs for each component.
      if (buildBlocks) {
        _lgr.info('Generating block images...');
        final blocksOutputDir = p.join(outputDir.path, 'blocks');
        for (final compDataDyn in jsonList) {
          final compData = compDataDyn as Map<String, dynamic>;
          await BlockRenderer.generateBlocks(
            componentData: compData,
            outputDir: blocksOutputDir,
          );
        }
      }
    } catch (e) {
      rethrow;
    } finally {
      await zipEncoder.close();
      final aixFile = aix.asFile();
      String sizeStr = '';
      if (aixFile.existsSync()) {
        sizeStr = _formatFileSize(aixFile.lengthSync());
        final relativePath = p.join('.', 'out', p.basename(aix));
        _lgr.info(
          'Packaging extension at ${relativePath.blue()} (${sizeStr.cyan()})',
        );
      } else {
        final relativePath = p.join('.', 'out', p.basename(aix));
        _lgr.info('Packaging extension at ${relativePath.blue()}');
      }

      // prepend size and min-api information to the markdown file
      final extTxt = p.join(outputDir.path, 'extension.txt').asFile();
      if (extTxt.existsSync()) {
        var original = await extTxt.readAsString();

        // insert size/min-api lines directly under the package entry in the
        // specifications section so users see them alongside other metadata.
        if (original.contains('📦 **Package:**')) {
          final lines = original.split('\n');
          for (var i = 0; i < lines.length; ++i) {
            if (lines[i].startsWith('📦 **Package:**')) {
              final insertLines = <String>[
                '💾 **Size:** ${sizeStr.isEmpty ? 'unknown' : sizeStr}',
                '📱 **Minimum API Level:** ${config.minSdk}',
              ];
              lines.insertAll(i + 1, insertLines);
              break;
            }
          }
          original = lines.join('\n');
        }

        // write the modified content back (no prepend header to avoid duplicates)
        await extTxt.writeAsString(original);
      }
    }
  }

  static String _formatFileSize(int bytes) {
    if (bytes < 1024) return '${bytes}B';
    if (bytes < 1024 * 1024) {
      final kb = bytes / 1024;
      return kb == kb.roundToDouble()
          ? '${kb.toInt()}KB'
          : '${kb.toStringAsFixed(1)}KB';
    }
    final mb = bytes / (1024 * 1024);
    return mb == mb.roundToDouble()
        ? '${mb.toInt()}MB'
        : '${mb.toStringAsFixed(1)}MB';
  }
}
