# Clean-Install Bootstrap and Recommended Qwen Acquisition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a stock Android device usable from one LAIN_OS APK, with truthful capability/permission setup and an in-app pinned Qwen3 1.7B Q4_K_M download path.

**Architecture:** Add a small app-owned bootstrap state machine that projects capability availability and routes permission requests without granting itself authority. Keep ordinary Android runtime permissions separate from Settings-owned special grants. Reuse the existing planner/profile/model store; add a pinned recommended-model descriptor and bounded downloader that publishes model bytes only after expected SHA-256 and GGUF validation pass.

**Tech Stack:** Kotlin, AndroidX Activity Result APIs, Android intents/settings panels, existing PlannerSettingsManager/OnDeviceModelStore, HttpURLConnection, JUnit/Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-11-universal-on-device-command-layer-design.md`

## Global Constraints

- Core LAIN_OS must launch from one APK on stock Android with no Termux, Tasker/Automate, Shizuku, root, ADB, Android Studio, or external Python prerequisite.
- Missing optional integrations disable only their capabilities.
- Special Android grants remain user-controlled in Android-owned UI.
- No silent cloud fallback.
- Recommended Qwen target is `Qwen3-1.7B-Q4_K_M.gguf`, approximately 1.28 GB.
- Recommended Qwen URL is `https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf`.
- Expected SHA-256 is `d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5`.
- Model bytes are private and become selectable only after bounded download, GGUF preflight, and exact checksum verification.
- Existing Import GGUF remains available.
- No new permission is declared merely because a future capability might need it.

## Review Focus

- Fresh install or cleared app data with no planner/model state must still launch and offer Demo immediately.
- Denying microphone or a later optional permission must not trap setup or disable unrelated typed capabilities.
- Returning from Android Settings without granting a special permission must remain truthful and retryable.
- Interrupted/partial/wrong-hash Qwen downloads must leave no selectable model or stale profile.
- Low-storage and process/activity recreation during download/setup must fail or resume truthfully without duplicate publication.

---

### Task 1: Capability availability model and permission broker

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/bootstrap/CapabilityAvailability.kt`
- Create: `android/app/src/main/java/dev/lain/os/bootstrap/PermissionBroker.kt`
- Create: `android/app/src/test/java/dev/lain/os/bootstrap/PermissionBrokerTest.kt`
- Modify: `android/app/src/main/java/dev/lain/os/MainActivity.kt`

**Interfaces:**
- Produces: `enum class AvailabilityState { READY, NEEDS_PERMISSION, NEEDS_SETTINGS_GRANT, OPTIONAL_DEPENDENCY_MISSING, UNSUPPORTED }`
- Produces: `data class CapabilityAvailability(val capability: String, val state: AvailabilityState, val requirement: String?)`
- Produces: `PermissionBroker.snapshot(): List<CapabilityAvailability>`
- Produces: `PermissionBroker.runtimePermissionFor(capability: String): String?`
- Produces: `PermissionBroker.settingsIntentFor(capability: String): Intent?`

- [ ] **Step 1: Write failing JVM tests** for READY/NEEDS_PERMISSION/NEEDS_SETTINGS_GRANT/OPTIONAL_DEPENDENCY_MISSING/UNSUPPORTED projection, denial isolation, and no undeclared future permission.
- [ ] **Step 2: Run focused tests and confirm RED.**
Run: `./android/gradlew --project-dir android :app:testDebugUnitTest --tests '*PermissionBrokerTest*'`
Expected: FAIL because bootstrap types do not exist.
- [ ] **Step 3: Implement the availability types and broker** using injected permission/settings/dependency probes so JVM tests need no device.
- [ ] **Step 4: Route existing microphone permission lookup through the broker** while keeping the current visible request flow and typed fallback unchanged.
- [ ] **Step 5: Run focused tests and existing voice tests.**
Expected: PASS.
- [ ] **Step 6: Commit.**
Commit: `feat: add capability permission broker`

### Task 2: First-run bootstrap state and UI

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/bootstrap/BootstrapStateStore.kt`
- Create: `android/app/src/main/java/dev/lain/os/bootstrap/BootstrapViewModel.kt`
- Create: `android/app/src/main/java/dev/lain/os/BootstrapActivity.kt`
- Create: `android/app/src/main/res/layout/activity_bootstrap.xml`
- Create: `android/app/src/androidTest/java/dev/lain/os/BootstrapActivityAndroidTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/dev/lain/os/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PermissionBroker.snapshot()`
- Produces: `BootstrapViewModel.state` containing planner readiness plus bounded capability availability.
- Produces: a skippable first-run UI with `Continue with Demo`, `Configure planner`, permission actions, and `Finish setup`.

- [ ] **Step 1: Write instrumentation tests** proving clean-data launch reaches bootstrap, Continue with Demo reaches MainActivity, denial does not loop, and recreation preserves truthful state.
- [ ] **Step 2: Run instrumentation target and confirm RED.**
- [ ] **Step 3: Implement atomic bootstrap-state persistence** containing only setup completion/version, never credentials or permission assertions.
- [ ] **Step 4: Implement BootstrapActivity/ViewModel** with live re-scan in `onResume`; never cache a special grant as true without rechecking platform state.
- [ ] **Step 5: Wire launch behavior** so first run opens bootstrap, while users may continue immediately with the built-in Demo planner.
- [ ] **Step 6: Verify grant/deny/settings-return/recreation cases.**
- [ ] **Step 7: Commit.**
Commit: `feat: add clean-install bootstrap flow`

### Task 3: Harden OnDeviceModelStore for pinned downloads

**Files:**
- Modify: `android/app/src/main/java/dev/lain/os/planner/OnDeviceModelStore.kt`
- Modify: `android/app/src/androidTest/java/dev/lain/os/planner/OnDeviceModelStoreAndroidTest.kt`

**Interfaces:**
- Produces: `importModel(source: InputStream, maxBytes: Long = ..., expectedSha256: String? = null, isCancelled: () -> Boolean = { false }): ImportedGgufModel`
- Existing call sites remain source-compatible through defaults.

- [ ] **Step 1: Add RED tests** for expected-hash match, mismatch, cancellation during stream, insufficient storage/staging cleanup, and no published model on failure.
- [ ] **Step 2: Run focused instrumentation/JVM model-store tests and confirm RED.**
- [ ] **Step 3: Extend importModel** so cancellation checks occur during streaming and expected hash is checked before final publication.
- [ ] **Step 4: Run tests and confirm no regression to document-picker import.**
- [ ] **Step 5: Commit.**
Commit: `fix: verify pinned gguf before publication`

### Task 4: Recommended Qwen descriptor and bounded downloader

**Files:**
- Create: `android/app/src/main/java/dev/lain/os/planner/RecommendedModelCatalog.kt`
- Create: `android/app/src/main/java/dev/lain/os/planner/RecommendedModelDownloader.kt`
- Create: `android/app/src/test/java/dev/lain/os/planner/RecommendedModelDownloaderTest.kt`

**Interfaces:**
- Produces: `data class RecommendedModel(val id: String, val displayName: String, val url: String, val expectedSha256: String, val expectedBytes: Long)`
- Produces: `RecommendedModelCatalog.default(): RecommendedModel`
- Produces: `RecommendedModelDownloader.download(model, onProgress, isCancelled): ImportedGgufModel`
- Downloader delegates final validation/publication to `OnDeviceModelStore.importModel(... expectedSha256=...)`.

- [ ] **Step 1: Add RED tests** using an injected HTTP connection/stream source for HTTPS-only URL, content-length/resource bounds, progress, cancellation, HTTP failure, truncation, and checksum mismatch.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement the pinned descriptor** with Qwen3 1.7B Q4_K_M URL and SHA-256 from the approved spec.
- [ ] **Step 4: Implement bounded foreground/on-demand downloader** with no background daemon and no external storage requirement.
- [ ] **Step 5: Run tests.**
- [ ] **Step 6: Commit.**
Commit: `feat: add pinned recommended qwen downloader`

### Task 5: Put “Download recommended Qwen” in Planner Settings and bootstrap

**Files:**
- Modify: `android/app/src/main/res/layout/activity_main.xml`
- Modify: `android/app/src/main/java/dev/lain/os/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/layout/activity_bootstrap.xml`
- Modify: `android/app/src/main/java/dev/lain/os/BootstrapActivity.kt`
- Create: `android/app/src/androidTest/java/dev/lain/os/RecommendedModelDownloadAndroidTest.kt`

**Interfaces:**
- Consumes: `RecommendedModelCatalog.default()`, `RecommendedModelDownloader.download(...)`
- Produces: visible `Download recommended Qwen` next to `Import on-device GGUF model`.
- On success, fills the on-device model SHA field from `ImportedGgufModel.sha256`; profile save/select remains explicit.

- [ ] **Step 1: Write RED UI tests** proving the button appears only for On-device mode and shows model name, quantization, approximate size, source, and checksum prefix.
- [ ] **Step 2: Add RED lifecycle tests** for cancel, rotation/recreation, failure, and successful import with no duplicate download.
- [ ] **Step 3: Wire UI and ViewModel-owned worker state**; disable conflicting model/profile mutations while the download is active.
- [ ] **Step 4: Keep Import GGUF present and usable** as the offline/manual fallback.
- [ ] **Step 5: Run focused UI tests plus planner settings tests.**
- [ ] **Step 6: Commit.**
Commit: `feat: download recommended qwen in app`

### Task 6: Clean-install acceptance gate

**Files:**
- Create: `android/app/src/androidTest/java/dev/lain/os/CleanInstallBootstrapAcceptanceTest.kt`
- Modify: `docs/ANDROID_DEVICE_ACCEPTANCE.md`
- Modify: `docs/engineering/native-gguf-text-chat.md`

**Interfaces:**
- Consumes all prior tasks.
- Produces automated clean-state acceptance plus a physical-device checklist.

- [ ] **Step 1: Add clean-state instrumentation** for no prior prefs/files, Demo continuation, planner configuration entry, permission denial/retry, and recommended-model download using injected transport.
- [ ] **Step 2: Run API 24 and API 35 instrumentation.**
Expected: PASS on exact implementation head.
- [ ] **Step 3: Run canonical verification and lint/unit build.**
Run: `python scripts/verify.py`
Run: `./android/gradlew --project-dir android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: all PASS.
- [ ] **Step 4: Document physical factory-equivalent acceptance**: clear app data/uninstall, install exact APK, no helpers installed, grant/deny permissions, import/download model, record APK/model hashes and observed outcome.
- [ ] **Step 5: Commit.**
Commit: `test: gate clean-install bootstrap acceptance`
