import 'dart:io';

import 'package:archive/archive_io.dart';
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
      allowed: ['rush', 'fast', 'template', 'ai2'],
      help: 'Specifies the type of project to migrate.',
      valueHelp: 'rush|fast|template|ai2',
    );
  }

  @override
  String get description =>
      'Migrates extension projects built with Rush v1 to Rush v2';

  @override
  String get name => 'migrate';

  @override
  Future<int> run() async {
    final stopwatch = Stopwatch()..start();
    await _backupProject();
    _lgr.startTask('Initializing');

    // Check if project type is specified via option or positional argument
    var projectType = argResults?['type'] as String?;
    if (projectType == null &&
        argResults != null &&
        argResults!.rest.isNotEmpty) {
      final possibleType = argResults!.rest.first;
      if (['rush', 'fast', 'template', 'ai2'].contains(possibleType)) {
        projectType = possibleType;
      }
    }

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

    // Convert fast.yml or rush.yml to bolt.yml if needed
    final configFile = _fs.configFile;
    if (p.basename(configFile.path) == 'fast.yml' ||
        p.basename(configFile.path) == 'rush.yml') {
      configFile.renameSync(p.join(_fs.cwd, 'bolt.yml'));
    }

    // TODO
    // final comptimeDeps = _fs.localDepsDir
    //     .listSync()
    //     .map((el) => p.basename(el.path))
    //     .where((el) => !(oldConfig.deps?.contains(el) ?? true));

    final resolvedName = oldConfig.name ?? p.basename(_fs.cwd);
    final resolvedVersion = oldConfig.version?.name?.toString() ?? '1.0.0';
    final resolvedAssets = oldConfig.assets?.other ?? [];
    final resolvedAuthor = oldConfig.author ??
        (oldConfig.authors?.isNotEmpty == true ? oldConfig.authors!.first : '');

    final newConfig = Config(
      version: resolvedVersion,
      minSdk: oldConfig.minSdk ?? 7,
      assets: resolvedAssets,
      desugar: oldConfig.build?.desugar?.enable ?? false,
      dependencies: oldConfig.deps ?? [],
      license: oldConfig.license ?? '',
      homepage: oldConfig.homepage ?? '',
      author: resolvedAuthor,
      authors: oldConfig.authors ?? [],
      kotlin: Kotlin(
        compilerVersion: defaultKtVersion,
      ),
      r8: true,
      desugarDex: true,
      proguard: false,
      deannonate: true,
      autoVersion: true,
    );
    _lgr.stopTask();

    final candidateNames = <String>{resolvedName};

    // Heuristic 1: Strip non-alphanumeric and compare (e.g. 'pop-menu' -> 'popmenu')
    final cleanResolvedName =
        resolvedName.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '').toLowerCase();

    // Heuristic 2: Convert resolvedName to UpperCamelCase (e.g. 'pop-menu' -> 'PopMenu')
    final upperCamelResolvedName = resolvedName
        .split(RegExp(r'[-_]'))
        .map((word) =>
            word.isNotEmpty ? word[0].toUpperCase() + word.substring(1) : '')
        .join('');
    if (upperCamelResolvedName.isNotEmpty) {
      candidateNames.add(upperCamelResolvedName);
    }

    // Heuristic 3: Check .fast folder and scan subfolders to find package name/main class
    final fastDir = Directory(p.join(_fs.cwd, '.fast'));
    if (fastDir.existsSync()) {
      try {
        final entities = fastDir.listSync(recursive: true);
        for (final entity in entities) {
          if (entity is Directory) {
            final name = p.basename(entity.path);
            if (name.isNotEmpty) {
              candidateNames.add(name);
              final uCamel = name[0].toUpperCase() + name.substring(1);
              candidateNames.add(uCamel);
            }
          }
        }
      } catch (_) {}
    }

    // Heuristic 4: Check AndroidManifest.xml in src folder (or subfolders) for package name
    final manifestFile = File(p.join(_fs.srcDir.path, 'AndroidManifest.xml'));
    if (manifestFile.existsSync()) {
      try {
        final content = manifestFile.readAsStringSync();
        final match = RegExp(r'package="([^"]+)"').firstMatch(content);
        if (match != null) {
          final pkg = match.group(1)!;
          final lastPart = pkg.split('.').last;
          candidateNames.add(lastPart);
          final uCamel = lastPart[0].toUpperCase() + lastPart.substring(1);
          candidateNames.add(uCamel);
        }
      } catch (_) {}
    }

    // Heuristic 5: Check proguard-rules.pro in src folder for -repackageclasses
    final proguardFile = File(p.join(_fs.srcDir.path, 'proguard-rules.pro'));
    if (proguardFile.existsSync()) {
      try {
        final content = proguardFile.readAsStringSync();
        final match = RegExp(r'-repackageclasses\s+([a-zA-Z0-9.]+)\.repacked')
            .firstMatch(content);
        if (match != null) {
          final pkg = match.group(1)!;
          final lastPart = pkg.split('.').last;
          candidateNames.add(lastPart);
          final uCamel = lastPart[0].toUpperCase() + lastPart.substring(1);
          candidateNames.add(uCamel);
        }
      } catch (_) {}
    }

    final lowerCandidates = candidateNames.map((e) => e.toLowerCase()).toSet();

    final srcFile = _fs.srcDir
        .listSync(recursive: true)
        .whereType<File>()
        .firstWhereOrNull((el) {
      final filename = p.basenameWithoutExtension(el.path);
      final cleanFilename =
          filename.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '').toLowerCase();

      return lowerCandidates.contains(filename.toLowerCase()) ||
          cleanFilename == cleanResolvedName ||
          lowerCandidates.contains(cleanFilename);
    });

    if (srcFile == null) {
      _lgr
        ..err('Unable to find the main extension source file.')
        ..log(
            '${'help  '.green()} Make sure that the name of the main source file matches with `name` field in bolt.yml: `${resolvedName}`');
      return 1;
    }

    _editSourceFile(srcFile, oldConfig);

    _lgr.startTask('Updating config file (bolt.yml)');
    _updateConfig(newConfig, oldConfig.build?.kotlin?.enable ?? false);
    _deleteOldHiveBoxes();
    _lgr.stopTask();

    // No need to start a task here since the sync command does that on its own.
    await SyncCommand().run(
      title: 'Syncing dependencies',
      showSummary: false,
    );

    final timeMs = stopwatch.elapsedMilliseconds;
    var line = '\n';
    line += '> ';
    line += 'MIGRATION SUCCESSFUL '.green();
    line += 'in ${timeMs}ms'.grey();
    _lgr.log(line);

    return 0;
  }

  void _editSourceFile(File srcFile, old.OldConfig oldConfig) {
    var fileContent = srcFile.readAsStringSync();

    String? extractedDescription;
    String? extractedIcon;

    // Strip @DesignerComponent
    final hasDesignerComponent = fileContent.contains('@DesignerComponent');
    if (hasDesignerComponent) {
      int index = 0;
      while (true) {
        final startIdx = fileContent.indexOf('@DesignerComponent', index);
        if (startIdx == -1) break;

        final endIdx = _findAnnotationEnd(fileContent, startIdx);
        if (endIdx == null) {
          index = startIdx + '@DesignerComponent'.length;
          continue;
        }

        final annotationStr = fileContent.substring(startIdx, endIdx);

        if (extractedDescription == null) {
          extractedDescription =
              _extractAnnotationStringParam(annotationStr, 'description');
        }
        if (extractedIcon == null) {
          extractedIcon =
              _extractAnnotationStringParam(annotationStr, 'iconName');
        }

        fileContent = fileContent.replaceRange(startIdx, endIdx, '');
        index = startIdx;
      }
    }

    final resolvedName = oldConfig.name ?? p.basename(srcFile.parent.path);
    final resolvedAuthor = oldConfig.author ??
        (oldConfig.authors?.isNotEmpty == true ? oldConfig.authors!.first : '');
    final description = extractedDescription ??
        oldConfig.description ??
        (resolvedAuthor.isNotEmpty
            ? 'Developed by $resolvedAuthor using Bolt.'
            : 'Extension component for $resolvedName. Built with <3 and Bolt.');

    final resolvedVersion = oldConfig.version?.number?.toString() ?? '1';
    final resolvedVersionName = oldConfig.version?.name?.toString() ?? '1.0';
    final icon = extractedIcon ?? oldConfig.assets?.icon ?? 'icon.png';

    final annotation = '''
@com.google.appinventor.components.annotations.Extension(
        description = "$description",
        version = "$resolvedVersion",
        versionName = "$resolvedVersionName",
        icon = "$icon"
)
''';

    // Check if file already contains `@Extension`
    int extStart = fileContent.indexOf('@Extension');
    if (extStart == -1) {
      extStart = fileContent
          .indexOf('@com.google.appinventor.components.annotations.Extension');
    }
    if (extStart != -1) {
      _lgr.info(
          'Source file already contains @Extension annotation. Updating description and properties.');
      final extEnd = _findAnnotationEnd(fileContent, extStart);
      if (extEnd != null) {
        final newContent =
            fileContent.replaceRange(extStart, extEnd, annotation);
        srcFile.writeAsStringSync(newContent);
        return;
      }
    }

    final RegExp regex;
    if (p.extension(srcFile.path) == '.java') {
      regex = RegExp(r'.*class.+\s+extends\s+AndroidNonvisibleComponent.*');
    } else {
      regex = RegExp(
          r'.*class\s+.+\s*\((.|\n)*\)\s+:\s+AndroidNonvisibleComponent.*');
    }

    final match = regex.firstMatch(fileContent);
    final matchedStr = match?.group(0);

    if (match == null || matchedStr == null) {
      _lgr
        ..err('Unable to process src file: ${srcFile.path}')
        ..log('Are you sure that it is a valid extension source file?',
            'help  '.green());
      if (hasDesignerComponent) {
        srcFile.writeAsStringSync(fileContent);
      }
      throw Exception();
    }

    final newContent =
        fileContent.replaceFirst(matchedStr, annotation + matchedStr);
    srcFile.writeAsStringSync(newContent);
  }

  void _deleteOldHiveBoxes() {
    if (_fs.dotBoltDir.existsSync()) {
      _fs.dotBoltDir
          .listSync()
          .where((el) =>
              p.extension(el.path) == '.hive' ||
              p.extension(el.path) == '.lock')
          .forEach((el) {
        el.deleteSync();
      });
    }
  }

  void _updateConfig(Config config, bool enableKotlin) {
    final authorLine =
        config.author.isNotEmpty ? "\nauthor: '${config.author}'\n" : '';

    var contents = '''
# Author name.
$authorLine
# This is the version name of your extension. You should update it everytime you
# publish a new version of your extension.
version: '${config.version}'

# The minimum Android SDK level your extension supports. Minimum SDK defined in
# AndroidManifest.xml is ignored, you should always define it here.
min_sdk: ${config.minSdk}

# Define the compile Android SDK API level.
# compile_sdk: 35
# If enabled, the D8 tool will generate desugared jar (classes.dex)
desugar_dex: ${config.desugarDex}
# If enabled, extension will be optimized using R8.
R8: ${config.r8}
# If enabled, extension will be optimized using ProGuard.
proguard: ${config.proguard}
# If enabled, extension annotations will be stripped for smaller size.
deannonate: ${config.deannonate}
# Define specific ProGuard version.
proguard_version: '${config.proguardVersion}'
# Kotlin Compiler version.
kotlin_version: '${config.kotlin.compilerVersion}'

# Desuagring allows you to use Java 8 language features in your extension. You 
# also need to enable desugaring if any of your dependencies use Java 8 language
# features.
desugar: ${config.desugar}
''';

    if (enableKotlin) {
      contents += '''

# Kotlin specific configuration.
kotlin:
  compiler_version: '${config.kotlin.compilerVersion}'
''';
    }

    contents += '\n';

    final resolvedDependencies =
        config.dependencies.where((el) => el != '.placeholder').toList();
    if (resolvedDependencies.isNotEmpty) {
      contents += '''
# External libraries your extension depends on. These can be local JARs / AARs
# stored in the "deps" directory or coordinates of remote Maven artifacts in
# <groupId>:<artifactId>:<version> format. 
dependencies:
${resolvedDependencies.map((el) => '- $el').join('\n')}

''';
    } else {
      contents += '''
# External libraries your extension depends on. These can be local JARs / AARs
# stored in the "deps" directory or coordinates of remote Maven artifacts in
# <groupId>:<artifactId>:<version> format. 
#dependencies:
${enableKotlin ? '#- org.jetbrains.kotlin:kotlin-stdlib:${config.kotlin.compilerVersion}\n' : ''}#- example.jar                 # Local JAR or AAR file stored in "deps" directory
#- com.example:foo-bar:1.2.3   # Coordinate of some remote Maven artifact

''';
    }

    contents += '''
# Runtime dependencies resolving for  [Local Only]
# dependencies:
# - mylibrary.jar

# Compile-time dependencies resolving for GradleResolver/MavenResolver [Local Only]
# compile_time:
# - mylibrary.jar

# Default Maven repositories includes Maven Central, Google Maven, JitPack and
# JCenter. Bolt will automatically add these to the resolver, so you rarely
# need to mention them here. If the library you want to use is not available in
# these repositories, you can add additional ones by specifying their URLs here.
#repositories:
#- https://jitpack.io

''';

    if (config.assets.isNotEmpty) {
      contents += '''
# Assets that your extension needs. Every asset file must be stored in the assets
# directory as well as declared here. Assets can be of any type.
assets:
${config.assets.map((el) => '- $el').join('\n')}

''';
    } else {
      contents += '''
# Assets that your extension needs. Every asset file must be stored in the assets
# directory as well as declared here. Assets can be of any type.
#assets:
#- data.json

''';
    }

    if (config.homepage.isNotEmpty) {
      contents += '''
# Homepage of your extension. This may be the announcement thread on community 
# forums or a link to your GitHub repository.
homepage: ${config.homepage}

''';
    } else {
      contents += '''
# Homepage of your extension. This may be the announcement thread on community 
# forums or a link to your GitHub repository.
#homepage: https://github.com/TechHamara/bolt-cli

''';
    }

    if (config.license.isNotEmpty) {
      contents += '''
# Path to the license file of your extension. This should be a path to a local
# file or link to something hosted online.
license: ${config.license}

''';
    } else {
      contents += '''
# Path to the license file of your extension. This should be a path to a local
# file or link to something hosted online.
#license: LICENSE.txt

''';
    }

    contents += '''
# Similar to dependencies, except libraries defined as provided are not included
# in the final AIX. This is useful when you want to use a library in your
# extension but don't want to include it in the final AIX because it's already
# included in the App Inventor.
#provided_dependencies:
#- com.example:foo-bar:1.2.3

# Enable to increment the version number of each component during build.
auto_version: ${config.autoVersion}
''';

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

    await SyncCommand().run(
      title: 'Syncing dependencies',
      showSummary: false,
    );
    return 0;
  }

  Future<void> _backupProject() async {
    _lgr.startTask('Creating backup archive');
    try {
      final projectName = p.basename(_fs.cwd);
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final backupFileName = '${projectName}_backup_$timestamp.zip';
      final backupPath = p.join(_fs.cwd, backupFileName);

      final zipEncoder = ZipFileEncoder()..create(backupPath);

      final dir = Directory(_fs.cwd);
      if (dir.existsSync()) {
        final entities = dir.listSync(recursive: true);

        for (final entity in entities) {
          if (entity is File) {
            final relativePath = p.relative(entity.path, from: _fs.cwd);

            // Exclude the backup file itself and common hidden cache/git directories
            if (relativePath == backupFileName) {
              continue;
            }
            final parts = p.split(relativePath);
            if (parts.any((part) =>
                part == '.git' ||
                part == '.bolt' ||
                part == '.dart_tool' ||
                part == '.rush')) {
              continue;
            }

            await zipEncoder.addFile(entity, relativePath);
          }
        }
      }
      await zipEncoder.close();
      _lgr.stopTask(true);
      _lgr.info(
          'Current project successfully backed up to ${backupFileName.green()}');
    } catch (e) {
      _lgr.stopTask(false);
      _lgr.err('Failed to create project backup zip: $e');
      rethrow;
    }
  }

  int? _findAnnotationEnd(String content, int startIndex) {
    int openParen = content.indexOf('(', startIndex);
    if (openParen == -1) return null;

    int parenDepth = 0;
    bool inString = false;
    bool inChar = false;

    for (int i = openParen; i < content.length; i++) {
      int char = content.codeUnitAt(i);

      if (inString) {
        if (char == 92 /* \ */) {
          // Skip next character (escaped character)
          i++;
        } else if (char == 34 /* " */) {
          inString = false;
        }
      } else if (inChar) {
        if (char == 92 /* \ */) {
          i++;
        } else if (char == 39 /* ' */) {
          inChar = false;
        }
      } else {
        if (char == 34 /* " */) {
          inString = true;
        } else if (char == 39 /* ' */) {
          inChar = true;
        } else if (char == 40 /* ( */) {
          parenDepth++;
        } else if (char == 41 /* ) */) {
          parenDepth--;
          if (parenDepth == 0) {
            // Check if there is trailing whitespace/newlines to consume
            int nextIdx = i + 1;
            while (nextIdx < content.length) {
              final c = content[nextIdx];
              if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                nextIdx++;
              } else {
                break;
              }
            }
            return nextIdx;
          }
        }
      }
    }
    return null;
  }

  String? _extractAnnotationStringParam(
      String annotationStr, String paramName) {
    final regExp = RegExp(r'\b' + paramName + r'\s*=\s*"((?:[^"\\]|\\.)*)"');
    final match = regExp.firstMatch(annotationStr);
    if (match != null) {
      return match.group(1);
    }
    return null;
  }
}
