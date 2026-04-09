# GitHub Release Upload Without Actions

Use this when GitHub Actions is blocked by account billing or quota issues.

## Prerequisites

- A local release APK already built:
  - `.\gradlew.bat :app:assembleRelease`
- A GitHub token with release upload permission:
  - Fine-grained token: repository contents `Read and write`
  - Classic token: `repo`

## PowerShell

Set the token in the current terminal session:

```powershell
$env:GITHUB_TOKEN = "YOUR_GITHUB_TOKEN"
```

Dry run:

```powershell
.\scripts\publish_github_release.ps1 -DryRun
```

Upload the newest local release APK to the matching tag release:

```powershell
.\scripts\publish_github_release.ps1
```

Build and publish in one step:

```powershell
.\scripts\build_and_publish_release.ps1
```

Upload a specific APK to a specific tag:

```powershell
.\scripts\publish_github_release.ps1 -Tag v1.1.3 -ApkPath .\app\build\outputs\apk\release\PokeRarityScanner-v1.1.3-release.apk
```

## Behavior

- Finds the newest `PokeRarityScanner-v*-release.apk` if `-ApkPath` is omitted
- Infers the tag from the APK filename if `-Tag` is omitted
- Reads a GitHub token from `GITHUB_TOKEN` or from the local Git credential manager cache
- Creates the release if it does not exist
- Creates the remote tag from the current HEAD if the release does not already exist
- Updates the existing release target commit if the tag already exists
- Replaces an existing APK asset with the same filename
- Publishes a simple changelog from commits since the previous semver tag

## Notes

- This bypasses GitHub Actions entirely
- It does not solve account billing lock for Actions; it avoids the Actions path
