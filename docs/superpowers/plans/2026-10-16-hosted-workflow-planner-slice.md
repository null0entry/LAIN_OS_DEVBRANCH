# Phase 2 slice: Hosted planner actions to trusted workflow

**Date:** 2026-10-16
**Dependencies:** PR #61 design; PR #62 executable workflow foundation
**Scope:** Integrate the already shipped OpenAI-compatible `AgentPlanner` protocol into the new trusted workflow compiler, without allowing model text to execute or consent.

## Outcome
One bounded model `continue` decision produces a persisted graph of sequential, preflighted actions. The user-facing application may then run that graph through the established workflow executor; no new action executor is created. `complete` and `blocked` model decisions must not falsely claim actual tool success.

## Tasks
1. Red tests: successful two-action proposal compiles and executes real operations; unsupported tool, invalid args, no-action decisions and overwrite confirmation fail closed; action IDs are assigned by trusted code.
2. Implement `lain/workflows/intent.py`: use `AgentPlanningService.decide`, convert a verified `CONTINUE` batch to a linear dependency graph, submit through `WorkflowRunner.create`; return narrow non-sensitive status and workflow ID. Unsupported/complete/blocked produce no side effect or fabricated receipt.
3. Green tests: full Python canonical Verify; Android unaffected because pure Python library change. Build a later adapter over `NativeBridgePlanner` using already approved secure HTTPS provider bindings.
4. Future UI integration: one canonical accepted voice/text turn invokes the planning adapter; expose the verified result only after runner completion; never send approval tokens to model context.

## Limits
- This slice composes sequential actions with literal arguments. Dynamic typed artifact references are validated by the workflow runner but the older AgentPlanner JSON schema currently cannot propose these references; do not infer full arbitrary typed DAG model support.
- No Android view, screenshot capture, group messaging integration or physical device acceptance is claimed.
