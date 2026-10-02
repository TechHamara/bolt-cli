## 🛠️ Building Bolt CLI from Source

You can easily build the Bolt compiler from source code using the included Gradle wrapper.

### 1. Prerequisites

* **Java Development Kit (JDK)**: JDK 11 or higher (JDK 17 or 21 recommended). Check with `java -version`.
* **Operating System**: Windows, macOS, Linux, or Android Termux.

### 2. Building the Standalone Executable Fat Jar (`bolt.jar`)

To compile the core compiler engine into a self-contained, executable fat JAR:

* **Windows (PowerShell / Command Prompt)**:

  ```powershell
  .\gradlew.bat jar
  ```

- **Linux / macOS / Termux**:

  ```bash
  ./gradlew jar
  ```

This packages all Kotlin code, AST/bytecode parsers, and dependencies (`GSON`, `ASM 9.7`, `Java-WebSocket`, `JAnsi`) directly into:

```
distribution/bolt.jar
```

You can immediately run the newly built jar anywhere:

```bash
java -jar distribution/bolt.jar --version
```

### 3. Assembling the Full Distribution (`distribution/`)

To assemble the complete distribution bundle—including platform launcher scripts (`bolt`, `bolt.bat`), runtime libraries, and compiler toolchains:

* **Windows**:

  ```powershell
  .\gradlew.bat assembleDistribution
  ```

- **Linux / macOS / Termux**:

  ```bash
  ./gradlew assembleDistribution
  ```

This Gradle task performs the following actions:

1. Compiles `bolt.jar` and places it in `distribution/`.
2. Generates the Windows `distribution/bolt.bat` script and Unix/macOS/Termux `distribution/bolt` executable launcher.
3. Synchronizes toolchain files (`android.jar`, `d8.jar`, `r8.jar`, `ecj.jar`, `kotlin-compiler.jar`, `desugar_jdk_libs.jar`, `annotations.jar`, `appinventor-stubs-v3.jar`) into `distribution/libs/tools/`.
4. Copies AndroidX, App Inventor, and runtime dependencies into `distribution/libs/`.
5. Copies native binaries (`aidl.exe`, `framework.aidl`) into `distribution/bin/` and `distribution/libs/tools/bin/`.

### 4. Packaging Release ZIP Archive

To create the official universal release ZIP package (`bolt.zip`):

* **Windows (PowerShell)**:

  ```powershell
  .\scripts\build.ps1 -Version "2.0.0"
  ```

- **Linux / macOS / Termux**:

  ```bash
  bash ./scripts/build.sh 2.0.0
  ```

---

## 📦 Updating Compiler Toolchain & Runtime Libraries

Bolt CLI relies on a curated set of compiler tools and runtime libraries located in `distribution/libs/tools/` (or `~/.bolt/libs/tools/`) and `distribution/libs/` (or `~/.bolt/libs/`). Here is the complete guide on how to update each component:

### Directory Structure Overview

```
distribution/ (or ~/.bolt/)
├── bolt.jar                        # Standalone compiled compiler executable
├── bolt.bat                        # Windows command launcher
├── bolt                            # Unix / macOS / Termux executable launcher
├── bin/
│   └── aidl.exe                    # Android Interface Definition Language native compiler
└── libs/
    ├── tools/                      # Compiler toolchain JARs
    │   ├── android.jar             # Android Framework API classes (compile SDK)
    │   ├── d8.jar / r8.jar         # Google D8/R8 Dexer, Shading, Desugaring engine
    │   ├── ecj.jar                 # Eclipse Compiler for Java (ECJ in-process Java compiler)
    │   ├── kotlin-compiler.jar     # JetBrains Kotlin compiler
    │   ├── kotlin-stdlib.jar       # Kotlin Standard Library runtime
    │   ├── desugar_jdk_libs.jar    # Java 8+ API desugaring runtime classes
    │   ├── desugar_jdk_libs_configuration.json # Desugaring config for D8/R8
    │   ├── appinventor-stubs-v3.jar# App Inventor component runtime stubs
    │   ├── annotations.jar         # App Inventor metadata annotations
    │   ├── junit-platform-console-standalone.jar # JUnit 5 local unit test runner
    │   └── strguard-plugin-1.0.1.jar # StrGuard string encryption engine
    └── *.jar / *.aar               # AndroidX, Google Play Services, and runtime libraries
```

---

### Step-by-Step Guide to Updating Specific Toolchain JARs

#### 1. Android Framework API (`android.jar`)

- **Current Path**: `distribution/libs/tools/android.jar`
* **When to update**: When targeting new Android API levels (e.g. Android 14 API 34, Android 15 API 35).
* **How to update**:
  1. Open your Android SDK platforms directory:
     * Windows: `%LOCALAPPDATA%\Android\Sdk\platforms\android-34\android.jar`
     * Linux/macOS: `$ANDROID_HOME/platforms/android-34/android.jar`
  2. Copy `android.jar` into `distribution/libs/tools/android.jar` or `~/.bolt/libs/tools/android.jar`.
  3. You can also name it `android-34.jar` and set `compile_sdk: 34` in your `bolt.yml`.

#### 2. Google D8 & R8 Dexer (`d8.jar` / `r8.jar`)

- **Current Path**: `distribution/libs/tools/r8.jar`, `distribution/libs/d8.jar`
* **When to update**: When Google releases optimizations or bug fixes for bytecode desugaring or dexing.
* **How to update**:
  * **Option A (From Android SDK)**: Copy `d8.jar` from `%LOCALAPPDATA%\Android\Sdk\build-tools\<latest_version>\lib\d8.jar`.
  * **Option B (From Google Maven)**: Download `r8` directly from Google Maven:

    ```
    https://maven.google.com/com/android/tools/r8/<version>/r8-<version>.jar
    ```

    Rename to `r8.jar` and copy to `distribution/libs/tools/` and `~/.bolt/libs/tools/`.

#### 3. Core Desugaring Libraries (`desugar_jdk_libs:2.1.5`)

- **Current Path**: `distribution/libs/tools/desugar_jdk_libs-2.1.5.jar` and `desugar_jdk_libs_configuration-2.1.5.jar`
* **When to update**: When upgrading support for newer Java 11/17 APIs on older Android versions.
* **How to update**:
  * **Automated (Recommended)**: Run:

    ```bash
    bolt sync dev
    ```

    Bolt connects directly to Google Maven (`dl.google.com/dl/android/maven2`), downloads `desugar_jdk_libs:2.1.5.jar` and `desugar_jdk_libs_configuration:2.1.5.jar`, and automatically places them in `libs/tools/`!
  * **Manual**: Download from `https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/<version>/` and place in `libs/tools/`.

#### 4. Eclipse In-Process Java Compiler (`ecj.jar`)

- **Current Path**: `distribution/libs/tools/ecj.jar` (e.g. `ecj-3.42.0-patched.jar`)
* **When to update**: To support new Java syntax features or compiler optimizations.
* **How to update**:
  1. Download the latest ECJ compiler from Maven Central (`org.eclipse.jdt:ecj`):

     ```
     https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/
     ```

  2. Rename the downloaded JAR to `ecj.jar` and place it in `distribution/libs/tools/`.

#### 5. Kotlin Compiler & Standard Library (`kotlin-compiler.jar`, `kotlin-stdlib.jar`)

- **Current Path**: `distribution/libs/tools/kotlin-compiler.jar`, `kotlin-stdlib.jar`
* **When to update**: When upgrading Kotlin language versions (e.g. Kotlin 1.9 or 2.0).
* **How to update**:
  1. Download `kotlin-compiler-embeddable-<version>.jar` from Maven Central (`org.jetbrains.kotlin:kotlin-compiler-embeddable`).
  2. Rename to `kotlin-compiler.jar` and place in `distribution/libs/tools/`.
  3. Download `kotlin-stdlib-<version>.jar` from Maven Central (`org.jetbrains.kotlin:kotlin-stdlib`), rename to `kotlin-stdlib.jar`, and place in `distribution/libs/tools/` and `distribution/libs/`.

#### 6. MIT App Inventor Runtime Stubs & Annotations

- **Current Path**: `distribution/libs/tools/appinventor-stubs-v3.jar`, `annotations.jar`
* **When to update**: When MIT App Inventor releases new components, helper methods, or annotations.
* **How to update**:
  1. Clone [appinventor-sources](https://github.com/mit-csl/appinventor-sources).
  2. Run `ant RunLocalBuild` or compile the `components` module.
  3. Copy the generated runtime classes jar to `distribution/libs/tools/appinventor-stubs-v3.jar` and annotations jar to `distribution/libs/tools/annotations.jar`.

#### 7. JUnit 5 Local Testing Runner (`junit-platform-console-standalone.jar`)

- **Current Path**: `distribution/libs/tools/junit-platform-console-standalone.jar`
* **When to update**: To upgrade the JUnit Jupiter engine or test reporting capabilities.
* **How to update**:
  1. Download the latest `junit-platform-console-standalone-<version>.jar` from Maven Central:

     ```
     https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/
     ```

  2. Rename to `junit-platform-console-standalone.jar` and copy to `distribution/libs/tools/` and `~/.bolt/libs/tools/`.

#### 8. Mini-NDK C/C++ Native Toolchain (`libs/ndk` & `~/.bolt/libs/ndk`)

- **Current Path**: `distribution/libs/ndk` (Bundled out-of-the-box in all release ZIPs) or `~/.bolt/libs/ndk`
* **When to update**: To support newer C++20/C++23 standards, newer LLVM/Clang compiler versions, or new CPU architectures.
* **How to update**:
  1. Download the Android NDK (or Bolt Mini-NDK bundle).
  2. Extract the NDK into `distribution/libs/ndk` or `~/.bolt/libs/ndk` (ensure `build/ndk-build` or `ndk-build.cmd` is present).
  3. Verify with:

     ```bash
     bolt sync ndk
     ```

  > [!NOTE]
  > Because Mini-NDK is bundled directly inside `libs/ndk/` in official release packages, native C/C++ compilation works **immediately out-of-the-box** without needing to run `bolt sync ndk` or configure external tools!

#### 9. AIDL Toolchain Assets (`distribution/libs/tools/aidl/`)

- **Current Path**: `distribution/libs/tools/aidl/` (containing `aidl.exe`, `aidl-arm64-v8a`, `aidl-armeabi-v7a`, `aidl-x86_64`, and `framework.aidl`)
* **When to update**: When new Android API interface definition features or build-tools updates are released by Google.
* **How to update**:
  * Copy the latest `aidl.exe` from Android SDK build-tools to `distribution/libs/tools/aidl/aidl.exe`.
  * Update `framework.aidl` from `android.jar` or SDK platforms directory.

#### 10. Extension Icon Isolation (`distribution/icon.png`)

- **Location**: Strictly outside `libs/` at the distribution root (`distribution/icon.png` and root of release ZIPs).
* **Purpose**: Fallback official icon for newly generated extensions if a custom `assets/icon.png` is not provided.

#### 11. Adding New Runtime Libraries (`distribution/libs/`)

- **Location**: `distribution/libs/` and `~/.bolt/libs/`
* **How to add**:
  * Simply drop any `.jar` or `.aar` library (e.g., custom AndroidX modules, JSON parsers, database drivers, audio decoders) directly into `distribution/libs/` or `~/.bolt/libs/`.
  * Bolt's dynamic classpath resolver automatically indexes and includes all `.jar` and `.aar` files in this directory during extension compilation, making their classes immediately available in your Java and Kotlin source code!

---
