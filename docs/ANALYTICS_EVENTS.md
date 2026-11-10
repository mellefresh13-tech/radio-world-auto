# Firebase Analytics events

This document is a concise event reference for the Android implementation in `AppAnalytics.kt`.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `radio_app_open` | App launch | `ui_profile`, `app_language`, `app_theme` |
| `station_play` | Playback becomes active | `station_id`, `station_name`, `station_genre`, `station_country`, `ui_profile` |
| `station_listen` | Listening segment ends | `station_id`, `station_name`, `station_genre`, `station_country`, `listen_seconds` |
| `search_used` | Debounced catalog search succeeds | optional `search_query`, `result_count`, `ui_profile` |
| `favorite_added` | Station is added to favorites | `station_id`, `station_name`, `station_genre`, `station_country` |
| `favorite_removed` | Station is removed from favorites | `station_id`, `station_name`, `station_genre`, `station_country` |
| `playback_error` | Player reports a playback error | optional `station_id`, `error_code`, `ui_profile` |
| `screen_opened` | User opens a main screen | `screen_name` |

Station metadata parameters are optional where the caller does not have the corresponding value. Identifiers and text values are length-limited in the app.

## Listening duration

Duration is measured using Android's monotonic clock. Segments shorter than 5 seconds are ignored; reported durations are capped at 4 hours. A segment is reported when playback pauses/stops or the station changes. If the process is force-stopped while playback is active, the final segment may be lost.

## User properties

- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected app locale (for example `en` or `ru`)
- `app_theme`: `system`, `light`, or `dark`
- `app_version`: app version name

Properties are refreshed at app launch and after language/theme changes.

## Search privacy filter

The search query is sent only if it passes the in-app filter. Queries containing `@`, `://`, or seven or more digits are omitted. Other queries are stripped to letters, digits and a limited punctuation set, whitespace is normalized, and text is capped at 40 characters. Text shorter than two characters is omitted. The event, result count and UI profile can still be logged without query text. Search events are emitted after a successful catalog response, not on each keystroke.

## Firebase reporting setup

For reporting, register relevant event parameters as GA4 event-scoped custom dimensions:
- `station_id`, `station_name`, `station_genre`, `station_country`
- `search_query`, `screen_name`, `ui_profile`, `error_code`

Register numeric `listen_seconds` and `result_count` as custom metrics where needed. Register `app_language`, `app_theme`, and `ui_profile` as user-scoped custom dimensions.

Standard reports may not expose every custom parameter immediately. BigQuery export is optional and is not enabled by the Android implementation itself.

See [FIREBASE_ANALYTICS.md](FIREBASE_ANALYTICS.md) for SDK details, Crashlytics, verification and privacy notes.
