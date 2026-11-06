# Visual identity and reference catalog

This is a deterministic, source-pinned research inventory. It is not an application runtime database and does not prove that a species, form, or costume was released. The catalog stays outside the APK until identity, release, asset rights, and recognition value are independently validated.

## Rebuild and validate

From the repository root:

```text
python3 scripts/build_visual_identity_catalog.py --write
python3 scripts/build_visual_identity_catalog.py --check
python3 scripts/test_visual_identity_catalog.py
```

`--write` verifies PoGoAPI hashes, WatWowMap file hashes, repository input hashes, eight bounded PokeMiners PNG samples, the pinned PokeMiners Pokemon 3D subtree, and two bounded 3D samples. The tree API call is restricted to the exact pinned source and fails if GitHub marks it truncated. PNG data is fetched in memory with a 128 KiB per-file cap and checked for chunk CRCs and bounded scanline decompression. The 3D sample bytes are checked against both SHA-256 and their Git tree blob SHA. The generated JSON contains identifiers and provenance only; no image or model bytes are written. `--check` works offline.

## Identity boundaries

- **Species:** National Dex IDs and canonical names are cross-checked between PoGoAPI and WatWowMap. A species in PoGoAPI's released feed is `released`; absence is `uncertain`, never `not released`.
- **GO forms:** memberships come from WatWowMap. Game Master presence alone remains `uncertain`. Repository crosswalks require exact source form and same-species evidence.
- **Costumes:** exact numeric Game Master form IDs are separate from repository variant IDs. Similar labels do not merge identities. A reused costume keeps one visual identity and links to each verified event separately.
- **Events:** official primary sources are required for canonical event records. Pixels cannot infer which event produced a visually identical costume.
- **Assets:** a path, tree Git blob SHA, verified content SHA-256, decoded image, renderable model, and redistribution permission are distinct evidence states. Filename tokens are unresolved candidates until an exact crosswalk is supported.

## Snapshot coverage at 2026-11-06

- 1,025 species records: 937 listed as released by PoGoAPI, 88 uncertain because the feed omits them, and none declared unreleased. Names exist for all records in each of 15 pinned locales.
- 1,508 species/form memberships and 1,506 form definitions. 1,416 catalog-form crosswalks remain unresolved.
- 2,168 visual identity candidates, 477 costume candidates, 4,311 normal/Shiny appearance records, and 8,080 total asset path references.
- 87 Game Master costume concepts and 92 costume identity candidates. Two costume identities have verified release evidence in this snapshot; there are four official event-to-costume links.
- 804 repository event observations, 570 with date claims; these remain unverified observations. Two official Pikachu events are canonicalized: GO Fest 2021 and GO Wild Area: Global 2024. Pop Star and Rock Star remain separate exact identities and each links to both event records. Pixels do not identify a particular re-release.
- 4,311 2D icon path hints and eight 256x256 PNG samples whose content hashes and PNG scanline streams were verified. These checks do not prove that an icon matches the in-game animated model.
- The pinned PokeMiners Pokemon subtree contains 987 model paths (`.fbx`/`.obj`) and 2,782 texture paths (`.png`). All 3,769 3D references have unresolved filename-to-identity mappings. Tree Git blob SHAs and byte sizes are recorded; only two local samples have SHA-256 content hashes. No raw assets are tracked or packaged, and redistribution rights are not cleared.

## Local 3D render probe

A private Bidoof detail capture was compared with a locally downloaded, source-pinned Bidoof rig and a 2D shiny icon. Blender 4.5.15 imported the FBX and rendered six camera views. On the same manually selected crop, the best 3D silhouette IoU was 0.703 versus 0.617 for the shiny 2D icon. This was a single development probe: crop and view were selected against that same capture, the 3D model had no verified Shiny texture, and it is not independent accuracy evidence. No classifier was added to the application.

## Pinned sources and limitations

`scripts/visual_identity_source_pins.json` pins PoGoAPI hashes; WatWowMap commit `99d0ce62ba770b0e11d1b9634fef31e48012b78a` and source file hashes; repository variant/signature inputs at commit `621f0300b7ecb07a43a6c2c6f9e8ab7242913fe5` (included in this draft branch and different from current main); and PokeMiners `pogo_assets` commit `db10df75d6f6f92018d9786af0aecc238fecc4ef` with Pokemon subtree SHA `58f017d9b6695d87051a9e6ae31be19e311e5691`. WatWowMap's primary forms, costumes, and Pokemon JSON contents match the previous pin byte-for-byte.

PokeMiners states that the asset contents belong to The Pokemon Company and Niantic; development access is not redistribution permission. PoGoAPI and WatWowMap license metadata is not an application redistribution grant. The catalog does not contain or package raw assets. It does not integrate a visual classifier, and it has no independent, multi-species real-capture holdout. Keep OCR/species authority unchanged until a controlled comparison demonstrates higher automatic coverage without more confidently wrong species.
