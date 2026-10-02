# ⚡ Bolt CLI (Universal Edition)

<p align="center">
  <img width="480" height="264" alt="bolt" src="https://github.com/user-attachments/assets/3e2d3025-a83c-49aa-8d75-217bbd22b65e" />
</p>

<p align="center">
  <b>Build Faster, Compile Smarter.</b><br>
  The Universal, Lightning-Fast Framework & CLI for MIT App Inventor 2 Extensions.
</p>

<p align="center">
  <a href="ReleaseNotes.md"><img src="https://img.shields.io/badge/version-2.0.0-blue.svg" alt="Version 2.0.0" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="License" /></a>
</p>

<p align="center">
  <a href="#-key-features">Features</a> •
  <a href="#-installation--setup">Installation</a> •
  <a href="#-commands-reference">Commands</a> •
  <a href="#-compiler-daemon-sub-4s-builds">Compiler Daemon</a> •
  <a href="#-clean-terminal-output--real-time-hardware-diagnostics">Diagnostics</a> •
  <a href="#-d8-dex-compilation--caching">D8 DEX Caching</a> •
  <a href="#-live-testing--hot-reloading">Live Testing</a> •
  <a href="#-mini-ndk--native-cc-support">Mini-NDK</a> •
  <a href="#-unit-testing-with-junit-5">Unit Testing</a> •
  <a href="#-automated-licensing-bolt-auth">Licensing</a> •
  <a href="#-app-inventor-build-server-compatibility">Compatibility</a> •
  <a href="#-project-configuration-boltyml">bolt.yml</a> •
  <a href="#-building-bolt-cli-from-source">Build from Source</a> •
  <a href="#-credits--acknowledgements">Credits</a>
</p>

---

## 🚀 Overview

**Bolt CLI** is a modern, high-performance universal command-line tool designed to revolutionize how you develop and build extensions for **MIT App Inventor 2** and its distributions (Kodular, Niotron, AndroidBuilder, etc.).

Powered by an **in-process Kotlin & ECJ JVM compiler engine**, Bolt runs universally on **Windows, macOS (Intel & Apple Silicon), Linux, and Android Termux** without platform-specific native binary dependencies.

> [!IMPORTANT]
> **Credits & Acknowledgements**: Bolt CLI is built upon the excellent foundation of the original [Rush CLI](https://github.com/shreyashsaitwal/rush-cli) project created by [Shreyash Saitwal](https://github.com/shreyashsaitwal). We express our sincere gratitude and credit to Shreyash and all the Rush contributors for their pioneering work in building compilation toolsets for the App Inventor community.

---

## ✨ Key Features

* **📦 Advanced Multi-Tier Dependency System**: Support for runtime `dependencies:` (Local JAR/AAR in `deps/` or remote Maven coordinates with automatic inline comment stripping), compile-time only `compile_time:` stubs (strictly excluded from AIX), and companion `provided_dependencies:` (excluded from final AIX).
* **🖥️ Real-Time System Diagnostics & Clean Terminal Output**: Clean, distraction-free bullet logs during `bolt build`, paired with detailed `.bolt/BuildLog.txt` recording real-time device specs (CPU cores, physical RAM `Used/Total GB`, storage disk space, Android/Windows OS detection) and exact build duration.
* **🧩 Auto-Injected Helper Enums (`OptionList`)**: Scaffold dropdown block helpers (`bolt generate helper <type>` or `bolt create -t <int|str>`) with automatic helper class imports and demo `@SimpleProperty` dropdown methods injected directly into your main component class.
* **🛡️ 100% App Inventor Build Server Compatibility (AAPT & D8)**: Clean `.aix` packaging layout with zero duplicate class collisions, XML comment stripping in `AndroidManifest.xml`, shorthand component expansion (`.MyService` -> `<pkg>.MyService`), `${applicationId}` substitution, and automated `min_sdk` verification.
* **🎨 Extension Assets Validation (`assets:`)**: Declared assets in `assets/` are validated with automated warnings if file size exceeds 5MB to prevent AIX upload rejections, and auto-packaged directly into the `.aix` archive.
* **🛡️ R8 Minimization & Code Preservation (`minimize:`)**: Custom `exclude_dependency` and `exclude_project` directives automatically generate `-keep class <pkg>.** { *; }` rules in `.bolt/minimize-rules.pro` to prevent essential classes from being stripped during shrinking.
* **🌐 Extension Homepage & Auto-Versioning**: Declare `homepage:` to render clickable links in component metadata and `out/extension.txt`, and enable `auto_version: true` to increment component version on every successful build automatically.
* **⚡ Lightning-Fast In-Process Builds (`bolt build`)**: Compiles Java, Kotlin, AIDL, ProGuard, R8, D8 Dex, and packages the `.aix` bundle in sub-3 seconds without external JVM spawn overhead.
* **🔥 Persistent Compiler Daemon (`bolt daemon`)**: Keeps JVM dependencies, HotSpot JIT, and compiler classes warmed up in RAM for sub-4 second incremental builds.
* **⚡ D8 DEX Compilation & Incremental Caching (`--dex`, `-dx`)**: Compiles Java/Kotlin bytecode into Dalvik/ART `classes.dex` with incremental MD5 checksum caching for instant cache hits.
* **🔄 Dev Sync (`bolt sync dev`)**: Automatically fetches and updates `desugar_jdk_libs:2.1.5` and configuration files directly from Google Maven.
* **🧪 Real Unit Testing (`bolt test`)**: Execute unit tests locally powered by the official **JUnit 5 Platform Console Standalone Runner** with automatic test scaffolding and live progress trees.
* **📱 Live Hot-Reloading (`bolt run`)**: Instant live extension hot-reloading to an Android phone over WebSocket (port 9000) and Wi-Fi UDP auto-discovery (port 9001) without rebuilding APKs.
* **🔨 Mini-NDK & Native C/C++ (JNI & NDK) Support**: Build ultra-compact C/C++ shared libraries out-of-the-box (`2-3 KB`) using `-Oz`, `-flto`, and `--strip-all` without requiring the full Android Studio NDK!
* **⚙️ JNI Native Library Support**: Package `.so` native libraries across architectures (`armeabi-v7a`, `arm64-v8a`, `x86_64`, `x86`). By parsing `@UsesNativeLibraries`, Bolt intelligently maps ABI suffixes like `-v7a` into `component_build_infos.json` and bundles physical binaries into the `.aix` per official MIT App Inventor build server specifications.
* **🎨 Custom XML Integration (`@UsesXmls`)**: Custom layouts and manifests specified via `@UsesXmls` are extracted and injected into `.aix` via `component_build_infos.json`, maintaining compatibility with Kodular and Niotron.
* **🧩 Multi-Component & Android 11+ Manifest Extraction**: Build projects with multiple `@DesignerComponent` classes, and automatically extract `<uses-feature>` and `<queries>` tags into `component_build_infos.json`.
* **✂️ Annotation Stripping (`deannonate`)**: Strip heavy App Inventor metadata annotations silently post-compilation using an ASM-based remapper for maximum binary efficiency.
* **📄 Automatic Documentation & Helper Enums**: Automatically generates an `out/extension.txt` Markdown file alongside your `.aix` summarizing properties, methods, events, and Helper Enums (OptionLists) with parameter tables, return types, and binary size.
* **🔐 Automated Offline RSA Licensing (`bolt auth`)**: Secure your premium extensions with offline RSA-2048 public/private key licensing automatically tied to customer App Inventor email addresses.
* **📂 Project Scaffolding (`bolt create`)**: Scaffold complete Java or Kotlin projects with pre-filled skeletons (`str`, `int`), IDE support (IntelliJ, VS Code, Eclipse), and GitHub Actions workflows.
* **📦 Smart Dependency Management (`bolt add` & `bolt sync`)**: Search Maven Central directly via CLI (`bolt add <group>:<artifact>`) and resolve remote dependencies automatically.
* **🌳 Project Tree Visualizer (`bolt tree`)**: Display a beautiful visual hierarchical representation of project files and export clean output to `tree.txt`.
* **🔄 Seamless Legacy Migration (`bolt migrate`)**: Port legacy projects (Rush, Fast, Extension-Template, AI2 sources) into modern Bolt projects with automatic pre-migration ZIP backups.
* **🛡️ Bytecode Shading & Protection**: Built-in JarJar relocation (shading), StrGuard string encryption, ProGuard obfuscation, and R8 shrinking with clean noise-filtered logs.
* **☕ Modern Java & Kotlin Support**: Write extensions in Java 8/11/17/21+, Kotlin, or both in the same project with full AIDL, AndroidManifest, and native App Inventor type resolution (`YailList`, `trove4j`, `kawa`).

---

## 📥 Installation & Setup

Bolt requires a **Java Runtime Environment (JRE or JDK 11+, JDK 17 recommended)** to execute.

> **Note:** Bolt bundles its own Eclipse Compiler for Java (`ecj.jar`), so a standalone `javac` compiler is **not required**. Any standard Java 11+ runtime (`java`) is sufficient to run Bolt CLI.

### 🪟 Windows (PowerShell)

Default location: `%LOCALAPPDATA%\Bolt` (e.g. `C:\Users\<user>\AppData\Local\Bolt`)

Run in PowerShell:

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

*To install in a custom directory:*

```powershell
& { $(iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb) } -InstallPath "D:\Tools\Bolt"
```

*To skip Java check/download:*

```powershell
& { $(iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb) } -SkipJava
```

### 🍎 macOS & 🐧 Linux

Default location: `~/Bolt`

Run in Terminal:

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

Build and compile extensions directly on your phone:

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

## 💻 Commands Reference

<details><summary>here</summary>

| Command | Description |
| :--- | :--- |
| `bolt build [path]` | Compiles the extension into a production `.aix` in `out/` |
| `bolt create <name>` | Scaffolds a new extension project (Java or Kotlin) |
| `bolt clean [path]` | Deletes old build artifacts, temporary files, and `.bolt` caches |
| `bolt tree [path]` | Visualizes project file hierarchy in terminal and exports to `tree.txt` |
| `bolt add <dependency>` | Searches Maven Central and adds dependency coordinate to `bolt.yml` |
| `bolt sync [path]` | Downloads and syncs project and dev dependencies into `deps/` |
| `bolt sync dev` | Automatically fetches `desugar_jdk_libs:2.1.5` & config from Google Maven |
| `bolt sync ndk` | Verifies and checks Mini-NDK installation in `~/.bolt/libs/ndk` |
| `bolt deps <sync\|tree>` | Inspects and resolves project dependency trees |
| `bolt run [path]` | Starts Live Testing hot-reload server for Android companion (ports 9000/9001) |
| `bolt test [path]` | Executes real unit tests locally via JUnit 5 Console Platform Runner |
| `bolt auth <init\|generate>` | Extension licensing system (RSA-2048 keypair & customer license generator) |
| `bolt generate helper <int\|str>` | Scaffolds `OptionList` dropdown helper and injects `@SimpleProperty` into main class |
| `bolt migrate [type]` | Migrates legacy projects (Rush, Fast, Template, AI2) with auto ZIP backup |
| `bolt daemon <start\|stop\|status>` | Manages the persistent background Bolt Compiler Daemon |
| `bolt upgrade` | Upgrades Bolt CLI to the newest available release |

</details>

### Command Options

<details><summary>here</summary>

* `-r` / `--proguard`: Force ProGuard bytecode obfuscation and optimization
* `-s` / `--r8`: Force Google R8 shrieker and tree-shaking task on `build`
* `-o` / `--optimize`: Optimize extension bytecode size
* `-dx` / `-x` / `--dex`: Generate desugared Dalvik/ART `classes.dex` via D8 with incremental caching
* `-m` / `--keep-manifest`: Keep manifest components (Activity, Service, Receiver) in ProGuard rules
* `--no-daemon`: Bypass the background compiler daemon and compile locally in-process
* `--deannotate`: Strip internal App Inventor metadata annotations post-build for smaller binary size
* `-t <str\|int>` / `--template <str\|int>`: Scaffold pre-filled extension skeleton with helper enum on `create`
* `-p <package>` / `--package <package>`: Specify root package identifier on `create`
* `-l <Java\|Kotlin>` / `--language <Java\|Kotlin>`: Set primary programming language on `create`
* `-v` / `-d` / `--verbose` / `--debug`: Enable verbose diagnostic logging
* `-c` / `--color`: Enable or disable ANSI terminal colors
* `-V` / `--version`: Display version information

</details>

---

## ⚡ Compiler Daemon

<details><summary>here</summary>

Bolt introduces a persistent background compiler daemon that keeps all toolchains (ECJ, Kotlin compiler, D8, ProGuard) resident and warm in RAM:

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

When active, `bolt build` automatically delegates to the daemon on port 19090, reducing build times to **under 3 to 4 seconds**! If the daemon is stopped, Bolt transparently falls back to local in-process compilation.

</details>

---

## 📄 Documentation & Wiki

For comprehensive usage guidelines, configurations reference, and architectural deep dives:

* Refer to [Documentation.md](Documentation.md)
* Read the full offline wiki guide: [WIKI.md](WIKI.md)

---

## 🤝 Contributing & Support

Got an issue, feature request, or just want to help build Bolt?

* **Issues / Bug Reports**: [Open an Issue](https://github.com/TechHamara/bolt-cli/issues)
* **Pull Requests**: We welcome PRs! Please fork the repository and create a pull request with your proposed changes.

## 🤝 Donations & Support

* Donate on [Paypal](https://www.paypal.com/ncp/payment/UB4JGKR8YGYJE)
* Donate on [BuyMeCoffie1](https://buymeacoffee.com/techhamara/membership)
* Donate on [BuyMeCoffie2](https://buymeacoffee.com/techhamara)

### ❤️ Thanks

*Built with ❤️ for the MIT App Inventor Community.*

## 🚀 Recent Powerful Features

* **Live Hot-Reloading (`bolt run`)**: Push Java code changes directly to the App Inventor Companion in milliseconds over Wi-Fi!

* **Automated Licensing (`bolt auth`)**: Secure your premium extensions with Offline RSA licensing automatically tied to the customer's MIT App Inventor email!
* **Live Performance Monitor**: Catch memory leaks instantly in the CLI dashboard while hot-reloading.
* **Mini-NDK Support**: Build native C/C++ extensions out-of-the-box without installing the full massive Android Studio NDK.
