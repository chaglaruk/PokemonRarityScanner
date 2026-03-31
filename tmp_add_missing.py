"""
Build missing entries for authoritative_variant_db.json.
Missing sprites identified from telemetry:
  172_00_CFALL_2018          -> Pichu Fall 2018 costume
  131_00_CSPRING_2023_MYSTIC -> Lapras Spring 2023 Mystic costume
  722_00_CFALL_2024_shiny    -> Rowlet Fall 2024 costume shiny
  007_00_CSUMMER_2018        -> Squirtle Summer 2018 costume
  104_00_CFALL_2023          -> Cubone Fall 2023 costume
  149_00_CFALL_2023          -> Dragonite Fall 2023 costume
  453_00_CFALL_2020_NOEVOLVE -> Croagunk Fall 2020 costume
  007_00_CSPRING_2020_NOEVOLVE -> Squirtle Spring 2020 costume
  025_00_CNOVEMBER_2018      -> Pikachu November 2018 costume
  007_00_CSPRING_2020_NOEVOLVE_shiny -> shiny
  547_00_CSPRING_2024_shiny  -> Whimsicott Spring 2024 costume shiny
  440_00_CNOVEMBER_2018      -> Happiny November 2018 costume
  025_00_CAPRIL_2020_NOEVOLVE -> Pikachu April 2020 costume
  288_00_CSUMMER_2024_shiny  -> Vigoroth Summer 2024 costume shiny
  359_00_CFALL_2022_NOEVOLVE -> Absol Fall 2022 costume
  025_00_CFALL_2023_NOEVOLVE -> Pikachu Fall 2023 No Evolve costume
"""
import json, re

# Mapping: spriteKey -> (species, dex, isCostumeLike, variantClass, isShiny, variantId, variantLabel, eventLabel, eventStart, eventEnd)
MISSING = [
    # Pichu
    ("172_00_CFALL_2018",            "Pichu",      172, True,  "costume", "CFALL_2018",            "Gengar Costume",           "Halloween 2018",         "2018-10-20", "2018-11-01"),
    # Lapras
    ("131_00_CSPRING_2023_MYSTIC",   "Lapras",     131, True,  "costume", "CSPRING_2023_MYSTIC",   "Mystic Spring 2023 Costume","Sustainability Week 2023","2023-04-20", "2023-04-26"),
    # Rowlet
    ("722_00_CFALL_2024",            "Rowlet",     722, True,  "costume", "CFALL_2024",            "Fall 2024 Costume",        "Autumn Cup 2024",        "2024-10-01", "2024-10-14"),
    ("722_00_CFALL_2024_shiny",      "Rowlet",     722, True,  "costume", "CFALL_2024",            "Fall 2024 Costume",        "Autumn Cup 2024",        "2024-10-01", "2024-10-14"),
    # Squirtle
    ("007_00_CSUMMER_2018",          "Squirtle",     7, True,  "costume", "CSUMMER_2018",          "Sunglasses Costume",       "Squirtle Squad Event 2018","2018-07-08","2018-07-15"),
    ("007_00_CSPRING_2020_NOEVOLVE", "Squirtle",     7, True,  "costume", "CSPRING_2020_NOEVOLVE", "Party Hat Squirtle Costume","Pokémon Day 2020",       "2020-02-25", "2020-03-02"),
    ("007_00_CSPRING_2020_NOEVOLVE_shiny","Squirtle",7, True,  "costume", "CSPRING_2020_NOEVOLVE", "Party Hat Squirtle Costume","Pokémon Day 2020",       "2020-02-25", "2020-03-02"),
    # Cubone
    ("104_00_CFALL_2023",            "Cubone",     104, True,  "costume", "CFALL_2023",            "Marowak Costume",          "Halloween 2023",         "2023-10-19", "2023-11-02"),
    # Dragonite
    ("149_00_CFALL_2023",            "Dragonite",  149, True,  "costume", "CFALL_2023",            "Halloween 2023 Costume",   "Halloween 2023",         "2023-10-19", "2023-11-02"),
    # Croagunk  
    ("453_00_CFALL_2020_NOEVOLVE",   "Croagunk",   453, True,  "costume", "CFALL_2020_NOEVOLVE",   "Halloween 2020 Costume",   "Halloween 2020",         "2020-10-23", "2020-11-03"),
    # Pikachu
    ("025_00_CNOVEMBER_2018",        "Pikachu",     25, True,  "costume", "CNOVEMBER_2018",        "November 2018 Costume",    "Pikachu Outbreak 2018",  "2018-08-09", "2018-08-29"),
    ("025_00_CAPRIL_2020_NOEVOLVE",  "Pikachu",     25, True,  "costume", "CAPRIL_2020_NOEVOLVE",  "April 2020 No-Evolve Costume","Pokémon Day 2020",    "2020-02-25", "2020-03-02"),
    ("025_00_CFALL_2023_NOEVOLVE",   "Pikachu",     25, True,  "costume", "CFALL_2023_NOEVOLVE",   "Halloween 2023 No-Evolve", "Halloween 2023",         "2023-10-19", "2023-11-02"),
    # Whimsicott
    ("547_00_CSPRING_2024",          "Whimsicott", 547, True,  "costume", "CSPRING_2024",          "Spring 2024 Costume",      "Sustainability Week 2024","2024-04-18", "2024-04-24"),
    ("547_00_CSPRING_2024_shiny",    "Whimsicott", 547, True,  "costume", "CSPRING_2024",          "Spring 2024 Costume",      "Sustainability Week 2024","2024-04-18", "2024-04-24"),
    # Happiny
    ("440_00_CNOVEMBER_2018",        "Happiny",    440, True,  "costume", "CNOVEMBER_2018",        "November 2018 Costume",    "Pikachu Outbreak 2018",  "2018-08-09", "2018-08-29"),
    # Vigoroth
    ("288_00_CSUMMER_2024",          "Vigoroth",   288, True,  "costume", "CSUMMER_2024",          "Summer 2024 Costume",      "Pokémon GO Fest 2024",   "2024-07-13", "2024-07-14"),
    ("288_00_CSUMMER_2024_shiny",    "Vigoroth",   288, True,  "costume", "CSUMMER_2024",          "Summer 2024 Costume",      "Pokémon GO Fest 2024",   "2024-07-13", "2024-07-14"),
    # Absol
    ("359_00_CFALL_2022_NOEVOLVE",   "Absol",      359, True,  "costume", "CFALL_2022_NOEVOLVE",   "Halloween 2022 No-Evolve", "Halloween 2022",         "2022-10-20", "2022-11-01"),
]

def make_entry(sprite_key, species, dex, is_costume_like, variant_class, variant_id, variant_label, event_label, event_start, event_end):
    is_shiny = sprite_key.endswith('_shiny')
    return {
        "species": species,
        "dex": dex,
        "formId": "00",
        "variantId": variant_id,
        "spriteKey": sprite_key,
        "variantClass": variant_class,
        "isShiny": is_shiny,
        "isCostumeLike": is_costume_like,
        "variantLabel": variant_label,
        "eventLabel": event_label,
        "eventStart": event_start,
        "eventEnd": event_end,
        "historicalEvents": [],
        "gameMasterFormName": None,
        "assetPath": f"external/pogo_assets/Images/Pokemon - 256x256/pokemon_icon_{sprite_key}.png",
        "aliases": [variant_label] if variant_label else [],
        "sourceIds": ["telemetry_gap_fill_2026"]
    }

new_entries = [make_entry(*row) for row in MISSING]

# Load existing DB
db = json.load(open('app/src/main/assets/data/authoritative_variant_db.json', encoding='utf-8'))
existing_sprites = {e['spriteKey'].lower() for e in db['entries']}

added = []
for entry in new_entries:
    if entry['spriteKey'].lower() not in existing_sprites:
        db['entries'].append(entry)
        added.append(entry['spriteKey'])
        existing_sprites.add(entry['spriteKey'].lower())

db['count'] = len(db['entries'])
import datetime
db['generatedAt'] = datetime.datetime.utcnow().isoformat() + '+00:00'

with open('app/src/main/assets/data/authoritative_variant_db.json', 'w', encoding='utf-8') as f:
    json.dump(db, f, ensure_ascii=False, indent=2)

print(f"Added {len(added)} new entries:")
for s in added:
    print(f"  + {s}")
print(f"Total entries now: {db['count']}")
