#!/bin/bash

# Exit immediately if any command exits with non-zero exit status.
set -e

# ANSI Color Codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Logging Helpers
info() {
  echo -e "${BLUE}[•]${NC} $1"
}

success() {
  echo -e "${GREEN}[✔]${NC} ${BOLD}$1${NC}"
}

warn() {
  echo -e "${YELLOW}[!]${NC} $1"
}

error() {
  echo -e "${RED}[✘]${NC} ${BOLD}$1${NC}"
}

clear
echo -e "${CYAN}${BOLD}"
echo "=========================================================="
echo "    🚀 BOLT CLI - Termux Advanced Native Installer 🚀     "
echo "=========================================================="
echo -e "${NC}"

info "Starting Bolt CLI installation for Android Termux..."

# 1. Update packages
info "Updating package lists and upgrading Termux resources..."
pkg update -y && pkg upgrade -y

# 2. Dependency checks
info "Installing required dependencies (openjdk-17, unzip, curl)..."
pkg install -y openjdk-17 unzip curl

BOLT_HOME="$HOME/.bolt"
mkdir -p "$BOLT_HOME/bin"

# 3. Download and Install Precompiled Binary
info "Downloading Bolt CLI precompiled package for Termux..."
zipUrl="https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt-termux.zip"
curl --location --progress-bar -o "$BOLT_HOME/bolt-termux.zip" "$zipUrl"

info "Extracting Bolt CLI..."
unzip -oq "$BOLT_HOME/bolt-termux.zip" -d "$BOLT_HOME"/
rm "$BOLT_HOME/bolt-termux.zip"

chmod +x "$BOLT_HOME/bin/bolt"

success "Installed Bolt CLI successfully at $BOLT_HOME/bin/bolt!"

# 4. Sync Java Dependencies
echo
info "Now, proceeding to download essential Java dependencies (approx. 170 MB)."
warn "These are required to compile App Inventor 2 extensions."
read -p "Do you want to download dependencies now? (Y/n) " -n 1 -r </dev/tty
echo

if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  info "Downloading and syncing libraries (this may take a moment)..."
  "$BOLT_HOME/bin/bolt" deps sync --dev-deps --no-logo
  success "Java dependency synchronization completed!"
else
  warn "Skipped dependency synchronization. You must run 'bolt deps sync --dev-deps' later to build extensions."
fi

# 5. Environment configuration & Shell profile auto-injection
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

# Inject variables if not already set
if ! grep -q "BOLT_HOME=" "$HOME/$shell_profile" 2>/dev/null; then
  echo -e "\n# Bolt CLI Configuration" >> "$HOME/$shell_profile"
  echo "export BOLT_HOME=\"$BOLT_HOME\"" >> "$HOME/$shell_profile"
  echo "export PATH=\"\$PATH:\$BOLT_HOME/bin\"" >> "$HOME/$shell_profile"
  echo -e "bolt() {\n    \"\$BOLT_HOME/bin/bolt\" \"\$@\"\n}" >> "$HOME/$shell_profile"
  success "Automatically updated your $shell_profile with BOLT_HOME, PATH, and bolt() function!"
else
  info "Rush configuration already exists in $shell_profile. Skipping auto-injection."
fi

echo -e "\n${GREEN}${BOLD}==========================================================${NC}"
success "Setup Complete! Enjoy development with Bolt CLI on Termux."
info "Please run:  source ~/$shell_profile  (or open a new terminal session)"
info "To verify, run:  bolt -v"
echo -e "${GREEN}${BOLD}==========================================================${NC}\n"
