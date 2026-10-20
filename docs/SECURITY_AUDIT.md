# Radio World Auto — security audit

**Status (2026-10-20):** security hardening PR #39 is merged into `main` as `e3d96c5`. The manual Android Release APK run #616 passed unit tests, lint, release build, persistent signing and the trusted SHA-256 certificate fingerprint check. Owner reports that the `android-release-signing` environment is configured. Remaining closeout items: verify the next scheduled catalog publication and complete a short device smoke test. This is a source/configuration hardening pass, not a penetration-test certification.  
**Baseline:** `main` at `e3d96c548f0bd815ee58895546e523cdce0f3939` (2026-10-20).  
**Rule:** do not make security changes directly on `main`; preserve playback, catalog ordering, existing layouts, and the tested release behavior.

## Scope and limits

Reviewed the current Android manifest, the exported Media3 playback services, crash-report storage, the release-signing workflow, all tracked GitHub Actions workflows, dependency declarations, and the repository's current indexed code for common private-key/token markers. Reviewed GitHub's reported protection state for `main`.

This is a source/configuration audit, not a claim of complete penetration testing. The review does not expose repository secret values, GitHub secret-scanning alert contents, historical deleted blobs, Play Console settings, the signing certificate actually installed on a device, or production runtime traffic. No production account or user device was attacked. Dynamic verification and device checks remain open.

## Confirmed findings

| ID | Priority | Finding | Disposition |
|---|---|---|---|
| SEC-01 | **P1 — owner action** | Direct changes to release source must be gated by PR review and required checks. | **Owner-side setup reported complete.** The repository owner has configured protection for `main` with three required CI checks. Keep force-push/deletion blocked for `main`; do not apply that deletion rule to `catalog-data`, which is intentionally replaced by generated snapshots. |
| SEC-02 | **P1 — release integrity** | The APK workflow fell back to an ephemeral signing key but uploaded the result under the same `radio-world-auto-release` artifact name. The same successful workflow status therefore did not distinguish a persistent-signature build from an APK that cannot update an install signed with another key. | **Fixed and merged in PR #39.** Artifact names distinguish persistent and temporary signing; incomplete persistent-key configuration and missing expected fingerprints fail; signing is restricted to `main` and uses the `android-release-signing` environment; `apksigner` reads passwords from environment-variable references. Owner reports that environment configuration is complete; release run #616 confirms the persistent signing path and fingerprint comparison succeeded. |
| SEC-03 | **P2 — local playback disruption** | The exported `RadioPlaybackService` used the deprecated `AcceptedResultBuilder(session)` and explicitly assigned the trusted default session commands to every connecting controller. A local app able to connect as a media controller could control the player, inject a media item/URL, and invoke the destructive custom `Close Radio` command. | **Fixed and merged in the earlier security hardening PR.** The callback starts with Media3's controller-aware defaults, so untrusted clients get read-only commands; `Close Radio` is added and executed only for a trusted controller. HUR/Android Auto compatibility still needs a device check. |
| SEC-04 | **P2 — diagnostic privacy** | `CrashReporter` stores up to 8 KB of an uncaught stack trace in `files/last_crash.txt`. With backup enabled and no explicit exclusion, that file could be included in backup/device transfer; traces can contain runtime details or URL strings. | **Fixed and merged.** The file is excluded from legacy backup, cloud backup, and device transfer. User settings and other app data remain eligible for backup. |
| SEC-05 | **P2 — CI supply chain** | Tracked workflows referenced GitHub Actions by moving major tags (`@v4`, `@v5`). A tag can be retargeted upstream, changing code executed in the runner. GitHub recommends pinning actions to a full commit SHA. | **Fixed and merged.** Existing action references are pinned to the commit currently resolved by their upstream version tags, with version comments. |
| SEC-06 | **P2 — catalog publishing token exposure** | The catalog build originally held a `contents: write` token while installing Python dependencies and running source-data processing. If a dependency or executed code were compromised, it could overwrite the published catalog branch. | **Source fix merged.** `build-catalog` has read-only contents permission and uploads a publication bundle; a separate minimal `publish-catalog` job gets `contents: write` and publishes only the validated bundle. Regression tests pass; the next scheduled run is the remaining end-to-end publication check. |
| SEC-07 | **P2 — transport integrity / compatibility** | `android:usesCleartextTraffic="true"` permits cleartext HTTP across the app, not just for a specific radio host. A network attacker may be able to alter an HTTP stream or logo response. | **Explicitly accepted for this release.** Some public radio stations only offer HTTP; a global HTTPS-only change could silently break station playback. Keep current compatibility behavior. Revisit only as a separate, measured HTTPS-first project with a station-coverage test, not as part of this closeout. |
| SEC-08 | **P2 — signing identity** | The workflow did not record or compare the official signing-certificate fingerprint against a known expected value. A changed repository secret could produce a correctly signed APK using an unexpected key. | **Verified in release run #616 after PR #39.** The persistent certificate fingerprint step passed and confirmed that the actual certificate matches the configured expected fingerprint. Secret values remain intentionally inaccessible to this review. |

## Implemented security hardening

1. Added Android backup exclusions for `last_crash.txt` while retaining backup/restore of ordinary settings.
2. Restored Media3's per-controller command defaults (read-only for untrusted controllers) and added a small unit-tested policy that gates the destructive media-session custom command at both advertisement and execution.
3. Limited the release workflow's `GITHUB_TOKEN` permission to `contents: read`, restricted signing to `main`, made the expected certificate fingerprint mandatory for persistent-key builds, distinguished persistent-key releases from temporary-key test builds, and passed `apksigner` passwords via environment-variable references rather than literal command-line arguments.
4. Pinned the action references in tracked workflows to immutable full commit SHAs.
5. Split catalog generation (read-only token) from publication (write token only in a minimal artifact publisher), and added independent JSON/SQLite integrity, content and size checks in the publishing job.
6. Added regression tests for the controller trust policy, backup exclusions, release signing-artifact distinction, immutable action pins, catalog publisher permissions, and the metadata diagnostic token gate.
7. Removed test-only packages from the API runtime image, added `api/requirements-dev.txt`, and ignored local signing keys and environment files.
8. Disabled `/debug/metadata` unless `METADATA_DEBUG_TOKEN` is configured; when enabled, the endpoint requires a Bearer token.

## Secret-search observation

The current default-branch code-search query returned no matches for common private-key headers, GitHub personal-token prefixes, or an API key marker. The plain-text `password` matches were in the signing workflow's documented temporary-key logic and a network-authentication implementation, not a checked-in credential. **This is not proof that no secret ever existed**: it does not cover all Git history, ignored/untracked local files, repository/environment secret values, workflow logs, or GitHub's private secret-scanning alerts. Those need separate checks in repository settings and the Actions/Play Console configuration.

## Further source review — merged URL-boundary hardening (PR #27)

The Android image path loads station-logo URLs from catalog records through `ImageLoader`. A compromised or malicious catalog record could previously use non-HTTP schemes, localhost/private addresses, or automatic cross-host redirects to induce unexpected network requests. This was a credible trust-boundary concern, not a demonstrated exploit.

**Merged in PR #27:** `StationLogoUrlPolicy` restricts logo URLs to HTTP(S), rejects credentials and local/special-use hostnames and IP ranges, checks DNS answers, disables automatic redirects, applies the same checks to each redirect target, caps redirects at five, and blocks HTTPS-to-HTTP downgrade redirects. Unit tests cover malformed schemes, private/local IPv4 and IPv6 ranges, DNS answers containing private addresses, relative redirects, unsafe redirect targets, and downgrade redirects. Existing byte-size and bitmap-dimension limits remain in place.

PR #27 also restricts the manifest's `delta_url` to the app's canonical GitHub Raw URL; missing, malformed, or different values fall back to the hard-coded canonical URL rather than being opened directly.

**Limit:** `HttpURLConnection` resolves a hostname again at connect time after the policy performs DNS checks, so the client cannot guarantee DNS pinning against a rebinding race with this implementation. The changes reduce the URL-boundary exposure but do not constitute a complete network sandbox. Device/runtime testing of representative legitimate logo redirects remains required.

## Finite closeout checklist

The closeout is intentionally limited to the two remaining operational checks below. Do not start another broad refactor or expand this audit to unrelated Dependabot updates unless a check fails.

- [x] Merge source hardening PRs #24 and #27; their Android regression tests passed on their respective PR heads.
- [x] Pin Python runtime/test dependency versions in PR #28 and keep shared package versions consistent across manifests.
- [x] Add the persistent APK signing-certificate fingerprint guard in PR #29.
- [x] Configure Dependabot weekly and limit concurrent update PRs to two per ecosystem in PR #37. Existing PRs #30–36 are optional proposals, not changes to `main`; they are intentionally outside the release gate.
- [x] Owner reports protection for `main` and the three required PR CI checks are configured.
- [x] Owner reports adding the repository Actions secret `ANDROID_SIGNING_CERT_SHA256`; secret values cannot be read during this source review.
- [x] **Owner-side signing environment setup:** owner reports that `android-release-signing` is configured with deployment restricted to `main` and the five signing secrets moved to environment-level secrets. The GitHub connector cannot inspect secret values or independently verify their settings.
- [x] **Manual APK validation:** [Android Release APK run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955) on `main` passed unit tests, lint, release build, persistent-key preparation, trusted certificate fingerprint verification, signing and artifact upload. The uploaded release artifact is `radio-world-auto-release`.
- [ ] **Scheduled catalog validation:** at the next six-hour schedule, confirm both `build-catalog` and `Publish validated catalog snapshot` succeed, `catalog-data` advances, and the manifest, delta, SQLite database, station JSON and debug page are present. Check Pages deployment after publication. Do not manually dispatch a catalog build merely to make this check happen early.
- [ ] **Final device smoke test:** install the validated APK on the phone and, where practical, test the head unit/HUR: launch, play/pause, station switching, and updating over the same installed app without uninstalling first. This is a compatibility check, not a request for another source refactor.

## Explicitly accepted limits for this closeout

- Cleartext HTTP remains enabled for compatibility with public stations that do not offer HTTPS. Do not change that globally without coverage data and a separate compatibility plan.
- The logo URL checks reduce unsafe URL/redirect risk, but `HttpURLConnection` may resolve a hostname again at connect time, so DNS rebinding is not fully eliminated.
- Exact Python pins provide repeatable version selection but do not verify package artifact hashes. Hash-locked installs remain a possible future hardening task, not a prerequisite for this bounded verification pass.
- No full penetration test or exhaustive historical secret scan has been performed; a clean source/configuration review is not a guarantee that the application is free of vulnerabilities.

## Stop point

The APK signing and fingerprint check is now complete. When the scheduled catalog publication and final device smoke test pass, mark this source-level security hardening pass closed. No additional audit PRs are planned by default. Future Dependabot updates are routine maintenance and should be reviewed only when intentionally scheduled; they are not part of this release closeout. If a check fails, fix the specific failure, re-run that check, and then resume this same checklist.
