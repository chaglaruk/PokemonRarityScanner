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


def display_name_from_token(token, preferred_names):
    normalized = normalize_name(token)
    if normalized in preferred_names:
        return preferred_names[normalized]
    special = {
        "MR_MIME": "Mr. Mime",
        "MIME_JR": "Mime Jr.",
        "MR_RIME": "Mr. Rime",
        "NIDORAN_FEMALE": "Nidoran♀",
        "NIDORAN_MALE": "Nidoran♂",
        "FARFETCHD": "Farfetch'd",
        "SIRFETCHD": "Sirfetch'd",
        "HO_OH": "Ho-Oh",
        "PORYGON_Z": "Porygon-Z",
        "TYPE_NULL": "Type: Null",
        "TAPU_KOKO": "Tapu Koko",
        "TAPU_LELE": "Tapu Lele",
        "TAPU_BULU": "Tapu Bulu",
        "TAPU_FINI": "Tapu Fini",
        "GREAT_TUSK": "Great Tusk",
        "SCREAM_TAIL": "Scream Tail",
        "BRUTE_BONNET": "Brute Bonnet",
        "FLUTTER_MANE": "Flutter Mane",
        "SLITHER_WING": "Slither Wing",
        "SANDY_SHOCKS": "Sandy Shocks",
        "IRON_TREADS": "Iron Treads",
        "IRON_BUNDLE": "Iron Bundle",
        "IRON_HANDS": "Iron Hands",
        "IRON_JUGULIS": "Iron Jugulis",
        "IRON_MOTH": "Iron Moth",
        "IRON_THORNS": "Iron Thorns",
    }
    if normalized in special:
        return special[normalized]
    return " ".join(part.capitalize() for part in normalized.split("_"))


def load_species_map(path, gm_path=None):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    preferred_names = {
        normalize_name(name): name
        for name in names
        if normalize_name(name)
    }

    if gm_path and os.path.exists(gm_path):
        with open(gm_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        items = data.get("itemTemplates") if isinstance(data, dict) else data
        name_to_dex = {}
        dex_to_name = {}
        for item in items if isinstance(items, list) else []:
            settings = item.get("data", {}).get("pokemonSettings") if isinstance(item, dict) else None
            if not isinstance(settings, dict):
                continue
            template_id = str(item.get("templateId") or "")
            match = re.match(r"^V(\d+)_POKEMON_(.+)$", template_id)
            if not match:
                continue
            dex = int(match.group(1))
            raw_name = settings.get("pokemonId") or match.group(2)
            normalized = normalize_name(str(raw_name))
            if not normalized:
                continue
            name_to_dex[normalized] = dex
            dex_to_name[dex] = display_name_from_token(normalized, preferred_names)
        if name_to_dex and dex_to_name:
            return name_to_dex, dex_to_name

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

    name_to_dex, dex_to_name = load_species_map(args.species_map, args.gm)
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
