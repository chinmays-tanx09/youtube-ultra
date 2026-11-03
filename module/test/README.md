# Module tests

Plain-JVM tests:

- `AccountDownloadTest`: "download with my account" (`Account`, `PlayerProto`, `Resolve`, stream selection in `Formats`).
- `FormatsTest` (v10.9): every video picks H.264 (or AV1 on capable phones) + AAC for MP4, never VP9/Opus; audio picks MP3 320/192/128; old v10.8 queue specs; file names (`Names`); the ID3 tag; the intro's fit and once-per-version rule.
- `RoutesTest` (v10.10): Shorts / every link shape -> video id; the order routes are tried in (app player data, own signed-in requests, the app's clients, no-sign-in, public) and which are skipped (SABR-only, JavaScript without an engine, sign-in clients when signed out); automatic fallback when a route is bot-checked, empty or its links answer 403; refresh after a 403 skips that route; the time budget; our innertube JSON client (body, headers, parsing); the full error text.
- `HighResTest` (v10.11): quality rows up to 4K / 8K / HDR (H.264 up to 1080p, then AV1 with a hardware decoder, else VP9; HDR rows; sizes, estimated from bitrate when YouTube gives no length); itags the extractor's table lacks (571, 402, 330-337, 694-702); quality-label heights (portrait Shorts, itag 780); `Mp4Mux` pure parts: VP9 key-frame header, VP9 levels, frame-rate snapping of WebM millisecond timestamps, a 4.3 GB output (sparse source) with co64 and a 64-bit mdat, truncated / cancelled / junk input.
- `muxer.sh` (v10.11): `Mp4Mux` on ffmpeg-made VP9 4K, 1440p60, VP9.2 HDR10 29.97, live-style WebM, AV1 4K (fragmented MP4), H.264 with B-frames and AAC (fragmented and plain): box layout, codec kept, every packet byte-identical (size + md5), frame counts, durations, clean decode, HDR colour tags, exact frame rates.
- `mp3.sh`: builds the LAME bridge for the desktop (`scripts/build-native.sh host`) and encodes stereo/mono/float/5.1 test signals through `Mp3.Encoder`, then checks codec, CBR bitrate, sample rate, duration, UTF-16 title, artist, cover art and the LAME Info frame with ffprobe.
- `containers.sh`: YouTube-shaped DASH fixtures (H.264, AV1, AAC, Opus) made with ffmpeg, checked as MP4 (H.264+AAC, AV1+AAC) and Opus -> MP3. ffmpeg stands in for the phone's MediaMuxer/MediaCodec, which only run on a device.
`stubs/` mimics the few public methods of the app's extension that the module calls; `fixtures/` holds two saved
player responses (protobuf) with **fake** URLs, ids and tokens: one signed-in/playable, one members-only/locked.

```sh
export ANDROID_HOME=$HOME/Android/Sdk
bash scripts/fetch-deps.sh          # or at least .deps/libs
bash module/test/run.sh          # mp3.sh/containers.sh need ffmpeg + a C compiler
```
