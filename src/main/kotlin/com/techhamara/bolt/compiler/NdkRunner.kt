package com.techhamara.bolt.compiler

import com.techhamara.bolt.parser.ConfigParser
import java.io.File

data class NdkBuildResult(
    val success: Boolean,
    val compiledLibraries: List<File> = emptyList(),
    val error: String? = null
)

/**
 * Cross-platform NDK runner supporting Mini-NDK and standard Android NDK installations.
 * Compiles C/C++ code into multi-architecture .so shared libraries.
 */
object NdkRunner {

    fun compile(
        projectDir: File,
        buildDir: File,
        config: ConfigParser.BoltConfig,
        libsDir: File? = null,
        logger: (String) -> Unit
    ): NdkBuildResult {
        if (!config.ndk.enabled) {
            return NdkBuildResult(true)
        }

        // 1. Locate C/C++ source directory
        val possibleCppDirs = listOf(
            File(projectDir, "src/cpp"),
            File(projectDir, "src/jni"),
            File(projectDir, "jni")
        )
        val cppDir = possibleCppDirs.firstOrNull { it.exists() && it.isDirectory }
        if (cppDir == null) {
            logger("- NDK is enabled but no C/C++ directory (src/cpp, src/jni, or jni) was found. Skipping native build.")
            return NdkBuildResult(true)
        }

        val sourceExtensions = setOf("cpp", "c", "cc", "cxx")
        val cppFiles = cppDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in sourceExtensions }.toList()
        if (cppFiles.isEmpty()) {
            logger("- No C/C++ source files found in ${cppDir.relativeTo(projectDir).path}. Skipping native build.")
            return NdkBuildResult(true)
        }

        logger("- Found ${cppFiles.size} native C/C++ file(s) in ${cppDir.relativeTo(projectDir).path}. Initializing Mini-NDK build...")

        // 2. Locate NDK installation
        val ndkDir = findNdkDirectory(config.ndk.path, libsDir)
        if (ndkDir == null || !ndkDir.exists()) {
            val errMsg = "Android NDK not found! Please ensure Mini-NDK is located at ~/.bolt/libs/ndk or set ANDROID_NDK_HOME."
            logger("error $errMsg")
            return NdkBuildResult(false, emptyList(), errMsg)
        }

        val ndkBuildExe = resolveNdkBuildExecutable(ndkDir)
        if (ndkBuildExe == null || !ndkBuildExe.exists()) {
            val errMsg = "ndk-build executable not found inside ${ndkDir.absolutePath}."
            logger("error $errMsg")
            return NdkBuildResult(false, emptyList(), errMsg)
        }

        if (!ndkBuildExe.canExecute()) {
            ndkBuildExe.setExecutable(true)
        }

        logger("- Using Mini-NDK: ${ndkDir.absolutePath}")

        // 3. Setup work directory: build/ndk-work/jni
        val workDir = File(buildDir, "ndk-work").apply { mkdirs() }
        val jniWorkDir = File(workDir, "jni").apply { mkdirs() }

        // Copy source files to work directory
        cppDir.copyRecursively(jniWorkDir, overwrite = true)

        // 4. Generate Android.mk if not present
        val androidMk = File(jniWorkDir, "Android.mk")
        if (!androidMk.exists()) {
            val relativeSrcs = jniWorkDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in sourceExtensions }
                .map { it.relativeTo(jniWorkDir).path.replace('\\', '/') }
                .joinToString(" ")

            val moduleName = config.ndk.module.ifBlank { "native-lib" }
            androidMk.writeText(
                """
                LOCAL_PATH := $(call my-dir)

                include $(CLEAR_VARS)
                LOCAL_MODULE    := $moduleName
                LOCAL_SRC_FILES := $relativeSrcs
                LOCAL_CPPFLAGS  := -Oz -fno-exceptions -fno-rtti -fvisibility=hidden -ffunction-sections -fdata-sections -flto
                LOCAL_CFLAGS    := -Oz -fvisibility=hidden -ffunction-sections -fdata-sections -flto
                LOCAL_LDFLAGS   := -Wl,--gc-sections -Wl,--exclude-libs,ALL -flto
                LOCAL_LDLIBS    := -llog -landroid
                include $(BUILD_SHARED_LIBRARY)
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        // 5. Generate Application.mk if not present
        val applicationMk = File(jniWorkDir, "Application.mk")
        if (!applicationMk.exists()) {
            val abisString = if (config.ndk.abis.isNotEmpty()) config.ndk.abis.joinToString(" ") else "armeabi-v7a arm64-v8a"
            val minPlatform = if (config.minSdk < 14) 14 else config.minSdk
            val stlString = config.ndk.stl.ifBlank { "c++_static" }
            applicationMk.writeText(
                """
                APP_ABI := $abisString
                APP_PLATFORM := android-$minPlatform
                APP_STL := $stlString
                APP_OPTIM := release
                APP_STRIP_MODE := --strip-all
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        // 6. Execute ndk-build
        logger("- Compiling Native C/C++ Code (${config.ndk.abis.joinToString(", ")})...")
        val command = listOf(
            ndkBuildExe.absolutePath,
            "NDK_PROJECT_PATH=${workDir.absolutePath}",
            "APP_BUILD_SCRIPT=${androidMk.absolutePath}",
            "NDK_APPLICATION_MK=${applicationMk.absolutePath}"
        )

        try {
            val process = ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isNotEmpty()) {
                    if (l.contains("error:", ignoreCase = true)) {
                        logger("error $l")
                    } else if (l.contains("warning:", ignoreCase = true)) {
                        logger("warning $l")
                    } else {
                        logger("- $l")
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val errMsg = "ndk-build failed with exit code $exitCode."
                logger("error $errMsg")
                return NdkBuildResult(false, emptyList(), errMsg)
            }
        } catch (e: Exception) {
            val errMsg = "NDK build execution exception: ${e.message}"
            logger("error $errMsg")
            return NdkBuildResult(false, emptyList(), errMsg)
        }

        // 7. Collect compiled .so libraries
        val libsOutDir = File(workDir, "libs")
        val projectJniDir = File(projectDir, "jni").apply { mkdirs() }
        val compiledLibs = mutableListOf<File>()

        if (libsOutDir.exists() && libsOutDir.isDirectory) {
            libsOutDir.walkTopDown().filter { it.isFile && it.extension.lowercase() == "so" }.forEach { soFile ->
                val abiDirName = soFile.parentFile.name
                val targetAbiDir = File(projectJniDir, abiDirName).apply { mkdirs() }
                val targetFile = File(targetAbiDir, soFile.name)
                soFile.copyTo(targetFile, overwrite = true)
                compiledLibs.add(targetFile)
            }
        }

        if (compiledLibs.isEmpty()) {
            val errMsg = "NDK build finished, but no .so shared libraries were generated in ${libsOutDir.absolutePath}."
            logger("error $errMsg")
            return NdkBuildResult(false, emptyList(), errMsg)
        }

        logger("- Native C/C++ compiled successfully (${compiledLibs.size} .so file(s) generated).")
        return NdkBuildResult(true, compiledLibs)
    }

    /**
     * Resolves the NDK directory using progressive discovery heuristics:
     * 1. Explicit path specified in bolt.yml (config.ndk.path)
     * 2. BOLT_NDK_DIR environment variable
     * 3. Bundled libs/ndk (inside libsDir or relative to bolt.jar) -> Out-of-the-box NDK
     * 4. User home Mini-NDK: ~/.bolt/libs/ndk
     * 5. Fallback local ./libs/ndk
     * 6. ANDROID_NDK_HOME or ANDROID_NDK_ROOT environment variables
     * 7. Standard Android SDK NDK in ANDROID_HOME/ndk or ANDROID_SDK_ROOT/ndk
     */
    fun findNdkDirectory(customPath: String? = null, libsDir: File? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.isDirectory) return f
        }

        System.getenv("BOLT_NDK_DIR")?.let {
            val f = File(it)
            if (f.exists() && f.isDirectory) return f
        }

        // 1. Check inside libsDir first (bundled out-of-the-box NDK)
        if (libsDir != null) {
            val candidates = listOf(
                File(libsDir, "ndk"),
                File(File(libsDir, "tools"), "ndk"),
                File(libsDir.parentFile, "libs/ndk"),
                File(libsDir.parentFile, "ndk")
            )
            for (c in candidates) {
                if (c.exists() && c.isDirectory) return c
            }
        }

        // 2. Relative to the running bolt.jar location (e.g., bin/../libs/ndk or libs/ndk)
        try {
            val codeSource = NdkRunner::class.java.protectionDomain.codeSource
            if (codeSource != null) {
                val jarFile = File(codeSource.location.toURI())
                val candidates = listOf(
                    File(jarFile.parentFile, "libs/ndk"),
                    File(jarFile.parentFile, "ndk"),
                    File(jarFile.parentFile.parentFile, "libs/ndk"),
                    File(jarFile.parentFile.parentFile, "ndk")
                )
                for (cand in candidates) {
                    if (cand.exists() && cand.isDirectory) return cand
                }
            }
        } catch (ignored: Exception) {}

        // 3. Bolt home Mini-NDK: <boltHome>/libs/ndk
        val boltHomeNdk = File(BoltLocator.getBoltHome(), "libs/ndk")
        if (boltHomeNdk.exists() && boltHomeNdk.isDirectory) return boltHomeNdk
        val userHomeNdk = File(System.getProperty("user.home"), ".bolt/libs/ndk")
        if (userHomeNdk.exists() && userHomeNdk.isDirectory) return userHomeNdk

        // 4. Fallback ./libs/ndk in current directory
        val cwdNdk = File("libs/ndk")
        if (cwdNdk.exists() && cwdNdk.isDirectory) return cwdNdk

        // 5. System environment variables
        listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT").forEach { envName ->
            System.getenv(envName)?.let {
                val f = File(it)
                if (f.exists() && f.isDirectory) return f
            }
        }

        // 6. Standard Android SDK NDK installations
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { envName ->
            System.getenv(envName)?.let { sdkPath ->
                val ndkDir = File(sdkPath, "ndk")
                if (ndkDir.exists() && ndkDir.isDirectory) {
                    val versions = ndkDir.listFiles { f -> f.isDirectory }
                    if (!versions.isNullOrEmpty()) {
                        versions.sortByDescending { it.name }
                        return versions.first()
                    }
                }
            }
        }

        return null
    }

    private fun resolveNdkBuildExecutable(ndkDir: File): File? {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val candidate = if (isWindows) File(ndkDir, "ndk-build.cmd") else File(ndkDir, "ndk-build")
        if (candidate.exists()) return candidate

        val subCandidate = if (isWindows) File(File(ndkDir, "build"), "ndk-build.cmd") else File(File(ndkDir, "build"), "ndk-build")
        if (subCandidate.exists()) return subCandidate

        return null
    }
}
