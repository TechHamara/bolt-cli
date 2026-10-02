package com.techhamara.bolt.compiler

import java.io.File

/**
 * Automatically configures OpenJDK's font subsystem on Android / Termux
 * by generating a fontconfig.properties mapping directly to Android's /system/fonts/
 * (e.g. Roboto-Regular.ttf, Roboto-Bold.ttf).
 *
 * This allows java.awt.Graphics2D, Font, and FontMetrics to work seamlessly
 * in Termux WITHOUT needing to install any packages like `fontconfig` or `ttf-dejavu`.
 */
object AndroidFontConfigHelper {

    fun ensureConfigured() {
        try {
            System.setProperty("java.awt.headless", "true")

            if (System.getProperty("sun.awt.fontconfig") != null) {
                return
            }

            val systemFontsDir = File("/system/fonts")
            if (!systemFontsDir.exists() || !systemFontsDir.isDirectory) {
                return
            }

            val regularCandidates = listOf(
                File(systemFontsDir, "Roboto-Regular.ttf"),
                File(systemFontsDir, "DroidSans.ttf"),
                File(systemFontsDir, "NotoSans-Regular.ttf")
            )
            val regularFont = regularCandidates.firstOrNull { it.exists() && it.canRead() }
                ?: systemFontsDir.listFiles()?.firstOrNull { it.extension.lowercase() == "ttf" && it.canRead() }
                ?: return

            val boldCandidates = listOf(
                File(systemFontsDir, "Roboto-Bold.ttf"),
                File(systemFontsDir, "DroidSans-Bold.ttf"),
                File(systemFontsDir, "NotoSans-Bold.ttf")
            )
            val boldFont = boldCandidates.firstOrNull { it.exists() && it.canRead() } ?: regularFont

            val homePath = System.getProperty("user.home") ?: "/data/data/com.termux/files/home"
            val boltDir = File(homePath, ".bolt")
            val targetDir = if (boltDir.exists() || boltDir.mkdirs()) boltDir else File(System.getProperty("java.io.tmpdir", "/tmp"))
            val fcFile = File(targetDir, "fontconfig.properties")

            if (!fcFile.exists() || fcFile.length() == 0L) {
                val regPath = regularFont.absolutePath
                val bldPath = boldFont.absolutePath
                val content = buildString {
                    appendLine("version=1")
                    appendLine("sequence.allfonts=latin-1")
                    appendLine("sequence.fallback=latin-1")
                    appendLine("allfonts.latin-1=roboto")
                    appendLine("allfonts.chinese-gb18030=roboto")
                    appendLine("allfonts.japanese-x0208=roboto")
                    appendLine("allfonts.korean=roboto")
                    appendLine("serif.plain.latin-1=roboto")
                    appendLine("serif.bold.latin-1=roboto-bold")
                    appendLine("serif.italic.latin-1=roboto")
                    appendLine("serif.bolditalic.latin-1=roboto-bold")
                    appendLine("sansserif.plain.latin-1=roboto")
                    appendLine("sansserif.bold.latin-1=roboto-bold")
                    appendLine("sansserif.italic.latin-1=roboto")
                    appendLine("sansserif.bolditalic.latin-1=roboto-bold")
                    appendLine("monospaced.plain.latin-1=roboto")
                    appendLine("monospaced.bold.latin-1=roboto-bold")
                    appendLine("monospaced.italic.latin-1=roboto")
                    appendLine("monospaced.bolditalic.latin-1=roboto-bold")
                    appendLine("dialog.plain.latin-1=roboto")
                    appendLine("dialog.bold.latin-1=roboto-bold")
                    appendLine("dialog.italic.latin-1=roboto")
                    appendLine("dialog.bolditalic.latin-1=roboto-bold")
                    appendLine("dialoginput.plain.latin-1=roboto")
                    appendLine("dialoginput.bold.latin-1=roboto-bold")
                    appendLine("dialoginput.italic.latin-1=roboto")
                    appendLine("dialoginput.bolditalic.latin-1=roboto-bold")
                    appendLine("filename.roboto=$regPath")
                    appendLine("filename.roboto-bold=$bldPath")
                    appendLine("awtfontpath.latin-1=/system/fonts/")
                }
                fcFile.writeText(content, Charsets.UTF_8)
            }

            if (fcFile.exists()) {
                System.setProperty("sun.awt.fontconfig", fcFile.absolutePath)
            }
        } catch (_: Throwable) {}
    }
}
