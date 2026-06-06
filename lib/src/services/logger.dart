import 'dart:convert';
import 'dart:io' show File, FileMode;

import 'package:dart_console/dart_console.dart';
import 'package:tint/tint.dart';

final _console = Console();

class Logger {
  bool debug = false;

  bool _isTaskRunning = false;
  bool _hasTaskLogged = false;
  final _taskStopwatch = Stopwatch();

  void dbg(String message) {
    log(message, 'debug '.blue(), debug);
  }

  void info(String message, {bool console = true}) {
    log(message, 'info  '.cyan(), console);
  }

  void warn(String message, {bool console = true}) {
    log(message, 'warn  '.yellow(), console);
  }

  void err(String message, {bool console = true}) {
    log(message, 'error '.red(), console);
  }

  final _warnRegex = RegExp('(warning:? ){1,2}', caseSensitive: false);
  final _errRegex = RegExp('(error:? ){1,2}', caseSensitive: false);
  final _exceptionRegex = RegExp('(exception:? )', caseSensitive: false);
  final _dbgRegex = RegExp('((note:? )|(debug:? )){1,2}', caseSensitive: false);
  final _infoRegex = RegExp('(info:? ){1,2}', caseSensitive: false);

  void parseAndLog(String chunk, {bool console = true}) {
    final lines = LineSplitter.split(chunk);
    for (final el in lines.toList()) {
      if (el.trim().isEmpty) {
        continue;
      }

      bool lineConsole = console;
      final elLower = el.toLowerCase();
      if (elLower.contains('auto_version') ||
          elLower.contains('autoversion') ||
          elLower.contains('not recognized by any processor')) {
        lineConsole = false;
      }

      final String prefix;
      final String msg;
      if (_warnRegex.hasMatch(el)) {
        prefix = 'warn  '.yellow();
        msg = el.replaceFirst(_warnRegex, '');
      } else if (_errRegex.hasMatch(el)) {
        prefix = 'error '.red();
        msg = el.replaceFirst(_errRegex, '');
      } else if (_exceptionRegex.hasMatch(el)) {
        prefix = 'error '.red();
        msg = el;
      } else if (_infoRegex.hasMatch(el)) {
        prefix = 'info  '.cyan();
        msg = el.replaceFirst(_infoRegex, '');
      } else if (_dbgRegex.hasMatch(el)) {
        if (!debug && lineConsole) {
          continue;
        }
        prefix = 'debug '.blue();
        msg = el.replaceFirst(_dbgRegex, '');
      } else {
        prefix = ' ' * 6;
        msg = el;
      }
      log(msg.trimRight(), prefix, lineConsole);
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

  void log(String message, [String prefix = '', bool console = true]) {
    if (console && !_hasTaskLogged && _isTaskRunning) {
      _console
        ..cursorUp()
        ..eraseLine()
        ..write('┌ '.brightBlack() + _taskTitle)
        ..writeLine();
      _hasTaskLogged = true;
    }
    if (_isTaskRunning) {
      prefix = '│ '.brightBlack() + prefix;
    }
    final line = prefix + message.trimRight();
    if (console) {
      _console.writeLine(line);
    }

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
      ..write('- '.brightBlack())
      ..write(title)
      ..writeLine();
  }

  void stopTask([bool success = true]) {
    if (!_isTaskRunning) {
      throw Exception('No task is running');
    }

    final time = (_taskStopwatch.elapsedMilliseconds / 1000).toStringAsFixed(2);
    String line = (success ? '✓'.green() : '×'.red()) +
        ' ' * 4 +
        '... (${time}s)'.brightBlack();
    if (_hasTaskLogged) {
      line = '└ '.brightBlack() + line;
    } else {
      line = '${'- '.brightBlack()}$_taskTitle $line';
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
