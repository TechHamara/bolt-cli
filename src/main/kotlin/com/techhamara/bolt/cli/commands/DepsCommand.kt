package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger

object DepsCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        if (args.isEmpty()) {
            logger.err("Usage: bolt deps <sync|tree> [options]")
            return 1
        }

        return when (val sub = args[0].lowercase()) {
            "sync" -> SyncCommand.execute(args.drop(1), logger)
            "tree" -> TreeCommand.execute(args.drop(1), logger)
            else -> {
                logger.err("Unknown deps subcommand: $sub. Available: sync, tree")
                1
            }
        }
    }
}
