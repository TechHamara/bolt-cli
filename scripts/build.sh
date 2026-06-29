#!/bin/bash

# Exit immediately if any commands exit with non-zero exit status.
set -euo pipefail

version="${VERSION:-}"

while (( "$#" )); do
  case "$1" in
    "-v" | "--version")
      if [ $# -lt 2 ]; then
        echo "error: missing value for $1"
        exit 1
      fi
      version="$2"
      shift 2 ;;
    *)
      echo "error: Unknown argument: $1"
      exit 1 ;;
  esac
done

if [ -z "$version" ] && [ -f "pubspec.yaml" ]; then
  version=$(awk '/^version:/ {print $2; exit}' pubspec.yaml)
fi

if [ -z "$version" ]; then
  version="0.0.0"
fi

# Write version.dart file
function writeVersionDart() {
  file='./lib/version.dart'

  printf "// Auto-generated; DO NOT modify\n" > "$file"
  printf "const boltVersion = '%s';\n" "$version" >> "$file"
  printf "const boltBuiltOn = '%s';\n" "$(date '+%Y-%m-%d %H:%M:%S')" >> "$file"

  echo 'Generated lib/version.dart'
}
writeVersionDart

mkdir -p "build/bin"

if [ "${OS:-}" = "Windows_NT" ]; then
  ext=".exe"
else
  ext=""
fi

dart pub get
# Compile Bolt executable
dart compile exe -o "build/bin/bolt$ext" bin/bolt.dart

if [ "${OS:-}" != "Windows_NT" ]; then
  chmod +x "build/bin/bolt$ext"
fi
