import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_web_socket/shelf_web_socket.dart';
import 'package:watcher/watcher.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:path/path.dart' as p;

import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';

class RunCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();
  final _clients = <WebSocketChannel>{};
  
  bool _isBuilding = false;
  bool _needsRebuild = false;

  RunCommand() {
    argParser.addOption(
      'port',
      abbr: 'p',
      help: 'Port to run the Live Testing WebSocket server on.',
      defaultsTo: '9000',
    );
  }

  @override
  String get description =>
      'Starts a Live Testing session for hot-reloading the extension to a mobile device.';

  @override
  String get name => 'run';

  @override
  Future<int> run() async {
    final port = int.tryParse(argResults?['port'] as String? ?? '9000') ?? 9000;
    
    // Validate that config exists
    final config = await Config.load(_fs.configFile, _lgr);
    if (config == null) return 1;

    // Start WebSocket server
    final handler = webSocketHandler((WebSocketChannel webSocket) {
      _lgr.info('New companion connected.');
      _clients.add(webSocket);
      
      // Send initial build if available
      _sendLatestDex();

      webSocket.stream.listen(
        (message) {
          if (message is String) {
            try {
              final data = jsonDecode(message);
              if (data['type'] == 'telemetry') {
                final memMB = data['usedMemoryMB'];
                final freeMB = data['freeMemoryMB'];
                _lgr.info('[TELEMETRY] Memory: ${memMB}MB Used / ${freeMB}MB Free');
              } else {
                _lgr.dbg('Received message from companion: $message');
              }
            } catch (e) {
              _lgr.dbg('Received message from companion: $message');
            }
          }
        },
        onDone: () {
          _lgr.info('Companion disconnected.');
          _clients.remove(webSocket);
        },
        onError: (e) {
          _lgr.err('WebSocket Error: $e');
          _clients.remove(webSocket);
        },
      );
    });

    final server = await shelf_io.serve(handler, '0.0.0.0', port);
    
    _lgr.info('🚀 Live Companion Server started!');
    _lgr.info('Listening on ws://${server.address.host}:${server.port}');
    
    // Start UDP auto-discovery
    await _startUdpDiscovery(port);
    
    // Start initial build
    await _triggerBuild(config);

    // Watch for file changes
    final watcher = DirectoryWatcher(_fs.srcDir.path);
    _lgr.info('Watching for file changes in src/ directory...');
    
    watcher.events.listen((event) async {
      if (event.type == ChangeType.MODIFY || event.type == ChangeType.ADD || event.type == ChangeType.REMOVE) {
        if (p.extension(event.path) == '.java' || p.extension(event.path) == '.kt' || p.extension(event.path) == '.cpp') {
          _lgr.info('File changed: ${event.path}');
          if (_isBuilding) {
            _needsRebuild = true;
          } else {
            await _triggerBuild(config);
          }
        }
      }
    });

    // Wait forever
    await Completer<void>().future;
    return 0;
  }
  
  Future<void> _triggerBuild(Config config) async {
    _isBuilding = true;
    _needsRebuild = false;
    
    try {
      _lgr.startTask('Hot-reloading Build');
      final result = await Process.run('bolt', ['build'], runInShell: true);
      if (result.exitCode == 0) {
        _lgr.stopTask();
        _lgr.info('Build successful, pushing to companion...');
        _sendLatestDex();
      } else {
        _lgr.stopTask(false);
        _lgr.err('Build failed:\n${result.stdout}\n${result.stderr}');
      }
    } catch (e) {
      _lgr.stopTask(false);
      _lgr.err('Failed to build: $e');
    } finally {
      _isBuilding = false;
      if (_needsRebuild) {
        await _triggerBuild(config);
      }
    }
  }
  
  void _sendLatestDex() {
    if (_clients.isEmpty) return;
    
    // We send classes.dex from build/dex/ directory
    final dexDir = p.join(_fs.buildDir.path, 'dex').asDir();
    if (!dexDir.existsSync()) return;
    
    final dexFiles = dexDir.listSync().whereType<File>().where((f) => f.path.endsWith('.dex')).toList();
    if (dexFiles.isEmpty) return;
    
    // Read classes.dex
    final primaryDex = dexFiles.firstWhere((f) => p.basename(f.path) == 'classes.dex', orElse: () => dexFiles.first);
    final bytes = primaryDex.readAsBytesSync();
    
    for (final client in _clients) {
      client.sink.add(bytes);
      Future.delayed(Duration(milliseconds: 100), () {
        client.sink.add("RELOAD:AnyComponent");
      });
    }
    _lgr.info('Sent classes.dex (${bytes.length} bytes) and RELOAD signal to ${_clients.length} companion(s).');
  }

  Future<void> _startUdpDiscovery(int wsPort) async {
    try {
      final udpSocket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 9001);
      udpSocket.broadcastEnabled = true;
      _lgr.info('UDP Auto-discovery listening on port 9001');

      udpSocket.listen((RawSocketEvent event) {
        if (event == RawSocketEvent.read) {
          final datagram = udpSocket.receive();
          if (datagram != null) {
            final message = String.fromCharCodes(datagram.data).trim();
            if (message == 'BOLT_DISCOVER') {
              final response = 'BOLT_SERVER:$wsPort';
              udpSocket.send(response.codeUnits, datagram.address, datagram.port);
              _lgr.dbg('Answered discovery broadcast from ${datagram.address.address}');
            }
          }
        }
      });
    } catch (e) {
      _lgr.warn('Failed to start UDP auto-discovery: $e');
    }
  }
}
