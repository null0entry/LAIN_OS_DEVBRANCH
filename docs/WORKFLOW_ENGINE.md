# LAIN_OS Reusable Tool and Workflow Framework

**Status:** Proposed v2 implementation architecture, owner review required. **No shipped capability claimed.**
**Date:** 2026-10-16
**Related:** [API-first 3-phase architecture](superpowers/specs/2026-10-16-api-first-three-phase-design.md), [50 workflow examples](FEATURE_MATRIX_1_0.md), [screenshot→crop→group-send acceptance](superpowers/specs/2026-10-16-screen-crop-group-send-golden-workflow.md)

## The answer: not one implementation per command

**Build each fundamental authorized Android/service integration once; compose it arbitrarily many ways.** Commands like "screenshot and text my friends" are **not** individually coded; they are model-built plans over a discoverable, typed capability registry.

- **One-time adapters that require implementation:** MediaProjection screenshot (consent), native image crop, scoped file write, Android brightness where permitted, contact/group resolution, message sending via a legitimate transport, GitHub integration, etc.
- **Reusable primitives:** generic argument validators, capability manifest loader, permission resolver, artifact store, lifecycle engine, audit, action approval, cancellation, graph scheduler, receipt verifier, cloud model protocol. These are generic code, shared by all adapters.
- **Generated at runtime, not separately coded:** new user tasks that combine exposed adapters, including branching, clarification, revision, recovery and long sequences.
- **Not automatic:** access to an unimplemented API, privileged Android system action, installed app's inaccessible private controls, or another vendor's undocumented messaging transport. Unknown or denied functions are UNSUPPORTED.

## Control-plane layering

```text
VOICE / TEXT (one input)
      ↓
Finalized STT or typed turn + durable ID
      ↓
API MODEL (interprets goal, proposes structured tool plan)
      ↓
TRUSTED PLANNER COMPILER
  normalize → validate available schemas → resolve typed dependencies
  assess risk/privacy/permissions → budget and feasibility → persistent DAG
      ↓
WORKFLOW EXECUTION ENGINE (trusted)
  pick ready step → preflight grants → owner consent
  → adapter execute → independent receipt + verify → checkpoint
  → feed typed output handles to newly ready steps
      ↘ blocked/uncertain? clarify, safe replan or stop
      ↓
MODEL API (explain verified outcome; no authority to mutate receipts)
      ↓
ONE CANONICAL CONVERSATION
```

## Capability packages and registry

Use versioned, separately tested plugins/adapters behind one local interface:

```python
# Interface pseudocode (not runnable implementation).
class CapabilityAdapter:
    manifest: CapabilityManifest

    async def check_availability(self, device, grants) -> Availability: ...
    async def execute(self, args: ValidatedArguments, ctx: TrustedContext) -> ActionReceipt: ...
    async def verify(self, receipt: ActionReceipt, ctx: TrustedContext) -> Verification: ...
    async def reconcile(self, attempt_id: str, ctx: TrustedContext) -> Reconciliation: ...
```

Each `CapabilityManifest` declares stable name+version, user description, typed input and output schemas, *real implementation entrypoint*, SDK/feature/package requirements, app-role/special-access/dangerous permissions, required foreground/user gesture, security and irreversible-effects category, approval scope, private data disclosure, timeout, cancellation semantics, retry/idempotency class, readback confidence, and unsupported reasons.

A tool becomes visible to the model only if **implemented and actually available now**. Catalog entries may be DESIGN or UNSUPPORTED but must not be exposed as executable tools. Dynamically re-evaluate availability before each use, since grants/restrictions can change during a workflow.

**No arbitrary model-authored shell code, raw Android intents with free-form extras, unchecked URLs, arbitrary Accessibility gestures, filesystem escape or implicit permission escalation.** A future extension system may load trusted, signed user-installed adapters with explicit new scopes, not grant the language model execution privileges.

## Typed artifact and dataflow contracts

Every step input references an immutable typed output from a previous trusted step:

```json
{
  "workflow_id": "trusted-generated-workflow",
  "steps": [
    {"id":"capture","capability_id":"screen.capture","args":{"display":"current"},"depends_on":[]},
    {"id":"crop","capability_id":"image.crop","args":{"source":{"$artifact":"capture.image"},"rectangle":{"x":0,"y":0,"width":500,"height":400}},"depends_on":["capture"]},
    {"id":"send","capability_id":"message.group.send","args":{"image":{"$artifact":"crop.image"},"group":{"$artifact":"resolve.group"}},"depends_on":["crop","resolve"]}
  ]
}
```

The JSON above is a **schematic**, not a runnable or sufficient plan: it omits the separate group-resolve node, policy approval and verification steps for brevity. A real validated graph must define every referenced producer and cannot use arbitrary `$artifact` values outside an allowlisted resolver. The trusted engine assigns IDs; never trust model-generated IDs or provenance.

Opaque `ArtifactRef` includes type, originating action+step ID, content hash, size/shape, source/retention/privacy labels, and scoped access token. Model cannot invent or override a ref. Engine validates dataflow types and ordering before executing anything.

## Scheduler, policy and safe replan

1. Persist goal + user turn; ask the hosted model to propose a bounded graph using *only current capability contracts*.
2. Compile/validate dependency ordering, argument schemas, immutable typed artifact wiring, step count, depth, token/time/cost budgets, policy and consent needs. Cycles, unavailable tools and unsafe targets fail closed.
3. Persist graph revision and await any OS permissions / owner approvals in user-visible UI. A final user voice transcript is never itself an approval to send a sensitive artifact unless explicit exact-action approval policy is satisfied.
4. Acquire an exclusive workflow lease. Run ready steps in safe order, check availability again, and persist results atomically before downstream steps begin.
5. For each output, distinguish `ACCEPTED`, `PASSED`, `LIMITED`, `FAILED`, `DENIED`, `UNSUPPORTED`, `UNCERTAIN`. Step outputs cannot be used as VERIFIED if the tool contract requires stronger evidence than obtained.
6. On failure, record checkpoint; ask model for a bounded **revised future graph** using read-only trusted receipts. Never replay confirmed or uncertain external sends; reconcile/seek consent first. Changing image, recipients or messaging transport invalidates prior send approval.
7. Compose one truthful model response from trusted status, persist it in the single conversation. Retry reply never reruns side effects.

**Critical invariant:** execution authority belongs to the registry, policy and executor, not to the model's ability to generate text or JSON.

## Extension process (one new tool vs infinite new combinations)

Example: adding `image.crop` *once*:

1. Write the schema/manifest for `source: ImageArtifactRef`, `rectangle`, output `CroppedImageArtifactRef`, image-format, aspect/bounds and max resource use.
2. Implement native bitmap crop using authorized app-owned image bytes.
3. Implement verification that output dimensions, SHA and file exist; attach scoped artifact ref.
4. Add permission/availability checks (crop itself needs no new Android platform grant, but input capture needs owner screen authorization).
5. Add unit/instrumentation/real-device tests. Only then expose the capability as `AVAILABLE` in the model catalog.

Afterward a planner may reuse it in image-to-email, image-to-MMS, bug report, redacted sharing, gallery conversion and other user goals **without coding separate command scripts**.

## Golden benchmark and realistic boundary

For "take a screenshot, crop it and group text it," a complete plan must:
- obtain MediaProjection consent and actual screenshot;
- crop on device, hash output and show preview;
- resolve the exact existing group and approved sending app/transport;
- get exact owner approval to transmit this image to those recipients;
- send **one actual group image message** through an authorized adapter and verify trustworthy submission/transport status;
- explain what happened in the conversation.

A chooser / open composer is a legitimate **ASSISTED** fallback but **NOT** a pass for the automated send benchmark. Android roles, privacy restrictions, RCS/MMS APIs and carrier support may block direct group send. The framework reports `BLOCKED`, it never bypasses platform rules. In particular, no generic public app can guarantee arbitrary group-RCS send for all third-party messaging apps.

## Delivery and verification gates

| Phase | Minimum demonstrable platform outcome |
| --- | --- |
| 1: scaffolding | Versioned registry and real supported adapters, grant/role/availability broker, artifacts and durable events; bounded typed crop tested with real captured input. |
| 2: planner | Model-generated multi-step typed graph, validated dependencies, orchestration, checkpoints, authorization, retry/reconcile and truthful outcomes, plus an actual authorized group-send feasibility proof. |
| 3: chat | One voice/text composer and transcript, visible node progress/approvals, correct shared context, real hosted-model assistant replies and **physical** screenshot→crop→group-send benchmark. |

Record exact source commit, APK SHA, phone/API, permissions/roles, provider, tool IDs and independently observable outcomes. Mocked send / synthetic screenshots / successful emulator alone cannot close a physical release gate.

**Design/approval state:** no change to Android implementation, no real phone benchmark passed by this document.
