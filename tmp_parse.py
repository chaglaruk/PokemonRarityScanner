import json

with open("recent_30_scans.json", "r", encoding="utf-8") as f:
    data = json.load(f)

for item in data.get("items", []):
    pred = item.get("prediction", {})
    debug = item.get("debug", {})
    species = pred.get("species", "Unknown")
    is_shiny = pred.get("isShiny", False)
    has_costume = pred.get("hasCostume", False)
    debug_str = debug.get("fullVariantDebug", "")
    print(f"Species: {species} | Shiny: {is_shiny} | Costume: {has_costume}")
    print(f"Debug: {debug_str}")
    print("-" * 40)
