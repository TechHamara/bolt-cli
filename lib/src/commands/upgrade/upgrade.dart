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
      ..addFlag('yes',
          abbr: 'y',
          help: 'Automatically accept upgrade prompt.')
      ..addOption('mode',
          abbr: 'm',
          allowed: ['fresh', 'inplace'],
          help: 'Specify upgrade mode: fresh (full zip) or inplace (bin zip).')
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

    final latestVersion = release.tagName ?? 'v$packageVersion';
    final force = (argResults!['force'] as bool? ?? false);
    final autoYes = (argResults!['yes'] as bool? ?? false);
    final modeOpt = argResults!['mode'] as String?;

    final isLatest = _compareVersions('v$packageVersion', latestVersion) >= 0;

    print('${'Current version:'.cyan().bold()} ${'v$packageVersion'.yellow()}');

    if (isLatest && !force) {
      _lgr.info('Bolt is already on the latest version. 🚀'.green());
      return 0;
    }

    if (!isLatest || force) {
      print('${'Available version:'.cyan().bold()} ${latestVersion.green()}');
    }

    if (!isLatest) {
      _lgr.info('An update is available: ${latestVersion.green().bold()}');
    }

    // Prompt 1: Do you want to upgrade? [Yes/No] (Default: No)
    bool shouldUpgrade = autoYes || force;
    if (!shouldUpgrade) {
      stdout.write('${'Do you want to upgrade?'.cyan()} [${'Yes'.green()}/${'No'.red()}] (${'Default:'.magenta()} ${'No'.red()}) ');
      final input = stdin.readLineSync()?.trim().toLowerCase() ?? '';
      if (input == 'yes' || input == 'y' || input == '1') {
        shouldUpgrade = true;
      }
    }

    if (!shouldUpgrade) {
      _lgr.info('Upgrade cancelled.'.yellow());
      return 0;
    }

    // Prompt 2: Select update mode? [Fresh/InPlace] type 2 for InPlace, (Default: Fresh)
    bool isInPlace = false;
    if (modeOpt != null) {
      isInPlace = modeOpt.toLowerCase() == 'inplace';
    } else {
      stdout.write('${'Select update mode?'.cyan()} [${'Fresh'.green()}/${'InPlace'.yellow()}] type ${'2'.blue()} for InPlace, (${'Default:'.magenta()} ${'Fresh'.green()}) ');
      final modeInput = stdin.readLineSync()?.trim().toLowerCase() ?? '';
      if (modeInput == '2' || modeInput == 'inplace') {
        isInPlace = true;
      }
    }

    final assets = release.assets ?? [];
    ReleaseAsset? archive;

    if (isInPlace) {
      archive = assets.firstWhereOrNull((el) =>
          el.name == 'bin.zip' ||
          el.name == 'bolt-bin.zip' ||
          el.name == 'bin-win.zip');

      if (archive == null || archive.browserDownloadUrl == null) {
        _lgr.err("Could not find 'bin.zip' release asset for InPlace update in $latestVersion.");
        _lgr.info("Please run 'bolt upgrade' and select 'Fresh' mode (Option 1) to download full package.");
        return 1;
      }
    } else {
      archive = assets.firstWhereOrNull((el) => el.name == archiveName());

      if (archive == null || archive.browserDownloadUrl == null) {
        _lgr.err("Could not find release asset '${archiveName()}' at ${release.htmlUrl}");
        return 1;
      }
    }

    final totalBytes = archive.size ?? 0;
    final totalMBStr = (totalBytes / (1024 * 1024)).toStringAsFixed(2);
    _lgr.info('Download size: $totalMBStr MB');

    final archiveDist =
        p.join(_fs.boltHomeDir.path, 'temp', archive.name).asFile();
    await archiveDist.create(recursive: true);

    try {
      final client = Client();
      final req = Request('GET', Uri.parse(archive.browserDownloadUrl!));
      final res = await client.send(req);

      if (res.statusCode != 200) {
        _lgr
          ..err('Something went wrong...')
          ..log('GET status code: ${res.statusCode}');
        return 1;
      }

      final streamTotalBytes = res.contentLength ?? totalBytes;
      final fileSink = archiveDist.openWrite();
      int receivedBytes = 0;
      final stopwatch = Stopwatch()..start();
      int lastReportBytes = 0;
      int lastReportTime = stopwatch.elapsedMilliseconds;

      await for (final chunk in res.stream) {
        receivedBytes += chunk.length;
        fileSink.add(chunk);

        final now = stopwatch.elapsedMilliseconds;
        if (now - lastReportTime >= 200 || receivedBytes == streamTotalBytes) {
          final timeSec = (now - lastReportTime) / 1000.0;
          final bytesSince = receivedBytes - lastReportBytes;
          final speedMB = timeSec > 0 ? (bytesSince / timeSec) / (1024 * 1024) : 0.0;
          final pct = streamTotalBytes > 0 ? (receivedBytes / streamTotalBytes * 100) : 0.0;

          final pctValStr = pct.toStringAsFixed(2).padLeft(6);
          final rxMBValStr = (receivedBytes / (1024 * 1024)).toStringAsFixed(2);
          final sizeMBValStr = (streamTotalBytes / (1024 * 1024)).toStringAsFixed(2);
          final speedValStr = '${speedMB.toStringAsFixed(2)} MB/s';

          final header = 'Downloading:'.yellow().bold();
          final pctStr = '$pctValStr%'.yellow().bold();
          final sizeStr = '($rxMBValStr MB/$sizeMBValStr MB)'.cyan();
          final speedStr = '| $speedValStr |'.green().bold();

          stdout.write('\r$header $pctStr $sizeStr $speedStr');

          lastReportBytes = receivedBytes;
          lastReportTime = now;
        }
      }
      await fileSink.flush();
      await fileSink.close();
      stdout.writeln();
    } catch (e) {
      _lgr
        ..err('Something went wrong during download...')
        ..log(e.toString());
      return 1;
    }

    _lgr.info('Extracting ${p.basename(archiveDist.path)}...');

    final zipDecoder =
        ZipDecoder().decodeBytes(await archiveDist.readAsBytes());

    final currentExePath = Platform.resolvedExecutable;
    final currentExeDir = p.dirname(currentExePath);

    for (final file in zipDecoder.files) {
      if (file.isFile) {
        if (isInPlace) {
          if (file.name.endsWith('bolt.exe') || file.name == 'bolt') {
            final outputDist = p.join(currentExeDir, 'bolt.exe.new').asFile();
            await outputDist.create(recursive: true);
            await outputDist.writeAsBytes(file.content as List<int>);
          }
        } else {
          final String path;
          if (file.name.endsWith('bolt.exe') || file.name == 'bolt') {
            path = p.join(currentExeDir, 'bolt.exe.new');
          } else {
            path = p.join(_fs.boltHomeDir.path, file.name);
          }
          final outputDist = File(path);
          await outputDist.create(recursive: true);
          await outputDist.writeAsBytes(file.content as List<int>);
        }
      }
    }
    await archiveDist.delete(recursive: true);

    if (Platform.isWindows) {
      final newExe = p.join(currentExeDir, 'bolt.exe.new').asFile();
      final targetExe = File(currentExePath);
      if (await newExe.exists()) {
        final tempDir = p.join(_fs.boltHomeDir.path, 'temp').asDir(true);
        final oldBackup = p.join(tempDir.path, 'bolt.$packageVersion.exe').asFile();
        if (await oldBackup.exists()) {
          await oldBackup.delete();
        }
        if (await targetExe.exists()) {
          await targetExe.rename(oldBackup.path);
        }
        await newExe.rename(targetExe.path);
      }
    } else {
      final targetExe = File(currentExePath);
      await Process.run('chmod', ['+x', targetExe.path]);
    }

    print('''
${'Success'.green()}! Bolt $latestVersion has been installed in ${isInPlace ? 'InPlace (bin.zip)' : 'Fresh (bolt-win.zip)'} mode. 🎉

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

  int _compareVersions(String v1, String v2) {
    final p1 = v1.replaceAll(RegExp(r'^v'), '').split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final p2 = v2.replaceAll(RegExp(r'^v'), '').split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final len = p1.length > p2.length ? p1.length : p2.length;
    for (int i = 0; i < len; i++) {
      final part1 = i < p1.length ? p1[i] : 0;
      final part2 = i < p2.length ? p2[i] : 0;
      if (part1 > part2) return 1;
      if (part1 < part2) return -1;
    }
    return 0;
  }
}
