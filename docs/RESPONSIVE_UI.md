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

1. Foundation / breakpoints — complete
2. Player — complete
3. Countries — complete
4. Genres — complete
5. Favorites — complete
6. Recently Played — complete
7. Search — complete
8. Station Info — removed; Info control is hidden in all profiles
9. Navigation and mini-player — mobile-specific refinement complete
10. Full regression audit — pending
11. Only then build APK / run GitHub Actions

## Latest mobile refinements

- Station cards on phone landscape use station artwork as a translucent full-card backdrop instead of a dedicated logo block.
- Phone-landscape Country and Genre cards use their flag/icon as a translucent backdrop with the name prominent at the top and count below.
- Station cards keep the compact favorite star at the top-right and the primary Play control centered.
- The mobile landscape sidebar uses a real scroll container for the navigation items and keeps the mini-player pinned below the navigation.
- Mobile mini-player uses the station artwork as a translucent backdrop, station name at the top, marquee track/artist text below, a large centered Play/Pause control, and a compact favorite star at the top-right.
- Info / Station Details is no longer presented to the user in any profile.
- Phone-landscape sidebar sizing was tightened to preserve usable space on small landscape phones.

## Non-regression rules

- Do not change playback/reconnect/fallback behavior.
- Do not change metadata handling.
- Do not change steering-wheel behavior.
- Do not change startup station restoration.
- Do not change favorites/recent persistence.
- Do not alter the existing car reference layout unless the change is required to keep it functionally intact.
- No GitHub Actions workflow run is allowed before responsive work is complete.
