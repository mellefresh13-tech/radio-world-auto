# Responsive UI contract

This branch is based on `ui/modern-auto-card-redesign`.

## Immutable reference

The current automotive UI is the visual and functional reference. `main` and `ui/modern-auto-card-redesign` must not be changed while responsive work is in progress.

The exact 1920x720 landscape HMI profile remains protected, including its 96px safe-area inset and automotive navigation/sidebar behavior.

## Profiles

- Phone portrait: up to 600dp wide. Primary smartphone experience; one-column station lists at compact widths.
- Phone landscape: under 1000dp wide. Compact sidebar/landscape experience with 2–3 columns depending on available width.
- Wide landscape: 1000dp and above. Preserve the existing automotive-style composition unless a later explicitly approved tablet layout requires otherwise.
- 1920x720: exact automotive reference; no responsive redesign is allowed to alter this profile.

## Responsive targets

Portrait validation targets: 360x640, 360x800, 390x844, 412x915, 430x932.

Landscape validation targets: 800x360, 854x480, 915x412, 1280x720.

## Screen order

1. Foundation / breakpoints
2. Player
3. Countries
4. Genres
5. Favorites
6. Recently Played
7. Search
8. Station Info
9. Navigation and mini-player
10. Full regression audit
11. Only then build APK / run GitHub Actions

## Non-regression rules

- Do not change playback/reconnect/fallback behavior.
- Do not change metadata handling.
- Do not change steering-wheel behavior.
- Do not change startup station restoration.
- Do not change favorites/recent persistence.
- Do not alter the existing car reference layout unless the change is required to keep it functionally intact.
- No GitHub Actions workflow run is allowed before responsive work is complete.
