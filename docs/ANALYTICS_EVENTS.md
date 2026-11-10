# Firebase Analytics events

This document describes the privacy-conscious analytics emitted by the Android app.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `radio_app_open` | App launch | `ui_profile`, `app_language`, `app_theme` |
| `station_play` | Playback becomes active | `station_id`, `station_name`, `station_genre`, `station_country`, `ui_profile` |
| `station_listen` | A listening segment ends after at least 5 seconds | `station_id`, `station_name`, `station_genre`, `station_country`, `listen_seconds` |
| `search_used` | A debounced catalog search succeeds | `search_query` (when safe), `result_count`, `ui_profile` |
| `favorite_added` | A station is added to favourites | `station_id`, `station_name`, `station_genre`, `station_country` |
| `favorite_removed` | A station is removed from favourites | `station_id`, `station_name`, `station_genre`, `station_country` |
| `playback_error` | Player reports a playback error | `station_id`, `error_code`, `ui_profile` |
| `screen_opened` | User navigates to a different main screen | `screen_name` |

Listening duration is measured with Android's monotonic clock. Segments shorter than 5 seconds are ignored; long segments are capped at 4 hours. A segment is reported when playback pauses/stops or the station changes. If the process is force-stopped while playback is active, its final segment may not be reported.

## User properties

- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected app locale (for example `en` or `ru`)
- `app_theme`: `system`, `light`, or `dark`
- `app_version`: app version name

Properties are refreshed at app launch and immediately after the language or theme setting changes.

## Search privacy

Search analytics records the query only when it looks like a radio/catalog search. Queries containing an at-sign, a URL marker, or seven or more digits are not included; remaining text is limited to 40 characters and a conservative character set. The event and result count are still recorded without the query. Search analytics is emitted after a successful catalog response, not for every keystroke.

## Firebase report setup

In Firebase Console / Google Analytics, register the following event parameters as event-scoped custom dimensions where useful:

- `station_id`, `station_name`, `station_genre`, `station_country`
- `search_query`, `screen_name`, `ui_profile`, `error_code`

Register numeric parameters `listen_seconds` and `result_count` as custom metrics if they are not available in the desired reports. Register user properties `app_language`, `app_theme`, and `ui_profile` as user-scoped custom dimensions.

Standard Firebase/GA4 reports may not expose every custom parameter immediately. BigQuery export is optional and is not enabled by this change.
