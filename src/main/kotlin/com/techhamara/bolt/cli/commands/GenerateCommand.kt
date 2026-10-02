package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.blue
import com.techhamara.bolt.templates.HelperTemplates
import java.io.File

object GenerateCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        val isForce = args.any { it == "-f" || it == "--force" }
        val nonFlagArgs = args.filter { it != "-f" && it != "--force" }

        if (nonFlagArgs.isEmpty()) {
            logger.err("Usage: bolt generate helper <str|int> [HelperName] [-f|--force]")
            return 64
        }

        val sub = nonFlagArgs[0].lowercase()
        if (sub != "helper") {
            logger.err("Unknown generate command: $sub. Allowed: helper")
            return 64
        }

        val type = nonFlagArgs.getOrNull(1)?.lowercase()
        if (type != "str" && type != "int") {
            logger.err("Invalid helper type. Allowed values are: str, int (e.g. 'bolt generate helper str')")
            return 64
        }

        val projectDir = File(".").canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")
        val manifestFile = File(projectDir, "src/AndroidManifest.xml")

        if (!ymlFile.exists() || !manifestFile.exists()) {
            logger.err("Not a Bolt project. Please run this command inside an existing project.")
            return 1
        }

        val manifestContent = manifestFile.readText(Charsets.UTF_8)
        val match = Regex("""package="([^"]+)"""").find(manifestContent)
        val orgName = match?.groupValues?.get(1)

        if (orgName == null) {
            logger.err("Could not find package name in AndroidManifest.xml")
            return 1
        }

        val extRelPath = orgName.replace('.', File.separatorChar)
        val helpersDir = File(projectDir, "src/$extRelPath/helpers").apply { mkdirs() }

        val customNameRaw = nonFlagArgs.getOrNull(2)
        val defaultBaseName = if (type == "str") "IntervalType" else "ModeType"
        val baseName = if (!customNameRaw.isNullOrBlank()) {
            val sanitized = customNameRaw.replace(Regex("[^a-zA-Z0-9_]"), "")
            if (sanitized.isNotEmpty()) sanitized.replaceFirstChar { it.uppercase() } else defaultBaseName
        } else {
            defaultBaseName
        }

        var targetName = baseName
        var helperFile = File(helpersDir, "$targetName.java")

        if (helperFile.exists()) {
            if (isForce) {
                logger.info("Overwriting existing helper file ${helperFile.name.blue()} (--force)")
            } else {
                var counter = 1
                var uniqueName = "${baseName}${counter}"
                while (File(helpersDir, "$uniqueName.java").exists()) {
                    counter++
                    uniqueName = "${baseName}${counter}"
                }
                logger.warn("Helper file ${helperFile.name} already exists. Generating ${uniqueName.blue()}.java instead (use -f to overwrite).")
                targetName = uniqueName
                helperFile = File(helpersDir, "$targetName.java")
            }
        }

        helperFile.writeText(HelperTemplates.getHelperTemplate(orgName, type, targetName))
        logger.info("Successfully generated helper class at ${helperFile.relativeTo(projectDir).path.blue()}")

        // Inject helper imports and demo @SimpleProperty method only into actual @DesignerComponent files
        val srcDir = File(projectDir, "src")
        var mainFiles = srcDir.walkTopDown().filter { file ->
            file.isFile && (file.extension == "java" || file.extension == "kt") &&
            !file.path.contains("helpers") &&
            file.readText(Charsets.UTF_8).contains("@DesignerComponent")
        }.toList()

        // Fallback if @DesignerComponent not found
        if (mainFiles.isEmpty()) {
            mainFiles = srcDir.walkTopDown().filter { file ->
                file.isFile && (file.extension == "java" || file.extension == "kt") &&
                !file.path.contains("helpers") &&
                (file.nameWithoutExtension.equals(projectDir.name, ignoreCase = true) || file.nameWithoutExtension.equals(orgName.substringAfterLast('.'), ignoreCase = true))
            }.toList()
        }

        for (mainFile in mainFiles) {
            var text = mainFile.readText(Charsets.UTF_8)
            val isKt = mainFile.extension == "kt"
            var modified = false

            // 1. Add imports if missing
            val helperImport = if (isKt) "import $orgName.helpers.*" else "import $orgName.helpers.*;"
            val optionsImport = if (isKt) "import com.google.appinventor.components.annotations.Options" else "import com.google.appinventor.components.annotations.Options;"
            val propImport = if (isKt) "import com.google.appinventor.components.annotations.SimpleProperty" else "import com.google.appinventor.components.annotations.SimpleProperty;"

            val importsToAdd = mutableListOf<String>()
            if (!text.contains("$orgName.helpers")) importsToAdd.add(helperImport)
            if (!text.contains("annotations.Options")) importsToAdd.add(optionsImport)
            if (!text.contains("annotations.SimpleProperty")) importsToAdd.add(propImport)

            if (importsToAdd.isNotEmpty()) {
                val packageMatch = Regex("""package\s+[^;]+;?""").find(text)
                if (packageMatch != null) {
                    val insertPos = packageMatch.range.last + 1
                    text = text.substring(0, insertPos) + "\n\n" + importsToAdd.joinToString("\n") + text.substring(insertPos)
                    modified = true
                }
            }

            // 2. Add demo @SimpleProperty function if missing
            val funcName = if (targetName == "IntervalType") "DemoInterval"
                           else if (targetName == "ModeType") "DemoType"
                           else "Demo$targetName"

            if (!text.contains(funcName)) {
                val methodCode = if (isKt) {
                    if (type == "int") {
                        """
    @SimpleProperty(
        description = "Set helper $targetName type (0=Demo1, 1=Demo2, 2=Demo3)"
    )
    fun $funcName(@Options($targetName::class) style: Int) {
        // add your function here
    }
"""
                    } else {
                        """
    @SimpleProperty(
        description = "Set helper $targetName type (Demo1, Demo2, Demo3)"
    )
    fun $funcName(@Options($targetName::class) style: String) {
        // add your function here
    }
"""
                    }
                } else {
                    if (type == "int") {
                        """
    @SimpleProperty(
        description = "Set helper $targetName type (0=Demo1, 1=Demo2, 2=Demo3)"
    )
    public void $funcName(@Options($targetName.class) int style) {
        // add your function here
    }
"""
                    } else {
                        """
    @SimpleProperty(
        description = "Set helper $targetName type (Demo1, Demo2, Demo3)"
    )
    public void $funcName(@Options($targetName.class) String style) {
        // add your function here
    }
"""
                    }
                }

                val lastBrace = text.lastIndexOf('}')
                if (lastBrace != -1) {
                    text = text.substring(0, lastBrace) + "\n" + methodCode + "\n" + text.substring(lastBrace)
                    modified = true
                }
            }

            if (modified) {
                mainFile.writeText(text, Charsets.UTF_8)
                logger.info("Added helper import and demo @SimpleProperty function to ${mainFile.name.blue()}")
            }
        }

        return 0
    }
}
