import argparse
import json
import os
import re
from datetime import datetime, timezone


def normalize_name(name):
    name = name.strip()
    if not name:
        return ""
    upper = name.upper()
    upper = upper.replace("♀", " FEMALE").replace("♂", " MALE")
    upper = upper.replace("'", "").replace(".", "")
    upper = upper.replace("-", " ").replace(":", " ")
    upper = re.sub(r"\s+", " ", upper).strip()
    upper = upper.replace(" ", "_")

    if upper in ("NIDORAN_F", "NIDORAN_FEMALE"):
        return "NIDORAN_FEMALE"
    if upper in ("NIDORAN_M", "NIDORAN_MALE"):
        return "NIDORAN_MALE"
    return upper


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    name_to_dex = {}
    dex_to_name = {}
    for idx, name in enumerate(names):
        dex = idx + 1
        norm = normalize_name(name)
        if norm:
            name_to_dex[norm] = dex
        dex_to_name[dex] = name
    return name_to_dex, dex_to_name


def extract_costume_forms(gm_path, name_to_dex, dex_to_name):
    with open(gm_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    items = data.get("itemTemplates") if isinstance(data, dict) else data
    if not isinstance(items, list):
        raise ValueError("Unsupported GM format: expected itemTemplates array.")

    forms_out = []
    species_set = set()

    for item in items:
        if not isinstance(item, dict):
            continue
        data_obj = item.get("data") or {}
        settings = data_obj.get("formSettings") or data_obj.get("formSettingsV2")
        if not settings or not isinstance(settings, dict):
            continue
        forms = settings.get("forms")
        if not isinstance(forms, list):
            continue

        pokemon_id = settings.get("pokemon") or settings.get("pokemonId")
        if not pokemon_id:
            continue
        dex = name_to_dex.get(normalize_name(str(pokemon_id)))
        if not dex:
            continue

        for form in forms:
            if not isinstance(form, dict):
                continue
            if not form.get("isCostume", False):
                continue
            forms_out.append(
                {
                    "dex": dex,
                    "species": dex_to_name.get(dex, f"#{dex}"),
                    "form": form.get("form", ""),
                    "assetBundleValue": form.get("assetBundleValue"),
                    "assetBundleSuffix": form.get("assetBundleSuffix"),
                }
            )
            species_set.add(dex_to_name.get(dex, f"#{dex}"))

    return forms_out, sorted(species_set)


def main():
    parser = argparse.ArgumentParser(description="Extract costume form keys from Game Master JSON.")
    parser.add_argument("--gm", required=True, help="Path to PokeMiners game_masters/latest.json")
    parser.add_argument(
        "--species-map",
        default=os.path.join("app", "src", "main", "assets", "data", "pokemon_names.json"),
        help="Path to pokemon_names.json",
    )
    parser.add_argument(
        "--out",
        default=os.path.join("scripts", "costume_keys.json"),
        help="Output JSON path",
    )
    args = parser.parse_args()

    name_to_dex, dex_to_name = load_species_map(args.species_map)
    forms_out, species_list = extract_costume_forms(args.gm, name_to_dex, dex_to_name)

    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(forms_out),
        "speciesCount": len(species_list),
        "species": species_list,
        "forms": forms_out,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {len(forms_out)} costume forms to {args.out}")


if __name__ == "__main__":
    main()
