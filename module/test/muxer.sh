#!/usr/bin/env bash
# v10.11: our own MP4 muxer (Mp4Mux) on YouTube-shaped fixtures made with ffmpeg:
#   VP9 4K / 1440p60 / VP9.2 HDR 29.97 (WebM, incl. live-style unknown-size clusters and DASH cues), AV1 4K (fragmented MP4),
#   H.264 with B-frames (fragmented MP4, the MediaMuxer-failure fallback), AAC (fragmented and plain M4A).
# Each output must: be an MP4 (isom, moov before mdat, every chunk offset inside mdat), keep the codec (no re-encode),
# carry exactly the source packets (size + md5 of every packet), have matching durations / frame counts, decode cleanly.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; OUT="$ROOT/build/test"; T="$OUT/muxer"; rm -rf "$T"; mkdir -p "$T"
L="$ROOT/.deps/libs"; AJ="$SDK/platforms/android-35/android.jar"; RUN="java -cp $OUT:$L/extractor.jar:$L/nanojson.jar:$AJ paradox.dl.MuxCli"
F="ffmpeg -nostdin -v error -y"; FRAG="-movflags frag_keyframe+empty_moov+default_base_moof"; VP9="-c:v libvpx-vp9 -deadline realtime -cpu-used 8 -row-mt 1 -an"
pass=0; fail=0; ok(){ if eval "$1"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAIL: $2"; fi; }
$F -f lavfi -i "testsrc2=s=3840x2160:r=30:d=1"        $VP9 -b:v 3M -g 30 -pix_fmt yuv420p -f webm "$T/v313.webm"                       # itag 313: 4K VP9
$F -f lavfi -i "testsrc2=s=2560x1440:r=60:d=1"        $VP9 -b:v 2M -g 60 -pix_fmt yuv420p -f webm -dash 1 "$T/v308.webm"               # itag 308: 1440p60, DASH-style cues
$F -f lavfi -i "testsrc2=s=3840x2160:r=30000/1001:d=1" $VP9 -profile:v 2 -b:v 3M -g 30 -pix_fmt yuv420p10le -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -color_range tv -f webm "$T/v337.webm"   # itag 337: VP9.2 HDR10
$F -f lavfi -i "testsrc2=s=1280x720:r=25:d=1"         $VP9 -b:v 1M -g 25 -pix_fmt yuv420p -f webm -live 1 "$T/vlive.webm"               # unknown-size segment / clusters
$F -f lavfi -i "testsrc2=s=3840x2160:r=30:d=1" -c:v libsvtav1 -preset 12 -pix_fmt yuv420p -g 30 -an $FRAG "$T/v401.mp4" 2>/dev/null       # itag 401: 4K AV1, fragmented
$F -f lavfi -i "testsrc2=s=1280x720:r=30:d=1" -c:v libx264 -preset veryfast -bf 3 -g 30 -pix_fmt yuv420p -an $FRAG "$T/v136.mp4"          # H.264 with B-frames (fallback path)
$F -f lavfi -i "sine=f=330:d=1" -ac 2 -ar 44100 -c:a aac -b:a 128k -vn $FRAG "$T/a140.m4a"                                              # itag 140, fragmented
$F -f lavfi -i "sine=f=440:d=1" -ac 2 -ar 48000 -c:a aac -b:a 128k -vn "$T/a_plain.m4a"                                                  # plain (non-fragmented) M4A
pk(){ ffmpeg -nostdin -v error -i "$1" -map 0:$2 -c copy -f framemd5 - | grep -v '^#' | awk -F, '{gsub(/ /,"");print $5","$6}' | md5sum | cut -c1-16; }
num(){ ffprobe -v error -select_streams $2 -count_packets -show_entries stream=nb_read_packets -of csv=p=0 "$1"; }
dur(){ ffprobe -v error -select_streams $2 -show_entries stream=duration -of csv=p=0 "$1"; }
fmtdur(){ ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
lastpts(){ ffprobe -v error -select_streams $2 -show_entries packet=pts_time,duration_time -of csv=p=0 "$1" | awk -F, 'BEGIN{m=0;mn=1e18;d=0}{if($1+0>m){m=$1+0}; if($1+0<mn){mn=$1+0}; if($2!="N/A"&&$2!="")d=$2+0}END{if(d==0)d=1/25; printf "%.6f", m-mn+d}'; }
close(){ awk -v a="$1" -v b="$2" -v t="$3" 'BEGIN{d=a-b; if(d<0)d=-d; exit !(d<=t)}'; }
boxes(){ python3 - "$1" <<'PY'
import struct,sys
d=open(sys.argv[1],'rb').read(); p=0; top=[]; mdat=None
while p+8<=len(d):
    s,t=struct.unpack('>I4s',d[p:p+8]); h=8
    if s==1: s=struct.unpack('>Q',d[p+8:p+16])[0]; h=16
    top.append(t.decode('latin1'))
    if t==b'mdat': mdat=(p+h,p+s)
    p+=s
assert p==len(d), "boxes don't add up to the file size"
assert top==['ftyp','moov','mdat'], top
offs=[]; i=0
while True:
    i=d.find(b'stco',i)
    if i<0 or i>mdat[0]: break
    n=struct.unpack('>I',d[i+8:i+12])[0]; offs+=list(struct.unpack('>%dI'%n,d[i+12:i+12+4*n])); i+=4
i=0
while True:
    i=d.find(b'co64',i)
    if i<0 or i>mdat[0]: break
    n=struct.unpack('>I',d[i+8:i+12])[0]; offs+=list(struct.unpack('>%dQ'%n,d[i+12:i+12+8*n])); i+=4
assert offs and all(mdat[0]<=o<mdat[1] for o in offs), "chunk offset outside mdat"
assert offs[0]==mdat[0], "first chunk starts mdat"
print("ok")
PY
}
for pair in "v313.webm a140.m4a vp9 3840x2160" "v308.webm a140.m4a vp9 2560x1440" "v337.webm a140.m4a vp9 3840x2160" "vlive.webm a_plain.m4a vp9 1280x720" "v401.mp4 a140.m4a av1 3840x2160" "v136.mp4 a140.m4a h264 1280x720"; do
  set -- $pair; n="$1"; v="$T/$1"; a="$T/$2"; o="$T/out_${1%%.*}.mp4"; c="$3"; wh="$4"
  $RUN "$v" "$a" "$o" >"$o.txt"
  ok '[ "$(boxes "$o")" = ok ]' "$n: MP4 box layout (ftyp, moov, mdat; offsets inside mdat)"
  ok '[ "$(head -c 12 "$o" | tail -c 4)" = isom ]' "$n: brand isom"
  ok '[ "$(ffprobe -v error -select_streams v -show_entries stream=codec_name,width,height -of csv=p=0 "$o")" = "$c,${wh/x/,}" ]' "$n: $c $wh kept"
  ok '[ "$(ffprobe -v error -select_streams a -show_entries stream=codec_name -of csv=p=0 "$o")" = aac ]' "$n: AAC audio"
  ok '[ "$(pk "$v" v)" = "$(pk "$o" v)" ]' "$n: every video packet identical to the download"
  ok '[ "$(pk "$a" a)" = "$(pk "$o" a)" ]' "$n: every audio packet identical"
  ok '[ "$(num "$v" v)" = "$(num "$o" v)" ]' "$n: same frame count ($(num "$v" v))"
  sd=$(lastpts "$v" v); od=$(dur "$o" v); ok 'close "$sd" "$od" 0.05' "$n: video duration $od vs source $sd"
  sa=$(fmtdur "$a"); oa=$(dur "$o" a); ok 'close "$sa" "$oa" 0.03' "$n: audio duration $oa vs source $sa"
  ok '[ -z "$(ffmpeg -nostdin -v error -threads 8 -i "$o" -f null - 2>&1)" ]' "$n: decodes with no errors"
done
ok '[ "$(ffprobe -v error -select_streams v -show_entries stream=profile,pix_fmt,color_space,color_transfer,color_primaries -of csv=p=0 "$T/out_v337.mp4")" = "Profile 2,yuv420p10le,bt2020nc,smpte2084,bt2020" ]' "HDR10: 10-bit, BT.2020, PQ kept"
ok '[ "$(ffprobe -v error -select_streams v -show_entries stream=r_frame_rate -of csv=p=0 "$T/out_v337.mp4")" = "30000/1001" ]' "29.97 fps kept exactly"
ok '[ "$(ffprobe -v error -select_streams v -show_entries stream=r_frame_rate -of csv=p=0 "$T/out_v308.mp4")" = "60/1" ]' "60 fps kept"
ok 'diff <(ffprobe -v error -select_streams v -show_entries packet=pts,dts -of csv=p=0 "$T/v136.mp4") <(ffprobe -v error -select_streams v -show_entries packet=pts,dts -of csv=p=0 "$T/out_v136.mp4") >/dev/null' "H.264 B-frames: pts/dts identical"
ok 'grep -q "hdr=true" "$T/out_v337.mp4.txt" && grep -q "depth=10" "$T/out_v337.mp4.txt"' "muxer reports 10-bit HDR"
ok '! $RUN "$T/a140.m4a" "$T/a140.m4a" "$T/bad.mp4" >/dev/null 2>&1' "audio file given as video -> error, no output claimed"
rm -f "$T"/*.webm "$T"/v*.mp4 "$T"/out_*.mp4
echo "muxer.sh: $pass passed, $fail failed"; [ $fail -eq 0 ]
