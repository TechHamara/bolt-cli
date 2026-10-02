#!/bin/bash

# Exit immediately if any command exits with non-zero exit status.
set -e

# ANSI Color Codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

info() {
  echo -e "${BLUE}[ ]${NC} $1"
}

success() {
  echo -e "${GREEN}[✓]${NC} ${BOLD}$1${NC}"
}

warn() {
  echo -e "${YELLOW}[!]${NC} $1"
}

error() {
  echo -e "${RED}[✗]${NC} ${BOLD}$1${NC}"
}

clear
echo -e "${CYAN}${BOLD}"
echo "=========================================================="
echo "      BOLT CLI - Termux Universal Native Installer        "
echo "=========================================================="
echo -e "${NC}"

info "Starting Bolt CLI installation for Android Termux..."

# Argument parsing
SKIP_JAVA=false
CUSTOM_PATH=""

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-java|--no-jdk)
      SKIP_JAVA=true
      shift
      ;;
    --path|-p)
      CUSTOM_PATH="$2"
      shift 2
      ;;
    -*)
      shift
      ;;
    *)
      if [ -z "$CUSTOM_PATH" ]; then
        CUSTOM_PATH="$1"
      fi
      shift
      ;;
  esac
done

if [ -n "$CUSTOM_PATH" ]; then
  BOLT_HOME="$CUSTOM_PATH"
elif [ -n "$BOLT_HOME" ]; then
  BOLT_HOME="$BOLT_HOME"
else
  BOLT_HOME="$HOME/Bolt"
fi

# 1. Update packages
info "Updating package lists..."
pkg update -y

# 2. Dependency checks
if [ "$SKIP_JAVA" = true ]; then
  info "Skipping Java installation as requested (--skip-java). Installing unzip & curl..."
  pkg install -y unzip curl
elif command -v java >/dev/null 2>&1; then
  info "Java runtime is already installed! Installing remaining dependencies (unzip, curl)..."
  pkg install -y unzip curl
else
  info "Installing required dependencies (openjdk-17, unzip, curl)..."
  pkg install -y openjdk-17 unzip curl
fi

mkdir -p "$BOLT_HOME/bin"

# 3. Download and Install Universal Package
info "Downloading Universal Bolt CLI package..."
zipUrl="https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt.zip"
curl --location --progress-bar -o "$BOLT_HOME/bolt.zip" "$zipUrl"

info "Extracting Bolt CLI..."
unzip -oq "$BOLT_HOME/bolt.zip" -d "$BOLT_HOME"/
rm "$BOLT_HOME/bolt.zip"

chmod +x "$BOLT_HOME/bin/bolt" 2>/dev/null || true

success "Installed Bolt CLI successfully at $BOLT_HOME/bin/bolt!"

# 4. Environment configuration & Shell profile auto-injection
shell_profile=".bashrc"
if [ -n "$ZSH_VERSION" ]; then
  shell_profile=".zshrc"
elif [ -f "$HOME/.zshrc" ]; then
  shell_profile=".zshrc"
elif [ -f "$HOME/.bashrc" ]; then
  shell_profile=".bashrc"
fi

info "Detecting shell environment... Found $shell_profile"
touch "$HOME/$shell_profile"

if ! grep -q "BOLT_HOME=" "$HOME/$shell_profile" 2>/dev/null; then
  echo -e "\n# Bolt CLI Configuration" >> "$HOME/$shell_profile"
  echo "export BOLT_HOME=\"$BOLT_HOME\"" >> "$HOME/$shell_profile"
  echo "export PATH=\"\$PATH:\$BOLT_HOME/bin\"" >> "$HOME/$shell_profile"
  echo -e "bolt() {\n    \"\$BOLT_HOME/bin/bolt\" \"\$@\"\n}" >> "$HOME/$shell_profile"
  success "Automatically updated your $shell_profile with BOLT_HOME, PATH, and bolt() function!"
else
  info "Bolt configuration already exists in $shell_profile. Skipping auto-injection."
fi

echo -e "\n${GREEN}${BOLD}==========================================================${NC}"
success "Setup Complete! Enjoy development with Universal Bolt CLI on Termux."
info "Please run:  source ~/$shell_profile  (or open a new terminal session)"
info "To verify, run:  bolt -v"
echo -e "${GREEN}${BOLD}==========================================================${NC}\n"
