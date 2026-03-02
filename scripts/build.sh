#!/bin/bash

# Exit immediately if any commands exit with non-zero exit status.
set -e

while (( "$#" )); do
  case "$1" in
    "-v" | "--version")
      version="$2"
      shift 2 ;;
    *)
      echo "error: Unknown argument: $1"
      exit 1 ;;
  esac
done

# Write version.dart file
function writeVersionDart() {
  file='./lib/version.dart'

  printf "// Auto-generated; DO NOT modify\n" > $file
  printf "const boltVersion = '%s';\n" "$version" >> $file
  printf "const boltBuiltOn = '%s';\n" "$(date '+%Y-%m-%d %H:%M:%S')" >> $file

  echo 'Generated lib/version.dart'
}
writeVersionDart

if [ ! -d "build/bin" ]; then
  mkdir -p "build/bin"
fi

if [ "$OS" = "Windows_NT" ]; then
  ext=".exe"
else
  ext=""
fi

# Compile Bolt executable
dart compile exe -o build/bin/bolt"$ext" bin/bolt.dart
chmod +x build/bin/bolt
