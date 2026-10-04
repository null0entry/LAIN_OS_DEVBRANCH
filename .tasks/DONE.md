# Done

## TASK-008: Close Phase-1 planner acceptance and adversarial gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-1, acceptance, adversarial
**Updated:** 2026-10-04

### Done summary

- Phase-1 planner acceptance/adversarial implementation is integrated through PR #24, including repeatable demo-file behavior and packaged Android file/clipboard acceptance coverage.
- Exact PR #24 head `f05a9317dfea53a98d8ee4ecee4963cb4250b19a` passed Verify #365 and Android #354.
- On 2026-10-04 the owner reported the required physical TASK-008 acceptance matrix complete with all tests green. This records owner-provided hardware acceptance; the GitHub connector did not independently observe the physical-device traces.
- Phase 1 is accepted complete and TASK-009 is promoted to begin Phase 2 Voice Conversation.

---

## TASK-007: Build Planner Settings and inert connection diagnostics
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-1, android-ui
**Updated:** 2026-10-03

### Done summary

- Added the native Android Planner Settings surface for Demo, Cloud, and Local profiles, including create/edit/select/delete and bounded endpoint/model/timeout/response controls.
- Credential UI supports save, replacement, removal, and saved/missing state without rendering or returning raw secrets.
- Added diagnostic-only Test Connection and visible active provider/model identity; instrumentation verifies diagnostics do not create an agent session.
- Corrected the settings layout to preserve existing Workbench control visibility and made the screen-level test independent of emulator viewport scrolling.
- Verify workflow #230 succeeded.
- Android workflow #219 succeeded after one targeted rerun of an unrelated API-24 RuntimeFlow timing failure; API 24 and API 35 build, JVM tests, lint, and instrumentation gates are green at head `8ce8a99a720cc5f9dfc0e6310fd29bcf4ce58f3f`.
- Final post-CI review `5401183419` found no unresolved threads or actionable correctness/security findings.
- PR #12 squash-merged as `be4e1d273e05784beeb5783c811d19d40a8a1930`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-006: Wire profile-selected planner runtime bridge
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- Added the Android/Python planner bridge over the native bounded transport while preserving native ownership of raw credentials and HTTP.
- Planner selection is keyed from each session's pinned `PlannerBinding`; Demo stays offline and Cloud/Local never auto-fallback across modes.
- Stop/lifecycle cancellation propagates into the active native planner call and maps to an explicit cancelled agent state.
- Verify workflow #173 succeeded.
- Android workflow #162 succeeded after a targeted API 35 rerun; API 24 and API 35 build, unit, lint, and instrumentation gates are green.
- The first API 35 attempt failed with unrelated Espresso window-focus failures after emulator/ADB startup trouble; the isolated retry succeeded without product-code changes.
- Fresh post-CI review found no submitted review threads and no product-code overlap with newer main planning commits.
- PR #11 squash-merged as `f8cff695f4dfdac5cdf5bffa33b2d5dc70776bb2`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-005: Implement persistent PlannerProfile selection
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- Added strict native Demo/Cloud/Local PlannerProfile validation and atomic profile persistence with opaque credential references only.
- Active profile selection now persists across restart and supplies the existing trusted PlannerBinding provider at session creation, preserving per-session pinning.
- PR #10 head `1d35244b83292f309f3cb1eb0dd16e5254587abc` received fresh whole-diff review with no actionable correctness or over-engineering finding.
- Verify workflow #150 succeeded.
- Android workflow #139 succeeded on API 24 and API 35, including build, unit tests, lint, and instrumentation.
- Draft state was confirmed as the mergeability gate; marking ready changed GitHub mergeability to true without history rewrite or product-code reconciliation.
- PR #10 squash-merged as `16fd0aa363576f897282dd932b8a56609884ff86`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-003: Implement bounded native planner transport
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- PR #8 head `a39b69c768984fcbe0d2febca79eac99ae204b38` received fresh same-run whole-diff review PASS `5399559135` against `main@04e4a5b2b0fd947ca423415de5c469e9a7650c04`.
- Prior P1/P2 findings were verified addressed and all three review threads were resolved.
- Verify workflow #135 succeeded.
- Android workflow #124 succeeded, including API 24 and API 35 instrumentation.
- Native planner endpoints remain HTTPS-only; global cleartext denial remains intact.
- PR #8 squash-merged as `c217ca76cc41ae24eb14357bf8ce3022f29b8534` under the review-only merge policy.
- Tests were run and succeeded; no UNTESTED status applies to this merge.

---

## TASK-001: Reconcile PR #4 with current main and restore mergeability
**Priority:** P0
**Updated:** 2026-10-02 17:56

### Done summary

- PR #4 was reconciled without dropping its seven-file Android Stop wake-up fix.
- Fresh code review passed on head `6217be8ec2b3d4bd20993c46239bf0425543617c`.
- Android workflow #22 and Verify workflow #33 succeeded.
- The PR merged as `077a4aea1cea9e3ce638403886ceeab4c32d51b3`.
- Physical-device acceptance remained deferred and is not claimed passed.

---

## TASK-002: Implement Android Keystore-backed planner SecretStore
**Priority:** P1
**Updated:** 2026-10-02 17:56

Review corrections implemented on PR #7.

### Done summary

- Added AES-GCM AAD binding to exact credential_ref plus domain/version.
- Added swapped-record and concurrent-initialization regressions.
- Fixed first-use planner-secrets mkdir race by re-checking isDirectory.
- Implementation commits: c67d1a5f152e7e82e38f21a141727b43c4d8e99e, c26925ed76e1e02fd49bd5b7ec796957c36fc423.
- Fresh code review passed on head c26925ed76e1e02fd49bd5b7ec796957c36fc423 with no new actionable finding.
- Verify workflow #56 succeeded; Android workflow #45 was cancelled, so Android instrumentation remains explicitly untested.
- PR #7 merged as 2946e1a042d57eddffce4477f2136aea4da0ccde under the review-only merge policy.

---
