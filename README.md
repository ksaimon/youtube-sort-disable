# FocusTube (starter)
Android app (Kotlin, AccessibilityService) that hides YouTube Shorts + home feed in the YouTube app and in Chrome/Brave/Edge/Samsung Internet.

## Build
1. Install Android Studio (Koala+) with JDK 17 and Android SDK 34.
2. Open this folder -> let Gradle sync (Android Studio creates the Gradle wrapper).
3. Run on a device (USB debugging) or Build > Build APK.
CLI: `gradle wrapper --gradle-version 8.7 && ./gradlew assembleDebug`
APK: app/build/outputs/apk/debug/app-debug.apk

## Release builds

Release builds use R8 and resource shrinking. To sign them, keep the keystore and credentials outside this repository and add these entries to `~/.gradle/gradle.properties`:

```properties
FOCUSTUBE_STORE_FILE=/absolute/path/to/release-key.jks
FOCUSTUBE_STORE_PASSWORD=your-store-password
FOCUSTUBE_KEY_ALIAS=your-key-alias
FOCUSTUBE_KEY_PASSWORD=your-key-password
```

All four signing properties are required together. If omitted, local release outputs are unsigned. Set `FOCUSTUBE_VERSION_CODE` and `FOCUSTUBE_VERSION_NAME` as Gradle properties to override the defaults (`1` and `1.0`) for a release, for example `./gradlew bundleRelease -PFOCUSTUBE_VERSION_CODE=2 -PFOCUSTUBE_VERSION_NAME=1.1`. The adaptive launcher icon is defined in `mipmap-anydpi-v26/ic_launcher.xml`.

## Use
Open FocusTube -> Open Accessibility settings -> Downloaded apps -> FocusTube -> ON.
Android 13+: if the toggle is greyed out, App info -> ⋮ -> "Allow restricted settings", then retry.
Samsung: also set battery to "Unrestricted" for FocusTube.

## How it works
- YouTube app: detects Shorts player view IDs -> presses Back; draws opaque overlays over Shorts tab, Shorts shelves, home feed.
- Browsers: reads URL bar; /shorts/ID -> reopens as /watch?v=ID; youtube.com home -> /feed/subscriptions.
See AGENTS.md to extend / fix when YouTube changes its UI.

## Refreshing selectors after a YouTube update

1. On a device with USB debugging enabled, open YouTube to Home, Shorts, Subscriptions, and a Shorts player in turn. For each screen, run `adb shell uiautomator dump /sdcard/screen.xml && adb pull /sdcard/screen.xml dumps/<screen>.xml` (use `home`, `shorts`, `subs`, and `player` as the screen names). Also capture YouTube search/channel pages and browser address bars when those behaviors change.
2. Search the XML for the affected visible labels and inspect nearby `resource-id`, `content-desc`, `class`, `bounds`, and `selected` attributes. Prefer resource IDs and descriptions that occur on the relevant screen and are not tied to changing video titles.
3. Update the corresponding documented selector or fallback in `Selectors.kt`. Keep more than one signal where possible; position and class checks should be fallbacks when stable IDs or descriptions are unavailable.
4. Compare fresh dumps for at least one supported Android version and screen size. Run `./gradlew lint testDebugUnitTest assembleDebug` before committing. Do not include personal account or feed content in committed dumps; redact it or keep local copies untracked.
