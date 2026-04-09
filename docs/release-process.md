# Release Process

## Local builds

- Debug APK: `.\gradlew.bat :app:assembleDebug`
- Release APK: `.\gradlew.bat :app:assembleRelease`

Outputs:

- `app/build/outputs/apk/debug/PokeRarityScanner-v<version>-debug.apk`
- `app/build/outputs/apk/release/PokeRarityScanner-v<version>-release.apk`

Version defaults come from `gradle.properties`:

- `pokerarity.versionCode`
- `pokerarity.versionName`

Override per build with environment variables:

- `POKERARITY_VERSION_CODE`
- `POKERARITY_VERSION_NAME`

## Release signing

If no release keystore is configured, local `release` builds fall back to the debug signing key so the APK remains installable for testing.

Provide a real release keystore through `local.properties` or environment variables:

- `releaseStoreFile` / `POKERARITY_RELEASE_STORE_FILE`
- `releaseStorePassword` / `POKERARITY_RELEASE_STORE_PASSWORD`
- `releaseKeyAlias` / `POKERARITY_RELEASE_KEY_ALIAS`
- `releaseKeyPassword` / `POKERARITY_RELEASE_KEY_PASSWORD`

## GitHub Releases

Workflow: `.github/workflows/release-apk.yml`

- Tag push `v1.1.0` builds a release APK
- Uploads the APK as a workflow artifact
- Publishes a GitHub Release for tagged builds

Recommended GitHub secrets:

- `POKERARITY_RELEASE_STORE_BASE64`
- `POKERARITY_RELEASE_STORE_PASSWORD`
- `POKERARITY_RELEASE_KEY_ALIAS`
- `POKERARITY_RELEASE_KEY_PASSWORD`

If these secrets are not set, CI still builds using the fallback signing path.

## Local upload fallback

If GitHub Actions is blocked by billing or quota issues, upload the local APK directly via GitHub API:

- helper script: `scripts/publish_github_release.ps1`
- one-step build + publish: `scripts/build_and_publish_release.ps1`
- guide: `docs/github-release-upload.md`
