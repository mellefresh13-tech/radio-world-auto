# Radio World Auto — security audit

**Status:** first source-level pass merged; URL-boundary follow-up is isolated in a candidate PR; device/runtime checks and remaining owner-side release settings are still pending.  
**Baseline:** `main` at `a0ff826c98f247dfdf040be8536c5c65ccf97f23` (2026-10-19).  
**Rule:** do not make security changes directly on `main`; preserve playback, catalog ordering, existing layouts, and the tested release behavior.

## Scope and limits

Reviewed the current Android manifest, the exported Media3 playback services, crash-report storage, the release-signing workflow, all tracked GitHub Actions workflows, dependency declarations, and the repository's current indexed code for common private-key/token markers. Reviewed GitHub's reported protection state for `main`.

This is a source/configuration audit, not a claim of complete penetration testing. The review does not expose repository secret values, GitHub secret-scanning alert contents, historical deleted blobs, Play Console settings, the signing certificate actually installed on a device, or production runtime traffic. No production account or user device was attacked. Dynamic verification and device checks remain open.

## Confirmed findings

| ID | Priority | Finding | Disposition |
|---|---|---|---|
| SEC-01 | **P1 — owner action** | GitHub currently reports `main` as unprotected (`protected: false`); required status checks are not enforced by branch protection. A change made by an account with write access can reach the release source without a mandatory PR/check gate. | **Open outside source code.** Enable a ruleset/branch protection rule for `main`; disallow force-push and deletion. Preserve the separate `catalog-data` publication workflow, which intentionally force-pushes generated snapshots. |
| SEC-02 | **P1 — release integrity** | The APK workflow fell back to an ephemeral signing key but uploaded the result under the same `radio-world-auto-release` artifact name. The same successful workflow status therefore did not distinguish a persistent-signature build from an APK that cannot update an install signed with another key. | **Fixed in this branch.** Artifact names now reflect the signing mode; a partially configured persistent keystore fails explicitly; temporary-key passwords are random; the build token is read-only and the release job is limited to the two existing build refs. |
| SEC-03 | **P2 — local playback disruption** | The exported `RadioPlaybackService` used the deprecated `AcceptedResultBuilder(session)` and explicitly assigned the trusted default session commands to every connecting controller. A local app able to connect as a media controller could control the player, inject a media item/URL, and invoke the destructive custom `Close Radio` command. | **Fixed in this branch.** The callback starts with Media3's controller-aware defaults, so untrusted clients get read-only commands; `Close Radio` is added and executed only for a trusted controller. HUR/Android Auto compatibility still needs a device check. |
| SEC-04 | **P2 — diagnostic privacy** | `CrashReporter` stores up to 8 KB of an uncaught stack trace in `files/last_crash.txt`. With backup enabled and no explicit exclusion, that file could be included in backup/device transfer; traces can contain runtime details or URL strings. | **Fixed in this branch.** The file is excluded from legacy backup, cloud backup, and device transfer. User settings and other app data remain eligible for backup. |
| SEC-05 | **P2 — CI supply chain** | Tracked workflows referenced GitHub Actions by moving major tags (`@v4`, `@v5`). A tag can be retargeted upstream, changing code executed in the runner. GitHub recommends pinning actions to a full commit SHA. | **Fixed in this branch.** Existing action references are pinned to the commit currently resolved by their upstream version tags, with version comments. |
| SEC-06 | **P2 — catalog publishing token exposure** | The catalog build originally held a `contents: write` token while installing Python dependencies and running source-data processing. If a dependency or executed code were compromised, it could overwrite the published catalog branch. | **Fixed in this branch.** `build-catalog` has read-only contents permission and uploads a publication bundle; a separate minimal `publish-catalog` job gets `contents: write`, downloads the bundle, and publishes the orphan snapshot without installing packages or executing collector/importer code. A regression test enforces this split. CI and the catalog pipeline must confirm it works end-to-end. |
| SEC-07 | **P2 — transport integrity / compatibility** | `android:usesCleartextTraffic="true"` permits cleartext HTTP across the app, not just for a specific radio host. A network attacker may be able to alter an HTTP stream or logo response. | **Open product/compatibility decision.** Some public radio stations only offer HTTP, so globally disabling cleartext could break station playback. Keep the compatibility behavior for now; separately assess HTTPS-first policy and safe redirect handling without silently dropping working stations. |
| SEC-08 | **P2 — signing identity** | The workflow did not record or compare the official signing-certificate fingerprint against a known expected value. A changed repository secret could produce a correctly signed APK using an unexpected key. | **Open release checklist item.** Record the expected SHA-256 signing-certificate fingerprint from the trusted APK and verify it for persistent-key builds before public release. Do not put the private keystore or passwords in the repository. |

## Changes made in this branch

1. Added Android backup exclusions for `last_crash.txt` while retaining backup/restore of ordinary settings.
2. Restored Media3's per-controller command defaults (read-only for untrusted controllers) and added a small unit-tested policy that gates the destructive media-session custom command at both advertisement and execution.
3. Limited the release workflow's `GITHUB_TOKEN` permission to `contents: read`, guarded the job to the two refs it already supported for push builds, and distinguished persistent-key releases from temporary-key test builds.
4. Pinned the action references in tracked workflows to immutable full commit SHAs.
5. Split catalog generation (read-only token) from publication (write token only in a minimal artifact publisher).
6. Added regression tests for the controller trust policy, backup exclusions, release signing-artifact distinction, immutable action pins, and catalog publisher permissions.

## Secret-search observation

The current default-branch code-search query returned no matches for common private-key headers, GitHub personal-token prefixes, or an API key marker. The plain-text `password` matches were in the signing workflow's documented temporary-key logic and a network-authentication implementation, not a checked-in credential. **This is not proof that no secret ever existed**: it does not cover all Git history, ignored/untracked local files, repository/environment secret values, workflow logs, or GitHub's private secret-scanning alerts. Those need separate checks in repository settings and the Actions/Play Console configuration.

## Further source review — open URL-boundary risk

The Android image path loads station-logo URLs from catalog records through `ImageLoader`. A compromised or malicious catalog record could previously use non-HTTP schemes, localhost/private addresses, or automatic cross-host redirects to induce unexpected network requests. This was a credible trust-boundary concern, not a demonstrated exploit.

**Candidate fix in PR:** `StationLogoUrlPolicy` now restricts logo URLs to HTTP(S), rejects credentials and local/special-use hostnames and IP ranges, checks every DNS answer, disables automatic redirects, applies the same checks to each redirect target, caps redirects at five, and blocks HTTPS-to-HTTP downgrade redirects. Unit tests cover malformed schemes, private/local IPv4 and IPv6 ranges, DNS answers containing private addresses, relative redirects, unsafe redirect targets, and downgrade redirects. Existing byte-size and bitmap-dimension limits remain in place.

The manifest's `delta_url` is also now accepted only when it exactly matches the app's canonical GitHub Raw URL; missing, malformed, or different values fall back to the hard-coded canonical URL rather than being opened directly.

**Limit:** `HttpURLConnection` resolves a hostname again at connect time after the policy performs DNS checks, so the client cannot guarantee DNS pinning against a rebinding race with this implementation. The changes reduce the URL-boundary exposure but do not constitute a complete network sandbox. Device/runtime testing of representative legitimate logo redirects remains required.

## Next audit passes

- [ ] Owner enables protection for `main` and reviews collaborator/write access.
- [ ] Open PR CI is green; inspect the final diff and exact action SHA pins.
- [ ] Confirm the actual release signing mode and record the trusted certificate fingerprint.
- [ ] Wait for workflow CI and the next scheduled/manual catalog snapshot to verify the least-privilege publication split end-to-end; verify `catalog-data` still has the expected files and delta/manifest behavior remains compatible.
- [ ] Review station-stream handling and app-wide cleartext HTTP compatibility; logo URL validation and manual redirect checks are implemented in the candidate PR, pending CI and device/runtime validation.
- [ ] Inspect dependency update policy and transitive dependency pinning for Python workflows.
- [ ] After installing the candidate APK on the phone and testing HUR, verify playback, pause/stop, station switching, custom media buttons, backup/restore behavior, and update-over-install using the same signing certificate.
- [ ] Before a public release, run a fresh secret scan including the repository history and confirm no secret value is present in artifacts or logs.

## Exit criteria

Do not mark the audit complete until all P1 items are resolved, the remaining P2 items have either a tested fix or an explicit documented acceptance decision, automated tests pass on the candidate branch, and real-device checks are recorded. A clean source review alone is not a guarantee of immunity to compromise.
