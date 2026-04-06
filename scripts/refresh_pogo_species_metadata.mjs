import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const NAMES_PATH = path.join(ROOT, "app/src/main/assets/data/pokemon_names.json");
const STATS_PATH = path.join(ROOT, "app/src/main/assets/data/pokemon_base_stats.json");
const VARIANT_DB_PATH = path.join(ROOT, "app/src/main/assets/data/authoritative_variant_db.json");

const RELEASED_URL = "https://pogoapi.net/api/v1/released_pokemon.json";
const STATS_URL = "https://pogoapi.net/api/v1/pokemon_stats.json";
const GAME_MASTER_URL = "https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json";

const REMOTE_TO_LOCAL_NAME = new Map([
  ["Farfetch’d", "Farfetch'd"],
  ["Farfetch’d", "Farfetch'd"],
  ["Nidoran♀", "Nidoran-f"],
  ["Nidoran♂", "Nidoran-m"],
]);

const LOCAL_SPECIAL_NORMALIZED = new Map([
  ["mrmime", "Mr. Mime"],
  ["mimejr", "Mime Jr."],
  ["typenull", "Type: Null"],
  ["farfetchd", "Farfetch'd"],
  ["sirfetchd", "Sirfetch'd"],
  ["nidoranf", "Nidoran-f"],
  ["nidoranm", "Nidoran-m"],
  ["hooh", "Ho-Oh"],
  ["porygonz", "Porygon-Z"],
  ["jangmoo", "Jangmo-o"],
  ["hakamoo", "Hakamo-o"],
  ["kommoo", "Kommo-o"],
  ["wochien", "Wo-Chien"],
  ["chienpao", "Chien-Pao"],
  ["tinglu", "Ting-Lu"],
  ["chiyu", "Chi-Yu"],
  ["simispour", "Simipour"],
  ["centiskorchere", "Centiskorch"],
  ["graafaiai", "Grafaiai"],
]);

function normalizeName(value) {
  return String(value ?? "")
    .toLowerCase()
    .replace(/♀/g, "f")
    .replace(/♂/g, "m")
    .replace(/['’.:\-\s]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

function toLocalName(value) {
  if (!value) return null;
  const direct = REMOTE_TO_LOCAL_NAME.get(value);
  if (direct) return direct;
  const normalized = normalizeName(value);
  return LOCAL_SPECIAL_NORMALIZED.get(normalized) ?? value.replace(/’/g, "'");
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.status}`);
  }
  return response.json();
}

function buildGameMasterStats(gameMaster, localNames) {
  const byName = new Map();
  const localLookup = new Map(localNames.map((name) => [normalizeName(name), name]));

  for (const entry of gameMaster) {
    const pokemonSettings = entry?.data?.pokemonSettings;
    if (!pokemonSettings?.pokemonId || !pokemonSettings?.stats) continue;

    const templateId = entry?.templateId ?? "";
    if (templateId.includes("_FORM_")) continue;

    const rawName = String(pokemonSettings.pokemonId)
      .toLowerCase()
      .replace("_female", "f")
      .replace("_male", "m")
      .replace(/_/g, "");
    const preferredLocalName =
      localLookup.get(rawName) ??
      LOCAL_SPECIAL_NORMALIZED.get(rawName) ??
      String(pokemonSettings.pokemonId)
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");

    if (byName.has(preferredLocalName)) continue;

    const stats = pokemonSettings.stats;
    if (
      stats.baseAttack == null ||
      stats.baseDefense == null ||
      stats.baseStamina == null
    ) {
      continue;
    }

    byName.set(preferredLocalName, {
      atk: Number(stats.baseAttack),
      def: Number(stats.baseDefense),
      sta: Number(stats.baseStamina),
      heightM: Number(pokemonSettings.pokedexHeightM ?? 0),
      weightKg: Number(pokemonSettings.pokedexWeightKg ?? 0),
    });

  }

  return byName;
}

function buildReleasedTargets(statsRows, releasedMap) {
  const releasedSet = new Set(Object.values(releasedMap).map((item) => item.name));
  return new Set(
    statsRows
      .filter((row) => row.form === "Normal" && releasedSet.has(row.pokemon_name))
      .map((row) => toLocalName(row.pokemon_name))
      .filter(Boolean)
  );
}

function buildVariantTargets(variantDb) {
  return new Set(
    (variantDb.entries ?? [])
      .map((entry) => toLocalName(entry.species))
      .filter(Boolean)
  );
}

function buildIdLookup(statsRows) {
  const lookup = new Map();
  for (const row of statsRows) {
    if (row.form !== "Normal" || row.pokemon_id == null) continue;
    const localName = toLocalName(row.pokemon_name);
    if (!localName) continue;
    const current = lookup.get(localName);
    if (current == null || row.pokemon_id < current) {
      lookup.set(localName, row.pokemon_id);
    }
  }
  return lookup;
}

function buildStatsRowLookup(statsRows) {
  const lookup = new Map();
  for (const row of statsRows) {
    if (row.form !== "Normal") continue;
    const localName = toLocalName(row.pokemon_name);
    if (!localName) continue;
    if (!lookup.has(localName)) {
      lookup.set(localName, {
        atk: Number(row.base_attack),
        def: Number(row.base_defense),
        sta: Number(row.base_stamina),
        heightM: 0,
        weightKg: 0,
      });
    }
  }
  return lookup;
}

function orderNames(names, existingNames, idLookup) {
  const existingIndex = new Map(existingNames.map((name, index) => [name, index]));
  return [...names].sort((a, b) => {
    const idA = idLookup.get(a);
    const idB = idLookup.get(b);
    if (idA != null && idB != null && idA !== idB) return idA - idB;
    if (idA != null && idB == null) return -1;
    if (idA == null && idB != null) return 1;
    return (existingIndex.get(a) ?? Number.MAX_SAFE_INTEGER) - (existingIndex.get(b) ?? Number.MAX_SAFE_INTEGER);
  });
}

async function main() {
  const existingNames = JSON.parse(fs.readFileSync(NAMES_PATH, "utf8"));
  const existingStats = JSON.parse(fs.readFileSync(STATS_PATH, "utf8"));
  const variantDb = JSON.parse(fs.readFileSync(VARIANT_DB_PATH, "utf8"));

  const [releasedMap, statsRows, gameMaster] = await Promise.all([
    fetchJson(RELEASED_URL),
    fetchJson(STATS_URL),
    fetchJson(GAME_MASTER_URL),
  ]);

  const gmStatsByName = buildGameMasterStats(gameMaster, existingNames);
  const byNameId = buildIdLookup(statsRows);
  const statsRowLookup = buildStatsRowLookup(statsRows);
  const releasedTargets = buildReleasedTargets(statsRows, releasedMap);
  const variantTargets = buildVariantTargets(variantDb);

  const mergedNames = new Set(existingNames);
  for (const name of releasedTargets) mergedNames.add(name);
  for (const name of variantTargets) {
    if (gmStatsByName.has(name) || statsRowLookup.has(name)) mergedNames.add(name);
  }

  const orderedNames = orderNames(mergedNames, existingNames, byNameId);
  const refreshedStats = {};
  const addedNames = [];
  const missingStats = [];

  for (const name of orderedNames) {
    if (!existingNames.includes(name)) addedNames.push(name);
    const stats = gmStatsByName.get(name) ?? statsRowLookup.get(name) ?? existingStats[name];
    if (!stats) {
      missingStats.push(name);
      continue;
    }
    refreshedStats[name] = stats;
  }

  fs.writeFileSync(NAMES_PATH, JSON.stringify(orderedNames, null, 2) + "\n", "utf8");
  fs.writeFileSync(STATS_PATH, JSON.stringify(refreshedStats, null, 2) + "\n", "utf8");

  console.log(
    JSON.stringify(
      {
        namesCount: orderedNames.length,
        statsCount: Object.keys(refreshedStats).length,
        addedNames,
        missingStats,
      },
      null,
      2
    )
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
