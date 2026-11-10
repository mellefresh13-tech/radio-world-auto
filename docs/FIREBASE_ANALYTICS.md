# Firebase Analytics and Crashlytics

## Firebase project and SDKs

- Firebase project: `radio-world-auto`
- Android application ID: `com.mellefresh13.radio`
- Configuration: `android/app/google-services.json`
- SDKs: Firebase Analytics and Firebase Crashlytics
- Firebase Android BoM: `34.19.0`
- Google Services Gradle plugin: `4.5.0`
- Crashlytics Gradle plugin: `3.0.8`

The Firebase configuration contains app/project identifiers and client configuration. Never add service-account credentials or private keys to the Android app or repository.

## Custom Analytics events

The following events are logged by `AppAnalytics.kt`:

| Event | Parameters | Meaning |
| --- | --- | --- |
| `radio_app_open` | `ui_profile`, `app_language`, `app_theme` | App launch |
| `station_play` | `station_id`, optional `station_name`, `station_genre`, `station_country`, `ui_profile` | Playback entered the playing state |
| `station_listen` | `station_id`, optional station details, `listen_seconds` | A listening segment ended |
| `search_used` | optional `search_query`, `result_count`, `ui_profile` | Catalog search usage |
| `favorite_added` / `favorite_removed` | `station_id`, optional station details | Favorite changed |
| `playback_error` | optional `station_id`, `error_code`, `ui_profile` | Player reported a playback error |
| `screen_opened` | `screen_name` | Main screen opened |

Station details are limited in length before logging. Listening segments shorter than 5 seconds are ignored; reported durations are capped at 4 hours. The final segment may not be reported if the process is force-stopped before the stop/pause/station-change handling runs.

## User properties

- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected app locale (for example `en` or `ru`)
- `app_theme`: `system`, `light`, or `dark`
- `app_version`: app version name

These properties are refreshed on app launch. The app does not set a Firebase User ID.

## Search query filtering

The query is trimmed and excluded if it contains `@`, `://`, or seven or more digits. Remaining text is filtered to letters, digits, spaces and a limited set of punctuation, normalized, and truncated to 40 characters. A query shorter than two characters is not included. The `search_used` event and result count can still be logged without the query.

This is a best-effort privacy filter, not a guarantee that all remaining search text is non-personal. Do not expand the set of transmitted query data without reviewing the privacy policy and data-safety disclosures.

## Advertising ID and automatic collection

The Android manifest disables Analytics Advertising ID collection and ad-personalization signals. This does not disable Analytics. Firebase Analytics may also collect SDK-defined app/session/device information and use a pseudonymous app-instance identifier. Do not describe the app as having no analytics or no telemetry.

The app does not request location permission. Network operators and Google services may process IP/network information under their own policies; do not describe this as precise GPS collection by the app.

## Crashlytics

Firebase Crashlytics is included in the Android app and collection is active by default in this build. It automatically sends supported crash and ANR diagnostics to the Firebase project. The app adds two custom keys:
- `ui_profile`
- `app_version`

Reports may include stack traces and technical app/device diagnostics. The local `CrashReporter` is a separate mechanism: it saves the last uncaught exception to a local file and offers the user a copy dialog on the next launch. It does not replace Crashlytics.

## Consent and public distribution

The current app does not expose in-app Analytics or Crashlytics opt-out controls. Before public distribution, review applicable consent and disclosure requirements for the countries where the app is available. Keep the public privacy policy and Google Play Data safety answers aligned with the SDK behavior and the actual SDK data disclosures. This document is an engineering inventory, not legal advice or a substitute for Google's current SDK disclosure documentation.

## Local Analytics verification

1. Install a debug build on a Google Play services-enabled Android device.
2. Enable Analytics DebugView:
   ```bash
   adb shell setprop debug.firebase.analytics.app com.mellefresh13.radio
   ```
3. Open the app, start a station, search, toggle a favorite, and navigate between screens.
4. Check **Analytics → DebugView** in Firebase Console.
5. Disable DebugView:
   ```bash
   adb shell setprop debug.firebase.analytics.app .none.
   ```

Standard Analytics reports can take longer to populate than DebugView. Crashlytics should be validated using a controlled test build/process, never by adding a permanent forced-crash control to production.

## References

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/crashlytics/android/get-started
- https://firebase.google.com/docs/analytics/android/configure-data-collection
- https://firebase.google.com/support/privacy
- https://support.google.com/firebase/answer/6318039
