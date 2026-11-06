# Visual identity and reference catalog

This catalog is a deterministic, source-pinned inventory for recognition research. It is not an application runtime database and does not itself prove that a species, form, or costume was released. The catalog is kept outside the APK until its identifiers, release claims, reference assets, and recognition value have been independently validated.

## Rebuild and validate

From the repository root:

```text
python3 scripts/build_visual_identity_catalog.py --write
python3 scripts/build_visual_identity_catalog.py --check
python3 scripts/test_visual_identity_catalog.py
```

`--write` verifies pinned PoGoAPI hashes, WatWowMap source file hashes, repository asset hashes, and eight small PokeMiners PNG samples. The PNG samples are fetched in memory with a 128 KiB per-file cap; the generator verifies chunk CRCs and bounded scanline decompression with a 4 MiB decoded-size cap. The generator writes only IDs, source paths, SHA-256 values, image dimensions, and provenance into `scripts/data/visual_identity_catalog.json`; it never writes image bytes. This byte-format check does not qualify visual identity or render quality. `--check` validates the checked-in output offline. Repository inputs are pinned by both commit and content hash so regeneration after a catalog commit does not make the generated file self-referential.

## Identity boundaries

- **Species** use National Dex IDs and canonical names from PoGoAPI, cross-checked against WatWowMap's species records. A species listed in PoGoAPI's released feed is `released`; absence from that feed is `uncertain`, never `not released`.
- **GO forms** are species-to-form memberships from WatWowMap. Game Master availability alone remains `uncertain`. Only exact source form names that match a repository candidate and belong to the same species create a repository crosswalk.
- **GO costume identities** are keyed by species plus the exact numeric Game Master form ID. A source candidate's broad `gameMasterCostumeForms` list is not accepted as an identity mapping. The repository's exact variant ID and asset path remain separate visual-reference candidates.
- **Visual appearances** distinguish normal and Shiny presentation while keeping the costume/form identity stable. Similar labels do not merge different exact IDs. An exact costume reused in later events links to the same GO costume identity; event history is stored separately.
- **Historical events** are canonical records only when a primary official source has been checked. The event links explicitly disallow inferring which historical release produced an image.
- **Visual assets** distinguish a source path from verified bytes. A hash-verified image is still not cleared for redistribution.

## Snapshot coverage at 2026-11-06

The generated catalog contains 1,025 species records; PoGoAPI lists 937 as released and 88 remain uncertain because they are absent from that feed. The pinned WatWowMap snapshot contains 1,508 species/form memberships and 1,506 form definitions. Names are present for all 1,025 species and 1,506 form definitions in each of 15 source locales.

The existing repository metadata contributes 2,168 visual identity candidates, including 453 costume candidates and 4,311 appearance/asset-path records. These are metadata candidates, not proof that each visually distinct variant is covered. The exact-proto and same-species membership rule currently creates 92 catalog-to-GO costume form crosswalks; 1,416 species/form mappings remain unresolved. Only two GO costume identities have release status supported by the official event records in this snapshot.

There are 87 Game Master costume concepts. Their IDs are not crosswalked to repository variant IDs and their release status remains uncertain. The catalog also retains 804 historical event observations from repository metadata; 570 have date claims, but those observations remain unverified source claims.

Two official events are currently canonicalized for Pikachu Pop Star and Pikachu Rock Star: Pokémon GO Fest 2021 (17–18 July 2021) and Pokémon GO Wild Area: Global 2024 (23–24 November 2024). This yields four event-to-costume links. The 2024 announcement calls these the first global appearance since 2021. Pop Star and Rock Star stay separate exact costume identities, while each identity is reused across both event records. Pixels are not allowed to assign a specific event to an encountered Pokémon.

The app metadata contains 4,311 2D icon path hints and no mapped 3D model references. Eight 256×256 PNG source samples have pinned hashes and fully checked PNG scanline streams: the two Pop/Rock Star icon pairs by catalog form, their addressable icon pair, and two similarly named `PGO_*` icons. This does not qualify the image's visual identity or prove that the asset depicts the target runtime rendering. The `PGO_*` assets remain separate because their source identifiers and content hashes differ; human-readable labels do not merge them. No raw source image is included in the catalog or APK. PokeMiners' disclaimer says the content belongs to the respective rights holders; redistribution rights are not cleared. PoGoAPI and WatWowMap license status is also not stated as an application redistribution grant.

## Pinned source manifest

The machine-readable manifest is `scripts/visual_identity_source_pins.json`. It pins:

- PoGoAPI `api_hashes.json`, `pokemon_names.json`, and `released_pokemon.json` by SHA-256.
- WatWowMap `pogo-data-api` commit `087bc276e4e818ea23684df22039f569abbe2435` and each JSON input by SHA-256.
- Repository variant/signature input hashes at `e3e26ff29ec2be94c8be2c88347c09cba02df874`.
- PokeMiners `pogo_assets` commit `db10df75d6f6f92018d9786af0aecc238fecc4ef` and eight bounded PNG samples by source path and SHA-256.
- The two official event URLs, dates, exact costume form IDs, and availability claims.

The snapshot is a dated cutoff, not a live roster. Refreshing it requires reviewing the changed source schemas and provenance claims, then updating the pins and generated catalog together.

## Visual matching status

The catalog does not integrate a new visual classifier. Asset path coverage, source artwork availability, and exact pixel hashes do not prove that an icon descriptor matches the animated 3D Pokémon on a live detail screen. There is no representative, independently separated real-capture holdout in this catalog, and the eight Pikachu asset samples are provenance checks rather than model evaluation data. Keep OCR/species authority unchanged until a controlled comparison against real game captures demonstrates improved automatic coverage without increasing confidently wrong species. PokeMiners documents that it has both 2D and 3D game assets, but those materials are not copied into this repository or the app.
