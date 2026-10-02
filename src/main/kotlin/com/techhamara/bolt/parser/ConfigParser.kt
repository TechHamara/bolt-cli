package com.techhamara.bolt.parser

import java.io.File

/**
 * Parser for bolt.yml project configuration.
 */
class ConfigParser {

    data class StrGuardConfig(
        val enabled: Boolean = false,
        val key: String = "TechHamara-MyKey-2026-Secret",
        val packages: List<String> = emptyList()
    )

    data class RelocationConfig(
        val enabled: Boolean = false,
        val skipStringConstants: Boolean = true,
        val include: List<String> = emptyList(),
        val exclude: List<String> = emptyList()
    )

    data class MinimizeConfig(
        val excludeDependency: List<String> = emptyList(),
        val excludeProject: List<String> = emptyList()
    )

    data class NdkConfig(
        val enabled: Boolean = false,
        val path: String? = null,
        val module: String = "native-lib",
        val abis: List<String> = listOf("armeabi-v7a", "arm64-v8a"),
        val stl: String = "c++_static"
    )

    data class BoltConfig(
        val author: String = "Unknown",
        val version: String = "1.0.0",
        val minSdk: Int = 14,
        val compileSdk: Int = 0,
        val autoVersion: Boolean = false,
        val boltVersion: String = "2.0.0",
        val buildTime: String = "",
        val dependencies: List<String> = emptyList(),
        val compileTime: List<String> = emptyList(),
        val providedDependencies: List<String> = emptyList(),
        val repositories: List<String> = emptyList(),
        val assets: List<String> = emptyList(),
        val xmls: List<String> = emptyList(),
        val homepage: String = "",
        val strguard: StrGuardConfig = StrGuardConfig(),
        val relocation: RelocationConfig = RelocationConfig(),
        val minimize: MinimizeConfig = MinimizeConfig(),
        val coreLibraryDesugaring: Boolean = false,
        val deannonate: Boolean = true,
        val desugarDex: Boolean = false,
        val r8: Boolean = false,
        val proguard: Boolean = false,
        val ndk: NdkConfig = NdkConfig(),
        val kotlinVersion: String = "1.9.22"
    )

    fun parse(ymlFile: File, defaultPackageName: String = ""): BoltConfig {
        if (!ymlFile.exists()) return BoltConfig()

        val content = ymlFile.readText(Charsets.UTF_8)
        var author = "Unknown"
        var version = "1.0.0"
        var minSdk = 14
        var compileSdk = 0
        var autoVersion = false
        var boltVersion = "2.0.0"
        var buildTime = ""
        var coreLibraryDesugaring = false
        var deannonate = true
        var desugarDex = false
        var r8 = false
        var proguard = false
        var kotlinVersion = "1.9.22"

        val dependencies = mutableListOf<String>()
        val compileTime = mutableListOf<String>()
        val providedDependencies = mutableListOf<String>()
        val repositories = mutableListOf<String>("https://repo1.maven.org/maven2", "https://dl.google.com/dl/android/maven2", "https://jitpack.io")
        val assets = mutableListOf<String>()
        val xmls = mutableListOf<String>()
        var homepage = ""

        // StrGuard
        var strguardEnabled = false
        var strguardKey = "TechHamara-MyKey-2026-Secret"
        val strguardPackages = mutableListOf<String>()
        var inStrGuard = false
        var inStrguardPackages = false

        // Relocation
        var relocationEnabled = false
        var relocationSkipStrings = true
        val relocationInclude = mutableListOf<String>()
        val relocationExclude = mutableListOf<String>()
        var inRelocation = false
        var currentRelocationList: MutableList<String>? = null

        // Minimize
        val minimizeExcludeDep = mutableListOf<String>()
        val minimizeExcludeProj = mutableListOf<String>()
        var inMinimize = false
        var currentMinimizeList: MutableList<String>? = null

        // NDK
        var ndkEnabled = false
        var ndkPath: String? = null
        var ndkModule = "native-lib"
        val ndkAbis = mutableListOf<String>()
        var ndkStl = "c++_static"
        var inNdk = false
        var inNdkAbis = false

        var currentSection = ""

        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) continue

            val lineWithoutComment = if (trimmed.contains("#")) {
                if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                    trimmed
                } else {
                    trimmed.substringBefore("#").trim()
                }
            } else {
                trimmed
            }
            if (lineWithoutComment.isEmpty()) continue

            if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t") && lineWithoutComment.endsWith(":")) {
                currentSection = lineWithoutComment.removeSuffix(":")
                inStrGuard = currentSection == "strguard"
                inRelocation = currentSection == "relocation"
                inMinimize = currentSection == "minimize"
                inNdk = currentSection == "ndk"
                inStrguardPackages = false
                inNdkAbis = false
                currentRelocationList = null
                currentMinimizeList = null
                continue
            }

            if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t")) {
                if (lineWithoutComment.startsWith("author:")) {
                    author = lineWithoutComment.substringAfter("author:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("version:")) {
                    version = lineWithoutComment.substringAfter("version:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("min_sdk:")) {
                    minSdk = lineWithoutComment.substringAfter("min_sdk:").trim().toIntOrNull() ?: 14
                } else if (lineWithoutComment.startsWith("compile_sdk:") || lineWithoutComment.startsWith("android_sdk:")) {
                    compileSdk = lineWithoutComment.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (lineWithoutComment.startsWith("auto_version:")) {
                    autoVersion = lineWithoutComment.substringAfter("auto_version:").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("coreLibraryDesugaring:")) {
                    coreLibraryDesugaring = lineWithoutComment.substringAfter("coreLibraryDesugaring:").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("desugar_dex:") || lineWithoutComment.startsWith("dex:")) {
                    desugarDex = lineWithoutComment.substringAfter(":").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("deannonate:") || lineWithoutComment.startsWith("deannotate:")) {
                    deannonate = lineWithoutComment.substringAfter(":").trim().lowercase() != "false"
                } else if (lineWithoutComment.startsWith("bolt_version:") || lineWithoutComment.startsWith("bolt:")) {
                    boltVersion = lineWithoutComment.substringAfter(":").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("build_time:") || lineWithoutComment.startsWith("buildTime:")) {
                    buildTime = lineWithoutComment.substringAfter(":").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("r8:") || lineWithoutComment.startsWith("R8:")) {
                    r8 = lineWithoutComment.substringAfter(":").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("proguard:")) {
                    proguard = lineWithoutComment.substringAfter(":").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("kotlin_version:") || lineWithoutComment.startsWith("kotlin:")) {
                    kotlinVersion = lineWithoutComment.substringAfter(":").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("homepage:")) {
                    homepage = lineWithoutComment.substringAfter("homepage:").trim().trim('"', '\'')
                }
            }

            if (currentSection == "dependencies" && lineWithoutComment.startsWith("-")) {
                val dep = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (dep.isNotEmpty()) dependencies.add(dep)
            } else if ((currentSection == "compile_time" || currentSection == "compileTime") && lineWithoutComment.startsWith("-")) {
                val dep = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (dep.isNotEmpty()) compileTime.add(dep)
            } else if ((currentSection == "provided_dependencies" || currentSection == "providedDependencies") && lineWithoutComment.startsWith("-")) {
                val dep = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (dep.isNotEmpty()) providedDependencies.add(dep)
            } else if (currentSection == "assets" && lineWithoutComment.startsWith("-")) {
                val asset = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (asset.isNotEmpty()) assets.add(asset)
            } else if (currentSection == "xmls" && lineWithoutComment.startsWith("-")) {
                val xml = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (xml.isNotEmpty()) xmls.add(xml)
            } else if (currentSection == "repositories" && lineWithoutComment.startsWith("-")) {
                val repo = lineWithoutComment.substring(1).trim().trim('"', '\'')
                if (repo.isNotEmpty() && !repositories.contains(repo)) repositories.add(repo)
            } else if (inStrGuard) {
                if (lineWithoutComment.startsWith("enabled:")) {
                    strguardEnabled = lineWithoutComment.substringAfter("enabled:").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("key:")) {
                    strguardKey = lineWithoutComment.substringAfter("key:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("packages:")) {
                    inStrguardPackages = true
                } else if (inStrguardPackages && lineWithoutComment.startsWith("-")) {
                    val pkg = lineWithoutComment.substring(1).trim().trim('"', '\'')
                    if (pkg.isNotEmpty()) strguardPackages.add(pkg)
                }
            } else if (inRelocation) {
                if (lineWithoutComment.startsWith("EnableAutoRelocation:") || lineWithoutComment.startsWith("enabled:")) {
                    relocationEnabled = lineWithoutComment.substringAfter(":").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("skipStringContants:") || lineWithoutComment.startsWith("skipStringConstants:")) {
                    relocationSkipStrings = lineWithoutComment.substringAfter(":").trim().lowercase() != "false"
                } else if (lineWithoutComment.startsWith("include:")) {
                    currentRelocationList = relocationInclude
                } else if (lineWithoutComment.startsWith("exclude:")) {
                    currentRelocationList = relocationExclude
                } else if (currentRelocationList != null && lineWithoutComment.startsWith("-")) {
                    val item = lineWithoutComment.substring(1).trim().trim('"', '\'')
                    if (item.isNotEmpty()) currentRelocationList.add(item)
                }
            } else if (inMinimize) {
                if (lineWithoutComment.startsWith("exclude_dependency:")) {
                    currentMinimizeList = minimizeExcludeDep
                } else if (lineWithoutComment.startsWith("exclude_project:")) {
                    currentMinimizeList = minimizeExcludeProj
                } else if (currentMinimizeList != null && lineWithoutComment.startsWith("-")) {
                    val item = lineWithoutComment.substring(1).trim().trim('"', '\'')
                    if (item.isNotEmpty()) currentMinimizeList.add(item)
                }
            } else if (inNdk) {
                if (lineWithoutComment.startsWith("enabled:")) {
                    ndkEnabled = lineWithoutComment.substringAfter("enabled:").trim().lowercase() == "true"
                } else if (lineWithoutComment.startsWith("path:")) {
                    ndkPath = lineWithoutComment.substringAfter("path:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("module:")) {
                    ndkModule = lineWithoutComment.substringAfter("module:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("stl:")) {
                    ndkStl = lineWithoutComment.substringAfter("stl:").trim().trim('"', '\'')
                } else if (lineWithoutComment.startsWith("abis:")) {
                    inNdkAbis = true
                } else if (inNdkAbis && lineWithoutComment.startsWith("-")) {
                    val abi = lineWithoutComment.substring(1).trim().trim('"', '\'')
                    if (abi.isNotEmpty()) ndkAbis.add(abi)
                }
            }
        }

        if (strguardPackages.isEmpty() && defaultPackageName.isNotEmpty()) {
            strguardPackages.add(defaultPackageName)
        }

        val finalNdkAbis = if (ndkAbis.isNotEmpty()) ndkAbis else listOf("armeabi-v7a", "arm64-v8a")

        return BoltConfig(
            author = author,
            version = version,
            minSdk = if (minSdk < 7) 7 else minSdk,
            compileSdk = compileSdk,
            autoVersion = autoVersion,
            boltVersion = boltVersion,
            buildTime = buildTime,
            dependencies = dependencies,
            compileTime = compileTime,
            providedDependencies = providedDependencies,
            repositories = repositories,
            assets = assets,
            xmls = xmls,
            homepage = homepage,
            strguard = StrGuardConfig(strguardEnabled, strguardKey, strguardPackages),
            relocation = RelocationConfig(relocationEnabled, relocationSkipStrings, relocationInclude, relocationExclude),
            minimize = MinimizeConfig(minimizeExcludeDep, minimizeExcludeProj),
            coreLibraryDesugaring = coreLibraryDesugaring,
            deannonate = deannonate,
            desugarDex = desugarDex,
            r8 = r8,
            proguard = proguard,
            ndk = NdkConfig(
                enabled = ndkEnabled,
                path = ndkPath,
                module = ndkModule,
                abis = finalNdkAbis,
                stl = ndkStl
            ),
            kotlinVersion = kotlinVersion
        )
    }
}
