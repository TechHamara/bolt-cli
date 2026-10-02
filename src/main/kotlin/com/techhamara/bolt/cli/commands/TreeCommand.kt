package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.blue
import com.techhamara.bolt.cli.bold
import com.techhamara.bolt.cli.cyan
import com.techhamara.bolt.cli.green
import com.techhamara.bolt.cli.grey
import java.io.File

object TreeCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        val targetPath = args.firstOrNull { !it.startsWith("-") } ?: "."
        val projectDir = File(targetPath).canonicalFile

        val projectName = projectDir.name
        val coloredLines = mutableListOf<String>()
        val plainLines = mutableListOf<String>()

        coloredLines.add(projectName.cyan().bold())
        plainLines.add(projectName)

        generateTree(projectDir, "", "", coloredLines, plainLines)

        for (line in coloredLines) {
            println(line)
        }

        try {
            val treeFile = File(projectDir, "tree.txt")
            treeFile.writeText(plainLines.joinToString("\n"))
            logger.info("Project hierarchical structure saved to ${"tree.txt".green()}")
        } catch (e: Exception) {
            logger.err("Failed to save project structure to tree.txt: ${e.message}")
        }

        return 0
    }

    private fun generateTree(
        dir: File,
        coloredIndent: String,
        plainIndent: String,
        coloredLines: MutableList<String>,
        plainLines: MutableList<String>
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        val entities = dir.listFiles() ?: return
        val visible = entities.filter { file ->
            !file.name.startsWith(".") && file.name != "tree.txt"
        }.sortedWith { a, b ->
            if (a.isDirectory && !b.isDirectory) -1
            else if (!a.isDirectory && b.isDirectory) 1
            else a.name.lowercase().compareTo(b.name.lowercase())
        }

        for (i in visible.indices) {
            val file = visible[i]
            val isLast = i == visible.size - 1
            val name = file.name

            val coloredConnector = if (isLast) "└── ".grey() else "├── ".grey()
            val plainConnector = if (isLast) "└── " else "├── "

            if (file.isDirectory) {
                coloredLines.add("$coloredIndent$coloredConnector${name.blue().bold()}/")
                plainLines.add("$plainIndent$plainConnector$name/")

                val nextIndentColored = coloredIndent + if (isLast) "    " else "│   ".grey()
                val nextIndentPlain = plainIndent + if (isLast) "    " else "│   "
                generateTree(file, nextIndentColored, nextIndentPlain, coloredLines, plainLines)
            } else {
                coloredLines.add("$coloredIndent$coloredConnector$name")
                plainLines.add("$plainIndent$plainConnector$name")
            }
        }
    }
}
