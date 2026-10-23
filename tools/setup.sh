#!/usr/bin/env bash
# Gets what build.sh and adb need into .tools/ (not committed), without installing anything on the computer or asking
# for a password: a JDK (Amazon Corretto 17), Android's build-tools and platform 34, and adb (platform-tools). Each
# download is checked against the checksum its maker publishes. macOS (Apple Silicon or Intel) and Linux (x86-64).
#
#   tools/setup.sh --accept-android-sdk-license
#
# About 350 MB. build.sh and tools/tv-report.sh then use these tools by themselves; for adb and java in your own
# commands, `. .tools/env.sh` first. Running it again only fetches what's missing. To undo it, delete .tools/.
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS="$(pwd)/.tools"
SDK="$TOOLS/android-sdk"

if [ "${1:-}" != "--accept-android-sdk-license" ]; then
  echo "Android's build-tools, platform and adb come under the Android SDK License Agreement:"
  echo "  https://developer.android.com/studio/terms"
  echo "Accept it to go on: tools/setup.sh --accept-android-sdk-license"
  exit 2
fi

# --- What to get: pinned versions, with the checksums Amazon and Google publish for them
JDK_VERSION=17.0.21.0.10
BUILD_TOOLS=35.0.1
GOOGLE=https://dl.google.com/android/repository
PLATFORM_FILE=platform-34-ext7_r03.zip;  PLATFORM_SHA1=1f2e9478d6a7601425ceaa553311dc43191f103d
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)
    JDK_FILE=amazon-corretto-$JDK_VERSION-macosx-aarch64.tar.gz
    JDK_SHA256=85667cd211b7cef065a35d1ffbe16a19059493ac0130be5f0f93465458525d77 ;;
  Darwin-x86_64)
    JDK_FILE=amazon-corretto-$JDK_VERSION-macosx-x64.tar.gz
    JDK_SHA256=f13aa9654979894dddcd448f8b95b9b355c299c137cb47367cbb01b668b65f7b ;;
  Linux-x86_64)
    JDK_FILE=amazon-corretto-$JDK_VERSION-linux-x64.tar.gz
    JDK_SHA256=b33adc502fdccdaa315feeafc5297b43e0009d0373182448920a46afe1656f34 ;;
  *)
    echo "tools/setup.sh knows macOS and x86-64 Linux (Android's tools for Linux are x86-64 only)."
    echo "Elsewhere, install a JDK 17+, the Android SDK and adb as the README says."
    exit 1 ;;
esac
if [ "$(uname -s)" = Darwin ]; then   # Google's macOS tools run natively on both Apple Silicon and Intel
  BT_FILE=build-tools_r35.0.1_macosx.zip;   BT_SHA1=f4dda6855ddf1ea1a51ee3ab6587104bd0c1d727
  PT_FILE=platform-tools_r37.0.1-darwin.zip; PT_SHA1=6ae73f4de6452dc57e62ec02b68eed92a4c21661
else
  BT_FILE=build-tools_r35.0.1_linux.zip;    BT_SHA1=e009a9b188cfeb1d2b4c318ab5cb4f1ddc368861
  PT_FILE=platform-tools_r37.0.1-linux.zip;  PT_SHA1=477254aa5f903c15cf51001717bdf347fb6b53e0
fi

# --- Helpers
hash_of() {   # hash_of <1|256> <file>: sha1sum/sha256sum (Linux), shasum (macOS) or openssl, whichever is there
  if command -v "sha${1}sum" >/dev/null 2>&1; then "sha${1}sum" "$2" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then shasum -a "$1" "$2" | cut -d' ' -f1
  else openssl dgst "-sha$1" "$2" | awk '{print $NF}'; fi
}

# get <name> <url> <1|256> <checksum> <destination>: downloads, checks, and unpacks the archive's one top folder as
# <destination>. Skipped when that very file is already in place (.tools/.<name> holds its file name).
get() {
  local name=$1 url=$2 bits=$3 sum=$4 dest=$5 file
  file="$(basename "$url")"
  if [ "$(cat "$TOOLS/.$name" 2>/dev/null)" = "$file" ] && [ -d "$dest" ]; then return; fi
  mkdir -p "$TOOLS/downloads"
  echo "Downloading $file…"
  curl -fsSL --retry 3 -o "$TOOLS/downloads/$file" "$url"
  if [ "$(hash_of "$bits" "$TOOLS/downloads/$file")" != "$sum" ]; then
    rm -f "$TOOLS/downloads/$file"
    echo "$file doesn't match its published checksum, so it wasn't used. Run tools/setup.sh again." >&2
    exit 1
  fi
  rm -rf "$TOOLS/unpack" && mkdir -p "$TOOLS/unpack"
  case "$file" in
    *.zip) unzip -q "$TOOLS/downloads/$file" -d "$TOOLS/unpack" ;;
    *) tar -xzf "$TOOLS/downloads/$file" -C "$TOOLS/unpack" ;;
  esac
  local top
  top="$(ls "$TOOLS/unpack")"
  [ -d "$TOOLS/unpack/$top" ] || { echo "$file: unexpected contents" >&2; exit 1; }
  rm -rf "$dest" && mkdir -p "$(dirname "$dest")" && mv "$TOOLS/unpack/$top" "$dest"
  rm -rf "$TOOLS/unpack" "$TOOLS/downloads/$file"
  printf '%s\n' "$file" > "$TOOLS/.$name"
}

# --- Get them
mkdir -p "$TOOLS"
get jdk "https://corretto.aws/downloads/resources/$JDK_VERSION/$JDK_FILE" 256 "$JDK_SHA256" "$TOOLS/jdk"
get build-tools "$GOOGLE/$BT_FILE" 1 "$BT_SHA1" "$SDK/build-tools/$BUILD_TOOLS"
get platform "$GOOGLE/$PLATFORM_FILE" 1 "$PLATFORM_SHA1" "$SDK/platforms/android-34"
get platform-tools "$GOOGLE/$PT_FILE" 1 "$PT_SHA1" "$SDK/platform-tools"
rm -rf "$TOOLS/downloads"

JAVA_HOME="$TOOLS/jdk"
if [ -d "$JAVA_HOME/Contents/Home" ]; then JAVA_HOME="$JAVA_HOME/Contents/Home"; fi   # macOS: the .jdk bundle
cat > "$TOOLS/env.sh" <<EOF
# Made by tools/setup.sh: the tools in .tools/ for this shell (. .tools/env.sh). Moved the folder? Run it again.
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$JAVA_HOME/bin:\$PATH"
EOF

# --- Check that each one runs here
"$JAVA_HOME/bin/javac" -version >/dev/null 2>&1 || { echo "The JDK in .tools/jdk doesn't run here." >&2; exit 1; }
"$SDK/build-tools/$BUILD_TOOLS/aapt2" version >/dev/null 2>&1 || { echo "aapt2 doesn't run here." >&2; exit 1; }
"$SDK/platform-tools/adb" version >/dev/null 2>&1 || { echo "adb doesn't run here." >&2; exit 1; }
echo "Ready: a JDK $JDK_VERSION, build-tools $BUILD_TOOLS, android-34 and adb in .tools/."
echo "./build.sh uses them by itself. For adb in your own commands: . .tools/env.sh"
