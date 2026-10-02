package com.techhamara.bolt.compiler

import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Discovers and manages toolchain libraries in the external `libs/` folder.
 * Works uniformly across Windows, macOS, Linux, and Android Termux.
 */
object LibLocator {

    private var explicitLibsDir: File? = null

    fun setExplicitLibsDir(dir: File) {
        explicitLibsDir = dir
    }

    /**
     * Resolves the `libs/` directory using multiple discovery heuristics:
     * 1. Explicit CLI argument (--libs <path>)
     * 2. Environment variable (BOLT_LIBS_DIR)
     * 3. Sibling directory relative to bolt.jar
     * 4. Current working directory (./libs)
     * 5. User home directory (~/.bolt/libs)
     */
    fun findLibsDir(projectDir: File? = null): File {
        explicitLibsDir?.let {
            if (it.exists() && it.isDirectory) return it
        }

        System.getenv("BOLT_LIBS_DIR")?.let { envPath ->
            val dir = File(envPath)
            if (dir.exists() && dir.isDirectory) return dir
        }

        try {
            val codeSource = LibLocator::class.java.protectionDomain.codeSource
            if (codeSource != null) {
                val jarFile = File(codeSource.location.toURI())
                val siblingLibs = File(jarFile.parentFile, "libs")
                if (siblingLibs.exists() && siblingLibs.isDirectory) {
                    return siblingLibs
                }
                val parentSiblingLibs = File(jarFile.parentFile?.parentFile, "libs")
                if (parentSiblingLibs.exists() && parentSiblingLibs.isDirectory) {
                    return parentSiblingLibs
                }
            }
        } catch (ignored: Exception) {}

        val cwdLibs = File("libs")
        if (cwdLibs.exists() && cwdLibs.isDirectory) return cwdLibs

        if (projectDir != null) {
            val projLibs = File(projectDir, "libs")
            if (projLibs.exists() && projLibs.isDirectory) return projLibs
        }

        val homeLibs = BoltLocator.getLibsDir()
        if (homeLibs.exists() && homeLibs.isDirectory) return homeLibs

        val legacyHomeLibs = File(System.getProperty("user.home"), ".bolt/libs")
        if (legacyHomeLibs.exists() && legacyHomeLibs.isDirectory) return legacyHomeLibs

        // Return fallback ./libs
        return File("libs")
    }

    fun findLib(libsDir: File, vararg candidateNames: String): File? {
        val boltHome = BoltLocator.getBoltHome()
        val searchDirs = listOf(
            File(libsDir, "tools"),
            File(libsDir, "proguard"),
            File(libsDir, "loots"),
            File(libsDir.parentFile, "tools"),
            File(libsDir.parentFile, "proguard"),
            File(libsDir.parentFile, "loots"),
            libsDir,
            File(boltHome, "libs/tools"),
            File(boltHome, "libs/proguard"),
            File(boltHome, "libs"),
            File(System.getProperty("user.home"), ".bolt/libs/tools"),
            File(System.getProperty("user.home"), ".bolt/libs/proguard"),
            File(System.getProperty("user.home"), ".bolt/libs")
        )
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in candidateNames) {
                val f = File(dir, name)
                if (f.exists() && f.isFile) return f
            }
            // Check with prefix matching (e.g. ecj*.jar, desugar_jdk_libs*.json)
            val files = dir.listFiles() ?: continue
            for (name in candidateNames) {
                val clean = name.replace(".jar", "").replace(".json", "")
                val matched = files.firstOrNull {
                    it.isFile && it.name.startsWith(clean) && (it.name.endsWith(".jar") || it.name.endsWith(".json") || it.name.endsWith(".png"))
                }
                if (matched != null) return matched
            }
        }
        return null
    }

    private val classLoaderCache = java.util.concurrent.ConcurrentHashMap<String, ClassLoader>()

    fun createClassLoader(parent: ClassLoader? = null, vararg jars: File?): ClassLoader {
        val existingJars = jars.filterNotNull().filter { it.exists() }
        val cacheKey = existingJars.map { it.canonicalPath }.sorted().joinToString(";")
        if (cacheKey.isNotEmpty()) {
            val cached = classLoaderCache[cacheKey]
            if (cached != null) return cached
        }

        val urls = existingJars.map { it.toURI().toURL() }
        val parentLoader = parent ?: (Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader())
        val loader = URLClassLoader(urls.toTypedArray(), parentLoader)
        if (cacheKey.isNotEmpty()) {
            classLoaderCache[cacheKey] = loader
        }
        return loader
    }
}
