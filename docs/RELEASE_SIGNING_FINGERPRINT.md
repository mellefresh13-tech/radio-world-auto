# Android release signing fingerprint

The Android release workflow can compare its persistent signing certificate against a trusted SHA-256 fingerprint before signing an APK.

## How to establish the expected value

1. Start from an APK that is already known to be trusted for this app (for example, the exact release APK installed from your trusted release process). Do not take the expected value from an unverified candidate build.
2. Obtain its signing-certificate SHA-256 fingerprint with Android SDK `apksigner verify --print-certs` against that trusted APK. If using a trusted keystore as the source of truth, you can also export the public certificate and calculate its SHA-256 digest.
3. Compare the fingerprint from the trusted APK/keystore with the value printed by the GitHub Actions step **Verify persistent signing certificate fingerprint**.
4. Only after confirming they match, add a repository Actions secret named `ANDROID_SIGNING_CERT_SHA256` with the 64 hexadecimal SHA-256 characters. Colons are accepted but not required.

## Result

- If the secret is not configured, the workflow warns and continues, so current manual builds are not unexpectedly blocked. That means fingerprint verification is not yet enforced.
- If configured, the workflow compares the signing certificate derived from the persistent keystore to the expected fingerprint and fails the build on invalid formatting or a mismatch.
- Temporary test-signing builds skip this check because their certificate is intentionally ephemeral.
- The fingerprint is public certificate metadata, not the keystore or signing password. Never put the keystore or passwords in the repository.

This check only helps when the expected value is obtained from an independent trusted source. Copying the value from the same unverified build would defeat the purpose.
