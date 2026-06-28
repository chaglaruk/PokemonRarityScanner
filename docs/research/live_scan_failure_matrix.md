# Live Scan Failure Matrix

Date: 2026-06-27

| Failure | Current guard | Remaining gap |
| --- | --- | --- |
| 2017 Flareon scores base-only | Date OCR now runs in fast path; age score regression test exists | Needs real-device QA on readable date screenshots |
| Wrong species from weak OCR | Species resolver trace and confidence gate downgrade weak evidence | Needs descriptor conflict tests and more labeled fixtures |
| Shiny false positive | Existing shiny signature and merge guardrails suppress weak classifier-only shiny | Needs labeled normal-vs-shiny holdout set |
| Shadow false positive | Visual detector prefers aura/background, not body color alone | Needs dark-body false-positive fixtures |
| Lucky false positive | Lucky OCR label support exists; visual background is conservative | Needs yellow/orange body false-positive fixtures |
| Costume false positive | Full variant matcher and merge logic require support | Needs accessory/body-shape false-positive fixtures |
| Background false positive | Location card flag exists but no complete catalog coverage | Needs normal-card vs special-background fixtures |
| Dynamax/Gigantamax | Unsupported category is reported | Needs metadata and visual evidence source |
| Storage/transition saved as Pokemon | Confidence gate blocks non-detail decisions | Needs connected regression/fixture labels |
| Slow scan | Detailed pass skip exists when CP/name/date reliable | Needs device timing and descriptor latency measurement |
| Broad sprite descriptor species mismatch | Broad descriptor species cannot be production authority; classifier override now requires candy/family support | Needs real holdout screenshots before descriptor species can be enabled |
| Fixture readiness hidden by passing tools | `validate_fixture_labels.ps1` reports descriptor_readiness separately from command success | Needs manual label capture to move readiness from NOT_READY to READY |

## Final Validation Update

Connected regression passed on `SM-S931B - 16`. The remaining recognition blocker is not sprite asset
coverage or reference descriptor mechanics; it is missing live holdout truth and descriptor domain
mismatch. Broad descriptor species recognition remains disabled/support-only.
