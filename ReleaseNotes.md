# Release Notes

## **1.1.0 (Latest)**

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
