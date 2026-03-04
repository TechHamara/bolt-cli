![Banner.png](https://github.com/user-attachments/assets/b354a91d-4290-4200-bb17-d06ff03f8d23)

# Bolt ✨
**The Modern Compiler at Lightning Speed Extension Builder for MIT App Inventor 2**

Welcome to the official documentation for **Bolt**. This guide will provide everything you need to know about Bolt's features, available commands, basic usage, and frequently asked questions.

---

## 🌟 Features

Bolt is designed to make extension development for MIT App Inventor faster, more intuitive, and feature-rich.

- ⚡ **Lightning Fast Builds**: Bolt is optimized for speed, so you spend less time waiting and more time coding.
- 📦 **Maven-style Dependency Management**: Easily manage external libraries and dependencies just like you would in a typical Android/Java project.
- 🔷 **Kotlin Support**: Write your extensions in Kotlin and take advantage of modern language features! Say goodbye to writing exclusively in Java.
- ⚙️ **AndroidManifest.xml Support**: Define components, permissions, receivers, and services using shorthand class names (e.g., `.MyService` automatically expands to `com.mypackage.MyService`).
- 🔄 **Universal Migration**: Migrate from Bolt v1, `extension-template`, or raw AI2 source projects to Bolt v2 with a single command.
- 🛠️ **Desugaring & ProGuard Optimization**: Fully supports Java 8+ features, including lambda expressions `()->`, and allows shrinking/obfuscation with aggressive defaults (`-optimizationpasses 5`).
- 📄 **Automatic Documentation**: Bolt auto-generates a formatted Markdown specifications catalog (`extension.txt`) on every build inside your `out/` directory.
- 🎛️ **Dynamic Android Compile SDK**: Supports target SDK configuration via `android_sdk` in `bolt.yml`. No more hardcoded SDK APIs!
- 🖼️ **Blocks PNG Generation**: Pass `-b` to create image mockups for extension blocks natively.
- 🧩 **Multi-Component Support**: Bundle multiple `@Extension` components in a single `.aix` with segregated auto-generated documentation.
- 🎨 **Red Drop-down Blocks**: Use App Inventor's `@Options` annotation for type-safe parameter enums.
- 🤖 **Continuous Integration Ready**: Scaffolds GitHub Actions workflows out of the box for fully managed deployment pipelines.
- 🧹 **Zero Configuration Deannotation**: Strip metadata annotations without needing to compress/shrink bytecode.
- 💻 **IDE Code Suggestions**: Scaffolds `.vscode/settings.json` and IntelliJ/Android Studio project files for instant code completion.
- 🛡️ **Keep Manifest Classes**: Use `-m` flag to automatically prevent ProGuard from stripping classes declared in your `AndroidManifest.xml`.
- 🔀 **Jetifier for AndroidX**: Set `jetify: true` in `bolt.yml` to automatically migrate `android.support.*` dependencies to AndroidX during sync.

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
You can develop and build extensions directly on your Android phone using Termux.

1. Download and install [Termux](https://github.com/termux/termux-app/releases/latest) and a text/file editor of your choice.
2. Open Termux and provide storage permissions:
   ```bash
   termux-setup-storage
   ```
3. Run the following command to automatically install all dependencies (Dart, OpenJDK, Git) and compile Bolt CLI on Termux:
   ```bash
   curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
   ```
4. Once completed, restart Termux or add `$HOME/.bolt/bin` to your `PATH` environment variable. Verify the installation by running:
   ```bash
   bolt -v
   ```
5. After installation, sync dependencies:
   ```bash
   bolt sync --dev-deps
   ```

---

## 🚀 How To Use (Quick Start)

### 1. Creating a New Extension
To start a new extension project, navigate to your desired directory and use the `create` command:

```bash
bolt create MyAwesomeExtension
```
Bolt will prompt you to configure:
1. **Package name**: The package for your new extension (e.g., `com.myname.myextension`).
2. **Language**: Choose between Java or Kotlin (you can write in both simultaneously later).
3. **IDE**: Choose the IDE/Editor you plan to use so Bolt can quickly configure it for you.

### 2. Building Your Extension
Navigate into your newly created project folder, then run:

```bash
bolt build
```

**Useful build flags:**
| Flag | Description |
|---|---|
| `-o, --optimize` | Optimize and obfuscate bytecode using ProGuard; equivalent to `-r`. Default is off. |
| `-b, --build-blocks` | Generate PNG block mockup images. |
| `-m, --keep-manifest` | Preserve all classes declared in `AndroidManifest.xml` from ProGuard stripping. |

Your generated `.aix` extension file will be in the `out/` directory.

---

## 📖 CLI Reference (Commands)

Bolt comes packed with commands to manage your project lifecycle.

| Command | Description |
|---|---|
| `bolt build` | Builds the extension project. Flags: `-o` (ProGuard optimization, defaults **off** unless `proguard:` in bolt.yml or `-r` is supplied), `-m` (keep manifest classes). |
| `bolt clean` | Deletes old build files and compiler caches for a fresh build. |
| `bolt create` | Scaffolds a new project with IDE configs (VSCode, IntelliJ), GitHub Actions, ProGuard rules, and manifest template. |
| `bolt sync` | Syncs project dependencies. Applies Jetifier automatically when `jetify: true` is set. |
| `bolt tree` | Prints the dependency graph of the current extension project. |
| `bolt migrate` | Migrates Bolt v1, `extension-template`, AI2 source projects, or `fast.yml` projects to Bolt v2 architecture. |
| `bolt upgrade` | Upgrades the Bolt CLI tool to the latest version from GitHub. |

### Global Options
- `-h, --help`: Print the help message.
- `-v, --verbose`: Turn on verbose logging for detailed output.
- `-c, --[no-]color`: Toggle colored output in the terminal (On by default).
- `-V, --version`: Print the current Bolt version.

---

## ⚙️ Configuration (`bolt.yml`)

The `bolt.yml` file at the root of your project controls all build behavior.

| Key | Type | Default | Description |
|---|---|---|---|
| `version` | `String` | *required* | The version name of your extension (e.g., `1.0.0`). |
| `min_sdk` | `int` | `7` | Minimum Android SDK level your extension supports. |
| `desugar` | `bool` | `false` | Enable Java 8+ desugaring (lambdas `()->`, method references, etc.). |

> insert the required `implements` clause and stub `onNoteOn`/`onNoteOff`
> methods during compilation to avoid confusing javac errors.
| `android_sdk` | `int` | `36` | Target Android compile SDK API level. |
| `java8` | `bool` | `false` | Force compilation with `-source 1.8`/`-target 1.8`.  Bolt will also automatically enable Java 8 when lambda expressions (`->`/`::`) are detected in your sources. |
| `jetify` | `bool` | `false` | Automatically convert `android.support.*` dependencies to AndroidX during sync. |
| `proguard` | `bool` | `false` | Enable ProGuard optimization and shrinking. When absent `bolt build` will not shrink; use `-r` to override. |
| `proguard_version` | `string` | bundled value | Specify a custom ProGuard version to download/use; defaults to the version shipped with Bolt. |
| `deannonate` | `bool` | `false` | Strip annotations without optimizing. |
| `gen_docs` | `bool` | `false` | Auto-generate `extension.txt` documentation during build. |
| `auto_version` | `bool` | `false` | Automatically increment the extension version number. |
| `dependencies` | `List` | `[]` | Maven coordinates or local JAR/AAR files. |
| `kotlin.compiler_version` | `String` | `1.8.0` | Kotlin compiler version to use. |
| `kotlin_version` | `String` | `1.8.0` | Alternate top‑level key mapping to `kotlin.compiler_version`. |

### Notes on Kotlin configuration

Bolt accepts two syntaxes for specifying the Kotlin version in `bolt.yml`:

```yaml
kotlin_version: '1.8.0'    # short form (top-level key)
# or
kotlin:
  compiler_version: '1.8.0'  # nested object form
```
The loader treats both forms identically.

### AndroidManifest.xml

Bolt supports a full `AndroidManifest.xml` inside `src/`. You can use **shorthand class names** — a leading dot like `.MyService` or `...MyReceiver` is automatically expanded to the full package name declared in the manifest's `package` attribute.

```xml
<service android:name=".MyService" />
<!-- Expands to: android:name="com.mypackage.MyService" -->
```

When using `-m` during build, all classes declared as `<activity>`, `<service>`, `<receiver>`, or `<provider>` are automatically kept from ProGuard stripping.

---

## ❓ FAQ (Frequently Asked Questions)

### **Q: Do I need to uninstall the old extension builder (like ant) to use Bolt?**
**A:** No, Bolt works completely independently. You need to uninstall then delete .bolt installation Dir to uninstall.

### **Q: I'm getting a "JDK not found" error!**
**A:** Ensure you have JDK 8 or above installed on your system and that the `JAVA_HOME` environment variable is successfully configured and added to your system `PATH`.

### **Q: Can I use both Kotlin and Java in the same project?**
**A:** Yes! Bolt supports interoperability between Kotlin and Java. You can use `.kt` and `.java` files side-by-side in your `src` directory.

### **Q: Where are the dependencies defined?**
**A:** Dependencies are defined inside `bolt.yml`. You can specify Maven coordinates or local JAR/AAR files placed in the `deps/` directory.

### **Q: My dependencies use `android.support.*` — how do I migrate to AndroidX?**
**A:** Add `jetify: true` to your `bolt.yml`. During `bolt sync`, all JAR/AAR artifacts will be automatically transformed from `android.support.*` to their `androidx.*` equivalents using Jetifier.

### **Q: Can I migrate from `fast.yml` projects?**
**A:** Yes! Bolt will automatically detect and convert `fast.yml` configuration files to the new `bolt.yml` format during migration using `bolt migrate`.

### **Q: Can I migrate from extension-template, AI2 source, or fast.yml projects?**
**A:** Yes! Run `bolt migrate` inside the project directory. Bolt will detect `build.xml`, `src/` folders, or legacy `fast.yml` configuration files from traditional extension templates and automatically inject the `@Extension` annotation if missing, generate/convert to `bolt.yml`, and restructure sources as needed.

### **Q: How do I get code suggestions in my IDE?**
**A:** When creating a new project with `bolt create`, choose your IDE (VSCode, IntelliJ/Android Studio, or Both). Bolt scaffolds the appropriate settings files (`.vscode/settings.json`, `.idea/`, `.iml`) for instant code completion and classpath resolution.

### **Q: How do I build the Bolt tool itself from source?**
**A:** If you want to contribute to Bolt or build the CLI from source:
1. Clone the repository.
2. Run `dart pub get` to fetch dependencies.
3. Generate required codebase using: `dart run build_runner build --delete-conflicting-outputs`
4. Run `./scripts/build.sh -v <version>` (Linux/Mac) or `.\scripts\build.ps1 -v <version>` (Windows).

---

## 🤝 Contributing & Support

Got an issue, feature request, or just want to help build Bolt?

- **Issues / Bug Reports**: [Open an Issue](https://github.com/TechHamara/bolt-cli/issues)
- **Pull Requests**: We welcome PRs! Please fork the repository and create a pull request with your proposed changes.

### ❤️ Footer
*Built with ❤️ for the MIT App Inventor Community.*

Let's make extension development a delightful experience! Happy coding! 💻
=======

# bolt-cli
A Modern Extension Builder for MIT App Inventor 2
