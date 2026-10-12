#pragma once

// A controlled engine boundary for the production JNI lifecycle test. The
// progress/abort callback signatures match the pinned llama.cpp API.
#include <cstdint>

using llama_token = int32_t;
using llama_pos = int32_t;
using llama_progress_callback = bool (*)(float, void *);
using ggml_abort_callback = bool (*)(void *);

struct llama_model;
struct llama_vocab;
struct llama_context;
struct llama_batch_ext;
struct llama_sampler;

struct llama_model_params {
    int32_t n_gpu_layers = 0;
    bool check_tensors = false;
    llama_progress_callback progress_callback = nullptr;
    void * progress_callback_user_data = nullptr;
};

struct llama_context_params {
    uint32_t n_ctx = 0;
    uint32_t n_batch = 0;
    uint32_t n_ubatch = 0;
    int32_t n_threads = 0;
    int32_t n_threads_batch = 0;
    bool no_perf = false;
    ggml_abort_callback abort_callback = nullptr;
    void * abort_callback_data = nullptr;
};

struct llama_sampler_chain_params { bool no_perf = false; };
enum llama_process_type { LLAMA_PROCESS_TYPE_DECODE };

void llama_backend_init();
void llama_backend_free();
llama_model_params llama_model_default_params();
llama_model * llama_model_load_from_file(const char *, llama_model_params);
void llama_model_free(llama_model *);
bool llama_model_has_encoder(const llama_model *);
const llama_vocab * llama_model_get_vocab(const llama_model *);
int32_t llama_tokenize(const llama_vocab *, const char *, int32_t, llama_token *, int32_t, bool, bool);
int32_t llama_token_to_piece(const llama_vocab *, llama_token, char *, int32_t, int32_t, bool);
bool llama_vocab_is_eog(const llama_vocab *, llama_token);
llama_context_params llama_context_default_params();
llama_context * llama_init_from_model(llama_model *, llama_context_params);
void llama_free(llama_context *);
llama_batch_ext * llama_batch_ext_init(llama_context *);
void llama_batch_ext_free(llama_batch_ext *);
void llama_batch_ext_clear(llama_batch_ext *);
int32_t llama_batch_ext_add_token(llama_batch_ext *, int32_t, llama_token);
void llama_batch_ext_set_pos(llama_batch_ext *, int32_t, const llama_pos *);
void llama_batch_ext_set_output_logits(llama_batch_ext *, int32_t, bool);
int32_t llama_process(llama_context *, llama_process_type, llama_batch_ext *);
llama_sampler_chain_params llama_sampler_chain_default_params();
llama_sampler * llama_sampler_chain_init(llama_sampler_chain_params);
llama_sampler * llama_sampler_init_greedy();
void llama_sampler_chain_add(llama_sampler *, llama_sampler *);
void llama_sampler_free(llama_sampler *);
llama_token llama_sampler_sample(llama_sampler *, llama_context *, int32_t);
