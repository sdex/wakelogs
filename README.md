# wakelogs

wakelogs is an open-source Android app for analyzing display wakeups,
CPU wakeups, background activity, and periods of device rest.

The app helps users understand why an Android device became active
and which system or app activity may have contributed.

## Features

- Display wakeup detection
- CPU and background wakeup analysis
- Device-rest analysis
- Source statistics and event timeline
- Shizuku-based system diagnostics
- Local report export
- Configurable detail levels and appearance

## Privacy

wakelogs processes diagnostic and event information locally on the
Android device.

The app contains no advertising, analytics, or tracking.

Reports are shared only when the user explicitly chooses to export
or share them.

## Requirements

- Android 8.0 or newer
- Notification access for notification-related analysis
- Shizuku for extended system diagnostics

Some features remain available without Shizuku, but system-level
diagnostic information may be limited.

## Build

Build the debug APK with:

    ./gradlew assembleDebug

The APK is created at:

    app/build/outputs/apk/debug/app-debug.apk

Build the release APK with:

    ./gradlew assembleRelease

## Package name

    de.sanniki.wakesleuth

The visible app name is **wakelogs**.

## License

wakelogs is licensed under the GNU General Public License,
version 3 or later.

SPDX-License-Identifier: GPL-3.0-or-later
