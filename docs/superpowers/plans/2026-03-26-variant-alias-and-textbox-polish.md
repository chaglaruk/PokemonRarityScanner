## Goal

Replace generic rarity explanation text like `Costume variant` with exact costume/event labels from authoritative catalog data, and increase textbox readability.

## Steps

1. Extend catalog generation to map sparse costume variant ids to ordered Game Master form names where possible.
2. Add regression coverage for a known Pikachu sprite key that currently falls back to generic text.
3. Slightly increase narrative textbox emphasis in overlay and full result UI.
4. Regenerate catalog, run targeted tests, build, install, and update `rapor.md`.
