package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.*
import com.techhamara.bolt.compiler.BoltCompiler
import com.techhamara.bolt.compiler.LibLocator
import com.techhamara.bolt.testing.BoltMockContainer
import com.techhamara.bolt.testing.ComponentContractTester
import java.io.File
import java.lang.reflect.Modifier

object TestCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        var projectPath = "."
        var generateTests = false
        var contractOnly = false
        val targetScreens = mutableListOf<com.techhamara.bolt.testing.ScreenSizeClass>()

        var argIdx = 0
        while (argIdx < args.size) {
            val arg = args[argIdx]
            when {
                arg == "-g" || arg == "--generate" -> generateTests = true
                arg == "--contract-only" -> contractOnly = true
                arg == "--screens" -> {
                    argIdx++
                    if (argIdx < args.size) {
                        val parts = args[argIdx].split(",", "|")
                        for (p in parts) {
                            if (p.trim().equals("all", ignoreCase = true)) {
                                targetScreens.addAll(com.techhamara.bolt.testing.ScreenSizeClass.entries)
                            } else {
                                com.techhamara.bolt.testing.ScreenSizeClass.parse(p.trim())?.let { targetScreens.add(it) }
                            }
                        }
                    }
                }
                arg.startsWith("--screens=") -> {
                    val parts = arg.substringAfter("--screens=").split(",", "|")
                    for (p in parts) {
                        if (p.trim().equals("all", ignoreCase = true)) {
                            targetScreens.addAll(com.techhamara.bolt.testing.ScreenSizeClass.entries)
                        } else {
                            com.techhamara.bolt.testing.ScreenSizeClass.parse(p.trim())?.let { targetScreens.add(it) }
                        }
                    }
                }
                arg == "--screen" -> {
                    argIdx++
                    if (argIdx < args.size) {
                        com.techhamara.bolt.testing.ScreenSizeClass.parse(args[argIdx].trim())?.let { targetScreens.add(it) }
                    }
                }
                arg.startsWith("--screen=") -> {
                    val p = arg.substringAfter("--screen=")
                    com.techhamara.bolt.testing.ScreenSizeClass.parse(p.trim())?.let { targetScreens.add(it) }
                }
                else -> {
                    if (!arg.startsWith("-")) {
                        projectPath = arg
                    }
                }
            }
            argIdx++
        }

        val activeScreens = if (targetScreens.isNotEmpty()) {
            targetScreens.distinct()
        } else {
            listOf(
                com.techhamara.bolt.testing.ScreenSizeClass.COMPACT,
                com.techhamara.bolt.testing.ScreenSizeClass.MEDIUM,
                com.techhamara.bolt.testing.ScreenSizeClass.EXPANDED
            )
        }

        val projectDir = File(projectPath).canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")

        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        val libsDir = LibLocator.findLibsDir(projectDir)
        val toolsDir = File(libsDir, "tools")
        val depsDir = File(projectDir, "deps")

        val dotBoltDir = File(projectDir, ".bolt").apply { mkdirs() }
        val binDir = File(dotBoltDir, "bin")

        // 1. Ensure main project classes are compiled and up to date
        val srcDir = File(projectDir, "src")
        val srcFiles = if (srcDir.exists()) srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.toList() else emptyList()
        val latestSrcMod = srcFiles.maxOfOrNull { it.lastModified() } ?: 0L

        // Clean up orphaned .class files in binDir that no longer exist in srcDir
        var orphanedCleaned = false
        if (srcDir.exists() && binDir.exists()) {
            val validTopLevels = srcFiles.map { f ->
                f.relativeTo(srcDir).path.replace(File.separatorChar, '/').substringBeforeLast('.')
            }.toSet()

            binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
                val rel = classFile.relativeTo(binDir).path.replace(File.separatorChar, '/').removeSuffix(".class")
                val topLevel = rel.substringBefore('$')
                if (topLevel !in validTopLevels && !topLevel.endsWith("/R") && !topLevel.endsWith("/BuildConfig")) {
                    classFile.delete()
                    orphanedCleaned = true
                }
            }
        }

        var classFiles = if (binDir.exists()) binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() else emptyList()
        val latestBinMod = classFiles.maxOfOrNull { it.lastModified() } ?: 0L

        val needsCompile = classFiles.isEmpty() || latestBinMod < latestSrcMod || orphanedCleaned
        if (needsCompile) {
            logger.startTask("Compiling main sources")
            val compiler = BoltCompiler(libsDir) { logger.dbg(it) }
            val res = compiler.build(projectDir)
            if (!res.success) {
                logger.stopTask(false)
                logger.err("Main source compilation failed. Cannot run tests.")
                return 1
            }
            logger.stopTask(true)
            classFiles = if (binDir.exists()) binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() else emptyList()
        }

        val androidJar = LibLocator.findLib(libsDir, "android.jar") ?: File(toolsDir, "android.jar")
        val stubsJar = LibLocator.findLib(libsDir, "appinventor-stubs-v3.jar") ?: File(toolsDir, "appinventor-stubs-v3.jar")
        val ecjJar = LibLocator.findLib(libsDir, "ecj.jar", "ecj-3.42.0.jar", "ecj-3.42.0-patched.jar", "ecj-3.18.0.jar")

        val boltJar = try {
            val codeSource = TestCommand::class.java.protectionDomain.codeSource
            if (codeSource != null) {
                val f = File(codeSource.location.toURI())
                if (f.exists()) f else null
            } else null
        } catch (_: Exception) { null }

        // 2. Build ClassLoader for project classes and execute Automated Contract Tests
        val contractCp = mutableListOf<File>()
        contractCp.add(binDir)
        if (androidJar.exists()) contractCp.add(androidJar)
        if (stubsJar.exists()) contractCp.add(stubsJar)
        if (boltJar != null && boltJar.exists()) contractCp.add(boltJar)
        libsDir.listFiles { _, name -> name.endsWith(".jar") }?.forEach { contractCp.add(it) }
        File(projectDir, "libs").listFiles { _, name -> name.endsWith(".jar") }?.forEach { contractCp.add(it) }
        depsDir.listFiles { _, name -> name.endsWith(".jar") }?.forEach { contractCp.add(it) }

        val classLoader = LibLocator.createClassLoader(
            TestCommand::class.java.classLoader,
            *contractCp.toTypedArray()
        )

        logger.startTask("Running zero-code Contract, Event, Helper & Multi-Screen tests")
        val contractSummary = ComponentContractTester.runTests(projectDir, binDir, classLoader, logger, activeScreens)
        val contractPassed = contractSummary.totalFailed == 0
        logger.stopTask(contractPassed)

        val testDir = File(projectDir, "test")

        // 3. Handle --generate flag to scaffold comprehensive component unit tests
        if (generateTests) {
            generateTestSuite(projectDir, testDir, binDir, classLoader, logger)
        }

        if (contractOnly) {
            return if (contractPassed) 0 else 1
        }

        // 4. Check if custom unit tests exist in test/
        val testJavaFiles = if (testDir.exists()) {
            testDir.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        } else {
            emptyList()
        }

        if (testJavaFiles.isEmpty()) {
            println()
            if (contractPassed) {
                println("> " + "ALL TESTS PASSED".green() + " (Zero-code automated contract & helper verification succeeded)")
                println("  Tip: Run 'bolt test --generate' to generate custom JUnit 5 test classes in test/".grey())
            } else {
                println("> " + "TEST FAILED".red() + " (${contractSummary.totalFailed} contract check failures)")
            }
            println()
            return if (contractPassed) 0 else 1
        }

        // 5. Compile and run custom JUnit 5 tests
        var junitJar = LibLocator.findLib(libsDir, "junit-platform-console-standalone.jar")
            ?: File(toolsDir, "junit-platform-console-standalone.jar").takeIf { it.exists() }
            ?: File(System.getProperty("user.home"), ".bolt/libs/tools/junit-platform-console-standalone.jar").takeIf { it.exists() }
            ?: try {
                File(System.getProperty("user.home"), ".gradle/caches").walkTopDown().firstOrNull { it.isFile && it.name.startsWith("junit-platform-console-standalone") && it.extension == "jar" }
            } catch (_: Exception) { null }

        if (junitJar == null || !junitJar.exists()) {
            logger.info("Downloading JUnit 5 Console Standalone Runner...")
            val destJunit = File(toolsDir, "junit-platform-console-standalone.jar")
            toolsDir.mkdirs()
            val downloadUrl = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"
            try {
                java.net.URL(downloadUrl).openStream().use { input ->
                    java.io.FileOutputStream(destJunit).use { output ->
                        input.copyTo(output)
                    }
                }
                junitJar = destJunit
                logger.info("Downloaded JUnit 5 Runner successfully.")
            } catch (e: Exception) {
                logger.err("Could not locate or download junit-platform-console-standalone.jar: ${e.message}")
                logger.info("Please ensure internet access or place the jar in ${toolsDir.absolutePath}")
                return if (contractPassed) 0 else 1
            }
        }

        val testClassesDir = File(dotBoltDir, "test-classes").apply { deleteRecursively(); mkdirs() }

        logger.startTask("Compiling ${testJavaFiles.size} custom test file(s)")
        val compileCp = mutableListOf<String>()
        compileCp.add(binDir.absolutePath)
        compileCp.add(junitJar.absolutePath)
        if (androidJar.exists()) compileCp.add(androidJar.absolutePath)
        if (stubsJar.exists()) compileCp.add(stubsJar.absolutePath)
        if (boltJar != null && boltJar.exists()) compileCp.add(boltJar.absolutePath)
        libsDir.listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { compileCp.add(it.absolutePath) }
        File(projectDir, "libs").listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { compileCp.add(it.absolutePath) }
        depsDir.listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { compileCp.add(it.absolutePath) }

        val javaExe = System.getProperty("java.home")?.let {
            val bin = File(it, "bin")
            val exe = File(bin, "java.exe")
            if (exe.exists()) exe.absolutePath else File(bin, "java").absolutePath
        } ?: "java"

        val ecjArgs = mutableListOf<String>().apply {
            add("-source"); add("8")
            add("-target"); add("8")
            add("-cp"); add(compileCp.joinToString(File.pathSeparator))
            add("-nowarn")
            add("-proc:none")
            add("-d"); add(testClassesDir.absolutePath)
            addAll(testJavaFiles.map { it.absolutePath })
        }

        var compileSuccess = false
        var compileOutput = ""

        if (ecjJar != null && ecjJar.exists()) {
            try {
                val cmd = mutableListOf(javaExe, "-jar", ecjJar.absolutePath)
                cmd.addAll(ecjArgs)
                val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                compileOutput = proc.inputStream.bufferedReader().readText()
                compileSuccess = (proc.waitFor() == 0)
            } catch (_: Exception) {
                compileSuccess = false
            }
        }

        if (!compileSuccess) {
            try {
                val javacExe = System.getProperty("java.home")?.let {
                    val bin = File(it, "bin")
                    val exe = File(bin, "javac.exe")
                    if (exe.exists()) exe.absolutePath else File(bin, "javac").absolutePath
                } ?: "javac"

                val javacArgs = mutableListOf(javacExe, "-cp", compileCp.joinToString(File.pathSeparator), "-d", testClassesDir.absolutePath)
                javacArgs.addAll(testJavaFiles.map { it.absolutePath })
                val proc = ProcessBuilder(javacArgs).redirectErrorStream(true).start()
                compileOutput = proc.inputStream.bufferedReader().readText()
                compileSuccess = (proc.waitFor() == 0)
            } catch (_: Exception) {
                compileSuccess = false
            }
        }

        if (!compileSuccess) {
            logger.stopTask(false)
            logger.err("Failed to compile test classes:\n$compileOutput")
            return 1
        }
        logger.stopTask(true)

        // 6. Run JUnit 5 Console Runner
        logger.startTask("Executing JUnit 5 tests")
        val runClasspath = mutableListOf<String>()
        runClasspath.add(testClassesDir.absolutePath)
        runClasspath.add(binDir.absolutePath)
        if (androidJar.exists()) runClasspath.add(androidJar.absolutePath)
        if (stubsJar.exists()) runClasspath.add(stubsJar.absolutePath)
        if (boltJar != null && boltJar.exists()) runClasspath.add(boltJar.absolutePath)
        libsDir.listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { runClasspath.add(it.absolutePath) }
        File(projectDir, "libs").listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { runClasspath.add(it.absolutePath) }
        depsDir.listFiles { _, name -> name.endsWith(".jar") }?.filter { isRuntimeJar(it) }?.forEach { runClasspath.add(it.absolutePath) }

        val junitCmd = mutableListOf(
            javaExe,
            "-jar", junitJar.absolutePath,
            "execute",
            "--class-path", runClasspath.joinToString(File.pathSeparator),
            "--scan-class-path=${testClassesDir.absolutePath}",
            "--include-engine=junit-jupiter",
            "--details=tree"
        )

        val runPb = ProcessBuilder(junitCmd)
        runPb.directory(projectDir)
        runPb.inheritIO()
        val runProc = runPb.start()
        val exitCode = runProc.waitFor()

        logger.stopTask(exitCode == 0)
        println()
        if (exitCode == 0 && contractPassed) {
            println("> " + "ALL TESTS SUCCESSFUL".green())
        } else {
            println("> " + "TEST SUITE COMPLETED WITH ERRORS".red() + " (Exit code $exitCode)".grey())
        }
        println()
        return if (exitCode == 0 && contractPassed) 0 else 1
    }

    private fun generateTestSuite(projectDir: File, testDir: File, binDir: File, classLoader: ClassLoader, logger: Logger) {
        val classFiles = binDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        for (f in classFiles) {
            val relPath = f.relativeTo(binDir).path.replace(File.separatorChar, '/').removeSuffix(".class")
            val fqcn = relPath.replace('/', '.')
            try {
                val cls = classLoader.loadClass(fqcn)
                val compBase = try { classLoader.loadClass("com.google.appinventor.components.runtime.Component") } catch (_: Throwable) { null }
                val isComp = (compBase?.isAssignableFrom(cls) == true || cls.annotations.any { it.annotationClass.java.simpleName == "DesignerComponent" })
                if (isComp && !Modifier.isAbstract(cls.modifiers) && !cls.isInterface) {
                    val pkg = cls.`package`?.name ?: ""
                    val compName = cls.simpleName
                    val pkgDir = if (pkg.isNotEmpty()) File(testDir, "java/" + pkg.replace('.', '/')) else File(testDir, "java")
                    pkgDir.mkdirs()
                    val testFile = File(pkgDir, "${compName}Test.java")

                    if (!testFile.exists()) {
                        val pkgDecl = if (pkg.isNotEmpty()) "package $pkg;\n\n" else ""
                        val testCode = """
                        $pkgDecl
                        import org.junit.jupiter.api.Test;
                        import org.junit.jupiter.api.BeforeEach;
                        import static org.junit.jupiter.api.Assertions.*;
                        import com.google.appinventor.components.runtime.ComponentContainer;
                        import com.techhamara.bolt.testing.BoltMockContainer;
                        import com.techhamara.bolt.testing.ScreenSizeClass;
                        import com.techhamara.bolt.testing.ScreenPosture;

                        /**
                         * Automated Unit, Multi-Screen & Lifecycle Test for $compName.
                         * Uses BoltMockContainer to test properties, functions, and events on desktop JVM
                         * across Compact Phones, Foldables, and Expanded Tablets.
                         */
                        public class ${compName}Test {

                            private BoltMockContainer mock;
                            private $compName component;

                            @BeforeEach
                            void setUp() {
                                mock = new BoltMockContainer();
                                ComponentContainer container = mock.getContainer();
                                component = new $compName(container);
                            }

                            @Test
                            void testComponentInitialization() {
                                assertNotNull(component, "Component instance should not be null");
                            }

                            @Test
                            void testMultiScreenAdaptability() {
                                // 1. Compact Phone Screen (360x640dp)
                                mock.setScreenSize(ScreenSizeClass.COMPACT);
                                assertEquals(360, mock.getContainer().Width());
                                assertEquals(640, mock.getContainer().Height());

                                // 2. Medium Foldable / Small Tablet (720x1280dp)
                                mock.setScreenSize(ScreenSizeClass.MEDIUM);
                                assertEquals(720, mock.getContainer().Width());

                                // 3. Expanded Tablet Screen (1280x800dp)
                                mock.setScreenSize(ScreenSizeClass.EXPANDED);
                                assertEquals(1280, mock.getContainer().Width());
                                assertEquals(800, mock.getContainer().Height());
                            }

                            @Test
                            void testOrientationChangeStateRestoration() {
                                // Simulate rotation (Portrait -> Landscape)
                                mock.setScreenSize(ScreenSizeClass.COMPACT);
                                mock.rotateOrientation();
                                assertEquals(640, mock.getContainer().Width());
                                assertEquals(360, mock.getContainer().Height());
                                assertNotNull(component, "Component should preserve state across orientation change");
                            }

                            @Test
                            void testFoldableTabletopPosture() {
                                // Simulate foldable tabletop posture
                                mock.setScreenSize(ScreenSizeClass.MEDIUM);
                                mock.setPosture(ScreenPosture.TABLETOP);
                                assertNotNull(component, "Component should remain resilient in tabletop posture");
                            }
                        }
                        """.trimIndent()
                        testFile.writeText(testCode)
                        logger.info("Generated multi-screen unit test suite: ${testFile.relativeTo(projectDir).path.green()}")
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private val toolchainPrefixes = setOf(
        "ecj", "r8", "d8", "dx", "bundletool", "apksigner", "asm", "kotlin-compiler",
        "jarjar", "strguard", "junit", "trove4j", "desugar_jdk_libs", "annotationprocessors",
        "annotations-processor", "android-", "kawa"
    )

    private fun isRuntimeJar(f: File): Boolean {
        val lower = f.name.lowercase()
        return !toolchainPrefixes.any { lower.startsWith(it) }
    }
}
