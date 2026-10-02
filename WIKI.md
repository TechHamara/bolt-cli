# ⚡ Bolt CLI Wiki: Installation & Setup Guide

Welcome to the official installation guide for **Bolt CLI** — the universal, lightning-fast framework and build system for MIT App Inventor 2 extensions.

---

## 📑 Table of Contents

- [⚡ Bolt CLI Wiki: Installation \& Setup Guide](#-bolt-cli-wiki-installation--setup-guide)
  - [📑 Table of Contents](#-table-of-contents)
  - [📋 Prerequisites](#-prerequisites)
  - [⚙️🖥️ Manual Setup for Windows](#️-manual-setup-for-windows)
    - [Step 1: Download `bolt.zip`](#step-1-download-boltzip)
    - [Step 2: Extract to Installation Directory](#step-2-extract-to-installation-directory)
    - [Step 3: Add to Environment Variables (PATH)](#step-3-add-to-environment-variables-path)
      - [Option 1: Via Windows GUI](#option-1-via-windows-gui)
      - [Option 2: Via PowerShell](#option-2-via-powershell)
    - [Step 4: Verify Installation](#step-4-verify-installation)
  - [⚙️💻 Manual Setup for Linux \& macOS](#️-manual-setup-for-linux--macos)
    - [Step 1: Download and Extract](#step-1-download-and-extract)
    - [Step 2: Clean Up Windows Batch File](#step-2-clean-up-windows-batch-file)
    - [Step 3: Make Launcher Executable](#step-3-make-launcher-executable)
    - [Step 4: Configure Shell Profile (`.bashrc` or `.zshrc`)](#step-4-configure-shell-profile-bashrc-or-zshrc)
    - [Step 5: Reload Shell and Verify](#step-5-reload-shell-and-verify)
  - [⚙️📱 Setup Process for Android Termux](#️-setup-process-for-android-termux)
    - [Step 1: Install Required Apps](#step-1-install-required-apps)
    - [Step 2: Configure Termux Environment](#step-2-configure-termux-environment)
    - [Step 3: Extract Bolt CLI](#step-3-extract-bolt-cli)
      - [Method A: Terminal Command (Recommended - Fast \& Easy)](#method-a-terminal-command-recommended---fast--easy)
      - [Method B: MT Manager GUI](#method-b-mt-manager-gui)
    - [Step 4: Configure `~/.bashrc`](#step-4-configure-bashrc)
    - [Step 5: Reload and Verify](#step-5-reload-and-verify)
  - [🚀 Quick Automated Installers (One-Line Setup)](#-quick-automated-installers-one-line-setup)
    - [Windows (PowerShell)](#windows-powershell)
    - [Linux \& macOS (Terminal)](#linux--macos-terminal)
    - [Android Termux](#android-termux)
  - [🧠 How It Works Under The Hood](#-how-it-works-under-the-hood)
    - [Architecture Diagram](#architecture-diagram)
    - [Key Components](#key-components)
  - [🧪 Test Your First Extension Build](#-test-your-first-extension-build)
  - [❓ Troubleshooting \& FAQ](#-troubleshooting--faq)

---

## 📋 Prerequisites

Bolt CLI requires a **Java Runtime Environment (JRE or JDK 11+, JDK 17 recommended)** to execute.

> [!NOTE]
> **No Standalone `javac` or Android Studio Required!**
> Bolt bundles its own Eclipse Compiler for Java (`ecj.jar`), Android SDK definitions (`android.jar`), D8 Dex compiler, and R8 toolchains. Any standard Java runtime (`java`) is sufficient to build extensions.

Verify your Java version:
```bash
java -version
```
If Java is not installed:
- **Windows**: Download from [Adoptium OpenJDK 17](https://adoptium.net/) or Microsoft OpenJDK.
- **Linux (Ubuntu/Debian)**: `sudo apt update && sudo apt install -y openjdk-17-jdk`
- **macOS**: `brew install openjdk@17`
- **Android Termux**: `pkg install -y openjdk-17`

---

## ⚙️🖥️ Manual Setup for Windows

### Step 1: Download `bolt.zip`
Download the latest `bolt.zip` from the [Bolt CLI GitHub Releases](https://github.com/TechHamara/bolt-cli/releases/latest).

### Step 2: Extract to Installation Directory
1. Create a folder named `Bolt` (e.g., `C:\Bolt` or `%LOCALAPPDATA%\Bolt`).
2. Extract the contents of `bolt.zip` into this folder.
3. Your folder structure should look like:
   ```text
   C:\Bolt\
   ├── bin\
   │   ├── bolt.bat
   │   ├── bolt
   │   └── bolt.jar
   ├── libs\
   │   ├── android.jar
   │   └── tools\
   └── icon.png
   ```

### Step 3: Add to Environment Variables (PATH)

#### Option 1: Via Windows GUI
1. Press `Win + R`, type `sysdm.cpl`, and press Enter.
2. Go to the **Advanced** tab and click **Environment Variables**.
3. Under **User variables** (or System variables):
   - *(Optional)* Click **New**, set Variable name: `BOLT_HOME`, Variable value: `C:\Bolt`.
   - Select the **Path** variable and click **Edit**.
   - Click **New** and add the path to the `bin` folder: `C:\Bolt\bin`.
4. Click **OK** on all dialogs to save changes.

#### Option 2: Via PowerShell
Run PowerShell as Administrator or standard user:
```powershell
# Set BOLT_HOME
[Environment]::SetEnvironmentVariable("BOLT_HOME", "C:\Bolt", "User")

# Append bin folder to PATH
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*C:\Bolt\bin*") {
    [Environment]::SetEnvironmentVariable("Path", "$currentPath;C:\Bolt\bin", "User")
}
```

### Step 4: Verify Installation
Open a **new** Command Prompt or PowerShell window and run:
```cmd
bolt -v
```
You should see the installed Bolt CLI version and system information.

---

## ⚙️💻 Manual Setup for Linux & macOS

### Step 1: Download and Extract
Open your terminal and run:
```bash
# Create directory
mkdir -p "$HOME/Bolt"

# Download the latest package
curl -L https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt.zip -o "$HOME/Bolt/bolt.zip"

# Extract
unzip -o "$HOME/Bolt/bolt.zip" -d "$HOME/Bolt"
rm -f "$HOME/Bolt/bolt.zip"
```

### Step 2: Clean Up Windows Batch File
Remove the Windows launcher script:
```bash
rm -f "$HOME/Bolt/bin/bolt.bat"
```

### Step 3: Make Launcher Executable
```bash
chmod +x "$HOME/Bolt/bin/bolt"
```

### Step 4: Configure Shell Profile (`.bashrc` or `.zshrc`)
Determine which shell you are using (`echo $SHELL`). If using **Zsh** (default on modern macOS), edit `~/.zshrc`. If using **Bash** (default on Linux), edit `~/.bashrc`.

#### For JDK 11 to 23:
Add the following lines to your profile:
```bash
# Bolt CLI Configuration
export BOLT_HOME="$HOME/Bolt"
export PATH="$PATH:$BOLT_HOME/bin"

bolt() {
    java -jar "$BOLT_HOME/bin/bolt.jar" "$@"
}
```

#### For JDK 24 and above:
JDK 24+ displays warnings on native access; add the `--enable-native-access` flag:
```bash
# Bolt CLI Configuration
export BOLT_HOME="$HOME/Bolt"
export PATH="$PATH:$BOLT_HOME/bin"

bolt() {
    java --enable-native-access=ALL-UNNAMED -jar "$BOLT_HOME/bin/bolt.jar" "$@"
}
```

### Step 5: Reload Shell and Verify
```bash
source ~/.bashrc   # On Linux / Bash
# or
source ~/.zshrc    # On macOS / Zsh

bolt -v
```

---

## ⚙️📱 Setup Process for Android Termux

Compile extensions directly on your Android phone with zero computer requirement!

### Step 1: Install Required Apps
1. **Termux App**: Download the latest release from [Termux GitHub Releases](https://github.com/termux/termux-app/releases) (recommended: `termux-app_v..._arm64-v8a.apk`) or from [F-Droid](https://f-droid.org/en/packages/com.termux/).
   *(Do NOT install Termux from Google Play Store as that version is deprecated).*
2. *(Optional)* **MT Manager**: Install MT Manager APK if you want a visual file manager for Android.

### Step 2: Configure Termux Environment
Open the Termux app and execute the following commands:
```bash
# 1. Grant storage permissions (tap Allow on prompt)
termux-setup-storage

# 2. Upgrade Termux packages
pkg upgrade -y

# 3. Install OpenJDK 17, unzip, and curl
pkg install openjdk-17 unzip curl -y
```

### Step 3: Extract Bolt CLI

#### Method A: Terminal Command (Recommended - Fast & Easy)
Run these commands inside Termux:
```bash
mkdir -p "$HOME/Bolt"
curl -L https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt.zip -o "$HOME/Bolt/bolt.zip"
unzip -o "$HOME/Bolt/bolt.zip" -d "$HOME/Bolt"
rm -f "$HOME/Bolt/bolt.zip"
chmod +x "$HOME/Bolt/bin/bolt"
```

#### Method B: MT Manager GUI
1. Download `bolt.zip` using your Android mobile browser.
2. Open **MT Manager**.
3. Add Termux storage: Click menu -> Add local storage -> Navigate to `/data/data/com.termux/files/home`.
4. Create a folder named `Bolt` in Termux home.
5. Extract the downloaded `bolt.zip` inside `$HOME/Bolt`.
6. Delete `bolt.bat` from `bin/`.

### Step 4: Configure `~/.bashrc`
In Termux, append the Bolt configuration to `.bashrc`:
```bash
cat << 'EOF' >> ~/.bashrc

# Bolt CLI Configuration
export BOLT_HOME="$HOME/Bolt"
export PATH="$PATH:$BOLT_HOME/bin"

bolt() {
    java -jar "$BOLT_HOME/bin/bolt.jar" "$@"
}
EOF
```

### Step 5: Reload and Verify
Reload your environment or restart Termux:
```bash
source ~/.bashrc

bolt -v
```
You are all set! You can now create and compile extensions on Android.

---

## 🚀 Quick Automated Installers (One-Line Setup)

If you prefer an automated installation that handles downloading, extracting, and configuring environment variables automatically:

### Windows (PowerShell)
```powershell
iwr https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.ps1 -useb | iex
```

### Linux & macOS (Terminal)
```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
```

### Android Termux
```bash
termux-setup-storage
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

---

## 🧠 How It Works Under The Hood

### Architecture Diagram

```text
                        ┌───────────────────────────────┐
                        │   User Command (e.g. `bolt`)  │
                        └───────────────┬───────────────┘
                                        │
                         Executes launcher shell/batch
                                        │
                                        ▼
                        ┌───────────────────────────────┐
                        │ java -jar $BOLT_HOME/bin/...  │
                        └───────────────┬───────────────┘
                                        │
                                        ▼
       ┌─────────────────────────────────────────────────────────────────┐
       │                   BoltLocator Auto-Discovery                    │
       │  1. Check explicit $BOLT_HOME environment variable              │
       │  2. Dynamic codeSource reflection from active bolt.jar location  │
       │  3. Fallback to platform standard paths (%LOCALAPPDATA%/Bolt)   │
       └────────────────────────────────┬────────────────────────────────┘
                                        │
                                        ▼
       ┌─────────────────────────────────────────────────────────────────┐
       │                   In-Process Compiler Engine                    │
       │  • Eclipse Compiler for Java (ECJ) -> compiles Java source      │
       │  • Kotlin Compiler Embeddable      -> compiles Kotlin source    │
       │  • Android SDK (libs/android.jar)  -> type & component binding  │
       │  • D8 / Desugar Engine             -> generates classes.dex     │
       │  • Mini-NDK Toolchain              -> builds C/C++ native .so   │
       │  • In-Process ZIP Archive Engine   -> packages final .aix       │
       └─────────────────────────────────────────────────────────────────┘
```

### Key Components
1. **Dynamic `BoltLocator` Engine**:
   Bolt does not hardcode filesystem paths. When `bolt.jar` is launched, `BoltLocator` dynamically resolves its root folder by checking parent directories or the `BOLT_HOME` environment variable. This allows Bolt to be placed in any directory or portable USB drive.
2. **Embedded ECJ (Eclipse Compiler for Java)**:
   Instead of requiring a heavy external JDK compiler (`javac`), Bolt bundles `ecj.jar` inside `libs/tools/`. Java source code is compiled directly in-process within milliseconds.
3. **Pure Universal Bytecode**:
   Because the entire compilation pipeline is written in Kotlin and Java bytecode, it runs natively on ARM64 (mobile phones, Apple Silicon M1/M2/M3) and x86_64 without needing platform-specific binary recompilation.

---

## 🧪 Test Your First Extension Build

Once installed, test that everything works end-to-end:

1. **Create a test extension**:
   ```bash
   bolt create HelloBolt
   ```
   Select Java or Kotlin when prompted.

2. **Navigate into the project**:
   ```bash
   cd HelloBolt
   ```

3. **Build the extension**:
   ```bash
   bolt build
   ```

4. **Result**:
   Your compiled extension `.aix` will be available in the `out/` folder:
   ```text
   out/
   └── com.example.HelloBolt.aix
   ```

---

## ❓ Troubleshooting & FAQ

### Q1: `command not found: bolt`
- **Cause**: The `bin` directory is not added to your `PATH`, or you have not reloaded your shell.
- **Fix**:
  - On Windows: Verify that `C:\Bolt\bin` is present in your User `Path`. Restart Command Prompt.
  - On Linux/macOS/Termux: Run `source ~/.bashrc` or `source ~/.zshrc`. Ensure `export PATH="$PATH:$BOLT_HOME/bin"` is in your profile.

### Q2: `Permission denied: /.../bin/bolt`
- **Fix**: Run `chmod +x $HOME/Bolt/bin/bolt`.

### Q3: `java: command not found`
- **Fix**: Java is not installed or not in your system PATH.
  - Windows: Install [Adoptium OpenJDK 17](https://adoptium.net/).
  - Linux: `sudo apt install -y openjdk-17-jdk`
  - Termux: `pkg install openjdk-17 -y`

### Q4: Upgrading Bolt CLI
To update an existing installation:
```bash
bolt upgrade
```
Or simply download the newest `bolt.zip` and extract it over your existing `Bolt` folder.
