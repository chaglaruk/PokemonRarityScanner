import argparse
import json
import os
import re
from datetime import datetime, timezone


def normalize_name(name):
    name = str(name or "").strip()
    if not name:
        return ""
    upper = name.upper()
    upper = upper.replace("♀", " FEMALE").replace("♂", " MALE")
    upper = upper.replace("'", "").replace(".", "")
    upper = upper.replace("-", " ").replace(":", " ")
    upper = " ".join(upper.split()).replace(" ", "_")
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
    with open(path, "r", encoding="utf-8") as handle:
        names = json.load(handle)
    preferred_names = {
        normalize_name(name): name
        for name in names
        if normalize_name(name)
    }

    if gm_path and os.path.exists(gm_path):
        with open(gm_path, "r", encoding="utf-8") as handle:
            payload = json.load(handle)
        items = payload.get("itemTemplates") if isinstance(payload, dict) else payload
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
            normalized = normalize_name(raw_name)
            if not normalized:
                continue
            name_to_dex[normalized] = dex
            dex_to_name[dex] = display_name_from_token(normalized, preferred_names)
        if name_to_dex and dex_to_name:
            return name_to_dex, dex_to_name

    name_to_dex = {}
    dex_to_name = {}
    for index, name in enumerate(names):
        dex = index + 1
        name_to_dex[normalize_name(name)] = dex
        dex_to_name[dex] = name
    return name_to_dex, dex_to_name


def is_non_normal_form(value):
    form = str(value or "").strip()
    if not form:
        return False
    if form == "FORM_UNSET":
        return False
    if form.isdigit():
        return False
    return not form.endswith("_NORMAL")


def guess_variant_kind(form_name):
    token = str(form_name or "").upper()
    costume_keywords = (
        "HOLIDAY",
        "WINTER",
        "HALLOWEEN",
        "GOFEST",
        "GOTOUR",
        "WCS",
        "FASHION",
        "ANNIV",
        "ANNIVERSARY",
        "COSTUME",
        "HAT",
        "CROWN",
        "FLOWER",
        "BOW",
        "TIARA",
        "MONOCLE",
        "GOGGLES",
        "DOCTOR",
        "KARIYUSHI",
        "JEJU",
        "KURTA",
        "DIWALI",
        "HORIZONS",
        "COPY",
        "PARTY",
        "WITCH",
        "FLYING",
        "POP_STAR",
        "ROCK_STAR",
        "TSHIRT",
        "NOEVOLVE",
    )
    special_form_keywords = (
        "ALOLA",
        "GALARIAN",
        "HISUI",
        "PALDEA",
        "MEGA",
        "GIGANTAMAX",
        "ORIGIN",
        "ALTERED",
        "THERIAN",
        "SKY",
    )
    if any(keyword in token for keyword in special_form_keywords):
        return "form"
    if any(keyword in token for keyword in costume_keywords):
        return "costume"
    return "unknown"


def extract_variant_forms(gm_path, name_to_dex, dex_to_name):
    with open(gm_path, "r", encoding="utf-8") as handle:
        payload = json.load(handle)

    items = payload.get("itemTemplates") if isinstance(payload, dict) else payload
    if not isinstance(items, list):
        raise ValueError("Unsupported Game Master format")

    rows = []
    seen = set()

    for item in items:
        if not isinstance(item, dict):
            continue
        settings = item.get("data", {}).get("pokemonSettings")
        if not settings:
            continue
        pokemon_id = settings.get("pokemonId")
        form = settings.get("form")
        if not pokemon_id or not is_non_normal_form(form):
            continue
        dex = name_to_dex.get(normalize_name(pokemon_id))
        if not dex:
            continue
        key = (dex, str(form))
        if key in seen:
            continue
        seen.add(key)
        rows.append(
            {
                "dex": dex,
                "species": dex_to_name.get(dex, f"#{dex}"),
                "form": str(form),
                "templateId": item.get("templateId"),
                "kindGuess": guess_variant_kind(form),
            }
        )

    rows.sort(key=lambda entry: (entry["dex"], entry["form"]))
    species = sorted({entry["species"] for entry in rows})
    return rows, species


def main():
    parser = argparse.ArgumentParser(description="Extract all non-normal Pokemon GO forms from Game Master.")
    parser.add_argument("--gm", default=os.path.join("external", "game_masters", "latest", "latest.json"))
    parser.add_argument(
        "--species-map",
        default=os.path.join("app", "src", "main", "assets", "data", "pokemon_names.json"),
    )
    parser.add_argument(
        "--out",
        default=os.path.join("scripts", "game_master_variant_forms.json"),
    )
    args = parser.parse_args()

    name_to_dex, dex_to_name = load_species_map(args.species_map, args.gm)
    rows, species = extract_variant_forms(args.gm, name_to_dex, dex_to_name)
    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(rows),
        "speciesCount": len(species),
        "species": species,
        "forms": rows,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"Wrote {len(rows)} non-normal forms to {args.out}")


if __name__ == "__main__":
    main()
