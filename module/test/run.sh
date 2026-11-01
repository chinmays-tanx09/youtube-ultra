#!/usr/bin/env bash
# Plain-JVM module tests:
#   AccountDownloadTest  "download with my account" (fixtures)
#   FormatsTest          MP4/MP3 format selection, file names, ID3, intro rules
#   mp3.sh               real LAME encoder (host build) + ffprobe checks   (skipped when ffmpeg is missing)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"
AJ="$SDK/platforms/android-35/android.jar"; L="$ROOT/.deps/libs"
CP="$L/extractor.jar:$L/nanojson.jar:$L/jsoup.jar:$L/protobuf-javalite.jar:$L/rhino.jar:$L/jsr305.jar"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -nowarn --release 8 -encoding UTF-8 -cp "$AJ:$CP" -d "$OUT" $(find "$ROOT/module/src" "$ROOT/module/test/stubs" "$ROOT/module/test/src" -name '*.java')
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.AccountDownloadTest "$ROOT/module/test/fixtures" "$@"
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.FormatsTest
if command -v ffmpeg >/dev/null && command -v ffprobe >/dev/null; then bash "$ROOT/module/test/mp3.sh"; bash "$ROOT/module/test/containers.sh"; else echo "mp3.sh skipped (no ffmpeg)"; fi
