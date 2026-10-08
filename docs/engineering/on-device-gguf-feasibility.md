# TASK-070 — on-device GGUF native feasibility ledger

**Date:** 2026-10-08
**Status:** JNI source-build verification in progress. Physical GGUF token generation remains **UNVERIFIED** until the exact APK and model run on the owner device in airplane mode.

## Pinned upstream source

- Project: `ggml-org/llama.cpp`
- Commit: `71ad0590f4808b6202f9213d166913858c73b1bc`
- Commit time observed: 2026-10-08T16:34:27Z
- License at that exact commit: MIT, copyright 2023–2026 ggml authors.
- Source acquisition: CMake `FetchContent` from the upstream Git repository at the exact commit. There is no floating branch/tag and no opaque prebuilt native binary.
- NDK: `29.0.13113456`
- CMake: `3.22.1`
- Declared LAIN ABIs remain `arm64-v8a` and `x86_64`; `minSdk=24` and `targetSdk=35` remain unchanged.

The upstream Android example currently declares minSdk 33, so LAIN does **not** import that AAR/library configuration. The source build instead follows upstream's documented Android NDK path and explicitly disables host-native/OpenMP/llamafile/OpenSSL/subprocess/UI paths for this probe.

## Native build flags

The probe pins CPU-only source build behavior:

- `GGML_NATIVE=OFF`
- `GGML_OPENMP=OFF`
- `GGML_LLAMAFILE=OFF`
- `GGML_BACKEND_DL=OFF`
- `LLAMA_OPENSSL=OFF`
- `LLAMA_SUBPROCESS=OFF`
- app/server/tools/examples/tests/common disabled
- `BUILD_SHARED_LIBS=OFF` for upstream internals; LAIN's `lain_gguf_probe` JNI library is shared and links the pinned `llama` target.

## TDD evidence

RED commit `c3314cc8157df71e615545478df3847905f9d64f` added `GgufNativeFeasibilityAndroidTest.nativeLibraryLoadsAndRejectsMissingModel()` before the native seam existed.

Android run #658 then failed on both API 35 and API 24 during `:app:compileDebugAndroidTestKotlin` with the expected missing symbols:
`GgufNativeProbe`, `GgufNativeProbeResult`, and `GgufNativeProbeFailure`.

The GREEN implementation adds only:
1. a private bounded Kotlin JNI seam,
2. a pinned source-built native library,
3. a missing-model typed failure path, and
4. a bounded greedy generation probe for a later real app-private model.

This does not add planner/profile authority and is not reachable from normal Workbench execution.

## Bounds and authority

Kotlin and native layers independently enforce context `64..4096`, generation `1..256`, and generation smaller than context. Native output is capped at 65,536 UTF-8 bytes. Missing/unloadable models and invalid generation state fail with typed errors. The probe uses a fixed diagnostic prompt and cannot execute actions, alter policy, access credentials, choose a cloud fallback, or mark a task complete.

The future model store must supply the app-private path. This feasibility seam does not accept document-picker URIs or expose a general arbitrary-path planner API.

## Remaining gate

CI must prove the pinned source compiles, packages, loads and returns `MODEL_NOT_FOUND` on both API 24 and API 35. That is only native-link feasibility.

The actual Task 1 feasibility gate remains **OPEN** until a real supported GGUF is loaded and produces bounded tokens on the exact owner phone with airplane mode on. Record the APK SHA-256, model SHA-256/license, ABI/API, peak RSS, context, time-to-first-token and thermal observation. Emulator/JVM evidence must not be promoted to that physical acceptance class.
