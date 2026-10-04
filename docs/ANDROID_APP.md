# Android interface

The `android/` module is the first standalone LAIN_OS GUI. It embeds the existing
Python package with Chaquopy in a private `:runtime` process; it does not require
Termux, an external Python installation, a server, or a cloud account on the phone.

## Current source scope

- Kotlin/AndroidX text workbench: command entry, Run/Stop, progress and per-action
  execution/verification results, exact-action approval, and recent sessions.
- One active session with existing finite budgets and atomic checkpoints.
- Non-exported bound service, same-UID Binder checks, bounded typed JSON, and an
  independent Stop channel. Backgrounding requests Stop; rotation keeps the
  selected session without submitting another task. This is on-demand execution,
  not a persistent background service.
- Run and approval wait for embedded Python initialization and durable-state
  inspection. Startup failures are distinct from initialization in progress.
  Reconnect inspects existing state; it never replays queued writes. Requests
  and replies from a previous connection cannot migrate into a new binding.
- Native adapters for the five existing expansion capabilities: battery,
  vibration, toast, clipboard write/private comparison, and confirmed share
  chooser. No receiver selection or generic clipboard read is exposed.
- Explicit user-started microphone capture using bounded 16 kHz mono PCM16,
  with a visible recording state. Rotation preserves truthful capture state;
  ordinary backgrounding, permission loss, lifecycle teardown, capture failure,
  and resource-limit failure release the microphone and discard buffered audio.
  Raw microphone audio is not written to durable storage by this slice.
- Native notification/URI operations and provider-backed planning remain
  unsupported in this interface. The portable CLI keeps its default adapters.

This version uses a deliberately labeled **offline demo planner**, not a language
model. Choose one of these exact commands:

| Command | Action |
| --- | --- |
| Create demo file | Write `demo.txt` in the app's private workspace; overwrite is not automatic |
| Show battery | Obtain approved structured battery fields |
| Show demo toast | Request a short “Hello from LAIN_OS.” toast |
| Vibrate briefly | Request a 200 ms vibration |
| Copy demo text | Set “Hello from LAIN_OS.” and compare privately |
| Share demo text | Request exact-action confirmation, then open a chooser with “Hello from LAIN_OS.” |

Unknown goals become blocked; they do not launch a shell or arbitrary operation.
During later device testing dismiss the share chooser without sending anything.

## Build a debug APK

Requirements: JDK 17 or 21, Android SDK platform 35 and its build tools, and network
access for the pinned Gradle/AndroidX/Chaquopy dependencies. Android Studio can
open `android/` directly. Configure the local SDK through `ANDROID_HOME` or an
ignored `android/local.properties` file containing `sdk.dir=/absolute/sdk/path`.

From the repository root:

```sh
./android/gradlew --project-dir android :app:assembleDebug
```

The official Gradle wrapper is included. If the default Gradle cache is not
writable, select a writable cache using `GRADLE_USER_HOME`. Keep the execution
environment's proxy and CA trust when downloading dependencies.

Expected artifact after a successful build:
`android/app/build/outputs/apk/debug/app-debug.apk`. A debug APK is not a production
release. No release-signing credentials are included. The source bundles
only `lain/`, not repository secrets, session files, or developer configuration.

## Approvals, privacy, and recovery

Approvals are one-use tokens expiring after 120 seconds and bound to the stored
session revision, request ID, and exact action. A planner cannot grant them. Stop,
resume, mutation, and process restart invalidate grants. Sensitive action arguments
are redacted on the interface/history path; clipboard readback stays private.

Data is scoped to the application's private `files/lain` directory. Checkpoints
retain original supplied input and action payloads for trusted recovery, in private
files, as the CLI does. History shows only fixed demo labels and structured results.
Backups and cleartext traffic are disabled. Internet permission is used only by
configured planner transports. `RECORD_AUDIO` is declared for voice input but capture
is owner-started: the runtime permission flow begins only after tapping **Record
voice**, and typed input remains available when permission is denied. No storage
permission is requested. Uninstalling the application removes its private data
through Android's normal behavior.

Stop acknowledges the request while an operation already in flight may settle.
It prevents further steps and records the actual outcome; it does not promise
rollback of an external effect. After process death, the app does not resume or
replay automatically. Select interrupted work and explicitly Resume, approve a
fresh pending action, or Stop it. Existing audit-ahead reconciliation remains
authoritative. Ambiguous requests are never automatically retried.

## Stop scheduling

Stop wake-ups enter the same single-worker queue as runtime steps, so a Stop
arriving while an action enters a confirmation pause still settles cancellation.
A deterministic JVM regression exercises this case alongside APK assembly, lint,
and instrumentation. Version `0.1.4-interface` still requires current physical
acceptance for Stop at the confirmation transition; older emulator or Termux
evidence does not certify a changed APK.

## Verification status

The latest recorded interface verification includes 19 focused Python tests, 251
tests in the canonical portable suite, 251 through the Android portable helper,
APK assembly, instrumentation compilation, and Android lint. The Android emulator
matrix covers API 24 and API 35; the API 24 run passed all nine applicable
instrumentation cases after the compatibility fixes documented in source.

The package is `dev.lain.os`, version `0.1.4-interface` (version code 5), supports
arm64-v8a and x86_64, and has minimum Android API level 24. Debug artifacts are
debug-signed and do not constitute a production release.

Physical-device acceptance remains required for native adapters, Binder behavior,
approval handling, rotation/rebind, interruption, recovery, and measured Stop
latency. Use [the native APK checklist](ANDROID_DEVICE_ACCEPTANCE.md).

Verification commands:

```sh
python -m unittest tests.test_app_control tests.test_app_native -v
python scripts/verify.py
./android/gradlew --project-dir android :app:connectedDebugAndroidTest
```

The current instrumentation set does not cover every physical-device failure
contract. Foreign-application IPC rejection, clipboard restrictions, chooser behavior,
stale approvals, backgrounding races, physical microphone behavior, and measured
Stop latency remain device acceptance work. Voice turn management, speech playback,
barge-in/echo handling, media workflows, provider integrations, and YouTube
publishing are later product stages.

