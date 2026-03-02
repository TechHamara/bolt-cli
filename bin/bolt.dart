import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:bolt/src/command_runner.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/services/service_locator.dart';

Future<void> main(List<String> args) async {
  ServiceLocator.setupServiceLocator();
  await GetIt.I.allReady();
  try {
    await BoltCommandRunner().run(args);
  } on UsageException catch (e) {
    GetIt.I<Logger>().err(e.message);
  }
}
