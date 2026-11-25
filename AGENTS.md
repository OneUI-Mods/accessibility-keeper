# Accessibility Keeper

- Keep the Android implementation in Kotlin and support Android 17 with libxposed API 102.
- Hooks run only in system_server and only change what a force stop does to accessibility services. Do not grant, enable or bind services the user has not enabled, and do not touch other permissions.
- No background/foreground service, periodic worker, alarm or polling loop. The only listener is a package-unstopped broadcast while services are waiting.
- Keep hot updates working: retire hooks and the receiver in `onHotReloading`, carry waiting services into the next generation.
- Every APK installed on the device gets a new versionCode/versionName. Install over the network with `adb install --no-incremental`.
- Release APKs only, continuous signing identity (`.signing/`, never in Git), public source, matching source archive and hashes. Follow `docs/releasing.md`.
