# util/

Scanner utility layer.

## Subareas

- `ocr/` — region selection, preprocessing, OCR extraction, diagnostics
- `vision/` — species/variant matching, appraisal/arc analysis, phase 2 classifier integration
- shared helpers for clipboard, haptics, and parsing support

Rule: keep the heavy recognition logic here. Business scoring belongs in `data/repository/`.
