from __future__ import annotations

import json
import os
from collections import Counter
from datetime import datetime, timezone


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AUTH_DB_PATH = os.path.join(ROOT, "app", "src", "main", "assets", "data", "authoritative_variant_db.json")
REGISTRY_PATH = os.path.join(ROOT, "scripts", "external_source_registry.json")
OUTPUT_PATH = os.path.join(ROOT, "app", "src", "main", "assets", "data", "global_rarity_legacy_db.json")
EXTERNAL_OVERRIDES_PATH = os.path.join(ROOT, "scripts", "bulbapedia_event_overrides.json")
EXTERNAL_SNAPSHOTS_DIR = os.path.join(ROOT, "scripts", "external_snapshots")
SNAPSHOT_ADAPTER_ID = "snapshot_adapter:bulbapedia_event_overrides"
SNAPSHOT_ADAPTER_NAME = "Bulbapedia Event Overrides Snapshot Adapter"


def load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def sprite_base_key(sprite_key: str) -> str:
    return sprite_key.removesuffix("_shiny")


def parse_date(value: str | None):
    if not value:
        return None
    try:
        return datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError:
        return None


def dedupe_list(values):
    return list(dict.fromkeys(value for value in values if value))


def build_bulbapedia_override_snapshots():
    if not os.path.exists(EXTERNAL_OVERRIDES_PATH):
        return {}
    payload = load_json(EXTERNAL_OVERRIDES_PATH)
    return {
        entry["spriteKey"]: {
            "spriteKey": entry["spriteKey"],
            "variantLabel": entry.get("variantLabel"),
            "eventLabel": entry.get("eventLabel"),
            "eventStart": entry.get("eventStart"),
            "eventEnd": entry.get("eventEnd"),
            "firstSeen": entry.get("eventStart"),
            "lastSeen": entry.get("eventEnd") or entry.get("eventStart"),
            "lastKnownEvent": entry.get("eventLabel"),
            "aliases": entry.get("aliases") or [],
            "sourceIds": dedupe_list([SNAPSHOT_ADAPTER_ID] + (entry.get("sourceIds") or [])),
        }
        for entry in payload.get("entries", [])
        if entry.get("spriteKey")
    }


def load_normalized_snapshots():
    if not os.path.isdir(EXTERNAL_SNAPSHOTS_DIR):
        return {}, []

    merged = {}
    species_live_entries = []
    for name in sorted(os.listdir(EXTERNAL_SNAPSHOTS_DIR)):
        if not name.endswith(".json"):
            continue
        payload = load_json(os.path.join(EXTERNAL_SNAPSHOTS_DIR, name))
        source_id = payload.get("sourceId")
        if not source_id:
            continue
        for entry in payload.get("entries", []):
            species = entry.get("species")
            sprite_key = entry.get("spriteKey")
            if species and not sprite_key:
                species_live_entries.append(
                    {
                        "species": species,
                        "applyTo": entry.get("applyTo", "species_all"),
                        "activeEventLabel": entry.get("activeEventLabel") or entry.get("eventLabel"),
                        "activeEventStart": entry.get("activeEventStart") or entry.get("eventStart"),
                        "activeEventEnd": entry.get("activeEventEnd") or entry.get("eventEnd"),
                        "sourceIds": dedupe_list([source_id] + (entry.get("sourceIds") or [])),
                    }
                )
                continue
            if not sprite_key:
                continue
            merged[sprite_key] = {
                "spriteKey": sprite_key,
                "variantLabel": entry.get("variantLabel"),
                "eventLabel": entry.get("eventLabel"),
                "eventStart": entry.get("eventStart"),
                "eventEnd": entry.get("eventEnd"),
                "firstSeen": entry.get("firstSeen") or entry.get("eventStart"),
                "lastSeen": entry.get("lastSeen") or entry.get("eventEnd") or entry.get("eventStart"),
                "lastKnownEvent": entry.get("lastKnownEvent") or entry.get("eventLabel"),
                "aliases": entry.get("aliases") or [],
                "sourceIds": dedupe_list([source_id] + (entry.get("sourceIds") or [])),
            }
    return merged, species_live_entries


def merge_snapshot(entry, snapshot):
    if not snapshot:
        return
    for field in ("variantLabel", "eventLabel", "eventStart", "eventEnd", "firstSeen", "lastSeen", "lastKnownEvent"):
        if snapshot.get(field):
            entry[field] = snapshot[field]
    entry["aliases"] = dedupe_list((entry.get("aliases") or []) + (snapshot.get("aliases") or []))
    entry["sourceIds"] = dedupe_list((entry.get("sourceIds") or []) + (snapshot.get("sourceIds") or []))


def status_for_window(start_value, end_value):
    event_start = parse_date(start_value)
    event_end = parse_date(end_value or start_value)
    today = datetime.now(timezone.utc).date()

    if event_start and event_start > today:
        return "upcoming"
    if event_end and event_end < today:
        return "retired"
    if event_start and event_end and event_start <= today <= event_end:
        return "active"
    return "unknown"


def derive_live_availability(entry):
    active_status = status_for_window(entry.get("activeEventStart"), entry.get("activeEventEnd"))
    if entry.get("activeEventLabel") and active_status != "unknown":
        return active_status
    historical_status = status_for_window(
        entry.get("eventStart") or entry.get("firstSeen"),
        entry.get("eventEnd") or entry.get("lastSeen") or entry.get("eventStart"),
    )
    if entry.get("eventLabel") and historical_status != "unknown":
        return historical_status
    return "unknown"


def should_apply_species_live(entry, live_entry, species_has_non_base):
    if entry["species"] != live_entry["species"]:
        return False
    apply_to = live_entry.get("applyTo", "species_all")
    if apply_to == "costume_like":
        return entry.get("isCostumeLike", False)
    if apply_to == "non_base_only":
        return entry.get("variantClass") != "base" or not species_has_non_base
    if apply_to == "costume_or_form":
        return entry.get("variantClass") in {"costume", "form"}
    return True


def merge_species_live_snapshot(entry, live_entries, species_has_non_base):
    matching = [
        live_entry
        for live_entry in live_entries
        if should_apply_species_live(entry, live_entry, species_has_non_base)
    ]
    if not matching:
        return
    matching.sort(key=lambda item: (item.get("activeEventStart") or "", item.get("activeEventLabel") or ""), reverse=True)
    selected = matching[0]
    if selected.get("activeEventLabel"):
        entry["activeEventLabel"] = selected["activeEventLabel"]
    if selected.get("activeEventStart"):
        entry["activeEventStart"] = selected["activeEventStart"]
    if selected.get("activeEventEnd"):
        entry["activeEventEnd"] = selected["activeEventEnd"]
    entry["sourceIds"] = dedupe_list((entry.get("sourceIds") or []) + (selected.get("sourceIds") or []))


def main() -> None:
    authoritative = load_json(AUTH_DB_PATH)
    registry = load_json(REGISTRY_PATH)
    snapshot_entries = build_bulbapedia_override_snapshots()
    normalized_snapshots, species_live_entries = load_normalized_snapshots()
    snapshot_entries.update(normalized_snapshots)

    source_names = {item["id"]: item["name"] for item in registry.get("sources", [])}
    source_names[SNAPSHOT_ADAPTER_ID] = SNAPSHOT_ADAPTER_NAME
    entries = authoritative["entries"]
    by_base_key = {}
    species_has_non_base = {}
    for entry in entries:
        by_base_key.setdefault(sprite_base_key(entry["spriteKey"]), []).append(entry)
        if entry["variantClass"] != "base":
            species_has_non_base[entry["species"]] = True

    output_entries = []
    source_summary = Counter()

    for entry in entries:
        same_variant = by_base_key.get(sprite_base_key(entry["spriteKey"]), [])
        has_shiny_peer = any(
            peer["spriteKey"] != entry["spriteKey"] and peer.get("isShiny") for peer in same_variant
        )
        if entry.get("isShiny"):
            shiny_availability = "released_variant"
        elif has_shiny_peer:
            shiny_availability = "released"
        else:
            shiny_availability = "unknown"

        first_seen = entry.get("eventStart")
        last_seen = entry.get("eventEnd") or entry.get("eventStart")
        source_ids = sorted(set(entry.get("sourceIds") or []))

        output_entry = {
            "species": entry["species"],
            "dex": entry["dex"],
            "formId": entry["formId"],
            "variantId": entry.get("variantId"),
            "spriteKey": entry["spriteKey"],
            "variantClass": entry["variantClass"],
            "isShiny": entry["isShiny"],
            "isCostumeLike": entry["isCostumeLike"],
            "variantLabel": entry.get("variantLabel"),
            "eventLabel": entry.get("eventLabel"),
            "eventStart": entry.get("eventStart"),
            "eventEnd": entry.get("eventEnd"),
            "firstSeen": first_seen,
            "lastSeen": last_seen,
            "lastKnownEvent": entry.get("eventLabel"),
            "activeEventLabel": None,
            "activeEventStart": None,
            "activeEventEnd": None,
            "liveAvailability": "unknown",
            "shinyAvailability": shiny_availability,
            "shinyOddsBucket": None,
            "aliases": entry.get("aliases") or [],
            "sourceIds": source_ids,
            "sourceNames": [],
        }
        merge_snapshot(output_entry, snapshot_entries.get(entry["spriteKey"]))
        merge_species_live_snapshot(
            output_entry,
            species_live_entries,
            species_has_non_base.get(entry["species"], False)
        )
        output_entry["liveAvailability"] = derive_live_availability(output_entry)
        output_entry["sourceNames"] = [
            source_names[source_id]
            for source_id in output_entry["sourceIds"]
            if source_id in source_names
        ]
        for source_id in output_entry["sourceIds"]:
            source_summary[source_id] += 1
        output_entries.append(output_entry)

    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(output_entries),
        "sourceSummary": dict(sorted(source_summary.items())),
        "entries": output_entries,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print(f"Generated {OUTPUT_PATH} with {len(output_entries)} entries")


if __name__ == "__main__":
    main()
