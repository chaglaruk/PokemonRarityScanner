# Manual Gate A — Status

## Gate Status: OPEN

Manual Gate A remains **OPEN**. This document records the current state
of the Manual Gate A review process.

## Summary

| Item | Status |
|------|--------|
| Gate status | **OPEN** |
| Total candidates | 120 |
| Development candidates | 100 |
| Prospective holdout candidates | 20 |
| Human-verified truth records | **0** |
| Privacy approvals | **0** |
| Provenance approvals | **0** |
| Scanner suggestions promoted to truth | **0** |
| Holdout truth exposure | **0** |
| Holdout quarantine | **Active** |
| native-1440 physical evidence | **BLOCKED** |
| OCR-policy experiment | **BLOCKED** |
| Mewtwo fixture gaps | **Unresolved** |

## Decisions

### UNKNOWN-Only Export

The user elected a **tooling-only partial closeout** rather than
manual review of all 120 candidate records. As a result:

- All 120 candidates remain **non-authoritative**.
- No development candidate gained human-verified truth from this session.
- No privacy approval was inferred.
- No provenance approval was inferred.
- All 20 prospective holdouts remain **quarantined**.
- Holdout truth exposure remains **zero**.
- UNKNOWN-only export does **not** count as Manual Gate A progress.

### native-1440

No genuine native-1440 physical-device corpus is available and validated.
A QEMU emulator is not native-1440 physical-device evidence.

**Status: BLOCKED**

### OCR-Policy Experiment

The OCR-policy experiment is blocked pending:
- Manual Gate A completion (sufficient verified truth data)
- native-1440 physical-device evidence

**Status: BLOCKED**

### Mewtwo / Active Fixture Gaps

The three Mewtwo fixtures still require recovery or real-device recapture.
Missing original/re-captured evidence remains explicitly unresolved.

**Status: Unresolved**

## Tooling Delivered

This PR delivers reusable Manual Gate A review infrastructure:

1. **Ledger Schema** (`scripts/manual_gate/ledger_schema.py`)
   - Deterministic UNKNOWN-only ledger construction
   - Privacy-safe field validation
   - Holdout truth rejection
   - Cross-lane leakage rejection
   - Canonical JSON serialization

2. **Review Generator** (`scripts/manual_gate/review_generator.py`)
   - Offline self-contained HTML review pages
   - Zero external network dependencies
   - Development/holdout isolation display

3. **Export Tool** (`scripts/manual_gate/export_unknown_ledger.py`)
   - CLI entry point for deterministic UNKNOWN-only export
   - Validates all trust boundaries
   - Byte-identical on repeated runs

4. **Review Ledger Schema** (`app/src/test/resources/scan_fixtures/review_ledger_schema.json`)
   - JSON Schema definition for the review ledger format

5. **Tests**
   - Comprehensive Python tests for all trust boundaries
   - Kotlin manifest integrity test

## Trust Boundaries

The following trust boundaries are maintained at all times:

- No species, CP, HP, shiny, shadow, purified, lucky, costume/form,
  date truth may be automatically inferred or promoted.
- No privacy approval may be granted without human review.
- No provenance approval may be granted without human verification.
- Holdout truth must never be exposed during development.
- Cross-lane leakage between development and holdout is rejected.
- Scanner/OCR suggestions must never become committed reviewed truth
  regardless of confidence score.
- Source screenshots are read-only and never committed.

## Remaining Blockers

1. Manual Gate A requires human review of all 120 candidates
2. native-1440 requires genuine physical-device evidence
3. OCR-policy experiment requires both Manual Gate A and native-1440
4. Mewtwo fixtures require recovery or recapture
5. PR-07 (holdout accuracy gate) is blocked on Manual Gate A completion
