# Embedded On-Device GGUF Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run a real, bounded GGUF planning model inside the installed LAIN_OS Android app and complete a trusted **Show battery** task in airplane mode without HTTP or cloud fallback.

**Architecture:** Add a dedicated on-device planner profile and strict binding type, an app-private GGUF inventory, a cancellable Android JNI llama.cpp adapter, and an `OnDeviceBridgePlanner` that feeds final raw decision JSON to the existing strict trusted parser. Continue to use the existing runtime, approval, executor, verifier, and audit; keep the existing Demo/Cloud/Local HTTP behavior unchanged.

**Tech Stack:** Kotlin, Android API 24–35, NDK/CMake/JNI, a pinned source-build of llama.cpp, app-private files, Chaquopy Python 3.11, JUnit4 / Android instrumentation / Python unittest.

**Spec:** [`docs/superpowers/specs/2026-10-08-on-device-gguf-inference-design.md`](../specs/2026-10-08-on-device-gguf-inference-design.md)

**Status:** Plan for owner review after explicit design approval on 2026-10-08. Not an implementation receipt; no native inference or physical test is claimed.

## Global Constraints

- LAIN presently declares `minSdk 24`, `targetSdk 35`, and `arm64-v8a` / `x86_64`. Do **not** silently raise the API floor or claim native support for untested ABIs.
- Upstream llama.cpp's Android *example library* declares `minSdk 33`; evaluate its **NDK source-build path**, not direct AAR drop-in. Pin exact upstream revision, license review, NDK/CMake flags and model SHA before shipping.
- `on_device` uses protocol `gguf_native_v1`, **no URL, credential, LAN/loopback HTTP, implicit model download or cloud fallback**.
- The existing `demo`, `cloud` and `local` profiles, credentials and session history must survive migration. `local` continues to mean HTTPS endpoint, not GGUF.
- Model output is untrusted. The only route to execution is existing decision parser → capability policy → explicit approvals → deterministic executor → verification → audit. No direct model-to-Android action interface.
- Pin exact model identity/hash/settings at session creation; reject stale generations and do not switch model or cloud providers inside the session.
- Fail closed and show a specific, non-secret status on missing/bad model, ABI, insufficient resources, timeout, cancellation and invalid JSON. Never fabricate `COMPLETE`.
- Preserve Stop task responsiveness, lifecycle recovery, private storage and no automatic retry of uncertain side effects. `Stop talking` remains a separate control.
- Do not conflate the independently observed Android STT failure or audible “no” with on-device LLM support.

## Review Focus

The five high-risk scenarios below have specific owning tests, rather than merely prose assurances.

1. A specially crafted GGUF header, truncated stream or directory escape must never be promoted as an importable model (**Task 2 tests**).
2. A forged `on_device` profile with HTTPS endpoint, embedded credential or unrecognized model hash must fail before generation (**Task 3 tests**).
3. A valid-looking planner action emitted after cancellation or session supersession must never execute (**Tasks 4–5 tests**).
4. A small model that emits malformed JSON, extra prose, or a valid-but-unauthorized capability must fail closed with zero effects (**Task 5 tests**).
5. Low-storage, memory pressure, app backgrounding and new model selection mid-session must never produce false completion or implicit cloud fallback (**Tasks 2, 4, 6 tests**).

---

## Task 1: Prove Android JNI/GGUF feasibility first

**Files:**
- Create: `android/app/src/main/cpp/CMakeLists.txt`
- Create: `android/app/src/main/cpp/lain_gguf_probe.cpp`
- Create: `android/app/src/main/java/dev/lain/os/planner/GgufNativeProbe.kt` (private JNI test seam; not a planner)
- Create: `docs/engineering/on-device-gguf-feasibility.md`
- Modify: `android/app/build.gradle.kts` (native source-build only)
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/GgufNativeFeasibilityAndroidTest.kt`

**Interfaces:** This task yields a pinned native source reference, ABI/SDK compatibility evidence, and a *non-planner* bounded GGUF load-and-token probe. No agent authority, no profile settings.

- [ ] **Step 1: Record and pin upstream dependency.** Inspect the current llama.cpp NDK build instructions, license and source revision; choose one reviewed exact SHA and record it in `docs/engineering/on-device-gguf-feasibility.md` with NDK version, CPU flags and required models' supported tokenizer/chat template. No unpinned network downloads in CI.
- [ ] **Step 2: Write an instrumentation failure fixture.** `GgufNativeFeasibilityAndroidTest.nativeLibraryLoadsAndRejectsMissingModel()` asserts the exact native library loads on every claimed ABI/API and nonexistent model returns a typed failure instead of crashing. Run `cd android && ./gradlew :app:connectedDebugAndroidTest` on the configured emulator; it must fail/red while the native entry point is absent.
- [ ] **Step 3: Build smallest CMake/JNI entry point.** In `GgufNativeProbe.kt`, provide `external fun nativeProbe(modelPath: String, contextTokens: Int, maxNewTokens: Int): String` behind a private Kotlin test seam; reject over-bound requests before JNI and return bounded UTF-8 generation or a typed error. Use user-provided test GGUF bytes *only* from app-private storage; never give JNI an arbitrary UI path.
- [ ] **Step 4: Re-run the compatibility checks.** Run `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest`; run `cd android && ./gradlew :app:connectedDebugAndroidTest` separately on API 24 and API 35 and report tested ABIs explicitly. Measure APK size, model size, peak RSS, context, time-to-first-token, max RAM and thermal response on the owner's actual device with airplane mode on. If unavailable, mark device evidence **UNVERIFIED**.
- [ ] **Step 5: Fail or pass feasibility gate.** Do not continue Tasks 2–7 on a failed source-build/ABI feasibility result. Record exact source commit, device/build, model SHA and observed result; report blockers instead of switching to `Local Endpoint`.
- [ ] **Step 6: Commit:** `git add android/app/build.gradle.kts android/app/src/main/cpp android/app/src/main/java/dev/lain/os/planner/GgufNativeProbe.kt android/app/src/androidTest/java/dev/lain/os/planner/GgufNativeFeasibilityAndroidTest.kt docs/engineering/on-device-gguf-feasibility.md && git commit -m "spike: verify embedded GGUF runtime feasibility"`.

## Task 2: Private GGUF import, inventory and storage admission

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/planner/OnDeviceModelStore.kt`
- Create: `android/app/src/test/java/dev/lain/os/planner/OnDeviceModelStoreTest.kt`
- Create: `android/app/src/androidTest/java/dev/lain/os/planner/OnDeviceModelStoreAndroidTest.kt`

**Interfaces:** `OnDeviceModelStore(context, availableBytes: () -> Long = { context.filesDir.usableSpace })` exposes `importModel(source: InputStream, maxBytes: Long): VerifiedGgufModel`, `resolve(id: String, sha256: String): File`, `list(): List<VerifiedGgufModel>` and `remove(id: String): Boolean`. `VerifiedGgufModel` has `id: String`, `sha256: String`, `sizeBytes: Long`, `architecture: String`, `displayName: String` and `status: String`; only the native runtime receives the private resolved file.

- [ ] **Step 1: Write failing unit tests** `rejectsTruncatedOrHostileGguf()`, `rejectsOversizeAndInsufficientSpace()`, `rejectsTraversalAndDigestMismatch()`, `preservesPriorModelAfterFailedImport()`; use a fake readable source, synthetic header fixtures and capacity injection. Run `cd android && ./gradlew :app:testDebugUnitTest --tests 'dev.lain.os.planner.OnDeviceModelStoreTest'`; expect red.
- [ ] **Step 2: Implement streamed import and strict header bounds.** Bound metadata length/count and supported GGUF versions/architectures; write to private staging, compute SHA-256 while streaming, fsync and atomically rename only after checks. Never keep entire model in memory. Reject model magic alone without validation.
- [ ] **Step 3: Test identity and lifecycle.** Instrumentation `importThenResolveByExactDigest()` proves copy-on-import, hash verification on use, finite storage budget, and safe deletion that refuses a pinned active model. Verify importer never accepts outside-app file names or follows symlinks.
- [ ] **Step 4: Verify:** targeted JVM + Android test runs pass (0 failures); regression with model fixture corruption gives typed invalid-model outcome.
- [ ] **Step 5: Commit:** `git add android/app/src/main/java/dev/lain/os/planner/OnDeviceModelStore.kt android/app/src/test/java/dev/lain/os/planner/OnDeviceModelStoreTest.kt android/app/src/androidTest/java/dev/lain/os/planner/OnDeviceModelStoreAndroidTest.kt && git commit -m "feat: admit and verify private GGUF models"`.

## Task 3: Strict on-device profile, binding and backward migration

**Files:**
- Modify: `android/app/src/main/java/dev/lain/os/planner/PlannerProfile.kt`
- Modify: `android/app/src/main/java/dev/lain/os/planner/PlannerProfileStore.kt`
- Modify: `android/app/src/main/java/dev/lain/os/planner/PlannerSettingsManager.kt`
- Modify: `lain/agent/models.py`
- Test: `android/app/src/test/java/dev/lain/os/planner/PlannerProfileTest.kt`
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/PlannerProfileStoreAndroidTest.kt`
- Test: `tests/test_agent_planner_binding.py`

**Interfaces:** Introduce explicit `on_device` / `gguf_native_v1` *tagged* profile and immutable planner binding with `model_id`, `model_sha256`, `context_tokens` and `generation_tokens`. Keep the existing Demo/Cloud/Local binding JSON **byte-shape and behavior unchanged**. Migrate persisted state version 1 to version 2 atomically without replacing existing saved credentials; support a failed migration without data loss.

- [ ] **Step 1: Write red tests** `onDeviceBindingRejectsUrlOrCredential()`, `onDeviceBindingRejectsWrongHash()`, `v1ProfilesStillRoundTripWithSecrets()` and Python `test_on_device_binding_rejects_unknown_or_network_fields`. Run `cd android && ./gradlew :app:testDebugUnitTest` and `python -m unittest tests.test_agent_planner_binding`; confirm failures are from missing new behavior.
- [ ] **Step 2: Define one exact `on_device` binding wire schema.** Use the fields named in Interfaces, existing session metadata conventions, and strict mode-specific allowed-field lists. The Kotlin `PlannerProfile.toTrustedBindingJson()` and Python `from_dict()` must agree. The on-device mode must be invalid if `base_url`, `credential_ref` or `allow_insecure_lan_http` grants any network/secret semantics; prohibit loose extra keys.
- [ ] **Step 3: Implement atomic v1→v2 profile migration and read-only trust boundary.** `PlannerSettingsManager` saves and selects the exact verified model ID; snapshot distinguishes `ready`, `missing` and `incompatible`. Existing profile switching still takes effect only on *new sessions*.
- [ ] **Step 4: Verify:** Android unit + instrumented persistent-profile tests and Python planner-binding tests pass; restored old Cloud/Local secrets remain encrypted and usable.
- [ ] **Step 5: Commit:** `git add android/app/src/main/java/dev/lain/os/planner/PlannerProfile.kt android/app/src/main/java/dev/lain/os/planner/PlannerProfileStore.kt android/app/src/main/java/dev/lain/os/planner/PlannerSettingsManager.kt lain/agent/models.py android/app/src/test/java/dev/lain/os/planner/PlannerProfileTest.kt android/app/src/androidTest/java/dev/lain/os/planner/PlannerProfileStoreAndroidTest.kt tests/test_agent_planner_binding.py && git commit -m "feat: add strict on-device planner identity"`.

## Task 4: Cancellable, resource-bounded native generation

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/planner/OnDeviceModelRunner.kt`
- Create: `android/app/src/main/java/dev/lain/os/planner/JniOnDeviceModelRunner.kt`
- Create: `android/app/src/main/cpp/lain_gguf_runner.cpp`
- Test: `android/app/src/test/java/dev/lain/os/planner/OnDeviceModelRunnerTest.kt`
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/OnDeviceModelRunnerAndroidTest.kt`
- Modify: `android/app/src/main/cpp/CMakeLists.txt`

**Interfaces:** Define `OnDeviceModelRunner.load(model: VerifiedGgufModel): LoadResult`, implemented by `JniOnDeviceModelRunner(modelStore: OnDeviceModelStore)` which re-resolves app-private file identity and hash before JNI; `generate(prompt: String, contextTokens: Int, maxNewTokens: Int, timeoutMs: Long, generationId: Long): GenerationResult`, `cancel(generationId: Long)`, `unload()`. `GenerationResult` is exactly one of `Success(text: String, modelSha256: String, generationId: Long)` or `Failure(code: String)`; output has an explicit byte ceiling. Native runner receives no Android capability or cloud credential.

- [ ] **Step 1: Write red cancellation/resource tests** `cancelInvalidatesLateCompletion()`, `rejectsOversizePromptAndResponse()`, `secondGenerationDoesNotRaceFirst()`, `insufficientMemoryFailsBeforeLoad()`, `unloadOnLifecycleShutdown()`. Use deterministic fake native work.
- [ ] **Step 2: Implement thin JNI adapter** with one loaded model, single-worker generation lease, 64-bit monotonic generation IDs, bounded context/tokens/threads, timeouts and cancellation polling at native generation boundaries. No network dependencies. Never accept raw file path, unlimited token length, unbounded allocations, or a second concurrent model.
- [ ] **Step 3: Validate negative paths** on emulator: invalid model, incompatible GGUF/tokenizer, wrong ABI, late callback, cancellation during generation, activity/background lifecycle and process teardown return truthful outcomes without replay or execution.
- [ ] **Step 4: Verify:** `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`; API 24/API 35 targeted instrumentation; archive exact ABI/runtime results.
- [ ] **Step 5: Commit:** `git add android/app/src/main/java/dev/lain/os/planner/OnDeviceModelRunner.kt android/app/src/main/java/dev/lain/os/planner/JniOnDeviceModelRunner.kt android/app/src/main/cpp android/app/src/test/java/dev/lain/os/planner/OnDeviceModelRunnerTest.kt android/app/src/androidTest/java/dev/lain/os/planner/OnDeviceModelRunnerAndroidTest.kt && git commit -m "feat: bound and cancel native GGUF generations"`.

## Task 5: Native bridge + strict agent decision, no HTTP envelope

**Files:**
- Modify: `android/app/src/main/java/dev/lain/os/planner/NativePlannerBridge.kt`
- Modify: `lain/app/planner_bridge.py`
- Modify: `lain/app/control.py` (only to reuse session-pinned existing factory if proven necessary)
- Test: `tests/test_android_planner_bridge.py`
- Test: `android/app/src/test/java/dev/lain/os/planner/NativePlannerTransportTest.kt`
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/NativePlannerTransportAndroidTest.kt`

**Interfaces:** Keep `NativePlannerBridge.execute(bindingJson: String, requestBody: String): String` and `cancel()`; dispatch on exact binding *mode/protocol*, using HTTP only for `cloud/local`. Native runner returns `{"ok":true,"body":"<bounded final raw decision JSON>"}`; `OnDeviceBridgePlanner` consumes the body through existing `parse_agent_decision` without `normalize_openai_response`. The JSON envelope is the **internal bridge result**, not a fabricated OpenAI chat-completions response.

- [ ] **Step 1: Write red planner-bridge tests** `test_on_device_decision_executes_via_existing_validator`, `test_on_device_rejects_invalid_json_without_action`, `test_on_device_rejects_unauthorized_capability`, `test_on_device_never_calls_http_or_cloud_fallback`, and `test_on_device_rejects_stale_generation_after_stop`. Use native runner and HTTP transport fakes; assert zero actions when rejected.
- [ ] **Step 2: Implement `OnDeviceBridgePlanner.decide(goal, context, capabilities)`** in `lain/app/planner_bridge.py`, preserving `agent_planner_request` and existing `parse_agent_decision(..., max_output_bytes=..., max_actions=...)`; add dedicated `PLANNER_MODEL_* / PLANNER_CANCELLED / PLANNER_OUTPUT_INVALID` error translation, but no raw model text in logs.
- [ ] **Step 3: Make `NativePlannerBridge.execute` mode-aware**, ensuring `on_device` selects `OnDeviceModelRunner` exclusively and `cloud/local` retain their original HTTP parser and secret-store behavior. Make `cancel()` affect current call without cross-session cancellation or releasing Stop authority.
- [ ] **Step 4: Verify:** `python -m unittest tests.test_android_planner_bridge tests.test_agent_planner_binding` and `cd android && ./gradlew :app:testDebugUnitTest`; run instrumentation on both API 24/35. Assert local model failure never falls back to `DemoPlanner` or `NativePlannerTransport`.
- [ ] **Step 5: Commit:** `git add android/app/src/main/java/dev/lain/os/planner/NativePlannerBridge.kt lain/app/planner_bridge.py lain/app/control.py tests/test_android_planner_bridge.py android/app/src/test/java/dev/lain/os/planner/NativePlannerTransportTest.kt android/app/src/androidTest/java/dev/lain/os/planner/NativePlannerTransportAndroidTest.kt && git commit -m "feat: route offline model decisions through trusted planner"`.

## Task 6: On-device model selection and truthful Android Workbench

**Files:**
- Modify: `android/app/src/main/java/dev/lain/os/MainActivity.kt`
- Modify: `android/app/src/main/res/layout/activity_main.xml`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/java/dev/lain/os/planner/PlannerSettingsManager.kt`
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/PlannerSettingsManagerTest.kt`
- Create: `android/app/src/androidTest/java/dev/lain/os/planner/OnDevicePlannerUiAndroidTest.kt`

**Interfaces:** The settings UI presents four distinct modes: `Offline Demo`, `Cloud`, `Local Endpoint`, and `On-device Model`. The on-device editor imports with `ActivityResultContracts.OpenDocument`, selects an exact verified model, discloses local-only status and memory readiness, and does not present endpoint, API key or misleading HTTP **Test connection** as meaningful on-device controls.

- [ ] **Step 1: Write red Android UI tests** `onDeviceModeShowsModelNotEndpoint()`, `missingModelBlocksRunWithUsefulStatus()`, `importFailureKeepsPriorSelection()`, `selectionDuringActiveSessionDoesNotRetargetIt()` and `cloudProfileStillWorksUnchanged()`.
- [ ] **Step 2: Implement Activity Result document-picker import** streaming through Task 2 model store; render loading, digest, compatibility and ready/unavailable errors in Workbench without exposing app-private paths or secret data. Display explicit profile source on active task.
- [ ] **Step 3: Verify** existing typed tasks, Run/Stop, confirmation, rotation, profile switching and voice settings remain usable on API 24/35 instrumentation.
- [ ] **Step 4: Commit:** `git add android/app/src/main/java/dev/lain/os/MainActivity.kt android/app/src/main/java/dev/lain/os/planner/PlannerSettingsManager.kt android/app/src/main/res/layout/activity_main.xml android/app/src/main/res/values/strings.xml android/app/src/androidTest/java/dev/lain/os/planner/PlannerSettingsManagerTest.kt android/app/src/androidTest/java/dev/lain/os/planner/OnDevicePlannerUiAndroidTest.kt && git commit -m "feat: select and inspect on-device planner"`.

## Task 7: Exact-head offline acceptance + release evidence

**Files:**
- Create: `docs/engineering/on-device-gguf-acceptance.md`
- Modify: `docs/ANDROID_ACCEPTANCE.md`
- Modify: `docs/PLUGGABLE_MODEL_RUNTIME.md`
- Test: `tests/test_android_planner_bridge.py` (end-to-end fake runner safety negatives)
- Test: `android/app/src/androidTest/java/dev/lain/os/planner/OnDevicePlannerUiAndroidTest.kt` (installed app lifecycle)

**Interfaces:** A candidate acceptance record containing source SHA, debug APK SHA-256, selected model SHA-256 and license, Android build/API/ABI, runner build SHA, airplane-mode state, typed task status, verification/audit receipt, memory and latency measurements and negative/fallback results.

- [ ] **Step 1: Write acceptance harness tests** confirming real-model mode demands an installed model hash, no network invocation, valid verified terminal battery result, and no action on malformed planner decisions. Fakes prove logic only, not physical model inference.
- [ ] **Step 2: Run fresh repository checks** on exact implementation head: `python -m unittest discover -s tests -v`, `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, and connected instrumentation on API 24 and API 35. Capture commands, exit codes and exact run URLs; a failed CI gate blocks acceptance.
- [ ] **Step 3: Exercise one exact APK on physical device**: import supported GGUF, verify digest, select `On-device Model`, enable airplane mode, issue typed **Show battery**, inspect task result, independent verifier and audit. Stop mid-generation, resume app/rebind and check no unexpected cloud or repeated side effects. Retain timestamps and measured memory/thermal/latency. If owner-device execution is unavailable, mark **PHYSICAL UNVERIFIED** and do not say feature passed.
- [ ] **Step 4: Verify model-native and package artifacts**: inspect `.so` ABIs, JNI/API compatibility, unbundled model-data assumptions and dependency licenses; check no embedded secrets or network fallback. Compare APK + model checksums against device-tested copies.
- [ ] **Step 5: Commit receipts** to the acceptance documents and open a separate implementation PR for human review. Do **not** merge PR #40 as if its documentation were the running native model.
- [ ] **Step 6: Handoff** only when code reviews and exact-head CI are green; request an explicit owner decision on merge/distribution. Physical/OEM results retain their own evidence class.

## Execution / sequencing

Each task gets a TDD red→green test cycle, a bounded diff, and a reviewer gate before advancing. Start with **Task 1 feasibility**. If the Galaxy cannot load a small model within RAM/thermal limits or supported ABI/SDK, stop and report the precise blocker; do not rename HTTPS Local Endpoint as on-device inference. For the user's goal, **native implementation with an independent final review** is an efficient default after plan approval. No work is scheduled or running outside an authorized session.

The canonical `.tasks/NEXT.md` currently assigns TASK-069; before beginning code, explicitly reconcile this owner-approved priority with the existing queue in its supported TaskPlanner flow and keep separate PR #39 intact. Preserve all source and state changes on a dedicated implementation branch, never on this design/documentation PR.
