package com.techhamara.bolt.cli.commands

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.green
import com.techhamara.bolt.cli.red
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.LibLocator
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import kotlin.system.exitProcess

object DaemonCommand {

    private const val DEFAULT_PORT = 19090

    fun execute(args: List<String>, logger: Logger): Int {
        var port = DEFAULT_PORT
        var sub: String? = null

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-p", "--port" -> {
                    if (i + 1 < args.size) { port = args[++i].toIntOrNull() ?: DEFAULT_PORT }
                }
                else -> {
                    if (!arg.startsWith("-") && sub == null) {
                        sub = arg
                    }
                }
            }
            i++
        }

        return when (sub?.lowercase()) {
            "start" -> startDaemon(port, logger)
            "stop" -> stopDaemon(port, logger)
            "status" -> checkStatus(port, logger)
            "server" -> runServer(port)
            else -> checkStatus(port, logger)
        }
    }

    private fun isRunning(port: Int): Boolean {
        return try {
            val url = URL("http://127.0.0.1:$port/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 800
            conn.readTimeout = 800
            val code = conn.responseCode
            val text = if (code == 200) conn.inputStream.bufferedReader().readText() else ""
            code == 200 && text.contains("running")
        } catch (_: Exception) {
            false
        }
    }

    private fun checkStatus(port: Int, logger: Logger): Int {
        if (isRunning(port)) {
            logger.info("Bolt Daemon Status: ${"RUNNING".green()} (Port: $port)")
        } else {
            logger.info("Bolt Daemon Status: ${"STOPPED".red()}. Run `bolt daemon start` to start it.")
        }
        return 0
    }

    private fun startDaemon(port: Int, logger: Logger): Int {
        if (isRunning(port)) {
            logger.info("Bolt Daemon is already running on port $port")
            return 0
        }

        logger.info("Starting Bolt Daemon on port $port...")

        val javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val jarPath = DaemonCommand::class.java.protectionDomain.codeSource.location.toURI().path
        val jarFile = File(jarPath)

        val pb = if (jarFile.isFile && jarFile.name.endsWith(".jar")) {
            ProcessBuilder(javaExe, "-jar", jarFile.absolutePath, "daemon", "server", "--port", "$port")
        } else {
            // Development mode or classpath
            ProcessBuilder(javaExe, "-cp", System.getProperty("java.class.path"), "com.techhamara.bolt.cli.Main", "daemon", "server", "--port", "$port")
        }

        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val process = pb.start()

        for (attempt in 1..10) {
            Thread.sleep(300)
            if (isRunning(port)) {
                logger.info("  ✓ Bolt Daemon started successfully (PID: ${process.pid()})")
                return 0
            }
        }

        logger.warn("Failed to verify Bolt Daemon startup.")
        return 1
    }

    private fun stopDaemon(port: Int, logger: Logger): Int {
        return try {
            val url = URL("http://127.0.0.1:$port/stop")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            if (conn.responseCode == 200) {
                logger.info("  ✓ Bolt Daemon stopped successfully.")
                0
            } else {
                logger.info("Bolt Daemon is not running.")
                0
            }
        } catch (_: Exception) {
            logger.info("Bolt Daemon is not running.")
            0
        }
    }

    private fun runServer(port: Int): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)

        server.createContext("/status") { exchange ->
            val response = "running"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server.createContext("/stop") { exchange ->
            val response = "stopped"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            Thread {
                Thread.sleep(200)
                server.stop(0)
                exitProcess(0)
            }.start()
        }

        server.createContext("/build") { exchange ->
            val query = exchange.requestURI.query ?: ""
            val params = query.split("&").associate {
                val pair = it.split("=")
                pair[0] to (pair.getOrNull(1) ?: "")
            }

            val projectPath = params["project"] ?: "."
            val projectDir = File(projectPath).canonicalFile
            val libsDir = LibLocator.findLibsDir(projectDir)
            val compiler = BoltCompiler(libsDir)

            val enableR8 = params["r8"] == "true"
            val enableProguard = params["proguard"] == "true"
            val enableDeannotate = params["deannotate"] == "true"
            val generateDex = params["dex"] == "true"
            val keepManifest = params["keepManifest"] == "true"
            val generateBlocks = params["blocks"] == "true"

            val result = compiler.build(
                projectDir = projectDir,
                enableR8 = enableR8,
                enableProguard = enableProguard,
                enableDeannotate = enableDeannotate,
                generateDex = generateDex,
                keepManifest = keepManifest,
                generateBlocks = generateBlocks
            )
            val response = if (result.success) "SUCCESS:${result.aixFile?.name}" else "FAILED"
            exchange.sendResponseHeaders(if (result.success) 200 else 500, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server.start()
        // Run until stopped
        while (true) {
            Thread.sleep(1000)
        }
    }
}
