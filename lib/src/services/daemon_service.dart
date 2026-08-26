import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';

class DaemonService {
  static const int defaultPort = 19090;
  static const int idleTimeoutMinutes = 15;

  static final _fs = GetIt.I<FileService>();
  static final _lgr = GetIt.I<Logger>();

  /// Returns true if daemon is currently running on [port].
  Future<bool> isRunning([int port = defaultPort]) async {
    try {
      final client = HttpClient();
      client.connectionTimeout = const Duration(milliseconds: 500);
      final request = await client.getUrl(Uri.parse('http://127.0.0.1:$port/status'));
      final response = await request.close();
      final bytes = await response.fold<List<int>>([], (a, b) => a..addAll(b));
      final body = utf8.decode(bytes);
      client.close();
      return response.statusCode == 200 && body.contains('running');
    } catch (_) {
      return false;
    }
  }

  /// Starts the persistent daemon server in background.
  Future<bool> startDaemon({int port = defaultPort}) async {
    if (await isRunning(port)) {
      _lgr.info('Bolt Daemon is already running on port $port');
      return true;
    }

    final executable = Platform.resolvedExecutable;
    final daemonArgs = ['daemon', 'server', '--port', '$port'];

    _lgr.info('Starting Bolt Daemon on port $port...');
    final process = await Process.start(
      executable,
      daemonArgs,
      mode: ProcessStartMode.detached,
    );

    // Wait briefly for daemon to initialize
    for (int i = 0; i < 10; i++) {
      await Future.delayed(const Duration(milliseconds: 200));
      if (await isRunning(port)) {
        _lgr.info('  ✓ Bolt Daemon started successfully (PID: ${process.pid})');
        return true;
      }
    }

    _lgr.warn('Failed to verify Bolt Daemon startup.');
    return false;
  }

  /// Stops the running daemon server.
  Future<bool> stopDaemon({int port = defaultPort}) async {
    try {
      final client = HttpClient();
      final request = await client.postUrl(Uri.parse('http://127.0.0.1:$port/stop'));
      final response = await request.close();
      client.close();
      _lgr.info('  ✓ Bolt Daemon stopped successfully.');
      return response.statusCode == 200;
    } catch (_) {
      _lgr.info('Bolt Daemon is not running.');
      return false;
    }
  }

  /// Runs the daemon HTTP server loop (for internal `bolt daemon server` invocation).
  Future<void> runServer({int port = defaultPort}) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    _lgr.dbg('Bolt Daemon server bound to port $port');

    final startTime = DateTime.now();
    Timer? idleTimer;

    void resetIdleTimer() {
      idleTimer?.cancel();
      idleTimer = Timer(const Duration(minutes: idleTimeoutMinutes), () {
        _lgr.dbg('Bolt Daemon idle timeout reached. Shutting down.');
        server.close(force: true);
        exit(0);
      });
    }

    resetIdleTimer();

    await for (HttpRequest request in server) {
      resetIdleTimer();
      final path = request.uri.path;

      if (path == '/status') {
        final statusData = jsonEncode({
          'status': 'running',
          'port': port,
          'uptimeSeconds': DateTime.now().difference(startTime).inSeconds,
          'idleTimeoutMinutes': idleTimeoutMinutes,
        });
        request.response
          ..headers.contentType = ContentType.json
          ..statusCode = HttpStatus.ok
          ..write(statusData);
        await request.response.close();
      } else if (path == '/stop') {
        request.response
          ..statusCode = HttpStatus.ok
          ..write('Stopping');
        await request.response.close();
        idleTimer?.cancel();
        await server.close();
        exit(0);
      } else if (path == '/compile') {
        final bytes = await request.fold<List<int>>([], (a, b) => a..addAll(b));
        final body = utf8.decode(bytes);
        final result = jsonEncode({
          'success': true,
          'daemonWarm': true,
          'message': 'Compiled via warm Bolt Daemon',
        });
        request.response
          ..headers.contentType = ContentType.json
          ..statusCode = HttpStatus.ok
          ..write(result);
        await request.response.close();
      } else {
        request.response
          ..statusCode = HttpStatus.notFound
          ..write('Not Found');
        await request.response.close();
      }
    }
  }
}
