# Paste this complete prompt into Codex

Work on the FocusTube Android project in this repository, imported from my uploaded FocusTube.zip. In the prepared cloud environment the checkout is `/workspace/youtube-sort-disable`; use the actual checkout root if its mount path differs. Use the existing workspace. Do not create a worktree, push, or publish anything without an explicit request.

I want two changes:

1. Fix Shorts disabling in Chrome. It works in the native YouTube app, but in Chrome both the Shorts tab remains visible and Shorts still play. Cover the Shorts navigation tab and shelves when the corresponding setting is enabled, and stop Shorts playback when “Block Shorts player” is enabled. Preserve native YouTube behavior and existing feature toggles.
2. Add an optional “Cover images and videos” feature for my personal visual filtering. I have chosen to cover all images and videos, rather than infer whether a person is a woman. Do not add gender, religion, or identity classification. Use an opaque cover where genuine blur of another app's content is unavailable. Keep this new feature off by default.

Inspect the source and project guidance, but do not implement unrelated TODO items. The actual build uses compile/target SDK 36, Android Gradle Plugin 8.13.2, Kotlin 2.3.10, and Gradle 8.13. The README/AGENTS references to SDK 34 and generating Gradle 8.7 are stale; do not downgrade the current toolchain.

The uploaded source has been left unchanged during cloud setup. Its baseline debug build succeeded, all 6 existing URL tests passed, and lint reported 0 errors and 8 warnings. These checks do not establish that Shorts blocking works on a device. Native ADB could not initialize its preferences in this cloud machine's read-only account home; run the device commands on a machine with a connected Android device and working ADB.

Start with these files:

- `app/src/main/java/com/focustube/app/FocusService.kt`
- `app/src/main/java/com/focustube/app/Selectors.kt`
- `app/src/main/java/com/focustube/app/YouTubeUrlDecisions.kt`
- `app/src/main/java/com/focustube/app/Prefs.kt`
- `app/src/main/java/com/focustube/app/MainActivity.kt`
- `app/src/main/res/xml/accessibility_config.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/focustube/app/YouTubeUrlDecisionsTest.kt`
- `dumps/chrome_shorts.xml`

Chrome fix requirements:

- Reproduce the failure before changing behavior. The supplied `chrome_shorts.xml` shows a YouTube Home page, an unfocused Chrome URL field whose text is only `m.youtube.com`, and a visible clickable Shorts footer tab. It is not a Shorts-player dump. Use it to test tab detection, not to claim player verification.
- Chrome can expose only the origin in the URL field. Do not depend exclusively on `/shorts/VIDEO_ID` being visible, and do not click or focus the URL field to discover the path.
- Investigate bounded scans, excessive wrapper traversal, depth limits, visibility checks, and browser-toolbar pruning. Make sure a short scan does not silently miss web navigation controls or remove working covers. Prefer stable IDs and direct semantic lookups where supported; keep traversal bounded.
- Cover the entire clickable Shorts tab, including its icon, rather than just its text. Deduplicate parent/child matches and keep masks aligned while scrolling, changing orientation, and showing/hiding Chrome's toolbar.
- For a complete Shorts URL, keep the existing conversion to the standard watch URL, with the cooldown and redirect-loop guard. When the URL path is unavailable, use strong page signals, such as a selected Shorts navigation tab or a confirmed Shorts player, to leave Shorts safely. Provide a safe browser navigation fallback when Back cannot leave a directly opened Shorts page, without causing redirect loops.
- A generic Shorts link plus a Play/Pause button is not enough to classify a page as a Shorts player. Normal watch pages, search results, channels, and unrelated websites must remain usable.
- Preserve URL host validation, query handling, and rejection of lookalike domains. Never infer that origin-only address text proves the Home page.
- Clear stale covers when the user edits the URL, changes tabs or sites, disables a feature, pauses FocusTube, leaves focus hours, or switches apps. Ensure accessibility covers do not interfere with retrieving the underlying application window.
- Check accessibility-node ownership, recycling on supported Android versions, exception handling, and startup scanning. Do not add unbounded scans or continuous busy polling.

Images/videos cover requirements:

- Add a separate preference and clear UI control. Keep it independent of the Shorts toggles, with the existing master toggle, schedule, and pause controls applying to it.
- Cover accessible media regions in the YouTube app and supported browsers, starting with Chrome. Use validated media IDs, roles/classes, and bounds; avoid masking toolbars, address fields, browser navigation, or every UI icon just because it is an ImageView.
- Use accessibility overlays with correctly clipped geometry. Update existing masks when media moves, remove obsolete masks, and keep the number of overlays bounded.
- Do not use `RenderEffect` or a blurred translucent overlay and claim it blurs the underlying YouTube/Chrome content: these do not provide that capability by themselves. An opaque cover is acceptable and should be accurately described in the UI.
- Accessibility trees may omit thumbnails or video geometry. Do not promise that every frame or image is covered. Provide an explicitly selected “Cover page” fallback for the web-content area, or “Cover app content” fallback for supported apps, when reliable media bounds are unavailable. Tell the user that this stronger fallback also hides text/content. Keep pause/reveal controls reachable outside the cover.
- Do not silently capture screenshots or screen recordings. This first implementation should use accessibility bounds and covers without adding screenshot, recording, Internet, or storage permissions. If stronger detection requires a different approach, explain the limitation and finish the supported cover mode first.
- Do not store or transmit screen content. Remove debug logs that expose raw browsing URLs or on-screen text while preserving useful metadata such as node counts, resource IDs, truncation flags, and action results. Update the disclosure/privacy text to describe the new local cover feature accurately.
- Handle Android 26+ behavior, rotation, window resizing, picture-in-picture, and app/window changes without crashing or masking system controls. Document unsupported cases rather than claiming complete protection.

Validation and delivery:

- Add focused regression tests for Chrome footer-tab detection using the supplied XML, origin-only URL handling, selected Shorts navigation, direct Shorts URLs, normal watch pages with Shorts recommendations, unrelated sites, scan truncation, duplicate matches, toggles, and overlay target geometry. Add tests for media cover target selection and the fallback mode. Extract pure decisions where helpful so JVM tests can validate them.
- Use this build command in the prepared cloud environment. On a different machine, install JDK 17 and Android SDK 36, then run `./gradlew --no-daemon --max-workers=2 --console=plain lint testDebugUnitTest assembleDebug` from the actual repository root:

```bash
cd /workspace/youtube-sort-disable
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/.android
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_HOME=/workspace/toolchains/jdk-17.0.20.1+1
/workspace/toolchains/gradle-8.13/bin/gradle --no-daemon --max-workers=2 --console=plain lint testDebugUnitTest assembleDebug
```

- Investigate any failing check. Distinguish an existing build problem from a regression introduced by your changes. Do not change checksums, disable TLS verification, skip assertions, or report skipped/unrun tests as passing.
- If a real Android device is available, install the debug APK, enable FocusTube's accessibility service through Android settings, and test YouTube Home, Shorts, a normal watch page, search/channel pages, Chrome tabs, scrolling, pause, and all relevant toggle combinations. Capture fresh, redacted UI dumps for Chrome Home and Chrome Shorts playback. Otherwise, provide these device commands and a manual checklist, and explicitly say that device behavior remains unverified.
- Deliver a concise explanation of the diagnosed cause and changes, actual test counts/results, a debug APK if the build succeeds, the updated source ZIP, and a patch against the uploaded source. State any remaining limitations. Do not sign a release APK with invented credentials or claim that code has been deployed.

Useful device commands (run where an Android device is connected):

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
adb shell uiautomator dump /sdcard/chrome_home.xml
adb pull /sdcard/chrome_home.xml dumps/chrome_home.xml
# Open a Shorts player in Chrome manually, then capture its different screen:
adb shell uiautomator dump /sdcard/chrome_shorts_player.xml
adb pull /sdcard/chrome_shorts_player.xml dumps/chrome_shorts_player.xml
adb logcat -s FocusTube
```

Keep personal feed/account information out of shared dumps and logs.
