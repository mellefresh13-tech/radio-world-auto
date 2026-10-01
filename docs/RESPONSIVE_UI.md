# Responsive UI contract

main is the current product baseline. The tested responsive phone/tablet implementation and the isolated automotive implementation are part of the same app, but they are maintained as separate UI/application surfaces.

## Branches and references

- main — current primary branch and release baseline.
- ui/modern-auto-card-redesign — immutable automotive visual/functional reference. Do not modify it.
- ui/responsive-mobile — historical responsive development branch. Its tested state was promoted to main.

The automotive reference commit remains abe46f8ebf077032ff162359984daddd057be7b2.

## Automotive isolation

The automotive 1920x720 HMI is treated as a separate application surface from responsive phone/tablet UI.

- CarMainActivity.kt is kept as a copy of the immutable automotive reference implementation.
- CarRadioPlaybackService.kt is kept as the automotive playback implementation from the same reference.
- MobileMainActivity.kt contains the responsive phone/tablet implementation.
- MainActivity.kt is only a dispatcher: an exact 1920x720 landscape device goes to CarMainActivity; all other devices go to MobileMainActivity.
- The automotive profile keeps its 96px safe-area inset and reference navigation/player geometry.
- ui/modern-auto-card-redesign must remain untouched.
- Future phone/tablet UI changes must not be implemented inside the automotive activity or automotive playback service.

## Profiles

- Phone portrait: up to 600dp wide. Primary smartphone portrait experience; station lists use one column.
- Phone landscape: under 1000dp wide. Compact landscape experience with a left navigation sidebar and 2–3 columns depending on available width.
- Tablet landscape: 1000dp and above, except the exact 1920x720 automotive profile. Uses a full-width responsive content area after the sidebar, larger readable cards, and a restrained 3-column grid for Countries, Genres, and Stations.
- 1920x720 landscape: exact automotive reference. No responsive redesign is allowed to alter this profile.

## Current responsive tablet rules

Tablet landscape deliberately follows the visual rhythm of the Station grid instead of packing too many small cards into the screen.

- Countries: 3 columns.
- Genres: 3 columns.
- Stations: 3 columns.
- Country/Genre cards use a larger tablet treatment with readable 18sp names and 12sp counts.
- Grid gaps are increased to keep cards visually separated.
- The content area stretches across the available width; it is not limited to the 1920px automotive layout.
- Portrait layout is unchanged by these tablet rules.

## Responsive targets

Portrait validation targets: 360x640, 360x800, 390x844, 412x915, 430x932.

Landscape validation targets: 800x360, 854x480, 915x412, 1280x720.

Automotive reference target: 1920x720.

## Current feature status

1. Foundation / breakpoints — complete
2. Player — complete
3. Countries — complete
4. Genres — complete
5. Favorites — complete
6. Recently Played — complete
7. Search — complete
8. Station Info — removed; Info control is hidden in all profiles
9. Navigation and mini-player — complete for responsive profiles
10. Automotive isolation — complete
11. Tablet landscape readability pass — complete
12. Latest responsive release build — successful and tested on phone and automotive head unit; tablet layout is included in the same build

## Latest responsive refinements

- Station cards on phone landscape use station artwork as a translucent full-card backdrop.
- Phone-landscape Country and Genre cards use their flag/icon as a translucent backdrop with the name prominent at the top and count below.
- Station cards keep the compact favorite star at the top-right and the primary Play control centered.
- The mobile landscape sidebar uses a scroll container for navigation items and keeps the mini-player pinned below navigation.
- Mobile mini-player uses station artwork as a translucent backdrop, station name at the top, marquee track/artist text below, a large centered Play/Pause control, and a compact favorite star at the top-right.
- Phone portrait station artwork fills the card edge-to-edge and is clipped to the card's rounded outline.
- Info / Station Details is no longer presented to the user in any profile.
- Phone-landscape sidebar sizing was tightened to preserve usable space on small landscape phones.
- Tablet landscape Countries/Genres were changed to a restrained 3-column layout with larger, readable cards.
- Tablet Stations remain a 3-column grid to preserve the same visual rhythm when drilling down from Countries/Genres.

## Non-regression rules

- Do not change playback/reconnect/fallback behavior during UI-only work.
- Do not change metadata handling.
- Do not change steering-wheel behavior.
- Do not change startup station restoration.
- Do not change favorites/recent persistence.
- Do not alter the automotive reference layout.
- Do not move responsive-only logic into CarMainActivity.kt or CarRadioPlaybackService.kt.
- Before a release build, verify that the 1920x720 automotive implementation remains unchanged and that the responsive build compiles successfully.

## GitHub Actions / release baseline

- Release workflow supports workflow_dispatch.
- main is the normal push/release branch.
- The last tested responsive build was Actions run #323, commit 5ffe7e296ec32434445ed8c6b0decb9bed0bda51, and completed successfully.
- That tested responsive state was promoted to main without rewriting the commit history.
