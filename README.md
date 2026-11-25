# Fire Home

A Fire TV-style home screen for Android TV / LineageOS TV. Original code and
layout; no Amazon assets or branding.

## What it does

- Top bar with **Home / Apps** tabs and a clock
- A hero area that shows the app you currently have focused
- **Your apps** row (leanback banners when an app has one, icons otherwise)
- **Quick settings** row (Settings, Wi-Fi, Bluetooth, Display, Sound, All apps)
- **Apps** tab with a grid of everything installed
- Tiles scale up with a white ring on focus; the name fades in
- Hold OK on an app to open its App info (uninstall, force stop, etc.)
- Home button returns to the first tile. Back never exits the launcher.
- Picks up installs and uninstalls live

## Build

1. Open this folder in **Android Studio** (Koala or newer) and let it sync.
   It needs internet the first time to fetch the Android Gradle Plugin and
   RecyclerView.
2. **Build > Build APK(s)**. The APK lands in `app/build/outputs/apk/debug/`.

Or from a terminal with the Android SDK installed (`ANDROID_HOME` set) and
Gradle 8.7 or newer:

    gradle assembleDebug

## Install on the Fire TV Stick

Enable ADB debugging and note the stick's IP address (on LineageOS TV:
Settings > System > About, then tap Build number 7 times, then
Developer options > USB debugging / Network debugging).

    adb connect <stick-ip>:5555
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Make it the default home

Press the Home button. Android asks which home app to use. Choose
**Fire Home** and tap **Always**. If it doesn't ask:

    adb shell cmd package set-home-activity com.example.firehome/.MainActivity

To go back to the stock launcher, uninstall Fire Home (it then falls back to
the previous home app):

    adb uninstall com.example.firehome

## Tweaking

- Tile size: `app/src/main/res/layout/item_tile.xml` (152dp x 86dp is 16:9)
- Colors: `res/drawable/bg_gradient.xml` and `tile_bg.xml`
- Quick settings entries: `buildQuickSettings()` in `MainActivity.java`
- Grid columns on the Apps tab: `new GridLayoutManager(this, 5)`
