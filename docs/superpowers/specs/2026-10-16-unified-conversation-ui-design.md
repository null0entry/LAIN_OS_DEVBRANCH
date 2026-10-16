# Superseded: single-conversation UI-only design (2026-10-16)

**Status:** Superseded by the broader owner-directed [LAIN_OS v2 API-first three-phase design](2026-10-16-api-first-three-phase-design.md). The previous complete document is retained in Git history before this replacement. Do **not** implement the former local model route or deterministic command bypass described in the original revision.

The UI remains a requirement, but it is now **Phase 3** after Phase 1 capability/permission scaffolding and Phase 2 hosted-model intent-to-action planning.

Carry-forward constraints:
- **One** user-visible chat timeline and **one** input composer for spoken/typed messages and device requests.
- Every accepted final message routes first to the selected hosted API model for intent; **Show Demo Toast** is a model-interpreted command, not a hidden direct-only fallback.
- The trusted planner validates all proposed capability actions, uses real Android permission checks and owner confirmations, executes through bounded adapters, and records truthful verification receipts.
- Each accepted turn receives a real model reply when available, including on device-action turns. API failure displays a truthful error rather than a counterfeit assistant response.
- Chat, speech and tool outcomes share a bounded canonical context, not parallel Local Chat/Workbench histories.
- UI consolidation, model failure behavior, history migration and physical Moto acceptance remain mandatory.

**This is a design change only; current APK behavior has not changed.**
