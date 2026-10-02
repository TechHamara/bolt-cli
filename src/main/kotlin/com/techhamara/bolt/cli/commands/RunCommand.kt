package com.techhamara.bolt.cli.commands

import com.google.gson.JsonParser
import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.LibLocator
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.*
import java.util.concurrent.CopyOnWriteArrayList

object RunCommand {

    private const val DEFAULT_PORT = 9000
    private const val UDP_DISCOVERY_PORT = 9001
    @Volatile
    private var isRunning = true
    private var udpSocket: DatagramSocket? = null

    fun execute(args: List<String>, logger: Logger): Int {
        isRunning = true
        var port = DEFAULT_PORT
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-p", "--port" -> {
                    if (i + 1 < args.size) { port = args[++i].toIntOrNull() ?: DEFAULT_PORT }
                }
            }
            i++
        }

        val projectDir = File(".").canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")
        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        val clients = CopyOnWriteArrayList<WebSocket>()
        val libsDir = LibLocator.findLibsDir(projectDir)
        val compiler = BoltCompiler(libsDir) { logger.dbg(it) }

        // 1. Start WebSocket Server
        val server = object : WebSocketServer(InetSocketAddress("0.0.0.0", port)) {
            override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
                if (conn != null) {
                    clients.add(conn)
                    logger.info("New companion connected from ${conn.remoteSocketAddress}")
                    sendLatestDex(projectDir, listOf(conn), logger)
                }
            }

            override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
                if (conn != null) {
                    clients.remove(conn)
                    logger.info("Companion disconnected.")
                }
            }

            override fun onMessage(conn: WebSocket?, message: String?) {
                if (message != null) {
                    try {
                        val json = JsonParser.parseString(message).asJsonObject
                        if (json.get("type")?.asString == "telemetry") {
                            val used = json.get("usedMemoryMB")?.asInt ?: 0
                            val free = json.get("freeMemoryMB")?.asInt ?: 0
                            logger.info("[TELEMETRY] Memory: ${used}MB Used / ${free}MB Free")
                        } else {
                            logger.dbg("Message from companion: $message")
                        }
                    } catch (_: Exception) {
                        logger.dbg("Message from companion: $message")
                    }
                }
            }

            override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {}

            override fun onError(conn: WebSocket?, ex: Exception?) {
                logger.err("WebSocket error: ${ex?.message}")
            }

            override fun onStart() {
                val localIp = getLocalIpAddress()
                logger.info("Live Companion Server started:")
                logger.info("  Local:   ws://127.0.0.1:$port")
                if (localIp != "127.0.0.1") {
                    logger.info("  Network: ws://$localIp:$port")
                }
                logger.info("Press [Ctrl + C] or type 'q' + Enter to stop live test session.")
            }
        }

        server.isReuseAddr = true
        server.start()

        // Register JVM Shutdown Hook for clean resource release on Ctrl + C
        val shutdownHook = Thread {
            try {
                udpSocket?.close()
                server.stop(500)
            } catch (_: Exception) {}
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        // 2. Start UDP Auto-Discovery
        startUdpDiscovery(port, logger)

        // 3. Initial Build
        triggerBuildAndPush(projectDir, compiler, clients, logger)

        // Background reader for 'q' / 'exit'
        val consoleReaderThread = Thread {
            try {
                val reader = System.`in`.bufferedReader()
                while (isRunning) {
                    val line = reader.readLine() ?: break
                    if (line.trim().lowercase() in listOf("q", "quit", "exit", "stop")) {
                        logger.info("Stopping Bolt live test session...")
                        isRunning = false
                        break
                    }
                }
            } catch (_: Exception) {}
        }
        consoleReaderThread.isDaemon = true
        consoleReaderThread.start()

        // 4. File Watcher on src/ (blocking loop until isRunning is false)
        startFileWatcher(projectDir, compiler, clients, logger)

        // Cleanup
        try {
            udpSocket?.close()
            server.stop(1000)
            logger.info("Live Companion Server stopped.")
        } catch (_: Exception) {}

        return 0
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun triggerBuildAndPush(projectDir: File, compiler: BoltCompiler, clients: List<WebSocket>, logger: Logger) {
        logger.startTask("Hot-reloading Build")
        val result = compiler.build(projectDir, generateDex = true)
        if (result.success) {
            logger.stopTask(true)
            logger.info("Build successful, pushing to companion(s)...")
            sendLatestDex(projectDir, clients, logger)
        } else {
            logger.stopTask(false)
            logger.err("Build failed.")
        }
    }

    private fun sendLatestDex(projectDir: File, clients: List<WebSocket>, logger: Logger) {
        if (clients.isEmpty()) return

        val dexDirs = listOf(
            File(projectDir, ".bolt/dex"),
            File(projectDir, ".bolt/bin"),
            File(projectDir, "build/dex"),
            File(projectDir, "build/bin")
        )
        val dexFiles = dexDirs.flatMap { if (it.exists()) it.walkTopDown().filter { f -> f.isFile && f.extension == "dex" }.toList() else emptyList() }
        val primaryDex = dexFiles.firstOrNull { it.name == "classes.dex" } ?: dexFiles.firstOrNull()
        if (primaryDex == null) {
            logger.warn("No classes.dex found in .bolt/dex or build/dex to push.")
            return
        }

        val dexBytes = primaryDex.readBytes()
        for (client in clients) {
            try {
                client.send(dexBytes)
                Thread.sleep(100)
                client.send("RELOAD:AnyComponent")
            } catch (e: Exception) {
                logger.err("Failed to send DEX to companion: ${e.message}")
            }
        }
        logger.info("Sent classes.dex (${dexBytes.size} bytes) and RELOAD signal to ${clients.size} companion(s).")
    }

    private fun startUdpDiscovery(wsPort: Int, logger: Logger) {
        Thread {
            try {
                val socket = DatagramSocket(UDP_DISCOVERY_PORT, InetAddress.getByName("0.0.0.0"))
                udpSocket = socket
                socket.broadcast = true
                logger.info("UDP Auto-discovery listening on port $UDP_DISCOVERY_PORT")

                val buffer = ByteArray(512)
                while (!socket.isClosed && isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == "BOLT_DISCOVER") {
                        val response = "BOLT_SERVER:$wsPort".toByteArray()
                        val respPacket = DatagramPacket(response, response.size, packet.address, packet.port)
                        socket.send(respPacket)
                        logger.dbg("Answered discovery broadcast from ${packet.address.hostAddress}")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    logger.warn("UDP Auto-discovery warning: ${e.message}")
                }
            }
        }.start()
    }

    private fun startFileWatcher(projectDir: File, compiler: BoltCompiler, clients: List<WebSocket>, logger: Logger) {
        val srcDir = File(projectDir, "src")
        logger.info("Watching for file changes in src/ directory...")

        var lastModifiedMap = mutableMapOf<String, Long>()
        for (f in srcDir.walkTopDown().filter { it.isFile }) {
            lastModifiedMap[f.absolutePath] = f.lastModified()
        }

        while (isRunning) {
            Thread.sleep(800)
            if (!isRunning) break
            var changed = false
            val currentFiles = srcDir.walkTopDown().filter { it.isFile }.toList()

            for (f in currentFiles) {
                val oldTime = lastModifiedMap[f.absolutePath]
                if (oldTime == null || oldTime != f.lastModified()) {
                    if (f.extension in listOf("java", "kt", "aidl", "cpp")) {
                        changed = true
                        logger.info("File changed: ${f.name}")
                        break
                    }
                }
            }

            if (changed) {
                lastModifiedMap.clear()
                for (f in currentFiles) {
                    lastModifiedMap[f.absolutePath] = f.lastModified()
                }
                triggerBuildAndPush(projectDir, compiler, clients, logger)
            }
        }
    }
}
