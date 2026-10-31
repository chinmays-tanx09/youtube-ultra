# Patches

YouTube Ultra is built in layers on top of the stock YouTube 20.05.46 APK
(which is **not** in this repository; you supply it):

1. **anddea/revanced-patches v4.4.0-dev.3**, applied with the Morphe CLI
   (ad blocking, SponsorBlock, Return YouTube Dislike, background play, spoofed
   streaming clients, MicroG support under package `app.paradox.youtube`, ...).
2. **The smali edits in `smali/`** (this folder): small hooks into YouTube and
   into the anddea extension code that route to the Paradox module.
3. **The Paradox module** (`../module/src`, plain Java): downloads, the
   Downloads page, the MicroG sheet, the intro screen and the signature check.
   It is compiled to its own dex and added as `classes13.dex`.
4. **`apply_resources.py`**: manifest and resource edits (app name, the
   Paradox activities/service/provider, download-quality settings rows).

`scripts/build.sh` runs all of it. Each `smali/classesN.diff` is a unified diff
against `baksmali d classesN.dex --debug-info false` of the anddea-patched APK,
so it applies with `patch -p1` from the folder that holds `classesN/`.

## What each smali edit does

| dex | class | change |
|---|---|---|
| classes | `PaneDescriptor` | reports browse ids to `Hooks.onBrowse` so the Downloads tab can be taken over |
| classes | `kod` | wraps the Downloads page view with `Hooks.downloadsPage` (In progress / Downloaded list) |
| classes | `gka`, `gkh` | no "Take a break" / bedtime reminders (sheet, dialog, toast and player pause) |
| classes2 | `aibt` | no "Video paused. Continue watching?" prompt or pause |
| classes2 | `wog` | removes a `finishAffinity()` that closed the app after the kids/onboarding flow |
| classes3 | `GeneralPrefsFragment` | hides the bedtime and break-reminder settings rows |
| classes3 | `glp` | take-a-break controller does nothing |
| classes8 | `MainActivity` | calls `Microg.missing()` (optional MicroG sheet + signature check) at startup |
| classes10 | `GmsCoreSupport` | replaces the "MicroG not installed" toast + browser + exit with the optional sheet, so the app keeps working signed out |
| classes10 | `IntentUtils` | the "Download" actions open `paradox.dl.DownloadActivity` instead of an external downloader app |
| classes10 | `CustomActionsPatch`, `ajuq` | Shorts ⋮ menu: "Auto scroll: On/Off" item at the top, "Send feedback" removed |
| classes10 | `aion` | caption-translation hook removed (as in the released v10.5) |
| classes12 | `CustomActionsPatch$CustomAction` | the Download / Auto scroll menu entries |
| classes12 | `Settings` | download-button overrides on by default |
| classes12 | `ExternalDownloader*Preference` | settings no longer ask for an external downloader app |
| classes12 | caption patches (`AutoCaptionsPatch`, `AutoTranslateCaptionsPatch`, `PreferredCaptionLanguagePatch`, `TranslatedCaptionPensPatch`) | their helper methods are stubbed exactly as in the released v10.5 build |

Some hunks only renumber smali labels (`:cond_29` → `:cond_2c`) because code
was inserted above them; they change nothing else.
