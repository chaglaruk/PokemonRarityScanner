# Live Recognition QA Plan

Date: 2026-06-27

Run on Samsung S25 and Pixel 4a. For every case, save local diagnostics only, inspect stage timings, and confirm telemetry remains metadata-only.

| Case | Expected recognition | Expected decision | Overlay/save | Latency target |
| --- | --- | --- | --- | --- |
| 2017 normal Flareon | Flareon, date parsed, no variants, score about 25-26 | ACCEPT or ACCEPT_LOW_CONFIDENCE | show/save only if accepted | <2s |
| recent normal Flareon | Flareon, recent/no age bonus, score about 4 | ACCEPT or ACCEPT_LOW_CONFIDENCE | show/save only if accepted | <2s |
| normal Raikou shiny false-positive regression | Raikou, shiny false, no shiny score bonus | ACCEPT or ACCEPT_LOW_CONFIDENCE | show/save only if accepted; no shiny bonus | <2s |
| obvious shiny | species correct, shiny true only with high confidence | ACCEPT/LOW | score bonus only if scoreEligible | <5s |
| subtle shiny | uncertain unless strong species-specific evidence | LOW/UNCERTAIN | no full shiny bonus when uncertain | <5s |
| shadow | shadow true only from aura/label/background evidence | ACCEPT/LOW | no bonus when uncertain | <5s |
| purified | purified true only from visible reliable indicator | ACCEPT/LOW/UNCERTAIN | no bonus when uncertain | <5s |
| lucky | lucky true from OCR label or safe background cue | ACCEPT/LOW | no yellow-body false positive | <2s if label visible |
| costume | catalog + descriptor support required | ACCEPT_LOW/UNCERTAIN | no full bonus when weak | <5s |
| event costume | event/costume labels correct when supported | ACCEPT_LOW/UNCERTAIN | no full bonus when weak | <5s |
| regional form | form correct or uncertain | ACCEPT_LOW/UNCERTAIN | do not force wrong form | <5s |
| special form | form correct or uncertain | ACCEPT_LOW/UNCERTAIN | no full bonus when weak | <5s |
| gender visual difference | detect only if supported, otherwise uncertain | ACCEPT_LOW/UNCERTAIN | no score bonus when unsupported | <5s |
| special background/location card | background true only with high-confidence card cue | ACCEPT_LOW/UNCERTAIN | no normal-card false positive | <5s |
| Dynamax | true only with supported label/badge/effect | UNCERTAIN until metadata exists | no bonus when unsupported | <5s |
| Gigantamax | true only with supported label/badge/effect | UNCERTAIN until metadata exists | no bonus when unsupported | <5s |
| Mega/Primal | correct form if visible/relevant | ACCEPT_LOW/UNCERTAIN | no wrong form save | <5s |
| storage list | no detail Pokemon result | REJECT_NOT_POKEMON_SCREEN or UNCERTAIN | no confident save | <1s |
| transition/blur | no detail Pokemon result | RETRY or REJECT | no confident save | <1s |
| appraisal screen | species/core fields and appraisal parsed if visible | ACCEPT/LOW | save only accepted | <2s |
| scrolled detail screen | date/candy/stardust from visible crops | ACCEPT/LOW | save only accepted | <2s |
| nickname Pokemon | canonical species from name/candy/profile evidence | ACCEPT_LOW/UNCERTAIN | no wrong confident species | <5s |
| non-English date/device locale | date parsed only when supported, else missing reason | ACCEPT_LOW/UNCERTAIN | no invented date | <5s |

Required diagnostics for every case:

* screen type and confidence
* crop provenance
* raw and normalized OCR candidates
* species resolver alternatives
* variant evidence and scoreEligible
* scan confidence decision
* rarity breakdown
* decode/OCR/descriptor/variant/rarity/total timings

Descriptor readiness prerequisites before release:

* Repair or replace the three undecodable armored/lucky Mewtwo fixture PNGs.
* Capture at least 50 decodable species-labeled holdout screenshots.
* Capture at least 3 positive and 3 negative holdout screenshots for each major variant flag.
* Re-run `.\scripts\reference_pipeline\evaluate_visual_descriptors.ps1` and require broad live-fixture species accuracy and false-positive gates to pass before descriptor-only species evidence is allowed to save scans.

## Fixture Tool Workflow

Use the local-only Scope C tools for the next real-data pass:

1. `.\scripts\fixture_tools\create_live_fixture_session.ps1 -SessionName live_s25_flareon_age`
2. Capture app-private local diagnostics on the device.
3. `.\scripts\fixture_tools\import_device_diagnostics.ps1 -IncludeScreenshots`
4. `.\scripts\fixture_tools\build_fixture_contact_sheet.ps1`
5. `.\scripts\fixture_tools\label_fixture_template.ps1`
6. Manually fill only visually certain labels.
7. `.\scripts\fixture_tools\validate_fixture_labels.ps1`
8. `.\scripts\fixture_tools\split_fixture_holdout.ps1`

The validator must report descriptor readiness as READY before descriptor broad species recognition is
considered for production. Until then, descriptor evidence stays support-only.

## Final Validation Note

Connected regression passed on `SM-S931B - 16` after the runtime guardrail fixes. Keep the 2017
Flareon, normal Raikou, regular Slowpoke, glasses Slowpoke, and non-shiny costumed Pikachu cases in
the first manual smoke run because they cover the bugs fixed in this branch.
