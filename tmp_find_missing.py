"""
Step 1: Identify ALL sprites that appeared in telemetry errors but are missing from authoritative_variant_db.json
"""
import json, re, collections

auth_db = json.load(open('app/src/main/assets/data/authoritative_variant_db.json', encoding='utf-8'))
auth_sprites = {e['spriteKey'].lower() for e in auth_db['entries']}
auth_by_species = collections.defaultdict(list)
for e in auth_db['entries']:
    auth_by_species[e['species'].lower()].append(e)

print(f"Auth DB: {len(auth_db['entries'])} entries, {len(auth_sprites)} unique sprite keys")

# Load telemetry
telem = json.load(open('all_scans.json', encoding='utf-8'))

missing_sprites = collections.Counter()
missing_species = set()
costume_no_auth = []

for item in telem['items']:
    p = item.get('payload') or {}
    dbg = p.get('debug', {})
    pred = p.get('prediction', {})
    raw = dbg.get('rawOcrText', '') or item.get('raw_ocr_text', '') or ''
    species = pred.get('species', '') or item.get('predicted_species', '')

    fields = {}
    for part in raw.split('|'):
        k, _, v = part.partition(':')
        fields[k.strip()] = v.strip()

    fv_sprite = fields.get('FullVariantSpriteKey', '')
    cl_sprite  = fields.get('ClassifierSpriteKey', '')
    var_sprite = fields.get('VariantClassifierSpriteKey', '')
    cl_costume = fields.get('ClassifierCostume', '').lower() == 'true'
    fv_costume = fields.get('FullVariantCostume', '').lower() == 'true'

    for sprite in [fv_sprite, cl_sprite, var_sprite]:
        if sprite and sprite.lower() not in auth_sprites:
            missing_sprites[sprite] += 1

    if cl_costume and not fv_costume and species:
        costume_no_auth.append((species, cl_sprite, var_sprite))

    if species and species.lower() not in auth_by_species:
        missing_species.add(species)

print(f"\n=== SPRITES IN TELEMETRY BUT NOT IN AUTH DB ===")
for sprite, count in missing_sprites.most_common(40):
    print(f"  {count:3d}x  {sprite}")

print(f"\n=== COSTUME DETECTED BY CLASSIFIER BUT NO AUTH MATCH ===")
for species, cl_s, var_s in sorted(set(costume_no_auth)):
    print(f"  {species}: cl={cl_s}, var={var_s}")

print(f"\n=== SPECIES IN TELEMETRY WITH NO AUTH DB ENTRY ===")
for s in sorted(missing_species):
    print(f"  {s}")
    
# Check Dedenne specifically
print(f"\n=== DEDENNE IN AUTH DB ===")
dedenne = auth_by_species.get('dedenne', [])
print(f"  Entries: {len(dedenne)}")
for e in dedenne:
    print(f"  {e['spriteKey']} | costume={e['isCostumeLike']}")
