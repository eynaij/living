#!/usr/bin/env bash
# 羽姿 APK 构建脚本
# 依赖：JDK 17、Android SDK（platforms;android-34 + build-tools;34.0.0）
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
WEB="$(cd "$PROJ/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
BUILD="${BUILD_DIR:-/tmp/yuzi-apk-build}"
OUT="$WEB/yuzi-v2.0.0.apk"

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(mise where java@17.0.20+101 2>/dev/null || true)"
fi
# 优先用 JDK 11 编译：JDK 17 会生成 MethodParameters，旧版 d8 会崩溃
J11="$(mise where java@11.0.32+101 2>/dev/null || true)"
if [ -n "$J11" ] && [ -x "$J11/bin/javac" ]; then
  JAVA_HOME="$J11"
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "需要 JDK 11/17：请设置 JAVA_HOME" >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

for f in "$BT/aapt2" "$BT/d8" "$BT/apksigner" "$BT/zipalign" "$PLATFORM"; do
  if [ ! -e "$f" ]; then
    echo "缺少构建工具：$f" >&2
    exit 1
  fi
done

rm -rf "$BUILD"
mkdir -p "$BUILD/assets" "$BUILD/classes" "$BUILD/gen" "$BUILD/dex"

# 复制离线 Web 资源
cd "$WEB"
for item in index.html styles.css app.js manifest.webmanifest sw.js icons models vendor; do
  cp -R "$item" "$BUILD/assets/"
done

# 编译资源并链接基础 APK
"$BT/aapt2" compile --dir "$PROJ/res" -o "$BUILD/res.zip"
"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -A "$BUILD/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 2.0.0 \
  "$BUILD/res.zip"

# 编译 Java 并转换为 dex
javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$PLATFORM" \
  -d "$BUILD/classes" $(find "$PROJ/java" -name '*.java')
"$BT/d8" --min-api 24 --lib "$PLATFORM" --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')

# 组装、对齐、签名
cd "$BUILD/dex"
zip -q "$BUILD/base.apk" classes.dex
cd "$BUILD"
"$BT/zipalign" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

KS="$PROJ/yuzi-release.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair \
    -keystore "$KS" -alias yuzi \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass yuzi2026 -keypass yuzi2026 \
    -dname "CN=Yuzi, OU=Mobile, O=Yuzi, L=Shanghai, C=CN" >/dev/null 2>&1
fi

"$BT/apksigner" sign \
  --ks "$KS" --ks-key-alias yuzi \
  --ks-pass pass:yuzi2026 --key-pass pass:yuzi2026 \
  --out "$OUT" "$BUILD/aligned.apk"

"$BT/apksigner" verify --print-certs "$OUT" | head -6
ls -lh "$OUT"
echo "APK OK: $OUT"
