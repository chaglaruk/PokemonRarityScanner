"""Generate local recognition facts from a pinned public Game Master snapshot.

Only species/form identifiers, base stats, types, candy families and level multipliers are emitted.
No images, models, game code, account data or private endpoints are consumed.
The source is the same public numeric reference used by existing metadata scripts:
https://github.com/PokeMiners/game_masters

Reproduce the checked-in asset (network access occurs only in this development tool):
    python scripts/generate_recognition_profiles.py --download
Or provide the exact cached bytes with --game-master PATH. All input hashes and
the immutable source URL are recorded; timestamps/local paths are not emitted.
"""

import argparse
import hashlib
import json
import math
import re
import struct
import unicodedata
import urllib.request
from pathlib import Path


SOURCE_REVISION = "8e227be44f288d34463e23bf04e9b564d3c16f79"
SOURCE_SHA256 = "5c947ac64d1de8859bea1b3bf044609d74b6e8429d53f8cb1aa30a72a46dce84"
SOURCE_URL = f"https://raw.githubusercontent.com/PokeMiners/game_masters/{SOURCE_REVISION}/latest/latest.json"
TYPES = frozenset("normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost dragon dark steel fairy".split())
SOURCE_NAME_ALIASES = {
    # Existing source misspellings also handled by refresh_pogo_species_metadata.mjs.
    "simispour": "simipour", "centiskorchere": "centiskorch", "graafaiai": "grafaiai",
}


def build_cp_multipliers(game_master: list) -> dict[str, float]:
    """Preserve source float32 precision; half levels use the mean of squared CPMs.

    Level 51 includes the active Best Buddy boost on a level-50 Pokemon. Rounding
    half-level multipliers to linear midpoints can change CP after flooring and
    wrongly remove the true species from the recognition candidate set.
    """
    settings = [row["data"].get("playerLevel", {}) for row in game_master
                if row.get("data", {}).get("templateId") == "PLAYER_LEVEL_SETTINGS"]
    if len(settings) != 1:
        raise ValueError("Expected exactly one player-level settings template")
    source = settings[0].get("cpMultiplier", [])
    if not isinstance(source, list) or len(source) < 51:
        raise ValueError("Missing whole-level multipliers through level 51")
    if any(type(value) not in (int, float) or not math.isfinite(value) or not 0 < value < 1
           for value in source[:51]):
        raise ValueError("Invalid whole-level multiplier")
    whole = [struct.unpack("<f", struct.pack("<f", value))[0] for value in source[:51]]
    if any(not 0 < value < 1 for value in whole) or any(a >= b for a, b in zip(whole, whole[1:])):
        raise ValueError("Whole-level multipliers must be strictly increasing within (0, 1)")
    result = {}
    for index, value in enumerate(whole):
        result[f"{index + 1:.1f}"] = value
        if index < 50:
            result[f"{index + 1.5:.1f}"] = math.sqrt((value ** 2 + whole[index + 1] ** 2) / 2)
    return result


def normalize_name(value: str) -> str:
    value = value.lower().replace("_female", "f").replace("_male", "m")
    value = value.replace("♀", "f").replace("♂", "m")
    value = unicodedata.normalize("NFKD", value)
    return re.sub(r"[^a-z0-9]", "", value)


def build_profiles(game_master: list, names: list[str]) -> list[dict]:
    lookup = {normalize_name(name): name for name in names}
    if len(lookup) != len(names):
        raise ValueError("Canonical names contain a normalization collision")
    for source, canonical in SOURCE_NAME_ALIASES.items():
        if canonical in lookup:
            lookup[source] = lookup[canonical]

    def name_for(identifier):
        return lookup.get(normalize_name(identifier))

    settings = [r["data"]["pokemonSettings"] for r in game_master
                if r.get("data", {}).get("pokemonSettings")]
    explicit_species = {s["pokemonId"] for s in settings if s.get("form")}
    grouped = {}

    def add(s, form, stats, type1, type2):
        species = name_for(s["pokemonId"])
        if species is None:
            return  # The asset has the same supported species scope as pokemon_names.json.
        family_id = s.get("familyId", "")
        if not family_id.startswith("FAMILY_"):
            raise ValueError(f"Missing family for {species}")
        candy_species = name_for(family_id.removeprefix("FAMILY_"))
        if candy_species is None:
            raise ValueError(f"Unmapped candy family for {species}: {family_id}")
        values = tuple(stats.get(k) for k in ("baseAttack", "baseDefense", "baseStamina"))
        if any(type(v) is not int or not 1 <= v <= 1000 for v in values):
            raise ValueError(f"Invalid base stats for {species}/{form}")
        types = []
        for raw in (type1, type2):
            if raw is None:
                continue
            value = raw.removeprefix("POKEMON_TYPE_").lower()
            if not raw.startswith("POKEMON_TYPE_") or value not in TYPES:
                raise ValueError(f"Invalid type for {species}/{form}: {raw}")
            if value not in types:
                types.append(value)
        types.sort()
        if not 1 <= len(types) <= 2:
            raise ValueError(f"Missing type for {species}/{form}")
        key = (species, *values, tuple(types), family_id, candy_species)
        if key not in grouped:
            grouped[key] = {"species": species, "forms": set(), "atk": values[0],
                            "def": values[1], "sta": values[2], "types": types,
                            "familyId": family_id, "candySpecies": candy_species}
        grouped[key]["forms"].add(form)

    for s in settings:
        pokemon_id = s["pokemonId"]
        form = s.get("form") or pokemon_id + "_BASE"
        # Species defaults can retain stale types/stats (e.g. Zacian). Explicit
        # form templates are the actual alternatives when they are available.
        if s.get("form") or pokemon_id not in explicit_species:
            add(s, form, s.get("stats", {}), s.get("type"), s.get("type2"))
        # Overrides sometimes exist only on the default template. The type pair
        # is replaced as a whole: Mega Aggron loses its ordinary secondary type.
        for override in s.get("tempEvoOverrides", []):
            if not override.get("tempEvoId"):
                if override.get("stats"):
                    raise ValueError(f"Unidentified temporary evolution for {pokemon_id}")
                continue  # Camera-only source overrides carry no recognition facts.
            add(s, pokemon_id + "/" + override["tempEvoId"], override.get("stats", {}),
                override.get("typeOverride1"), override.get("typeOverride2"))

    result = []
    for key in sorted(grouped):
        profile = grouped[key]
        profile["forms"] = sorted(profile["forms"])
        result.append(profile)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--game-master", type=Path)
    source.add_argument("--download", action="store_true")
    parser.add_argument("--names", type=Path, default=Path("app/src/main/assets/data/pokemon_names.json"))
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/data/recognition_profiles.json"))
    args = parser.parse_args()
    if args.download:
        with urllib.request.urlopen(SOURCE_URL, timeout=60) as response:
            source_bytes = response.read()
    else:
        source_bytes = args.game_master.read_bytes()
    if hashlib.sha256(source_bytes).hexdigest() != SOURCE_SHA256:
        raise ValueError("Game Master hash differs from the pinned source; review/update pin explicitly")
    names_bytes = args.names.read_bytes()
    names = json.loads(names_bytes)
    game_master = json.loads(source_bytes)
    profiles = build_profiles(game_master, names)
    multipliers = build_cp_multipliers(game_master)
    represented = {p["species"] for p in profiles}
    missing = sorted(set(names) - represented)
    if missing:
        raise ValueError(f"Missing canonical species profiles: {missing}")
    provenance = {"url": SOURCE_URL, "revision": SOURCE_REVISION, "sha256": SOURCE_SHA256,
                  "namesSha256": hashlib.sha256(names_bytes).hexdigest()}
    # A profile per line keeps this compact but reviewable and deterministic.
    header = json.dumps({"version": 1, "source": provenance, "speciesCount": len(represented),
                         "cpMultipliers": {"version": 1, "method": "float32_integer_rms_half", "values": multipliers}},
                        ensure_ascii=False, separators=(",", ":"))
    lines = [json.dumps(p, ensure_ascii=False, separators=(",", ":")) for p in profiles]
    output = header[:-1] + ',"profiles":[\n' + ',\n'.join(lines) + '\n]}\n'
    args.output.write_text(output, encoding="utf-8", newline="\n")
    print(f"Generated {len(profiles)} profiles for {len(represented)} species")


if __name__ == "__main__":
    main()
