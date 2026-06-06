import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:tint/tint.dart';

import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';

class TreeCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  @override
  String get description =>
      'Prints the folder and file hierarchical structure of the current project.';

  @override
  String get name => 'tree';

  @override
  Future<int> run() async {
    final projectName = p.basename(_fs.cwd);
    
    final coloredLines = <String>[];
    final plainLines = <String>[];
    
    coloredLines.add(projectName.cyan().bold());
    plainLines.add(projectName);
    
    _generateProjectTree(Directory(_fs.cwd), '', '', coloredLines, plainLines);
    
    final coloredOutput = coloredLines.join('\n');
    final plainOutput = plainLines.join('\n');
    
    _lgr.log(coloredOutput);
    
    // Save plainOutput to tree.txt in current project dir
    try {
      final treeFile = File(p.join(_fs.cwd, 'tree.txt'));
      treeFile.writeAsStringSync(plainOutput);
      _lgr.info('Project hierarchical structure saved to ${"tree.txt".green()}');
    } catch (e) {
      _lgr.err('Failed to save project structure to tree.txt: $e');
    }
    
    return 0;
  }

  void _generateProjectTree(
    Directory dir,
    String coloredIndent,
    String plainIndent,
    List<String> coloredLines,
    List<String> plainLines,
  ) {
    if (!dir.existsSync()) return;
    
    final List<FileSystemEntity> entities;
    try {
      entities = dir.listSync();
    } catch (e) {
      return;
    }
    
    final List<FileSystemEntity> visibleEntities = [];
    for (final entity in entities) {
      final name = p.basename(entity.path);
      
      // Exclude hidden directories/files starting with a dot
      if (name.startsWith('.')) continue;
      
      // Exclude tree.txt
      if (name == 'tree.txt') continue;
      
      visibleEntities.add(entity);
    }
    
    // Sort directories first alphabetically, then files alphabetically
    visibleEntities.sort((a, b) {
      final aIsDir = a is Directory;
      final bIsDir = b is Directory;
      if (aIsDir && !bIsDir) return -1;
      if (!aIsDir && bIsDir) return 1;
      return p.basename(a.path).toLowerCase().compareTo(p.basename(b.path).toLowerCase());
    });
    
    for (var i = 0; i < visibleEntities.length; i++) {
      final entity = visibleEntities[i];
      final isLast = i == visibleEntities.length - 1;
      final name = p.basename(entity.path);
      
      final coloredConnector = isLast ? '└─ '.grey() : '├─ '.grey();
      final plainConnector = isLast ? '└─ ' : '├─ ';
      
      if (entity is Directory) {
        coloredLines.add('$coloredIndent$coloredConnector${name.blue().bold()}/');
        plainLines.add('$plainIndent$plainConnector$name/');
        
        final nextIndentColored = coloredIndent + (isLast ? '   ' : '│  '.grey());
        final nextIndentPlain = plainIndent + (isLast ? '   ' : '│  ');
        _generateProjectTree(entity, nextIndentColored, nextIndentPlain, coloredLines, plainLines);
      } else {
        coloredLines.add('$coloredIndent$coloredConnector$name');
        plainLines.add('$plainIndent$plainConnector$name');
      }
    }
  }
}
