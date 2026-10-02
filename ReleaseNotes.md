# Release Notes

## **2.0.0 (Latest)**

### ⚡ Bolt CLI 2.0.0 (Universal Edition) – Build Faster, Compile Smarter

Bolt CLI 2.0.0 is a major milestone release delivering a unified, lightning-fast universal build system and toolchain for **MIT App Inventor 2** extensions and distributions (Kodular, Niotron, AndroidBuilder, etc.).

#### 🚀 Universal Engine Architecture
- **In-Process Kotlin & ECJ JVM Engine**: Replaced platform-specific binary dependencies with an integrated, universal JVM compiler engine that runs natively across Windows, macOS (Intel & Apple Silicon), Linux, and Android Termux with Java 11+.
- **Unified Release Package (`bolt.zip`)**: Streamlined multi-platform packaging into a single cross-platform universal distribution archive containing launchers (`bolt.bat` and `bolt`) and self-contained toolchain libraries.
- **Dynamic `BoltLocator` Discovery**: Automatically resolves `$BOLT_HOME`, script relative paths, and OS standard directories without hardcoded filesystem paths.

#### ⚡ Performance & Daemon
- **Persistent Compiler Daemon (`bolt daemon`)**: Keep Kotlin compiler, ECJ, D8, and R8 toolchains hot in RAM, cutting incremental extension build times to **under 3–4 seconds**.
- **Optimized Release Builds**: Defaulted release builds (`-r`) to R8 with tuned ProGuard JVM heap parameters and aggressive optimizations.
- **D8 DEX Incremental Caching**: Instant Dalvik/ART `classes.dex` generation with MD5 bytecode caching for zero-latency cache hits.
- **Optimized Classpath Resolution**: Dynamically discovers AndroidX and runtime libraries with high-performance class scanning and maximum ZIP compression.

#### 🖥️ Developer Experience & Diagnostics
- **Clean Terminal Output & System Telemetry**: Distraction-free bullet logs during compilation paired with detailed real-time hardware telemetry (CPU cores, RAM usage, storage space, OS platform) saved to `.bolt/BuildLog.txt`.
- **Interactive Upgrade (`bolt upgrade`)**: Built-in interactive upgrade workflow featuring Fresh (full package) and InPlace update modes with real-time download progress visualizer, transferred megabytes, and download speed.
- **Auto-Injected Helper Enums (`OptionList`)**: Quick scaffolding via `bolt generate helper <type>` and `bolt create -t <str|int>` with automatic `@SimpleProperty` injection into component source.
- **Auto-AIDL Compilation**: Automatic discovery and compilation of `.aidl` interface files in `src/` to Java stubs.

#### 🧪 Testing, Live Reload & Tooling
- **Official JUnit 5 Unit Testing (`bolt test`)**: Execute unit tests locally via JUnit 5 Platform Console Standalone Runner with live progress trees.
- **Live Companion Hot-Reloading (`bolt run`)**: Instant wireless extension testing over WebSocket (port 9000) and Wi-Fi UDP auto-discovery (port 9001) without rebuilding full APKs.
- **Mini-NDK & Native C/C++ Support**: Compile ultra-compact `.so` libraries out-of-the-box (`-Oz`, `-flto`, `--strip-all`) with automated `@UsesNativeLibraries` ABI mapping into `component_build_infos.json`.
- **Google Maven Dev Sync (`bolt sync dev`)**: Automated sync and updating of `desugar_jdk_libs:2.1.5` and configuration files.
- **Offline RSA-2048 Licensing (`bolt auth`)**: Cryptographic licensing system tied to App Inventor developer emails.

---

## **1.1.1**

- **Interactive `bolt upgrade` Command**:
  - Displays version and download size details (`Current version: v1.1.0`, `New version: v1.1.1`, `Download size: 589.14 MB`).
  - Added interactive prompt: `Do you want to upgrade? [Yes/No] (Default: No)`.
  - Added mode selection prompt: `Select update mode? [Fresh/InPlace] type 2 for InPlace, (Default: Fresh)`.
  - Fresh mode downloads full `bolt-win.zip` for a complete installation update.
  - InPlace mode downloads `bin.zip` and replaces only `bolt.exe` at installation path (`$BOLT_HOME\bin\bolt.exe`).
  - **Real-time Progress Visualizer**: Added live download percentage, downloaded size, and real-time speed display (`Downloading: 12.00% (43.45 MB/362.14 MB) | 3.94 MB/s |`).

## **1.1.0**

Bolt CLI brings modern Lightning Fast Java, Kotlin & C/C++ CLI Build tool for Extension Development:

### 🚀 Performance & Build Speed

- **Bolt CLI Compiler Daemon (`bolt daemon`)**: Added `bolt daemon start / stop / status` commands to keep compiler dependencies warmed up in RAM, eliminating JVM cold-start penalties.
- **D8 DEX Caching**: Added `d8TimestampKey` caching. D8 bytecode generation is now automatically skipped when input bytecode has not changed, reducing cached build times to **~3 seconds**.
- **Kotlin Build Speed Optimization**: Added caching to Kotlin files, making Kotlin builds up to 3.1x faster.

### 🛠️ Developer Experience & CLI Features

- **`bolt sync dev` Command**: Automated downloading & updating of `desugar_jdk_libs:2.1.5` and `desugar_jdk_libs_configuration.json` from Google Maven.
- **Auto-AIDL Compilation**: Bolt now automatically scans for `.aidl` source files in `src/` and compiles them into Java without requiring `aidl: true` in `bolt.yml`.
- **Clean Terminal Output**: Streamlined console logging to remove clutter on terminal print.
- **Dynamic Help Commands**: `bolt --help` now dynamically lists all registered CLI commands.
- **📱 [Companion App](https://github.com/TechHamara/Bolt-Companion-App) Hot-Reloading**: Run `bolt run` to instantly push and test your extension on a live Android device via Wi-Fi UDP Auto-Discovery without reinstalling an APK!
- **🔥 Live Hot-Reloading (`bolt run`) & [BoltHotReloader](https://github.com/TechHamara/BoltHotReloader)**: Pushes Java code changes directly to your device via WebSocket in less than a second! We built a custom `BoltHotReloader.aix` extension (223KB) that connects via `Java-WebSocket`, clears the `ReplForm` cache using **Java Reflection**, and hot-swaps your newly compiled `.dex` dynamically!
- **🧠 Live Performance Monitor**: Catch memory leaks instantly in the CLI dashboard while hot-reloading.
- **Automated Licensing (`bolt auth`)**: Secure your premium extensions with Offline RSA licensing automatically tied to the customer's MIT App Inventor email.

### 🆕 New CLI Commands & Core Features

- **`bolt run` (Live Hot-Reloading)**: Pushes Java code changes directly to the App Inventor Companion in milliseconds over Wi-Fi! Includes custom `BoltHotReloader.aix`.
- **`bolt auth` (Automated Licensing)**: Secure your premium extensions with Offline RSA-2048 licensing automatically tied to the customer's MIT App Inventor email.
- **`bolt test` (Unit Testing Support)**: Execute standard JUnit 5 tests locally on your PC powered by [Robolectric](https://developer.android.com/training/testing/local-tests/robolectric).
- **`bolt add` (Dependency Manager)**: Easily add remote libraries and their transitive dependencies.
- **`bolt deps`**: Work with project dependencies.

### 📦 NDK, Desugaring & Optimizations

- **Mini-NDK & JNI Support**: Build native C/C++ `.so` shared extensions out-of-the-box by enabling `ndk: enabled: true` in `bolt.yml` without requiring the full Android Studio NDK (work the features after app inventor implementation on there AI2 build server, as it is not implemented yet as this features is under development).
- **[CoreLibraryDesugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)**: Added support for modern APIs (`java.time.*`, `java.util.stream.*`) on older devices and robust ProGuard optimizations.
- **[Minimization](https://gradleup.com/shadow/configuration/minimizing/) Exclusions**: Exclude specific maven coordinates from shrinking dynamically via `bolt.yml` to prevent reflection issues.
- **Dependency [Relocation](https://gradleup.com/shadow/configuration/relocation/) (Shading)**: Automatically resolves conflicting dependencies by shading and repackaging.

---

## **1.0.0**

- Initial release of Bolt CLI framework.
