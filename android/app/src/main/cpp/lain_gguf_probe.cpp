#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr size_t MAX_OUTPUT_BYTES = 65'536;
constexpr size_t MAX_PROMPT_BYTES = 16'384;
std::mutex model_lease;
std::atomic<uint64_t> active_generation{0};
std::atomic<uint64_t> cancelled_generation{0};
struct ActiveGenerationReset {
    ~ActiveGenerationReset() { active_generation.store(0); }
};
constexpr int MIN_CONTEXT_TOKENS = 64;
constexpr int MAX_CONTEXT_TOKENS = 4096;
constexpr int MIN_NEW_TOKENS = 1;
constexpr int MAX_NEW_TOKENS = 256;

std::string error(const char * code) {
    return std::string("ERR:") + code;
}

void batch_set_tokens(
        llama_batch_ext * batch,
        const llama_token * tokens,
        int32_t count,
        llama_pos start) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < count; ++i) {
        const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        if (index < 0) {
            return;
        }
        const llama_pos position = start + i;
        llama_batch_ext_set_pos(batch, index, &position);
    }
    if (count > 0) {
        llama_batch_ext_set_output_logits(batch, count - 1, true);
    }
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    char small[256];
    int32_t size = llama_token_to_piece(vocab, token, small, sizeof(small), 0, true);
    if (size >= 0) {
        return std::string(small, static_cast<size_t>(size));
    }
    const int32_t required = -size;
    if (required <= 0 || required > 4096) {
        return {};
    }
    std::vector<char> large(static_cast<size_t>(required));
    size = llama_token_to_piece(vocab, token, large.data(), required, 0, true);
    if (size < 0) {
        return {};
    }
    return std::string(large.data(), static_cast<size_t>(size));
}

std::string run_probe(
        const char * model_path, const std::string & prompt,
        int context_tokens, int max_new_tokens, uint64_t generation_id) {
    std::unique_lock<std::mutex> lease(model_lease, std::try_to_lock);
    if (!lease.owns_lock()) return error("MODEL_BUSY");
    active_generation.store(generation_id);
    ActiveGenerationReset clear_active;
    const auto cancelled = [&]() {
        return generation_id != 0 && cancelled_generation.load() == generation_id;
    };
    if (cancelled()) return error("CANCELLED");
    if (prompt.empty() || prompt.size() > MAX_PROMPT_BYTES ||
            prompt.find('\0') != std::string::npos) return error("INVALID_BOUNDS");

    if (
        model_path == nullptr || model_path[0] == '\0' ||
        context_tokens < MIN_CONTEXT_TOKENS || context_tokens > MAX_CONTEXT_TOKENS ||
        max_new_tokens < MIN_NEW_TOKENS || max_new_tokens > MAX_NEW_TOKENS ||
        max_new_tokens >= context_tokens
    ) {
        return error("INVALID_BOUNDS");
    }
    if (access(model_path, R_OK) != 0) {
        return error("MODEL_NOT_FOUND");
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.check_tensors = true;

    llama_model * model = llama_model_load_from_file(model_path, model_params);
    if (model == nullptr) {
        llama_backend_free();
        return error("MODEL_LOAD_FAILED");
    }

    if (cancelled()) {
        llama_model_free(model);
        llama_backend_free();
        return error("CANCELLED");
    }
    if (llama_model_has_encoder(model)) {
        llama_model_free(model);
        llama_backend_free();
        return error("MODEL_LOAD_FAILED");
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int32_t token_count = -llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (token_count <= 0 || token_count + max_new_tokens > context_tokens) {
        llama_model_free(model);
        llama_backend_free();
        return error("TOKENIZE_FAILED");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(token_count));
    if (llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(),
            token_count,
            true,
            true) < 0) {
        llama_model_free(model);
        llama_backend_free();
        return error("TOKENIZE_FAILED");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_tokens);
    context_params.n_batch = static_cast<uint32_t>(std::max(token_count, 1));
    context_params.n_ubatch = context_params.n_batch;
    context_params.n_threads = 2;
    context_params.n_threads_batch = 2;
    context_params.no_perf = true;

    llama_context * context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        llama_model_free(model);
        llama_backend_free();
        return error("CONTEXT_FAILED");
    }

    llama_batch_ext * batch = llama_batch_ext_init(context);
    if (batch == nullptr) {
        llama_free(context);
        llama_model_free(model);
        llama_backend_free();
        return error("CONTEXT_FAILED");
    }

    auto sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) {
        llama_batch_ext_free(batch);
        llama_free(context);
        llama_model_free(model);
        llama_backend_free();
        return error("CONTEXT_FAILED");
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    batch_set_tokens(batch, prompt_tokens.data(), token_count, 0);
    if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
        llama_sampler_free(sampler);
        llama_batch_ext_free(batch);
        llama_free(context);
        llama_model_free(model);
        llama_backend_free();
        return error("DECODE_FAILED");
    }

    std::string output;
    bool stopped = false;
    llama_pos position = token_count;
    for (int i = 0; i < max_new_tokens; ++i) {
        if (cancelled()) { stopped = true; break; }
        const llama_token token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        const std::string piece = token_piece(vocab, token);
        if (piece.empty() || piece.size() > MAX_OUTPUT_BYTES - output.size()) {
            output.clear();
            break;
        }
        output += piece;

        if (i + 1 < max_new_tokens) {
            batch_set_tokens(batch, &token, 1, position);
            if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
                output.clear();
                break;
            }
            ++position;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    llama_free(context);
    llama_model_free(model);
    llama_backend_free();

    if (cancelled() || stopped) return error("CANCELLED");
    if (output.empty()) {
        return error("OUTPUT_INVALID");
    }
    return std::string("OK:") + output;
}

jbyteArray result_bytes(JNIEnv * env, const std::string& payload) {
    // A successful probe is capped at 65,536 bytes of model output plus the OK: tag.
    if (payload.size() > MAX_OUTPUT_BYTES + 3) {
        return nullptr;
    }
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(payload.size()));
    if (bytes != nullptr && !payload.empty()) {
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(payload.size()),
                reinterpret_cast<const jbyte *>(payload.data()));
    }
    return bytes;
}

} // namespace

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_dev_lain_os_planner_GgufNativeProbe_nativeProbeBytes(
        JNIEnv * env,
        jobject,
        jstring model_path,
        jint context_tokens,
        jint max_new_tokens) {
    if (model_path == nullptr) {
        return result_bytes(env, error("INVALID_BOUNDS"));
    }
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        return result_bytes(env, error("MODEL_NOT_FOUND"));
    }
    const std::string result = run_probe(path, "Reply with OK.", context_tokens, max_new_tokens, 0);
    env->ReleaseStringUTFChars(model_path, path);
    return result_bytes(env, result);
}


extern "C"
JNIEXPORT jbyteArray JNICALL
Java_dev_lain_os_planner_GgufNativeProbe_nativeGenerateBytes(
        JNIEnv * env, jobject, jstring model_path, jbyteArray prompt_bytes,
        jint context_tokens, jint max_new_tokens, jlong generation_id) {
    if (model_path == nullptr || prompt_bytes == nullptr || generation_id <= 0) {
        return result_bytes(env, error("INVALID_BOUNDS"));
    }
    const jsize prompt_size = env->GetArrayLength(prompt_bytes);
    if (prompt_size <= 0 || static_cast<size_t>(prompt_size) > MAX_PROMPT_BYTES) {
        return result_bytes(env, error("INVALID_BOUNDS"));
    }
    std::string prompt(static_cast<size_t>(prompt_size), '\0');
    env->GetByteArrayRegion(prompt_bytes, 0, prompt_size, reinterpret_cast<jbyte *>(&prompt[0]));
    if (env->ExceptionCheck()) return nullptr;

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return result_bytes(env, error("MODEL_NOT_FOUND"));
    const std::string result = run_probe(path, prompt, context_tokens,
            max_new_tokens, static_cast<uint64_t>(generation_id));
    env->ReleaseStringUTFChars(model_path, path);
    return result_bytes(env, result);
}

extern "C"
JNIEXPORT void JNICALL
Java_dev_lain_os_planner_GgufNativeProbe_nativeCancelGeneration(
        JNIEnv *, jobject, jlong generation_id) {
    if (generation_id > 0 &&
            active_generation.load() == static_cast<uint64_t>(generation_id)) {
        cancelled_generation.store(static_cast<uint64_t>(generation_id));
    }
}
