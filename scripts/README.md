# scripts/

Local automation scripts.

## Key scripts

- `build_and_publish_release.ps1` — build the release APK locally and publish it to GitHub Releases
- `publish_github_release.ps1` — upload an already-built APK with retry logic and release-body generation
- data refresh scripts under this folder update species/base-stat metadata from structured sources

These scripts are the primary release path when GitHub Actions is unavailable or rate/billing limited.
