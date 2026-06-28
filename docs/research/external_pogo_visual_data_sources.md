# External Pokemon GO Visual Data Sources

Date: 2026-06-27

## Primary Machine-Readable Sources

* PoGoAPI: `https://pogoapi.net/api/v1/`
  * Used endpoints include `released_pokemon.json`, `pokemon_forms.json`, `shiny_pokemon.json`, `shadow_pokemon.json`, `mega_pokemon.json`, `pokemon_stats.json`, and `api_hashes.json`.
  * Source hashes are recorded in `build/reports/pogo_reference/metadata_fetch_report.csv`.
* PokeMiners Game Master: `https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json`
  * Downloaded to `.local/pogo_reference_cache/metadata/pokeminers_latest_game_master.json`.
  * SHA-256 in this run: `136401ba7186599f5e61122b5a382a3ac62f432d9d8f542152da1db14ef24ab7`.
* PokeMiners pogo_assets: `https://github.com/PokeMiners/pogo_assets`
  * Indexed through the GitHub tree API.
  * Raw sprites are cached only under `.local/pogo_reference_cache/`.
  * Raw sprites are not committed.

## Manual Cross-Check Sources

* Pokemon GO Live News: `https://pokemongolive.com/news`
* Pokemon GO Max and Gigantamax official news/category pages: `https://pokemongolive.com/`
* Pokemon GO Location Cards and Special Background official news/category pages: `https://pokemongolive.com/`
* Leek Duck Events: `https://leekduck.com/events/`
* Leek Duck current and historical costume/event reference pages: `https://leekduck.com/`
* Bulbapedia Event Pokemon GO: `https://bulbapedia.bulbagarden.net/wiki/Event_Pokemon_(GO)`
* Bulbapedia Pokemon GO gameplay/state references for Lucky and Purified Pokemon: `https://bulbapedia.bulbagarden.net/wiki/Pokemon_GO`
* Serebii Pokemon GO events: `https://www.serebii.net/pokemongo/events.shtml`

## Legal And Runtime Risk

Raw external sprites are copyrighted third-party assets. They are safe only as local research cache in this workflow. Generated descriptor data is kept under `build/reports/pogo_reference/` in this sprint and is not committed into app runtime assets until legal and quality review are complete.

The existing packaged runtime descriptor model in `app/src/main/assets/data/variant_classifier_model.json` is also external-sprite-derived and must be treated as a release legal/product review item.
