# TASK-070: owner-device GGUF token-generation smoke (opt-in)

**This exercises only the JNI feasibility probe, not LAIN planner execution.** The app's ordinary Workbench still cannot select or import GGUF. A successful run here is *not* the airplane-mode `Show battery` acceptance test.

## Preconditions

- Use the **same exact PR #41 debug Android APK and Android test APK** from a single successful CI artifact. No release signing or production build is necessary.
- Obtain an appropriately licensed **instruction-capable, quantized, small GGUF model** yourself. Record the model's official source, license, SHA-256, file size, and your device model/ABI/API. Neither this test nor LAIN downloads a model.
- You need ADB (a computer, or another device running a functional ADB client paired to the test phone). **ADB `run-as` works only with the debuggable APK.** Leave normal app data intact; never clear storage to run this test.
- Put the GGUF on the ADB host as `./model.gguf`. Run commands in a trusted host shell, using your own real model path. If the model is too large for the phone or probe limits, stop and report that limit instead of silently using cloud.

## Commands

```sh
# 1. Local hashes: record SHA (lowercase, 64 hex digits) and model bytes.
MODEL="./model.gguf"
MODEL_SHA="$(sha256sum "$MODEL" | cut -d ' ' -f 1)"
printf 'GGUF SHA-256: %s\n' "$MODEL_SHA"

# 2. Install the matching test APKs from one PR #41 Android workflow artifact.
adb install -r ./app-debug.apk
adb install -r ./app-debug-androidTest.apk

# 3. Stream the user-supplied GGUF directly to the debug app's PRIVATE files.
#    Do not put it in publicly readable downloads or send it to a server.
adb shell run-as dev.lain.os mkdir -p files/models
adb exec-in run-as dev.lain.os sh -c "cat > files/models/$MODEL_SHA.gguf" < "$MODEL"

# 4. On the phone enable airplane mode AND switch Wi-Fi off. Then:
adb shell am instrument -w \
  -e class 'dev.lain.os.planner.GgufRealModelHardwareAndroidTest#importedModelGeneratesTokensInAirplaneMode' \
  -e lain_model_sha256 "$MODEL_SHA" \
  'dev.lain.os.test/androidx.test.runner.AndroidJUnitRunner'

# 5. Collect *non-secret* device diagnostic result.
adb logcat -d -s LAIN_GGUF_HARDWARE:I
```

If your shell or ADB version lacks `exec-in`, do not bypass app-private confinement. Use an ADB client that supports forwarding stdin. The test always verifies the exact app-private file hash before attempting generation.

## Interpretation

- `OK (1 test)`, combined with log `LAIN_GGUF_HARDWARE ... result=Generated`, means the supplied model produced nonempty bounded UTF-8 through the JNI probe on that device under airplane mode, **not** that its plan was authorized or executed.
- `FAILURES!!!`: capture test failure, API/ABI, approximate duration, model family and SHA, and whether airplane mode and Wi-Fi off were true. Common failure types: invalid model/tokenizer, unavailable memory, missing native library, I/O and output invalid. Model text itself need not be shared.
- If you omit the `lain_model_sha256` argument, the test is **SKIPPED**. A green default CI run never counts as hardware inference proof.
- The test checks `Settings.Global.AIRPLANE_MODE_ON`, which is **necessary but not sufficient** to prove that all radios are off. Independently verify Wi-Fi/cellular disabled. A hidden network fallback is not implemented in this probe, but end-to-end application networking is a separate acceptance gate.
- Results must be attributed to the exact source head, APK SHA, model SHA, Android build/ABI and device. Record free memory/thermal state and measure time to response. No claim of reliable planner output follows from this smoke test.

## Boundaries

The probe retains its fixed harmless prompt `Reply with OK.` and context/token ceilings. It does **not** provide a model-import UI, verified private model inventory, live cancellation, agent decision validation, policy, approval, or trusted task execution. Those remain explicitly blocked as subsequent design-plan tasks. The purpose of this smoke is to answer the first unresolved hardware feasibility question truthfully.
