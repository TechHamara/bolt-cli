import 'dart:convert' show utf8, Utf8Decoder;
import 'dart:io' show Process, ProcessException;

import 'package:get_it/get_it.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/version.dart';

class ProcessRunner {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();
  final _libService = GetIt.I<LibService>();

  Future<void> runExecutable(
    String exe,
    List<String> args, {
    bool logToConsole = true,
  }) async {
    final Process process;
    final ai2ProvidedDeps = await _libService.providedDependencies(null);
    try {
      process = await Process.start(exe, args, environment: {
        // These variables are used by the annotation processor
        'BOLT_PROJECT_ROOT': _fs.cwd,
        'BOLT_ANNOTATIONS_JAR': ai2ProvidedDeps
            .singleWhere((el) =>
                el.coordinate ==
                'io.github.techhamara.bolt:annotations:$ai2AnnotationVersion')
            .classesJar,
        'BOLT_RUNTIME_JAR': ai2ProvidedDeps
            .singleWhere((el) =>
                el.coordinate ==
                'io.github.techhamara.bolt:runtime:$ai2RuntimeVersion')
            .classesJar,
        'BOLT_VERSION': packageVersion,
      });
    } catch (e) {
      if (e.toString().contains('The system cannot find the file specified')) {
        _lgr.err(
            'Could not run `$exe`. Make sure it is installed and in PATH.');
      }
      rethrow;
    }

    final outputBuffer = StringBuffer();

    await Future.wait([
      process.stdout
          .transform(const Utf8Decoder(allowMalformed: true))
          .forEach((chunk) {
        if (!logToConsole) {
          outputBuffer.write(chunk);
        }
        _lgr.parseAndLog(chunk, console: logToConsole);
      }),
      process.stderr
          .transform(const Utf8Decoder(allowMalformed: true))
          .forEach((chunk) {
        if (!logToConsole) {
          outputBuffer.write(chunk);
        }
        _lgr.parseAndLog(chunk, console: logToConsole);
      }),
    ]);

    if (await process.exitCode != 0) {
      if (!logToConsole) {
        _lgr.parseAndLog(outputBuffer.toString());
      }
      throw ProcessException(exe, args);
    }
  }
}
