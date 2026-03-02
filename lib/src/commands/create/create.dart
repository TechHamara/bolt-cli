import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:interact/interact.dart';
import 'package:path/path.dart' as p;
import 'package:recase/recase.dart';
import 'package:bolt/src/commands/create/templates/eclipse_files.dart';
import 'package:tint/tint.dart';

import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/commands/create/templates/extension_source.dart';
import 'package:bolt/src/commands/create/templates/intellij_files.dart';
import 'package:bolt/src/commands/create/templates/other.dart';
import 'package:bolt/src/commands/create/templates/vscode_files.dart';
import 'package:bolt/src/commands/create/templates/github_actions.dart';

class CreateCommand extends Command<int> {
  final FileService _fs = GetIt.I<FileService>();

  CreateCommand() {
    argParser
      ..addOption('package',
          abbr: 'p',
          help:
              'The organization name in reverse domain name notation. This is used as extension\'s package name.')
      ..addOption('author',
          abbr: 'a',
          help: 'Author name (shown in generated extension documentation).')
      ..addOption('language',
          abbr: 'l',
          help:
              'The language in which the extension\'s starter template should be generated',
          allowed: ['Java', 'Kotlin']);
  }

  @override
  String get description =>
      'Scaffolds a new extension project in the current working directory.';

  @override
  String get name => 'create';

  /// Creates a new extension project in the current directory.
  @override
  Future<int> run() async {
    final String name;
    if (argResults!.rest.length == 1) {
      name = argResults!.rest.first;
    } else {
      printUsage();
      return 64; // Exit code 64 indicates usage error
    }

    final projectDir = p.join(_fs.cwd, name);

    final dir = projectDir.asDir();
    if (await dir.exists() && dir.listSync().isNotEmpty) {
      throw Exception(
          'Cannot create "$projectDir" because it already exists and is not empty.');
    }

    var orgName = (argResults!['package'] ??
        Input(
          prompt: 'Package name',
        ).interact()) as String;

    var author = argResults!['author'] as String?;
    if (author == null || author.isEmpty) {
      author = Input(
            prompt: 'Author name',
          ).interact() as String? ??
          '';
    }

    var lang = argResults!['language'] as String?;
    if (lang == null) {
      // ask user via simple text input so we can default to Java and allow
      // typing 'k' or 'K' for Kotlin.  blank ➜ Java.
      final input = Input(
        prompt: 'Language (Java/Kotlin) type k or K for Kotlin, Defaults to',
        defaultValue: 'Java',
      ).interact() as String?;
      if (input == null || input.trim().isEmpty) {
        lang = 'Java';
      } else if (input.trim().toLowerCase().startsWith('k')) {
        lang = 'Kotlin';
      } else {
        lang = 'Java';
      }
    }

    final camelCasedName = name.camelCase;
    final pascalCasedName = name.pascalCase;

    // If the last word after '.' in package name is not same as the extension
    // name, then append `.$extName` to orgName.
    final isOrgAndNameSame =
        orgName.split('.').last.toLowerCase() == camelCasedName.toLowerCase();
    if (!isOrgAndNameSame) {
      orgName = '${orgName.toLowerCase()}.${camelCasedName.toLowerCase()}';
    }

    final processing = Spinner(
        icon: '\n✓ '.green(),
        rightPrompt: (done) => !done
            ? 'Getting things ready...'
            : '''
${'Success!'.green()} Generated a new extension project in ${p.relative(projectDir).blue()}.
  Next up,
  - ${'cd'.yellow()} into ${p.relative(projectDir).blue()}, and
  - run ${'bolt build'.yellow()} to build your extension.
''').interact();

    final extPath = p.joinAll([projectDir, 'src', ...orgName.split('.')]);
    final ideaDir = p.join(projectDir, '.idea');

    await GetIt.I.isReady<LibService>();
    final libService = GetIt.I<LibService>();

    final artifacts = await libService.providedDependencies(null);
    final providedDepJars = artifacts.map((el) => el.classesJar).nonNulls;
    final providedDepSources = artifacts.map((el) => el.sourcesJar).nonNulls;

    final filesToCreate = <String, String>{
      if (['j', 'java'].contains(lang.toLowerCase()))
        p.join(extPath, '$pascalCasedName.java'): getExtensionTempJava(
          pascalCasedName,
          orgName,
        )
      else
        p.join(extPath, '$pascalCasedName.kt'): getExtensionTempKt(
          pascalCasedName,
          orgName,
        ),
      p.join(projectDir, 'src', 'AndroidManifest.xml'):
          androidManifestXml(orgName),
      p.join(projectDir, 'src', 'proguard-rules.pro'): pgRules(orgName),
      p.join(projectDir, 'bolt.yml'): config(lang == 'Kotlin', author),
      p.join(projectDir, 'README.md'): readmeMd(pascalCasedName),
      p.join(projectDir, '.gitignore'): dotGitignore,
      p.join(projectDir, 'deps', '.placeholder'):
          'This directory stores your extension\'s local dependencies.',
      p.join(projectDir, '.github', 'workflows', 'main.yml'):
          githubActionsYaml(pascalCasedName),

      // IntelliJ IDEA files
      ...{
        p.join(ideaDir, 'misc.xml'): ijMiscXml,
        p.join(ideaDir, 'libraries', 'local-deps.xml'): ijLocalDepsXml,
        p.join(ideaDir, 'libraries', 'provided-deps.xml'):
            ijProvidedDepsXml(providedDepJars, providedDepSources),
        p.join(ideaDir, '${name.paramCase}.iml'):
            ijImlXml(['provided-deps', 'local-deps']),
        p.join(ideaDir, 'modules.xml'): ijModulesXml(name.paramCase),
      },
      // Eclipse & VS Code files
      ...{
        p.join(projectDir, '.project'): dotProject(name.paramCase),
        p.join(projectDir, '.classpath'): dotClasspath(providedDepJars, []),
        p.join(projectDir, '.vscode', 'settings.json'): vscodeSettingsJson,
      },
    };

    // Creates the required files for the extension.
    try {
      // Create all files with proper async/await
      for (final entry in filesToCreate.entries) {
        await entry.key.asFile(true).writeAsString(entry.value);
      }

      // Ensure assets directory is created
      final assetsDir = p.join(projectDir, 'assets');
      assetsDir.asDir(true);

      // Copy icon if it exists, otherwise create a minimal placeholder PNG
      final iconDest = p.join(assetsDir, 'icon.png').asFile();
      final iconSource = p.join(_fs.boltHomeDir.path, 'icon.png').asFile();

      if (await iconSource.exists()) {
        await iconSource.copy(iconDest.path);
      } else {
        // Minimal valid 1x1 green PNG (89 bytes)
        await iconDest.writeAsBytes([
          0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
          0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
          0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
          0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, // 8-bit RGB
          0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, // IDAT chunk
          0x54, 0x08, 0xD7, 0x63, 0x60, 0x64, 0x60, 0x00, // compressed data
          0x00, 0x00, 0x04, 0x00, 0x01, 0x27, 0x34, 0x27,
          0x0A, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, // IEND chunk
          0x44, 0xAE, 0x42, 0x60, 0x82,
        ]);
      }
    } catch (e) {
      GetIt.I<Logger>().err(e.toString());
      rethrow;
    }

    processing.done();
    return 0;
  }
}
