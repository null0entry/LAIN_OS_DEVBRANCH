# LAIN_OS: single conversation interface and guaranteed response attempt

Status: Proposed design revision, owner review required before implementation
Date: 2026-10-16
Scope: Android UI, conversation routing, model response lifecycle, canonical transcript
Priority: P0, before expanding general-purpose device controls
Supersedes: TASK-081's proposed *two views of one conversation* as a product UX. TASK-081's canonical storage requirements still apply.

## 1. Owner intent and acceptance

The current Android application is not working reliably for the owner. Separate Workbench and Local chat screens make one assistant feel like two broken applications. The owner requires **ONE visible conversation screen and ONE text composer for all messages**. Entering "Show Demo Toast" must use the same Send action as entering "How are you?" Every submitted message must lead to an attempted answer from the selected model, including commands. No separate chat window, special command input mode, or separate command Run button.

A **working result** means the installed APK demonstrates this behavior on the owner's actual Android handset, not merely in mock, unit tests, Linux or emulator CI.

This document specifies the target design. It does **not** claim existing code or the owner's current APK implements it.

## 2. Product decision

**Chosen: one chronological conversation timeline backed by a single trusted turn manager and one model provider selection.** Deterministic command recognition remains underneath the conversation interface, not in a second UI.

Alternatives rejected:
- Two screens sharing a model or database: user still has to select between chat and work; violates one-box requirement.
- Send every message only to a model and let its text drive Android: unsafe and unreliable; text is not proof of execution.
- Treat commands as executor-only messages without a model reply: violates the explicit every-turn response requirement.

A configured, healthy model **MUST** generate a conversational response for **every accepted final user turn**, whether routed to chat or to a device capability. System status cards are not substitutes for that reply. If inference cannot finish, the UI **MUST** surface a truthful failure, preserve the user turn and tool evidence, and offer **Retry reply** without re-executing the device action. No software can promise successful model generation when the model is missing, out of memory, broken or timed out.

## 3. Single-screen Android layout

Illustrative portrait layout:

    +---------------------------------------+
    | LAIN       Local: Ready       Settings |
    +---------------------------------------+
    | YOU  Show Demo Toast                  |
    | TOOL android.toast                    |
    |      Accepted / verification LIMITED  |
    | LAIN I requested the demo toast.      |
    |      Android accepted the request,    |
    |      but visibility is unverified.    |
    |                                       |
    | YOU  Why is the sky blue?             |
    | LAIN Because sunlight scatters ...   |
    |                                       |
    | [task / model progress or error]      |
    +---------------------------------------+
    | [Mic]  Message Lain...       [Send]    |
    +---------------------------------------+

Requirements:
1. **One main Activity and one timeline**. Existing LocalChatActivity is removed or becomes a migration/redirect shim, never an owner-facing second chat. The existing Workbench task box and Run Task control are removed from the primary surface.
2. **One multiline text composer** with Send, optional microphone entry, and keyboard submit behavior. Examples, presets, and demo commands, if retained, insert text into this same composer or submit through its exact handler. They must not open a secondary execution screen.
3. **One scrollable, persistent conversation** with user bubbles, assistant responses, inline trusted tool receipt cards, permissions/confirmations, loading and explicit errors. Conversation history remains available after rotation and process restart.
4. **One visible selected-provider state** such as Local: Ready, Loading, Failed or Cloud: Ready. Model choice, import/download, permissions, privacy and advanced diagnostics live in Settings/onboarding, not in a separate chat screen.
5. A single voice input affordance writes **only final trusted transcripts** to the same composer/submit path. Partial STT is provisional; voice errors are visible and typed input is always available.
6. Distinguish **Stop reply**, **Stop speaking**, **Stop task** and **Stop listening** by scope and status. Do not make a single ambiguous Stop that cancels unrelated work.
7. Accessible portrait layout: IME resize, rotation restoration, focus, readable contrast, large touch targets, text scaling, loading affordances, and stable scrolling. Show approvals as inline trusted controls, not assistant text.
8. No chat/workbench tab, toggle, second transcript, or other navigation mode is permitted in the target UX.

## 4. One authoritative conversation contract

    final typed or spoken user message
      -> persist exactly one user turn (stable turn_id)
      -> trusted route selection:
           * exact built-in demo / typed command
           * bounded model-proposed capability request
           * ordinary conversation
      -> if action: validate + policy + exact approval if necessary
      -> if action: trusted executor + independent verification + audit
      -> persist trusted structured tool outcome (even on denial/failure)
      -> invoke selected model for a natural-language reply
           using bounded conversation and read-only trusted outcome context
      -> persist one linked assistant response or explicit reply failure
      -> render same conversation timeline

- The **canonical ConversationTurnManager / AppController** owns transcript identity and assistant-response persistence. Android UI observes that authority rather than maintaining parallel histories. Preserve bounded prompt context, privacy filters and provider independence.
- Built-in demo commands such as **Show Demo Toast** retain exact, deterministic parsing so the model cannot derail or veto them. They may execute without invoking a model **for planning**, but **never bypass the model's conversational reply attempt**.
- Other messages may use the model for classification/proposals; proposals are always untrusted and require typed capability/schema checks before execution. No assistant wording is an action/approval instruction.
- For an action turn, invoke the conversational model **after** terminal verified/limited/failed/denied status, so it can report reality accurately. For approval-waiting cases, an interim model response may ask for user approval; after the owner acts, produce an outcome reply linked to the same turn without replaying the action.
- A normal chat message invokes no device executor.
- Completion facts must be supplied to the model as structured, provenance-labeled, read-only observations. Model replies must not upgrade **accepted/LIMITED** into **verified PASSED**. The source-of-truth receipt remains independently visible.
- The selected model remains an **untrusted speaker/planner**; authoritative permission, tool execution, audit and verification stay below it.
- Use one inference service and one ordered model-generation lane; model selection changes do not create a second transcript.

## 5. State machine and failure handling

**Turn stages:** ACCEPTED -> ROUTING -> [AWAITING_APPROVAL | EXECUTING | NO_ACTION] -> OUTCOME_RECORDED -> REPLY_PENDING -> [REPLY_COMPLETE | REPLY_FAILED | REPLY_CANCELLED].

**Model readiness:**
- After first-run setup, the configured on-device model may auto-preload when permitted, with visible progress and low-memory diagnostics; do not silently select cloud.
- If model is not configured or cannot load, show **Model unavailable** explicitly in the **same** timeline/composer, with Setup/Retry model actions. Keep deterministic demos available if possible, but never imply that their system receipt is a model response.
- Accepted user turns are durable even if model inference fails. **Retry reply** resumes response generation using the already recorded outcome; it never resubmits the Android action.
- Timeout, model cancellation, OOM, STT failure, lost Binder, process death, permission denial and unsupported device API each have distinct owner-visible terminal states, no infinite Planning spinner, and no fabricated success.
- Serialise authoritative turn submission, with a bounded busy indicator. Preserve typed draft while processing. No accidental duplicate turn on repeated Send, Activity recreation, speech final callback or retry.
- An uncertain in-flight side effect is never automatically retried. Reconcile separately; reply must describe uncertainty honestly.
- Existing Local chat private history must be migrated once with stable IDs or presented via explicit read-only export/recovery. No silent deletion, double import or parallel future writes.

## 6. Interaction examples

### A. Built-in command

Owner enters **Show Demo Toast** and taps Send.

1. One user bubble appears and persists.
2. Trusted deterministic parser identifies android.toast; policy and executor operate.
3. An inline receipt appears, e.g. **Toast requested; Android accepted; LIMITED verification**, or an exact failure/unsupported result.
4. The selected model receives that trusted result and generates an assistant bubble explaining what happened without claiming it appeared unless independently verified.

### B. Ordinary conversation

Owner enters **Hey Lain, how are you?**

1. One user bubble appears.
2. No tool invocation.
3. Selected model generates a real conversational reply in the same timeline with bounded prior context.

### C. Consequential device command

Owner enters **Text +15551234567 saying hello**.

1. Capability is validated; approval UI is inline and unforgeable.
2. After exact owner approval, the SMS composer may open. No automatic SMS send.
3. The receipt states **SMS composer opened** / LIMITED, never **message sent**.
4. The model explains the remaining tap-to-send step. If approval is denied, the model can say no text was sent.

### D. Model failure after side effect

Owner enters **Vibrate briefly**. Android accepts the vibration, but local Qwen times out.

1. The trusted receipt remains **Vibration requested / LIMITED**.
2. The reply bubble shows **Model reply failed (timeout)**, not a fake model message.
3. **Retry reply** retries only model generation; it never vibrates again.

## 7. Existing seams to consolidate

- **MainActivity + WorkbenchViewModel**: evolve into the sole conversation Activity/ViewModel and composer/event stream. Remove user-facing navigation to Local chat.
- **LocalChatActivity** and its independent **native-gguf-chat-v1** SharedPreferences and QwenChatPrompt direct path: migrate or retire safely; do not maintain two assistant histories or distinct Send handlers.
- **ConversationTurnManager / AppController.complete_chat**: make final-turn acceptance, turn IDs, assistant text persistence and tool receipt linkage authoritative for all user interactions.
- **Native GGUF resident service / cloud model adapters**: reuse the single selected model transport for every response, including actions. No local-to-cloud implicit fallback.
- **NativeCapabilities / RuntimeEngine**: keep narrow validated capability execution separate from model conversation, with the existing audit/verification/policy contract.
- **VoiceCaptureViewModel**: deliver final speech as the same message submission path, not a second route.

Files/components above are known existing integration points, not a claim that all are functioning on the owner's current device.

## 8. Non-goals

This redesign does not add unrestricted Android control, hidden Accessibility access, arbitrary shell, raw authenticated HTTP, always-listening microphone behavior, autonomous sending of messages, new provider integrations, or the 1.0 video/publishing workflow. TASK-082 continuous wake-listening and TASK-075 physical device acceptance remain separate. Fix the one-box conversation path before further capability expansion.

## 9. Verification and release gate

**Automated evidence (required, not sufficient):**
1. Android UI instrumentation asserts **exactly one** owner-facing composer and conversation destination, no visible Local chat tab/window or Workbench Run Task flow. Verify keyboard, rotation and accessibility.
2. Deterministic test: submit **Show Demo Toast** -> one durable user turn -> one trusted toast receipt -> **one model generation invocation** -> model assistant reply linked to that turn.
3. Plain-language chat uses the exact same Send pathway and model selection, without executor invocation.
4. Approval-required and denial flows produce legitimate model replies plus protected inline controls, with no invented approval or send status.
5. Model-not-ready, timeout, crash, OOM and canceled-generation tests show truthful failures; Retry reply never re-executes commands. No endless spinner.
6. Legacy migration is idempotent; conversation survives restart, upgrades, tab removal and multiple messages without duplication.
7. Speech final transcriptions behave exactly like typed sends; no partial or echoed speech can authorize actions.
8. Preserve risk policy, prompt injection resistance, read-only outcome context, redaction, API 24/35 coverage, and local-only no-fallback invariant.

**Physical Moto G Play acceptance (mandatory):**
- Install exact APK tied to a green build SHA. Provision/select/start actual Qwen or a chosen working provider; do not treat simulated responses as model replies.
- Perform in a single screen, in sequence: **Show Demo Toast**, **Show battery**, **Vibrate briefly**, **How are you?**, **Show Demo Toast** again. Observe one user message, one truthful result if applicable, and one real model answer for **every** submission; no user navigation to a second screen.
- Repeat with typed input and available real voice transcription. Record STT provider/error separately if device lacks one.
- Exercise deny, unsupported, timeout and model-stop cases. Confirm visible error instead of fabricated reply, durable turn recovery, no unintended repeat execution, and one-tap safe recovery.
- Record APK SHA, app build, handset model/API, selected model/provider, success/failure counts, latency and device diagnostics. CI green without these observations **does not** close this gate.

## 10. Delivery boundary

The next implementation should prioritize eliminating the duplicate UI + histories and proving the five-turn Moto acceptance sequence above **before** broadening device tools. A design PR may merge after owner approval; implementation and APK acceptance are separate future work. Until physical tests pass, do not describe this revision as already shipped or working.
