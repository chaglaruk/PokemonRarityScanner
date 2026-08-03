# Security Scan Evidence — 3 August 2026

## Status and authority

This document records point-in-time evidence supplied on 3 August 2026. It does not replace live GitHub state or `docs/POKERARITY_IMPLEMENTATION_PLAN.md`.

Repository baseline before this evidence document:

- `origin/main`: `15ef00cf2cfd13cfe05dbff9dd6d6f399fee89ff`
- merged screenshot candidate curation: PR #46, merge `3ed8f5184403e8705f7b87fe839bd4957433fdba`
- merged Copilot repository guidance: PR #47, merge `15ef00cf2cfd13cfe05dbff9dd6d6f399fee89ff`
- privacy hardening under review: PR #48, head `dd8840f022b99ae8891cf9390ccb686fbd436c39`

The uploaded scan artifacts are not committed because they contain tool output, dependency paths, and local-environment references. Only aggregate, non-sensitive results and immutable artifact hashes are recorded here.

## Evidence inventory

| Artifact | SHA-256 | Size |
|---|---|---:|
| Snyk dependency JSON | `6A51332405227E881652714F46CB8FC02BF868C69726942E1172CC580934B3C3` | 2,363,047 bytes |
| Snyk readable dependency output | `81A5D11FA719F4AAD45AE9159A209674317C3EC2D231AD99D5C729745A9E0091` | 438,550 bytes |
| Snyk Code JSON | `F76A718C58481AE80CC4CD2CF3AC1F770B5C209535B42B1E184CD57B3C38B0BB` | 5,184,327 bytes |
| Snyk Code SARIF | `543047A7516609C4DEBF3BC76E5B2150D442747950AFF1A6265B132D97647494` | 5,184,327 bytes |
| Dependabot alert export | `2F7417CE59896A89B5757BF80E87483ABF6A6C6A0DE41E92E37740296D245DB6` | 518,564 bytes |

## Snyk dependency result

The relevant Android Gradle application result resolved 358 dependencies and reported:

- 47 unique vulnerabilities;
- 250 vulnerable dependency paths;
- 1 critical, 24 high, 21 medium, and 1 low unique findings.

Unique findings by package:

| Package | Findings |
|---|---:|
| `io.netty:netty-codec-http` | 18 |
| `io.netty:netty-codec-http2` | 10 |
| `io.netty:netty-handler` | 5 |
| `org.bouncycastle:bcprov-jdk18on` | 4 |
| `io.netty:netty-codec` | 3 |
| `io.netty:netty-common` | 2 |
| `com.google.protobuf:protobuf-java` | 1 |
| `commons-io:commons-io` | 1 |
| `io.netty:netty-handler-proxy` | 1 |
| `io.netty:netty-transport-native-unix-common` | 1 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1 |

The dominant Netty and Protobuf paths resolve through Android Gradle Plugin Unified Test Platform and related build/test tooling. The Bouncy Castle path also appears through Robolectric unit-test tooling. The evidence does not demonstrate a production APK runtime path for these build/test dependencies, but build/test-only exposure is not equivalent to no risk.

The broad `--all-projects` run also inspected unrelated package manifests under `.agent-references`. Twenty-one of forty-eight potential projects could not resolve because their fixture directories intentionally lacked `node_modules`. Those failures do not invalidate the successfully resolved Android Gradle application result.

## Dependabot result

The supplied GitHub API export contains 52 open alerts:

- 1 critical;
- 20 high;
- 29 medium;
- 2 low.

All 52 alerts are transitive, all are associated with `settings.gradle.kts`, and the export provides no dependency scope value.

Alert counts by package:

| Package | Alerts |
|---|---:|
| `io.netty:netty-codec-http` | 18 |
| `io.netty:netty-codec-http2` | 8 |
| `org.bouncycastle:bcprov-jdk18on` | 7 |
| `io.netty:netty-handler` | 5 |
| `io.netty:netty-codec` | 3 |
| `org.bouncycastle:bcpkix-jdk18on` | 2 |
| `io.netty:netty-common` | 2 |
| `org.apache.commons:commons-compress` | 2 |
| `io.netty:netty-handler-proxy` | 1 |
| `org.bitbucket.b_c:jose4j` | 1 |
| `org.jdom:jdom2` | 1 |
| `commons-io:commons-io` | 1 |
| `com.google.protobuf:protobuf-java` | 1 |

The previous authoritative maintenance snapshot recorded 43 open alerts. This export therefore adds nine alerts to that point-in-time inventory: four high and five medium, predominantly newly published Netty advisories. The authoritative plan must be reconciled before treating the old 43-alert count as current.

## Snyk Code result

The Snyk Code output contains 910 findings:

- 862 under `.agent-references`;
- 47 under `scripts`;
- 1 under `.github`;
- 0 under the Android `app` source tree.

All 48 findings outside `.agent-references` are note-level `python/PT` path-traversal reports based on command-line input flowing into local file operations. These are local maintenance scripts, not remotely reachable Android application endpoints. They require script-by-script validation before any code change; the count must not be represented as 48 confirmed exploitable vulnerabilities.

Future Snyk Code runs should exclude `.agent-references` and record the exact scanned roots so copied agent tools and framework fixtures do not dominate project findings.

## Risk decision

1. Do not dismiss or mass-ignore the alerts merely because most observed paths are build/test tooling.
2. Do not force Netty, Protobuf, Bouncy Castle, Kotlin, or Commons versions globally without proving Android Gradle Plugin, Robolectric, KSP/kapt, and CI compatibility.
3. Keep production-runtime and build/test-tooling exposure separate in every report.
4. Treat the 52-alert Dependabot export and 47-unique Snyk dependency result as point-in-time evidence, not permanent counts.
5. Keep release privacy hardening independent from the dependency compatibility experiments when changes do not share files or failure modes.

## Updated execution plan

### M2-A — Evidence reconciliation

- Merge this evidence-only document after CI and review.
- Update `docs/POKERARITY_IMPLEMENTATION_PLAN.md` in a separate reviewed documentation PR.
- Replace the stale 43-alert snapshot with the dated 52-alert Dependabot evidence and 47-unique Snyk result.
- Record the Snyk Code scope contamination and require `.agent-references` exclusion for repeat scans.
- Do not claim dependency remediation complete.

### M2-B — Release privacy containment

- Complete PR #48 if all tests, CI, security checks, and review findings pass.
- Required invariant: release builds must not write OCR crops, raw OCR summaries, screenshot paths, or diagnostics bundles.
- Required invariant: release-visible logcat and app-private audit files must not contain raw upload identifiers, arbitrary error payloads, local paths, or caller-provided sensitive values.
- Keep debug diagnostics available for controlled local evidence work.

### M2-C — Controlled dependency compatibility matrix

Run dependency families as separate, reversible experiments. Do not combine unrelated upgrades into one first trial.

1. Establish and archive baseline dependency graphs for debug runtime, release runtime, debug unit tests, Android tests, and buildscript/UTP tooling.
2. Test the smallest supported Android Gradle Plugin and Gradle upgrade that materially reduces Netty/Protobuf/build-tool alerts.
3. Test Robolectric and its Bouncy Castle path separately.
4. Test Hilt/Kotlin tooling separately if the low Kotlin finding remains after the build-tool trials.
5. For each trial, compare resolved graphs and exact alert families before and after.
6. Reject a trial that breaks compilation, kapt/KSP, unit tests, lint, debug assembly, instrumentation configuration, or required CI checks.
7. Do not use blanket `resolutionStrategy.force` as a substitute for a supported toolchain upgrade.

A successful trial must include:

- focused tests for affected tooling;
- full debug JVM unit suite;
- detekt;
- lintDebug;
- assembleDebug;
- dependency graph evidence for all relevant configurations;
- GitHub Run Tests, CodeQL, Semgrep, SonarCloud when available, and CodeRabbit review;
- a clear statement of alerts fixed, alerts remaining, and new compatibility risks.

### M2-D — Local-script Snyk triage

- Re-run Snyk Code with `.agent-references` excluded.
- Review the 48 remaining note-level path findings by trust boundary.
- Fix only scripts that accept untrusted paths or can overwrite outside an intended root.
- Do not add broad path restrictions to scripts whose documented purpose requires arbitrary user-selected local inputs unless a real unsafe write boundary is demonstrated.

### Existing recognition roadmap

- PR-06 remains incomplete and evidence-gated.
- Native-1440 physical-device evidence remains required.
- Manual Gate A remains open.
- PR-07 remains blocked by Manual Gate A.
- The 120-record candidate manifest from PR #46 remains non-authoritative and requires human privacy, provenance, and truth review.
- No dependency, privacy, or Snyk result weakens the zero accepted-wrong species invariant.

## Non-actions

This evidence document does not:

- change dependencies or Gradle configuration;
- dismiss Dependabot alerts;
- change workflows;
- modify production recognition behavior;
- approve screenshots or truth labels;
- run a release build;
- claim signed-release or MobSF completion;
- alter PR-06, Manual Gate A, or PR-07 completion status.
