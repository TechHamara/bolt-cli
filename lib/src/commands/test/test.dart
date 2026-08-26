import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/build/tools/compiler.dart';
import 'package:bolt/src/commands/deps/sync.dart';
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/utils/process_runner.dart';

class TestCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();
  late final LibService _libService;

  TestCommand() {
    argParser.addFlag(
      'sync',
      abbr: 'y',
      help: 'Forces a dependency sync before testing.',
    );
  }

  @override
  String get description => 'Runs unit tests for the extension.';

  @override
  String get name => 'test';

  final _stopwatch = Stopwatch();

  @override
  Future<int> run() async {
    _stopwatch.start();
    await GetIt.I.isReady<LibService>();
    _libService = GetIt.I<LibService>();

    final config = await Config.load(_fs.configFile, _lgr);
    if (config == null) {
      _lgr.err('Not a Bolt project.');
      return 1;
    }

    final testDir = p.join(_fs.cwd, 'test').asDir();
    if (!testDir.existsSync()) {
      _lgr.warn('No `test` directory found. Scaffolding `test/java/ExampleTest.java` for you.');
      final sampleTestFile = p.join(testDir.path, 'java', 'ExampleTest.java').asFile();
      sampleTestFile.createSync(recursive: true);
      sampleTestFile.writeAsStringSync('''
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleTest {

    @Test
    void addition() {
        assertEquals(2, 1 + 1);
    }
}
''');
    }

    final forceSync = (argResults?['sync'] ?? false) as bool;
    if (forceSync) {
      final syncCmd = SyncCommand();
      final res = await syncCmd.run(title: 'Syncing test dependencies');
      if (res != 0) return res;
    }

    _lgr.startTask('Compiling sources and tests');

    final timestampBox = await Hive.openLazyBox<DateTime>(timestampBoxName);
    
    // Resolve all dependencies including test ones
    final allDeps = await _libService.extensionDependencies(
      config,
      includeAi2ProvidedDeps: true,
      includeProjectProvidedDeps: true,
      includeLocal: true,
      includeTestDeps: true,
    );

    final classpathJars = allDeps.map((e) => e.artifactFile).toSet();
    final junitJar = await _libService.junitConsoleJar();
    classpathJars.add(junitJar);
    
    try {
      // 1. Compile main sources (to build/classes)
      final mainJavaFiles = _fs.srcDir
          .listSync(recursive: true)
          .where((el) => el is File && p.extension(el.path) == '.java')
          .map((el) => el.path)
          .toSet();

      if (mainJavaFiles.isNotEmpty) {
        await Compiler.compileJavaFiles(
          classpathJars,
          config.java8,
          timestampBox,
          config,
          false,
          javaFiles: mainJavaFiles,
        );
      }

      // 2. Compile test sources (to build/test-classes)
      final testClassesDir = p.join(_fs.buildDir.path, 'test-classes').asDir(true);
      final testJavaFiles = testDir
          .listSync(recursive: true)
          .where((el) => el is File && p.extension(el.path) == '.java')
          .map((el) => el.path)
          .toSet();

      if (testJavaFiles.isNotEmpty) {
        final cp = <String>[
          ...classpathJars,
          _fs.buildClassesDir.path, // main classes
        ].join(BuildUtils.cpSeparator);

        final args = [
          if (config.java8) ...['-source', '8', '-target', '8'],
          ...['-d', testClassesDir.path],
          ...['-cp', cp],
          ...testJavaFiles,
        ];

        final processRunner = ProcessRunner();
        await processRunner.runExecutable(
          BuildUtils.javaExe(true),
          args,
          logToConsole: false,
        );
      }
      _lgr.stopTask();
      _lgr.startTask('Running tests');

      // 3. Run JUnit Console Launcher
      final cp = <String>[
        ...classpathJars,
        _fs.buildClassesDir.path,
        testClassesDir.path,
      ].join(BuildUtils.cpSeparator);

      final args = [
        '-jar',
        junitJar,
        '--class-path',
        cp,
        '--scan-classpath',
      ];

      final processRunner = ProcessRunner();
      await processRunner.runExecutable(
        BuildUtils.javaExe(false),
        args,
        logToConsole: true, // We want to see JUnit output directly!
      );

      _lgr.stopTask();
      _lgr.log('\n> ${'TEST SUCCESSFUL'.green()}');
    } catch (e) {
      _lgr.stopTask(false);
      _lgr.err('Tests failed or compilation error occurred.\n$e');
      return 1;
    }

    return 0;
  }
}
