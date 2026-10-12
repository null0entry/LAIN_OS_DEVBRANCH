# Native Qwen GGUF text chat (Android)

This slice is a **foreground, on-device-only** conversational interface using LAIN_OS's
embedded native `llama.cpp` GGUF engine from the stacked offline integration.
It does **not** call Termux, external HTTP endpoints or the trusted task executor.

## Prepare the model

Android sandboxes Termux's `~/models/` directory. The model must be imported into
LAIN_OS's own private model store; LAIN_OS cannot execute `llama-cli` installed in Termux.

From Termux, to make a downloaded model visible to Android's document picker:

```sh
termux-setup-storage
cp ~/models/qwen.gguf ~/storage/downloads/qwen.gguf
```

Ensure sufficient free storage for the shared copy and app-private import. The source
model should be a compatible Qwen2.5 0.5B Instruct GGUF, not a download stub or HTML page.

Install an APK containing PRs #41, #42, #43 and this feature branch. In LAIN_OS:

1. Open **Planner Settings → New Profile → On-device Model**.
2. **Import GGUF** from Downloads. Wait for SHA-256 import to finish.
3. **Save** the profile and make sure the selected profile shows the imported hash.
4. Tap **Chat with On-device Qwen (offline)** near the top of the Workbench.
5. Type `Hello LAIN`, tap **Send** and wait for an actual model response.
6. Send a second turn to confirm bounded conversation context.
7. **Stop response** cancels the in-flight generation; **New chat** resets local history.

The UI stores at most 32 bounded user/assistant messages in private app preferences,
injects no task capabilities, and trims oldest messages to an 8 KiB Qwen prompt.
The existing native engine retains its 4096-token context, two CPU threads,
256-token generation bound, checksum-verified private model and cancellation lease.

## Evidence boundary

The prompt builder has deterministic JVM tests. A successful CI APK build and mocked
generator tests are **not** physical inference proof. Confirm real Qwen output,
memory usage, load time and Stop on the specific handset before calling this feature
hardware-accepted. There is no cloud or Termux fallback.

Known constraints: foreground-only, one generation at a time, no streaming,
Qwen2.5 Instruct chat template only, selected native GGUF profile only. Other
provider profiles remain unchanged and still use the Workbench task interface.
