# Bigme Agenda

A Czech-language Android app that turns one CalDAV calendar into a seven-day PNG agenda for the **Bigme HiBreak** screensaver. Bigme draws the clock; the app updates the agenda. Your usual calendar app handles editing and reminders. Google Calendar and DAVx⁵ are not required.

## Requirements

- **HiBreak, Android 14, 824 × 1648 px**, with `com.xrz.standby` 1.13.13. The image size and space reserved for the clock are fixed; other models and firmware are untested.
- One HTTPS CalDAV **calendar collection** with username/password authentication. The server must support bounded [CalDAV expansion](https://www.rfc-editor.org/rfc/rfc4791.html#section-9.6.5) for recurring events. Tested with DAViCal Go.
- The app runs on API 29 or newer and targets API 34. Its interface is currently Czech.

## Setup

1. Build and install the app as described below.
2. Enter the **full collection URL**, for example `https://calendar.example.com/user/calendar/`, your username and password. Use the final HTTPS URL ending in `/`; redirects, credentials in the URL and query parameters are rejected. Press **Uložit a načíst** (Save and load).
3. After a successful load, open **Nastavení spořiče Bigme** (Bigme screensaver settings). Choose **Picture**, select only the generated file in `Pictures/BigmeAgenda`, and turn **Crop wallpaper off**. The app shows the exact filename, normally `agenda.png`.
4. **Enable Bigme's native clock and a one-minute clock refresh.** The native refresh reloads the changed image. Keep **Text display: Do not show**. Picture mode has its own fixed clock size; the larger clocks from Bigme's calendar mode are unavailable here.
5. Put the phone to sleep and check the panel. **Obnovit** (Refresh) requests a calendar fetch; the app shows the last successful fetch time.

A blank password field keeps the saved password only while the URL and username remain unchanged. Enter the password again when changing either. After reinstalling, Android may choose a filename such as `agenda (1).png`; select the new file shown by the app. Normal updates preserve the same MediaStore row, URI and path.

## Updates and limits

The agenda includes today and six following local calendar days, timed and all-day events, and ongoing events. Finished events disappear. Long titles use at most two lines; overflow is counted. The upper 420 pixels stay free for Bigme's clock. Local events and edits that your calendar app has not uploaded to CalDAV will not appear.

Calendar fetches and cached redraws are scheduled about every **30 minutes**, with additional cached redraws around event starts, ends and local midnight. **Android Doze and manufacturer power saving can delay them.** Exact update times are not guaranteed. The app has no minute alarm, notification service, reminders or clock of its own. Bigme refreshes its clock independently. Open the app again after force-stopping it.

If the URL and username are unchanged, failed fetches keep the last successful calendar data. Offline redraws can still hide finished events; the image marks old data when its cached range no longer covers the displayed week. Unchanged visible content does not rewrite the PNG.

Saving a different URL or username clears the cached events. The previous PNG and last successful fetch time remain visible until the new source loads successfully; without cached events, the old image cannot be redrawn offline.

The server must expand recurrence rules and exceptions. Expanded responses must omit `VTIMEZONE` definitions; these are rejected before the calendar library interprets them. Unexpanded rules, unknown time zones, malformed calendar data and incomplete CalDAV responses are reported as errors rather than an empty calendar. Responses are bounded to 2 MiB after decompression and 4,000 occurrences.

Existing-image writes through MediaStore are **not atomic for Bigme's direct file reader**. A reader can briefly encounter an incomplete write; the app keeps a private last-good copy for recovery.

An earlier version was verified on the phone for real calendar loading, stable-file updates, unchanged-output handling, and the sleeping panel with Bigme's native clock. The latest calendar-parsing and scheduling changes have passed automated tests but have not yet been installed or tested on the phone. Restart persistence, long-term battery use and update timing in Doze remain untested.

## Privacy

The app only reads CalDAV using `REPORT`. It does not modify calendars, write Android CalendarProvider, or read another calendar app's private database. It does not request calendar, contacts or broad media/storage access. Android WorkManager supplies network-state, wake-lock and boot scheduling permissions in addition to Internet access.

Credentials and cached events are encrypted with an Android Keystore key and excluded from backups. HTTPS uses normal certificate and hostname checks; redirects never receive credentials. There is no analytics or event/credential logging.

**The generated PNG is shared media:** event titles can be seen in the gallery and on the locked phone. A recovery copy is also stored privately by the app.

## Build and test

Use **JDK 17**, Android SDK platform **35** and Build Tools **35.0.0**. Install Android SDK Platform-Tools for the `adb` command below. Set `ANDROID_HOME`, or provide an untracked `local.properties` with `sdk.dir`. The first build needs Internet access to download Gradle and dependencies.

```sh
./gradlew --project-cache-dir .ai/gradle-project :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install .ai/build/app/outputs/apk/debug/app-debug.apk
```

Application ID: `one.jiri.bigmeagenda`. Build outputs and reports go to `.ai/build/`. The debug APK is for local installation; keep the same signing key when updating an existing installation.

Tests use synthetic calendars, local HTTPS servers and Robolectric. They do not need an account, phone or emulator. Coverage includes recurrence exceptions, time zones and DST, XML/TLS failures, rendering, cache serialization, cancelled boundary work and recovery of a stable MediaStore image.

## License

Copyright (C) 2026 Jiří Němec. Licensed under **GPL-3.0-or-later**; see [LICENSE](LICENSE). Third-party notices and licenses are in [app/src/main/assets/licenses](app/src/main/assets/licenses).

Developed with AI assistance.
