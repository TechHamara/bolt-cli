package com.techhamara.bolt.compiler

import java.io.File
import java.util.Locale

object SystemInfo {

    fun getDeviceName(): String {
        // Realtime Android / Termux check
        val isAndroid = File("/system/bin/getprop").exists() || System.getenv("TERMUX_VERSION") != null || File("/data/data/com.termux").exists()
        if (isAndroid) {
            try {
                val manufacturer = runCmd("getprop", "ro.product.manufacturer").trim()
                val model = runCmd("getprop", "ro.product.model").trim()
                val name = listOf(manufacturer, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
                if (name.isNotBlank()) return name
            } catch (_: Exception) {}
            return "Android Device"
        }

        val osName = System.getProperty("os.name", "").lowercase()
        if (osName.contains("win")) {
            val compName = System.getenv("COMPUTERNAME")
            return if (!compName.isNullOrBlank()) compName else "Windows PC"
        } else if (osName.contains("mac")) {
            try {
                val model = runCmd("sysctl", "-n", "hw.model").trim()
                if (model.isNotBlank()) return model
            } catch (_: Exception) {}
            return "Mac"
        } else {
            // Linux
            try {
                val dmiProduct = File("/sys/devices/virtual/dmi/id/product_name")
                if (dmiProduct.exists()) {
                    val name = dmiProduct.readText().trim()
                    if (name.isNotBlank() && name != "System Product Name") return name
                }
            } catch (_: Exception) {}
            val host = System.getenv("HOSTNAME")
            return if (!host.isNullOrBlank()) host else "Linux System"
        }
    }

    fun getAvailableProcessors(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    fun getRamInfo(): String {
        // 1. Try /proc/meminfo for Linux / Android Termux
        val meminfo = File("/proc/meminfo")
        if (meminfo.exists()) {
            try {
                var totalKb = 0L
                var availKb = 0L
                for (line in meminfo.readLines()) {
                    val parts = line.split(Regex(":\\s+"))
                    if (parts.size >= 2) {
                        val key = parts[0].trim()
                        val valueKb = parts[1].split(Regex("\\s+"))[0].toLongOrNull() ?: 0L
                        if (key == "MemTotal") totalKb = valueKb
                        if (key == "MemAvailable" || (availKb == 0L && key == "MemFree")) availKb = valueKb
                    }
                }
                if (totalKb > 0) {
                    val usedKb = totalKb - availKb
                    val usedGb = usedKb / (1024.0 * 1024.0)
                    val totalGb = totalKb / (1024.0 * 1024.0)
                    return String.format(Locale.ENGLISH, "%.1f/%.1fGB", usedGb, totalGb)
                }
            } catch (_: Exception) {}
        }

        // 2. Try OperatingSystemMXBean methods
        try {
            val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            val methods = osBean.javaClass.methods
            val totalMethod = methods.firstOrNull { it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize" }
            val freeMethod = methods.firstOrNull { it.name == "getFreeMemorySize" || it.name == "getFreePhysicalMemorySize" }
            if (totalMethod != null && freeMethod != null) {
                totalMethod.isAccessible = true
                freeMethod.isAccessible = true
                val totalBytes = (totalMethod.invoke(osBean) as Number).toLong()
                val freeBytes = (freeMethod.invoke(osBean) as Number).toLong()
                if (totalBytes > 0) {
                    val usedBytes = totalBytes - freeBytes
                    val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
                    val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                    return String.format(Locale.ENGLISH, "%.1f/%.1fGB", usedGb, totalGb)
                }
            }
        } catch (_: Exception) {}

        // 3. Try Windows wmic fallback
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            try {
                val output = runCmd("wmic", "OS", "get", "FreePhysicalMemory,TotalVisibleMemorySize", "/Value")
                var totalKb = 0L
                var freeKb = 0L
                for (line in output.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("TotalVisibleMemorySize=")) {
                        totalKb = trimmed.substringAfter("=").toLongOrNull() ?: 0L
                    } else if (trimmed.startsWith("FreePhysicalMemory=")) {
                        freeKb = trimmed.substringAfter("=").toLongOrNull() ?: 0L
                    }
                }
                if (totalKb > 0) {
                    val usedKb = totalKb - freeKb
                    val usedGb = usedKb / (1024.0 * 1024.0)
                    val totalGb = totalKb / (1024.0 * 1024.0)
                    return String.format(Locale.ENGLISH, "%.1f/%.1fGB", usedGb, totalGb)
                }
            } catch (_: Exception) {}
        }

        // 4. Fallback to JVM heap
        val totalMem = Runtime.getRuntime().totalMemory()
        val freeMem = Runtime.getRuntime().freeMemory()
        val usedMem = totalMem - freeMem
        return String.format(Locale.ENGLISH, "%.1f/%.1fGB", usedMem / (1024.0 * 1024 * 1024), totalMem / (1024.0 * 1024 * 1024))
    }

    fun getStorageInfo(projectDir: File): String {
        try {
            val total = projectDir.totalSpace
            val free = projectDir.usableSpace
            if (total > 0) {
                val used = total - free
                val usedGb = used / (1024.0 * 1024.0 * 1024.0)
                val totalGb = total / (1024.0 * 1024.0 * 1024.0)
                return String.format(Locale.ENGLISH, "%.0f/%.0fGB", usedGb, totalGb)
            }
        } catch (_: Exception) {}
        return "Unknown"
    }

    fun getDetailedOsVersion(): String {
        val isAndroid = File("/system/bin/getprop").exists() || System.getenv("TERMUX_VERSION") != null || File("/data/data/com.termux").exists()
        if (isAndroid) {
            val release = try { runCmd("getprop", "ro.build.version.release").trim() } catch (_: Exception) { "" }
            val sdk = try { runCmd("getprop", "ro.build.version.sdk").trim() } catch (_: Exception) { "" }
            val termuxVer = System.getenv("TERMUX_VERSION")
            val releasePart = if (release.isNotBlank()) "Android $release" else "Android"
            val sdkPart = if (sdk.isNotBlank()) " (API $sdk)" else ""
            val termuxPart = if (!termuxVer.isNullOrBlank()) " [Termux v$termuxVer]" else ""
            return "$releasePart$sdkPart$termuxPart"
        }

        return BoltLocator.getDetailedOsVersion()
    }

    fun getJavaOptions(): String? {
        val env = System.getenv("_JAVA_OPTIONS")
        if (!env.isNullOrBlank()) return env.trim()

        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            try {
                val regOutput = runCmd("reg", "query", "HKCU\\Environment", "/v", "_JAVA_OPTIONS")
                for (line in regOutput.lines()) {
                    if (line.contains("_JAVA_OPTIONS")) {
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val valStr = parts.subList(2, parts.size).joinToString(" ").trim()
                            if (valStr.isNotBlank()) return valStr
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val regOutput = runCmd("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment", "/v", "_JAVA_OPTIONS")
                for (line in regOutput.lines()) {
                    if (line.contains("_JAVA_OPTIONS")) {
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val valStr = parts.subList(2, parts.size).joinToString(" ").trim()
                            if (valStr.isNotBlank()) return valStr
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val toolOptions = System.getenv("JAVA_TOOL_OPTIONS")
        if (!toolOptions.isNullOrBlank()) return toolOptions.trim()

        return null
    }

    private fun runCmd(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return text
    }
}
