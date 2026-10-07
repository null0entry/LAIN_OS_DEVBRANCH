#!/usr/bin/env python3
"""Run the canonical, portable LAIN_OS developer verification suite."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tomllib
from collections.abc import Callable
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
Runner = Callable[..., subprocess.CompletedProcess[str]]
COMMANDS = (
    (sys.executable, "-m", "compileall", "-q", "lain"),
    (sys.executable, "-m", "unittest", "discover", "-s", "tests", "-q"),
    ("git", "diff", "--cached", "--check"),
    ("git", "diff", "--check"),
)
SECURITY_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (r"shell\s*=\s*True", r"os\.system\s*\(", r"\beval\s*\(", r"\bexec\s*\(")
)
STALE_NAMES = ("L" + ".A.I.N", "loopmother/" + "L.A.I.N")

RELEASE_STATE_PATH = Path("release/state.json")
TASK_HEADING = re.compile(r"^##\s+(TASK-\d+):", re.MULTILINE)
EVIDENCE_CLASSES = frozenset(
    {"automated", "emulator", "physical", "owner_reported", "live_service"}
)
EVIDENCE_STATUSES = frozenset({"PASSED", "UNVERIFIED", "UNSUPPORTED"})
GRADLE_FIELDS: dict[str, tuple[re.Pattern[str], Callable[[str], object]]] = {
    "application_id": (re.compile(r'\bapplicationId\s*=\s*"([^"]+)"'), str),
    "compile_sdk": (re.compile(r"\bcompileSdk\s*=\s*(\d+)"), int),
    "min_sdk": (re.compile(r"\bminSdk\s*=\s*(\d+)"), int),
    "target_sdk": (re.compile(r"\btargetSdk\s*=\s*(\d+)"), int),
    "version_code": (re.compile(r"\bversionCode\s*=\s*(\d+)"), int),
    "version_name": (re.compile(r'\bversionName\s*=\s*"([^"]+)"'), str),
}


def _tracked_files(root: Path, runner: Runner) -> list[Path]:
    result = runner(["git", "ls-files", "-z"], cwd=root, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError("git ls-files failed")
    return [root / name for name in result.stdout.split("\0") if name]


def _load_task_states(root: Path) -> tuple[dict[str, tuple[str, str]], list[str]]:
    violations: list[str] = []
    config_path = root / ".tasks" / "config.json"
    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        return {}, [f".tasks/config.json: cannot read TaskPlanner state mapping: {exc}"]

    states = config.get("states")
    if not isinstance(states, list):
        return {}, [".tasks/config.json: states must be a list"]

    found: dict[str, tuple[str, str]] = {}
    for state in states:
        if not isinstance(state, dict):
            violations.append(".tasks/config.json: each state entry must be an object")
            continue
        state_name = state.get("name")
        file_name = state.get("fileName")
        if not isinstance(state_name, str) or not state_name:
            violations.append(".tasks/config.json: state name must be nonempty")
            continue
        if not isinstance(file_name, str) or not file_name:
            violations.append(f".tasks/config.json: {state_name}: fileName must be nonempty")
            continue

        relative = f".tasks/{file_name}"
        path = root / ".tasks" / file_name
        try:
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            violations.append(f"{relative}: cannot read TaskPlanner state file: {exc}")
            continue

        for task_id in TASK_HEADING.findall(content):
            previous = found.get(task_id)
            if previous is not None:
                violations.append(
                    f"{task_id}: appears in both {previous[1]} and {relative}"
                )
                continue
            found[task_id] = (state_name, relative)
    return found, violations


def _load_gradle_metadata(root: Path) -> tuple[dict[str, object], list[str]]:
    path = root / "android" / "app" / "build.gradle.kts"
    try:
        content = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        return {}, [f"android/app/build.gradle.kts: cannot read Android metadata: {exc}"]

    metadata: dict[str, object] = {}
    violations: list[str] = []
    for key, (pattern, converter) in GRADLE_FIELDS.items():
        match = pattern.search(content)
        if match is None:
            violations.append(
                f"android/app/build.gradle.kts: authoritative field for android.{key} is missing"
            )
            continue
        metadata[key] = converter(match.group(1))
    return metadata, violations


def check_release_state(root: Path = ROOT) -> list[str]:
    """Validate structured release claims against repository-native authorities.

    This deliberately ignores arbitrary prose. It checks only release/state.json
    fields with a deterministic machine-readable counterpart.
    """

    violations: list[str] = []
    state_path = root / RELEASE_STATE_PATH
    try:
        state = json.loads(state_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        return [f"{RELEASE_STATE_PATH.as_posix()}: cannot read structured release state: {exc}"]

    if not isinstance(state, dict):
        return [f"{RELEASE_STATE_PATH.as_posix()}: root must be an object"]
    if state.get("version") != 1:
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: version must be 1")

    package = state.get("package")
    if not isinstance(package, dict):
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: package must be an object")
        package = {}

    python_claims = package.get("python")
    if not isinstance(python_claims, dict):
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: package.python must be an object")
        python_claims = {}
    try:
        pyproject = tomllib.loads((root / "pyproject.toml").read_text(encoding="utf-8"))
        project = pyproject["project"]
    except (OSError, UnicodeDecodeError, tomllib.TOMLDecodeError, KeyError, TypeError) as exc:
        project = {}
        violations.append(f"pyproject.toml: cannot read [project] metadata: {exc}")

    for key in ("name", "version"):
        claim = python_claims.get(key)
        authoritative = project.get(key) if isinstance(project, dict) else None
        if claim is None:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: python.{key} claim is missing"
            )
        elif authoritative is None:
            violations.append(f"pyproject.toml: authoritative python.{key} is missing")
        elif claim != authoritative:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: python.{key} claimed {claim} "
                f"but pyproject.toml [project].{key} is {authoritative}"
            )

    android_claims = package.get("android")
    if not isinstance(android_claims, dict):
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: package.android must be an object")
        android_claims = {}
    android_metadata, android_violations = _load_gradle_metadata(root)
    violations.extend(android_violations)
    for key in GRADLE_FIELDS:
        claim = android_claims.get(key)
        authoritative = android_metadata.get(key)
        if claim is None:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: android.{key} claim is missing"
            )
        elif authoritative is not None and claim != authoritative:
            source_field = {
                "application_id": "applicationId",
                "compile_sdk": "compileSdk",
                "min_sdk": "minSdk",
                "target_sdk": "targetSdk",
                "version_code": "versionCode",
                "version_name": "versionName",
            }[key]
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: android.{key} claimed {claim} "
                f"but android/app/build.gradle.kts {source_field} is {authoritative}"
            )

    task_claims = state.get("tasks")
    if not isinstance(task_claims, dict):
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: tasks must be an object")
        task_claims = {}
    task_states, task_violations = _load_task_states(root)
    violations.extend(task_violations)
    for task_id, claimed_state in task_claims.items():
        if not isinstance(task_id, str) or not isinstance(claimed_state, str):
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: task claims must map strings to strings"
            )
            continue
        actual = task_states.get(task_id)
        if actual is None:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: {task_id} has no canonical TaskPlanner state"
            )
            continue
        actual_state, source = actual
        if claimed_state != actual_state:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: {task_id} claimed {claimed_state} "
                f"but {source} says {actual_state}"
            )

    evidence = state.get("evidence")
    if not isinstance(evidence, dict):
        violations.append(f"{RELEASE_STATE_PATH.as_posix()}: evidence must be an object")
        evidence = {}
    for evidence_id, record in evidence.items():
        if not isinstance(record, dict):
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: evidence.{evidence_id} must be an object"
            )
            continue
        required = record.get("required_class")
        recorded = record.get("recorded_classes")
        status = record.get("status")
        if required not in EVIDENCE_CLASSES:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: evidence.{evidence_id}.required_class "
                f"must be one of {','.join(sorted(EVIDENCE_CLASSES))}"
            )
            continue
        if (
            not isinstance(recorded, list)
            or any(item not in EVIDENCE_CLASSES for item in recorded)
        ):
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: evidence.{evidence_id}.recorded_classes "
                "contains an unknown evidence class"
            )
            continue
        if status not in EVIDENCE_STATUSES:
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: evidence.{evidence_id}.status "
                f"must be one of {','.join(sorted(EVIDENCE_STATUSES))}"
            )
            continue
        if status == "PASSED" and required not in recorded:
            observed = ",".join(recorded) if recorded else "none"
            violations.append(
                f"{RELEASE_STATE_PATH.as_posix()}: evidence.{evidence_id} claims PASSED "
                f"requiring {required} but recorded_classes={observed}; "
                f"{observed} evidence cannot satisfy {required}"
            )

    return violations


def scan_repository(root: Path = ROOT, runner: Runner = subprocess.run) -> list[str]:
    """Return actionable security and stale-name violations in tracked files."""
    violations: list[str] = []
    for path in _tracked_files(root, runner):
        try:
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        relative = path.relative_to(root).as_posix()
        # Tests/docs intentionally name forbidden APIs; runtime source must not use them.
        if relative.startswith("lain/") and path.suffix == ".py":
            for number, line in enumerate(content.splitlines(), 1):
                if any(pattern.search(line) for pattern in SECURITY_PATTERNS):
                    violations.append(f"{relative}:{number}: forbidden execution pattern")
        if relative != "scripts/verify.py":
            for number, line in enumerate(content.splitlines(), 1):
                if any(name in line for name in STALE_NAMES):
                    violations.append(f"{relative}:{number}: stale project naming")
    return violations


def main(
    *, root: Path = ROOT, runner: Runner = subprocess.run, diff_range: str | None = None
) -> int:
    commands = list(COMMANDS)
    if diff_range:
        commands.append(("git", "diff", "--check", diff_range))
    for command in commands:
        rendered = " ".join(("python", *command[2:])) if command[0] == sys.executable else " ".join(command)
        print(f"==> {rendered}", flush=True)
        result = runner(list(command), cwd=root, check=False)
        if result.returncode:
            print(f"FAILED ({result.returncode}): {rendered}", file=sys.stderr)
            return result.returncode or 1

    print("==> release-state truth", flush=True)
    release_violations = check_release_state(root)
    if release_violations:
        print("\n".join(release_violations), file=sys.stderr)
        print("FAILED: release-state truth", file=sys.stderr)
        return 1

    print("==> security and stale naming scans", flush=True)
    try:
        violations = scan_repository(root, runner)
    except RuntimeError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1
    if violations:
        print("\n".join(violations), file=sys.stderr)
        print("FAILED: repository scan", file=sys.stderr)
        return 1
    print("Verification passed.")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--diff-range",
        help="also check whitespace in the specified committed Git range (for example BASE..HEAD)",
    )
    raise SystemExit(main(diff_range=parser.parse_args().diff_range))
