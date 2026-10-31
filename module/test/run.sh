#!/usr/bin/env bash
# Compiles the module sources + test stubs on a plain JVM and runs AccountDownloadTest.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"
AJ="$SDK/platforms/android-35/android.jar"; L="$ROOT/.deps/libs"
CP="$L/extractor.jar:$L/nanojson.jar:$L/jsoup.jar:$L/protobuf-javalite.jar:$L/rhino.jar:$L/jsr305.jar"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -nowarn --release 8 -cp "$AJ:$CP" -d "$OUT" $(find "$ROOT/module/src" "$ROOT/module/test/stubs" "$ROOT/module/test/src" -name '*.java')
java -cp "$OUT:$CP:$AJ" paradox.dl.AccountDownloadTest "$ROOT/module/test/fixtures" "$@"
