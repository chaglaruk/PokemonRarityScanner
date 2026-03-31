import json, datetime, collections

data = json.load(open('all_scans.json', encoding='utf-8'))
items = data.get('items', [])

def get_scan(item):
    """Normalize - data may come from 'payload' sub-object or flat SQL columns."""
    p = item.get('payload') or {}
    pred   = p.get('prediction') or {}
    dev    = p.get('device') or {}
    dbg    = p.get('debug') or {}
    ts_ms  = p.get('uploadedAtEpochMs') or int(item.get('uploaded_at_epoch_ms') or 0)
    if not pred:
        # flat SQL row fallback
        pred = {
            'species':      item.get('predicted_species', 'Unknown'),
            'isShiny':      item.get('predicted_is_shiny') == '1' or item.get('predicted_is_shiny') is True,
            'hasCostume':   item.get('predicted_has_costume') == '1' or item.get('predicted_has_costume') is True,
            'rarityScore':  int(item.get('predicted_rarity_score') or 0),
            'rarityTier':   item.get('predicted_rarity_tier', ''),
            'cp':           item.get('predicted_cp'),
        }
        dev = {
            'manufacturer': item.get('device_manufacturer', '?'),
            'model':        item.get('device_model', '?'),
        }
        raw_ocr = item.get('raw_ocr_text', '') or ''
    else:
        raw_ocr = dbg.get('rawOcrText', '') or ''

    return pred, dev, raw_ocr, ts_ms, dbg

print("=== TELEMETRY ANALYSIS REPORT ===")
print(f"Total records: {len(items)}\n")

# ── per-device stats ────────────────────────────────────────────────────────
devices = collections.defaultdict(list)
for item in items:
    pred, dev, raw_ocr, ts_ms, dbg = get_scan(item)
    key = f"{dev.get('manufacturer','?')} {dev.get('model','?')}"
    devices[key].append((pred, ts_ms, raw_ocr, dbg))

print("=" * 60)
print("USERS / DEVICES (sorted by scan count)")
print("=" * 60)
for dev_name, scans in sorted(devices.items(), key=lambda x: -len(x[1])):
    times = sorted(s[1] for s in scans if s[1] > 0)
    if times:
        first = datetime.datetime.fromtimestamp(times[0]/1000).strftime('%Y-%m-%d %H:%M')
        last  = datetime.datetime.fromtimestamp(times[-1]/1000).strftime('%Y-%m-%d %H:%M')
    else:
        first = last = 'N/A'
    shiny_count   = sum(1 for s in scans if s[0].get('isShiny'))
    costume_count = sum(1 for s in scans if s[0].get('hasCostume'))
    known_species = sum(1 for s in scans if s[0].get('species','Unknown') not in ('Unknown',''))
    print(f"\n📱 {dev_name}")
    print(f"   Total scans    : {len(scans)}")
    print(f"   Species known  : {known_species}/{len(scans)}")
    print(f"   Shiny detected : {shiny_count}")
    print(f"   Costume detected: {costume_count}")
    print(f"   First scan     : {first}")
    print(f"   Last scan      : {last}")

print("\n")

# ── shiny/costume detection errors ─────────────────────────────────────────
print("=" * 60)
print("SHINY/COSTUME DETECTION ERRORS")
print("=" * 60)

errors = []
for item in items:
    pred, dev, raw_ocr, ts_ms, dbg = get_scan(item)
    species     = pred.get('species', 'Unknown')
    pred_shiny  = pred.get('isShiny', False)
    pred_costume= pred.get('hasCostume', False)

    # Parse Full Variant fields from rawOcrText
    fields = {}
    for part in raw_ocr.split('|'):
        k, _, v = part.partition(':')
        fields[k.strip()] = v.strip()

    fv_shiny   = fields.get('FullVariantShiny', '').lower() == 'true'
    fv_costume = fields.get('FullVariantCostume', '').lower() == 'true'
    fv_sprite  = fields.get('FullVariantSpriteKey', '')
    fv_mode    = fields.get('FullVariantExplanationMode', '')
    cl_shiny   = fields.get('ClassifierShiny', '').lower() == 'true'
    cl_costume = fields.get('ClassifierCostume', '').lower() == 'true'
    cl_species = fields.get('ClassifierSpecies', '')
    var_shiny  = fields.get('VariantClassifierShiny', '').lower() == 'true'

    sprite_has_shiny = '_shiny' in fv_sprite.lower()
    
    problems = []
    if sprite_has_shiny and not fv_shiny:
        problems.append(f'SPRITE_SHINY_BUT_FV=false (sprite={fv_sprite})')
    if fv_shiny and not pred_shiny:
        problems.append('FV_SHINY_LOST_IN_PREDICTION')
    if fv_costume and not pred_costume:
        problems.append('FV_COSTUME_LOST_IN_PREDICTION')
    if (cl_shiny or var_shiny) and not pred_shiny and not fv_shiny:
        problems.append(f'CLASSIFIER_DETECTED_SHINY_BUT_LOST (cl={cl_shiny}, var={var_shiny})')
    if cl_costume and not pred_costume and not fv_costume:
        problems.append(f'CLASSIFIER_DETECTED_COSTUME_BUT_LOST')
    # Species mismatch: classifer says one thing but prediction is different
    if cl_species and cl_species.lower() not in (species.lower(), 'unknown', ''):
        problems.append(f'SPECIES_MISMATCH: classifier={cl_species} vs pred={species}')

    if problems and ts_ms > 0:
        ts = datetime.datetime.fromtimestamp(ts_ms/1000).strftime('%m-%d %H:%M')
        dev_name = f"{item.get('payload',{}).get('device',{}).get('model') or item.get('device_model','?')}"
        errors.append((ts, species, dev_name, problems, fv_mode))

print(f"Found {len(errors)} scans with issues:\n")
for ts, species, dev_name, problems, mode in errors[:30]:
    print(f"[{ts}] {species} ({dev_name})")
    for p in problems:
        print(f"   ⚠ {p}")
    print(f"   Mode: {mode}")
    print()

print("\n")
# ── top errors breakdown ────────────────────────────────────────────────────  
print("=" * 60)
print("ERROR TYPE BREAKDOWN")
print("=" * 60)
error_counts = collections.Counter()
for _, _, _, problems, _ in errors:
    for p in problems:
        key = p.split('(')[0].strip()
        error_counts[key] += 1
for err_type, count in error_counts.most_common():
    print(f"  {count:3d}x  {err_type}")

print("\n")

# ── overall quality ─────────────────────────────────────────────────────────
print("=" * 60)
print("OVERALL SCAN QUALITY")
print("=" * 60)
total = len(items)
valid = [(get_scan(i)) for i in items]
known     = sum(1 for p,_,_,_,_ in valid if p.get('species','Unknown') not in ('Unknown',''))
shiny_ok  = sum(1 for p,_,_,_,_ in valid if p.get('isShiny'))
costume_ok= sum(1 for p,_,_,_,_ in valid if p.get('hasCostume'))
epic_plus = sum(1 for p,_,_,_,_ in valid if p.get('rarityScore',0) >= 50)
print(f"Species identified : {known}/{total} ({100*known//total if total else 0}%)")
print(f"Shiny detected     : {shiny_ok}/{total}")
print(f"Costume detected   : {costume_ok}/{total}")
print(f"Epic+ rarity (≥50) : {epic_plus}/{total}")
