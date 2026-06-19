# IV Accuracy Benchmark

Status: verified on 2026-06-19.

## Fixtures

| Case | Inputs | Expected |
| --- | --- | --- |
| Mewtwo perfect level 40 | Base 300/182/214, IV 15/15/15, level 40 | CP 4178, HP 180 |
| Bulbasaur level 20 | Base 118/111/128, IV 12/14/15, level 20 | CP 620, HP 85 |
| Exact solve | Bulbasaur CP 620, HP 85, level 20, exact appraisal 12/14/15 | One exact candidate |
| CP-only | Bulbasaur CP 620 only | Range mode, not exact |
| Stardust bucket | Regular stardust 2500 | Candidate levels only 19.0 through 20.5 |
| Conflict | Bulbasaur CP 620, HP 999, level 20 | Insufficient/conflict, no candidate |

## Limitations

- Slice 1 uses regular stardust buckets only.
- Form-specific base stat variants depend on future adapter work.
- Appraisal OCR and level arc evidence are not wired yet.
- PvP rank/stat product is deferred.

## Actual Results

| Check | Result |
| --- | --- |
| Mewtwo level 40 perfect | CP 4178, HP 180 matched. |
| Mewtwo level 50 perfect | CP 4724 matched. |
| Bulbasaur level 20 12/14/15 | CP 620, HP 85 matched. |
| Exact solve | Returned one `12/14/15`, level 20.0 candidate. |
| CP-only solve | Returned `RANGE`, not `EXACT`. |
| Stardust bucket | Returned only levels inside 19.0 through 20.5. |
| Conflict | Returned `INSUFFICIENT` with no candidates. |

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Iv*" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```
