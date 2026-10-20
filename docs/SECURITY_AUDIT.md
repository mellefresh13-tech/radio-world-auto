# Radio World Auto — security audit

**Status:** source-level hardening PRs #24, #27, #28 and #29 are merged; Dependabot PR-limit change #37 is merged. This closeout is bounded to verifying one APK signed with the trusted certificate, one scheduled catalog publication, and a short device smoke test. This is a source/configuration hardening pass, not a penetration-test certification.  
**Baseline:** `main` at `a0ff826c98f247dfdf040be8536c5c65ccf97f23` (2026-10-19).  
**Rule:** do not make security changes directly on `main`; preserve playback, catalog ordering, existing layouts, and the tested release behavior.

## Scope and limits

Reviewed the current Android manifest, the exported Media3 playback services, crash-report storage, the release-signing workflow, all tracked GitHub Actions workflows, dependency declarations, and the repository's current indexed code for common private-key/token markers. Reviewed GitHub's reported protection state for `main`.

This is a source/configuration audit, not a claim of complete penetration testing. The review does not expose repository secret values, GitHub secret-scanning alert contents, historical deleted blobs, Play Console settings, the signing certificate actually installed on a device, or production runtime traffic. No production account or user device was attacked. Dynamic verification and device checks remain open.

## Confirmed findings

| ID | Priority | Finding | Disposition |
|---|---|---|---|
| SEC-01 | **P1 — owner action** | Direct changes to release source must be gated by PR review and required checks. | **Owner-side setup reported complete.** The repository owner has configured protection for `main` with three required CI checks. Keep force-push/deletion blocked for `main`; do not apply that deletion rule to `catalog-data`, which is intentionally replaced by generated snapshots. |
| SEC-02 | **P1 — release integrity** | The APK workflow fell back to an ephemeral signing key but uploaded the result under the same `radio-world-auto-release` artifact name. The same successful workflow status therefore did not distinguish a persistent-signature build from an APK that cannot update an install signed with another key. | **Fixed in this branch.** Artifact names now reflect the signing mode; a partially configured persistent keystore fails explicitly; temporary-key passwords are random; the build token is read-only and the release job is limited to the two existing build refs. |
| SEC-03 | **P2 — local playback disruption** | The exported `RadioPlaybackService` used the deprecated `AcceptedResultBuilder(session)` and explicitly assigned the trusted default session commands to every connecting controller. A local app able to connect as a media controller could control the player, inject a media item/URL, and invoke the destructive custom `Close Radio` command. | **Fixed in this branch.** The callback starts with Media3's controller-aware defaults, so untrusted clients get read-only commands; `Close Radio` is added and executed only for a trusted controller. HUR/Android Auto compatibility still needs a device check. |
| SEC-04 | **P2 — diagnostic privacy** | `CrashReporter` stores up to 8 KB of an uncaught stack trace in `files/last_crash.txt`. With backup enabled and no explicit exclusion, that file could be included in backup/device transfer; traces can contain runtime details or URL strings. | **Fixed in this branch.** The file is excluded from legacy backup, cloud backup, and device transfer. User settings and other app data remain eligible for backup. |
| SEC-05 | **P2 — CI supply chain** | Tracked workflows referenced GitHub Actions by moving major tags (`@v4`, `@v5`). A tag can be retargeted upstream, changing code executed in the runner. GitHub recommends pinning actions to a full commit SHA. | **Fixed in this branch.** Existing action references are pinned to the commit currently resolved by their upstream version tags, with version comments. |
| SEC-06 | **P2 — catalog publishing token exposure** | The catalog build originally held a `contents: write` token while installing Python dependencies and running source-data processing. If a dependency or executed code were compromised, it could overwrite the published catalog branch. | **Source fix merged.** `build-catalog` has read-only contents permission and uploads a publication bundle; a separate minimal `publish-catalog` job gets `contents: write` and publishes only the validated bundle. Regression tests pass; the next scheduled run is the remaining end-to-end publication check. |
| SEC-07 | **P2 — transport integrity / compatibility** | `android:usesCleartextTraffic="true"` permits cleartext HTTP across the app, not just for a specific radio host. A network attacker may be able to alter an HTTP stream or logo response. | **Explicitly accepted for this release.** Some public radio stations only offer HTTP; a global HTTPS-only change could silently break station playback. Keep current compatibility behavior. Revisit only as a separate, measured HTTPS-first project with a station-coverage test, not as part of this closeout. |
| SEC-08 | **P2 — signing identity** | The workflow did not record or compare the official signing-certificate fingerprint against a known expected value. A changed repository secret could produce a correctly signed APK using an unexpected key. | **Guard merged in PR #29; owner reports the expected fingerprint secret has been added.** The secret value is intentionally not visible to this review. The next manual build from `main` must print the persistent certificate fingerprint and confirm the configured value matches before its artifact is used. If it fails or warns that the secret is missing, stop and correct the secret before treating the APK as a release candidate. |

## Changes made in this branch

1. Added Android backup exclusions for `last_crash.txt` while retaining backup/restore of ordinary settings.
2. Restored Media3's per-controller command defaults (read-only for untrusted controllers) and added a small unit-tested policy that gates the destructive media-session custom command at both advertisement and execution.
3. Limited the release workflow's `GITHUB_TOKEN` permission to `contents: read`, guarded the job to the two refs it already supported for push builds, and distinguished persistent-key releases from temporary-key test builds.
4. Pinned the action references in tracked workflows to immutable full commit SHAs.
5. Split catalog generation (read-only token) from publication (write token only in a minimal artifact publisher).
6. Added regression tests for the controller trust policy, backup exclusions, release signing-artifact distinction, immutable action pins, and catalog publisher permissions.

## Secret-search observation

The current default-branch code-search query returned no matches for common private-key headers, GitHub personal-token prefixes, or an API key marker. The plain-text `password` matches were in the signing workflow's documented temporary-key logic and a network-authentication implementation, not a checked-in credential. **This is not proof that no secret ever existed**: it does not cover all Git history, ignored/untracked local files, repository/environment secret values, workflow logs, or GitHub's private secret-scanning alerts. Those need separate checks in repository settings and the Actions/Play Console configuration.

## Further source review — merged URL-boundary hardening (PR #27)

The Android image path loads station-logo URLs from catalog records through `ImageLoader`. A compromised or malicious catalog record could previously use non-HTTP schemes, localhost/private addresses, or automatic cross-host redirects to induce unexpected network requests. This was a credible trust-boundary concern, not a demonstrated exploit.

**Merged in PR #27:** `StationLogoUrlPolicy` restricts logo URLs to HTTP(S), rejects credentials and local/special-use hostnames and IP ranges, checks DNS answers, disables automatic redirects, applies the same checks to each redirect target, caps redirects at five, and blocks HTTPS-to-HTTP downgrade redirects. Unit tests cover malformed schemes, private/local IPv4 and IPv6 ranges, DNS answers containing private addresses, relative redirects, unsafe redirect targets, and downgrade redirects. Existing byte-size and bitmap-dimension limits remain in place.

PR #27 also restricts the manifest's `delta_url` to the app's canonical GitHub Raw URL; missing, malformed, or different values fall back to the hard-coded canonical URL rather than being opened directly.

**Limit:** `HttpURLConnection` resolves a hostname again at connect time after the policy performs DNS checks, so the client cannot guarantee DNS pinning against a rebinding race with this implementation. The changes reduce the URL-boundary exposure but do not constitute a complete network sandbox. Device/runtime testing of representative legitimate logo redirects remains required.

## Finite closeout checklist

The current closeout has three remaining operational checks. Do not start another broad refactor or expand this audit to unrelated Dependabot updates unless a check below fails.

- [x] Merge source hardening PRs #24 and #27; their Android regression tests passed on their respective PR heads.
- [x] Pin Python runtime/test dependency versions in PR #28 and keep shared package versions consistent across manifests.
- [x] Add the persistent APK signing-certificate fingerprint guard in PR #29.
- [x] Configure Dependabot weekly and limit concurrent update PRs to two per ecosystem in PR #37. Existing PRs #30–36 are optional proposals, not changes to `main`; they are intentionally outside the release gate.
- [x] Owner reports protection for `main` and the three required PR CI checks are configured.
- [x] Owner reports adding the repository Actions secret `ANDROID_SIGNING_CERT_SHA256`; secret values cannot be read during this source review.
- [ ] **Manual APK validation:** run `Android Release APK` on `main`. In the log, confirm `Using persistent Android release signing key.`, the expected `Persistent signing certificate SHA-256`, and `Persistent signing certificate fingerprint matches the configured expected value.`. Confirm the artifact is `radio-world-auto-release`, not `radio-world-auto-test-build`. If any check fails, do not install/distribute the artifact; fix only the signing configuration and retry.
- [ ] **Scheduled catalog validation:** on the next six-hour schedule, confirm both `build-catalog` and `Publish validated catalog snapshot` succeed, the `catalog-data` publication is updated, and the manifest, delta, SQLite database, station JSON and debug page are present. Verify the Pages deployment after publication. Do not manually dispatch a catalog build merely to make this check happen early.
- [ ] **Final device smoke test:** install the validated APK on the phone and, where practical, test the head unit/HUR: launch, play/pause, station switching, and updating over the same installed app without uninstalling first. This is a compatibility check, not a request for another source refactor.

## Explicitly accepted limits for this closeout

- Cleartext HTTP remains enabled for compatibility with public stations that do not offer HTTPS. Do not change that globally without coverage data and a separate compatibility plan.
- The logo URL checks reduce unsafe URL/redirect risk, but `HttpURLConnection` may resolve a hostname again at connect time, so DNS rebinding is not fully eliminated.
- Exact Python pins provide repeatable version selection but do not verify package artifact hashes. Hash-locked installs remain a possible future hardening task, not a prerequisite for this bounded verification pass.
- No full penetration test or exhaustive historical secret scan has been performed; a clean source/configuration review is not a guarantee that the application is free of vulnerabilities.

## Stop point

When the manual APK fingerprint check, scheduled catalog publication, and final device smoke test pass, mark this source-level security hardening pass closed. No additional audit PRs are planned by default. Future Dependabot updates are routine maintenance and should be reviewed only when intentionally scheduled; they are not part of this release closeout. If a check fails, fix the specific failure, re-run that check, and then resume this same checklist.
