# UI profiles

The Android UI is driven by runtime width/orientation profiles rather than a single fixed layout.

## Mobile reference set

StatCounter's worldwide mobile screen-resolution data for August 2026 lists these five common portrait resolutions:

| Portrait | Share |
|---|---:|
| 414×896 | 13.63% |
| 360×800 | 9.25% |
| 390×844 | 6.81% |
| 393×873 | 5.27% |
| 384×832 | 4.35% |

Landscape QA uses the same five devices rotated:

- 896×414
- 800×360
- 844×390
- 873×393
- 832×384

These are a test matrix, not a separate market-share ranking.

## Belgee X50 reference

The primary automotive design canvas is **1920×720 landscape**.

The Belgee X50 / Geely Coolray-style head unit uses a permanent OEM side area on compatible/newer units. The app therefore keeps its own automotive navigation in a **left-side rail** and must not rely on a top app title bar.

A first safe-area profile reserves **96 physical pixels on the left** as the conservative OEM inset. This value should be refined against the actual head-unit screenshot when available.

## Layout rules

### Automotive 1920×720

- landscape-first;
- persistent left navigation rail;
- no internal top app title bar;
- large Play/Pause;
- smaller Previous/Next;
- Shuffle and Browse/Source as secondary controls;
- station/track block must not overlap the right edge;
- navigation accent extends through the rail height;
- list screens use three station cards per row where the viewport allows it.

### Phone portrait

- adaptive vertical player;
- primary transport controls remain immediately accessible;
- compact navigation may use a bottom bar;
- lists target three cards per row where the available width permits it.

### Phone landscape

- adaptive horizontal player;
- preserve the automotive control hierarchy where possible;
- navigation can use a compact side layout instead of a bottom bar when width allows it.

## Touch targets

Critical playback controls use approximately 48–72dp or larger targets depending on profile. Automotive controls may be larger than phone controls.

Touch feedback must not cause visible screen flicker or recreate the playback surface unnecessarily. A local UI interaction must not restart the live stream.

## Search

Phone portrait uses the Android keyboard. Automotive profile may use the larger custom keyboard where it fits the available viewport.
