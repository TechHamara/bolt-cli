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
  target="bolt-win"
else
  case $(uname -sm) in
  "Darwin x86_64") target="x86_64-apple-darwin" ;;
  "Darwin arm64") target="arm64-apple-darwin" ;;
  *) target="linux" ;;
  esac
fi

zipUrl="https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt-$target.zip"
echo "Downloading Bolt CLI..."
curl --location --progress-bar -w "\nDownload completed!\n" -o "$boltHome/bolt-$target.zip" "$zipUrl"

echo "Extracting files..."
unzip -oq "$boltHome/bolt-$target.zip" -d "$boltHome"/
echo "Extraction completed!"
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
    "./"$boltHome"/bin/bolt.exe" deps sync --dev-deps --no-logo
  else
    "./"$boltHome"/bin/bolt" deps sync --dev-deps --no-logo
  fi
fi

echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  echo "Success! Installed Bolt at $boltHome/bin/bolt"
else
  echo "Bolt has been partially installed at $boltHome/bin/bolt"
  echo 'Please run `bolt deps sync --dev-deps` to download the necessary Java libraries.'
fi

case $SHELL in
  /bin/zsh) shell_profile=".zshrc" ;;
  *) shell_profile=".bash_profile" ;;
esac

echo
echo "Now, add the following to your \$HOME/$shell_profile (or similar):"
echo "export PATH=\"\$PATH:$boltHome/bin\""

echo
echo 'Run `bolt --help` to get started.'
