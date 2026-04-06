import argparse
import json
import re
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
    "simispour": "Simipour",
    "centiskorchere": "Centiskorch",
}


def normalize_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def build_name_lookup(names_path: Path) -> dict[str, str]:
    names = json.loads(names_path.read_text(encoding="utf-8"))
    lookup: dict[str, str] = {}
    for name in names:
        lookup[normalize_name(name)] = name
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
    parser.add_argument("--output", default="app/src/main/assets/data/pokemon_base_stats.json")
    args = parser.parse_args()

    game_master_path = Path(args.game_master)
    names_path = Path(args.names)
    output_path = Path(args.output)

    lookup = build_name_lookup(names_path)
    game_master = json.loads(game_master_path.read_text(encoding="utf-8"))

    stats: dict[str, dict[str, int]] = {}
    for entry in game_master:
        pokemon_settings = entry.get("data", {}).get("pokemonSettings")
        if not pokemon_settings:
            continue
        pokemon_id = pokemon_settings.get("pokemonId")
        stat_block = pokemon_settings.get("stats")
        if not pokemon_id or not stat_block:
            continue

        species_name = pokemon_id_to_name(pokemon_id, lookup)
        if not species_name or species_name in stats:
            continue

        base_attack = stat_block.get("baseAttack")
        base_defense = stat_block.get("baseDefense")
        base_stamina = stat_block.get("baseStamina")
        if None in (base_attack, base_defense, base_stamina):
            continue

        stats[species_name] = {
            "atk": int(base_attack),
            "def": int(base_defense),
            "sta": int(base_stamina),
            "heightM": float(pokemon_settings.get("pokedexHeightM") or 0.0),
            "weightKg": float(pokemon_settings.get("pokedexWeightKg") or 0.0),
        }

    output_path.write_text(
        json.dumps(dict(sorted(stats.items())), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Wrote {output_path} with {len(stats)} species")


if __name__ == "__main__":
    main()
