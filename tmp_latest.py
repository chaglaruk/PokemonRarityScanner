import json, datetime

data = json.load(open('latest_scans.json', encoding='utf-8'))
items = data.get('items', [])
print(f"Fetched: {len(items)} scans\n")

shiny_ok = 0
shiny_fail = 0
costume_ok = 0
costume_fail = 0
species_wrong = 0

print(f"{'#':<3} {'Time':<8} {'Species':<14} {'Shiny?':<8} {'Costume?':<9} {'FV_Shiny':<10} {'FV_Cos':<8} {'Issue'}")
print("-" * 100)

for i, item in enumerate(items):
    p = item.get('payload') or {}
    pred = p.get('prediction') or {}
    dbg  = p.get('debug') or {}
    raw  = dbg.get('rawOcrText', '') or item.get('raw_ocr_text', '') or ''
    ts_ms = p.get('uploadedAtEpochMs') or int(item.get('uploaded_at_epoch_ms') or 0)
    ts = datetime.datetime.fromtimestamp(ts_ms/1000).strftime('%H:%M:%S') if ts_ms else '?'

    species      = pred.get('species') or item.get('predicted_species') or 'Unknown'
    pred_shiny   = pred.get('isShiny', False) or item.get('predicted_is_shiny') == '1'
    pred_costume = pred.get('hasCostume', False) or item.get('predicted_has_costume') == '1'

    fields = {}
    for part in raw.split('|'):
        k, _, v = part.partition(':')
        fields[k.strip()] = v.strip()

    fv_shiny   = fields.get('FullVariantShiny', '').lower() == 'true'
    fv_costume = fields.get('FullVariantCostume', '').lower() == 'true'
    fv_sprite  = fields.get('FullVariantSpriteKey', '')
    cl_species = fields.get('ClassifierSpecies', '')
    sprite_shiny = '_shiny' in fv_sprite.lower()

    issues = []
    if sprite_shiny and not fv_shiny:   issues.append('FV_SHINY=false')
    if sprite_shiny and not pred_shiny: issues.append('PRED_SHINY=false')
    if fv_shiny and not pred_shiny:     issues.append('FV_OK_BUT_PRED_SHINY=false')
    if fv_costume and not pred_costume: issues.append('COSTUME_LOST')
    if cl_species and cl_species.lower() not in (species.lower(), ''):
        issues.append(f'CL={cl_species}')

    if sprite_shiny and pred_shiny:
        shiny_ok += 1
    elif sprite_shiny:
        shiny_fail += 1

    if fv_costume and pred_costume:
        costume_ok += 1
    elif fv_costume:
        costume_fail += 1

    if cl_species and cl_species.lower() not in (species.lower(), ''):
        species_wrong += 1

    issue_str = ', '.join(issues) if issues else '✅'
    print(f"{i:<3} {ts:<8} {species:<14} {'✅' if pred_shiny else '❌':<8} {'✅' if pred_costume else '❌':<9} {'T' if fv_shiny else 'F':<10} {'T' if fv_costume else 'F':<8} {issue_str}")

print()
print("=" * 60)
print("SUMMARY")
print("=" * 60)
print(f"Shiny sprite -> pred correct : {shiny_ok}")
print(f"Shiny sprite -> LOST in pred : {shiny_fail}")
print(f"Costume FV -> pred correct   : {costume_ok}")
print(f"Costume FV -> LOST in pred   : {costume_fail}")
print(f"Species mismatch (classifier): {species_wrong}")
