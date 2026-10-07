# In Progress

## TASK-014: Implement voice progress narration
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, progress
**Updated:** 2026-10-03

### Goal

Add bounded spoken progress events that narrate useful task state without turning speech delivery into durable workflow truth or making TTS availability a task dependency.

### Scope

- Define short progress-narration events derived from trusted durable task/session state.
- Keep spoken progress separate from durable workflow/task state and planner assertions.
- Route progress narration through the provider-neutral synthesis/playback boundaries from TASK-009/TASK-012.
- Coalesce/rate-limit repetitive progress speech so narration cannot starve work or create unbounded provider calls.
- Allow playback interruption through TASK-013 without cancelling the task.
- Ensure speech/TTS failure never marks the underlying task failed or complete.
- Preserve visual/text progress as the authoritative fallback.
- Do not implement Phase-3 workflow DAG state, publication narration, or provider-specific TTS behavior.

### Dependencies

- TASK-009 complete: speech provider interfaces.
- TASK-011 complete: turn semantics.
- TASK-012 complete: cancellable playback.
- TASK-013 complete: barge-in/echo protection.

### Plan

- Define a minimal progress-event schema sourced only from trusted runtime state.
- Add bounded event-to-utterance formatting with coalescing/rate limits.
- Send narration through synthesis/playback as a non-authoritative side channel.
- Make synthesis/playback errors local to narration and preserve underlying task state.
- Add deterministic tests for event provenance, coalescing, failure isolation, and interruption behavior.

### Acceptance

- Spoken progress is generated only from trusted current task/session state.
- Narration cannot change task status, grant approval, authorize capabilities, or fabricate completion.
- TTS/provider/playback failure leaves the task running or settled exactly as before.
- Repetitive progress events are bounded/coalesced.
- User barge-in can stop progress speech without stopping the task.
- Visual/text state remains available and authoritative when speech is unavailable.
- No raw provider credential enters progress events or narration artifacts.

### Verification

- Focused progress-event/provenance/coalescing tests.
- Negative tests proving narration failure does not alter task state.
- Regression proving spoken “complete” text cannot itself mark work complete.
- Android integration coverage for narration playback/interruption.
- Canonical verification after implementation.

### Expected result

LAIN_OS can speak concise progress while work continues, with speech treated as a fallible presentation channel rather than a source of execution truth.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.6 Voice progress narration: progress events separate from durable workflow state, speech failure never equals task failure, and tasks continue if TTS is unavailable.
- No current TaskPlanner task, open issue, or open PR represents R2.6.

### Projection basis

- Progress narration is the last functional voice slice before R2.7 acceptance and therefore should reuse already-stable turn/playback/barge-in contracts rather than introduce a new authority path.
- Failure isolation is necessary before voice acceptance can truthfully test provider outages and interruption.

### Risks / unknowns

- Exact wording/verbosity policy is presentation-level and should remain adjustable without changing durable state contracts.
- Aggregate speech-provider cost budgets may be refined later with Phase-3 workflow budgets; this task needs only local bounded/rate-limited behavior.
- Reference-device latency and full voice-session acceptance belong to R2.7.

---
