# Descriptor Domain Mismatch Report

Date: 2026-06-28
Branch: `feature/calcy-level-visual-recognition-engine`

## Decision

Broad descriptor species recognition is **not production-enabled**.

## Evidence

The descriptor pipeline has complete local sprite cache coverage for the indexed source set and
works inside the sprite/reference domain, but it does not transfer to live Pokemon GO detail
screenshots yet:

* cached indexed assets: 5,840 / 5,840
* generated dev descriptors: 3,591
* packaged runtime descriptors: 4,044
* augmented cached-asset species accuracy: 1.000
* augmented exact-sprite accuracy: 0.981
* live fixture broad species accuracy: 0.000 on 16 evaluable labeled species fixtures
* current fixture decode errors: 3

Interpretation: 2D reference sprites and live Pokemon GO 3D screenshots are different domains.
Self-match and augmented sprite tests prove descriptor mechanics, not production screenshot
recognition.

## Runtime Policy

Descriptor/classifier evidence may:

* support OCR/candy/family narrowed candidates
* support variant recognition only after species identity is reliable
* downgrade conflicts to UNCERTAIN
* emit local diagnostics explaining support-only or blocked evidence

Descriptor/classifier evidence must not:

* produce ACCEPT by itself
* save collection history by itself
* award rarity points for uncertain variants
* override strong OCR or candy/family evidence across families
* claim Calcy-level broad species recognition

## Readiness Blocker

Descriptor production readiness requires at least 50 decodable species-labeled holdout screenshots
and at least 3 positive plus 3 negative holdout screenshots for each major variant flag. The current
manifest does not meet that bar.

## Final Validation Note

The reference pipeline completed with full indexed asset coverage and the evaluator still reports
`descriptor_eval_status=NOT READY`. This is the intended release gate: production runtime guardrails
can ship, but broad descriptor species recognition remains disabled until live holdout evaluation
passes.
