#!/usr/bin/env python3
"""Build a source-pinned, rights-aware Pokémon GO identity inventory.

The catalog records identifiers and source claims. It never treats a Game
Master entry, a filename, or an unverified third-party event claim as proof
that a form/costume was released or that its pixels are available.
"""

from __future__ import annotations

import argparse
import hashlib
from http.client import HTTPException, HTTPSConnection
import json
import re
import ssl
import sys
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import quote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
PINS_PATH = ROOT / "scripts" / "visual_identity_source_pins.json"
DEFAULT_OUTPUT = ROOT / "scripts" / "data" / "visual_identity_catalog.json"
MAX_SOURCE_BYTES = 2 * 1024 * 1024
MAX_ASSET_SAMPLE_BYTES = 128 * 1024
MAX_GITHUB_TREE_BYTES = 4 * 1024 * 1024
MAX_3D_ASSET_SAMPLE_BYTES = 4 * 1024 * 1024
ALLOWED_SOURCE_HOSTS = frozenset({"api.github.com", "pogoapi.net", "raw.githubusercontent.com"})
LOCALES = ("de", "en", "es", "es-mx", "fr", "hi", "id", "it", "ja", "ko", "pt-br", "ru", "th", "tr", "zh-tw")


def canonical_json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def stable_id(namespace: str, parts: Any) -> str:
    return f"{namespace}:{sha256_bytes(canonical_json_bytes(parts))[:20]}"


def _pokemon_3d_asset_references(
    tree: dict[str, Any] | None,
    pins: dict[str, Any],
    verified_samples: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    if tree is None:
        return []
    source = pins.get("pogoMinersAssets", {})
    expected_tree_sha = str(source.get("pokemon3dTreeSha") or "")
    if tree.get("sha") != expected_tree_sha or tree.get("truncated") is not False:
        raise ValueError("PokeMiners Pokemon 3D asset tree does not match its complete source pin")
    entries = tree.get("tree")
    if not isinstance(entries, list):
        raise ValueError("PokeMiners Pokemon 3D asset tree entries must be a list")

    references: list[dict[str, Any]] = []
    seen_paths: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("type") != "blob":
            continue
        relative_path = entry.get("path")
        if not isinstance(relative_path, str) or not relative_path:
            raise ValueError("PokeMiners Pokemon 3D asset tree contains an invalid path")
        path = PurePosixPath(relative_path)
        if path.is_absolute() or ".." in path.parts or "\\" in relative_path:
            raise ValueError(f"PokeMiners Pokemon 3D asset tree contains an unsafe path: {relative_path}")
        suffix = path.suffix.lower()
        if suffix not in {".fbx", ".obj", ".png"}:
            continue
        if relative_path in seen_paths:
            raise ValueError(f"Duplicate PokeMiners Pokemon 3D asset path: {relative_path}")
        seen_paths.add(relative_path)
        size = entry.get("size")
        blob_sha = str(entry.get("sha") or "")
        if isinstance(size, bool) or not isinstance(size, int) or size < 0:
            raise ValueError(f"PokeMiners Pokemon 3D asset has an invalid size: {relative_path}")
        if re.fullmatch(r"[0-9a-f]{40}", blob_sha) is None:
            raise ValueError(f"PokeMiners Pokemon 3D asset has an invalid Git blob SHA: {relative_path}")

        source_path = f"3D Assets/Pokemon/{relative_path}"
        sample = verified_samples.get(source_path, {})
        if sample and sample.get("gitBlobSha") != blob_sha:
            raise ValueError(f"Verified 3D sample does not match its tree entry: {relative_path}")
        name_candidates = sorted(set(re.findall(r"pm\d{4}_\d{2}", relative_path, flags=re.IGNORECASE)))
        references.append({
            "id": stable_id("visual-asset-ref", source_path),
            "sourcePath": source_path,
            "format": suffix.lstrip("."),
            "referenceKind": "3d_texture_path_hint" if suffix == ".png" else "3d_model_path_hint",
            "contentSha256": sample.get("sha256"),
            "pixelWidth": sample.get("width"),
            "pixelHeight": sample.get("height"),
            "sourceRevision": source.get("observedCommit"),
            "sourceTreeSha": expected_tree_sha,
            "sourceGitBlobSha": blob_sha,
            "sourceByteSize": size,
            "sourceSnapshotDate": pins.get("snapshotDate"),
            "assetNameCandidates": name_candidates,
            "identityMappingStatus": "unresolved_filename_candidate" if name_candidates else "unresolved_no_identity_token",
            "trackedByApplicationRepository": False,
            "verificationStatus": sample.get("decodeStatus", "source_tree_entry_verified"),
            "renderabilityStatus": sample.get("localValidation", "not_downloaded_unverified"),
            "rightsPolicyRef": "PokeMiners/pogo_assets",
            "redistributionRightsStatus": "not_cleared_for_redistribution",
        })
    return sorted(references, key=lambda item: item["sourcePath"])


def _read_json_bytes(payload: bytes, label: str) -> Any:
    try:
        return json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid JSON from {label}: {error}") from error


def _validated_https_target(url: str) -> tuple[str, str]:
    parsed = urlsplit(url)
    host = parsed.hostname
    is_pokeminers_tree = (
        host == "api.github.com"
        and re.fullmatch(r"/repos/PokeMiners/pogo_assets/git/trees/[0-9a-f]{40}", parsed.path) is not None
        and parsed.query == "recursive=1"
    )
    if (
        parsed.scheme != "https"
        or host not in ALLOWED_SOURCE_HOSTS
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port not in (None, 443)
        or (parsed.query and not is_pokeminers_tree)
        or parsed.fragment
        or (host == "api.github.com" and not is_pokeminers_tree)
    ):
        raise ValueError("Catalog fetch URL is not an approved HTTPS source")
    request_target = (parsed.path or "/") + (f"?{parsed.query}" if parsed.query else "")
    if any(ord(character) < 0x20 or character == " " for character in request_target):
        raise ValueError("Catalog fetch URL contains an invalid request path")
    return host, request_target


def _fetch_bytes(url: str, *, max_bytes: int = MAX_SOURCE_BYTES) -> bytes:
    host, request_target = _validated_https_target(url)
    try:
        # Pin certificate and hostname checks explicitly for the build-time fetch.
        connection = HTTPSConnection(host, timeout=20, context=ssl.create_default_context())  # nosemgrep: python.lang.security.audit.httpsconnection-detected.httpsconnection-detected
        try:
            connection.request(
                "GET",
                request_target,
                headers={"User-Agent": "PokemonRarityScanner-visual-catalog/1"},
            )
            response = connection.getresponse()
            if response.status != 200:
                raise RuntimeError(f"Pinned source returned HTTP {response.status}: {url}")
            payload = response.read(max_bytes + 1)
        finally:
            connection.close()
    except (OSError, HTTPException) as error:
        raise RuntimeError(f"Could not fetch pinned catalog input {url}: {error}") from error
    if len(payload) > max_bytes:
        raise ValueError(f"Pinned source exceeded the {max_bytes}-byte cap: {url}")
    return payload


def _fetch_verified_json(url: str, expected_sha256: str) -> tuple[Any, str]:
    payload = _fetch_bytes(url)
    actual = sha256_bytes(payload)
    if actual != expected_sha256:
        raise ValueError(f"Source hash changed for {url}: expected {expected_sha256}, got {actual}")
    return _read_json_bytes(payload, url), actual


def _png_dimensions(payload: bytes, label: str) -> tuple[int, int]:
    import struct
    import zlib

    if len(payload) < 45 or payload[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Pinned visual sample is not a valid PNG header: {label}")
    offset = 8
    header: tuple[int, int, int, int, int, int, int] | None = None
    image_data: list[bytes] = []
    saw_end = False
    palette_seen = False
    while offset + 12 <= len(payload):
        length = struct.unpack(">I", payload[offset:offset + 4])[0]
        chunk_type = payload[offset + 4:offset + 8]
        chunk_end = offset + 12 + length
        if chunk_end > len(payload):
            raise ValueError(f"Truncated PNG chunk in source sample: {label}")
        chunk_data = payload[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack(">I", payload[offset + 8 + length:chunk_end])[0]
        if zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF != expected_crc:
            raise ValueError(f"PNG chunk checksum mismatch in source sample: {label}")
        if chunk_type == b"IHDR":
            if header is not None or offset != 8 or length != 13:
                raise ValueError(f"Invalid PNG IHDR in source sample: {label}")
            header = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"PLTE":
            palette_seen = True
        elif chunk_type == b"IDAT":
            if header is None or saw_end:
                raise ValueError(f"PNG image data is out of order in source sample: {label}")
            image_data.append(chunk_data)
        elif chunk_type == b"IEND":
            if length != 0 or not image_data:
                raise ValueError(f"Invalid PNG IEND in source sample: {label}")
            saw_end = True
            offset = chunk_end
            break
        elif chunk_type[0] & 0x20 == 0:
            raise ValueError(f"Unsupported critical PNG chunk in source sample: {label}")
        offset = chunk_end

    if not saw_end or offset != len(payload) or header is None:
        raise ValueError(f"Incomplete PNG image in source sample: {label}")
    width, height, bit_depth, color_type, compression, filter_method, interlace = header
    if width < 1 or height < 1 or width > 4096 or height > 4096:
        raise ValueError(f"Pinned visual sample has invalid dimensions: {label}")
    valid_depths = {0: {1, 2, 4, 8, 16}, 2: {8, 16}, 3: {1, 2, 4, 8}, 4: {8, 16}, 6: {8, 16}}
    if bit_depth not in valid_depths.get(color_type, set()) or compression != 0 or filter_method != 0 or interlace != 0:
        raise ValueError(f"Unsupported PNG encoding in source sample: {label}")
    if color_type == 3 and not palette_seen:
        raise ValueError(f"Indexed PNG sample is missing its palette: {label}")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    row_size = (width * channels * bit_depth + 7) // 8 + 1
    decoded_size = row_size * height
    if decoded_size > 4 * 1024 * 1024:
        raise ValueError(f"PNG decoded sample exceeds the bounded image size: {label}")
    decoder = zlib.decompressobj()
    decoded = decoder.decompress(b"".join(image_data), decoded_size + 1)
    if decoder.unconsumed_tail or len(decoded) > decoded_size:
        raise ValueError(f"PNG decoded sample exceeds its declared dimensions: {label}")
    decoded += decoder.flush()
    if not decoder.eof or decoder.unused_data or len(decoded) != decoded_size:
        raise ValueError(f"PNG image data does not decode to its declared dimensions: {label}")
    if any(decoded[row * row_size] > 4 for row in range(height)):
        raise ValueError(f"PNG image uses an invalid scanline filter: {label}")
    return width, height


def _fetch_verified_asset_samples(pins: dict[str, Any]) -> dict[str, dict[str, Any]]:
    source = pins.get("pogoMinersAssets", {})
    commit = source.get("observedCommit")
    samples = source.get("verifiedAssetSamples", [])
    if not isinstance(samples, list):
        raise ValueError("PokeMiners verifiedAssetSamples must be a list")
    verified: dict[str, dict[str, Any]] = {}
    for sample in samples:
        relative_path = str(sample["path"])
        if relative_path in verified:
            raise ValueError(f"Duplicate pinned asset sample path: {relative_path}")
        url = f"https://raw.githubusercontent.com/PokeMiners/pogo_assets/{commit}/{quote(relative_path, safe='/')}"
        payload = _fetch_bytes(url, max_bytes=MAX_ASSET_SAMPLE_BYTES)
        actual = sha256_bytes(payload)
        expected = str(sample["sha256"])
        if actual != expected:
            raise ValueError(f"Asset hash changed for {relative_path}: expected {expected}, got {actual}")
        width, height = _png_dimensions(payload, relative_path)
        verified[relative_path] = {
            "sha256": actual,
            "width": width,
            "height": height,
            "sourceCommit": commit,
            "decodeStatus": "full_png_scanline_stream_verified",
        }
    return verified


def _fetch_pinned_pokemon_3d_asset_tree(pins: dict[str, Any]) -> dict[str, Any]:
    source = pins.get("pogoMinersAssets", {})
    tree_sha = str(source.get("pokemon3dTreeSha") or "")
    if re.fullmatch(r"[0-9a-f]{40}", tree_sha) is None:
        raise ValueError("PokeMiners Pokemon 3D asset tree SHA is missing or invalid")
    url = f"https://api.github.com/repos/PokeMiners/pogo_assets/git/trees/{tree_sha}?recursive=1"
    tree = _read_json_bytes(_fetch_bytes(url, max_bytes=MAX_GITHUB_TREE_BYTES), url)
    if tree.get("sha") != tree_sha:
        raise ValueError(f"PokeMiners Pokemon 3D tree SHA mismatch: expected {tree_sha}, got {tree.get('sha')}")
    if tree.get("truncated") is not False or not isinstance(tree.get("tree"), list):
        raise ValueError("PokeMiners Pokemon 3D asset tree is truncated or malformed")
    return tree


def _fetch_verified_3d_asset_samples(
    pins: dict[str, Any],
    tree: dict[str, Any],
) -> dict[str, dict[str, Any]]:
    source = pins.get("pogoMinersAssets", {})
    commit = str(source.get("observedCommit") or "")
    tree_sha = str(source.get("pokemon3dTreeSha") or "")
    samples = source.get("verified3DAssetSamples", [])
    if not isinstance(samples, list):
        raise ValueError("PokeMiners verified3DAssetSamples must be a list")
    tree_entries = {
        str(item.get("path")): item
        for item in tree.get("tree", [])
        if isinstance(item, dict) and item.get("type") == "blob"
    }
    verified: dict[str, dict[str, Any]] = {}
    for sample in samples:
        relative_path = str(sample.get("path") or "")
        subtree_path = relative_path.removeprefix("Pokemon/")
        source_path = f"3D Assets/Pokemon/{subtree_path}"
        path = PurePosixPath(subtree_path)
        if (
            not subtree_path
            or path.is_absolute()
            or ".." in path.parts
            or "\\" in subtree_path
            or relative_path in verified
        ):
            raise ValueError(f"Invalid or duplicate pinned 3D asset sample path: {relative_path}")
        tree_entry = tree_entries.get(subtree_path)
        if tree_entry is None:
            raise ValueError(f"Pinned 3D asset sample is absent from the pinned tree: {relative_path}")
        size = tree_entry.get("size")
        if isinstance(size, bool) or not isinstance(size, int) or size < 0 or size > MAX_3D_ASSET_SAMPLE_BYTES:
            raise ValueError(f"Pinned 3D asset sample exceeds its bounded size: {relative_path}")
        url = f"https://raw.githubusercontent.com/PokeMiners/pogo_assets/{commit}/{quote(source_path, safe='/')}"
        payload = _fetch_bytes(url, max_bytes=MAX_3D_ASSET_SAMPLE_BYTES)
        if len(payload) != size:
            raise ValueError(f"Asset byte size changed for {relative_path}: expected {size}, got {len(payload)}")
        # Git's tree format defines blob IDs with SHA-1; SHA-256 is independently
        # checked against the pinned source sample below for content integrity.
        git_blob_sha = hashlib.sha1(  # nosemgrep: python.lang.security.insecure-hash-algorithms.insecure-hash-algorithm-sha1 -- required to verify Git blob identity, not used as a security digest
            b"blob " + str(len(payload)).encode("ascii") + b"\0" + payload
        ).hexdigest()
        if git_blob_sha != tree_entry.get("sha"):
            raise ValueError(f"Git blob hash changed for {relative_path}: expected {tree_entry.get('sha')}, got {git_blob_sha}")
        actual_sha256 = sha256_bytes(payload)
        expected_sha256 = str(sample.get("sha256") or "")
        if actual_sha256 != expected_sha256:
            raise ValueError(f"Asset SHA-256 changed for {relative_path}: expected {expected_sha256}, got {actual_sha256}")
        suffix = path.suffix.lower()
        width = height = None
        decode_status = "git_blob_and_sha256_verified"
        if suffix == ".png":
            width, height = _png_dimensions(payload, relative_path)
            decode_status = "git_blob_sha256_and_png_scanline_stream_verified"
        verified[source_path] = {
            "sha256": actual_sha256,
            "width": width,
            "height": height,
            "sourceCommit": commit,
            "sourceTreeSha": tree_sha,
            "gitBlobSha": git_blob_sha,
            "decodeStatus": decode_status,
            "localValidation": str(sample.get("localValidation") or "not_locally_rendered"),
        }
    return verified


def fetch_pinned_sources(pins: dict[str, Any]) -> dict[str, Any]:
    """Fetch only small, pinned JSON indexes; never download visual assets."""
    pogo = pins["pogoApi"]
    api_hashes_url = pogo["baseUrl"] + "api_hashes.json"
    api_hashes_payload = _fetch_bytes(api_hashes_url)
    api_hashes_sha256 = sha256_bytes(api_hashes_payload)
    if api_hashes_sha256 != pogo["apiHashesResponseSha256"]:
        raise ValueError(
            f"PoGoAPI hash registry changed: expected {pogo['apiHashesResponseSha256']}, got {api_hashes_sha256}"
        )
    api_hashes = _read_json_bytes(api_hashes_payload, api_hashes_url)

    pogo_payloads: dict[str, Any] = {}
    for name in ("pokemonNames", "releasedPokemon"):
        source = pogo[name]
        advertised = api_hashes.get(source["path"], {}).get("hash_sha256")
        if advertised != source["sha256"]:
            raise ValueError(
                f"PoGoAPI advertised hash changed for {source['path']}: "
                f"expected {source['sha256']}, got {advertised}"
            )
        payload, _ = _fetch_verified_json(pogo["baseUrl"] + source["path"], source["sha256"])
        pogo_payloads[name] = payload

    watwow = pins["watWowMap"]
    raw_base = f"https://raw.githubusercontent.com/WatWowMap/pogo-data-api/{watwow['commit']}/"
    watwow_payloads: dict[str, Any] = {}
    for relative_path, expected_hash in sorted(watwow["files"].items()):
        payload, _ = _fetch_verified_json(raw_base + relative_path, expected_hash)
        watwow_payloads[relative_path] = payload

    pokemon_translations = {
        locale: watwow_payloads[f"data/v1/translations/{locale}/pokemon.json"]
        for locale in LOCALES
    }
    form_translations = {
        locale: watwow_payloads[f"data/v1/translations/{locale}/forms.json"]
        for locale in LOCALES
    }
    pokemon_3d_tree = _fetch_pinned_pokemon_3d_asset_tree(pins)
    return {
        "pokemonNames": pogo_payloads["pokemonNames"],
        "releasedPokemon": pogo_payloads["releasedPokemon"],
        "pokemon": watwow_payloads["data/v1/pokemon.json"],
        "forms": watwow_payloads["data/v1/forms.json"],
        "costumes": watwow_payloads["data/v1/costumes.json"],
        "pokemonTranslations": pokemon_translations,
        "formTranslations": form_translations,
        "verifiedAssetSamples": _fetch_verified_asset_samples(pins),
        "pokemon3dAssetTree": pokemon_3d_tree,
        "verified3DAssetSamples": _fetch_verified_3d_asset_samples(pins, pokemon_3d_tree),
    }


def _dex(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"Invalid National Dex ID in {label}: {value!r}")
    try:
        result = int(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"Invalid National Dex ID in {label}: {value!r}") from error
    if result < 1:
        raise ValueError(f"Invalid National Dex ID in {label}: {value!r}")
    return result


def _source_rows(value: Any, field: str) -> list[dict[str, Any]]:
    rows = value.get(field, []) if isinstance(value, dict) else value
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError(f"Expected a list of records at {field}")
    return rows


def _identity_key(row: dict[str, Any]) -> tuple[int, str, str | None]:
    return (_dex(row.get("dex"), "variant catalog"), str(row.get("formId", "00")), row.get("variantId"))


def _event_source_confidence(source_ids: list[str]) -> str:
    if "bulbapedia:event-pokemon-go" in source_ids:
        return "secondary_source_claim_unverified_by_official_record"
    if any(item.startswith("manual:") for item in source_ids):
        return "manual_source_claim_without_independent_event_link"
    if any("propagation" in item for item in source_ids):
        return "propagated_source_claim_unverified"
    return "repository_metadata_claim_unverified"


def _event_name(record: dict[str, Any]) -> str | None:
    value = record.get("eventLabel") or record.get("eventName") or record.get("label")
    return str(value).strip() if value is not None and str(value).strip() else None


def _event_dates(record: dict[str, Any]) -> tuple[str | None, str | None]:
    start = record.get("startDate", record.get("eventStart"))
    end = record.get("endDate", record.get("eventEnd"))
    return (str(start) if start else None, str(end) if end else None)


def _event_candidates(row: dict[str, Any]) -> list[tuple[dict[str, Any], str]]:
    result: list[tuple[dict[str, Any], str]] = []
    history = row.get("historicalEvents")
    if isinstance(history, list):
        result.extend((event, f"history:{index}") for index, event in enumerate(history) if isinstance(event, dict))
    primary = {
        "eventLabel": row.get("eventLabel"),
        "eventStart": row.get("eventStart"),
        "eventEnd": row.get("eventEnd"),
    }
    if _event_name(primary):
        signature = (_event_name(primary), *_event_dates(primary))
        if not any((_event_name(event), *_event_dates(event)) == signature for event, _ in result):
            result.append((primary, "primary"))
    return result


def build_catalog(
    *,
    inputs: dict[str, Any],
    pins: dict[str, Any],
    repo_input_hashes: dict[str, str],
    repository_commit: str,
    tracked_paths: set[str] | None = None,
) -> dict[str, Any]:
    """Normalize pinned source facts while retaining unresolved provenance."""
    tracked_paths = {path.replace("\\", "/") for path in (tracked_paths or set())}
    pokemon_rows = _source_rows(inputs["pokemon"], "pokemon")
    form_rows = _source_rows(inputs["forms"], "forms")
    costume_rows = _source_rows(inputs["costumes"], "costumes")
    variant_rows = _source_rows(inputs["variantCatalog"], "entries")
    authoritative_rows = _source_rows(inputs["authoritativeVariants"], "entries")
    costume_signature_rows = _source_rows(inputs["costumeSignatures"], "signatures")
    shiny_signature_rows = _source_rows(inputs["shinySignatures"], "entries")

    canonical_names: dict[int, str] = {}
    name_source = inputs["pokemonNames"]
    if not isinstance(name_source, dict):
        raise ValueError("PoGoAPI pokemon_names input must be an object")
    for key, record in name_source.items():
        dex = _dex(record.get("id", key), "PoGoAPI pokemon_names")
        name = record.get("name")
        if not isinstance(name, str) or not name.strip() or dex in canonical_names:
            raise ValueError(f"Missing or duplicate canonical species name for dex {dex}")
        canonical_names[dex] = name.strip()

    released_source = inputs["releasedPokemon"]
    if not isinstance(released_source, dict):
        raise ValueError("PoGoAPI released_pokemon input must be an object")
    released_ids: set[int] = set()
    for key, record in released_source.items():
        dex = _dex(record.get("id", key), "PoGoAPI released_pokemon")
        if dex in released_ids:
            raise ValueError(f"Duplicate released species ID {dex}")
        released_ids.add(dex)
    if not released_ids.issubset(canonical_names):
        raise ValueError("Release feed contains species missing from the canonical names source")

    pokemon_by_dex: dict[int, dict[str, Any]] = {}
    for record in pokemon_rows:
        dex = _dex(record.get("pokedexId"), "WatWowMap pokemon")
        if dex in pokemon_by_dex:
            raise ValueError(f"Duplicate GO species record for dex {dex}")
        if dex not in canonical_names:
            raise ValueError(f"GO species {dex} has no PoGoAPI canonical name")
        pokemon_by_dex[dex] = record
    if set(pokemon_by_dex) != set(canonical_names):
        raise ValueError("PoGoAPI and WatWowMap species rosters do not match exactly")

    forms_by_id: dict[int, dict[str, Any]] = {}
    forms_by_proto: dict[str, list[dict[str, Any]]] = {}
    for form in form_rows:
        if "formId" not in form:
            continue
        form_id = int(form["formId"])
        if form_id in forms_by_id:
            raise ValueError(f"Duplicate GO form ID {form_id}")
        forms_by_id[form_id] = form
        proto = form.get("proto")
        if isinstance(proto, str) and proto:
            forms_by_proto.setdefault(proto, []).append(form)

    localized_species = inputs["pokemonTranslations"]
    localized_forms = inputs["formTranslations"]
    for locale in LOCALES:
        if locale not in localized_species or locale not in localized_forms:
            raise ValueError(f"Missing localization source for {locale}")

    species: list[dict[str, Any]] = []
    species_forms: list[dict[str, Any]] = []
    form_definitions: dict[int, dict[str, Any]] = {}
    name_conflicts: list[dict[str, Any]] = []
    for dex in sorted(pokemon_by_dex):
        record = pokemon_by_dex[dex]
        go_name = record.get("pokemonName")
        if not isinstance(go_name, str) or not go_name.strip():
            raise ValueError(f"Missing GO dataset species name for dex {dex}")
        if canonical_names[dex] != go_name.strip():
            name_conflicts.append({"speciesId": f"national-dex:{dex:04d}", "pogoApiName": canonical_names[dex], "watWowMapName": go_name.strip()})
        names: dict[str, str] = {}
        for locale in LOCALES:
            translated = localized_species[locale].get(f"poke_{dex}")
            if isinstance(translated, str) and translated.strip():
                names[locale] = translated.strip()
        species_id = f"national-dex:{dex:04d}"
        release_status = "released" if dex in released_ids else "uncertain"
        species_record = {
            "id": species_id,
            "nationalDexId": dex,
            "canonicalName": canonical_names[dex],
            "localizedNames": names,
            "releaseStatus": release_status,
            "releaseEvidenceCode": "pogoapi_listed_as_released" if dex in released_ids else "pogoapi_feed_absence_only",
            "defaultGoFormId": record.get("defaultFormId"),
            "goFormCount": len(set(int(value) for value in record.get("forms", []))),
        }
        if canonical_names[dex] != go_name.strip():
            species_record["goDatasetNameAlias"] = go_name.strip()
        species.append(species_record)

        form_ids = sorted(set(int(value) for value in record.get("forms", [])))
        default_form = record.get("defaultFormId")
        for form_id in form_ids:
            source_form = forms_by_id.get(form_id)
            form_names: dict[str, str] = {}
            for locale in LOCALES:
                translated = localized_forms[locale].get(f"form_{form_id}")
                if isinstance(translated, str) and translated.strip():
                    form_names[locale] = translated.strip()
            if form_id not in form_definitions:
                form_definitions[form_id] = {
                    "id": f"go-form-definition:{form_id}",
                    "sourceFormId": form_id,
                    "sourceFormProto": source_form.get("proto") if source_form else None,
                    "sourceFormName": source_form.get("formName") if source_form else None,
                    "localizedFormNames": form_names,
                }
            species_forms.append({
                "id": f"go-form:{dex:04d}:{form_id}",
                "speciesId": species_id,
                "sourceFormId": form_id,
                "formDefinitionId": f"go-form-definition:{form_id}",
                "sourceFormIsCostume": bool(source_form and source_form.get("isCostume") is True),
                "defaultInGoDataset": form_id == default_form,
                "releaseStatus": "uncertain",
                "releaseEvidence": "Game Master possible-form presence does not prove player release",
                "appearanceDifferenceStatus": "unverified",
                "variantCatalogFormMapping": None,
                "variantCatalogFormMappingStatus": "unresolved_identifier_namespace",
            })

    costume_concepts: list[dict[str, Any]] = []
    for record in sorted(costume_rows, key=lambda row: int(row.get("id", -1))):
        costume_id = int(record["id"])
        name = str(record.get("name") or "")
        costume_concepts.append({
            "id": f"go-costume-concept:{costume_id}",
            "sourceCostumeId": costume_id,
            "sourceProto": record.get("proto"),
            "sourceName": name,
            "localizedNames": {"en": name} if name else {},
            "noEvolveSetting": record.get("noEvolve"),
            "releaseStatus": "uncertain",
            "releaseEvidenceCode": "game_master_presence_only",
            "speciesMappingStatus": "unresolved",
        })

    verified_asset_samples = inputs.get("verifiedAssetSamples", {})
    if not isinstance(verified_asset_samples, dict):
        raise ValueError("verifiedAssetSamples must be a path-keyed object")
    verified_3d_asset_samples = inputs.get("verified3DAssetSamples", {})
    if not isinstance(verified_3d_asset_samples, dict):
        raise ValueError("verified3DAssetSamples must be a path-keyed object")
    pokemon_3d_asset_refs = _pokemon_3d_asset_references(
        inputs.get("pokemon3dAssetTree"), pins, verified_3d_asset_samples
    )
    costume_signature_keys = {str(record.get("key")) for record in costume_signature_rows if record.get("key")}
    shiny_signature_keys = {str(record.get("key")) for record in shiny_signature_rows if record.get("key")}
    identity_records: dict[tuple[int, str, str | None], dict[str, Any]] = {}
    appearance_rows: list[dict[str, Any]] = []
    assets: dict[str, dict[str, Any]] = {}
    identity_proto_names: dict[tuple[int, str, str | None], set[str]] = {}
    seen_appearance_keys: set[tuple[tuple[int, str, str | None], bool]] = set()
    for row in variant_rows:
        key = _identity_key(row)
        identity_id = stable_id("visual-identity", list(key))
        kind = "costume_candidate" if bool(row.get("isCostumeLike")) else str(row.get("variantClass") or "unknown")
        shiny = bool(row.get("isShiny"))
        appearance_key = (key, shiny)
        if appearance_key in seen_appearance_keys:
            raise ValueError(f"Duplicate shiny/normal appearance for source identity {key}")
        seen_appearance_keys.add(appearance_key)
        existing = identity_records.get(key)
        source_proto = row.get("gameMasterFormName")
        if isinstance(source_proto, str) and source_proto.strip():
            identity_proto_names.setdefault(key, set()).add(source_proto.strip())
        if existing is None:
            existing = {
                "id": identity_id,
                "speciesId": f"national-dex:{key[0]:04d}",
                "catalogFormId": key[1],
                "catalogVariantId": key[2],
                "sourceVariantClass": str(row.get("variantClass") or "unknown"),
                "identityKind": kind,
                "isCostumeCandidate": bool(row.get("isCostumeLike")),
                "candidateLabel": row.get("variantLabel"),
                "identityEvidenceStatus": "repository_metadata_candidate_unverified",
                "releaseStatus": "uncertain",
                "releaseHistoryStatus": "no_release_history_recorded",
            }
            identity_records[key] = existing
        else:
            if existing["identityKind"] != kind or existing["sourceVariantClass"] != str(row.get("variantClass") or "unknown"):
                raise ValueError(f"Conflicting classification for source identity {key}")
            if existing.get("candidateLabel") is None and row.get("variantLabel") is not None:
                existing["candidateLabel"] = row.get("variantLabel")

        raw_path = str(row.get("assetPath") or "").replace("\\", "/")
        asset_ref_id: str | None = None
        if raw_path:
            relative_source_path = raw_path.removeprefix("external/pogo_assets/")
            asset_ref_id = stable_id("visual-asset-ref", relative_source_path)
            if asset_ref_id not in assets:
                tracked = raw_path in tracked_paths
                suffix = PurePosixPath(relative_source_path).suffix.lower().lstrip(".") or None
                if "." in relative_source_path:
                    reference_kind = "2d_icon_path_hint" if "pokemon - 256x256/" in relative_source_path.lower() else "asset_path_hint"
                else:
                    reference_kind = "unknown"
                assets[asset_ref_id] = {
                    "id": asset_ref_id,
                    "sourcePath": relative_source_path,
                    "format": suffix,
                    "referenceKind": reference_kind,
                    "contentSha256": verified_asset_samples.get(relative_source_path, {}).get("sha256"),
                    "pixelWidth": verified_asset_samples.get(relative_source_path, {}).get("width"),
                    "pixelHeight": verified_asset_samples.get(relative_source_path, {}).get("height"),
                    "sourceRevision": verified_asset_samples.get(relative_source_path, {}).get("sourceCommit"),
                    "trackedByApplicationRepository": tracked,
                    "verificationStatus": "source_hash_and_full_png_decode_verified" if relative_source_path in verified_asset_samples and verified_asset_samples[relative_source_path].get("decodeStatus") == "full_png_scanline_stream_verified" else "source_hash_and_png_header_verified" if relative_source_path in verified_asset_samples else "source_path_only",
                    "renderabilityStatus": "png_scanline_stream_decoded; visual identity quality not qualified" if relative_source_path in verified_asset_samples and verified_asset_samples[relative_source_path].get("decodeStatus") == "full_png_scanline_stream_verified" else "format_header_verified; full decode not verified" if relative_source_path in verified_asset_samples else "not_downloaded_unverified",
                    "rightsPolicyRef": "PokeMiners/pogo_assets",
                    "redistributionRightsStatus": "not_cleared_for_redistribution",
                }

        appearance_id = stable_id("appearance", [identity_id, shiny, row.get("spriteKey")])
        descriptor_refs: list[str] = []
        if row.get("assetKey") in costume_signature_keys:
            descriptor_refs.append(f"costume_signature:{row['assetKey']}")
        if row.get("assetKey") in shiny_signature_keys:
            descriptor_refs.append(f"shiny_signature:{row['assetKey']}")
        appearance_rows.append({
            "id": appearance_id,
            "visualIdentityId": identity_id,
            "spriteKey": row.get("spriteKey"),
            "isShiny": shiny,
            "assetRefId": asset_ref_id,
            "descriptorRefs": descriptor_refs,
        })

    go_costume_records: dict[tuple[int, int], dict[str, Any]] = {}
    visual_form_links: dict[tuple[int, int], list[dict[str, str]]] = {}
    for key, identity in identity_records.items():
        proto_names = sorted(identity_proto_names.get(key, set()))
        identity["sourceGameMasterFormNames"] = proto_names
        if len(proto_names) != 1:
            identity["gameMasterCrosswalkStatus"] = "missing_or_conflicting_exact_proto"
            continue
        proto = proto_names[0]
        candidates = forms_by_proto.get(proto, [])
        species_form_ids = {int(value) for value in pokemon_by_dex[key[0]].get("forms", [])}
        exact_forms = [form for form in candidates if int(form["formId"]) in species_form_ids and form.get("isCostume") is True]
        if len(exact_forms) != 1:
            identity["gameMasterCrosswalkStatus"] = "no_unique_species_costume_form_match"
            continue
        source_form = exact_forms[0]
        form_id = int(source_form["formId"])
        costume_identity_id = f"go-costume-identity:{key[0]:04d}:{form_id}"
        identity["sourceGoFormId"] = form_id
        identity["goCostumeIdentityId"] = costume_identity_id
        identity["gameMasterCrosswalkStatus"] = "exact_proto_and_species_form_membership"
        if identity["isCostumeCandidate"]:
            visual_form_links.setdefault((key[0], form_id), []).append({
                "visualIdentityId": identity["id"],
                "catalogFormId": key[1],
                "catalogVariantId": key[2] or "",
            })
        go_costume_records.setdefault((key[0], form_id), {
            "id": costume_identity_id,
            "speciesId": identity["speciesId"],
            "sourceGoFormId": form_id,
            "sourceFormProto": proto,
            "sourceFormName": source_form.get("formName"),
            "identityEvidenceStatus": "game_master_costume_form_exact_species_mapping",
            "releaseStatus": "uncertain",
            "releaseEvidence": "Game Master membership alone does not prove release",
            "runtimeEventInferenceAllowed": False,
            "visualIdentityIds": [],
            "canonicalEventIds": [],
        })

    for key, links in visual_form_links.items():
        go_costume_records[key]["visualIdentityIds"] = sorted({link["visualIdentityId"] for link in links})
        species_dex, form_id = key
        go_form = next(
            item for item in species_forms
            if item["speciesId"] == f"national-dex:{species_dex:04d}" and item["sourceFormId"] == form_id
        )
        ordered_links = sorted(links, key=lambda item: (item["catalogFormId"], item["catalogVariantId"], item["visualIdentityId"]))
        catalog_form_ids = sorted({link["catalogFormId"] for link in ordered_links})
        go_form["variantCatalogFormMappings"] = ordered_links
        go_form["variantCatalogFormMapping"] = catalog_form_ids[0] if len(catalog_form_ids) == 1 else None
        go_form["variantCatalogFormMappingStatus"] = "exact_species_proto_and_membership"
        for link in ordered_links:
            identity_record = next(item for item in identity_records.values() if item["id"] == link["visualIdentityId"])
            if identity_record["identityEvidenceStatus"] == "repository_metadata_candidate_unverified":
                identity_record["identityEvidenceStatus"] = "exact_species_game_master_costume_crosswalk"

    event_records: dict[str, dict[str, Any]] = {}
    identity_event_links: set[tuple[str, str]] = set()
    authoritative_match_count = 0
    for auth_row in authoritative_rows:
        key = _identity_key(auth_row)
        identity = identity_records.get(key)
        if identity is None:
            continue
        authoritative_match_count += 1
        source_ids = sorted({str(value) for value in auth_row.get("sourceIds", []) if value})
        for event_record, position in _event_candidates(auth_row):
            name = _event_name(event_record)
            if not name:
                continue
            start, end = _event_dates(event_record)
            scope = None if start and end else identity["id"]
            event_key = [source_ids, name, start, end, scope, position if scope else None]
            event_id = stable_id("event-observation", event_key)
            event_records.setdefault(event_id, {
                "id": event_id,
                "canonicalEventId": None,
                "sourceClaimName": name,
                "startDateClaim": start,
                "endDateClaim": end,
                "sourceIds": source_ids,
                "verificationStatus": _event_source_confidence(source_ids),
                "eventIdentityStatus": "unresolved_source_observation",
            })
            identity_event_links.add((identity["id"], event_id))

    canonical_events: list[dict[str, Any]] = []
    canonical_event_costume_links: list[dict[str, Any]] = []
    official_release_form_ids: set[tuple[int, int]] = set()
    for source_event in pins.get("officialEventEvidence", []):
        event_id = str(source_event["id"])
        verified_form_ids = sorted({int(value) for value in source_event.get("verifiedCostumeFormIds", [])})
        canonical_events.append({
            "id": event_id,
            "name": str(source_event["name"]),
            "startDate": str(source_event["startDate"]),
            "endDate": str(source_event["endDate"]),
            "sourceUrl": str(source_event["sourceUrl"]),
            "sourceType": str(source_event["sourceType"]),
            "sourceDateVerified": pins["snapshotDate"],
            "verifiedClaim": str(source_event["claim"]),
            "availabilityRestriction": str(source_event["availability"]),
        })
        for form_id in verified_form_ids:
            linked_costumes = [
                record for (dex, candidate_form_id), record in go_costume_records.items()
                if candidate_form_id == form_id and dex == 25
            ]
            if len(linked_costumes) != 1:
                continue
            costume = linked_costumes[0]
            canonical_event_costume_links.append({
                "canonicalEventId": event_id,
                "goCostumeIdentityId": costume["id"],
                "linkEvidenceStatus": "official_source_exact_form_name_and_pinned_game_master_proto",
                "runtimeEventInferenceAllowed": False,
            })
            official_release_form_ids.add((25, form_id))
            if event_id not in costume["canonicalEventIds"]:
                costume["canonicalEventIds"].append(event_id)
            costume["releaseStatus"] = "released"
            costume["releaseEvidence"] = "official event announcement linked to exact species Game Master costume form"
            for species_form in species_forms:
                if species_form["speciesId"] == f"national-dex:0025" and species_form["sourceFormId"] == form_id:
                    species_form["releaseStatus"] = "released"
                    species_form["releaseEvidence"] = f"official_event:{event_id}"
            for identity in identity_records.values():
                if identity.get("goCostumeIdentityId") == costume["id"]:
                    identity["releaseStatus"] = "released"
                    identity["identityEvidenceStatus"] = "exact_species_game_master_crosswalk_and_official_release"

    for costume in go_costume_records.values():
        costume["canonicalEventIds"] = sorted(set(costume["canonicalEventIds"]))

    for identity in identity_records.values():
        linked = sorted(event_id for visual_id, event_id in identity_event_links if visual_id == identity["id"])
        identity["releaseHistoryStatus"] = (
            "officially_verified_release_history" if identity.get("releaseStatus") == "released"
            else "partial_unverified_source_claims" if linked
            else "no_release_history_recorded"
        )

    event_links = [
        {"visualIdentityId": visual_id, "eventObservationId": event_id, "linkStatus": "source_claim_unverified"}
        for visual_id, event_id in sorted(identity_event_links)
    ]
    for asset in pokemon_3d_asset_refs:
        if asset["id"] in assets:
            raise ValueError(f"Duplicate visual asset source path: {asset['sourcePath']}")
        assets[asset["id"]] = asset
    identities = sorted(identity_records.values(), key=lambda item: (item["speciesId"], item["catalogFormId"], item["catalogVariantId"] or ""))
    appearances = sorted(appearance_rows, key=lambda item: item["id"])
    species_form_rows = sorted(species_forms, key=lambda item: (item["speciesId"], item["sourceFormId"]))
    form_definition_rows = [form_definitions[form_id] for form_id in sorted(form_definitions)]
    events = [event_records[event_id] for event_id in sorted(event_records)]
    asset_rows = [assets[asset_id] for asset_id in sorted(assets)]

    released_ids_with_visuals = {item["nationalDexId"] for item in species if item["releaseStatus"] == "released"}
    visual_dex = {key[0] for key in identity_records}
    localized_species_counts = {
        locale: sum(locale in item["localizedNames"] for item in species)
        for locale in LOCALES
    }
    localized_form_counts = {
        locale: sum(locale in item["localizedFormNames"] for item in form_definition_rows)
        for locale in LOCALES
    }
    costume_candidate_count = sum(item["isCostumeCandidate"] for item in identities)
    identities_with_event_claims = {visual_id for visual_id, _ in identity_event_links}
    three_dimensional_assets = [item for item in asset_rows if item["referenceKind"] in {"3d_model_path_hint", "3d_texture_path_hint"}]
    three_dimensional_models = [item for item in three_dimensional_assets if item["referenceKind"] == "3d_model_path_hint"]
    three_dimensional_textures = [item for item in three_dimensional_assets if item["referenceKind"] == "3d_texture_path_hint"]
    catalog = {
        "schemaVersion": 1,
        "catalogVersion": "1.1.0",
        "sourceCutoffDate": pins["snapshotDate"],
        "repositorySourceCommit": repository_commit,
        "sourceManifest": {
            "pogoApi": pins["pogoApi"],
            "watWowMap": pins["watWowMap"],
            "pogoMinersAssets": pins["pogoMinersAssets"],
            "officialEventEvidence": pins.get("officialEventEvidence", []),
            "repositoryInputs": repo_input_hashes,
            "identityRules": [
                "A release-feed absence remains uncertain and is not promoted to a confirmed unreleased state.",
                "Game Master species, form, or costume presence is not proof of player release.",
                "Repository costume flags create candidates only; they are not verified pixel identities.",
                "Visual identity is keyed by exact species, repository form ID, and repository variant ID; event labels never define costume identity.",
                "Events without an exact external event identifier remain source observations; pixels must not infer a specific event.",
                "3D model and texture filename tokens remain unresolved candidates until exact identity crosswalk evidence exists.",
                "Pinned 3D tree paths and Git blob SHAs are inventory metadata only; no raw visual assets are emitted or packaged.",
                "Eight pinned 2D samples and three pinned 3D samples have verified source hashes; the 3D Bidoof rig imported and rendered six views locally, but this does not qualify a production visual classifier.",
            ],
        },
        "identifierNamespaces": {
            "species": "National Dex ID shared by PoGoAPI and WatWowMap pokemon.pokedexId",
            "goForm": "WatWowMap/pogo-data-api numeric formId",
            "catalogForm": "existing repository variant_catalog.formId string; crosswalk only when an exact Game Master proto belongs to the same species",
            "catalogVariant": "existing repository variant_catalog.variantId exact source string",
            "costumeConcept": "WatWowMap/pogo-data-api numeric costume id; not crosswalked to repository variantId",
        },
        "species": species,
        "goFormDefinitions": form_definition_rows,
        "goForms": species_form_rows,
        "gameMasterCostumeConcepts": costume_concepts,
        "goCostumeIdentities": sorted(go_costume_records.values(), key=lambda item: item["id"]),
        "visualIdentities": identities,
        "appearances": appearances,
        "visualAssets": asset_rows,
        "eventObservations": events,
        "identityEventLinks": event_links,
        "canonicalEvents": sorted(canonical_events, key=lambda item: item["id"]),
        "canonicalEventCostumeLinks": sorted(canonical_event_costume_links, key=lambda item: (item["canonicalEventId"], item["goCostumeIdentityId"])),
        "nameSourceConflicts": sorted(name_conflicts, key=lambda item: item["speciesId"]),
        "coverage": {
            "speciesRecords": len(species),
            "speciesReleasedByPogoApi": sum(item["releaseStatus"] == "released" for item in species),
            "speciesReleaseUncertainFromFeedAbsence": sum(item["releaseStatus"] == "uncertain" for item in species),
            "speciesConfirmedNotReleased": 0,
            "releasedSpeciesWithRepositoryVisualIdentityCandidates": len(released_ids_with_visuals & visual_dex),
            "releasedSpeciesWithoutRepositoryVisualIdentityCandidates": len(released_ids_with_visuals - visual_dex),
            "localizedSpeciesNamesByLocale": localized_species_counts,
            "speciesFormMappings": len(species_form_rows),
            "formsWithKnownReleaseStatus": sum(item["releaseStatus"] != "uncertain" for item in species_form_rows),
            "localizedFormNamesByLocale": localized_form_counts,
            "unresolvedCatalogFormCrosswalks": sum(item["variantCatalogFormMappingStatus"] == "unresolved_identifier_namespace" for item in species_form_rows),
            "exactCatalogFormCrosswalks": sum(item["variantCatalogFormMappingStatus"] != "unresolved_identifier_namespace" for item in species_form_rows),
            "gameMasterCostumeConcepts": len(costume_concepts),
            "gameMasterCostumeConceptsWithVerifiedRelease": 0,
            "gameMasterCostumeIdentityCandidates": len(go_costume_records),
            "gameMasterCostumeIdentitiesWithVerifiedRelease": sum(item["releaseStatus"] == "released" for item in go_costume_records.values()),
            "visualIdentityCandidates": len(identities),
            "costumeIdentityCandidates": costume_candidate_count,
            "costumeCandidatesWithEventClaims": sum(item["id"] in identities_with_event_claims for item in identities if item["isCostumeCandidate"]),
            "officiallyVerifiedCostumeReleaseLinks": len(canonical_event_costume_links),
            "appearances": len(appearances),
            "assetPathReferences": len(asset_rows),
            "assetContentHashesVerified": sum(item["contentSha256"] is not None for item in asset_rows),
            "verifiedAssetSampleCount": len(verified_asset_samples),
            "assetsClearedForRedistribution": 0,
            "assetsTrackedByApplicationRepository": sum(item["trackedByApplicationRepository"] for item in asset_rows),
            "assetBytesIncludedInCatalog": 0,
            "twoDimensionalPathHints": sum(item["referenceKind"] == "2d_icon_path_hint" for item in asset_rows),
            "threeDimensionalReferences": len(three_dimensional_models),
            "threeDimensionalTextureReferences": len(three_dimensional_textures),
            "threeDimensionalAssetPathReferences": len(three_dimensional_assets),
            "threeDimensionalMappingsUnresolved": sum(item["identityMappingStatus"] != "exact_identity_crosswalk" for item in three_dimensional_assets),
            "threeDimensionalContentHashesVerified": sum(item["contentSha256"] is not None for item in three_dimensional_assets),
            "threeDimensionalRenderVerified": sum(item["renderabilityStatus"] == "blender_4.5.15_imported_and_six_views_rendered" for item in three_dimensional_models),
            "eventObservations": len(events),
            "eventObservationsWithDateClaims": sum(bool(item["startDateClaim"] or item["endDateClaim"]) for item in events),
            "eventObservationsOfficiallyVerified": 0,
            "canonicalEvents": len(canonical_events),
            "costumeSignatureDescriptorRecords": len(costume_signature_rows),
            "shinySignatureDescriptorRecords": len(shiny_signature_rows),
            "authoritativeVariantRowsMatched": authoritative_match_count,
            "authoritativeVariantRowsUnmatched": len(authoritative_rows) - authoritative_match_count,
            "speciesNameSourceConflicts": len(name_conflicts),
            "localizedNameCoverageStatus": "complete_for_pinned_species_roster_in_15_listed_locales",
            "formAndCostumeReleaseCoverageStatus": "partial; only explicitly linked official event records promote release status",
            "visualAssetCoverageStatus": f"2d_metadata_plus_8_hash_verified_private_samples; 3d_pinned_tree_inventory_with_{len(three_dimensional_models)}_model_and_{len(three_dimensional_textures)}_texture_paths; identity_quality_and_redistribution_rights_not_qualified",
            "historicalEventCoverageStatus": "partial; two official Pikachu re-release events canonicalized, all other source observations remain unresolved",
        },
    }
    validate_catalog(catalog)
    return catalog


def validate_catalog(catalog: dict[str, Any]) -> None:
    if catalog.get("schemaVersion") != 1:
        raise ValueError("Unsupported visual identity catalog schema")
    required_arrays = ("species", "goFormDefinitions", "goForms", "gameMasterCostumeConcepts", "goCostumeIdentities", "visualIdentities", "appearances", "visualAssets", "eventObservations", "identityEventLinks", "canonicalEvents", "canonicalEventCostumeLinks")
    for field in required_arrays:
        if not isinstance(catalog.get(field), list):
            raise ValueError(f"Catalog field {field} must be a list")

    def unique_ids(field: str) -> set[str]:
        ids = [record.get("id") for record in catalog[field]]
        if any(not value for value in ids) or len(ids) != len(set(ids)):
            raise ValueError(f"Catalog field {field} has missing or duplicate IDs")
        return set(ids)

    species_ids = unique_ids("species")
    form_definition_ids = unique_ids("goFormDefinitions")
    form_ids = unique_ids("goForms")
    costume_ids = unique_ids("gameMasterCostumeConcepts")
    go_costume_ids = unique_ids("goCostumeIdentities")
    identity_ids = unique_ids("visualIdentities")
    appearance_ids = unique_ids("appearances")
    asset_ids = unique_ids("visualAssets")
    event_ids = unique_ids("eventObservations")
    canonical_event_ids = unique_ids("canonicalEvents")
    _ = (form_ids, costume_ids)

    for form in catalog["goForms"]:
        if form["speciesId"] not in species_ids or form["formDefinitionId"] not in form_definition_ids:
            raise ValueError("Form must link to an existing species and form definition")
        if form["releaseStatus"] not in {"uncertain", "released"}:
            raise ValueError("Form release status must remain uncertain unless officially verified")
        if form["releaseStatus"] == "released" and not str(form.get("releaseEvidence", "")).startswith("official_event:"):
            raise ValueError("Released form requires an official event evidence reference")
    appearance_identity_ids: dict[str, set[str]] = {}
    for appearance in catalog["appearances"]:
        appearance_identity_ids.setdefault(appearance["visualIdentityId"], set()).add(appearance["id"])
    for identity in catalog["visualIdentities"]:
        if identity["speciesId"] not in species_ids:
            raise ValueError("Visual identity links to a missing species")
        if not appearance_identity_ids.get(identity["id"]):
            raise ValueError("Visual identity references a missing appearance")
        if identity.get("goCostumeIdentityId") is not None and identity["goCostumeIdentityId"] not in go_costume_ids:
            raise ValueError("Visual identity references a missing canonical GO costume identity")
    for appearance in catalog["appearances"]:
        if appearance["visualIdentityId"] not in identity_ids:
            raise ValueError("Appearance references a missing visual identity")
        if appearance["assetRefId"] is not None and appearance["assetRefId"] not in asset_ids:
            raise ValueError("Appearance references a missing asset record")
    for asset in catalog["visualAssets"]:
        if asset["contentSha256"] is not None and len(asset["contentSha256"]) != 64:
            raise ValueError("Asset content SHA-256 must be a full digest")
        if asset["contentSha256"] is not None and asset["verificationStatus"] not in {
            "source_hash_and_png_header_verified",
            "source_hash_and_full_png_decode_verified",
            "git_blob_and_sha256_verified",
            "git_blob_sha256_and_png_scanline_stream_verified",
        }:
            raise ValueError("An asset hash requires a source-hash-verified status")
        if (
            asset["contentSha256"] is not None
            and asset["format"] == "png"
            and (asset.get("pixelWidth", 0) < 1 or asset.get("pixelHeight", 0) < 1)
        ):
            raise ValueError("A content-verified image requires valid pixel dimensions")
        if asset.get("referenceKind") in {"3d_model_path_hint", "3d_texture_path_hint"}:
            if re.fullmatch(r"[0-9a-f]{40}", str(asset.get("sourceGitBlobSha") or "")) is None:
                raise ValueError("A 3D path hint requires a source Git blob SHA")
            if asset.get("identityMappingStatus") not in {"unresolved_filename_candidate", "unresolved_no_identity_token", "exact_identity_crosswalk"}:
                raise ValueError("A 3D asset has an invalid identity mapping status")
        if asset.get("redistributionRightsStatus") != "not_cleared_for_redistribution":
            raise ValueError("PokeMiners assets must not be marked cleared for redistribution")
    for link in catalog["identityEventLinks"]:
        if link["visualIdentityId"] not in identity_ids or link["eventObservationId"] not in event_ids:
            raise ValueError("Event link references a missing identity or event observation")
    for costume in catalog["goCostumeIdentities"]:
        if costume["speciesId"] not in species_ids or costume["releaseStatus"] not in {"uncertain", "released"}:
            raise ValueError("GO costume identity has an invalid species or release status")
        if costume["runtimeEventInferenceAllowed"] is not False:
            raise ValueError("Runtime must not infer a historical event from costume pixels")
        if costume["releaseStatus"] == "released" and not costume.get("canonicalEventIds"):
            raise ValueError("Released GO costume identity requires a verified event link")
        if not any(
            form["speciesId"] == costume["speciesId"]
            and form["sourceFormId"] == costume["sourceGoFormId"]
            and form["sourceFormIsCostume"] is True
            for form in catalog["goForms"]
        ):
            raise ValueError("GO costume identity must map to a costume form for the same species")
    for event in catalog["canonicalEvents"]:
        if event["sourceType"] != "official_event_announcement" or not event.get("sourceUrl"):
            raise ValueError("Canonical event requires an official primary source")
    for link in catalog["canonicalEventCostumeLinks"]:
        if link["canonicalEventId"] not in canonical_event_ids or link["goCostumeIdentityId"] not in go_costume_ids:
            raise ValueError("Official event costume link references a missing event or identity")
        if link["runtimeEventInferenceAllowed"] is not False:
            raise ValueError("Official event record must not enable runtime event inference")
    coverage = catalog.get("coverage", {})
    expected = {
        "speciesRecords": len(catalog["species"]),
        "speciesFormMappings": len(catalog["goForms"]),
        "visualIdentityCandidates": len(catalog["visualIdentities"]),
        "appearances": len(catalog["appearances"]),
        "assetPathReferences": len(catalog["visualAssets"]),
        "threeDimensionalReferences": sum(asset["referenceKind"] == "3d_model_path_hint" for asset in catalog["visualAssets"]),
        "threeDimensionalTextureReferences": sum(asset["referenceKind"] == "3d_texture_path_hint" for asset in catalog["visualAssets"]),
        "eventObservations": len(catalog["eventObservations"]),
        "gameMasterCostumeIdentityCandidates": len(catalog["goCostumeIdentities"]),
        "canonicalEvents": len(catalog["canonicalEvents"]),
    }
    for field, count in expected.items():
        if coverage.get(field) != count:
            raise ValueError(f"Coverage count mismatch for {field}: expected {count}, got {coverage.get(field)}")


def _load_repo_inputs(root: Path) -> tuple[dict[str, Any], dict[str, str]]:
    files = {
        "variantCatalog": "app/src/main/assets/data/variant_catalog.json",
        "authoritativeVariants": "app/src/main/assets/data/authoritative_variant_db.json",
        "costumeSignatures": "app/src/main/assets/data/costume_signatures.json",
        "shinySignatures": "app/src/main/assets/data/shiny_signatures.json",
    }
    inputs: dict[str, Any] = {}
    hashes: dict[str, str] = {}
    for key, relative in files.items():
        raw = (root / relative).read_bytes()
        inputs[key] = _read_json_bytes(raw, relative)
        hashes[relative] = sha256_bytes(raw)
    return inputs, hashes


def _repository_commit(root: Path) -> str:
    import subprocess

    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def _tracked_paths(root: Path) -> set[str]:
    import subprocess

    result = subprocess.run(["git", "ls-files"], cwd=root, check=True, capture_output=True, text=True)
    return set(result.stdout.splitlines())


def _load_pins() -> dict[str, Any]:
    return json.loads(PINS_PATH.read_text(encoding="utf-8"))


def _validate_existing_file(path: Path) -> dict[str, Any]:
    catalog = json.loads(path.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    return catalog


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true", help="fetch hash-pinned small JSON sources and regenerate the catalog")
    group.add_argument("--check", action="store_true", help="validate the checked-in catalog offline")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    try:
        if args.check:
            catalog = _validate_existing_file(args.output)
            print(json.dumps(catalog["coverage"], ensure_ascii=False, sort_keys=True))
            return 0

        pins = _load_pins()
        external = fetch_pinned_sources(pins)
        local, local_hashes = _load_repo_inputs(ROOT)
        expected_local_hashes = pins.get("repositoryInputHashes", {})
        if local_hashes != expected_local_hashes:
            raise ValueError("Repository reference inputs do not match the pinned source hashes")
        inputs = {**external, **local}
        catalog = build_catalog(
            inputs=inputs,
            pins=pins,
            repo_input_hashes=local_hashes,
            repository_commit=str(pins["repositoryInputsCommit"]),
            tracked_paths=_tracked_paths(ROOT),
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(canonical_json_bytes(catalog))
        print(f"Wrote {args.output.relative_to(ROOT).as_posix()} ({args.output.stat().st_size} bytes)")
        print(json.dumps(catalog["coverage"], ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, KeyError, TypeError, ValueError, RuntimeError) as error:
        print(f"visual identity catalog: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
