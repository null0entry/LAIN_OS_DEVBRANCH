# LAIN_OS 1.0 Golden Workflow: screenshot → crop → group send

**Status:** owner-directed acceptance specification; no implementation or physical pass evidence yet.
**Date:** 2026-10-16
**Canonical parent:** [API-first three-phase design](2026-10-16-api-first-three-phase-design.md)
**Benchmark command:** "Take a screenshot, crop it, then send it as a group text, all in one go."

## 1. What constitutes success

From one final voice transcript or one typed message, LAIN compiles **one durable, bounded, dependency-aware plan**. It must capture an authorized device screen, crop a region selected by explicit owner instruction or clarified intent, persist a safe image artifact, resolve a specific messaging group, obtain all required consent and exact outbound approval, transmit that **single image to the actual group** through an available authorized transport, then report observed submission and, if independently available, delivery status.

**Do not call the benchmark passed** merely because the screenshot was captured, a file saved, a messaging app opened, a share chooser displayed, the recipients were populated, or the owner still needs to press Send. That is an **assisted composition** or **blocked** outcome, not an autonomously sent group message. One batch of unrelated single-recipient SMS texts is not one group message.

**One go** means no extra natural-language prompting or manual copy/crop/attach/navigation between authorized steps. It does **not** mean bypassing Android system consent, the messaging app's role requirements, or a clear owner confirmation for transmission. If owner action is mandatory and no lawful automated transport is available, pause and label the exact blocker instead of fabricating success.

## 2. Required planner capabilities

Represent this as a versioned **workflow graph**, not one opaque shell command or a flat series of unverified model instructions:

```text
request R-001
  [resolve.intent + recipient group + crop bounds]
           |
  [capture.screen] ---- requires OS screen-capture consent / permitted capture
           |
  [image.crop] ------ requires captured artifact handle
           |
  [image.validate] -- crop bounds, bytes, format, privacy redaction
           |
  [group.resolve] --- selected app/transport and exact group ID/recipients
           |
  [policy.approve] -- screenshot scope, thumbnail preview, recipients, send
           |
  [message.group.send] -- ONLY through an explicitly authorized transport
           |
  [message.verify] --- provider/carrier accepted/failed/unknown, delivery if available
           |
  [assistant.reply] --- model summarizes trusted real evidence
```

Each node has `step_id, action_id, dependency_ids, capability_id, validated_args, required_grants, sensitivity, risk, artifact_in, artifact_out, status, verification, attempt_policy, timeout, cancellation_token, audit_ref`. Overall workflow has `workflow_id, turn_id, source_device, selected_model, task_budget, max_steps, plan_revision, current_step, execution_lease, checkpoints, last_trusted_effect`.

The planner may **replan on new trusted evidence** (e.g. crop target offscreen or no group resolver) while preserving completed step records and provenance. It may not silently substitute a different recipient, messaging protocol, unapproved outgoing image, or a series of individual sends. Clarify ambiguous crop and group targets *before* outbound approval. At most one active execution lease per workflow. No duplicate sending after timeout, OS re-creation, network retry or model retry.

## 3. Tool contracts and boundaries

| Tool | Input → trusted output | Gate / expected failure |
| --- | --- | --- |
| `screen.capture` | `{target:"current_display"}` → `CapturedImageRef` | User-approved Android MediaProjection session; foreground-service constraints; content protected by `FLAG_SECURE` may not be accessible. Android 14+ target apps request consent for each session, no token reuse. Optional alternate screenshot path is **not** a stealth consent bypass. |
| `image.crop` | `{image_ref, rectangle:{x,y,width,height}}` → `CroppedImageRef` | Clamp bounds to actual pixels, preserve orientation, reject zero-sized/off-image crops; never model-provide raw filesystem paths. |
| `image.inspect` / `image.redact` | image reference → bounded preview / approved redacted artifact | Input may contain sensitive messages, tokens, identity information. Keep image on device by default; never upload it to model automatically for crop inference. |
| `contacts.group.resolve` | owner group name or explicit recipients → canonical `GroupTarget` | Permission/role and adapter-specific APIs; collisions require clarification; confirm exact recipient set, transport and group thread identifier. |
| `message.group.compose` | approved image URI + recipients + caption → external compose intent | Documented Android `ACTION_SEND` / `ACTION_SENDTO` with a scoped URI grant. **LIMITED: composing is not sending**. |
| `message.group.send` | artifact + canonical group target + exact approval reference → `SendAttemptRef` | Available **only** through a demonstrably authorized adapter supporting real group image messaging (e.g. vetted messaging provider SDK or explicitly opted-in SMS/MMS default-app integration that supports the target group semantics). No generic third-party RCS/send authority assumed. |
| `message.group.verify` | `SendAttemptRef` → status/provenance | Distinguish REQUESTED, COMPOSED, SUBMITTED, PROVIDER_ACCEPTED, SENT, DELIVERED, FAILED and UNKNOWN. A submission callback is **not** recipient delivery. |

**Proposed type boundaries:**
- `CapturedImageRef` is an opaque app-private content reference plus content SHA-256, screen/capture dimensions, orientation, media type, timestamp and ownership.
- `CroppedImageRef` carries parent reference, normalized crop rectangle, resulting dimensions/format/hash and an expiration/retention policy.
- `GroupTarget` is an opaque transport-specific conversation/group identifier plus an owner-reviewable recipient summary. Do not treat raw model-named people as a verified destination.
- `SendAttemptRef` carries trusted provider transport, message ID, idempotency token (where available), accepted timestamp and restricted status transitions. No phone numbers or image contents in ordinary logs.
- External recipient selection, screen capture and sending are **consequential** operations requiring owner-visible consent/authorization. Permission dialogs are Android-owned; the model cannot grant consent on the owner's behalf.

## 4. Android and transport feasibility decisions

**Capture:** use a user-authorized MediaProjection session and handle token/session lifecycle correctly. Android 14 targets require new consent for each projection capture session. Android 15 can visibly signal projection and stops projection when locked. No root/shizuku/Accessibility workaround to bypass OS or protected-content restrictions.

**Crop:** native Android bitmap processing; no model or cloud dependency for pixel editing. Ambiguity can trigger model clarification or an owner crop selection UI. Result should be validated and sharable by a narrow `content://` URI grant, not a world-readable path.

**Messaging:** Android's common intent APIs permit opening an MMS composer with an attached image; they do **not** guarantee hands-free transmission or a stable interoperable group thread. Direct group multimedia sending depends on actual installed/app-owned transport privileges and carrier/platform implementation. `SmsManager.sendMultimediaMessage` exists but is **not**, by itself, evidence of complete group MMS address/thread interoperability on the owner's device. Test actual device roles, messaging provider and group behavior. For RCS, do not assume a general public programmatic group-send API. If only compose is possible on the Moto, show the composer and **fail the fully sent release gate**, then choose a supported transport or adjust product scope explicitly with the owner.

**Security/privacy:** require the user to initiate capture; show a preview and exact recipient approval for high-risk outgoing image content. One task-level approval may authorize the exact group/image pair. Never reuse approval for changed crops/recipients. Do not send captured screen pixels to a remote model without a separate explicit disclosure and approval. Do not acquire special app roles covertly.

Official Android references:
- MediaProjection / Android 14 behavior: https://developer.android.com/about/versions/14/behavior-changes-14
- MMS compose intents and attachments: https://developer.android.com/guide/components/intents-common
- MMS direct transport API: https://developer.android.com/reference/android/telephony/SmsManager
- Android 15 projection visibility and stop: https://developer.android.com/about/versions/15/behavior-changes-all

## 5. Execution and recovery contract

Progresses through `CREATED → INTERPRETING → PLAN_VALIDATED → AWAITING_OS_CONSENT → CAPTURING → CROPPING → TARGET_RESOLUTION → AWAITING_SEND_APPROVAL → SENDING → VERIFYING → [COMPLETE|BLOCKED|FAILED|UNKNOWN]`.

- Persist a canonical checkpoint between every effect. A failed crop may re-run only from the already owned captured image, not silently start a new projection.
- Capture consent rejection ⇒ BLOCKED with no image and no communication.
- Invalid/ambiguous crop ⇒ clarification, not arbitrary pixel selection.
- Group cannot be resolved ⇒ BLOCKED before send.
- Sending capability only supports compose ⇒ ASSISTED_COMPOSE, not COMPLETE.
- Loss of network/carrier acknowledgment after send attempt ⇒ UNKNOWN, no automatic resend.
- After user cancels before transmission ⇒ CANCELLED, and cleans temporary scoped sharing grants.
- Replay or app crash after send ⇒ compare trusted provider message ID / persisted send marker before doing anything. Never resubmit without explicit reconciliation.
- The assistant's final message is synthesized by the API model from **read-only** structured receipts; model text cannot claim `SENT` without trusted evidence.

## 6. Automated + physical acceptance tests

**Automated fixtures (not release proof):**
1. One voice/text request produces graph with proper dependency edges, typed artifact references and exactly one outbound send proposal.
2. Planner never passes an untrusted path, undocumented permission or unauthorized recipient to the executor.
3. Crop validates orientation, image dimensions, actual output MIME and SHA, image redaction and temporary URI grant.
4. False/denied/expired capture consent blocks before any screenshot; no token reuse.
5. Model, STT, binder, process, carrier, network or timeout failure cannot duplicate send; safe replan only.
6. `COMPOSED` and `PROVIDER_ACCEPTED` cannot be promoted to `SENT` or `DELIVERED` without appropriate evidence.
7. Multiple candidates for a group ⇒ ask owner to clarify; changed recipient set invalidates exact send approval.
8. Inject malicious text into image OCR/recipient labels ⇒ model cannot gain execution, recipient or approval authority.

**Moto G Play physical release gate:**
1. Install exact source-bound APK. Record build commit and APK SHA, OS/API, selected hosted API/STT services and SMS/MMS/group transport role.
2. With a seeded known group and no sensitive content, speak the complete benchmark once.
3. Observe real Android capture consent, actual captured image, crop coordinates and byte hash, approved exact group destination, single actual group-send attempt, and trustworthy sent/provider status.
4. Repeat with **no** intermediary manual capture/crop/attach or app navigation. Android-owned capture consent and exact send authorization are permitted, but any required manual Send tap means the hands-free send gate is NOT MET.
5. Repeat with typed parity, permission denial, app backgrounding, group-lookup ambiguity, connectivity loss, canceled send and process death. Assert zero duplicate sends.
6. Reviewer independently inspects physical artifact result and group message on receiving side when possible. Keep message delivery status separate from local send evidence.
7. A green emulator suite, share chooser, simulated transport or model-authored success statement is insufficient.

**Mandatory pass:** the image really reaches one group conversation through an authorized transport with a real send result and there is exactly one active workflow record. If not achievable under available Android roles/integrations, flag **BLOCKED: GROUP_SEND_AUTHORITY** as an explicit release blocker, not a success.

## 7. Integration order

- **Phase 1:** register screen capture, crop, image artifact, group target, compose/send/verify schemas, Android role/permission/consent probes, URI/credential boundary and provider feasibility spike. Implement actual image transforms as a bounded native tool. Do not advertise unsupported direct-send transport.
- **Phase 2:** build dependency-aware orchestrator with artifacts, checkpoints, replan/Stop/approval. Prove component composition and verified send against a real authorized provider.
- **Phase 3:** expose one voice/text instruction through the one conversation composer, with progress, approval, real receipts and final API response. Pass Moto physical benchmark.
- **Broader coverage:** see [50-task capability catalog](../../FEATURE_MATRIX_1_0.md); only named proven features are supported, regardless of marketing ambition.

**Design only.** No code was changed or installed APK tested by this document.
