![Banner.png](https://github.com/user-attachments/assets/b354a91d-4290-4200-bb17-d06ff03f8d23)

# ⚡ Bolt CLI

**Bolt CLI** Manual Check-Pass File. to manual check bolt cli features functions and mention here pass verify or add bugs issue.

---

## 🌟 Key Features

Bolt CLI brings modern Android and JVM build practices to App Inventor extension development:

* **⚡ Persistent Bolt Compiler Daemon (`bolt daemon`)**✅: Keeps JVM compiler loaded in memory for sub-second to 4s incremental builds.
* **🔄 Dev Dependencies Sync (`bolt sync dev`)**✅: Auto-fetches and extracts latest `desugar_jdk_libs:2.1.5` and configuration files from Maven.
* **📡 Auto-Detect AIDL Compilation**✅: Automatically finds and transpiles `.aidl` interface files in `src/` to `.java` without extra configuration flags.
* **📦 Native C/C++ NDK Compression**✅: Uses `c++_static` STL linking and `--strip-unneeded` stripping to shrink C/C++ native extension `.aix` bundles.
* **⚡ Incremental D8 Bytecode Caching**✅: Added D8 Bytecode Caching.
* **📦 Maven-Style Dependency Management**✅: Seamlessly declare remote dependencies using coordinates (e.g., `groupId:artifactId:version`) or point to local JAR/AAR packages inside your `deps/` directory.
* **☕ Kotlin & Java Language Support**✅: Write extensions in Java, Kotlin, or both simultaneously in the same project!
* **⚙️ Full AndroidManifest.xml Integration**✅: Declare receivers, providers, services, activities, and metadata inside a custom `AndroidManifest.xml` in your `src/` folder with shorthand class name expansion (e.g. `.MyService` expands to your package name).
* **🛠️ Custom XML Bundling (`@UsesXmls`)**⚠️ (Not Tested): Easily bundle custom XML files (layouts, menus, values, etc.) directly into your extension with automatic manifest merging.
* **🛠️ Android Resources (`@UsesAssets`, `@UsesPermissions`)**✅: Automatically includes assets and declares necessary permissions in your extension.
* **🛠️ Native libraries (`@UsesNativeLibs`)**✅: Automatically includes native C/C++ libraries (`.so`) in your extension.
* **🛠️ Advanced Optimization & Bytecode Stripping**✅: Supports R8, ProGuard optimizations, desugaring, and custom ProGuard configurations.
* **🔄 Seamless Project Migration**✅: Easily port legacy projects (Rush, Fast,  `extension-template`, AI2 source projects) to modern Bolt CLI architecture with **automatic pre-migration backup ZIP** to guarantee no data loss.
* **🛠️ Desugaring & ProGuard Optimization**✅: Fully supports Java 8+ features, including lambda expressions `()->`, and allows shrinking/obfuscation with aggressive defaults (`-optimizationpasses 5`).
* **📄 Automatic Documentation**✅: Bolt auto-generates a formatted Markdown specifications catalog (`extension.txt`) on every build inside your `out/` directory.
* **🎛️ Dynamic Android Compile SDK**✅: Supports target SDK configuration via `android_sdk` in `bolt.yml`. No more hardcoded SDK APIs!
* **🧩 Multi-Component Support**✅: Bundle multiple `@Extension` components in a single `.aix` with segregated auto-generated documentation.
* **🎨 Red Drop-down Blocks**✅: Use App Inventor's `@Options` annotation for type-safe parameter enums.
* **🧹 Zero Configuration Deannotation**✅: Strip metadata annotations without needing to compress/shrink bytecode.
* **🌳 Project Directory Tree Visualizer**✅: The `bolt tree` command displays a beautiful visual hierarchical representation of all files and folders in your project and automatically saves it as `tree.txt` at the project root.

---

## 🛠️ Installation & Setup

Before installing Bolt CLI, ensure you have **JDK 8 or above** installed on your system.

### Windows (PowerShell)✅

To install to the default location (`$HOME\.bolt`):

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

Or specify a custom install directory:

```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex -Args @{ InstallPath = 'C:\MyCustomPath\.bolt' }
```

*The installer script respects the `BOLT_HOME` environment variable if set.*

### Linux & macOS⚠️ (Not Tested)

1. In the terminal, run the automated installation script:

   ```bash
   curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
   ```

2. Add `$HOME/.bolt/bin` to your `PATH` environment variable.

### Android (Termux)⚠️ (Not Tested)

Build and compile extensions directly on your phone! Run the automated Termux installer script:

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

## 📖 CLI Command Reference

| Command | Options | Manual Check | Description |
| :--- | :--- | :--- | :--- |
| `bolt build` | `-y, --sync`, `-o, --optimize`, `-m, --keep-manifest` | ✅ | Compiles source files, processes annotations, resolves dependencies, and bundles the `.aix` file. |
| `bolt clean` | *none* | ✅ | Deletes compiler caches and build files for a clean environment. |
| `bolt create` | *interactive* | ✅ | Scaffolds a new project with IDE settings, sample templates, and configurations. |
| `bolt daemon` | `start`, `stop`, `status` | ✅ | Manages persistent background compiler daemon (Port 19090) for sub-4s builds. |
| `bolt sync` | `dev`, `-f, --force` | ✅ | Syncs project dependencies or dev-dependencies (`desugar_jdk_libs:2.1.5`). |
| `bolt add` | `<maven-coordinate>` | ✅ | Automatically searches Maven Central and adds dependencies to `bolt.yml`. |
| `bolt run` | *none* | ✅ | Starts live testing session with hot-reloading over WebSocket to mobile device. |
| `bolt auth` | *interactive* | ✅ | Manages offline RSA-2048 licensing for premium extensions. |
| `bolt test` | *none* | ✅ | Executes unit tests locally on PC using JUnit 5 and Robolectric. |
| `bolt tree` | *none* | ✅ | Displays visual project directory hierarchy and saves a plain text copy to `tree.txt`. |
| `bolt migrate` | `rush`, `fast`, `template`, `ai2` | ✅ | Converts legacy project architectures to modern Bolt CLI standard. Automatically saves a zip backup first. |
| `bolt upgrade` | `--force` | ⚠️ | Securely upgrades the local Bolt CLI binary to the latest release on GitHub. |

---

## 🤝 Donations & Support

* Donate on [Paypal](https://www.paypal.com/ncp/payment/UB4JGKR8YGYJE)
* Donate on [BuyMeCoffie1](https://buymeacoffee.com/techhamara/membership)
* Donate on [BuyMeCoffie2](https://buymeacoffee.com/techhamara)
