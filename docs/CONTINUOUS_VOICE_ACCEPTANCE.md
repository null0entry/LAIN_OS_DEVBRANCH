# TASK-082 continuous voice — physical acceptance checklist

Status: **engineering candidate, NOT physically accepted**. PR #60 remains draft pending
exact-head Android tests, privacy/lifecycle review, and tests on the Moto G Play 2026.

## What the installed UI does

Open Workbench. Grant Android's RECORD_AUDIO permission and, on Android 13+,
POST_NOTIFICATIONS. Tap **Start listening** while Workbench is foregrounded.
The new microphone foreground service must show **LAIN is listening locally**
and its **Stop** notification action. Tap **Install or configure offline speech
language** if the OS has no installed on-device recognizer/language. Without
one, service startup fails closed and typed/manual input remains functional.

Commands to test:
- `LAIN, flashlight on` / `LAIN, flashlight off`
- `Hey LAIN`, wait less than 5 seconds, then `volume down`
- `lane, set media volume to 40 percent` (alias enabled by default)
- `LAIN stop`: Stop the active task; continue listening
- `LAIN stop listening`: Stop mic session and its voice-owned work
- `airplane`, `explain LAIN` and casual ambient speech: no task or transcript

All activated commands go through the existing `turn_submit` speech path
and policy/verification. Speech never creates approval tokens; SMS commands
open a compose draft only after exact-action approval in Workbench, then
the owner taps Send in the messaging app. No direct or covert SMS sends.

## Physical checks required before acceptance

1. Clean install and upgrade, offline recognizer installed/uninstalled,
   denied/revoked mic and notification grants, language download/unavailability.
2. Verify persistent microphone notification and **Stop** action function while
   another app is visible and screen is off, including Android 16 restrictions.
3. Confirm isolated activation, five-second expiry, duplicate results, busy
   task rejection, local Stop when Qwen/cloud is unavailable, no replay of
   uncertain submissions, and no app/TTS echo acceptance.
4. For direct controls verify real torch callbacks, volume bounds and readback,
   WRITE_SETTINGS permissions, app launch/settings user-tap limitations.
5. Verify manual recorder exclusivity, TTS pause/resume, screen-off microphone
   resources, process termination/restart, battery use and 4 GB RAM budget.
6. Verify installed APK package `dev.lain.os`, source SHA, SHA256, ABI support,
   Android version and exact CI run before identifying an APK as ready.
7. Optional Accessibility app interaction remains **UNIMPLEMENTED** on PR #60;
   registry definitions alone do not grant capability and must fail closed.

## Evidence limits

CI Kotlin/Python/JVM tests cannot prove that an OEM offline speech recognizer
works with continuous sessions on a physical 2026 Moto G Play. No attempt is made
to self-grant OS accessibility/permission settings, bypass lock screens, auto-start
the microphone after reboot/force-stop, or send audio to a cloud speech provider.
A failed or missing Android service/recognizer is a genuine test blocker rather
than a successful action.

Exact physical results should be recorded in TASK-075/TASK-082 without changing
the meaning of any CI-only evidence.
