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
DEFAULT_CATALOG = os.path.join(
    "app",
    "src",
    "main",
    "assets",
    "data",
    "variant_catalog.json",
)

HASH_SIZE = 8
EDGE_SIZE = 48
EDGE_BINS = 8
HIST_BINS = 12


def load_species_map(path):
    with open(path, "r", encoding="utf-8") as f:
        names = json.load(f)
    return {idx + 1: name for idx, name in enumerate(names)}


def load_variant_catalog(path):
    if not path or not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    entries = data.get("entries", []) if isinstance(data, dict) else []
    return {
        str(entry.get("spriteKey")).strip(): entry
        for entry in entries
        if isinstance(entry, dict) and str(entry.get("spriteKey", "")).strip()
    }


def iter_sprite_files(root_dir):
    for root, _, files in os.walk(root_dir):
        for name in files:
            lower = name.lower()
            if not lower.endswith(".png"):
                continue
            if "pokemon_icon_" not in lower and ".icon.png" not in lower:
                continue
            if "_shadow" in lower:
                continue
            yield os.path.join(root, name)


def normalize_variant_token(value):
    if not value:
        return None
    normalized = str(value).strip().upper()
    normalized = re.sub(r"([A-Z]+)(20\d{2})", r"\1_\2", normalized)
    normalized = re.sub(r"_+", "_", normalized).strip("_")
    if should_strip_leading_costume_marker(normalized):
        normalized = normalized[1:]
    return normalized or None


def should_strip_leading_costume_marker(value):
    if not value or not value.startswith("C") or "_" not in value or len(value) <= 1:
        return False
    suffix = value[1:]
    known_prefixes = (
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
        "HOLIDAY",
        "WINTER",
        "HALLOWEEN",
        "FASHION",
        "ANNIV",
        "NEWYEAR",
        "GOFEST",
        "GOTOUR",
    )
    return any(suffix.startswith(prefix) for prefix in known_prefixes)


def looks_like_costume_variant(variant_id):
    if not variant_id:
        return False
    token = str(variant_id).upper()
    if looks_like_special_form_variant(token):
        return False
    costume_keywords = (
        "HOLIDAY",
        "WINTER",
        "FASHION",
        "HALLOWEEN",
        "ANNIV",
        "GOFEST",
        "TOUR",
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
        "NOEVOLVE",
        "TIARA",
        "HAT",
        "CROWN",
        "BOW",
        "FLOWER",
        "PGO_",
    )
    return any(keyword in token for keyword in costume_keywords)


def looks_like_special_form_variant(variant_id):
    if not variant_id:
        return False
    token = str(variant_id).upper()
    form_keywords = (
        "MEGA",
        "GIGANTAMAX",
        "ALOLA",
        "GALAR",
        "HISUI",
        "PALDEA",
        "THERIAN",
        "ORIGIN",
        "ALTERED",
        "SKY",
    )
    return any(keyword in token for keyword in form_keywords)


def parse_sprite_filename(filename, species_map, catalog_by_sprite):
    base = os.path.basename(filename)
    m = re.match(r"pokemon_icon_(\d+)_([0-9]+)(?:_([0-9]+))?(_shiny)?\.png$", base, re.IGNORECASE)
    if m:
        dex = int(m.group(1))
        form_id = m.group(2)
        variant_id = m.group(3)
        is_shiny = m.group(4) is not None
    else:
        m = re.match(r"pokemon_icon_pm(\d+)_([0-9]+)_([a-z0-9_]+?)(_shiny)?\.png$", base, re.IGNORECASE)
        if m:
            dex = int(m.group(1))
            form_id = m.group(2)
            variant_id = normalize_variant_token(m.group(3))
            is_shiny = m.group(4) is not None
        else:
            m = re.match(r"pm(\d+)(?:\.(s))?\.icon\.png$", base, re.IGNORECASE)
            if m:
                dex = int(m.group(1))
                form_id = "00"
                variant_id = None
                is_shiny = m.group(2) is not None
            else:
                m = re.match(r"pm(\d+)\.([a-z0-9_]+)\.(s)\.icon\.png$|pm(\d+)\.([a-z0-9_]+)\.icon\.png$", base, re.IGNORECASE)
                if not m:
                    return None
                dex = int(m.group(1) or m.group(4))
                form_id = "00"
                variant_id = normalize_variant_token(m.group(2) or m.group(5))
                is_shiny = m.group(3) is not None
    species = species_map.get(dex)
    if not species:
        return None

    asset_key = f"{dex:03d}_{form_id}"
    if variant_id:
        asset_key += f"_{variant_id}"

    sprite_key = asset_key + ("_shiny" if is_shiny else "")
    catalog_entry = catalog_by_sprite.get(sprite_key)
    if catalog_entry:
        species = catalog_entry.get("species", species)
        is_shiny = bool(catalog_entry.get("isShiny", is_shiny))
        variant_type = catalog_entry.get("variantClass", "base")
        is_costume_like = bool(catalog_entry.get("isCostumeLike", False))
        event_tags = list(catalog_entry.get("eventTags", []))
        has_event_metadata = bool(catalog_entry.get("hasEventMetadata", False))
        release_window = catalog_entry.get("releaseWindow")
        game_master_costume_forms = list(catalog_entry.get("gameMasterCostumeForms", []))
    else:
        has_non_base_variant = bool(variant_id) or form_id != "00"
        is_costume_like = looks_like_costume_variant(variant_id)
        variant_type = "costume" if is_costume_like else ("form" if has_non_base_variant else "base")
        event_tags = []
        has_event_metadata = False
        release_window = None
        game_master_costume_forms = []

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
        "eventTags": event_tags,
        "hasEventMetadata": has_event_metadata,
        "releaseWindow": release_window,
        "gameMasterCostumeForms": game_master_costume_forms,
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


def train_model(assets_dir, species_map, catalog_by_sprite):
    entries = []
    selected_paths = {}
    species_counts = defaultdict(int)
    for path in iter_sprite_files(assets_dir):
        metadata = parse_sprite_filename(path, species_map, catalog_by_sprite)
        if not metadata:
            continue
        sprite_key = metadata["spriteKey"]
        path_normalized = path.replace("\\", "/")
        preference = 2 if "Addressable Assets" not in path_normalized else (1 if metadata.get("variantId") else 0)
        current = selected_paths.get(sprite_key)
        if current and current["preference"] >= preference:
            continue
        selected_paths[sprite_key] = {
            "path": path,
            "metadata": metadata,
            "preference": preference,
        }

    for item in selected_paths.values():
        entry = build_entry(item["path"], item["metadata"])
        entries.append(entry)
        species_counts[item["metadata"]["species"]] += 1

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
        "--variant-catalog",
        default=DEFAULT_CATALOG,
        help="Path to authoritative variant_catalog.json",
    )
    parser.add_argument("--out", default=DEFAULT_OUTPUT, help="Output model JSON path")
    args = parser.parse_args()

    species_map = load_species_map(args.species_map)
    catalog_by_sprite = load_variant_catalog(args.variant_catalog)
    payload = train_model(args.assets_dir, species_map, catalog_by_sprite)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"Wrote {payload['entryCount']} variant prototypes for {payload['speciesCount']} species to {args.out}")


if __name__ == "__main__":
    main()
