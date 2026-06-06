import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:github/github.dart';
import 'package:collection/collection.dart';
import 'package:http/http.dart';
import 'package:path/path.dart' as p;

import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/version.dart';
import 'package:tint/tint.dart';

class UpgradeCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  UpgradeCommand() {
    argParser
      ..addFlag('force',
          abbr: 'f',
          help: 'Upgrades Bolt even if you\'re using the latest version.')
      ..addOption('access-token',
          abbr: 't',
          help: 'Your GitHub access token. Normally, you don\'t need this.');
  }

  @override
  String get description => 'Upgrades Bolt to the latest available version.';

  @override
  String get name => 'upgrade';

  @override
  Future<int> run() async {
    _lgr.info('Checking for new version...');

    final gh = GitHub(
        auth: Authentication.withToken(argResults!['access-token'] as String?));
    final release = await gh.repositories
        .getLatestRelease(RepositorySlug.full('TechHamara/bolt-cli'));

    final latestVersion = release.tagName;
    final force = (argResults!['force'] as bool);

    if (latestVersion == 'v$packageVersion') {
      if (!force) {
        _lgr.info(
            'You\'re already on the latest version of Bolt. Use `--force` to upgrade anyway.');
        return 0;
      }
    } else {
      _lgr.info('A newer version is available: $latestVersion');
    }

    final archive =
        release.assets?.firstWhereOrNull((el) => el.name == archiveName());
    if (archive == null || archive.browserDownloadUrl == null) {
      _lgr
        ..err(
            'Could not find release asset ${archiveName()} at ${release.htmlUrl}')
        ..log('This is not supposed to happen. Please report this issue.');
      return 1;
    }

    _lgr.info('Downloading ${archiveName()}...');
    final archiveDist =
        p.join(_fs.boltHomeDir.path, 'temp', archive.name).asFile();
    try {
      final response = await get(Uri.parse(archive.browserDownloadUrl!));
      if (response.statusCode != 200) {
        _lgr
          ..err('Something went wrong...')
          ..log('GET status code: ${response.statusCode}')
          ..log('GET body:\n${response.body}');
        return 1;
      }

      await archiveDist.create(recursive: true);
      await archiveDist.writeAsBytes(response.bodyBytes);
    } catch (e) {
      _lgr
        ..err('Something went wrong...')
        ..log(e.toString());
      return 1;
    }

    // TODO: We should delete the old files.

    _lgr.info('Extracting ${p.basename(archiveDist.path)}...');

    final zipDecoder =
        ZipDecoder().decodeBytes(await archiveDist.readAsBytes());
    for (final file in zipDecoder.files) {
      if (file.isFile) {
        final String path;
        if (file.name.endsWith('bolt.exe')) {
          path = p.join(_fs.boltHomeDir.path, '${file.name}.new');
        } else {
          path = p.join(_fs.boltHomeDir.path, file.name);
        }

        final outputDist = File(path);
        await outputDist.create(recursive: true);
        await outputDist.writeAsBytes(file.content as List<int>);
      }
    }
    await archiveDist.delete(recursive: true);

    final exePath = Platform.resolvedExecutable;

    // On Windows, we can't replace the executable while it's running. So, we
    // move it to `$BOLT_HOME/temp/bolt.{version}.exe` and then rename the new
    // exe, which would have been downloaded in the bin directory with name `bolt.exe.new`,
    // to the old name.
    if (Platform.isWindows) {
      final newExe = p.join(p.dirname(exePath), 'bolt.exe.new').asFile();
      if (await newExe.exists()) {
        final tempDir = p.join(_fs.boltHomeDir.path, 'temp').asDir(true);
        await exePath
            .asFile()
            .rename(p.join(tempDir.path, 'bolt.$packageVersion.exe'));
        await newExe.rename(exePath);
      }
    } else {
      await Process.run('chmod', ['+x', Platform.resolvedExecutable]);
    }

    // ignore: avoid_print
    print('''
${'Success'.green()}! Bolt $latestVersion has been installed. 🎉

Now, run ${'`bolt deps sync --dev-deps`'.blue()} to re-sync updated dev-dependencies.

Check out the changelog for this release at: ${release.htmlUrl}
''');

    return 0;
  }

  String archiveName() {
    if (Platform.isWindows) {
      return 'bolt-win.zip';
    }

    if (Platform.isLinux) {
      return 'bolt-linux.zip';
    }

    if (Platform.isMacOS) {
      return 'bolt-mac.zip';
    }

    throw UnsupportedError('Unsupported platform');
  }
}
