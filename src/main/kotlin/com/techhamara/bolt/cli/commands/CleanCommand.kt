package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.green
import java.io.File

object CleanCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        val projectDir = File(args.firstOrNull { !it.startsWith("-") } ?: ".").canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")

        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        val dotBolt = File(projectDir, ".bolt")
        val buildDir = File(projectDir, "build")

        if (dotBolt.exists()) dotBolt.deleteRecursively()
        if (buildDir.exists()) buildDir.deleteRecursively()

        logger.log("${"✓".green()} Deleted build files and caches")
        return 0
    }
}
