import 'dart:convert';
import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:http/http.dart' as http;
import 'package:tint/tint.dart';

import 'package:bolt/src/commands/deps/sync.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';

class AddCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  @override
  String get description => 'Adds a dependency to bolt.yml automatically from Maven Central.';

  @override
  String get name => 'add';

  @override
  String get invocation => 'bolt add <groupId:artifactId>';

  @override
  Future<int> run() async {
    if (argResults!.rest.isEmpty) {
      _lgr.err('Please provide a dependency to add. Format: groupId:artifactId');
      return 1;
    }

    final depString = argResults!.rest.first;
    final parts = depString.split(':');
    
    String groupId = '';
    String artifactId = '';
    String? version;

    if (parts.length == 2) {
      groupId = parts[0];
      artifactId = parts[1];
    } else if (parts.length == 3) {
      groupId = parts[0];
      artifactId = parts[1];
      version = parts[2];
    } else {
      _lgr.err('Invalid dependency format. Use groupId:artifactId or groupId:artifactId:version');
      return 1;
    }

    if (version == null) {
      _lgr.startTask('Searching Maven Central for $groupId:$artifactId');
      try {
        final query = 'g:"$groupId" AND a:"$artifactId"';
        final url = Uri.parse('https://search.maven.org/solrsearch/select?q=$query&rows=1&wt=json');
        final response = await http.get(url);
        
        if (response.statusCode == 200) {
          final json = jsonDecode(response.body);
          final docs = json['response']['docs'] as List;
          if (docs.isEmpty) {
            _lgr.stopTask(false);
            _lgr.err('Dependency not found on Maven Central.');
            return 1;
          }
          version = docs.first['latestVersion'] as String;
          _lgr.stopTask();
          _lgr.info('Found latest version: $version');
        } else {
          _lgr.stopTask(false);
          _lgr.err('Failed to query Maven Central: ${response.statusCode}');
          return 1;
        }
      } catch (e) {
        _lgr.stopTask(false);
        _lgr.err('Network error occurred: $e');
        return 1;
      }
    }

    final coordinate = '$groupId:$artifactId:$version';
    
    // Inject into bolt.yml
    final file = _fs.configFile;
    if (!file.existsSync()) {
      _lgr.err('Not a Bolt project (bolt.yml not found).');
      return 1;
    }

    final content = file.readAsStringSync();
    
    // Basic string replacement to inject dependency
    // We look for dependencies: array.
    final depsRegex = RegExp(r'^(\s*)dependencies:\s*$', multiLine: true);
    
    String newContent;
    if (depsRegex.hasMatch(content)) {
      final match = depsRegex.firstMatch(content)!;
      final indent = match.group(1)!;
      final replacement = '${match.group(0)}\n$indent  - $coordinate';
      newContent = content.replaceFirst(depsRegex, replacement);
    } else {
      // Append at the end if it doesn't exist
      newContent = '$content\n\ndependencies:\n  - $coordinate\n';
    }
    
    file.writeAsStringSync(newContent);
    
    _lgr.info('Added \'$coordinate\' to bolt.yml'.green());

    // Run sync
    _lgr.info('Syncing dependencies...');
    final syncCmd = SyncCommand();
    return await syncCmd.run(title: 'Syncing new dependency');
  }
}
