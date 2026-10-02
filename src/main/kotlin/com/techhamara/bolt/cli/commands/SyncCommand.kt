package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.*
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.LibLocator
import com.techhamara.bolt.compiler.NdkRunner
import com.techhamara.bolt.parser.ConfigParser
import com.techhamara.bolt.resolver.MavenResolver
import java.io.File

object SyncCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        val startTime = System.currentTimeMillis()
        var projectPath = "."
        var force = false
        var onlyDevDeps = false
        var onlyProjectDeps = false
        var syncNdk = false
        var customLibsPath: String? = null

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-f", "--force" -> force = true
                "-d", "--dev-deps", "dev", "dev-deps" -> onlyDevDeps = true
                "-p", "--project-deps" -> onlyProjectDeps = true
                "ndk", "--ndk" -> syncNdk = true
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
        if (customLibsPath != null) {
            LibLocator.setExplicitLibsDir(File(customLibsPath).canonicalFile)
        }

        val libsDir = LibLocator.findLibsDir(projectDir)

        if (syncNdk) {
            logger.startTask("Checking Mini-NDK")
            val ndkDir = NdkRunner.findNdkDirectory(null, libsDir)
            if (ndkDir != null && ndkDir.exists()) {
                val propFile = File(ndkDir, "source.properties")
                var releaseInfo = ""
                if (propFile.exists()) {
                    val relLine = propFile.readLines().firstOrNull { it.startsWith("Pkg.ReleaseName") }
                    if (relLine != null) {
                        releaseInfo = " (${relLine.substringAfter("=").trim()})"
                    }
                }
                logger.stopTask(true)
                logger.info("Mini-NDK detected at: ${ndkDir.absolutePath}$releaseInfo")
                println("\n> " + "SYNC SUCCESSFUL".green() + " (Mini-NDK is ready to compile native C/C++ code)".grey())
                return 0
            } else {
                logger.stopTask(false)
                logger.warn("Mini-NDK was not found in ~/.bolt/libs/ndk or ANDROID_NDK_HOME.")
                logger.info("To set up Mini-NDK:")
                logger.info("  1. Download the Bolt Mini-NDK bundle.")
                logger.info("  2. Extract it into: ${File(System.getProperty("user.home"), ".bolt/libs/ndk").absolutePath}")
                println("\n> " + "SYNC FAILED".red() + " (Mini-NDK missing)".grey())
                return 1
            }
        }

        if (onlyDevDeps) {
            logger.startTask("Syncing developer tools & desugaring libraries")
            val ok = syncDevDependencies(libsDir, logger)
            logger.stopTask(ok)
            val elapsed = System.currentTimeMillis() - startTime
            val sec = elapsed / 1000
            val ms = elapsed % 1000
            if (ok) {
                println("\n> " + "SYNC SUCCESSFUL".green() + " in ${sec}s ${ms}ms (Dev dependencies updated from Google Maven)".grey())
                return 0
            } else {
                println("\n> " + "SYNC WARNING".yellow() + " in ${sec}s ${ms}ms (Some dev dependencies could not be fetched)".grey())
                return 0
            }
        }

        logger.startTask("Syncing dependencies")

        var syncedRemoteCount = 0
        val ymlFile = File(projectDir, "bolt.yml")

        if (ymlFile.exists()) {
            try {
                val config = ConfigParser().parse(ymlFile)
                val depsDir = File(projectDir, "deps").apply { mkdirs() }
                val srcDir = File(projectDir, "src")
                val isKotlin = srcDir.exists() && srcDir.walkTopDown().any { it.isFile && it.extension == "kt" }

                val remoteDeps = (config.dependencies + config.providedDependencies).filter { it.contains(":") }
                if (remoteDeps.isNotEmpty()) {
                    val resolver = MavenResolver { logger.dbg(it) }
                    val resolved = resolver.resolve(remoteDeps, config.repositories, depsDir)
                    if (resolved) {
                        syncedRemoteCount = remoteDeps.size
                    }
                }

                val localDeps = depsDir.walkTopDown().filter { it.isFile && (it.extension == "jar" || it.extension == "aar") }.toList()
                if (localDeps.isNotEmpty()) {
                    logger.info("  ✓ Found ${localDeps.size} local dependency file(s) in deps/")
                }

                if (isKotlin) {
                    val kotlinStdlib = LibLocator.findLib(libsDir, "kotlin-stdlib.jar") ?: File(libsDir, "tools/kotlin-stdlib.jar")
                    if (kotlinStdlib.exists()) {
                        logger.info("  ✓ Linked Kotlin lib...")
                    }
                }

                // Update IDE classpath files (.classpath and .vscode/settings.json)
                updateIdeClasspaths(projectDir, depsDir, libsDir, isKotlin, config)
                logger.info("  ✓ Updated IDE configuration")
            } catch (e: Exception) {
                logger.err("Error syncing project dependencies: ${e.message}")
                logger.stopTask(false)
                logFinalLine(false, startTime, 0, false, logger)
                return 1
            }
        }

        logger.stopTask(true)
        val isKotlinProject = File(projectDir, "src").walkTopDown().any { it.isFile && it.extension == "kt" }
        logFinalLine(true, startTime, syncedRemoteCount, isKotlinProject, logger)
        return 0
    }

    private fun syncDevDependencies(libsDir: File, logger: Logger): Boolean {
        logger.info("Fetching desugar_jdk_libs:2.1.5 and configuration from Google Maven...")
        val targetToolsDir = File(libsDir, "tools").apply { mkdirs() }
        val homeToolsDir = File(System.getProperty("user.home"), ".bolt/libs/tools").apply { mkdirs() }

        val downloads = listOf(
            "https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/2.1.5/desugar_jdk_libs-2.1.5.jar" to "desugar_jdk_libs-2.1.5.jar",
            "https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs_configuration/2.1.5/desugar_jdk_libs_configuration-2.1.5.jar" to "desugar_jdk_libs_configuration-2.1.5.jar"
        )

        var success = true
        for ((urlStr, fileName) in downloads) {
            try {
                val targetFile = File(targetToolsDir, fileName)
                val homeFile = File(homeToolsDir, fileName)
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("  ✓ $fileName is already up to date")
                    continue
                }
                logger.info("  ⬇ Downloading $fileName...")
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.readBytes()
                    targetFile.writeBytes(bytes)
                    homeFile.writeBytes(bytes)
                    logger.info("  ✓ Successfully saved $fileName (${bytes.size / 1024} KB)")
                } else {
                    logger.warn("  ✗ Failed to download $fileName (HTTP ${conn.responseCode})")
                    success = false
                }
            } catch (e: Exception) {
                logger.warn("  ✗ Error downloading $fileName: ${e.message}")
                success = false
            }
        }
        return success
    }

    private fun logFinalLine(success: Boolean, startTime: Long, count: Int, isKotlin: Boolean, logger: Logger) {
        val elapsed = System.currentTimeMillis() - startTime
        val sec = elapsed / 1000
        val ms = elapsed % 1000
        val statusText = if (success) "SYNC SUCCESSFUL".green() else "SYNC FAILED".red()
        val countText = if (success) {
            if (count > 0) {
                " ($count ${if (count == 1) "library" else "libraries"} synced, classpath updated)".grey()
            } else {
                " (Bolt toolchain updated${if (isKotlin) " & Kotlin stdlib" else ""})".grey()
            }
        } else ""
        println("\n> $statusText in ${sec}s ${ms}ms".grey() + countText)
    }

    private fun updateIdeClasspaths(
        projectDir: File,
        depsDir: File,
        libsDir: File,
        isKotlin: Boolean,
        config: ConfigParser.BoltConfig
    ) {
        val classpathEntries = mutableListOf<File>()

        // 1. Android Platform & Core App Inventor Stubs
        val androidJar = if (config.compileSdk > 0) {
            LibLocator.findLib(libsDir, "android-${config.compileSdk}.jar", "android.jar")
                ?: LibLocator.findLib(libsDir, "android.jar")
        } else {
            LibLocator.findLib(libsDir, "android.jar")
        }
        if (androidJar != null && androidJar.exists()) classpathEntries.add(androidJar)

        val stubsJar = LibLocator.findLib(libsDir, "appinventor-stubs-v3.jar")
        if (stubsJar != null && stubsJar.exists()) classpathEntries.add(stubsJar)

        val annotationsJar = LibLocator.findLib(libsDir, "annotations.jar")
        if (annotationsJar != null && annotationsJar.exists()) classpathEntries.add(annotationsJar)

        val kawaJar = LibLocator.findLib(libsDir, "kawa.jar", "kawa-1.11-modified.jar")
        if (kawaJar != null && kawaJar.exists()) classpathEntries.add(kawaJar)

        val runtimeJar = LibLocator.findLib(libsDir, "AndroidRuntime.jar")
        if (runtimeJar != null && runtimeJar.exists()) classpathEntries.add(runtimeJar)

        // 2. Kotlin Standard Library
        if (isKotlin) {
            val kotlinStdlib = LibLocator.findLib(libsDir, "kotlin-stdlib.jar")
            if (kotlinStdlib != null && kotlinStdlib.exists()) classpathEntries.add(kotlinStdlib)
        }

        // 3. App Inventor & AndroidX Provided Runtime Libraries from libs/
        val androidXOrRuntimePrefixes = setOf(
            "androidruntime", "androidx", "annotation", "appcompat", "asynclayoutinflater",
            "cardview", "collection", "constraintlayout", "coordinatorlayout", "core",
            "cursoradapter", "customview", "documentfile", "drawerlayout", "dynamicanimation",
            "fragment", "interpolator", "legacy-support", "lifecycle", "loader",
            "localbroadcastmanager", "print", "recyclerview", "slidingpanelayout",
            "swiperefreshlayout", "vectordrawable", "versionedparcelable", "viewpager"
        )
        val toolchainPrefixes = setOf(
            "ecj", "r8", "d8", "dx", "bundletool", "apksigner", "asm-", "kotlin-compiler",
            "jarjar", "strguard", "junit", "trove4j", "desugar_jdk_libs", "annotationprocessors",
            "annotations-processor"
        )

        val libsDirFiles = libsDir.listFiles() ?: emptyArray()
        for (f in libsDirFiles) {
            if (f.isFile && f.extension.equals("jar", ignoreCase = true)) {
                val lower = f.name.lowercase()
                val isTool = toolchainPrefixes.any { lower.startsWith(it) }
                val isRuntime = androidXOrRuntimePrefixes.any { lower.startsWith(it) }
                if (!isTool && isRuntime && !classpathEntries.contains(f)) {
                    classpathEntries.add(f)
                }
            }
        }

        // 4. Local User Dependencies in deps/
        val userJars = depsDir.walkTopDown().filter { it.isFile && (it.extension == "jar" || it.extension == "aar") }.toList()
        for (f in userJars) {
            if (!classpathEntries.contains(f)) {
                classpathEntries.add(f)
            }
        }

        // 5. Generate .classpath file
        val classpathFile = File(projectDir, ".classpath")
        val classpathEntriesXml = classpathEntries.joinToString("\n") { file ->
            val pathStr = file.absolutePath.replace('\\', '/')
            "    <classpathentry kind=\"lib\" path=\"$pathStr\"/>"
        }
        val classpathXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <classpath>
                <classpathentry kind="src" path="src/" including="**/*.java|**/*.kt" />
                <classpathentry kind="output" path=".bolt/bin" />
            $classpathEntriesXml
            </classpath>
        """.trimIndent()
        classpathFile.writeText(classpathXml, Charsets.UTF_8)

        // 6. Generate/Update .vscode/settings.json
        val vscodeDir = File(projectDir, ".vscode").apply { mkdirs() }
        val vscodeSettings = File(vscodeDir, "settings.json")
        val referencedLibsJson = (listOf("\"deps/**/*.jar\"", "\"deps/**/*.aar\"") +
            classpathEntries.map { "\"${it.absolutePath.replace('\\', '/')}\"" }).joinToString(",\n    ")

        vscodeSettings.writeText(
            """
            {
              "java.project.sourcePaths": ["src"],
              "java.project.outputPath": ".bolt/bin",
              "java.project.referencedLibraries": [
                $referencedLibsJson
              ]
            }
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}
