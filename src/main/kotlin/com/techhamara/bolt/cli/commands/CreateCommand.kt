package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.ConsolePrompt
import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.blue
import com.techhamara.bolt.cli.green
import com.techhamara.bolt.cli.yellow
import com.techhamara.bolt.templates.HelperTemplates
import com.techhamara.bolt.templates.ProjectTemplates
import java.io.File

object CreateCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        var rawName: String? = null
        var packageName: String? = null
        var author: String? = null
        var language: String? = null
        var template: String? = null

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-p", "--package" -> {
                    if (i + 1 < args.size) { packageName = args[++i] }
                }
                "-a", "--author" -> {
                    if (i + 1 < args.size) { author = args[++i] }
                }
                "-l", "--language" -> {
                    if (i + 1 < args.size) { language = args[++i] }
                }
                "-t", "--template" -> {
                    if (i + 1 < args.size) { template = args[++i] }
                }
                else -> {
                    if (!arg.startsWith("-") && rawName == null) {
                        rawName = arg
                    }
                }
            }
            i++
        }

        if (rawName == null) {
            rawName = ConsolePrompt.prompt("Extension name")
        }

        if (rawName.isBlank()) {
            logger.err("Extension name cannot be empty.")
            return 1
        }

        // Convert name to PascalCase
        val name = toPascalCase(rawName)
        val projectDir = File(name).canonicalFile

        if (projectDir.exists() && (projectDir.listFiles()?.isNotEmpty() == true)) {
            logger.err("Cannot create \"${projectDir.path}\" because it already exists and is not empty.")
            return 1
        }

        if (packageName.isNullOrBlank()) {
            packageName = ConsolePrompt.prompt("Package name", "com.example.${name.lowercase()}")
        }

        if (author == null) {
            author = ConsolePrompt.prompt("Author name", "")
        }

        if (language.isNullOrBlank()) {
            val langInput = ConsolePrompt.prompt("Language (Java/Kotlin) type k for Kotlin, Defaults to", "Java")
            language = if (langInput.trim().lowercase().startsWith("k")) "Kotlin" else "Java"
        }

        val isKotlin = language.trim().lowercase().startsWith("k")

        // Ensure package name ends with extension name lowercased if needed
        var finalPackage = packageName.trim()
        val lastPkgSegment = finalPackage.split(".").lastOrNull() ?: ""
        if (!lastPkgSegment.equals(name, ignoreCase = true)) {
            finalPackage = "$finalPackage.${name.lowercase()}"
        }

        val packageRelPath = finalPackage.replace('.', File.separatorChar)
        val srcDir = File(projectDir, "src/$packageRelPath").apply { mkdirs() }
        val assetsDir = File(projectDir, "assets").apply { mkdirs() }
        val depsDir = File(projectDir, "deps").apply { mkdirs() }
        val ideaDir = File(projectDir, ".idea").apply { mkdirs() }
        val ideaLibsDir = File(ideaDir, "libraries").apply { mkdirs() }
        val vscodeDir = File(projectDir, ".vscode").apply { mkdirs() }
        val workflowsDir = File(projectDir, ".github/workflows").apply { mkdirs() }

        // 1. Source code file
        if (isKotlin) {
            File(srcDir, "$name.kt").writeText(ProjectTemplates.getExtensionTempKt(name, finalPackage, author ?: "", template))
        } else {
            File(srcDir, "$name.java").writeText(ProjectTemplates.getExtensionTempJava(name, finalPackage, author ?: "", template))
        }

        // 2. Helper template if requested
        if (template != null && (template == "str" || template == "int")) {
            val helpersDir = File(srcDir, "helpers").apply { mkdirs() }
            val helperName = if (template == "str") "IntervalType.java" else "ModeType.java"
            File(helpersDir, helperName).writeText(HelperTemplates.getHelperTemplate(finalPackage, template))
        }

        // 3. AndroidManifest.xml & proguard-rules.pro
        File(projectDir, "src/AndroidManifest.xml").writeText(ProjectTemplates.androidManifestXml(finalPackage))
        File(projectDir, "src/proguard-rules.pro").writeText(ProjectTemplates.pgRules(finalPackage))

        // 4. bolt.yml & README.md & .gitignore
        File(projectDir, "bolt.yml").writeText(ProjectTemplates.configYaml(isKotlin, finalPackage, author ?: ""))
        File(projectDir, "README.md").writeText(ProjectTemplates.readmeMd(name))
        File(projectDir, ".gitignore").writeText(ProjectTemplates.dotGitignore)
        File(depsDir, ".placeholder").writeText("This directory stores your extension's local dependencies.")
        File(workflowsDir, "main.yml").writeText(ProjectTemplates.githubActionsYaml(name))

        // 5. IDE files
        val paramCaseName = toParamCase(name)
        File(ideaDir, "misc.xml").writeText(ProjectTemplates.ijMiscXml())
        File(ideaDir, "modules.xml").writeText(ProjectTemplates.ijModulesXml(paramCaseName))
        File(ideaDir, "$paramCaseName.iml").writeText(ProjectTemplates.ijImlXml())
        File(ideaLibsDir, "local-deps.xml").writeText(ProjectTemplates.ijLocalDepsXml())

        File(vscodeDir, "settings.json").writeText(ProjectTemplates.vscodeSettingsJson())
        File(projectDir, ".project").writeText(ProjectTemplates.dotProject(paramCaseName))
        File(projectDir, ".classpath").writeText(ProjectTemplates.dotClasspath())

        // 6. icon.png
        val defaultIcon = File("assets/icon.png")
        val iconBytes = if (defaultIcon.exists() && defaultIcon.isFile) defaultIcon.readBytes() else ProjectTemplates.iconBytes
        File(assetsDir, "icon.png").writeBytes(iconBytes)

        logger.log("${"Success!".green()} Generated a new extension project in ${projectDir.name.blue()}.")
        logger.log("  Next up,")
        logger.log("  - ${"cd".yellow()} into ${projectDir.name.blue()}, and")
        logger.log("  - run ${"bolt build".yellow()} to build your extension.\n")

        return 0
    }

    private fun toPascalCase(str: String): String {
        return str.split(Regex("[^a-zA-Z0-9]"))
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifEmpty { "MyExtension" }
    }

    private fun toParamCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
    }
}
