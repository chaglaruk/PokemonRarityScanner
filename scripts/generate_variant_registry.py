import argparse
import json
import os
import re
from collections import defaultdict
from datetime import datetime, timezone


DEFAULT_OUTPUT = os.path.join(
    "app",
    "src",
    "main",
    "assets",
    "data",
    "variant_registry.json",
)


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    return {idx + 1: name for idx, name in enumerate(names)}


def load_game_master_costume_species(path):
    if not path:
        return set()
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict) and "species" in data:
        return {str(x).strip() for x in data.get("species", []) if str(x).strip()}
    return set()


def iter_sprite_files(root_dir):
    for root, _, files in os.walk(root_dir):
        for name in files:
            lower = name.lower()
            if not lower.endswith(".png"):
                continue
            if "pokemon_icon_" not in lower:
                continue
            if "shiny" in lower or "shadow" in lower:
                continue
            yield os.path.join(root, name)


def parse_sprite_key(filename):
    base = os.path.basename(filename)
    m = re.search(r"pokemon_icon_(\d+)_([0-9]+)(?:_([0-9]+))?", base)
    if not m:
        return None
    dex_id = int(m.group(1))
    form_id = m.group(2)
    variant_id = m.group(3)
    key = f"{dex_id:03d}_{form_id}" if variant_id is None else f"{dex_id:03d}_{form_id}_{variant_id}"
    return dex_id, form_id, variant_id, key


def build_registry(assets_dir, species_map, gm_species):
    grouped = defaultdict(lambda: {"dex": None, "species": "", "base_keys": set(), "variant_keys": set(), "forms": set()})
    for path in iter_sprite_files(assets_dir):
        parsed = parse_sprite_key(path)
        if not parsed:
            continue
        dex_id, form_id, variant_id, key = parsed
        species = species_map.get(dex_id, f"#{dex_id}")
        item = grouped[species]
        item["dex"] = dex_id
        item["species"] = species
        item["forms"].add(form_id)
        if variant_id is None:
            item["base_keys"].add(key)
        else:
            item["variant_keys"].add(key)

    entries = []
    for species, item in grouped.items():
        if not item["variant_keys"]:
            continue
        entries.append(
            {
                "dex": item["dex"],
                "species": species,
                "forms": sorted(item["forms"]),
                "baseKeys": sorted(item["base_keys"]),
                "variantKeys": sorted(item["variant_keys"]),
                "variantCount": len(item["variant_keys"]),
                "hasGameMasterCostume": species in gm_species,
            }
        )

    entries.sort(key=lambda entry: entry["dex"])
    return {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": os.path.abspath(assets_dir),
        "count": len(entries),
        "speciesCount": len(entries),
        "costumeLikeSpecies": [entry["species"] for entry in entries],
        "entries": entries,
    }


def main():
    parser = argparse.ArgumentParser(description="Generate asset-backed variant registry from local sprite assets.")
    parser.add_argument("--assets-dir", required=True, help="Path to sprite asset directory")
    parser.add_argument(
        "--species-map",
        default=os.path.join("app", "src", "main", "assets", "data", "pokemon_names.json"),
        help="Path to pokemon_names.json",
    )
    parser.add_argument(
        "--gm-costume-keys",
        default=os.path.join("scripts", "costume_keys.json"),
        help="Optional costume_keys.json path for GM-backed species hints",
    )
    parser.add_argument("--out", default=DEFAULT_OUTPUT, help="Output JSON path")
    args = parser.parse_args()

    species_map = load_species_map(args.species_map)
    gm_species = load_game_master_costume_species(args.gm_costume_keys)
    payload = build_registry(args.assets_dir, species_map, gm_species)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {payload['count']} variant species to {args.out}")


if __name__ == "__main__":
    main()
