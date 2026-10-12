# Native APK physical acceptance

This checklist validates the new standalone APK. Prior Termux acceptance and
emulator results do not establish physical toast, vibration, clipboard, or
chooser behavior for these native adapters.

Install the current debug APK, package `dev.lain.os`, version `0.1.4-interface`
(code 5). Record the APK SHA-256, Android version, device model, and date.
The APK uses an offline demo planner and needs no provider credentials.

For a fresh install or cleared app data, first complete the bootstrap checks below.
After choosing **Continue with Demo**, follow the native capability checks.

## Clean-data first-run setup

Physical clean-install acceptance remains **UNVERIFIED** until these checks run
on a stock or factory-equivalent device. Record the exact APK hash and device/API.

1. Uninstall LAIN_OS or clear its app data through Android Settings. Install one
   APK without Termux, Tasker/Automate, Shizuku, root, ADB, or external Python.
2. Open LAIN_OS from its launcher icon. Expect **Set up LAIN_OS**, an available
   **Continue with Demo**, and a capability scan. No permission dialog opens
   automatically. Rotate or close/reopen before finishing; setup stays incomplete.
3. Tap **Enable microphone** and deny the Android request. Typed commands and
   Demo remain available. Reopening setup must not request microphone repeatedly.
4. Tap **Configure planner**. Expect the existing Planner Settings, including
   document-picker GGUF import. Return without configuring a model: Demo remains
   available and setup has not completed automatically.
5. Tap **Continue with Demo**. Expect the workbench with the built-in Demo selected.
   Close/reopen from the launcher; it should go directly to the workbench.
6. Repeat with cleared data, grant microphone, then revoke it through Android
   Settings before finishing setup. On returning, voice availability must reflect
   revocation while unrelated battery/share capabilities remain available.

Permission readiness is separate from installed speech-service availability and
verified model inference. Recommended-model downloading is a later feature.

## TASK-073 universal typed and final-speech bridge

Physical acceptance is **UNVERIFIED** until performed on a declared installed
APK. Record the APK SHA-256, device model/API, selected planner profile,
offline/online state, and permission availability. Emulator CI proves only its
respective API and fixture behavior.

1. Start with **Demo**. Type **Show battery** and tap Run. Verify a new task
   appears, executes `android.battery_status`, and shows a truthful verification
   outcome and audit. Stop or finish the task before the next command.
2. On Android 13+ with local offline recognition available, grant microphone
   explicitly. Record and finalize **Show battery**. Verify the accepted final
   speech uses exactly the same task/permission/execution/verification path as
   typed input. No task may start on partial or low-confidence speech.
3. Type **What can you do?** while idle. The result must appear as an assistant
   conversation response with **no session or device action created**.
4. Type **Send it** without an identified target. Expect clarification and no
   capability invocation; do not grant approval merely because the model
   answered.
5. Start a task, then issue a final typed or spoken revision. The same session
   must receive the revision and invalidate any previous exact approval.
   **Stop talking** or **Stop response** must not stop the task; **Stop task**
   must remain independently reachable.
6. Rotate/recreate the Activity while a speech final is being delivered.
   An already accepted speech result must not create a second session. A
   genuinely new recorded final utterance may create a new task after the first
   settles.
7. Select **On-device Model**, import a verified local GGUF, and repeat an
   explicit device request and a non-executing conversational question in
   airplane mode. Record model SHA-256, routing/result and timing. A model
   load/busy/invalid-output failure must clarify or report unavailable, never
   silently switch to cloud or execute an unclassified action.
8. Deny/revoke an optional permission, repeat a supported command, and record
   truthful unsupported/denied behavior. No missing grant may be fabricated,
   and no raw model-generated action payload may bypass policy.

For every path record **actual** route (`chat/task/clarify/revision`), session
identity where applicable, action status, verification status, and relevant
redacted audit evidence. Physical voice recognition and on-device GGUF
inference are not established merely by CI passing.

## Native capability checks

1. Open LAIN_OS. Wait for “Local runtime ready”; Run must remain disabled until
   initialization and saved-state inspection finish.
2. Select **Show battery**, then Run. Expect execution success, verification
   PASSED, and structured approved fields including percentage.
3. Run **Show demo toast**. Observe the toast physically. Expect execution
   success and LIMITED verification; LIMITED alone is not evidence of the toast.
4. Run **Vibrate briefly**. Observe vibration physically on supported hardware.
   Expect execution success and LIMITED; a device without a vibrator should
   safely report unsupported.
5. Run **Copy demo text**. Expect success and PASSED when Android permits the
   immediate private comparison. Restricted clipboard access may produce a
   limited result; record it rather than claiming a match. No clipboard getter
   or private observed clipboard value should appear in results/history.
6. Run **Share demo text**. Before approval, expect PAUSED CONFIRMATION, redacted
   content, and no chooser. Approve exactly that stored action. Observe the
   chooser, then dismiss it without selecting a destination or sending anything.
   Expect LIMITED, not independent proof of delivery.
7. Start another share task, leave it unapproved, and tap Stop. Expect CANCELLED
   with no chooser. Also repeat several times, tapping Stop as the task reaches
   its confirmation pause; cancellation must settle without a second Stop or
   reconnect. Rotate the app and confirm no duplicate task is submitted.
8. Start a share task and leave it paused. Terminate only this app's processes
   through Android's app controls (or `adb shell am force-stop dev.lain.os`).
   Reopen LAIN_OS. No chooser or automatic execution should occur. Inspect the
   durable task, then Stop it or explicitly approve its fresh stored action;
   dismiss any chooser without sending. Record recovery behavior.

Report each step as pass/fail/unsupported with actual displayed execution and
verification states. Include any startup, disconnect, or crash symptoms. Never
paste private clipboard contents, credentials, or sensitive logs into the report.

The debug build and portable tests are separate evidence. Physical acceptance
remains open until physical observations are recorded for the final APK.
