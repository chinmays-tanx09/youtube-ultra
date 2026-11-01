#!/usr/bin/env bash
# Encodes test signals through the real LAME bridge and checks the MP3s with ffprobe/ffmpeg.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"; T="$OUT/mp3"; mkdir -p "$T"
AJ="$SDK/platforms/android-35/android.jar"; L="$ROOT/.deps/libs"; CP="$OUT:$L/extractor.jar:$L/nanojson.jar:$AJ"
[ -f "$ROOT/build/native/host/libparadoxmp3.so" ] || bash "$ROOT/scripts/build-native.sh" host >/dev/null
JR="java -Dfile.encoding=UTF-8 -Djava.library.path=$ROOT/build/native/host -cp $CP paradox.dl.Mp3RoundTrip"
pass=0; fail=0; ok(){ if eval "$1"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAIL: $2"; fi; }
ffmpeg -nostdin -v error -y -f lavfi -i "testsrc2=s=480x360:d=1" -frames:v 1 "$T/cover.jpg"
TITLE="केसरिया 🔥 Kesariya (Official Video)"; ARTIST="Sony Music India"
# case: name rate channels pcmfmt float kbps expected_out_rate
while read -r name rate ch fmt fl kbps exprate; do
  ffmpeg -nostdin -v error -y -f lavfi -i "sine=f=440:d=12,aformat=channel_layouts=mono" -f lavfi -i "anoisesrc=d=12:a=0.05" -filter_complex "[0][1]amix=inputs=2,volume=2" -ar "$rate" -ac "$ch" -f "$fmt" "$T/$name.pcm"
  $JR "$T/$name.pcm" "$rate" "$ch" "$fl" "$T/$name.mp3" "$kbps" "$TITLE" "$ARTIST" "$T/cover.jpg" </dev/null | sed "s/^/  $name: /"
  P=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels,bit_rate -of default=nw=1 "$T/$name.mp3")
  D=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$T/$name.mp3")
  ok '[[ "$P" == *"codec_name=mp3"* ]]' "$name codec mp3 ($P)"
  ok '[[ "$P" == *"sample_rate=$exprate"* ]]' "$name sample rate $exprate ($P)"
  ok '[[ "$P" == *"channels=$([ $ch -ge 2 ] && echo 2 || echo 1)"* ]]' "$name channels ($P)"
  ok '[[ "$P" == *"bit_rate=${kbps}000"* ]]' "$name CBR ${kbps}k ($P)"
  ok 'awk -v d="$D" "BEGIN{exit !(d>11.9 && d<12.1)}"' "$name duration 12 s (got $D)"
  ok '[ "$(ffprobe -v error -show_entries format_tags=title -of csv=p=0 "$T/$name.mp3")" = "$TITLE" ]' "$name ID3 title (UTF-16)"
  ok '[ "$(ffprobe -v error -show_entries format_tags=artist -of csv=p=0 "$T/$name.mp3")" = "$ARTIST" ]' "$name ID3 artist"
  ok 'ffprobe -v error -show_entries stream=codec_name:stream_disposition=attached_pic -of csv=p=0 "$T/$name.mp3" | grep -q "mjpeg,1"' "$name cover art"
  ok '[ -z "$(ffmpeg -nostdin -v error -i "$T/$name.mp3" -f null - 2>&1)" ]' "$name decodes cleanly"
  ok '[ "$(ffmpeg -nostdin -i "$T/$name.mp3" -af volumedetect -f null - 2>&1 | sed -n "s/.*mean_volume: \(-*[0-9]*\).*/\1/p")" -gt -40 ]' "$name not silent (down-mix keeps the centre channel)"
  ok 'python3 -c "import sys;b=open(sys.argv[1],\"rb\").read();n=10+((b[6]&127)<<21|(b[7]&127)<<14|(b[8]&127)<<7|(b[9]&127));f=b[n:n+400];sys.exit(not(b[:3]==b\"ID3\" and f[0]==255 and (f[1]&0xE0)==0xE0 and b\"Info\" in f[:60] and b\"LAME\" in f))" "$T/$name.mp3"' "$name first frame after ID3 is the LAME Info frame"
done <<'EOF'
stereo48k_320 48000 2 s16le 0 320 48000
stereo44k_192 44100 2 s16le 0 192 44100
mono44k_128 44100 1 s16le 0 128 44100
float48k_320 48000 2 f32le 1 320 48000
surround51_192 48000 6 s16le 0 192 48000
EOF
echo "mp3.sh: $pass passed, $fail failed"; [ $fail -eq 0 ]
