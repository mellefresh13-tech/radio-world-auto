# Firebase Analytics events

This document describes the custom Analytics events emitted by `AppAnalytics.kt` and the playback services.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `radio_app_open` | App launch | `ui_profile`, `app_language`, `app_theme` |
| `station_play` | A station starts playing for the first time in the current station selection; pause/resume and buffering recovery do not create another event | `station_id`, optional `station_name`, `station_genre`, `station_country`, `ui_profile` |
| `station_listen` | A listening session ends after at least 5 seconds of actual playing time | `station_id`, optional station details, `listen_seconds`, `ui_profile` |
| `search_used` | Search results are available and the query remains unchanged for 800 ms | optional filtered `search_query`, `result_count`, `ui_profile` |
| `favorite_added` | A station is added to favorites | `station_id`, optional station details |
| `favorite_removed` | A station is removed from favorites | `station_id`, optional station details |
| `playback_error` | Player reports a playback error | optional `station_id`, `error_code`, `ui_profile` |
| `screen_opened` | A main screen other than the initial app/player screen is opened | `screen_name` |

## Listening measurement

The playback service measures only time when Media3 reports that audio is actually playing. The clock is monotonic and local; no periodic event or network request is made while listening.

Buffering and temporary playback suppression pause the clock without closing the listening session. The session is finalized on an explicit pause, station transition, player stop/end, or service cleanup. Segments shorter than 5 seconds are ignored. Durations are not artificially capped at 4 hours.

A final segment can still be lost if Android terminates the process without allowing service cleanup. This implementation does not add periodic persistence or background network traffic to avoid that rare edge case.

## User properties

- `ui_profile`: `car`, `phone`, or `tablet`
- `app_language`: selected locale (for example `en` or `ru`)
- `app_theme`: `system`, `light`, or `dark`

The app does not set a Firebase User ID. App version is available through Firebase's automatic app/version reporting and is not duplicated as a custom user property.

## Search privacy filter

Search text is not sent verbatim in all cases. Queries containing `@`, `://`, or seven or more digits are excluded. Remaining text is filtered to a limited character set, normalized, and truncated to 40 characters. Values shorter than two characters are omitted. The event and result count can still be sent without the query.

This filter reduces accidental transmission of contact details but cannot guarantee that all remaining text is non-personal. Review any change to this behavior against `PRIVACY_POLICY.md`.

## Firebase reporting setup

In Firebase Console / Google Analytics, register useful event parameters as event-scoped custom dimensions:
- `station_id`, `station_name`, `station_genre`, `station_country`
- `search_query`, `screen_name`, `ui_profile`, `error_code`

Register `listen_seconds` and `result_count` as custom metrics if needed. Register `app_language`, `app_theme`, and `ui_profile` as user-scoped custom dimensions where appropriate.

Standard GA4 reports may not expose every custom parameter immediately. BigQuery export is optional and is not enabled by the app code.
