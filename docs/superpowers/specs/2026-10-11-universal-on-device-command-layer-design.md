# Universal On-Device Command Layer — Design

**Date:** 2026-10-11  
**Status:** Proposed architecture  
**Repository:** `null0entry/LAIN_OS_DEVBRANCH`

## 1. Intent

LAIN_OS should let a person speak or type an ordinary request and, when that request is an execution command, turn it into a bounded, permissioned, verified on-device action.

The target interaction is:

```text
voice or typed intent
  -> command/conversation routing
  -> planner proposal
  -> trusted capability lookup
  -> policy + exact approval when required
  -> deterministic execution
  -> independent verification
  -> audit + durable result
  -> spoken/visual response
```

The model interprets intent. It does not become the device driver.

The architecture must scale toward broad Android control without introducing arbitrary model-authored shell execution, unrestricted authenticated HTTP, hidden authority escalation, or a planner-controlled AccessibilityService.

## 2. Current repository position

The existing repository already contains the trusted execution spine required by this design:

- typed ACTION_PROTOCOL validation;
- capability registry and trusted argument validation;
- policy and exact-action confirmation;
- deterministic execution;
- verification and append-only audit;
- AGENT_PROTOCOL and durable AgentController sessions;
- bounded budgets, checkpointing, resume, cancellation, and reconciliation;
- Android voice-turn, playback, barge-in, progress narration, and conversational revision work;
- native Android capability adapters;
- embedded GGUF work in the PR #41 -> #44 stack.

As of this design:

- PR #41 provides bounded GGUF preflight/JNI feasibility.
- PR #42 integrates an on-device GGUF planner path.
- PR #43 hardens GGUF Stop and profile-save lifecycle behavior.
- PR #44 exposes the embedded engine as a separate native offline Qwen text-chat surface.
- PR #44 intentionally prevents ordinary chat input from starting a task, executing a capability, or granting approval.
- At PR #44 head `5acdfb4aebc7f66c1c83d21666ccd64d811db739`, Verify is green. Android API 24 and API 35 both complete APK build, unit tests, and lint successfully; the later emulator instrumentation step is red.
- Physical GGUF inference and real-device acceptance remain separate evidence gates.

The architectural gap is therefore not a missing planner or executor. It is the bridge between natural conversation/voice input and the existing trusted command runtime, followed by capability breadth.

### 2.1 Clean-install / factory-device requirement

A clean stock Android device is a first-class acceptance environment, not an edge case.

The baseline installation contract is:

```text
factory/stock Android device
  -> install one LAIN_OS APK
  -> launch
  -> first-run capability scan
  -> choose planner path
  -> request only the permissions needed for selected capabilities
  -> become usable without Termux, Tasker, Shizuku, root, or developer setup
```

Core LAIN_OS must not require Termux, Tasker/Automate, Shizuku, root, ADB, Android Studio, a separately installed Python runtime, or a preconfigured shell environment.

Those systems may extend LAIN_OS later, but their absence must never prevent first launch, typed conversation, planner configuration, model import, ordinary native Android capabilities, or truthful capability discovery.

First-run setup must be app-owned and recoverable. It should include:

- planner choice: Demo, configured Cloud/Local endpoint, or On-device GGUF;
- On-device setup that can import a GGUF through Android's document picker into app-private storage without Termux;
- a future app-managed recommended-model acquisition path, so manual browser/Termux model staging is not a permanent product requirement;
- a capability scan showing which capabilities are READY, NEEDS_PERMISSION, NEEDS_SETTINGS_GRANT, OPTIONAL_DEPENDENCY_MISSING, or UNSUPPORTED;
- runtime permission requests only when the user enables or first invokes a capability that needs them;
- deep links into the correct Android Settings panel for special grants that Android does not expose as ordinary runtime permission dialogs;
- re-check on return from Settings and after permission revocation;
- continued use of all unaffected capabilities when one permission or optional dependency is denied.

LAIN_OS must never claim that it can silently grant Android special permissions. AccessibilityService enablement, Shizuku authorization, notification-listener access, overlay/write-settings style grants, battery-optimization exemptions, and similar platform-controlled privileges require explicit user action in Android-owned UI when used.

The bootstrap UI must explain why a requested permission is needed, which named capabilities it unlocks, and whether denial merely limits that capability or blocks a selected workflow. Denial must fail closed without trapping the user in an unusable setup loop.

## 3. Non-negotiable trust boundary

The canonical rule remains:

> Models plan; trusted code authorizes, executes, verifies, and records.

A planner may propose only capabilities published by the trusted registry. It may not choose:

- execution backend;
- risk class;
- confirmation policy;
- request/action identifiers;
- permissions;
- verification status;
- audit state;
- durable session authority.

The trusted host constructs and validates the executable envelope.

General-purpose model-controlled shell execution is explicitly excluded. If an underlying operation requires a shell, Shizuku, AccessibilityService, Tasker, Termux, IPC, or another privileged mechanism, LAIN_OS exposes a narrow typed capability around that operation rather than exposing the mechanism itself.

## 4. Conversation versus command

PR #44's chat boundary is preserved.

LAIN_OS should have two semantically distinct turn outcomes:

### 4.1 Conversation turn

A conversational turn may:

- answer questions;
- discuss plans;
- maintain bounded chat history;
- explain device state already supplied to it;
- suggest an action.

It may not execute a capability or grant an approval.

### 4.2 Command turn

A command turn explicitly enters the trusted task path:

```text
accepted final voice turn or typed command
  -> CommandRouter
  -> AgentController / planning service
  -> capability proposal
  -> trusted validation + policy
  -> executor
  -> verifier
  -> audit/session checkpoint
```

A command can be entered from the same user-facing conversation surface, but the execution state must be visually and structurally distinguishable from ordinary model text.

The command router is not an authority layer. Its purpose is only to select whether a turn remains conversational or becomes a candidate trusted task.

When classification is ambiguous and execution would have side effects, LAIN_OS should fail toward conversation/clarification rather than silently execute.

## 5. Capability model

Each exposed operation should be represented by a trusted capability specification conceptually equivalent to:

```text
CapabilitySpec {
  name
  argument_schema
  risk_class
  required_permissions
  executor
  verifier
  redaction_policy
  cancellation_contract
}
```

The planner sees a registry-derived capability catalog, not a hand-maintained prompt list.

Examples of future capability names:

```text
android.flashlight.set
android.volume.set
android.media.play_pause
android.app.launch
android.brightness.set
android.settings.open
android.bluetooth.open_settings
android.wifi.open_settings
android.notification.post
file.copy
file.move
file.write_text
```

Natural language such as:

```text
"turn the flashlight on"
```

may produce an untrusted proposal such as:

```json
{
  "type": "android.flashlight.set",
  "arguments": {
    "enabled": true
  }
}
```

Only the trusted runtime determines whether that proposal is valid and executable.

## 6. Executor priority ladder

Capability implementations should prefer the narrowest reliable Android primitive in this order:

1. **Native Android API**
2. **Android Intent / Binder / supported IPC**
3. **Existing deterministic local integrations** such as bounded Tasker, Termux:API, or local services
4. **Narrow Shizuku-backed adapter**
5. **Explicitly reviewed AccessibilityService adapter**
6. **Coordinate/vision UI automation as a last resort**

This priority order is consistent with the existing architecture preference for direct APIs over screen-driving.

The presence of a more privileged backend never broadens the planner's authority. Privilege remains encapsulated inside one named capability.

## 7. Phased delivery

### Stage 0 — Stabilize embedded Qwen

Goal: make the current PR #41 -> #44 stack a truthful, physically tested native inference base.

Exit evidence:

- supported GGUF imports into the Android app;
- real bounded generation occurs on the reference device in airplane mode;
- Stop works with measured behavior;
- model and APK SHA-256 are recorded;
- memory/latency/resource behavior is captured;
- PR #44 text chat works across first message, follow-up, Stop, New Chat, and relaunch;
- emulator instrumentation failures are understood or fixed rather than ignored.

### Stage 0.5 — Clean-install bootstrap and permission broker

Goal: prove that a new stock Android device can reach a usable LAIN_OS state from one APK with no preinstalled helper stack.

Required behavior:

- first launch succeeds with no existing LAIN_OS state;
- app-private directories and durable stores initialize themselves;
- planner setup offers a usable path without external tooling;
- on-device GGUF import uses the Android document picker and app-private model storage;
- capability availability is derived from device/API/permission state rather than hardcoded assumptions;
- ordinary runtime permissions are requested through Android's permission APIs when needed;
- special grants open the correct Android-owned Settings surface and are revalidated on return;
- denied permissions or missing optional integrations disable only the affected capabilities;
- no helper app is required for core Stage-1/Stage-2 native capabilities;
- reinstall/clear-data returns to the same truthful first-run state.

Factory-device acceptance must be run on an actual clean or app-data-cleared device state, not inferred from upgrade testing.

### Stage 1 — Universal command bridge

Goal: connect final typed and spoken commands to the existing AgentController without weakening PR #44's ordinary-chat boundary.

Required behavior:

- typed and final accepted voice commands use the same trusted task path;
- planner receives the trusted live capability catalog;
- policy/approval/execution/verification/audit remain unchanged;
- ordinary chat cannot directly call a capability;
- ambiguous side-effecting turns do not execute silently;
- Stop task remains separate from Stop talking/Stop response.

First acceptance command:

```text
"Show battery"
```

The typed and spoken forms must reach the same trusted capability and terminal result.

### Stage 2 — Core Android capability pack

Goal: make LAIN_OS immediately useful as a phone-control layer using narrow native and Intent capabilities.

Initial target families:

- app launch;
- flashlight;
- media play/pause where supported;
- volume;
- brightness where supported;
- notifications;
- common settings surfaces;
- URI/deep-link launching;
- existing battery, vibration, toast, clipboard, share, and file operations.

Each capability requires:

- typed argument bounds;
- explicit risk class;
- deterministic executor;
- best-available verification;
- truthful LIMITED/UNAVAILABLE states where Android cannot independently prove the physical/UI effect;
- cancellation semantics where applicable;
- adversarial validation.

### Stage 3 — Privileged capability pack

Goal: expose operations unavailable to an ordinary Android application through narrowly scoped Shizuku-backed adapters.

Requirements:

- no generic shell capability;
- each privileged operation gets its own schema and policy;
- missing Shizuku or permission state returns a truthful unsupported/blocked result;
- elevated execution does not bypass confirmation, verification, audit, or redaction.

### Stage 4 — Extensible capability/plugin contract

Goal: allow new trusted on-device actions to be added without changing planner architecture.

The extension boundary must define:

- capability metadata;
- arguments and bounds;
- risk and approval;
- executor binding;
- verifier binding;
- redaction;
- platform/permission availability;
- cancellation;
- structured errors.

An extension may add capability implementations. It may not redefine the trusted policy engine or grant itself authority.

### Stage 5 — Reviewed UI automation fallback

Goal: operate applications that expose no useful API, Intent, or trusted integration.

AccessibilityService should be capability-scoped rather than planner-scoped.

Examples:

```text
ui.app.perform_known_action
ui.screen.activate_named_control
```

should be preferred over exposing unrestricted raw taps or arbitrary accessibility traversal.

Coordinate/vision automation remains the final fallback because it is least deterministic and hardest to verify.

### Stage 6 — Physical universal-command acceptance

Goal: prove the integrated experience on the reference Android device.

Acceptance matrix includes:

- clean install / cleared-app-data bootstrap from one APK;
- first-run planner/model setup without Termux, Tasker, Shizuku, root, or ADB;
- permission grant, denial, revocation, and Settings-return revalidation;
- typed and voice equivalence;
- permission denied/granted transitions;
- exact approvals;
- foreground/background/rebind behavior;
- Stop and cancellation;
- low-confidence or ambiguous speech;
- malformed planner output;
- unknown capability proposals;
- unavailable executor backends;
- verification PASS/FAILED/LIMITED/UNAVAILABLE truthfulness;
- restart and resume without duplicate effects;
- audit/redaction;
- resource pressure;
- no silent cloud fallback.

## 8. Roadmap sequencing change

The current roadmap was written before embedded GGUF became active product work. It still treats embedded GGUF/on-device LLM inference as deferred beyond 1.0, which no longer matches the active PR #40 -> #44 direction.

After this design is approved, the roadmap should be rebased from:

```text
voice acceptance
-> durable workflows
-> artifact/media pipeline
-> providers
-> publishing
```

to:

```text
clean-install bootstrap
-> native Qwen acceptance
-> universal command bridge
-> core Android capability expansion
-> privileged/extensible capability layer
-> physical phone-control acceptance
-> durable workflows
-> artifact/media pipeline
-> providers
-> publishing
```

This does not delete the existing workflow/media/provider roadmap. It changes the critical path so LAIN_OS becomes a broadly useful on-device operating layer before later media-production scope dominates development.

## 9. Product success definition

Before the command examples below count as product success, a second untouched or factory-reset-equivalent Android device must be able to install the APK, complete first-run setup, grant or deny permissions through Android-owned flows, configure or import a planner/model, and reach the same command surface without developer tooling.

The first meaningful product threshold is reached when a user can naturally say or type commands such as:

```text
"Open Spotify."
"Turn the flashlight on."
"Set the volume to thirty percent."
"Copy this text."
"Move that file into Documents."
"Open Bluetooth settings."
```

and LAIN_OS:

1. interprets the intent through the selected planner;
2. selects only registered capabilities;
3. obtains any required trusted approval;
4. executes through the appropriate deterministic backend;
5. verifies the strongest available postcondition;
6. records the result;
7. returns a truthful spoken/visual outcome.

The broader "any on-device function" goal means:

> Any operation that Android, a user-authorized privileged backend, or an installed trusted adapter can expose as a bounded capability can become reachable through natural language without changing the planner/executor trust model.

It does not mean bypassing Android's security model or exposing an unrestricted device-driver primitive to the model.

## 10. Explicit non-goals

This design does not authorize:

- unrestricted arbitrary shell execution;
- arbitrary authenticated HTTP;
- planner-selected privilege escalation;
- planner-controlled raw AccessibilityService traversal;
- hidden execution;
- self-modifying authorization policy;
- silent local-to-cloud fallback;
- automatic retry of uncertain external writes;
- representing command acceptance as verified physical success.

## 11. Required follow-up after review

Once this design is reviewed and accepted:

1. update `docs/ROADMAP_1.0.md` so clean-install bootstrap, active embedded GGUF work, and universal command control are on the critical path;
2. update the canonical task sequencer with a clean-install bootstrap/permission broker slice before the command-bridge and capability-expansion slices;
3. write a task-level implementation plan for Stage 1;
4. implement Stage 1 against the exact post-PR-#44 stack using TDD;
5. expand Stage 2 capabilities in small independently reviewable slices;
6. keep physical-device acceptance separate from CI/emulator evidence.
