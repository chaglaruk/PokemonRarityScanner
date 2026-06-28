# Recognition Branch PR Split Plan

Date: 2026-06-28
Branch: `feature/calcy-level-visual-recognition-engine`

## PR 1: Production Runtime Guardrails

Recommended commit message:

`fix: harden live recognition guardrails`

Scope:

* Date OCR fast-path and age scoring fixes.
* Species name recovery and candy rescue.
* Marker-token species rejection.
* Classifier species authority guardrails.
* Variant false-positive suppression.
* Rarity scoring score-eligibility guardrails.
* Confidence/save behavior for weak or non-detail scans.
* Focused JVM and Android test updates.

Review note:

This PR must not include raw external assets, generated build reports, descriptor DB replacement, or
Calcy-level broad visual recognition claims.

Validation status:

* Full JVM unit tests passed.
* Connected Android regression passed on `SM-S931B - 16`.
* `assembleDebug`, `lintDebug`, `compileDebugAndroidTestKotlin`, and `git diff --check` passed.

## PR 2: Dev-Only Reference Pipeline

Recommended commit message:

`chore: add dev-only pogo reference descriptor pipeline`

Scope:

* `scripts/reference_pipeline/*`
* `.gitignore` cache rules.
* Research/source/catalog/asset/descriptor reports.
* Descriptor evaluator and readiness reports.

Review note:

This PR is tooling only. Runtime descriptor broad species recognition remains disabled/support-only.
Generated descriptor DB files and raw assets stay ignored unless a later legal/product review approves
a release artifact.

## PR 3: Fixture Capture And Labeling Tools

Recommended commit message:

`test: add live fixture capture and holdout tooling`

Scope:

* `scripts/fixture_tools/*`
* fixture contact sheet/template/validation/split preview support
* fixture truth plan and live QA docs

Review note:

This PR does not fabricate labels. It creates the local workflow needed to collect at least 50
decodable species-labeled holdout screenshots and the required positive/negative variant cases.

## PR 4: Future Descriptor Runtime Enablement

Do not open this PR yet.

Prerequisites:

* Real holdout descriptor evaluation passes thresholds.
* Broad live fixture species accuracy is acceptable.
* False-positive reports pass for shiny, shadow, lucky, costume, and special background.
* Legal/product review approves any generated descriptor artifact derived from external sprites.
* Device latency meets the QA targets.
