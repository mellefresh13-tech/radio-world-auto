# Roadmap

## Phase 1 — Foundation
- [x] GitHub repository
- [x] project documentation
- [x] Android native skeleton
- [x] classic XML Views UI
- [x] catalog data model
- [x] source adapter concept
- [x] source research

## Phase 2 — Catalog ingestion
- [x] Radio Browser adapter
- [x] IPRD adapter
- [x] countries registry
- [x] genre normalization
- [x] raw snapshot writer
- [x] station merge layer
- [ ] Icecast adapter
- [x] bounded scheduled raw snapshot generated from primary sources
- [x] source coverage report

## Phase 3 — Stream discovery
- [x] basic URL extraction from HTML
- [x] HLS/M3U8 detection
- [x] stream verification
- [x] bounded parallel verification
- [ ] PLS/M3U playlist expansion
- [ ] JavaScript extraction
- [ ] browser/network extraction
- [ ] Icecast/Shoutcast server discovery
- [ ] search-engine discovery
- [ ] official-site crawler at scale

## Phase 4 — Quality
- [ ] station fuzzy deduplication
- [ ] stream deduplication
- [ ] source confidence model
- [ ] dead stream policy
- [ ] scheduled recheck
- [ ] coverage statistics
- [ ] manual review queue for ambiguous matches

## Phase 5 — Backend
- [x] canonical SQLite database
- [x] read-only API
- [x] country endpoint
- [x] genre endpoint
- [x] search endpoint
- [x] station endpoint
- [x] stream fallback metadata

## Phase 6 — Android MVP
- [x] Android project
- [x] Media3 player service skeleton
- [x] landscape-first player shell
- [x] player design implementation from supplied mockup
- [x] country browser
- [x] genre browser
- [x] favorites
- [x] recently played
- [x] automotive search
- [x] persistent favorites/recent state
- [x] reconnect/fallback skeleton
- [x] adaptive landscape layouts
- [x] adaptive portrait layouts

## Phase 7 — Automotive hardening
- [ ] multiple head-unit resolutions
- [ ] screen-off playback
- [x] audio focus
- [x] network loss handling
- [ ] low-light UI
- [ ] Android Auto / compatible integrations
- [ ] real head-unit testing

## Phase 8 — Production
- [x] scheduled catalog snapshot job
- [x] GHCR API image publication pipeline
- [ ] monitoring
- [ ] backups
- [ ] API caching
- [ ] rate-limit protection
- [ ] Play Store packaging
- [ ] attribution/source pages
- [ ] privacy policy
