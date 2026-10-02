#!/bin/sh

# Exit immediately if any command exits with non-zero exit status.
set -e

skipJava=false
boltHome=""

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-java|--no-jdk)
      skipJava=true
      shift
      ;;
    --path|-p)
      boltHome="$2"
      shift 2
      ;;
    -*)
      shift
      ;;
    *)
      if [ -z "$boltHome" ]; then
        boltHome="$1"
      fi
      shift
      ;;
  esac
done

# optionally provide the installation directory as the first argument
if [ -z "$boltHome" ]; then
  if [ -n "$BOLT_HOME" ]; then
    boltHome="$BOLT_HOME"
  else
    if ! command -v bolt >/dev/null 2>&1; then
      boltHome="$HOME/Bolt"
    else
      boltHome="$(dirname $(dirname $(which bolt)))"
    fi
  fi
fi

# ensure directory exists
mkdir -p "$boltHome/bin"

zipUrl="https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt.zip"
echo "Downloading Universal Bolt CLI from $zipUrl..."
curl --location --progress-bar -o "$boltHome/bolt.zip" "$zipUrl"

unzip -oq "$boltHome/bolt.zip" -d "$boltHome"/
rm "$boltHome/bolt.zip"

# Make the Bolt binary executable
if [ -f "$boltHome/bin/bolt" ]; then
  chmod +x "$boltHome/bin/bolt"
fi

echo
echo "Successfully installed Bolt CLI at $boltHome/bin/bolt"

# Verify Java installation
if [ "$skipJava" = true ]; then
  echo "Skipping Java verification as requested (--skip-java)."
elif ! command -v java >/dev/null 2>&1; then
  echo "Warning: Java runtime (JRE/JDK 11 or later) is required to run Bolt CLI."
  echo "Note: Bolt bundles ECJ compiler (ecj.jar) for compiling Java, but requires Java runtime to execute."
  echo "Please install OpenJDK: sudo apt install openjdk-17-jdk (or brew install openjdk@17)"
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
