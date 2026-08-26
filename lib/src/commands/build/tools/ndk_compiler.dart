import 'dart:io';

import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;

import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/process_runner.dart';
import 'package:bolt/src/utils/file_extension.dart';

class NdkCompiler {
  static final _fs = GetIt.I<FileService>();
  static final _lgr = GetIt.I<Logger>();
  static final _processRunner = ProcessRunner();

  static Future<void> compileNativeCode(Config config) async {
    // 1. Check if NDK compilation is enabled in config
    if (!(config.ndk?.enabled ?? false)) {
      return;
    }

    final cppDir = _fs.cppDir;
    if (!cppDir.existsSync()) {
      _lgr.info('NDK is enabled but src/cpp directory was not found. Skipping native build.');
      return;
    }

    // Try to find NDK
    final env = Platform.environment;
    String? ndkHome = env['ANDROID_NDK_HOME'];
    if (ndkHome == null) {
      // 1. Fallback to custom Bolt Mini NDK inside .bolt/libs/ndk
      final boltNdkDir = p.join(_fs.libsDir.path, 'ndk').asDir();
      if (boltNdkDir.existsSync()) {
        ndkHome = boltNdkDir.path;
      } else {
        // 2. Fallback to Android Studio SDK standard NDK installation
        final androidHome = env['ANDROID_HOME'] ?? env['ANDROID_SDK_ROOT'];
        if (androidHome != null) {
          final ndkDir = p.join(androidHome, 'ndk').asDir();
          if (ndkDir.existsSync()) {
            final versions = ndkDir.listSync().whereType<Directory>().toList();
            if (versions.isNotEmpty) {
              // Pick the latest version by default if config.ndk?.version is not specified
              versions.sort((a, b) => b.path.compareTo(a.path));
              if (config.ndk?.version != null) {
                final specifiedDir = versions.firstWhere(
                    (d) => p.basename(d.path) == config.ndk?.version,
                    orElse: () => throw Exception('Specified NDK version ${config.ndk?.version} not found in ${ndkDir.path}'));
                ndkHome = specifiedDir.path;
              } else {
                ndkHome = versions.first.path;
              }
            }
          }
        }
      }
    }

    if (ndkHome == null || !Directory(ndkHome).existsSync()) {
      _lgr.warn('ANDROID_NDK_HOME is not set or NDK is not installed in ANDROID_HOME/ndk. Skipping native build.');
      return;
    }

    _lgr.info('Using Android NDK from $ndkHome', console: false);
    final ndkBuildExe = p.join(ndkHome, Platform.isWindows ? 'ndk-build.cmd' : 'ndk-build');

    // Create a temporary Application.mk and Android.mk if they don't exist
    final jniDir = p.join(_fs.buildDir.path, 'ndk-jni').asDir(true);
    
    // Copy cpp files to jniDir
    await _copyDirectory(cppDir, jniDir);

    final androidMk = p.join(jniDir.path, 'Android.mk').asFile();
    if (!androidMk.existsSync()) {
      final cppFiles = jniDir.listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.cpp') || f.path.endsWith('.c'))
          .map((f) => p.relative(f.path, from: jniDir.path).replaceAll(r'\', '/'))
          .join(' ');

      androidMk.writeAsStringSync('''
LOCAL_PATH := \$(call my-dir)

include \$(CLEAR_VARS)
LOCAL_MODULE    := native-lib
LOCAL_SRC_FILES := $cppFiles
include \$(BUILD_SHARED_LIBRARY)
''');
    }

    final applicationMk = p.join(jniDir.path, 'Application.mk').asFile();
    if (!applicationMk.existsSync()) {
      applicationMk.writeAsStringSync('''
APP_ABI := armeabi-v7a arm64-v8a
APP_PLATFORM := android-${config.minSdk}
APP_STL := c++_static
APP_OPTIM := release
APP_STRIP_MODE := --strip-unneeded
''');
    }

    _lgr.info('Compiling Native C/C++ Code', console: false);
    try {
      await _processRunner.runExecutable(
        ndkBuildExe,
        ['NDK_PROJECT_PATH=${_fs.buildDir.path}/ndk-jni', 'APP_BUILD_SCRIPT=${androidMk.path}', 'NDK_APPLICATION_MK=${applicationMk.path}'],
        logToConsole: false,
      );
      _lgr.info('Native C/C++ compiled successfully.');
      
      // Copy libs to build/jni
      final libsDir = p.join(_fs.buildDir.path, 'ndk-jni', 'libs').asDir();
      if (libsDir.existsSync()) {
        await _copyDirectory(libsDir, _fs.buildJniDir);
      }
    } catch (e, s) {
      _lgr.err('NDK Build failed: $e\n$s');
      rethrow;
    }
  }

  static Future<void> _copyDirectory(Directory source, Directory destination) async {
    await for (final entity in source.list(recursive: false)) {
      if (entity is Directory) {
        final newDirectory = Directory(p.join(destination.path, p.basename(entity.path)));
        if (!await newDirectory.exists()) {
          await newDirectory.create(recursive: true);
        }
        await _copyDirectory(entity.absolute, newDirectory);
      } else if (entity is File) {
        final destFile = File(p.join(destination.path, p.basename(entity.path)));
        bool shouldCopy = true;
        if (await destFile.exists()) {
          final srcStat = await entity.stat();
          final destStat = await destFile.stat();
          if (srcStat.modified.isBefore(destStat.modified) || srcStat.modified.isAtSameMomentAs(destStat.modified)) {
            shouldCopy = false;
          }
        }
        if (shouldCopy) {
          await entity.copy(destFile.path);
        }
      }
    }
  }
}
