import json
import math
import os
import argparse
from datetime import datetime, timezone

from PIL import Image, ImageFilter


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AUTHORITATIVE_DB = os.path.join(ROOT, "app", "src", "main", "assets", "data", "authoritative_variant_db.json")
COSTUME_SIGNATURES = os.path.join(ROOT, "app", "src", "main", "assets", "data", "costume_signatures.json")
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "data", "master_pokedex.json")


def load_json(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def sanitize_historical_events(events):
    cleaned = []
    for item in events or []:
        label = item.get("eventLabel")
        start = item.get("startDate")
        end = item.get("endDate")
        if label and start and end:
            cleaned.append(
                {
                    "eventLabel": label,
                    "startDate": start,
                    "endDate": end,
                }
            )
    return cleaned


def should_keep_top_level_event(entry, historical_events):
    if entry.get("variantClass") == "base" and not entry.get("isCostumeLike"):
        return False
    if historical_events:
        return True
    return bool(entry.get("eventStart") and entry.get("eventEnd"))


def crop_to_alpha(img, min_alpha=8):
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    alpha = img.split()[3]
    mask = alpha.point(lambda value: 255 if value > min_alpha else 0)
    bbox = mask.getbbox()
    if not bbox:
        return img
    return img.crop(bbox)


def to_grayscale(img):
    return img.convert("L")


def bits_to_hex(bits):
    if len(bits) % 4 != 0:
        raise ValueError("bit length must be a multiple of 4")
    out = []
    for index in range(0, len(bits), 4):
        nibble = 0
        for offset in range(4):
            nibble = (nibble << 1) | (1 if bits[index + offset] else 0)
        out.append(format(nibble, "x"))
    return "".join(out)


def ahash(img, size=8):
    img = to_grayscale(img.resize((size, size), Image.BILINEAR))
    pixels = list(img.getdata())
    avg = sum(pixels) / len(pixels)
    return bits_to_hex([pixel >= avg for pixel in pixels])


def dhash(img, size=8):
    img = to_grayscale(img.resize((size + 1, size), Image.BILINEAR))
    pixels = list(img.getdata())
    bits = []
    for row in range(size):
        row_start = row * (size + 1)
        for col in range(size):
            bits.append(pixels[row_start + col] < pixels[row_start + col + 1])
    return bits_to_hex(bits)


def edge_histogram(img, size=64, bins=8):
    img = to_grayscale(img.resize((size, size), Image.BILINEAR))
    edged = img.filter(ImageFilter.FIND_EDGES)
    pixels = list(edged.getdata())
    hist = [0] * bins
    for pixel in pixels:
        idx = min(bins - 1, int((pixel / 256) * bins))
        hist[idx] += 1
    total = sum(hist) or 1
    return [round(count / total, 6) for count in hist]


def phash(img, source_size=16, hash_size=8):
    img = to_grayscale(img.resize((source_size, source_size), Image.BILINEAR))
    pixels = list(img.getdata())

    def pixel(x, y):
        return pixels[y * source_size + x]

    coeffs = []
    for v in range(hash_size):
        for u in range(hash_size):
            total = 0.0
            for y in range(source_size):
                for x in range(source_size):
                    total += (
                        pixel(x, y)
                        * math.cos(((2 * x + 1) * u * math.pi) / (2.0 * source_size))
                        * math.cos(((2 * y + 1) * v * math.pi) / (2.0 * source_size))
                    )
            cu = 1.0 / math.sqrt(2.0) if u == 0 else 1.0
            cv = 1.0 / math.sqrt(2.0) if v == 0 else 1.0
            coeffs.append(0.25 * cu * cv * total)

    dc = coeffs[0]
    rest = coeffs[1:]
    avg = sum(rest) / len(rest)
    bits = [dc >= avg] + [value >= avg for value in rest]
    return bits_to_hex(bits)


def crop_head(img):
    height = max(1, int(img.height * 0.35))
    return img.crop((0, 0, img.width, height))


def compute_signature(asset_path):
    full_path = os.path.join(ROOT, asset_path)
    if not os.path.exists(full_path):
        return None
    with Image.open(full_path) as image:
        cropped = crop_to_alpha(image)
        return {
            "aHash": ahash(cropped),
            "dHash": dhash(cropped),
            "pHash": phash(cropped),
            "headPHash": phash(crop_head(cropped)),
            "edge": edge_histogram(cropped),
        }


def build_signature_index():
    if not os.path.exists(COSTUME_SIGNATURES):
        return {}
    payload = load_json(COSTUME_SIGNATURES)
    index = {}
    for item in payload.get("signatures", []):
        key = item.get("key")
        if not key:
            continue
        index[key] = {
            "aHash": item.get("aHash"),
            "dHash": item.get("dHash"),
            "pHash": item.get("pHash"),
            "headPHash": item.get("headPHash"),
            "edge": item.get("edge") or [],
        }
    return index


def build_previous_signature_index(path):
    if not os.path.exists(path):
        return {}
    index = {}
    duplicates = set()
    seen = set()
    for entry in load_json(path).get("entries", []):
        key = entry.get("spriteKey")
        signature = entry.get("signature")
        if not key:
            continue
        if key in seen:
            index.pop(key, None)
            duplicates.add(key)
            continue
        seen.add(key)
        if not isinstance(signature, dict):
            continue
        try:
            hashes = [signature[name] for name in ("aHash", "dHash", "pHash", "headPHash")]
            edges = signature["edge"]
            valid = all(isinstance(value, str) and len(value) == 16 and 0 <= int(value, 16) < 2**64 for value in hashes)
            valid = valid and isinstance(edges, list) and len(edges) == 8 and all(
                isinstance(value, (int, float)) and math.isfinite(value) and 0 <= value <= 1 for value in edges
            )
        except (KeyError, TypeError, ValueError):
            valid = False
        if not valid or key in duplicates:
            continue
        index[key] = signature
    return index


def build_master_pokedex(previous_master_path=None, preserve_existing_signatures=False):
    authoritative = load_json(AUTHORITATIVE_DB)
    signature_index = build_signature_index()
    previous_signatures = build_previous_signature_index(previous_master_path or OUTPUT)
    entries = []
    for entry in authoritative.get("entries", []):
        sprite_key = entry.get("spriteKey")
        signature = signature_index.get(sprite_key)
        if signature is None:
            signature = compute_signature(entry.get("assetPath") or "")
        if signature is None and sprite_key in previous_signatures:
            if not preserve_existing_signatures:
                raise ValueError(
                    f"Source signature unavailable for {sprite_key}; refusing to erase an existing signature. "
                    "Use --preserve-existing-signatures only after verifying reference provenance."
                )
            signature = previous_signatures[sprite_key]
        historical_events = sanitize_historical_events(entry.get("historicalEvents"))
        keep_top_level_event = should_keep_top_level_event(entry, historical_events)
        entries.append(
            {
                "species": entry.get("species"),
                "spriteKey": sprite_key,
                "variantClass": entry.get("variantClass"),
                "isShiny": bool(entry.get("isShiny")),
                "isCostumeLike": bool(entry.get("isCostumeLike")),
                "variantLabel": entry.get("variantLabel"),
                "eventLabel": entry.get("eventLabel") if keep_top_level_event else None,
                "eventStart": entry.get("eventStart") if keep_top_level_event else None,
                "eventEnd": entry.get("eventEnd") if keep_top_level_event else None,
                "historicalEvents": historical_events,
                "aliases": entry.get("aliases") or [],
                "signature": signature,
            }
        )
    return {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(entries),
        "entries": entries,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--preserve-existing-signatures", action="store_true",
        help="Explicitly reuse existing valid same-key signatures when source images are unavailable; verify provenance first.",
    )
    args = parser.parse_args()
    payload = build_master_pokedex(preserve_existing_signatures=args.preserve_existing_signatures)
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"Wrote {payload['count']} master pokedex entries to {OUTPUT}")


if __name__ == "__main__":
    main()
