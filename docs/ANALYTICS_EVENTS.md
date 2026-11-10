# Firebase Analytics events

This document describes the custom Analytics events emitted by `android/app/src/main/java/com/mellefresh13/radio/AppAnalytics.kt`.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `radio_app_open` | App launch | `ui_profile`, `app_language`, `app_theme` |
| `station_play` | Playback becomes active | `station_id`, `station_name`, `station_genre`, `station_country`, `ui_profile` (station details are optional) |
| `station_listen` | A listening segment ends after at least 5 seconds | `station_id`, `station_name`, `station_genre`, `station_country`, `listen_seconds` (station details are optional) |
| `search_used` | Search usage is recorded | `search_query` (optional, filtered), `result_count`, `ui_profile` |
| `favorite_added` | A station is added to favorites | `station_id`, optional station details |
| `favorite_removed` | A station is removed from favorites | `station_id`, optional station details |
| `playback_error` | Player reports a playback error | `station_id` (optional), `error_code`, `ui_profile` |
| `screen_opened` | A main screen is opened | `screen_name` |

Station details are limited in length before logging. Listening duration uses Android's monotonic clock. Segments shorter than 5 seconds are ignored and reported segments are capped at 4 hours. The last segment may be lost if the process is force-stopped before the app records the stop.

## User properties

- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected locale (for example `en` or `ru`)
- `app_theme`: `system`, `light`, or `dark`
- `app_version`: app version name

Properties are refreshed at app launch and when app settings are refreshed. The app does not set a Firebase User ID.

## Search privacy filter

Search text is not sent verbatim in all cases. Queries containing `@`, `://`, or seven or more digits are excluded. Remaining text is filtered to a limited character set, normalized, and truncated to 40 characters. Values shorter than two characters are omitted. The event and result count can still be sent without the query.

This filter reduces accidental transmission of contact details but cannot guarantee that all remaining text is non-personal. Review any change to this behavior against `PRIVACY_POLICY.md`.

## Firebase reporting setup

In Firebase Console / Google Analytics, register useful event parameters as event-scoped custom dimensions:
- `station_id`, `station_name`, `station_genre`, `station_country`
- `search_query`, `screen_name`, `ui_profile`, `error_code`

Register `listen_seconds` and `result_count` as custom metrics if needed. Register `app_language`, `app_theme`, `ui_profile`, and `app_version` as user-scoped custom dimensions where appropriate.

Standard GA4 reports may not expose every custom parameter immediately. BigQuery export is optional and is not enabled by the app code.
