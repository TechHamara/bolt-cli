#!/bin/sh

# Exit immediately if any command exits with non-zero exit status.
set -e

# optionally provide the installation directory as the first argument
if [ -n "$1" ]; then
  boltHome="$1"
elif [ -n "$BOLT_HOME" ]; then
  boltHome="$BOLT_HOME"
else
  if ! command -v bolt >/dev/null 2>&1; then
    boltHome="$HOME/.bolt"
  else
    boltHome="$(dirname $(dirname $(which bolt)))"
  fi
fi

# ensure the directory exists
mkdir -p "$boltHome"

if [ "$OS" = "Windows_NT" ]; then
  target="win"
else
  case $(uname -sm) in
  Darwin*) target="mac" ;;
  *) target="linux" ;;
  esac
fi

zipUrl="https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt-$target.zip"
curl --location --progress-bar -o "$boltHome/bolt-$target.zip" "$zipUrl"

unzip -oq "$boltHome/bolt-$target.zip" -d "$boltHome"/
rm "$boltHome/bolt-$target.zip"

# Make the Bolt binary executable on Unix systems. 
if [ ! "$OS" = "Windows_NT" ]; then
  chmod +x "$boltHome/bin/bolt"
fi

echo
echo "Successfully downloaded the Bolt CLI binary at $boltHome/bin/bolt"

# Prompt user of they want to download dev dependencies now.
echo "Now, proceeding to download necessary Java libraries (approx size: 170 MB)."
read -p "Do you want to continue? (Y/n) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  if [ "$OS" = "Windows_NT" ]; then
    "./$boltHome/bin/bolt.exe" deps sync --dev-deps --no-logo
  else
    "./$boltHome/bin/bolt" deps sync --dev-deps --no-logo
  fi
fi

echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  echo "Success! Installed Bolt CLI at $boltHome/bin/bolt"
else
  echo "Bolt CLI has been partially installed at $boltHome/bin/bolt"
  echo 'Please run `bolt deps sync --dev-deps` to download the necessary Java libraries.'
fi

shell_profile=".bashrc"
case "$SHELL" in
  */zsh) shell_profile=".zshrc" ;;
  */bash)
    if [ "$(uname -sm | grep -c Darwin)" -gt 0 ]; then
      shell_profile=".bash_profile"
    else
      shell_profile=".bashrc"
    fi
    ;;
  *)
    if [ -f "$HOME/.zshrc" ]; then
      shell_profile=".zshrc"
    elif [ -f "$HOME/.bash_profile" ] && [ "$(uname -sm | grep -c Darwin)" -gt 0 ]; then
      shell_profile=".bash_profile"
    elif [ -f "$HOME/.bashrc" ]; then
      shell_profile=".bashrc"
    fi
    ;;
esac

touch "$HOME/$shell_profile"

if ! grep -q "BOLT_HOME=" "$HOME/$shell_profile" 2>/dev/null; then
  echo "" >> "$HOME/$shell_profile"
  echo "# Bolt CLI Configuration" >> "$HOME/$shell_profile"
  echo "export BOLT_HOME=\"$boltHome\"" >> "$HOME/$shell_profile"
  echo "export PATH=\"\$PATH:\$BOLT_HOME/bin\"" >> "$HOME/$shell_profile"
  echo
  echo "Successfully updated your $shell_profile with BOLT_HOME and PATH."
  echo "Please run:  source ~/$shell_profile  (or open a new terminal session)"
else
  echo
  echo "Bolt CLI configuration already exists in $shell_profile. Skipping auto-injection."
fi

echo
echo 'Run `bolt --help` to get started.'
