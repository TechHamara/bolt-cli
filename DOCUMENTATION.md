# Bolt CLI ✨

**The Ultimate Extension Builder for MIT App Inventor 2**

[![Latest Version](https://img.shields.io/badge/version-1.0.0-blue.svg?style=for-the-badge)](https://github.com/TechHamara/bolt-cli)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg?style=for-the-badge)](#)
[![MIT App Inventor](https://img.shields.io/badge/platform-App%20Inventor%20nb201-orange.svg?style=for-the-badge)](https://github.com/mit-cml/appinventor-sources)

Welcome to the official documentation for **Bolt** (v1.0.0). Bolt is a modern, high-performance, and feature-rich command-line tool designed to revolutionize the way you develop and build extensions for MIT App Inventor 2 and its various distributions (Kodular, Niotron, etc.).

---

## 🌟 Key Features

Bolt brings modern Android and JVM build practices to App Inventor extension development.

### ⚡ Lightning-Fast Core

* **Optimized Build Pipelines**: High-speed incremental compiler integration keeps compile-to-packaged times under a few seconds.
* **Modern Tooling Environment**: Prints active **Gradle** and **Maven** environments in the terminal build header on launch for complete build observability.

### 📦 Dependency & Resource Management

* **Maven-style Coordination**: Seamlessly declare remote dependencies using coordinates (e.g., `groupId:artifactId:version`) or point to local JAR/AAR packages inside your `deps/` directory.
* **Non-Class Resource Integration**: Fully bundles non-class and resource files from transitive runtime dependencies directly into your final `AndroidRuntime.jar`.
* **Provided/Compile-time AAR Extraction**: Robust extraction and classpath processing for local compile-time (`compile_time`) and provided (`provided_dependencies`) AAR files.
* **Smart AAR Content Inspection**: Automatically scans extracted AAR dependencies for unsupported folders (such as `assets` and `jni` directories) and prints warning logs to guide developers on potential integration issues.

### ⚙️ Rich AndroidManifest.xml Integration

* **Full Manifest Support**: Declare receivers, providers, services, activity-aliases, and metadata inside a custom `AndroidManifest.xml` in your `src/` folder.
* **Shorthand Class Name Expansion**: Write clean, concise class names. Leading single dots (e.g., `.MyService`) or triple dots (e.g., `...MyService`) automatically expand to your package name during compilation.
* **Extended Element Support**: Fully supports advanced application tags including `<activity-alias>`, `<profileable>`, `<uses-library>`, and `<uses-native-library>`.
* **`<uses-feature>` Serialization**: Automatically parses `<uses-feature>` elements and serializes their `name` and `required` parameters into the component's metadata (`component_build_infos.json`).

### 🛠️ Advanced Optimization & Stripping

* **Automatic Size Optimization (`-o`)**: Scans your extension configuration and automatically triggers bytecode optimization (`-o`) when runtime external dependencies are declared.
* **Smart ProGuard Obfuscation Control**: Runs ProGuard with `-dontobfuscate` when bytecode optimization is enabled but full class obfuscation is not requested, ensuring safe runtime resolution.
* **Project-Specific ProGuard Versions**: Define a project-specific ProGuard distribution version in `bolt.yml` (`proguard_version: '7.8.2'`) to easily bypass bundled tool limitations.
* **Selective Manifest Class Keeping**: Pass `-m` or `--keep-manifest` to automatically keep all components defined in your `AndroidManifest.xml` from ProGuard stripping—now fully functional even for projects with no external dependencies!
* **Annotation Stripping (`deannonate`)**: Strip heavy App Inventor metadata annotations silently post-compilation for maximum binary size efficiency.

### 🧩 App Inventor nb201 Compatibility

* **Custom XML Bundling**: Fully supports `@UsesXmls` and `@XmlElement` annotations. Bolt parses custom XML structures and bundles them with the final APK in the `dir/name:content` format expected by App Inventor nb201.

---

## 🛠️ Installation & Setup

Before installing Bolt, ensure you have **JDK 8 or above** installed on your system.

### Windows (PowerShell)

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

### Linux & macOS

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
```

*Make sure to add `$HOME/.bolt/bin` to your `PATH` environment variable.*

### Android (Termux)

Develop and build extensions directly on your mobile device! For a comprehensive, step-by-step walkthrough covering MT Manager setup, custom code editors, directory visual graphs, and standard FAQs, see the official [Termux & Linux Setup Guide](termux-installation.md).

1. Download and install the latest official [Termux app](https://github.com/termux/termux-app/releases/latest).
2. Set up storage permissions:

   ```bash
   termux-setup-storage
   ```

3. Run the automated installer script (this automatically checks dependencies, compiles the tool natively on your phone, and auto-configures your shell environment pathing/variables):

   ```bash
   curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
   ```

4. Restart your Termux session (or run `source ~/.bashrc` / `source ~/.zshrc`) and verify:

   ```bash
   bolt -v
   ```

---

## 🚀 Quick Start Guide

### 1. Scaffolding a New Extension

Navigate to your working directory and use the `create` command to scaffold a new extension:

```bash
bolt create MyAwesomeExtension
```

Bolt will guide you through:

1. **Package name**: Your reverse-domain identifier (e.g., `com.example.myextension`).
2. **Language**: Choose between Java or Kotlin (you can write in both simultaneously later).
3. **IDE**: Configure files for VSCode, IntelliJ/Android Studio, or both.

### 2. Building and Packaging

Navigate into your project folder and run the build command:

```bash
bolt build
```

**Useful Build Flags:**

| Flag | Description |
|---|---|
| `-y, --sync` | Forcefully triggers dependency synchronization before compiling the project. Default behavior bypasses sync for high performance. |
| `-o, --optimize` | Shrink and optimize bytecode using ProGuard. Automatically triggered if runtime dependencies are present. |
| `-b, --build-blocks` | Generate beautiful block mockups as PNG images inside the `out/` directory. |
| `-m, --keep-manifest` | Preserve all classes declared in `AndroidManifest.xml` from ProGuard stripping. |

Your generated `.aix` extension bundle will be available in the `out/` directory.

---

## 📖 CLI Reference (Commands)

| Command | Options | Description |
|---|---|---|
| `bolt build` | `-y`, `-o`, `-b`, `-m`, `-v` | Compiles source files, processes annotations, resolves dependencies, and bundles the `.aix` file. |
| `bolt clean` | *none* | Deletes compiler caches and build files for a clean environment. |
| `bolt create` | *interactive* | Scaffolds a new project with IDE settings, CI pipelines, and configurations. |
| `bolt sync` | `--dev-deps` | Resolves dependencies declared in `bolt.yml`. Performs automated Support-to-AndroidX Jetifier translation when `jetify: true` is configured. |
| `bolt tree` | *none* | Renders a beautiful visual tree of the current project's hierarchical structure and automatically saves it as `tree.txt` in the project root. |
| `bolt migrate` | *none* | Port legacy projects (Bolt v1, `extension-template`, AI2 source, or `fast.yml` projects) to modern Bolt CLI architecture. Automatically zips the current project folder as a backup before migration. |
| `bolt upgrade` | `--force` | Upgrade the local Bolt CLI binary to the latest release on GitHub. |

### Global Options

* `-h, --help`: Displays CLI help context.
* `-v, --verbose`: Enables verbose debug logs (perfect for troubleshooting).
* `-c, --[no-]color`: Toggle colorized shell formatting.
* `-V, --version`: Prints the current Bolt CLI version (`1.0.0`).

---

## ⚙️ Configuration File (`bolt.yml`)

The `bolt.yml` configuration at your project's root drives compiler behavior:

```yaml
# Version name of your extension (shown in App Inventor designer)
version: '1.0.0'

# Author name displayed in catalog/documentation
author: 'Developer'

# Minimum Android SDK level your extension supports
min_sdk: 14

# Target Android compile SDK API level
compile_sdk: 35

# Enable Java 8 desugaring (for lambdas, stream APIs, etc.)
desugar: true

# Enable ProGuard optimization/shrinking (defaults to off)
proguard: true

# Specify project-specific ProGuard version to download and use
proguard_version: '7.8.2'

# Strip metadata annotations post-compilation for smaller binary sizes
deannonate: true

# Auto-generate Markdown documentation (extension.txt) inside out/
gen_docs: true

# Automatically increment version number on every build
auto_version: false

# External dependencies (Maven coordinates or local jars/aars)
dependencies:
  - example.jar
  - com.google.code.gson:gson:2.10.1

# Provided dependencies (compiled against, but not included in aix)
provided_dependencies:
  - com.google.android.material:material:1.9.0

# Assets to bundle with your extension
assets:
  - icon.png

# Kotlin setup (both syntax forms are supported)
kotlin_version: '1.8.0'
```

---

## 🎨 Advanced Features & Best Practices

### Shorthand Class Names in `AndroidManifest.xml`

Bolt supports full manifest editing. Instead of writing verbose full-qualified class names, use the `.` or `...` prefix shorthands:

```xml
<manifest package="com.example.extension">
    <application>
        <!-- Expands to: com.example.extension.MyService -->
        <service android:name=".MyService" />

        <!-- Expands to: com.example.extension.MyActivityAlias -->
        <activity-alias android:name="...MyActivityAlias" android:targetActivity=".MyActivity" />
    </application>
</manifest>
```

### Custom XML Bundling (`@UsesXmls`)

App Inventor nb201 introduced support for custom XML resources. Use the `@UsesXmls` annotation in your extension source:

```java
import com.google.appinventor.components.annotations.UsesXmls;
import com.google.appinventor.components.annotations.XmlElement;

@UsesXmls(xmls = {
    @XmlElement(
        dir = "xml", 
        name = "file_paths.xml", 
        content = "<paths><external-path name=\"external\" path=\".\"/></paths>"
    )
})
public class MyExtension extends AndroidNonvisibleComponent { ... }
```

Bolt parses and packages these xml parameters directly inside `component_build_infos.json` so they compile perfectly during final APK generation.

---

## ❓ FAQ (Frequently Asked Questions)

### **Q: Do I need to uninstall ant or other build tools to use Bolt?**

**A:** No. Bolt runs completely independently and will not conflict with other development tools installed on your machine.

### **Q: Why am I getting "cannot find symbol" errors for UsesXmls/XmlElement?**

**A:** Ensure your workspace has the latest `annotations.jar` file containing these definitions (nb201 spec) inside `C:\Users\kapil\.bolt\libs\tools\annotations.jar`.

### **Q: Can I use both Kotlin and Java in the same project?**

**A:** Absolutely! You can place `.java` and `.kt` source files side-by-side inside your `src/` directory, and Bolt will compile them together seamlessly.

### **Q:** Why was I getting "can't access jdk.internal.loader.ClassLoaders" or other illegal reflective access warnings when compiling Kotlin/kapt?

**A:** These warnings used to appear when reflection-heavy build tools (like Kotlin's Kapt compiler plugin or Java's desugarer) accessed JRE compiler internals on modern JDKs (like Java 11/17).
**Resolution:** This is now **100% resolved**. Bolt dynamically registers JVM `--add-opens` flags and dynamic compiler version queries so that all builds run completely warning-free!

### **Q:** Why does my Kotlin extension have a large `.aix` file size (~1.5MB), and how can I minimize it?

**A:** By default, if the Kotlin standard library (`kotlin-stdlib`) is packaged directly into your extension, it adds around 1.5MB to the binary. There are two highly effective methods to reduce this size:

1. **Method A (Compile-time Only / Exclude stdlib)**: If the host application environment (such as a custom player or a companion app) already provides the Kotlin runtime standard library, move the dependency to `provided_dependencies` in your `bolt.yml`:

   ```yaml
   provided_dependencies:
     - org.jetbrains.kotlin:kotlin-stdlib:1.8.0
   ```

   This compiles successfully but keeps your `.aix` file extremely tiny (just a few kilobytes!).
2. **Method B (Aggressive ProGuard Shrinking)**: If the Kotlin runtime library must be bundled inside the extension, add aggressive ProGuard rules in `proguard-rules.pro` to discard unused standard library helper classes and strip Kotlin metadata annotations:

   ```proguard
   -keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
   -dontwarn kotlin.**
   -dontnote kotlin.**
   ```

### **Q:** My dependencies use `android.support.*` — how do I migrate to AndroidX?

**A:** Simply add `jetify: true` to your `bolt.yml`. When running `bolt sync`, Bolt will automatically transform all legacy dependencies into their modern `androidx.*` counterparts.

### **Q:** How do I build the Bolt tool itself from source?**

**A:**

1. Clone the repository.
2. Run `dart pub get` to fetch Dart dependencies.
3. Run `dart run build_runner build --delete-conflicting-outputs` to build source generators.
4. Run the PowerShell build script: `.\scripts\build.ps1 -v 1.0.0`

---

## 🤝 Contributing & Support

* **Bug Reports & Feature Requests**: [Open a GitHub Issue](https://github.com/TechHamara/bolt-cli/issues)
* **Pull Requests**: Fork the repository, create your branch, and submit a PR. We look forward to your contributions!

---

*Built with ❤️ for the MIT App Inventor Community.*  
**Let's build beautiful, high-performance extensions together! Happy Coding! 💻**
