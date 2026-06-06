# 📱 Bolt CLI: Termux & Linux Setup Guide

Developing, compiling, and packaging **MIT App Inventor 2 extensions** directly on your Android device or Linux machines is fully supported and optimized in **Bolt CLI**. 

Unlike JVM-based tools, **Bolt CLI is built natively using Dart**, delivering instant execution speeds and zero JVM startup latency. This guide walks you through setting up a complete, professional extension-building environment on your phone or Linux distribution.

---

## ⚙️📱 Setup Process for Android Termux

Follow this step-by-step guide to get everything configured on your Android device.

### Step 1: Install Required Android Applications
For a premium, visual workspace management experience on mobile, we recommend using a dual-panel file manager alongside the terminal.

1. **Download MT Manager**: Install [MT Manager APK](https://mt2.cn/download/) (A highly versatile dual-panel file manager and text editor).
2. **Download Termux**: Install the latest official APK from GitHub Releases:
   * **[termux-app vX.X.X (GitHub Debug arm64-v8a)](https://github.com/termux/termux-app/releases)**
   * *Warning: Do NOT download Termux from the Google Play Store as that version is deprecated and no longer receives package updates.*

---

### Step 2: Initialize Storage & Upgrade Packages
Open the **Termux** app and execute the following commands to configure permissions and download core repositories:

```bash
# 1. Grant Termux access to your device's internal storage
termux-setup-storage

# 2. Upgrade the package list and core utilities
pkg upgrade -y
```

---

### Step 3: Map Termux inside MT Manager
To navigate and edit files in your Termux home directory using MT Manager's dual-panel GUI:
1. Open the **MT Manager** app.
2. Open the side menu and click on **Add Local Storage** (or **Virtual SD Card**).
3. Select **Termux** as the folder location and grant permission.
4. You can now easily copy files, edit `.bashrc`, and browse projects between your regular phone storage and the Termux home directory!

---

### Step 4: Install Java Development Kit
App Inventor 2 extensions are compiled into JVM bytecode. Install OpenJDK 17 on Termux by running:
```bash
pkg install openjdk-17 -y
```

---

### Step 5: Choose Your Installation Method

You can choose either the fully automated setup script (recommended) or the manual configuration.

#### Option A: Automated Configuration (Recommended) ⚡
Run this command to automatically fetch, compile the native binary for your device's architecture, configure your environment files, and sync necessary build systems:

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

*What the installer does under the hood:*
* Verifies/installs dependencies (`git`, `dart`, `openjdk-17`, `unzip`, `curl`).
* Compiles Bolt CLI natively on your device's CPU architecture for absolute peak performance.
* Automatically appends paths to your `.bashrc` or `.zshrc`.
* Prompts you to automatically download the 170 MB App Inventor Java libraries.

#### Option B: Manual Setup 🛠️
If you skipped the automated script or want to configure it yourself:
1. Extract/download the latest `rush` packages and resources to `$HOME/.rush`.
2. Open your shell profile file (e.g. `.bashrc` or `.zshrc`) inside MT Manager's text editor.
3. Paste the following configuration lines at the bottom of the file:

```bash
# Bolt CLI Environment Configuration
export BOLT_HOME="$HOME/.rush"
export PATH="$PATH:$BOLT_HOME/bin"

# Quick-launch wrapper function
bolt() {
    "$BOLT_HOME/bin/bolt" "$@"
}
```
4. Save the file, restart Termux (or run `source ~/.bashrc`), and verify the command by typing:
   ```bash
   bolt -v
   ```

---

## 📂 Visual Architecture: Workspace Graph Tree

Here is how the Bolt CLI environment organizes itself on your system. Understanding this helps you manage libraries and packages effectively:

```text
$HOME/ (Termux Home / User Directory)
├── .bolt/                                <-- BOLT_HOME (Default Installation root)
│   ├── bin/
│   │   └── rush                          <-- Natively compiled executable (No JVM!)
│   └── libs/
│       └── tools/
│           ├── annotations.jar           <-- Extension annotations library
│           ├── deps.json                 <-- Dependency definitions cache
│           ├── android.jar               <-- Android Platform APIs reference
│           └── ... (App Inventor Java compilation tools, approx. 170MB)
│
├── .bashrc (or .zshrc)                    <-- Shell settings with Rush environment variables
│
└── storage/
    └── shared/                           <-- Symlink to your phone's internal storage
        └── Documents/
            └── RushProjects/             <-- Recommended location for project folders
```

---

## 🛠️ Usage Example

Once the setup is complete, you can build an extension directly on your phone:

```bash
# 1. Navigate to your projects directory (internal storage)
cd ~/storage/shared/Documents/RushProjects

# 2. Scaffold a brand-new extension
bolt create MyNewExtension

# 3. Enter the project folder
cd MyNewExtension

# 4. Sync project dependencies
bolt deps sync

# 5. Compile and build the extension
bolt build
```
Your compiled extension bundle (`.aix` file) will be waiting in the `out/` directory, ready to be uploaded to App Inventor, Kodular, or Niotron!

---

## 🐧 Linux Native Build Instructions

If you are running on a Linux desktop, laptop, or cloud server, you can compile Bolt CLI to a standalone native binary for your target system using these commands:

### Prerequisites
Make sure the Dart SDK is installed on your Linux system:
```bash
# For Debian/Ubuntu
sudo apt-get update
sudo apt-get install dart
```

### Build Commands
Run these commands from the root directory of your cloned `rush-cli` repository:
```bash
# 1. Fetch Dart dependencies
dart pub get

# 2. Generate build runner files
dart run build_runner build --delete-conflicting-outputs

# 3. Compile to native Linux binary (x86_64 or aarch64 depending on your CPU)
dart compile exe -o build/bin/bolt bin/bolt.dart

# 4. Make the binary executable
chmod +x build/bin/bolt
```
This produces a self-contained executable at `build/bin/bolt` which has **zero external runtime dependencies**!

---

## ❓ FAQ (Frequently Asked Questions)

### **Q1: Why is there no `bolt.jar` like `fast-cli`'s `fast.jar`?**
**A:** **This is a deliberate architectural advantage!** 
* **`fast-cli`** is a Java/Kotlin program, which compiles to JVM bytecode and runs through `java -jar fast.jar`. This means every single command has to spin up the Java Virtual Machine, causing startup delays and using more CPU memory.
* **`rush-cli`** is written in **Dart**, which compiles directly to native machine assembly language (native ELF/EXE binary).
* Because of this, `rush` executes **instantly** (0ms startup latency) and compiles extensions significantly faster, with no JVM container wrapping. You do not need to prefix commands with `java -jar`; you run `rush` natively!

### **Q2: When running `bolt build`, I get an "Unsupported JRE" or "Java compiler not found" warning.**
**A:** Ensure OpenJDK 17 is installed correctly on your system. Run `java -version` and `javac -version` in Termux. If it is missing, run `pkg install openjdk-17 -y`.

### **Q3: I updated my `.bashrc` or `.zshrc` manually, but Termux says `command not found: rush`.**
**A:** After modifying your profile files, the active shell session does not automatically know about the changes. You must reload it. Run:
```bash
source ~/.bashrc
```
(Or `source ~/.zshrc` if using ZSH). Alternatively, simply close the Termux app completely and re-open it.

### **Q4: How do I sync dependencies if I skipped the prompt during installation?**
**A:** You can trigger the download manually at any time by running:
```bash
bolt deps sync --dev-deps
```
This downloads the required platform and compiler support JARs (~170 MB) and places them in your `$BOLT_HOME/libs/` directory.

### **Q5: Can I edit extension source code directly on my Android device?**
**A:** Yes! You can use **MT Manager's built-in text editor** or install **Acode** (a modern, robust code editor for Android that supports tabs, syntax highlighting, and folder trees). Point it to your project folder in your storage directory!