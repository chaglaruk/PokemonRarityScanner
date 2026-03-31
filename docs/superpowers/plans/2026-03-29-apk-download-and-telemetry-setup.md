## APK download + telemetry setup

### APK path
- `C:\Users\Caglar\Desktop\PokeRarityScanner\app\build\outputs\apk\debug\app-debug.apk`

### Android app telemetry key
- Base URL:
  - `https://caglardinc.com/scan-telemetry/api`
- API key:
  - `pkr_2026_telemetry_7f4c9a2d1e8b5c3f`

### Website config.php
Edit:
- `public_html/scan-telemetry/config.php`

Set:
```php
'api_key' => 'pkr_2026_telemetry_7f4c9a2d1e8b5c3f',
'public_base_url' => 'https://caglardinc.com/scan-telemetry',
```

### Verify export works
Open:
- `https://caglardinc.com/scan-telemetry/api/scan-export.php?api_key=pkr_2026_telemetry_7f4c9a2d1e8b5c3f&limit=5`

Expected:
- HTTP 200
- JSON payload with `ok: true`

If it returns `Invalid api_key`, the live site config still does not match the app build.

### Publish APK on website
Upload:
- `app-debug.apk`

Recommended destination:
- `public_html/downloads/pokerarityscanner.apk`

Recommended link:
```html
<a href="/downloads/pokerarityscanner.apk" download>PokeRarityScanner APK indir</a>
```

If your host needs MIME mapping, use:
- `application/vnd.android.package-archive`

### What web telemetry gives us
- Friends install the same APK.
- Their scan uploads hit the same backend automatically.
- We can read them from:
  - `scan-export.php`
- Feedback buttons send:
  - `wrong_species`
  - `wrong_event`
  - `wrong_costume`
  - `wrong_shiny`

