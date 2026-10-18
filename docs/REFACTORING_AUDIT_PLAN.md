# Refactoring audit and execution plan

**Status:** core refactoring and safety fixes implemented; baseline API, Android unit, and collector workflows were green before the latest catalog-threading changes; latest Android CI is not yet verifiable through the available status endpoint; physical car/phone/tablet validation remains open
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
| P1-05 | `api/src/radio_api/metadata_probe.py::probe_stream` trusts the remote `icy-metaint` header and asks `_ByteReader` to accumulate that many audio bytes before metadata. A stream can declare an extremely large interval and keep sending data, driving avoidable memory growth inside the diagnostic endpoint. | Added a 1 MiB upper bound and reject oversized values before reading the response body; ordinary ICY probing and track-title parsing remain unchanged. Added tests that oversized headers cause zero body reads and normal ICY metadata still parses. | `[~]` — two focused local test scenarios passed; full API CI remains unverified |
| P1-06 | `api/src/radio_api/app.py::startup` called `initialize(DB_PATH)` before attempting a replacement download. SQLite schema initialization can throw on an existing non-SQLite/corrupt file, preventing the download/atomic replacement logic from recovering even if the remote catalog is available. | Removed the premature schema initialization. Startup first records whether the existing DB is usable, then tries the validated atomic download; a successful download replaces a corrupt file, while download failure still raises if no usable catalog existed. Added a regression test for corrupt-file recovery. | `[~]` — three focused local startup/fallback/schema scenarios passed; full API CI remains unverified |
| P1-07 | `api/src/radio_api/catalog_sync.py::download_catalog` streamed the remote SQLite response into a temporary file until EOF without an overall size limit. A runaway or unexpectedly large response could fill the disk before SQLite validation ran. | Added a 256 MiB download cap, checked before writing each chunk beyond the remaining byte budget. Oversized responses abort and remove the temporary file while preserving the existing destination. Added a regression test for cleanup and target preservation. | `[~]` — five focused local catalog-download/schema/concurrency scenarios passed; full API CI remains unverified |
| P1-08 | `api/src/radio_api/catalog_sync.py::_validate_catalog_database` and `db.has_usable_catalog` checked SQLite integrity, table names and station count, but not whether the tables contained every column queried by the API. A structurally incomplete DB could therefore replace a working DB or be retained as a fallback, then fail at request time. | Defined a shared required-column contract for `stations` and `streams`; both downloaded-file validation and existing-catalog health checks verify it. Added tests proving a valid SQLite file with missing columns is rejected before replacing the current DB and is not accepted as a startup fallback. Updated the accepted-file test to use the real schema. | `[~]` — eight focused local download/schema/playability scenarios passed; full API CI remains unverified |
| P1-09 | `api/start.sh` downloaded the database before launching Uvicorn, then the FastAPI `startup()` handler downloaded it again. This doubled startup traffic and meant a transient first download failure exited the shell script before the application-level last-known-good fallback could run. | Removed pre-launch download code; `start.sh` now only configures `PYTHONPATH` and starts Uvicorn. The application lifecycle remains the sole owner of validation, atomic replacement, fallback, and periodic refresh. Updated `api/README.md` to document the single startup path. | `[~]` — `sh -n` passed and three local startup scenarios passed; deployed API/CI validation remains pending |
| P1-10 | `download_catalog()` used the fixed temporary path `radio.db.tmp`. Concurrent startup/download attempts sharing a data volume could write and validate the same temp file, so one worker could see partial contents or another worker's `os.replace()` could remove its temp path first. | Switched to a unique temporary file in the destination directory using `tempfile.mkstemp`; atomic replacement still occurs only after size and SQLite checks pass. Error cleanup removes only the caller's temp file. Added a two-thread regression test that synchronizes both writers at validation and verifies they use distinct temp paths. | `[~]` — five focused local catalog-download/schema/concurrency tests passed; full API CI remains unverified |
| P1-12 | The SQLite safety checks validated integrity, required tables/columns, and a non-empty station list, but could still accept a database containing zero `online` streams. The API's station search only returns stations with an online stream, so such a database could replace the current catalog or be treated as a usable startup fallback while all normal station listings were empty. | Added a shared usability rule requiring at least one station-linked `online` stream both when validating a downloaded SQLite file and when checking an existing fallback DB. The catalog publication workflow now also fails validation when `online_streams <= 0`, before publishing. Added tests proving offline-only downloads are rejected and offline-only fallback DBs are not considered usable. | `[~]` — eight focused local catalog-sync tests and four startup/fallback tests passed; full API/catalog CI remains unverified |
| P1-11 | `probe_streams()` used a semaphore local to each function call, so concurrent `/debug/metadata` requests could each run up to 8 probes simultaneously. Request parameters could yield as many as 300 probe URLs in one request, creating a long-running diagnostic batch and multiplying total network concurrency. | Added one shared semaphore per running asyncio event loop so overlapping calls share an eight-probe ceiling, while retaining the per-call limit. `debug_metadata` now rejects requests that produce more than 32 probe jobs with HTTP 422, and a nonblocking process-wide gate admits at most four diagnostic requests at once (HTTP 429 when saturated), preventing unbounded waiting batches. Added tests for the global cap across concurrent calls, oversized batches, and overload rejection; README documents all three bounds. | `[~]` — metadata-probe mirror tests pass; API request/overload tests committed, full API CI/load validation pending |
| P1-13 | `metadata_probe._ByteReader.read_exact()` awaited the next response-body chunk without a total deadline. The HTTP client's read timeout bounds inactivity between chunks, so a slow-drip stream could keep an ICY probe alive much longer than the requested `timeout_seconds` while holding one of the four diagnostic request slots. | `_ByteReader` now tracks a monotonic body-read deadline and wraps each `__anext__()` in `asyncio.wait_for` using only the remaining time. The timeout maps to the existing stable `timeout` result. Added a slow-drip stream regression test that emits one partial chunk then stalls. | `[~]` — implementation and regression test committed; runtime test and API CI could not be run in this environment |

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
| P2-10 | `ImageLoader` allowed parallel requests for the same URL to write directly to the same disk-cache path, so reads could observe partial PNG files. Concurrent requests also duplicated network downloads and work. | Added `AtomicFileWriter` and serialized image-cache write/trim; existing target is replaced only after complete PNG encoding. Added `KeyedRequestCoalescer` to atomically join callers by URL, share one disk/network load, cache once, and deliver each caller's own success/failure callback. Unexpected disk/cache exceptions are converted to failures so they do not strand the queued callers. Added pure-JVM coordinator tests for same-key joining, independent keys, cache fast path, failure/retry, and clearing pending requests. A disk-cache metadata-touch failure is best-effort and does not discard a successfully decoded bitmap. | `[~]` — implementation and test committed; local Kotlin checks passed for the coordinator; latest Android Gradle CI remains unverified |
| P2-11 | Activity controller-future listeners used `MoreExecutors.directExecutor()` while their callback updates UI/controller state. Direct executor does not guarantee main-thread execution. | Replaced it with `ContextCompat.getMainExecutor(this)` in both Activities, preserving the listener body and its attach/restore sequence. | `[x]` — Android unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346); callback thread/lifecycle behavior still needs device/integration validation |
| P2-12 | `MobileMainActivity` persisted updated station metadata, while automotive UI changed only in-memory state/UI. | Added `playbackStateStore.saveLastStation(updated)` to the automotive title/artist handlers, retaining parsing and rendering behavior. | `[x]` — Android build/unit tests passed in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-13 | `merge_stations` could append duplicate canonical stream URLs/source identities from one incoming record because sets were not updated; first-record duplicates also survived. | Deduplicates the first merged record preserving first occurrence, updates identity sets when merging, and includes regression coverage. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P2-14 | Activities wrote a partial `catalog` list to the same snapshot file used by `ApiCatalogRepository` for the full catalog; filtered pages or `loadStation` callbacks could overwrite the full cache and reset `saved_at`. | Removed every Activity-level `saveCatalogCacheAsync()` call and helper. Repository exclusively persists full catalog snapshots; Activity reads remain for warm start and lookup. | `[x]` — Android unit tests passed after this change in run [39064094346](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064094346) |
| P2-15 | `RadioApiClient.syncCatalog` posts its callback to the main looper. `ApiCatalogRepository.syncFromGitHub` then maps the entire result and serializes/compresses the full catalog cache before returning to the UI callback; a large catalog (up to the 50,000 station request) may block the main thread. `CatalogCacheStore.save` also catches and suppresses persistence exceptions, so current success callbacks do not prove that the new cache was durably written. | Added an injectable `BackgroundResultProcessor`; station/country/genre mapping, index preparation and cache persistence now run on a single background executor. Results are applied and callbacks delivered on the main looper after the save attempt completes. Cache save now returns success/failure and uses `AtomicFileWriter`; a failed disk write keeps the old snapshot and does not discard a fresh in-memory catalog (best-effort cache semantics retained). Added JVM tests for processing/callback order and transform-failure delivery; existing atomic-file tests verify that a failed write preserves the previous target. A direct CatalogCacheStore integration test remains unavailable in the current local-unit-test harness (Android Context/JSON dependency), so this item stays in progress pending CI and broader validation. | `[~]` — implementation committed; Android CI result still pending verification |
| P2-16 | `is_curated_family` recreated `set(_CURATED_ANCHORS)` for every station. | Precomputed a module-level `frozenset`; matching/token normalization are unchanged. | `[x]` — collector tests passed in run [39064381277](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39064381277) |
| P2-17 | `ApiCatalogRepository.ensureLocalCatalog` originally called `CatalogCacheStore.load()` synchronously when its in-memory catalog was empty. On cold start this can decompress and parse a cache containing up to 50,000 stations on the caller thread (typically the main thread); it also needs to coalesce simultaneous first requests and preserve callback behavior. | Added a first-load request queue; cache decompression/JSON parsing and lazy station-index construction use the serial background executor. Concurrent sync callers share one network sync, mapping, and cache write; each retains its own local-catalog fallback. A repository completion flag prevents repeated first-load scheduling, and `CatalogCacheStore` memoizes a missing/corrupt load result so concurrent callers do not repeatedly probe/decompress the same unusable file. Activities now share the repository's cache-store instance and prebuild the index in their background cache task. No cache format change. | `[~]` — implementation committed; Android CI and cold-start/network validation still pending |
| P2-18 | `ApiCatalogRepository.loadStations` runs `filterStations` synchronously in its callback path, usually on the main thread. Search requests can scan and lowercase fields across the full in-memory catalog (up to 50,000 stations) after every debounced query, and even the initial full-catalog request copies the list there. | Added a `StationFilterRequest` processing path using `BackgroundResultProcessor` and the existing single-thread executor. Query/country/genre filtering and paging now run off-main; results and failures are returned via the main executor. Filter semantics and station order are unchanged. | `[~]` — code committed; Android unit/CI and UI responsiveness validation pending |
| P2-19 | Both Activities and `ApiCatalogRepository` previously constructed separate `CatalogCacheStore` instances for the same gzip snapshot, so a cold start could parse/decompress the full catalog twice and each in-memory snapshot could become stale relative to the other's writes. Sharing a `Snapshot` also requires safely publishing its lazy station index. | Injected each Activity's existing cache store into its repository, so synchronized `load()`/`save()` and `memorySnapshot` are shared. Both Activity cache tasks force `stationsById` on the background executor; changed the lazy index mode to `SYNCHRONIZED` so concurrent users build/publish one map safely. The store also remembers a completed missing/invalid-cache read, avoiding repeated parsing of the same broken file until a successful save replaces it. No file format or UI change. | `[~]` — code committed; Android CI and cold-start contention validation pending |
| P2-20 | Manifest `station_count` describes the raw generated snapshot, while Android filters streams by playability and removes stations with no playable stream. Comparing the raw count to the app's cached projection can reject a same-version cache or cause every valid delta to fall back to the full catalog. | Catalog builder now emits an optional `playable_station_count` using an extracted Python mirror of Android's exact URL/status rule (blank status or case-insensitive `online` without trimming `online` itself). Tests also cover padded/non-string statuses so the two platforms do not silently disagree. Android uses it for cached/delta projection validation when present; for legacy manifests it skips invalid raw-vs-filtered count comparisons, while verifying delta updated/removed item counts against manifest metadata. Full-download validation still checks raw `station_count`. Added Python helper tests and JVM policy tests. The live `catalog-data` manifest is still legacy because only the main-guarded workflow publishes it; no catalog data was changed from this refactor branch. | `[~]` — code/tests committed; isolated Python helper tests and five Kotlin policy scenarios passed; Android Gradle CI and next main-only manifest publication remain pending |
| P2-21 | The same-version branch of `RadioApiClient.syncCatalog` converted cached `Station` values to JSON then parsed them back into `ApiStation`. The intermediate JSON intentionally omits `songTitle` and `artist`, so a successful no-change sync could clear track metadata when the repository persisted the resulting snapshot. | Added a small pure mapper for the cached `Station` projection and used it directly in the same-version path. Station order, playable stream URLs, and optional catalog fields are unchanged; saved `songTitle`/`artist` now survive the sync. Removed the unnecessary JSON round-trip and retained playable-count validation when that manifest field exists. | `[~]` — two local Kotlin mapper scenarios passed; actual Android Gradle/JUnit CI and runtime metadata validation pending |
| P2-22 | Both Activities considered a cache fresh based on timestamp even when `cached.stations` was empty. A recently written empty/unusable catalog could therefore prevent `loadRemoteCatalog()` from running until the refresh interval elapsed, leaving the app without stations. | Added a shared pure `CatalogRefreshPolicy`: an empty station list, invalid timestamp, or stale timestamp requires refresh; a populated recent cache remains warm. Applied the policy identically in car and mobile Activities and added JVM tests for empty/fresh/stale/invalid timestamp cases. | `[~]` — four local Kotlin policy scenarios passed; Android Gradle CI and startup behavior validation pending |
| P2-23 | Even after fixing the same-version branch, delta and full catalog responses still omit live `songTitle`/`artist` values, and `ApiCatalogClient.parseStation` returns them as null. Mapping that result into the cached app model could erase metadata previously persisted from active playback. | Added `StationTrackMetadataPolicy` after the client computes any same-version, delta, or full result. It preserves prior nonblank track metadata by station ID unless incoming data supplies a nonblank replacement; newly added IDs do not inherit metadata from unrelated stations. Added pure-JVM tests for preservation, replacement/fallback behavior, and ID isolation. | `[~]` — test cases committed; local Kotlin smoke test and Android Gradle/JUnit CI pending |
| P2-24 | `RadioApiClient.downloadText` reported `loadedBytes` by counting decoded UTF-16 characters, but compared that value to HTTP `Content-Length` in bytes. Non-ASCII station names/metadata can make the displayed progress diverge from actual network bytes. | Added `ProgressReportingUtf8Reader` with a counting input-stream wrapper and incremental UTF-8 decoding, reporting byte-accurate progress without buffering a second full raw-byte copy of the catalog. It reports final consumed bytes even if the UTF-8 decoder consumes a trailing sequence without yielding visible characters. Progress remains optional for manifest/delta requests, and unknown `Content-Length` continues to be represented as `-1`. Added tests with multilingual UTF-8 payloads and unknown total size. | `[~]` — two local Kotlin reader tests passed; Android Gradle/JUnit CI and UI progress validation pending |
| P2-25 | `filter_stations(..., require_active=True)` deliberately retains inactive stations from the direct curated source for manual review, but that early-return path incremented only `kept_offline_curated`. Coverage summaries consequently understated total kept stations and curated/FM-evidence counts even though those records were in the output. | Updated the retained-curated branch to increment `kept`, `kept_curated`, and `kept_fm_evidence` when appropriate, as well as its existing `kept_offline_curated` counter. Selection conditions and station output are unchanged; expanded the existing regression assertions and added a no-FM-evidence case to ensure the counters do not overstate that metric. | `[~]` — focused regression committed; collector test suite result pending |
| P2-26 | `import_snapshot()` upserted records and replaced streams for IDs present in a JSON snapshot, but never removed station IDs absent from a later snapshot. Re-importing a smaller catalog left stale stations in the SQLite database. An empty array was also accepted as a successful import, so treating the importer as a true snapshot replacement could otherwise erase the only usable catalog. | Reject empty snapshots before schema/data changes. Validate all records up front (object shape, nonblank/unpadded string ID, and uniqueness) before initializing or modifying the DB. In the same SQLite transaction, delete existing station IDs not in the incoming snapshot, relying on the existing foreign-key cascade to remove their streams; then import all current stations. Added tests for stale station/stream deletion, empty-snapshot preservation, transaction rollback after a malformed station, and protection from duplicate/invalid IDs. API CI now explicitly sets `PYTHONPATH=api/src` while running the API and catalog metadata tests together. | `[~]` — implementation committed; five focused local Python tests passed against the importer logic/schema; full API suite remains unverified |
| P2-27 | `tools/build_catalog_delta.py` built `current_by_id` as a dictionary comprehension, silently overwriting duplicate station IDs (and skipping missing IDs), while `station_count` still described the original list length. The generated `stations.json`, manifest counts/delta, imported SQLite primary keys, and Android station cache could then disagree. | Added `index_unique_stations` to validate each current record is an object with a string ID that is nonblank and unpadded, and reject duplicate IDs before generating a version/manifest/delta. This matches the stricter SQLite importer boundary. The previous catalog remains tolerant of legacy shape, but newly generated output cannot silently collapse records. Added tests for duplicate IDs, missing/blank/padded/non-string IDs and non-object records. | `[~]` — implementation committed; five local Python metadata-helper tests passed; full catalog/API CI remains unverified |
| P2-28 | `start_background_refresh()` accepted zero or negative intervals. A zero interval causes a tight loop of sleep-and-download attempts, while a negative interval makes `time.sleep()` raise inside the worker and silently ends automatic refresh. | Reject non-positive intervals immediately with a clear `ValueError`, before creating the background thread. Added parameterized tests for zero and negative values; normal positive intervals retain current behavior. | `[~]` — seven focused local catalog-sync scenarios passed; full API CI remains unverified |

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


## Resume checkpoint — 2026-10-18 (catalog filtering responsiveness)

- Review found `loadStations` still scanned the entire loaded station list synchronously from UI callbacks. Search on the warm-cache path could scan and lowercase fields for up to 50,000 stations on the main thread after each debounced query.
- Routed station filtering and paging through the existing background result processor and serial executor. Both successful results and transform failures are posted through the main executor; filtering rules, offsets, limits, and source order are unchanged.
- The underlying filter function is unchanged, and the shared processor already has pure-JVM tests for background-transform/callback-executor ordering. Android Gradle CI and interactive phone/car search responsiveness have not been verified here; P2-18 remains `[~]`.


## Resume checkpoint — 2026-10-18 (concurrent catalog sync follow-up)

- A review of the new cold-start request queue found that multiple queued requests could call `syncCatalog` independently. With the client's two-worker executor, this could duplicate manifest/catalog downloads and cache writes on a cold start.
- Added `SingleFlightCallbacks` to coalesce simultaneous sync requests and distribute one processed result to waiting callers. On failure, each waiter still falls back to the local station list it supplied, or receives the failure when it has no local data.
- Added JVM tests covering coalescing/order, coordinator reuse after completion, clearing pending callbacks, and duplicate completion suppression.
- A further cold-start review found that callers arriving after a cache miss but before network completion could trigger another disk read. Added a one-time-load completion flag so requests after a missing/unusable cache join or retry sync directly without reparsing the same file.
- The pure coordinator tests are committed but the Android Gradle/JUnit suite has not been run here. A standalone Kotlin compile/runtime harness passed the coordinator's coalescing, delivery-order, clear, and duplicate-completion checks. Repository-level cold-start/network behavior still needs Android CI or integration validation; P2-15 and P2-17 remain `[~]`.
- The comparison made on 2026-10-18 after the latest code/test commits and before this documentation update showed 112 commits ahead of `experiment/lava-lamp-player`, 0 behind. No reference-branch or `main` update was made.


## Resume checkpoint — 2026-10-18 (image request coalescing)

- Completed the previously deferred P2-10 optimization: concurrent calls for the same logo URL now share one disk/network operation instead of competing for the same file and downloading duplicate data.
- Added a thread-safe keyed request coordinator. Cache lookup/registration and success cache insertion/completion are synchronized together to avoid the race where a caller misses the cache immediately before completion and starts a second download. Different URLs remain independent.
- Success and failure callbacks are delivered individually through the main Handler; memory-cache hits retain the prior immediate callback behavior. Closing the loader clears pending callback references.
- Wrapped the worker's cache/file operations so unexpected exceptions complete the in-flight request as a failure instead of leaving every coalesced caller waiting indefinitely.
- Added pure-JVM tests for same-key joining and delivery order, independent keys, cache hit, failure/retry, and clearing. Four equivalent coordinator scenarios passed in a local Kotlin 1.9 harness; `ImageLoader` plus the coordinator passed a syntax/type compile against lightweight Android API stubs. This is not the actual Android Gradle/JUnit suite, and end-to-end image fetching still needs CI validation; P2-10 stays in progress.


## Resume checkpoint — 2026-10-18 (shared cache snapshot)

- Found that the Activities and `ApiCatalogRepository` each created their own `CatalogCacheStore` for the same file, allowing duplicated gzip/JSON parsing and separate in-memory snapshots that could be stale after writes.
- Injected the Activity's existing store into its repository so both paths share one `memorySnapshot`; synchronized store methods serialize the disk read/write. The Activities force the lazy station-index build on their existing background cache task, and `Snapshot.stationsById` now uses synchronized lazy initialization to make sharing safe across threads.
- `CatalogCacheStore` now remembers that a missing/invalid cache was already read, instead of repeatedly probing or reparsing the same file; a successful save installs a new in-memory snapshot and completes that state.
- P2-19 is recorded as in progress until Android Gradle tests and startup contention can be checked. The standalone Kotlin tests and stub-based source compilation do not validate Android lifecycle/device behavior.

- On a disk-cache hit, updating the cached file's last-modified timestamp is now best-effort; failure to update recency no longer turns a decoded image into an image-load failure.


## Resume checkpoint — 2026-10-18 (catalog count validation compatibility)

- Confirmed the currently published `catalog-data/data/catalog-manifest.json` has `station_count`, `updated_count`, and `removed_count` but no `playable_station_count`. Since `station_count` is the raw generator input count and Android caches only stations retaining playable streams, the old same-version and delta comparisons were not comparing like-for-like values.
- `tools/build_catalog_delta.py` now emits `playable_station_count` matching nonblank URLs with blank/online status, and Python tests cover case-insensitive statuses, missing/blank status, mixed online/offline streams, and malformed lists.
- Android uses the optional playable count when available. Legacy manifests no longer compare their raw count to a filtered local projection; delta updated/removed array counts are still checked against existing manifest fields, and full downloads still validate raw `station_count`.
- Updated `.github/workflows/api-tests.yml` so changes to the catalog manifest builder/tests trigger on both push and pull request, and it runs `python -m pytest api/tests tools/tests` from the repository root.
- Ran the helper's two Python scenarios in an isolated fixture (2 passed) and the pure Kotlin delta-policy test scenarios (5 passed). Neither is a substitute for the actual repository workflow or Android Gradle suite. The live `catalog-data` manifest intentionally remains unchanged until the guarded workflow publishes from `main`; P2-20 stays `[~]`.


## Resume checkpoint — 2026-10-18 (same-version metadata preservation)

- Found that the unchanged-version path round-tripped cached stations through JSON before returning them. That intermediate shape does not carry `songTitle` or `artist`, so repository cache persistence could erase locally saved current-track metadata after a no-change catalog check.
- Added `StationApiMapper` for the cached station projection and used it directly in the same-version sync path. The local count check now uses the number of cached stations directly and only compares it to `playable_station_count` when the optional field exists. Full catalog downloads still validate raw `station_count`; delta payload counts remain independently validated.
- Added two pure Kotlin mapper tests for metadata preservation and blank optional fields. Both passed in a local Kotlin/JUnit-stub smoke harness; this verifies the mapper and test logic but is not the Android Gradle test run.


## Resume checkpoint — 2026-10-18 (empty-cache startup recovery)

- Found that both Activities used cache age alone to decide whether to call `loadRemoteCatalog()`; a recently written snapshot with zero stations was treated as fresh. On that startup path the app could remain without stations until the refresh interval expired.
- Added `CatalogRefreshPolicy` and used it in both Activities. A cache with no stations or an invalid/expired timestamp now triggers refresh; a populated, recent cache continues to be used for warm start.
- Added four pure Kotlin policy test scenarios. All four passed in a local Kotlin 1.9 smoke harness with a minimal JUnit-compatible runner. Android Gradle/JUnit and device startup checks remain pending; P2-22 stays `[~]`.
- The most recent branch comparison before this documentation update showed 133 commits ahead of `experiment/lava-lamp-player`, 0 behind. Neither `main` nor the reference branch was changed.


## Resume checkpoint — 2026-10-18 (track metadata across catalog versions)

- Follow-up review found the previous same-version fix did not cover delta or full downloads: catalog JSON does not ordinarily carry the currently playing track, and `parseStation` initializes those fields to null.
- Added `StationTrackMetadataPolicy` at the sync-result boundary so all paths preserve previously observed nonblank title/artist by station ID when the incoming catalog has no nonblank replacement. New station IDs never inherit metadata from other stations.
- Added three JVM tests for preservation, server-value precedence with blank-value fallback, and station-ID isolation. Android Gradle/JUnit CI and live playback metadata validation remain pending; P2-23 stays `[~]`.
- The branch comparison at the start of this metadata follow-up showed 137 commits ahead of `experiment/lava-lamp-player`, 0 behind; no reference branch or `main` was modified.


## Resume checkpoint — 2026-10-18 (byte-accurate download progress)

- Found that the catalog downloader added the number of decoded characters to `loadedBytes` while using the HTTP `Content-Length` value (bytes) as the total. With multilingual catalog text those values can diverge.
- Added `ProgressReportingUtf8Reader`, which counts each raw byte read and decodes the complete payload as UTF-8 after reading. Manifest/delta downloads still suppress progress callbacks; full catalog downloads report values in consistent byte units. Unknown content length stays `-1`.
- Added two test scenarios for multilingual UTF-8 and unknown content length. Both passed in a local Kotlin 1.9 smoke harness; this is not the Android Gradle/JUnit run, so P2-24 remains `[~]`.
- Branch comparison before this documentation update: 141 commits ahead of `experiment/lava-lamp-player`, 0 behind. No update to `main` or the reference branch was made.


## Resume checkpoint — 2026-10-18 (collector retained-station metrics)

- Fixed a diagnostics bug in `filter_stations`: direct curated records retained under `require_active=True` now contribute to the total kept/curated counts, plus FM-evidence count only when the station actually has such evidence. The existing `kept_offline_curated` counter is preserved.
- Expanded the targeted filter regression test to assert the counters for the retained offline curated record. No station-selection condition or output ordering changed.
- The latest available commit status endpoint returned no statuses, and the available workflow-runs endpoint only exposes pull-request-triggered runs; the collector CI outcome for this edit is unverified.


## Resume checkpoint — 2026-10-18 (snapshot importer correctness)

- Fixed `api/src/radio_api/importer.py`: it now treats input as a full snapshot, deleting stale station IDs (and cascading their streams) in the same transaction before inserting/updating current records.
- Empty snapshots are rejected before database initialization or mutation, protecting a previously usable catalog from an accidental empty import. Malformed records still roll back stale deletions and prior upserts because changes stay inside the connection transaction.
- Added three API tests for stale station/stream removal, empty-snapshot preservation, and transaction rollback on invalid station data. Three focused tests passed in a local Python harness using the importer logic and matching SQLite schema. The full repository API workflow has not run in this environment.
- Collector metrics change now has an additional regression test verifying a manually retained curated offline station with no FM evidence contributes to kept/curated counts, but not to FM-evidence count. The latest collector workflow status is not available through the current status endpoint.
- The branch comparison before this checkpoint update showed 149 commits ahead of `experiment/lava-lamp-player`, 0 behind. No reference branch or `main` was modified.


## Resume checkpoint — 2026-10-18 (validation and test harness)

- Reran a standalone Kotlin smoke harness for the new pure helpers (metadata mapping/preservation, delta-count validation, empty/stale-cache policy, keyed image request coalescing, catalog sync coalescing, and UTF-8 byte progress): 20 assertions passed. It uses lightweight model stubs and is not a substitute for compiling/testing the Android Gradle module.
- Re-ran the focused Python importer harness: 3 tests passed (snapshot replacement, empty-input safety, and rollback on malformed input). The committed full API test suite has not been run in this environment.
- The API workflow now sets `PYTHONPATH=api/src` explicitly for the combined `api/tests` and `tools/tests` invocation to avoid relying on pytest config discovery across the two test directories.
- Branch comparison before the playability-parity checkpoint: 158 commits ahead of `experiment/lava-lamp-player`, 0 behind; `main` and the reference branch remain unchanged. The current GitHub status endpoint returns no checks for the latest code commit, so Android/collector/API CI is still unverified.


## Resume checkpoint — 2026-10-18 (stream playability parity)

- Tightened the Python `playable_station_count` helper to match Android's `StreamAvailabilityPolicy` precisely: the URL must be nonblank, and status is playable only when blank/whitespace or exactly `online` ignoring case. A status such as `" online "` is intentionally not normalized to online, matching Android.
- Isolated the behavior in `_is_playable_stream_record` and expanded the fixture to cover padded, numeric, and boolean statuses. A local focused Python check over ten cases passed; the full tools/API CI run is still pending verification.


## Resume checkpoint — 2026-10-18 (catalog ID integrity)

- The catalog manifest builder previously used a dict comprehension keyed by station ID, which silently overwrote duplicated IDs (and omitted missing IDs) while `station_count` still reflected the raw list length. SQLite's station primary key and the Android cache use IDs as identity, so such input could make JSON, delta, manifest, and cache counts disagree.
- Added `index_unique_stations` to reject current snapshots containing a non-object record, missing/blank/whitespace-padded ID, or duplicate ID before semantic-version/delta generation. It does not rewrite catalog records or guess which duplicate is authoritative; the build fails safely instead of publishing an ambiguous catalog.
- Added regression tests for duplicates and malformed IDs/records. The five local `tools/tests/test_catalog_manifest.py` scenarios passed. The source workflow's full catalog/API CI status is still unavailable.
- Branch comparison before this documentation update: 161 commits ahead of `experiment/lava-lamp-player`, 0 behind. `main` and the reference branch were not modified.


## Resume checkpoint — 2026-10-18 (bounded ICY metadata probing)

- A review of the API metadata diagnostic found that a remote stream's `icy-metaint` header was trusted as the exact number of bytes to buffer before metadata. A malformed stream could declare a huge interval and keep delivering bytes, causing the byte reader to grow its buffer well beyond the useful size for a station diagnostic.
- Added `MAX_ICY_METAINT_BYTES = 1 MiB`. Oversized values now return the stable diagnostic error `icy_metaint_too_large` before the response body is read. Valid interval behavior remains unchanged.
- Added `api/tests/test_metadata_probe.py` with one oversized-interval early-rejection test and one normal ICY title parsing regression test. Both passed in the focused local Python harness; the full API workflow has not been verified.
- One file-creation call accidentally targeted the repository default branch instead of this work branch. The misplaced test file was immediately deleted from `main`, its absence was verified, and it was then created on this work branch. This restored the file tree on `main`; the branch history contains the add/delete corrective commits.
- Branch comparison before this documentation update: 164 commits ahead of `experiment/lava-lamp-player`, 0 behind. No change to the reference branch.


## Resume checkpoint — 2026-10-18 (importer ID validation)

- Mirrored catalog-builder identity checks at the public SQLite importer boundary. Before schema initialization or data mutation, `import_snapshot()` now rejects non-object records, missing/blank/whitespace-padded IDs, and duplicate IDs. This protects direct CLI/API importer callers too, not only the guarded catalog-build workflow.
- Added tests proving duplicate/malformed IDs are rejected while an existing snapshot and its streams remain untouched. Five importer tests passed in the local SQLite harness (snapshot replacement, empty input, transactional rollback, duplicate ID, invalid IDs); this harness mirrors the committed importer and is not the entire API suite.
- Branch comparison before this documentation update: 167 commits ahead of `experiment/lava-lamp-player`, 0 behind. `main` remains free of the misplaced test file; the corrective add/delete commits remain in its history, as disclosed in the prior checkpoint.


## Resume checkpoint — 2026-10-18 (recover corrupt API database on startup)

- Found that API startup called `initialize(DB_PATH)` before the download attempt. If a pre-existing catalog file was corrupt or not SQLite, the initializer could throw before `download_catalog` got a chance to validate and atomically replace it.
- Removed the initializer from startup; `has_usable_catalog` now decides whether a previous file is safe to fall back to, and the validated download can replace a corrupt target. If both the existing DB is unusable and download fails, startup still fails rather than serving an empty/invalid DB. Other endpoints retain their own schema-initialization behavior where needed.
- Added `test_startup_recovers_corrupt_local_database_when_download_succeeds`. Two focused local startup scenarios passed in a SQLite harness: corrupt DB is replaced when download succeeds; corrupt DB plus download failure still aborts startup. These checks mirror the startup/db behavior and do not replace the full API workflow.
- Branch comparison before this documentation update: 170 commits ahead of `experiment/lava-lamp-player`, 0 behind. `main` and the reference branch were not changed by this fix.


## Resume checkpoint — 2026-10-18 (bounded SQLite catalog downloads)

- Added `MAX_CATALOG_DOWNLOAD_BYTES = 256 MiB` to the API's SQLite fetcher. The download loop checks the remaining budget before writing the next chunk, and reads at most one byte beyond the budget to detect an oversized response without writing that extra data.
- On limit breach, the existing exception cleanup removes the temporary file and leaves the previous destination intact; SQLite integrity validation and atomic replacement run only after an in-limit response completes.
- Added a test where a large fake response exceeds a monkeypatched small limit and verifies the old DB stays unchanged and the temporary file is removed. All three focused `catalog_sync` tests passed in the local harness (invalid DB preservation, size-cap preservation, and valid SQLite acceptance); this is not the full API suite.
- Branch comparison before this documentation update: 174 commits ahead of `experiment/lava-lamp-player`, 0 behind.


## Resume checkpoint — 2026-10-18 (catalog ID type parity)

- Tightened the catalog generator's ID validation one step further: IDs must be actual strings, not just values that stringify to nonblank text. This matches the SQLite importer's explicit ID contract and catches numeric/boolean IDs before manifest/delta generation rather than failing later in import.
- Expanded the local metadata fixture to exercise numeric and boolean IDs; five focused tests still pass. This verifies the checked cases in the local helper harness, not the complete catalog workflow.
- Branch comparison before this documentation update: 177 commits ahead of `experiment/lava-lamp-player`, 0 behind.


## Resume checkpoint — 2026-10-18 (SQLite schema compatibility guard)

- Extended the shared `REQUIRED_COLUMNS` contract for API catalog databases. Both `has_usable_catalog` (startup fallback) and `_validate_catalog_database` (download replacement) now reject tables missing columns queried by runtime endpoints, not just missing table names.
- Updated the valid-download fixture to use the application schema and added an incomplete-schema fixture with required tables and data but missing runtime columns. The test verifies download rejection, temp-file cleanup, and preservation of the existing target.
- Four focused local `catalog_sync` scenarios passed: malformed SQLite rejected, oversized response rejected, incomplete schema rejected, and a valid database accepted. This is the local mirrored harness; complete API CI remains unverified.
- An additional local startup/schema harness test verified that `has_usable_catalog` rejects a SQLite file that contains the expected table names but lacks required columns; three startup/fallback predicate scenarios passed.
- Branch comparison before this documentation update: 182 commits ahead of `experiment/lava-lamp-player`, 0 behind.


## Resume checkpoint — 2026-10-18 (single API catalog startup path)

- Found that the `api/start.sh` deployment mode downloaded the SQLite file before launching Uvicorn, while `app.startup()` downloaded it again. This wasted a full catalog transfer on each start and bypassed the application's last-known-good fallback when the shell's first download failed.
- Simplified `api/start.sh` to set `PYTHONPATH` and exec Uvicorn; the FastAPI startup lifecycle now exclusively owns catalog validation/download/recovery/background refresh. Updated `api/README.md` accordingly.
- The shell script's syntax check and local startup-recovery scenarios remain the intended targeted validation; full deployment/CI validation is pending.
- Branch comparison before this documentation update: 187 commits ahead of `experiment/lava-lamp-player`, 0 behind. `main` and the reference branch were not modified by this change.


## Resume checkpoint — 2026-10-18 (concurrent database download isolation)

- Replaced the fixed `<target>.tmp` path in `download_catalog` with a unique temp file created in the target directory. Multiple startup workers can now download and validate independently, and each failure cleanup removes only its own temp file. Final publication remains a same-directory `os.replace` after byte-limit and SQLite/schema checks.
- Added a barrier-based test that runs two downloads concurrently and forces both to reach validation at the same time; it verifies distinct temp paths, successful completion of both calls, a valid final file, and no leftover temp files. Five focused local download tests passed.
- Branch comparison before this documentation update: 191 commits ahead of `experiment/lava-lamp-player`, 0 behind; no changes were made to `main` or the reference branch for this fix.


## Resume checkpoint — 2026-10-18 (background refresh interval validation)

- Added an upfront positive-interval check to `start_background_refresh`. Invalid `CATALOG_REFRESH_SECONDS=0` no longer creates a tight download loop, and negative values fail clearly before starting a thread rather than killing the worker at its first `sleep`.
- Added a parameterized test for zero and negative intervals. Seven focused local catalog-sync scenarios passed, covering invalid SQLite, oversized payload, incomplete schema, independent concurrent temp paths, valid SQLite, and both invalid refresh intervals.
- Branch comparison before this documentation update: 194 commits ahead of `experiment/lava-lamp-player`, 0 behind. The live published catalog and both protected branches were not changed by this fix.


## Resume checkpoint — 2026-10-18 (metadata probe concurrency budget)

- A per-call semaphore only capped one request. Several concurrent `/debug/metadata` requests each had their own limit of eight, and the public parameters could generate up to 300 URLs per request. This could multiply total outbound probes and keep diagnostic requests active for several minutes.
- `metadata_probe.probe_streams` now uses a weakly-referenced semaphore shared by callers on the same asyncio event loop, capped at eight active probes, plus its existing per-call semaphore. The semaphore reference is weak so semaphore/event-loop lifetime cycles do not leave test or server loops pinned in the registry.
- `app.debug_metadata` rejects batches over 32 URLs with HTTP 422 before starting any network probes. A process-wide nonblocking `BoundedSemaphore` allows at most four simultaneous diagnostic requests and returns HTTP 429 immediately for excess requests, so they do not accumulate an unbounded queue of batches. The gate is released by FastAPI's yield-dependency cleanup on successful or failed requests.
- Added tests for concurrent probe batches sharing one eight-probe limit, a too-large 33-URL request being rejected before network probes, and the fifth simultaneous batch slot receiving HTTP 429. The standalone metadata-probe mirror tests pass (3 passed); endpoint-level 422/429 tests are committed but the full API test suite and load validation have not been run here. README now documents the 4-request / 32-URL / 8-active-probe bounds.
- Latest branch comparison before this documentation update: 209 commits ahead of `experiment/lava-lamp-player`, 0 behind. The published catalog and protected branches remain unchanged by this block.

- A per-call semaphore only capped one request. Several concurrent `/debug/metadata` requests each had their own limit of eight, and the public parameters could generate up to 300 URLs per request. This could multiply total outbound probes and keep diagnostic requests active for several minutes.
- `metadata_probe.probe_streams` now uses a weakly-referenced semaphore shared by callers on the same asyncio event loop, capped at eight active probes, plus its existing per-call semaphore. The semaphore reference is weak so semaphore/event-loop lifetime cycles do not leave test or server loops pinned in the registry.
- `app.debug_metadata` rejects batches over 32 URLs with HTTP 422 before starting any network probes. Added API tests for rejecting such a batch and a metadata-probe test running two concurrent batches to verify their combined active count does not exceed eight; README now documents these caps.
- Local mirror run for metadata probe tests: `3 passed`; the request-slot generator harness also passed (`1 passed`, including release after the limit error). This covers ICY parse/error and shared semaphore behavior, plus the gate's HTTP 429 exception/release contract. The endpoint-level 422 and full FastAPI dependency/request integration tests are committed but the full API test suite has not been run here. Latest GitHub status endpoint has no current API CI result.
- Branch comparison before this documentation update: 202 commits ahead of `experiment/lava-lamp-player`, 0 behind.


## Resume checkpoint — 2026-10-18 (catalog must contain a playable stream)

- Hardened both API catalog acceptance paths. A candidate SQLite DB must have at least one station-linked stream with `status = 'online'`; a non-empty `stations` table alone is not considered serviceable because API search endpoints filter to stations with online streams.
- `has_usable_catalog` now rejects offline-only databases as startup fallbacks, and `_validate_catalog_database` rejects them before atomic replacement. The main-guarded catalog workflow also stops before publication when its SQLite validation reports `online_streams <= 0`.
- Added regression cases for downloaded offline-only catalog preservation and offline-only fallback rejection. The focused local mirrors passed `8 passed` for catalog sync and `4 passed` for startup/fallback behavior. Full API/collector GitHub Actions remains unverified.
- Branch comparison before this documentation update: 216 commits ahead of `experiment/lava-lamp-player`, 0 behind.


## Resume checkpoint — 2026-10-18 (offline-only catalog rejection)

- Strengthened the catalog usability contract beyond table shape/non-empty station count: the DB must contain at least one station-linked stream whose status is `online`, because API search/listing endpoints intentionally exclude stations without online streams.
- `has_usable_catalog` rejects offline-only databases as fallbacks; `_validate_catalog_database` rejects them before atomic replacement; and the main-guarded catalog publication workflow now exits if `online_streams <= 0`.
- Added focused tests for rejecting the downloaded offline-only DB while preserving the previous target and refusing an offline-only startup fallback. Local mirror suites currently pass: 8 catalog-download/schema/concurrency/playability cases and 4 startup/fallback cases. Full API/Actions CI remains unverified.
- Branch comparison before this documentation update: 216 commits ahead of `experiment/lava-lamp-player`, 0 behind; protected/reference branches were not changed by this block.


## Resume checkpoint — 2026-10-18 (total deadline for ICY body reads)

- The HTTP client's read timeout is an inactivity timeout; a stream that keeps delivering occasional small chunks can avoid that timeout while stretching the read of the audio interval and metadata over a long period. With only four diagnostic request slots, such streams could occupy the slots far longer than the request's `timeout_seconds` suggests.
- `_ByteReader` now starts a monotonic deadline when response-body reading begins and passes the remaining budget to each awaited stream chunk. Deadline expiry is returned as the established `timeout` probe error; the earlier ICY interval size cap still limits maximum buffered bytes.
- Added a regression test using a fake slow-drip response (partial first chunk, then a delay longer than the budget). The test is committed but was not executable from the current environment: no local repository clone is mounted and the container cannot resolve GitHub. The available GitHub status endpoint also has no new API CI run to report. This item must stay in progress until the test and API workflow pass.
- Branch comparison before this documentation update: 221 commits ahead of `experiment/lava-lamp-player`, 0 behind.
