# Visual Descriptor Generation Report

Date: 2026-06-27

Generated files:

* `build/reports/pogo_reference/visual_descriptor_db.json`
* `build/reports/pogo_reference/descriptor_generation_manifest.json`
* `build/reports/pogo_reference/descriptor_quality_report.md`
* `build/reports/pogo_reference/descriptor_eval_summary.md`
* `build/reports/pogo_reference/confusion_matrix_species.csv`
* `build/reports/pogo_reference/confusion_matrix_variant.csv`
* `build/reports/pogo_reference/false_positive_report.csv`
* `build/reports/pogo_reference/false_negative_report.csv`
* `build/reports/pogo_reference/subtle_shiny_report.csv`
* `build/reports/pogo_reference/latency_descriptor_report.md`

## Descriptor Engine

The generator reuses the existing project-owned descriptor path in `scripts/train_variant_prototypes.py` and app runtime classes:

* aHash
* dHash
* edge descriptor
* full/head/upper/body hue histograms
* foreground ratio
* aspect ratio

Generated descriptor count: 3,591.
Generated species count: 953.

## Strong Model Path

No MobileNet/EfficientNet/ONNX/TFLite model was added. The blocker is not model plumbing; it is missing labeled live crops and false-positive thresholds. Adding a model before labeled evaluation would increase false confidence risk.

## Evaluation

Status: NOT READY.

The evaluation command now performs real local evaluation, not descriptor presence only:

* Packaged runtime model: `app/src/main/assets/data/variant_classifier_model.json`
* Runtime descriptors: 4,044 entries for 928 species.
* Generated dev-only descriptor report DB: 3,591 entries for 953 species.
* Fixture cases: 47.
* Evaluable labeled species fixtures: 16.
* Undecodable labeled fixture files: 3.
* Broad live-fixture species accuracy: 0.000.
* Strict live-fixture species accuracy: 0.000.
* High-confidence fixture false positives: 0.
* Augmented cached-asset cases: 1,500.
* Augmented species accuracy: 1.000.
* Augmented exact sprite accuracy: 0.981.
* Fixture p95 descriptor evaluation latency: 569.0 ms on this JVM run.
* Augmented narrowed p95 descriptor latency: 79.3 ms on this JVM run.

Interpretation: the descriptor model is useful as OCR/species-scoped supporting evidence, but it is not production-ready as broad live screenshot species recognition. The domain mismatch between 2D reference sprites and live Pokémon GO 3D detail screenshots remains a quality blocker. See `build/reports/pogo_reference/descriptor_eval_summary.md` and `build/reports/pogo_reference/descriptor_fixture_predictions.csv`.
