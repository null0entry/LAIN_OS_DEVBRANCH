# On-device GGUF task acceptance

**Status: physical execution unverified.** Model import, profile selection and a native planner path exist in the integration source. A compiled native library, a matching file hash, and passing tests with synthetic model bytes do not establish that a real model can plan and complete a task.

The initial prompt format targets Qwen2.5 Instruct. Use the downloaded Qwen2.5 0.5B Instruct Q4_K_M GGUF for the first acceptance attempt. Other families and chat formats require separate compatibility evidence. LAIN does not download models or select a network fallback when an offline request fails.

## Prepare the exact APK and model

Install a debug APK built from the integration source and record its source commit and SHA-256. Keep the model's official source, license, filename, byte size and SHA-256 with the receipt. The current import ceiling is 2,000,000,000 bytes with a 96 MiB storage reserve; importing needs space for a temporary private copy. This file-size ceiling is not a RAM guarantee.

On an ADB host, these commands collect identity information without clearing app data:

```sh
sha256sum ./app-debug.apk ./model.gguf
wc -c ./model.gguf
adb install -r ./app-debug.apk
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.sdk
```

Put the model in the phone's Downloads directory using the normal file-transfer workflow. In LAIN, open Planner Settings, choose **New Profile**, enter a name, select **On-device Model**, and tap **Import GGUF**. Choose the downloaded file. Wait for import to finish, then tap **Save**. Saving verifies the private copy and selects the profile; the active planner must display this profile and its full SHA-256 identity. Reopen the app and confirm the same saved profile remains selected.

Import and Test connection validate the fixed header and/or file identity. The diagnostic **Model integrity verified. Inference has not been tested.** does not assert tokenizer, tensor or model-load compatibility. Native loading performs additional checks when a task starts. Save should leave the interface responsive while the file is verified.

## Execute with radios disabled

Enable airplane mode and separately disable Wi-Fi, cellular data and any tethering. Verify the phone has no network connection before starting. An ADB airplane-mode setting alone does not prove every radio is disabled.

Enter **Show battery** and tap **Run**. Record the elapsed time and the visible action results. A passing result requires a real model proposal for `android.battery_status`, the trusted executor's battery result, its verification status and the durable task/audit record. A planner message saying it completed the task is insufficient. The task must not silently run under the built-in demo or a cloud/local profile.

The native path currently uses CPU inference with two threads, at most 4,096 context tokens, 256 generated tokens and 65,536 output bytes. Serialized planner input is limited to 12,288 UTF-8 bytes to reserve space for the chat envelope. A short valid-looking header is insufficient to make a runnable model. Invalid JSON, unsupported models, exhausted bounds and missing/corrupt model files must fail visibly without network retry.

## Stop and resource checks

Start another request and tap **Stop** during model verification/loading and again during generation. Record time from Stop to the visible terminal state and to CPU/memory settling. Start a later task to confirm the model lease is released. Repeat with the app moved to the background; a hidden task must not continue to execute actions.

Sample memory before import, during a task and after Stop. On an ADB host:

```sh
adb shell dumpsys meminfo dev.lain.os
adb shell top -b -n 1
adb shell dumpsys thermalservice
```

Callback cancellation is cooperative. No measured Stop latency, memory ceiling or thermal suitability is established until these checks run on the target phone. Report load failures or process termination as failures, preserving the exact model and APK identities.

## Receipt

Record each result as passed, failed or unverified, with device model, ABI/API, source commit, APK/model hashes, radio state, import/save timing, task timing, peak observed memory, thermal state and Stop/lease-release observations. Include task/action verification and audit evidence; omit credentials and unrelated private data. A skipped optional model test remains unverified. Token-generation smoke and full trusted task execution are separate results.
