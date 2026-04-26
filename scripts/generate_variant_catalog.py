import json
import os
import re
from collections import defaultdict
from datetime import datetime, timezone


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_ROOT = os.path.join(ROOT, "external", "pogo_assets", "Images", "Pokemon - 256x256")
GAME_MASTER_COSTUMES = os.path.join(ROOT, "scripts", "costume_keys.json")
GAME_MASTER_VARIANT_FORMS = os.path.join(ROOT, "scripts", "game_master_variant_forms.json")
VARIANT_TOKEN_ALIASES = os.path.join(ROOT, "scripts", "variant_token_aliases.json")
EVENT_HISTORY = os.path.join(ROOT, "app", "src", "main", "assets", "data", "event_history.json")
COSTUME_SPECIES = os.path.join(ROOT, "app", "src", "main", "assets", "data", "costume_species.json")
POKEMON_NAMES = os.path.join(ROOT, "app", "src", "main", "assets", "data", "pokemon_names.json")
POKEMON_FAMILIES = os.path.join(ROOT, "app", "src", "main", "assets", "data", "pokemon_families.json")
GAME_MASTER_LATEST = os.path.join(ROOT, "external", "game_masters", "latest", "latest.json")
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "data", "variant_catalog.json")


SPRITE_RE = re.compile(r"pokemon_icon_(\d+)_([0-9]+)(?:_([0-9]+))?(_shiny)?\.png$", re.IGNORECASE)
ALT_SPRITE_RE = re.compile(r"pokemon_icon_pm(\d+)_([0-9]+)_([a-z0-9_]+?)(_shiny)?\.png$", re.IGNORECASE)
ADDRESSABLE_BASE_RE = re.compile(r"pm(\d+)(?:\.(s))?\.icon\.png$", re.IGNORECASE)
ADDRESSABLE_VARIANT_RE = re.compile(r"pm(\d+)\.([a-z0-9_]+)\.(s)\.icon\.png$|pm(\d+)\.([a-z0-9_]+)\.icon\.png$", re.IGNORECASE)


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_species_map():
    names = load_json(POKEMON_NAMES)
    preferred_names = {
        normalize_species_name(name): name
        for name in names
        if normalize_species_name(name)
    }
    gm_species_map = load_game_master_species_map(preferred_names)
    if gm_species_map:
        return gm_species_map
    return {idx + 1: name for idx, name in enumerate(names)}


def normalize_species_name(name):
    upper = str(name or "").strip().upper()
    upper = upper.replace("♀", " FEMALE").replace("♂", " MALE")
    upper = upper.replace("'", "").replace(".", "")
    upper = upper.replace("-", " ").replace(":", " ")
    upper = re.sub(r"\s+", " ", upper).strip().replace(" ", "_")
    if upper in ("NIDORAN_F", "NIDORAN_FEMALE"):
        return "NIDORAN_FEMALE"
    if upper in ("NIDORAN_M", "NIDORAN_MALE"):
        return "NIDORAN_MALE"
    return upper


def display_name_from_token(token, preferred_names):
    normalized = normalize_species_name(token)
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


def load_game_master_species_map(preferred_names):
    if not os.path.exists(GAME_MASTER_LATEST):
        return None
    payload = load_json(GAME_MASTER_LATEST)
    items = payload.get("itemTemplates") if isinstance(payload, dict) else payload
    if not isinstance(items, list):
        return None
    dex_to_name = {}
    for item in items:
        if not isinstance(item, dict):
            continue
        settings = item.get("data", {}).get("pokemonSettings")
        if not isinstance(settings, dict):
            continue
        template_id = str(item.get("templateId") or "")
        match = re.match(r"^V(\d+)_POKEMON_(.+)$", template_id)
        if not match:
            continue
        dex = int(match.group(1))
        raw_name = settings.get("pokemonId") or match.group(2)
        dex_to_name[dex] = display_name_from_token(raw_name, preferred_names)
    return dex_to_name or None


def load_costume_species():
    payload = load_json(COSTUME_SPECIES)
    if isinstance(payload, list):
        return set(payload)
    if isinstance(payload, dict):
        species = payload.get("species") or payload.get("costumeLikeSpecies") or []
        return set(species)
    return set()


def load_game_master_costumes():
    payload = load_json(GAME_MASTER_COSTUMES)
    forms = payload.get("forms", [])
    by_species = defaultdict(list)
    for item in forms:
        species = item.get("species")
        form_name = item.get("form")
        if species and form_name:
            by_species[species].append(str(form_name))
    return {k: [str(x) for x in v] for k, v in by_species.items()}


def load_game_master_variant_forms():
    if not os.path.exists(GAME_MASTER_VARIANT_FORMS):
        return {}
    payload = load_json(GAME_MASTER_VARIANT_FORMS)
    by_species = defaultdict(list)
    for item in payload.get("forms", []):
        species = item.get("species")
        form_name = item.get("form")
        if species and form_name:
            by_species[species].append(str(form_name))
    return {k: [str(x) for x in v] for k, v in by_species.items()}


def load_variant_token_aliases():
    if not os.path.exists(VARIANT_TOKEN_ALIASES):
        return {}
    payload = load_json(VARIANT_TOKEN_ALIASES)
    return payload.get("globalTokenAliases", {})


def load_species_families():
    payload = load_json(POKEMON_FAMILIES)
    species_to_family = payload.get("speciesToFamily", {})
    family_to_species = defaultdict(list)
    for species, family in species_to_family.items():
        family_to_species[family].append(species)
    return species_to_family, dict(family_to_species)


def load_event_history():
    payload = load_json(EVENT_HISTORY)
    by_species = defaultdict(list)
    for event in payload:
        name = event.get("eventName")
        start = event.get("startDate")
        end = event.get("endDate")
        for species in event.get("pokemonIds", []):
            by_species[species].append(
                {
                    "name": name,
                    "startDate": start,
                    "endDate": end,
                }
            )
    return dict(by_species)


def normalize_variant_token(value):
    if not value:
        return None
    normalized = str(value).strip().upper()
    normalized = re.sub(r"([A-Z]+)(20\d{2})", r"\1_\2", normalized)
    normalized = re.sub(r"_+", "_", normalized).strip("_")
    if should_strip_leading_costume_marker(normalized):
        normalized = normalized[1:]
    return normalized or None


def normalize_form_token(value, species=None):
    if not value:
        return None
    token = str(value).strip().upper()
    if species:
        prefix = f"{str(species).strip().upper()}_"
        if token.startswith(prefix):
            token = token[len(prefix):]
    token = normalize_variant_token(token)
    return token


def canonical_variant_token(value):
    token = normalize_variant_token(value)
    if not token:
        return None
    replacements = (
        ("POKEMON_GO_", ""),
        ("PGO_", ""),
        ("_NOEVOLVE", ""),
    )
    for old, new in replacements:
        token = token.replace(old, new)
    while token.startswith("F") and len(token) > 2 and token[1].isalpha():
        stripped = token[1:]
        if any(
            stripped.startswith(prefix)
            for prefix in (
                "WINTER_",
                "HOLIDAY_",
                "HALLOWEEN_",
                "GOFEST_",
                "GOTOUR_",
                "WCS_",
                "FALL_",
                "SUMMER_",
                "SPRING_",
                "DOCTOR",
                "DIWALI_",
                "HORIZONS",
                "JEJU",
                "KARIYUSHI",
                "KURTA",
                "FLYING_",
                "COPY_",
                "COSTUME_",
                "TSHIRT_",
                "POP_STAR",
                "ROCK_STAR",
                "WILDAREA_",
                "NEWYEAR",
                "ANNIVERSARY",
                "ANNIV",
            )
        ):
            token = stripped
        else:
            break
    return token


def should_strip_leading_costume_marker(value):
    if not value or not value.startswith("C") or "_" not in value or len(value) <= 1:
        return False
    suffix = value[1:]
    known_prefixes = (
        "JAN_",
        "FEB_",
        "MAR_",
        "APR_",
        "MAY_",
        "JUN_",
        "JUL_",
        "AUG_",
        "SEP_",
        "OCT_",
        "NOV_",
        "DEC_",
        "HOLIDAY",
        "WINTER",
        "HALLOWEEN",
        "FASHION",
        "ANNIV",
        "NEWYEAR",
        "GOFEST",
        "GOTOUR",
        "SPRING",
        "SUMMER",
        "FALL",
        "APRIL",
        "NOVEMBER",
        "HOENN",
        "JOHTO",
        "KANTO",
        "SAFARI",
        "SINNOH",
        "TCG",
        "INDONESIA",
        "WILDAREA",
    )
    return any(suffix.startswith(prefix) for prefix in known_prefixes)


def parse_sprite_file_name(file_name):
    match = SPRITE_RE.match(file_name)
    if match:
        return {
            "dex": int(match.group(1)),
            "form_id": match.group(2),
            "variant_id": match.group(3),
            "is_shiny": bool(match.group(4)),
        }

    match = ALT_SPRITE_RE.match(file_name)
    if match:
        return {
            "dex": int(match.group(1)),
            "form_id": match.group(2),
            "variant_id": normalize_variant_token(match.group(3)),
            "is_shiny": bool(match.group(4)),
        }

    match = ADDRESSABLE_BASE_RE.match(file_name)
    if match:
        return {
            "dex": int(match.group(1)),
            "form_id": "00",
            "variant_id": None,
            "is_shiny": bool(match.group(2)),
        }

    match = ADDRESSABLE_VARIANT_RE.match(file_name)
    if match:
        dex = int(match.group(1) or match.group(4))
        token = match.group(2) or match.group(5)
        is_shiny = bool(match.group(3))
        return {
            "dex": dex,
            "form_id": "00",
            "variant_id": normalize_variant_token(token),
            "is_shiny": is_shiny,
        }

    return None


def iter_sprite_files():
    for root, _, files in os.walk(ASSET_ROOT):
        for file_name in files:
            parsed = parse_sprite_file_name(file_name)
            if parsed:
                yield os.path.join(root, file_name), parsed


def looks_like_costume_variant(variant_id):
    if not variant_id:
        return False
    token = str(variant_id).upper()
    if looks_like_special_form_variant(token):
        return False
    costume_keywords = (
        "HOLIDAY",
        "WINTER",
        "FASHION",
        "HALLOWEEN",
        "ANNIV",
        "GOFEST",
        "TOUR",
        "JAN_",
        "FEB_",
        "MAR_",
        "APR_",
        "MAY_",
        "JUN_",
        "JUL_",
        "AUG_",
        "SEP_",
        "OCT_",
        "NOV_",
        "DEC_",
        "NOEVOLVE",
        "TIARA",
        "HAT",
        "CROWN",
        "BOW",
        "FLOWER",
        "PGO_",
    )
    return any(keyword in token for keyword in costume_keywords)


def looks_like_special_form_variant(variant_id):
    if not variant_id:
        return False
    token = str(variant_id).upper()
    form_keywords = (
        "MEGA",
        "GIGANTAMAX",
        "ALOLA",
        "GALAR",
        "HISUI",
        "PALDEA",
        "THERIAN",
        "ORIGIN",
        "ALTERED",
        "SKY",
    )
    return any(keyword in token for keyword in form_keywords)


def determine_variant_class(species, form_id, variant_id, costume_species, gm_costume_forms, species_with_variant_id_costumes):
    has_non_base_variant = bool(variant_id) or form_id != "00"
    species_has_costumes = species in costume_species or species in gm_costume_forms
    if variant_id and looks_like_special_form_variant(variant_id):
        return "form"
    if variant_id and species_has_costumes:
        return "costume"
    if variant_id and looks_like_costume_variant(variant_id):
        return "costume"
    if (
        form_id != "00" and
        not variant_id and
        species_has_costumes and
        species not in species_with_variant_id_costumes
    ):
        return "costume"
    if has_non_base_variant and not variant_id:
        return "form"
    if has_non_base_variant:
        return "form"
    return "base"


def build_release_window(events):
    if not events:
        return None
    starts = sorted(x["startDate"] for x in events if x.get("startDate"))
    ends = sorted(x["endDate"] for x in events if x.get("endDate"))
    if not starts and not ends:
        return None
    return {
        "firstSeen": starts[0] if starts else None,
        "lastSeen": ends[-1] if ends else None,
    }


def prettify_token(token):
    upper = token.upper()
    exact = {
        "GOFEST": "GO Fest",
        "GOTOUR": "GO Tour",
        "WCS": "World Championships",
        "VS": "VS",
        "ANNIV": "Anniversary",
        "OKINAWA": "Okinawa",
        "JEJU": "Jeju",
        "DIWALI": "Diwali",
        "KARIYUSHI": "Kariyushi",
        "KURTA": "Kurta",
        "HORIZONS": "Horizons",
        "DOCTOR": "Doctor",
        "POGO": "Pokemon GO",
        "PGO": "Pokemon GO",
        "NOEVOLVE": "No evolve",
    }
    if upper in exact:
        return exact[upper]
    if upper.isdigit():
        return upper
    if re.fullmatch(r"\d+(ST|ND|RD|TH)", upper):
        return upper.lower().capitalize()
    if len(upper) == 1:
        return upper
    return upper.capitalize()


def build_variant_label(species, raw_form_name):
    if not raw_form_name:
        return None
    value = str(raw_form_name).strip()
    if not value or value.isdigit():
        return None
    prefix = f"{species.upper()}_"
    cleaned = value
    if cleaned.upper().startswith(prefix):
        cleaned = cleaned[len(prefix):]
    if should_strip_leading_costume_marker(cleaned.upper()):
        cleaned = cleaned[1:]
    cleaned = re.sub(r"([A-Za-z]+)(20\d{2})", r"\1_\2", cleaned)
    tokens = [part for part in cleaned.split("_") if part]
    if not tokens:
        return None
    label_parts = []
    for token in tokens:
        label_parts.append(prettify_token(token))
    label = " ".join(label_parts).strip()
    if "costume" not in label.lower() and "form" not in label.lower():
        label = f"{label} costume"
    return label


def build_event_label(raw_form_name, fallback_event_name):
    if fallback_event_name:
        return fallback_event_name
    if not raw_form_name:
        return None
    value = str(raw_form_name).strip()
    if not value or value.isdigit():
        return None
    if should_strip_leading_costume_marker(value.upper()):
        value = value[1:]
    value = re.sub(r"([A-Za-z]+)(20\d{2})", r"\1_\2", value)
    parts = [part for part in value.split("_") if part]
    if not parts:
        return None
    event_parts = [prettify_token(part) for part in parts]
    event_label = " ".join(event_parts).strip()
    return event_label or None


def dedupe_preserve_order(values):
    seen = set()
    result = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result


def entry_preference(entry):
    path = (entry.get("assetPath") or "").replace("\\", "/")
    if "/Addressable Assets/" not in path:
        return 2
    if entry.get("variantId"):
        return 1
    return 0


def main():
    species_map = load_species_map()
    costume_species = load_costume_species()
    gm_costume_forms = load_game_master_costumes()
    gm_variant_forms = load_game_master_variant_forms()
    token_aliases = load_variant_token_aliases()
    event_history = load_event_history()
    species_to_family, family_to_species = load_species_families()
    sprite_rows = []
    species_with_variant_id_costumes = set()

    for path, parsed in iter_sprite_files():
        dex = parsed["dex"]
        species = species_map.get(dex)
        if not species:
            continue
        form_id = parsed["form_id"]
        variant_id = parsed["variant_id"]
        is_shiny = parsed["is_shiny"]
        sprite_rows.append((path, dex, species, form_id, variant_id, is_shiny))
        if variant_id and (species in costume_species or species in gm_costume_forms):
            species_with_variant_id_costumes.add(species)

    entries = []
    for path, dex, species, form_id, variant_id, is_shiny in sprite_rows:
        asset_key = f"{dex:03d}_{form_id}"
        if variant_id:
            asset_key += f"_{variant_id}"
        sprite_key = asset_key + ("_shiny" if is_shiny else "")

        variant_class = determine_variant_class(
            species=species,
            form_id=form_id,
            variant_id=variant_id,
            costume_species=costume_species,
            gm_costume_forms=gm_costume_forms,
            species_with_variant_id_costumes=species_with_variant_id_costumes,
        )
        species_events = event_history.get(species, [])

        entries.append(
            {
                "dex": dex,
                "species": species,
                "formId": form_id,
                "variantId": variant_id,
                "assetKey": asset_key,
                "spriteKey": sprite_key,
                "isShiny": is_shiny,
                "variantClass": variant_class,
                "isCostumeLike": variant_class == "costume",
                "eventTags": sorted({x["name"] for x in species_events if x.get("name")}),
                "primaryEventLabel": None,
                "hasEventMetadata": bool(species_events),
                "releaseWindow": build_release_window(species_events),
                "variantLabel": None,
                "gameMasterCostumeForms": gm_costume_forms.get(species, []),
                "gameMasterFormName": None,
                "assetPath": os.path.relpath(path, ROOT).replace("\\", "/"),
            }
        )

    entries_by_species = defaultdict(list)
    for entry in entries:
        entries_by_species[entry["species"]].append(entry)

    species_variant_label_map = defaultdict(dict)
    species_form_label_map = defaultdict(dict)
    for species, species_entries in entries_by_species.items():
        variant_ids = dedupe_preserve_order(
            sorted({entry["variantId"] for entry in species_entries if entry["variantClass"] == "costume" and entry["variantId"]})
        )
        costume_form_ids = dedupe_preserve_order(
            sorted({entry["formId"] for entry in species_entries if entry["variantClass"] == "costume" and not entry["variantId"] and entry["formId"] != "00"})
        )
        raw_forms = [
            form for form in gm_variant_forms.get(species, [])
            if isinstance(form, str) and form and not str(form).isdigit()
        ]
        raw_forms = dedupe_preserve_order(raw_forms)
        numeric_raw_forms = [
            form for form in gm_costume_forms.get(species, [])
            if isinstance(form, str) and form and not str(form).isdigit()
        ]
        numeric_raw_forms = dedupe_preserve_order(numeric_raw_forms)

        if not variant_ids or not raw_forms:
            continue

        if len(raw_forms) == 1 and len(variant_ids) == 1:
            label = build_variant_label(species, raw_forms[0])
            event_label = build_event_label(raw_forms[0], None)
            for variant_id in variant_ids:
                species_variant_label_map[species][variant_id] = {
                    "variantLabel": label,
                    "eventLabel": event_label,
                    "gameMasterFormName": raw_forms[0],
                }
            continue

        normalized_form_map = defaultdict(list)
        for raw_form in raw_forms:
            token = canonical_variant_token(normalize_form_token(raw_form, species))
            if token:
                normalized_form_map[token].append(raw_form)

        for variant_id in variant_ids:
            token = canonical_variant_token(variant_id)
            if not token:
                continue
            matches = normalized_form_map.get(token, [])
            if len(matches) == 1:
                raw_form = matches[0]
                species_variant_label_map[species][variant_id] = {
                    "variantLabel": build_variant_label(species, raw_form),
                    "eventLabel": build_event_label(raw_form, None),
                    "gameMasterFormName": raw_form,
                }

        unresolved_variant_ids = [variant_id for variant_id in variant_ids if variant_id not in species_variant_label_map[species]]

        numeric_variant_ids = [
            (int(variant_id), variant_id)
            for variant_id in unresolved_variant_ids
            if str(variant_id).isdigit()
        ]

        numeric_mapping_forms = numeric_raw_forms if numeric_raw_forms else raw_forms
        if numeric_variant_ids and max(item[0] for item in numeric_variant_ids) <= len(numeric_mapping_forms):
            for variant_id_int, variant_id in sorted(numeric_variant_ids, key=lambda item: item[0]):
                raw_form = numeric_mapping_forms[variant_id_int - 1]
                species_variant_label_map[species][variant_id] = {
                    "variantLabel": build_variant_label(species, raw_form),
                    "eventLabel": build_event_label(raw_form, None),
                    "gameMasterFormName": raw_form,
                }
            unmapped_variant_ids = [variant_id for variant_id in unresolved_variant_ids if not str(variant_id).isdigit()]
            if not unmapped_variant_ids:
                continue

        unresolved_variant_ids = [variant_id for variant_id in variant_ids if variant_id not in species_variant_label_map[species]]
        if unresolved_variant_ids and len(raw_forms) == len(variant_ids):
            for variant_id, raw_form in zip(variant_ids, raw_forms):
                species_variant_label_map[species][variant_id] = {
                    "variantLabel": build_variant_label(species, raw_form),
                    "eventLabel": build_event_label(raw_form, None),
                    "gameMasterFormName": raw_form,
                }

        if not costume_form_ids or not raw_forms:
            continue

        if len(raw_forms) == 1:
            label = build_variant_label(species, raw_forms[0])
            event_label = build_event_label(raw_forms[0], None)
            for form_id in costume_form_ids:
                species_form_label_map[species][form_id] = {
                    "variantLabel": label,
                    "eventLabel": event_label,
                    "gameMasterFormName": raw_forms[0],
                }
            continue

        if len(raw_forms) == len(costume_form_ids):
            for form_id, raw_form in zip(costume_form_ids, raw_forms):
                species_form_label_map[species][form_id] = {
                    "variantLabel": build_variant_label(species, raw_form),
                    "eventLabel": build_event_label(raw_form, None),
                    "gameMasterFormName": raw_form,
                }

    family_variant_label_map = defaultdict(dict)
    for family, family_species in family_to_species.items():
        variant_candidates = defaultdict(list)
        for species in family_species:
            for variant_id, data in species_variant_label_map.get(species, {}).items():
                if data.get("variantLabel"):
                    variant_candidates[variant_id].append(data)
        for variant_id, items in variant_candidates.items():
            labels = dedupe_preserve_order(item.get("variantLabel") for item in items if item.get("variantLabel"))
            events = dedupe_preserve_order(item.get("eventLabel") for item in items if item.get("eventLabel"))
            if len(labels) == 1:
                family_variant_label_map[family][variant_id] = {
                    "variantLabel": labels[0],
                    "eventLabel": events[0] if events else None,
                    "gameMasterFormName": items[0].get("gameMasterFormName"),
                }

    family_form_label_map = defaultdict(dict)
    for family, family_species in family_to_species.items():
        form_candidates = defaultdict(list)
        for species in family_species:
            for form_id, data in species_form_label_map.get(species, {}).items():
                if data.get("variantLabel"):
                    form_candidates[form_id].append(data)
        for form_id, items in form_candidates.items():
            labels = dedupe_preserve_order(item.get("variantLabel") for item in items if item.get("variantLabel"))
            events = dedupe_preserve_order(item.get("eventLabel") for item in items if item.get("eventLabel"))
            if len(labels) == 1:
                family_form_label_map[family][form_id] = {
                    "variantLabel": labels[0],
                    "eventLabel": events[0] if events else None,
                    "gameMasterFormName": items[0].get("gameMasterFormName"),
                }

    for entry in entries:
        fallback_event = entry["eventTags"][0] if entry["eventTags"] else None
        variant_label = None
        event_label = fallback_event
        if entry["variantClass"] == "costume" and entry.get("variantId"):
            species_data = species_variant_label_map.get(entry["species"], {}).get(entry["variantId"])
            if species_data:
                variant_label = species_data.get("variantLabel")
                event_label = species_data.get("eventLabel") or event_label
                entry["gameMasterFormName"] = species_data.get("gameMasterFormName")
            else:
                family = species_to_family.get(entry["species"])
                family_data = family_variant_label_map.get(family, {}).get(entry["variantId"]) if family else None
                if family_data:
                    variant_label = family_data.get("variantLabel")
                    event_label = family_data.get("eventLabel") or event_label
                    entry["gameMasterFormName"] = family_data.get("gameMasterFormName")

        if entry["variantClass"] == "costume" and not entry.get("variantId") and entry["formId"] != "00":
            species_data = species_form_label_map.get(entry["species"], {}).get(entry["formId"])
            if species_data:
                variant_label = species_data.get("variantLabel") or variant_label
                event_label = species_data.get("eventLabel") or event_label
                entry["gameMasterFormName"] = species_data.get("gameMasterFormName")
            else:
                family = species_to_family.get(entry["species"])
                family_data = family_form_label_map.get(family, {}).get(entry["formId"]) if family else None
                if family_data:
                    variant_label = family_data.get("variantLabel") or variant_label
                    event_label = family_data.get("eventLabel") or event_label
                    entry["gameMasterFormName"] = family_data.get("gameMasterFormName")

        if entry["variantClass"] == "form" and not variant_label and entry["formId"] != "00":
            variant_label = f"{entry['species']} form {entry['formId']}"

        if entry["variantClass"] == "costume" and not variant_label and entry.get("variantId"):
            variant_label = build_variant_label(entry["species"], entry["variantId"])
            event_label = build_event_label(entry["variantId"], event_label) or event_label
            aliased_tokens = token_aliases.get(str(entry["variantId"]).upper()) or []
            if aliased_tokens:
                aliased = aliased_tokens[0]
                variant_label = build_variant_label(entry["species"], aliased) or variant_label
                event_label = build_event_label(aliased, event_label) or event_label

        entry["variantLabel"] = variant_label
        entry["primaryEventLabel"] = event_label

    deduped_entries = {}
    for entry in entries:
        current = deduped_entries.get(entry["spriteKey"])
        if current is None or entry_preference(entry) > entry_preference(current):
            deduped_entries[entry["spriteKey"]] = entry

    entries = sorted(deduped_entries.values(), key=lambda item: item["spriteKey"])
    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": {
            "assets": os.path.relpath(ASSET_ROOT, ROOT).replace("\\", "/"),
            "gameMasterCostumes": os.path.relpath(GAME_MASTER_COSTUMES, ROOT).replace("\\", "/"),
            "eventHistory": os.path.relpath(EVENT_HISTORY, ROOT).replace("\\", "/"),
        },
        "count": len(entries),
        "speciesCount": len({item["species"] for item in entries}),
        "entries": entries,
    }

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {len(entries)} entries to {OUTPUT}")


if __name__ == "__main__":
    main()
