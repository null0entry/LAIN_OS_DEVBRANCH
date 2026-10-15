# Continuous LAIN Voice and Device Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute activated hands-free commands on a stock Moto G Play 2026 while another app is open or the screen is off.

**Architecture:** A user-started microphone foreground service holds the command runtime connection. A pure wake gate and audio coordinator enforce activation, deduplication and one microphone owner; registered native controls and an optional bounded Accessibility bridge execute actions through existing policy and verification.

**Tech Stack:** Python 3.11 controller/registry, Kotlin Android Views/Binder/Services, native Android offline SpeechRecognizer, CameraManager/AudioManager/Settings and AccessibilityService; existing GGUF inference remains optional for routine controls.

**Spec:** `docs/superpowers/specs/2026-10-14-continuous-voice-device-control-design.md`

## Global Constraints

- Owner approved the written design and instructed “Approved. Proceed.” Execute the following tasks sequentially with implementer and reviewer agents; do not repeat authorization questions.
- Activation: LAIN or default configurable alias lane, optional hey prefix, exact word boundary; five-second arming; final-only; command maximum 4096 UTF-8 bytes.
- Offline recognition only, API31+; silence restart 300 ms; error delays 500/1000/2000/4000/8000/15000 ms then stop.
- No ambient audio/transcript persistence, no raw shell, no automatic approval tokens or permission grants, no reboot/force-stop recording.
- Native controls use actual permissions and readback. Percentages 0–100; unnamed volume stream defaults to media; timeout 15000–1800000 ms.
- UI observations: active window, 128 nodes, depth20, text256 characters, total32KiB; snapshots expire at120s or window/content change and must be rechecked at execution; text entry4096 UTF-8 bytes.
- A visible notification Stop control is required for continuous mode; local Stop is independent of model and runtime health.
- Keep compile/target SDK35, minSDK24; extend emulator coverage to API36. Physical handset speech/battery/RAM acceptance remains separate.

## Review Focus

- Wake-word substrings and delayed callbacks must never trigger commands: Task4 gate tests.
- A permission denied midway, process disconnect or pending Binder write must release ownership and never replay work: Tasks5/6 tests.
- UI and background service bindings must not accidentally cancel or retain each other's task: Task5 lifecycle tests.
- LAIN's own TTS and manual capture must not race the continuous microphone: Tasks4/6 coordination tests.
- Reported success with wrong readback, stale UI nodes or OS-grant dialogs must not become verified execution: Tasks1–3 evidence and accessibility tests.

---

### Task 1: Registered commands, deterministic routing and native evidence contracts

**Files:** `lain/capabilities/registry.py`, new focused device-command/evidence modules, `lain/app/demo.py`, `lain/app/control.py`, `lain/app/native.py`, `lain/conversation_routing.py`, `lain/verification/android.py`; tests under `tests/`; new `specs/DEVICE_CONTROL_PROTOCOL.md`.

**Interfaces:** Produce `parse_device_command(text: str) -> ProposedAction | None` and the complete closed native argument/evidence schema in the protocol document. New controls are android.flashlight_set(enabled), volume_get(stream=media), volume_set(percentage,stream=media), volume_adjust(direction,stream=media), brightness_set(percentage), rotation_set(enabled), screen_timeout_set(milliseconds), settings_open(page), app_open(package), app_list(query=""). App-list is read-only discovery of launchable package identities needed for human app names. UI capabilities: android.ui.inspect(), ui.click(snapshot_id,node_id), ui.scroll(snapshot_id,node_id,direction), ui.set_text(snapshot_id,node_id,text), ui.global_action(action). Preserve exact-action approval for external/generic UI mutations.

- [ ] Write tests for direct flashlight/torch, volume and settings commands bypassing a broken/model-free router, bounded input, strict native evidence, mismatch/unavailable verification, external UI approval and unsupported nonnative hosts.
- [ ] Run the relevant unittest classes and observe failures.
- [ ] Implement registry, deterministic built-in proposals and native result preservation/validation. Extend trusted built-in selection only for exact recognized commands, with ordinary prose remaining chat. No new capability may fall back to shell on nonnative hosts.
- [ ] Run targeted tests and `python scripts/verify.py`; commit and document native JSON contracts for Tasks2/3.

### Task 2: Native direct device controls and navigation

**Files:** new focused `runtime/NativeDeviceControls.kt` and navigation helper, `runtime/NativeCapabilities.kt`, manifest permissions/queries, Android unit/instrumentation tests.

**Interfaces:** Consume Task1 protocol exactly. Produce `NativeDeviceControls(context).execute(capability: String, args: JSONObject): JSONObject?` returning null only for unrelated names; return existing status/details envelopes for owned names. Navigation may initially post an immutable PendingIntent to a private navigation Activity, never report that a settings change occurred merely because navigation was requested.

- [ ] Write tests for stream/percentage/index mapping, booleans and unknown fields, denied CAMERA/WRITE_SETTINGS, fixed volume, readback mismatch, settings/app navigation and launcher inventory bounds.
- [ ] Run tests to establish failure, then implement CameraManager callbacks, AudioManager and Settings readback and a bounded launcher query. Torch callback wait belongs off the main thread, times out honestly and unregisters.
- [ ] Add CAMERA, MODIFY_AUDIO_SETTINGS and WRITE_SETTINGS declarations and optional flash/camera features; add launcher query without QUERY_ALL_PACKAGES. Opening an app/page must obey background activity limits and offer a user-tap route.
- [ ] Run appropriate JVM/build checks; commit with actual hardware behavior marked unverified.

### Task 3: Optional bounded Accessibility app control

**Files:** new accessibility service/actions/snapshot store and private main-process Binder bridge/client, accessibility XML and manifest entries; navigation helper; tests.

**Interfaces:** Consume Task1 UI protocol. NativeCapabilities executes UI calls across the private bridge because :runtime cannot share the main process Accessibility singleton. Only the application's UID may transact. Produce a bounded/fresh active-window snapshot and selector-based native actions with limited acceptance evidence.

- [ ] Write tests for snapshot bounds and expiry/window changes, unique enabled selectors, no stale coordinates, own-app/OS grant/credential/locked-screen rejection, disabled accessibility and foreign Binder callers.
- [ ] Observe failure, implement the OS-enabled AccessibilityService plus private bridge, native dispatch and normal foreground/notification navigation. No service self-enabling, grant clicks, password entry or approval-UI clicks.
- [ ] Test and compile the APK/test APK, then commit. Do not require accessibility for direct device controls.

### Task 4: Wake command gate and shared audio/session ownership

**Files:** new `voice/WakeCommandGate.kt`, `VoiceAudioCoordinator.kt`, `ContinuousVoiceSession.kt`, pure JVM tests.

**Interfaces:** `WakeCommandGate.setEpoch(epoch: Long)`, `onFinal(epoch: Long,id: Long,text: String,confidence: Float?,nowMs: Long): WakeDecision`, `reset()`; decisions Ignored, Armed, Command(text), StopTask, StopListening. Epoch changes preserve only an unexpired activation window; reset clears it. Audio coordinator offers acquire/releaseManual(owner), acquire/releaseContinuous(owner), setPlayback(owner,active), playbackActive and subscribe/unsubscribe playback callbacks. Session state uses a token-bound immutable snapshot exposing running/ownsRuntime/message; late old-token updates cannot end a newer session.

- [ ] Test exact prefixes, lane option, no explain/airplane activation, five-second expiry, partial exclusion, confidence -1/null/low/NaN, UTF-8 limits, stale epochs and duplicate IDs, local Stop semantics.
- [ ] Test one microphone owner, idempotent leases, multiple playback owners and stale session teardown; observe failing tests.
- [ ] Implement the pure classes with no Android dependency in their decision logic; run targeted JVM tests and commit.

### Task 5: Continuous foreground voice service and runtime ownership

**Files:** new `voice/ContinuousVoiceService.kt`, live-recognizer abstraction/backend and bounded runtime command dispatcher; `WorkbenchViewModel.kt`; manifest mic service and permissions; tests.

**Interfaces:** Service ACTION_START/ACTION_STOP, main-process microphone FGS, uses Task4 coordinator/state and separate RuntimeClient binding. Live recognizer is offline-only API31+, final callbacks tagged by epoch, supports test injection. No Activity-owned microphone. Runtime ready/pending/recovery states determine submission; active tasks are not revised by unrelated utterances. Only a real foreground+connected lease prevents Workbench detach from issuing Stop.

- [ ] Test final activated command becomes one existing speech turn_submit envelope, routine command without Qwen, local stop while runtime is blocked, busy/recovery rejection, startup/disconnect, no replay, service teardown and retry schedule.
- [ ] Test hiding Workbench with/without the actual voice lease, TTS suspension, permission loss, notification-channel denial and stale callback after Stop.
- [ ] Implement service, visible notification Stop and status, bounded recognizer restart, wake-lock/mic ownership and runtime inspection/dispatch. No saved preference auto-starts recording; stop/destroy releases resources and settles voice-owned work.
- [ ] Run targeted JVM/build/instrumentation compilation checks and commit.

### Task 6: Owner UI, provisioning, playback coordination and final handoff

**Files:** `MainActivity.kt`, Workbench layout/strings, PermissionBroker/CapabilityAvailability, voice manual-capture/TTS/PCM engines, offline language setup, workflow/API36 matrix, `docs/ANDROID_APP.md`, task records and verification report.

**Interfaces:** UI starts/stops Task5 service from a visible action, renders Task4 state only on change, lets owner configure lane alias, grants microphone/notifications/camera/write-settings/DND/accessibility through Android's own screens. Provisioning uses the actual Android download outcome where supported. Manual capture and both playback paths use Task4 leases and clear them on all exits.

- [ ] Write tests for start permissions/Stop status, retained background owner, manual capture/transcription exclusion, TTS queue-flush/errors/closure and PCM completion, provisioning failure and missing optional grants.
- [ ] Implement UI and integration; preserve typed/manual availability when continuous mode is off and no stale preference-based authority.
- [ ] Run Python verification, native lifecycle tests, Android JVM tests, debug/test APK builds and lint; add API36 emulator checks, inspect native ABI/alignment and APK provenance; run end-to-end emulator suites and fix meaningful failures.
- [ ] Whole-branch review, complete TASK-082/work log, update PR60 from design to final implementation, provide a signed source/checksum-bound APK. Do not merge or claim physical-device acceptance automatically.
