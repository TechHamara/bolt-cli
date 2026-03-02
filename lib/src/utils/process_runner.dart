import 'dart:io' show Process, ProcessException, systemEncoding, File, Platform;

import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/version.dart';

class ProcessRunner {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();
  final _libService = GetIt.I<LibService>();

  Future<void> runExecutable(String exe, List<String> args) async {
    final Process process;
    final ai2ProvidedDeps = await _libService.providedDependencies(null);

    // Try to get annotations JAR from cache, fallback to local path if not found
    String annotationsJar;
    try {
      annotationsJar = ai2ProvidedDeps
          .singleWhere((el) =>
              el.coordinate ==
              'io.github.techhamara.bolt:annotations:$ai2AnnotationVersion')
          .classesJar;
    } catch (e) {
      // If not in cache, try local path override
      final localAnnotationsPath =
          p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'annotations.jar');
      if (File(localAnnotationsPath).existsSync()) {
        annotationsJar = localAnnotationsPath;
        _lgr.dbg('Using local annotations JAR: $annotationsJar');
      } else {
        _lgr.err(
            'Could not find annotations JAR in cache or at $localAnnotationsPath');
        rethrow;
      }
    }

    // Try to get runtime JAR from cache, fallback to local path if not found
    String runtimeJar;
    try {
      runtimeJar = ai2ProvidedDeps
          .singleWhere((el) =>
              el.coordinate ==
              'io.github.techhamara.bolt:runtime:$ai2RuntimeVersion')
          .classesJar;
    } catch (e) {
      // If not in cache, try local path override
      final localRuntimePath =
          p.join(_fs.boltHomeDir.path, 'libs', 'AndroidRuntime.jar');
      if (File(localRuntimePath).existsSync()) {
        runtimeJar = localRuntimePath;
        _lgr.dbg('Using local runtime JAR: $runtimeJar');
      } else {
        _lgr.err('Could not find runtime JAR in cache or at $localRuntimePath');
        rethrow;
      }
    }

    // Prepare environment variables - merge with existing environment
    final Map<String, String> env = {
      ...Platform.environment,
      // These variables are used by the annotation processor
      // Use absolute paths to ensure the processor can locate everything
      'BOLT_PROJECT_ROOT': p.absolute(_fs.cwd),
      'BOLT_ANNOTATIONS_JAR': p.absolute(annotationsJar),
      'BOLT_RUNTIME_JAR': p.absolute(runtimeJar),
      'BOLT_VERSION': packageVersion,
    };

    // Log the cwd and environment setup
    _lgr.info('Setting up process environment for annotation processor');
    _lgr.info('BOLT_PROJECT_ROOT="${env['BOLT_PROJECT_ROOT']}"');
    _lgr.info('Working directory: "${_fs.cwd}"');

    try {
      process = await Process.start(
        exe,
        args,
        environment: env,
        workingDirectory: p.absolute(_fs.cwd),
      );
    } catch (e) {
      if (e.toString().contains('The system cannot find the file specified')) {
        _lgr.err(
            'Could not run `$exe`. Make sure it is installed and in PATH.');
      }
      rethrow;
    }

    process
      ..stdout.transform(systemEncoding.decoder).listen((chunk) {
        _lgr.parseAndLog(chunk);
      })
      ..stderr.transform(systemEncoding.decoder).listen((chunk) {
        _lgr.parseAndLog(chunk);
      });

    if (await process.exitCode != 0) {
      throw ProcessException(exe, args);
    }
  }
}
