# LAIN_OS — Embedded Offline GGUF Planner (Android) Design

Date: 2026-10-08  
Status: **design for owner review; no runtime implementation or physical acceptance claimed**  
Owner request: [canonical queue handoff](https://github.com/null0entry/LAIN_OS_DEVBRANCH/issues/6#issuecomment-6066165567)

## Intent and acceptance

Make LAIN execute an **actual model-generated planning decision entirely on the installed Android device**, without a LAN server, HTTP request, cloud account, or cloud fallback. After explicitly importing a supported GGUF model, the owner must be able to enable airplane mode, start a **new** on-device planner session, type **Show battery**, and observe a truthful terminal result with trusted local action, verification, and audit. A canned demo, fake runner, model-selection UI alone, successful token sample outside LAIN, or a LAN endpoint does not satisfy this.

This is an opt-in **next development build** target, not a claim about the TASK-055 APK. This feature advances work previously deferred to the post-1.0 roadmap (§18, 1.1). Keep the release baseline and canonical TASK-069 queue intact until the owner reviews an explicit priority change. In-flight voice PR #39 must not be mixed into this branch.

## Existing seams and non-negotiable boundaries

- `PlannerProfile.kt`, `PlannerProfileStore.kt`, and `PlannerSettingsManager.kt` presently recognize Demo, Cloud, and Local; both non-demo modes carry HTTPS endpoints. `NativePlannerTransport.kt` is exclusively an HTTP transport.
- `lain/agent/models.py::PlannerBinding` and `lain/app/planner_bridge.py` validate a strict session-pinned transport contract; `RuntimeService.kt` embeds the existing trusted Python controller.
- **Add a fourth distinct mode `on_device` with protocol `gguf_native_v1`**; do not recycle the current `local` HTTPS mode or invent a localhost server. The new mode has no endpoint, no credential reference, no implicit downloads, and no network fallback.
- The inference result is **untrusted planning data** and goes through the *existing* `agent_planner_request` / planner-decision validation → capability policy → exact approval → executor → verifier → audit. Native model code has no direct capability, approval, file mutation, or task-completion authority.
- Active sessions pin exact profile ID, model ID, SHA-256 and bounded context/generation settings. Changing the selected model cannot redirect a live session.

## Recommended architecture

```text
Android Planner settings / app-private GGUF import
    ↓ validated model inventory {id, sha256, size, format, compatibility}
OnDevicePlanner profile (new explicit type, no endpoint/credential)
    ↓ session-pinned native request from existing Python planner bridge
NativeModelRunner in the existing app-owned runtime process
    ↓ JNI, pinned llama.cpp source, CPU-only initial backend
Bounded model-generated text/structured JSON
    ↓ SAME trusted planner parser, policy, approvals, executor, verification, audit
```

Use the upstream [llama.cpp Android example and build instructions](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) as the initial JNI reference; pin and review a specific source revision, license, build flags, ABI and dependency hashes. **Do not vendor an opaque prebuilt binary or pull an unpinned native artifact during CI.** Any build/NDK/API compatibility gap must be established with an actual cross-build before choosing the final packaging mechanism.

The initial device target is an arm64 Galaxy-class phone. Keep the app's existing `minSdk=24` and declared `arm64-v8a` + `x86_64` packaging contract intact; unsupported native backends/ABIs must report **unsupported** instead of making the whole APK incompatible, and the supported subset must be disclosed. Start with a user-supplied small *instruction-tuned* quantized GGUF (roughly 0.5B–1.5B parameters; verify actual model license, template, size and device fit before choosing a default), CPU-only, single loaded model, conservative bounded context and output. No GPU, model marketplace, model download manager, or multiple concurrent models in this slice.

### Model storage and admission

1. Import through Android's user-visible document picker; **stream** bytes into app-private staging without buffering whole models or exposing arbitrary filesystem paths to the planner.
2. Enforce maximum file bytes, available-storage floor, GGUF header/metadata/version compatibility, path confinement and atomic commit. Record content SHA-256, actual size, model architecture and import provenance. A self-computed checksum detects later corruption; it does **not** prove upstream authenticity without comparison to a trusted expected hash.
3. Reject truncated, oversized, hostile and incompatible models. Keep prior active model usable after failed import; remove only safe unreferenced staging. No credentials or private documents enter model metadata.
4. Perform conservative available-memory checks before loading; limit threads, context tokens, generated tokens and duration. Stop/unload and return a specific error on model-not-installed, incompatible ABI, bad template, insufficient memory, load failure, timeout, cancellation, resource pressure or invalid response.

### Runtime and protocol

- Introduce a small `OnDeviceModelRunner` interface with `load(verifiedModelId)`, `generate(boundedRequest, cancellationToken)`, `stop()` and `unload()` as the only native inference authority. The JNI backend must not call network functions.
- Make planner-binding mode/protocol a **strict tagged union**: existing Demo/Cloud/Local records round-trip unchanged, with explicit versioned migration for the new on-device record. Reject a purported on-device binding containing a URL, credential, unknown key or mismatched content digest. Do not silently reinterpret old `local` entries.
- Keep the existing bounded planner prompt and strict response parser. The small local model may emit invalid JSON; that is a **failed or blocked planning outcome**, never an excuse to turn on cloud fallback, run unvalidated actions or fabricate success.
- Stop task and cancellation must interrupt generation without holding the UI's independent Stop path; closing/rebinding must not replay already-attempted effects. Validate no stale result can be accepted after a new session/revision.
- UI distinguishes **Offline Demo**, **Cloud**, **Local Endpoint**, and **On-device Model**, and displays exact selected model/version and readiness. Offline model failure is explicit; no credential required.

## Ordered deliverable slices

**A — native feasibility gate (no false feature claims).** Pin an upstream llama.cpp revision; build a minimal JNI runner for the existing Android toolchain and target ABI(s); import one tiny GGUF; prove model metadata + bounded token generation with network disabled on the owner's Galaxy. Record peak memory, approximate time-to-first-token, context capacity, APK/native ABI impact, and licensing. An emulator-only test is not device acceptance. If A fails, report a measured blocker rather than substituting Local Endpoint.

**B — model management and planner seam.** Implement import/admission + explicit on-device profile + session-pinned model identity; TDD for profile migration, zero URL/credential, checksum, resource limits and no-cloud-fallback.

**C — trusted end-to-end.** Route the native generation result through the existing untrusted planner parser. Unit/instrumented tests prove valid plan → trusted action and invalid JSON → no action, Stop timing and stale generation rejection. Verify deterministic offline typed battery workflow on a physical arm64 device with airplane mode on.

**D — release verification.** Run exact-head Verify and Android API-24/API-35 build/instrumentation checks and evaluate ABI/package metadata. Record exact source SHA, APK checksum, model SHA, Android build/API, result and device-only evidence before labeling the APK as offline-model capable.

## Acceptance checklist (must all pass)

- [ ] On-device mode executes a real GGUF inference, not a canned response or HTTP call.
- [ ] No network path is invoked by the selected on-device planner; airplane-mode smoke succeeds after import.
- [ ] Mode/profile/session identity is explicit; no implicit cloud, Local Endpoint or Demo fallback.
- [ ] Existing Demo, Cloud and Local profiles migrate without unexpected changes.
- [ ] GGUF import integrity, storage/memory/resource ceilings, load/unload and cancellation fail safely.
- [ ] Invalid/late/model-generated action never bypasses trusted validation, approval, Stop or audit.
- [ ] Existing typed Workbench and release/API/ABI support remain truthful.
- [ ] Same exact built APK/model is physically exercised on the owner's phone; artifact and logs are recorded.

## Deliberate exclusions

This specification does **not** fix the independent reported on-device speech-to-text failure (Record voice → Stop voice → “On-device speech recognition failed”) or the unexpected audible “no.” Track these as separate voice acceptance defects, without conflating cloud connection, STT, offline LLM and Android TTS. No credentials, SSH access, root, Shizuku, or external Termux runtime are prerequisites for the embedded path.

## Risk and decisions for review

The biggest feasibility risk is constrained phone RAM/CPU and native build compatibility, not planner wiring. A tiny model may not reliably emit valid structured actions; record its actual success rate and fail closed on malformed decisions. A default GGUF and pinned native runtime must be selected from verified licenses and tested exact artifacts. No exact model, performance or device compatibility is claimed yet.
