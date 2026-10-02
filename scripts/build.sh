#!/bin/bash
set -e

VERSION="${1:-2.0.0}"
echo "Building Universal Bolt CLI v$VERSION..."

./gradlew assembleDistribution

DIST_DIR="distribution"
BIN_DIR="$DIST_DIR/bin"
mkdir -p "$BIN_DIR"

cp "$DIST_DIR/bolt.jar" "$BIN_DIR/bolt.jar"
cp "$DIST_DIR/bolt.bat" "$BIN_DIR/bolt.bat"
cp "$DIST_DIR/bolt" "$BIN_DIR/bolt"
chmod +x "$BIN_DIR/bolt"

# Remove any icon inside bin or libs
rm -f "$BIN_DIR/icon.png"
rm -f "$DIST_DIR/libs/icon.png"
rm -f "$DIST_DIR/libs/tools/icon.png"
rm -f "$DIST_DIR/libs/tools/aidl/icon.png"

STAGING="$DIST_DIR/staging"
rm -rf "$STAGING"
mkdir -p "$STAGING/bin" "$STAGING/libs"

cp -r "$BIN_DIR"/* "$STAGING/bin/"
cp -r "$DIST_DIR/libs"/* "$STAGING/libs/"
[ -f "$DIST_DIR/icon.png" ] && cp "$DIST_DIR/icon.png" "$STAGING/icon.png"

rm -f "$STAGING/bin/icon.png"
rm -f "$STAGING/libs/icon.png"
rm -f "$STAGING/libs/tools/icon.png"
rm -f "$STAGING/libs/tools/aidl/icon.png"

echo "Compressing universal package bolt.zip (Fresh, with bundled Mini-NDK)..."
rm -f "bolt.zip" "bolt-universal.zip" "bolt-win.zip" "bolt-linux.zip" "bolt-mac.zip" "bolt-termux.zip"
(cd "$STAGING" && zip -rq "../../bolt.zip" .)
rm -rf "$STAGING"
echo "Created universal bolt.zip successfully!"

# InPlace update package update.zip (contains bin/ ONLY, NO icon.png)
UPDATE_STAGING="$DIST_DIR/update_staging"
rm -rf "$UPDATE_STAGING"
mkdir -p "$UPDATE_STAGING/bin"
cp -r "$BIN_DIR"/* "$UPDATE_STAGING/bin/"
rm -f "$UPDATE_STAGING/icon.png" "$UPDATE_STAGING/bin/icon.png"

rm -f "update.zip"
echo "Compressing lightweight update package update.zip (InPlace, bin only without icon.png)..."
(cd "$UPDATE_STAGING" && zip -rq "../../update.zip" .)
rm -rf "$UPDATE_STAGING"
echo "Created lightweight update.zip successfully (without icon.png)!"
echo "Build and packaging complete! (bolt.zip & update.zip)"
