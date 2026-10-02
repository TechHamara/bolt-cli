package com.techhamara.bolt.compiler

import com.techhamara.bolt.packager.AixPackager
import com.techhamara.bolt.parser.AnnotationParser.ComponentInfo
import com.techhamara.bolt.parser.ConfigParser
import com.techhamara.bolt.parser.ManifestParser
import java.io.File

/**
 * Executes Guardsquare ProGuard 7.10.0 for shrinking, bytecode optimization, and repackaging.
 * Triggered by `bolt build -r`, `bolt build --proguard`, or `proguard: true` in bolt.yml.
 */
object ProGuardRunner {

    fun compile(
        projectDir: File,
        buildDir: File,
        binDir: File,
        proguardFile: File,
        config: ConfigParser.BoltConfig,
        libsDir: File,
        androidJar: File,
        stubsJar: File,
        kotlinStdlibJar: File,
        kawaJar: File?,
        providedLibFiles: List<File>,
        compileOnlyNames: Set<String>,
        depsDir: File,
        componentList: List<ComponentInfo>,
        packageName: String,
        keepManifest: Boolean,
        manifestData: ManifestParser.ManifestData,
        logger: (String) -> Unit
    ): Boolean {
        val classFiles = binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        if (classFiles.isEmpty()) {
            logger("debug No .class files found for ProGuard optimization.")
            return false
        }

        // 1. Discover ProGuard jar
        val toolsDir = File(libsDir, "tools")
        val proguardDir = File(libsDir, "proguard")
        val boltHome = BoltLocator.getBoltHome()
        val userHome = File(System.getProperty("user.home"))

        val proguardJar = LibLocator.findLib(libsDir, "proguard.jar")
            ?: File(proguardDir, "proguard.jar").takeIf { it.exists() }
            ?: File(toolsDir, "proguard.jar").takeIf { it.exists() }
            ?: File(boltHome, "libs/proguard/proguard.jar").takeIf { it.exists() }
            ?: File(boltHome, "libs/tools/proguard.jar").takeIf { it.exists() }
            ?: File(userHome, ".bolt/libs/proguard/proguard.jar").takeIf { it.exists() }
            ?: File(userHome, ".bolt/libs/tools/proguard.jar").takeIf { it.exists() }

        if (proguardJar == null || !proguardJar.exists()) {
            logger("warning ProGuard jar (proguard.jar) not found; falling back to R8/default...")
            return false
        }

        logger("- Running ProGuard...")

        val tempPgIn = File(buildDir, "pg_in.jar")
        val tempPgOut = File(buildDir, "pg_out.jar")
        val mergedRulesFile = File(buildDir, "proguard_merged.pro")

        if (tempPgIn.exists()) tempPgIn.delete()
        if (tempPgOut.exists()) tempPgOut.delete()

        try {
            // 2. Package compiled .class files into temp input jar
            AixPackager.zipDirectory(binDir, tempPgIn)

            // 3. Collect library jars
            val libJars = mutableListOf<File>()
            if (androidJar.exists()) libJars.add(androidJar)
            if (stubsJar.exists()) libJars.add(stubsJar)
            if (kotlinStdlibJar.exists()) libJars.add(kotlinStdlibJar)
            if (kawaJar != null && kawaJar.exists()) libJars.add(kawaJar)

            for (lib in providedLibFiles) {
                if (lib.exists() && !libJars.contains(lib)) {
                    libJars.add(lib)
                }
            }

            depsDir.listFiles { _, name -> name.endsWith(".jar") }?.forEach { depJar ->
                val isCompileOnly = compileOnlyNames.contains(depJar.name) ||
                        config.compileTime.any { it.isNotEmpty() && depJar.name.contains(it) } ||
                        config.providedDependencies.any { it.isNotEmpty() && depJar.name.contains(it.substringAfterLast(':')) }
                if (isCompileOnly && !libJars.contains(depJar)) {
                    libJars.add(depJar)
                }
            }

            // 4. Build merged ProGuard configuration
            val rules = StringBuilder()
            rules.appendLine("# Auto-generated ProGuard Configuration for Bolt Compiler")
            rules.appendLine("-injars \"${tempPgIn.canonicalPath}\"")
            rules.appendLine("-outjars \"${tempPgOut.canonicalPath}\"")
            rules.appendLine()

            // Add library jars
            for (lib in libJars) {
                rules.appendLine("-libraryjars \"${lib.canonicalPath}\"")
            }
            rules.appendLine()

            // Safe base optimization directives
            rules.appendLine("-dontnote **")
            rules.appendLine("-dontwarn **")
            rules.appendLine("-ignorewarnings")
            rules.appendLine("-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,Deprecated,SourceFile,LineNumberTable")
            rules.appendLine()

            // App Inventor Component Keep Rules
            rules.appendLine("# Keep App Inventor Component Interfaces and Base Classes")
            rules.appendLine("-keep class * implements com.google.appinventor.components.common.OptionList { *; }")
            rules.appendLine("-keepclassmembers enum * {")
            rules.appendLine("    public static **[] values();")
            rules.appendLine("    public static ** valueOf(java.lang.String);")
            rules.appendLine("    public *;")
            rules.appendLine("}")
            rules.appendLine()

            // Keep all detected extension components (only the main @DesignerComponent classes and their public/protected API)
            if (componentList.isNotEmpty()) {
                for (comp in componentList) {
                    rules.appendLine("# Keep Extension Component API: ${comp.type}")
                    rules.appendLine("-keep public class ${comp.type} {")
                    rules.appendLine("    public <init>(...);")
                    rules.appendLine("    public protected *;")
                    rules.appendLine("}")
                }
            } else {
                rules.appendLine("-keep public class * extends com.google.appinventor.components.runtime.AndroidNonvisibleComponent { public *; protected *; }")
                rules.appendLine("-keep public class * extends com.google.appinventor.components.runtime.AndroidViewComponent { public *; protected *; }")
                rules.appendLine("-keep public class * extends com.google.appinventor.components.runtime.Component { public *; protected *; }")
            }
            rules.appendLine()

            // Record obfuscation mapping
            val mappingFile = File(buildDir, "mapping.txt")
            rules.appendLine("-printmapping \"${mappingFile.canonicalPath}\"")
            rules.appendLine()

            // Keep manifest components if requested
            if (keepManifest) {
                rules.appendLine("# Keep manifest components (-m / --keep-manifest)")
                val classRegex = Regex("""android:name="([^"]+)"""")
                val manifestXmlEntries = manifestData.activities + manifestData.activityAliases + manifestData.services + manifestData.receivers + manifestData.providers
                for (entry in manifestXmlEntries) {
                    val m = classRegex.find(entry)
                    if (m != null) {
                        val cls = m.groupValues[1]
                        if (cls.isNotEmpty() && !cls.startsWith("@")) {
                            rules.appendLine("-keep public class $cls extends android.app.Activity { *; }")
                            rules.appendLine("-keep public class $cls extends android.app.Service { *; }")
                            rules.appendLine("-keep public class $cls extends android.content.BroadcastReceiver { *; }")
                            rules.appendLine("-keep public class $cls extends android.content.ContentProvider { *; }")
                            rules.appendLine("-keep class $cls { *; }")
                        }
                    }
                }
                rules.appendLine()
            }

            // Exclude dependencies and projects from config.minimize
            if (config.minimize.excludeDependency.isNotEmpty() || config.minimize.excludeProject.isNotEmpty()) {
                rules.appendLine("# User minimize exclusions from bolt.yml")
                config.minimize.excludeDependency.forEach { rules.appendLine("-keep class $it.** { *; }") }
                config.minimize.excludeProject.forEach { rules.appendLine("-keep class $it.** { *; }") }
                rules.appendLine()
            }

            // Include project's custom rules from src/proguard-rules.pro
            if (proguardFile.exists()) {
                val pgContent = proguardFile.readText(Charsets.UTF_8)
                if (pgContent.contains("-dontskipnonpubliclibraryclassmemberss")) {
                    proguardFile.writeText(pgContent.replace("-dontskipnonpubliclibraryclassmemberss", "-dontskipnonpubliclibraryclassmembers"), Charsets.UTF_8)
                } else if (pgContent.contains("-dontskipnonpubliclibraryclassmember") && !pgContent.contains("-dontskipnonpubliclibraryclassmembers")) {
                    proguardFile.writeText(pgContent.replace("-dontskipnonpubliclibraryclassmember", "-dontskipnonpubliclibraryclassmembers"), Charsets.UTF_8)
                }
                rules.appendLine("# Include project custom rules")
                rules.appendLine("-include \"${proguardFile.canonicalPath}\"")
                rules.appendLine()
            }

            mergedRulesFile.writeText(rules.toString(), Charsets.UTF_8)

            // 5. Execute ProGuard via ProcessBuilder
            val pb = ProcessBuilder(
                "java",
                "-Dfile.encoding=UTF-8",
                "-jar",
                proguardJar.canonicalPath,
                "@" + mergedRulesFile.canonicalPath
            )
            pb.directory(projectDir)
            pb.redirectErrorStream(true)

            val proc = pb.start()
            val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isNotEmpty() && !l.startsWith("Picked up _JAVA_OPTIONS")) {
                    if (l.startsWith("Error:") || l.startsWith("Unexpected error") || l.contains("proguard.ParseException")) {
                        logger("error $l")
                    } else if (l.startsWith("Warning:")) {
                        logger("debug $l")
                    } else if (l.startsWith("Note:")) {
                        logger("debug $l")
                    }
                }
            }

            val exitCode = proc.waitFor()
            if (exitCode != 0 || !tempPgOut.exists()) {
                logger("error ProGuard processing failed with exit code $exitCode.")
                return false
            }

            // 6. Unzip optimized classes back into binDir
            binDir.deleteRecursively()
            binDir.mkdirs()
            AixPackager.unzipClassesOnly(tempPgOut, binDir)

            // Copy obfuscation mapping for developer inspection
            if (mappingFile.exists()) {
                try {
                    val dotBolt = File(projectDir, ".bolt").apply { mkdirs() }
                    mappingFile.copyTo(File(dotBolt, "mapping.txt"), overwrite = true)
                    val outDir = File(projectDir, "out").apply { mkdirs() }
                    mappingFile.copyTo(File(outDir, "mapping.txt"), overwrite = true)
                } catch (_: Exception) {}
            }

            // Clean up temporary files
            try { tempPgIn.delete() } catch (_: Exception) {}
            try { tempPgOut.delete() } catch (_: Exception) {}
            try { mergedRulesFile.delete() } catch (_: Exception) {}

            logger("- ProGuard processing completed successfully.")
            return true
        } catch (e: Exception) {
            logger("error ProGuard exception: ${e.message}")
            try { tempPgIn.delete() } catch (_: Exception) {}
            try { tempPgOut.delete() } catch (_: Exception) {}
            return false
        }
    }
}
