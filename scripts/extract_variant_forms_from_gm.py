import argparse
import json
import os
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


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as handle:
        names = json.load(handle)
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

    name_to_dex, dex_to_name = load_species_map(args.species_map)
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
