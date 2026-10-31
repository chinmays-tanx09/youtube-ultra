#!/usr/bin/env bash
# Builds YouTube Ultra from source.
#
#   scripts/build.sh /path/to/youtube-20.05.46-universal.apk
#
# Signing: set these to your OWN keystore (nothing secret lives in this repo):
#   ULTRA_KEYSTORE=/path/to/your.jks ULTRA_KEY_ALIAS=yourkey ULTRA_STORE_PASS=... [ULTRA_KEY_PASS=...]
# Without them you get an unsigned APK (build/out/YouTube_Ultra-unsigned.apk).
#
# Note: a build signed with your key is NOT the official app. The built-in signature
# check (paradox.dl.Guard) will show "Modified copy" unless you change
# Guard.CERT_SHA256 to your own certificate's SHA-256 - and please also rename
# the app (see NOTICE: the "YouTube Ultra" / "Paradox" names and branding are not licensed).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; DEPS="$ROOT/.deps"; W="$ROOT/build/work"; OUT="$ROOT/build/out"
STOCK="${1:?usage: scripts/build.sh <stock YouTube 20.05.46 universal APK>}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"; BT="$SDK/build-tools/35.0.0"
VERSION_CODE="${ULTRA_VERSION_CODE:-1552692681}"
JAVA="${JAVA:-java}"   # Morphe CLI needs Java 21+

"$ROOT/scripts/fetch-deps.sh"
"$ROOT/scripts/build-module.sh"
rm -rf "$W"; mkdir -p "$W" "$OUT"

echo "== 1/5 patch stock YouTube with anddea patches (Morphe CLI)"
"$JAVA" -Xmx6g -jar "$DEPS/morphe-cli.jar" patch -p "$DEPS/patches.mpp" \
  -O packageNameYouTube=app.paradox.youtube -e "GmsCore support" -O "customName=YT Pro Max" \
  -e "Custom branding for YouTube" -t "$W/tmp" --unsigned -o "$W/patched.apk" "$STOCK"

echo "== 2/5 decode"
"$JAVA" -jar "$DEPS/apktool.jar" d -s -f -o "$W/app" "$W/patched.apk"

echo "== 3/5 apply YouTube Ultra smali edits"
for diff in "$ROOT"/patches/smali/classes*.diff; do
  dex=$(basename "$diff" .diff)
  "$JAVA" -jar "$DEPS/baksmali.jar" d "$W/app/$dex.dex" -o "$W/smali/$dex" --debug-info false
  ( cd "$W/smali" && patch -p1 --no-backup-if-mismatch < "$diff" )
  "$JAVA" -jar "$DEPS/smali.jar" a -a 26 "$W/smali/$dex" -o "$W/app/$dex.dex"
done
cp "$ROOT/build/module/classes.dex" "$W/app/classes13.dex"
mkdir -p "$W/app/unknown"; cp -r "$ROOT/build/module/res/paradox" "$W/app/unknown/"

echo "== 4/5 resources, manifest, assets"
python3 "$ROOT/patches/apply_resources.py" "$W/app" --version-code "$VERSION_CODE"
rm -rf "$W/app/lib/x86" "$W/app/lib/x86_64"            # arm64-v8a + armeabi-v7a only
mkdir -p "$W/app/assets"
for f in paradox_intro.mp4 microg_icon.png; do          # optional branding; the app works without it
  [ -f "$ROOT/branding/$f" ] && cp "$ROOT/branding/$f" "$W/app/assets/$f" || true
done

echo "== 5/5 build, align, sign"
"$JAVA" -jar "$DEPS/apktool.jar" b -j 4 -o "$W/unsigned.apk" "$W/app"
"$BT/zipalign" -p -f 4 "$W/unsigned.apk" "$W/aligned.apk"
if [ -n "${ULTRA_KEYSTORE:-}" ]; then
  "$BT/apksigner" sign --ks "$ULTRA_KEYSTORE" --ks-key-alias "${ULTRA_KEY_ALIAS:?}" \
    --ks-pass env:ULTRA_STORE_PASS --key-pass env:"$( [ -n "${ULTRA_KEY_PASS:-}" ] && echo ULTRA_KEY_PASS || echo ULTRA_STORE_PASS )" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/YouTube_Ultra.apk" "$W/aligned.apk"
  "$BT/apksigner" verify --print-certs "$OUT/YouTube_Ultra.apk" | head -3
  echo "built $OUT/YouTube_Ultra.apk"
else
  mv "$W/aligned.apk" "$OUT/YouTube_Ultra-unsigned.apk"
  echo "built $OUT/YouTube_Ultra-unsigned.apk (unsigned: set ULTRA_KEYSTORE etc. to sign)"
fi
