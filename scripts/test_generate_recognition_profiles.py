import copy
import math
import unittest

from generate_recognition_profiles import build_cp_multipliers, build_profiles, normalize_name


def row(species, form=None, *, atk=100, defense=100, sta=100,
        types=("NORMAL",), family=None, overrides=()):
    settings = {
        "pokemonId": species,
        "stats": {"baseAttack": atk, "baseDefense": defense, "baseStamina": sta},
        "type": "POKEMON_TYPE_" + types[0],
        "familyId": "FAMILY_" + (family or species),
    }
    if len(types) == 2:
        settings["type2"] = "POKEMON_TYPE_" + types[1]
    if form:
        settings["form"] = species + "_" + form
    if overrides:
        settings["tempEvoOverrides"] = list(overrides)
    return {"data": {"pokemonSettings": settings}}


def level_settings(values=None):
    if values is None:
        values = [0.1 + index * 0.01 for index in range(51)]
        values[48:] = [0.8353, 0.8403, 0.8453]
    return {"data": {"templateId": "PLAYER_LEVEL_SETTINGS", "playerLevel": {"cpMultiplier": values}}}


class RecognitionCpMultiplierTest(unittest.TestCase):
    def test_covers_every_half_level_including_best_buddy(self):
        values = build_cp_multipliers([level_settings()])
        self.assertEqual({f"{1 + index / 2:.1f}" for index in range(101)}, set(values))
        self.assertTrue(all(0 < value < 1 for value in values.values()))
        self.assertEqual(sorted(values.values()), list(values.values()))

    def test_preserves_float32_whole_levels_and_rms_half_levels(self):
        values = build_cp_multipliers([level_settings()])
        self.assertAlmostEqual(0.8403000235557556, values["50.0"], places=15)
        self.assertAlmostEqual(0.845300018787384, values["51.0"], places=15)
        self.assertAlmostEqual(0.8428037290347484, values["50.5"], places=15)
        self.assertAlmostEqual(0.8378037559315699, values["49.5"], places=15)
        self.assertNotAlmostEqual((values["50.0"] + values["51.0"]) / 2, values["50.5"], places=8)

    def test_precise_half_level_preserves_boundary_cp(self):
        values = build_cp_multipliers([level_settings()])
        # Skwovet with IVs 15/14/7: the former linear midpoint floors to CP 1032.
        cp = math.floor((95 + 15) * math.sqrt(86 + 14) * math.sqrt(172 + 7) * values["49.5"] ** 2 / 10)
        self.assertEqual(1033, cp)
        self.assertEqual(149, math.floor((172 + 7) * values["49.5"]))

    def test_rejects_missing_duplicate_and_truncated_level_settings(self):
        for rows in ([], [level_settings(), level_settings()], [level_settings([.1] * 50)]):
            with self.subTest(rows=len(rows)), self.assertRaises(ValueError):
                build_cp_multipliers(rows)

    def test_rejects_invalid_or_nonincreasing_multipliers(self):
        for value in (float("nan"), float("inf"), 0, 1, True, "0.2", .01):
            source = level_settings()
            source["data"]["playerLevel"]["cpMultiplier"][1] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                build_cp_multipliers([source])

    def test_source_order_and_source_data_are_preserved(self):
        source = [row("PIKACHU"), level_settings()]
        before = copy.deepcopy(source)
        self.assertEqual(build_cp_multipliers(source), build_cp_multipliers(list(reversed(source))))
        self.assertEqual(before, source)


class RecognitionProfileGeneratorTest(unittest.TestCase):
    def test_name_normalization_preserves_gender_and_accents(self):
        self.assertEqual("nidoranf", normalize_name("NIDORAN_FEMALE"))
        self.assertEqual("nidoranm", normalize_name("Nidoran♂"))
        self.assertEqual("flabebe", normalize_name("Flabébé"))
        self.assertEqual(normalize_name("FARFETCHD"), normalize_name("Farfetch’d"))

    def test_explicit_forms_replace_stale_base_and_preserve_regional_profile(self):
        rows = [row("FARFETCHD", atk=999),
                row("FARFETCHD", "NORMAL", atk=124, defense=115, sta=141,
                    types=("NORMAL", "FLYING")),
                row("FARFETCHD", "GALARIAN", atk=174, defense=114, sta=141,
                    types=("FIGHTING",))]
        profiles = build_profiles(rows, ["Farfetch'd"])
        self.assertEqual({124, 174}, {p["atk"] for p in profiles})
        self.assertTrue(all(p["candySpecies"] == "Farfetch'd" for p in profiles))
        self.assertIn(["fighting"], [p["types"] for p in profiles])

    def test_equivalent_costume_profiles_merge_deterministically(self):
        rows = [row("PIKACHU", "NORMAL"), row("PIKACHU", "COSTUME")]
        first = build_profiles(rows, ["Pikachu"])
        self.assertEqual(first, build_profiles(list(reversed(rows)), ["Pikachu"]))
        self.assertEqual(1, len(first))
        self.assertEqual(["PIKACHU_COSTUME", "PIKACHU_NORMAL"], first[0]["forms"])

    def test_mega_type_override_replaces_both_types_and_retains_candy_family(self):
        mega = {"tempEvoId": "TEMP_EVOLUTION_MEGA",
                "stats": {"baseAttack": 247, "baseDefense": 331, "baseStamina": 172},
                "typeOverride1": "POKEMON_TYPE_STEEL"}
        profiles = build_profiles([row("AGGRON", types=("STEEL", "ROCK"),
                                       family="ARON", overrides=[mega])], ["Aggron", "Aron"])
        transformed = next(p for p in profiles if p["atk"] == 247)
        self.assertEqual(["steel"], transformed["types"])
        self.assertEqual("Aron", transformed["candySpecies"])

    def test_base_only_mega_override_is_not_lost_when_explicit_forms_exist(self):
        mega = {"tempEvoId": "TEMP_EVOLUTION_MEGA",
                "stats": {"baseAttack": 200, "baseDefense": 200, "baseStamina": 100},
                "typeOverride1": "POKEMON_TYPE_NORMAL"}
        profiles = build_profiles([row("PIDGEOT", overrides=[mega]),
                                   row("PIDGEOT", "NORMAL")], ["Pidgeot"])
        self.assertEqual({100, 200}, {p["atk"] for p in profiles})

    def test_malformed_numeric_profile_is_rejected_instead_of_silently_omitted(self):
        bad = row("PIKACHU")
        del bad["data"]["pokemonSettings"]["stats"]["baseStamina"]
        with self.assertRaises(ValueError):
            build_profiles([bad], ["Pikachu"])

    def test_unknown_type_is_rejected(self):
        with self.assertRaises(ValueError):
            build_profiles([row("PIKACHU", types=("MYSTERY",))], ["Pikachu"])

    def test_source_data_is_not_mutated(self):
        source = [row("PIKACHU")]
        before = copy.deepcopy(source)
        build_profiles(source, ["Pikachu"])
        self.assertEqual(before, source)

    def test_ordinary_evolution_costs_include_only_explicit_candy_variants(self):
        source = row("PIKACHU")
        source["data"]["pokemonSettings"]["evolutionBranch"] = [
            {"evolution": "RAICHU", "candyCost": 50, "candyCostPurified": 45},
            {"evolution": "RAICHU", "candyCost": 50},
            {"temporaryEvolution": "TEMP_EVOLUTION_MEGA", "candyCost": 200},
        ]
        before = copy.deepcopy(source)
        self.assertEqual([45, 50], build_profiles([source], ["Pikachu"])[0]["evolutionCandyCosts"])
        self.assertEqual(before, source)

    def test_same_stats_forms_with_different_evolution_capability_do_not_merge(self):
        normal = row("PIKACHU", "NORMAL")
        normal["data"]["pokemonSettings"]["evolutionBranch"] = [{"evolution": "RAICHU", "candyCost": 50}]
        costume = row("PIKACHU", "COSTUME")
        result = build_profiles([normal, costume], ["Pikachu"])
        self.assertEqual(2, len(result))
        self.assertEqual({(), (50,)}, {tuple(p["evolutionCandyCosts"]) for p in result})
        self.assertEqual(result, build_profiles([costume, normal], ["Pikachu"]))

    def test_absent_or_empty_ordinary_branches_are_known_terminal(self):
        for branches in (None, []):
            source = row("PIKACHU")
            if branches is not None:
                source["data"]["pokemonSettings"]["evolutionBranch"] = branches
            self.assertEqual([], build_profiles([source], ["Pikachu"])[0]["evolutionCandyCosts"])

    def test_temporary_form_evolution_capability_is_unknown_even_with_identical_stats(self):
        temporary = {"tempEvoId": "TEMP_EVOLUTION_MEGA",
                     "stats": {"baseAttack": 100, "baseDefense": 100, "baseStamina": 100},
                     "typeOverride1": "POKEMON_TYPE_NORMAL"}
        source = row("PIKACHU", overrides=[temporary])
        result = build_profiles([source], ["Pikachu"])
        self.assertEqual(2, len(result))
        transformed = next(p for p in result if any("TEMP_EVOLUTION" in f for f in p["forms"]))
        self.assertIsNone(transformed["evolutionCandyCosts"])
        ordinary = next(p for p in result if p is not transformed)
        self.assertEqual([], ordinary["evolutionCandyCosts"])

    def test_malformed_evolution_metadata_cannot_silently_make_a_form_terminal(self):
        invalid = [None, {}, "branches", [None], [{}], [{"evolution": ""}]]
        for cost in (True, -1, 1001, 1.5, "50", None):
            invalid.append([{"evolution": "RAICHU", "candyCost": cost}])
            invalid.append([{"evolution": "RAICHU", "candyCost": 50, "candyCostPurified": cost}])
        for branches in invalid:
            source = row("PIKACHU")
            source["data"]["pokemonSettings"]["evolutionBranch"] = branches
            with self.subTest(branches=branches), self.assertRaises(ValueError):
                build_profiles([source], ["Pikachu"])

    def test_missing_candy_cost_keeps_ordinary_evolution_unknown(self):
        for branches in ([{"evolution": "RAICHU", "evolutionItemRequirement": "ITEM_SUN_STONE"}],
                         [{"evolution": "RAICHU", "candyCost": 50}, {"evolution": "RAICHU"}]):
            source = row("PIKACHU")
            source["data"]["pokemonSettings"]["evolutionBranch"] = branches
            self.assertIsNone(build_profiles([source], ["Pikachu"])[0]["evolutionCandyCosts"])

    def test_explicit_free_evolution_cost_is_preserved(self):
        source = row("PIKACHU")
        source["data"]["pokemonSettings"]["evolutionBranch"] = [{"evolution": "RAICHU", "candyCost": 0}]
        self.assertEqual([0], build_profiles([source], ["Pikachu"])[0]["evolutionCandyCosts"])


if __name__ == "__main__":
    unittest.main()
