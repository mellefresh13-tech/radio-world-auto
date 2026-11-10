# Firebase Analytics and Crashlytics

This document describes the Firebase integration currently present in the Android source.

## Project and SDKs

- Firebase project: `radio-world-auto`
- Android application ID: `com.mellefresh13.radio`
- Configuration file: `android/app/google-services.json`
- SDKs: Firebase Analytics and Firebase Crashlytics
- Firebase Android BoM: `34.19.0`
- Google Services Gradle plugin: `4.5.0`
- Crashlytics Gradle plugin: `3.0.8`

The Firebase configuration file contains app/project identifiers and an API key. Do not put service-account credentials or private keys in the Android app or repository.

## Custom Analytics events

The following event names and parameters are implemented in `AppAnalytics.kt`.

| Event | When | Parameters |
| --- | --- | --- |
| `radio_app_open` | App launch | `ui_profile`, `app_language`, `app_theme` |
| `station_play` | Playback enters the playing state | `station_id`, optional `station_name`, `station_genre`, `station_country`, `ui_profile` |
| `station_listen` | A listening segment ends | `station_id`, `listen_seconds`, optional station metadata |
| `search_used` | A catalog search succeeds | optional `search_query`, `result_count`, `ui_profile` |
| `favorite_added` / `favorite_removed` | A station is added/removed from favorites | `station_id`, optional station metadata |
| `playback_error` | The player reports a playback error | optional `station_id`, `error_code`, `ui_profile` |
| `screen_opened` | A main screen is opened | `screen_name` |

Listening duration uses Android's monotonic clock. Segments shorter than 5 seconds are ignored and reported durations are capped at 4 hours. A segment is reported when playback pauses/stops or the station changes. If the process is force-stopped while playback is active, its final segment may not be reported.

## User properties and Crashlytics keys

Analytics user properties:
- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected locale, for example `en` or `ru`
- `app_theme`: `system`, `light`, or `dark`
- `app_version`: app version name

Properties are refreshed at app launch and when relevant settings change. Crashlytics custom keys set by the app are `ui_profile` and `app_version`.

## Search handling

Search text is not sent without filtering. Queries containing `@`, `://`, or seven or more digits are omitted. Other text is stripped to letters, digits and a conservative set of punctuation, whitespace is normalized, and the result is limited to 40 characters. A query shorter than two characters is not included. The event, result count and UI profile may still be recorded without the query text.

## Data not included in custom events

The app does not intentionally include email addresses, phone numbers, user names, precise GPS coordinates, audio stream URLs, track titles or artist names in its custom events. Station identifiers and public station metadata (name, genre, country) are included where available. The app does not set a Firebase User ID.

The manifest disables Analytics Advertising ID collection and ad-personalization signals. This does not disable Analytics itself or all automatic Firebase/Google processing. Refer to the [privacy policy](PRIVACY_POLICY.md) and Google's [Firebase privacy documentation](https://firebase.google.com/support/privacy) for the broader SDK data handling.

## Crash reporting

Firebase Crashlytics collection is active in this build. Fatal crashes and supported ANR diagnostics are automatically reported and may include stack traces and app/device diagnostics. The app adds only the UI profile and app version as custom keys.

The separate local `CrashReporter` writes the last uncaught exception to `last_crash.txt` for display/copy on the next launch. This local file is excluded from Android cloud backup and device transfer. It is not the mechanism used to upload Crashlytics reports.

## Collection controls and release obligations

There is currently no in-app consent or opt-out control for Analytics or Crashlytics. Collection is enabled in the current build. Before distributing to users, the developer must ensure that the app's privacy disclosures and Google Play Data safety answers accurately describe both SDKs and comply with requirements in each distribution region. Do not assume that disabling Advertising ID is equivalent to disabling Analytics.

## Local Analytics verification

1. Install a debug build on an Android device with Google Play services.
2. Enable Analytics DebugView for the device:

   ```bash
   adb shell setprop debug.firebase.analytics.app com.mellefresh13.radio
   ```

3. Open the app, start a station, search, toggle a favorite and navigate between sections.
4. In Firebase Console, open **Analytics → DebugView** and inspect events and parameters.
5. Disable DebugView mode when finished:

   ```bash
   adb shell setprop debug.firebase.analytics.app .none.
   ```

Standard Analytics reports can take longer to populate than DebugView. Crashlytics should be verified with a controlled test in a non-production build or with a genuine test crash; do not add a permanent forced-crash control to production.

## Firebase documentation

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/crashlytics/android/get-started
- https://firebase.google.com/docs/analytics/android/configure-data-collection
- https://firebase.google.com/support/privacy
- https://support.google.com/firebase/answer/6318039
