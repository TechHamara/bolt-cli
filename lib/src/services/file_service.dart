import 'dart:io';

import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';

class FileService {
  final String cwd;
  late final Directory boltHomeDir;

  final _lgr = GetIt.I.get<Logger>();

  FileService(this.cwd) {
    final Directory homeDir;
    final env = Platform.environment;

    if (env.containsKey('BOLT_HOME')) {
      homeDir = env['BOLT_HOME']!.asDir();
    } else if (env.containsKey('BOLT_DATA_DIR')) {
      _lgr.warn('BOLT_DATA_DIR env var is deprecated. Use BOLT_HOME instead.');
      homeDir = env['BOLT_DATA_DIR']!.asDir();
    } else {
      if (Platform.operatingSystem == 'windows') {
        homeDir = p.join(env['UserProfile']!, '.bolt').asDir();
      } else {
        homeDir = p.join(env['HOME']!, '.bolt').asDir();
      }
    }

    if (!homeDir.existsSync() || homeDir.listSync().isEmpty) {
      _lgr.err('Could not find Bolt data directory at $homeDir.');
      exit(1);
    }

    boltHomeDir = homeDir;
  }

  Directory get srcDir => p.join(cwd, 'src').asDir();
  Directory get localDepsDir => p.join(cwd, 'deps').asDir();
  Directory get dotBoltDir => p.join(cwd, '.bolt').asDir();

  Directory get buildDir => p.join(dotBoltDir.path, 'build').asDir(true);
  Directory get buildClassesDir => p.join(buildDir.path, 'classes').asDir(true);
  Directory get buildRawDir => p.join(buildDir.path, 'raw').asDir(true);
  Directory get buildFilesDir => p.join(buildDir.path, 'files').asDir(true);
  Directory get buildKaptDir => p.join(buildDir.path, 'kapt').asDir(true);
  Directory get buildAarsDir =>
      p.join(buildDir.path, 'extracted-aars').asDir(true);

  Directory get libsDir => p.join(boltHomeDir.path, 'libs').asDir();

  File get configFile {
    if (p.join(cwd, 'bolt.yml').asFile().existsSync()) {
      return p.join(cwd, 'bolt.yml').asFile();
    } else if (p.join(cwd, 'bolt.yaml').asFile().existsSync()) {
      return p.join(cwd, 'bolt.yaml').asFile();
    } else if (p.join(cwd, 'fast.yml').asFile().existsSync()) {
      return p.join(cwd, 'fast.yml').asFile();
    } else {
      return p.join(cwd, 'bolt.yaml').asFile();
    }
  }

  File get javacArgsFile =>
      p.join(buildFilesDir.path, 'javac.args').asFile(true);
  File get kotlincArgsFile =>
      p.join(buildFilesDir.path, 'kotlinc.args').asFile(true);
}
