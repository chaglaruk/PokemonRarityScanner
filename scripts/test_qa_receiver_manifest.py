#!/usr/bin/env python3
"""Assert the QA scan receiver keeps Android's privileged sender gate."""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
MANIFEST = Path(__file__).resolve().parents[1] / "app/src/qa/AndroidManifest.xml"


class QaReceiverManifestTest(unittest.TestCase):
    def test_scan_receiver_is_exported_only_behind_dump_permission(self) -> None:
        root = ET.parse(MANIFEST).getroot()
        receivers = [
            receiver
            for receiver in root.findall("./application/receiver")
            if receiver.get(ANDROID_NS + "name") == ".qa.QaScanOnceReceiver"
        ]

        self.assertEqual(len(receivers), 1)
        receiver = receivers[0]
        self.assertEqual(receiver.get(ANDROID_NS + "exported"), "true")
        self.assertEqual(receiver.get(ANDROID_NS + "permission"), "android.permission.DUMP")
        self.assertFalse(receiver.findall("./intent-filter"))


if __name__ == "__main__":
    unittest.main()
