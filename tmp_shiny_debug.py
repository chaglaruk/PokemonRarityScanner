import json, datetime

data = json.load(open('latest_scans.json', encoding='utf-8'))
items = data.get('items', [])
print(f"Fetched: {len(items)} scans\n")

print(f"{'#':<3} {'Time':<8} {'Species':<14} {'Pred Shiny':<10} {'FV_Sprite':<40} {'Issue'}")
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
    
    fields = {}
    for part in raw.split('|'):
        k, _, v = part.partition(':')
        fields[k.strip()] = v.strip()

    fv_shiny   = fields.get('FullVariantShiny', '').lower() == 'true'
    fv_sprite  = fields.get('FullVariantSpriteKey', '')
    cl_species = fields.get('ClassifierSpecies', '')
    sprite_shiny = '_shiny' in fv_sprite.lower()

    issues = []
    if sprite_shiny and not fv_shiny:   issues.append('FV_SHINY=false')
    if sprite_shiny and not pred_shiny: issues.append('PRED_SHINY=false')
    
    if issues or (sprite_shiny and not pred_shiny):
        issue_str = ', '.join(issues)
        print(f"{i:<3} {ts:<8} {species:<14} {'✅' if pred_shiny else '❌':<10} {fv_sprite:<40} {issue_str}")
