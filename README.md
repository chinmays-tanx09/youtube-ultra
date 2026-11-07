<p align="center">
  <img src="images/icon.png" width="128" alt="YouTube Ultra icon">
</p>

<h1 align="center">YouTube Ultra</h1>

<p align="center">
  YouTube for Android without ads, with built-in downloads, SponsorBlock and Shorts auto scroll.<br>
  <a href="https://github.com/chinmays-tanx09/youtube-ultra/releases/latest"><b>Download the latest release</b></a>
</p>

<p align="center">
  <img src="images/screenshot1.jpg" width="220" alt="Shorts menu with Auto scroll">
  <img src="images/screenshot2.jpg" width="220" alt="Download quality picker">
  <img src="images/screenshot3.jpg" width="220" alt="Download video or audio">
  <img src="images/screenshot4.jpg" width="220" alt="Home feed">
</p>

## Features

- **No ads**: no video ads and no feed ads.
- **Downloads**: download any video in any quality it was uploaded in, from 144p up to 4K (and 8K / HDR when the video has them), or audio only. Videos are saved as real **MP4** files and audio as real **MP3** files (v10.9). Fast multi-connection downloads that keep full speed in the background or with the screen off, and paused downloads resume where they stopped. Finished files show up in your Gallery or Music. There's an In progress / Downloaded list inside YouTube's Downloads tab.
- **Shorts auto scroll**: in the Shorts ⋮ menu, turn on "Auto scroll" and the next Short plays when one ends. Turn it off and Shorts loop like normal. Your choice is saved.
- **Cleaner Shorts menu**: "Send feedback" is removed.
- **No time reminders**: no "Take a break" or bedtime reminders, and no "Video paused. Continue watching?" pause.
- **Background play and picture-in-picture**: keep listening with the screen off or in another app.
- **SponsorBlock**: skips sponsor segments automatically.
- **Return YouTube Dislike**: dislike counts are back.
- **Swipe controls** for brightness and volume, a miniplayer, and many layout options in Settings.
- **WhatsApp channel card**: once per app start, an invite to the Paradox WhatsApp channel (Join channel / Later / Don't show again). It waits until MicroG is set up and never shows again after "Don't show again" or after you join.
- **Update check** (v10.7): on launch, at most every 12 hours, the app reads this repo's releases list and, if a release with a higher `versionCode:` line exists, shows an "Update available" card (Download / Later / Skip this version). Download opens the APK link in your browser; nothing is installed by the app itself. No tracking, no new permissions. Code: `module/src/paradox/banner/UpdateCheck.java`.
- **Download with your account** (v10.8): when you're signed in, videos your account can watch but that need a sign-in (members-only videos of channels you've joined, age-checked videos, "confirm you're not a bot") download like any other video. Public videos still use the normal download path. YouTube decides what your account may play, so members-only videos still fail without the membership, with a clear "This video needs a channel membership on your account" message. Your sign-in stays inside the app's memory: it is never written to a file or log and only goes to YouTube. Downloads are for your own offline viewing. Code: `module/src/paradox/dl/Account.java`, `Resolve.java`, `PlayerProto.java`; tests in `module/test/`.
- **MP4 and MP3 downloads** (v10.9): videos are saved as `<title>.mp4` with H.264 video and AAC audio, joined without re-encoding, so they play in the Samsung Gallery and any player. YouTube only offers 1440p and 4K as VP9/AV1, which Android can't put in an MP4 except AV1 on Android 14+; so those qualities appear (as "AV1") only on phones with Android 14+ and a hardware AV1 decoder, and other phones get up to 1080p H.264 (lifted in v10.11, below). Audio is converted on the phone to `<title>.mp3` (320, 192 or 128 kbps CBR, LAME encoder) from the best original audio track, with the title, channel and thumbnail as ID3 tags and cover art. Code: `module/src/paradox/dl/Formats.java`, `Mux.java`, `ToMp3.java`, `Mp3.java`, `Id3.java`, `Names.java`, `module/jni/`.
- **Shorts and blocked-network downloads** (v10.10): downloads no longer depend on YouTube's anonymous access, which YouTube "temporarily blocks" on busy networks (that stopped Shorts downloads with "Couldn't load this video"). The app first uses the stream data its own player already received for the video on screen, then a player request with your signed-in session, then the app's other clients, and only then the anonymous routes, moving on automatically whenever a route gives nothing or its links are refused. If every route fails, a dialog shows the full reason for each one, with Try again. Code: `module/src/paradox/dl/Resolve.java`, `Innertube.java`, `Account.java`.
- **Up to 4K / 8K and HDR** (v10.11): the quality list shows every resolution the video really has, up to its highest: 1440p, 4K, 8K, 60fps and HDR. YouTube serves those only as VP9 or AV1, so v10.11 has its own MP4 writer (`Mp4Mux.java`, pure Java) that puts VP9 (`vp09` + `vpcC`) or AV1 (`av01` + `av1C`) video and AAC audio into `<title>.mp4` with no re-encoding, on any Android version. Per resolution it uses H.264 where YouTube has it (up to 1080p, via Android's MediaMuxer as before), else AV1 when the phone has a hardware AV1 decoder for that size, else VP9. Each row shows its size; the app checks free space first, shows Saving MP4 · N %, and resumes / refreshes links as before. Code: `module/src/paradox/dl/Mp4Mux.java`, `Formats.java`, `Codecs.java`; tests `module/test/HighResTest`, `module/test/muxer.sh`.
- **Fixes** (v10.12): downloads always show in Library > Downloads (the list can no longer lose finished files, and every file in Movies/YouTube Ultra or Music/YouTube Ultra is listed); downloads pick the video's **original** audio track, never an auto-dubbed language (also when a link is refreshed mid-download); the Shorts default quality is applied to every new Short (it was only applied once per list); steadier download service (no "Preparing…" flash on a second download, no stuck progress notification, safe start on Android 12+, clean stop at Android 15's 6-hour limit, a visible "Download failed" note).
- **Settings -> YouTube Ultra** (v10.13): a new section at the top of YouTube's Settings that holds every customisation switch of this build in one place, grouped as Player (swipe gestures, quality and speed, Picture-in-Picture), Clean feed, Shorts, Downloads, Queue & Watch later, SponsorBlock & dislikes, Backup and About. It is the same switches and screens as the RVX settings (they reuse the existing patches), shown in one organised list; "All advanced settings" at the bottom still opens the complete RVX screen.
- **Swipe gestures** (v10.13): swipe up/down on the left half of the video for brightness and on the right half for volume (the left/right actions, sensitivity, zones, overlay and haptics are in Settings -> YouTube Ultra -> Player -> Swipe gestures). This is the existing swipe-controls patch, surfaced; v10.13 adds no new gesture code.
- **Clean feed** (v10.13): separate switches to hide the Shorts shelves (home / subscriptions / search), the comments section and comment preview, related videos under the player, and the filter chips bar. Existing patches, surfaced.
- **Shorts** (v10.13): switches to hide the Like button and the Comments button in the Shorts player (existing patches), and a new **Skip Shorts you have seen** (`Ultra.java` + `SeenShorts.java`): a Short you stayed on for 3 seconds is remembered on the phone; when you scroll to one you already saw, the app swipes past it (never the first Short of a session, never more than 8 in a row). The list is kept on the phone only and can be cleared in Settings.
- **v10.15 to v10.20:** hold-to-share fires at 0.38 s and cancels YouTube's own hold menu; the suggestion pill and the bottom bar in Shorts are hidden by default (switches in Settings -> YouTube Ultra -> Shorts); the Shorts hold menu has a **Hide buttons / Show buttons** item (it reuses the unused Gemini slot) that flips all the Shorts hide switches together.
- **Shorts switches and gesture** (v10.14): in Settings -> YouTube Ultra -> Shorts, separate hide switches (all off by default) for the Save, Share, Remix, Sound and Subscribe buttons, the channel name and logo, and the Short title (existing patches, surfaced), plus **Hold on a Short to share it** (on by default; master switch "Shorts gestures"). Code: `module/src/paradox/dl/UltraG.java`, `UltraGLogic.java`; test `module/test/src/UltraGTest.java`. Double-tap like and tap pause/play are YouTube's own Shorts gestures. Summary lines under the YouTube Ultra settings entries were removed.
- **Queue and offline Watch later** (v10.13): add a video from the **Download** sheet ("Add to queue" / "Watch later") or from Android's Share sheet ("Add to queue (YouTube Ultra)" / "Watch later (YouTube Ultra)"). Both lists are in Settings -> YouTube Ultra and as two buttons on the Downloads page: play, play next, move up/down, remove, clear, download. A video that is downloaded plays in the built-in offline player with previous/next and automatic advance; one that is not downloaded opens in YouTube (the app moves to the next queued video when it ends). The lists are stored in app-private storage with a backup copy, like the downloads list.
- **Intro animation**: plays on the first launch after install and once after each update, in portrait or landscape (fitted, never cropped). Every other launch opens YouTube straight away. Tap to skip.
- **Installs next to the regular YouTube app** (package `app.paradox.youtube`).

## Install

Needs **Android 8.0 or newer**.

1. **Install MicroG first.** You need it to sign in with your Google account.
   Download **MicroG-RE** from the [latest release](https://github.com/chinmays-tanx09/youtube-ultra/releases/latest): `MicroG-RE_7.2.2_arm64-v8a.apk` for most phones, or `MicroG-RE_7.2.2.apk` (universal) if you're not sure.
   Official source: [MorpheApp/MicroG-RE](https://github.com/MorpheApp/MicroG-RE/releases).
2. **Install YouTube Ultra.** Download `YouTube_Ultra_v10.20.apk` from the [latest release](https://github.com/chinmays-tanx09/youtube-ultra/releases/latest) and open it. If Android asks, allow installs from your browser or file manager.
3. **Sign in.** Open YouTube Ultra, tap your profile icon, then sign in. MicroG will ask for your Google account.

You can watch without signing in. Without MicroG, YouTube Ultra still works signed out and shows a "MicroG needed to sign in" sheet.

**Updating:** install the new APK over the old one. Your settings and downloads are kept.

**"App not installed" error?** You probably have a copy signed by someone else. Uninstall it first, then install the official APK from this page.

## Official builds only

Official builds are signed with the developer's private key, and YouTube Ultra checks its own signature every time it starts. If someone modifies and re-signs it, it shows "Modified copy" and closes. Only releases from this repository's [Releases](https://github.com/chinmays-tanx09/youtube-ultra/releases) page are genuine.

## Source code

YouTube Ultra is open source under the **GPL-3.0** license (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). This repository holds everything that makes YouTube Ultra different from stock YouTube:

| Folder | What's in it |
|---|---|
| [`module/`](module) | The Paradox module, plain Java (plus a small JNI bridge to the LAME MP3 encoder in `module/jni/`): the download engine (multi-connection, resumable), download with your account, the Downloads page, quality picker, MicroG sheet, intro screen and the signature check ([`Guard.java`](module/src/paradox/dl/Guard.java)) |
| [`patches/`](patches) | The smali hooks into YouTube and the anddea extension, plus the manifest/resource edits ([what each one does](patches/README.md)) |
| [`scripts/`](scripts) | The full build: fetch pinned tools, build the module, patch, sign |
| [`branding/`](branding) | Intro animation and MicroG sheet icon (not covered by the GPL, see NOTICE) |

The YouTube app itself is not included. It's Google's, so you supply the stock APK when you build.

## Build from source

You need Linux (or WSL on Windows), **JDK 21**, Python 3, `patch`, `curl`, `zip`/`unzip`, the **Android NDK** (r27c tested; set `ANDROID_NDK_HOME`) and the **Android SDK** with `platforms;android-35` and `build-tools;35.0.0` (Android Studio installs these; point `ANDROID_HOME` at the SDK). About 3 GB of free disk.

1. Get the stock **YouTube 20.05.46** universal APK (nodpi, `com.google.android.youtube`, versionCode 1552692672).
2. Build:
   ```sh
   git clone https://github.com/chinmays-tanx09/youtube-ultra.git
   cd youtube-ultra
   export ANDROID_HOME=$HOME/Android/Sdk

   # sign with YOUR OWN keystore (create one with: keytool -genkeypair -keystore my.jks -alias me -keyalg RSA -keysize 2048 -validity 10000)
   export ULTRA_KEYSTORE=$PWD/my.jks ULTRA_KEY_ALIAS=me
   read -s ULTRA_STORE_PASS; export ULTRA_STORE_PASS

   scripts/build.sh /path/to/youtube_20.05.46_universal.apk
   ```
   The APK lands in `build/out/YouTube_Ultra.apk`. Without the `ULTRA_*` variables you get an unsigned APK instead.

`scripts/fetch-deps.sh` downloads the pinned tools (Morphe CLI 1.19.1, anddea patches 4.4.0-dev.3, Apktool 3.0.3, smali 2.5.2, jarjar, the Java libraries) into `.deps/` and checks each file's SHA-256. To work on the module alone, open `module/src` in Android Studio or any Java IDE and run `scripts/build-module.sh`.

**Your own build is not the official app.** It's signed with your key, so the signature check will show "Modified copy" unless you put your own certificate's SHA-256 into `Guard.CERT_SHA256`. If you publish a modified version, the GPL lets you, but under the [NOTICE](NOTICE) terms you must give it a different name and package, keep the credits, and not use the YouTube Ultra name, icon or intro.

## License

YouTube Ultra © 2026 Chinmay Shastry, licensed under the [GNU GPL v3.0](LICENSE) with the additional terms in [NOTICE](NOTICE). It's GPL because it bundles GPL code: [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) for downloads and [anddea's patches](https://github.com/anddea/revanced-patches). The "YouTube Ultra" and "Paradox" names, icon and intro aren't licensed for reuse.

Credits: [anddea/revanced-patches](https://github.com/anddea/revanced-patches), [Morphe](https://github.com/MorpheApp), [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), [LAME](https://lame.sourceforge.io) (MP3 encoder, LGPL), [MicroG RE](https://github.com/MorpheApp/MicroG-RE), jsoup, Mozilla Rhino, Protocol Buffers. Full list in [NOTICE](NOTICE).

YouTube Ultra isn't affiliated with or endorsed by Google or YouTube. YouTube is a trademark of Google LLC. MicroG RE is a separate app by its own developers. It's provided here unchanged from its official release.
