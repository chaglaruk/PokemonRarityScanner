# Purpose: Fetch and normalize Bulbapedia Event Pokemon GO data.
from __future__ import annotations

import argparse
import http.client
import json
import re
import ssl
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
    tls_context = ssl.create_default_context()
    connection = http.client.HTTPSConnection(
        API_HOST,
        timeout=60,
        context=tls_context,
    )  # nosemgrep: python.lang.security.audit.httpsconnection-detected.httpsconnection-detected -- TLS verification uses Python's default trusted CA store.
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
    token = re.sub(r"[^A-Za-z0-9]+", "_", token).strip("_").lower()
    aliases = {
        "pikachu_world_cap": "world_cap_2020",
        "flower_crown": "flower_crown",
    }
    return aliases.get(token, token) or None


def extract_templates(wikitext: str, template_name: str) -> list[str]:
    pattern = re.compile(r"\{\{" + re.escape(template_name) + r"\|(.*?)\n\}\}", re.DOTALL)
    return pattern.findall(wikitext)


def parse_template(body: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in body.splitlines():
        line = line.strip()
        if not line.startswith("|") or "=" not in line:
            continue
        key, value = line[1:].split("=", 1)
        values[key.strip()] = clean_inline(value)
    return values


def build_snapshot(wikitext: str) -> dict:
    species: dict[int, dict] = {}
    entries: list[dict] = []
    by_species: defaultdict[int, list[dict]] = defaultdict(list)

    for template_name in ("Event Pokémon", "Event Pokémon/GO"):
        for template_body in extract_templates(wikitext, template_name):
            values = parse_template(template_body)
            dex_number_raw = values.get("ndex") or values.get("dex") or ""
            dex_match = re.search(r"\d+", dex_number_raw)
            if not dex_match:
                continue
            dex_number = int(dex_match.group(0))
            name = values.get("name") or values.get("pokemon") or ""
            if not name:
                continue

            form = normalize_external_token(values.get("form", ""))
            costume = normalize_external_token(values.get("costume", ""))
            event_label = values.get("event") or values.get("notes") or ""
            start_date = values.get("start") or values.get("start date") or ""
            end_date = values.get("end") or values.get("end date") or ""

            entry = {
                "dexNumber": dex_number,
                "name": name,
                "form": form,
                "costume": costume,
                "eventLabel": event_label or None,
                "startDate": start_date or None,
                "endDate": end_date or None,
            }
            entries.append(entry)
            by_species[dex_number].append(entry)
            species[dex_number] = {"dexNumber": dex_number, "name": name}

    return {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceId": SOURCE_ID,
        "sourceName": SOURCE_NAME,
        "pageTitle": PAGE_TITLE,
        "count": len(entries),
        "speciesCount": len(species),
        "species": sorted(species.values(), key=lambda item: item["dexNumber"]),
        "entries": sorted(
            entries,
            key=lambda item: (
                item["dexNumber"],
                item.get("form") or "",
                item.get("costume") or "",
                item.get("startDate") or "",
                item.get("eventLabel") or "",
            ),
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    snapshot = build_snapshot(fetch_wikitext(PAGE_TITLE))
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(snapshot, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"Wrote {args.out}: {snapshot['count']} entries")


if __name__ == "__main__":
    main()
