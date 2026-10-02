package com.techhamara.bolt.cli.commands

import com.google.gson.JsonParser
import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.blue
import com.techhamara.bolt.cli.bold
import com.techhamara.bolt.cli.brightYellow
import com.techhamara.bolt.cli.cyan
import com.techhamara.bolt.cli.green
import com.techhamara.bolt.cli.grey
import com.techhamara.bolt.cli.magenta
import com.techhamara.bolt.cli.red
import com.techhamara.bolt.cli.yellow
import com.techhamara.bolt.compiler.BoltLocator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object UpgradeCommand {

    private const val CURRENT_VERSION = "2.0.0"

    fun execute(args: List<String>, logger: Logger): Int {
        var force = false
        var autoYes = false
        var modeOpt: String? = null

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-f", "--force" -> force = true
                "-y", "--yes" -> autoYes = true
                "-m", "--mode" -> {
                    if (i + 1 < args.size) {
                        modeOpt = args[++i]
                    }
                }
                else -> {
                    if (arg.startsWith("--mode=")) {
                        modeOpt = arg.substringAfter("--mode=")
                    }
                }
            }
            i++
        }

        logger.info("- Checking for new version...")

        val latestVersion: String
        val releaseHtmlUrl: String
        val root: com.google.gson.JsonObject

        try {
            val url = URL("https://api.github.com/repos/TechHamara/bolt-cli/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Bolt-CLI")

            if (conn.responseCode != 200) {
                logger.warn("Could not check latest release on GitHub: HTTP ${conn.responseCode}")
                return 0
            }

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            root = JsonParser.parseString(jsonText).asJsonObject
            latestVersion = root.get("tag_name")?.asString?.removePrefix("v") ?: CURRENT_VERSION
            releaseHtmlUrl = root.get("html_url")?.asString ?: "https://github.com/TechHamara/bolt-cli/releases"
        } catch (e: Exception) {
            logger.warn("Network error checking for updates: ${e.message}")
            return 0
        }

        val isLatest = compareVersions("v$CURRENT_VERSION", "v$latestVersion") >= 0

        println("${"Current version:".cyan().bold()} ${"v$CURRENT_VERSION".yellow().bold()}")

        if (isLatest && !force) {
            println("${"✓".green().bold()} ${"Bolt is already on the latest version. 🚀".green().bold()}")
            return 0
        }

        if (!isLatest || force) {
            println("${"Available version:".cyan().bold()} ${"v$latestVersion".green().bold()}")
        }

        if (!isLatest) {
            println("${"★".yellow().bold()} ${"An update is available:".yellow().bold()} ${"v$latestVersion".green().bold()}")
        }

        // Prompt 1: Do you want to upgrade? [Yes/No] (Default: No)
        var shouldUpgrade = autoYes || force
        if (!shouldUpgrade) {
            print("${"Do you want to upgrade?".cyan().bold()} ${"[".grey()}${"Yes".green().bold()}${"/".grey()}${"No".red().bold()}${"]".grey()} ${"(".grey()}${"Default:".magenta().bold()} ${"No".red().bold()}${")".grey()} ")
            val input = try {
                readLine()?.trim()?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
            if (input == "yes" || input == "y" || input == "1") {
                shouldUpgrade = true
            }
        }

        if (!shouldUpgrade) {
            println("${"Upgrade cancelled.".yellow()}")
            return 0
        }

        // Prompt 2: Select update mode? [Fresh/InPlace] type 2 for InPlace, (Default: Fresh)
        var isInPlace = false
        if (!modeOpt.isNullOrBlank()) {
            isInPlace = modeOpt.lowercase() == "inplace" || modeOpt == "2"
        } else {
            print("${"Select update mode?".cyan().bold()} ${"[".grey()}${"Fresh".green().bold()}${"/".grey()}${"InPlace".yellow().bold()}${"]".grey()} ${"type".grey()} ${"2".blue().bold()} ${"for InPlace,".grey()} ${"(".grey()}${"Default:".magenta().bold()} ${"Fresh".green().bold()}${")".grey()} ")
            val modeInput = try {
                readLine()?.trim()?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
            if (modeInput == "2" || modeInput == "inplace" || modeInput == "in-place") {
                isInPlace = true
            }
        }

        val assets = root.getAsJsonArray("assets") ?: com.google.gson.JsonArray()
        val archive = if (isInPlace) {
            // InPlace mode: looks for update.zip (or bin.zip / bolt-bin.zip)
            assets.mapNotNull { it.asJsonObject }.firstOrNull { el ->
                val name = el.get("name")?.asString?.lowercase() ?: ""
                name == "update.zip" || name == "bin.zip" || name == "bolt-bin.zip"
            }
        } else {
            // Fresh mode: looks for bolt.zip (or universal zip)
            assets.mapNotNull { it.asJsonObject }.firstOrNull { el ->
                val name = el.get("name")?.asString?.lowercase() ?: ""
                name == "bolt.zip" || name == "bolt-universal.zip"
            } ?: assets.mapNotNull { it.asJsonObject }.firstOrNull { el ->
                el.get("name")?.asString?.endsWith(".zip") == true
            }
        }

        if (archive == null || archive.get("browser_download_url")?.asString.isNullOrBlank()) {
            if (isInPlace) {
                logger.err("Could not find 'update.zip' release asset for InPlace update in v$latestVersion.")
                logger.info("Please run 'bolt upgrade' and select 'Fresh' mode (Option 1) to download full package.")
            } else {
                logger.err("Could not find release asset 'bolt.zip' at $releaseHtmlUrl")
            }
            return 1
        }

        val downloadUrl = archive.get("browser_download_url").asString
        val archiveName = archive.get("name").asString
        val totalBytes = archive.get("size")?.asLong ?: 0L
        val totalMBStr = String.format("%.2f", totalBytes / (1024.0 * 1024.0))
        println("${"Download size:".cyan().bold()} ${"$totalMBStr MB".magenta().bold()}")

        val boltHomeDir = BoltLocator.getBoltHome()
        val tempDir = File(boltHomeDir, "temp").apply { mkdirs() }
        val archiveDist = File(tempDir, archiveName)

        try {
            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Bolt-CLI")

            if (conn.responseCode !in 200..299) {
                logger.err("Something went wrong during download...")
                logger.log("GET status code: ${conn.responseCode}")
                return 1
            }

            val streamTotalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else totalBytes
            val stopwatch = System.currentTimeMillis()
            var receivedBytes = 0L
            var lastReportBytes = 0L
            var lastReportTime = stopwatch
            val buffer = ByteArray(65536)

            conn.inputStream.use { input ->
                FileOutputStream(archiveDist).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        receivedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 150 || receivedBytes == streamTotalBytes) {
                            val timeSec = (now - lastReportTime) / 1000.0
                            val bytesSince = receivedBytes - lastReportBytes
                            val speedMB = if (timeSec > 0) (bytesSince / timeSec) / (1024.0 * 1024.0) else 0.0
                            val pct = if (streamTotalBytes > 0) (receivedBytes.toDouble() / streamTotalBytes * 100.0) else 0.0

                            val pctStr = String.format("%6.2f%%", pct)
                            val rxMBStr = String.format("%.2f", receivedBytes / (1024.0 * 1024.0))
                            val sizeMBStr = String.format("%.2f", streamTotalBytes / (1024.0 * 1024.0))
                            val speedStr = String.format("%.2f MB/s", speedMB)

                            val header = "Downloading:".yellow().bold()
                            val pctFormatted = pctStr.brightYellow().bold()
                            val currentMB = "${rxMBStr} MB".cyan().bold()
                            val totalMB = "${sizeMBStr} MB".magenta().bold()
                            val sizeFormatted = "${"(".grey()}$currentMB${"/".grey()}$totalMB${")".grey()}"
                            val speedFormatted = "${"|".grey()} ${speedStr.green().bold()}"

                            print("\r$header  $pctFormatted $sizeFormatted $speedFormatted ")
                            lastReportBytes = receivedBytes
                            lastReportTime = now
                        }
                    }
                }
            }
            val finalSizeMBStr = String.format("%.2f", streamTotalBytes / (1024.0 * 1024.0))
            val doneHeader = "Downloading:".yellow().bold()
            val donePct = "100.00%".brightYellow().bold()
            val doneCurrentMB = "${finalSizeMBStr} MB".cyan().bold()
            val doneTotalMB = "${finalSizeMBStr} MB".magenta().bold()
            val doneSize = "${"(".grey()}$doneCurrentMB${"/".grey()}$doneTotalMB${")".grey()}"
            println("\r$doneHeader  $donePct $doneSize ${"|".grey()} ${"Done".green().bold()}       ")
        } catch (e: Exception) {
            logger.err("Something went wrong during download: ${e.message}")
            return 1
        }

        println("${"Extracting:".cyan().bold()} ${archiveDist.name.yellow().bold()}...")
        try {
            extractZip(archiveDist, boltHomeDir)
            archiveDist.delete()
        } catch (e: Exception) {
            logger.err("Extraction failed: ${e.message}")
            return 1
        }

        // On Linux / macOS / Termux, ensure launchers are executable
        val unixLauncher = File(boltHomeDir, "bin/bolt")
        if (unixLauncher.exists()) {
            try { unixLauncher.setExecutable(true, false) } catch (_: Exception) {}
        }
        val rootUnixLauncher = File(boltHomeDir, "bolt")
        if (rootUnixLauncher.exists()) {
            try { rootUnixLauncher.setExecutable(true, false) } catch (_: Exception) {}
        }

        val modeText = if (isInPlace) {
            "${"InPlace".yellow().bold()} ${"(update.zip)".grey()}"
        } else {
            "${"Fresh".green().bold()} ${"(bolt.zip)".grey()}"
        }
        println("""
${"Success".green().bold()}! Bolt ${"v$latestVersion".green().bold()} has been installed in $modeText mode. 🎉

${"Release Notes:".cyan().bold()} ${releaseHtmlUrl.cyan()}
""".trimIndent())

        return 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val p2 = v2.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val part1 = p1.getOrElse(i) { 0 }
            val part2 = p2.getOrElse(i) { 0 }
            if (part1 > part2) return 1
            if (part1 < part2) return -1
        }
        return 0
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
