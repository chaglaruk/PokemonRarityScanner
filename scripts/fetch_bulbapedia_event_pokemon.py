# Purpose: Fetch and normalize Bulbapedia Event Pokemon GO data.
from __future__ import annotations

import argparse
import http.client
import json
import re
import urllib.parse
from collections import defaultdict
from datetime import datetime, timezone


API_URL = "https://bulbapedia.bulbagarden.net/w/api.php"
API_HOST = "bulbapedia.bulbagarden.net"
SOURCE_ID = "bulbapedia:event-pokemon-go"
SOURCE_NAME = "Bulbapedia Event Pokemon (GO)"
PAGE_TITLE = "Event_Pokémon_(GO)"


def validate_api_url(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != API_HOST or parsed.path != "/w/api.php":
        raise ValueError(f"Refusing unexpected Bulbapedia API URL: {url}")
    return url


def fetch_wikitext(page_title: str) -> str:
    params = {
        "action": "parse",
        "page": page_title,
        "prop": "wikitext",
        "format": "json",
        "formatversion": "2",
    }
    url = validate_api_url(f"{API_URL}?{urllib.parse.urlencode(params)}")
    parsed = urllib.parse.urlparse(url)
    connection = http.client.HTTPSConnection(API_HOST, timeout=60)
    try:
        connection.request(
            "GET",
            f"{parsed.path}?{parsed.query}",
            headers={"User-Agent": "PokeRarityScanner/1.0"},
        )
        response = connection.getresponse()
        if response.status < 200 or response.status >= 300:
            raise RuntimeError(f"Bulbapedia API returned HTTP {response.status}")
        payload = json.loads(response.read().decode("utf-8"))
    finally:
        connection.close()
    return payload["parse"]["wikitext"]


def clean_inline(value: str) -> str:
    text = value or ""
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL)
    text = re.sub(r"<[^>]+>", "", text)

    def replace_link(match):
        target, label = match.group(1), match.group(2)
        return label or target

    text = re.sub(r"\[\[([^|\]]+)(?:\|([^\]]+))?\]\]", replace_link, text)
    text = re.sub(r"\{\{Shinystar/GO[^}]*\}\}", "", text)
    text = re.sub(r"\{\{GO\|([^}|]+)(?:\|([^}]+))?\}\}", lambda m: m.group(2) or m.group(1), text)
    text = re.sub(r"\{\{m\|([^}|]+)(?:\|([^}]+))?\}\}", lambda m: m.group(2) or m.group(1), text)
    text = re.sub(r"\{\{p\|([^}|]+)(?:\|([^}]+))?\}\}", lambda m: m.group(2) or m.group(1), text)
    text = re.sub(r"\{\{ruby\|([^|}]+)\|[^}]+\}\}", lambda m: m.group(1), text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = text.replace("—", "-").replace("–", "-")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def normalize_external_token(value: str) -> str | None:
    token = (value or "").strip()
    if not token:
        return None
    token = re.sub(r"^\d{4}", "", token)
    token = re.sub(r"([a-z])([A-Z])", r"\1_\2", token)
    token = re.sub(r"([A-Za-z])(\d)", r"\1_\2", token)
    token = re.sub(r"(\d)([A-Za-z])", r"\1_\2", token)
    token = token.replace("-", "_").replace(" ", "_")
    token = re.sub(r"_+", "_", token).strip("_").upper()
    replacements = {
        "WCS": "WCS",
        "GO_FEST": "GOFEST",
        "GO_TOUR": "GOTOUR",
        "POKEMON_DAY": "POKEMON_DAY",
    }
    for old, new in replacements.items():
        token = token.replace(old, new)
    return token or None


def extract_event_table(wikitext: str) -> str:
    section_match = re.search(r"==List of Event Pokémon==(?P<body>.*)", wikitext, flags=re.DOTALL)
    if not section_match:
        raise ValueError("Could not find Event Pokémon table section")
    body = section_match.group("body")
    table_match = re.search(r"\{\|(?P<table>.*?\n\|\})", body, flags=re.DOTALL)
    if not table_match:
        raise ValueError("Could not extract Event Pokémon table")
    return "{|" + table_match.group("table")


def split_rows(table_text: str) -> list[str]:
    rows = re.split(r"\n\|-", table_text)
    return [row for row in rows if "{{MSP/GO|" in row]


def split_cells(row_text: str) -> list[str]:
    cells = []
    current = None
    for line in row_text.splitlines():
        if line.startswith("{|") or line.startswith("|}") or line.startswith("!"):
            continue
        if line.startswith("|") and not line.startswith("|-"):
            if current is not None:
                cells.append(current.strip())
            current = line[1:].strip()
        else:
            if current is None:
                continue
            current += "\n" + line
    if current is not None:
        cells.append(current.strip())
    return cells


def parse_form_cell(cell: str) -> tuple[str, int]:
    rowspan = 1
    match = re.match(r'rowspan\s*=\s*"?(\d+)"?\s*\|\s*(.*)$', cell, flags=re.IGNORECASE | re.DOTALL)
    if match:
        rowspan = int(match.group(1))
        cell = match.group(2)
    return clean_inline(cell), rowspan


def parse_species_templates(cell: str) -> list[dict]:
    results = []
    for template_body in re.findall(r"\{\{MSP/GO\|([^}]+)\}\}", cell):
        parts = [part.strip() for part in template_body.split("|") if part.strip()]
        positional = [part for part in parts if "=" not in part]
        if len(positional) < 2:
            continue
        code = positional[-2]
        species = clean_inline(positional[-1])
        dex_match = re.match(r"^(\d{4})(.+)$", code)
        dex = int(dex_match.group(1)) if dex_match else None
        slug = dex_match.group(2) if dex_match else code
        results.append(
            {
                "species": species,
                "dex": dex,
                "code": code,
                "normalizedToken": normalize_external_token(slug),
            }
        )
    return results


def parse_human_date(value: str, fallback_year: int | None = None, fallback_month: str | None = None) -> str | None:
    raw = value.strip()
    month_day_year = re.match(r"^([A-Za-z]+ \d{1,2}), (\d{4})$", raw)
    if month_day_year:
        return datetime.strptime(raw, "%B %d, %Y").date().isoformat()
    month_day = re.match(r"^([A-Za-z]+) (\d{1,2})$", raw)
    if month_day and fallback_year:
        return datetime.strptime(f"{month_day.group(1)} {month_day.group(2)}, {fallback_year}", "%B %d, %Y").date().isoformat()
    day_year = re.match(r"^(\d{1,2}), (\d{4})$", raw)
    if day_year and fallback_year and fallback_month:
        return datetime.strptime(f"{fallback_month} {day_year.group(1)}, {day_year.group(2)}", "%B %d, %Y").date().isoformat()
    return None


def parse_date_range(value: str) -> tuple[str | None, str | None]:
    text = clean_inline(value)
    if not text:
        return None, None
    if " - " not in text:
        single = parse_human_date(text)
        return single, single
    left, right = [part.strip() for part in text.split(" - ", 1)]
    right_date = parse_human_date(right)
    right_month_match = re.match(r"^([A-Za-z]+)", right)
    right_year_match = re.search(r"(\d{4})$", right)
    fallback_year = int(right_year_match.group(1)) if right_year_match else None
    fallback_month = right_month_match.group(1) if right_month_match else None
    left_date = parse_human_date(left, fallback_year=fallback_year, fallback_month=fallback_month)
    if left_date and right_date:
        return left_date, right_date
    same_month_range = re.match(r"^([A-Za-z]+ \d{1,2}) - (\d{1,2}, \d{4})$", text)
    if same_month_range:
        month = same_month_range.group(1).split()[0]
        start = parse_human_date(same_month_range.group(1), fallback_year=int(same_month_range.group(2).split(",")[-1].strip()))
        end = parse_human_date(same_month_range.group(2), fallback_month=month)
        return start, end
    return None, None


def parse_availability(cell: str, species_names: list[str]) -> list[dict]:
    appearances = []
    for line in cell.splitlines():
        stripped = line.strip()
        if stripped.startswith("* ") and not stripped.startswith("** "):
            content = clean_inline(stripped[2:])
            if ":" not in content:
                continue
            event_label, date_part = [part.strip() for part in content.split(":", 1)]
            start_date, end_date = parse_date_range(date_part)
            appearances.append(
                {
                    "eventLabel": event_label,
                    "startDate": start_date,
                    "endDate": end_date,
                    "speciesOnly": [],
                    "notes": [],
                }
            )
        elif stripped.startswith("** ") and appearances:
            note = clean_inline(stripped[3:])
            appearances[-1]["notes"].append(note)
            if "only" in note.lower():
                restricted = [species for species in species_names if species.lower() in note.lower()]
                if restricted:
                    appearances[-1]["speciesOnly"] = restricted
    return appearances


def build_entries(wikitext: str) -> list[dict]:
    table_text = extract_event_table(wikitext)
    rows = split_rows(table_text)
    by_key = {}
    carried_form_label = None
    carried_rows = 0

    for row in rows:
        cells = split_cells(row)
        if len(cells) >= 4:
            form_label, rowspan = parse_form_cell(cells[0])
            carried_form_label = form_label
            carried_rows = max(0, rowspan - 1)
            pokemon_cell, evolution_cell, availability_cell = cells[1], cells[2], cells[3]
        elif len(cells) == 3 and carried_form_label:
            form_label = carried_form_label
            carried_rows = max(0, carried_rows - 1)
            pokemon_cell, evolution_cell, availability_cell = cells[0], cells[1], cells[2]
        else:
            continue

        templates = parse_species_templates(pokemon_cell) + parse_species_templates(evolution_cell)
        if not templates:
            continue
        species_names = sorted({item["species"] for item in templates})
        appearances = parse_availability(availability_cell, species_names)

        for template in templates:
            token = template["normalizedToken"]
            if not token:
                continue
            applicable = []
            for appearance in appearances:
                species_only = appearance.get("speciesOnly") or []
                if species_only and template["species"] not in species_only:
                    continue
                applicable.append(
                    {
                        "eventLabel": appearance["eventLabel"],
                        "startDate": appearance["startDate"],
                        "endDate": appearance["endDate"],
                    }
                )
            key = (template["species"], token)
            entry = by_key.setdefault(
                key,
                {
                    "species": template["species"],
                    "dex": template["dex"],
                    "bulbapediaCode": template["code"],
                    "normalizedToken": token,
                    "formLabel": form_label,
                    "sourceIds": [SOURCE_ID],
                    "appearances": [],
                },
            )
            entry["appearances"].extend(applicable)

    for entry in by_key.values():
        deduped = []
        seen = set()
        for appearance in entry["appearances"]:
            key = (
                appearance.get("eventLabel"),
                appearance.get("startDate"),
                appearance.get("endDate"),
            )
            if key in seen:
                continue
            seen.add(key)
            deduped.append(appearance)
        deduped.sort(key=lambda item: ((item.get("startDate") or ""), (item.get("eventLabel") or "")))
        entry["appearances"] = deduped

    return sorted(by_key.values(), key=lambda item: (item["dex"] or 0, item["species"], item["normalizedToken"]))


def main():
    parser = argparse.ArgumentParser(description="Fetch and normalize Bulbapedia Event Pokémon (GO) data.")
    parser.add_argument(
        "--out",
        default="scripts/external_snapshots/bulbapedia_event_pokemon_go.json",
    )
    args = parser.parse_args()

    wikitext = fetch_wikitext(PAGE_TITLE)
    entries = build_entries(wikitext)
    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceId": SOURCE_ID,
        "sourceName": SOURCE_NAME,
        "pageTitle": PAGE_TITLE,
        "count": len(entries),
        "entries": entries,
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"Wrote {len(entries)} entries to {args.out}")


if __name__ == "__main__":
    main()
