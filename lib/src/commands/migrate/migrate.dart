import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:collection/collection.dart';
import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/deps/sync.dart';
import 'package:bolt/src/commands/migrate/old_config/old_config.dart' as old;
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/utils/file_extension.dart';

class MigrateCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  MigrateCommand() {
    argParser.addOption(
      'type',
      allowed: ['bolt', 'template', 'ai2'],
      help: 'Specifies the type of project to migrate.',
      valueHelp: 'bolt|template|ai2',
    );
  }

  @override
  String get description =>
      'Migrates extension projects built with Bolt v1 to Bolt v2';

  @override
  String get name => 'migrate';

  @override
  Future<int> run() async {
    _lgr.startTask('Initializing');

    // Check if project type is specified via argument
    final projectType = argResults?['type'] as String?;

    // If type is ai2, migrate directly to ai2 template
    if (projectType == 'ai2') {
      return await _migrateAi2Template();
    }

    final oldConfig = await old.OldConfig.load(_fs.configFile, _lgr);
    if (oldConfig == null) {
      // If no config found and no explicit type given, check for ai2/template indicators
      if (projectType == null &&
          await _fs.cwd.asDir().list().any((el) =>
              p.basename(el.path) == 'build.xml' ||
              p.basename(el.path) == 'src')) {
        return await _migrateAi2Template();
      }
      // If type is template, migrate as template
      if (projectType == 'template') {
        return await _migrateAi2Template();
      }
      _lgr
        ..err('Failed to load old config')
        ..stopTask(false);
      return 1;
    }

    // Convert fast.yml to bolt.yml if needed
    final configFile = _fs.configFile;
    if (p.basename(configFile.path) == 'fast.yml') {
      _lgr.info('Converting fast.yml to bolt.yml...');
      configFile.renameSync(p.join(_fs.cwd, 'bolt.yml'));
    }

    // TODO
    // final comptimeDeps = _fs.localDepsDir
    //     .listSync()
    //     .map((el) => p.basename(el.path))
    //     .where((el) => !(oldConfig.deps?.contains(el) ?? true));

    final newConfig = Config(
      version: oldConfig.version.name.toString(),
      minSdk: oldConfig.minSdk ?? 7,
      assets: oldConfig.assets.other ?? [],
      desugar: oldConfig.build?.desugar?.enable ?? false,
      dependencies: oldConfig.deps ?? [],
      // TODO
      // comptimeDeps: comptimeDeps.toList(),
      license: oldConfig.license ?? '',
      homepage: oldConfig.homepage ?? '',
      kotlin: Kotlin(
        compilerVersion: defaultKtVersion,
      ),
    );
    _lgr.stopTask();

    _lgr.startTask('Parsing old source files');
    final srcFile = _fs.srcDir
        .listSync(recursive: true)
        .whereType<File>()
        .firstWhereOrNull(
            (el) => p.basenameWithoutExtension(el.path) == oldConfig.name);
    if (srcFile == null) {
      _lgr
        ..err('Unable to find the main extension source file.')
        ..log(
            '${'help  '.green()} Make sure that the name of the main source file matches with `name` field in bolt.yml: `${oldConfig.name}`');
      return 1;
    }

    _lgr.info('Main extension source file found: ${srcFile.path}');
    _editSourceFile(srcFile, oldConfig);
    _lgr.stopTask();

    _lgr.startTask('Updating config file (bolt.yml)');
    _updateConfig(newConfig, oldConfig.build?.kotlin?.enable ?? false);
    _deleteOldHiveBoxes();
    _lgr.stopTask();

    // No need to start a task here since the sync command does that on its own.
    await SyncCommand().run(title: 'Syncing dependencies');
    return 0;
  }

  void _editSourceFile(File srcFile, old.OldConfig oldConfig) {
    final RegExp regex;
    if (p.extension(srcFile.path) == '.java') {
      regex = RegExp(r'.*class.+\s+extends\s+AndroidNonvisibleComponent.*');
    } else {
      regex = RegExp(
          r'.*class\s+.+\s*\((.|\n)*\)\s+:\s+AndroidNonvisibleComponent.*');
    }

    final fileContent = srcFile.readAsStringSync();
    final match = regex.firstMatch(fileContent);
    final matchedStr = match?.group(0);

    if (match == null || matchedStr == null) {
      _lgr
        ..err('Unable to process src file: ${srcFile.path}')
        ..log('Are you sure that it is a valid extension source file?',
            'help  '.green());
      throw Exception();
    }

    final description = oldConfig.description.isNotEmpty
        ? oldConfig.description
        : 'Extension component for ${oldConfig.name}. Built with <3 and Bolt.';
    final annotation = '''
// FIXME: You might want to shorten this by importing `@Extension` annotation.
@com.google.appinventor.components.annotations.Extension(
    description = "$description",
    icon = "${oldConfig.assets.icon}"
)
''';

    final newContent =
        fileContent.replaceFirst(matchedStr, annotation + matchedStr);
    srcFile.writeAsStringSync(newContent);
  }

  void _deleteOldHiveBoxes() {
    _fs.dotBoltDir
        .listSync()
        .where((el) =>
            p.extension(el.path) == '.hive' || p.extension(el.path) == '.lock')
        .forEach((el) {
      el.deleteSync();
    });
  }

  void _updateConfig(Config config, bool enableKotlin) {
    var contents = '''
# This is the version name of your extension. You should update it everytime you
# publish a new version of your extension.
version: '${config.version}'

# The minimum Android SDK level your extension supports. Minimum SDK defined in
# AndroidManifest.xml is ignored, you should always define it here.
min_sdk: ${config.minSdk}

''';

    if (config.homepage.isNotEmpty) {
      contents += '''
# Homepage of your extension. This may be the announcement thread on community 
# forums or a link to your GitHub repository.
${config.homepage}\n\n''';
    }

    if (config.license.isNotEmpty) {
      contents += '''
# Path to the license file of your extension. This should be a path to a local file
# or link to something hosted online.
${config.license}\n\n''';
    }

    if (config.assets.isNotEmpty) {
      contents += '''
# Assets that your extension needs. Every asset file must be stored in the assets
# directory as well as declared here. Assets can be of any type.
assets:
${config.assets.map((el) => '- $el').join('\n')}

''';
    }

    if (config.desugar) {
      contents += '''
# Desuagring allows you to use Java 8 language features in your extension. You 
# also need to enable desugaring if any of your dependencies use Java 8 language
# features.
desugar: true\n\n''';
    }

    if (enableKotlin) {
      contents += '''
# Kotlin specific configuration.
kotlin:
  compiler_version: '${config.kotlin.compilerVersion}'

''';
    }

    if (config.dependencies.isNotEmpty || enableKotlin) {
      contents += '''
# External libraries your extension depends on. These can be local JARs / AARs
# stored in the "deps" directory or coordinates of remote Maven artifacts in
# <groupId>:<artifactId>:<version> format. 
dependencies:
${enableKotlin ? 'org.jetbrains.kotlin:kotlin-stdlib:${config.kotlin.compilerVersion}\n' : ''}${config.dependencies.map((el) {
        if (el != '.placeholder') return '- $el';
      }).join('\n')}

''';
    }

    // TODO
//     if (config.comptimeDeps.isNotEmpty) {
//       contents += '''
// # Similar to dependencies, except libraries defined as comptime (compile-time)
// # are only available during compilation and not included in the resulting AIX.
// comptime_dependencies:
// ${config.comptimeDeps.map((el) {
//   if (el != '.placeholder') return '- $el';
// }).join('\n')}
// ''';
//     }

    _fs.configFile.writeAsStringSync(contents);
  }

  Future<int> _migrateAi2Template() async {
    _lgr.info('Detected extension-template / AI2 source project. Migrating...');

    // Find java files
    final srcDirs = [
      _fs.srcDir,
      p.join(_fs.cwd, 'appinventor', 'components', 'src').asDir()
    ];
    final javaFiles = <File>[];
    for (final dir in srcDirs) {
      if (dir.existsSync()) {
        javaFiles.addAll(dir
            .listSync(recursive: true)
            .whereType<File>()
            .where((f) => f.path.endsWith('.java')));
      }
    }

    if (javaFiles.isEmpty) {
      _lgr.err('Could not find any Java source files to migrate.');
      return 1;
    }

    // Try to extract version and name
    for (final file in javaFiles) {
      final content = file.readAsStringSync();
      if (content.contains('@DesignerComponent')) {
        if (!content.contains('@Extension')) {
          final regex =
              RegExp(r'.*class.+\s+extends\s+AndroidNonvisibleComponent.*');
          final match = regex.firstMatch(content);
          if (match != null) {
            final annotation = '''
// Migrated by Bolt
@com.google.appinventor.components.annotations.Extension(
    description = "",
    icon = ""
)
''';
            file.writeAsStringSync(content.replaceFirst(
                match.group(0)!, annotation + match.group(0)!));
          }
        }
        break;
      }
    }

    final newConfig = Config(
      version: '1.0.0',
      assets: [],
      desugar: true,
      dependencies: [],
      kotlin: Kotlin(
        compilerVersion: defaultKtVersion,
      ),
    );

    _lgr.startTask('Updating config file (bolt.yml)');
    _updateConfig(newConfig, false);
    _lgr.stopTask();

    // Copy src files if they are in appinventor
    final ai2SrcDir =
        p.join(_fs.cwd, 'appinventor', 'components', 'src').asDir();
    if (ai2SrcDir.existsSync() && !_fs.srcDir.existsSync()) {
      _lgr.info('Restructuring source directories...');
      _fs.srcDir.createSync(recursive: true);
      for (final file in javaFiles) {
        final destPath = p.join(
            _fs.srcDir.path, p.relative(file.path, from: ai2SrcDir.path));
        File(destPath).parent.createSync(recursive: true);
        file.copySync(destPath);
      }
    }

    await SyncCommand().run(title: 'Syncing dependencies');
    return 0;
  }
}
