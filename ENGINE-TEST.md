# Weiktube Test 0.1.0 — isolated engine feasibility build

This APK is a separate application (`com.weiktube.probe`). It does not replace
Weiktube, share its data, or enter the stable auto-update feed. Android 8 or later,
ARM64 phones and x86-64 emulators are supported. It contains no account session.

## Implemented

- GeckoView 159 stable with its own persistent app-private website profile.
- Original mobile YouTube Home, subscriptions and account pages.
- Top-level video link interception into a separate in-app Gecko player session.
  A native navigation delegate also handles full navigations and SPA URL changes.
- Feed and player share the same engine profile; no Chrome/Brave/Custom Tabs.
- One player session is reused when expanding/minimizing. Back first minimizes;
  closing the player stops and unloads it. Backgrounding pauses media.
- First-party sign-in windows remain inside the application and preserve their
  opener relationship. Non-YouTube pages show their HTTPS origin.
- Explicit web-data deletion and a user-initiated diagnostic share sheet that
  contains counters, engine readiness and Android version, never cookies or URLs.

## Acceptance gate and limitations

Real Google account sign-in and personalized recommendations have NOT been
verified. A normal GeckoView identity is used. There is no cookie import, user
agent spoofing, TV OAuth client, or bypass for an embedded-browser login block.
If Google refuses this engine, this approach has failed the authentication gate.
The native YouTube Data API sign-in is not a website session and cannot supply
the personalized Home feed.

The test player is a dedicated internal GeckoView displaying YouTube's watch
page. This is not the official native YouTube player and does not establish full
YouTube app feature parity. This test build has no YouTube ad blocker and no
automatic updater. DRM, purchases, uploads, casting, background audio and all
account mutations are outside this feasibility test.

## Remote acceptance steps

1. Install `Weiktube-Test.apk`; open **Weiktube Test** beside the existing app.
2. Open **Konto**, then use the website's regular sign-in flow with your account.
3. Return to **Startseite** and compare recommendations with YouTube for the same
   account. No substitute subscription feed counts as success.
4. Tap a recommendation: it must open inside Weiktube Test, without an external
   browser or Android app chooser. Check audio, pause, seek and full screen.
5. Back minimizes the same player; expand and check playback position. Close it
   and ensure sound stops. Background the app and ensure sound stops.
6. Restart the app and verify the website session persists. Use **Webdaten
   löschen** and verify that the test app is signed out afterward.

If sign-in fails, report only the visible error text. Do not share passwords,
cookies, tokens or account screenshots containing private details.

## Build and local validation

The test project is deliberately independent of the stable app build:

```powershell
cd engine-probe
.\gradlew.bat exportTestApk testDebugUnitTest lintDebug --no-daemon
cd ..
node scripts/test-navigation.cjs
python scripts/package-source.py
```

Requirements: JDK 17+, Android SDK platform 37.0, Build Tools 36.0.0. Set
`ANDROID_HOME` or an untracked `engine-probe/local.properties`. The wrapper pins
Gradle 9.3.1; AGP is 9.1.1. APK export is `../Weiktube-Test.apk`.
The source ZIP is produced from explicit allowlisted sources; local properties,
profiles, keys, caches and validation captures are excluded.

The JavaScript fixture checks interception, foreign/auth-page isolation,
trusted-click filtering, role isolation, disconnect and background-pause policy.
JVM tests check URL shapes, malformed/foreign URLs, timestamps and playlist bounds.
Neither proves actual Google sign-in or device playback.

Validation on 2026-11-03: the APK compiles and its v2 signature verifies; all four
JVM tests and the JavaScript fixture pass. Android Lint reports no errors. An
Android 35 x86-64 emulator was installed locally but failed at startup with
Windows exit code 0xC0000005; no emulator UI, playback or login test passed.
No physical phone was accessed. Account sign-in, recommendation identity,
session persistence, actual playback and visual mini-player behavior remain
remote acceptance checks. Do not promote this prerelease to stable before they
pass.
