# 📱 Bolt CLI: Termux Installation Guide

Developing MIT App Inventor 2 extensions directly on your Android device is fully supported with Bolt CLI and **Termux**. This guide will walk you through the process of setting up a complete development environment on your smartphone.

---

## ⚠️ Prerequisites

Before installing Bolt, ensure you have the following Android applications installed:

1. **[Termux](https://github.com/termux/termux-app/releases/latest)**: The terminal emulator and Linux environment app (Download from GitHub or F-Droid, *not* the Google Play Store).
2. **A Code Editor**: We recommend [Acode](https://github.com/deadlyjack/Acode/releases) or any other preferred code/text editor.
3. **A File Manager**: Applications like [MT Manager](https://mt2.cn/download/) are highly recommended for managing your extension files effectively.

---

## ⚙️ Setup Process

Follow these steps precisely **in order** inside the Termux app to install Bolt CLI correctly.

### Step 1: Grant Storage Permission

Open the **Termux** app and grant it access to your device's internal storage. Run the following command and accept the Android permission prompt when asked:

```bash
termux-setup-storage
```

**Expected Output:**
```
termux-setup-storage
Allow Termux to access files on your device? [Allow/Deny]
```

---

### Step 2: Update and Upgrade Packages

Update your package manager and upgrade existing packages. Run:

```bash
pkg upgrade -y
```

This will update all Termux packages to the latest versions. *(This may take a few minutes)*

**Expected Output:**
```
Reading package lists... Done
Calculating the upgrade... Done
Installing updates... Done
```

---

### Step 3: Add Termux as Local Storage in MT Manager (Optional)

If you want to use **MT Manager** as your file manager:
1. Open **MT Manager** app
2. Go to settings and add Termux as a local storage location
3. This allows easy access to your Termux files and projects

---

### Step 4: Install OpenJDK 17

Install Java Development Kit (required for building extensions):

```bash
pkg install openjdk-17 -y
```

**Expected Output:**
```
Reading package lists... Done
Setting up openjdk-17...
Done!
```

---

### Step 5: Install Git (Required for downloading Bolt)

Install Git to clone the Bolt repository:

```bash
pkg install git -y
```

---

### Step 6: Automatic Bolt Installation (Recommended)

Download and install **Bolt CLI** automatically using the installation script:

```bash
curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install-termux.sh -fsSL | bash
```

**What this script does:**
- ✅ Downloads Bolt CLI source code
- ✅ Installs Dart dependencies
- ✅ Compiles Bolt for your Android device
- ✅ Sets up the `bolt` command in your shell
- ✅ Optionally downloads Java libraries (170 MB)

**Expected Output:**
```
Starting Bolt CLI installation for Termux...
Cloning Bolt CLI repository to build from source...
Repository URL: https://github.com/TechHamara/bolt-cli.git
Repository cloned successfully!

Fetching Dart dependencies...
Dependencies downloaded successfully!

Generating build configurations...
Build configurations generated successfully!

Compiling Bolt CLI...
Compilation completed successfully!

Moving compiled binary to $HOME/.bolt/bin...

Success! Installed Bolt at $HOME/.bolt/bin/bolt
```

---

### Step 7: Download Java Dependencies (Recommended)

If the installation script asks, proceed with downloading necessary Java libraries (~170 MB):

```
Do you want to continue? (Y/n) Y
```

The script will automatically run:
```bash
bolt deps sync --dev-deps --no-logo
```

This downloads essential Android build tools, SDK, and libraries needed to compile extensions.

---

### Step 8: Close and Reopen Termux

**This is important!** Close the Termux app completely and reopen it. This ensures the environment variables are properly loaded.

1. Close Termux (slide it away or use back button)
2. Wait a few seconds
3. Reopen Termux from your app drawer

---

### Step 9: Verify Installation

Check that Bolt is installed correctly by running:

```bash
bolt -v
```

**Expected Output:**
```
2.0.0
```

You should see the version number of Bolt CLI printed on the screen.

---

### Step 10: Verify Environment Setup

Confirm that the Bolt binary location is in your PATH:

```bash
which bolt
```

**Expected Output:**
```
/data/data/com.termux/files/home/.bolt/bin/bolt
```

---

## 🎉 You're All Set!

Congratulations! You've successfully installed Bolt CLI on Termux. You can now navigate to any folder on your internal storage and start building extensions:

```bash
# Navigate to your projects folder
cd ~/storage/shared/Documents

# Create a new extension
bolt create MyExtension

# Navigate into your project
cd MyExtension

# Build the extension
bolt build
```

---

## 📋 Troubleshooting

### Issue: `bolt: command not found`
**Solution:** Close and reopen Termux as described in Step 8. The environment variables need to be reloaded.

### Issue: Storage permission denied
**Solution:** Run `termux-setup-storage` again and ensure you grant the permission when prompted.

### Issue: OpenJDK 17 installation fails
**Solution:** Try running `pkg update -y` first, then retry `pkg install openjdk-17 -y`.

### Issue: Dart dependency download fails
**Solution:** Ensure you have a stable internet connection and run:
```bash
dart pub cache clean
dart pub get
```

---

## 🚀 Next Steps

- Check out the [Bolt CLI Documentation](https://github.com/TechHamara/bolt-cli) for more commands
- Build your first extension with `bolt create`
- Test your extensions on MIT App Inventor 2

Happy coding on the go! 📱✨