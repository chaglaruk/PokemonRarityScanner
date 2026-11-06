import copy
import struct
import unittest
import zlib

from build_visual_identity_catalog import _png_dimensions, build_catalog, canonical_json_bytes, validate_catalog
from generate_variant_catalog import determine_variant_class, parse_sprite_file_name


LOCALES = ("de", "en", "es", "es-mx", "fr", "hi", "id", "it", "ja", "ko", "pt-br", "ru", "th", "tr", "zh-tw")


def appearance(dex, species, variant_id, sprite_key, *, form_id="00", shiny=False, costume=False, label=None, asset_key=None, source_proto=None):
    return {
        "dex": dex,
        "species": species,
        "formId": form_id,
        "variantId": variant_id,
        "assetKey": asset_key or sprite_key,
        "spriteKey": sprite_key,
        "isShiny": shiny,
        "variantClass": "costume" if costume else "base",
        "isCostumeLike": costume,
        "variantLabel": label,
        "gameMasterFormName": source_proto,
        "assetPath": f"external/pogo_assets/Images/Pokemon - 256x256/{sprite_key}.png",
    }


def make_inputs():
    rows = [
        appearance(25, "Pikachu", "PARTY_A", "025_00_PARTY_A", costume=True, label="Party hat", source_proto="PIKACHU_POP_STAR"),
        appearance(25, "Pikachu", "PARTY_A", "025_00_PARTY_A_shiny", shiny=True, costume=True, label="Party hat", source_proto="PIKACHU_POP_STAR"),
        appearance(25, "Pikachu", "PARTY_B", "025_00_PARTY_B", costume=True, label="Party hat", source_proto="PIKACHU_ROCK_STAR"),
        appearance(26, "Raichu", None, "026_00", label=None),
    ]
    events = [
        {"eventLabel": "Pikachu Day", "startDate": "2017-02-26", "endDate": "2017-03-06"},
        {"eventLabel": "Pikachu Anniversary", "startDate": "2019-06-28", "endDate": "2019-07-06"},
    ]
    authoritative = []
    for row in rows:
        identity_events = events if row["variantId"] in {"PARTY_A", "PARTY_B"} else []
        authoritative.append({
            **row,
            "historicalEvents": copy.deepcopy(identity_events),
            "sourceIds": ["local_variant_catalog", "bulbapedia:event-pokemon-go"] if identity_events else ["local_variant_catalog"],
        })

    return {
        "pokemonNames": {
            "25": {"id": 25, "name": "Pikachu"},
            "26": {"id": 26, "name": "Raichu"},
        },
        "releasedPokemon": {"25": {"id": 25, "name": "Pikachu"}},
        "pokemon": [
            {"pokedexId": 25, "pokemonName": "Pikachu", "defaultFormId": 7, "forms": [7, 2676, 2677]},
            {"pokedexId": 26, "pokemonName": "Raichu", "defaultFormId": 8, "forms": [8]},
        ],
        "forms": [
            {"formId": 7, "proto": "PIKACHU_NORMAL", "formName": "Normal"},
            {"formId": 8, "proto": "RAICHU_NORMAL", "formName": "Normal"},
            {"formId": 2676, "proto": "PIKACHU_POP_STAR", "formName": "Pop Star", "isCostume": True},
            {"formId": 2677, "proto": "PIKACHU_ROCK_STAR", "formName": "Rock Star", "isCostume": True},
        ],
        "costumes": [{"id": 4, "proto": "PARTY_HAT", "name": "Party Hat", "noEvolve": False}],
        "pokemonTranslations": {
            locale: {"poke_25": "Pikachu", "poke_26": "Raichu"}
            for locale in LOCALES
        },
        "formTranslations": {
            locale: {"form_7": "Normal", "form_8": "Normal", "form_2676": "Pop Star", "form_2677": "Rock Star"}
            for locale in LOCALES
        },
        "variantCatalog": {"entries": rows},
        "authoritativeVariants": {"entries": authoritative},
        "costumeSignatures": {"signatures": [{"key": "025_00_PARTY_A", "isCostume": True}]},
        "shinySignatures": {"entries": [{"key": "025_00_PARTY_A"}]},
    }


def make_catalog(inputs=None, verified_asset_samples=None):
    catalog_inputs = inputs or make_inputs()
    catalog_inputs["verifiedAssetSamples"] = verified_asset_samples or {}
    return build_catalog(
        inputs=catalog_inputs,
        pins={
            "snapshotDate": "2026-11-06",
            "pogoApi": {"baseUrl": "https://pogoapi.net/api/v1/"},
            "watWowMap": {"commit": "fixture", "files": {}},
            "pogoMinersAssets": {"observedCommit": "fixture"},
            "officialEventEvidence": [
                {
                    "id": "fest-2021", "name": "GO Fest 2021", "startDate": "2021-07-17", "endDate": "2021-07-18",
                    "sourceUrl": "https://example.invalid/fest-2021", "sourceType": "official_event_announcement",
                    "verifiedCostumeFormIds": [2676, 2677], "claim": "Pop and Rock Star Pikachu encounters",
                    "availability": "Ticket holder selection and snapshot encounter",
                },
                {
                    "id": "wild-area-2024", "name": "GO Wild Area Global 2024", "startDate": "2024-11-23", "endDate": "2024-11-24",
                    "sourceUrl": "https://example.invalid/wild-area-2024", "sourceType": "official_event_announcement",
                    "verifiedCostumeFormIds": [2676, 2677], "claim": "Pop and Rock Star Pikachu return",
                    "availability": "Ticket holder encounter methods",
                },
            ],
        },
        repo_input_hashes={"fixture": "0" * 64},
        repository_commit="fixture",
        tracked_paths=set(),
    )


class VisualIdentityCatalogTest(unittest.TestCase):
    @staticmethod
    def png_chunk(chunk_type, payload):
        return struct.pack(">I", len(payload)) + chunk_type + payload + struct.pack(">I", zlib.crc32(chunk_type + payload) & 0xFFFFFFFF)

    def test_png_sample_verifier_checks_full_bounded_scanline_stream(self):
        header = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + self.png_chunk(b"IHDR", header)
        png += self.png_chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\xff"))
        png += self.png_chunk(b"IEND", b"")
        self.assertEqual((1, 1), _png_dimensions(png, "fixture.png"))
        corrupt = png[:-13] + bytes([png[-13] ^ 1]) + png[-12:]
        with self.assertRaises(ValueError):
            _png_dimensions(corrupt, "fixture.png")

    def test_costume_identity_is_shared_across_releases_and_shiny_appearances(self):
        catalog = make_catalog()
        identities = [item for item in catalog["visualIdentities"] if item["catalogVariantId"] == "PARTY_A"]
        self.assertEqual(1, len(identities))
        identity = identities[0]
        self.assertEqual("costume_candidate", identity["identityKind"])
        linked_appearances = [item for item in catalog["appearances"] if item["visualIdentityId"] == identity["id"]]
        self.assertEqual(2, len(linked_appearances))
        links = [item for item in catalog["identityEventLinks"] if item["visualIdentityId"] == identity["id"]]
        self.assertEqual(2, len(links))
        linked_event_ids = {item["eventObservationId"] for item in links}
        linked_events = [event for event in catalog["eventObservations"] if event["id"] in linked_event_ids]
        self.assertEqual({"Pikachu Day", "Pikachu Anniversary"}, {event["sourceClaimName"] for event in linked_events})

    def test_similar_costume_labels_do_not_merge_different_source_identifiers(self):
        catalog = make_catalog()
        candidates = [item for item in catalog["visualIdentities"] if item["candidateLabel"] == "Party hat"]
        self.assertEqual({"PARTY_A", "PARTY_B"}, {item["catalogVariantId"] for item in candidates})
        self.assertEqual(2, len({item["id"] for item in candidates}))

    def test_game_master_presence_and_release_feed_absence_remain_uncertain(self):
        catalog = make_catalog()
        raichu = next(item for item in catalog["species"] if item["nationalDexId"] == 26)
        form = next(item for item in catalog["goForms"] if item["speciesId"] == raichu["id"])
        costume = catalog["gameMasterCostumeConcepts"][0]
        self.assertEqual("uncertain", raichu["releaseStatus"])
        self.assertEqual("pogoapi_feed_absence_only", raichu["releaseEvidenceCode"])
        self.assertEqual("uncertain", form["releaseStatus"])
        self.assertEqual("unresolved_identifier_namespace", form["variantCatalogFormMappingStatus"])
        self.assertEqual("uncertain", costume["releaseStatus"])
        pika_costume_forms = [item for item in catalog["goForms"] if item["speciesId"] == "national-dex:0025" and item["sourceFormId"] in {2676, 2677}]
        self.assertEqual({"released"}, {item["releaseStatus"] for item in pika_costume_forms})

    def test_asset_path_is_not_treated_as_verified_pixels_or_cleared_rights(self):
        catalog = make_catalog()
        asset = catalog["visualAssets"][0]
        self.assertIsNone(asset["contentSha256"])
        self.assertEqual("source_path_only", asset["verificationStatus"])
        self.assertEqual("not_downloaded_unverified", asset["renderabilityStatus"])
        self.assertEqual("PokeMiners/pogo_assets", asset["rightsPolicyRef"])
        self.assertEqual("not_cleared_for_redistribution", asset["redistributionRightsStatus"])

    def test_exact_species_proto_crosswalk_beats_broad_costume_candidate_lists(self):
        inputs = make_inputs()
        female = appearance(25, "Pikachu", "PARTY_A", "025_01_PARTY_A", form_id="01", costume=True, label="Party hat", source_proto="PIKACHU_POP_STAR")
        inputs["variantCatalog"]["entries"].append(female)
        inputs["authoritativeVariants"]["entries"].append({**female, "sourceIds": ["local_variant_catalog"]})
        pgo = appearance(25, "Pikachu", "PGO_POPSTAR", "025_00_PGO_POPSTAR", costume=True, label="Pop Star")
        pgo["gameMasterCostumeForms"] = ["PIKACHU_POP_STAR", "PIKACHU_ROCK_STAR"]
        inputs["variantCatalog"]["entries"].append(pgo)
        inputs["authoritativeVariants"]["entries"].append({**pgo, "sourceIds": ["local_variant_catalog"]})
        wrong_species = appearance(26, "Raichu", "PIKA_PROTO", "026_00_PIKA_PROTO", costume=True, label="Pikachu costume", source_proto="PIKACHU_POP_STAR")
        inputs["variantCatalog"]["entries"].append(wrong_species)
        inputs["authoritativeVariants"]["entries"].append({**wrong_species, "sourceIds": ["local_variant_catalog"]})
        catalog = make_catalog(inputs)
        exact = next(item for item in catalog["visualIdentities"] if item["catalogVariantId"] == "PARTY_A")
        broad_only = next(item for item in catalog["visualIdentities"] if item["catalogVariantId"] == "PGO_POPSTAR")
        self.assertEqual("exact_proto_and_species_form_membership", exact["gameMasterCrosswalkStatus"])
        self.assertEqual("go-costume-identity:0025:2676", exact["goCostumeIdentityId"])
        female_exact = next(item for item in catalog["visualIdentities"] if item["catalogVariantId"] == "PARTY_A" and item["catalogFormId"] == "01")
        self.assertEqual(exact["goCostumeIdentityId"], female_exact["goCostumeIdentityId"])
        self.assertNotEqual(exact["id"], female_exact["id"])
        self.assertEqual("missing_or_conflicting_exact_proto", broad_only["gameMasterCrosswalkStatus"])
        self.assertNotIn("goCostumeIdentityId", broad_only)
        wrong_species_identity = next(item for item in catalog["visualIdentities"] if item["catalogVariantId"] == "PIKA_PROTO")
        self.assertEqual("no_unique_species_costume_form_match", wrong_species_identity["gameMasterCrosswalkStatus"])
        self.assertNotIn("goCostumeIdentityId", wrong_species_identity)

    def test_official_rereleases_share_costume_identity_without_runtime_event_inference(self):
        catalog = make_catalog()
        pop_links = [item for item in catalog["canonicalEventCostumeLinks"] if item["goCostumeIdentityId"] == "go-costume-identity:0025:2676"]
        self.assertEqual({"fest-2021", "wild-area-2024"}, {item["canonicalEventId"] for item in pop_links})
        self.assertEqual(2, len(pop_links))
        self.assertTrue(all(item["runtimeEventInferenceAllowed"] is False for item in pop_links))
        identity_ids = {item["id"] for item in catalog["goCostumeIdentities"]}
        self.assertIn("go-costume-identity:0025:2676", identity_ids)

    def test_verified_private_asset_sample_records_hash_and_dimensions_only(self):
        path = "Images/Pokemon - 256x256/025_00_PARTY_A.png"
        digest = "a" * 64
        catalog = make_catalog(verified_asset_samples={path: {"sha256": digest, "width": 256, "height": 256, "sourceCommit": "fixture", "decodeStatus": "full_png_scanline_stream_verified"}})
        asset = next(item for item in catalog["visualAssets"] if item["sourcePath"] == path)
        self.assertEqual(digest, asset["contentSha256"])
        self.assertEqual((256, 256), (asset["pixelWidth"], asset["pixelHeight"]))
        self.assertEqual("source_hash_and_full_png_decode_verified", asset["verificationStatus"])
        self.assertIn("visual identity quality not qualified", asset["renderabilityStatus"])
        self.assertEqual("not_cleared_for_redistribution", asset["redistributionRightsStatus"])
        self.assertNotIn("imageBytes", asset)

    def test_build_is_deterministic_under_source_row_reordering(self):
        inputs = make_inputs()
        reversed_inputs = make_inputs()
        reversed_inputs["variantCatalog"]["entries"].reverse()
        reversed_inputs["authoritativeVariants"]["entries"].reverse()
        first = make_catalog(inputs)
        second = make_catalog(reversed_inputs)
        self.assertEqual(canonical_json_bytes(first), canonical_json_bytes(second))
        validate_catalog(first)


class VariantCatalogCostumeAssetMarkerTest(unittest.TestCase):
    def test_addressable_costume_marker_is_preserved_as_variant_identity(self):
        parsed = parse_sprite_file_name("pm546.cSPRING_2024.icon.png")

        self.assertIsNotNone(parsed)
        self.assertEqual("SPRING_2024", parsed["variant_id"])
        self.assertTrue(parsed.get("explicit_costume_marker"))
        self.assertEqual(
            "costume",
            determine_variant_class(
                species="Cottonee",
                form_id=parsed["form_id"],
                variant_id=parsed["variant_id"],
                costume_species=set(),
                gm_costume_forms={},
                species_with_variant_id_costumes=set(),
                explicit_costume_marker=parsed["explicit_costume_marker"],
            ),
        )


if __name__ == "__main__":
    unittest.main()
