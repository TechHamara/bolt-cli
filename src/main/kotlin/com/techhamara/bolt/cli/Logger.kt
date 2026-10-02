package com.techhamara.bolt.cli

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * High-fidelity Terminal Logger matching Bolt CLI.
 */
class Logger {
    var debug: Boolean = false

    private var isTaskRunning: Boolean = false
    private var hasTaskLogged: Boolean = false
    private var taskStartTime: Long = 0
    private var taskTitle: String = ""
    private var logFile: File? = null

    fun setOutputFile(file: File) {
        logFile = file
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    fun dbg(message: String) {
        if (debug) {
            log(message, "debug ".blue())
        }
    }

    fun info(message: String) {
        log(message, "- ".purple())
    }

    fun taskInfo(message: String) {
        log(message, "- ".purple())
    }

    fun warn(message: String) {
        log(message, "- ".yellow())
    }

    fun err(message: String) {
        log(message, "- ".red())
    }

    fun success(message: String) {
        log(message, "✓ ".green())
    }

    fun startTask(title: String) {
        if (isTaskRunning) {
            stopTask(true)
        }
        taskStartTime = System.currentTimeMillis()
        isTaskRunning = true
        hasTaskLogged = false
        taskTitle = title
        println("- ".purple() + title)
        writeLogToFile("- $title")
    }

    fun stopTask(success: Boolean = true) {
        if (!isTaskRunning) return
        val elapsedSec = (System.currentTimeMillis() - taskStartTime) / 1000.0
        val timeStr = String.format("%.2f", elapsedSec)
        val status = if (success) "✓".green() else "x".red()
        val timeLine = "$status    ... (${timeStr}s)".grey()

        val line = if (hasTaskLogged) {
            "│ ".grey() + timeLine
        } else {
            "│ ".grey() + timeLine
        }
        println(line)
        writeLogToFile(Ansi.stripAnsi(line))

        isTaskRunning = false
        hasTaskLogged = false
    }

    fun log(message: String, prefix: String = "", printToConsole: Boolean = true) {
        if (printToConsole && !hasTaskLogged && isTaskRunning) {
            hasTaskLogged = true
        }

        var fullPrefix = prefix
        if (isTaskRunning) {
            fullPrefix = "│ ".grey() + prefix
        }

        val fullLine = fullPrefix + message.trimEnd()
        if (printToConsole) {
            println(fullLine)
        }
        writeLogToFile(Ansi.stripAnsi(fullLine))
    }

    fun parseAndLog(chunk: String) {
        val lines = chunk.lines()
        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue

            val trimmed = line.trim()
            if (trimmed == "}" || trimmed == "}`" || trimmed == "};" || trimmed.matches(Regex("""^[`\}\{\s;]+$"""))) {
                continue
            }

            val lineLower = line.lowercase()
            if (lineLower.contains("auto_version") ||
                lineLower.contains("autoversion") ||
                lineLower.contains("not recognized by any processor") ||
                lineLower.contains("supported source version") ||
                lineLower.contains("from annotation processor") ||
                lineLower.contains("wrote file file:///") ||
                lineLower.contains("simple_components") ||
                lineLower.contains("illegal reflective access") ||
                lineLower.contains("please consider reporting this") ||
                lineLower.contains("use --illegal-access=warn") ||
                lineLower.contains("illegal access operations will be denied") ||
                lineLower.contains("ignoring option:") ||
                lineLower.contains("does not match anything") ||
                lineLower.contains("rule does not match anything")
            ) {
                continue
            }

            when {
                line.startsWith("- ") -> {
                    log(line.substring(2), "- ".purple())
                }
                lineLower.startsWith("warning") || lineLower.contains("warning:") -> {
                    warn(line.replaceFirst(Regex("(?i)(warning:?\\s*)"), ""))
                }
                lineLower.startsWith("error") || lineLower.contains("error:") -> {
                    err(line.replaceFirst(Regex("(?i)(error:?\\s*)"), ""))
                }
                lineLower.startsWith("info") || lineLower.contains("info:") -> {
                    val msg = line.replaceFirst(Regex("(?i)(info:?\\s*)"), "")
                    if (msg.startsWith("- ")) {
                        log(msg.substring(2), "- ".purple())
                    } else {
                        info(msg)
                    }
                }
                lineLower.startsWith("debug") || lineLower.contains("debug:") || lineLower.contains("note:") -> {
                    dbg(line.replaceFirst(Regex("(?i)((debug|note):?\\s*)"), ""))
                }
                else -> {
                    log(line, "      ")
                }
            }
        }
    }

    private fun writeLogToFile(text: String) {
        val file = logFile ?: return
        try {
            FileOutputStream(file, true).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(text)
                    writer.write("\n")
                }
            }
        } catch (_: Exception) {
        }
    }
}
