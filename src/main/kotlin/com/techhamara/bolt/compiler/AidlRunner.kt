package com.techhamara.bolt.compiler

import java.io.File

/**
 * Cross-platform AIDL runner supporting Windows, macOS, Linux, and Android Termux.
 */
object AidlRunner {

    fun compile(
        srcDir: File,
        genDir: File,
        libsDir: File,
        logger: (String) -> Unit
    ): Boolean {
        val aidlFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "aidl" }.toList()
        if (aidlFiles.isEmpty()) return true

        logger("info  Found ${aidlFiles.size} AIDL file(s). Compiling...")

        val boltHome = BoltLocator.getBoltHome()
        val possibleBinDirs = listOf(
            File(File(libsDir, "tools"), "aidl"),
            File(libsDir, "aidl"),
            File(File(libsDir, "tools"), "bin"),
            File(libsDir, "tools"),
            File(libsDir, "bin"),
            File(boltHome, "libs/tools/aidl"),
            File(boltHome, "libs/aidl"),
            File(System.getProperty("user.home"), ".bolt/libs/tools/aidl"),
            File(System.getProperty("user.home"), ".bolt/libs/aidl")
        )
        val binDir = possibleBinDirs.firstOrNull { it.exists() && it.isDirectory } ?: File(libsDir, "bin")
        val frameworkAidl = possibleBinDirs.map { File(it, "framework.aidl") }.firstOrNull { it.exists() }
            ?: File(binDir, "framework.aidl")

        if (!frameworkAidl.exists()) {
            logger("error framework.aidl missing from ${binDir.absolutePath}")
            return false
        }

        val aidlBin = possibleBinDirs.mapNotNull { resolveAidlBinary(it) }.firstOrNull { it.exists() }
        if (aidlBin == null || !aidlBin.exists()) {
            // Check if system aidl is available
            val hasSystemAidl = try {
                val proc = ProcessBuilder("aidl", "--version").start()
                proc.waitFor() == 0
            } catch (e: Exception) {
                false
            }
            if (!hasSystemAidl) {
                logger("error AIDL binary not found for current platform in ${binDir.absolutePath} or system PATH.")
                return false
            }
        }

        val aidlPath = aidlBin?.absolutePath ?: "aidl"
        if (aidlBin != null && !aidlBin.canExecute()) {
            aidlBin.setExecutable(true)
        }

        genDir.mkdirs()

        for (aidlFile in aidlFiles) {
            try {
                val command = listOf(
                    aidlPath,
                    "-p${frameworkAidl.absolutePath}",
                    "-I${srcDir.absolutePath}",
                    "-o${genDir.absolutePath}",
                    aidlFile.absolutePath
                )

                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    logger("error AIDL compilation failed for ${aidlFile.name}:\n$output")
                    return false
                }
            } catch (e: Throwable) {
                logger("error AIDL execution exception: ${e.message}")
                return false
            }
        }

        logger("info  AIDL compilation completed successfully.")
        return true
    }

    private fun resolveAidlBinary(binDir: File): File? {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val arch = System.getProperty("os.arch")?.lowercase() ?: ""

        val candidateName = when {
            os.contains("win") -> "aidl.exe"
            arch.contains("aarch64") || arch.contains("arm64") -> "aidl-arm64-v8a"
            arch.contains("arm") -> "aidl-armeabi-v7a"
            arch.contains("x86_64") || arch.contains("amd64") -> "aidl-x86_64"
            os.contains("mac") || os.contains("darwin") -> "aidl-macos"
            else -> "aidl"
        }

        val file = File(binDir, candidateName)
        if (file.exists()) return file

        // Fallback checks
        val genericAidl = File(binDir, "aidl")
        if (genericAidl.exists()) return genericAidl

        val genericExe = File(binDir, "aidl.exe")
        if (genericExe.exists()) return genericExe

        return null
    }
}
