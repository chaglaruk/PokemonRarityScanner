# Recognition recovery: implementation and measured results

Status: debug candidate; real-device bitmap replay verified. Fresh live Pokemon GO capture, overlay delivery and independent holdout acceptance remain unverified. No release build or publication was performed.

## What changed

The scanner now identifies species from independent, visible family evidence. It reads one local ML Kit document, follows the HP, type, candy and POWER UP labels, and accepts a species only when the remaining family profiles identify one canonical species. The editable title does not choose between plausible species. All runtime recognition stays on the device.

The original implementation was examined at `91d7555fc18615f8d9eff7355032ce10bf0f1d4a`. Its debug APK was preserved before implementation. The recovery work is on `fix/recognition-recovery`.

## Root causes reproduced

1. **An unavailable arc detector vetoed otherwise correct results.** OCR always supplied a missing arc, while profile compatibility and both acceptance gates required it. On the S25, the original scanner accepted none of the 15 confirmed development images.
2. **Crop geometry did not follow the actual detail screen.** Several HP and candy crops covered neighboring fields. Scrolling moved fields far outside the historical offsets. A green button could also resemble an HP bar; visible HP text now confirms the bar before it constrains title geometry.
3. **The edit pencil corrupted the title.** ML Kit sometimes returned a trailing slash, such as `Pikipek /`. Cleanup is confined to the spatial title label. An exact title is still editable and cannot establish species identity independently.
4. **Profile checks were incomplete.** They used current rather than maximum HP in places, required the arc, and did not jointly establish CP and HP at the same level and stamina IV. The old species table also missed regional profiles needed by the development screenshots.
5. **Fusion and confidence could combine incompatible observations.** Fast-pass uncertainty could poison an otherwise readable frame; detailed evidence could not properly upgrade identity. Conversely, scoring fields from discarded frames could make a selected incomplete observation appear complete. The new path keeps one entire observation and checks other frames for explicit contradictions.
6. **Numeric edge cases were unsafe or unnecessarily rejected.** CP below 100 is valid. Fainted and damaged Pokemon retain their maximum HP. Strict anchored HP parsing now preserves this distinction without repairing malformed digits into identity evidence.

Review also reproduced three mathematical false-accept cases in an early resolver experiment: a boosted Skwovet could become Greedent when level 51 was omitted; rounded half-level multipliers could produce the same error; and using boosted rather than underlying level for power-up cost could turn Pikipek into Trumbeak. Regression tests cover these cases.

## Architecture and safety

The active path is:

`consented capture -> bounded bitmap -> one ML Kit layout -> spatial labels -> independent family profiles -> coherent frame selection -> consistency/confidence -> existing variant/result pipeline`

- CP and current/maximum HP come from explicit labels. Malformed or contradictory numeric labels are not repaired into hard evidence.
- Candy identifies a family. Type text must occupy the measurement/type row; unrelated move text and incomplete dual-type labels are excluded.
- Power-up stardust comes only from the numeric column aligned with POWER UP. Inventory stardust does not constrain level.
- Profiles retain distinct regional and temporary-form stats/types. The asset contains 1,216 profiles covering the 1,011 canonical names in this repository.
- Level multipliers cover all 101 half-levels from 1 through 51. Whole levels retain source float32 precision; half levels use the mean of squared adjacent multipliers. CP and maximum HP must share a feasible level and stamina IV.
- Normal, lucky, purified and shadow cost possibilities are retained conservatively, including the possible Best Buddy level offset. An absent arc is not a contradiction.
- A family member with a unique, independently observed type can be identified when numeric fields have scrolled away. Missing CP and HP remain missing; they are never synthesized for display.
- A detailed result may replace the selected observation only as a whole, from the same source image, with stronger coherent evidence. Candy/type conflicts remain visible even when another frame's numeric reading is unusable.
- Confidence scoring uses only the selected observation's frame. Other frames supply explicit conflict checks, not borrowed fields or invented agreement.
- Legacy nickname refinement and CP estimation cannot overwrite the new identity or its observed CP. The obsolete multi-pass OCR implementation was removed.

The bundled numeric facts are reproducible from a [pinned public Game Master snapshot](https://raw.githubusercontent.com/PokeMiners/game_masters/8e227be44f288d34463e23bf04e9b564d3c16f79/latest/latest.json). Its source SHA-256 is `5c947ac64d1de8859bea1b3bf044609d74b6e8429d53f8cb1aa30a72a46dce84`. Regeneration matched the bundled asset byte for byte. This development download adds no runtime network dependency and includes no images, account data or game code.

Existing MediaProjection consent and overlay controls remain in place. New diagnostics do not include raw OCR. The ephemeral recognition observation is excluded from ordinary Gson persistence. Existing telemetry remains opt-in and excludes raw OCR, screenshot paths and diagnostic directories; the recognition change adds no upload behavior.

## Controlled experiments

- **Original 900-pixel vs native 1080-pixel input:** native input increased the original title-derived species correctness from 10/15 to 12/15 on the S25, but both still accepted 0/15. Resolution alone did not repair the acceptance architecture.
- **Whole OCR blocks vs individual lines:** line granularity alone did not improve species correctness on the development experiment. It was useful for spatial association, not sufficient as a standalone fix.
- **HP-anchored crops at 0.5x, 1x and 1.5x:** the 13 visible HP labels were readable at all three scales on the S25. The other two HP labels were actually offscreen. Title correctness did not increase monotonically with upscaling; the 0.5x experiment skipped one crop below ML Kit's minimum input size.
- **Numeric compatibility alone:** rejected as species authority. Nicknames can name another Pokemon, family members can overlap in CP/HP, and Slowbro/Slowking share relevant profiles. Independent family/type/cost evidence must separate candidates.
- **Alternative OCR engines or a new visual model:** researched as options, but not implemented or represented as tested. One spatial ML Kit pass plus corrected evidence semantics solved the reproduced readable detail cases. The existing visual classifier does not independently establish global species identity.

The default runtime width remains 900 because the recovery's confirmed-label acceptance was identical at 900 and 1080. Native width remains an explicit experiment, rather than an unmeasured production default. [ML Kit's input guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android#input-image-guidelines) also recommends sufficient character resolution and avoiding unnecessary image area.

## Measured recognition results

Physical device: Samsung Galaxy S25, 1080 x 2340. The connected device reports Android 17 and a 4 KB page size. These measurements replay genuine saved screenshots through the on-device OCR, resolver, consistency, gated visual/variant and confidence components. They do **not** measure MediaProjection capture, a real temporal burst, overlay delivery, rarity scoring or database save latency.

The strongest corpus contains 15 user-confirmed development screenshots: five species at three scroll positions. These are correlated development cases, not an independent holdout. Image hashes were verified. The older corpus has 16 historical declared labels and 28 unknown labels; these are reported separately.

| Corpus and configuration | Correct species | Accepted correct | Accepted wrong | Uncertain/rejected labeled cases | Median replay |
|---|---:|---:|---:|---:|---:|
| Original, confirmed development, width 900 | 10/15 | 0/15 | 0 | 15/15 | 1,745 ms |
| Original, confirmed development, native 1080 | 12/15 | 0/15 | 0 | 15/15 | 1,514 ms |
| Recovery, confirmed development, width 900 | 14/15 | 14/15 | 0 | 1/15 | 448 ms |
| Recovery, confirmed development, native 1080 | 14/15 | 14/15 | 0 | 1/15 | 460 ms |
| Recovery, historical labels, width 900 | 16/16 | 16/16 | 0 | 0/16 | 505 ms |
| Recovery, historical labels, native 1080 | 16/16 | 16/16 | 0 | 0/16 | 523 ms |

Recovery development coverage is 93.3%, with one of the 14 results accepted with limited supporting evidence. Thirteen receive full acceptance. Accepted-wrong counts include both acceptance categories. Zero observed errors in this small corpus is not proof of a zero error rate in normal use.

The 900-pixel development replay p95 changed from 2,859 ms to 1,867 ms; its largest sampled PSS changed from 388,082 KiB to 326,237 KiB. PSS samples are not continuous peak-memory measurements. The first accepted visual pass includes cold initialization; native recovery p95 was 550 ms. No OCR, classifier, visual or phase-two processing errors were observed in the completed 118-case comparison.

Of the 28 unknown historical images, 24 were accepted at width 900 and 22 at native width. Their correctness is **unscored**, not assumed. They are excluded from accuracy, accepted-wrong and labeled coverage calculations.

These recognition measurements precede the final native dependency compatibility update; the final replay is recorded separately in the local evidence inventory below.

## Remaining failure and coverage boundaries

The remaining confirmed rejection is fully scrolled Farfetch'd. Its title, CP and HP are absent; the visible Fighting type and Farfetch'd candy also admit Sirfetch'd. Rejection is appropriate until another visible discriminator is available. Scrolling back to the HP/title region supplies that evidence without asking the user to identify the species manually.

The historical approximately 730-image corpus and a usable independent holdout were not found in the checked workspace locations. Their absence did not prevent the real-device experiments above. The current evidence covers 13 distinct labeled species across the two labeled sets, and cannot establish broad species/form/language/lighting coverage. Appraisal, collection-grid, encounter, highly obstructed screens and exact variant/IV accuracy are not established by these results.

Poke Genie and Calcy IV are practical automatic-recognition UX benchmarks. Public descriptions support screenshot scanning and convenient local workflows; they do not supply an independently comparable accuracy dataset. This candidate makes substantial measured progress on readable detail screens, but no parity or production-readiness claim is made. Sources: [Poke Genie listing](https://play.google.com/store/apps/details?id=com.cjin.pokegenie.standard), [Calcy IV listing](https://play.google.com/store/apps/details?id=tesmath.calcy).

## Native compatibility

Launching the debug app exposed a real Android compatibility warning. ELF inspection found OpenCV, its C++ runtime and SQLCipher with 4 KB load alignment. ML Kit and AndroidX graphics were already 16 KB aligned, despite additional generic entries in the Android warning.

Only `org.opencv:opencv` 4.10.0 -> 4.12.0 and `net.zetetic:sqlcipher-android` 4.5.4 -> 4.6.1 were updated. Room, encryption passphrase handling and schema settings were preserved. All ten bundled arm64/x86_64 libraries subsequently passed ELF load alignment, APK ZIP alignment and RELRO-presence checks. This is not a 16 KB device runtime test: the connected phone uses 4 KB pages.

An isolated synthetic encrypted database was created using 4.5.4, then reopened using 4.6.1. Its existing row survived; a new row survived a further reopen. The test never opens the app's database, reads its passphrase or uses destructive recovery. Official basis: [OpenCV maintainer discussion](https://github.com/opencv/opencv/issues/26724), [Zetetic announcement](https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/), [Android alignment guidance](https://developer.android.com/guide/practices/page-sizes).

## Verification and reproducibility

Completed: original 654-test baseline; recovery 710-test suite; 14 Python generator tests; debug application and test APK builds; physical S25 bitmap comparisons; synthetic encrypted database compatibility; generated-asset reproduction; native alignment audit. A final added incomplete-type regression and the final dependency build/replay are tracked in local evidence before closure.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
python -m unittest discover -s scripts -p test_generate_recognition_profiles.py -v
python scripts/generate_recognition_profiles.py --download --output build/recognition_profiles_reproduced.json
```

`RecognitionRecoveryBenchmarkTest` accepts `subset=pr06|legacy|all`, `policies=baseline,native`, a safe JSON report name and a source revision/fingerprint. Here `baseline` names the 900-pixel input policy, not an automatic checkout of the old implementation. The report identifies its source and explicitly marks capture, temporal-burst and holdout verification false. The shared frame-selection helper has separate regression coverage for transitions, missing fields, conflicting families and coherent detailed-frame replacement.

Ignored local evidence includes `baseline_pr06_physical.json`, `recovery_all_physical.json`, `recognition_candidate_source.json`, `native_alignment_before.json`, `native_alignment_after.json`, `native_database_compat_prepare.json` and `native_database_compat_verify.json`, all under `build/`. Raw experiment text, screenshots, APKs and device artifacts remain outside version control.

The debug app is installed. The remaining live acceptance requires the user's explicit Android overlay/capture permissions and a normal Pokemon GO detail screen. Recognition correctness, result delivery and fresh holdout coverage must be reported separately; the bitmap replay does not establish those remaining claims.
