#!/usr/bin/env bash
# Builds Scrape Stack without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Needs a JDK (17+) and an Android SDK with build-tools 34.0.0 and platform android-34.
# Override the defaults with environment variables:
#   JAVA_HOME     JDK install directory
#   ANDROID_HOME  Android SDK directory (ANDROID_SDK_ROOT also accepted)
#   BUILD_DIR     scratch directory for intermediates (must not contain spaces)
set -euo pipefail

TOOLS="${TOOLS:-$HOME/android-tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk-17.0.20.1+1}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$TOOLS/sdk}}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="${BUILD_DIR:-$TOOLS/build-scrapestack}"

JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
KEYTOOL="$JAVA_HOME/bin/keytool"

# Windows (Git Bash) needs .exe suffixes, Windows-style paths and ';' as the classpath separator
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe"; winpath() { cygpath -w "$1"; }; SEP=";" ;;
  *) EXE=""; winpath() { echo "$1"; }; SEP=":" ;;
esac

for f in "$JAVAC" "$BT/aapt2$EXE" "$PLATFORM"; do
  [ -e "$f" ] || { echo "missing: $f (set JAVA_HOME / ANDROID_HOME, see README)"; exit 1; }
done

ROOT="$(cd "$(dirname "$0")" && pwd)"
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile"
"$BT/aapt2$EXE" compile --dir "$ROOT/res" -o "$OUT/res/compiled.zip"

echo "== aapt2 link"
"$BT/aapt2$EXE" link -o "$OUT/base.apk" -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --java "$OUT/gen" "$OUT/res/compiled.zip"

echo "== javac"
# argfiles can't hold paths with spaces, so compile from a space-free staging dir
cp -r "$ROOT/src" "$OUT/src"
cd "$OUT"
find src gen -name '*.java' > sources.txt
BOOTCP="$(winpath "$PLATFORM")$SEP$(winpath "$BT/core-lambda-stubs.jar")"
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$BOOTCP" \
  -Xlint:-options -d classes @sources.txt

echo "== d8"
find classes -name '*.class' > classes.txt
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$PLATFORM" --output dex @classes.txt

echo "== package dex"
cp base.apk unaligned.apk
"$JAR" -uMf unaligned.apk -C dex classes.dex

echo "== zipalign"
"$BT/zipalign$EXE" -p -f 4 unaligned.apk aligned.apk

# Local signing key; not committed. Each machine that builds gets its own.
KS="$ROOT/scrapestack.keystore"
if [ ! -f "$KS" ]; then
  echo "== creating signing key"
  "$KEYTOOL" -genkeypair -keystore "$KS" -storepass scrapestack -keypass scrapestack \
    -alias scrapestack -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Scrape Stack, C=US" >/dev/null 2>&1
fi

echo "== apksigner"
"$JAVA" -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:scrapestack \
  --key-pass pass:scrapestack --ks-key-alias scrapestack \
  --out "$ROOT/ScrapeStack.apk" aligned.apk
"$JAVA" -jar "$BT/lib/apksigner.jar" verify "$ROOT/ScrapeStack.apk"
ls -la "$ROOT/ScrapeStack.apk"
echo "BUILD OK"
