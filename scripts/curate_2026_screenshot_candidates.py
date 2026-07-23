"""Build and validate the privacy-safe 2026 screenshot candidate manifest.

The source directory is read-only. Filenames and pixel text never enter output.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from PIL import Image


EXPECTED_SOURCE_COUNT = 730
EXPECTED_SOURCE_BYTES = 473_826_206
EXPECTED_SOURCE_DIGEST = "e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1"
EXPECTED_NEAR_GROUPS = 61
EXPECTED_NEAR_FILES = 176
EXPECTED_ELIGIBLE = 724
DEVELOPMENT_COUNT = 100
HOLDOUT_COUNT = 20
RECORD_COUNT = DEVELOPMENT_COUNT + HOLDOUT_COUNT

NEAR_DUPLICATE_METHOD = {
    "imageLibrary": "Pillow",
    "colorMode": "L",
    "pHash": {"resize": "32x32", "dctSize": 8, "median": "excluding_dc", "maxHammingDistance": 8},
    "dHash": {"resize": "9x8", "maxHammingDistance": 8},
    "thumbnail": {"resize": "36x78", "maxMae": 1.0, "minCorrelation": 0.999},
}

DEVELOPMENT_SHA256 = """
001320f78dc7931317e70fef4ac6a010f4a7e6df8cc27b849a28cead3ea62e48
055e7effd4336d7af96fab8b25d7493f25d1e235d44fbee5507de0b5e68917b2
0e6d00636e51545115c1aa2ee5f1a6feb03ff96b709b12aa143c68d3faccaa48
0f9dd98b6234a3a68a50e5b66abf19210d32cfba3b4a64288a8c7877e2de1031
1278f0f52b5fd3c3c33313ef65ee2f8fa4ef8f0b6dea4c3d9658faa5645a3f37
18c4ca0565a40ac1c0ec06de1d7485b27acae662f289f717a58bdb009f79ad3e
18eb82d241a744333e910905262f75a5c43941b9df771b4c9e1fb92d7c68668c
1badb83c6d7e7b84013094796ad6ac3e108ca4d54d9e3f7804741b05fafa1ad2
1c576ea5006ca30771d234b527b8d4ae51f73f2f3add7994c8a3088b934fabed
1e607535c6723869a648d787cd4b63429b95854ae33bd722274426ad24b678d6
1fb8ebc94af1864e26f29a7de8b57691028c5ced9baf17dc534f04085075ed89
23655c9d4a01af5d86bd7c31eb65fd3bf3cf60d4f8b7f9b4f69b409ca2cee2ed
25c503667718ae0a7bb8ef62f3451d6c3d2fad30d804bcbd0631b7c8610058e0
25e92a91f0abfecf40d1c7447e1b5d864b2ad968489c29d032818332705f4488
2a398a5b2f501d03173b97e0cd673397ed09af207edb34a57f332cc303c3c978
304d5a1726f5e33191fe4cc579a753a0e8ee54e1353392ec522af2543e1456fa
318e556f6accdc58ef6f7540df163ffa2c22684f8ff7f3383b9d4b6aa43ed427
31fe26075dbb468168a3dc74fc19d45edea5cb461ca744c9bb163a4efbfc3277
320327729036ec15197f3a92331adcd798e98c6ace9a65f88c6c5f5196d93a75
3a1fe75764d75e84d26471c461de7f104ecf7e8d4545113286132062a7195bb7
3df5a34a46f03d2c45ad6ac111fbec56056f3b0bb42c9664bbd690621f160ffc
3ea0973737cd5d849f7d40ba02c608e227d36e4d2a1c6352d1a05b917d01d2a6
3ecab28f913a4919bef68d79d74c307604fd89f361fa8f93545688605901fcf6
3f189ab7e17aff48c3906f102c7fd4f98e5f4008e7340b1617d7268c19146c56
3f888ad2c771489282862e4146b98639232fb5343496bae33de81c3d472d93c4
4495f95a3c913894b71147ce31e82f90544d7393f0bca189cfaf914d3104b32c
44f4bf9a71fd66160eb8ca4c10a57885442cd0e5bd0698fb5f44764dd444a378
4d014b3c8b4700033c0c9ebdf847c50b32507fd48f425830768e8b41c8fb0a71
4eae3202f4a247d3cf7dcd12e9d28b216299cd8f08fc8504c2255113c483f935
4f4a00f24670b668cc1fe14e947520a7e515f5bda04b35e62e3abdda5c8250c8
505468b77fae8d6648ad878667e49c5ccc9b6d2af45b43e8edc6ff0bfd607702
587b2397746ad8703e39a3ab2d723b99c342fb62dd77fa77926d295b75571a7e
5a8503b9a1ac6a694409ef62388a342a5922f67af9c5e83ea7605ddd4df79467
66c4fdd21ba7d622d5d84add4cb66246fb6fbce1795a0ed4ca936834b1e42f52
67c29cebe70549501b6f7ce6ec1a8c28e3941de6cc367de37b399b67a868f049
68d6df297a2716de7aca9f5fb7c4709827414802a7279b6f836d3dd877ea8c4a
6d579f30030cb2e7bf04ce015a1373788bb6cdf0a84331838995b4bd209f5ca6
6d89328b132baa8511e067d52e735097aeaeb482dc9294786109ba0bde350f65
6f87404390b20d662978ef31652ffb25e79ee8b11a1830ae392aab7180a1b436
71ca48f67f3fab982bb99431f911720b271c4204b1dce9cec091c1bfabac94ff
7363217dbe014f6290a7f27d5212da82e8a6a11fda71d52c50645470c02716d9
7407d949985e0017e302c353d25d93df0501f7e53e0edf980a70a988d626b740
74e827432818ab9c665d9f07ee68450e327bc76ce3348f6b48290f355076467f
77bb33afa4b7b14f261598bd8f4f108cb24a6d00891984b3ab6b3ce8f3060760
7bb0b60dc7d6f9ebe905b201235bf0c946c0f993c65ff265c7b2c0ec2a7a925f
7bbc3c1742b9882d1c32db24cdddf7cdbdf8368c6882dfa64dd4e92ab8d0f411
7c96f55e95b4e78125b6627a9055cf00c35ff216216b90dbe19fcfc09a505347
7d6c57c15c4436bcd0ea64141aaddcc9477bf09546d540bae3f4ddd76bc98531
84f9aace0efb9e506b1c1310fa7f2f8c2d1bbaff9d819bd3b3f39231ed0eb06c
8a6c8fcc560acef016bff24bd64aaeed941f9cee05eaecbccbd7263588f75531
8d5125f33f62a9f0a1e3f608b910d8a4f9580bb85b4d6987218b32de92c86da4
8e2d9d9712ddc4917920cf6c3a065e8bfd509921bfd41c83940bef2e83c6331c
92a1750ac01d23e6e48ab8bc234ea86089f143a4df225e2f4b2b824c9f9b5647
93d6d70ae4adf034555f69884c48bece5db04a6b95d610b2c1b8538b652b26c2
94a2208bdfe1c825275b5a170fa7db98e80cc9806e1ecd3081f8f9b4fe330bbc
95a001fe25aa20e760be1a6194253999ac02c8af95e5161903995a5d46e0fd38
9bac0b9b3657889898a1da04f09ca3652c46555d1d02f15a0a9bd0b76ce270c4
9bba6bde144dc18110ea8a4623f6abb79c932f175f2dfe2395d231d4a79e9386
9c0c90ac59707adc2fea36b8aeb30509af5548b5d894bfdc509f3b07384234ea
9df9e1f262bfba62192497b9d8170aa72674bd34afd82aa372297e489d3e0dcb
9e60a6bcfc89c8d8dc2875dd3d8a1402d39a84a7f46f51c026fc2bc209df9901
9f8cf0ec9e50910923a94824023863a6e60ce70eea950a709d4ba1393aaea494
a071d34f7cca2d0a93e516ab1576f72c3722a806c577861d87643dadeaac5f19
a1d8128ff930cd0dfdd0fcee46ac00e8e5de23f3e31f1281ef6db72eb2b5a2db
a52b416f4de4aac01e3cec510eb372ac05b258adb1373e6d38b774921f21b150
a973c01041eb6cdc01239ba113667ffad21ca21db5000c9f57ded2a006876094
ac833ba67554e6d66da737025c5a654990e180a1bf961e4cf06ad50d2c4d9292
aca4f692bc598a52dee42271086bf0e7db6c4bcaed1a58cb16c79bd49bda230e
adda05348cb4ac8f92dafe599a91a8f812eddd4df0d0999921f55abbf9d9fe9e
af5ec49c9621f5f9917d75b8e76cbb9c46021b060a7c27b300be7ea997c6df3a
b03f4b71173f1df6e9ac296bfb83086b13b387d160e19d38f0ce1afffda1cf98
b0b4fa6f68e8fb932dfd9b2d760032274c366416f20c5757f13bd73a62c42757
b0e5a5ec7a8d288d8f38a2c67e210b2899baa0596f1296deff30b3e0b9d3dcdb
b6a593a7692ae6be79e462e09ae62b0f63af30c3354506c45e9151b8ec28dd2c
b8cc73a13506f0bd753b85c702484eae4f65da21b3091cee3888c63d46e04204
b98f0095dffeb83226e0e5cfb2a9570adcd91f320ad9fde4ac36acee402283ab
bc074f61b9999e86aba679a65f133714e8368d33f4104648412ffe5fc68a0b1f
bdefd05f8e5e15632b12de924a634133beaf88596d4c0cb6b6b5f9b52f48ff48
be45b94201aa0a43dedcd4e4718f5cded31ef66bd5dfcc44dc1919ee4acdb9ed
c4bfb41b7801feb35cbf51e02c60640820ce05332dbb81cb7c6db79262986d39
c78dd9ddd8c7cb0cbf64482543eb996942c315bd0ee26c35bc5c7e82fdcb7ac7
cb0d43d645adf90a6499075486b2a7ee7dca861628db1982cb70368944485f17
cd6d9e422a50e3345ceb3dfb57ca2d7377df388d49e85700b5aa71643bf26c02
ce1ae21471890f57d1b465e5ad68cbd93bca0122c291d0e72deb01ced8211a01
ce1e704e2bc6a8379ea180db5c4a3110553e5761454a52af0a34b8cd5f364eed
d36a6e97b2812d8a84f42f9a22c4afefbd59edd182b0e9af13bbbd81daf10d34
d60086bc9cc6faf625164359979bcc83366215e447d33cfbaae96615a32ad9df
d89a7640c7fa571120f6f9692a06253fa54d10cfba0f6fd300c10c4263b8da1f
db8376c2860f163bcf10530ac8d5e3e95a3d01a5829f9f52317ea7d795533b84
df858d65c09ff3861412071acc82ab00f9b53c2c08ec276fe01b8ff1d97f06d8
dfec9d10beff1a14ec95aecaa15e61824c7bf4f5aa1a2affa8cd9696caad46cb
e1c63bd06462bbda21c4cce4c032743a790b35124a2c346bb314fd608b2f954d
e4db3e53aa02bbb4704eb2b3a206dbf5c81f7a9dc89e977f63d56eaa3891e5f4
e5b1c0e3fa193441cafc5581c665396c9721a9002221ae354198dcc0b7f5874b
e7be5664aa85e90ace199d12603a2a34a7a94b640fbfb27d5be7c3bf56265f3d
e9adcd9b08bcc3dae245c4f10fc51f8c9651d8f18d516d2b295baaee891894fa
f1086a3ffb9140093efd41c978f73ada625e742f3d3fb114fe39e467b955ce20
f528e88b34aee1e462d94107d4282678092e8d5cee3b9959624e3758bb8d31d8
f5dc83d6e67296e296d6e80ed85752f8de2d8d8814fb9213cd083765df0698d0
f8d22a6583a9b3ca5bb1ac586511ae6d0e53f40b3d1394ab5cffc9a85956580b
""".split()

HOLDOUT_SHA256 = """
012482209f32f08c61e68d8815b8c81d6c6636595d0cb703a1c3e226677eb15e
037dfa4aa14c3bfaef35a66ef53bd4af53f71181220eaa1afeb4a4964313ffe2
13587c375e562e185f70a921ef88510efbf6344fd3e00658c96ea93dc4c90034
1a8661d0986f9640ac118c946337cf0b388e53dadf1a26a5dbd2e8ad3a776457
265ff7cea4aa56476d831f6a61479570a8f0abae669e050dbd387989eee66a30
2b2a7098afb56ea2be9c7b11cf47844141f802baaa52361930aae8903f1814ac
2bd9484446d538bc76fb72667c46763fa51da0fa352cb12c53e3f9ec00048ca8
3c93668220f29e5214f4773fe4236c52926e2338004840c0611eb021faba3df2
50b141893c3ff5ce06cb6f7395b6874f924000a0b032faa831df4f2104df3da7
5ba42543f9f23d33b78b1569261580f31cda7aa80d5a88047948bff6680e72b9
6d21b40fd53f8bf3a8c5bb0fa70ccdb7fd4411961ed8c1e3e7894f6dd0e944ea
8c0e0a73f13864174d02dbf05d001617851ff22582561959675c7b91d2720d67
a1f8e3796e27d93900b973a1b37397349c753eadc89e015502b444eed3ac1a7a
b41ad69cc5f82b54a27553fb5e4445cdd6e079b51cefbf5dd20f48a1d152f4e6
b751a29efd000279a2d1068f9299f2600aabcba6969f9715c9755f85abe48e59
b761da88300e618cdec701eb1057913585aa6228159f9a92f5eb9d2259314180
ba19d73c87646d9e38cbc61570519b808accf5bd933f26b9dec8b42bbf833632
bdf4d071eb242dc630fb4f76740d9033a4e16d0cbfc48a47aadee84ea0649e86
e2320724d3677b9f6b19ae9680c60b76b3eccb3f1ff2e280733a77be4dcd7ab5
eb96a0727433b689698ce8e234ccfff0a7b74b400c35ed8bce4960785f16caca
""".split()

REDUNDANT_EXCLUSIONS = {
    "near_010": ("7ea78b97767221affa2dfb6a5907f116b65be8c9fb4cd8844c216edb6f0f69a5", "51b9727efa0904ba68cfded04b91a6345d7bcc01767a6631c6fac37af686b78d"),
    "near_016": ("7596e755662b39c9d4b4e0ee747fdf9f383cc8794fb981bc71543b5c3de73bda", "198f5d7ea6619130aa6069693906d24a66c5893c49ef6a0f78a34e58021acaac"),
    "near_025": ("215f3cb03dae52659980e944e0c0345c11e98e107ec75694543de457ef4fc270", "2654f7b33a81279c8d4c81e7bcada3e055553b0bc1fa4761957f647768ac8a0f"),
    "near_029": ("54d98a8583ed4ff226a6d9b395ae2cbe970b2a39ef14d91ae95713c29b8925f8", "2f34e7b7160d385553a45e9fbb27e678335bb85f86d68e5785e06bee91a56596"),
    "near_057": ("9d620864a2a6e9b962bca71540f586227f2491cf1147d08ecf4c70c7703c9c39", "957bed26d57d7f970e137d006045cb08666b0cd449d7dae1c59d32afae364e81"),
}

ROOT_KEYS = {
    "schemaVersion", "datasetId", "candidateOnly", "authoritative", "containsScreenshotBytes",
    "truthLabelsPresent", "prospectiveHoldoutQuarantined", "sourceFileCount", "sourceAggregateBytes",
    "sourceDigestSha256", "nearDuplicateGroupCount", "nearDuplicateGroupedFileCount",
    "redundantExcludedCount", "structurallyEligibleCount", "nearDuplicateMethod",
    "nearDuplicateClusters", "records",
}
RECORD_KEYS = {
    "id", "lane", "sha256", "nearDuplicateClusterId", "duplicateDecisionReason", "width", "height",
    "format", "colorMode", "bitDepth", "byteSize", "privacyClassification", "manualTruthStatus",
    "provenanceStatus", "publicationStatus", "overlayPresent", "likelyDetailsScreen", "likelyCpPresent",
    "likelyHpPresent", "likelyNamePresent", "likelyCandyFamilyPresent",
}
CLUSTER_KEYS = {"id", "selectedMemberIds", "sourceMemberCount", "decisionReason"}
LANES = {"development_candidate", "prospective_holdout_candidate"}
DUPLICATE_DECISIONS = {
    "REDUNDANT_NEAR_IDENTICAL", "PRESERVE_SCROLL_VARIANT", "PRESERVE_STATE_VARIANT",
    "PRESERVE_LAYOUT_VARIANT", "FALSE_POSITIVE_SIMILARITY", "NEEDS_HUMAN_REVIEW",
}
SHA_PATTERN = re.compile(r"^[0-9a-f]{64}$")
PRIVATE_VALUE_PATTERNS = [
    re.compile(r"(?i)(?:[a-z]:[\\/]|/(?:Users|home)/)"),
    re.compile(r"(?i)\b[^\s/\\]+\.(?:png|jpe?g|webp)\b"),
    re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?\b"),
    re.compile(r"(?i)\badb\b"),
    re.compile(r"(?i)\b(?:authorization|bearer|password|secret|telemetry)\b"),
]


@dataclass(frozen=True)
class ImageRecord:
    sha256: str
    byte_size: int
    width: int
    height: int
    format: str
    color_mode: str
    bit_depth: int
    phash: int
    dhash: int
    thumbnail: np.ndarray
    overlay_present: bool
    likely_details: bool
    likely_cp: bool
    likely_hp: bool
    likely_name: bool
    likely_candy: bool


def _bits_to_int(bits: np.ndarray) -> int:
    return sum(1 << index for index, value in enumerate(bits.ravel()) if bool(value))


def _edge_fraction(gray: np.ndarray, x1: float, y1: float, x2: float, y2: float) -> float:
    height, width = gray.shape
    region = gray[int(y1 * height):int(y2 * height), int(x1 * width):int(x2 * width)]
    return float((cv2.Canny(region, 50, 150) > 0).mean())


def _read_image(data: bytes) -> ImageRecord:
    sha256 = hashlib.sha256(data).hexdigest()
    try:
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            image_format = image.format or ""
            color_mode = image.mode
            width, height = image.size
            gray = np.asarray(image.convert("L"))
            rgb = np.asarray(image.convert("RGB").resize((108, 234), Image.Resampling.BOX))
    except Exception as exc:
        raise ValueError(f"Undecodable source image sha256={sha256}") from exc

    psmall = cv2.resize(gray, (32, 32), interpolation=cv2.INTER_AREA).astype(np.float32)
    dct = cv2.dct(psmall)[:8, :8]
    median = float(np.median(dct.ravel()[1:]))
    phash = _bits_to_int(dct > median)
    dsmall = cv2.resize(gray, (9, 8), interpolation=cv2.INTER_AREA)
    dhash = _bits_to_int(dsmall[:, 1:] > dsmall[:, :-1])
    thumbnail = cv2.resize(gray, (36, 78), interpolation=cv2.INTER_AREA).astype(np.float32)

    gray_small = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    card = gray_small[70:229, 3:105]
    likely_details = float((card > 210).mean()) > 0.4
    cyan = ((hsv[:, :, 0] >= 78) & (hsv[:, :, 0] <= 98) & (hsv[:, :, 1] >= 100) & (hsv[:, :, 2] >= 80))
    overlay_present = float(cyan[168:, :].mean()) > 0.005
    return ImageRecord(
        sha256=sha256,
        byte_size=len(data),
        width=width,
        height=height,
        format=image_format,
        color_mode=color_mode,
        bit_depth=data[24] if len(data) > 24 and data.startswith(b"\x89PNG\r\n\x1a\n") else 0,
        phash=phash,
        dhash=dhash,
        thumbnail=thumbnail,
        overlay_present=overlay_present,
        likely_details=likely_details,
        likely_cp=_edge_fraction(gray_small, 0.25, 0.03, 0.75, 0.14) > 0.02,
        likely_hp=_edge_fraction(gray_small, 0.25, 0.38, 0.75, 0.50) > 0.01,
        likely_name=_edge_fraction(gray_small, 0.18, 0.32, 0.82, 0.46) > 0.02,
        likely_candy=_edge_fraction(gray_small, 0.02, 0.52, 0.98, 0.69) > 0.02,
    )


def _inventory(source_root: Path) -> tuple[list[ImageRecord], tuple[tuple[str, int, int], ...]]:
    if not source_root.is_dir():
        raise ValueError("Source root is not a directory")
    files = sorted((path for path in source_root.rglob("*") if path.is_file()), key=lambda path: path.relative_to(source_root).as_posix())
    if not files:
        raise ValueError("Source root contains no files")
    if any(path.suffix.lower() != ".png" for path in files):
        raise ValueError("Source root contains a non-PNG file")
    records = []
    snapshot = []
    for path in files:
        stat_before = path.stat()
        data = path.read_bytes()
        record = _read_image(data)
        records.append(record)
        snapshot.append((record.sha256, stat_before.st_size, stat_before.st_mtime_ns))
    if len({record.sha256 for record in records}) != len(records):
        raise ValueError("Source corpus contains an exact SHA-256 duplicate")
    return records, tuple(sorted(snapshot))


def _source_snapshot(source_root: Path) -> tuple[tuple[str, int, int], ...]:
    snapshot = []
    for path in sorted((item for item in source_root.rglob("*") if item.is_file()), key=lambda item: item.relative_to(source_root).as_posix()):
        stat = path.stat()
        snapshot.append((hashlib.sha256(path.read_bytes()).hexdigest(), stat.st_size, stat.st_mtime_ns))
    return tuple(sorted(snapshot))


def _near_groups(records: list[ImageRecord]) -> list[list[ImageRecord]]:
    parent = list(range(len(records)))

    def find(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def union(left: int, right: int) -> None:
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parent[right_root] = left_root

    for left in range(len(records)):
        for right in range(left + 1, len(records)):
            first, second = records[left], records[right]
            if (first.phash ^ second.phash).bit_count() > 8 or (first.dhash ^ second.dhash).bit_count() > 8:
                continue
            mae = float(np.mean(np.abs(first.thumbnail - second.thumbnail)))
            if mae > 1.0:
                continue
            correlation = float(np.corrcoef(first.thumbnail.ravel(), second.thumbnail.ravel())[0, 1])
            if correlation >= 0.999:
                union(left, right)

    components: dict[int, list[ImageRecord]] = {}
    for index, record in enumerate(records):
        components.setdefault(find(index), []).append(record)
    groups = [sorted(group, key=lambda item: item.sha256) for group in components.values() if len(group) > 1]
    return sorted(groups, key=lambda group: group[0].sha256)


def _source_digest(records: list[ImageRecord]) -> str:
    text = "\n".join(sorted(record.sha256 for record in records)) + "\n"
    return hashlib.sha256(text.encode("ascii")).hexdigest()


def _actual_assignments(
    records: list[ImageRecord],
    source_digest: str,
    allow_test_corpus: bool,
) -> tuple[list[str], list[str], bool]:
    if source_digest == EXPECTED_SOURCE_DIGEST:
        return sorted(DEVELOPMENT_SHA256), sorted(HOLDOUT_SHA256), True
    if allow_test_corpus and len(records) == RECORD_COUNT:
        hashes = sorted(record.sha256 for record in records)
        return hashes[:DEVELOPMENT_COUNT], hashes[DEVELOPMENT_COUNT:], False
    raise ValueError("Unrecognized source corpus digest")


def build_manifest(source_root: Path, *, allow_test_corpus: bool = False) -> dict[str, Any]:
    records, before_snapshot = _inventory(Path(source_root))
    source_digest = _source_digest(records)
    development, holdout, is_real = _actual_assignments(records, source_digest, allow_test_corpus)
    by_hash = {record.sha256: record for record in records}
    selected_hashes = development + holdout
    missing = sorted(set(selected_hashes) - by_hash.keys())
    if missing:
        raise ValueError(f"Curated source hash is missing sha256={missing[0]}")
    if set(development) & set(holdout):
        raise ValueError("Candidate lanes overlap")

    groups = _near_groups(records) if is_real else []
    group_by_hash: dict[str, tuple[str, list[ImageRecord]]] = {}
    for index, group in enumerate(groups, start=1):
        group_id = f"near_{index:03}"
        for record in group:
            group_by_hash[record.sha256] = (group_id, group)

    if is_real:
        if len(records) != EXPECTED_SOURCE_COUNT or sum(record.byte_size for record in records) != EXPECTED_SOURCE_BYTES:
            raise ValueError("Source corpus count or aggregate bytes changed")
        if len(groups) != EXPECTED_NEAR_GROUPS or sum(len(group) for group in groups) != EXPECTED_NEAR_FILES:
            raise ValueError("Near-duplicate audit changed")
        for group_id, (excluded, preferred) in REDUNDANT_EXCLUSIONS.items():
            excluded_group = group_by_hash.get(excluded)
            preferred_group = group_by_hash.get(preferred)
            if not excluded_group or not preferred_group or excluded_group[0] != group_id or preferred_group[0] != group_id:
                raise ValueError(f"Reviewed redundancy decision changed cluster={group_id}")
        if any(not by_hash[sha].likely_details for sha in selected_hashes):
            raise ValueError("A curated record is no longer a likely details screen")
        if any(not by_hash[sha].overlay_present for sha in selected_hashes):
            raise ValueError("A curated record is missing the expected scanner overlay")
        if any(not all((by_hash[sha].likely_cp, by_hash[sha].likely_hp, by_hash[sha].likely_name, by_hash[sha].likely_candy)) for sha in selected_hashes):
            raise ValueError("A curated record is missing a required OCR-region presence flag")

    assignments = [(sha, "development_candidate") for sha in development] + [
        (sha, "prospective_holdout_candidate") for sha in holdout
    ]
    id_by_hash: dict[str, str] = {}
    for index, sha in enumerate(development, start=1):
        id_by_hash[sha] = f"s25_2026_dev_{index:03}"
    for index, sha in enumerate(holdout, start=1):
        id_by_hash[sha] = f"s25_2026_holdout_{index:03}"

    output_records = []
    for sha, lane in assignments:
        record = by_hash[sha]
        group = group_by_hash.get(sha)
        output_records.append({
            "id": id_by_hash[sha],
            "lane": lane,
            "sha256": sha,
            "nearDuplicateClusterId": group[0] if group else None,
            "duplicateDecisionReason": "PRESERVE_STATE_VARIANT" if group else None,
            "width": record.width,
            "height": record.height,
            "format": record.format,
            "colorMode": record.color_mode,
            "bitDepth": record.bit_depth,
            "byteSize": record.byte_size,
            "privacyClassification": "NEEDS_HUMAN_PRIVACY_REVIEW",
            "manualTruthStatus": "unreviewed",
            "provenanceStatus": "user_supplied_local_corpus",
            "publicationStatus": "not_approved",
            "overlayPresent": record.overlay_present,
            "likelyDetailsScreen": record.likely_details,
            "likelyCpPresent": record.likely_cp,
            "likelyHpPresent": record.likely_hp,
            "likelyNamePresent": record.likely_name,
            "likelyCandyFamilyPresent": record.likely_candy,
        })

    clusters = []
    selected_set = set(selected_hashes)
    for index, group in enumerate(groups, start=1):
        selected_members = [record.sha256 for record in group if record.sha256 in selected_set]
        if not selected_members:
            continue
        member_ids = [id_by_hash[sha] for sha in sorted(selected_members)]
        lanes = {next(item["lane"] for item in output_records if item["id"] == member_id) for member_id in member_ids}
        if len(lanes) != 1:
            raise ValueError(f"Near-duplicate cluster crosses candidate lanes cluster=near_{index:03}")
        clusters.append({
            "id": f"near_{index:03}",
            "selectedMemberIds": member_ids,
            "sourceMemberCount": len(group),
            "decisionReason": "PRESERVE_STATE_VARIANT",
        })

    manifest = {
        "schemaVersion": 1,
        "datasetId": "candidate_2026_s25",
        "candidateOnly": True,
        "authoritative": False,
        "containsScreenshotBytes": False,
        "truthLabelsPresent": False,
        "prospectiveHoldoutQuarantined": True,
        "sourceFileCount": len(records),
        "sourceAggregateBytes": sum(record.byte_size for record in records),
        "sourceDigestSha256": source_digest,
        "nearDuplicateGroupCount": len(groups),
        "nearDuplicateGroupedFileCount": sum(len(group) for group in groups),
        "redundantExcludedCount": len(REDUNDANT_EXCLUSIONS) if is_real else 0,
        "structurallyEligibleCount": EXPECTED_ELIGIBLE if is_real else len(records),
        "nearDuplicateMethod": NEAR_DUPLICATE_METHOD,
        "nearDuplicateClusters": clusters,
        "records": output_records,
    }
    validate_manifest(manifest)
    after_snapshot = _source_snapshot(Path(source_root))
    if before_snapshot != after_snapshot:
        raise ValueError("Source corpus changed during curation")
    return manifest


def _walk_strings(value: Any):
    if isinstance(value, dict):
        for child in value.values():
            yield from _walk_strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from _walk_strings(child)
    elif isinstance(value, str):
        yield value


def validate_manifest(manifest: dict[str, Any]) -> None:
    if set(manifest) != ROOT_KEYS:
        raise ValueError("Manifest root schema mismatch")
    if manifest["schemaVersion"] != 1 or manifest["datasetId"] != "candidate_2026_s25":
        raise ValueError("Manifest identity mismatch")
    if not manifest["candidateOnly"] or manifest["authoritative"] or manifest["containsScreenshotBytes"]:
        raise ValueError("Manifest authority boundary mismatch")
    if manifest["truthLabelsPresent"] or not manifest["prospectiveHoldoutQuarantined"]:
        raise ValueError("Truth or holdout quarantine boundary mismatch")
    if manifest["nearDuplicateMethod"] != NEAR_DUPLICATE_METHOD:
        raise ValueError("Near-duplicate method mismatch")
    if not SHA_PATTERN.fullmatch(manifest["sourceDigestSha256"]):
        raise ValueError("Invalid source digest")
    records = manifest["records"]
    if len(records) != RECORD_COUNT:
        raise ValueError("Manifest must contain exactly 120 records")
    if any(set(record) != RECORD_KEYS for record in records):
        raise ValueError("Candidate record schema mismatch")
    hashes = [record["sha256"] for record in records]
    if len(hashes) != len(set(hashes)) or any(not SHA_PATTERN.fullmatch(value) for value in hashes):
        raise ValueError("Candidate SHA-256 values are invalid or duplicated")
    lane_counts = {lane: sum(record["lane"] == lane for record in records) for lane in LANES}
    if lane_counts != {"development_candidate": DEVELOPMENT_COUNT, "prospective_holdout_candidate": HOLDOUT_COUNT}:
        raise ValueError("Candidate lane counts mismatch")
    expected_ids = [f"s25_2026_dev_{index:03}" for index in range(1, 101)] + [
        f"s25_2026_holdout_{index:03}" for index in range(1, 21)
    ]
    if [record["id"] for record in records] != expected_ids:
        raise ValueError("Candidate IDs or ordering mismatch")
    for record in records:
        if record["lane"] not in LANES:
            raise ValueError("Invalid candidate lane")
        if any(type(record[key]) is not bool for key in ("overlayPresent", "likelyDetailsScreen", "likelyCpPresent", "likelyHpPresent", "likelyNamePresent", "likelyCandyFamilyPresent")):
            raise ValueError("Candidate heuristic flags must be booleans")
        if any(type(record[key]) is not int or record[key] <= 0 for key in ("width", "height", "bitDepth", "byteSize")):
            raise ValueError("Candidate numeric metadata is invalid")
        if record["privacyClassification"] != "NEEDS_HUMAN_PRIVACY_REVIEW" or record["manualTruthStatus"] != "unreviewed":
            raise ValueError("Candidate review status mismatch")
        if record["provenanceStatus"] != "user_supplied_local_corpus" or record["publicationStatus"] != "not_approved":
            raise ValueError("Candidate provenance or publication status mismatch")
        cluster_id = record["nearDuplicateClusterId"]
        decision = record["duplicateDecisionReason"]
        if cluster_id is None and decision is not None:
            raise ValueError("Unclustered candidate has a duplicate decision")
        if cluster_id is not None and (not re.fullmatch(r"near_\d{3}", cluster_id) or decision not in DUPLICATE_DECISIONS):
            raise ValueError("Clustered candidate metadata is invalid")

    records_by_id = {record["id"]: record for record in records}
    clusters = manifest["nearDuplicateClusters"]
    if any(set(cluster) != CLUSTER_KEYS for cluster in clusters):
        raise ValueError("Near-duplicate cluster schema mismatch")
    clusters_by_id = {cluster["id"]: cluster for cluster in clusters}
    if len(clusters_by_id) != len(clusters):
        raise ValueError("Duplicate near-duplicate cluster ID")
    for cluster_id, cluster in clusters_by_id.items():
        members = cluster["selectedMemberIds"]
        if not re.fullmatch(r"near_\d{3}", cluster_id) or not members or len(members) != len(set(members)):
            raise ValueError("Invalid near-duplicate cluster")
        if type(cluster["sourceMemberCount"]) is not int or cluster["sourceMemberCount"] < len(members):
            raise ValueError("Invalid source cluster count")
        if cluster["decisionReason"] not in DUPLICATE_DECISIONS or any(member not in records_by_id for member in members):
            raise ValueError("Invalid near-duplicate cluster decision or member")
        if len({records_by_id[member]["lane"] for member in members}) != 1:
            raise ValueError("Near-duplicate cluster crosses candidate lanes")
        if any(records_by_id[member]["nearDuplicateClusterId"] != cluster_id for member in members):
            raise ValueError("Near-duplicate cluster reference mismatch")
    for record in records:
        cluster_id = record["nearDuplicateClusterId"]
        if cluster_id is not None and (cluster_id not in clusters_by_id or record["id"] not in clusters_by_id[cluster_id]["selectedMemberIds"]):
            raise ValueError("Dangling near-duplicate cluster reference")
    for text in _walk_strings(manifest):
        if any(pattern.search(text) for pattern in PRIVATE_VALUE_PATTERNS):
            raise ValueError("Manifest contains prohibited private or source text")


def canonical_json_bytes(manifest: dict[str, Any]) -> bytes:
    validate_manifest(manifest)
    return json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8") + b"\n"


def write_manifest(manifest: dict[str, Any], output: Path) -> None:
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(canonical_json_bytes(manifest))


def ensure_output_outside_source(source_root: Path, output: Path) -> None:
    source = Path(source_root).resolve()
    destination = Path(output).resolve()
    if destination == source or source in destination.parents:
        raise ValueError("Output must be outside the source corpus")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    ensure_output_outside_source(args.source_root, args.output)
    manifest = build_manifest(args.source_root)
    expected = canonical_json_bytes(manifest)
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != expected:
            raise SystemExit("Candidate manifest is not byte-identical to deterministic regeneration")
    else:
        write_manifest(manifest, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
