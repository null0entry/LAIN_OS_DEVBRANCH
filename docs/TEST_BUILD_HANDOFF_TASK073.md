# TASK-073 stock-device debug APK test handoff
Date: 2026-10-12

## Exact test candidate
- **Source:** `null0entry/LAIN_OS_DEVBRANCH`, commit `0b3245aae72ad9834d701466572afc00a27b97d1` on `task-073-universal-command-bridge`, PR #48.
- **CI Verify:** [#38246030584](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/38246030584) — success on this commit.
- **CI Android:** [#38246030626](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/38246030626) — success, API24 and API35 build/unit/lint/emulator instrumentation on this commit.
- **GitHub artifact:** [API24 debug APK and reports, artifact #11746362204](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/38246030626/artifacts/11746362204) (ZIP, account access may be required). [API35 duplicate-build artifact #11746237583](https://github.com/null0entry/LAIN_OS_DEVBRANCH/actions/runs/38246030626/artifacts/11746237583) is also present, but the hash below refers **only to the API24 APK**.
- **GitHub API artifact expiration:** 2027-01-10T11:57:58Z (reported by GitHub at handoff); access can change sooner.
- **ZIP entry:** `outputs/apk/debug/app-debug.apk` (do **not** install `outputs/apk/androidTest/debug/app-debug-androidTest.apk`).
- **APK SHA-256:** `900d2fc7c272fe6640391453ed252b5209e75b95eb1cc950bd4c85be92ab2b9a` (calculated from extracted binary).
- **APK byte length:** `64387310`.
- **ZIP SHA-256:** `787970f7df863cb983065f919fe4e9765961dc4e128ce73f346af050c261abe6`.
- **ZIP integrity check:** all ZIP entries readable, no corrupt member.
- **APK integrity check:** all inner APK ZIP entries readable, no corrupt member.
- **App ID/variant/version:** `dev.lain.os`, debug, versionCode `5`, versionName `0.1.4-interface` (APK output metadata).
- **Native ABI dirs seen inside APK:** `arm64-v8a`, `x86_64`. Manifest SDK policy from `android/app/build.gradle.kts`: minSdk 24, targetSdk 35. This handoff does **not** claim an independent parsed AndroidManifest.xml SDK inspection.

## On-device install and smoke test
1. Download the API24 ZIP via the GitHub artifact link. Extract `outputs/apk/debug/app-debug.apk` using an archive manager, then check the APK's SHA-256 against the value above. GitHub may require sign-in; a missing/expired artifact must be reported as unavailable, never substituted with another build.
2. Open the APK with Android's file manager/package installer. Permit installs from that file-manager source if Android prompts; no Termux, Shizuku, root, Automate, ADB or developer environment is required for installing and launching this APK. This is a **debug APK**, not an official Play release; only install from the verified artifact if you trust the source.
3. Complete the LAIN first-run setup in Demo mode. Type `Show battery`. Verify the existing trusted task/session path and status.
4. In Planner Settings use **Download recommended Qwen** or **Import on-device GGUF model**. The suggested download is about 1.28 GB and needs network and adequate storage for acquisition; after verified import the selected on-device profile can run offline.
5. Test accepted final speech where local speech recognition and microphone permission are available. Low-confidence/partial speech must never create a task; voice provider unavailable must leave typed input usable.
6. Record device/Android API, memory/latency/thermal observations, APK and model hashes, offline mode, permission outcomes, and exact audit/status. Run non-command questions and ambiguous requests to verify no unintended device action. A local model answer in the **Workbench** is outside TASK-073's bounded router slice (TASK-076); existing ordinary Qwen chat has a separate screen.

## Evidence boundary
Both emulator matrices and exact-head Verify are **PASS**. Physical-device APK install, local speech, on-device GGUF inference, action execution, privilege grants and resilience remain **UNVERIFIED** as of this record. TASK-075 owns that physical acceptance. This document is a reproducible **test-build handoff**, not final release signing, publishing, or warranty of success on a 4-GB-RAM phone.

TaskPlanner: TASK-077. Canonical queue: [issue #6](https://github.com/null0entry/LAIN_OS_DEVBRANCH/issues/6).
