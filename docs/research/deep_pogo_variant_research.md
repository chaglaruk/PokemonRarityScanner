# Deep Pokemon GO Variant Research

Date: 2026-06-27

## Sources Used

* PoGoAPI endpoints: released Pokemon, forms, shiny Pokemon, shadow Pokemon, mega Pokemon, stats, regional endpoint attempts, and API hashes.
* PokeMiners Game Master: latest raw Game Master JSON.
* PokeMiners pogo_assets: GitHub tree for Pokemon 256x256 sprites.
* Existing project data: `authoritative_variant_db.json`, `variant_catalog.json`, `variant_classifier_model.json`, `shiny_signatures.json`, `costume_signatures.json`, `bulbapedia_event_pokemon_go.json`.
* Public event/reference sources identified for manual cross-check: Pokemon GO Live News, Leek Duck events, Bulbapedia Event Pokemon GO, Serebii Pokemon GO events.

## Source Findings

* PoGoAPI reported 937 released Pokemon in `released_pokemon.json`.
* PoGoAPI reported 863 shiny-available entries and 245 shadow-available entries.
* PoGoAPI mega metadata reported 8 entries.
* Project authoritative variant DB reported 4,202 entries, including 883 costume/event-class entries.
* PokeMiners pogo_assets tree contained 5,840 Pokemon PNG asset paths under `Images/Pokemon - 256x256`.

## Variant Categories

Covered or partially covered by current sources:

* species
* forms from project variant DB and PoGoAPI forms endpoint
* shiny availability
* shadow availability
* purified possible only as a derivative of shadow availability
* lucky as generic visual state
* costume/event variants from project DB
* Mega/Primal from PoGoAPI/project data
* regional forms from available regional endpoints and project variant DB

Not sufficiently covered for score-eligible recognition:

* Dynamax
* Gigantamax
* purified visual indicator
* special background/location card catalog coverage
* gender visual differences
* subtle shiny difficulty labels

## Conflicts And Gaps

* `hisuian_pokemon.json` and `paldean_pokemon.json` endpoint attempts returned 404 in this run, so regional coverage must be sourced from Game Master/project assets rather than assumed PoGoAPI endpoints.
* PokeMiners asset coverage is indexed and the full indexed `Images/Pokemon - 256x256` cache was downloaded during the follow-up validation: 5,840 / 5,840 files cached and image-decodable.
* Existing project data is broad, but not fully cross-reconciled with PoGoAPI and Game Master in this first pass.

## Readiness

NOT READY. Research and source inventory exist and the indexed sprite cache is complete, but full source reconciliation and labeled visual validation remain incomplete. Dynamax, Gigantamax, purified visual indicator, special background/location card, and gender-difference coverage still need current source reconciliation and labeled screenshots before score-eligible runtime claims.
