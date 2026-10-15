# Continuous LAIN commands and Android device control

Date: 2026-10-14
Status: Approved by owner on 2026-10-14; implementation in progress

## Intended outcome and target

The owner wants to say “LAIN” followed by a task and have LAIN_OS carry it out,
including flashlight, volume and settings changes, while another app is open or
the screen is off. The owner selected the activation phrase and identified a
Moto G Play 2026. Motorola's published specifications list Android 16 and 4 GB
of physical RAM. Installed OS version and speech-provider availability still
require checking on the actual phone.

Success means that starting listening once establishes a visible, stoppable
voice session; supported commands execute without opening Workbench for each
utterance; outcomes are verified where Android permits readback. Routine phone
controls must work without loading Qwen. Complex requests use the existing
planner and registered tools. Ambient speech must not become durable tasks.

“Any task” means expanding supported, permissioned actions and app workflows.
An ordinary Android app cannot acquire every system privilege, bypass a locked
screen, operate every third-party UI, or continue after force-stop. Unsupported
operations must explain the required interaction or permission.

## Current constraints found in the code

- `VoiceCaptureViewModel` records a finite PCM clip and transcribes it afterward.
  Its activity lifecycle cancels capture when the screen leaves the foreground.
- `AndroidOnDeviceSpeechBackend` requires API 33 captured-audio support. Live
  microphone recognition does not require that optional audio-source feature.
- `WorkbenchViewModel.detach()` stops active work on backgrounding. Adding a
  microphone service alone would therefore still interrupt voice-started tasks.
- `RuntimeService` stops work when its last binding disappears. Continuous mode
  needs its own runtime connection and explicit ownership while active.
- Native Android execution has seven registered capabilities. Flashlight,
  volume, brightness and a functioning Accessibility adapter are absent.
- Existing registry validation, policy, exact-action approvals, cancellation and
  verification must remain authoritative for typed and spoken requests alike.

## Approaches and selected design

1. **Live Android on-device recognition plus a microphone foreground service
   (selected):** reuses Android's installed offline recognizer, avoids captured-PCM
   compatibility problems and additional native dependencies. Availability and
   language installation are checked explicitly. Recognition sessions restart
   with controlled backoff; this is not a claim of hardware low-power hotword
   detection or zero gaps between sessions.
2. **An embedded offline speech engine:** provides consistent recognition when a
   vendor recognizer is absent, at the cost of another model download, native
   packaging, memory, licensing and Android 16 compatibility work. If the target
   phone cannot support the selected live recognizer, expose that failure and
   revisit this alternative instead of selecting cloud recognition silently.
3. **Privileged device automation:** root, device-owner management or ADB/Shizuku
   can expose additional settings. These privileges are not prerequisites for the
   stock-phone design, and the app will not attempt to grant them to itself.

Use direct Android APIs for routine controls. Offer an optional, explicitly
user-enabled Accessibility adapter for broader supported app interaction.
Protected settings also have a settings-panel route when direct control is not
available. Platform permissions determine which route can execute.

## Listening and activation

A new user-started `ContinuousVoiceService` runs in the application process as a
microphone foreground service and owns the live recognizer. It binds separately
to the existing private `:runtime` service. Its lifecycle does not depend on an
Activity remaining visible.

The UI provides Start listening, Stop listening, recognizer/language setup and
current state. A persistent notification shows microphone use and includes a
Stop action. Startup requests microphone permission from a visible Activity;
notification setup is explicit. Continuous mode starts only when its notification
channel and notification permission allow a visible Stop control. Denial leaves
continuous mode stopped; manual voice and typed commands retain their existing
permission requirements. The manifest declares the microphone foreground
service permission/type. Android 16 compatibility is tested; current compile and
target SDK values are not changed solely to add this feature.

Continuous mode requires API 31+ and an available offline recognizer. The
recognizer is created with `createOnDeviceSpeechRecognizer`. It never falls
back to a network recognizer. Use the configured device language and check for
an installed offline recognizer/language. When provisioning is supported, a
visible setup action requests the Android speech-language download and reports
its actual outcome. No language download is mislabeled as successful recognition.

Activation rules:

- “LAIN, turn on the flashlight” submits one final command after the prefix.
- “Hey LAIN” is an accepted invocation. The pronunciation alias “lane” is
  enabled by default, configurable and shown explicitly; it does not allow substring matches such
  as “explain” or “airplane” to activate commands.
- “LAIN” alone opens a five-second window for the next final utterance and gives
  a short acknowledgement. Expiry returns to waiting for the activation phrase.
- Partial results never execute actions. Only nonempty final commands of at most 4,096 UTF-8 bytes
  enter the existing speech-turn interface. Preserve rejection of genuinely low
  confidence and acceptance of Android's unavailable-confidence sentinel.
- Recognition callbacks carry a listening epoch and utterance identity. Duplicate
  and stale callbacks cannot dispatch a second task.
- “LAIN, stop listening” stops the voice session locally, independently of Qwen or
  the task runtime. “LAIN, stop” cancels the current task while leaving listening enabled. The notification Stop action is equally independent.
- No raw microphone audio or unactivated ambient transcript is persisted. Accepted
  commands use the existing bounded conversation/audit records.

Routine silence/no-match restarts recognition after 300 ms. Busy/audio/service errors use
backoff of 500 ms, 1 s, 2 s, 4 s, 8 s and 15 s, then stop with a visible error. Permission revocation or an unavailable recognizer enters a
visible stopped/error state. No infinite retry loop, concealed microphone use or
claim of perpetual operation after Android kills the process is allowed.

Routine device controls use a short acknowledgement/status. Listening pauses
around LAIN's own spoken playback, including Workbench TTS,
and resumes afterward if the session is still enabled. Manual capture and
continuous recognition have one microphone owner. There is no simultaneous
second recording session or automatic execution of LAIN's own spoken reply.

## Runtime ownership and task flow

The voice service holds a live runtime connection and an explicit active-session
lease. That lease becomes active only after the microphone foreground service
and runtime connection are established, not merely because a saved preference
says listening was enabled.

When that lease exists, hiding Workbench disconnects its UI connection without
issuing the current automatic Stop. Without the voice lease, existing background
Stop behavior is preserved. Stopping the voice session releases its lease and
settles any voice-owned active work; work must not continue silently after the
owner has stopped its background execution surface.

Commands use the existing `turn_submit` speech route. Deterministic parsing covers
common device commands before model routing; it still produces registered actions
and uses the same argument validation, policy, execution and verification. It
never invokes arbitrary shell commands or Java methods from model output.

Execute one mutation at a time. During an active task, an explicit voice Stop is
handled promptly; other new commands receive a busy response instead of silently
revising an unrelated task or accumulating unbounded work. Ambiguous IPC outcomes
are inspected and not replayed. Pending approvals remain visible and bound to the
exact action. Hearing “approve” does not create an approval token. The activation phrase is
not speaker authentication.

Complex tasks may preload/use Qwen through the existing shared model process.
Optional preloading starts from the visible listening setup while Android permits
the foreground-service start; otherwise the existing bounded cold inference
path is used. Background voice commands do not bypass service-start restrictions.
Simple controls remain responsive without the 1.7B model resident, which matters
on the target phone's 4 GB RAM. Model or memory failures cannot disable the local
Stop-listening action.

## Direct Android controls

Expose explicit typed capabilities through the Python registry, native adapter,
permission broker and verification layer. Boolean arguments are actual booleans;
percentages and stream names are validated before any platform call.

| Capability | Contract and execution | Evidence and permission |
| --- | --- | --- |
| `android.flashlight_set` | `enabled: boolean`; select an available flash-equipped camera and set torch state | CAMERA grant and flash hardware; verify a matching torch callback, otherwise report unavailable/limited evidence |
| `android.volume_get` | One of media, ring, notification, alarm, voice-call streams | Return current/minimum/maximum indices from AudioManager |
| `android.volume_set` | Stream and integer percentage 0–100; map to supported indices | MODIFY_AUDIO_SETTINGS; read back the actual index and report mismatch honestly |
| `android.volume_adjust` | Stream and direction up/down/mute/unmute | Read back state; Do Not Disturb restrictions require their separate user grant |
| `android.brightness_set` | Integer percentage 0–100; set manual brightness mode explicitly | WRITE_SETTINGS special grant; read back both mode and value |
| `android.rotation_set` | Automatic rotation on/off | WRITE_SETTINGS grant; read back the stored system setting |
| `android.screen_timeout_set` | Integer milliseconds 15,000–1,800,000 | WRITE_SETTINGS grant; read back the accepted value; device policy may constrain it |
| `android.settings_open` | Page enum: wifi, bluetooth, display, sound, apps, app_permissions, notifications, accessibility, write_settings or dnd | Navigate when Android permits it; otherwise offer an explicit notification/user-tap route; opening a page does not prove a setting changed |
| `android.app_open` | An installed, launchable package identity | Query launchable packages through a launcher-intent visibility declaration; a launch request is not proof an app workflow completed |

Examples include “LAIN, flashlight on,” “LAIN, turn the flashlight off,”
“LAIN, set media volume to 40 percent,” “LAIN, volume down,” “LAIN, set brightness
to 30 percent,” and “LAIN, open Wi-Fi settings.” All volume commands default to the media stream unless a stream is named.

All permission requests and special-access grants remain OS-owned interactions.
The UI explains missing grants and opens the right setup screen. Unsupported
hardware, camera contention, fixed volume, policy-managed settings and absent
handlers are returned as real failures/unavailability. State readback upgrades
verification only when it actually matches the requested state.

## Optional app interaction and settings breadth

A user-enabled `AccessibilityService` extends registered capabilities for visible
app inspection, selecting a visible element, scrolling, text entry and global
navigation. It is not enabled implicitly. The setup screen describes the access
and opens Android's accessibility settings.

Expose a bounded snapshot with package, window and element identities. A proposed
click/type action must reference the same current package/window and a fresh
snapshot; missing, ambiguous, disabled or stale elements fail instead of falling
back to arbitrary coordinates. Inspect only the active window, at most 128 nodes to depth 20, at most 256
Unicode characters per node text and at most 32 KiB of serialized observation.
Snapshot IDs expire after 120 seconds or an intervening window/content change.
The longer bound accommodates on-device planning on this 4 GB phone. Immediately
before execution, re-read the active window and require the observed context and
selected element to match; a valid timestamp alone never authorizes a stale tap.
The bounded UI contracts are `android.ui.inspect`,
`android.ui.click {snapshot_id,node_id}`,
`android.ui.scroll {snapshot_id,node_id,direction}` (forward/backward), and
`android.ui.set_text {snapshot_id,node_id,text}` (4,096 UTF-8 bytes maximum).
`android.ui.global_action` accepts only home, back, recents, notifications or
quick_settings. A click resolves one current enabled element; it does not search
indefinitely, tap arbitrary coordinates or reuse an expired snapshot.
App text is untrusted observation, not permission or policy authority.

Do not automate the app's own approval controls, OS permission/privilege grants,
credential entry into protected fields or lock-screen bypass. External/destructive
operations retain their existing exact-action approval requirements. When a
settings page exposes an ordinary supported control, the adapter can operate it
under those same rules. It must not claim an unsupported secure setting changed.

Wi-Fi, Bluetooth, airplane mode, mobile data, device security and third-party app
behavior have Android/OEM restrictions. Direct toggling is exposed only when a
supported API and the required authority exist. Otherwise use supported UI
navigation/interaction or explain the needed user step. Accessibility is not a
promise that every app or every system restriction can be automated.

## Lifecycle, privacy and resource boundaries

- Start listening from a visible user action. Continue during normal background
  use and screen-off operation while the microphone foreground service survives.
- Release recognizer, callbacks, microphone ownership and any wake lock on Stop,
  destruction, permission loss or unrecoverable errors. Late callbacks cannot
  restart a stopped session. Use only the wake-lock scope needed for active
  screen-off recording; release it whenever listening pauses.
- Do not start recording after reboot/force-stop or claim such automatic recovery.
  Show that listening must be restarted if Android has ended the session.
- Locked-screen routine controls follow OS permission rules. Operations needing
  a visible app, approval or unlock wait for the owner; do not simulate success.
- Keep queues, recordings, observations and history bounded. No raw audio export
  or upload; an explicitly configured cloud task planner sees only an activated
  command under the existing planner configuration.
- Check runtime capabilities on the actual phone. RAM Boost is storage-backed and
  is not treated as additional physical memory for model budgeting.

## Acceptance evidence

Before claiming this implemented, verify:

1. Activation prefix, five-second arming/expiry, no substring activation, duplicate
   callbacks, final-only execution and immediate local Stop with deterministic tests.
2. Service ownership transfer: backgrounding Workbench preserves work only while
   the real voice lease is alive; stopping/revoking permission tears it down.
3. Actual stream/torch/settings call arguments and readback, including denied
   grants, absent flash, busy camera, fixed volume and settings-policy rejection.
4. One microphone owner, TTS suspension/resumption, silence/error backoff, service
   disconnect, Android process death and no replay of uncertain commands.
5. Accessibility snapshot freshness, ambiguous selectors, credential/approval
   boundaries, missing service and background-activity restrictions.
6. Existing Python/native/Android tests, APK assembly and lint. Add Android 16/API
   36 emulator coverage alongside the supported compatibility matrix and inspect
   packaged native-library alignment/ABI support.
7. A clean, signed debug APK with exact source/checksum metadata for owner testing.
8. On the actual Moto G Play: offline recognizer/language availability, hands-free
   flashlight and volume changes, screen-off and other-app listening, Stop from
   notification, speaker feedback prevention, RAM/battery measurements and tasks
   that genuinely require unlock or settings grants. Emulator tests cannot stand
   in for these physical-device results.

## Sources

- [Motorola specifications for Moto G Play 2026](https://en-us.support.motorola.com/app/answers/detail/a_id/189848/~/specifications---moto-g-play-2026)
- Existing product architecture: `docs/superpowers/specs/2026-10-11-universal-on-device-command-layer-design.md`.
