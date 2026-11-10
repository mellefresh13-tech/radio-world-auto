# Firebase analytics and crash reporting

## Firebase project

- Firebase project: `radio-world-auto`
- Android application ID: `com.mellefresh13.radio`
- Configuration: `app/google-services.json`
- Firebase SDKs: Analytics and Crashlytics
- Firebase Android BoM: `35.1.0`
- Google Services Gradle plugin: `4.5.0`
- Crashlytics Gradle plugin: `3.0.8`

The Firebase configuration file contains project/app identifiers and an API key. Firebase documents these values as non-secret app configuration. Do not put service-account credentials or private keys in the Android app.

## Custom Analytics events

| Event | Parameters | Meaning |
| --- | --- | --- |
| `radio_app_open` | `ui_profile` | App launch (car, phone, tablet) |
| `station_play` | `station_id`, `ui_profile` | Playback actually entered the playing state |
| `playback_error` | `station_id` (when known), `error_code`, `ui_profile` | Media3 playback error before the existing recovery logic runs |
| `search_used` | `ui_profile` | A search request was sent; the query itself is never sent |
| `favorite_added` / `favorite_removed` | `station_id` | A station was added to or removed from favorites |

Firebase Analytics also collects its documented automatic app/session/device events and properties. The app does not set a Firebase User ID.

## Data deliberately not included in custom events

- Search text
- Track title or artist metadata
- Stream URLs
- Google account, email, or user name
- Precise GPS/location
- Advertising ID

The Android manifest disables Advertising ID collection and ad-personalization signals for Analytics. Firebase Analytics still uses a pseudonymous app-instance identifier and can derive approximate geography from network information as described in Google's Firebase/Analytics privacy documentation.

Crashlytics automatically reports fatal app crashes and supported ANR diagnostics. Reports include technical stack traces and device/app diagnostics; the app adds only the UI profile and app version as custom keys. The existing local `CrashReporter` remains unchanged.

## Local verification

1. Build and install the debug APK on a Google Play services-enabled Android device.
2. Enable Analytics DebugView for the device:

   ```bash
   adb shell setprop debug.firebase.analytics.app com.mellefresh13.radio
   ```

3. Open Radio World Auto, start a station, search, and toggle a favorite.
4. In Firebase Console, open **Analytics → DebugView** and check the custom events and parameters.
5. Disable Analytics DebugView mode when finished:

   ```bash
   adb shell setprop debug.firebase.analytics.app .none.
   ```

Standard Analytics reports can take longer to populate than DebugView.

Crashlytics receives real fatal crash/ANR reports automatically. Do not add a permanent forced-crash button to the production app. The first report may not appear until a real crash occurs and the app is launched again.

## Google Play privacy checklist

Before distributing a build with Firebase to the public, review the app's Data safety form and publish a privacy policy that accurately describes the SDKs' data collection. At minimum, review app interactions, device/other identifiers, approximate location derived by Google Analytics, crash logs, and diagnostics. Do not copy old Data safety answers without rechecking them.

Firebase/Google documentation:
- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/crashlytics/android/get-started
- https://firebase.google.com/docs/analytics/android/configure-data-collection
- https://support.google.com/firebase/answer/6318039
- https://firebase.google.com/support/privacy
