import 'dart:convert';
import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:tint/tint.dart';
import 'package:bolt/src/services/daemon_service.dart';
import 'package:bolt/src/services/logger.dart';

class DaemonCommand extends Command<int> {
  final _lgr = GetIt.I<Logger>();
  final _daemonService = DaemonService();

  DaemonCommand() {
    argParser.addOption('port', abbr: 'p', help: 'Port for daemon server.', defaultsTo: '19090');
    addSubcommand(_DaemonStartSubcommand(_daemonService));
    addSubcommand(_DaemonStopSubcommand(_daemonService));
    addSubcommand(_DaemonStatusSubcommand(_daemonService));
    addSubcommand(_DaemonServerSubcommand(_daemonService));
  }

  @override
  String get description => 'Manages the persistent Bolt Compiler Daemon.';

  @override
  String get name => 'daemon';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '19090') ?? 19090;
    final isRunning = await _daemonService.isRunning(port);
    if (isRunning) {
      _lgr.info('Bolt Daemon is ${'RUNNING'.green()} on port $port');
    } else {
      _lgr.info('Bolt Daemon is ${'STOPPED'.red()}. Run `bolt daemon start` to start it.');
    }
    return 0;
  }
}

class _DaemonStartSubcommand extends Command<int> {
  final DaemonService _daemonService;
  final _lgr = GetIt.I<Logger>();

  _DaemonStartSubcommand(this._daemonService) {
    argParser.addOption('port', abbr: 'p', help: 'Port for daemon server.', defaultsTo: '19090');
  }

  @override
  String get description => 'Starts the background Bolt Compiler Daemon.';

  @override
  String get name => 'start';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '19090') ?? 19090;
    final ok = await _daemonService.startDaemon(port: port);
    return ok ? 0 : 1;
  }
}

class _DaemonStopSubcommand extends Command<int> {
  final DaemonService _daemonService;

  _DaemonStopSubcommand(this._daemonService) {
    argParser.addOption('port', abbr: 'p', help: 'Port for daemon server.', defaultsTo: '19090');
  }

  @override
  String get description => 'Stops the running Bolt Compiler Daemon.';

  @override
  String get name => 'stop';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '19090') ?? 19090;
    final ok = await _daemonService.stopDaemon(port: port);
    return ok ? 0 : 1;
  }
}

class _DaemonStatusSubcommand extends Command<int> {
  final DaemonService _daemonService;
  final _lgr = GetIt.I<Logger>();

  _DaemonStatusSubcommand(this._daemonService) {
    argParser.addOption('port', abbr: 'p', help: 'Port for daemon server.', defaultsTo: '19090');
  }

  @override
  String get description => 'Displays current status of the Bolt Compiler Daemon.';

  @override
  String get name => 'status';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '19090') ?? 19090;
    final isRunning = await _daemonService.isRunning(port);
    if (isRunning) {
      _lgr.info('Bolt Daemon Status: ${'RUNNING'.green()} (Port: $port)');
    } else {
      _lgr.info('Bolt Daemon Status: ${'STOPPED'.red()}');
    }
    return 0;
  }
}

class _DaemonServerSubcommand extends Command<int> {
  final DaemonService _daemonService;

  _DaemonServerSubcommand(this._daemonService) {
    argParser.addOption('port', abbr: 'p', help: 'Port for daemon server.', defaultsTo: '19090');
  }

  @override
  String get description => 'Internal worker subcommand to run the server.';

  @override
  String get name => 'server';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '19090') ?? 19090;
    await _daemonService.runServer(port: port);
    return 0;
  }
}
