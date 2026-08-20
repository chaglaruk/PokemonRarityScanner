# Manual Gate A — Supporting Status Snapshot

This document is a non-authoritative supporting snapshot for the tooling-only
partial closeout.

- **Snapshot captured (UTC):** `2026-08-20T06:37Z`
- **Snapshot revision:** `30e8a1adfa4a1677c23ceadac5788aba3a9b3373` (`test/manual-gate-a-review-workflow`)
- **Authoritative roadmap:** `docs/POKERARITY_IMPLEMENTATION_PLAN.md`
- **Authoritative plan blob at the start of this work:** `98361d3519ef8932c941a8a11d978f204fea8f62`
- **Authoritative `origin/main` at the start of this work:** `5e82106cccd446dc24422ccd842ce2870439002b`

## Current status

| Item | Status |
| --- | --- |
| Manual Gate A | **OPEN** |
| Candidate manifest | 120 non-authoritative records |
| Development candidates | 100 |
| Prospective holdout candidates | 20, quarantined |
| Human-verified truth added by this work | **0** |
| Privacy approvals added by this work | **0** |
| Provenance approvals added by this work | **0** |
| Scanner/OCR suggestions promoted | **0** |
| Holdout truth exposure | **0** |
| PR-06 geometry/OCR-resolution experiment | **INCOMPLETE / evidence-gated** |
| Native-1440 physical evidence | **BLOCKED / not supplied** |
| OCR-policy controlled experiment | **BLOCKED** |
| PR-08 logging/privacy/release hardening | **PARTIAL** — narrow privacy containment merged in PR #48; remaining release scope incomplete |
| PR-09 signed-release verification + MobSF | **NOT COMPLETE / not advanced** |
| Mewtwo recovery/recapture gaps | **Unresolved** |

## Tooling-only decision

Human review of the candidate corpus is deferred. The deterministic export in
this PR can represent only the following state for every candidate:

- `reviewStatus = UNKNOWN`
- `privacyDisposition = NOT_REVIEWED`
- `provenanceDisposition = NOT_VERIFIED`
- empty truth fields and reviewer notes
- `suggestionsPromoted = false`

The exporter and validator are deliberately unable to represent a completed
gate, an approval, verified truth, promoted scanner suggestions, or holdout
truth. The generated session ledger must remain outside the repository.

## Repository evidence versus local evidence

The committed candidate manifest records the source-corpus identity constants:
730 source files, 473,826,206 aggregate bytes, and source digest
`e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1`.
Those committed constants and the manifest structure are GitHub-verifiable.
Whether a particular local screenshot directory still matches those bytes is a
point-in-time local check and must not be inferred from GitHub alone.

## Tooling delivered

- `scripts/manual_gate/ledger_schema.py` strictly validates the exact candidate
  dataset accepted by this partial-closeout tool and constructs only the
  UNKNOWN-only overlay.
- `scripts/manual_gate/export_unknown_ledger.py` writes deterministic canonical
  JSON and rejects repository-local output regardless of where the supplied
  manifest originates.
- `scripts/manual_gate/review_generator.py` creates a static offline
  status/readiness page. It is **not** a human truth editor and does not embed
  screenshots or source filenames.
- `app/src/test/resources/scan_fixtures/review_ledger_schema.json` mirrors the
  fail-closed UNKNOWN-only contract.
- Python tests cover dataset identity, exact lane/ID/hash binding, approvals and
  truth rejection, holdout isolation, privacy/path filtering, deterministic
  output, canonical bytes, repository-output rejection, committed-manifest
  integration, and HTML input/network validation.
- The existing `Candidate2026S25ManifestTest` remains the repository's stronger
  JVM integrity test for the committed candidate manifest; this PR does not add
  a redundant second copy.

## Remaining roadmap gates

Per the authoritative plan, Manual Gate A remains open until there is sufficient
manually confirmed truth for the next recognition gate and a separately approved
immutable end-to-end holdout. This snapshot does **not** assert that all 120
candidate records must be manually reviewed before any progress is possible.

PR-06 still requires genuine native-1440 physical-device evidence, a controlled
OCR-policy comparison, and real-device memory/performance evidence. Emulator,
resized, synthetic, display-overridden, or web-sourced images do not satisfy that
gate. PR-07 remains blocked on sufficient Manual Gate A completion and an
approved immutable holdout. PR-08 remains only partially advanced by the narrow
privacy containment merged in PR #48; this tooling does not complete its release
scope. PR-09 signed-release verification and MobSF remain unadvanced. The three
Mewtwo fixtures still require original recovery or genuine recapture; no
replacement truth is fabricated here.
