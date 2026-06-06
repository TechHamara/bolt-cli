import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:dart_console/dart_console.dart';
import 'package:get_it/get_it.dart';
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/build/build.dart';
import 'package:bolt/src/commands/clean.dart';
import 'package:bolt/src/commands/create/create.dart';
import 'package:bolt/src/commands/deps/deps.dart';
import 'package:bolt/src/commands/deps/sync.dart';
import 'package:bolt/src/commands/deps/tree.dart';
import 'package:bolt/src/commands/migrate/migrate.dart';
import 'package:bolt/src/commands/upgrade/upgrade.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/version.dart';
import 'package:bolt/version.dart' show boltBuiltOn;

class BoltCommandRunner extends CommandRunner<int> {
  BoltCommandRunner()
      : super('bolt',
            'Bolt is a fast and feature rich extension builder for MIT App Inventor 2.') {
    argParser
      ..addFlag(
        'verbose',
        abbr: 'v',
        negatable: false,
        help: 'Turns on verbose logging.',
        callback: (ok) {
          GetIt.I<Logger>().debug = ok;
        },
      )
      ..addFlag(
        'debug',
        abbr: 'd',
        negatable: false,
        help: 'Pass it to enable verbose logging.',
        callback: (ok) {
          GetIt.I<Logger>().debug = ok;
        },
      )
      ..addFlag(
        'color',
        abbr: 'c',
        defaultsTo: true,
        help:
            'Whether output should be colorized or not. Defaults to true in terminals that support ANSI colors.',
        callback: (ok) {
          // supportsAnsiColor is read-only in the standard tint package
        },
      )
      ..addFlag(
        'version',
        abbr: 'V',
        negatable: false,
        help: 'Prints the current version name.',
        callback: (ok) {
          if (ok) {
            Console().writeLine('Running on version ${packageVersion.cyan()}');
            exit(0);
          }
        },
      )
      ..addFlag(
        'logo',
        abbr: 'l',
        defaultsTo: true,
        hide: true,
        callback: (ok) {
          if (ok) _printLogo();
        },
      );

    addCommand(BuildCommand());
    addCommand(CleanCommand());
    addCommand(CreateCommand());
    addCommand(DepsCommand());
    addCommand(SyncCommand());
    addCommand(TreeCommand());
    addCommand(MigrateCommand());
    addCommand(UpgradeCommand());
  }

  @override
  Future<int?> run(Iterable<String> args) async {
    final listArgs = args.toList();
    if (listArgs.isNotEmpty && listArgs[0] == 'v') {
      listArgs[0] = '-v';
    }

    if (listArgs.length >= 2 &&
        listArgs[0] == 'sync' &&
        listArgs[1] == 'build') {
      // First, run 'sync'
      final syncRes = await super.run(['sync']);
      if (syncRes != 0) {
        return syncRes;
      }
      // Then, run 'build' with any remaining arguments
      final buildArgs = ['build'] + listArgs.sublist(2);
      return await super.run(buildArgs);
    }

    return await super.run(listArgs);
  }

  @override
  String get usage {
    final buffer = StringBuffer();

    buffer.writeln('${'Build Faster, Compile Smarter.'.white()}');
    buffer.writeln(
        '${'An Efficient Framework for MIT App Inventor 2 Extensions.'.white()}');
    buffer.writeln('${'Usage: bolt'.brightYellow()}');
    buffer.writeln(' ${'<command>'.green()} ${'[arguments]'.magenta()}');
    buffer.writeln();

    // Section 1: Available commands in Green
    buffer.writeln('Available commands:'.green());
    buffer
        .writeln('  ${'help'.green()}      Display help information for bolt.');
    buffer.writeln(
        '  ${'build'.green()}     Builds the extension project in current working directory.');
    buffer.writeln(
        '  ${'clean'.green()}     Deletes old build files and caches.');
    buffer.writeln(
        '  ${'create'.green()}    Scaffolds a new extension project in the current working directory.');
    buffer.writeln(
        '  ${'sync'.green()}      Syncs dev and project dependencies.');
    buffer.writeln(
        '  ${'tree'.green()}      Prints the graph of the current extension project.');
    buffer.writeln(
        '  ${'migrate'.green()}   Migrates the rush/fast/extension-template project to bolt in current working directory.');
    buffer.writeln(
        '  ${'upgrade'.green()}   Upgrades Bolt to the latest available version.');
    buffer.writeln();

    // Section 2: Available arguments in Magenta
    buffer.writeln('Available arguments:'.magenta());
    buffer.writeln(
        '       ${'-d'.magenta()}  Pass it to enable verbose logging.');
    buffer.writeln(
        '       ${'-r'.magenta()}  Indicates the execution of the ProGuard task. Pass it with the ${'build'.green()} command.');
    buffer.writeln(
        '       ${'-s'.magenta()}  Indicates the execution of the R8 shriker task. Pass it with the ${'build'.green()} command.');
    buffer.writeln(
        '       ${'-o'.magenta()}  Indicates to optimize the extension size even there is no ProGuard. Pass it with the ${'build'.green()} command.');
    buffer.writeln(
        '      ${'-dx'.magenta()}  Indicates to generate the DEX Bytecode by the R8 dexer. Pass it with the ${'build'.green()} command.');
    buffer.writeln(
        "     ${'rush'.magenta()}  Indicates that it's a rush project to execute with the ${'migrate'.green()} command.");
    buffer.writeln(
        "     ${'fast'.magenta()}  Indicates that it's a fast project to execute with the ${'migrate'.green()} command.");
    buffer.writeln(
        " ${'template'.magenta()}  Indicates that it's an extension-template project to execute with the ${'migrate'.green()} command.");
    buffer.writeln(
        "      ${'ai2'.magenta()}  Indicates that it's an App Inventor sources project to execute with the ${'migrate'.green()} command.");
    buffer.writeln();

    // Section 3: Global options in Cyan
    buffer.writeln('Global options:'.cyan());
    buffer.writeln(
        '${'-h, --help'.cyan()}          Print this usage information.');
    buffer.writeln('${'-v, --verbose'.cyan()}       Turns on verbose logging.');
    buffer.writeln(
        '${'-d, --debug'.cyan()}         Pass it to enable verbose logging.');
    buffer.writeln(
        '${'-c, --[no-]color'.cyan()}    Whether output should be colorized or not. Defaults to true in terminals that support ANSI colors.');
    buffer.writeln('                    (defaults to on)');
    buffer.writeln(
        '${'-V, --version'.cyan()}       Prints the current version name.');

    return buffer.toString();
  }

  void _printLogo() {
    const v = packageVersion;
    final logo = r'''
+===============================+
| ___.            .__     __    |
| \_ |__    ____  |  |  _/  |_  |
|  | __ \  /  _ \ |  |  \   __\ |
|  | \_\ \(  <_> )|  |__ |  |   |
|  |___  / \____/ |____/ |__|   |
|      \/                       |
+===============================+
'''
        ' (v$v)';
    Console().writeLine(logo.brightYellow());
    Console().writeLine('Built on $boltBuiltOn'.grey());
    Console().writeLine();
  }
}
