#!/bin/bash
set -e

ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BT="/usr/lib/android-sdk/build-tools/debian"
AAPT2="$BT/aapt2"
AAPT="$BT/aapt"
DX="$BT/dx"
APKSIGNER="$BT/apksigner"
ZIPALIGN="$BT/zipalign"

KEYSTORE="keystore/release.keystore"
KEY_ALIAS="klarnet"
KEY_PASS="klarnet123"

SRC="src"
MANIFEST="AndroidManifest.xml"

BUILD="build"
GEN="$BUILD/gen"
OBJ="$BUILD/obj"
RES_FLAT="$BUILD/res_flat"
DIST="$BUILD/dist"

rm -rf "$BUILD"
mkdir -p "$GEN" "$OBJ" "$RES_FLAT" "$DIST"

echo "==> Compiling resources with aapt2..."
for f in res/values/*.xml; do
    $AAPT2 compile "$f" -o "$RES_FLAT/"
done

echo "==> Linking resources..."
$AAPT2 link \
    --proto-format \
    -o "$BUILD/resources.pb" \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    --java "$GEN" \
    "$RES_FLAT"/*.flata 2>/dev/null || true

# Fall back to aapt if aapt2 proto-format fails
echo "==> Packaging resources with aapt..."
$AAPT package -f -m \
    -J "$GEN" \
    -M "$MANIFEST" \
    -S res \
    -I "$ANDROID_JAR" \
    -F "$DIST/resources.apk"

echo "==> Compiling Java..."
find "$SRC" "$GEN" -name "*.java" > "$BUILD/sources.txt"
javac -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OBJ" \
    @"$BUILD/sources.txt" 2>&1 | grep -v "^\(warning\|Note\)"

echo "==> Converting to DEX..."
$DX --dex --output="$BUILD/classes.dex" "$OBJ"

echo "==> Building unsigned APK..."
cp "$DIST/resources.apk" "$DIST/clarinet-unsigned.apk"
# Add DEX uncompressed (critical for Android 5+)
(cd "$BUILD" && zip -0 -j "$OLDPWD/$DIST/clarinet-unsigned.apk" classes.dex)

echo "==> Zipaligning..."
$ZIPALIGN -f 4 "$DIST/clarinet-unsigned.apk" "$DIST/clarinet-aligned.apk"

echo "==> Signing with persistent key (v1+v2+v3)..."
$APKSIGNER sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$KEY_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --min-sdk-version 21 \
    --out "$DIST/Klarnet.apk" \
    "$DIST/clarinet-aligned.apk"

echo "==> Verifying..."
$APKSIGNER verify --verbose "$DIST/Klarnet.apk" 2>&1 | grep "Verified using"

echo ""
echo "====================================="
echo " APK HAZIR: $DIST/Klarnet.apk"
ls -lh "$DIST/Klarnet.apk" | awk '{print " Boyut: " $5}'
echo "====================================="
