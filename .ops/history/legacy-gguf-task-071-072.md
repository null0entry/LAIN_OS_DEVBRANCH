# Archived legacy Qwen task records (2026-10-12)

Earlier stacked offline-GGUF branches reused task numbers subsequently assigned in the canonical task sequencer by PR #45. These records are preserved verbatim, outside the active `.tasks` board, because their semantics differ from the current TASK-071 and TASK-072. The current IDs and execution states in `.tasks/NEXT.md` remain authoritative.

<!-- Prior location: .tasks/DONE.md -->
## TASK-071: Harden offline GGUF cancellation and profile-save responsiveness
**Priority:** P2 | **Tags:** android, offline, gguf, lifecycle
**Updated:** 2026-10-09

### Goal
Address review findings in the draft offline planner integration without promoting unverified physical-device acceptance.

### Plan
- Preserve per-generation Stop through native startup, hash verification, load and CPU decode, with bounded registrations and worker-owned cleanup.
- Keep offline profile saves responsive across recreation and preserve the correct profile/draft after failure.
- Coordinate Python asset extraction across both Android processes and align request byte bounds.
- Verify the implementation and provide a physical offline-task acceptance procedure without promoting skipped model tests to hardware evidence.

### Acceptance
- Stop cannot be lost before JNI generation begins, and cancellation resources are released.
- Model hashing and profile save cannot block the UI thread.
- API/emulator evidence and physical airplane-mode execution remain separately reported.

### Verification
Implementation commit `a49281d2fab1f32ac309819ac320693a36737f46`:
- [Verify](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/37874739310): passed 325 portable tests and eight native lifecycle groups.
- [Android](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/37874739298): build, JVM tests, lint and instrumentation passed on API 24 and API 35. API 24 reported 67 tests with three skips; API 35 reported 66 tests with one skip.
- Local Android JVM suite: 96 tests, zero failures/errors. Host lifecycle harness also passed ASAN/UBSAN; NDK 29 checks used actual pinned llama.cpp headers for ARM64 and x86_64 at API 24.
- Real-model hardware smoke was skipped. Physical Qwen inference, airplane-mode trusted task execution and measured Stop/resource acceptance remain UNVERIFIED; TASK-070 remains open.

---

---

<!-- Prior location: .tasks/IN_PROGRESS.md -->
## TASK-072: Deliver native offline GGUF text chat
**Priority:** P0 | **Tags:** owner-directed, android, on-device, gguf, conversation
**Updated:** 2026-10-11 15:20

### Goal
Use the existing imported Qwen GGUF and embedded native inference engine to exchange real
plain-text messages inside the installed Android client, with no Termux runtime dependency.

### Plan
- Build a bounded Qwen plain-text chat prompt independent of task planner JSON.
- Reuse native generation, cancellation lease and verified app-private model identity.
- Add explicit foreground Chat UI, persistence, New Chat, Send and Stop response.
- Verify prompt bounds/authority separation and Android API 24/35 CI.
- Retain honest physical-device inference acceptance status.

### Acceptance
- Owner can select imported Qwen GGUF and receive real native assistant responses.
- Follow-up messages retain bounded prior context and completed chat survives restart.
- No ordinary chat message can execute tasks or grant capabilities.
- Chat cancellation does not become task cancellation; foreground-only behavior is explicit.
- Real device output remains UNVERIFIED until owner exercises exact APK with model.

### Status
Implementation source pushed to stacked feature branch; CI and device acceptance pending.

---
