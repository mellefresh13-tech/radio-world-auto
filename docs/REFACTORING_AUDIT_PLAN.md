# Refactoring audit and execution plan

**Status:** audit in progress — no runtime code has been changed yet  
**Plan opened:** 2026-10-17  
**Reference branch:** `experiment/lava-lamp-player` at `508edfefdda9f52f1ffec7e52dab88da6dc04a9c`  
**Work branch:** `refactor/lava-lamp-audit-2026-10-17`  
**Invariant:** preserve the observable behavior of the current branch. `experiment/lava-lamp-player` and `main` are reference branches and must not be modified by this work.

## Status and priority rules

- `[ ]` Pending; `[~]` in progress; `[x]` completed and checked; `[!]` blocked / requires an explicit product decision.
- **P0 — release blocker:** data loss, crash, playback failure, or a behavior regression introduced by a change.
- **P1 — high:** confirmed correctness, reliability, or severe responsiveness defect.
- **P2 — medium:** avoidable work, duplicated logic that can drift, missing regression protection, or a credible edge-case defect.
- **P3 — low:** dead code, small cleanup, or a potential improvement with limited user impact.
- A code change is not complete merely because it compiles: its item needs a validation note and must retain the existing UI/playback contract.

## Non-negotiable behavior contract

1. Preserve the automotive UI and its 1920×720 reference profile.
2. Preserve phone portrait, phone landscape, tablet portrait, and tablet landscape layout decisions in `UiProfile`; visual dimensions, tap targets, spacing, navigation, and existing mini-player behavior are out of scope for redesign.
3. Preserve catalog ordering and filtering, search debounce and pagination, favorites and recents, resume/restore, stream fallback/retries, metadata presentation, online/offline recovery, notification/media-session controls, and the separate car/mobile service registrations.
4. A shared implementation is acceptable only where it does not erase meaningful differences between automotive and mobile/tablet UI.
5. Do not merge into `main`, amend the reference branch, change station/catalog data, upgrade dependencies, or change public API contracts as part of routine refactoring.
6. Each finding must be recorded below before fixing it. If inspection reveals a behavior whose intended outcome cannot be inferred from code/tests, leave it unchanged and mark it for clarification rather than guessing.

## Repository map inspected

- Android: Kotlin app, XML resources, automotive `CarMainActivity`, responsive `MobileMainActivity`, separate `CarRadioPlaybackService` and `RadioPlaybackService`, catalog/API client/cache/state stores, RecyclerView adapters, and pure playback-policy classes with unit tests.
- Catalog collector: source discovery, normalization, merge, filters, stream verification, coverage reporting, and tests.
- Catalog API: FastAPI endpoints, SQLite schema/importer, catalog refresh thread, metadata-probe endpoint, and API tests.
- Build/automation: Android release workflow, collector tests, catalog publication and health workflows, Python build metadata, and helper tools.

The frontend has two large Activities (currently about 1,863 and 2,287 lines) with many identically or near-identically implemented methods. The two media-session services also repeat the majority of playback/state/metadata implementation. This is a strong refactoring candidate, but it is also the area most likely to regress playback if merged mechanically.

## Findings and change queue

### P1 — correctness / reliability

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P1-01 | `api/src/radio_api/app.py::debug_metadata` creates one `probe_streams([url], concurrency=1)` call per URL inside one `asyncio.gather`. Each call has its own semaphore, so the configured concurrency limit does not constrain the total number of simultaneous probes for the request. With the endpoint limits, one request can start hundreds of network probes. | Probe the URL list through one bounded `probe_streams` invocation, preserve input/result order, add a regression test that proves the global concurrency cap and response mapping remain stable. | `[ ]` |
| P1-02 | API startup calls `download_catalog` synchronously and lets network errors escape. A temporarily unavailable catalog host can prevent the API from starting even when a previously downloaded database exists. | Keep schema initialization; treat a failed initial refresh as non-fatal only when a usable local DB is already present, keep the periodic refresh, and test both existing-DB fallback and first-start failure. Do not silently report success with an empty DB. | `[ ]` |

### P2 — internal performance / consistency

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P2-01 | Successful catalog sync already writes the snapshot from `ApiCatalogRepository.syncFromGitHub`. Both Activities then schedule additional full snapshot serializations from the country callback, genre callback, and end of `loadRemoteCatalog`. That is a repeated, potentially large JSON+gzip write of the same dataset. | Verify cache ownership and call order; remove only provably redundant Activity-level writes, keep one authoritative snapshot write and version metadata. Do not change cache contents or read/restore order. | `[ ]` |
| P2-02 | `ApiCatalogRepository.stationIndex` is populated from an existing cache but is not refreshed on the successful network-sync path; `loadStation` then degrades to a linear scan. | Rebuild the index whenever the authoritative station list changes; add a repository-level regression test where feasible. | `[ ]` |
| P2-03 | `ApiCatalogRepository.filterStations` uses locale-dependent `lowercase()` for search and matching, while the client-side copy uses `Locale.ROOT`. This can make case-insensitive matching differ under certain device locales. | Use locale-independent normalization without changing filter fields, pagination, or ordering; test non-English locale behavior. | `[ ]` |
| P2-04 | `RadioApiClient` contains a second catalog-loading/filtering API (`loadStations`, `loadCountries`, `loadGenres`, `loadStation`, `loadCatalog` and supporting helpers), while production UI goes through `CatalogRepository` / `ApiCatalogRepository`; only `syncCatalog` is visibly needed by that path. | Perform a full reference check. Remove only methods/helpers proven unreachable inside this repository. Preserve `syncCatalog`, delta validation/fallback, parsing, progress callbacks, URLs, and executor shutdown behavior. | `[ ]` |
| P2-05 | Catalog stream playability is expressed both in `StreamAvailabilityPolicy` and inline in `RadioApiClient.parseStation`. | Use the existing policy in the live path after confirming exact equivalence; keep its tests as the contract. | `[ ]` |
| P2-06 | `CarMainActivity` and `MobileMainActivity` duplicate many state/playback, cache, network-recovery, title/artist, favorite, and mini-player methods. Current differences include layout/profile behavior and small persistence/order differences. | Create a method-by-method parity map first. Extract only small pure logic or identical non-visual helpers in isolated commits; preserve profile-specific rendering and any current behavior difference unless it is proven an accidental defect. | `[ ]` |
| P2-07 | `CarRadioPlaybackService` and `RadioPlaybackService` repeat catalog loading, station-to-media-item mapping, previous-station transitions, stream recovery/metadata publication, and saved-station handling, with some service-specific session actions. | Map the differences and available tests before sharing any code. Prefer a small shared helper/base only when service lifecycle, media-session commands, and callback threading remain identical; otherwise document deliberate duplication rather than forcing an abstraction. | `[ ]` |
| P2-08 | Country and genre adapters repeat much of the responsive card construction and dimension selection, while the actual content artwork/layout differs. | Consider extracting shared sizing/card-frame helpers only after comparison at all UI profiles; do not alter layout resources or dimensions. | `[ ]` |
| P2-09 | API tests exist but no dedicated API-test workflow is present in the inspected workflow set. Android validation currently runs on `main` pushes or manual dispatch; Android lint is `continue-on-error`. That does not reliably validate this work branch before merge. | Add narrow CI validation for API tests and Android unit tests on PRs/refactor branches; keep the release-signing/APK workflow separate and do not turn a unit-test run into an APK release. Determine whether lint may become required without changing release behavior. | `[ ]` |
| P2-10 | Image loading uses a shared three-thread executor and disk cache. Same-URL concurrent requests can independently download and write the same cache path; disk cache trimming and cache use have no explicit per-key coordination. | Inspect call patterns and reproduce/validate before changing. If worth fixing, coalesce duplicate in-flight URL loads and make writes atomic without changing fallback rendering or callbacks. | `[ ]` |
| P2-11 | Activity controller-future listeners use `MoreExecutors.directExecutor()` and touch UI after resolving the controller. Confirm the Media3 future completion-thread guarantee for the current setup. | If callback thread is not guaranteed to be the main thread, dispatch only UI/state work to main while preserving ordering of attach-listener → resolve → playlist sync → restore → render. Do not change until verified. | `[ ]` |

### P3 — dead code / hygiene / follow-up

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P3-01 | `collector/src/radio_catalog/checker.py` resembles the newer `verify.py` checker, and `collector/src/radio_catalog/raw.py` exposes JSONL helpers; repository code search found no external call sites for either helper. | Recheck call sites, tests, scripts, docs, and workflow commands. Remove only files confirmed unused by this project; otherwise retain and explain intended use. | `[ ]` |
| P3-02 | Collector verification selects curated stream URLs in addition to `--max-verify-streams`, so the option’s “maximum unique stream URLs” description may not describe the actual total. | Confirm whether curated streams are deliberately exempt; if so clarify the help/documentation only. Do not change catalog selection/output as a cleanup side effect. | `[ ]` |
| P3-03 | Android workflow has repeated stale comment lines and non-blocking lint. | Keep workflow changes minimal and factual; treat enforcement changes separately from refactoring. | `[ ]` |
| P3-04 | Manifest explicitly permits cleartext traffic. Radio catalogs can contain HTTP-only streams, so removing the flag may break playback. | Leave unchanged unless a separate secure-transport policy is requested and the catalog compatibility impact is understood. | `[ ]` |
| P3-05 | Other potential unused methods/imports and duplicate helpers remain to be checked after full call-site/reference scanning. | Record each confirmed dead item here before removal. | `[ ]` |

## Execution order

1. **Baseline and inventory.** Confirm branch parent, enumerate source/tests/workflows, capture the current behavior contract. No behavior edits.
2. **API bounded probing and resilient startup.** Fix P1-01/P1-02 with focused tests.
3. **Catalog repository cleanup.** Fix P2-01 through P2-05 only with reference checks and regression coverage. Avoid touching catalog input/output formats.
4. **Cross-profile parity review.** Compare shared logic in car/mobile paths and separate service implementations; take low-risk extractions only. Do not refactor UI-rendering blocks just to reduce line count.
5. **Adapter/image/cache review.** Reconcile duplicate helpers and concurrency/performance concerns without altering design dimensions.
6. **CI guardrails.** Provide branch/PR test coverage for API and Android unit tests; keep release APK/signing separate.
7. **Dead-code pass.** Remove only confirmed unreachable code; do not delete sample/demo, compatibility, or fallback behavior without a usage decision.
8. **Final regression pass.** Run collector tests, API tests, Android unit tests, and available lint/build checks. Record exact outcomes (including any check that could not be run). Review the diff against the starting SHA and ensure no main/reference branch moved.

## Regression checklist

- [ ] Automotive reference UI at 1920×720: navigation, player, mini-player, favorites, catalog/search, on-screen keypad, metadata, status, and system media controls.
- [ ] Phone portrait: card geometry, text clipping, controls, bottom navigation, search, scroll restoration, Now Playing navigation.
- [ ] Phone landscape: content columns, sidebar/navigation, mini-player, search, playback controls.
- [ ] Tablet portrait: spacing, card geometry, player/mini-player, favorite/play hit areas.
- [ ] Tablet landscape: columns, player/mini-player, text clipping, navigation and control hit areas.
- [ ] Cold start with no cache / warm start with a valid cache / expired cache / network unavailable.
- [ ] Search, country/genre filters, paging, catalog delta and full-download fallback.
- [ ] Play/pause, next/previous, favorites, recents, stream failover, retry, station failover, offline pause and reconnect.
- [ ] Restore current station after activity recreation and process restart; ensure the two service registrations retain their current session behavior.
- [ ] Stream metadata parsing and station fallback metadata.
- [ ] API health/list/detail/filter endpoints; bounded debug metadata probing; API startup with and without a previously downloaded database.
- [ ] Catalog collector tests and deterministic/required catalog checks.
