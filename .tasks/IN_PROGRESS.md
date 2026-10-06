# In Progress

## TASK-063: Apply accepted conversational revisions to active task sessions
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, revision, session, authority
**Updated:** 2026-10-04

### Goal
Complete the missing Phase-2 revision path so one accepted final conversational revision such as “make it shorter” can update the intended active task session through trusted runtime state, invalidate stale approval/planning authority, and replan safely without letting transcript text or ambiguous references mutate execution state.

### Scope
- Consume the exact final-turn revision_intent produced by TASK-011 only after active-task/reference resolution succeeds.
- Add the smallest trusted current-session revision operation needed by the existing bounded AgentSession/runtime controller; preserve the stable session identity while advancing trusted revision state where the existing model supports it.
- Invalidate stale approval/request/action authority derived from the prior session revision before any revised plan can execute.
- Replan from the accepted revision text under the session’s existing selected planner/provider binding and remaining budgets; never let the model choose the target session or regain consumed budgets.
- Reject partial speech, ambiguous/missing targets, terminal/non-revisable sessions, stale revision races, duplicate revision submission, or target-session substitution.
- Preserve typed fallback and the same route for typed and spoken accepted revisions.
- Keep Phase-3 workflow artifact invalidation under TASK-020; this task owns only the existing Phase-1/2 active AgentSession revision seam needed by the Phase-2 exit gate.
- Do not add new capabilities, generic session mutation, hidden restart/retry, or publication/workflow-artifact behavior.

### Dependencies
- TASK-008: trusted bounded AgentSession planning/execution and exact approval semantics.
- TASK-011: monotonic turns, active-task reference, ambiguity handling, and non-mutating revision_intent.
- TASK-050: accepted final local speech can enter the same TASK-011 turn path.
- TASK-013: barge-in/echo protection must keep synthesized/partial speech from becoming revision authority before final voice acceptance.

### Plan
- Trace the current turn_submit → revision_intent → AgentSession boundary and identify the existing trusted session mutation/replan primitive or add the minimum explicit one if absent.
- Write failing tests proving the current revision intent leaves session revision/goal/planning state unchanged and that partial/ambiguous/stale turns cannot mutate it.
- Implement one revision application path bound to the target session, current trusted revision, accepted turn ID/text, existing planner binding, and remaining budgets.
- Atomically invalidate stale approvals/plans before revised planning resumes; preserve completed verified action history without replay.
- Wire Android/runtime flow so accepted typed and spoken revisions use the same trusted operation and explicit outcome.
- Add restart/race/duplicate/terminal-session negatives and run canonical + Android verification.

### Acceptance
- A final accepted typed or spoken revision for the uniquely resolved active task changes only that intended session through a trusted runtime operation and produces an incremented/new trusted revision state.
- Partial speech, synthesized echo, ambiguous reference, missing target, stale prior revision, duplicate submit, or cross-session substitution executes no revision.
- All approval/request/action authority bound to the prior session revision is invalid before revised planning can execute.
- Existing completed verified actions are not replayed merely because the request was revised.
- Planner/provider binding and consumed session budgets do not reset or switch silently during revision.
- The revised request enters the same policy/approval/execution/verification/audit path as any other trusted planning iteration.
- Typed fallback and spoken final turns converge on the same revision semantics.
- This task does not perform TASK-020 durable workflow-artifact invalidation or grant generic mutation authority.

### Verification
- TDD RED→GREEN around the current test that proves revision_intent is non-mutating.
- Target-session/current-revision/turn-ID binding and duplicate/stale-race tests.
- Partial/ambiguous/echo/cross-session/terminal-session negatives.
- Approval invalidation, consumed-budget preservation, planner-binding preservation, and no-replay assertions.
- App/runtime protocol tests for typed and speech-final revision outcomes.
- Canonical Verify plus Android API matrix and fresh whole-diff architecture/security review; physical spoken revision remains separate TASK-015/TASK-036 evidence.

### Expected result
Phase 2 has a real, bounded conversational revision operation: “make it shorter” can safely revise the intended active task and trigger trusted replanning without stale approval, replay, budget reset, ambiguous authority, or premature Phase-3 workflow semantics.

### Evidence basis
- docs/ROADMAP_1.0.md sequential item 29 explicitly requires voice-driven task revisions, and the Phase-2 exit gate requires the user to revise a spoken request.
- Current lain/app/control.py returns a revision_intent for TASK-011 revision turns but does not apply it.
- Current tests/test_app_turns.py explicitly asserts the target session revision is unchanged after a revision turn, proving routing exists while application remains absent.
- TASK-020 covers later durable workflow revision invalidation, not the current bounded AgentSession revision required before Phase-2 exit.

### Projection basis
- Without this bridge, TASK-015 can test revision routing but cannot prove the product actually revises active work, leaving a direct gap between the current turn manager and the stated voice-first milestone.
- Implementing the AgentSession seam before Phase-3 avoids forcing later workflow-artifact revision semantics into the Phase-2 controller.

### Risks / unknowns
- The existing AgentSession model may not yet expose a single explicit revision transition; extend it minimally rather than adding a parallel session type.
- Revising after consequential effects have already occurred cannot imply rollback; preserve observed effects and only change future planning.
- In-flight planner cancellation/restart ordering must fail closed so two revisions cannot race into concurrent planning.
- Physical speech quality and interruption latency remain separate acceptance evidence.

---


