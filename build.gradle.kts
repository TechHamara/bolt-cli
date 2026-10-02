import java.text.SimpleDateFormat
import java.util.Date

plugins {
    kotlin("jvm") version "1.9.22"
    application
}

version = "2.0.0"

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

val jarjarDep by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(kotlin("stdlib:1.9.22"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("org.fusesource.jansi:jansi:2.4.1")
    implementation("org.ow2.asm:asm:9.7.1")
    jarjarDep("org.pantsbuild:jarjar:1.7.2")
}

application {
    mainClass.set("com.techhamara.bolt.cli.Main")
}

kotlin {
    jvmToolchain(11)
}

val distributionDir = layout.projectDirectory.dir("distribution")
val distLibsDir = distributionDir.dir("libs")

tasks.jar {
    archiveFileName.set("bolt.jar")
    destinationDirectory.set(distributionDir.asFile)

    manifest {
        val buildDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        attributes["Main-Class"] = "com.techhamara.bolt.cli.Main"
        attributes["Implementation-Title"] = "Bolt Compiler"
        attributes["Implementation-Version"] = "2.0.0"
        attributes["Built-On"] = buildDateStr
    }

    // Embed gson and kotlin runtime inside bolt.jar so it runs with `java -jar bolt.jar` standalone
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val assembleDistribution by tasks.registering {
    dependsOn(tasks.jar)
    doLast {
        val libsFolder = distLibsDir.asFile
        libsFolder.mkdirs()
        val toolsFolder = File(libsFolder, "tools").apply { mkdirs() }

        val assetsDir = file("../app/src/main/assets")
        val appLibsDir = file("../app/libs")

        // 1. Copy toolchain libraries to tools/ and libs/
        val assetFiles = listOf(
            "android.jar",
            "annotations.jar",
            "appinventor-stubs-v3.jar",
            "desugar_jdk_libs.jar",
            "desugar_jdk_libs_configuration.json",
            "kotlin-compiler.jar",
            "kotlin-stdlib.jar",
            "r8.jar"
        )
        for (name in assetFiles) {
            val src = File(assetsDir, name)
            if (src.exists()) {
                src.copyTo(File(toolsFolder, name), overwrite = true)
                src.copyTo(File(libsFolder, name), overwrite = true)
                println("Copied asset: $name -> distribution/libs/tools/ and distribution/libs/")
            }
        }

        // 2. Setup tools/aidl directory and clean up legacy bin directories
        val destAidlTools = File(toolsFolder, "aidl").apply { mkdirs() }
        val legacyBinTools = File(toolsFolder, "bin")
        if (legacyBinTools.exists()) {
            legacyBinTools.deleteRecursively()
            println("Removed legacy tools/bin directory")
        }
        val legacyBinLibs = File(libsFolder, "bin")
        if (legacyBinLibs.exists()) {
            // Copy any aidl binaries from legacyBinLibs to destAidlTools before deleting
            legacyBinLibs.copyRecursively(destAidlTools, overwrite = false)
            legacyBinLibs.deleteRecursively()
            println("Migrated and removed legacy libs/bin directory")
        }

        val srcBin = File(assetsDir, "bin")
        if (srcBin.exists()) {
            srcBin.copyRecursively(destAidlTools, overwrite = true)
            println("Copied native bin/ directory -> distribution/libs/tools/aidl/")
        }

        // 3. Copy aidl.exe if found in Android SDK
        val sdkDir = File("C:/Users/kapil/AppData/Local/Android/Sdk/build-tools")
        if (sdkDir.exists()) {
            val aidlExe = sdkDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("aidl.exe", ignoreCase = true) }
            if (aidlExe != null) {
                aidlExe.copyTo(File(destAidlTools, "aidl.exe"), overwrite = true)
                println("Copied Windows aidl.exe -> distribution/libs/tools/aidl/aidl.exe")
            }
        }

        // 4. Place toolchain libraries to distribution/libs/tools/
        val boltLibs = File("C:/Users/kapil/.bolt/libs")
        val boltTools = File(boltLibs, "tools")
        val boltAidl = File(boltLibs, "aidl")
        
        val toolchainFiles = listOf(
            "android.jar",
            "annotations.jar",
            "appinventor-stubs-v3.jar",
            "desugar_jdk_libs.jar",
            "desugar_jdk_libs_configuration.json",
            "ecj.jar",
            "ecj-3.42.0-patched.jar",
            "jarjar-1.7.2.jar",
            "kotlin-compiler.jar",
            "kotlin-stdlib.jar",
            "r8.jar",
            "strguard-plugin-1.0.1.jar",
            "trove4j.jar",
            "kawa.jar"
        )

        for (name in toolchainFiles) {
            val dest = File(toolsFolder, name)
            val candidate = listOfNotNull(
                File(toolsFolder, name),
                File(libsFolder, name),
                File(boltTools, name),
                File(boltLibs, name),
                File(appLibsDir, name),
                File(projectDir, "../ecj_test/$name"),
                if (name == "trove4j.jar") File("C:/Users/kapil/.gradle/caches/jars-9/f82ca070d815f3b9333bbe244d578a6e/trove4j-1.0.20200330.jar") else null
            ).firstOrNull { it.exists() && it.isFile }

            if (candidate != null) {
                if (!dest.exists() || dest.length() != candidate.length() || dest.absolutePath != candidate.absolutePath) {
                    candidate.copyTo(dest, overwrite = true)
                }
                println("Placed toolchain jar: $name -> distribution/libs/tools/$name (${dest.length() / 1024} KB)")
            } else {
                println("WARNING: toolchain file not found: $name")
            }
        }

        // Copy AIDL toolchain assets to tools/aidl/
        if (File(boltAidl, "framework.aidl").exists()) {
            File(boltAidl, "framework.aidl").copyTo(File(destAidlTools, "framework.aidl"), overwrite = true)
            File(boltAidl, "framework.aidl").copyTo(File(toolsFolder, "framework.aidl"), overwrite = true)
        }
        if (File(boltAidl, "aidl.exe").exists()) {
            File(boltAidl, "aidl.exe").copyTo(File(destAidlTools, "aidl.exe"), overwrite = true)
        }

        // 5. Copy App Inventor and AndroidX runtime libraries from C:\Users\kapil\.bolt\libs to distribution/libs/
        // Exclude unnecessary old bolt annotations processor jar, old android jar files, and toolchain files
        val excludeFromLibs = setOf(
            "AnnotationProcessors.jar",
            "annotations-processor.jar",
            "android-33.jar",
            "android-javadoc.jar",
            "android.jar",
            "r8.jar",
            "kotlin-compiler.jar",
            "ecj.jar",
            "ecj-3.42.0-patched.jar",
            "strguard-plugin-1.0.1.jar",
            "jarjar-1.7.2.jar",
            "desugar_jdk_libs.jar",
            "desugar_jdk_libs_configuration.json"
        )

        if (boltLibs.exists()) {
            val jars = boltLibs.listFiles { f -> f.isFile && (f.extension == "jar" || f.extension == "aar") } ?: emptyArray()
            for (jar in jars) {
                if (excludeFromLibs.contains(jar.name)) continue
                val dest = File(libsFolder, jar.name)
                if (!dest.exists() || dest.length() != jar.length()) {
                    jar.copyTo(dest, overwrite = true)
                }
            }
            println("Copied runtime libraries from C:/Users/kapil/.bolt/libs -> distribution/libs/")
        }

        // 6. Purge unnecessary old processor jars and android jars from distribution/libs/
        for (unwanted in excludeFromLibs) {
            val f = File(libsFolder, unwanted)
            if (f.exists() && f.isFile) {
                f.delete()
                println("Purged unnecessary file from distribution/libs/: $unwanted")
            }
        }

        // 7. Bundle Mini-NDK out-of-the-box into distribution/libs/ndk/
        val boltNdk = File(boltLibs, "ndk")
        val destNdk = File(libsFolder, "ndk")
        if (boltNdk.exists() && boltNdk.isDirectory) {
            val ndkMarker = File(destNdk, "source.properties")
            if (!destNdk.exists() || !ndkMarker.exists()) {
                println("Bundling Mini-NDK from ${boltNdk.absolutePath} -> distribution/libs/ndk/...")
                boltNdk.copyRecursively(destNdk, overwrite = true)
                println("Mini-NDK bundled successfully!")
            } else {
                println("Mini-NDK already present in distribution/libs/ndk/")
            }
        }

        // 7b. Bundle ProGuard into distribution/libs/proguard/
        val boltProguard = File(boltLibs, "proguard")
        val destProguard = File(libsFolder, "proguard").apply { mkdirs() }
        val srcProguardJar = File(boltProguard, "proguard.jar")
        val destProguardJar = File(destProguard, "proguard.jar")
        if (srcProguardJar.exists() && (!destProguardJar.exists() || destProguardJar.length() != srcProguardJar.length())) {
            srcProguardJar.copyTo(destProguardJar, overwrite = true)
            println("Copied ProGuard from ${srcProguardJar.absolutePath} -> distribution/libs/proguard/proguard.jar")
        }

        // 8. Copy icon.png ONLY outside libs/ (at root of distribution)
        val iconSrc = file("assets/icon.png")
        if (iconSrc.exists()) {
            iconSrc.copyTo(File(distributionDir.asFile, "icon.png"), overwrite = true)
            println("Copied official icon.png -> distribution/icon.png")
        }
        // Purge any unwanted icon.png inside libs/, tools/, and bin/
        val unwantedIcons = listOf(
            File(libsFolder, "icon.png"),
            File(toolsFolder, "icon.png"),
            File(destAidlTools, "icon.png"),
            File(distributionDir.asFile, "bin/icon.png")
        )
        for (icon in unwantedIcons) {
            if (icon.exists()) {
                icon.delete()
                println("Purged unwanted icon: ${icon.absolutePath}")
            }
        }

        // 8. Create platform launcher scripts
        val boltBat = File(distributionDir.asFile, "bolt.bat")
        boltBat.writeText(
            """
            @echo off
            setlocal
            chcp 65001 >nul 2>&1
            java -Dfile.encoding=UTF-8 -jar "%~dp0bolt.jar" %*
            """.trimIndent(),
            Charsets.UTF_8
        )
        println("Generated Windows launcher: distribution/bolt.bat")

        val boltSh = File(distributionDir.asFile, "bolt")
        boltSh.writeText(
            """
            #!/usr/bin/env sh
            # Bolt Compiler Linux / macOS / Termux Launcher
            SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
            exec java -jar "${'$'}SCRIPT_DIR/bolt.jar" "$@"
            """.trimIndent(),
            Charsets.UTF_8
        )
        boltSh.setExecutable(true)
        println("Generated Unix/Termux launcher: distribution/bolt")

        println("\n>>> Bolt Distribution successfully assembled in: ${distributionDir.asFile.absolutePath} <<<")
    }
}

