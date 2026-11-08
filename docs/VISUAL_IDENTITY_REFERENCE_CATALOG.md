# Visual identity and reference catalog

This is a deterministic, source-pinned research inventory. It is not an application runtime database and does not prove that a species, form, or costume was released. The catalog stays outside the APK until identity, release, asset rights, and recognition value are independently validated.

## Rebuild and validate

From the repository root:

```text
python3 scripts/build_visual_identity_catalog.py --write
python3 scripts/build_visual_identity_catalog.py --check
python3 scripts/test_visual_identity_catalog.py
```

`--write` verifies PoGoAPI hashes, WatWowMap file hashes, repository input hashes, nine bounded PokeMiners PNG samples, the pinned PokeMiners Pokemon 3D subtree, and two bounded 3D samples. The tree API call is restricted to the exact pinned source and fails if GitHub marks it truncated. PNG data is fetched in memory with a 128 KiB per-file cap and checked for chunk CRCs and bounded scanline decompression. The 3D sample bytes are checked against both SHA-256 and their Git tree blob SHA. The generated JSON contains identifiers and provenance only; no image or model bytes are written. `--check` works offline.

## Identity boundaries

- **Species:** National Dex IDs and canonical names are cross-checked between PoGoAPI and WatWowMap. A species in PoGoAPI's released feed is `released`; absence is `uncertain`, never `not released`.
- **GO forms:** memberships come from WatWowMap. Game Master presence alone remains `uncertain`. Repository crosswalks require exact source form and same-species evidence.
- **Visual identity keys:** every source appearance must include an explicit, nonempty `formId`. Missing, null, blank, or boolean values fail catalog generation; an unknown form is never silently assigned to base form `00`.
- **Costumes:** exact numeric Game Master form IDs are separate from repository variant IDs. Similar labels do not merge identities. A reused costume keeps one visual identity and links to each verified event separately.
- **Events:** official primary sources are required for canonical event records. Pixels cannot infer which event produced a visually identical costume.
- **Assets:** a path, tree Git blob SHA, verified content SHA-256, decoded image, renderable model, and redistribution permission are distinct evidence states. Filename tokens are unresolved candidates until an exact crosswalk is supported.

## Snapshot coverage at 2026-11-07

- 1,025 species records: 937 listed as released by PoGoAPI, 88 uncertain because the feed omits them, and none declared unreleased. Names exist for all records in each of 15 pinned locales.
- 1,508 species/form memberships and 1,506 form definitions. 1,416 catalog-form crosswalks remain unresolved.
- 2,168 visual identity candidates, 477 costume candidates, 4,311 normal/Shiny appearance records, and 8,080 total asset path references.
- 87 Game Master costume concepts and 108 exact species/form costume identity candidates. Four distinct costume identities have six explicit official event/form links in this snapshot; the remaining candidates stay uncertain. A Game Master costume form is represented even when this repository has no corresponding visual prototype or renderable reference.
- 804 repository event observations, 570 with date claims; these remain unverified observations. Three official events are canonicalized. GO Fest 2021 and GO Wild Area: Global 2024 both link the same Pop Star and Rock Star Pikachu identities, preserving their re-releases without duplicating visual identities. Halloween 2026 Part I links Pikachu's hat/capelet and Zubat's top hat to exact source form IDs. The official announcement names Sinistea wearing a bow but does not distinguish its Phony and Antique form identities, so both candidates remain in a separate ambiguous claim and neither is promoted to a verified exact release mapping. Pixels do not identify a particular re-release or resolve that event ambiguity.
- 4,311 2D icon path hints and nine 256x256 PNG samples whose content hashes and PNG scanline streams were verified. These checks do not prove that an icon matches the in-game animated model.
- The pinned PokeMiners Pokemon subtree contains 987 model paths (`.fbx`/`.obj`) and 2,782 texture paths (`.png`). All 3,769 3D references have unresolved filename-to-identity mappings. Tree Git blob SHAs and byte sizes are recorded; only two local samples have SHA-256 content hashes. No raw assets are tracked or packaged, and redistribution rights are not cleared.

## Local 3D render probe

A private Bidoof detail capture was compared with a locally downloaded, source-pinned Bidoof rig and a 2D shiny icon. Blender 4.5.15 imported the FBX and rendered six camera views. On the same manually selected crop, the best 3D silhouette IoU was 0.703 versus 0.617 for the shiny 2D icon. This was a single development probe: crop and view were selected against that same capture, the 3D model had no verified Shiny texture, and it is not independent accuracy evidence. No classifier was added to the application.

## Pinned sources and limitations

`scripts/visual_identity_source_pins.json` pins PoGoAPI hashes; WatWowMap commit `f9df2e21b22abccb939202466c8468783a116f9a` and 33 source file hashes; repository variant/signature inputs at commit `621f0300b7ecb07a43a6c2c6f9e8ab7242913fe5` (included in this draft branch and different from current main); and PokeMiners `pogo_assets` commit `db10df75d6f6f92018d9786af0aecc238fecc4ef` with Pokemon subtree SHA `58f017d9b6695d87051a9e6ae31be19e311e5691`. The [official Halloween 2026 Part I announcement](https://pokemongo.com/news/halloween-part-1-2026?hl=en) dates the event October 27–November 1 and names costumed Pikachu, Zubat, and Sinistea. The pinned WatWowMap forms data contains corresponding costume memberships for those species and forms for their listed evolutions. The announcement does not establish release of the evolved forms or distinguish Sinistea's Phony and Antique identities. Those unsupported mappings are not promoted.

PokeMiners states that the asset contents belong to The Pokemon Company and Niantic; development access is not redistribution permission. PoGoAPI and WatWowMap license metadata is not an application redistribution grant. The catalog does not contain or package raw assets. It does not integrate a visual classifier, and it has no independent, multi-species real-capture holdout. Keep OCR/species authority unchanged until a controlled comparison demonstrates higher automatic coverage without more confidently wrong species.


## Private pinned source-asset archive (developer machine only)

The separate downloader `scripts/download_visual_reference_assets.py` fetches the **available public-source asset files** from the pinned PokeMiners commit, without committing or packaging the copyrighted bytes. It is not a claim to cover every Pokémon GO release or form. Its manifest is generated from the verified, non-truncated source Git trees and records exact source paths, Git blob SHA-1 identities, sizes and categories.

The checked PokeMiners commit `db10df75d6f6f92018d9786af0aecc238fecc4ef` currently supplies:

| Source class | Files | Bytes |
| --- | ---: | ---: |
| 256x256 Pokémon PNG icons | 6,063 | 177,328,821 |
| Pokémon 3D texture PNGs | 2,782 | Included below |
| Pokémon 3D FBX/OBJ models | 987 | Included below |
| 3D models + textures | 3,769 | 828,784,571 |
| **Available source assets total** | **9,832** | **1,006,113,392** |

These are **files**, not 9,832 distinct Pokémon visual identities. Many files are textures or historical asset variants; some forms are missing. For example, this pinned source contains two normal/Shiny Shellos form pairs (`pokemon_icon_422_11` and `pokemon_icon_422_12`), but **no `pokemon_icon_928_*` Smoliv icons**. Downloading the source cannot create missing Smoliv references. Visual matching still needs trusted identity mapping and independent real-device validation.

From the repository root on a developer machine with Python 3.10+ and sufficient free storage:

```powershell
py -3 scripts/download_visual_reference_assets.py --download --scope all
py -3 scripts/download_visual_reference_assets.py --verify
```

`--scope icons` downloads only the available 2D PNG icons. `--scope textures` and `--scope models` select other categories. The script uses four bounded workers by default (maximum eight), retries transient network failures, checks each file's source Git blob SHA, resumes by re-verifying existing files, and preserves mismatched pre-existing files unless explicitly authorized with `--replace-invalid`. `--limit N` is only for smoke tests and is never full coverage. Offline `--verify` checks the last local inventory and fails if source assets are absent or modified.

The resulting data and inventory stay in `external/pogo_assets/`, which is already gitignored. **No download to the user's computer has been executed by the GitHub-only operation that added this script.** The source rights are not cleared for repackaging or redistribution, and a complete Pokémon GO visual-identity atlas would require additional authorized sources plus validated coverage for all missing forms, Shiny pairs and costumes. Do not feed uncertain filename matches into production decisions.
