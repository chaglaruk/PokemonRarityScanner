import argparse
import json
import math
import os
import re
from collections import defaultdict
from datetime import datetime, timezone
from io import BytesIO

from PIL import Image, ImageEnhance, ImageFilter


DEFAULT_OUTPUT = os.path.join(
    "app",
    "src",
    "main",
    "assets",
    "data",
    "variant_classifier_model.json",
)

HASH_SIZE = 8
EDGE_SIZE = 48
EDGE_BINS = 8
HIST_BINS = 12


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    return {idx + 1: name for idx, name in enumerate(names)}


def load_costume_species(path):
    if not path or not os.path.exists(path):
        return set()
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        return {str(x).strip() for x in data if str(x).strip()}
    if isinstance(data, dict):
        keys = data.get("species") or data.get("costumeLikeSpecies") or []
        return {str(x).strip() for x in keys if str(x).strip()}
    return set()


def iter_sprite_files(root_dir):
    for root, _, files in os.walk(root_dir):
        for name in files:
            lower = name.lower()
            if not lower.endswith(".png"):
                continue
            if "pokemon_icon_" not in lower:
                continue
            if "_shadow" in lower:
                continue
            yield os.path.join(root, name)


def parse_sprite_filename(filename, species_map, costume_species):
    base = os.path.basename(filename)
    m = re.match(r"pokemon_icon_(\d+)_([0-9]+)(?:_([0-9]+))?(_shiny)?\.png$", base, re.IGNORECASE)
    if not m:
        return None

    dex = int(m.group(1))
    form_id = m.group(2)
    variant_id = m.group(3)
    is_shiny = m.group(4) is not None
    species = species_map.get(dex)
    if not species:
        return None

    asset_key = f"{dex:03d}_{form_id}"
    if variant_id:
        asset_key += f"_{variant_id}"

    sprite_key = asset_key + ("_shiny" if is_shiny else "")
    has_non_base_variant = bool(variant_id) or form_id != "00"
    is_costume_like = has_non_base_variant and species in costume_species
    variant_type = "base"
    if is_costume_like:
        variant_type = "costume"
    elif has_non_base_variant:
        variant_type = "form"

    return {
        "dex": dex,
        "species": species,
        "formId": form_id,
        "variantId": variant_id,
        "assetKey": asset_key,
        "spriteKey": sprite_key,
        "isShiny": is_shiny,
        "isCostumeLike": is_costume_like,
        "variantType": variant_type,
        "filename": base,
    }


def crop_alpha(image):
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if not bbox:
        return rgba
    return rgba.crop(bbox)


def center_on_canvas(image, scale=1.0):
    src = image.convert("RGBA")
    w, h = src.size
    target_w = max(1, int(round(w * scale)))
    target_h = max(1, int(round(h * scale)))
    resized = src.resize((target_w, target_h), Image.Resampling.BILINEAR)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    x = (w - target_w) // 2
    y = (h - target_h) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def augmentations(image):
    base = crop_alpha(image)

    out = [base]

    contrast = ImageEnhance.Contrast(base).enhance(1.08)
    color = ImageEnhance.Color(contrast).enhance(1.10)
    out.append(color)

    out.append(base.filter(ImageFilter.GaussianBlur(radius=0.6)))
    out.append(center_on_canvas(base, 0.92))
    out.append(center_on_canvas(base, 1.08))

    return out


def ahash(image, size=HASH_SIZE):
    gray = image.convert("L").resize((size, size), Image.Resampling.BILINEAR)
    pixels = list(gray.getdata())
    avg = sum(pixels) / len(pixels)
    bits = [1 if px > avg else 0 for px in pixels]
    return bits_to_hex(bits)


def dhash(image, size=HASH_SIZE):
    gray = image.convert("L").resize((size + 1, size), Image.Resampling.BILINEAR)
    pixels = list(gray.getdata())
    bits = []
    stride = size + 1
    for y in range(size):
        row = y * stride
        for x in range(size):
            bits.append(1 if pixels[row + x] < pixels[row + x + 1] else 0)
    return bits_to_hex(bits)


def bits_to_hex(bits):
    chars = []
    for index in range(0, len(bits), 4):
        value = 0
        for offset in range(4):
            value = (value << 1) | bits[index + offset]
        chars.append(format(value, "x"))
    return "".join(chars)


def hamming_hex(a, b):
    if len(a) != len(b):
        return 64
    total = 0
    for ca, cb in zip(a, b):
        total += int(ca, 16) ^ int(cb, 16)
        total -= ((int(ca, 16) ^ int(cb, 16)) & 1) * 0  # keep integer path stable
    # nibble xor above is not bitcount, so do real popcount
    total = 0
    for ca, cb in zip(a, b):
        total += (int(ca, 16) ^ int(cb, 16)).bit_count()
    return total


def edge_histogram(image, size=EDGE_SIZE, bins=EDGE_BINS):
    gray = image.convert("L").resize((size, size), Image.Resampling.BILINEAR)
    px = list(gray.getdata())
    hist = [0] * bins
    count = 0
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            idx = y * size + x
            lum_l = px[idx - 1]
            lum_r = px[idx + 1]
            lum_u = px[idx - size]
            lum_d = px[idx + size]
            grad = min(255, abs(lum_r - lum_l) + abs(lum_d - lum_u))
            bin_idx = min(bins - 1, (grad * bins) // 256)
            hist[bin_idx] += 1
            count += 1
    if count <= 0:
        return [0.0] * bins
    return [round(v / count, 6) for v in hist]


def hue_histogram(image, bins=HIST_BINS):
    rgba = image.convert("RGBA")
    hist = [0.0] * bins
    total = 0.0
    width, height = rgba.size
    step = 2
    for y in range(0, height, step):
        for x in range(0, width, step):
            r, g, b, a = rgba.getpixel((x, y))
            if a < 16:
                continue
            h, s, v = rgb_to_hsv(r, g, b)
            if s < 0.15 or v < 0.15:
                continue
            bin_idx = min(bins - 1, int((h / 360.0) * bins))
            weight = min(1.0, s * v)
            hist[bin_idx] += weight
            total += weight
    if total <= 0:
        return [0.0] * bins
    return [round(v / total, 6) for v in hist]


def rgb_to_hsv(r, g, b):
    rf = r / 255.0
    gf = g / 255.0
    bf = b / 255.0
    mx = max(rf, gf, bf)
    mn = min(rf, gf, bf)
    delta = mx - mn

    if delta == 0:
        h = 0.0
    elif mx == rf:
        h = (60 * ((gf - bf) / delta) + 360) % 360
    elif mx == gf:
        h = (60 * ((bf - rf) / delta) + 120) % 360
    else:
        h = (60 * ((rf - gf) / delta) + 240) % 360

    s = 0.0 if mx == 0 else delta / mx
    v = mx
    return h, s, v


def split_regions(image):
    width, height = image.size
    head_h = max(1, int(round(height * 0.30)))
    upper_h = max(1, int(round(height * 0.55)))
    body_top = min(height - 1, max(0, int(round(height * 0.30))))
    full = image
    head = image.crop((0, 0, width, head_h))
    upper = image.crop((0, 0, width, upper_h))
    body = image.crop((0, body_top, width, height))
    return full, head, upper, body


def extract_features(image):
    cropped = crop_alpha(image)
    full, head, upper, body = split_regions(cropped)
    width, height = cropped.size
    fg_ratio = foreground_ratio(cropped)
    aspect = round(width / max(1.0, float(height)), 6)
    return {
        "aHash": ahash(cropped),
        "dHash": dhash(cropped),
        "edge": edge_histogram(cropped),
        "fullHist": hue_histogram(full),
        "headHist": hue_histogram(head),
        "upperHist": hue_histogram(upper),
        "bodyHist": hue_histogram(body),
        "foregroundRatio": round(fg_ratio, 6),
        "aspectRatio": aspect,
    }


def foreground_ratio(image):
    rgba = image.convert("RGBA")
    width, height = rgba.size
    total = width * height
    if total <= 0:
        return 0.0
    fg = 0
    step = 2
    sampled = 0
    for y in range(0, height, step):
        for x in range(0, width, step):
            sampled += 1
            if rgba.getpixel((x, y))[3] >= 16:
                fg += 1
    return fg / max(1, sampled)


def average_vectors(vectors):
    if not vectors:
        return []
    size = len(vectors[0])
    totals = [0.0] * size
    for vector in vectors:
        for index, value in enumerate(vector):
            totals[index] += float(value)
    return [round(v / len(vectors), 6) for v in totals]


def medoid_hash(hashes):
    if not hashes:
        return ""
    best = hashes[0]
    best_score = float("inf")
    for candidate in hashes:
        score = sum(hamming_hex(candidate, other) for other in hashes)
        if score < best_score:
            best = candidate
            best_score = score
    return best


def build_entry(path, metadata):
    with Image.open(path) as image:
        variants = augmentations(image)
        feature_samples = [extract_features(variant) for variant in variants]

    return {
        **metadata,
        "sampleCount": len(feature_samples),
        "prototype": {
            "aHash": medoid_hash([sample["aHash"] for sample in feature_samples]),
            "dHash": medoid_hash([sample["dHash"] for sample in feature_samples]),
            "edge": average_vectors([sample["edge"] for sample in feature_samples]),
            "fullHist": average_vectors([sample["fullHist"] for sample in feature_samples]),
            "headHist": average_vectors([sample["headHist"] for sample in feature_samples]),
            "upperHist": average_vectors([sample["upperHist"] for sample in feature_samples]),
            "bodyHist": average_vectors([sample["bodyHist"] for sample in feature_samples]),
            "foregroundRatio": round(
                sum(sample["foregroundRatio"] for sample in feature_samples) / len(feature_samples),
                6,
            ),
            "aspectRatio": round(
                sum(sample["aspectRatio"] for sample in feature_samples) / len(feature_samples),
                6,
            ),
        },
    }


def train_model(assets_dir, species_map, costume_species):
    entries = []
    species_counts = defaultdict(int)
    for path in iter_sprite_files(assets_dir):
        metadata = parse_sprite_filename(path, species_map, costume_species)
        if not metadata:
            continue
        entry = build_entry(path, metadata)
        entries.append(entry)
        species_counts[metadata["species"]] += 1

    entries.sort(key=lambda item: (item["dex"], item["spriteKey"]))
    payload = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": os.path.abspath(assets_dir),
        "entryCount": len(entries),
        "speciesCount": len(species_counts),
        "entries": entries,
    }
    return payload


def main():
    parser = argparse.ArgumentParser(description="Train asset-backed variant prototype classifier from local Pokemon sprites.")
    parser.add_argument("--assets-dir", required=True, help="Path to sprite asset directory")
    parser.add_argument(
        "--species-map",
        default=os.path.join("app", "src", "main", "assets", "data", "pokemon_names.json"),
        help="Path to pokemon_names.json",
    )
    parser.add_argument(
        "--costume-species",
        default=os.path.join("app", "src", "main", "assets", "data", "costume_species.json"),
        help="Path to costume species JSON",
    )
    parser.add_argument("--out", default=DEFAULT_OUTPUT, help="Output model JSON path")
    args = parser.parse_args()

    species_map = load_species_map(args.species_map)
    costume_species = load_costume_species(args.costume_species)
    payload = train_model(args.assets_dir, species_map, costume_species)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {payload['entryCount']} variant prototypes for {payload['speciesCount']} species to {args.out}")


if __name__ == "__main__":
    main()
