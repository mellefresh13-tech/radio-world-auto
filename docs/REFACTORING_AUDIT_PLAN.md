# Refactoring audit and execution plan

**Status:** core refactoring and safety fixes implemented; API, Android unit, and collector test workflows are green; physical car/phone/tablet validation remains open
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
| P1-01 | `api/src/radio_api/app.py::debug_metadata` originally created one `probe_streams([url], concurrency=1)` call per URL inside one `asyncio.gather`; separate semaphores did not limit total request concurrency. | Replaced with one `probe_streams(urls, concurrency=8)` call and a regression test with 15 streams checking the max active probes and response grouping. | `[x]` — API tests passed in run [39063520646](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063520646) |
| P1-02 | API startup originally let a failed catalog download abort service startup even when a previously downloaded catalog was usable. | Startup retains a usable existing DB on refresh failure, logs the failure, starts background refresh, and still fails on first startup if no usable DB exists. Tests cover both cases. | `[x]` — API tests passed in run [39063520646](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063520646) |

### P1 — catalog integrity

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P1-03 | The remote catalog refresh originally trusted HTTP success plus a 4 KB size threshold before replacing the live SQLite file. A large error page or malformed database could overwrite a healthy local database. | Validate the temporary file using SQLite `quick_check`, required `stations`/`streams` tables, and non-empty station count before atomic replacement. Tests cover invalid-payload preservation and valid database replacement. | `[x]` — API tests passed in run [39063520646](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063520646) |
| P1-04 | `.github/workflows/catalog.yml` originally triggered on any push touching collector/catalog files, with write permission and a force-push to `catalog-data`. | Added both a `main`-only push trigger and a job-level `github.ref == 'refs/heads/main'` guard (so manual dispatch from other refs is blocked). The off-branch run [39063250992](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063250992) was cancelled during candidate generation before validation/publication; `catalog-data` stayed on its prior commit (`95328ec7`). | `[x]` — guard committed and state verified |

### P2 — internal performance / consistency

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P2-01 | Successful catalog sync wrote the full snapshot from `ApiCatalogRepository`, then both Activities queued three additional full snapshot writes. | Removed all Activity-level snapshot writes and helper from both Activities. The Repository is now the sole owner of full catalog snapshot writes; this also eliminates the partial-cache overwrite risk in P2-14. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-02 | `ApiCatalogRepository.stationIndex` was populated from an existing cache but not refreshed after a successful network sync. | Rebuild the ID map when the synced station list becomes authoritative. | `[x]` — Android build/unit tests passed; indexed lookup code reviewed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-03 | Repository filtering and both Activities used device-locale case normalization; that can break matching or regional flag construction under locales such as Turkish. | Switched query/country/genre matching to `Locale.ROOT` and country flag uppercase to `uppercase(Locale.ROOT)` in repository and both Activities; filter fields, pagination, and order are unchanged. | `[x]` — Android build/unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); no dedicated locale instrumentation test |
| P2-04 | `RadioApiClient` contained a parallel legacy catalog-loading/filtering API never called by repository/application code; the active path uses `ApiCatalogRepository` and `syncCatalog`. | Removed unused `forceRefresh`, legacy load/filter APIs, cached catalog loader and duplicate filter/search helpers. Kept `syncCatalog`, full/delta validation, parsing, progress callbacks, URLs, and `close()`. | `[x]` — Android build/unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-05 | Catalog stream playability was duplicated inline in `RadioApiClient.parseStation`. | Reused `StreamAvailabilityPolicy.isPlayable` while preserving the nonblank-URL check. | `[x]` — Android build/unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-06 | `CarMainActivity` and `MobileMainActivity` duplicate state/playback and rendering code; layout/profile methods also differ materially. | Compared matching methods and extracted only the identical station-to-media-item mapping into `StationMediaItemFactory`; left profile-specific rendering/layout blocks unchanged. Aligned car title/artist persistence with mobile behavior. | `[x]` — low-risk shared logic checked by Android unit/build run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); real-device visual regression still pending |
| P2-07 | `CarRadioPlaybackService` and `RadioPlaybackService` repeat some media/session handling, but have distinct lifecycle/session command behavior. | Extracted only identical `stationToMediaItem` mapping. Kept each service's lifecycle, session command handling, metadata listener, restoration, and recovery flows separate to avoid risky abstraction. | `[x]` — Android unit/build run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); real playback/session integration still needs device validation |
| P2-08 | Country and genre adapters repeated responsive card height, text sizes, and margins although their artwork/content differs. | Added `CatalogCardMetrics` to share only common dimensions, leaving card view trees and flag/genre art separate. Added profile matrix tests for phone portrait/landscape and tablet portrait/landscape, including landscape-card measurements. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); no physical device screenshot comparison available |
| P2-09 | No API-test workflow was present; Android validation was limited to `main` push/manual dispatch and lint was non-blocking. | Added `.github/workflows/api-tests.yml` and `.github/workflows/android-tests.yml`; API tests passed in [39063520646](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063520646), Android unit tests in [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346). They run tests only, not release signing/APK publishing. | `[x]` — test workflows added and green |
| P2-10 | `ImageLoader` allowed parallel requests for the same URL to write directly to the same disk-cache path, so reads could observe partial PNG files. Duplicate downloads still may waste bandwidth/executor time. | Added `AtomicFileWriter` and serialized image-cache write/trim; existing target is replaced only after complete PNG encoding. Added pure file-I/O tests for successful replacement and preservation/cleanup on failed write. Single-flight network coalescing is deferred because there is no image-loader callback/instrumentation harness to verify scheduling. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); duplicate downloads remain a deferred optimization |
| P2-11 | Activity controller-future listeners used `MoreExecutors.directExecutor()` while their callback updates UI/controller state. Direct executor does not guarantee main-thread execution. | Replaced it with `ContextCompat.getMainExecutor(this)` in both Activities, preserving the listener body and its attach/restore sequence. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); callback thread/lifecycle behavior still needs device/integration validation |
| P2-12 | `MobileMainActivity` persisted updated station metadata, while automotive UI changed only in-memory state/UI. | Added `playbackStateStore.saveLastStation(updated)` to the automotive title/artist handlers, retaining parsing and rendering behavior. | `[x]` — Android build/unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-13 | `merge_stations` could append duplicate canonical stream URLs/source identities from one incoming record because sets were not updated; first-record duplicates also survived. | Deduplicates the first merged record preserving first occurrence, updates identity sets when merging, and includes regression coverage. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P2-14 | Activities wrote a partial `catalog` list to the same snapshot file used by `ApiCatalogRepository` for the full catalog; filtered pages or `loadStation` callbacks could overwrite the full cache and reset `saved_at`. | Removed every Activity-level `saveCatalogCacheAsync()` call and helper. Repository exclusively persists full catalog snapshots; Activity reads remain for warm start and lookup. | `[x]` — Android unit tests passed after this change in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-15 | `RadioApiClient.syncCatalog` posts its callback to the main looper. `ApiCatalogRepository.syncFromGitHub` then maps the entire result and serializes/compresses the full catalog cache before returning to the UI callback; a large catalog (up to the 50,000 station request) may block the main thread. `CatalogCacheStore.save` also catches and suppresses persistence exceptions, so current success callbacks do not prove that the new cache was durably written. | Added an injectable `BackgroundResultProcessor`; station/country/genre mapping, index preparation and cache persistence now run on a single background executor. Results are applied and callbacks delivered on the main looper after the save attempt completes. Cache save now returns success/failure and uses `AtomicFileWriter`; a failed disk write keeps the old snapshot and does not discard a fresh in-memory catalog (best-effort cache semantics retained). Added JVM tests for processing/callback order and transform-failure delivery; existing atomic-file tests verify that a failed write preserves the previous target. A direct CatalogCacheStore integration test remains unavailable in the current local-unit-test harness (Android Context/JSON dependency), so this item stays in progress pending CI and broader validation. | `[~]` — implementation committed; Android CI result still pending verification |
| P2-16 | `is_curated_family` recreated `set(_CURATED_ANCHORS)` for every station. | Precomputed a module-level `frozenset`; matching/token normalization are unchanged. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P2-17 | `ApiCatalogRepository.ensureLocalCatalog` synchronously calls `CatalogCacheStore.load()` when its in-memory catalog is empty. On a cold repository start this decompresses and parses a cache that can contain 50,000 stations on the caller thread (typically the main thread), even though Activities separately warm their own cache instance in the background. Moving this read asynchronously needs to coalesce simultaneous first requests so the same file is not parsed repeatedly and callbacks keep their existing main-thread behavior. | Added a first-load request queue; cache decompression/JSON parsing and lazy station-index construction now use the existing serial background executor. The loaded snapshot is published once, then each queued request retains its `allowSync` behavior and callbacks are dispatched through the main executor. No new dependency or cache format change. | `[~]` — implementation committed; Android CI and concurrent cold-start validation still pending |

### P3 — dead code / hygiene / follow-up

| ID | Finding and evidence | Planned change | Status |
|---|---|---|---|
| P3-01 | `checker.py` duplicated the stream checker now in `verify.py`; `raw.py` had JSONL helpers with no call sites in this repository. | Removed both unused files after checking imports, tests, workflows and project references. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P3-02 | `--max-verify-streams` limited non-curated URLs while curated URLs were prioritized additionally; its help text previously implied a hard total maximum. | Clarified help text without changing stream selection/output behavior. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P3-03 | Android release workflow carried several stale comments suggesting prior list items/review actions, plus a non-blocking lint step. | Removed only stale comments; the `continue-on-error` lint policy and release/signing path remain unchanged. | `[x]` — comment-only workflow cleanup; lint policy deliberately unchanged |
| P3-04 | Manifest explicitly permits cleartext traffic. Radio catalogs can contain HTTP-only streams, so removing the flag may break playback. | Leave unchanged unless a separate secure-transport policy is requested and the catalog compatibility impact is understood. | `[ ]` |
| P3-05 | `sources/radio_garden.py` and `sources/web.py` were standalone scrapers not imported by the active collector pipeline, tests, docs, or workflows. | Removed the two unreferenced source adapters. Active Radio Browser, IPRD and curated import path is unchanged. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P3-06 | Both Activities had an unused identity function `renderEmoji`, called a no-op `styleNavigationButtons()`, and `UiProfile.playerHeroWeight` was unused and always returned `1f`. | Removed only these dead helpers/property; no visual dimensions or rendering paths changed. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |

## Execution order

1. **Baseline and inventory.** Confirm branch parent, enumerate source/tests/workflows, capture the current behavior contract. No behavior edits.
2. **API bounded probing and resilient startup.** Fix P1-01/P1-02 with focused tests.
3. **Catalog repository cleanup.** Fix P2-01 through P2-05 only with reference checks and regression coverage. Avoid touching catalog input/output formats.
4. **Cross-profile parity review.** Compare shared logic in car/mobile paths and separate service implementations; take low-risk extractions only. Do not refactor UI-rendering blocks just to reduce line count.
5. **Adapter/image/cache review.** Reconcile duplicate helpers and concurrency/performance concerns without altering design dimensions.
6. **CI guardrails.** Provide branch/PR test coverage for API and Android unit tests; keep release APK/signing separate.
7. **Dead-code pass.** Remove only confirmed unreachable code; do not delete sample/demo, compatibility, or fallback behavior without a usage decision.
8. **Final regression pass.** Run collector tests, API tests, Android unit tests, and available lint/build checks. Record exact outcomes (including any check that could not be run). Review the diff against the starting SHA and ensure no main/reference branch moved.


## Resume checkpoint — 2026-10-18

- Reconnected to the existing work branch; no branch reset, rebase, or reference-branch modification was performed.
- Verified the branch is 64 commits ahead of `experiment/lava-lamp-player` with no commits behind it at the time of inspection.
- Re-checked the recorded CI runs directly: Android unit tests (run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346)), API tests (run [39063520646](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39063520646)), and collector tests (run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277)) each report completed/success.
- Reconciled P2-10 and P2-11 with the green Android unit-test run. This confirms unit-test success, not physical-device playback or UI validation.
- Reviewed `ApiCatalogRepository.syncFromGitHub` and `RadioApiClient.syncCatalog`: the full mapping and cache save currently execute after the client posts its callback to the main looper. P2-15 remains the next implementation item, but requires a test seam for callback-thread, persistence-ordering, and failure behavior before moving work between threads.
- Next bounded task: add that test seam and focused regression tests for P2-15. Do not combine it with UI or playback refactoring.

## Resume checkpoint — 2026-10-18 (P2-15 implementation)

- Added a small executor-injected processing boundary so ordering can be tested without Android instrumentation.
- Moved the full API catalog mapping and compressed cache write off the main thread. Repository state changes and success callbacks are posted back to the main looper after the save attempt.
- Changed cache save to report whether disk persistence succeeded and to use the existing atomic writer. The fresh in-memory catalog remains usable if caching fails; the previous disk snapshot is not replaced by a partial write.
- Added focused JVM tests for background/callback ordering and transform-failure delivery. Existing AtomicFileWriter tests cover target preservation when writing fails. There is no direct CatalogCacheStore integration test in the current JVM harness; Android CI and broader validation remain pending, so keep P2-15 in progress.

## Resume checkpoint — 2026-10-18 (catalog cold-start follow-up)

- Follow-up review found that the initial `CatalogCacheStore.load()` path also decompresses/parses the full catalog synchronously when the repository instance has no in-memory snapshot.
- Added a queue for simultaneous first requests and moved cache loading plus lazy station-index construction onto the existing serial background executor. The result is applied and callbacks are dispatched on the main executor; each queued request retains its existing sync/no-sync behavior.
- P2-17 remains in progress until Android CI and cold-start behavior can be validated. The current JVM test harness cannot directly instantiate the Android-backed repository/cache without adding a heavier Android test dependency; do not mark this complete on code review alone.

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
