# Real-game costume reference pairing: acceptance gate

**Scope:** local QA only. This is a dataset-readiness gate, **not** a trained classifier, accuracy claim, or replacement for the authoritative recognition plan. The checked-in `master_pokedex.json` signatures and `variant_classifier_model.json` are sprite-derived metadata. They do not establish that independent real-game reference screenshots exist.

## Why the current 18-frame experiment cannot establish a winner

The 2026-11-04 local probe compared various descriptors against frames of **other species**. In the genuine species-conditioned, leave-query-out experiment, there was no independent same-species normal-and-costume reference pair for any evaluation frame. Those 18 UNKNOWN outcomes mean **reference coverage was absent**, not that every candidate algorithm was conclusively shown to fail.

A useful comparison needs ground-truth normal **and** costume references from the same species/form/Shiny stratum. For precise costume *identity* it also needs positive samples for each exact event/costume subtype. Real-world evaluation must reserve different capture groups for untouched holdout. A Shiny, non-costumed Cottonee and a normal, flower-crown Cottonee do **not** form a matched normal/costume pair: their Shiny status differs.

## Private local manifest contract

Use `tools/costume_pair_readiness.py` on a JSON manifest held **outside Git**, constructed by reviewing saved local screenshots and checking their full SHA-256. The script reads no PNGs and does not report hashes or local paths.

Each record must have:

- `sha256`: full SHA-256 of the native image, 64 hex characters.
- `species`: canonical species, human-verified.
- `formId`: form identifier, default `00` if independently confirmed.
- `shiny` and `costume`: independent **boolean ground truths**, not the scanner's own predictions.
- `truthStatus`: `human_confirmed` to count; others are excluded.
- `role`: one of `reference`, `development`, `holdout`.
- `captureGroup`: an opaque identifier shared by different views of the **same Pokémon/session**. A group cannot occur across roles.

Example structure with **fabricated IDs and hashes only** (not usable real-world evidence):

```json
{
  "schemaVersion": 1,
  "records": [
    {
      "sha256": "0000000000000000000000000000000000000000000000000000000000000001",
      "species": "Slowpoke",
      "formId": "00",
      "shiny": false,
      "costume": false,
      "truthStatus": "human_confirmed",
      "role": "reference",
      "captureGroup": "example-normal-a"
    },
    {
      "sha256": "0000000000000000000000000000000000000000000000000000000000000002",
      "species": "Slowpoke",
      "formId": "00",
      "shiny": false,
      "costume": true,
      "truthStatus": "human_confirmed",
      "role": "reference",
      "captureGroup": "example-glasses-b"
    }
  ]
}
```

Run locally:

```powershell
python tools/costume_pair_readiness.py C:\\private-qa\\costume-frames-manifest.json --output C:\\private-qa\\costume-reference-readiness.json
```

Output contains only aggregate reference and evaluation counts, species/form labels, Shiny strata and readiness booleans. `pairedReferenceReady` requires at least one independent reference capture group for **both classes** within the same species/form/Shiny stratum. `holdoutBothClassesReady` additionally requires at least one normal and one costumed untouched holdout in that stratum. Neither flag guarantees sample quality, sample size, accurate costume subtype identification, or independent multi-pose generalization.

The gate rejects repeated identical screenshot hashes, capture-group leakage between partitions, missing boolean truth, and raw screenshot/path/OCR fields. A `human_confirmed` label must be independently established, not derived from the detector being benchmarked. Keep all screenshot content, private filenames and source manifests off GitHub unless the user explicitly authorizes publication under existing repository privacy rules.

## Next experimental milestone

Collect **the smallest missing reference pairs first**, preferably from naturally encountered, manually opened game detail screens with scanner-only consent. Prioritize Slowpoke glasses, Butterfree bows and Pikachu hat/tie while retaining Crown Cottonee and Shiny controls. Never automate Pokémon GO gameplay, alter its state, or infer a false normal label from absence of a Shiny icon.

Re-run the current production baseline on the same untouched test frames, then compare *within the locked species* with separate references and an untouched holdout. Only after demonstrated gains should a descriptor, reference format or threshold be promoted into production.

If genuine normal/costume references cannot be obtained for a stratum, report **NO ELIGIBLE PAIRED BENCHMARK**, not 0% model accuracy or a false production fix.
