import argparse
import json
import math
import os
import re
import sys
from datetime import datetime, timezone

try:
    from PIL import Image, ImageFilter
except Exception as exc:  # pragma: no cover
    print("PIL_NOT_FOUND: install Pillow with `pip install pillow`", file=sys.stderr)
    raise


DEFAULT_OUTPUT = os.path.join(
    "app",
    "src",
    "main",
    "assets",
    "data",
    "costume_signatures.json",
)


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    return {idx + 1: name for idx, name in enumerate(names)}


def load_costume_keys(path):
    if not path:
        return {"keys": set(), "species": set()}
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict):
        keys = {str(x).strip() for x in data.get("keys", []) if str(x).strip()}
        species = {str(x).strip() for x in data.get("species", []) if str(x).strip()}
        return {"keys": keys, "species": species}
    keys = {str(x).strip() for x in data if str(x).strip()}
    return {"keys": keys, "species": set()}


def load_variant_registry(path):
    if not path:
        return None
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    entries = data.get("entries", [])
    by_species = {}
    for entry in entries:
        species = str(entry.get("species", "")).strip()
        if not species:
            continue
        by_species[species] = {
            "base_keys": {str(x).strip() for x in entry.get("baseKeys", []) if str(x).strip()},
            "variant_keys": {str(x).strip() for x in entry.get("variantKeys", []) if str(x).strip()},
        }
    return by_species


def iter_sprite_files(root_dir):
    for root, _, files in os.walk(root_dir):
        for name in files:
            lower = name.lower()
            if not lower.endswith(".png"):
                continue
            if "pokemon_icon_" not in lower:
                continue
            if "shiny" in lower or "shadow" in lower:
                continue
            yield os.path.join(root, name)


def parse_sprite_key(filename):
    base = os.path.basename(filename)
    m = re.search(r"pokemon_icon_(\d+)_([0-9]+)(?:_([0-9]+))?", base)
    if not m:
        return None, None, None, None
    dex_id = int(m.group(1))
    form_id = m.group(2)
    variant_id = m.group(3)
    if variant_id:
        key = f"{dex_id:03d}_{form_id}_{variant_id}"
    else:
        key = f"{dex_id:03d}_{form_id}"
    return dex_id, form_id, variant_id, key


def crop_to_alpha(img, min_alpha=8):
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    alpha = img.split()[3]
    mask = alpha.point(lambda a: 255 if a > min_alpha else 0)
    bbox = mask.getbbox()
    if not bbox:
        return img
    return img.crop(bbox)


def to_grayscale(img):
    return img.convert("L")


def ahash(img, size=8):
    img = to_grayscale(img.resize((size, size), Image.BILINEAR))
    pixels = list(img.getdata())
    avg = sum(pixels) / len(pixels)
    bits = [p > avg for p in pixels]
    return bits_to_hex(bits)


def dhash(img, size=8):
    img = to_grayscale(img.resize((size + 1, size), Image.BILINEAR))
    pixels = list(img.getdata())
    bits = []
    for row in range(size):
        row_start = row * (size + 1)
        for col in range(size):
            left = pixels[row_start + col]
            right = pixels[row_start + col + 1]
            bits.append(left < right)
    return bits_to_hex(bits)


def edge_histogram(img, size=64, bins=8):
    img = to_grayscale(img.resize((size, size), Image.BILINEAR))
    edged = img.filter(ImageFilter.FIND_EDGES)
    pixels = list(edged.getdata())
    hist = [0] * bins
    for p in pixels:
        idx = min(bins - 1, int((p / 256) * bins))
        hist[idx] += 1
    total = sum(hist) or 1
    return [round(h / total, 6) for h in hist]


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


def bits_to_hex(bits):
    if len(bits) % 4 != 0:
        raise ValueError("bit length must be multiple of 4")
    out = []
    for i in range(0, len(bits), 4):
        val = 0
        for j in range(4):
            val = (val << 1) | (1 if bits[i + j] else 0)
        out.append(format(val, "x"))
    return "".join(out)


def build_signatures(assets_dir, species_map, costume_keys, variant_registry):
    signatures = []
    for path in iter_sprite_files(assets_dir):
        dex_id, form_id, variant_id, key = parse_sprite_key(path)
        if not dex_id:
            continue
        species = species_map.get(dex_id, f"#{dex_id}")

        if variant_registry:
            registry_entry = variant_registry.get(species)
            if not registry_entry:
                continue
            allowed_keys = registry_entry["base_keys"] | registry_entry["variant_keys"]
            if key not in allowed_keys:
                continue
            is_costume = key in registry_entry["variant_keys"]
        elif costume_keys["keys"]:
            is_costume = key in costume_keys["keys"]
        elif costume_keys["species"]:
            if species not in costume_keys["species"]:
                continue
            is_costume = variant_id is not None
        else:
            is_costume = variant_id is not None

        img = Image.open(path)
        cropped = crop_to_alpha(img)

        entry = {
            "dex": dex_id,
            "species": species,
            "form": f"{form_id}_{variant_id}" if variant_id else form_id,
            "key": key,
            "isCostume": is_costume,
            "aHash": ahash(cropped, size=8),
            "dHash": dhash(cropped, size=8),
            "pHash": phash(cropped),
            "headPHash": phash(crop_head(cropped)),
            "edge": edge_histogram(cropped, size=64, bins=8),
            "src": os.path.relpath(path, assets_dir).replace("\\", "/"),
        }
        signatures.append(entry)
    return signatures


def main():
    parser = argparse.ArgumentParser(description="Generate costume signature set from local sprite assets.")
    parser.add_argument("--assets-dir", required=True, help="Path to PokeMiners pogo_assets or Images/Pokemon")
    parser.add_argument(
        "--species-map",
        default=os.path.join("app", "src", "main", "assets", "data", "pokemon_names.json"),
        help="Path to pokemon_names.json",
    )
    parser.add_argument(
        "--costume-keys",
        default="",
        help="Optional JSON list of costume keys (e.g., [\"025_11\", \"133_03\"]).",
    )
    parser.add_argument(
        "--variant-registry",
        default="",
        help="Optional variant_registry.json path. If provided, drives exact species/key coverage.",
    )
    parser.add_argument("--out", default=DEFAULT_OUTPUT, help="Output JSON path")
    args = parser.parse_args()

    species_map = load_species_map(args.species_map)
    costume_keys = load_costume_keys(args.costume_keys) if args.costume_keys else {"keys": set(), "species": set()}
    variant_registry = load_variant_registry(args.variant_registry) if args.variant_registry else None

    signatures = build_signatures(args.assets_dir, species_map, costume_keys, variant_registry)

    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": os.path.abspath(args.assets_dir),
        "count": len(signatures),
        "signatures": signatures,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {len(signatures)} signatures to {args.out}")


if __name__ == "__main__":
    main()
