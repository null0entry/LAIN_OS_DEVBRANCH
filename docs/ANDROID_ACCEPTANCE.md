# Android Capability Expansion v1 — manual hardware gate

Run on real F-Droid Termux plus matching Termux:API. This document records the
completed v1 acceptance and remains the procedure for future revalidation. Execute
one step, inspect its result, then continue. Use a fixed source revision or packaged
artifact and record its exact identity before testing. No credential contents are
printed or changed here.

## Current standalone APK Phase-1 revalidation — IN PROGRESS (2026-10-03)

### Current patched candidate

- PR #16 candidate head: `1a894fb4ac7936549362e857bdb92f21e28251b9`.
- Verify #297: **GREEN**.
- Android #286: **GREEN** on API 24 and API 35, including instrumentation and artifact upload.
- Exact API-35 `app-debug.apk` SHA-256: `d6474f768ee15ba2a1418d92a48d13f9647ed86cb85c102ae3b96e50d3f92102`; size 38,093,311 bytes.
- Included fixes since the prior physical build: zero-argument schema validity; plain connection diagnostic; explicit provider error classes; planner-neutral Run copy; blocking Stop regression; Android hardlink-`EACCES` filesystem fallback; JSON-Schema prompt compaction; 1024 completion ceiling; Groq GPT-OSS low reasoning; JSON-object compatibility preserved.
- Prior physical observations remain evidence only for the older binaries. **No physical acceptance claim exists yet for this exact candidate.**

The older `c700eed` record below remains the evidence that discovered the defects; it is not the current acceptance candidate.


This is a separate, newer acceptance track from the historical Termux capability record below. Evidence must not be conflated.

Candidate source/build:
- PR #16 head: `c700eed0b9de6964729a6b038eaa565daf44864d`.
- Verify #270: GREEN.
- Android #259: GREEN, including API 24/35 automation.
- Physical-install APK SHA-256: `9506f9ba813774a5ad8cd13a5d83c323bbd66c89136b0f357f4806e63c09fe28`.
- Physical reference device: current Galaxy; exact Android/API identity must be captured with the final rerun rather than inferred.

Observed physical results on this exact build:
- APK install/launch: successful.
- Embedded/local runtime: ready.
- Offline Demo **Show battery**: terminal **COMPLETE**.
- Real Cloud planner profile: Groq selected with model `openai/gpt-oss-120b`.
- Groq receives requests from the APK, establishing device network reachability to the configured provider.
- **Test connection** returns `unavailable` on this build.
- Real planner task attempts end **FAILED**.
- Provider logs show HTTP 400 with the exact schema defect: `invalid JSON schema for response_format: 'lain_agent_decision': /properties/actions/items/anyOf/0/properties/arguments: 'required' present but 'properties' is missing`.
- Separate diagnostic requests show `json_validate_failed` after the current diagnostic requests structured JSON with a one-token completion ceiling.

Required fixes before another physical acceptance run:
- correct zero-argument capability schema generation;
- correct the connection diagnostic;
- expose safe provider error classes instead of generic `unavailable`;
- replace misleading `Run locally` copy;
- add an actually-blocking planner cancellation test and change Stop only if that test fails.

### Standalone APK Phase-1 completion matrix

A single battery success is only a smoke test. Using a fresh session for each case, the exact post-fix GREEN APK must exercise the current installed Workbench presets through the intended real selected planner and reach terminal **COMPLETE** for every applicable flow:

1. **Create demo file** — COMPLETE with trusted execution and verification evidence.
2. **Show battery** — COMPLETE with structured battery verification.
3. **Show demo toast** — COMPLETE; retain truthful LIMITED semantics for physical UI observation where independent verification is unavailable.
4. **Vibrate briefly** — COMPLETE; retain truthful LIMITED semantics for physical haptic observation where independent verification is unavailable.
5. **Copy demo text** — COMPLETE with the capability's private verification semantics and without exposing clipboard contents.
6. **Share demo text** — pause for exact approval, then COMPLETE after approval; opening the chooser is the allowed effect and no destination is selected automatically.

Also required:
- **Test connection** reports the correct bounded status without a probe-induced false negative.
- Deliberately hold a planner call in flight, press **Stop task**, and verify cancellation reaches a truthful terminal state; fast provider rejection is not a valid Stop test.
- Recheck rotation/rebind/background behavior, stale approval rejection, selected-planner session pinning, no Local-to-Cloud fallback, and planner/provider secret isolation.
- Record exact device model, Android version/API, APK/source SHA, APK checksum, provider/model identity, and observed result for every physical run.

TASK-008 remains open until this matrix is reconciled. Phase-2 voice work must not use the current battery-only smoke result as Phase-1 completion evidence.

## Acceptance record

Manual acceptance completed on Android 16 for source revision
`0d7efe584b78a050c2817b2ab3e3f555e2450dff`.

Observed results:

- `android.battery_status`: success, verification PASSED.
- `android.toast`: success, verification LIMITED; toast physically observed.
- `android.vibrate`: success, verification LIMITED; vibration physically observed.
- `android.clipboard_set`: success, verification PASSED.
- `android.share_text`: unconfirmed execution blocked with
  `CONFIRMATION_REQUIRED`; after explicit confirmation, success + LIMITED and the
  Android share chooser appeared. No destination was selected and nothing was sent.
- Audit records redacted clipboard/share `content` as `[REDACTED]` and preserved
  the expected risk and policy decisions.

Portable verification for this accepted implementation lineage recorded 232 tests.
This is manual hardware evidence plus portable automated verification, not automated
Android hardware verification.

The physical timeout found during acceptance was traced to the filtered subprocess
environment omitting Android runtime variables required by Termux:API on Android
16. The accepted implementation preserves those required runtime variables while
continuing to exclude provider/executor credentials.

Any later runtime-affecting change to the Android command transport, adapters,
policy, verification semantics, command arguments, environment propagation,
clipboard handling, or share behavior requires a fresh hardware acceptance run.

## 1. Confirm exact source revision

From the source checkout used to build/test the candidate, record the exact revision:

```sh
git rev-parse HEAD
```

Do not change source during the acceptance run. If testing a packaged artifact
instead of a checkout, record the package version and cryptographic checksum.

## 2. Isolated checkout

For source-based testing, create a detached worktree at the recorded revision:

```sh
LAIN_HW_HEAD=$(git rev-parse HEAD)
git worktree add --detach "$HOME/lain-android-v1-${LAIN_HW_HEAD}" "$LAIN_HW_HEAD"
```

```sh
cd "$HOME/lain-android-v1-${LAIN_HW_HEAD}"
git rev-parse HEAD
```

Confirm the value matches the recorded source revision. Commands below use
`python -m lain` from this isolated checkout so an older installed console script
cannot affect the result.

## 3. Non-destructive helper and isolated request fixtures

```sh
python scripts/verify_android.py
```

Expected: portable suite and scans pass. Missing commands are informational in
this helper, but a device acceptance action needs its matching command.

Create an isolated temporary workspace containing harmless test requests only:

```sh
LAIN_HW_WORKSPACE=$(mktemp -d "$HOME/lain-android-v1-test.XXXXXX")
export LAIN_HW_WORKSPACE
python - <<'PY'
import json, os, uuid
from pathlib import Path
root = Path(os.environ['LAIN_HW_WORKSPACE'])
fixtures = (
    ('battery', 'android.battery_status', {}),
    ('toast', 'android.toast', {'content': 'LAIN_OS Android v1 test'}),
    ('vibrate', 'android.vibrate', {'duration_ms': 100}),
    ('clipboard', 'android.clipboard_set', {'content': 'LAIN_OS_SAFE_CLIPBOARD_MARKER'}),
    ('share', 'android.share_text', {'content': 'LAIN_OS harmless chooser test; do not send'}),
)
for stem, name, args in fixtures:
    request = {'version': '0', 'request_id': str(uuid.uuid4()),
               'intent': 'manual Android acceptance',
               'actions': [{'id': 'a1', 'type': name, 'arguments': args}]}
    (root / (stem + '.json')).write_text(json.dumps(request), encoding='utf-8')
print('Harmless request fixtures prepared in isolated workspace.')
PY
```

Requests are single-use. To repeat tests, prepare a fresh temporary workspace;
do not circumvent duplicate-request protection or delete audit records.

## 4. Battery

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/battery.json" --json
```

Expected: success with conservative battery fields and PASSED structured-evidence
verification. Check percentage against the device; PASSED does not prove sensor
calibration. Unsupported/failure is a stop to inspect prerequisites/output format.

## 5. Short toast

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/toast.json" --json
```

Observe the short local message. Upstream `termux-toast` uses Bash `echo`, so
option-only text such as `-n` can display empty; command acceptance cannot establish
text fidelity. This test uses an ordinary explicit marker. Expected: success + LIMITED. Record whether it
appeared; do not relabel command acceptance as independent UI verification.

## 6. Short vibration

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/vibrate.json" --json
```

Expected: success + LIMITED; observe 100-ms vibration where device mode permits.
The adapter does not force vibration in silent mode.

## 7. Explicit harmless clipboard marker

This deliberately replaces current clipboard text with the marker; it never reads
or returns the previous clipboard. Run when that local overwrite is acceptable.

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/clipboard.json" --json
```

Expected: PASSED on private immediate comparison, LIMITED when Android denies/
lacks readback, or FAILED/VERIFICATION_FAILED on mismatch. Output must not contain
the marker or any unrelated clipboard content. Do not run a generic clipboard
getter or paste private readback into chat. Record the exact verification status.

## 8. Share chooser only

First prove that unconfirmed execution does not launch a chooser:

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/share.json" --json
```

Expected: confirmation_required, no execution. Then authorize this exact action:

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" execute "$LAIN_HW_WORKSPACE/share.json" --confirm a1 --json
```

Expected: only a user-visible chooser, success + LIMITED. Dismiss it. Do not select
a destination or send anything. LAIN_OS must not automatically send content.

## 9. Audit redaction / statuses

```sh
python -m lain --workspace "$LAIN_HW_WORKSPACE" audit tail --limit 20 --json
```

Clipboard/share `content` must be `[REDACTED]`; raw stdout/stderr or clipboard
readback must be absent. Battery is PASSED only with valid evidence. UI/haptic
effects are LIMITED. Share has a confirmation record and a Class 2 pre-execution
barrier plus final result. Do not change statuses manually.

## 10. Optional real Groq loop

Only after deterministic acceptance, reuse the user's existing secure Groq
configuration. Do not display/edit/copy its key file. This command reads battery
and requests a local short toast; it sends the normal planner context to Groq:

```sh
python -m lain --config "$HOME/.config/lain/config.toml" run "Read battery status, display a short local toast saying LAIN_OS Android v1 test, then complete. Do not use other capabilities." --json
```

Inspect trusted action history, verification and final checkpoint. Existing
policy/confirmation/budgets remain authoritative. This is optional networked
planner acceptance, not a prerequisite for offline deterministic capabilities.

For future runtime-affecting changes, repeat this procedure against the new exact source revision and preserve the resulting evidence with the release/test record. No automatic cleanup or modification of unrelated user data is performed.
