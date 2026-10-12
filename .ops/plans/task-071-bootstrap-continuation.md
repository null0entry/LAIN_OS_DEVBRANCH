# PR #46 bootstrap continuation

Scope: TASK-071 from the approved clean-install plan on design PR #45, Task 2 only. Recommended model acquisition remains TASK-072.

### Plan
- Confirm the existing bootstrap instrumentation compilation RED on head `1cf2163`.
- Persist only setup version/completion, and refresh planner/capability state on resume.
- Provide Demo, planner configuration, explicit microphone permission, and Finish setup actions.
- Make bootstrap the launcher; completed setup routes to the workbench.
- Verify persistence, launcher routing, grant/deny/revocation, recreation and planner entry; run Verify and both Android API jobs.

### Decisions
- The stacked implementation branch has a historical, unrelated TASK-071 in Done. Preserve that section and track this PR here until the design branch task queue is integrated; duplicating the ID would violate canonical verification.
- The existing workbench remains directly launchable for compatibility with its instrumentation and device diagnostics; normal launcher entry owns first-run routing.
- No future special-grant capability is enabled or permission declared. The existing broker separates Settings grants and optional dependencies; default capabilities currently require only microphone permission.
- This checkout is isolated from other work and already on the PR feature branch.

### Evidence
- Android run 38187749149 fails compiling BootstrapActivityAndroidTest because BootstrapStateStore, BootstrapViewModel, BootstrapActivity and bootstrap resource IDs do not exist.
- Local canonical verification: 325 portable tests passed; all eight native GGUF lifecycle groups passed.
- Fresh review found failed-commit in-memory completion, missing on-device speech-provider detection, and blocking bootstrap storage operations. Regression checkpoint `f2ee585` adds failed-commit and missing-provider tests before their fixes.
- Android run 38189758249 confirmed the two regression tests fail by assertion on both API jobs (108 JVM tests, exactly two failures). Fixes restore the previous completion value after a failed commit, probe the actual on-device recognizer, and move planner reads/completion writes to ViewModel-owned background work with pending completion UI.
