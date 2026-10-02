package com.techhamara.bolt.compiler

import com.techhamara.bolt.packager.AixPackager
import com.techhamara.bolt.parser.ConfigParser
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Executes Android D8 DEX compiler with incremental bytecode caching.
 * Compiles Java/Kotlin bytecode into classes.dex for AIX packaging and Live Hot-Reloading.
 */
object D8Runner {

    fun compile(
        projectDir: File,
        buildDir: File,
        binDir: File,
        config: ConfigParser.BoltConfig,
        libsDir: File,
        androidJar: File,
        logger: (String) -> Unit,
        providedLibs: List<File> = emptyList()
    ): File? {
        val classFiles = binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        if (classFiles.isEmpty()) {
            logger("debug No .class files found for D8 DEX compilation.")
            return null
        }

        val dotBolt = File(projectDir, ".bolt").apply { mkdirs() }
        val cacheFile = File(dotBolt, "dex_cache.hash")
        val dexDir = File(buildDir, "dex").apply { mkdirs() }
        val targetDex = File(dexDir, "classes.dex")

        // 1. Calculate MD5 hash across all .class files + config
        val currentHash = calculateBytecodeHash(classFiles, config)

        // 2. Incremental Cache Check
        if (targetDex.exists() && cacheFile.exists()) {
            val cachedHash = cacheFile.readText().trim()
            if (cachedHash == currentHash) {
                logger("- D8 DEX generated successfully.")
                return targetDex
            }
        }

        // 3. Locate D8 compiler
        val toolsDir = File(libsDir, "tools")
        val d8Jar = LibLocator.findLib(libsDir, "d8.jar", "r8.jar")
            ?: File(toolsDir, "d8.jar").takeIf { it.exists() }
            ?: File(BoltLocator.getBoltHome(), "libs/tools/d8.jar").takeIf { it.exists() }
            ?: File(System.getProperty("user.home"), ".bolt/libs/tools/d8.jar").takeIf { it.exists() }

        if (d8Jar == null || !d8Jar.exists()) {
            logger("warning d8.jar not found; skipping DEX compilation.")
            return null
        }

        logger("- Running D8 DEX compilation...")

        // 4. Create temporary input JAR containing only .class files
        val d8InJar = File(buildDir, "d8_in.jar")
        if (d8InJar.exists()) d8InJar.delete()

        ZipOutputStream(FileOutputStream(d8InJar)).use { zos ->
            for (file in classFiles) {
                val relPath = file.relativeTo(binDir).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(relPath))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        // 5. Construct D8 arguments
        val minApi = if (config.minSdk >= 14) config.minSdk else 21
        val d8Args = mutableListOf(
            "--output", dexDir.absolutePath,
            "--min-api", minApi.toString(),
            "--lib", androidJar.absolutePath
        )
        for (lib in providedLibs) {
            if (lib.exists() && !d8Args.contains(lib.absolutePath)) {
                d8Args.add("--lib")
                d8Args.add(lib.absolutePath)
            }
        }

        // Check for desugaring config if enabled
        if (config.coreLibraryDesugaring || config.desugarDex) {
            val desugarJson = LibLocator.findLib(libsDir, "desugar_jdk_libs_configuration.json")
                ?: File(toolsDir, "desugar_jdk_libs_configuration.json").takeIf { it.exists() }
            if (desugarJson != null && desugarJson.exists()) {
                d8Args.add("--desugared-lib")
                d8Args.add(desugarJson.absolutePath)
            }
        }

        d8Args.add(d8InJar.absolutePath)

        // 6. Run D8 in-process first for maximum build performance
        var d8Success = false
        var d8ErrorOutput = ""

        try {
            val d8Loader = LibLocator.createClassLoader(Thread.currentThread().contextClassLoader, d8Jar)
            val cmdClass = d8Loader.loadClass("com.android.tools.r8.D8Command")
            val originClass = d8Loader.loadClass("com.android.tools.r8.origin.Origin")
            val unknownOrigin = originClass.getMethod("unknown").invoke(null)
            val parseMethod = cmdClass.getMethod("parse", Array<String>::class.java, originClass)
            val builder = parseMethod.invoke(null, d8Args.toTypedArray(), unknownOrigin)
            val buildMethod = builder.javaClass.getMethod("build")
            val command = buildMethod.invoke(builder)
            val d8Class = d8Loader.loadClass("com.android.tools.r8.D8")
            val runMethod = d8Class.getMethod("run", cmdClass)

            val origOut = System.out
            val origErr = System.err
            val byteOut = java.io.ByteArrayOutputStream()
            val dummyStream = java.io.PrintStream(byteOut)
            try {
                System.setOut(dummyStream)
                System.setErr(dummyStream)
                runMethod.invoke(null, command)
                d8Success = true
            } finally {
                System.setOut(origOut)
                System.setErr(origErr)
            }
        } catch (_: Throwable) {
            d8Success = false
        }

        // Fallback to external process if in-process D8 failed
        if (!d8Success && (!targetDex.exists() || d8InJar.exists())) {
            try {
                val javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
                val cmd = mutableListOf(javaExe, "-cp", d8Jar.absolutePath, "com.android.tools.r8.D8")
                cmd.addAll(d8Args)
                val pb = ProcessBuilder(cmd)
                pb.directory(projectDir)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText().trim()
                val exitCode = proc.waitFor()
                d8Success = (exitCode == 0)
                if (!d8Success) d8ErrorOutput = output
            } catch (e: Exception) {
                d8ErrorOutput = e.message ?: ""
            }
        }

        d8InJar.delete()

        if (d8Success && targetDex.exists()) {
            cacheFile.writeText(currentHash)
            logger("- D8 DEX generated successfully.")
            return targetDex
        } else {
            logger("error D8 compilation failed: $d8ErrorOutput")
            return null
        }
    }

    private fun calculateBytecodeHash(classFiles: List<File>, config: ConfigParser.BoltConfig): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update("minSdk:${config.minSdk};desugar:${config.desugarDex}".toByteArray())
        for (file in classFiles.sortedBy { it.absolutePath }) {
            digest.update(file.name.toByteArray())
            digest.update(file.length().toString().toByteArray())
            digest.update(file.lastModified().toString().toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
