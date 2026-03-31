# Global Rarity & Legacy Engine Spec

## Scope
- Build an offline authoritative metadata layer for:
  - species
  - shiny release state
  - costume/form event history
  - live availability hints
- Do not scrape at app runtime.
- Do not replace the full matcher. This engine enriches it.

## Accepted Parts From The Emini Prompt
- Historical archive sources are useful.
- Live availability sources are useful.
- A normalized relational mapping of base/form/shiny/costume is useful.
- Narrative collector output is useful.

## Rejected Parts
- `Antigravity` is not used.
- Runtime autonomous scraping inside the Android app is not used.
- Hard-coding a full rarity formula into the scan engine is premature while event/history metadata is still incomplete.

## Source Trust Order
1. `pokemongolive:news`
2. `bulbapedia:event-pokemon-go`
3. `serebii:pokemongo-events`
4. `pokeminers:pogo_assets`
5. `leekduck:events`

## Data Model
- `global_rarity_legacy_db.json` stores one row per sprite/variant.
- Required fields:
  - identity: species, dex, formId, variantId, spriteKey
  - semantics: variantClass, isShiny, isCostumeLike
  - historical: eventLabel, eventStart, eventEnd, firstSeen, lastSeen, lastKnownEvent
  - live: liveAvailability
  - shiny: shinyAvailability, shinyOddsBucket
  - provenance: sourceIds, sourceNames, aliases

## Integration Rules
- Scan/full matcher remains the recognition layer.
- Rarity/explanation consumes global legacy DB as historical context.
- Exact event naming still requires a species-safe variant match.
- Live availability must never override historical identity.

## Scheduling
- Historical merge: monthly
- Live delta sync: daily at `04:00`

## First Delivery
- Source registry
- Global legacy DB generator
- Runtime loader
- Initial app asset generated from existing authoritative DB + overrides
