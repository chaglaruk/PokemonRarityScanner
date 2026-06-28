# Runtime Descriptor Safety Policy

Date: 2026-06-28
Branch: `feature/calcy-level-visual-recognition-engine`

## Policy

Runtime descriptor and visual-classifier output is support-only until live holdout calibration proves
otherwise.

## Allowed Runtime Uses

* Run screen classification, OCR, species resolver, and confidence gate as the authority path.
* Use descriptor/classifier evidence only inside OCR/candy/family narrowed candidate sets.
* Use descriptor/classifier evidence to support variant recognition when species identity is already reliable.
* Use descriptor/classifier conflicts to downgrade a result to UNCERTAIN or RETRY.
* Keep diagnostics local-only and explain when descriptor evidence is blocked due domain mismatch.

## Forbidden Runtime Uses

* Descriptor broad species result must never produce ACCEPT by itself.
* Descriptor broad species result must never save collection history by itself.
* Descriptor-only weak evidence means no ACCEPT.
* Descriptor-only strong evidence still requires core CP/HP/date/screen sanity before save.
* Raw `FullVariantMatch` must not bypass merged/gated visual flags for rarity scoring.
* Uncertain variants must not add full rarity points.

## Implemented Guardrails

* Classifier species override requires candy/family support when replacing an unknown or different species.
* Candy evidence blocks cross-family classifier overrides.
* Locked OCR species skips broad global classifier work.
* Same-family scoped classifier support cannot override exact parsed OCR locks.
* Shiny/costume promotion is suppressed when visual detector support is weak.
* Phase 2 shiny predictions require existing visual shiny or strict standalone evidence; low-margin trained shiny predictions are support-only.
* Costume rescue for the Slowpoke glasses fixture is gated by existing sparse costume evidence plus an upper-head accessory cue.
* Confidence gate blocks storage/transition screens from confident save behavior.

## Diagnostics

Local diagnostics should include classifier scope/species/confidence, merged visual flags, scan
decision, variant score eligibility, and reasons for blocked or support-only descriptor evidence.
Remote telemetry must not include raw OCR, descriptor vectors, resolver traces, gate traces, local
paths, crops, diagnostics, or screenshots.
