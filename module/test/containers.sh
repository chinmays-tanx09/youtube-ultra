#!/usr/bin/env bash
# Box-side check of the stream combinations v10.9 saves (YouTube-shaped DASH fixtures made with ffmpeg):
#   H.264 + AAC -> .mp4, AV1 + AAC -> .mp4, Opus (WebM) -> PCM -> LAME -> .mp3.
# ffmpeg stands in for the phone's MediaMuxer / MediaCodec here; the real ones only run on a device.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"; T="$OUT/containers"; mkdir -p "$T"
F="ffmpeg -nostdin -v error -y"; FRAG="-movflags frag_keyframe+empty_moov+default_base_moof"
pass=0; fail=0; ok(){ if eval "$1"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAIL: $2"; fi; }
$F -f lavfi -i "testsrc2=s=1280x720:r=30:d=8" -c:v libx264 -profile:v high -pix_fmt yuv420p -g 60 -an $FRAG "$T/v136.mp4"              # like itag 136 (avc1, fragmented, video only)
$F -f lavfi -i "testsrc2=s=1280x720:r=30:d=8" -c:v libsvtav1 -preset 10 -pix_fmt yuv420p -g 60 -an $FRAG "$T/v398.mp4"                # like itag 398 (av01, fragmented, video only)
$F -f lavfi -i "sine=f=330:d=8" -ac 2 -ar 44100 -c:a aac -b:a 128k -vn $FRAG "$T/a140.m4a"                                          # like itag 140
$F -f lavfi -i "sine=f=330:d=8" -ac 2 -ar 48000 -c:a libopus -b:a 160k -vn -f webm "$T/a251.webm"                                     # like itag 251
for v in 136 398; do
  $F -i "$T/v$v.mp4" -i "$T/a140.m4a" -map 0:v -map 1:a -c copy -f mp4 "$T/out$v.mp4"
  P=$(ffprobe -v error -show_entries stream=codec_type,codec_name -of csv=p=0 "$T/out$v.mp4" | tr '\n' ' ')
  ok '[[ "$(ffprobe -v error -show_entries format=format_name -of csv=p=0 "$T/out$v.mp4")" == *mp4* ]]' "out$v is an MP4 container"
  ok '[[ "$P" == *"video"* && "$P" == *"aac,audio"* ]]' "out$v has video + AAC ($P)"
  ok '[ -z "$(ffmpeg -nostdin -v error -i "$T/out$v.mp4" -f null - 2>&1)" ]' "out$v decodes cleanly"
  ok '[ "$(head -c 12 "$T/out$v.mp4" | tail -c 4)" = "isom" ]' "out$v brand isom"
done
ok '[[ "$(ffprobe -v error -select_streams v -show_entries stream=codec_name -of csv=p=0 "$T/out398.mp4")" == av1 ]]' "AV1 stays AV1 (no re-encode)"
$F -i "$T/a251.webm" -f s16le -ac 2 -ar 48000 "$T/a251.pcm"
AJ="$SDK/platforms/android-35/android.jar"; L="$ROOT/.deps/libs"
java -Dfile.encoding=UTF-8 -Djava.library.path="$ROOT/build/native/host" -cp "$OUT:$L/extractor.jar:$L/nanojson.jar:$AJ" paradox.dl.Mp3RoundTrip "$T/a251.pcm" 48000 2 0 "$T/a251.mp3" 320 "Opus source" "Test" </dev/null >/dev/null
ok '[[ "$(ffprobe -v error -show_entries stream=codec_name,bit_rate -of csv=p=0 "$T/a251.mp3")" == "mp3,320000" ]]' "Opus -> MP3 320k"
echo "containers.sh: $pass passed, $fail failed"; [ $fail -eq 0 ]
