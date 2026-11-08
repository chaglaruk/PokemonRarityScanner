#!/usr/bin/env python3
"""Download a pinned, private Pokémon GO visual-reference source snapshot.

This fetches *available* PokeMiners assets, not a complete released-variant roster.
All asset bytes remain in gitignored external/pogo_assets, outside the APK.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PINS = ROOT / "scripts" / "visual_identity_source_pins.json"
DEFAULT_OUTPUT = ROOT / "external" / "pogo_assets"
MANIFEST_NAME = ".pinned_visual_assets_manifest.json"
SUMMARY_NAME = ".pinned_visual_assets_summary.json"
REPO = "PokeMiners/pogo_assets"
GITHUB_API = "https://api.github.com/repos/PokeMiners/pogo_assets/git/trees/"
RAW_BASE = "https://raw.githubusercontent.com/PokeMiners/pogo_assets/"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
MAX_FILE_BYTES = 100 * 1024 * 1024
MAX_TREE_BYTES = 16 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024


@dataclass(frozen=True)
class Asset:
    path: str
    sha: str
    size: int
    kind: str


def validate_sha(value: Any) -> str:
    if not isinstance(value, str) or SHA_RE.fullmatch(value) is None:
        raise ValueError(f"Invalid pinned Git SHA: {value!r}")
    return value


def safe_relative_path(value: Any) -> str:
    if not isinstance(value, str) or not value or "\\" in value or "\x00" in value:
        raise ValueError(f"Invalid asset path: {value!r}")
    parts = value.split("/")
    if any(part in ("", ".", "..") or part.startswith(".") or part.endswith((".", " ")) for part in parts):
        raise ValueError(f"Unsafe asset path: {value!r}")
    if any(any(char in part for char in '<>:"|?*') for part in parts):
        raise ValueError(f"Nonportable asset path: {value!r}")
    if any(part.split(".")[0].upper() in {"CON", "PRN", "AUX", "NUL", "COM1", "LPT1"} for part in parts):
        raise ValueError(f"Reserved device asset path: {value!r}")
    if PurePosixPath(value).is_absolute():
        raise ValueError(f"Absolute asset path: {value!r}")
    return value


def read_pins(path: Path) -> tuple[str, dict[str, tuple[str, str]]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    source = payload["pogoMinersAssets"]
    if source.get("repository") != f"https://github.com/{REPO}":
        raise ValueError("Unexpected asset repository")
    commit = validate_sha(source["observedCommit"])
    roots = {
        "icons": ("Images/Pokemon - 256x256", validate_sha(source["pokemon2dTreeSha"])),
        "3d": ("3D Assets/Pokemon", validate_sha(source["pokemon3dTreeSha"])),
    }
    return commit, roots


def request_bytes(url: str, *, limit: int, retries: int = 3) -> bytes:
    if not (url.startswith(GITHUB_API) or url.startswith(RAW_BASE)):
        raise ValueError("Untrusted asset source URL")
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urlopen(Request(url, headers={"User-Agent": "PokemonRarityScanner-private-reference-sync/1"}), timeout=45) as response:
                data = response.read(limit + 1)
                if len(data) > limit:
                    raise ValueError("Remote payload exceeded safety cap")
                return data
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            last_error = exc
            if isinstance(exc, HTTPError) and exc.code not in (408, 429, 500, 502, 503, 504):
                break
            if attempt < retries - 1:
                time.sleep(min(4, 2 ** attempt))
    raise RuntimeError(f"Failed HTTPS fetch: {url}") from last_error


def tree_assets(payload: dict[str, Any], expected_sha: str, root: str) -> list[Asset]:
    if payload.get("sha") != expected_sha or payload.get("truncated") is not False:
        raise ValueError("Source tree SHA mismatch or truncated response")
    rows = payload.get("tree")
    if not isinstance(rows, list):
        raise ValueError("Invalid source tree")
    result: list[Asset] = []
    seen: set[str] = set()
    for row in rows:
        if row.get("type") != "blob":
            continue
        rel = safe_relative_path(row.get("path"))
        ext = PurePosixPath(rel).suffix.lower()
        if root == "Images/Pokemon - 256x256":
            if ext != ".png":
                continue
            kind = "icons"
        else:
            if ext == ".png":
                kind = "textures"
            elif ext in (".fbx", ".obj"):
                kind = "models"
            else:
                continue
        if row.get("mode") not in ("100644", "100755"):
            raise ValueError(f"Unexpected asset file mode: {rel}")
        size = row.get("size")
        if isinstance(size, bool) or not isinstance(size, int) or size < 0 or size > MAX_FILE_BYTES:
            raise ValueError(f"Invalid asset size: {rel}")
        path = safe_relative_path(f"{root}/{rel}")
        if path in seen:
            raise ValueError(f"Duplicate asset: {path}")
        seen.add(path)
        result.append(Asset(path=path, sha=validate_sha(row["sha"]), size=size, kind=kind))
    return sorted(result, key=lambda x: x.path)


def fetch_inventory(pins_path: Path, scope: str) -> tuple[str, list[Asset]]:
    commit, roots = read_pins(pins_path)
    selected = ("icons",) if scope == "icons" else ("3d",) if scope in ("models", "textures") else ("icons", "3d")
    all_assets: list[Asset] = []
    for label in selected:
        root, sha = roots[label]
        payload = json.loads(request_bytes(GITHUB_API + sha + "?recursive=1", limit=MAX_TREE_BYTES))
        all_assets.extend(tree_assets(payload, sha, root))
    return commit, sorted(
        (asset for asset in all_assets if scope == "all" or asset.kind == scope),
        key=lambda x: x.path,
    )


def file_hashes(path: Path, size: int) -> tuple[str, str] | None:
    if not path.is_file() or path.is_symlink() or path.stat().st_size != size:
        return None
    git = hashlib.sha1(f"blob {size}\0".encode("ascii"))
    sha256 = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(CHUNK_SIZE):
            git.update(chunk)
            sha256.update(chunk)
    return git.hexdigest(), sha256.hexdigest()


def verified_local(path: Path, asset: Asset) -> bool:
    hashes = file_hashes(path, asset.size)
    return hashes is not None and hashes[0] == asset.sha


def raw_url(commit: str, asset: Asset) -> str:
    validate_sha(commit)
    return RAW_BASE + commit + "/" + "/".join(quote(part, safe="") for part in asset.path.split("/"))


def download_one(asset: Asset, commit: str, output: Path, replace_invalid: bool = False) -> str:
    path = output.joinpath(*asset.path.split("/"))
    if path.exists() or path.is_symlink():
        if verified_local(path, asset):
            return "existing_verified"
        if not replace_invalid:
            raise ValueError(f"Local file differs from pinned source (preserved): {asset.path}")
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"Refusing to replace non-file: {asset.path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".partial")
    if temp.is_symlink():
        raise ValueError(f"Unexpected symlink at temporary path: {asset.path}")
    url = raw_url(commit, asset)
    try:
        for attempt in range(3):
            downloaded = 0
            git = hashlib.sha1(f"blob {asset.size}\0".encode("ascii"))
            try:
                with urlopen(Request(url, headers={"User-Agent": "PokemonRarityScanner-private-reference-sync/1"}), timeout=45) as response:
                    with temp.open("wb") as handle:
                        while chunk := response.read(CHUNK_SIZE):
                            downloaded += len(chunk)
                            if downloaded > asset.size:
                                raise ValueError(f"Source exceeded pinned size: {asset.path}")
                            git.update(chunk)
                            handle.write(chunk)
                if downloaded != asset.size or git.hexdigest() != asset.sha:
                    raise ValueError(f"Pinned Git blob mismatch: {asset.path}")
                if asset.kind in ("icons", "textures"):
                    with temp.open("rb") as handle:
                        if handle.read(8) != b"\x89PNG\r\n\x1a\n":
                            raise ValueError(f"Unexpected non-PNG asset: {asset.path}")
                temp.replace(path)
                return "downloaded"
            except (HTTPError, URLError, TimeoutError, OSError) as exc:
                if isinstance(exc, HTTPError) and exc.code not in (408, 429, 500, 502, 503, 504):
                    raise
                if attempt == 2:
                    raise
                time.sleep(min(4, 2 ** attempt))
    finally:
        if temp.exists():
            temp.unlink()
    raise RuntimeError(f"Unexpected transfer state: {asset.path}")


def asset_summary(assets: list[Asset]) -> dict[str, Any]:
    counts: dict[str, int] = {}
    bytes_by_kind: dict[str, int] = {}
    for asset in assets:
        counts[asset.kind] = counts.get(asset.kind, 0) + 1
        bytes_by_kind[asset.kind] = bytes_by_kind.get(asset.kind, 0) + asset.size
    paths = {asset.path for asset in assets if asset.kind == "icons"}
    paired = [name for name in paths if name.lower().endswith("_shiny.png") and name[:-10] + ".png" in paths]
    shellos = sorted(name for name in paths if "pokemon_icon_422_" in name.lower())
    smoliv = sorted(name for name in paths if "pokemon_icon_928_" in name.lower())
    return {
        "count": len(assets),
        "bytes": sum(asset.size for asset in assets),
        "countsByKind": counts,
        "bytesByKind": bytes_by_kind,
        "candidateNormalShinyIconPairs": len(paired),
        "shellosIconPaths": shellos,
        "smolivIconPaths": smoliv,
        "caveat": "Source availability is not verified release, identity mapping, or recognition accuracy.",
    }


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".partial")
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temp.replace(path)


def verify_offline(output: Path) -> int:
    manifest = output / MANIFEST_NAME
    if not manifest.is_file():
        print(f"No local inventory found: {manifest}", file=sys.stderr)
        return 2
    data = json.loads(manifest.read_text(encoding="utf-8"))
    verified = 0
    missing = []
    for row in data["assets"]:
        asset = Asset(**row)
        path = output.joinpath(*safe_relative_path(asset.path).split("/"))
        if verified_local(path, asset):
            verified += 1
        else:
            missing.append(asset.path)
    print(json.dumps({
        "expected": len(data["assets"]),
        "verified": verified,
        "missingOrModified": len(missing),
        "firstMissing": missing[:20],
    }, indent=2))
    return 0 if not missing else 1


def run_download(commit: str, assets: list[Asset], output: Path, workers: int, replace_invalid: bool) -> int:
    summary = asset_summary(assets)
    probe = output
    while not probe.exists():
        probe = probe.parent
    if shutil.disk_usage(probe).free < summary["bytes"] + 128 * 1024 * 1024:
        print("Not enough free space for the full source snapshot", file=sys.stderr)
        return 2
    results = {"downloaded": 0, "existing_verified": 0, "failed": 0}
    failures = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(download_one, asset, commit, output, replace_invalid): asset for asset in assets
        }
        for number, future in enumerate(as_completed(futures), 1):
            asset = futures[future]
            try:
                results[future.result()] += 1
            except Exception as exc:
                results["failed"] += 1
                failures.append({"path": asset.path, "error": str(exc)[:300]})
            if number % 250 == 0 or number == len(assets):
                print(f"Checked {number}/{len(assets)}: {results}", flush=True)
    summary.update({"transfer": results, "failedAssets": failures[:250]})
    write_json(output / SUMMARY_NAME, summary)
    print(json.dumps({"transfer": results, "summary": str(output / SUMMARY_NAME)}, indent=2))
    return 1 if results["failed"] else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=("all", "icons", "textures", "models"), default="all")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--pins", type=Path, default=DEFAULT_PINS)
    parser.add_argument("--download", action="store_true", help="Download all available pinned assets")
    parser.add_argument("--verify", action="store_true", help="Offline check against local inventory")
    parser.add_argument("--replace-invalid", action="store_true", help="Explicitly replace mismatched inventory-listed files")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--limit", type=int, help="Smoke test only, never full coverage")
    args = parser.parse_args(argv)
    if not 1 <= args.workers <= 8:
        parser.error("--workers must be 1 to 8")
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    if args.verify:
        return verify_offline(args.output)
    commit, assets = fetch_inventory(args.pins, args.scope)
    if args.limit is not None:
        assets = assets[:args.limit]
    summary = asset_summary(assets)
    write_json(args.output / MANIFEST_NAME, {
        "version": 1, "sourceRepo": REPO, "sourceCommit": commit,
        "scope": args.scope, "limited": args.limit is not None,
        "assets": [asdict(asset) for asset in assets],
    })
    print(json.dumps({"sourceCommit": commit, "scope": args.scope, **summary}, indent=2))
    if not args.download:
        print("Inventory only. Pass --download to fetch private source bytes.")
        return 0
    return run_download(commit, assets, args.output, args.workers, args.replace_invalid)


if __name__ == "__main__":
    raise SystemExit(main())
