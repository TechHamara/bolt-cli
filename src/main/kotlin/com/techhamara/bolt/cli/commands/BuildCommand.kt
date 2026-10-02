package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.*
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.BoltLocator
import com.techhamara.bolt.compiler.LibLocator
import com.techhamara.bolt.parser.ConfigParser
import java.io.File

object BuildCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        var projectPath = "."
        var enableR8 = false
        var enableProguard = false
        var enableDeannotate = false
        var generateDex = false
        var keepManifest = false
        var generateBlocks = false
        var noDaemon = false
        var forceSync = false
        var customLibsPath: String? = null

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-s", "--r8" -> enableR8 = true
                "-r", "--proguard" -> enableProguard = true
                "-b", "--blocks" -> generateBlocks = true
                "-o", "--optimize" -> {
                    enableR8 = true
                }
                "-dx", "-x", "--dex" -> generateDex = true
                "-m", "--keep-manifest" -> keepManifest = true
                "--no-daemon" -> noDaemon = true
                "-y", "--sync" -> forceSync = true
                "--deannotate" -> enableDeannotate = true
                "--libs" -> {
                    if (i + 1 < args.size) { customLibsPath = args[++i] }
                }
                else -> {
                    if (!arg.startsWith("-")) {
                        projectPath = arg
                    }
                }
            }
            i++
        }

        val projectDir = File(projectPath).canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")

        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        // Synchronize ProGuard flag in bolt.yml with -r/--proguard flag
        updateBoltYmlProguard(ymlFile, enableProguard)

        // 1. Remove legacy build/ folder if it exists in the project root
        val legacyBuild = File(projectDir, "build")
        if (legacyBuild.exists()) {
            try { legacyBuild.deleteRecursively() } catch (_: Exception) {}
        }

        // 2. Set log file in .bolt/BuildLog.txt with system & bolt info header at the top (latest build only)
        val dotBolt = File(projectDir, ".bolt").apply { mkdirs() }
        val logFile = File(dotBolt, "BuildLog.txt")
        val legacyLog = File(dotBolt, "build.log")

        if (legacyLog.exists()) {
            try { legacyLog.delete() } catch (_: Exception) {}
        }

        val timeFormatter = java.text.SimpleDateFormat("h.mma/dd.MM.yyyy", java.util.Locale.ENGLISH)
        val buildTimeStr = timeFormatter.format(java.util.Date()).lowercase()
        val osVersionStr = com.techhamara.bolt.compiler.SystemInfo.getDetailedOsVersion()
        val boltHomePath = BoltLocator.getBoltHome().canonicalPath
        val javaOptions = com.techhamara.bolt.compiler.SystemInfo.getJavaOptions()

        val header = buildString {
            if (!javaOptions.isNullOrBlank()) {
                appendLine("Picked up _JAVA_OPTIONS: $javaOptions")
            }
            appendLine("Bolt is initialized.")
            appendLine("BOLT Version: ${Main.VERSION}")
            appendLine("Build Time: $buildTimeStr")
            appendLine("System/Device: ${com.techhamara.bolt.compiler.SystemInfo.getDeviceName()}")
            appendLine("Available Processors: ${com.techhamara.bolt.compiler.SystemInfo.getAvailableProcessors()}")
            appendLine("RAM: ${com.techhamara.bolt.compiler.SystemInfo.getRamInfo()}")
            appendLine("Storage: ${com.techhamara.bolt.compiler.SystemInfo.getStorageInfo(projectDir)}")
            appendLine("JRE Version: ${System.getProperty("java.version") ?: "unknown"}")
            appendLine("JRE Specification: ${System.getProperty("java.specification.version") ?: "unknown"}")
            appendLine("JRE Home: ${System.getProperty("java.home") ?: "unknown"}")
            appendLine("OS Name: ${System.getProperty("os.name") ?: "unknown"}")
            appendLine("OS Version: $osVersionStr")
            appendLine("Architecture: ${System.getProperty("os.arch") ?: "unknown"}")
            appendLine("Username: ${System.getProperty("user.name") ?: "unknown"}")
            appendLine("User Home: ${System.getProperty("user.home") ?: "unknown"}")
            appendLine("Working Directory: ${projectDir.canonicalPath}")
            appendLine("PROJECT_DIR: ${projectDir.canonicalPath}")
            appendLine("bolt.yml is found at: ${ymlFile.canonicalPath}")
            appendLine("BOLT_HOME: $boltHomePath")
            appendLine("_________________________________")
            appendLine()
        }

        // Overwrite BuildLog.txt to record only the latest build log
        logFile.writeText(header, Charsets.UTF_8)
        logger.setOutputFile(logFile)

        if (forceSync) {
            val syncRes = SyncCommand.execute(listOf(projectPath), logger)
            if (syncRes != 0) return syncRes
        }

        // Check if Bolt Daemon is active for sub-4s accelerated builds
        if (!noDaemon && customLibsPath == null) {
            var daemonAvailable = false
            try {
                val statusUrl = java.net.URL("http://127.0.0.1:19090/status")
                val conn = statusUrl.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 300
                conn.readTimeout = 300
                daemonAvailable = conn.responseCode == 200 && conn.inputStream.bufferedReader().readText().contains("running")
            } catch (_: Exception) {}

            if (daemonAvailable) {
                logger.info("⚡ Delegating build to running Bolt Daemon (127.0.0.1:19090)...")
                val startD = System.currentTimeMillis()
                try {
                    val encodedProj = java.net.URLEncoder.encode(projectDir.absolutePath, "UTF-8")
                    val buildUrl = java.net.URL("http://127.0.0.1:19090/build?project=$encodedProj" +
                        "&r8=$enableR8&proguard=$enableProguard&deannotate=$enableDeannotate&dex=$generateDex&keepManifest=$keepManifest&blocks=$generateBlocks")
                    val conn = buildUrl.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 120000
                    val code = conn.responseCode
                    val resp = (if (code == 200) conn.inputStream else conn.errorStream).bufferedReader().readText()
                    val elapsed = System.currentTimeMillis() - startD
                    val sec = elapsed / 1000
                    val ms = elapsed % 1000

                    if (code == 200 && resp.startsWith("SUCCESS")) {
                        val aixName = resp.substringAfter("SUCCESS:").trim()
                        val aixFile = File(projectDir, "out/$aixName")
                        val sizeStr = if (aixFile.exists()) String.format("%.1f KB", aixFile.length() / 1024.0) else ""
                        val sizePart = if (sizeStr.isNotBlank()) " ($sizeStr)" else ""
                        val relPath = ".${File.separator}out${File.separator}$aixName"

                        logFile.appendText("\nBUILD SUCCESSFUL in ${sec}s ${ms}ms$sizePart\n$relPath$sizePart\n")
                        println()
                        println("BUILD SUCCESSFUL".green())
                        println("${sec}s ${ms}ms$sizePart".grey())
                        println("$relPath$sizePart".cyan())
                        println()
                        return 0
                    } else {
                        logger.warn("Daemon build returned: $resp; falling back to local compilation...")
                    }
                } catch (e: Exception) {
                    logger.warn("Daemon build error (${e.message}); falling back to local compilation...")
                }
            }
        }

        if (customLibsPath != null) {
            LibLocator.setExplicitLibsDir(File(customLibsPath).canonicalFile)
        }

        val libsDir = LibLocator.findLibsDir(projectDir)
        val compiler = BoltCompiler(libsDir) { chunk ->
            logger.parseAndLog(chunk)
        }

        val result = compiler.build(
            projectDir = projectDir,
            enableR8 = enableR8,
            enableProguard = enableProguard,
            enableDeannotate = enableDeannotate,
            generateDex = generateDex,
            keepManifest = keepManifest,
            generateBlocks = generateBlocks
        )

        val sec = result.durationMs / 1000
        val ms = result.durationMs % 1000

        if (!result.success) {
            logFile.appendText("\nBUILD FAILED in ${sec}s ${ms}ms\n")
            println()
            println("BUILD FAILED".red())
            println("${sec}s ${ms}ms".grey())
            println()
            return 1
        }

        val aixFile = result.aixFile
        val sizeStr = if (aixFile != null && aixFile.exists()) {
            val kb = aixFile.length() / 1024.0
            String.format("%.1f KB", kb)
        } else {
            ""
        }
        val sizePart = if (sizeStr.isNotBlank()) " ($sizeStr)" else ""
        val relPath = ".${File.separator}out${File.separator}${aixFile?.name ?: ""}"

        logFile.appendText("\nBUILD SUCCESSFUL in ${sec}s ${ms}ms$sizePart\n$relPath$sizePart\n")

        println()
        println("BUILD SUCCESSFUL".green())
        println("${sec}s ${ms}ms$sizePart".grey())
        if (aixFile != null && aixFile.exists()) {
            println("$relPath$sizePart".cyan())
        }
        println()
        return 0
    }

    private fun updateBoltYmlProguard(ymlFile: File, enableProguard: Boolean) {
        if (!ymlFile.exists()) return
        try {
            var content = ymlFile.readText(Charsets.UTF_8)
            val proguardRegex = Regex("(?m)^\\s*#?\\s*proguard\\s*:.*$")
            if (proguardRegex.containsMatchIn(content)) {
                content = proguardRegex.replace(content, "proguard: $enableProguard")
            } else {
                content = content.trimEnd() + "\n\n# If enabled, extension will be optimized using ProGuard.\nproguard: $enableProguard\n"
            }
            ymlFile.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {}
    }
}
