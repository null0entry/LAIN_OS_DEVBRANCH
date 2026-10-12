# Native GGUF lifecycle tests

Run from the repository root:

```sh
bash tests/native/run_gguf_lifecycle_test.sh
```

The harness compiles the production `lain_gguf_probe.cpp` with a host C++17
compiler. Controlled JNI and llama.cpp adapters let it call the real native
registration, Stop, release, and generation entry points without Android or a
downloaded model. Synchronization gates issue Stop inside model loading, prompt
evaluation, and token evaluation, then assert the engine callback actually aborts,
resources are released, and another generation can acquire the model lease.

It also covers Stop before the worker starts, per-request cancellation isolation,
the eight-entry registration limit, released ID reuse, concurrent model access,
and the prompt microbatch bound. The adapters do not verify llama.cpp internals,
JNI ABI compatibility, inference quality, or Android hardware behavior; those
remain covered by the Android build and device acceptance procedure.

Injected engine failures and an allocation exception verify cleanup after partial
and complete resource acquisition. To run with host memory/undefined-behavior
checks when the compiler supports them:

```sh
CXXFLAGS='-fsanitize=address,undefined -fno-omit-frame-pointer' \
    bash tests/native/run_gguf_lifecycle_test.sh
```
