# Python dependency policy

## Goals

- Make CI and deployment dependency resolution repeatable.
- Keep shared Python packages on the same tested versions in the root, API runtime/development, and collector environments.
- Receive dependency updates as reviewable pull requests rather than allowing unreviewed versions to enter a build.

## Version pins

The `requirements.txt`, `api/requirements.txt`, `api/requirements-dev.txt`, and `collector/requirements.txt` manifests pin exact versions with `==`. Their current pins were taken from the successful Python 3.12/Linux GitHub Actions dependency resolution on 2026-10-20. `api/requirements.txt` contains runtime dependencies only; API test-only packages live in `api/requirements-dev.txt`. The root manifest remains the root runtime dependency set; the collector has its own test/runtime closure.

Platform markers are retained for `httptools` and `uvloop`, which are not applicable to every operating system/interpreter. When adding or updating a package, run the three required CI suites and keep versions consistent between manifests when they share a dependency.

## Automated updates

Dependabot checks Python packages in all three requirement directories and GitHub Actions weekly. At most two version-update PRs are requested concurrently per ecosystem. Patch/minor updates are grouped where possible; major dependency changes remain separate update proposals. Review each PR in isolation and require all mandatory checks to pass before merging. No dependency update is merged automatically by this policy. Existing proposals can remain open without affecting `main`; they are not a reason to merge several major tool upgrades at once.

## Remaining limitation

Exact version pins make version selection repeatable but do not verify the downloaded artifact's cryptographic hash. Hash-locked requirements (for example, generated with pip-tools and installed with pip hash-checking mode) remain a separate hardening step and must be introduced only after CI can generate and validate platform-appropriate hashes.
