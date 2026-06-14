![Banner.png](https://github.com/user-attachments/assets/b354a91d-4290-4200-bb17-d06ff03f8d23)

# ⚡ Bolt CLI

[![Latest Version](https://img.shields.io/badge/version-1.0.0-blue.svg?style=for-the-badge)](https://github.com/TechHamara/bolt-cli)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg?style=for-the-badge)](#)
[![MIT App Inventor](https://img.shields.io/badge/platform-App%20Inventor%202-orange.svg?style=for-the-badge)](https://github.com/mit-cml/appinventor-sources)

**Bolt CLI** is a modern, high-performance command-line tool designed to revolutionize the way you develop and build extensions for MIT App Inventor 2 and its various distributions (Kodular, Niotron, etc.).

> [!IMPORTANT]
> **Credits & Acknowledgements**: Bolt CLI is built upon the excellent foundation of the original [Rush CLI](https://github.com/shreyashsaitwal/rush-cli) project created by [Shreyash Saitwal](https://github.com/shreyashsaitwal). We express our sincere gratitude and credit to Shreyash and all the Rush contributors for their pioneering work in building high-performance compilation toolsets for the App Inventor community.

---

## 🌟 Key Features

Bolt CLI brings modern Android and JVM build practices to App Inventor extension development:

* **⚡ Lightning-Fast Builds**: Optimized build pipelines with high-speed incremental compiler integration keep compile-to-packaged times under a few seconds.
* **📦 Maven-Style Dependency Management**: Seamlessly declare remote dependencies using coordinates (e.g., `groupId:artifactId:version`) or point to local JAR/AAR packages inside your `deps/` directory.
* **☕ Kotlin & Java Language Support**: Write extensions in Java, Kotlin, or both simultaneously in the same project!
* **⚙️ Full AndroidManifest.xml Integration**: Declare receivers, providers, services, activities, and metadata inside a custom `AndroidManifest.xml` in your `src/` folder with shorthand class name expansion (e.g. `.MyService` expands to your package name).
* **🛠️ Advanced Optimization & Bytecode Stripping**: Supports R8, ProGuard optimizations, desugaring, and custom ProGuard configurations.
* **🔄 Seamless Project Migration**: Easily port legacy projects (Rush, Fast,  `extension-template`, AI2 source projects) to modern Bolt CLI architecture with **automatic pre-migration backup ZIP** to guarantee no data loss.
* **🛠️ Desugaring & ProGuard Optimization**: Fully supports Java 8+ features, including lambda expressions `()->`, and allows shrinking/obfuscation with aggressive defaults (`-optimizationpasses 5`).
* **📄 Automatic Documentation**: Bolt auto-generates a formatted Markdown specifications catalog (`extension.txt`) on every build inside your `out/` directory.
* **🎛️ Dynamic Android Compile SDK**: Supports target SDK configuration via `android_sdk` in `bolt.yml`. No more hardcoded SDK APIs!
* **🧩 Multi-Component Support**: Bundle multiple `@Extension` components in a single `.aix` with segregated auto-generated documentation.
* **🎨 Red Drop-down Blocks**: Use App Inventor's `@Options` annotation for type-safe parameter enums.
* **🧹 Zero Configuration Deannotation**: Strip metadata annotations without needing to compress/shrink bytecode.
* **🌳 Project Directory Tree Visualizer**: The `bolt tree` command displays a beautiful visual hierarchical representation of all files and folders in your project and automatically saves it as `tree.txt` at the project root.

---

## Demo Terminal 

<details><summary>here</summary>

## Bolt CLI version:

<img width="968" height="797" alt="bolt-v" src="https://github.com/user-attachments/assets/d756a82a-8631-4d5e-9f3e-92fffd195c36" />

## Bolt Language Selection

<img width="591" height="347" alt="bolt-lang" src="https://github.com/user-attachments/assets/c55a2d7f-4277-456a-9b1c-1394a9a980fd" />

## Bolt Project Created

<img width="561" height="423" alt="bolt-created" src="https://github.com/user-attachments/assets/8ed4a88d-55ca-4628-9eca-2e87435169fc" />

## Bolt Tree

<img width="497" height="489" alt="bolt-tree" src="https://github.com/user-attachments/assets/bbf3018f-d314-4dc5-b178-e0aaab8db5eb" />

## Bolt Migrate

<img width="731" height="510" alt="migrate-demo" src="https://github.com/user-attachments/assets/ba94a564-2210-4c45-a7f2-b41b074e3468" />

## Bolt Build

<img width="515" height="738" alt="bolt-build" src="https://github.com/user-attachments/assets/0cc04e94-276c-47d2-a02f-c2a48c941040" />


</details>

---

## 🛠️ Installation & Setup

Before installing Bolt CLI, ensure you have **JDK 8 or above** installed on your system.

### Windows (PowerShell)

To install to the default location (`$HOME\.bolt`):

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

Or specify a custom install directory:

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex -Args @{ InstallPath = 'C:\MyCustomPath\.bolt' }
```

*The installer script respects the `BOLT_HOME` environment variable if set.*

### Linux & macOS 

<details><summary>
(Current Not working 🚫)</summary>

1. In the terminal, run the automated installation script:

   ```bash
   curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
   ```

2. Add `$HOME/.bolt/bin` to your `PATH` environment variable.

### Android (Termux)

Build and compile extensions directly on your phone! Run the automated Termux installer script:

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

</details>

---

## 🚀 Quick Start Guide

Let's create a simple extension:

1. **Scaffold a new project**:

   ```bash
   bolt create MyAwesomeExtension
   ```

   Bolt CLI will guide you interactively through the package name, programming language (Java or Kotlin), and target IDE (VSCode, IntelliJ/Android Studio, or both) configurations.

2. **Navigate into the directory**:

   ```bash
   cd MyAwesomeExtension
   ```

3. **Compile the extension**:

   ```bash
   bolt build
   ```

4. **Retrieve the bundle**:
   Your generated `.aix` extension bundle will be available inside the `out/` directory, along with auto-generated markdown documentation `extension.txt`!

---

## 📖 CLI Command Reference

| Command | Options | Description |
| :--- | :--- | :--- |
| `bolt build` | `-y, --sync`, `-o, --optimize`, `-m, --keep-manifest` | Compiles source files, processes annotations, resolves dependencies, and bundles the `.aix` file. |
| `bolt clean` | *none* | Deletes compiler caches and build files for a clean environment. |
| `bolt create` | *interactive* | Scaffolds a new project with IDE settings, sample templates, and configurations. |
| `bolt sync` | `--dev-deps` | Resolves dependencies declared in `bolt.yml` and performs Support-to-AndroidX Jetifier translation when `jetify: true` is set. |
| `bolt tree` | *none* | Displays a beautiful visual project directory hierarchy and saves a plain text copy to `tree.txt`. |
| `bolt migrate` | `rush`, `fast`, `template`, `ai2` | Converts legacy project architectures to modern Bolt CLI standard. Automatically saves a zip backup of the folder first. |
| `bolt upgrade` | `--force` | Securely upgrades the local Bolt CLI binary to the latest release on GitHub. |

---

## 📄 Documentation & Wiki

For comprehensive usage guidelines, configurations reference, and architectural deep dives:

* Refer to [DOCUMENTATION.md](https://github.com/TechHamara/bolt-cli/blob/main/DOCUMENTATION.md)
* Read the full offline wiki guide: [WIKI.md](https://github.com/TechHamara/bolt-cli/wiki)
* See how Bolt works under the hood: [how-to-work.md](https://github.com/TechHamara/bolt-cli/blob/main/how-to-work.md)

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
