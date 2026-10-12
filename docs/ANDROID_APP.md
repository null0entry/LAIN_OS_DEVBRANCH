# Android app

The `android/` module embeds the Python runtime and native GGUF inference in a
private `:runtime` process. Launching the app requires no Termux, external Python,
root, Shizuku, server, or cloud account.

## Setup and conversation

On first launch, choose Demo for fixed offline commands, or configure a planner.
Typed input works without microphone or notification permissions.

- **Demo:** the six exact commands below use deterministic plans.
- **On-device:** download the recommended Qwen3 GGUF or import a supported GGUF,
  then save and select its on-device profile. The download verifies the pinned
  size and SHA-256 before use. The recommended model is approximately 1.28 GB;
  its private copy and optional public Downloads copy require separate space.
- **Cloud / local server:** configure an HTTPS OpenAI-compatible endpoint and
  model. Cloud credentials remain in Android Keystore. Local-server mode connects
  to a server; on-device mode runs the GGUF on the phone.

Tap **Start model** to keep the selected offline GGUF in memory. An animated
progress bar appears while it loads; the status changes to **Offline model ready**
when loading succeeds, or shows the failure. **Stop model** releases the model.
The Workbench refreshes status when you return to it. Loading is indeterminate
because the native loader does not provide a reliable completion percentage.

The Workbench accepts typed and final spoken turns. Ordinary conversation uses
bounded prior turns, assistant replies, and trusted task observations. Cloud,
local-server, and on-device profiles generate their replies through the selected
transport. No cloud fallback occurs from on-device mode. Chat responses cannot
execute capabilities or approve actions.

Qwen3 prompts disable thinking using its non-thinking chat template so the bounded
router and planner can request JSON without a reasoning preamble. Native decoding
constrains routing fields and task JSON, including the supplied capability names;
follow-up plans cannot copy historical IDs or add trailing non-JSON text. Successful
history stays read-only. Only strict validated proposals enter the controller. Exact built-in commands bypass model
routing and planning even when a model is selected.

A generating reply prevents another submission until it settles. App restart
marks interrupted replies as failed and leaves them available to inspect; it does
not replay model calls. Accepted turns and replies are durable; partial speech is
provisional and raw microphone audio is not persisted.

## Device tools

| Capability | Native behavior | Verification |
| --- | --- | --- |
| `android.battery_status` | Structured battery reading | Validated reading |
| `android.vibrate` | Bounded vibration | Command acceptance only |
| `android.toast` | Short toast | Command acceptance only |
| `android.clipboard_set` | Clipboard write and private immediate comparison | Match, mismatch, or unavailable readback |
| `android.share_text` | Open a share chooser after exact-action approval | Chooser acceptance only |
| `android.notify` | Post an app notification | Command acceptance only |
| `android.open_uri` | Open an HTTP, HTTPS, geo, or mailto URI through a VIEW intent | Intent acceptance only |

Disabled notifications, denied permission, or a missing URI handler report
unavailability. **Enable notifications** appears in the Workbench when app
notifications are disabled. Android 13+ asks for notification permission only
when that button is tapped; older versions open app settings if needed.

Limited verification means Android accepted the operation, not that the user
saw its effect. Successful limited operations can finish their task without
upgrading that evidence. Clipboard readback values remain private.

Filesystem writes, copies, and moves stay within the configured private workspace.
Other integrations retain their own configuration requirements; an unavailable
adapter never simulates success.

### Fixed Demo commands

| Command | Action |
| --- | --- |
| Create demo file | Write the next available `demo.txt` / `demo-N.txt` slot |
| Show battery | Read approved structured battery fields |
| Show demo toast | Request “Hello from LAIN_OS.” |
| Vibrate briefly | Request a 200 ms vibration |
| Copy demo text | Write “Hello from LAIN_OS.” and compare privately |
| Share demo text | Request approval, then open a chooser with “Hello from LAIN_OS.” |

## Voice and lifecycle

Voice capture starts only after tapping **Record voice** and granting microphone
permission. Recognition of captured PCM requires Android 13+ and an installed
on-device recognizer that supports the captured-audio request. Offline speech
output requires an installed offline TTS voice. The manifest declares discovery
of recognition and TTS services for Android package visibility.

Run and approval wait for embedded Python initialization and durable-state
inspection. Rotation preserves selected work without submitting it again.
Backgrounding requests Stop for active work and cancels model transport. Stop
prevents further steps while an operation already in flight may settle; inspect
its execution and verification results.

Approvals expire after 120 seconds and are bound to the exact action and stored
session revision. Stop, resume, mutation, and process restart invalidate grants.
Interrupted tasks require explicit recovery; the app never resumes automatically.
A non-exported Binder service and same-UID checks protect the control interface.
Data stays under the app's private files directory. Backups and cleartext planner
traffic are disabled.

## Build and verification

Use a full JDK 17 or 21, Android platform/build tools 35, NDK 29.0.13113456, and
CMake 3.22.1. Configure `ANDROID_HOME` or an ignored `android/local.properties`.
The wrapper downloads the pinned Gradle and dependency versions. Preserve the
execution environment's proxy and CA trust.

```sh
python -m pip install -e .
python scripts/verify.py
bash tests/native/run_gguf_lifecycle_test.sh
./android/gradlew --project-dir android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./android/gradlew --project-dir android :app:connectedDebugAndroidTest
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`, package
`dev.lain.os`, version `0.1.4-interface` (code 5), minimum API 24, with arm64-v8a
and x86_64 runtimes. Debug signing is for testing; release signing remains
owner-configured.

GitHub Android checks build the app and run instrumentation on API 24 and API 35.
Their APK artifact includes `build-info.json` and `SHA256SUMS`. These bind the APK
binary to the checkout, workflow run, package version, and bundled architectures.
After extracting the debug APK directory, run `sha256sum -c SHA256SUMS` before
installation. Artifacts require GitHub access and expire under its retention rules.

Compilation and emulator results do not establish physical model performance,
microphone behavior, clipboard restrictions, chooser behavior, or Stop latency.
Use [the device acceptance checklist](ANDROID_DEVICE_ACCEPTANCE.md) for those checks.
