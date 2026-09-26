# Browser Twins

Makes **copies (clones) of apps** already on your Android phone, so each copy is a separate app with
its own data. It's mainly for browsers — Android browsers have no profiles, so a clone gives you a
"Work" and a "Home" Firefox — but any app works, e.g. a second Messenger for a second account.

> **Status: early. The app builds and its screens work, but the clone engine is not finished yet** —
> see [What works today](#what-works-today). This is a work in progress, not a usable app.

## What it does (the plan)

- **Clone browsers, and any app.** Pick an app, get a separate copy with its own logins, cookies,
  tabs and history.
- **Name and recolour each clone**, so *Firefox Work* and *Firefox Home* are easy to tell apart.
- **Keep clones updated** with the original app, keeping their data.
- **A long-press menu** on the app's icon to launch any profile.

## What works today

- The app builds and installs, with the Material 3 look of LinkPilot (dynamic colour on Android 12+).
- Setup, and the four tabs: Profiles, Menu, Log, Settings.
- Listing the apps on the phone and reading each one's APK.
- **Not yet:** the clone engine (repackage an APK with a new package name, re-sign it, install it).
  This is the hard core and the next task — see [`TODO.md`](TODO.md) (kept local) and the plan.

## Building

- Android Studio, Android SDK 35, Kotlin, Jetpack Compose (Material 3). Minimum Android 8.0.
- `gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- The release build is signed with a key outside the repo (`~/.keystores/browsertwins-signing.properties`);
  without it, it falls back to the debug key.

## Licence

MIT — see [LICENSE](LICENSE).
