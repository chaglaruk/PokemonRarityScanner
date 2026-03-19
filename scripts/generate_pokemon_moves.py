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


def normalize_move(value: str) -> str:
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


def pretty_move_name(move_id: str) -> str:
    return move_id.lower().replace("_", " ")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game-master", default="external/game_masters/latest/latest.json")
    parser.add_argument("--names", default="app/src/main/assets/data/pokemon_names.json")
    parser.add_argument("--output", default="app/src/main/assets/data/pokemon_moves.json")
    args = parser.parse_args()

    game_master_path = Path(args.game_master)
    names_path = Path(args.names)
    output_path = Path(args.output)

    lookup = build_name_lookup(names_path)
    game_master = json.loads(game_master_path.read_text(encoding="utf-8"))

    species_to_moves: dict[str, set[str]] = defaultdict(set)
    move_to_species: dict[str, set[str]] = defaultdict(set)

    for entry in game_master:
        pokemon_settings = entry.get("data", {}).get("pokemonSettings")
        if not pokemon_settings:
            continue

        pokemon_id = pokemon_settings.get("pokemonId")
        species_name = pokemon_id_to_name(pokemon_id, lookup) if pokemon_id else None
        if not species_name:
            continue

        for move_id in pokemon_settings.get("quickMoves", []) + pokemon_settings.get("cinematicMoves", []):
            pretty = pretty_move_name(str(move_id))
            normalized = normalize_move(pretty)
            if len(normalized) < 4:
                continue
            species_to_moves[species_name].add(pretty)
            move_to_species[normalized].add(species_name)

    payload = {
        "version": 1,
        "source": str(game_master_path),
        "speciesCount": len(species_to_moves),
        "moveCount": len(move_to_species),
        "speciesToMoves": {
            species: sorted(moves)
            for species, moves in sorted(species_to_moves.items())
        },
        "moveToSpecies": {
            move: sorted(species)
            for move, species in sorted(move_to_species.items())
        },
    }
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        f"Wrote {output_path} with {len(species_to_moves)} species and {len(move_to_species)} normalized moves"
    )


if __name__ == "__main__":
    main()
