# Universal Command Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let final typed and spoken turns enter the existing trusted AgentController as commands while ordinary on-device chat remains non-executing.

**Architecture:** Reuse the existing Workbench task route, ConversationTurnManager, AgentController, planner binding, policy, approvals, executor, verification, audit, and Stop paths. Introduce one bounded turn-disposition seam that can classify a final turn as CHAT, TASK, or CLARIFY; only TASK creates/updates an AgentSession. Conversation output never becomes execution authority.

**Tech Stack:** Kotlin Android UI/ViewModels, embedded Python controller/conversation runtime, existing GGUF planner bridge, ACTION_PROTOCOL/AGENT_PROTOCOL, JUnit/Android instrumentation/Python unittest.

**Spec:** `docs/superpowers/specs/2026-10-11-universal-on-device-command-layer-design.md`

## Global Constraints

- Bootstrap/permission state from the clean-install plan is authoritative for capability availability.
- Ordinary chat input cannot execute a capability or approve an action.
- TASK routes use the exact existing AgentController policy/execution/verification/audit path.
- Ambiguous side-effecting turns route to CLARIFY rather than execution.
- Typed and final accepted speech use the same route.
- Partial speech never creates a task.
- Stop talking/Stop response remains independent from Stop task.
- No arbitrary shell, authenticated HTTP, or raw AccessibilityService authority.

## Review Focus

- A conversational question containing action-like words must not accidentally execute.
- Low-confidence/partial speech must never cross into TASK.
- Active-session revision must remain distinct from starting a second task.
- Router/model failure must fail toward clarification/chat, not hidden execution.
- Duplicate/stale final turn delivery across rotation/rebind must not duplicate a task.

---

### Task 1: Trusted turn-disposition contract

**Files:**
- Create: `lain/conversation_routing.py`
- Create: `tests/test_conversation_routing.py`

**Interfaces:**
- Produces: `TurnDisposition = CHAT | TASK | CLARIFY`
- Produces: `TurnRoutingDecision(disposition, response_text, reason)`
- Produces strict parser/validator for bounded untrusted routing output.
- No decision object can carry capability/action/approval fields.

- [ ] **Step 1: Write RED Python tests** for exact fields, unknown fields, malformed JSON, oversize, action-like extra fields, and CLARIFY fail-closed behavior.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement the contract and strict parser.**
- [ ] **Step 4: Run focused tests.**
- [ ] **Step 5: Commit.**
Commit: `feat: define bounded turn routing contract`

### Task 2: Route final turns without bypassing AgentController

**Files:**
- Modify: `lain/app/control.py`
- Modify: existing conversation module used by `ConversationTurnManager`
- Modify: `tests/test_app_turns.py`
- Modify: `tests/test_agent_controller.py`

**Interfaces:**
- Consumes: `TurnRoutingDecision`
- Produces app-protocol result with either `conversation_response`, `clarification_required`, or existing `session` snapshot.
- TASK continues to call `_start_session(record.text)`; revision continues to call `controller.revise(...)`.

- [ ] **Step 1: Add RED tests** proving CHAT cannot start session, TASK starts exactly one session, CLARIFY starts none, active revision semantics are unchanged, stale duplicate turn cannot duplicate effects.
- [ ] **Step 2: Run tests and confirm RED.**
- [ ] **Step 3: Insert routing before START_TASK only for new final turns**; keep legacy explicit `start` RPC as command-authoritative for compatibility.
- [ ] **Step 4: Preserve approval/Stop/recovery code unchanged.**
- [ ] **Step 5: Run controller/app tests.**
- [ ] **Step 6: Commit.**
Commit: `feat: route final turns into trusted task path`

### Task 3: On-device router adapter

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/planner/OnDeviceTurnRouterCall.kt`
- Create: `android/app/src/main/java/dev/lain/os/planner/QwenTurnRouterPrompt.kt`
- Create: `android/app/src/test/java/dev/lain/os/planner/OnDeviceTurnRouterCallTest.kt`
- Modify: `lain/app/planner_bridge.py`

**Interfaces:**
- Consumes selected on-device PlannerBinding and final user turn.
- Produces bounded route JSON only; cannot return ACTION_PROTOCOL actions.
- Uses existing native GGUF lease/cancellation model rather than a second inference engine.

- [ ] **Step 1: Add RED tests** for CHAT/TASK/CLARIFY output and rejection of action/approval fields.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement a minimal Qwen route prompt** with recent bounded conversational context but no executor authority.
- [ ] **Step 4: Map cancellation/model busy/load failures to CLARIFY/unavailable rather than execution.**
- [ ] **Step 5: Run tests.**
- [ ] **Step 6: Commit.**
Commit: `feat: classify local qwen turns safely`

### Task 4: Unify typed and speech entry in Workbench

**Files:**
- Modify: `android/app/src/main/java/dev/lain/os/ui/WorkbenchViewModel.kt`
- Modify: `android/app/src/main/java/dev/lain/os/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/androidTest/java/dev/lain/os/voice/ConversationalRevisionAndroidTest.kt`
- Create: `android/app/src/androidTest/java/dev/lain/os/UniversalCommandBridgeAndroidTest.kt`

**Interfaces:**
- `run(text)` and `submitSpeech(text)` submit the same final-turn protocol when no active task exists.
- Active task continues to route final turns as revisions.
- UI renders conversation response separately from task execution/result state.

- [ ] **Step 1: Add RED instrumentation** for typed “Show battery” and final speech “Show battery” producing equivalent task route.
- [ ] **Step 2: Add RED instrumentation** for “What can you do?” staying conversational and for ambiguous side-effecting text requiring clarification.
- [ ] **Step 3: Refactor ViewModel entry** so typed/speech final turns share one method while preserving source identity.
- [ ] **Step 4: Render CHAT/CLARIFY response without creating fake session state.**
- [ ] **Step 5: Run voice/transcript/revision instrumentation.**
- [ ] **Step 6: Commit.**
Commit: `feat: unify voice and typed command routing`

### Task 5: Command bridge acceptance

**Files:**
- Modify: `docs/ANDROID_DEVICE_ACCEPTANCE.md`
- Create: `android/app/src/androidTest/java/dev/lain/os/UniversalCommandAcceptanceTest.kt`

**Interfaces:**
- Consumes prior tasks.
- Produces exact acceptance evidence for typed/speech equivalence and trust boundaries.

- [ ] **Step 1: Gate typed and speech “Show battery” through the same selected real planner/task path.**
- [ ] **Step 2: Gate CHAT and CLARIFY negatives, low-confidence speech, partial speech, duplicate turn, active revision, Stop talking vs Stop task, unknown capability planner output, and permission-unavailable capability behavior.**
- [ ] **Step 3: Run canonical Python verification, Android unit/lint/assemble, API 24/API 35 instrumentation.**
- [ ] **Step 4: Add physical-device checklist with exact APK/model SHA, airplane/offline behavior for on-device mode, voice command, task result, verification, audit, and Stop.**
- [ ] **Step 5: Commit.**
Commit: `test: gate universal command bridge`
