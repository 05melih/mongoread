#!/bin/bash
set -e

ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BUILD_TOOLS="/usr/lib/android-sdk/build-tools/debian"
AAPT="$BUILD_TOOLS/aapt"
DX="$BUILD_TOOLS/dx"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"

SRC="src"
RES="res"
MANIFEST="AndroidManifest.xml"

BUILD="build"
GEN="$BUILD/gen"
OBJ="$BUILD/obj"
DIST="$BUILD/dist"

rm -rf "$BUILD"
mkdir -p "$GEN" "$OBJ" "$DIST"

echo "==> Generating R.java..."
$AAPT package -f -m -J "$GEN" -M "$MANIFEST" -S "$RES" -I "$ANDROID_JAR"

echo "==> Compiling Java sources..."
find "$SRC" "$GEN" -name "*.java" > "$BUILD/sources.txt"
javac -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OBJ" \
    @"$BUILD/sources.txt"

echo "==> Converting to DEX..."
$DX --dex --output="$BUILD/classes.dex" "$OBJ"

echo "==> Packaging APK..."
$AAPT package -f -M "$MANIFEST" -S "$RES" -I "$ANDROID_JAR" \
    -F "$DIST/clarinet-unsigned.apk"
cd "$BUILD" && zip -0 -j "$OLDPWD/$DIST/clarinet-unsigned.apk" classes.dex && cd "$OLDPWD"

echo "==> Generating keystore..."
keytool -genkey -v -keystore "$BUILD/debug.keystore" \
    -alias debug -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, S=Debug, C=US" \
    -storepass android -keypass android -noprompt 2>/dev/null

echo "==> Zipaligning APK..."
$ZIPALIGN -f 4 "$DIST/clarinet-unsigned.apk" "$DIST/clarinet-aligned.apk"

echo "==> Signing APK (v1+v2+v3)..."
$APKSIGNER sign \
    --ks "$BUILD/debug.keystore" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias debug \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --min-sdk-version 21 \
    --out "$DIST/Klarnet.apk" \
    "$DIST/clarinet-aligned.apk"

echo ""
echo "====================================="
echo " APK HAZIR: $DIST/Klarnet.apk"
SIZE=$(wc -c < "$DIST/Klarnet.apk")
echo " Boyut: $SIZE byte"
echo "====================================="
