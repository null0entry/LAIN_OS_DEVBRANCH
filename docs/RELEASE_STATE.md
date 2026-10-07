# Release-state truth verifier

`release/state.json` is the repository's narrow machine-checkable surface for stable release-state claims. It is intentionally not a second release system and it does not infer truth from prose.

The canonical verifier compares only claims with repository-native machine authorities:

- Python package name/version -> `pyproject.toml [project]`
- Android application/package/API/version fields -> `android/app/build.gradle.kts`
- Referenced task state -> TaskPlanner's configured `.tasks/*.md` state files
- Evidence-strength promotion -> the explicit evidence class recorded in `release/state.json`

Evidence classes remain distinct: `automated`, `emulator`, `physical`, `owner_reported`, and `live_service`. There is no implicit hierarchy. A `PASSED` claim is valid only when the exact required evidence class is explicitly recorded. In particular, automated or emulator evidence cannot satisfy a physical or live-service requirement.

`UNVERIFIED` and `UNSUPPORTED` are valid truthful outcomes. The verifier does not manufacture physical-device, owner-reported, or live-service evidence, and it does not decide whether a human observation is credible. Those facts must be established outside automation and then deliberately recorded.

Unstructured README/spec/roadmap prose is not parsed as authority. Historical audit/evidence records are not rewritten when current state changes.

When verification fails, update whichever side is actually stale:
1. If implementation/package/task metadata changed, update `release/state.json` to match the authoritative source.
2. If the structured claim is correct but its machine authority is wrong, fix the authoritative source.
3. Never weaken an evidence requirement merely to make CI green.

TASK-047 may later extend candidate-specific provenance, but it must preserve these authority boundaries.
