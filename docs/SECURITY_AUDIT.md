# Radio World Auto — security audit

**Status:** first source-level pass complete; targeted fixes are on `security/security-audit-2026-10-20`; automated PR validation and owner-side GitHub settings remain pending.  
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
| SEC-03 | **P2 — local playback disruption** | The exported `RadioPlaybackService` advertised the destructive custom `Close Radio` command to every connecting media controller and executed it without checking the controller's trust status. A local app able to connect as a media controller could stop the service and clear the media item. | **Fixed in this branch.** The custom command is advertised and executed only for a Media3-trusted controller. The normal media command set is otherwise unchanged. HUR/Android Auto compatibility still needs a device check. |
| SEC-04 | **P2 — diagnostic privacy** | `CrashReporter` stores up to 8 KB of an uncaught stack trace in `files/last_crash.txt`. With backup enabled and no explicit exclusion, that file could be included in backup/device transfer; traces can contain runtime details or URL strings. | **Fixed in this branch.** The file is excluded from legacy backup, cloud backup, and device transfer. User settings and other app data remain eligible for backup. |
| SEC-05 | **P2 — CI supply chain** | Tracked workflows referenced GitHub Actions by moving major tags (`@v4`, `@v5`). A tag can be retargeted upstream, changing code executed in the runner. GitHub recommends pinning actions to a full commit SHA. | **Fixed in this branch.** Existing action references are pinned to the commit currently resolved by their upstream version tags, with version comments. |
| SEC-06 | **P2 — catalog publishing token exposure** | `.github/workflows/catalog.yml` grants `contents: write` to the same job that installs Python dependencies and runs the collector, importers, and data-processing code. If a dependency or executed code were compromised, the job's token could be used to overwrite the published catalog branch. | **Open; design change queued.** Split the build into a read-only job and put the write token only in a minimal publisher job that handles already-built artifacts. This is a larger workflow change and needs dedicated pipeline validation. |
| SEC-07 | **P2 — transport integrity / compatibility** | `android:usesCleartextTraffic="true"` permits cleartext HTTP across the app, not just for a specific radio host. A network attacker may be able to alter an HTTP stream or logo response. | **Open product/compatibility decision.** Some public radio stations only offer HTTP, so globally disabling cleartext could break station playback. Keep the compatibility behavior for now; separately assess HTTPS-first policy and safe redirect handling without silently dropping working stations. |
| SEC-08 | **P2 — signing identity** | The workflow did not record or compare the official signing-certificate fingerprint against a known expected value. A changed repository secret could produce a correctly signed APK using an unexpected key. | **Open release checklist item.** Record the expected SHA-256 signing-certificate fingerprint from the trusted APK and verify it for persistent-key builds before public release. Do not put the private keystore or passwords in the repository. |

## Changes made in this branch

1. Added Android backup exclusions for `last_crash.txt` while retaining backup/restore of ordinary settings.
2. Added a small unit-tested policy for the destructive media-session custom command and applied it on both command advertisement and execution.
3. Limited the release workflow's `GITHUB_TOKEN` permission to `contents: read`, guarded the job to the two refs it already supported for push builds, and distinguished persistent-key releases from temporary-key test builds.
4. Pinned the action references in tracked workflows to immutable full commit SHAs.
5. Added regression tests for the controller trust policy.

## Secret-search observation

The current default-branch code-search query returned no matches for common private-key headers, GitHub personal-token prefixes, or an API key marker. The plain-text `password` matches were in the signing workflow's documented temporary-key logic and a network-authentication implementation, not a checked-in credential. **This is not proof that no secret ever existed**: it does not cover all Git history, ignored/untracked local files, repository/environment secret values, workflow logs, or GitHub's private secret-scanning alerts. Those need separate checks in repository settings and the Actions/Play Console configuration.

## Next audit passes

- [ ] Owner enables protection for `main` and reviews collaborator/write access.
- [ ] Open PR CI is green; inspect the final diff and exact action SHA pins.
- [ ] Confirm the actual release signing mode and record the trusted certificate fingerprint.
- [ ] Split catalog generation and publication into separate least-privilege jobs, with tests proving the build job cannot write repository contents.
- [ ] Review station-stream and logo URL handling, including cleartext/redirect behavior, without breaking valid HTTP radio sources.
- [ ] Inspect dependency update policy and transitive dependency pinning for Python workflows.
- [ ] After installing the candidate APK on the phone and testing HUR, verify playback, pause/stop, station switching, custom media buttons, backup/restore behavior, and update-over-install using the same signing certificate.
- [ ] Before a public release, run a fresh secret scan including the repository history and confirm no secret value is present in artifacts or logs.

## Exit criteria

Do not mark the audit complete until all P1 items are resolved, the remaining P2 items have either a tested fix or an explicit documented acceptance decision, automated tests pass on the candidate branch, and real-device checks are recorded. A clean source review alone is not a guarantee of immunity to compromise.
