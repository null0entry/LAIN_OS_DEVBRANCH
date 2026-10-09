#include "jni.h"
#include "llama.h"

#include <unistd.h>

#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <future>
#include <iostream>
#include <mutex>
#include <new>
#include <string>
#include <vector>

extern "C" {
jboolean Java_dev_lain_os_planner_GgufNativeProbe_nativeRegisterGeneration(JNIEnv *, jobject, jlong);
void Java_dev_lain_os_planner_GgufNativeProbe_nativeReleaseGeneration(JNIEnv *, jobject, jlong);
void Java_dev_lain_os_planner_GgufNativeProbe_nativeCancelGeneration(JNIEnv *, jobject, jlong);
jbyteArray Java_dev_lain_os_planner_GgufNativeProbe_nativeGenerateBytes(
        JNIEnv *, jobject, jstring, jbyteArray, jint, jint, jlong);
}

namespace {
using namespace std::chrono_literals;
enum class PauseAt { None, Load, PromptDecode, TokenDecode };
enum class FailAt { None, Load, Context, Batch, Sampler, PromptDecode, OutputAllocation };

struct EngineStats {
    int backends_started = 0;
    int backends_freed = 0;
    int models_loaded = 0;
    int models_freed = 0;
    int contexts_created = 0;
    int contexts_freed = 0;
    int batches_created = 0;
    int batches_freed = 0;
    int samplers_created = 0;
    int samplers_freed = 0;
    int progress_calls = 0;
    int load_aborts = 0;
    int decode_calls = 0;
    int decode_aborts = 0;
    int prompt_tokens = 4;
    bool gate_timed_out = false;
    FailAt failure = FailAt::None;
    llama_context_params context_params{};
} engine;

// Pause inside the mocked engine call, then let the production callback observe
// Stop. Tests synchronize on this gate; no timing sleeps or polling are needed.
struct EngineGate {
    std::mutex mutex;
    std::condition_variable changed;
    PauseAt phase = PauseAt::None;
    bool entered = false;
    bool resumed = false;

    void reset(PauseAt next) {
        std::lock_guard<std::mutex> lock(mutex);
        phase = next;
        entered = resumed = false;
    }

    void pause(PauseAt current) {
        std::unique_lock<std::mutex> lock(mutex);
        if (phase != current) return;
        entered = true;
        changed.notify_all();
        if (!changed.wait_for(lock, 3s, [&] { return resumed; })) {
            engine.gate_timed_out = true;
        }
    }

    bool await_entry() {
        std::unique_lock<std::mutex> lock(mutex);
        return changed.wait_for(lock, 3s, [&] { return entered; });
    }

    void resume() {
        std::lock_guard<std::mutex> lock(mutex);
        resumed = true;
        changed.notify_all();
    }
} gate;

void require(bool condition, const std::string & message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(EXIT_FAILURE);
    }
}

void reset_engine(PauseAt phase = PauseAt::None) {
    engine = EngineStats{};
    gate.reset(phase);
}

void require_released_resources() {
    require(engine.backends_started == engine.backends_freed, "backend cleanup");
    require(engine.models_loaded == engine.models_freed, "model cleanup");
    require(engine.contexts_created == engine.contexts_freed, "context cleanup");
    require(engine.batches_created == engine.batches_freed, "batch cleanup");
    require(engine.samplers_created == engine.samplers_freed, "sampler cleanup");
    require(!engine.gate_timed_out, "engine callback gate completed without timeout");
}

bool register_generation(jlong id) {
    JNIEnv env;
    return Java_dev_lain_os_planner_GgufNativeProbe_nativeRegisterGeneration(&env, nullptr, id)
            == JNI_TRUE;
}

void cancel_generation(jlong id) {
    JNIEnv env;
    Java_dev_lain_os_planner_GgufNativeProbe_nativeCancelGeneration(&env, nullptr, id);
}

void release_generation(jlong id) {
    JNIEnv env;
    Java_dev_lain_os_planner_GgufNativeProbe_nativeReleaseGeneration(&env, nullptr, id);
}

std::string generate(const std::string & model_path, jlong id, int context_tokens = 64) {
    JNIEnv env;
    std::string path = model_path;
    std::vector<jbyte> prompt{'T', 'e', 's', 't'};
    jbyteArray bytes = Java_dev_lain_os_planner_GgufNativeProbe_nativeGenerateBytes(
            &env, nullptr, &path, &prompt, context_tokens, 2, id);
    require(bytes != nullptr && !env.ExceptionCheck(), "JNI generation produced bytes");
    std::string result(bytes->begin(), bytes->end());
    delete bytes;
    return result;
}

void require_fresh_success(const std::string & model_path, jlong id) {
    reset_engine();
    require(register_generation(id), "fresh generation registered after cleanup");
    require(generate(model_path, id) == "OK:OKOK", "model lease released for fresh generation");
    release_generation(id);
    require_released_resources();
}

void test_registry(const std::string & model_path) {
    reset_engine();
    require(!register_generation(0) && !register_generation(-1), "nonpositive IDs rejected");
    require(generate(model_path, 50) == "ERR:INVALID_BOUNDS", "unregistered generation rejected");
    for (jlong id = 1; id <= 8; ++id) require(register_generation(id), "registry accepts capacity");
    require(!register_generation(1), "duplicate ID rejected");
    require(!register_generation(9), "registry capacity is bounded");
    release_generation(4);
    require(register_generation(9), "release returns registry capacity");
    for (jlong id = 1; id <= 9; ++id) release_generation(id);
    require(engine.backends_started == 0, "registry and invalid request never enter engine");
    require_released_resources();
    std::cout << "PASS: bounded registration, duplicate rejection, release\n";
}

void test_stop_before_worker(const std::string & model_path) {
    reset_engine();
    require(register_generation(10), "early Stop generation registered");
    cancel_generation(10);
    require(generate(model_path, 10) == "ERR:CANCELLED", "Stop before native start retained");
    require(engine.backends_started == 0, "early Stop avoids model load");
    release_generation(10);
    // Reusing a released ID must not inherit its previous cancellation.
    require_fresh_success(model_path, 10);
    std::cout << "PASS: pre-start Stop, no model load, clean ID reuse\n";
}

void test_stop_in_engine(const std::string & model_path, PauseAt phase, jlong id) {
    reset_engine(phase);
    require(register_generation(id), "in-flight generation registered");
    auto result = std::async(std::launch::async, [&] { return generate(model_path, id); });
    require(gate.await_entry(), "generation entered engine call");

    if (phase == PauseAt::Load) {
        require(register_generation(id + 1), "concurrent request registered");
        require(generate(model_path, id + 1) == "ERR:MODEL_BUSY", "only one engine lease allowed");
        release_generation(id + 1);
    }

    cancel_generation(id);
    gate.resume();
    require(result.wait_for(3s) == std::future_status::ready, "Stop completed native worker");
    require(result.get() == "ERR:CANCELLED", "aborted engine call reports cancellation");
    release_generation(id);

    if (phase == PauseAt::Load) {
        require(engine.load_aborts == 1, "model-loading progress callback aborted the load");
        require(engine.models_loaded == 0, "cancelled load never returns a model");
        require(engine.contexts_created == 0, "cancelled load never creates a context");
    } else {
        require(engine.decode_aborts == 1, "CPU abort callback interrupted decode");
        require(engine.decode_calls == (phase == PauseAt::PromptDecode ? 1 : 2),
                "Stop occurred inside the selected prompt/token decode");
        require(engine.models_loaded == 1 && engine.contexts_created == 1,
                "decode cancellation exercised loaded model and context cleanup");
    }
    require_released_resources();
    require_fresh_success(model_path, id);
    const char * name = phase == PauseAt::Load ? "model load" :
            phase == PauseAt::PromptDecode ? "prompt decode" : "token decode";
    std::cout << "PASS: Stop during " << name << ", callback abort, cleanup, lease reuse\n";
}

void test_stop_isolation(const std::string & model_path) {
    reset_engine(PauseAt::Load);
    require(register_generation(40) && register_generation(41), "isolated IDs registered");
    auto result = std::async(std::launch::async, [&] { return generate(model_path, 40); });
    require(gate.await_entry(), "uncancelled generation entered loading");
    cancel_generation(41);
    cancel_generation(999); // An unknown Stop cannot affect an active request.
    gate.resume();
    require(result.wait_for(3s) == std::future_status::ready, "isolated request completed");
    require(result.get() == "OK:OKOK", "Stop for another ID leaves active generation intact");
    require(generate(model_path, 41) == "ERR:CANCELLED", "other ID retains early Stop");
    release_generation(40);
    release_generation(41);
    require(engine.load_aborts == 0 && engine.decode_aborts == 0, "no cross-request abort");
    require_released_resources();
    std::cout << "PASS: cancellation stays scoped to its registered request\n";
}

void test_prompt_memory_bound(const std::string & model_path) {
    reset_engine();
    engine.prompt_tokens = 300;
    require(register_generation(60), "large prompt generation registered");
    require(generate(model_path, 60, 512) == "OK:OKOK", "large prompt generates");
    release_generation(60);
    require(engine.context_params.n_batch == 300, "logical batch fits prompt");
    require(engine.context_params.n_ubatch == 128, "physical prompt batch is bounded");
    require_released_resources();
    std::cout << "PASS: bounded prompt evaluation batch\n";
}

void test_failure_cleanup(const std::string & model_path) {
    struct FailureCase { FailAt stage; const char * expected; };
    const FailureCase cases[] = {
        {FailAt::Load, "ERR:MODEL_LOAD_FAILED"},
        {FailAt::Context, "ERR:CONTEXT_FAILED"},
        {FailAt::Batch, "ERR:CONTEXT_FAILED"},
        {FailAt::Sampler, "ERR:CONTEXT_FAILED"},
        {FailAt::PromptDecode, "ERR:DECODE_FAILED"},
        {FailAt::OutputAllocation, "ERR:CONTEXT_FAILED"},
    };
    for (const FailureCase & failure : cases) {
        reset_engine();
        engine.failure = failure.stage;
        require(register_generation(70), "failure-path generation registered");
        require(generate(model_path, 70) == failure.expected, "engine failure remains a JNI result");
        release_generation(70);
        require(engine.backends_started == 1, "failure exercised native resource acquisition");
        if (failure.stage == FailAt::OutputAllocation) {
            require(engine.samplers_created == 2, "allocation exception exercised all native resources");
        }
        require_released_resources();
        require_fresh_success(model_path, 70);
    }
    std::cout << "PASS: engine failures and allocation exception release resources and model lease\n";
}
} // namespace

struct llama_vocab {};
struct llama_model { llama_vocab vocab; };
struct llama_context { llama_context_params params; };
struct llama_batch_ext { int32_t token_count = 0; };
struct llama_sampler { std::vector<llama_sampler *> children; };

void llama_backend_init() { ++engine.backends_started; }
void llama_backend_free() { ++engine.backends_freed; }
llama_model_params llama_model_default_params() { return {}; }

llama_model * llama_model_load_from_file(const char *, llama_model_params params) {
    if (engine.failure == FailAt::Load) return nullptr;
    if (params.progress_callback != nullptr) {
        ++engine.progress_calls;
        if (!params.progress_callback(0.0f, params.progress_callback_user_data)) {
            ++engine.load_aborts;
            return nullptr;
        }
    }
    gate.pause(PauseAt::Load);
    if (params.progress_callback != nullptr) {
        ++engine.progress_calls;
        if (!params.progress_callback(0.5f, params.progress_callback_user_data)) {
            ++engine.load_aborts;
            return nullptr;
        }
    }
    ++engine.models_loaded;
    return new llama_model;
}

void llama_model_free(llama_model * model) { ++engine.models_freed; delete model; }
bool llama_model_has_encoder(const llama_model *) { return false; }
const llama_vocab * llama_model_get_vocab(const llama_model * model) { return &model->vocab; }

int32_t llama_tokenize(const llama_vocab *, const char *, int32_t, llama_token * tokens,
        int32_t capacity, bool, bool) {
    if (tokens == nullptr || capacity < engine.prompt_tokens) return -engine.prompt_tokens;
    for (int32_t i = 0; i < engine.prompt_tokens; ++i) tokens[i] = i;
    return engine.prompt_tokens;
}

int32_t llama_token_to_piece(const llama_vocab *, llama_token, char * bytes, int32_t capacity,
        int32_t, bool) {
    if (engine.failure == FailAt::OutputAllocation) throw std::bad_alloc();
    if (capacity < 2) return -2;
    std::memcpy(bytes, "OK", 2);
    return 2;
}

bool llama_vocab_is_eog(const llama_vocab *, llama_token) { return false; }
llama_context_params llama_context_default_params() { return {}; }

llama_context * llama_init_from_model(llama_model *, llama_context_params params) {
    engine.context_params = params;
    if (engine.failure == FailAt::Context) return nullptr;
    ++engine.contexts_created;
    return new llama_context{params};
}

void llama_free(llama_context * context) { ++engine.contexts_freed; delete context; }
llama_batch_ext * llama_batch_ext_init(llama_context *) {
    if (engine.failure == FailAt::Batch) return nullptr;
    ++engine.batches_created;
    return new llama_batch_ext;
}
void llama_batch_ext_free(llama_batch_ext * batch) { ++engine.batches_freed; delete batch; }
void llama_batch_ext_clear(llama_batch_ext * batch) { batch->token_count = 0; }
int32_t llama_batch_ext_add_token(llama_batch_ext * batch, int32_t, llama_token) {
    return batch->token_count++;
}
void llama_batch_ext_set_pos(llama_batch_ext *, int32_t, const llama_pos *) {}
void llama_batch_ext_set_output_logits(llama_batch_ext *, int32_t, bool) {}

int32_t llama_process(llama_context * context, llama_process_type, llama_batch_ext *) {
    ++engine.decode_calls;
    if (engine.failure == FailAt::PromptDecode && engine.decode_calls == 1) return -1;
    gate.pause(engine.decode_calls == 1 ? PauseAt::PromptDecode : PauseAt::TokenDecode);
    if (context->params.abort_callback != nullptr &&
            context->params.abort_callback(context->params.abort_callback_data)) {
        ++engine.decode_aborts;
        return 2; // llama.cpp reports a nonzero result when graph evaluation aborts.
    }
    return 0;
}

llama_sampler_chain_params llama_sampler_chain_default_params() { return {}; }
llama_sampler * llama_sampler_chain_init(llama_sampler_chain_params) {
    if (engine.failure == FailAt::Sampler) return nullptr;
    ++engine.samplers_created;
    return new llama_sampler;
}
llama_sampler * llama_sampler_init_greedy() { return llama_sampler_chain_init({}); }
void llama_sampler_chain_add(llama_sampler * chain, llama_sampler * child) {
    chain->children.push_back(child);
}
void llama_sampler_free(llama_sampler * sampler) {
    for (llama_sampler * child : sampler->children) llama_sampler_free(child);
    ++engine.samplers_freed;
    delete sampler;
}
llama_token llama_sampler_sample(llama_sampler *, llama_context *, int32_t) { return 7; }

int main() {
    char model_path[] = "/tmp/lain-lifecycle-model.XXXXXX";
    const int descriptor = mkstemp(model_path);
    require(descriptor >= 0, "readable fixture path created");
    close(descriptor);
    test_registry(model_path);
    test_stop_before_worker(model_path);
    test_stop_in_engine(model_path, PauseAt::Load, 20);
    test_stop_in_engine(model_path, PauseAt::PromptDecode, 30);
    test_stop_in_engine(model_path, PauseAt::TokenDecode, 35);
    test_stop_isolation(model_path);
    test_prompt_memory_bound(model_path);
    test_failure_cleanup(model_path);
    unlink(model_path);
    std::cout << "All native GGUF lifecycle tests passed.\n";
}
