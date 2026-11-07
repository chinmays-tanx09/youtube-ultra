#!/usr/bin/env python3
"""Resource + manifest edits for YouTube Ultra, applied to an `apktool d -s` tree
of the Morphe/anddea-patched YouTube 20.05.46 APK.

Usage: apply_resources.py <decoded_dir> [--version-code N]
Every edit asserts that it found what it expects, so a different base fails loudly
instead of producing a half-patched app.
"""
import re, sys, pathlib

PKG = "app.paradox.youtube"

def edit(path, fn):
    p = pathlib.Path(path); s = p.read_text(encoding="utf-8"); n = fn(s)
    if n == s: raise SystemExit(f"no change made to {path}")
    p.write_text(n, encoding="utf-8")

def once(s, old, new, count=1):
    c = s.count(old)
    if c != count: raise SystemExit(f"expected {count} x {old[:70]!r}, found {c}")
    return s.replace(old, new)

def pref_block(indent):
    i = indent
    out = []
    for which, title in (("video", "Video download quality"), ("audio", "Audio download quality")):
        out.append(f'{i}<Preference android:persistent="false" android:title="{title}" android:key="ytpm_{which}_quality" android:summary="Ask each time, or always use one quality" app:iconSpaceReserved="false">\n'
                   f'{i}    <intent android:targetPackage="{PKG}" android:targetClass="paradox.dl.DownloadSettingsActivity">\n'
                   f'{i}        <extra android:name="which" android:value="{which}" />\n'
                   f'{i}    </intent>\n'
                   f'{i}</Preference>\n')
    return "".join(out)

PARADOX_COMPONENTS = """        <activity android:configChanges="keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize" android:excludeFromRecents="false" android:exported="true" android:label="YouTube Ultra" android:name="paradox.dl.IntroActivity" android:screenOrientation="unspecified" android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:excludeFromRecents="true" android:exported="true" android:label="Download (YouTube Ultra)" android:name="paradox.dl.DownloadActivity" android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter android:label="Download (YouTube Ultra)">
                <action android:name="android.intent.action.SEND"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="text/plain"/>
            </intent-filter>
        </activity>
        <activity android:exported="true" android:label="Downloads" android:name="paradox.dl.DownloadsActivity" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:excludeFromRecents="true" android:exported="false" android:label="Open" android:name="paradox.dl.OpenActivity" android:noHistory="true" android:taskAffinity="" android:theme="@android:style/Theme.Translucent.NoTitleBar"/>
        <activity-alias android:exported="false" android:name="paradox.dl.PlayerActivity" android:targetActivity="paradox.dl.OpenActivity"/>
        <provider android:authorities="app.paradox.youtube.ytpm.files" android:exported="false" android:grantUriPermissions="true" android:name="paradox.dl.FileShare"/>
        <activity android:exported="false" android:label="Download quality" android:name="paradox.dl.DownloadSettingsActivity" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:configChanges="keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize" android:excludeFromRecents="true" android:exported="false" android:label="MicroG" android:launchMode="singleTop" android:name="paradox.dl.MicrogActivity" android:theme="@android:style/Theme.Translucent.NoTitleBar"/>
        <service android:exported="false" android:foregroundServiceType="dataSync" android:name="paradox.dl.DownloadService"/>
"""

def manifest(s):
    # The intro screen becomes the only launcher entry (it opens YouTube's home after 2 s).
    s = re.sub(r"<intent-filter[^>]*>.*?</intent-filter>",
               lambda m: re.sub(r'\n\s*<category android:name="android.intent.category.LAUNCHER"/>', "", m.group(0)), s, flags=re.S)
    s = once(s, 'android:hardwareAccelerated="true"', 'android:extractNativeLibs="true" android:hardwareAccelerated="true"')
    s = once(s, 'android:label="@string/morphe_custom_branding_name_custom"', 'android:label="@string/application_name"')
    s = once(s, "    </application>", PARADOX_COMPONENTS + "    </application>")
    # Renamed install permission: the MicroG sheet now links to the release page instead of installing.
    s = once(s, "</manifest>", f'    <uses-permission android:name="{PKG}.permission.NO_INSTALLER"/>\n</manifest>')
    return s

def strings(s):
    s = once(s, '<string name="application_name">YouTube</string>', '<string name="application_name">YouTube Ultra</string>')
    s = once(s, '<string name="morphe_custom_branding_name_custom">YouTube</string>', '<string name="morphe_custom_branding_name_custom">YouTube Ultra</string>')
    s = once(s, '<string name="revanced_shorts_custom_actions_external_downloader_label">External downloader</string>',
                '<string name="revanced_shorts_custom_actions_external_downloader_label">Download</string>')
    return s

def attrs(s):
    # aapt2 rejects the patcher's <item type="attr"> form for these two; declare them properly.
    s = once(s, '<item type="attr" name="snackBarColor" format="string" />', '<attr name="snackBarColor" format="color|reference" />')
    s = once(s, '<item type="attr" name="splashScreenColor" format="string" />', '<attr name="splashScreenColor" format="color|reference" />')
    return s

def shortcuts(s):
    return once(s, 'android:targetPackage="com.google.android.youtube"', f'android:targetPackage="{PKG}"', 3)

def offline(s, first_child=False):
    # Our download-quality screens replace YouTube's own offline-quality picker.
    if first_child:   # flat screen: our two entries go first
        m = re.search(r'\n( *)<com\.google', s)
    else:             # inside the "Downloads" category, above Smart downloads
        m = re.search(r'\n( *)<com\.google\.android\.libraries\.youtube\.common\.ui\.preferences\.ProtoDataStoreSwitchPreference [^\n]*android:key="smart_downloads_opted_in"', s)
    if not m: raise SystemExit("offline preference anchor not found")
    s = s[:m.start()+1] + pref_block(m.group(1)) + s[m.start()+1:]
    return once(s, 'android:key="offline_quality" app:iconSpaceReserved="false" />', 'android:key="offline_quality" app:iconSpaceReserved="false" app:isPreferenceVisible="false" />')

def apktool_yml(s, vc):
    s = re.sub(r"versionCode: \d+", f"versionCode: {vc}", s)
    if "\n- mp4\n" not in s: s = once(s, "\n- ogg\n", "\n- mp4\n- ogg\n")
    return s


# ---------------------------------------------------------------- v10.13: Settings -> "YouTube Ultra" + queue / watch-later components
ULTRA_ENTRY = """    <Preference android:icon="@drawable/ytultra_settings_key_icon" android:title="YouTube Ultra" android:key="ytultra_settings_key">
        <intent android:targetPackage="app.paradox.youtube" android:data="revanced_settings_intent" android:targetClass="com.google.android.libraries.social.licenses.LicenseActivity">
            <extra android:name="ytultra" android:value="1" />
        </intent>
    </Preference>
"""
ULTRA_COMPONENTS = """        <activity android:exported="false" android:label="Queue &amp; Watch later" android:name="paradox.dl.QueueActivity" android:theme="@android:style/Theme.Material.NoActionBar"/>
        <activity android:configChanges="keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize" android:exported="false" android:label="Offline player" android:name="paradox.dl.OfflinePlayerActivity" android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"/>
        <activity android:excludeFromRecents="true" android:exported="true" android:label="Add to queue (YouTube Ultra)" android:name="paradox.dl.QueueAddActivity" android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter android:label="Add to queue (YouTube Ultra)">
                <action android:name="android.intent.action.SEND"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="text/plain"/>
            </intent-filter>
        </activity>
        <activity android:excludeFromRecents="true" android:exported="true" android:label="Watch later (YouTube Ultra)" android:name="paradox.dl.WatchLaterAddActivity" android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter android:label="Watch later (YouTube Ultra)">
                <action android:name="android.intent.action.SEND"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="text/plain"/>
            </intent-filter>
        </activity>
        <activity android:excludeFromRecents="true" android:exported="false" android:label="YouTube Ultra" android:name="paradox.dl.UltraActionActivity" android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar"/>
"""

def ultra_settings_entry(s):
    if "ytultra_settings_key" in s: raise SystemExit("already applied")
    m = re.search(r'<Preference [^>]*android:key="revanced_settings_key"', s)   # the RVX entry (attribute order differs between the three files)
    if not m: raise SystemExit("revanced settings entry not found")
    i = m.start()
    ls = s.rfind("\n", 0, i) + 1
    return s[:ls] + ULTRA_ENTRY + s[ls:]

def ultra(d, here):
    d = pathlib.Path(d)
    edit(d/"AndroidManifest.xml", lambda s: once(s, "    </application>", ULTRA_COMPONENTS + "    </application>"))
    for f in ("settings_fragment.xml", "settings_fragment_cairo.xml", "settings_fragment_legacy.xml"):
        edit(d/"res/xml"/f, ultra_settings_entry)
    import shutil
    shutil.copy(here/"res/xml/ytultra_prefs.xml", d/"res/xml/ytultra_prefs.xml")
    shutil.copy(here/"res/drawable/ytultra_settings_key_icon.xml", d/"res/drawable/ytultra_settings_key_icon.xml")
    print("v10.13 resources added")

def main():
    d = pathlib.Path(sys.argv[1]); vc = "1552692680"
    if "--only-ultra" in sys.argv:   # apply just the v10.13 additions to a tree that already has the v10.12 edits
        ultra(d, pathlib.Path(__file__).parent)
        if "--version-code" in sys.argv: edit(d/"apktool.yml", lambda s: apktool_yml(s, sys.argv[sys.argv.index("--version-code")+1]))
        return
    if "--version-code" in sys.argv: vc = sys.argv[sys.argv.index("--version-code")+1]
    edit(d/"AndroidManifest.xml", manifest)
    edit(d/"res/values/strings.xml", strings)
    edit(d/"res/values/attrs.xml", attrs)
    edit(d/"res/xml/main_shortcuts.xml", shortcuts)
    edit(d/"res/xml/offline_prefs.xml", offline)
    edit(d/"res/xml/offline_prefs_without_category_dividers.xml", lambda s: offline(s, first_child=True))
    edit(d/"apktool.yml", lambda s: apktool_yml(s, vc))
    ultra(d, pathlib.Path(__file__).parent)
    print("resources patched, versionCode", vc)

if __name__ == "__main__": main()
