# TASK-070 — GGUF fixed-header preflight (partial native-inference work)

**Date:** 2026-10-08
**Status:** bounded header parser + private pinned-source JNI probe compiled and exercised on Android emulators. **NOT** an installed model, end-to-end planner, model-import verifier, or physical device acceptance.
**Design / plan:** [PR #40](https://github.com/null0entry/LAIN_OS_DEVBRANCH/pull/40) with `docs/superpowers/specs/2026-10-08-on-device-gguf-inference-design.md` and `docs/superpowers/plans/2026-10-08-on-device-gguf-inference-implementation.md`.

## Owner-approved scope adjustment

The user selected native execution and then explicitly approved **guardrailed scaffolding and tests without claiming JNI hardware evidence** after the runtime environment could not build Android NDK code. This is a partial deliverable, not permission to pass the plan's native feasibility gate. We advanced the lightweight model-import **preflight** from plan Task 2 into TASK-070; leave real native-runtime feasibility from plan Task 1 **OPEN**, without skipping it for any later model import/execution.

## Precisely what the Kotlin preflight checks

`GgufHeaderPreflight.inspect(source, declaredSizeBytes, maxSizeBytes)` reads **at most 24 bytes**: GGUF magic (ASCII), v3 version (unsigned little-endian layout represented as checked `Int`), tensor count (`u64` range restricted to 1…1,000,000), metadata entry count (`u64` restricted to 0…100,000). It rejects invalid budgets, size <= header, over-budget size, EOF, malformed magic, unsupported versions, out-of-range counts, no-progress reads, over-reported read counts, I/O exceptions, and revoked-provider SecurityException. Inputs are not retained.

An accepted `GgufHeaderPreflightResult.Candidate` means **only that the fixed header is plausible**. It does not validate metadata value types/lengths, tensor layout/digests, actual file size, source trust, symlinks, compatibility, available memory or model loadability. An untrusted claimed size is not an admission decision. The later model-store/importer must independently enforce source byte accounting, all GGUF structural validation, SHA-256, private staging, atomic commit and storage/memory ceilings before allocating a verified model ID. Do **not** wire this preflight result directly into the planner.

The first implementation conservatively supports GGUF header version 3 only. Earlier/later GGUF versions produce explicit `UNSUPPORTED_VERSION`, not an unsafe fallback. The count ceilings are conservative guardrails rather than proof of valid model structure.

## Verification evidence

- **Test-first RED observed locally:** 7/7 newly written tests failed against an intentionally inert `BAD_MAGIC` placeholder implementation; failures were specific assertions about the missing behavior, not an NDK build failure.
- **GREEN observed locally:** 7/7 focused tests passed with the bounded parser, using local `kotlinc-jvm 1.9.0` / JDK 21 and a *non-repository* lightweight JUnit annotation/assertion shim. Cases: valid candidate/24-byte read bound, bad magic/version, truncated/header-only, size budgets, hostile tensor/metadata counts, short reads, stalled/throwing streams. The same tests are checked in as normal Android JUnit4 unit tests.
- **Android CI evidence on earlier head `a6be319`:** [Verify #669](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/37826964857) and [Android #658?](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/37826964861) completed successfully; API 24 emulator executed 53 tests and API 35 executed 52. These precede the latest two regression fixes; exact-head status must be checked separately. (The Android run URL, not this descriptive run number, is the authoritative reference.)
- **Native JNI/NDK compile + library loading:** verified by prior Android CI on API 24 and API 35 (missing-model rejection only); arm64 physical runtime UNVERIFIED. **GGUF token generation:** native probe implemented but **not tested with a real GGUF**. **Real model import:** NOT IMPLEMENTED. **Galaxy airplane-mode test:** UNVERIFIED.
- **Security/behavior invariants:** no new app network calls or planner actions, no profile changes, no new permission, no secret handling and no model file import in this slice. The JNI probe is private, not wired to tasks.

## Next gated work

1. Verify the two new adversarial stream regressions on the **latest** PR head in GitHub Android CI, while keeping TASK-070 open.
2. Native probe is now pinned to llama.cpp `71ad0590f4808b6202f9213d166913858c73b1bc` and builds through JNI/NDK on API 24/35 emulators; record further arm64/OEM ABI evidence separately. Upstream Android example minSdk 33 is not a substitute for LAIN's minSdk 24.
3. Demonstrate *real* GGUF load + bounded token generation on the exact phone in airplane mode and record model/APK hashes, memory and latency **before** claiming native feasibility.
4. Only then proceed to trusted app-private model import, profile migration, native planner integration and later end-to-end acceptance. Keep unrelated STT and spoken “no” bug reports separate.

## Ruling for the native execution ledger

**Ruling:** Native JNI/NDK compile was delegated to GitHub Actions, which can exercise Android emulators, while actual model bytes and physical owner-phone access remain unavailable here. Keep Task 1 INCOMPLETE despite CI compiling JNI: true model generation and phone measurements must be proven before integration or release claims. **Risk if wrong:** promoting this candidate marker as an installed model would bypass full GGUF validation; the type and docs explicitly prohibit that promotion.
