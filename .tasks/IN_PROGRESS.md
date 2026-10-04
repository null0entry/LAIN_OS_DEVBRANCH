# In Progress

## TASK-011: Implement bounded voice turn manager
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, conversation
**Updated:** 2026-10-04
**Integration:** PR #27 retargeted to `main`; branch is currently non-mergeable after TASK-010 squash integration and requires conflict reconciliation.

### Goal

Add the durable conversation-turn coordination layer that converts final transcripts or typed revisions into ordered task-facing turns without allowing partial speech or ambiguous references to authorize consequential work.

### Scope

- Assign monotonic turn IDs for each accepted user turn.
- Keep partial/interim transcripts separate from final accepted transcripts.
- Track an explicit active-task reference for conversational follow-ups.
- Route clear revisions to the referenced task/workflow without mutating already-approved consequential payloads in place.
- Surface ambiguous referents as a clarification-required state rather than guessing.
- Preserve typed input as an equivalent complete turn source.
- Do not implement microphone capture, speech playback, barge-in, workflow DAG semantics, or provider-specific speech logic in this task.

### Dependencies

- TASK-009 complete: bounded speech provider interfaces.
- TASK-010 complete: Android microphone lifecycle producing final/partial capture results.
- Existing durable agent-session identifiers and approval semantics remain authoritative.

### Plan

- Define the smallest turn record/state model with monotonic IDs and explicit source/finality fields.
- Add active-task reference tracking without duplicating durable workflow authority.
- Route only final accepted turns into task/planner input; partial transcripts remain non-authoritative UI state.
- Add explicit ambiguous-reference and revision-routing outcomes.
- Add deterministic tests for turn ordering, partial/final separation, active-task reference, ambiguity, and revision routing.

### Acceptance

- Accepted turns receive strictly monotonic IDs.
- Partial transcripts cannot start work, grant approval, or alter durable task state.
- Final transcripts and typed messages enter the same bounded turn pipeline.
- Follow-up references resolve only when an active target is unambiguous.
- Ambiguous referents require clarification and execute nothing.
- Revision routing preserves prior approvals/effects and creates a new revision intent rather than silently mutating an approved consequential payload.
- Turn state contains no raw provider credential or hidden capability authority.

### Verification

- Focused unit tests for ordering, finality, reference resolution, ambiguity, and revisions.
- Negative tests proving partial transcripts and ambiguous turns cannot trigger planner/executor work.
- Regression proving typed input follows the same turn contract.
- Canonical portable verification and Android integration checks after platform wiring.

### Expected result

LAIN_OS has a deterministic conversation-turn boundary that later playback, barge-in, and workflow features can consume without conflating speech fragments with authoritative user intent.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.3 Turn manager with monotonic turn IDs, final-vs-partial transcript separation, active-task reference, ambiguous referent clarification, and revision routing.
- No current TaskPlanner task, open issue, or open PR represents R2.3.

### Projection basis

- A stable turn boundary is required before speech playback/barge-in can safely distinguish conversational interruption from task cancellation or authorization.
- Explicit finality and reference semantics prevent later voice features from treating low-confidence/partial speech as consequential intent.

### Risks / unknowns

- Full workflow revision invalidation belongs to Phase 3; this task should expose revision intent/reference only, not pre-build the DAG scheduler.
- Multi-workflow targeting is beyond the current single-active-workflow 1.0 scope and should not broaden this contract.
- Low-confidence STT scoring may be provider-specific later; this task should depend on explicit finality/clarification semantics rather than a hard-coded confidence model.

---

