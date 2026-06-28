# Clean-Room Recognition Behavior Follow-Up

Date: 2026-06-26
Branch: main

## Calcy Folder Status

No local Calcy decompiled folder was found under the repository search scope used for this sprint. No Calcy source, assets, databases, traineddata, constants, thresholds, identifiers, strings, templates, or implementation details were accessed, copied, imported, pasted, or ported.

This note uses only existing project reports and clean-room behavior categories already documented for PokemonRarityScanner.

## Behavior Categories To Match

Fast reliable recognition needs these architectural properties:

* Route screen state early so storage, transition, and unknown screens do not run the full detail pipeline.
* Prefer a fast detail-screen path for essential fields: CP, HP, Name, Date, and cheap variant cues.
* Run detailed OCR or heavier visual checks only when essential evidence is missing, conflicting, or low confidence.
* Validate each field before it reaches scoring.
* Keep variant evidence conservative; weak visual evidence should not add full rarity bonuses.
* Explain retry, missing field, and low-confidence outcomes through local diagnostics.

## Implemented In This Sprint

* Date OCR now runs in the fast pass instead of being treated as secondary.
* Date normalization supports common Pokemon GO date shapes without inventing partial dates.
* Future, pre-Pokemon-GO, and impossible dates are rejected with explicit diagnostic reasons.
* High-confidence fast scans can skip detailed OCR when CP, name, and date are already reliable.
* Local diagnostics now include stage timings and rarity breakdown data needed to explain missing age score.
* Missing caught date now triggers a local diagnostic export, so base-only age failures are inspectable.

## Not Implemented

* No ML Kit replacement.
* No native OCR engine.
* No new visual matcher, templates, copyrighted assets, thresholds database, or third-party dataset.
* No copied Calcy implementation.
* No large species/form/variant rewrite.
* No telemetry behavior change.

## Remaining Original Tasks

* Label live screenshots for date, species, screen state, confidence decision, and variant flags.
* Use those labels to measure actual false-positive and false-negative rates.
* Add small project-owned visual rules only when the local evidence shows a repeatable failure pattern.
* Keep the fast path under the normal detail-screen latency budget by avoiding detailed OCR unless the gate needs it.
