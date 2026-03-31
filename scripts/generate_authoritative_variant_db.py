import json
import os
import re
from collections import defaultdict
from datetime import datetime, timezone
from PIL import Image


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VARIANT_CATALOG = os.path.join(ROOT, "app", "src", "main", "assets", "data", "variant_catalog.json")
EXTERNAL_OVERRIDES = os.path.join(ROOT, "scripts", "bulbapedia_event_overrides.json")
EXTERNAL_BULBAPEDIA_SNAPSHOT = os.path.join(ROOT, "scripts", "external_snapshots", "bulbapedia_event_pokemon_go.json")
VARIANT_TOKEN_ALIASES = os.path.join(ROOT, "scripts", "variant_token_aliases.json")
POKEMON_FAMILIES = os.path.join(ROOT, "app", "src", "main", "assets", "data", "pokemon_families.json")
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "data", "authoritative_variant_db.json")


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def build_entry(row):
    release = row.get("releaseWindow") or {}
    variant_label = row.get("variantLabel")
    event_label = row.get("primaryEventLabel") or (row.get("eventTags") or [None])[0]
    aliases = []
    if variant_label:
        aliases.append(variant_label)
    if event_label and event_label != variant_label:
        aliases.append(event_label)

    return {
        "species": row["species"],
        "dex": row["dex"],
        "formId": row["formId"],
        "variantId": row.get("variantId"),
        "spriteKey": row["spriteKey"],
        "variantClass": row["variantClass"],
        "isShiny": row["isShiny"],
        "isCostumeLike": row["isCostumeLike"],
        "variantLabel": variant_label,
        "eventLabel": event_label,
        "eventStart": release.get("firstSeen"),
        "eventEnd": release.get("lastSeen"),
        "historicalEvents": [],
        "gameMasterFormName": row.get("gameMasterFormName"),
        "assetPath": row.get("assetPath"),
        "aliases": aliases,
        "sourceIds": ["local_variant_catalog"],
    }


def load_external_overrides():
    if not os.path.exists(EXTERNAL_OVERRIDES):
        return {}
    payload = load_json(EXTERNAL_OVERRIDES)
    return {
        entry["spriteKey"]: entry
        for entry in payload.get("entries", [])
        if entry.get("spriteKey")
    }


def load_variant_token_aliases():
    if not os.path.exists(VARIANT_TOKEN_ALIASES):
        return {
            "globalTokenAliases": {},
            "speciesTokenAliases": {},
            "spriteKeyTokenAliases": {},
        }
    payload = load_json(VARIANT_TOKEN_ALIASES)
    return {
        "globalTokenAliases": payload.get("globalTokenAliases", {}),
        "speciesTokenAliases": payload.get("speciesTokenAliases", {}),
        "spriteKeyTokenAliases": payload.get("spriteKeyTokenAliases", {}),
    }


def normalize_variant_token(value):
    token = str(value or "").strip().upper()
    if not token:
        return None
    token = re.sub(r"([A-Z]+)(20\d{2})", r"\1_\2", token)
    token = re.sub(r"([A-Z])(\d)", r"\1_\2", token)
    token = re.sub(r"(\d)([A-Z])", r"\1_\2", token)
    token = token.replace("-", "_").replace(" ", "_")
    token = re.sub(r"_+", "_", token).strip("_")
    return token or None


def canonical_variant_token(value):
    token = normalize_variant_token(value)
    if not token:
        return None
    token = token.replace("POKEMON_GO_", "").replace("PGO_", "").replace("_NOEVOLVE", "")
    if token.startswith("C") and len(token) > 2 and token[1].isalpha():
        stripped = token[1:]
        if any(
            stripped.startswith(prefix)
            for prefix in (
                "SPRING_",
                "SUMMER_",
                "FALL_",
                "APRIL_",
                "NOVEMBER_",
                "HOENN_",
                "JOHTO_",
                "KANTO_",
                "SAFARI_",
                "SINNOH_",
                "TCG",
                "INDONESIA_",
                "WILDAREA_",
                "ANNIVERSARY",
                "ONE_YEAR_ANNIVERSARY",
            )
        ):
            token = stripped
    if token.startswith("F") and len(token) > 2 and token[1].isalpha():
        stripped = token[1:]
        if any(
            stripped.startswith(prefix)
            for prefix in (
                "HOLIDAY",
                "HALLOWEEN",
                "WINTER",
                "FALL",
                "SUMMER",
                "SPRING",
                "GOFEST",
                "GOTOUR",
                "WCS",
                "FLYING",
                "DIWALI",
                "HORIZONS",
                "JEJU",
                "KARIYUSHI",
                "KURTA",
                "COSTUME",
                "COPY",
                "DOCTOR",
                "POP_STAR",
                "ROCK_STAR",
                "TSHIRT",
                "WILDAREA",
                "ANNIV",
                "ANNIVERSARY",
                "NEWYEAR",
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
            )
        ):
            token = stripped
    token = token.replace("WORLD_CHAMPIONSHIPS", "WCS")
    return token or None


def load_bulbapedia_snapshot():
    if not os.path.exists(EXTERNAL_BULBAPEDIA_SNAPSHOT):
        return {}
    payload = load_json(EXTERNAL_BULBAPEDIA_SNAPSHOT)
    index = defaultdict(list)
    for entry in payload.get("entries", []):
        species = entry.get("species")
        token = canonical_variant_token(entry.get("normalizedToken"))
        if species and token:
            index[(species, token)].append(entry)
    return dict(index)


def load_bulbapedia_snapshot_by_species():
    if not os.path.exists(EXTERNAL_BULBAPEDIA_SNAPSHOT):
        return {}
    payload = load_json(EXTERNAL_BULBAPEDIA_SNAPSHOT)
    by_species = defaultdict(list)
    for entry in payload.get("entries", []):
        species = entry.get("species")
        if species:
            by_species[species].append(entry)
    return dict(by_species)


def load_species_families():
    payload = load_json(POKEMON_FAMILIES)
    species_to_family = payload.get("speciesToFamily", {})
    family_to_species = defaultdict(list)
    for species, family in species_to_family.items():
        family_to_species[family].append(species)
    return species_to_family, dict(family_to_species)


def unique_or_none(values):
    unique = [value for value in dict.fromkeys(values) if value]
    if len(unique) == 1:
        return unique[0]
    return None


def latest_appearance(appearances):
    def sort_key(item):
        return (
            item.get("endDate") or item.get("startDate") or "",
            item.get("eventLabel") or "",
        )
    return sorted(appearances, key=sort_key)[-1] if appearances else None


def build_candidate_tokens(entry):
    candidates = []
    aliases = entry.get("_tokenAliases") or []
    gm_form_name = entry.get("gameMasterFormName")
    if gm_form_name:
        prefix = f"{entry.get('species', '').upper()}_"
        gm_form_name = str(gm_form_name).upper()
        if gm_form_name.startswith(prefix):
            gm_form_name = gm_form_name[len(prefix):]
    for value in (
        gm_form_name,
        entry.get("variantId"),
        entry.get("variantLabel"),
        entry.get("eventLabel"),
        *aliases,
    ):
        token = canonical_variant_token(value)
        if token and token not in candidates:
            candidates.append(token)
    return candidates


def attach_candidate_aliases(entry, alias_config):
    aliases = []

    def add(value):
        token = canonical_variant_token(value) or normalize_variant_token(value)
        if token and token not in aliases:
            aliases.append(token)

    for value in (
        entry.get("variantId"),
        entry.get("gameMasterFormName"),
        entry.get("variantLabel"),
        entry.get("eventLabel"),
    ):
        token = canonical_variant_token(value) or normalize_variant_token(value)
        if not token:
            continue
        for alias in alias_config.get("globalTokenAliases", {}).get(token, []):
            add(alias)
        for alias in alias_config.get("speciesTokenAliases", {}).get(entry["species"], {}).get(token, []):
            add(alias)

    for alias in alias_config.get("spriteKeyTokenAliases", {}).get(entry["spriteKey"], []):
        add(alias)

    entry["_tokenAliases"] = aliases


def apply_bulbapedia_snapshot(entry, snapshot_entry):
    if not snapshot_entry:
        return False
    appearances = snapshot_entry.get("appearances") or []
    latest = latest_appearance(appearances)
    entry["variantClass"] = "costume"
    entry["isCostumeLike"] = True
    form_label = snapshot_entry.get("formLabel")
    if form_label:
        label = form_label if "costume" in form_label.lower() or entry["variantClass"] == "form" else f"{form_label} costume"
        entry["variantLabel"] = label
        entry["aliases"] = list(dict.fromkeys((entry.get("aliases") or []) + [form_label, label]))
    if latest:
        entry["eventLabel"] = latest.get("eventLabel") or entry.get("eventLabel")
        entry["eventStart"] = latest.get("startDate") or entry.get("eventStart")
        entry["eventEnd"] = latest.get("endDate") or entry.get("eventEnd")
    entry["historicalEvents"] = [
        {
            "eventLabel": appearance.get("eventLabel"),
            "startDate": appearance.get("startDate"),
            "endDate": appearance.get("endDate"),
        }
        for appearance in appearances
        if appearance.get("eventLabel")
    ]
    entry["sourceIds"] = list(dict.fromkeys((entry.get("sourceIds") or []) + (snapshot_entry.get("sourceIds") or [])))
    return True


def canonical_label_token(value):
    token = normalize_variant_token(value)
    if not token:
        return None
    token = token.replace("_COSTUME", "").replace("COSTUME_", "")
    token = token.replace("_FORM", "").replace("FORM_", "")
    token = token.replace("WORLD_CHAMPIONSHIPS", "WCS")
    return token.strip("_") or None


def enrich_with_snapshot_history_by_label(entry, species_entries):
    if entry.get("historicalEvents") or not species_entries:
        return False
    target = canonical_label_token(entry.get("variantLabel")) or canonical_label_token(entry.get("eventLabel"))
    if not target:
        return False

    matches = []
    for candidate in species_entries:
        form_token = canonical_label_token(candidate.get("formLabel"))
        normalized_token = canonical_label_token(candidate.get("normalizedToken"))
        if target in {form_token, normalized_token}:
            matches.append(candidate)

    if len(matches) != 1:
        return False

    return apply_bulbapedia_snapshot(entry, matches[0])


def compute_average_hash(asset_path):
    if not asset_path:
        return None
    full_path = os.path.join(ROOT, asset_path)
    if not os.path.exists(full_path):
        return None
    with Image.open(full_path) as image:
        image = image.convert("L").resize((16, 16))
        pixels = list(image.getdata())
    avg = sum(pixels) / len(pixels)
    return [1 if pixel >= avg else 0 for pixel in pixels]


def hamming_distance(left, right):
    if left is None or right is None or len(left) != len(right):
        return None
    return sum(1 for a, b in zip(left, right) if a != b)


def propagate_visual_semantics(entries):
    propagated = 0
    matched_by_species = defaultdict(list)
    for entry in entries:
        if entry.get("variantClass") != "costume":
            continue
        if not entry.get("historicalEvents"):
            continue
        matched_by_species[entry["species"]].append(entry)

    hash_cache = {}
    for species_entries in matched_by_species.values():
        for entry in species_entries:
            hash_cache[entry["spriteKey"]] = compute_average_hash(entry.get("assetPath"))

    for entry in entries:
        if entry.get("variantClass") != "costume":
            continue
        if entry.get("historicalEvents"):
            continue
        if any(source in entry.get("sourceIds", []) for source in ("bulbapedia:event-pokemon-go", "family_override_propagation")):
            continue
        variant_id = str(entry.get("variantId") or "")
        if not variant_id.isdigit():
            continue
        candidates = matched_by_species.get(entry["species"], [])
        if not candidates:
            continue
        source_hash = compute_average_hash(entry.get("assetPath"))
        if source_hash is None:
            continue

        best_candidate = None
        best_distance = None
        for candidate in candidates:
            candidate_hash = hash_cache.get(candidate["spriteKey"])
            distance = hamming_distance(source_hash, candidate_hash)
            if distance is None:
                continue
            if best_distance is None or distance < best_distance:
                best_distance = distance
                best_candidate = candidate

        if best_candidate is None or best_distance is None or best_distance > 8:
            continue

        entry["variantLabel"] = best_candidate.get("variantLabel") or entry.get("variantLabel")
        entry["eventLabel"] = best_candidate.get("eventLabel") or entry.get("eventLabel")
        entry["eventStart"] = best_candidate.get("eventStart") or entry.get("eventStart")
        entry["eventEnd"] = best_candidate.get("eventEnd") or entry.get("eventEnd")
        entry["historicalEvents"] = best_candidate.get("historicalEvents") or []
        entry["aliases"] = list(dict.fromkeys((entry.get("aliases") or []) + (best_candidate.get("aliases") or [])))
        entry["sourceIds"] = list(dict.fromkeys((entry.get("sourceIds") or []) + ["visual_semantic_propagation"]))
        propagated += 1

    return propagated


def main():
    catalog = load_json(VARIANT_CATALOG)
    overrides = load_external_overrides()
    alias_config = load_variant_token_aliases()
    bulbapedia_snapshot = load_bulbapedia_snapshot()
    bulbapedia_snapshot_by_species = load_bulbapedia_snapshot_by_species()
    species_to_family, _ = load_species_families()
    entries = []
    override_count = 0
    snapshot_match_count = 0
    propagated_count = 0
    visual_propagation_count = 0
    exact_override_keys = set()
    for row in catalog["entries"]:
        entry = build_entry(row)
        attach_candidate_aliases(entry, alias_config)
        matched_snapshot = None
        for token in build_candidate_tokens(entry):
            candidates = bulbapedia_snapshot.get((entry["species"], token)) or []
            if len(candidates) == 1:
                matched_snapshot = candidates[0]
                break
        if matched_snapshot:
            snapshot_match_count += 1
            apply_bulbapedia_snapshot(entry, matched_snapshot)
        override = overrides.get(entry["spriteKey"])
        if override:
            override_count += 1
            exact_override_keys.add(entry["spriteKey"])
            entry["variantLabel"] = override.get("variantLabel") or entry["variantLabel"]
            entry["eventLabel"] = override.get("eventLabel") or entry["eventLabel"]
            entry["eventStart"] = override.get("eventStart") or entry["eventStart"]
            entry["eventEnd"] = override.get("eventEnd") or entry["eventEnd"]
            entry["aliases"] = list(dict.fromkeys((entry["aliases"] or []) + (override.get("aliases") or [])))
            entry["sourceIds"] = list(dict.fromkeys((entry["sourceIds"] or []) + (override.get("sourceIds") or [])))
        enrich_with_snapshot_history_by_label(entry, bulbapedia_snapshot_by_species.get(entry["species"]))
        entries.append(entry)

    grouped = defaultdict(list)
    for entry in entries:
        family = species_to_family.get(entry["species"])
        if not family:
            continue
        if entry["variantClass"] == "costume" and entry.get("variantId"):
            key = (family, entry["variantClass"], entry["variantId"], entry["isShiny"])
            grouped[key].append(entry)

    for group_entries in grouped.values():
        exact_sources = [entry for entry in group_entries if entry["spriteKey"] in exact_override_keys]
        if not exact_sources:
            continue
        variant_label = unique_or_none(entry.get("variantLabel") for entry in exact_sources)
        event_label = unique_or_none(entry.get("eventLabel") for entry in exact_sources)
        event_start = unique_or_none(entry.get("eventStart") for entry in exact_sources)
        event_end = unique_or_none(entry.get("eventEnd") for entry in exact_sources)
        historical_events = unique_or_none(
            json.dumps(entry.get("historicalEvents") or [], ensure_ascii=False, sort_keys=True)
            for entry in exact_sources
        )
        if not any([variant_label, event_label, event_start, event_end, historical_events]):
            continue

        propagated_aliases = []
        for source in exact_sources:
            propagated_aliases.extend(source.get("aliases") or [])
        propagated_aliases = list(dict.fromkeys(propagated_aliases))

        for entry in group_entries:
            if entry["spriteKey"] in exact_override_keys:
                continue
            changed = False
            if variant_label and not entry.get("variantLabel"):
                entry["variantLabel"] = variant_label
                changed = True
            if event_label and not entry.get("eventLabel"):
                entry["eventLabel"] = event_label
                changed = True
            if event_start and not entry.get("eventStart"):
                entry["eventStart"] = event_start
                changed = True
            if event_end and not entry.get("eventEnd"):
                entry["eventEnd"] = event_end
                changed = True
            if historical_events and not entry.get("historicalEvents"):
                entry["historicalEvents"] = json.loads(historical_events)
                changed = True
            if propagated_aliases:
                entry["aliases"] = list(dict.fromkeys((entry.get("aliases") or []) + propagated_aliases))
            if changed:
                propagated_count += 1
                entry["sourceIds"] = list(dict.fromkeys((entry.get("sourceIds") or []) + ["family_override_propagation"]))

    visual_propagation_count = propagate_visual_semantics(entries)

    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(entries),
        "sourceSummary": {
            "local_variant_catalog": len(entries),
            "bulbapedia_snapshot_matches": snapshot_match_count,
            "external_overrides": override_count,
            "family_override_propagation": propagated_count,
            "visual_semantic_propagation": visual_propagation_count
        },
        "entries": entries,
    }
    for entry in payload["entries"]:
        entry.pop("_tokenAliases", None)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"Wrote {len(entries)} authoritative entries to {OUTPUT}")


if __name__ == "__main__":
    main()
