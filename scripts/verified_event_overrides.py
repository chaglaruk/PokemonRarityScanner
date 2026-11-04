"""Apply exact, sourced event overrides without creating inferred associations."""
import json


def load_verified_event_overrides(path):
    with open(path, encoding="utf-8") as handle:
        entries = json.load(handle).get("entries", [])
    result = {}
    for entry in entries:
        if entry.get("verifiedOfficial") is not True:
            continue
        key = entry["spriteKey"]
        if key in result or not entry.get("sourceUrl", "").startswith("https://pokemongo.com/news/"):
            raise ValueError("Invalid or duplicate official event override")
        if not all(entry.get(k) for k in ("eventLabel", "eventStart", "eventEnd", "variantLabel")):
            raise ValueError("Incomplete official event override")
        if entry["eventEnd"] < entry["eventStart"]:
            raise ValueError("Official event end precedes start")
        result[key] = entry
    return result


def apply_catalog_override(entry, override):
    if not override:
        return
    if entry.get("spriteKey") != override.get("spriteKey"):
        raise ValueError("Official override does not match sprite identity")
    entry["variantLabel"] = override["variantLabel"]
    excluded = {event["eventLabel"] for event in override.get("excludeUnverifiedAppearances", [])}
    tags = [tag for tag in (entry.get("eventTags") or []) if tag not in excluded]
    entry["eventTags"] = list(dict.fromkeys(tags + [override["eventLabel"]]))
    entry["primaryEventLabel"] = override["eventLabel"]
    entry["hasEventMetadata"] = True
    entry["releaseWindow"] = {"firstSeen": override["eventStart"], "lastSeen": override["eventEnd"]}


def apply_authoritative_override(entry, override):
    if not override:
        return
    if entry.get("spriteKey") != override.get("spriteKey"):
        raise ValueError("Official override does not match sprite identity")
    for field in ("variantLabel", "eventLabel", "eventStart", "eventEnd"):
        entry[field] = override[field]
    aliases = [alias for alias in (entry.get("aliases") or [])
               if alias != override["eventLabel"] or alias in override.get("aliases", [])]
    entry["aliases"] = list(dict.fromkeys(aliases + override.get("aliases", [])))
    entry["sourceIds"] = list(dict.fromkeys((entry.get("sourceIds") or []) + override.get("sourceIds", [])))
    excluded = {(x["eventLabel"], x["startDate"]) for x in override.get("excludeUnverifiedAppearances", [])}
    history = [event for event in (entry.get("historicalEvents") or [])
               if (event.get("eventLabel"), event.get("startDate")) not in excluded
               and (event.get("eventLabel"), event.get("startDate")) !=
               (override["eventLabel"], override["eventStart"])]
    history.append({"eventLabel": override["eventLabel"], "startDate": override["eventStart"], "endDate": override["eventEnd"]})
    entry["historicalEvents"] = history


def apply_legacy_override(entry, override):
    if not override:
        return
    if entry.get("spriteKey") != override.get("spriteKey"):
        raise ValueError("Official override does not match sprite identity")
    for field in ("variantLabel", "eventLabel", "eventStart", "eventEnd"):
        entry[field] = override[field]
    entry["firstSeen"] = override["eventStart"]
    entry["lastSeen"] = override["eventEnd"]
    entry["lastKnownEvent"] = override["eventLabel"]
    excluded = {(event["eventLabel"], event["startDate"]) for event in override.get("excludeUnverifiedAppearances", [])}
    if (entry.get("activeEventLabel"), entry.get("activeEventStart")) in excluded:
        entry["activeEventLabel"] = entry["activeEventStart"] = entry["activeEventEnd"] = None
    # A past confirmed event does not prove current unavailability.
    entry["liveAvailability"] = "unknown"
    entry["aliases"] = list(dict.fromkeys((entry.get("aliases") or []) + override.get("aliases", [])))
    entry["sourceIds"] = list(dict.fromkeys((entry.get("sourceIds") or []) + override.get("sourceIds", [])))
    if "sourceNames" in entry:
        entry["sourceNames"] = list(dict.fromkeys((entry.get("sourceNames") or []) + ["Pokemon GO Live News"]))
