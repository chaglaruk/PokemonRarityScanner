import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_catalog import PLACEHOLDER_TOKENS, validate_catalog


class ValidateCatalogTest(unittest.TestCase):
    def test_verified_records_require_source_links(self):
        catalog = minimal_catalog()
        catalog["events"].append(
            {
                "id": "event_without_source",
                "name": "Event Without Source",
                "eventType": "global",
                "startDate": "2026-01-01",
                "endDate": "2026-01-02",
                "costumeIds": [],
                "featuredSpecies": ["Pikachu"],
                "isFirstRelease": False,
                "sourceLinks": [],
                "verificationStatus": "verified_community",
            }
        )

        errors = validate_catalog(catalog)

        self.assertTrue(any("verified but has no sourceLinks" in error for error in errors))

    def test_manual_review_records_must_not_carry_source_links(self):
        catalog = minimal_catalog()
        catalog["costumes"].append(
            {
                "id": "manual_costume",
                "species": "Pikachu",
                "costumeName": "Manual Costume",
                "costumeType": "notable",
                "eventIds": [],
                "sourceLinks": ["https://www.serebii.net/pokemongo/events/pokemongofest2024global.shtml"],
                "verificationStatus": "manual_review_needed",
            }
        )

        errors = validate_catalog(catalog)

        self.assertTrue(any("manual-review records must not carry sourceLinks" in error for error in errors))

    def test_placeholder_tokens_are_rejected(self):
        catalog = minimal_catalog()
        catalog["events"].append(
            {
                "id": "placeholder_event",
                "name": "IN_PERSON_EVENT_NAME",
                "eventType": "in_person",
                "startDate": "2026-01-01",
                "endDate": "2026-01-01",
                "costumeIds": [],
                "featuredSpecies": [],
                "isFirstRelease": False,
                "sourceLinks": [],
                "verificationStatus": "manual_review_needed",
            }
        )

        errors = validate_catalog(catalog)

        self.assertTrue(any("placeholder token" in error for error in errors))

    def test_each_forbidden_placeholder_token_is_rejected(self):
        for token in PLACEHOLDER_TOKENS:
            with self.subTest(token=token):
                catalog = minimal_catalog()
                catalog["events"].append(
                    {
                        "id": "placeholder_event",
                        "name": token,
                        "eventType": "global",
                        "startDate": "2026-01-01",
                        "endDate": "2026-01-01",
                        "costumeIds": [],
                        "featuredSpecies": [],
                        "isFirstRelease": False,
                        "sourceLinks": [],
                        "verificationStatus": "manual_review_needed",
                    }
                )

                errors = validate_catalog(catalog)

                self.assertTrue(any("placeholder token" in error for error in errors))

    def test_example_source_urls_are_rejected_as_placeholders(self):
        catalog = minimal_catalog()
        catalog["events"].append(
            {
                "id": "bad_source",
                "name": "Bad Source",
                "eventType": "global",
                "startDate": "2026-01-01",
                "endDate": "2026-01-01",
                "costumeIds": [],
                "featuredSpecies": [],
                "isFirstRelease": False,
                "sourceLinks": ["https://example.com/source"],
                "verificationStatus": "verified_community",
            }
        )

        errors = validate_catalog(catalog)

        self.assertTrue(any("placeholder token" in error for error in errors))

    def test_event_end_date_must_not_precede_start_date(self):
        catalog = minimal_catalog()
        catalog["events"].append(
            {
                "id": "bad_window",
                "name": "Bad Window",
                "eventType": "global",
                "startDate": "2026-01-02",
                "endDate": "2026-01-01",
                "costumeIds": [],
                "featuredSpecies": [],
                "isFirstRelease": False,
                "sourceLinks": [],
                "verificationStatus": "manual_review_needed",
            }
        )

        errors = validate_catalog(catalog)

        self.assertTrue(any("endDate is before startDate" in error for error in errors))


def minimal_catalog():
    return {
        "version": {
            "version": "test",
            "generatedAt": "2026-06-05T00:00:00Z",
            "schemaVersion": 1,
        },
        "costumes": [],
        "events": [],
        "regionals": [],
        "specialSpecies": [],
        "currentAvailability": [],
        "metaDemand": [],
    }


if __name__ == "__main__":
    unittest.main()
