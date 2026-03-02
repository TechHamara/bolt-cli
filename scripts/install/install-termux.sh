#!/bin/bash

# Bolt CLI Installation Script for Termux
# This script automatically downloads, builds, and installs Bolt CLI

set -e

echo "=========================================="
echo "  Bolt CLI - Termux Installation Script"
echo "=========================================="
echo ""

# Check if we're in Termux
if [ ! -d "$PREFIX" ]; then
  echo "❌ Error: This script must be run in Termux environment."
  echo "   Please open Termux and try again."
  exit 1
fi

echo "✅ Termux environment detected!"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
if ! command -v git &> /dev/null; then
  echo "❌ Git is not installed. Installing git..."
  pkg install -y git
fi

if ! command -v dart &> /dev/null; then
  echo "❌ Dart is not installed. Installing dart..."
  pkg install -y dart
fi

if ! command -v javac &> /dev/null; then
  echo "❌ OpenJDK 17 is not installed. Installing openjdk-17..."
  pkg install -y openjdk-17
fi

echo "✅ All prerequisites are installed!"
echo ""

BOLT_HOME="$HOME/.bolt"
mkdir -p "$BOLT_HOME/bin"

echo "Downloading Bolt CLI source code..."
echo "Repository: https://github.com/TechHamara/bolt-cli.git"
temp_dir=$(mktemp -d)
echo "Destination: $temp_dir"

if git clone https://github.com/TechHamara/bolt-cli.git "$temp_dir"; then
  echo "✅ Repository cloned successfully!"
else
  echo "❌ Failed to clone repository"
  exit 1
fi

cd "$temp_dir"
echo ""

echo "Fetching Dart dependencies..."
if dart pub get; then
  echo "✅ Dependencies downloaded successfully!"
else
  echo "❌ Failed to download dependencies"
  cd "$HOME"
  rm -rf "$temp_dir"
  exit 1
fi

echo ""
echo "Generating build configurations..."
if dart run build_runner build --delete-conflicting-outputs; then
  echo "✅ Build configurations generated successfully!"
else
  echo "❌ Failed to generate build configurations"
  cd "$HOME"
  rm -rf "$temp_dir"
  exit 1
fi

echo ""
echo "Compiling Bolt CLI..."
version=$(grep '^version: ' pubspec.yaml | cut -d ' ' -f 2 | tr -d '\r')
chmod +x scripts/build.sh
if ./scripts/build.sh -v "$version"; then
  echo "✅ Compilation completed successfully!"
else
  echo "❌ Compilation failed"
  cd "$HOME"
  rm -rf "$temp_dir"
  exit 1
fi

echo ""
echo "Installing Bolt binary..."
cp build/bin/bolt "$BOLT_HOME/bin/bolt"
chmod +x "$BOLT_HOME/bin/bolt"
echo "✅ Bolt installed at $BOLT_HOME/bin/bolt"

echo ""
cd "$HOME"
rm -rf "$temp_dir"

# Add to PATH in .bashrc
echo "Configuring shell environment..."
SHELL_PROFILE="$HOME/.bashrc"
if [ -n "$ZSH_VERSION" ]; then
  SHELL_PROFILE="$HOME/.zshrc"
fi

# Check if PATH is already in profile
if ! grep -q "BOLT_HOME" "$SHELL_PROFILE" 2>/dev/null; then
  cat >> "$SHELL_PROFILE" << 'EOF'

# Bolt CLI
export BOLT_HOME="$HOME/.bolt"
export PATH="$PATH:$BOLT_HOME/bin"

EOF
  echo "✅ Added Bolt to $SHELL_PROFILE"
else
  echo "✅ Bolt is already in $SHELL_PROFILE"
fi

echo ""
echo "=========================================="
echo "  ✅ Installation Successful!"
echo "=========================================="
echo ""
echo "📝 Next Steps:"
echo "   1. Close Termux completely (swipe it away)"
echo "   2. Wait a few seconds"
echo "   3. Reopen Termux from your app drawer"
echo "   4. Run: bolt -v"
echo "   5. Run: bolt deps sync --dev-deps"
echo ""
echo "Questions?"
read -p "Do you want to download Java dependencies now? (Y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  echo ""
  echo "Downloading Java dependencies (this may take a few minutes)..."
  "$BOLT_HOME/bin/bolt" deps sync --dev-deps --no-logo
  echo "✅ Java dependencies downloaded successfully!"
else
  echo ""
  echo "⏭️  Skipped. You can download them later by running:"
  echo "   bolt deps sync --dev-deps"
fi

echo ""
echo "=========================================="
echo "  📱 Ready to Build Extensions!"
echo "=========================================="
echo ""
echo "Commands to get started:"
echo "   bolt create MyExtension"
echo "   cd MyExtension"
echo "   bolt build"
echo ""
echo "For more help, run: bolt --help"
echo ""
