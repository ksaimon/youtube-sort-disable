# Instructions for coding agents (Codex / Claude Code)

Project: FocusTube, Android, Kotlin, AccessibilityService. Package com.focustube.app. minSdk 26, target 34.

## Goals / TODO (in order)
1. Make `./gradlew assembleDebug` pass. Generate wrapper first: `gradle wrapper --gradle-version 8.7`.
2. Verify against a real device/emulator with the YouTube app + Chrome installed:
   - `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml`
   - Open YouTube Home, Shorts tab, Shorts player and dump each. Find resource-ids / content-descriptions
     and update `shortsPlayerIds`, `shortsShelfIds`, `homeFeedId` in FocusService.kt.
3. Overlays follow scrolling poorly: re-run on TYPE_VIEW_SCROLLED without throttle, or instead of overlays,
   try ACTION_SCROLL_FORWARD / hiding via parent bounds. Reduce flicker.
4. Add tests (unit tests for URL parsing; extract it into a pure function).
5. Add UI: per-feature toggles for Subscriptions-only, hide comments, hide recommendations on watch page,
   hide Explore/Trending, schedule (focus hours), and a "pause for 10 min" button.
6. Add Firefox for Android (org.mozilla.firefox) support (URL bar id differs: mozac_browser_toolbar_url_view).
7. Add a release build config (signing via local.properties, never commit keystore).

## Rules
- Never collect or transmit user data; no INTERNET permission.
- Keep accessibility config limited to listed packages.
- Keep code compiling after every change; run `./gradlew lint assembleDebug`.

## Useful commands
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s FocusTube
