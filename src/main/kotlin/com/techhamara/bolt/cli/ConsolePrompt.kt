package com.techhamara.bolt.cli

import java.util.Scanner

/**
 * Interactive CLI prompt utilities for user input and configuration.
 */
object ConsolePrompt {

    private val scanner = Scanner(System.`in`)

    fun prompt(message: String, default: String? = null): String {
        val promptStr = if (default != null && default.isNotBlank()) {
            "$message (${default.grey()}): "
        } else {
            "$message: "
        }
        print(promptStr)
        val line = try {
            if (scanner.hasNextLine()) scanner.nextLine() else ""
        } catch (_: Exception) {
            ""
        }
        val trimmed = line.trim()
        return if (trimmed.isEmpty() && default != null) default else trimmed
    }

    fun confirm(message: String, default: Boolean = false): Boolean {
        val defaultText = if (default) "[${"Y".green()}/n]" else "[y/${"N".red()}]"
        print("$message $defaultText: ")
        val line = try {
            if (scanner.hasNextLine()) scanner.nextLine().trim().lowercase() else ""
        } catch (_: Exception) {
            ""
        }
        return when (line) {
            "y", "yes", "1", "true" -> true
            "n", "no", "0", "false" -> false
            else -> default
        }
    }
}
