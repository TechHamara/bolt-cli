package com.techhamara.bolt.compiler

import java.io.File

/**
 * Universal locator for the Bolt CLI installation and home directory.
 * Discovers the active Bolt installation across Windows (%LOCALAPPDATA%\Bolt),
 * Linux/macOS (~/.bolt), Android Termux, or ANY custom directory via dynamic JAR codeSource inspection.
 */
object BoltLocator {

    private var explicitBoltHome: File? = null

    fun setExplicitBoltHome(dir: File) {
        explicitBoltHome = dir
    }

    /**
     * Resolves the Bolt installation home directory using the following hierarchy:
     * 1. Explicitly set home directory (in-process override)
     * 2. Environment variable `BOLT_HOME`
     * 3. Dynamic runtime detection via `bolt.jar` location (protection domain codeSource)
     * 4. Platform default:
     *    - Windows: `%LOCALAPPDATA%\Bolt` (or `%USERPROFILE%\AppData\Local\Bolt`)
     *    - Linux / macOS / Termux: `~/.bolt`
     */
    fun getBoltHome(): File {
        explicitBoltHome?.let {
            if (it.exists() && it.isDirectory) return it
        }

        // 1. Check environment variable BOLT_HOME
        System.getenv("BOLT_HOME")?.let { envPath ->
            if (envPath.isNotBlank()) {
                val dir = File(envPath)
                if (dir.exists() && dir.isDirectory) return dir
            }
        }

        // 2. Dynamic runtime detection from bolt.jar location
        try {
            val codeSource = BoltLocator::class.java.protectionDomain?.codeSource
            if (codeSource != null) {
                val jarFile = File(codeSource.location.toURI())
                // If bolt.jar is inside <home>/bin/bolt.jar
                if (jarFile.parentFile?.name.equals("bin", ignoreCase = true)) {
                    val candidateHome = jarFile.parentFile?.parentFile
                    if (candidateHome != null && candidateHome.exists()) {
                        return candidateHome
                    }
                }
                // If bolt.jar is in <home>/bolt.jar directly
                val candidateHome = jarFile.parentFile
                if (candidateHome != null && candidateHome.exists()) {
                    val libsDir = File(candidateHome, "libs")
                    if (libsDir.exists() && libsDir.isDirectory) {
                        return candidateHome
                    }
                }
            }
        } catch (ignored: Exception) {}

        // 3. Platform default fallback
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) {
            val localAppData = System.getenv("LOCALAPPDATA")
            if (!localAppData.isNullOrBlank()) {
                val appDataBolt = File(localAppData, "Bolt")
                if (appDataBolt.exists() && appDataBolt.isDirectory) return appDataBolt
            }
            val userHome = System.getProperty("user.home")
            val fallbackAppData = File(userHome, "AppData/Local/Bolt")
            if (fallbackAppData.exists() && fallbackAppData.isDirectory) return fallbackAppData

            // Also check legacy ~/.bolt if it already exists
            val legacyHome = File(userHome, ".bolt")
            if (legacyHome.exists() && legacyHome.isDirectory) return legacyHome

            // Default new target on Windows
            return if (!localAppData.isNullOrBlank()) File(localAppData, "Bolt") else fallbackAppData
        }

        // Linux, macOS, Termux default: ~/Bolt (without leading dot)
        val homeDir = System.getProperty("user.home")
        val boltHome = File(homeDir, "Bolt")
        if (boltHome.exists() && boltHome.isDirectory) return boltHome

        val legacyDotBolt = File(homeDir, ".bolt")
        if (legacyDotBolt.exists() && legacyDotBolt.isDirectory) return legacyDotBolt

        return File(homeDir, "Bolt")
    }

    /**
     * Resolves the `libs/` directory within the active Bolt installation.
     */
    fun getLibsDir(): File = File(getBoltHome(), "libs")

    /**
     * Resolves the `bin/` directory within the active Bolt installation.
     */
    fun getBinDir(): File = File(getBoltHome(), "bin")

    /**
     * Resolves the `libs/tools/` directory within the active Bolt installation.
     */
    fun getToolsDir(): File = File(getLibsDir(), "tools")

    /**
     * Resolves detailed OS version information (e.g. "Windows 10 Pro Education" 10.0 (Build 19045)).
     */
    fun getDetailedOsVersion(): String {
        val osName = System.getProperty("os.name", "unknown")
        val osVersion = System.getProperty("os.version", "unknown")
        if (osName.lowercase().contains("win")) {
            try {
                val process = ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                var productName: String? = null
                var buildNumber: String? = null

                for (line in output.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("ProductName")) {
                        productName = trimmed.split(Regex("\\s+"), limit = 3).getOrNull(2)
                    } else if (trimmed.startsWith("CurrentBuild")) {
                        buildNumber = trimmed.split(Regex("\\s+"), limit = 3).getOrNull(2)
                    }
                }

                if (!productName.isNullOrBlank() && !buildNumber.isNullOrBlank()) {
                    return "\"$productName\" $osVersion (Build $buildNumber)"
                } else if (!productName.isNullOrBlank()) {
                    return "\"$productName\" $osVersion"
                }
            } catch (_: Exception) {}
        }
        return "\"$osName\" $osVersion"
    }
}
