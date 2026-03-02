import 'dart:io';
import 'dart:math';

import 'package:args/command_runner.dart';
import 'package:dart_console/dart_console.dart';
import 'package:get_it/get_it.dart';
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/build/build.dart';
import 'package:bolt/src/commands/clean.dart';
import 'package:bolt/src/commands/create/create.dart';
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
          final noColorEnv =
              int.tryParse(Platform.environment['NO_COLOR'] ?? '0');
          final colorEnabled =
              noColorEnv != null && noColorEnv == 1 ? false : ok;
          GetIt.I<Logger>().useColor = colorEnabled;
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
            Console().writeLine('Built on $boltBuiltOn'.grey());
            Console().writeLine();
            // Print full usage without description
            _printVersionHelp();
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
    addCommand(SyncCommand());
    addCommand(TreeCommand());
    addCommand(MigrateCommand());
    addCommand(UpgradeCommand());
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

  void _printVersionHelp() {
    final buffer = StringBuffer();

    // Usage line
    buffer.writeln('Usage: $executableName'.yellow());
    if (commands.isNotEmpty) buffer.write(' <command>'.green());
    buffer.writeln(' [arguments]\n'.magenta());

    // Available commands
    if (commands.isNotEmpty) {
      buffer.writeln('Available commands:'.green());
      final commandWidth =
          commands.keys.map((name) => name.length).reduce((w, n) => max(w, n));
      for (final name in commands.keys) {
        final command = commands[name]!;
        final desc = command.description;
        buffer.writeln('  ${name.padRight(commandWidth).green()}   $desc');
      }
      buffer.writeln();
    }

    // Available arguments
    buffer.writeln('Available arguments:'.magenta());
    buffer.writeln(
        '       ${'-d'.magenta()}  Pass it to enable verbose logging.');
    buffer.writeln(
        '       ${'-r'.magenta()}  Indicates the execution of the ProGuard task. Pass it with the build command.');
    buffer.writeln(
        '       ${'-s'.magenta()}  Indicates the execution of the R8 shriker task. Pass it with the build command.');
    buffer.writeln(
        '       ${'-o'.magenta()}  Indicates to optimize the extension size even there is no ProGuard. Pass it with the build command.');
    buffer.writeln(
        '      ${'-dx'.magenta()}  Indicates to generate the DEX Bytecode by the R8 dexer. Pass it with the build command.');
    buffer.writeln(
        '     ${'bolt'.magenta()}  Indicates that it\'s a bolt project to execute with the migrate command.');
    buffer.writeln(
        ' ${'template'.magenta()}  Indicates that it\'s an extension-template project to execute with the migrate command.');
    buffer.writeln(
        '      ${'ai2'.magenta()}  Indicates that it\'s an App Inventor sources project to execute with the migrate command.\n');

    // Global options
    buffer.writeln('Global options:'.cyan());
    buffer.writeln(argParser.usage);

    Console().writeLine(buffer.toString());
  }

  @override
  void printUsage() {
    Console().writeLine(usage);
  }

  @override
  String get usage {
    final buffer = StringBuffer();
    buffer.writeln('$description\n');

    // Usage line
    buffer.writeln('Usage: $executableName'.yellow());
    if (commands.isNotEmpty) buffer.write(' <command>'.green());
    buffer.writeln(' [arguments]\n'.magenta());

    // Available commands (moved before arguments)
    if (commands.isNotEmpty) {
      buffer.writeln('Available commands:'.green());
      final commandWidth =
          commands.keys.map((name) => name.length).reduce((w, n) => max(w, n));
      for (final name in commands.keys) {
        final command = commands[name]!;
        final desc = command.description;
        buffer.writeln('  ${name.padRight(commandWidth).green()}   $desc');
      }
      buffer.writeln();
    }

    // Available arguments
    buffer.writeln('Available arguments:'.magenta());
    buffer.writeln(
        '       ${'-d'.magenta()}  Pass it to enable verbose logging.');
    buffer.writeln(
        '       ${'-r'.magenta()}  Indicates the execution of the ProGuard task. Pass it with the build command.');
    buffer.writeln(
        '       ${'-s'.magenta()}  Indicates the execution of the R8 shriker task. Pass it with the build command.');
    buffer.writeln(
        '       ${'-o'.magenta()}  Indicates to optimize the extension size even there is no ProGuard. Pass it with the build command.');
    buffer.writeln(
        '      ${'-dx'.magenta()}  Indicates to generate the DEX Bytecode by the R8 dexer. Pass it with the build command.');
    buffer.writeln(
        '     ${'bolt'.magenta()}  Indicates that it\'s a bolt project to execute with the migrate command.');
    buffer.writeln(
        ' ${'template'.magenta()}  Indicates that it\'s an extension-template project to execute with the migrate command.');
    buffer.writeln(
        '      ${'ai2'.magenta()}  Indicates that it\'s an App Inventor sources project to execute with the migrate command.\n');

    // Global options
    buffer.writeln('Global options:'.cyan());
    buffer.writeln(argParser.usage);

    return buffer.toString();
  }
}
