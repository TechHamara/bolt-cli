package com.techhamara.bolt.cli

import com.techhamara.bolt.cli.commands.*
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.LibLocator
import com.techhamara.bolt.parser.ConfigParser
import java.io.File
import kotlin.system.exitProcess

/**
 * Universal Command Line Interface for Bolt Compiler.
 * 100% Drop-in Universal Replacement for Bolt CLI across Windows, macOS, Linux, and Android Termux.
 */
object Main {

    const val VERSION = "2.0.0"
    val BUILT_ON: String by lazy { resolveBuildTimestamp() }

    private fun resolveBuildTimestamp(): String {
        try {
            val stream = Main::class.java.getResourceAsStream("/META-INF/MANIFEST.MF")
            if (stream != null) {
                val manifest = java.util.jar.Manifest(stream)
                val built = manifest.mainAttributes.getValue("Built-On")
                if (!built.isNullOrBlank()) return built
            }
            val loc = Main::class.java.protectionDomain?.codeSource?.location
            if (loc != null) {
                val f = File(loc.toURI())
                if (f.exists() && f.isFile) {
                    val lastMod = f.lastModified()
                    if (lastMod > 0L) {
                        return java.text.SimpleDateFormat("yyyy-MM-dd/HH:mm:ss").format(java.util.Date(lastMod))
                    }
                }
            }
        } catch (_: Exception) {}
        return "2026-09-08/22:00:00"
    }

    private val logger = Logger()

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            System.setProperty("java.awt.headless", "true")
            com.techhamara.bolt.compiler.AndroidFontConfigHelper.ensureConfigured()
            val isWin = System.getProperty("os.name", "").lowercase().contains("win")
            val isModern = System.getenv("WT_SESSION") != null ||
                           System.getenv("VSCODE_PID") != null ||
                           System.getenv("TERM_PROGRAM") != null ||
                           System.getenv("COLORTERM") != null
            if (!isWin || isModern) {
                System.setOut(java.io.PrintStream(System.out, true, "UTF-8"))
                System.setErr(java.io.PrintStream(System.err, true, "UTF-8"))
            }
        } catch (_: Throwable) {}

        val argList = args.toMutableList()

        if (argList.isNotEmpty() && argList[0] == "v") {
            argList[0] = "-v"
        }

        // Global flag parsing
        val filteredArgs = mutableListOf<String>()
        var showVersion = false
        var showHelp = false
        var noLogo = false

        for (arg in argList) {
            when (arg) {
                "-V", "--version" -> showVersion = true
                "-h", "--help" -> showHelp = true
                "-v", "--verbose", "-d", "--debug" -> logger.debug = true
                "-c", "--color" -> Ansi.enabled = true
                "--no-color" -> Ansi.enabled = false
                "--no-logo" -> noLogo = true
                else -> filteredArgs.add(arg)
            }
        }

        if (showVersion) {
            println("Running on version ${VERSION.cyan()}")
            exitProcess(0)
        }

        if (filteredArgs.isEmpty() || showHelp) {
            if (!noLogo) printLogo()
            printUsage()
            exitProcess(0)
        }

        // Special combo: sync build
        if (filteredArgs.size >= 2 && filteredArgs[0].lowercase() == "sync" && filteredArgs[1].lowercase() == "build") {
            val syncCode = SyncCommand.execute(listOf(), logger)
            if (syncCode != 0) exitProcess(syncCode)
            val buildCode = BuildCommand.execute(filteredArgs.drop(2), logger)
            exitProcess(buildCode)
        }

        val isBuildFlag = filteredArgs[0].startsWith("-")
        val command = if (isBuildFlag) "build" else filteredArgs[0].lowercase()
        val commandArgs = if (isBuildFlag) filteredArgs else filteredArgs.drop(1)

        if (!noLogo && (command == "build" || command == "create" || command == "run")) {
            printLogo()
        }

        val exitCode = when (command) {
            "build" -> BuildCommand.execute(commandArgs, logger)
            "clean" -> CleanCommand.execute(commandArgs, logger)
            "create" -> CreateCommand.execute(commandArgs, logger)
            "deps" -> DepsCommand.execute(commandArgs, logger)
            "sync" -> SyncCommand.execute(commandArgs, logger)
            "tree" -> TreeCommand.execute(commandArgs, logger)
            "migrate" -> MigrateCommand.execute(commandArgs, logger)
            "daemon" -> DaemonCommand.execute(commandArgs, logger)
            "upgrade" -> UpgradeCommand.execute(commandArgs, logger)
            "test" -> TestCommand.execute(commandArgs, logger)
            "add" -> AddCommand.execute(commandArgs, logger)
            "run" -> RunCommand.execute(commandArgs, logger)
            "auth" -> AuthCommand.execute(commandArgs, logger)
            "generate", "gen" -> GenerateCommand.execute(commandArgs, logger)
            "repair" -> handleRepair(commandArgs)
            "info" -> handleInfo(commandArgs)
            "help" -> {
                if (!noLogo) printLogo()
                printUsage()
                0
            }
            "version" -> {
                println("Running on version ${VERSION.cyan()}")
                0
            }
            else -> {
                logger.err("Unknown command: $command")
                printUsage()
                1
            }
        }

        exitProcess(exitCode)
    }

    private fun handleRepair(args: List<String>): Int {
        val projectPath = args.firstOrNull { !it.startsWith("-") } ?: "."
        val projectDir = File(projectPath).canonicalFile
        val compiler = BoltCompiler { logger.info(it) }
        compiler.repairProjectStructure(projectDir)
        logger.info("${"Success!".green()} Project structure verified and repaired in: ${projectDir.path}")
        return 0
    }

    private fun handleInfo(args: List<String>): Int {
        val projectPath = args.firstOrNull { !it.startsWith("-") } ?: "."
        val projectDir = File(projectPath).canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")

        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        val config = ConfigParser().parse(ymlFile)
        val libsDir = LibLocator.findLibsDir(projectDir)

        println("\n==============================")
        println("  Bolt Extension Project Info")
        println("==============================")
        println("Path:         ${projectDir.path}")
        println("Author:       ${config.author}")
        println("Version:      ${config.version}")
        println("Min SDK:      ${config.minSdk}")
        println("Dependencies: ${config.dependencies.size}")
        println("StrGuard:     ${if (config.strguard.enabled) "Enabled" else "Disabled"}")
        println("Relocation:   ${if (config.relocation.enabled) "Enabled" else "Disabled"}")
        println("Desugaring:   ${if (config.coreLibraryDesugaring) "Enabled" else "Disabled"}")
        println("Toolchain:    ${libsDir.path}")
        println("==============================\n")
        return 0
    }

    fun printLogo() {
        val logo = """
        +===============================+
        | ___.            .__     __    |
        | \_ |__    ____  |  |  _/  |_  |
        |  | __ \  /  _ \ |  |  \   __\ |
        |  | \_\ \(  <_> )|  |__ |  |   |
        |  |___  / \____/ |____/ |__|   |
        |      \/                       |
        +===============================+
        """.trimIndent()

        println(logo.brightYellow())
        println(" (v$VERSION)".brightYellow())
        println("Built on $BUILT_ON".grey())
        println()
    }

    fun printUsage() {
        println("Build Faster, Compile Smarter.".white())
        println("An Efficient Framework for MIT App Inventor 2 Extensions.".white())
        println("Usage: bolt".brightYellow())
        println(" ${"<command>".green()} ${"[arguments]".magenta()}")
        println()

        println("Available commands:".green())
        val cmds = listOf(
            "help" to "Display help information for bolt.",
            "build" to "Builds the extension project in current working directory.",
            "clean" to "Deletes old build files and caches.",
            "create" to "Scaffolds a new extension project in the current working directory.",
            "deps" to "Work with project dependencies.",
            "sync" to "Syncs dev and project dependencies.",
            "tree" to "Prints the folder and file hierarchical structure of the current project.",
            "migrate" to "Migrates extension projects built with Rush v1 to Rush v2",
            "daemon" to "Manages the persistent Bolt Compiler Daemon.",
            "upgrade" to "Upgrades Bolt to the latest available version.",
            "test" to "Runs zero-code contract, helper, unit & multi-screen tests for the extension.",
            "add" to "Adds a dependency to bolt.yml automatically from Maven Central.",
            "run" to "Starts a Live Testing session for hot-reloading the extension to a mobile device.",
            "auth" to "Automated Extension Licensing System (Offline RSA)",
            "generate" to "Generates boilerplate code for the current project."
        )

        for ((name, desc) in cmds) {
            println("  ${name.green().padEnd(18)} $desc")
        }
        println()

        println("Available arguments:".magenta())
        println("       ${"-d".magenta()}  Pass it to enable verbose logging.")
        println("       ${"-r".magenta()}  Indicates the execution of the ProGuard task. Pass it with the ${"build".green()} command.")
        println("       ${"-b".magenta()}  Indicates to generate block PNG images for all extension functions. Pass with ${"build".green()}.")
        println("       ${"--screens".magenta()}  Target Window Size Classes (compact, medium, expanded, all). Pass with ${"test".green()}.")
        println("       ${"-s".magenta()}  Indicates the execution of the R8 shriker task. Pass it with the ${"build".green()} command.")
        println("       ${"-o".magenta()}  Indicates to optimize the extension size even there is no ProGuard. Pass it with the ${"build".green()} command.")
        println("      ${"-dx".magenta()}  Indicates to generate the DEX Bytecode by the R8 dexer. Pass it with the ${"build".green()} command.")
        println("       ${"-t".magenta()}  Indicates to scaffold a pre-filled extension skeleton (str, int). Pass it with the ${"create".green()} command.")
        println("   ${"helper".magenta()}  Generates a boilerplate helper enum. Pass it with the ${"generate".green()} command.")
        println("     ${"rush".magenta()}  Indicates that it's a rush project to execute with the ${"migrate".green()} command.")
        println("     ${"fast".magenta()}  Indicates that it's a fast project to execute with the ${"migrate".green()} command.")
        println(" ${"template".magenta()}  Indicates that it's an extension-template project to execute with the ${"migrate".green()} command.")
        println("      ${"ai2".magenta()}  Indicates that it's an App Inventor sources project to execute with the ${"migrate".green()} command.")
        println()

        println("Global options:".cyan())
        println("${"-h, --help".cyan()}          Print this usage information.")
        println("${"-v, --verbose".cyan()}       Turns on verbose logging.")
        println("${"-d, --debug".cyan()}         Pass it to enable verbose logging.")
        println("${"-c, --[no-]color".cyan()}    Whether output should be colorized or not. Defaults to true in terminals that support ANSI colors.")
        println("                    (defaults to on)")
        println("${"-V, --version".cyan()}       Prints the current version name.")
        println()
    }
}
