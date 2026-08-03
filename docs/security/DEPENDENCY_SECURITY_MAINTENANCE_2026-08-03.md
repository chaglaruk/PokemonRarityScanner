# Dependency security maintenance evidence — 3 August 2026

## Authority and scope

This is dated evidence for the bounded M2-C dependency compatibility matrix and M2-D local-script triage. Live GitHub and live repository files remain authoritative.

- Repository: `chaglaruk/PokemonRarityScanner`
- Task-start `origin/main`: `3137e4014ab1c408390a7bc99f6cb10c16240e61`
- Task-start open pull requests: 0
- Task-start authoritative-plan blob: `2a6bb08201e41e2a026057f6628c46f8769870e5`
- Task-start security-evidence blob: `7cf74d8960d8825d29339d448ae48a8252c7b7f1`
- Raw JSON, SARIF, readable scan output and dependency graphs were retained only in an external temporary directory. No raw scan output, cache, absolute local path, account name, token or machine identifier is committed.

No release build, signing operation, APK installation, ADB/device operation, MobSF scan, alert dismissal or repository-security-setting change was performed.

## Toolchain observed

| Tool | Version |
|---|---|
| Java used by the shell | Microsoft OpenJDK 11.0.29 |
| Java used by the Gradle daemon | Eclipse Adoptium 17.0.11+9 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin Gradle plugin / KAPT | 1.9.24 |
| Compose compiler | 1.5.14 |
| Hilt | 2.54 |
| Robolectric | 4.14.1 |
| Snyk CLI | 1.1306.2 |
| GitHub CLI | 2.92.0 |

The installed Snyk CLI help was inspected before selecting flags. Dependency scanning used the documented Gradle `--file` and `--all-sub-projects` options. This Snyk Code CLI exposes no exclusion flag, so Code scans were rooted explicitly at `scripts`, `.github` and `app/src`. That root selection excluded `.agent-references`, repository build outputs, Gradle caches and generated build sources without adding or changing `.snyk`.

## Point-in-time security baseline

### Snyk dependency scan

Command shape: `snyk test --file=build.gradle.kts --all-sub-projects`, with JSON and SARIF output written externally.

- Projects tested: 2
- Resolved application dependencies: 358
- Unique findings: 47
- Vulnerable paths: 250
- Unique severity: 1 critical, 24 high, 21 medium, 1 low
- Affected families: Protobuf, Commons IO, Netty, Bouncy Castle and Kotlin stdlib

The nonzero Snyk exit was caused by reported findings; report generation completed.

### Dependabot

The authenticated GitHub API export recorded 53 open alerts: 1 critical, 21 high, 29 medium and 2 low. All were transitive, all were associated by GitHub with `settings.gradle.kts`, and GitHub returned no dependency scope. The earlier evidence-file export recorded 52: 1 critical, 20 high, 29 medium and 2 low. The added alert was high-severity Netty HTTP/2 advisory `GHSA-93wv-jw9v-4972`, created on 3 August 2026, with patched version `4.1.136.Final`. Both counts are dated snapshots.

### Snyk Code

| Scanned root | Findings |
|---|---:|
| `scripts` | 47 |
| `.github` | 1 |
| `app/src` | 0 |
| **Total** | **48** |

All 48 were note-level `python/PT` reports. JSON, SARIF and readable summaries were generated externally. In CLI 1.1306.2, the Code JSON output is SARIF-shaped.

## Dependency-path separation

Baseline graphs and `dependencyInsight` reports were generated for debug runtime, release runtime, debug unit-test runtime, debug Android-test runtime, buildscript/plugin/UTP tooling, KAPT and Kotlin compiler tooling. Requested insight selectors covered `io.netty`, `com.google.protobuf:protobuf-java`, `org.bouncycastle`, `commons-io`, `org.apache.commons:commons-compress` and `org.jetbrains.kotlin:kotlin-stdlib`. The repository's signing guard intentionally prevents ordinary release-variant task selection. An external Gradle init script registered a configuration-only report task for `releaseRuntimeClasspath`; it did not execute a release build or access signing credentials.

| Exposure class | Relevant resolved paths |
|---|---|
| Debug APK runtime | No Netty, Protobuf, Bouncy Castle, Commons IO or Commons Compress path; Kotlin stdlib 2.0.21 |
| Release APK runtime graph | No Netty, Protobuf, Bouncy Castle, Commons IO or Commons Compress path; Kotlin stdlib 2.0.21 |
| Android-test runtime | No Netty, Protobuf, Bouncy Castle, Commons IO or Commons Compress path; Kotlin stdlib 2.0.21 |
| JVM unit-test runtime | Robolectric 4.14.1 to `bcprov-jdk18on` 1.78.1; Kotlin stdlib 2.0.21 |
| Buildscript/plugin/UTP | AGP 8.7.3 / tools 31.7.3 / UTP to Netty 4.1.93.Final, Protobuf 3.22.3, Commons IO 2.13.0, Commons Compress 1.21, Bouncy Castle 1.77 and Kotlin stdlib 1.9.23 |
| KAPT/compiler | Room compiler 2.6.1 and Hilt compiler 2.54 on KAPT; Kotlin compiler classpath 1.9.24 |

The affected Netty, Protobuf, Commons and AGP Bouncy Castle paths are build/test tooling paths, not APK runtime paths. Robolectric's Bouncy Castle path is JVM-test-only. That separation does not prove build/test tooling is risk-free.

## Controlled compatibility matrix

Trials ran in isolated, reversible worktrees. Candidates were selected from official Android Gradle Plugin and Compose/Kotlin compatibility documentation plus resolved dependency evidence. The source set was the official [AGP 8.13 release and compatibility note](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [AGP compatibility overview](https://developer.android.com/build/releases/about-agp), [AGP 9 release note](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [Compose compiler/Kotlin compatibility map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin), [Robolectric getting-started guidance](https://robolectric.org/getting-started/) and the published [Robolectric 4.16.1 POM](https://repo1.maven.org/maven2/org/robolectric/robolectric/4.16.1/robolectric-4.16.1.pom). AGP 9 was not attempted because it requires Gradle 9.1, built-in Kotlin migration and removal of legacy variant APIs used by this repository. AGP 8.8.2 was not repeated because existing resolved evidence showed it retained the affected Protobuf version and could not materially clear the target families.

| Trial | Exact change | Compatibility gate | Security result | Status |
|---|---|---|---|---|
| AGP / Gradle | AGP 8.7.3 to 8.13.2; Gradle 8.9 to 8.13 | Graphs and debug compilation/assembly succeeded. The full JVM run had one failure in `Candidate2026S25ManifestTest`; the untouched baseline worktree reproduced the same CRLF-checkout failure, so it was not an AGP regression. Validation elapsed 8m08s; recorded graph/scan/validation phases totaled 11m59.2s. | Before: Netty 4.1.93.Final, Protobuf 3.22.3, Commons IO 2.13.0, Commons Compress 1.21 and Bouncy Castle 1.77. After: Netty 4.1.110.Final, Protobuf 3.25.5, Commons IO 2.16.1, Commons Compress 1.21 and Bouncy Castle 1.79. Snyk reported 394 dependencies, 46 unique findings and 329 paths: 1 critical, 24 high, 20 medium, 1 low. Commons IO cleared; Netty, Protobuf, Bouncy Castle, Kotlin and Commons Compress exposure remained. New critical/high unique findings: 0. | **FAIL** — insufficient remediation |
| Robolectric / Bouncy Castle | Robolectric 4.14.1 to 4.16.1 | All 654 JVM tests passed in 3m44s; total observed trial command elapsed 5m21.1s. | Before: JVM-test Bouncy Castle 1.78.1 and build-tool Bouncy Castle 1.77. After: JVM-test Bouncy Castle 1.81 while build-tool Bouncy Castle stayed 1.77. Version 1.81 remains below the current 1.84 patched threshold. Snyk reported 359 dependencies, 46 unique findings and 255 paths: 1 critical, 24 high, 20 medium, 1 low. Non-target Netty, Protobuf, Commons and Kotlin families remained at baseline. New critical/high unique findings: 0. | **FAIL** — family remains affected |
| Kotlin / Compose / Hilt | Kotlin/KAPT 1.9.24 to 1.9.25; Compose compiler 1.5.14 to 1.5.15; Hilt unchanged at 2.54 | Compile, KAPT/Hilt integration, all 654 JVM tests and debug assembly passed in 8m32s; total observed trial command elapsed 10m58.5s. | Before: compiler stdlib 1.9.24, runtime/KAPT 2.0.21 and buildscript 1.9.23. After: compiler stdlib 1.9.25, while runtime/KAPT stayed 2.0.21 and buildscript stayed 1.9.23. Snyk was unchanged at 358 dependencies, 47 unique findings and 250 paths. No Kotlin alert cleared; non-Kotlin families remained. New critical/high unique findings: 0. | **FAIL** — no security delta |
| Combined integration | Not run | Only independently successful remediation families were eligible. | No independent trial passed its security gate. | **BLOCKED** |

No trial introduced a new critical/high unique finding, but none delivered a safe and complete target-family remediation. The original declarations were therefore retained. No `resolutionStrategy.force`, transitive direct override, repository addition, baseline regeneration or scanner suppression was used.

All Android-gated trials emitted the existing SDK XML tooling-version warning, and Gradle reported features deprecated for Gradle 9. These warnings were recorded; no candidate-specific compatibility warning established a safe alert-family remediation.

Rollback for each isolated candidate was a path-scoped restore of its version declaration (`git restore -- build.gradle.kts gradle/wrapper/gradle-wrapper.properties` for AGP/Gradle, `git restore -- app/build.gradle.kts` for Robolectric, and `git restore -- build.gradle.kts app/build.gradle.kts` for Kotlin/Compose). No rollback command was needed in the final branch because no candidate was promoted.

## Classification of all 48 scoped Snyk Code findings

### Decision method

Every path value below originates from an explicit local CLI argument. `P1` means the process runs with the invoking local user's privileges and exploitation would require a lower-trust actor to control that deliberately selected argument or its referenced metadata while a more-privileged victim runs the maintenance CLI. No service, scheduled, remotely reachable, setuid or elevated invocation path was found. `N/A` in the escape column means the tool's documented boundary is a caller-selected local/external location rather than a fixed repository root; absolute paths and traversal are therefore capabilities, not escapes. No listed sink deletes data.

| ID | File and reported line | Path source | User-controlled | Intended-root escape | Sink | Overwrite / delete | Privilege / exploit precondition | Decision and evidence |
|---:|---|---|---|---|---|---|---|---|
| 01 | `scripts/curate_2026_screenshot_candidates.py:568` | `--output` | Yes | Source-corpus writes are rejected; other destinations are intentional | Write manifest | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; existing guard protects the documented source corpus |
| 02 | `scripts/generate_pokemon_base_stats.py:59` | `--output` | Yes | N/A; selected output | Write JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 03 | `scripts/generate_pokemon_families.py:59` | `--output` | Yes | N/A; selected output | Write JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 04 | `scripts/generate_pokemon_moves.py:66` | `--output` | Yes | N/A; selected output | Write JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 05 | `scripts/extract_costume_keys_from_gm.py:64` | `--species-map` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 06 | `scripts/extract_costume_keys_from_gm.py:73` | `--gm` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 07 | `scripts/extract_costume_keys_from_gm.py:108` | `--gm` propagated to parser | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same explicit input |
| 08 | `scripts/extract_variant_forms_from_gm.py:62` | `--species-map` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 09 | `scripts/extract_variant_forms_from_gm.py:71` | `--gm` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 10 | `scripts/extract_variant_forms_from_gm.py:169` | `--gm` propagated to parser | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same explicit input |
| 11 | `scripts/generate_costume_signatures.py:27` | `--species-map` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 12 | `scripts/generate_costume_signatures.py:35` | `--costume-keys` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 13 | `scripts/generate_costume_signatures.py:48` | `--variant-registry` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 14 | `scripts/generate_variant_registry.py:20` | `--species-map` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 15 | `scripts/generate_variant_registry.py:28` | `--gm-costume-keys` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 16 | `scripts/train_phase2_from_telemetry.py:292` | `--base-model` | Yes | N/A; selected input | Read model | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 17 | `scripts/train_variant_prototypes.py:37` | `--species-map` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 18 | `scripts/train_variant_prototypes.py:45` | `--variant-catalog` | Yes | N/A; selected input | Read JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 19 | `scripts/extract_costume_keys_from_gm.py:183` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 20 | `scripts/extract_variant_forms_from_gm.py:237` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 21 | `scripts/fetch_bulbapedia_event_pokemon.py:167` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 22 | `scripts/generate_costume_signatures.py:267` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 23 | `scripts/generate_variant_registry.py:127` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 24 | `scripts/train_phase2_from_telemetry.py:299` | `--out` | Yes | N/A; selected output | Serialize JSON | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 25 | `scripts/train_variant_prototypes.py:547` | `--out` | Yes | N/A; selected output | Serialize JSON/model | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 26 | `scripts/extract_variant_forms_from_gm.py:236` | `--out` | Yes | N/A; selected output | Open JSON for write | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 27 | `scripts/fetch_bulbapedia_event_pokemon.py:166` | `--out` | Yes | N/A; selected output | Open JSON for write | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 28 | `scripts/train_phase2_from_telemetry.py:298` | `--out` | Yes | N/A; selected output | Open model/report for write | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output |
| 29 | `scripts/generate_costume_signatures.py:64` | `--assets-dir` | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external asset root |
| 30 | `scripts/generate_costume_signatures.py:187` | `--assets-dir` propagated to builder | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same explicit asset root |
| 31 | `scripts/generate_variant_registry.py:36` | `--assets-dir` | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external asset root |
| 32 | `scripts/generate_variant_registry.py:62` | `--assets-dir` propagated to builder | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same explicit asset root |
| 33 | `scripts/train_variant_prototypes.py:56` | `--assets-dir` | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external asset root |
| 34 | `scripts/train_variant_prototypes.py:492` | `--assets-dir` propagated to trainer | Yes | N/A; selected input tree | Enumerate/read assets | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same explicit asset root |
| 35 | `scripts/generate_pokemon_base_stats.py:57` | `--game-master` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 36 | `scripts/generate_pokemon_base_stats.py:58` | `--names` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 37 | `scripts/generate_pokemon_families.py:57` | `--game-master` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 38 | `scripts/generate_pokemon_families.py:58` | `--names` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 39 | `scripts/generate_pokemon_moves.py:64` | `--game-master` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 40 | `scripts/generate_pokemon_moves.py:65` | `--names` | Yes | N/A; selected input | Read text/JSON | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit external input |
| 41 | `scripts/import_manual_screenshot_dataset.py:383` | `--inbox`-derived source | Yes | N/A; selected input tree | Read/copy source file | No / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit local dataset import |
| 42 | `scripts/import_manual_screenshot_dataset.py:430` | `--inbox` source and `--out-dir` destination | Yes | N/A for selected roots | Copy file | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit import source and destination |
| 43 | `scripts/import_manual_screenshot_dataset.py:414` | `--out-dir` | Yes | Derived children remain under selected root | Create/write image subtree | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; selected external dataset output is required |
| 44 | `scripts/import_manual_screenshot_dataset.py:415` | `--out-dir` derived review path | Yes | Derived sibling is intentional | Create/write review subtree | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; same selected output workflow |
| 45 | `scripts/import_manual_screenshot_dataset.py:433` | `--out-dir` | Yes | Derived child remains under selected root | Write manifest | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output root |
| 46 | `scripts/import_manual_screenshot_dataset.py:434` | `--out-dir` | Yes | Derived child remains under selected root | Write auto-label data | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output root |
| 47 | `scripts/import_manual_screenshot_dataset.py:435` | `--out-dir` | Yes | Derived child remains under selected root | Write summary | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit output root |
| 48 | `.github/prompts/ui-ux-pro-max/scripts/search.py:76` | `--output-dir` | Yes | N/A; selected output root | Write `MASTER.md` | Yes / No | Local user / P1 | `SAFE_LOCAL_CLI_BOUNDARY`; explicit design-system output location |

Classification totals:

- `VALID_SECURITY_DEFECT`: 0
- `SAFE_LOCAL_CLI_BOUNDARY`: 48
- `FALSE_POSITIVE`: 0
- `NEEDS_FOLLOW_UP`: 0

No script change or focused script test was justified. Adding a repository-root restriction would break the documented external input/output purpose of these local maintenance tools without establishing a real trust-boundary improvement.

## Final decision

No dependency or script candidate was promoted. The reviewable change is evidence and authoritative-plan reconciliation only. Future remediation must refresh live alerts and resolved graphs, then choose a supported top-level toolchain combination that clears its complete target family without introducing a critical/high finding.

## Local validation

The isolated worktree did not contain `local.properties`. The first JVM-test invocation therefore stopped during configuration with `SDK location not found`; no test executed. The installed SDK was then supplied through process-local `ANDROID_HOME` and `ANDROID_SDK_ROOT`, without reading, copying or changing `local.properties`.

- `.\gradlew.bat :app:detekt --no-daemon --console=plain` — PASS in 13s.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` — PASS in 2m09s; 654 tests, 0 failures, 0 errors, 0 skipped.
- `.\gradlew.bat :app:lintDebug --no-daemon --console=plain` — PASS in 2m36s.
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` — PASS in 2m27s.
- Final debug, release, JVM-test and Android-test runtime graphs, plus build-environment, KAPT and Kotlin-compiler graphs — PASS for all seven reports.
- Final targeted Snyk dependency scan — reproduced 358 dependencies, 47 unique findings and 250 vulnerable paths.
- Final scoped Snyk Code scan — reproduced 47 `scripts`, 1 `.github`, 0 `app/src`.
- Final Dependabot export — reproduced 53 open transitive alerts: 1 critical, 21 high, 29 medium, 2 low.

The required debug assembly generated an untracked build output for validation only. It was not staged or committed.
