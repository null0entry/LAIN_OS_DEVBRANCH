# Integrated functionality verification

Repair scope: Python packaging/runtime, Android Workbench/planner/permissions,
native tools, and the existing GitHub verification/artifact path (PR #57).

## Local automated evidence

- Canonical verification: 351 Python tests, release-state checks, whitespace and source scans pass.
- Android: 143 JVM tests, debug APK/instrumentation assembly, and lint pass. Lint retains 82 warnings and has zero errors.
- Native lifecycle harness: cancellation, scoped generation registration, cleanup, grammar ownership, invalid-grammar rejection, and grammar byte bounds pass.
- Installed wheel/CLI imports and capability listing pass.
- APK package, SDK/ABI metadata, debug signature and binary SHA-256 independently inspected; `scripts/android_artifact.py` records the exact committed checkout and checksum.

## Real Qwen evidence

Model: Qwen3 1.7B Q4_K_M, 1,282,439,264 bytes, SHA-256
`d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5`.
Backend: pinned llama.cpp `71ad0590f4808b6202f9213d166913858c73b1bc`, built for Linux CPU.

The smoke harness reflected the actual prompt and grammar builders from compiled
Android Kotlin classes and used the real Python AppController. Its Android action
executor was a recording callback: this is model/controller integration evidence,
not evidence that a physical phone displayed a toast.

Reproduced failures included thinking text instead of routing JSON, copying the
literal action type `capability`, classifying an Android toast as chat, copying a
historical action ID into a new proposal, and appending a stray quote to completion
JSON. Prompt context, non-thinking framing, and native decoder constraints repair
these paths without stripping/repairing untrusted model JSON.

The final real-model flow classified the toast request as a task, generated one
valid `android.toast` proposal, passed trusted validation/execution/verification,
and returned COMPLETE after one executor callback with LIMITED verification.
It made three model calls (route, initial plan, completion). A separate contextual
chat smoke correctly recalled a purple bicycle. The CLI's printed end-of-text UI
marker was excluded from the harness capture; it is not present in JNI output.

## Remaining acceptance

The GitHub API24/API35 jobs are rerun on the final PR revision. Their results are
visible on PR #57. Physical Android inference/voice behavior, latency/thermal
measurements, and live provider credentials remain unverified in this environment.
No physical or live-service release gate is closed by these automated checks.
