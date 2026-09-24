# UI profiles

The Android UI is now driven by runtime width/orientation profiles rather than a single fixed landscape layout.

## Mobile reference set

StatCounter's worldwide mobile screen-resolution data for August 2026 lists these five most common mobile resolutions:

| Portrait | Share |
|---|---:|
| 414×896 | 13.63% |
| 360×800 | 9.25% |
| 390×844 | 6.81% |
| 393×873 | 5.27% |
| 384×832 | 4.35% |

Source: StatCounter Global Stats, mobile worldwide, August 2026.

Landscape QA uses the same five devices rotated:

- 896×414
- 800×360
- 844×390
- 873×393
- 832×384

These are a test matrix, not a separate market-share ranking: StatCounter publishes the screen resolution itself, not a separate top-five list by orientation.

## Belgee X50 reference

The current Belgee X50 head unit used for this project is documented by owner-community sources as a 1920×720 display, with the newer head unit associated with a 10.25-inch panel. The public sources also show that the X50 head unit has a permanent left-side system menu on compatible/newer units.

For the app, **1920×720 is the primary automotive design canvas**.

A first safe-area profile reserves **96 physical pixels on the left** so the app's own controls and text do not sit under the OEM side menu. The public sources I found do not publish the exact pixel width of that side rail, so 96 px is deliberately a conservative implementation value to be refined against an actual X50 screenshot.

## Design rules

- Phone portrait: vertical player, three primary transport controls, secondary Browse/Shuffle row.
- Phone landscape: horizontal player, compact top navigation, large transport controls.
- Automotive 1920×720: no internal left navigation rail; the app uses the top navigation strip and reserves the OEM left-side area.
- Touch controls use roughly 48–72dp targets and scale down only when the available viewport requires it.
- Search uses the Android keyboard on phones; the larger custom QWERTY pad is kept for the automotive profile so it does not consume most of a small portrait display.
