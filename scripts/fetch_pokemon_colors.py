import json
import requests

# Bu script Pokemon GO shiny ve normal formlarının dominant renklerini çeker.
# Not: Gerçek bir Pokemon API'si (PokeAPI gibi) her zaman RGB verisi sağlamayabilir.
# Bu örnekte bir veritabanından veya sprite analizinden geldiği varsayılmaktadır.

def fetch_pokemon_colors():
    pokemon_list = ["Dragonite", "Exeggutor", "Charizard", "Gyarados", "Mewtwo"]
    colors_db = {}

    for name in pokemon_list:
        print(f"Fetching colors for {name}...")
        # Mocking data fetching logic
        # Gerçekte bir sprite indirip dominant rengi hesaplayabilir:
        # img = Image.open(requests.get(sprite_url, stream=True).raw)
        # dominant_color = calculate_dominant_color(img)
        
        # Örnek değerler:
        if name == "Dragonite":
            normal = [255, 182, 66]
            shiny = [141, 196, 82]
        elif name == "Charizard":
            normal = [238, 135, 34]
            shiny = [50, 50, 50]
        else:
            normal = [200, 200, 200]
            shiny = [150, 150, 150]

        colors_db[name] = {
            "normal": normal,
            "shiny": shiny
        }

    with open("app/src/main/assets/data/pokemon_colors_update.json", "w") as f:
        json.dump(colors_db, f, indent=2)
    
    print("Veri çekme scripti tamamlandı.")

if __name__ == "__main__":
    fetch_pokemon_colors()
