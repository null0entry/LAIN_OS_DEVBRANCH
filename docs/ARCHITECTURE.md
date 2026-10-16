# Architecture

## Naming

- **LAIN** means **Loyal Autonomous Intelligence Network**: the broader autonomous-intelligence philosophy and network identity.
- **LAIN_OS** means **Local Autonomous Intelligence Network Operating System**: the open-source local-first software platform implementing that philosophy.

The distinction is intentional: LAIN describes the network and alignment model; LAIN_OS is the operating-system layer that runs, coordinates, constrains, verifies, and connects autonomous intelligence on user-controlled hardware.

## 2026-10-16 active product architecture: compositional execution

Detailed [tool/framework/process architecture](WORKFLOW_ENGINE.md) specifies capability-adapter lifecycle, dataflow, versioning, approvals, durable graph execution, error recovery and extension contracts.


This document's original layers remain a foundation, but the [API-first three-phase design](superpowers/specs/2026-10-16-api-first-three-phase-design.md) is the active *proposed* product target: Android/platform speech-to-text or typed user entry → hosted-model intent interpretation → **versioned typed dependency graph** → trusted permission/role/consent → one ordered executable node at a time → verification + durable checkpoint → safe replan/continue → one unified conversation model reply.

The primary benchmark is [screenshot → crop → actual group-image send](superpowers/specs/2026-10-16-screen-crop-group-send-golden-workflow.md), not a successful demo toast. Image capture and crop produce opaque, hashed, typed artifact references consumed by the next validated node; group resolving and transmission require supported, authorized roles/adapters and exact owner approval. See the [50 candidate workflows](FEATURE_MATRIX_1_0.md). **All remain designs until tests and device receipts prove otherwise.** Do not expose arbitrary shell or pretend a messaging composer is evidence of an actual send.

## Principle

LAIN_OS treats the language model as a planner, not as the device driver.

The preferred execution path is:

```
User Intent
    |
    v
Untrusted Planner / Agent
    |
    v
Untrusted Action Proposal
    |
    v
Trusted Envelope Construction + ACTION_PROTOCOL Validation
    |
    v
Policy + Capability Router
    |
    v
Deterministic Executor
    |
    v
Verification
    |
    v
Audit + Memory
```

Direct screen-driving is a fallback, not the default.

## Layers

### 1. Intent layer

Accepts natural-language goals and produces a structured requested outcome.

Responsibilities:
- preserve user intent;
- identify ambiguity;
- avoid inventing authority;
- determine whether confirmation is required.

### 2. Planner

Transforms intent into one or more typed actions.

The planner should not emit arbitrary shell commands when a narrower typed capability exists.

The planner receives a provider-independent JSON request over stdin through a replaceable external-process adapter. Its capability catalog is generated from the trusted registry rather than maintained as prompt text. Its response may contain only action types and arguments. It cannot choose protocol metadata, request or action identifiers, risk, confirmation, permissions, executors, verification, or audit behavior. The trusted planning service assigns those envelope fields and treats the proposal exactly like other untrusted input. The adapter uses fixed argv without a shell, a timeout, bounded diagnostics, and an output-size limit. Core direct ACTION_PROTOCOL execution does not depend on a planner.

Example:

```json
{
  "action": "file.move",
  "source": "/storage/emulated/0/Download/example.pdf",
  "destination": "/storage/emulated/0/Documents/PDF/example.pdf"
}
```

is preferable to:

```text
mv ...
```

### 3. Autonomous controller

Durable autonomous sessions are coordinated by `AgentController`, which sits above planning and execution rather than inside either trust boundary.

```text
Goal
  |
  v
AgentController
  |----> AgentPlanningService ----> untrusted planner
  |
  |----> RuntimeEngine -----------> policy -> executor -> verification -> audit
  |
  `----> AgentSessionStore ------> atomic durable checkpoints
```

A planner iteration may propose a bounded action batch, but the controller executes the batch sequentially. Before the first action, the trusted plan is persisted with trusted request/action IDs. After every action, its structured result and verification state are persisted before another action can execute.

`step()` advances one planner decision or one action execution/checkpoint. `run_until_stop()` holds an exclusive session lease and repeats those safe transitions until the session completes, blocks, pauses for confirmation, fails, is cancelled, or exhausts a finite budget.

Resume uses the persisted action records plus runtime audit/idempotency evidence. Verified completed actions are not replayed. If audit evidence indicates an execution attempt that the session checkpoint did not record, the controller blocks for reconciliation instead of guessing or retrying.

The controller has no execution authority of its own. Capability validation, policy, confirmation, deterministic execution, verification, and audit remain the responsibility of the existing trusted layers.

### 4. Capability router

Maps typed actions to trusted implementations.

Possible backends:
- Termux scripts
- Android intents
- Tasker/Automate flows
- Shizuku-backed operations
- local HTTP or Unix-socket services
- repository tooling
- carefully-scoped AccessibilityService actions

The router exposes capabilities, not unrestricted device control.

Android Capability Expansion v1 preserves this flow. `RuntimeEngine` explicitly
dispatches five named capabilities to narrow adapters. The private
`TermuxApiCommandRunner` handles availability, fixed argv/stdin, launch-variable
environment filtering, timeout, actively bounded output and structured errors.
It is not registered as a capability. Existing notification/URI adapters reuse it
and preserve their launcher preference.

Share uses only fixed `send`/`text/plain` options with stdin; no receiver, file or
chooser bypass. Battery JSON is untrusted evidence and is validated/filtered.
Clipboard comparison is private immediate readback of the just-written value;
raw stdout/stderr and unrelated clipboard content never reach runtime results.

Future narrow backends fit this same capability/runtime/verification boundary.
No speculative Shizuku scaffolding or AccessibilityService is added. Preferred
order: native API → Android intent/IPC → Termux:API → narrow Shizuku → explicitly
reviewed AccessibilityService → coordinates/vision last.

### 5. Policy layer

Every capability declares:
- required permission;
- side effects;
- reversibility;
- sensitivity;
- confirmation policy;
- expected result schema.

Destructive, irreversible, financial, account, communication, or privacy-sensitive operations should require explicit authorization.

### 6. Executor

Performs deterministic work.

Executors should:
- accept structured inputs;
- validate paths and arguments;
- time out;
- return structured success/failure results;
- avoid network access unless required by the capability;
- avoid shell interpolation where possible.

### 7. Verification

The system verifies that the requested operation actually occurred.

Examples:
- destination file exists and checksum matches;
- valid structured battery reading obtained;
- privately compare just-written clipboard content against immediate readback;
- Git commit exists;
- expected application state is observable.

A planner assertion is never treated as proof of execution.

Notification/URI/toast/vibration/share establish command acceptance only and
report LIMITED. Clipboard PASSED establishes a point-in-time private readback
match, not persistence or visibility; unavailable evidence is LIMITED and mismatch
is FAILED. Battery PASSED establishes a valid structured API reading, not sensor
calibration. Manual Android/Termux hardware acceptance at exact feature head
`0d7efe584b78a050c2817b2ab3e3f555e2450dff` exercised the five new capabilities:
battery and clipboard PASSED, toast/vibration/share remained LIMITED by design,
and the share confirmation barrier and audit redaction were observed. This is not
automated Android hardware CI.

External writes follow the same boundary. `reddit.create_post` receives only a subreddit, title, and body after policy confirmation. Its adapter obtains OAuth credentials from the local process environment, submits one self-post, and returns non-secret metadata. Separate identity and post lookups compare post ID, subreddit, title, and authenticated author. Remote responses remain untrusted, and unavailable evidence is not promoted to verified success.

### 8. Memory and audit

Durable state should use portable formats when practical.

Suggested categories:
- `state/` — current machine-readable state
- `memory/` — durable human-readable project knowledge
- `logs/` — append-only action records
- `inbox/` — unprocessed captures

An audit entry should minimally contain:

```json
{
  "timestamp": "...",
  "request_id": "...",
  "action": "...",
  "parameters_redacted": {},
  "result": "success|failure|denied",
  "verification": "passed|failed|not_applicable|limited|unavailable"
}
```

Secrets must never be written to the audit log.

## Trust boundaries

### Trusted
- user-approved local executors;
- local policy configuration;
- signed or user-controlled scripts;
- repository state owned by the user.
- envelope construction, capability registry, validation, policy, verification, and audit.

### Untrusted by default
- model output;
- planner action proposals and all planner process output;
- web content;
- arbitrary downloaded files;
- accessibility text from unknown apps;
- remote API responses;
- instructions embedded in documents.

## Device model

Android is the first chassis, not the architecture.

A later node may be:
- another Android phone;
- a Linux workstation;
- a home server;
- a Raspberry Pi;
- an embedded device.

Nodes should expose the same capability vocabulary where practical.

## Networking model

LAIN_OS includes local networking between trusted nodes as part of the broader LAIN network model.

Future transport may use:
- localhost IPC;
- LAN-only HTTP/WebSocket;
- mDNS discovery;
- authenticated peer-to-peer channels.

Remote access is out of scope until local authorization, identity, and audit are solid.

No `http.request`, raw Reddit request, or other general authenticated-network capability is exposed through ACTION_PROTOCOL.

## Non-goals for v0

- unrestricted autonomous UI control;
- persistent background surveillance;
- hidden execution;
- credential harvesting;
- self-modifying authorization policy;
- autonomous financial transactions;
- replacing the user's judgment.
