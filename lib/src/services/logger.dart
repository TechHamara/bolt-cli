import 'dart:convert';
import 'dart:io' show File, FileMode;

import 'package:dart_console/dart_console.dart';
import 'package:tint/tint.dart';

final _console = Console();

class Logger {
  bool debug = false;
  bool useColor = true;

  bool _isTaskRunning = false;
  bool _hasTaskLogged = false;
  final _taskStopwatch = Stopwatch();

  String _applyColor(String text, String Function(String) colorFn) {
    return useColor ? colorFn(text) : text;
  }

  void dbg(String message) {
    if (debug) {
      log(message, _applyColor('debug ', (s) => s.blue()));
    }
  }

  void info(String message) {
    log(message, _applyColor('info  ', (s) => s.cyan()));
  }

  void warn(String message) {
    log(message, _applyColor('warn  ', (s) => s.yellow()));
  }

  void err(String message) {
    log(message, _applyColor('error ', (s) => s.red()));
  }

  final _warnRegex = RegExp('(warning:? ){1,2}', caseSensitive: false);
  final _errRegex = RegExp('(error:? ){1,2}', caseSensitive: false);
  final _exceptionRegex = RegExp('(exception:? )', caseSensitive: false);
  final _dbgRegex = RegExp('((note:? )|(debug:? )){1,2}', caseSensitive: false);
  final _infoRegex = RegExp('(info:? ){1,2}', caseSensitive: false);

  void parseAndLog(String chunk) {
    final lines = LineSplitter.split(chunk);
    for (final el in lines.toList()) {
      if (el.trim().isEmpty) {
        continue;
      }

      final String prefix;
      final String msg;
      if (_warnRegex.hasMatch(el)) {
        prefix = _applyColor('warn  ', (s) => s.yellow());
        msg = el.replaceFirst(_warnRegex, '');
      } else if (_errRegex.hasMatch(el)) {
        prefix = _applyColor('error ', (s) => s.red());
        msg = el.replaceFirst(_errRegex, '');
      } else if (_exceptionRegex.hasMatch(el)) {
        prefix = _applyColor('error ', (s) => s.red());
        msg = el;
      } else if (_infoRegex.hasMatch(el)) {
        prefix = _applyColor('info  ', (s) => s.cyan());
        msg = el.replaceFirst(_infoRegex, '');
      } else if (_dbgRegex.hasMatch(el)) {
        if (!debug) {
          continue;
        }
        prefix = _applyColor('debug ', (s) => s.blue());
        msg = el.replaceFirst(_dbgRegex, '');
      } else {
        prefix = ' ' * 6;
        msg = el;
      }
      log(msg.trimRight(), prefix);
    }
  }

  String _taskTitle = '';

  File? _logFile;

  /// When provided, all written log lines are also appended to this file
  /// (ANSI escape sequences are stripped to keep the text readable).
  void setOutputFile(File file) {
    _logFile = file;
    if (!_logFile!.existsSync()) {
      _logFile!.createSync(recursive: true);
    }
  }

  void log(String message, [String prefix = '']) {
    if (!_hasTaskLogged && _isTaskRunning) {
      _console
        ..cursorUp()
        ..eraseLine()
        ..write(_applyColor('┌ ', (s) => s.brightBlack()) + _taskTitle)
        ..writeLine();
      _hasTaskLogged = true;
    }
    if (_isTaskRunning) {
      prefix = _applyColor('│ ', (s) => s.brightBlack()) + prefix;
    }
    final line = prefix + message.trimRight();
    _console.writeLine(line);

    if (_logFile != null) {
      // strip ANSI color codes before writing
      final plain = line.replaceAll(RegExp(r'\x1B\[[0-9;]*m'), '');
      try {
        _logFile!.writeAsStringSync('$plain\n', mode: FileMode.append);
      } catch (_) {
        // ignore write failures
      }
    }
  }

  void startTask(String title) {
    if (_isTaskRunning) {
      throw Exception('A task is already running');
    }
    _taskStopwatch.start();
    _isTaskRunning = true;
    _taskTitle = title;
    _console
      ..write(_applyColor('- ', (s) => s.brightBlack()))
      ..write(title)
      ..writeLine();
  }

  void stopTask([bool success = true]) {
    if (!_isTaskRunning) {
      throw Exception('No task is running');
    }

    final time = (_taskStopwatch.elapsedMilliseconds / 1000).toStringAsFixed(2);
    final checkmark =
        useColor ? (success ? '✓'.green() : '×'.red()) : (success ? '✓' : '×');
    String line = checkmark +
        ' ' * 4 +
        _applyColor('... (${time}s)', (s) => s.brightBlack());
    if (_hasTaskLogged) {
      line = _applyColor('└ ', (s) => s.brightBlack()) + line;
    } else {
      line = '${_applyColor('- ', (s) => s.brightBlack())}$_taskTitle $line';
      _console
        ..cursorUp()
        ..eraseLine();
    }
    _console
      ..write(line)
      ..writeLine();

    _isTaskRunning = false;
    _hasTaskLogged = false;
    _taskStopwatch.reset();
  }
}
