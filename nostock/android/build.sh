#!/usr/bin/env bash
# 构建戒股 APK（内嵌全部离线资源）
set -euo pipefail

PROJ="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$PROJ/android"
APP_NAME="jiegu"
PKG="com.nostock"
VERSION_NAME="1.0.0"
VERSION_CODE=1
OUT="$PROJ/${APP_NAME}-v${VERSION_NAME}.apk"

# —— SDK 路径 ——
SDK="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
if [ ! -d "$SDK/platforms" ]; then
  SDK="$HOME/Android/Sdk"
fi
if [ ! -d "$SDK/platforms" ]; then
  SDK="$HOME/.android-sdk"
fi
if [ ! -d "$SDK/platforms" ]; then
  echo "找不到 Android SDK（已尝试 /tmp/android-sdk, $HOME/Android/Sdk, $HOME/.android-sdk）" >&2
  exit 1
fi
BT=$(ls -d "$SDK/build-tools"/* 2>/dev/null | sort -V | tail -1)
PLATFORM="$SDK/platforms/android-34/android.jar"
if [ ! -f "$PLATFORM" ]; then
  PLATFORM=$(find "$SDK/platforms" -name 'android.jar' 2>/dev/null | sort -V | tail -1)
fi

# —— JDK（优先 11，避免 d8 崩溃）——
J11="$(mise where java@11 2>/dev/null || mise where java@11.0.32+101 2>/dev/null || true)"
if [ -z "$J11" ] || [ ! -x "$J11/bin/javac" ]; then
  J11=$(ls -d /root/.pyenv/versions/*/bin 2>/dev/null | head -1 || true)
fi
if [ -n "$J11" ] && [ -x "$J11/bin/javac" ]; then
  export JAVA_HOME="${J11%/bin}"
  echo "用 JDK 11: $JAVA_HOME"
else
  JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "javac: $(javac -version)"

# —— 签名密钥 ——
KS="$ANDROID/release.keystore"
KS_ALIAS="release"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass jiegu2026 -keypass jiegu2026 \
    -dname "CN=戒股, O=戒股, L=BJ, ST=BJ, C=CN" 2>&1 | tail -3
fi

# —— 构建目录 ——
BUILD="$(mktemp -d)"
trap "rm -rf '$BUILD'" EXIT
mkdir -p "$BUILD/assets" "$BUILD/res" "$BUILD/classes" "$BUILD/dex" "$BUILD/manifests"

# —— 复制前端资源到 assets ——
cp "$PROJ/index.html" "$BUILD/assets/"
cp "$PROJ/app.js" "$BUILD/assets/"
cp "$PROJ/styles.css" "$BUILD/assets/"
cp "$PROJ/manifest.webmanifest" "$BUILD/assets/"
cp "$PROJ/sw.js" "$BUILD/assets/"
cp -r "$PROJ/icons" "$BUILD/assets/icons"

# —— 1) aapt2 compile ——
echo "== aapt2 compile =="
$BT/aapt2 compile --dir "$ANDROID/res" -o "$BUILD/res.zip" 2>&1 | tail -5

# —— 2) aapt2 link ——
echo "== aapt2 link =="
$BT/aapt2 link \
  -I "$PLATFORM" \
  --manifest "$ANDROID/AndroidManifest.xml" \
  -o "$BUILD/base-unsigned.apk" \
  "$BUILD/res.zip" \
  -0 arsc \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  -A "$BUILD/assets" \
  2>&1 | tail -5

# —— 3) javac ——
echo "== javac =="
javac -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$PLATFORM" \
  -d "$BUILD/classes" \
  $(find "$ANDROID/java" -name '*.java') 2>&1 | tail -10

# —— 4) d8 ——
echo "== d8 =="
$BT/d8 --min-api 24 --lib "$PLATFORM" --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class') 2>&1 | tail -5

# —— 5) 组装 dex 进 APK ——
echo "== assemble =="
cp "$BUILD/base-unsigned.apk" "$BUILD/base.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/base.apk" classes.dex)

# —— 6) zipalign ——
echo "== zipalign =="
$BT/zipalign -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

# —— 7) apksigner ——
echo "== apksigner =="
$BT/apksigner sign \
  --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass pass:jiegu2026 --key-pass pass:jiegu2026 \
  --out "$OUT" "$BUILD/aligned.apk"

ls -lh "$OUT"
$BT/apksigner verify --verbose "$OUT" 2>&1 | tail -5
echo "✅ 完成: $OUT"
