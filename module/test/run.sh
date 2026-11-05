#!/usr/bin/env bash
# Plain-JVM module tests:
#   AccountDownloadTest  "download with my account" (fixtures)
#   FormatsTest          MP4/MP3 format selection, file names, ID3, intro rules
#   RoutesTest           v10.10: Shorts ids, route order + fallback, player data, link probe, innertube client, full errors
#   HighResTest          v10.11: 4K/8K/HDR quality rows, extra itags, VP9 header/levels, frame-rate snapping, >4 GB co64, errors
#   V1012Test            v10.12: original audio track (never dubbed) incl. refresh + innertube audioTrack, download list robustness
#   muxer.sh             v10.11: our MP4 muxer on ffmpeg-made VP9/AV1/H.264/AAC fixtures, checked with ffprobe/ffmpeg   (skipped without ffmpeg)
#   mp3.sh               real LAME encoder (host build) + ffprobe checks   (skipped when ffmpeg is missing)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"
AJ="$SDK/platforms/android-35/android.jar"; L="$ROOT/.deps/libs"
CP="$L/extractor.jar:$L/nanojson.jar:$L/jsoup.jar:$L/protobuf-javalite.jar:$L/rhino.jar:$L/jsr305.jar:$L/orgjson-test.jar"   # orgjson-test.jar: tests only (the phone has org.json; android.jar's stub throws)
rm -rf "$OUT"; mkdir -p "$OUT"
javac -nowarn --release 8 -encoding UTF-8 -cp "$AJ:$CP" -d "$OUT" $(find "$ROOT/module/src" "$ROOT/module/test/stubs" "$ROOT/module/test/src" -name '*.java')
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.AccountDownloadTest "$ROOT/module/test/fixtures" "$@"
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.FormatsTest
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.RoutesTest
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.HighResTest
java -Dfile.encoding=UTF-8 -cp "$OUT:$CP:$AJ" paradox.dl.V1012Test
if command -v ffmpeg >/dev/null && command -v ffprobe >/dev/null; then bash "$ROOT/module/test/mp3.sh"; bash "$ROOT/module/test/containers.sh"; bash "$ROOT/module/test/muxer.sh"; else echo "mp3.sh skipped (no ffmpeg)"; fi
