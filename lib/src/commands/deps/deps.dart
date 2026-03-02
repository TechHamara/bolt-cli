import 'package:args/command_runner.dart';

import 'package:bolt/src/commands/deps/tree.dart';
import 'package:bolt/src/commands/deps/sync.dart';

class DepsCommand extends Command<int> {
  DepsCommand() {
    addSubcommand(TreeCommand());
    addSubcommand(SyncCommand());
  }

  @override
  String get description => 'Work with project dependencies.';

  @override
  String get name => 'deps';
}
