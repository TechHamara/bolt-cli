package com.techhamara.bolt.compiler

import com.google.gson.GsonBuilder
import com.techhamara.bolt.packager.AixPackager
import com.techhamara.bolt.parser.AnnotationParser
import com.techhamara.bolt.parser.ConfigParser
import com.techhamara.bolt.parser.ManifestParser
import com.techhamara.bolt.resolver.MavenResolver
import java.io.*
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.objectweb.asm.*

/**
 * Universal, standalone Bolt Compiler Engine.
 * Runs on Windows, macOS, Linux, and Android Termux without any Android framework dependencies.
 */
class BoltCompiler(
    private val libsDir: File = LibLocator.findLibsDir(),
    private val logger: (String) -> Unit = { println(it) }
) {

    data class Diagnostic(
        val isError: Boolean,
        val filePath: String,
        val line: Int,
        val message: String
    )

    data class BuildResult(
        val success: Boolean,
        val aixFile: File?,
        val diagnostics: List<Diagnostic>,
        val durationMs: Long
    )

    // ==========================================
    // Core Compile Pipeline
    // ==========================================
    fun build(
        projectDir: File,
        enableR8: Boolean = false,
        enableProguard: Boolean = false,
        enableDeannotate: Boolean = false,
        generateDex: Boolean = false,
        keepManifest: Boolean = false,
        generateBlocks: Boolean = false
    ): BuildResult {
        val startTime = System.currentTimeMillis()
        val timeFormatter = java.text.SimpleDateFormat("h.mma dd.MM.yyyy", java.util.Locale.ENGLISH)
        val buildTimeStr = timeFormatter.format(java.util.Date()).lowercase()
        val boltVersionStr = "2.0.0"

        if (!projectDir.exists()) {
            logger("error Project directory does not exist: ${projectDir.absolutePath}")
            return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "Directory not found")), 0)
        }

        logger("debug === Bolt CLI Compiler v$boltVersionStr ===")
        logger("debug Time: ${Date()}")
        logger("debug OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
        logger("debug Project: ${projectDir.name}")
        logger("debug Libs: ${libsDir.absolutePath}")

        if (!libsDir.exists()) {
            logger("error Toolchain libs directory not found: ${libsDir.absolutePath}")
            logger("error Please ensure 'libs/' exists or set --libs <path> or BOLT_LIBS_DIR.")
            return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "libs directory not found")), 0)
        }

        // Remove legacy build/ folder if it exists in the project root
        val legacyBuild = File(projectDir, "build")
        if (legacyBuild.exists()) {
            try { legacyBuild.deleteRecursively() } catch (_: Exception) {}
        }

        // All build outputs and intermediates are stored inside project root's .bolt/ directory
        val buildDir = File(projectDir, ".bolt").apply { mkdirs() }
        val binDir = File(buildDir, "bin").apply { deleteRecursively(); mkdirs() }
        val genDir = File(buildDir, "gen")
        if (genDir.exists() && (genDir.list()?.isEmpty() == true)) {
            genDir.delete()
        }
        val outDir = File(projectDir, "out").apply { mkdirs() }
        val depsDir = File(projectDir, "deps").apply { mkdirs() }
        val srcDir = File(projectDir, "src").apply { mkdirs() }
        val boltDir = File(buildDir, "staging").apply { deleteRecursively(); mkdirs() }

        // 1. Repair Project Structure
        repairProjectStructure(projectDir)

        // 2. Parse Manifest & Config
        val manifestFile = File(srcDir, "AndroidManifest.xml")
        val ymlFile = File(projectDir, "bolt.yml")
        val proguardFile = File(srcDir, "proguard-rules.pro")

        val fallbackPackage = guessPackageName(srcDir)
        val manifestParser = ManifestParser()
        val manifestData = manifestParser.parse(manifestFile, fallbackPackage)
        val packageName = manifestData.packageName

        logger("debug Package name is: $packageName")

        val configParser = ConfigParser()
        val config = configParser.parse(ymlFile, packageName)

        if (config.autoVersion) {
            logger("- Increasing Components version")
            incrementComponentVersions(srcDir)
        }

        // Automatically update bolt_version and build_time in bolt.yml
        updateBoltYmlMetadata(ymlFile, boltVersionStr, buildTimeStr)

        val boltPkgDir = File(boltDir, packageName).apply { mkdirs() }

        // 3. Compile AIDL if present
        val hasAidl = srcDir.walkTopDown().any { it.isFile && it.extension == "aidl" }
        if (hasAidl) {
            genDir.mkdirs()
            if (!AidlRunner.compile(srcDir, genDir, libsDir, logger)) {
                logger("warning AIDL compilation had issues; continuing...")
            }
        } else if (genDir.exists() && (genDir.list()?.isEmpty() == true)) {
            genDir.delete()
        }

        // 3b. Compile Native C/C++ (Mini-NDK) if enabled
        if (config.ndk.enabled) {
            val ndkResult = NdkRunner.compile(projectDir, buildDir, config, libsDir, logger)
            if (!ndkResult.success) {
                logger("error Native C/C++ compilation failed: ${ndkResult.error}")
                return BuildResult(false, null, listOf(Diagnostic(true, "", 0, ndkResult.error ?: "NDK build failed")), System.currentTimeMillis() - startTime)
            }
        }

        // 4. Source Files Discovery
        val genJavaFiles = if (genDir.exists()) genDir.walkTopDown().filter { it.isFile && it.extension == "java" }.map { it.absolutePath }.toList() else emptyList()
        val javaFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "java" }.map { it.absolutePath }.toList() + genJavaFiles
        val kotlinFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.absolutePath }.toList()

        if (javaFiles.isEmpty() && kotlinFiles.isEmpty()) {
            logger("error No Java or Kotlin source files found in src/")
            return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "No source files found in src/")), System.currentTimeMillis() - startTime)
        }

        // 5. Toolchain Libraries Verification
        val toolsDir = File(libsDir, "tools")
        val androidJar = if (config.compileSdk > 0) {
            LibLocator.findLib(libsDir, "android-${config.compileSdk}.jar", "android.jar")
                ?: File(System.getenv("ANDROID_HOME") ?: "", "platforms/android-${config.compileSdk}/android.jar").takeIf { it.exists() }
                ?: File(System.getenv("ANDROID_SDK_ROOT") ?: "", "platforms/android-${config.compileSdk}/android.jar").takeIf { it.exists() }
                ?: LibLocator.findLib(libsDir, "android.jar")
                ?: File(toolsDir, "android.jar")
        } else {
            LibLocator.findLib(libsDir, "android.jar") ?: File(toolsDir, "android.jar")
        }
        val stubsJar = LibLocator.findLib(libsDir, "appinventor-stubs-v3.jar") ?: File(toolsDir, "appinventor-stubs-v3.jar")
        val depsStdlib = (depsDir.listFiles() ?: emptyArray()).firstOrNull { 
            it.isFile && it.extension == "jar" && it.name.startsWith("kotlin-stdlib") 
        }
        val kotlinStdlibJar = depsStdlib ?: LibLocator.findLib(libsDir, "kotlin-stdlib.jar") ?: File(toolsDir, "kotlin-stdlib.jar")
        val annotationsJar = LibLocator.findLib(libsDir, "annotations.jar") ?: File(toolsDir, "annotations.jar")
        val desugarConfigJson = LibLocator.findLib(libsDir, "desugar_jdk_libs_configuration.json") ?: File(toolsDir, "desugar_jdk_libs_configuration.json")

        val kawaJar = LibLocator.findLib(libsDir, "kawa.jar", "kawa-1.11-modified.jar")

        // 6. Build Classpath
        val classpathEntries = mutableListOf<String>()
        if (androidJar.exists()) classpathEntries.add(androidJar.absolutePath)
        if (stubsJar.exists()) classpathEntries.add(stubsJar.absolutePath)
        if (kawaJar != null && kawaJar.exists()) classpathEntries.add(kawaJar.absolutePath)
        if (kotlinStdlibJar.exists() && !classpathEntries.contains(kotlinStdlibJar.absolutePath)) classpathEntries.add(kotlinStdlibJar.absolutePath)
        if (annotationsJar.exists()) classpathEntries.add(annotationsJar.absolutePath)

        // Automatically discover and include App Inventor & AndroidX provided runtime libraries from libs/
        val providedLibFiles = mutableListOf<File>()

        val toolchainPrefixes = setOf(
            "ecj", "r8", "d8", "dx", "bundletool", "apksigner", "asm-", "kotlin-compiler",
            "jarjar", "strguard", "junit", "trove4j", "desugar_jdk_libs", "annotationprocessors",
            "annotations-processor", "android-", "android.jar", "appinventor-stubs", "kawa",
            "kotlin-stdlib", "annotations.jar"
        )

        val androidXOrRuntimePrefixes = setOf(
            "androidruntime", "androidx", "annotation", "appcompat", "asynclayoutinflater",
            "cardview", "collection", "constraintlayout", "coordinatorlayout", "core",
            "cursoradapter", "customview", "documentfile", "drawerlayout", "dynamicanimation",
            "fragment", "interpolator", "legacy-support", "lifecycle", "loader",
            "localbroadcastmanager", "print", "recyclerview", "slidingpanelayout",
            "swiperefreshlayout", "vectordrawable", "versionedparcelable", "viewpager"
        )

        val libsDirFiles = libsDir.listFiles() ?: emptyArray()
        val rawProvidedFiles = mutableListOf<File>()
        for (f in libsDirFiles) {
            if (f.isFile && (f.extension.equals("jar", ignoreCase = true) || f.extension.equals("aar", ignoreCase = true))) {
                val lowerName = f.name.lowercase()
                val isToolchain = toolchainPrefixes.any { lowerName.startsWith(it) || lowerName == it }
                val isAndroidXOrRuntime = androidXOrRuntimePrefixes.any { lowerName.startsWith(it) }
                if (!isToolchain && isAndroidXOrRuntime) {
                    if (f.extension.equals("jar", ignoreCase = true)) {
                        rawProvidedFiles.add(f)
                    } else if (f.extension.equals("aar", ignoreCase = true)) {
                        val tempAarJar = File(buildDir, "aar_provided_${f.nameWithoutExtension}.jar")
                        if (AixPackager.extractAarClasses(f, tempAarJar)) {
                            rawProvidedFiles.add(tempAarJar)
                        }
                    }
                }
            }
        }

        // Deduplicate provided libraries by normalized prefix (e.g. appcompat vs appcompat-1.0.0, core vs core-1.9.0)
        val groupedProvided = rawProvidedFiles.groupBy { file ->
            file.nameWithoutExtension.lowercase().replace(Regex("-\\d+(\\.\\d+)*.*$"), "")
        }
        for ((_, group) in groupedProvided) {
            val chosen = group.maxWithOrNull(compareBy<File> { it.nameWithoutExtension.contains(Regex("\\d")) }.thenBy { it.length() })
            if (chosen != null) {
                providedLibFiles.add(chosen)
            }
        }

        for (libFile in providedLibFiles) {
            if (!classpathEntries.contains(libFile.absolutePath)) {
                classpathEntries.add(libFile.absolutePath)
            }
        }

        classpathEntries.add(binDir.absolutePath)

        // Validate project-specific minimum Android version
        if (config.minSdk < 7) {
            logger("warning Minimum SDK declared in bolt.yml is ${config.minSdk}. App Inventor minimum baseline is 7.")
        }

        // Include user dependencies from deps/
        val declaredArtifactMap = (config.dependencies + config.compileTime + config.providedDependencies)
            .mapNotNull { dep ->
                val parts = dep.split(":")
                if (parts.size >= 3) {
                    parts[1] to parts[2]
                } else null
            }.toMap()

        val depsFiles = (depsDir.listFiles() ?: emptyArray()).filter { file ->
            if (!file.isFile || (file.extension != "jar" && file.extension != "aar")) return@filter false
            val nameWithoutExt = file.nameWithoutExtension
            val matchedArtifact = declaredArtifactMap.keys.firstOrNull { artifactId ->
                nameWithoutExt == artifactId || nameWithoutExt.startsWith("$artifactId-")
            }
            if (matchedArtifact != null) {
                val expectedVersion = declaredArtifactMap[matchedArtifact]
                val expectedName = "$matchedArtifact-$expectedVersion"
                if (nameWithoutExt != expectedName && nameWithoutExt != matchedArtifact) {
                    logger("warning Skipping stale/conflicting dependency '${file.name}' because version '$expectedVersion' is declared in bolt.yml.")
                    return@filter false
                }
            }
            true
        }.toTypedArray()
        for (dep in depsFiles) {
            if (dep.isFile && dep.extension == "jar") {
                classpathEntries.add(dep.absolutePath)
            } else if (dep.isFile && dep.extension == "aar") {
                // Check dependency minSdkVersion against project min_sdk
                try {
                    val zip = java.util.zip.ZipFile(dep)
                    val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    if (manifestEntry != null) {
                        val manifestContent = zip.getInputStream(manifestEntry).bufferedReader().readText()
                        val minSdkMatch = Regex("""android:minSdkVersion="(\d+)"""").find(manifestContent)
                        if (minSdkMatch != null) {
                            val depMinSdk = minSdkMatch.groupValues[1].toIntOrNull() ?: 7
                            if (depMinSdk > config.minSdk) {
                                logger("warning Dependency '${dep.name}' requires minSdkVersion $depMinSdk, but project 'min_sdk' is set to ${config.minSdk} in bolt.yml.")
                                logger("warning Consider increasing 'min_sdk: $depMinSdk' in bolt.yml to avoid runtime incompatibilities on older devices.")
                            }
                        }
                    }
                    zip.close()
                } catch (_: Exception) {}

                val tempAarJar = File(buildDir, "aar_${dep.nameWithoutExtension}.jar")
                if (AixPackager.extractAarClasses(dep, tempAarJar)) {
                    classpathEntries.add(tempAarJar.absolutePath)
                }
            }
        }

        // Include compile_time and provided_dependencies in classpath
        for (depName in (config.compileTime + config.providedDependencies)) {
            if (depName.isBlank()) continue
            val depFile = listOfNotNull(
                File(depName),
                File(projectDir, depName),
                File(depsDir, depName),
                File(depsDir, if (depName.endsWith(".jar") || depName.endsWith(".aar")) depName else "$depName.jar"),
                File(libsDir, depName),
                File(libsDir, if (depName.endsWith(".jar") || depName.endsWith(".aar")) depName else "$depName.jar"),
                LibLocator.findLib(libsDir, depName, if (depName.endsWith(".jar")) depName else "$depName.jar")
            ).firstOrNull { it.exists() }

            if (depFile != null) {
                if (depFile.extension == "jar") {
                    if (!classpathEntries.contains(depFile.absolutePath)) {
                        classpathEntries.add(depFile.absolutePath)
                    }
                    if (!providedLibFiles.contains(depFile)) {
                        providedLibFiles.add(depFile)
                    }
                } else if (depFile.extension == "aar") {
                    val tempAarJar = File(buildDir, "aar_${depFile.nameWithoutExtension}.jar")
                    if (AixPackager.extractAarClasses(depFile, tempAarJar)) {
                        if (!classpathEntries.contains(tempAarJar.absolutePath)) {
                            classpathEntries.add(tempAarJar.absolutePath)
                        }
                        if (!providedLibFiles.contains(tempAarJar)) {
                            providedLibFiles.add(tempAarJar)
                        }
                    }
                }
            }
        }

        val classpath = classpathEntries.joinToString(File.pathSeparator)

        // 7. Kotlin Compilation (if .kt files present) with 2-Step KAPT Pipeline (Kotlin 2.x compliant)
        var generatedKaptSources = emptyList<String>()
        val kaptClassesDir = File(buildDir, "kapt_classes")

        if (kotlinFiles.isNotEmpty()) {
            val kotlinCompilerJar = LibLocator.findLib(libsDir, "kotlin-compiler.jar", "kotlin-compiler-embeddable.jar")
            if (kotlinCompilerJar == null || !kotlinCompilerJar.exists()) {
                logger("error kotlin-compiler.jar missing in ${libsDir.absolutePath}")
                return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "kotlin-compiler.jar missing")), System.currentTimeMillis() - startTime)
            }

            val oldLoader = Thread.currentThread().contextClassLoader
            try {
                val troveJar = LibLocator.findLib(libsDir, "trove4j.jar", "trove4j-1.0.20200330.jar")
                val loaderJars = listOfNotNull(kotlinCompilerJar, kotlinStdlibJar, troveJar)
                val loader = LibLocator.createClassLoader(oldLoader, *loaderJars.toTypedArray())
                Thread.currentThread().contextClassLoader = loader

                val compilerClass = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                val compilerInstance = compilerClass.getDeclaredConstructor().newInstance()
                val execMethod = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)

                // Check for annotation processors in deps/
                val aptProcessors = depsFiles.filter { f ->
                    if (!f.isFile || f.extension != "jar") false
                    else try {
                        java.util.zip.ZipFile(f).use { z ->
                            z.getEntry("META-INF/services/javax.annotation.processing.Processor") != null
                        }
                    } catch (_: Throwable) { false }
                }

                val kaptPluginJar = LibLocator.findLib(libsDir, "kotlin-annotation-processing.jar", "kotlin-annotation-processing-embeddable.jar")
                    ?: kotlinCompilerJar

                val isKotlin2 = config.kotlinVersion.startsWith("2.")

                // Step 1: If annotation processors exist, run KAPT in stubsAndApt mode (Kotlin 2.x compliant)
                if (aptProcessors.isNotEmpty() && kaptPluginJar != null && kaptPluginJar.exists()) {
                    logger("- Annotation processor(s) detected (${aptProcessors.size}). Running KAPT (stubsAndApt mode)...")
                    val kaptSourcesDir = File(buildDir, "kapt_sources").apply { mkdirs() }
                    kaptClassesDir.mkdirs()
                    val kaptStubsDir = File(buildDir, "kapt_stubs").apply { mkdirs() }
                    val apClasspath = aptProcessors.joinToString(File.pathSeparator) { it.absolutePath }

                    val kaptArgs = mutableListOf<String>().apply {
                        add("-cp"); add(classpath)
                        add("-Xplugin=${kaptPluginJar.absolutePath}")
                        add("-P"); add("plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt")
                        add("-P"); add("plugin:org.jetbrains.kotlin.kapt3:sources=${kaptSourcesDir.absolutePath}")
                        add("-P"); add("plugin:org.jetbrains.kotlin.kapt3:classes=${kaptClassesDir.absolutePath}")
                        add("-P"); add("plugin:org.jetbrains.kotlin.kapt3:stubs=${kaptStubsDir.absolutePath}")
                        add("-P"); add("plugin:org.jetbrains.kotlin.kapt3:apclasspath=$apClasspath")
                        add("-no-stdlib"); add("-no-reflect")
                        add("-Xskip-metadata-version-check")
                        if (!isKotlin2) add("-no-jdk")
                        add("-kotlin-home"); add(buildDir.absolutePath)
                        addAll(kotlinFiles)
                        addAll(javaFiles)
                    }

                    val kaptErrBytes = ByteArrayOutputStream()
                    val kaptErrStream = PrintStream(kaptErrBytes)
                    val kaptExitCode = execMethod.invoke(compilerInstance, kaptErrStream, kaptArgs.toTypedArray())
                    val kaptOutput = kaptErrBytes.toString()

                    if (kaptExitCode?.toString() != "OK") {
                        logger("error KAPT annotation processing failed:\n$kaptOutput")
                        return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "KAPT Failed:\n$kaptOutput")), System.currentTimeMillis() - startTime)
                    }
                    logger("- KAPT annotation processing successful.")

                    val genFiles = kaptSourcesDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
                    if (genFiles.isNotEmpty()) {
                        logger("  Generated ${genFiles.size} source file(s) from annotation processors.")
                        generatedKaptSources = genFiles.map { it.absolutePath }
                    }
                }

                // Step 2: Final Kotlin Compilation via kotlinc (K2JVMCompiler)
                logger("- Compiling ${kotlinFiles.size} Kotlin file(s)...")
                val finalClasspath = if (kaptClassesDir.exists()) "$classpath${File.pathSeparator}${kaptClassesDir.absolutePath}" else classpath
                val generatedKtFiles = generatedKaptSources.filter { it.endsWith(".kt") }

                val kotlinArgs = mutableListOf<String>().apply {
                    add("-cp"); add(finalClasspath)
                    add("-d"); add(binDir.absolutePath)
                    add("-no-stdlib"); add("-no-reflect")
                    add("-Xskip-metadata-version-check")
                    if (!isKotlin2) add("-no-jdk")
                    add("-kotlin-home"); add(buildDir.absolutePath)
                    addAll(kotlinFiles)
                    addAll(generatedKtFiles)
                    addAll(javaFiles)
                }

                val errBytes = ByteArrayOutputStream()
                val errStream = PrintStream(errBytes)
                val exitCode = execMethod.invoke(compilerInstance, errStream, kotlinArgs.toTypedArray())
                val compilerOutput = errBytes.toString()

                if (exitCode?.toString() != "OK") {
                    logger("error Kotlin compilation failed:\n$compilerOutput")
                    return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "Kotlin Compilation Failed:\n$compilerOutput")), System.currentTimeMillis() - startTime)
                } else {
                    logger("- Kotlin compilation successful.")
                }
            } catch (e: Throwable) {
                logger("error Kotlin compiler crash: ${e.message}")
                e.printStackTrace()
                return BuildResult(false, null, listOf(Diagnostic(true, "", 0, "Kotlin compiler crash: ${e.message}")), System.currentTimeMillis() - startTime)
            } finally {
                Thread.currentThread().contextClassLoader = oldLoader
            }
        }

        // 8. Java Compilation via ECJ
        val generatedKaptJavaFiles = generatedKaptSources.filter { it.endsWith(".java") }
        val allJavaSources = (javaFiles + generatedKaptJavaFiles).distinct()

        val ecjSources = if (enableDeannotate) {
            val strippedDir = File(buildDir, "stripped_sources").apply { mkdirs() }
            allJavaSources.map { path ->
                val orig = File(path)
                val content = orig.readText(Charsets.UTF_8)
                val stripped = stripAnnotations(content)
                val dest = File(strippedDir, orig.name)
                dest.writeText(stripped, Charsets.UTF_8)
                dest.absolutePath
            }
        } else {
            allJavaSources
        }

        var diagnostics = emptyList<Diagnostic>()
        if (allJavaSources.isNotEmpty()) {
            logger("- Compiling ${allJavaSources.size} Java file(s) with ECJ...")
            logger("- Coping extension assets")
            logger("- Reading AndroidManifest.xml")

            val ecjJar = LibLocator.findLib(libsDir, "ecj.jar", "ecj-3.42.0.jar", "ecj-3.42.0-patched.jar", "ecj-3.18.0.jar")
            val ecjArgs = mutableListOf<String>().apply {
                add("-source"); add("8")
                add("-target"); add("8")
                add("-cp"); add(classpath)
                add("-nowarn")
                add("-proc:none")
                add("-g:lines,source")
                add("-d"); add(binDir.absolutePath)
                addAll(ecjSources)
            }

            var compileSuccess = false
            var compilerOutput = ""

            // 1. In-process ECJ compilation (Instant, 10x faster)
            val stdout = StringWriter()
            val stderr = StringWriter()
            val outWriter = PrintWriter(stdout)
            val errWriter = PrintWriter(stderr)

            try {
                val ecjLoader = if (ecjJar != null && ecjJar.exists()) {
                    LibLocator.createClassLoader(Thread.currentThread().contextClassLoader, ecjJar)
                } else {
                    this.javaClass.classLoader
                }
                val mainClass = ecjLoader.loadClass("org.eclipse.jdt.internal.compiler.batch.Main")
                val constructor = mainClass.getConstructor(PrintWriter::class.java, PrintWriter::class.java, Boolean::class.javaPrimitiveType)
                val ecjInstance = constructor.newInstance(outWriter, errWriter, false)
                val compileMethod = mainClass.getMethod("compile", Array<String>::class.java)
                compileSuccess = compileMethod.invoke(ecjInstance, ecjArgs.toTypedArray()) as Boolean
                compilerOutput = stderr.toString() + "\n" + stdout.toString()
            } catch (e: Throwable) {
                compileSuccess = false
            }

            // 2. Fallback to external Java process if in-process invocation threw an error
            if (!compileSuccess && compilerOutput.isBlank() && ecjJar != null && ecjJar.exists()) {
                try {
                    val javaExe = System.getProperty("java.home")?.let {
                        val bin = File(it, "bin")
                        val exe = File(bin, "java.exe")
                        if (exe.exists()) exe.absolutePath else File(bin, "java").absolutePath
                    } ?: "java"

                    val cmd = mutableListOf(javaExe, "-jar", ecjJar.absolutePath)
                    cmd.addAll(ecjArgs)

                    val proc = ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()

                    compilerOutput = proc.inputStream.bufferedReader().readText()
                    val exitCode = proc.waitFor()
                    compileSuccess = (exitCode == 0)
                } catch (e: Throwable) {
                    compileSuccess = false
                }
            }

            diagnostics = parseCompilerDiagnostics(compilerOutput)

            if (!compileSuccess) {
                logger("error Java compilation failed:\n$compilerOutput")
                return BuildResult(false, null, diagnostics, System.currentTimeMillis() - startTime)
            }
            logger("- Java compilation successfully.")
        }

        // 8b. Merge Runtime Dependencies into binDir (excluding compile-time and provided dependencies)
        val compileOnlyNames = (config.compileTime + config.providedDependencies).map {
            val n = File(it).name
            if (n.endsWith(".jar") || n.endsWith(".aar")) n else "$n.jar"
        }.toSet()

        for (dep in depsFiles) {
            if (dep.isFile && (dep.extension == "jar" || dep.extension == "aar")) {
                val isCompileOnly = compileOnlyNames.contains(dep.name) ||
                    config.compileTime.any { it.isNotEmpty() && dep.name.contains(it) } ||
                    config.providedDependencies.any { it.isNotEmpty() && dep.name.contains(it.substringAfterLast(':')) }
                if (!isCompileOnly) {
                    val jarToExtract = if (dep.extension == "aar") {
                        val tempAarJar = File(buildDir, "aar_${dep.nameWithoutExtension}.jar")
                        if (tempAarJar.exists()) tempAarJar else null
                    } else dep
                    if (jarToExtract != null && jarToExtract.exists()) {
                        AixPackager.unzipClassesOnly(jarToExtract, binDir)
                    }
                }
            }
        }

        // 9. StrGuard Bytecode String Obfuscation
        if (config.strguard.enabled) {
            logger("- StrGuard string obfuscation enabled.")
            val strguardJar = LibLocator.findLib(libsDir, "strguard-plugin-1.0.1.jar", "strguard-plugin.jar", "strguard.jar")
            if (strguardJar != null && strguardJar.exists()) {
                try {
                    val strguardLoader = LibLocator.createClassLoader(Thread.currentThread().contextClassLoader, strguardJar)
                    // Inject StrGuard API classes into binDir
                    injectStrGuardApiClasses(binDir, strguardLoader)

                    val mainClass = strguardLoader.loadClass("io.github.weg2022.strguard.cli.Main")
                    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
                    val strGuardArgs = arrayOf(
                        "--input", binDir.absolutePath,
                        "--output", binDir.absolutePath,
                        "--key", config.strguard.key,
                        "--packages", config.strguard.packages.joinToString(",")
                    )
                    mainMethod.invoke(null, strGuardArgs)
                    logger("- StrGuard string obfuscation completed successfully.")
                } catch (e: Throwable) {
                    logger("warning StrGuard string obfuscation error: ${e.message}")
                }
            } else {
                logger("warning strguard.jar not found in ${libsDir.absolutePath}. Skipping obfuscation.")
            }
        }

        // 10. Package Relocation (JarJar Shading)
        if (config.relocation.enabled && config.relocation.include.isNotEmpty()) {
            logger("- Package Relocation (JarJar Shading) enabled.")
            val jarjarJar = LibLocator.findLib(libsDir, "jarjar-1.7.2.jar", "jarjar.jar")
            if (jarjarJar != null && jarjarJar.exists()) {
                try {
                    val rulesFile = File(buildDir, "jarjar-rules.txt")
                    val rulesText = StringBuilder()
                    for (inc in config.relocation.include) {
                        val sanitized = inc.replace("**", "")
                        val targetPkg = "${packageName}.shaded.${sanitized}"
                        rulesText.append("rule $inc ${targetPkg}@1\n")
                    }
                    for (exc in config.relocation.exclude) {
                        rulesText.append("zap $exc\n")
                    }
                    rulesFile.writeText(rulesText.toString(), Charsets.UTF_8)

                    val tempInJar = File(buildDir, "temp_in.jar")
                    val tempOutJar = File(buildDir, "temp_out.jar")
                    AixPackager.zipDirectory(binDir, tempInJar)

                    val jarjarLoader = LibLocator.createClassLoader(Thread.currentThread().contextClassLoader, jarjarJar)
                    val mainClass = jarjarLoader.loadClass("org.pantsbuild.jarjar.Main")
                    val mainMethod = mainClass.getMethod("main", Array<String>::class.java)
                    val jarJarArgs = arrayOf("process", rulesFile.absolutePath, tempInJar.absolutePath, tempOutJar.absolutePath)
                    mainMethod.invoke(null, jarJarArgs)

                    binDir.deleteRecursively()
                    binDir.mkdirs()
                    AixPackager.unzipClassesOnly(tempOutJar, binDir)

                    tempInJar.delete()
                    tempOutJar.delete()
                    logger("- Package Relocation completed successfully.")
                } catch (e: Throwable) {
                    logger("warning Package Relocation error: ${e.message}")
                }
            } else {
                logger("warning jarjar.jar not found in ${libsDir.absolutePath}. Skipping relocation.")
            }
        }

        // 11. Assets and JNI Libraries
        val aiwebresDir = File(boltPkgDir, "aiwebres").apply { mkdirs() }

        // Copy palette icon ONLY to aiwebres/icon.png (never to assets/ unless declared in bolt.yml)
        val projAssetsDir = File(projectDir, "assets")
        val projectIcon = File(projAssetsDir, "icon.png")
        val fallbackIcon = listOfNotNull(
            libsDir.parentFile?.let { File(it, "icon.png") },
            File("icon.png"),
            LibLocator.findLib(libsDir, "icon.png"),
            File(toolsDir, "icon.png")
        ).firstOrNull { it.exists() }
        val iconToUse = when {
            projectIcon.exists() -> projectIcon
            fallbackIcon != null && fallbackIcon.exists() -> fallbackIcon
            else -> null
        }

        if (iconToUse != null) {
            iconToUse.copyTo(File(aiwebresDir, "icon.png"), overwrite = true)
        } else {
            val defaultBytes = com.techhamara.bolt.templates.ProjectTemplates.iconBytes
            File(aiwebresDir, "icon.png").writeBytes(defaultBytes)
        }

        // Handle runtime assets declared in bolt.yml
        val targetAssetsDir = File(boltPkgDir, "assets")
        val declaredAssets = config.assets.map { it.trim() }.filter { it.isNotBlank() }
        if (declaredAssets.isNotEmpty() && projAssetsDir.exists()) {
            targetAssetsDir.mkdirs()
            for (declaredAsset in declaredAssets) {
                val assetFile = File(projAssetsDir, declaredAsset)
                if (!assetFile.exists()) {
                    logger("warning Declared asset '$declaredAsset' does not exist in assets/ directory.")
                } else {
                    val dest = File(targetAssetsDir, declaredAsset)
                    dest.parentFile?.mkdirs()
                    if (assetFile.isDirectory) {
                        assetFile.copyRecursively(dest, overwrite = true)
                    } else {
                        assetFile.copyTo(dest, overwrite = true)
                    }
                    val sizeMB = assetFile.length() / (1024.0 * 1024.0)
                    if (sizeMB > 5.0) {
                        logger("warning Asset '$declaredAsset' is large (%.1f MB). Recommended App Inventor AIX limit is 30MB.".format(sizeMB))
                    }
                }
            }
        } else if (targetAssetsDir.exists()) {
            targetAssetsDir.deleteRecursively()
        }
        logger("debug Copied assets and extension icon to AIX.")

        val projJniDir = File(projectDir, "jni")
        if (projJniDir.exists() && projJniDir.isDirectory) {
            val targetJniDir = File(boltPkgDir, "jni").apply { mkdirs() }
            projJniDir.copyRecursively(targetJniDir, overwrite = true)
            logger("debug Copied JNI native libraries to AIX.")
        }

        // 12. Parse Annotations & Metadata Generation
        val parser = AnnotationParser()
        var componentList = parser.parseJavaFilesList(srcDir)

        // Automatically detect native libraries in projJniDir (from NDK build or prebuilts)
        val detectedNativeLibs = mutableListOf<String>()
        if (projJniDir.exists() && projJniDir.isDirectory) {
            projJniDir.walkTopDown().filter { it.isFile && it.extension.lowercase() == "so" }.forEach { soFile ->
                val abiDir = soFile.parentFile.name.lowercase()
                val soName = soFile.name
                val targetSuffix = when (abiDir) {
                    "armeabi-v7a", "v7a" -> "-v7a"
                    "arm64-v8a", "v8a" -> "-v8a"
                    "x86_64" -> "-x86_64"
                    "x86" -> "-x86"
                    "armeabi" -> "-armeabi"
                    else -> ""
                }
                if (targetSuffix.isNotEmpty()) {
                    detectedNativeLibs.add("$soName$targetSuffix")
                } else {
                    detectedNativeLibs.add(soName)
                }
            }
        }

        if (componentList.isNotEmpty()) {
            val comp = componentList[0]
            val updatedComp = comp.copy(
                activities = manifestData.activities,
                activityAliases = manifestData.activityAliases,
                services = manifestData.services,
                receivers = manifestData.receivers,
                providers = manifestData.providers,
                application = manifestData.application,
                libraries = manifestData.libraries,
                nativeLibraries = (comp.nativeLibraries + manifestData.nativeLibraries + detectedNativeLibs).distinct(),
                permissions = (comp.permissions + manifestData.permissions).distinct(),
                androidMinSdk = config.minSdk
            )
            componentList = listOf(updatedComp) + componentList.drop(1)
        }

        val componentJsonContent = GsonBuilder().create().toJson(componentList)
        File(boltPkgDir, "components.json").writeText(componentJsonContent, Charsets.UTF_8)

        val helperEnums = parser.parseHelperEnums(srcDir)
        val extTxtFile = File(outDir, "extension.txt")
        extTxtFile.writeText(parser.generateMarkdown(componentList, config.author, helperEnums, boltVersionStr, buildTimeStr, config.homepage), Charsets.UTF_8)

        if (generateBlocks) {
            val blocksOutDir = File(outDir, "blocks")
            try {
                val count = com.techhamara.bolt.generator.BlockPngGenerator.generateAll(
                    componentList = componentList,
                    outBlocksDir = blocksOutDir,
                    logger = { msg -> logger(msg) }
                )
            } catch (t: Throwable) {
                logger("warning Failed to generate block PNG images: ${t.message ?: t.javaClass.simpleName}")
                val isLinux = System.getProperty("os.name", "").lowercase().contains("linux") || File("/system/fonts").exists()
                if (isLinux) {
                    logger("hint On Termux/Linux, install font packages with: pkg install fontconfig ttf-dejavu (or apt install fontconfig)")
                }
            }
        }

        // 13. Strip App Inventor metadata annotations (deannonate) if enabled
        val shouldDeannotate = enableDeannotate || config.deannonate
        if (shouldDeannotate) {
            Deannotator.deannotateDirectory(binDir, logger)
        }

        // 14. Optimization / Shrinking: ProGuard vs R8
        val isProguardRequested = enableProguard || config.proguard
        var proguardExecuted = false

        if (isProguardRequested) {
            proguardExecuted = ProGuardRunner.compile(
                projectDir = projectDir,
                buildDir = buildDir,
                binDir = binDir,
                proguardFile = proguardFile,
                config = config,
                libsDir = libsDir,
                androidJar = androidJar,
                stubsJar = stubsJar,
                kotlinStdlibJar = kotlinStdlibJar,
                kawaJar = kawaJar,
                providedLibFiles = providedLibFiles,
                compileOnlyNames = compileOnlyNames,
                depsDir = depsDir,
                componentList = componentList,
                packageName = packageName,
                keepManifest = keepManifest,
                manifestData = manifestData,
                logger = logger
            )
        }

        val isOptimizeMode = enableR8 || config.r8
        val shouldRunR8 = (!proguardExecuted) && (isOptimizeMode || config.coreLibraryDesugaring)
        val r8Jar = LibLocator.findLib(libsDir, "r8.jar")
        if (shouldRunR8 && r8Jar != null && r8Jar.exists()) {
            logger("- Running R8...")
            try {
                val tempR8In = File(buildDir, "r8_in.jar")
                val tempR8Out = File(buildDir, "r8_out.jar")
                AixPackager.zipDirectory(binDir, tempR8In)

                val r8Args = mutableListOf(
                    "--classfile",
                    "--output", tempR8Out.absolutePath,
                    "--lib", androidJar.absolutePath,
                    "--lib", stubsJar.absolutePath,
                    "--lib", kotlinStdlibJar.absolutePath
                )
                if (kawaJar != null && kawaJar.exists()) {
                    r8Args.add("--lib"); r8Args.add(kawaJar.absolutePath)
                }
                val referencedPrefixes = getReferencedPackagePrefixes(binDir)
                val filteredProvidedLibs = if (referencedPrefixes.isNotEmpty()) {
                    providedLibFiles.filter { lib ->
                        val base = lib.nameWithoutExtension.lowercase().replace(Regex("-\\d+(\\.\\d+)*.*$"), "")
                        referencedPrefixes.any { it.contains(base) }
                    }
                } else providedLibFiles

                for (lib in filteredProvidedLibs) {
                    if (lib.exists() && !r8Args.contains(lib.absolutePath)) {
                        r8Args.add("--lib"); r8Args.add(lib.absolutePath)
                    }
                }
                depsDir.listFiles { _, name -> name.endsWith(".jar") }?.forEach { depJar ->
                    val isCompileOnly = compileOnlyNames.contains(depJar.name) ||
                        config.compileTime.any { it.isNotEmpty() && depJar.name.contains(it) } ||
                        config.providedDependencies.any { it.isNotEmpty() && depJar.name.contains(it.substringAfterLast(':')) }
                    if (isCompileOnly) {
                        r8Args.add("--lib"); r8Args.add(depJar.absolutePath)
                    }
                }

                val minimizeRulesFile = File(buildDir, "minimize-rules.pro")
                val keepRules = StringBuilder("\n# Auto-generated keep rules\n")
                config.minimize.excludeDependency.forEach { keepRules.append("-keep class $it.** { *; }\n") }
                config.minimize.excludeProject.forEach { keepRules.append("-keep class $it.** { *; }\n") }
                keepRules.append("-dontnote **\n")
                keepRules.append("-dontwarn **\n")
                keepRules.append("-keep public class * extends com.google.appinventor.components.runtime.AndroidNonvisibleComponent { public *; protected *; }\n")
                keepRules.append("-keep public class * extends com.google.appinventor.components.runtime.Component { public *; protected *; }\n")
                keepRules.append("-keep class * implements com.google.appinventor.components.common.OptionList { *; }\n")
                keepRules.append("-keep enum * { *; }\n")
                keepRules.append("-keepclassmembers enum * { *; }\n")
                keepRules.append("-keep class $packageName.** { public *; protected *; }\n")
                keepRules.append("-keepclassmembers class $packageName.** { public *; protected *; <init>(...); }\n")
                if (shouldDeannotate) {
                    keepRules.append("-keepattributes !*Annotation*,Exceptions,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable\n")
                } else {
                    keepRules.append("-keepattributes *Annotation*,Exceptions,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable\n")
                }
                for (comp in componentList) {
                    keepRules.append("-keep class ${comp.type} { *; }\n")
                    keepRules.append("-keepclassmembers class ${comp.type} { public *; protected *; }\n")
                }
                if (keepManifest) {
                    keepRules.append("\n# Keep manifest components (-m / --keep-manifest)\n")
                    val classRegex = Regex("""android:name="([^"]+)"""")
                    val manifestXmlEntries = manifestData.activities + manifestData.activityAliases + manifestData.services + manifestData.receivers + manifestData.providers
                    for (entry in manifestXmlEntries) {
                        val m = classRegex.find(entry)
                        if (m != null) {
                            val cls = m.groupValues[1]
                            if (cls.isNotEmpty() && !cls.startsWith("@")) {
                                keepRules.append("-keep public class $cls extends android.app.Activity { *; }\n")
                                keepRules.append("-keep public class $cls extends android.app.Service { *; }\n")
                                keepRules.append("-keep public class $cls extends android.content.BroadcastReceiver { *; }\n")
                                keepRules.append("-keep public class $cls extends android.content.ContentProvider { *; }\n")
                                keepRules.append("-keep class $cls { *; }\n")
                            }
                        }
                    }
                }
                minimizeRulesFile.writeText(keepRules.toString(), Charsets.UTF_8)

                if (isOptimizeMode) {
                    if (proguardFile.exists()) {
                        r8Args.add("--pg-conf"); r8Args.add(proguardFile.absolutePath)
                    }
                    r8Args.add("--pg-conf"); r8Args.add(minimizeRulesFile.absolutePath)
                } else {
                    r8Args.add("--no-tree-shaking")
                    r8Args.add("--no-minification")
                    r8Args.add("--pg-conf"); r8Args.add(minimizeRulesFile.absolutePath)
                }

                if (config.coreLibraryDesugaring && desugarConfigJson.exists()) {
                    r8Args.add("--desugared-lib"); r8Args.add(desugarConfigJson.absolutePath)
                    logger("- Core Library Desugaring enabled.")
                }

                r8Args.add(tempR8In.absolutePath)

                val r8Loader = LibLocator.createClassLoader(Thread.currentThread().contextClassLoader, r8Jar)
                val cmdClass = r8Loader.loadClass("com.android.tools.r8.R8Command")
                val originClass = r8Loader.loadClass("com.android.tools.r8.origin.Origin")
                val unknownOrigin = originClass.getMethod("unknown").invoke(null)
                val parseMethod = cmdClass.getMethod("parse", Array<String>::class.java, originClass)

                val origOut = System.out
                val origErr = System.err

                val filteringStream = object : OutputStream() {
                    private val buffer = ByteArrayOutputStream()
                    private val headerLines = mutableListOf<String>()
                    private var isCollectingHeader = false
                    private var inIgnorableBlock = false

                    override fun write(b: Int) {
                        if (b == '\n'.code) {
                            val line = buffer.toString(StandardCharsets.UTF_8.name())
                            buffer.reset()
                            processLine(line)
                        } else if (b != '\r'.code) {
                            buffer.write(b)
                        }
                    }

                    private fun processLine(rawLine: String) {
                        val trimmed = rawLine.trim()
                        if (trimmed.isEmpty()) return

                        // Drop any stray braces, backticks, or closing rule delimiters
                        if (trimmed == "}" || trimmed == "}`" || trimmed == "};" || trimmed.matches(Regex("""^[`\}\{\s;]+$"""))) {
                            if (inIgnorableBlock && (trimmed.endsWith("`") || trimmed == "}" || trimmed == "};")) {
                                inIgnorableBlock = false
                            }
                            return
                        }

                        val lower = trimmed.lowercase()

                        val isIgnorable = lower.contains("ignoring option:") ||
                                lower.contains("rule does not match anything") ||
                                lower.contains("does not match anything") ||
                                lower.contains("malformed inner-class attribute")

                        if (isIgnorable) {
                            inIgnorableBlock = true
                            isCollectingHeader = false
                            headerLines.clear()
                            return
                        }

                        if (inIgnorableBlock) {
                            val isRuleLine = rawLine.startsWith("\t") || rawLine.startsWith(" ") ||
                                    trimmed.startsWith("-keep") || trimmed.startsWith("`") ||
                                    trimmed.contains("*") || trimmed.contains("<init>") ||
                                    trimmed.contains("<fields>") || trimmed.contains("<methods>") ||
                                    lower.startsWith("outertype") || lower.startsWith("innertype") || lower.startsWith("innername") ||
                                    trimmed.endsWith("`") || trimmed.endsWith("}") || trimmed.endsWith("};")

                            if (isRuleLine) {
                                if (trimmed.endsWith("`") || trimmed == "}" || trimmed == "};") {
                                    inIgnorableBlock = false
                                }
                                return
                            } else {
                                inIgnorableBlock = false
                            }
                        }

                        if (lower.startsWith("info in ") || lower.startsWith("warning in ")) {
                            inIgnorableBlock = false
                            if (headerLines.isNotEmpty()) {
                                headerLines.forEach { origErr.println(it) }
                                headerLines.clear()
                            }
                            isCollectingHeader = true
                            headerLines.add(rawLine)
                            return
                        }

                        if (isCollectingHeader) {
                            if (lower.startsWith("at line ") || lower.contains("column ") || lower == ":") {
                                headerLines.add(rawLine)
                                return
                            } else {
                                isCollectingHeader = false
                            }
                        }

                        if (headerLines.isNotEmpty()) {
                            headerLines.forEach { origErr.println(it) }
                            headerLines.clear()
                        }
                        origErr.println(rawLine)
                    }

                    override fun flush() {
                        if (buffer.size() > 0) {
                            val line = buffer.toString(StandardCharsets.UTF_8.name())
                            buffer.reset()
                            processLine(line)
                        }
                    }
                }

                val filteredPrintStream = PrintStream(filteringStream, true, "UTF-8")
                try {
                    System.setOut(filteredPrintStream)
                    System.setErr(filteredPrintStream)

                    val r8Builder = parseMethod.invoke(null, r8Args.toTypedArray(), unknownOrigin)
                    val buildMethod = r8Builder.javaClass.getMethod("build")
                    val r8Command = buildMethod.invoke(r8Builder)

                    val r8Class = r8Loader.loadClass("com.android.tools.r8.R8")
                    val runMethod = r8Class.getMethod("run", cmdClass)
                    runMethod.invoke(null, r8Command)
                } finally {
                    filteredPrintStream.flush()
                    System.setOut(origOut)
                    System.setErr(origErr)
                }

                binDir.deleteRecursively()
                binDir.mkdirs()
                AixPackager.unzipClassesOnly(tempR8Out, binDir)

                tempR8In.delete()
                tempR8Out.delete()
                logger("- R8 processing successfully.")
            } catch (e: Throwable) {
                logger("error R8 processing failed: ${e.message}")
                e.printStackTrace()
            }
        }

        // 14. Package AndroidRuntime.jar and component_build_infos.json
        val filesDir = File(boltPkgDir, "files").apply { mkdirs() }
        val filesLibDir = File(filesDir, "lib")
        if (projJniDir.exists() && projJniDir.isDirectory && projJniDir.listFiles()?.isNotEmpty() == true) {
            filesLibDir.mkdirs()
            // Copy raw jni structure to files/lib/
            projJniDir.copyRecursively(filesLibDir, overwrite = true)

            // Copy physical binaries into files/ according to MIT App Inventor build server specs
            // (e.g. files/libtest.so-v7a, files/libtest.so-v8a, files/libtest.so-x86_64)
            projJniDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { soFile ->
                val abiDir = soFile.parentFile.name.lowercase()
                val soName = soFile.name
                val targetSuffix = when (abiDir) {
                    "armeabi-v7a", "v7a" -> "-v7a"
                    "arm64-v8a", "v8a" -> "-v8a"
                    "x86_64" -> "-x86_64"
                    "x86" -> "-x86"
                    "armeabi" -> "-armeabi"
                    else -> ""
                }
                if (targetSuffix.isNotEmpty()) {
                    val suffixed = "$soName$targetSuffix"
                    soFile.copyTo(File(filesDir, suffixed), overwrite = true)
                    if (targetSuffix == "-x86_64") {
                        soFile.copyTo(File(filesDir, "$soName-x8a"), overwrite = true)
                    }
                } else {
                    soFile.copyTo(File(filesDir, soName), overwrite = true)
                }
            }

            // Also ensure any explicitly declared native libraries in @UsesNativeLibraries exist in files/
            componentList.firstOrNull()?.nativeLibraries?.forEach { libStr ->
                val targetDest = File(filesDir, libStr)
                if (!targetDest.exists()) {
                    val baseName = when {
                        libStr.endsWith("-v7a") -> libStr.removeSuffix("-v7a")
                        libStr.endsWith("-v8a") -> libStr.removeSuffix("-v8a")
                        libStr.endsWith("-x86_64") -> libStr.removeSuffix("-x86_64")
                        libStr.endsWith("-x8a") -> libStr.removeSuffix("-x8a")
                        libStr.endsWith("-x86") -> libStr.removeSuffix("-x86")
                        libStr.endsWith("-armeabi") -> libStr.removeSuffix("-armeabi")
                        else -> libStr
                    }
                    val candidate = projJniDir.walkTopDown().firstOrNull { it.isFile && it.name == baseName }
                    if (candidate != null) {
                        candidate.copyTo(targetDest, overwrite = true)
                    }
                }
            }
            logger("Packaged JNI native libraries into AIX archive.")
        }
        if (filesLibDir.exists() && filesLibDir.listFiles()?.isEmpty() == true) {
            filesLibDir.delete()
        }

        // 13. Extract and copy physical XML files defined via bolt.yml (config.xmls) and @UsesXmls into both assets/ and files/
        val validDirPattern = Regex("^(layout|values|drawable|mipmap|xml|color|menu|animator|anim)([a-zA-Z0-9-+_]*)?$")
        val configXmls = mutableListOf<String>()
        if (config.xmls.isNotEmpty()) {
            for (rawXmlPath in config.xmls) {
                val cleanRaw = rawXmlPath.trim().replace('\\', '/')
                val cleanRelPath = cleanRaw.removePrefix("assets/").removePrefix("/")

                val candidateFile = listOf(
                    File(projAssetsDir, cleanRelPath),
                    File(projAssetsDir, cleanRaw),
                    File(projectDir, cleanRaw),
                    File(projectDir, cleanRelPath),
                    File(projectDir, "assets/$cleanRelPath")
                ).firstOrNull { it.exists() && it.isFile }

                if (candidateFile != null) {
                    val xmlContent = candidateFile.readText(Charsets.UTF_8)

                    // Determine resource directory and sanitized filename for App Inventor XmlConfig
                    val parts = cleanRelPath.split('/')
                    val (resDir, rawFilename) = if (parts.size > 1) {
                        val declaredDir = parts[0].trim()
                        val fn = parts.drop(1).joinToString("/").trim()
                        if (validDirPattern.matches(declaredDir)) {
                            declaredDir to fn
                        } else {
                            // If invalid prefix like "xmlPath" or "custom", normalize to "xml"
                            "xml" to fn.substringAfterLast('/')
                        }
                    } else {
                        // Bare filename (e.g. "network_security_config.xml") -> default to "xml"
                        "xml" to parts[0].trim()
                    }

                    var cleanFilename = rawFilename.substringAfterLast('/')
                    if (cleanFilename.isNotEmpty() && cleanFilename[0].isUpperCase()) {
                        cleanFilename = cleanFilename[0].lowercaseChar() + cleanFilename.substring(1)
                    }
                    if (!cleanFilename.endsWith(".xml")) {
                        cleanFilename += ".xml"
                    }

                    val normalizedResPath = "$resDir/$cleanFilename"
                    configXmls.add("$normalizedResPath:$xmlContent")
                    logger("- Injected custom XML: $normalizedResPath")
                } else {
                    logger("warning XML file declared in bolt.yml not found: $rawXmlPath (expected in assets/$cleanRelPath)")
                }
            }
        }

        val allXmls = (componentList.flatMap { it.xmls } + configXmls).distinct()
        for (xmlEntry in allXmls) {
            val colonIdx = xmlEntry.indexOf(':')
            if (colonIdx > 0) {
                val relPath = xmlEntry.substring(0, colonIdx)
                val xmlContent = xmlEntry.substring(colonIdx + 1)

                val assetXml = File(targetAssetsDir, relPath)
                assetXml.parentFile?.mkdirs()
                assetXml.writeText(xmlContent, Charsets.UTF_8)

                val filesXml = File(filesDir, relPath)
                filesXml.parentFile?.mkdirs()
                filesXml.writeText(xmlContent, Charsets.UTF_8)
            }
        }
        if (allXmls.isNotEmpty()) {
            logger("Injected XML layout/manifest resources into AIX archive.")
        }

        val assetsList = if (declaredAssets.isNotEmpty()) declaredAssets else emptyList()

        // 14. D8 DEX Compilation (Always compile classes.dex for classes.jar)
        val classesDex = D8Runner.compile(projectDir, buildDir, binDir, config, libsDir, androidJar, logger, providedLibFiles)

        // Generate component_build_infos.json for all components in project
        val componentEntries = if (componentList.isNotEmpty()) {
            componentList.map { comp ->
                val compPerms = (comp.permissions + manifestData.permissions).distinct().filter { it.isNotBlank() }
                val compNatives = (comp.nativeLibraries + manifestData.nativeLibraries).distinct().filter { it.isNotBlank() }
                val compAppElements = (comp.activities + manifestData.allApplicationElements).distinct().filter { it.isNotBlank() }
                val compXmls = (comp.xmls + allXmls).distinct().filter { it.isNotBlank() }
                val compQueries = (comp.queries + manifestData.queries).distinct().filter { it.isNotBlank() }
                val compFeatures = manifestData.features.mapNotNull { f ->
                    val map = mutableMapOf<String, Any>()
                    if (f.name.isNotBlank()) map["name"] = f.name
                    map["required"] = f.required
                    if (f.glEsVersion != null) map["glEsVersion"] = f.glEsVersion
                    if (map.isNotEmpty()) map else null
                }

                val entry = mutableMapOf<String, Any>()
                entry["features"] = compFeatures
                entry["assets"] = assetsList
                entry["xmls"] = compXmls
                entry["activities"] = compAppElements
                entry["permissions"] = compPerms
                if (compNatives.isNotEmpty()) entry["native"] = compNatives
                entry["boltVersion"] = boltVersionStr
                entry["type"] = comp.type
                val effectiveMinSdk = if (comp.androidMinSdk > 0) comp.androidMinSdk else config.minSdk
                entry["androidMinSdk"] = listOf(effectiveMinSdk.coerceAtLeast(7))
                entry["queries"] = compQueries

                entry
            }
        } else {
            val compAppElements = manifestData.allApplicationElements.filter { it.isNotBlank() }
            val compPerms = manifestData.permissions.filter { it.isNotBlank() }
            val compQueries = manifestData.queries.filter { it.isNotBlank() }
            val compFeatures = manifestData.features.mapNotNull { f ->
                val map = mutableMapOf<String, Any>()
                if (f.name.isNotBlank()) map["name"] = f.name
                map["required"] = f.required
                if (f.glEsVersion != null) map["glEsVersion"] = f.glEsVersion
                if (map.isNotEmpty()) map else null
            }

            val entry = mutableMapOf<String, Any>()
            entry["features"] = compFeatures
            entry["assets"] = assetsList
            entry["xmls"] = allXmls
            entry["activities"] = compAppElements
            entry["permissions"] = compPerms
            if (manifestData.nativeLibraries.isNotEmpty()) entry["native"] = manifestData.nativeLibraries
            entry["boltVersion"] = boltVersionStr
            entry["type"] = packageName
            entry["androidMinSdk"] = listOf(config.minSdk.coerceAtLeast(7))
            entry["queries"] = compQueries

            listOf(entry)
        }

        val componentBuildInfoContent = GsonBuilder().create().toJson(componentEntries)
        File(filesDir, "component_build_infos.json").writeText(componentBuildInfoContent, Charsets.UTF_8)

        val runtimeJar = File(filesDir, "AndroidRuntime.jar")
        AixPackager.zipDirectory(binDir, runtimeJar, setOf("dex"))

        val classesJar = File(boltPkgDir, "classes.jar")
        if (classesDex != null && classesDex.exists()) {
            AixPackager.zipSingleFile(classesDex, "classes.dex", classesJar)
          //  logger("Packaged classes.dex into classes.jar.")
        } else {
            logger("classes.dex could not be compiled; fallback classes.jar")
            AixPackager.zipDirectory(binDir, classesJar)
        }

        val strayDex = File(filesDir, "classes.dex")
        if (strayDex.exists()) {
            strayDex.delete()
        }

        // 15. Package .aix Archive
        val aixFile = File(outDir, "$packageName.aix")
        AixPackager.zipDirectory(boltDir, aixFile)

        val duration = System.currentTimeMillis() - startTime
        val aixSizeKB = "%.1f".format(aixFile.length() / 1024f)

        logger("- Generating docs in Markdown")
        if (extTxtFile.exists()) {
            val text = extTxtFile.readText(Charsets.UTF_8).replace("[Calculate automatically during build]", "${aixSizeKB}KB")
            extTxtFile.writeText(text, Charsets.UTF_8)
        }

        val relPath = ".${File.separator}out${File.separator}${aixFile.name}"
        logger("- Packaging extension..")

        // Clean up empty gen directory if no AIDL files generated
        if (genDir.exists() && (genDir.list()?.isEmpty() == true)) {
            genDir.delete()
        }

        return BuildResult(true, aixFile, diagnostics, duration)
    }

    // ==========================================
    // Dependency Sync Pipeline
    // ==========================================
    fun sync(projectDir: File): Boolean {
        logger("- Syncing project dependencies: ${projectDir.name}")
        repairProjectStructure(projectDir)

        val ymlFile = File(projectDir, "bolt.yml")
        if (!ymlFile.exists()) {
            logger("error bolt.yml not found in ${projectDir.absolutePath}")
            return false
        }

        val config = ConfigParser().parse(ymlFile)
        val depsDir = File(projectDir, "deps").apply { mkdirs() }

        val remoteDeps = (config.dependencies + config.providedDependencies).filter { it.contains(":") }
        val resolver = MavenResolver(logger)
        return resolver.resolve(remoteDeps, config.repositories, depsDir)
    }

    // ==========================================
    // Helper Methods
    // ==========================================
    fun repairProjectStructure(projectDir: File) {
        val srcDir = File(projectDir, "src").apply { mkdirs() }
        File(projectDir, "deps").apply { mkdirs() }
        File(projectDir, "assets").apply { mkdirs() }
        File(projectDir, "out").apply { mkdirs() }

        val manifestFile = File(srcDir, "AndroidManifest.xml")
        val ymlFile = File(projectDir, "bolt.yml")
        val proguardFile = File(srcDir, "proguard-rules.pro")

        val packageName = guessPackageName(srcDir)

        if (!manifestFile.exists()) {
            logger("- Recreating missing AndroidManifest.xml")
            manifestFile.writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="$packageName">
                  <application/>
                  <uses-permission android:name="android.permission.INTERNET"/>
                </manifest>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        if (!ymlFile.exists()) {
            logger("- Recreating missing bolt.yml")
            val timeFormatter = java.text.SimpleDateFormat("h.mma dd.MM.yyyy", java.util.Locale.ENGLISH)
            val currentBuildTime = timeFormatter.format(java.util.Date()).lowercase()
            ymlFile.writeText(
                """
                author: 'Unknown'
                version: '1.0.0'
                min_sdk: 14
                bolt_version: '2.0.0'
                build_time: '$currentBuildTime'
                auto_version: true

                # Bytecode-level string obfuscation tool.
                strguard:
                  enabled: false
                  key: "TechHamara-MyKey-2026-Secret"
                  packages:
                    - "$packageName"

                # Shading/relocation for dependencies to prevent Duplicate Class errors.
                relocation:
                  EnableAutoRelocation: false
                  skipStringConstants: true

                # R8/ProGuard exclusion to prevent minifying specific dependencies.
                minimize:
                  exclude_dependency: []
                  exclude_project: []

                # Enable core library desugaring for Java 8+ features on older Androids.
                coreLibraryDesugaring: false
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        if (!proguardFile.exists()) {
            logger("- Recreating missing proguard-rules.pro")
            proguardFile.writeText(
                """
                -repackageclasses ${packageName}.repacked
                -android
                -optimizationpasses 4
                -allowaccessmodification
                -mergeinterfacesaggressively
                -overloadaggressively
                -useuniqueclassmembernames
                -dontskipnonpubliclibraryclasses
                -dontskipnonpubliclibraryclassmembers
                """.trimIndent(),
                Charsets.UTF_8
            )
        } else {
            try {
                val pgContent = proguardFile.readText(Charsets.UTF_8)
                if (pgContent.contains("-dontskipnonpubliclibraryclassmember") && !pgContent.contains("-dontskipnonpubliclibraryclassmembers")) {
                    proguardFile.writeText(pgContent.replace("-dontskipnonpubliclibraryclassmember", "-dontskipnonpubliclibraryclassmembers"), Charsets.UTF_8)
                    logger("- Sanitized legacy ProGuard rule (-dontskipnonpubliclibraryclassmember -> -dontskipnonpubliclibraryclassmembers)")
                }
            } catch (_: Exception) {}
        }
    }

    private fun guessPackageName(srcDir: File): String {
        val files = srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.toList()
        for (file in files) {
            val match = Regex("""package\s+([a-zA-Z0-9_.]+)""").find(file.readText(Charsets.UTF_8))
            if (match != null) return match.groupValues[1]
        }
        return "com.example.extension"
    }

    private fun stripAnnotations(source: String): String {
        var result = source
        val annotations = listOf(
            "DesignerComponent", "DesignerProperty", "SimpleObject",
            "SimpleFunction", "SimpleProperty", "SimpleEvent"
        )
        for (annotation in annotations) {
            var startIndex = result.indexOf("@$annotation")
            while (startIndex != -1) {
                var endIndex = startIndex + "@$annotation".length
                var tempIndex = endIndex
                while (tempIndex < result.length && result[tempIndex].isWhitespace()) tempIndex++
                if (tempIndex < result.length && result[tempIndex] == '(') {
                    var parenCount = 1
                    var inString = false
                    var escape = false
                    tempIndex++
                    while (tempIndex < result.length && parenCount > 0) {
                        val c = result[tempIndex]
                        if (escape) escape = false
                        else if (c == '\\') escape = true
                        else if (c == '"') inString = !inString
                        else if (!inString) {
                            if (c == '(') parenCount++
                            else if (c == ')') parenCount--
                        }
                        tempIndex++
                    }
                    endIndex = tempIndex
                }
                result = result.substring(0, startIndex) + result.substring(endIndex)
                startIndex = result.indexOf("@$annotation")
            }
        }
        return result
    }

    private fun injectStrGuardApiClasses(binDir: File, loader: ClassLoader) {
        val apiNames = listOf(
            "StrGuard", "StrGuardImpl", "IStrGuard", "IkeyGenerator",
            "HardCodeKeyGenerator", "RandomKeyGenerator", "KeepString", "KeepMetadata"
        )
        val targetDir = File(binDir, "io/github/weg2022/strguard/api").apply { mkdirs() }
        for (name in apiNames) {
            val resourcePath = "io/github/weg2022/strguard/api/$name.class"
            try {
                loader.getResourceAsStream(resourcePath)?.use { input ->
                    val outFile = File(targetDir, "$name.class")
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            } catch (ignored: Throwable) {}
        }
    }

    private fun parseCompilerDiagnostics(log: String): List<Diagnostic> {
        val messages = mutableListOf<Diagnostic>()
        val reader = BufferedReader(StringReader(log))
        var line: String? = reader.readLine()
        var currentFile = ""
        var currentLineNum = 0
        var isError = false
        val errMsgBuilder = StringBuilder()

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("----------")) {
                if (currentFile.isNotEmpty() && errMsgBuilder.isNotEmpty()) {
                    messages.add(Diagnostic(isError, currentFile, currentLineNum, errMsgBuilder.toString().trim()))
                    currentFile = ""
                    currentLineNum = 0
                    isError = false
                    errMsgBuilder.setLength(0)
                }
            } else if (line.contains("ERROR in ") || line.contains("WARNING in ")) {
                isError = line.contains("ERROR in ")
                val delimiter = if (isError) "ERROR in " else "WARNING in "
                currentFile = line.substringAfter(delimiter).substringBefore(" (at line").trim()
                currentLineNum = line.substringAfter("(at line ").substringBefore(")").trim().toIntOrNull() ?: 1
            } else if (currentFile.isNotEmpty()) {
                if (!trimmed.startsWith("^^^") && !trimmed.startsWith("public") && !trimmed.startsWith("import")) {
                    errMsgBuilder.append(line).append("\n")
                }
            }
            line = reader.readLine()
        }

        if (currentFile.isNotEmpty() && errMsgBuilder.isNotEmpty()) {
            messages.add(Diagnostic(isError, currentFile, currentLineNum, errMsgBuilder.toString().trim()))
        }
        return messages
    }

    private fun updateBoltYmlMetadata(ymlFile: File, boltVersion: String, buildTime: String) {
        if (!ymlFile.exists()) return
        try {
            var content = ymlFile.readText(Charsets.UTF_8)
            val boltVerRegex = Regex("(?m)^\\s*bolt_version\\s*:.*$")
            val buildTimeRegex = Regex("(?m)^\\s*build_time\\s*:.*$")

            content = if (boltVerRegex.containsMatchIn(content)) {
                boltVerRegex.replace(content, "bolt_version: '$boltVersion'")
            } else {
                content.trimEnd() + "\nbolt_version: '$boltVersion'\n"
            }

            content = if (buildTimeRegex.containsMatchIn(content)) {
                buildTimeRegex.replace(content, "build_time: '$buildTime'")
            } else {
                content.trimEnd() + "\nbuild_time: '$buildTime'\n"
            }

            ymlFile.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun getReferencedPackagePrefixes(classesDir: File): Set<String> {
        val prefixes = mutableSetOf<String>()
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        for (file in classFiles) {
            try {
                val reader = ClassReader(file.readBytes())
                reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    private fun checkType(t: String?) {
                        if (t == null) return
                        val clean = t.removePrefix("[").removePrefix("L").removeSuffix(";")
                        val slashIdx = clean.lastIndexOf('/')
                        if (slashIdx > 0) {
                            prefixes.add(clean.substring(0, slashIdx).replace('/', '.'))
                        }
                    }

                    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
                        checkType(superName)
                        interfaces?.forEach { checkType(it) }
                    }

                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
                                checkType(owner)
                            }
                            override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
                                checkType(owner)
                            }
                            override fun visitTypeInsn(opcode: Int, type: String?) {
                                checkType(type)
                            }
                        }
                    }
                }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            } catch (_: Exception) {}
        }
        return prefixes
    }

    private fun incrementComponentVersions(srcDir: File) {
        if (!srcDir.exists()) return
        val versionPattern = java.util.regex.Pattern.compile(
            "(@(?:[a-zA-Z0-9_.]+\\.)?DesignerComponent\\s*\\([\\s\\S]*?\\bversion\\b\\s*=\\s*)(\"?)(\\d+)(\"?)",
            java.util.regex.Pattern.DOTALL
        )
        srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.forEach { file ->
            val content = file.readText(Charsets.UTF_8)
            val matcher = versionPattern.matcher(content)
            val sb = StringBuffer()
            var modified = false
            while (matcher.find()) {
                val prefix = matcher.group(1)
                val openQuote = matcher.group(2)
                val currentVersion = matcher.group(3).toIntOrNull() ?: 1
                val closeQuote = matcher.group(4)
                val newVersion = currentVersion + 1
                val replacement = java.util.regex.Matcher.quoteReplacement("${prefix}${openQuote}${newVersion}${closeQuote}")
                matcher.appendReplacement(sb, replacement)
                modified = true
            }
            if (modified) {
                matcher.appendTail(sb)
                file.writeText(sb.toString(), Charsets.UTF_8)
            }
        }
    }
}
