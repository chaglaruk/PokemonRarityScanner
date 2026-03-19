import argparse
import json
import re
from collections import defaultdict
from pathlib import Path


SPECIAL_NAME_MAP = {
    "mrmime": "Mr. Mime",
    "mimejr": "Mime Jr.",
    "type:null": "Type: Null",
    "typenull": "Type: Null",
    "farfetchd": "Farfetch'd",
    "sirfetchd": "Sirfetch'd",
    "nidoranf": "Nidoran-f",
    "nidoranm": "Nidoran-m",
    "hooh": "Ho-Oh",
    "porygonz": "Porygon-Z",
    "porygon2": "Porygon2",
    "jangmoo": "Jangmo-o",
    "hakamoo": "Hakamo-o",
    "kommoo": "Kommo-o",
    "wochien": "Wo-Chien",
    "chienpao": "Chien-Pao",
    "tinglu": "Ting-Lu",
    "chiyu": "Chi-Yu",
}


def normalize_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def build_name_lookup(names_path: Path) -> dict[str, str]:
    names = json.loads(names_path.read_text(encoding="utf-8"))
    lookup: dict[str, str] = {}
    for name in names:
        normalized = normalize_name(name)
        lookup[normalized] = name
    lookup.update(SPECIAL_NAME_MAP)
    return lookup


def pokemon_id_to_name(pokemon_id: str, lookup: dict[str, str]) -> str | None:
    raw = str(pokemon_id)
    candidate = raw.lower().replace("_female", "f").replace("_male", "m").replace("_", "")
    return lookup.get(candidate)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game-master", default="external/game_masters/latest/latest.json")
    parser.add_argument("--names", default="app/src/main/assets/data/pokemon_names.json")
    parser.add_argument("--output", default="app/src/main/assets/data/pokemon_families.json")
    args = parser.parse_args()

    game_master_path = Path(args.game_master)
    names_path = Path(args.names)
    output_path = Path(args.output)

    lookup = build_name_lookup(names_path)
    game_master = json.loads(game_master_path.read_text(encoding="utf-8"))

    family_members: dict[str, set[str]] = defaultdict(set)
    species_to_family: dict[str, str] = {}

    for entry in game_master:
        pokemon_settings = entry.get("data", {}).get("pokemonSettings")
        if not pokemon_settings:
            continue
        family_id = pokemon_settings.get("familyId")
        pokemon_id = pokemon_settings.get("pokemonId")
        if not family_id or not pokemon_id:
            continue

        name = pokemon_id_to_name(pokemon_id, lookup)
        if not name:
            continue

        species_to_family[name] = family_id
        family_members[family_id].add(name)

    normalized_families = {
        family_id: sorted(members)
        for family_id, members in family_members.items()
        if len(members) >= 1
    }
    output = {
        "version": 1,
        "source": str(game_master_path),
        "familyCount": len(normalized_families),
        "speciesCount": len(species_to_family),
        "speciesToFamily": dict(sorted(species_to_family.items())),
        "families": dict(sorted(normalized_families.items())),
    }
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {output_path} with {len(normalized_families)} families and {len(species_to_family)} species")


if __name__ == "__main__":
    main()
