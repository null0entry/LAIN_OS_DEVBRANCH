#!/usr/bin/env python3
"""Bind a debug APK checksum to its checkout and Gradle package metadata."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


def describe_debug_apk(apk: Path, metadata: Path, package: dict, commit: str,
                       workflow_run_id: str | None = None) -> dict:
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("an exact source commit is required")
    info = json.loads(metadata.read_text(encoding="utf-8"))
    if info.get("variantName") != "debug" or info.get("applicationId") != package["application_id"]:
        raise ValueError("artifact is not the expected Android debug package")
    elements = info.get("elements")
    if not isinstance(elements, list) or len(elements) != 1:
        raise ValueError("expected one universal debug APK")
    element = elements[0]
    if (element.get("outputFile") != apk.name or element.get("versionCode") != package["version_code"]
            or element.get("versionName") != package["version_name"]):
        raise ValueError("APK metadata does not match the current release state")
    with ZipFile(apk) as archive:
        names = set(archive.namelist())
    required = {"AndroidManifest.xml", "classes.dex"}
    abis = ["arm64-v8a", "x86_64"]
    for abi in abis:
        required.update({f"lib/{abi}/liblain_gguf_probe.so", f"lib/{abi}/libpython3.11.so"})
    if not required <= names:
        raise ValueError("APK is missing its manifest, code or supported native/Python runtime")
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return {
        "schema_version": 1, "apk": apk.name, "sha256": digest.hexdigest(),
        "bytes": apk.stat().st_size, "source_commit": commit,
        "workflow_run_id": workflow_run_id, "variant": "debug", "abis": abis,
        **package, "sdk_metadata_source": "release/state.json",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path,
                        default=ROOT / "android/app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    # Never label an uncommitted build as the binary for a clean source commit.
    dirty = subprocess.check_output(
        ["git", "status", "--porcelain", "--untracked-files=normal"], cwd=ROOT, text=True,
    )
    if dirty.strip():
        raise ValueError("APK handoff requires a clean committed checkout")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    package = json.loads((ROOT / "release/state.json").read_text())["package"]["android"]
    record = describe_debug_apk(args.apk, args.apk.with_name("output-metadata.json"), package,
                                commit, os.environ.get("GITHUB_RUN_ID"))
    args.apk.with_name("build-info.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    args.apk.with_name("SHA256SUMS").write_text(f"{record['sha256']}  {args.apk.name}\n", encoding="utf-8")
    print(json.dumps(record, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
