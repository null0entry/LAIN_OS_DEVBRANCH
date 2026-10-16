# LAIN_OS

**Local Autonomous Intelligence Network Operating System**

LAIN_OS is the open-source software platform we are building: a local-first operating system for autonomous intelligence, beginning on Android and designed to expand across trusted devices and nodes.

## Naming

- **LAIN** — **Loyal Autonomous Intelligence Network**: the broader philosophy and network identity. "Loyal" describes alignment to the human using the system rather than a vendor, advertiser, or remote service.
- **LAIN_OS** — **Local Autonomous Intelligence Network Operating System**: the concrete software platform. "Local" describes its architectural priority: execution, authority, durable state, and user data stay on the user's hardware whenever practical.

`LAIN_OS` remains the canonical project and repository name. The Python import namespace remains `lain` for compatibility and ergonomics.

The core idea is simple:

```
intent -> plan -> permissioned action -> verification -> memory
```

LAIN_OS is not a monolithic assistant and not a cloud-first automation service. It is an integration layer between reasoning models, local files, deterministic automation, Android capabilities, developer tools, and user-owned data.

## Design goals

- Local-first execution and storage
- Human authority over consequential actions
- Portable, inspectable state
- Replaceable tools and adapters
- Deterministic automation beneath probabilistic reasoning
- Explicit permissions and auditability
- Graceful offline behavior
- Minimal dependence on proprietary services

## Initial environment

The current experimental stack assumes some combination of:

- Android
- Termux
- Shizuku
- Tasker or another deterministic automation engine
- Obsidian or plain Markdown for durable knowledge
- Git for source control
- External or local language models as planners

These are implementation choices, not permanent dependencies.

## System model

LAIN_OS separates four concerns:

1. **Reasoning** — interpret intent and choose an operation.
2. **Control** — authorize, schedule, and route actions.
3. **Execution** — perform deterministic device or filesystem operations.
4. **Memory** — persist results, state, provenance, and audit history.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

**Current authoritative proposed architecture (not yet implemented):** [API-first three-phase LAIN_OS design](docs/superpowers/specs/2026-10-16-api-first-three-phase-design.md). Phase 1: backend capability/permission/variable scaffolding. Phase 2: hosted HTTPS model interprets every final speech/text request and the trusted planner executes validated actions. Phase 3: one shared conversation UI for both commands and chat, with real model replies and truthful tool receipts. Local GGUF inference is deferred/removed from the active release target; historical code and docs remain until safely migrated. Android sandbox limits and physical-device acceptance remain explicit.

For the sequenced path from the current runtime to the 1.0 voice-agent release, see [docs/ROADMAP_1.0.md](docs/ROADMAP_1.0.md).

## 1.0 defining benchmark and 50-workflow catalog

**Implementation strategy:** [One reusable tool/adapter framework and a generic workflow engine](docs/WORKFLOW_ENGINE.md). Each native Android or external integration is implemented and verified once; the API model/planner creates new combinations dynamically. No separate implementation for every natural-language command.


The owner's acceptance benchmark is **one spoken/typed request → screenshot → verified crop → one authorized group image send → truthful receipt**, using a single persistent dependency-aware execution plan. Android screen projection requires system consent; opening a compose window is **not** equivalent to sending. The full [golden workflow specification](docs/superpowers/specs/2026-10-16-screen-crop-group-send-golden-workflow.md) and [50 workflow candidate catalog](docs/FEATURE_MATRIX_1_0.md) are **proposed design goals**, not features verified to work. The catalog does not assert categorical competitive exclusivity over Gemini.

Development order remains **Phase 1 capability/permission scaffolding → Phase 2 model-driven stateful planner → Phase 3 one voice/text chat**. Reusable typed tools, artifact passing, durable checkpoints, safe replanning and exact owner-approved external side effects are mandatory. A physical Moto group-send result and source-bound APK evidence are required before claiming the flagship works.

## First milestone

Demonstrate a complete local loop on Android:

```
natural-language request
        |
        v
structured action request
        |
        v
policy/permission check
        |
        v
local deterministic executor
        |
        v
verified result
        |
        v
append-only audit record
```

The first implementation should favor boring, observable operations such as file routing, notifications, project archiving, and launching intents before attempting unrestricted UI automation.

## Runtime quick start

The standalone Android interface source is in `android/`. It embeds this runtime
and provides a text workbench with fixed offline demos, confirmations, results,
history, and Stop controls. Portable verification, Android lint, and API 24
emulator tests pass; physical-device acceptance remains pending. See
[Android interface](docs/ANDROID_APP.md) for build instructions and
the current evidence status.

LAIN_OS requires Python 3.11 or newer and has no runtime third-party dependencies.

```sh
python -m pip install -e .
python -m lain capabilities --json
python -m lain --config ~/.lain/config.toml plan "open https://example.com" --json
python -m lain --config ~/.lain/config.toml do "open https://example.com" --json
python -m lain --workspace /tmp/lain-demo validate request.json --json
python -m lain --workspace /tmp/lain-demo execute request.json --confirm a1 --json
```

Requests use the typed [ACTION_PROTOCOL](specs/ACTION_PROTOCOL.md). Filesystem operations are restricted to configured roots, consequential actions require an explicit action ID passed to `--confirm`, and results distinguish execution from verification. Android actions report `unsupported` outside a supported Termux environment rather than simulating success.

## Build and verification

Install the package in editable mode and run the canonical portable verification suite:

```sh
python -m pip install -e .
python scripts/verify.py
```

The verifier compiles the package, runs the unit tests, checks the Git diff for whitespace errors, and scans runtime source for unsafe execution primitives and the repository for stale project naming. The same verifier is used by automated repository checks.

### Android Capability Expansion v1

Requires F-Droid Termux, matching Termux:API app and its command package.

| Capability | Arguments / bounds | Risk | Verification |
|---|---|---:|---|
| `android.battery_status` | `{}` | 0 | PASSED for validated structured reading |
| `android.vibrate` | `duration_ms`: integer, 1–5000 | 1 | LIMITED; no physical observation |
| `android.toast` | `content`: nonempty UTF-8, ≤1024 bytes | 1 | LIMITED; short toast command accepted |
| `android.clipboard_set` | `content`: nonempty UTF-8, ≤16384 bytes | 1 | PASSED on private immediate match; FAILED on mismatch; LIMITED if readback unavailable |
| `android.share_text` | `content`: nonempty UTF-8, ≤16384 bytes | 2 | LIMITED; chooser command accepted, never proof of sharing |

Unknown fields, NUL, invalid Unicode and boolean durations are rejected. Battery
results expose only percentage, status, plugged, health, temperature (°C), and
current (µA). Optional fields may be absent; raw extra fields never reach planners.
Share requires exact-action confirmation under existing policy and never chooses
a destination. Dismiss the chooser during testing without sending anything.

There is no general Termux, shell, intent or clipboard-read capability. Fixed argv
and stdin keep payloads separate from options. The filtered launch environment
preserves the Android runtime variables required by Termux:API while excluding
provider/executor credentials. Configured timeout and actively enforced
65536/4096-byte stdout/stderr limits bound command execution. Missing commands
return PLATFORM_UNSUPPORTED. The registry publishes duration/byte limits in both
CLI and planner catalogs; trusted validation remains authoritative.

Clipboard content uses existing recursive `content` redaction in audit, session
output and planner history. Private readback never leaves the adapter, including
on mismatch/error. Known payload echoes in intent/goal/reason views are masked after the typed
payload is known. The initial user goal remains available to its planner.
Clipboard/share arguments are also redacted in `plan`/`do`
display output; do not reuse that display as an executable request. Session
checkpoints retain supplied action content, including completed actions, in the
existing private mode-0600 files for exact-action resume; those files remain under the device user's
control. Keep credentials out of goals/payloads; free-form goal/reason text is not
a secret vault.

Manual physical acceptance was completed on Android 16 using F-Droid Termux plus
matching Termux:API at exact feature head
`0d7efe584b78a050c2817b2ab3e3f555e2450dff`. Battery and clipboard verification
PASSED; toast, vibration, and share reported LIMITED as designed; the user observed
the toast, vibration, and share chooser; unconfirmed share was blocked by policy;
and audit redaction was confirmed. This is manual device evidence, not automated
Android CI. Later runtime-affecting changes require revalidation. See
[hardware acceptance](docs/ANDROID_ACCEPTANCE.md). Portable tests use injected
command boundaries and require no phone or internet.

### Environment smoke check

On Android/Termux (or ordinary Linux), run:

```sh
python scripts/verify_android.py
```

The helper reports platform information, checks that `lain` is importable, lists the availability of commands used by the Android adapters, and invokes the canonical portable verification suite. It is deliberately non-destructive: it does not open a URI, post a notification, modify user files, or perform any other device action.

`plan` and `do` use a configured, provider-independent external-process planner. Configure `planner_command` as a TOML argv array; LAIN_OS sends a registry-derived capability catalog and the intent as JSON on stdin. Planner output is an untrusted proposal, not an executable envelope: the trusted planning service supplies protocol version, request ID, exact original intent, and deterministic action IDs, then the existing runtime validates capabilities and arguments. `plan` performs preflight only. `do` uses the same path and leaves policy—including confirmation requirements—authoritative. With no planner configured these commands fail with `PLANNER_UNAVAILABLE`; direct `validate` and `execute` remain fully offline and supported.

#### Groq planner adapter

The optional `lain-groq-planner` command uses Groq without adding an SDK dependency. Store the API key outside the repository, restrict access, and configure the subprocess argv:

```sh
mkdir -p ~/.config/lain
printf '%s\n' '<your Groq API key>' > ~/.config/lain/groq.key
chmod 600 ~/.config/lain/groq.key
```

```toml
planner_command = [
  "lain-groq-planner",
  "--key-file",
  "/data/data/com.termux/files/home/.config/lain/groq.key",
]
```

The same adapter supports both planner contracts. For a one-shot action, inspect the proposal first and then execute through the trusted runtime:

```sh
lain --config ~/.config/lain/config.toml plan "open https://example.com" --json
lain --config ~/.config/lain/config.toml do "open https://example.com" --json
```

For a durable autonomous session, `lain run` sends the AGENT_PROTOCOL request to Groq. Groq may propose bounded batches and `continue | complete | blocked`; trusted LAIN_OS code still owns request/action IDs, policy, confirmation, execution, verification, checkpoints, and budgets:

```sh
lain --config ~/.config/lain/config.toml run "create hello.txt containing hello, then create done.txt containing finished" --json
```

### Autonomous loops

The bounded durable agent runtime sits above the existing planner and trusted executor. A session repeatedly plans a bounded action batch, executes one authorized action at a time, verifies it, checkpoints the result, and replans from durable state.

```sh
lain --config ~/.lain/config.toml run "create hello.txt, then create done.txt" --json
lain --config ~/.lain/config.toml sessions --json
lain --config ~/.lain/config.toml session <session-id> --json
lain --config ~/.lain/config.toml resume <session-id> --json
lain --config ~/.lain/config.toml cancel <session-id> --json
```

If an action requires confirmation, the session pauses rather than allowing the planner to approve itself:

```sh
lain --config ~/.lain/config.toml resume <session-id> --confirm i1a1 --json
```

Default trusted budgets are 12 planner iterations, 32 attempted actions total, 900 seconds of active planner/executor runtime, and at most `planner_max_actions` actions in one planner batch. Consumed budgets survive restart and resume.

Session state is stored beside the configured audit directory under `sessions/<session-id>/state.json`. With the default configuration this is `~/.lain/sessions/<session-id>/state.json`; with `--workspace /tmp/lain-demo` it is `/tmp/lain-demo/.lain/sessions/<session-id>/state.json`.

Each action is checkpointed before another action executes. Resume never blindly replays a completed action, and audit evidence that is ahead of the session checkpoint causes a fail-closed reconciliation state. This milestone is foreground/on-demand only: it does not add a background daemon or unrestricted AccessibilityService control.

See [specs/AGENT_PROTOCOL.md](specs/AGENT_PROTOCOL.md) for the planner lifecycle wire contract.

### Reddit post creation

`reddit.create_post` is the only Reddit API operation exposed. Copy `.env.example` to an ignored local `.env` if useful, but export its values into the process environment; LAIN_OS does not automatically load dotenv files. Configure a Reddit OAuth application and refresh token without supplying an account password to LAIN_OS:

```sh
export LAIN_REDDIT_CLIENT_ID=...
export LAIN_REDDIT_CLIENT_SECRET=...
export LAIN_REDDIT_REFRESH_TOKEN=...
export LAIN_REDDIT_USER_AGENT='linux:LAIN_OS:0.1 (by /u/example)'
python -m lain execute reddit-request.json --confirm a1 --json
```

Generate a fresh request ID with `python -c 'import uuid; print(uuid.uuid4())'`. Example request:

```json
{
  "version": "0",
  "request_id": "<fresh UUID>",
  "intent": "Publish a Reddit post",
  "actions": [{
    "id": "a1",
    "type": "reddit.create_post",
    "arguments": {
      "subreddit": "example",
      "title": "LAIN_OS test",
      "body": "Example content."
    }
  }]
}
```

The runtime exchanges the refresh token only at execution time, publishes through Reddit's OAuth API, and performs a separate post lookup. It never exposes a general authenticated HTTP capability. Unit tests inject a fake transport; live posting is intentionally not automatic.

## Status

The v0 runtime, planner boundary, durable autonomous loops, narrow Reddit slice,
and Android Capability Expansion v1 are implemented and covered by portable tests.
Notification and URI opening were manually validated in an earlier F-Droid
Termux + Termux:API device pass. The exact-head acceptance for Android Capability
Expansion v1 separately validated the five new capabilities and audit behavior at
`0d7efe584b78a050c2817b2ab3e3f555e2450dff`: battery and clipboard PASSED;
toast, vibration, and share remained LIMITED by design; and the share confirmation
barrier held. A real configured Groq planner also completed a live Android/Termux
autonomous session with two independently verified file writes before returning
complete. Manual device evidence is not automated Android CI and does not claim
live interruption/resume validation. Live Reddit posting remains unvalidated.

## Security and data handling

Secrets, authentication tokens, private messages, precise personal location history, recovery codes, and other sensitive user data must not be embedded in source, build artifacts, planner context, logs, audit output, or exported settings.

LAIN_OS should make the device more capable without making the human using it less in control.
