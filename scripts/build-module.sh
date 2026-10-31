#!/usr/bin/env bash
# Builds the Paradox module (downloads, MicroG sheet, intro, anti-tamper guard) into one dex.
# Output: build/module/classes.dex  (goes into the APK as classes13.dex)
# Needs: JDK 17+, Android SDK (platforms/android-35 and build-tools/35.0.0 for d8).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; DEPS="$ROOT/.deps"; OUT="$ROOT/build/module"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
AJ="$SDK/platforms/android-35/android.jar"; D8="$SDK/build-tools/35.0.0/lib/d8.jar"
[ -f "$AJ" ] || { echo "android-35 platform not found under $SDK (set ANDROID_HOME)" >&2; exit 1; }
[ -f "$D8" ] || { echo "build-tools 35.0.0 not found under $SDK" >&2; exit 1; }
L="$DEPS/libs"; LIBS="$L/extractor.jar $L/nanojson.jar $L/jsoup.jar $L/protobuf-javalite.jar $L/rhino.jar $L/jsr305.jar"
CP=$(echo $LIBS | tr ' ' ':'); JJ="$DEPS/jarjar/*"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/combined" "$OUT/dex" "$OUT/tools"
javac -nowarn --release 8 -cp "$AJ:$CP" -d "$OUT/classes" $(find "$ROOT/module/src" -name '*.java')
# Bundle the libraries and move them under paradox.lib.* so they can't clash with the app's own classes.
( cd "$OUT/combined"; for j in $LIBS; do unzip -qo "$j" -x 'META-INF/*' || true; done; find . -name module-info.class -delete; cp -r "$OUT/classes/"* . ; zip -qr "$OUT/combined.jar" . )
java -cp "$JJ" org.pantsbuild.jarjar.Main process "$ROOT/module/jarjar-rules.txt" "$OUT/combined.jar" "$OUT/shaded.jar"
# Java 9+ library calls that Android 8 lacks are redirected to paradox.dl.Compat, then API 26 is checked.
javac -cp "$JJ" -d "$OUT/tools" "$ROOT/module/tools/Remap.java" "$ROOT/module/tools/Check.java"
java -cp "$JJ:$OUT/tools" Remap "$OUT/shaded.jar" "$OUT/shaded2.jar"
if [ -f "$SDK/platforms/android-26/android.jar" ]; then
  java -cp "$JJ:$OUT/tools" Check "$SDK/platforms/android-26/android.jar" "$OUT/shaded2.jar" | grep -v -E "List.of|Set.of|Map.of|SerializedLambda|java/beans|ContrastEnforced" || true
fi
java -cp "$D8" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" --output "$OUT/dex" "$OUT/shaded2.jar"
# Rhino's message bundles travel as plain files next to the dex.
mkdir -p "$OUT/res"; ( cd "$OUT/res"; unzip -qo "$OUT/shaded2.jar" 'paradox/lib/mozilla/javascript/resources/*.properties' )
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"; ls -la "$OUT/classes.dex"
