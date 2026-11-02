# Module tests

Plain-JVM tests:

- `AccountDownloadTest`: "download with my account" (`Account`, `PlayerProto`, `Resolve`, stream selection in `Formats`).
- `FormatsTest` (v10.9): every video picks H.264 (or AV1 on capable phones) + AAC for MP4, never VP9/Opus; audio picks MP3 320/192/128; old v10.8 queue specs; file names (`Names`); the ID3 tag; the intro's fit and once-per-version rule.
- `RoutesTest` (v10.10): Shorts / every link shape -> video id; the order routes are tried in (app player data, own signed-in requests, the app's clients, no-sign-in, public) and which are skipped (SABR-only, JavaScript without an engine, sign-in clients when signed out); automatic fallback when a route is bot-checked, empty or its links answer 403; refresh after a 403 skips that route; the time budget; our innertube JSON client (body, headers, parsing); the full error text.
- `mp3.sh`: builds the LAME bridge for the desktop (`scripts/build-native.sh host`) and encodes stereo/mono/float/5.1 test signals through `Mp3.Encoder`, then checks codec, CBR bitrate, sample rate, duration, UTF-16 title, artist, cover art and the LAME Info frame with ffprobe.
- `containers.sh`: YouTube-shaped DASH fixtures (H.264, AV1, AAC, Opus) made with ffmpeg, checked as MP4 (H.264+AAC, AV1+AAC) and Opus -> MP3. ffmpeg stands in for the phone's MediaMuxer/MediaCodec, which only run on a device.
`stubs/` mimics the few public methods of the app's extension that the module calls; `fixtures/` holds two saved
player responses (protobuf) with **fake** URLs, ids and tokens: one signed-in/playable, one members-only/locked.

```sh
export ANDROID_HOME=$HOME/Android/Sdk
bash scripts/fetch-deps.sh          # or at least .deps/libs
bash module/test/run.sh          # mp3.sh/containers.sh need ffmpeg + a C compiler
```
