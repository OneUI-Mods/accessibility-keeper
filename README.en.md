# Accessibility Keeper

[日本語](README.md) · [English](README.en.md)

A Vector / Xposed module that keeps an app's accessibility permission when the app is force stopped. Supports Android 17 with libxposed API 102.

## Features

- A force-stopped app's accessibility services stay in the enabled list, and their accessibility shortcuts stay too.
- Choose what happens after a force stop:
  - **Wait until the app is opened again** (default): the service resumes when you open the app. Until then Settings may show it as not working.
  - **Reconnect right away**: the accessibility service starts again immediately.
- Every app that offers an accessibility service is listed and can be turned on or off individually. All apps are on by default.
- Supports Vector hot updates without restarting the device or the system.

The force stop itself still ends the app's process as before.

## Requirements

- Validated on the Galaxy S26 Ultra (SM-S948Q), Android 17 / One UI 9 (BZI9).
- Vector (libxposed API 102).

## Usage

1. Install the signed APK.
2. Enable Accessibility Keeper in Vector. Its scope is System Framework.
3. Restart the device once. Later updates apply through hot updates.
4. Open the app and choose the behaviour after a force stop and which apps it covers.

## Limitations

- Apps force stopped before the system first starts after a reboot are not covered.
- While waiting, Settings may show the service as not working.

## License

MIT License. Licenses of the libraries used are available in the app.
