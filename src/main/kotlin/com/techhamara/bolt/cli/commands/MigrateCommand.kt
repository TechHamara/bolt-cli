package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.Main
import com.techhamara.bolt.cli.green
import com.techhamara.bolt.templates.ProjectTemplates
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MigrateCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        var projectPath = "."
        for (arg in args) {
            if (!arg.startsWith("-") && arg.lowercase() !in listOf("rush", "fast", "template", "ai2")) {
                projectPath = arg
            }
        }
        val projectDir = File(projectPath).canonicalFile

        val rushYml = File(projectDir, "rush.yml")
        val fastYml = File(projectDir, "fast.yml")
        val boltYml = File(projectDir, "bolt.yml")
        val srcDir = File(projectDir, "src").apply { mkdirs() }

        logger.info("Migrating project to Bolt v${Main.VERSION}")

        // 1. Pre-migration backup as .zip file for older Bolt, Rush, or Fast projects
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val backupFile = File(projectDir, "${projectDir.name}_backup_$timestamp.zip")
            createBackupZip(projectDir, backupFile)
            logger.info("Created pre-migration backup at ${backupFile.name.green()}")
        } catch (e: Exception) {
            logger.warn("Could not create backup: ${e.message}")
        }

        val orgPackage = guessPackageName(srcDir) ?: "com.example.${projectDir.name.lowercase().replace(Regex("[^a-z0-9_]"), "")}"

        // 2. Handle App Inventor component source structure reorganization if migrating from template/ai2
        val ai2ComponentsSrc = File(projectDir, "appinventor/components/src")
        if (ai2ComponentsSrc.exists() && ai2ComponentsSrc.isDirectory) {
          //  logger.info("Found App Inventor component source structure. Reorganizing to src/...")
            ai2ComponentsSrc.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.forEach { file ->
                val relPath = file.relativeTo(ai2ComponentsSrc).path
                val destFile = File(srcDir, relPath)
                destFile.parentFile?.mkdirs()
                file.copyTo(destFile, overwrite = true)
            }
        }

        val timeFormatter = SimpleDateFormat("h.mma dd.MM.yyyy", java.util.Locale.ENGLISH)
        val buildTimeStr = timeFormatter.format(Date()).lowercase()

        // 3. Migrate or Upgrade Configuration (bolt.yml)
        when {
            // Case A: Existing Bolt project - non-destructive upgrade preserving all user configurations
            boltYml.exists() && !rushYml.exists() && !fastYml.exists() -> {
                val currentContent = boltYml.readText(Charsets.UTF_8)
                val currentVer = extractBoltVersion(currentContent)
                if (!isOlderVersion(currentVer, Main.VERSION)) {
                    // logger.info("bolt.yml is already up-to-date (v$currentVer). Skipping configuration changes.")
                } else {
                    //  logger.info("Upgrading bolt.yml from v${currentVer ?: "legacy"} to Bolt v${Main.VERSION}...")
                    val upgraded = upgradeBoltYml(currentContent, orgPackage, buildTimeStr)
                    boltYml.writeText(upgraded, Charsets.UTF_8)
                    // logger.info("Preserved all dependencies, assets, and metadata from bolt.yml.")
                }
            }

            // Case B: Migrating from Rush (rush.yml)
            rushYml.exists() -> {
              //  logger.info("Found rush.yml - migrating Rush project configuration to bolt.yml...")
                val rushContent = rushYml.readText(Charsets.UTF_8)
                val migratedContent = convertRushToBolt(rushContent, orgPackage, buildTimeStr)
                boltYml.writeText(migratedContent, Charsets.UTF_8)
                rushYml.delete()
                // logger.info("Preserved all dependencies, assets, and metadata from rush.yml.")
            }

            // Case C: Migrating from Fast (fast.yml)
            fastYml.exists() -> {
              //  logger.info("Found fast.yml - migrating Fast project configuration to bolt.yml...")
                val fastContent = fastYml.readText(Charsets.UTF_8)
                val migratedContent = convertFastToBolt(fastContent, orgPackage, buildTimeStr)
                boltYml.writeText(migratedContent, Charsets.UTF_8)
                fastYml.delete()
               // logger.info("Preserved all dependencies, assets, and metadata from fast.yml.")
            }

            // Case D: Fresh/Unconfigured project
            else -> {
                logger.info("Generating modern bolt.yml for v${Main.VERSION}...")
                boltYml.writeText(ProjectTemplates.configYaml(false, orgPackage))
            }
        }

        // 4. Non-destructive AndroidManifest.xml handling
        val manifestFile = File(srcDir, "AndroidManifest.xml")
        if (manifestFile.exists() && manifestFile.length() > 20) {
          //  logger.info("Found existing AndroidManifest.xml - preserving all custom permissions & components.")
        } else {
            logger.info("Creating default AndroidManifest.xml...")
            manifestFile.writeText(ProjectTemplates.androidManifestXml(orgPackage), Charsets.UTF_8)
        }

        // 5. Non-destructive proguard-rules.pro handling
        val pgFile = File(srcDir, "proguard-rules.pro")
        if (pgFile.exists() && pgFile.length() > 10) {
          //  logger.info("Found existing proguard-rules.pro - preserving all custom optimization rules.")
            var pgText = pgFile.readText(Charsets.UTF_8)
            if (pgText.contains("-dontskipnonpubliclibraryclassmember") && !pgText.contains("-dontskipnonpubliclibraryclassmembers")) {
                pgText = pgText.replace("-dontskipnonpubliclibraryclassmember", "-dontskipnonpubliclibraryclassmembers")
                pgFile.writeText(pgText, Charsets.UTF_8)
               // logger.info("Fixed legacy ProGuard typo (-dontskipnonpubliclibraryclassmember -> -dontskipnonpubliclibraryclassmembers)")
            }
        } else {
            logger.info("Creating default proguard-rules.pro...")
            pgFile.writeText(ProjectTemplates.pgRules(orgPackage), Charsets.UTF_8)
        }

        // 6. Ensure assets directory exists and replace old icon.png with official Bolt CLI icon.png
        val assetsDir = File(projectDir, "assets").apply { mkdirs() }
        val iconFile = File(assetsDir, "icon.png")
        iconFile.writeBytes(ProjectTemplates.iconBytes)
       // logger.info("Updated assets/icon.png to Bolt CLI icon.png.")

        // 7. Ensure deps directory exists
        File(projectDir, "deps").apply { mkdirs() }

        // 8. Inject @DesignerComponent only if component class is missing it
        srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.forEach { compFile ->
            val text = compFile.readText(Charsets.UTF_8)
            if (text.contains("AndroidNonvisibleComponent") && !text.contains("@DesignerComponent")) {
                logger.info("Injecting missing @DesignerComponent into ${compFile.name}...")
                val importInjection = if (compFile.extension == "kt") {
                    "import com.google.appinventor.components.annotations.DesignerComponent\n"
                } else {
                    "import com.google.appinventor.components.annotations.DesignerComponent;\n"
                }
                val annotationBlock = """
                    @DesignerComponent(
                        version = 1,
                        versionName = "1.0",
                        description = "Migrated to Bolt CLI",
                        iconName = "icon.png"
                    )
                """.trimIndent() + "\n"

                var newText = if (!text.contains("com.google.appinventor.components.annotations.DesignerComponent")) {
                    text.replaceFirst(Regex("""(?m)^package\s+[^;\n]+[;\n]"""), "$0\n$importInjection")
                } else text

                if (compFile.extension == "kt") {
                    newText = newText.replaceFirst(Regex("""(?m)^(\s*(?:open\s+)?class\s+[a-zA-Z0-9_]+)"""), "$annotationBlock$1")
                } else {
                    newText = newText.replaceFirst(Regex("""public\s+class\s+([a-zA-Z0-9_]+)\s+extends\s+AndroidNonvisibleComponent"""), "$annotationBlock$0")
                }
                compFile.writeText(newText, Charsets.UTF_8)
            }
        }

        // 9. Clean up legacy cached hive/lock files
        listOf(".bolt/timestamps.hive", ".bolt/timestamps.lock", ".bolt/deps.hive", ".bolt/deps.lock", ".fast", ".rush").forEach { relPath ->
            val f = File(projectDir, relPath)
            if (f.exists()) {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
        }

        logger.info("${"Success!".green()} Project successfully migrated to Bolt v${Main.VERSION}.")
        logger.info("Syncing project dependencies...")
        return SyncCommand.execute(listOf(projectPath), logger)
    }

    private fun extractBoltVersion(content: String): String? {
        val m = Regex("(?m)^\\s*bolt_version\\s*:\\s*['\"]?([^'\"\\r\\n#]+)").find(content)
        return m?.groupValues?.get(1)?.trim()
    }

    private fun isOlderVersion(currentVer: String?, targetVer: String): Boolean {
        if (currentVer.isNullOrBlank()) return true
        val currParts = currentVer.trim().split('.').mapNotNull { it.toIntOrNull() }
        val targetParts = targetVer.trim().split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(currParts.size, targetParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val t = targetParts.getOrElse(i) { 0 }
            if (c < t) return true
            if (c > t) return false
        }
        return false
    }

    /**
     * Upgrades an existing bolt.yml to the latest version:
     * - Preserves ALL existing user configurations (dependencies, assets, provided_dependencies, minimize, strguard, ndk, etc.)
     * - Updates bolt_version and build_time
     * - Appends any missing modern configuration keys without breaking existing structure
     */
    private fun upgradeBoltYml(existing: String, orgPackage: String, buildTimeStr: String): String {
        var content = existing

        // 1. Update or add bolt_version
        val verRegex = Regex("(?m)^\\s*bolt_version\\s*:.*$")
        content = if (verRegex.containsMatchIn(content)) {
            verRegex.replace(content, "bolt_version: '${Main.VERSION}'")
        } else {
            "bolt_version: '${Main.VERSION}'\n$content"
        }

        // 2. Update or add build_time
        val timeRegex = Regex("(?m)^\\s*build_time\\s*:.*$")
        content = if (timeRegex.containsMatchIn(content)) {
            timeRegex.replace(content, "build_time: '$buildTimeStr'")
        } else {
            content.replaceFirst("bolt_version: '${Main.VERSION}'", "bolt_version: '${Main.VERSION}'\nbuild_time: '$buildTimeStr'")
        }

        // 3. Append missing modern compiler options if not present
        val missingAdditions = StringBuilder()

        if (!Regex("(?m)^\\s*#?\\s*compile_sdk\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Define the compile Android SDK API level.\n# compile_sdk: 35\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*desugar_dex\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# If enabled, the D8/R8 tool will generate desugared dex (classes.dex)\ndesugar_dex: true\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*R8\\s*:").containsMatchIn(content) && !Regex("(?m)^\\s*#?\\s*r8\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# If enabled, extension will be optimized using R8.\nR8: true\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*proguard\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# If enabled, extension will be optimized using ProGuard.\nproguard: false\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*deannonate\\s*:").containsMatchIn(content) && !Regex("(?m)^\\s*#?\\s*deannotate\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# If enabled, extension annotations will be stripped for smaller size.\ndeannonate: true\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*kotlin_version\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Kotlin Compiler version.\nkotlin_version: '1.9.22'\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*auto_version\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Enable to increment the version number of each component during build.\nauto_version: true\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*coreLibraryDesugaring\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Enable modern Java API support on older devices default\ncoreLibraryDesugaring: false\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*compile_time\\s*:").containsMatchIn(content) && !Regex("(?m)^\\s*#?\\s*compileTime\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Compile-time dependencies resolving for GradleResolver/MavenResolver [Local Only]\n# compile_time:\n#   - mylibrary.jar\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*repositories\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Default Maven repositories includes Maven Central, Google Maven, JitPack and\n# JCenter. Bolt will automatically add these to the resolver, so you rarely\n# need to mention them here. If the library you want to use is not available in\n# these repositories, you can add additional ones by specifying their URLs here.\n# repositories:\n#   - https://jitpack.io\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*provided_dependencies\\s*:").containsMatchIn(content) && !Regex("(?m)^\\s*#?\\s*providedDependencies\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Similar to dependencies, except libraries defined as provided are not included\n# in the final AIX. This is useful when you want to use a library in your\n# extension but don't want to include it in the final AIX because it's already\n# included in the App Inventor.\n# provided_dependencies:\n#   - com.example:foo-bar:1.2.3\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*minimize\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Minimization Exclusions explicitly exclude dependencies that use reflection/dynamic loading from being minimized.\n# minimize:\n#   exclude_dependency:\n#     - org.slf4j:slf4j-simple:.*\n#   exclude_project:\n#     - :api\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*homepage\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Homepage of your extension. This may be the announcement thread on community \n# forums or a link to your GitHub repository.\n# homepage: https://github.com/TechHamara/bolt-cli\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*relocation\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Implement Package Relocation (Shading).\nrelocation:\n  EnableAutoRelocation: true\n  skipStringConstants: true\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*strguard\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Bytecode-level string obfuscation tool, to protect hardcoded strings.\nstrguard:\n  enabled: false\n  key: \"TechHamara-MyKey-2026-Secret\"\n  packages:\n    - \"$orgPackage\"\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*xmls\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Attach custom XML to bundle it with APK resources (e.g. network security, file provider paths).\n# [Add your XML files in assets/xml/, assets/layout/, or assets/values/ folders]\n# xmls: \n#   - xml/network_security_config.xml\n#   - xml/provider_paths.xml\n")
        }
        if (!Regex("(?m)^\\s*#?\\s*ndk\\s*:").containsMatchIn(content)) {
            missingAdditions.append("\n# Native C/C++ (JNI & NDK) Support\n# ndk:\n#   enabled: true\n#   module: native-lib\n#   abis:\n#     - armeabi-v7a\n#     - arm64-v8a\n")
        }

        if (missingAdditions.isNotEmpty()) {
            content = content.trimEnd() + "\n" + missingAdditions.toString()
        }

        return content
    }

    /**
     * Converts a rush.yml configuration into a modern bolt.yml, preserving all dependencies and assets.
     */
    private fun convertRushToBolt(rushContent: String, orgPackage: String, buildTimeStr: String): String {
        // Extract author
        var author = ""
        val authorSingle = Regex("(?m)^\\s*author\\s*:\\s*['\"]?([^'\"\\r\\n#]+)").find(rushContent)?.groupValues?.get(1)?.trim()
        if (authorSingle != null) {
            author = authorSingle
        } else {
            val authorsBlock = Regex("(?s)authors\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(rushContent)
            if (authorsBlock != null) {
                val list = authorsBlock.groupValues[1].lines().mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
                }.filter { it.isNotEmpty() }
                author = list.joinToString(", ")
            }
        }

        // Extract min_sdk
        val minSdk = Regex("(?m)^\\s*min_sdk\\s*:\\s*(\\d+)").find(rushContent)?.groupValues?.get(1)?.toIntOrNull() ?: 14

        // Extract dependencies (deps: or dependencies:)
        val depsBlock = Regex("(?s)(?:deps|dependencies)\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(rushContent)
        val depsList = depsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        // Extract assets
        val assetsBlock = Regex("(?s)assets\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(rushContent)
        val assetsList = assetsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        // Extract xmls
        val xmlsBlock = Regex("(?s)xmls\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(rushContent)
        val xmlsList = xmlsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        val sb = StringBuilder()
        sb.append("# Author name.\n")
        if (author.isNotEmpty()) sb.append("author: '$author'\n")
        sb.append("\n# Bolt build metadata.\n")
        sb.append("bolt_version: '${Main.VERSION}'\n")
        sb.append("build_time: '$buildTimeStr'\n\n")
        sb.append("# The minimum Android SDK level your extension supports.\n")
        sb.append("min_sdk: $minSdk\n\n")
        sb.append("# Define the compile Android SDK API level.\n")
        sb.append("# compile_sdk: 35\n\n")
        sb.append("# If enabled, the D8/R8 tool will generate desugared dex (classes.dex)\n")
        sb.append("desugar_dex: true\n\n")
        sb.append("# If enabled, extension will be optimized using R8.\n")
        sb.append("R8: true\n\n")
        sb.append("# If enabled, extension will be optimized using ProGuard.\n")
        sb.append("proguard: false\n\n")
        sb.append("# If enabled, extension annotations will be stripped for smaller size.\n")
        sb.append("deannonate: true\n\n")
        sb.append("# Kotlin Compiler version.\n")
        sb.append("kotlin_version: '1.9.22'\n\n")
        sb.append("#desugar: true\n\n")

        sb.append("# External libraries your extension depends on.\n")
        if (depsList.isNotEmpty()) {
            sb.append("dependencies:\n")
            depsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("#dependencies:\n#- example.jar                 # Local JAR or AAR file stored in 'deps' directory\n#- com.example:foo-bar:1.2.3   # Coordinate of some remote Maven artifact\n")
        }
        sb.append("\n")

        sb.append("# Compile-time dependencies resolving for GradleResolver/MavenResolver [Local Only]\n")
        sb.append("# compile_time:\n#   - mylibrary.jar\n\n")

        sb.append("# Default Maven repositories includes Maven Central, Google Maven, JitPack and\n")
        sb.append("# JCenter. Bolt will automatically add these to the resolver, so you rarely\n")
        sb.append("# need to mention them here. If the library you want to use is not available in\n")
        sb.append("# these repositories, you can add additional ones by specifying their URLs here.\n")
        sb.append("# repositories:\n#   - https://jitpack.io\n\n")

        sb.append("# Assets that your extension needs. Every asset file must be stored in the assets\n")
        sb.append("# directory as well as declared here. Assets can be of any type.\n")
        if (assetsList.isNotEmpty()) {
            sb.append("assets:\n")
            assetsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("# assets:\n#   - data.json\n")
        }
        sb.append("\n")

        sb.append("# Attach custom XML to bundle it with APK resources (e.g. network security, file provider paths).\n")
        sb.append("# [Add your XML files in assets/xml/, assets/layout/, or assets/values/ folders]\n")
        if (xmlsList.isNotEmpty()) {
            sb.append("xmls:\n")
            xmlsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("# xmls: \n#   - xml/network_security_config.xml\n#   - xml/provider_paths.xml\n")
        }
        sb.append("\n")

        sb.append("# Similar to dependencies, except libraries defined as provided are not included\n")
        sb.append("# in the final AIX. This is useful when you want to use a library in your\n")
        sb.append("# extension but don't want to include it in the final AIX because it's already\n")
        sb.append("# included in the App Inventor.\n")
        sb.append("# provided_dependencies:\n#   - com.example:foo-bar:1.2.3\n\n")

        sb.append("# Minimization Exclusions explicitly exclude dependencies that use reflection/dynamic loading from being minimized.\n")
        sb.append("# minimize:\n#   exclude_dependency:\n#     - org.slf4j:slf4j-simple:.*\n#   exclude_project:\n#     - :api\n\n")

        sb.append("# Enable to increment the version number of each component during build.\n")
        sb.append("auto_version: true\n\n")

        sb.append("# Homepage of your extension. This may be the announcement thread on community \n")
        sb.append("# forums or a link to your GitHub repository.\n")
        sb.append("# homepage: https://github.com/TechHamara/bolt-cli\n\n")

        sb.append("# Bytecode-level string obfuscation tool, to protect hardcoded strings.\n")
        sb.append("strguard:\n  enabled: false\n  key: \"TechHamara-MyKey-2026-Secret\"\n  packages:\n    - \"$orgPackage\"\n\n")
        sb.append("# Implement Package Relocation (Shading).\nrelocation:\n  EnableAutoRelocation: true\n  skipStringConstants: true\n\n")
        sb.append("# Enable modern Java API support on older devices default\n")
        sb.append("coreLibraryDesugaring: false\n\n")

        sb.append("# Native C/C++ (JNI & NDK) Support\n# ndk:\n#   enabled: true\n#   module: native-lib\n#   abis:\n#     - armeabi-v7a\n#     - arm64-v8a\n")

        return sb.toString()
    }

    /**
     * Converts a fast.yml configuration into a modern bolt.yml, preserving all dependencies and assets.
     */
    private fun convertFastToBolt(fastContent: String, orgPackage: String, buildTimeStr: String): String {
        val author = Regex("(?m)^\\s*author\\s*:\\s*['\"]?([^'\"\\r\\n#]+)").find(fastContent)?.groupValues?.get(1)?.trim() ?: ""
        val minSdk = Regex("(?m)^\\s*min_sdk\\s*:\\s*(\\d+)").find(fastContent)?.groupValues?.get(1)?.toIntOrNull() ?: 14

        // Extract dependencies
        val depsBlock = Regex("(?s)dependencies\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(fastContent)
        val depsList = depsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        // Extract assets
        val assetsBlock = Regex("(?s)assets\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(fastContent)
        val assetsList = assetsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        // Extract xmls
        val fastXmlsBlock = Regex("(?s)xmls\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(fastContent)
        val fastXmlsList = fastXmlsBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        // Extract provided_dependencies
        val provBlock = Regex("(?s)provided_dependencies\\s*:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)").find(fastContent)
        val provList = provBlock?.groupValues?.get(1)?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) trimmed.removePrefix("-").trim().trim('"', '\'') else null
        }?.filter { it.isNotEmpty() } ?: emptyList()

        val sb = StringBuilder()
        sb.append("# Author name.\n")
        if (author.isNotEmpty()) sb.append("author: '$author'\n")
        sb.append("\n# Bolt build metadata.\n")
        sb.append("bolt_version: '${Main.VERSION}'\n")
        sb.append("build_time: '$buildTimeStr'\n\n")
        sb.append("# The minimum Android SDK level your extension supports.\n")
        sb.append("min_sdk: $minSdk\n\n")
        sb.append("# Define the compile Android SDK API level.\n")
        sb.append("# compile_sdk: 35\n\n")
        sb.append("# If enabled, the D8/R8 tool will generate desugared dex (classes.dex)\n")
        sb.append("desugar_dex: true\n\n")
        sb.append("# If enabled, extension will be optimized using R8.\n")
        sb.append("R8: true\n\n")
        sb.append("# If enabled, extension will be optimized using ProGuard.\n")
        sb.append("proguard: false\n\n")
        sb.append("# If enabled, extension annotations will be stripped for smaller size.\n")
        sb.append("deannonate: true\n\n")
        sb.append("# Kotlin Compiler version.\n")
        sb.append("kotlin_version: '1.9.22'\n\n")
        sb.append("#desugar: true\n\n")

        sb.append("# External libraries your extension depends on.\n")
        if (depsList.isNotEmpty()) {
            sb.append("dependencies:\n")
            depsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("#dependencies:\n#- example.jar                 # Local JAR or AAR file stored in 'deps' directory\n#- com.example:foo-bar:1.2.3   # Coordinate of some remote Maven artifact\n")
        }
        sb.append("\n")

        if (provList.isNotEmpty()) {
            sb.append("# Similar to dependencies, except libraries defined as provided are not included\n")
            sb.append("# in the final AIX. This is useful when you want to use a library in your\n")
            sb.append("# extension but don't want to include it in the final AIX because it's already\n")
            sb.append("# included in the App Inventor.\n")
            sb.append("provided_dependencies:\n")
            provList.forEach { sb.append("  - '$it'\n") }
            sb.append("\n")
        } else {
            sb.append("# Similar to dependencies, except libraries defined as provided are not included\n")
            sb.append("# in the final AIX. This is useful when you want to use a library in your\n")
            sb.append("# extension but don't want to include it in the final AIX because it's already\n")
            sb.append("# included in the App Inventor.\n")
            sb.append("# provided_dependencies:\n#   - com.example:foo-bar:1.2.3\n\n")
        }

        sb.append("# Compile-time dependencies resolving for GradleResolver/MavenResolver [Local Only]\n")
        sb.append("# compile_time:\n#   - mylibrary.jar\n\n")

        sb.append("# Default Maven repositories includes Maven Central, Google Maven, JitPack and\n")
        sb.append("# JCenter. Bolt will automatically add these to the resolver, so you rarely\n")
        sb.append("# need to mention them here. If the library you want to use is not available in\n")
        sb.append("# these repositories, you can add additional ones by specifying their URLs here.\n")
        sb.append("# repositories:\n#   - https://jitpack.io\n\n")

        sb.append("# Assets that your extension needs. Every asset file must be stored in the assets\n")
        sb.append("# directory as well as declared here. Assets can be of any type.\n")
        if (assetsList.isNotEmpty()) {
            sb.append("assets:\n")
            assetsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("# assets:\n#   - data.json\n")
        }
        sb.append("\n")

        sb.append("# Attach custom XML to bundle it with APK resources (e.g. network security, file provider paths).\n")
        sb.append("# [Add your XML files in assets/xml/, assets/layout/, or assets/values/ folders]\n")
        if (fastXmlsList.isNotEmpty()) {
            sb.append("xmls:\n")
            fastXmlsList.forEach { sb.append("  - '$it'\n") }
        } else {
            sb.append("# xmls: \n#   - xml/network_security_config.xml\n#   - xml/provider_paths.xml\n")
        }
        sb.append("\n")

        sb.append("# Minimization Exclusions explicitly exclude dependencies that use reflection/dynamic loading from being minimized.\n")
        sb.append("# minimize:\n#   exclude_dependency:\n#     - org.slf4j:slf4j-simple:.*\n#   exclude_project:\n#     - :api\n\n")

        sb.append("# Enable to increment the version number of each component during build.\n")
        sb.append("auto_version: true\n\n")

        sb.append("# Homepage of your extension. This may be the announcement thread on community \n")
        sb.append("# forums or a link to your GitHub repository.\n")
        sb.append("# homepage: https://github.com/TechHamara/bolt-cli\n\n")

        sb.append("# Bytecode-level string obfuscation tool, to protect hardcoded strings.\n")
        sb.append("strguard:\n  enabled: false\n  key: \"TechHamara-MyKey-2026-Secret\"\n  packages:\n    - \"$orgPackage\"\n\n")
        sb.append("# Implement Package Relocation (Shading).\nrelocation:\n  EnableAutoRelocation: true\n  skipStringConstants: true\n\n")
        sb.append("# Enable modern Java API support on older devices default\n")
        sb.append("coreLibraryDesugaring: false\n\n")

        sb.append("# Native C/C++ (JNI & NDK) Support\n# ndk:\n#   enabled: true\n#   module: native-lib\n#   abis:\n#     - armeabi-v7a\n#     - arm64-v8a\n")

        return sb.toString()
    }

    private fun guessPackageName(srcDir: File): String? {
        val javaOrKt = srcDir.walkTopDown().firstOrNull { it.isFile && (it.extension == "java" || it.extension == "kt") } ?: return null
        return try {
            val text = javaOrKt.readText(Charsets.UTF_8)
            val m = Regex("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;").find(text)
            m?.groupValues?.get(1)?.trim()
        } catch (_: Exception) { null }
    }

    private fun createBackupZip(projectDir: File, zipFile: File) {
        val excludeDirs = setOf(".bolt", "build", ".git", ".idea", ".vscode", "out", ".dart_tool", ".rush", ".fast")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val files = projectDir.walkTopDown()
                .filter { f ->
                    f.isFile && !f.name.endsWith(".zip") &&
                    !f.relativeTo(projectDir).path.split(File.separatorChar).any { excludeDirs.contains(it) }
                }
            for (f in files) {
                val relPath = f.relativeTo(projectDir).path.replace('\\', '/')
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}

