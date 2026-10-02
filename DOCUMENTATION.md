# ⚡ Bolt CLI Documentation

**The Modern High-Performance Universal Extension Compiler for MIT App Inventor 2 & Distributions**

[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS%20%7C%20Android%20Termux-blue.svg)](#)
[![MIT App Inventor](https://img.shields.io/badge/Compatibility-MIT%20App%20Inventor%20%7C%20Kodular%20%7C%20Niotron-orange.svg)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

Welcome to the official technical documentation for **Bolt CLI**. Bolt is a state-of-the-art universal command-line tool, compiler daemon, and build system designed to develop, compile, optimize, and test extensions for **MIT App Inventor 2**, **Kodular**, **Niotron**, **AndroidBuilder**, and other compatible block-programming platforms.

---

## 📑 Table of Contents

1. [Architectural Overview](#-architectural-overview)
2. [Installation & Setup](#-installation--setup)
3. [CLI Commands Reference](#-cli-commands-reference)
4. [Project Configuration (`bolt.yml`)](#-project-configuration-boltyml)
5. [Step-by-Step Feature Guides](#-step-by-step-feature-guides)
   - [1. Creating & Building Extensions (Java & Kotlin)](#1-creating--building-extensions-java--kotlin)
   - [2. Lightning-Fast Builds with Compiler Daemon (`bolt daemon`)](#2-lightning-fast-builds-with-compiler-daemon-bolt-daemon)
   - [3. Clean Terminal Output & Real-Time System Telemetry (`BuildLog.txt`)](#3-clean-terminal-output--real-time-system-telemetry-buildlogtxt)
   - [4. D8 DEX Compilation & Incremental Bytecode Caching (`--dex`)](#4-d8-dex-compilation--incremental-bytecode-caching---dex)
   - [5. Dev Sync with Google Maven (`bolt sync dev`)](#5-dev-sync-with-google-maven-bolt-sync-dev)
   - [6. Real Local Unit Testing with JUnit 5 (`bolt test`)](#6-real-local-unit-testing-with-junit-5-bolt-test)
   - [7. Live Companion Hot-Reloading (`bolt run`)](#7-live-companion-hot-reloading-bolt-run)
   - [8. Mini-NDK & Native C/C++ (JNI) Compilation](#8-mini-ndk--native-cc-jni-compilation)
   - [9. Precompiled JNI Native Libraries (`@UsesNativeLibraries`)](#9-precompiled-jni-native-libraries-usesnativelibraries)
   - [10. Custom XML Integration (`@UsesXmls` & `@XmlElement`)](#10-custom-xml-integration-usesxmls--xmlelement)
   - [11. Multi-Component Projects, Features (`<uses-feature>`) & Queries (`<queries>`)](#11-multi-component-projects-features-uses-feature--queries-queries)
   - [12. 100% App Inventor Cloud Build Server Compatibility](#12-100-app-inventor-cloud-build-server-compatibility)
   - [13. Bytecode Deannotation Post-Build (`deannonate: true`)](#13-bytecode-deannotation-post-build-deannonate-true)
   - [14. Automatic Documentation & Helper Enums (`OptionList`)](#14-automatic-documentation--helper-enums-optionlist)
   - [15. Automated Offline RSA Licensing (`bolt auth`)](#15-automated-offline-rsa-licensing-bolt-auth)
   - [16. Auto-Detect AIDL Compilation](#16-auto-detect-aidl-compilation)
   - [17. Dependency Management, Assets & Shading (JarJar Relocation)](#17-dependency-management-assets--shading-jarjar-relocation)
   - [18. Modern Desugaring & Bytecode Protection](#18-modern-desugaring--bytecode-protection)
   - [19. Legacy Project Migration with Pre-Migration Backups (`bolt migrate`)](#19-legacy-project-migration-with-pre-migration-backups-bolt-migrate)
6. [Building Bolt Compiler from Source & Toolchain Maintenance](#-building-bolt-compiler-from-source--toolchain-maintenance)
7. [Frequently Asked Questions (FAQ)](#-frequently-asked-questions-faq)

---

## 🏗️ Architectural Overview

Bolt CLI is engineered from the ground up for maximum developer productivity, zero bloat, and instant build times:

- **In-Process JVM Engine**: Unlike legacy tools that spawn independent JVM processes for each build step, Bolt runs Java compilation (ECJ), Kotlin compilation, AIDL processing, R8 desugaring/shrinking, ProGuard, and ZIP packaging in an integrated in-process pipeline.
- **Persistent Compiler Daemon (`bolt daemon`)**: Keeps compiler toolchains and JIT-optimized classes resident in memory, achieving sub-4s incremental builds.
- **Clean Terminal Output & Deep Telemetry**: Distraction-free bullet logs in your terminal during builds, with comprehensive real-time hardware telemetry (RAM, storage, CPU cores, Android/Windows OS) and build duration saved to `.bolt/BuildLog.txt`.
- **100% MIT App Inventor Build Server Compatibility**: Packages `.aix` bundles using pure Java bytecode inside `AndroidRuntime.jar`, eliminates duplicate D8 DEX errors, strips XML comments from manifests, expands relative component classes, and enforces minimum SDK levels.
- **Incremental D8 DEX Caching**: Hashes class bytecode and configuration to provide instant DEX cache hits when no Java/Kotlin logic changed.
- **Universal Multiplatform**: Runs seamlessly on Windows, macOS (Intel & Apple Silicon), Linux, and on Android devices via Termux.
- **Zero-Setup Mini-NDK**: Built-in native C/C++ compiler toolchain (`~/.bolt/libs/ndk`) eliminates the need for 4GB+ Android Studio NDK installations while generating optimized **2-3 KB** `.so` binaries with `-Oz`, `-flto`, and `--strip-all`.
- **Automated Dropdown Helpers**: Rapidly scaffold `OptionList` dropdown enums (`bolt generate helper <type>` or `bolt create -t <int|str>`) with auto-import and `@SimpleProperty` demo method injection.

---

## 📥 Installation & Setup

Bolt requires a **Java Runtime Environment (JRE or JDK 11+, JDK 17 recommended)** to execute.

> [!NOTE]
> **ECJ Bundled**: Bolt includes its own Eclipse Compiler for Java (`ecj.jar`), so a standalone `javac` compiler is **not required**. Any standard Java 11+ runtime (`java`) is sufficient to run Bolt CLI and its bundled toolchain.

### 🪟 Windows (PowerShell)

Default location: `%LOCALAPPDATA%\Bolt` (e.g. `C:\Users\<user>\AppData\Local\Bolt`)

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

*To install in a custom directory:*

```powershell
& { $(iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb) } -InstallPath "D:\Tools\Bolt"
```

*To skip automated Java check/download:*

```powershell
& { $(iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb) } -SkipJava
```

### 🐧 Linux & 🍎 macOS

Default location: `~/Bolt`

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
```

*To install in a custom directory:*

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh -s -- --path /opt/Bolt
```

*To skip Java verification:*

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh -s -- --skip-java
```

### 📱 Android (Termux)

Default location: `~/Bolt`

Build extensions directly on your phone:

```bash
termux-setup-storage
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

*To install in a custom directory:*

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash -s -- --path ~/my_custom_bolt
```

*To skip Termux OpenJDK installation:*

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash -s -- --skip-java
```

---

## 💻 CLI Commands Reference

| Command | Description |
| :--- | :--- |
| `bolt build [path]` | Compiles the extension into a production `.aix` in `out/` |
| `bolt create <name>` | Scaffolds a new extension project (Java or Kotlin) |
| `bolt clean [path]` | Deletes old build artifacts, temporary files, and `.bolt` caches |
| `bolt tree [path]` | Generates visual project file hierarchy in terminal and `tree.txt` |
| `bolt add <coordinate>` | Searches Maven Central and adds dependency coordinate to `bolt.yml` |
| `bolt sync [path]` | Downloads and syncs project runtime dependencies into `deps/` |
| `bolt sync ndk` | Verifies and inspects Mini-NDK installation in `~/.bolt/libs/ndk` |
| `bolt sync dev` | Syncs internal dev toolchains and `desugar_jdk_libs:2.1.5` from Google Maven |
| `bolt deps <sync\|tree>` | Inspects and resolves project dependency trees |
| `bolt run [path]` | Launches live testing server with WebSocket (9000) & UDP (9001) companion discovery |
| `bolt test [path]` | Executes real unit tests locally powered by JUnit 5 Console Runner |
| `bolt auth <init\|generate>` | Offline RSA-2048 licensing system (keygen & customer activation license string) |
| `bolt generate helper <int\|str>` | Generates `OptionList` dropdown helper and injects demo `@SimpleProperty` into main class |
| `bolt migrate [type]` | Migrates legacy Rush, Fast, Template, or AI2 projects with automated ZIP backup |
| `bolt daemon <start\|stop\|status>` | Manages the background compilation daemon |
| `bolt upgrade` | Upgrades Bolt CLI to the newest available release |

### Command Flags

- `-r`, `--proguard`: Enable ProGuard bytecode optimization & obfuscation.
- `-s`, `--r8`: Enable Google R8 shrinking and tree-shaking task.
- `-o`, `--optimize`: Optimize bytecode size.
- `-dx`, `-x`, `--dex`: Generate desugared Dalvik/ART `classes.dex` via D8 with incremental caching.
- `-m`, `--keep-manifest`: Keep manifest components (`Activity`, `Service`, `Receiver`, `Provider`) in ProGuard / R8 rules.
- `--no-daemon`: Bypass the background daemon and compile locally in-process.
- `--deannotate`: Force bytecode deannotation (strip internal App Inventor annotations).
- `-t`, `--template <str|int>`: Scaffold pre-filled extension skeletons with helper enums on `create`.
- `-p`, `--package <pkg>`: Specify root package identifier on `create`.
- `-l`, `--language <Java|Kotlin>`: Set primary language on `create`.
- `-v`, `-d`, `--verbose`, `--debug`: Enable verbose diagnostic logs.
- `-c`, `--color`: Enable/disable ANSI terminal colors.
- `-V`, `--version`: Print CLI version information.

---

## ⚙️ Project Configuration (`bolt.yml`)

Every Bolt project contains a `bolt.yml` file in the project root:

```yaml
# Extension Author Name
author: YourName

# Extension Semantic Version
version: 1.0.0

# Extension Homepage URL (rendered in metadata and out/extension.txt)
homepage: "https://github.com/YourUsername/MyExtension"

# Minimum Android SDK level supported (Default: 14)
min_sdk: 14

# Target / Compile Android SDK level (e.g. 33, 34)
compile_sdk: 34

# Automatically increment component version on each build
auto_version: true

# Generate classes.dex via D8 automatically during build
desugar_dex: false

# Strip internal App Inventor annotations post-build for smaller binary size
deannonate: true

# Java 8+ API Backporting (java.time, Streams) down to API 14
coreLibraryDesugaring: false

# Extension assets declaration & packaging (warns if file > 5MB)
assets:
  - icon.png

# External libraries and runtime dependencies (Local JAR/AAR or Remote Maven)
dependencies:
  - "example.jar"                 # Local JAR or AAR file stored in 'deps' directory
  - "com.google.code.gson:gson:2.10.1" # Remote Maven coordinate

# Compile-time only dependencies [Local Only] (excluded from final AIX and classes.jar/dex)
compile_time:
  # - compile-only-lib.jar

# Provided dependencies (available in App Inventor companion; excluded from final AIX)
provided_dependencies:
  # - companion-provided.jar

# Custom Maven Repositories
repositories:
  - "https://repo1.maven.org/maven2"
  - "https://dl.google.com/dl/android/maven2"
  - "https://jitpack.io"

# StrGuard String Encryption
strguard:
  enabled: false
  key: "YourSecretEncryptionKey"
  packages:
    - "com.example.extension"

# Bytecode Package Relocation (JarJar Shading)
relocation:
  EnableAutoRelocation: true
  skipStringConstants: true
  include:
    - "com.google.gson.**"
  exclude: []

# Code Minimization & Tree Shaking Rules (R8)
minimize:
  exclude_dependency:
    - "com.google.code.gson:gson"
  exclude_project:
    - "com.example.extension"

# Native C/C++ Compilation (Mini-NDK)
ndk:
  enabled: false
  module: "native-lib"
  stl: "none" # "none" or "c++_static"
  abis:
    - "armeabi-v7a"
    - "arm64-v8a"
```

### 📋 Configuration Directives Reference

| Directive / Key | Description | Implementation & Build Details |
| :--- | :--- | :--- |
| `dependencies` | Runtime dependencies (Local JAR/AAR or Remote Maven) | Added to compilation classpath. Classes are extracted into `binDir` and bundled into `classes.jar` and `classes.dex` (with JarJar shading and R8/ProGuard optimization). Inline comments (`# Local JAR...`) are automatically stripped. |
| `compile_time` | Compile-time only dependencies `[Local Only]` | Added to ECJ/Kotlin classpath during source compilation, but **strictly excluded** from final `.aix` packaging and `classes.jar`/`classes.dex`. |
| `provided_dependencies` | Provided dependencies (Available in App Inventor companion) | Added to classpath and synced in `deps/`, but excluded from final `.aix` packaging to avoid duplicate class definitions at runtime. |
| `repositories` | Custom Maven repository URLs | Passed directly to `MavenResolver` (Default: MavenCentral, Google Maven, JitPack, JCenter). |
| `assets` | Extension assets declaration & validation | Declared assets are verified in `assets/`. Files larger than **5 MB** trigger an AIX packaging size warning. Verified assets are automatically bundled into the `.aix` archive. |
| `minimize` | R8 exclusion & keep rules | `exclude_dependency` and `exclude_project` generate `-keep class <pkg>.** { *; }` directives inside `.bolt/minimize-rules.pro` to protect crucial classes from shrinking. |
| `homepage` | Extension Homepage URL | Injected into component metadata and rendered in `out/extension.txt` as 🌐 **Homepage:** [Link](url). |
| `auto_version` | Component version increment | Automatically increments component version number in `bolt.yml` and `components.json` on each successful build. |
| `deannonate` | Bytecode annotation stripping | ASM-based remapper strips App Inventor metadata annotations (`@DesignerComponent`, `@SimpleFunction`) from bytecode post-compilation. |
| `desugar_dex` / `-dx` | D8 DEX compilation & caching | Direct Dalvik/ART `classes.dex` generation with incremental MD5 checksum caching. |

---

## 🚀 Step-by-Step Feature Guides

### 1. Creating & Building Extensions (Java & Kotlin)

#### Scaffold a New Project

```bash
bolt create MyExtension -l Java -p com.example.myextension
```

Or for Kotlin:

```bash
bolt create MyKotlinExt -l Kotlin -p com.example.kotlinext
```

#### Build Production Extension

```bash
bolt build
```

Output: `out/com.example.myextension.aix` ready to drag-and-drop into MIT App Inventor, Kodular, or Niotron!

#### Kotlin Extension Example

```kotlin
package com.example.kotlinext

import com.google.appinventor.components.annotations.*
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer

@DesignerComponent(
    version = 1,
    description = "A powerful Kotlin extension built with Bolt CLI",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "icon.png"
)
@SimpleObject(external = true)
class MyKotlinExt(container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()) {

    @SimpleFunction(description = "Calculates sum of two numbers")
    fun AddNumbers(a: Double, b: Double): Double = a + b

    @SimpleEvent(description = "Triggered when calculation finishes")
    fun OnCalculated(result: Double) {
        EventDispatcher.dispatchEvent(this, "OnCalculated", result)
    }
}
```

---

### 2. Lightning-Fast Builds with Compiler Daemon (`bolt daemon`)

Bolt includes a background compiler daemon that keeps all JVM toolchain libraries (ECJ, Kotlin compiler, ProGuard, D8) warmed up in RAM.

#### Usage

```bash
# Check daemon status
bolt daemon status

# Start daemon in background
bolt daemon start

# Run builds (automatically delegates to daemon!)
bolt build

# Stop daemon when finished
bolt daemon stop
```

When the daemon is running, `bolt build` automatically delegates to `http://127.0.0.1:19090/build`, executing builds in **under 3 to 4 seconds**! To bypass the daemon, simply append `--no-daemon`.

---

### 3. Clean Terminal Output & Real-Time System Telemetry (`BuildLog.txt`)

Bolt CLI provides an elegant, minimalist terminal build log while storing exhaustive machine diagnostics in `.bolt/BuildLog.txt`.

#### Clean Terminal Output

During `bolt build`, noisy log prefixes and internal ASM bytecode deannotation notices are suppressed in favor of clean bulleted milestones:

```text
- Increasing Components version
- Compiling 1 Java file(s) with ECJ...
- Coping extension assets
- Reading AndroidManifest.xml
- Java compilation completed successfully.
- Running D8 DEX compilation...
- D8 DEX generated successfully.
- Generating docs in Markdown
- Packaging extension at .\out\io.th.demo.aix (4.6KB)
```

#### Real-Time Host Telemetry & Hardware Specs

Every execution generates or appends to `.bolt/BuildLog.txt` with real-time diagnostic parameters:

```text
================================================================================
                    Bolt Compiler Build Log
================================================================================
Timestamp:          2026-09-07 20:38:15
Project Directory:  F:\Bolt-cli\bolt-jar\bolt-compiler\Demo
Extension:          io.th.demo
Build Status:       SUCCESS

=== Hardware & System Specs ===
System/Device:      MY-PC (or ro.product.model on Android Termux)
Available Processors: 16
Physical RAM:       7.5/7.9GB
Storage:            55.2/200.0GB
OS Name:            Windows 11
OS Version:         10.0 (Build 22631) (or Android 14 API 34 on Termux)
Java Version:       21.0.2 (Oracle Corporation)

... [ECJ, Manifest, D8, and AIX Packaging Logs] ...

Build completed in 3.42s
```

* **Physical RAM**: Uses Windows `GlobalMemoryStatusEx` and Linux/Termux `/proc/meminfo` to report true physical RAM usage (`Used/Total GB`).
* **Disk Space**: Queries the active filesystem volume to report free and total drive space (`Free/Total GB`).
* **Dynamic Termux Environment Detection**: Executes `getprop` on Android to extract exact device hardware model and OS build API levels.
* **Build Stopwatch**: Measures and logs total build completion duration at the footer.

---

### 4. D8 DEX Compilation & Incremental Bytecode Caching (`--dex`)

Compile your extension directly into Android Dalvik/ART bytecode:

```bash
bolt build -dx
# or in bolt.yml: desugar_dex: true
```

#### Incremental Caching

Bolt hashes all `.class` files in `build/bin/` using MD5 and saves the cache state in `.bolt/dex_cache.hash`. If no bytecode changed on subsequent builds, D8 compilation is bypassed instantly (`D8 DEX cache hit: classes.dex is up-to-date`), saving valuable development time.

---

### 5. Dev Sync with Google Maven (`bolt sync dev`)

Bolt provides an automated developer sync command that fetches and updates the official `desugar_jdk_libs:2.1.5` runtime and desugaring configuration directly from Google Maven (`https://dl.google.com/dl/android/maven2`):

```bash
bolt sync dev
```

Files are verified and stored in `libs/tools/` and `~/.bolt/libs/tools/`.

---

### 6. Real Local Unit Testing with JUnit 5 (`bolt test`)

Test your extension logic locally on your machine or inside Termux without requiring an Android emulator or physical device:

```bash
# Test current project
bolt test

# Test project at specific path
bolt test path/to/my-extension
```

#### How Bolt Runs Unit Tests

1. **Automatic Scaffolding**: If no test directory exists, Bolt automatically scaffolds `test/java/<pkg>/ExampleTest.java` with ready-to-run JUnit Jupiter test cases.
2. **Unified Classpath Compilation**: Test sources are compiled in-process using ECJ against your extension's compiled classes (`build/bin/classes`), project dependencies (`deps/`), App Inventor stubs (`appinventor-stubs-v3.jar`), and Android framework classes (`android.jar`).
3. **Official JUnit 5 Platform Console Standalone Runner**: Bolt invokes `junit-platform-console-standalone.jar` to run the test suite and display an interactive hierarchical test tree directly in your terminal:

```java
package com.example.myext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleTest {

    @Test
    @DisplayName("Verify addition logic")
    void addition() {
        assertEquals(4, 2 + 2, "Math calculation should match");
    }
}
```

```text
[INFO] Running Unit Tests with JUnit 5...
Thanks for using JUnit! Support its development at https://junit.org/sponsoring

.
+-- JUnit Jupiter [OK]
| '-- ExampleTest [OK]
|   '-- Verify addition logic() [OK]
'-- JUnit Vintage [OK]

Test run finished after 58 ms
[ 2 containers found ]
[ 0 containers skipped ]
[ 2 containers started ]
[ 0 containers aborted ]
[ 2 containers successful ]
[ 0 containers failed ]
[ 1 tests found ]
[ 0 tests skipped ]
[ 1 tests started ]
[ 0 tests aborted ]
[ 1 tests successful ]
[ 0 tests failed ]
```

---

### 7. Live Companion Hot-Reloading (`bolt run`)

Hot-reload your Java/Kotlin code directly onto your Android device in under a second!

```bash
bolt run
```

1. Starts a WebSocket server on `ws://0.0.0.0:9000`.
2. Starts UDP auto-discovery on port `9001` so the companion app can find your PC on your local Wi-Fi.
3. Watches `src/` for file changes (`.java`, `.kt`, `.aidl`, `.cpp`).
4. On file save, Bolt recompiles the project with D8, generates `classes.dex`, and immediately streams the byte array to the connected device via WebSocket.
5. Sends the `RELOAD` signal to dynamically reload the extension in `ReplForm`!

---

### 8. Mini-NDK & Native C/C++ (JNI) Compilation

Build high-performance C/C++ code directly into `.so` shared libraries without needing Android Studio NDK!

#### Configuration in `bolt.yml`

```yaml
ndk:
  enabled: true
  module: "native-lib"
  stl: "none" # Use "none" for ultra-compact 2KB binaries
  abis:
    - "armeabi-v7a"
    - "arm64-v8a"
```

#### Writing C++ Code (`src/cpp/native.cpp`)

```cpp
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_io_th_testndk_TestNDK_getNativeMessage(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("Hello from Bolt Mini-NDK C++!");
}
```

#### Compiling

```bash
bolt build
```

Bolt uses `-Oz` (size optimization), `-flto` (Link-Time Optimization), and `--strip-all`, producing native binaries as small as **2 KB - 3 KB**! The compiled `.so` files are automatically packaged into `files/libnative-lib.so-v7a` and `files/libnative-lib.so-v8a` per App Inventor specs.

---

### 9. Precompiled JNI Native Libraries (`@UsesNativeLibraries`)

If you have precompiled `.so` libraries (e.g. from OpenCV, TensorFlow Lite, or native SDKs), place them in `jni/`:

```
jni/
├── armeabi-v7a/
│   └── libsample.so
└── arm64-v8a/
    └── libsample.so
```

In your Java class:

```java
@UsesNativeLibraries(
    v7aLibraries = "libsample.so",
    v8aLibraries = "libsample.so"
)
public class MyExtension extends AndroidNonvisibleComponent { ... }
```

Bolt automatically maps `-v7a` and `-v8a` into `component_build_infos.json` and copies the physical binaries into the `.aix` archive.

---

### 10. Custom XML Integration (`@UsesXmls` & `@XmlElement`)

Integrate custom Android XML layouts and manifest snippets:

```java
@UsesXmls(xmls = {
    @XmlElement(
        relPath = "layout/custom_dialog.xml",
        xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
              "    android:layout_width=\"match_parent\"\n" +
              "    android:layout_height=\"wrap_content\">\n" +
              "    <TextView android:id=\"@+id/title\" android:text=\"Custom Layout\"/>\n" +
              "</LinearLayout>"
    )
})
public class MyExtension extends AndroidNonvisibleComponent { ... }
```

Bolt extracts these definitions, injects them into `assets/` and `files/` inside the `.aix`, and registers them in `component_build_infos.json`, maintaining full compatibility with Kodular and Niotron.

---

### 11. Multi-Component Projects, Features (`<uses-feature>`) & Queries (`<queries>`)

#### Multi-Component Extensions

Bolt supports multiple `@DesignerComponent` annotated classes within the same project. All components are scanned, updated with manifest permissions and native libraries, and serialized into `component_build_infos.json` as a complete JSON array.

#### Manifest Extraction

In `src/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myext">
    
    <!-- Required hardware feature -->
    <uses-feature android:name="android.hardware.camera" android:required="true" />

    <!-- Android 11+ Package Visibility Queries -->
    <queries>
        <package android:name="com.google.android.youtube" />
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
    </queries>

    <application>
        <activity android:name=".PreviewActivity" />
        <service android:name=".BackgroundSyncService" />
    </application>
</manifest>
```

Bolt automatically parses `<uses-feature>` and `<queries>` tags and injects them into `component_build_infos.json` under `"features"` and `"queries"`:

```json
[
  {
    "features": [
      {
        "name": "android.hardware.camera",
        "required": true
      }
    ],
    "queries": [
      "<package android:name=\"com.google.android.youtube\" />",
      "<intent><action android:name=\"android.intent.action.VIEW\" /><data android:scheme=\"https\" /></intent>"
    ]
  }
]
```

To prevent ProGuard/R8 from stripping manifest components (Activities, Services, Receivers), pass `-m` or `--keep-manifest`:

```bash
bolt build -r -m
```

---

### 12. 100% App Inventor Cloud Build Server Compatibility

Extensions frequently compile fine locally but cause catastrophic failures when the end-user builds an APK on the **MIT App Inventor**, **Kodular**, or **Niotron** build servers:

```text
App Inventor is unable to compile this project.
The compiler error output was:
[LoadComponentInfo] Starting Task
[LoadComponentInfo] INFO: Generating assets...
...
D8: Type com.example.MyClass is defined multiple times
```

Bolt CLI fixes the root architectural causes of build server failures:

1. **Clean `.aix` Structure (Fast-CLI Parity, ~4.6 KB)**:
   * Bolt generates Dalvik `classes.dex` for fast Companion live testing, but **strictly excludes** it from `AndroidRuntime.jar` and `files/` in production AIX packages. This ensures App Inventor's cloud D8 dexer receives only clean Java bytecode and never encounters duplicate dex class collisions.
   * Component palette icon is placed strictly in `aiwebres/icon.png`.
   * When no external assets are declared, empty `"assets": []` arrays are omitted from `component_build_infos.json` to avoid build server null/empty asset lookup warnings.
2. **Automated XML Comment Stripping**:
   * App Inventor's AAPT parser frequently throws syntax exceptions when parsing merged XML comments (`<!-- ... -->`).
   * Bolt automatically strips all XML comments from `src/AndroidManifest.xml` during metadata extraction.
3. **Shorthand Class Name Expansion**:
   * Build servers reject shorthand component names (e.g. `android:name=".MyService"`).
   * Bolt automatically expands them to full canonical package paths (e.g. `android:name="com.example.myext.MyService"`).
4. **Manifest Package Macro Substitution**:
   * Replaces Gradle-style `${applicationId}` occurrences with App Inventor's `%packageName%` macro.
5. **Minimum SDK Level Verification**:
   * Enforces `min_sdk >= 7` and compares against all third-party `.aar` libraries' `minSdkVersion` in `deps/`.

---

### 13. Bytecode Deannotation Post-Build (`deannonate: true`)

App Inventor uses metadata annotations (`@DesignerComponent`, `@SimpleFunction`, `@SimpleProperty`, `@SimpleEvent`) solely at design time. Leaving them inside the final `.class` files increases APK size and method count.

With `deannonate: true` in `bolt.yml`:

1. Bolt extracts metadata and generates `components.json` and `component_build_infos.json`.
2. Bolt uses an **ASM-based ClassRemapper** (`Deannotator.kt`) to cleanly strip all `com.google.appinventor.components.annotations.*` references from class bytecodes without touching method signatures or runtime bytecode.

---

### 14. Automatic Documentation & Helper Enums (`OptionList`)

#### Automatic Markdown Documentation

Every build automatically generates an `out/extension.txt` markdown summary containing:

- Extension Name, Package, Version, Author, and AIX binary size.
- Properties (with read/write permissions).
- Events (with parameter names and YAIL types).
- Methods (with return types and arguments).
- Helper enum definitions and dropdown block values.

#### Helper Enums (`OptionList`) with Auto-Injected `@SimpleProperty`

App Inventor supports dropdown blocks through `OptionList<T>`. Bolt CLI completely automates scaffolding, import injection, and demo property creation:

```bash
# Generate Integer dropdown helper enum
bolt generate helper int

# Or generate String dropdown helper enum
bolt generate helper str

# Or generate helper by custom name
bolt generate helper Direction
```

When you generate a helper or create a project with `-t int` or `-t str`:
1. Bolt creates the enum in `<package>.helpers.<HelperName>.java` implementing `OptionList<Integer>` or `OptionList<String>`.
2. Bolt automatically injects `import <package>.helpers.*;` into your main extension class.
3. Bolt automatically injects a working demo `@SimpleProperty` dropdown method into your main component class:

```java
// In your extension class:
import io.th.myextension.helpers.*;

@SimpleProperty(
    description = "Set helper Mode type (0=Demo1, 1=Demo2, 2=Demo3)"
)
public void DemoType(@Options(ModeType.class) int type) {
    // Dropdown parameter ready for App Inventor block editor!
}
```

```java
// In src/.../helpers/ModeType.java:
package io.th.myextension.helpers;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

public enum ModeType implements OptionList<Integer> {
    Demo1(0),
    Demo2(1),
    Demo3(2);

    private final int value;
    ModeType(int value) { this.value = value; }
    public Integer toUnderlyingValue() { return value; }

    private static final Map<Integer, ModeType> lookup = new HashMap<>();
    static {
        for (ModeType item : ModeType.values()) {
            lookup.put(item.toUnderlyingValue(), item);
        }
    }
    public static ModeType fromUnderlyingValue(Integer value) {
        return lookup.get(value);
    }
}
```

---

### 15. Automated Offline RSA Licensing (`bolt auth`)

Protect your commercial extensions with offline RSA-2048 licensing:

```bash
# 1. Initialize RSA-2048 keys and scaffold LicenseVerifier.java
bolt auth init

# 2. Check license in your Java code
if (!LicenseVerifier.verify(form, licenseKey)) {
    throw new YailRuntimeError("Invalid license key", "AuthError");
}

# 3. Generate customer key locked to their MIT AI2 email
bolt auth generate -e customer@gmail.com
```

---

### 16. Auto-Detect AIDL Compilation

Bolt automatically detects Android Interface Definition Language (`.aidl`) files:

1. Place `.aidl` files anywhere in `src/`.
2. Run `bolt build`.
3. Bolt automatically compiles interfaces to Java sources in `build/gen/` before Java/Kotlin compilation.

---

### 17. Dependency Management, Assets & Shading (JarJar Relocation)

Bolt features a comprehensive, multi-tiered dependency management system:

#### 1. Runtime Dependencies (`dependencies:`)

Declare local JAR/AAR files or remote Maven coordinates required at runtime:

```yaml
dependencies:
  - "my-library.jar"              # Local JAR or AAR stored in 'deps/'
  - "com.google.code.gson:gson:2.10.1" # Remote Maven coordinate
```

- **Local JAR/AAR**: Drop your `.jar` or `.aar` files into `deps/`. Bolt parses them into the compilation classpath. For `.aar` packages, the inner `classes.jar` is automatically unpacked and linked.
- **Remote Maven**: Bolt queries configured Maven repositories (defaulting to Maven Central, Google Maven, JitPack, and JCenter).
- **Inline Comments**: You can add comments directly after dependencies (e.g. `- "lib.jar" # Local JAR in deps/`). Bolt's YAML parser automatically strips inline comments cleanly.
- **Packaging**: During `bolt build`, dependency classes are extracted into `binDir` and packaged into `classes.jar` and `classes.dex` (with JarJar shading and R8/ProGuard optimization applied).

#### 2. Compile-Time Only Dependencies (`compile_time:`)

Libraries required only to compile Java or Kotlin source code:

```yaml
compile_time:
  - "compile-stubs.jar"
```

- Added to the ECJ and Kotlin compiler classpath.
- **Strictly excluded** from the final `.aix` archive and `classes.jar`/`classes.dex`, preventing bloat when using compile-time stubs.

#### 3. Provided Dependencies (`provided_dependencies:`)

Libraries already provided by the App Inventor Companion or host distribution:

```yaml
provided_dependencies:
  - "companion-lib.jar"
```

- Linked on the classpath and synced into `deps/`, but excluded from final `.aix` packaging to eliminate duplicate class conflicts.

#### 4. Custom Maven Repositories (`repositories:`)

Declare additional remote Maven endpoints:

```yaml
repositories:
  - "https://repo1.maven.org/maven2"
  - "https://dl.google.com/dl/android/maven2"
  - "https://jitpack.io"
  - "https://custom.myrepo.com/maven"
```

#### 5. Extension Assets Declaration & Validation (`assets:`)

Declare non-code assets (images, sound effects, data JSONs) to be bundled with your extension:

```yaml
assets:
  - icon.png
  - config.json
```

- Bolt verifies that declared files exist inside the project `assets/` folder.
- **Size Warning**: If any asset exceeds **5 MB**, Bolt raises an explicit AIX size limit warning in the terminal and build log to safeguard against App Inventor build server size limits.
- Automatically bundled into the `.aix` archive.

#### 6. Code Minimization & R8 Keep Rules (`minimize:`)

When running `bolt build -s` or `bolt build -r`, you can protect specific packages or dependencies from being stripped:

```yaml
minimize:
  exclude_dependency:
    - "com.google.code.gson:gson"
  exclude_project:
    - "com.example.myextension"
```

- Bolt generates `-keep class <pkg>.** { *; }` directives inside `.bolt/minimize-rules.pro`, ensuring critical reflection classes and dependencies remain intact.

#### 7. JarJar Bytecode Package Relocation (Shading)

To prevent duplicate class collisions with other extensions in the same App Inventor project:

```yaml
relocation:
  EnableAutoRelocation: true
  skipStringConstants: true
  include:
    - "com.google.gson.**"
  exclude: []
```

Bolt rewrites imports and packages the shaded classes into your extension namespace.

---

### 18. Modern Desugaring & Bytecode Protection

- **Core Library Desugaring (`coreLibraryDesugaring: true`)**:
  Enables modern Java 8+ APIs (`java.time.*`, `java.util.stream.*`, `java.util.function.*`) on older Android devices (down to API 14) via D8 desugar configuration.
- **StrGuard String Encryption**:
  Encrypts sensitive hardcoded strings in your compiled classes using an AES secret key:

  ```yaml
  strguard:
    enabled: true
    key: "MySuperSecretKey2026"
    packages:
      - "com.example.myext"
  ```

---

### 19. Legacy Project Migration with Pre-Migration Backups (`bolt migrate`)

Convert existing projects from older builders with automated safety guarantees:

```bash
# Auto-detects Rush, Fast, Extension Template, or AI2 repositories
bolt migrate

# Or explicitly migrate by builder type
bolt migrate rush
bolt migrate fast
bolt migrate template
```

#### 🔒 Automated Pre-Migration ZIP Backup

Before modifying any project files, Bolt automatically compresses your entire project into a timestamped ZIP archive in the parent folder:

```text
../MyExtension_backup_20260907_202410.zip
```

- **Zero Data Loss**: Ensures you can instantly restore your previous project state at any time.
- **Lightweight & Instant**: Automatically excludes bulky directories (`.bolt`, `build`, `out`, `.git`, `.gradle`) so the backup is generated in milliseconds.
- **Automated Restructuring**:
  - Automatically migrates configuration directives from `rush.yml` or `fast.yml` into modern `bolt.yml`.
  - Re-maps source directory layouts into standard `src/`.
  - Scaffolds `src/AndroidManifest.xml` and VS Code / IntelliJ IDEA configuration files.

---

## 🛠️ Building Bolt Compiler from Source & Toolchain Maintenance

This section provides complete developer instructions for compiling the Bolt compiler engine itself, assembling distributions, and upgrading internal `.jar` toolchains.

### 1. Prerequisites

- **Java Development Kit (JDK)**: JDK 11 or higher (JDK 17 or 21 recommended). Check with `java -version`.
- **Operating System**: Windows, macOS, Linux, or Android Termux.
- **Gradle Wrapper**: The project includes `gradlew` and `gradlew.bat`.

### 2. Building `bolt.jar` (Fat JAR)

Compile the standalone executable compiler engine:

- **Windows**:

  ```powershell
  .\gradlew.bat jar
  ```

- **Linux / macOS / Termux**:

  ```bash
  ./gradlew jar
  ```

Output: `distribution/bolt.jar` (~2.8 MB fat JAR containing GSON, ASM, Java-WebSocket, JAnsi, and Kotlin standard library).

### 3. Assembling the Full Distribution (`distribution/`)

To assemble all launcher scripts, toolchains, and runtime libraries:

```bash
./gradlew assembleDistribution
```

(On Windows: `.\gradlew.bat assembleDistribution`).

### 4. Creating Production Release ZIPs

- **Windows PowerShell**: `.\scripts\build.ps1 -Version "2.0.0"`
- **Linux / macOS / Termux**: `bash ./scripts/build.sh 2.0.0`

### 5. Managing & Updating Compiler Toolchain & Assets

All compiler toolchains reside in `distribution/libs/tools/` (or `~/.bolt/libs/tools/`):

- **Mini-NDK (`distribution/libs/ndk/`)**: Bundled directly inside `libs/ndk/` in official release ZIPs, making C/C++ compilation work **immediately out-of-the-box** without running `bolt sync ndk` or installing external tools.
- **AIDL Toolchain (`distribution/libs/tools/aidl/`)**: Contains cross-platform AIDL compilers (`aidl.exe`, `aidl-arm64-v8a`, `aidl-armeabi-v7a`, `aidl-x86_64`) and `framework.aidl`.
- **Extension Icon Isolation (`distribution/icon.png`)**: Extension icon is placed strictly outside `libs/` at the root directory (`distribution/icon.png` and root of release ZIPs).
- **`android.jar`**: Copy from Android SDK platforms directory (`$ANDROID_HOME/platforms/android-XX/android.jar`).
- **`d8.jar` / `r8.jar`**: Update from Google Maven (`com.android.tools:r8`) or Android SDK build-tools.
- **`desugar_jdk_libs:2.1.5`**: Run `bolt sync dev` to automatically download latest desugaring JARs from Google Maven.
- **`ecj.jar`**: Download newer Eclipse ECJ compiler releases from Maven Central (`org.eclipse.jdt:ecj`).
- **`kotlin-compiler.jar` & `kotlin-stdlib.jar`**: Update from Maven Central (`org.jetbrains.kotlin:kotlin-compiler-embeddable`).
- **`junit-platform-console-standalone.jar`**: Update from Maven Central (`org.junit.platform:junit-platform-console-standalone`).
- **Runtime libraries (`distribution/libs/`)**: Drop any `.jar` or `.aar` directly into `distribution/libs/` or `~/.bolt/libs/`.

---

## ❓ Frequently Asked Questions (FAQ)

### Q1: Does Bolt require Android Studio or Android SDK to be installed?

**No.** Bolt is completely self-contained. It includes its own in-process ECJ compiler (`ecj.jar`), Kotlin compiler, D8/R8 desugarer, ProGuard optimizer, and Mini-NDK. You do not even need a standalone `javac` compiler; you only need a Java runtime (JRE or JDK 11+) to run Bolt CLI.

### Q2: Why does App Inventor show an error when importing extensions with native `.so` files?

App Inventor requires native libraries to be referenced in `component_build_infos.json` under `"native"` with ABI suffixes (e.g. `libtest.so-v7a`). Bolt handles this mapping automatically during `bolt build`.

### Q3: How can I minimize the size of my native `.so` files?

In `bolt.yml`, set `stl: none` if your C/C++ code does not depend on heavy standard C++ libraries (`std::string`, `std::vector`). Bolt will apply `-Oz`, `-flto`, and `--strip-all`, producing native binaries as small as **2 KB - 3 KB**!

### Q4: How does the compiler daemon achieve sub-4s builds?

The daemon runs as a persistent background HTTP server on port 19090. Because it remains resident in memory, the JVM class loaders, ECJ compiler classes, and JIT optimizations are already warm, avoiding JVM cold-start latency.

### Q5: How does incremental D8 caching work?

Bolt computes an MD5 checksum of all `.class` files in `build/bin/`. When `bolt build -dx` runs, if the checksum matches `.bolt/dex_cache.hash`, D8 compilation is skipped and the existing `classes.dex` is reused immediately.

### Q6: Can I use third-party `.aar` libraries in Bolt?

Yes! Drop any `.aar` file into the `deps/` directory (or specify the coordinate in `dependencies:` in `bolt.yml` and run `bolt sync`). Bolt automatically unpacks the `classes.jar` inside the AAR and links it to the compilation classpath.

### Q7: Can I write extensions in Kotlin without huge `.aix` file size?

Yes! Bolt automatically bundles Kotlin stdlib and uses R8 shrinking and tree-shaking to eliminate unused Kotlin runtime classes, keeping your final extension compact.

### Q8: How does offline RSA licensing work?

The private key stays on your computer and is used by `bolt auth generate` to sign the customer's email. The public key is compiled into `LicenseVerifier.java` to verify the digital signature offline on the user's phone.

### Q9: How do I upgrade Bolt CLI to the newest version?

Simply run:

```bash
bolt upgrade
```

### Q10: Do I need to run `bolt sync ndk` to compile C/C++ native code?

**No.** Official Bolt distribution ZIPs now bundle the Mini-NDK directly in `libs/ndk/`, so native compilation works **immediately out-of-the-box** without running any sync commands or installing Android Studio! You only need to set `ndk: enabled: true` in your `bolt.yml`.

---

## 📄 License

Bolt CLI is licensed under the [Apache License 2.0](LICENSE).
Built with ❤️ for the MIT App Inventor and open-source Android development communities.
