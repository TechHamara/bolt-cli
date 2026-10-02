package com.techhamara.bolt.cli

import org.fusesource.jansi.AnsiConsole

/**
 * Terminal ANSI styling and color utilities matching Bolt CLI.
 */
object Ansi {

    var enabled: Boolean = true

    init {
        val os = System.getProperty("os.name", "").lowercase()
        val isWindows = os.contains("win")

        if (System.getenv("NO_COLOR") != null) {
            enabled = false
        } else if (isWindows) {
            try {
                AnsiConsole.systemInstall()
                enabled = true
            } catch (_: Throwable) {
                enabled = detectAnsiSupport()
            }
        } else {
            // Linux, macOS, and Android Termux natively interpret ANSI escape sequences.
            // DO NOT call AnsiConsole.systemInstall() on non-Windows/Android because:
            // 1. Android/Termux uses Bionic libc (libc.so) instead of GNU libc (libc.so.6),
            //    causing Jansi's bundled glibc libjansi.so to fail with UnsatisfiedLinkError: libc.so.6 not found.
            // 2. When Jansi fails to load native library, its fallback wrapper strips all ANSI color codes.
            // 3. Unix and Termux terminals parse ANSI codes natively without needing any wrapper or native library.
            System.setProperty("jansi.passthrough", "true")
            enabled = detectAnsiSupport()
        }
    }

    private fun detectAnsiSupport(): Boolean {
        if (System.getenv("NO_COLOR") != null) return false
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) {
            if (System.getenv("WT_SESSION") != null) return true // Windows Terminal
            if (System.getenv("ConEmuPID") != null) return true // ConEmu / Cmder
            if (System.getenv("TERM_PROGRAM") != null) return true // VS Code, Hyper, etc.
            if (System.getenv("ANSICON") != null) return true // Ansicon
            val term = System.getenv("TERM")?.lowercase()
            return term != null && term != "dumb"
        }
        // Linux, macOS, Android Termux
        val term = System.getenv("TERM")?.lowercase()
        return term == null || term != "dumb"
    }

    fun stripAnsi(str: String): String {
        return str.replace(Regex("\u001B\\[[0-9;]*m"), "")
    }
}

fun String.colored(code: String): String {
    return if (Ansi.enabled) "\u001B[${code}m$this\u001B[0m" else this
}

fun String.bold(): String = colored("1")
fun String.red(): String = colored("31;1")
fun String.green(): String = colored("32;1")
fun String.yellow(): String = colored("33;1")
fun String.blue(): String = colored("34;1")
fun String.magenta(): String = colored("35;1")
fun String.purple(): String = colored("35;1")
fun String.cyan(): String = colored("36;1")
fun String.white(): String = colored("37;1")
fun String.grey(): String = colored("90")
fun String.brightBlack(): String = colored("90")
fun String.brightYellow(): String = colored("93;1")
fun String.gold(): String = colored("38;5;220;1")

