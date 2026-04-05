/**
 * JNI bridge for llama.cpp on Android.
 *
 * Provides native methods called from LlamaBridge.kt for:
 * - Loading/unloading GGUF models
 * - Streaming text generation
 * - Token counting and model info
 *
 * When compiled with LLAMA_STUB=1 (llama.cpp source not present),
 * all methods return error states gracefully.
 *
 * NOTE: The crash handler in OpenClawApp.kt only catches JVM-level exceptions.
 * Native signals (SIGSEGV, SIGABRT from llama.cpp OOM) kill the process directly
 * and bypass Java's UncaughtExceptionHandler. To capture those, a native signal
 * handler (e.g. Google Breakpad) would be needed.
 */

#include <jni.h>
#include <string>
#include <mutex>
#include <chrono>
#include <android/log.h>
#include <sys/sysinfo.h>

#define TAG "llama_bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifndef LLAMA_STUB

#include "llama.h"
#include "ggml.h"
#include "common.h"

// ── Global state ────────────────────────────────────────────────────────────

// g_model_mutex: protects model load/unload and pointer reads.
//   Held briefly to copy pointers or swap model state.
// g_gen_mutex:   serialises generation calls (held for the duration of generate).
//   Prevents concurrent generation but does NOT block isModelLoaded/tokenCount/etc.
// Rule: never hold both simultaneously.
static std::mutex g_model_mutex;
static std::mutex g_gen_mutex;
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ── Helper: convert jstring to std::string ──────────────────────────────────

static std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char *chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// ── JNI methods ─────────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeIsRealBuild(
    JNIEnv *env, jobject /* this */
) {
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeLoadModel(
    JNIEnv *env, jobject /* this */,
    jstring modelPath, jint nThreads, jint nGpuLayers, jint contextSize,
    jboolean useMmap, jboolean flashAttn
) {
    std::lock_guard<std::mutex> lock(g_model_mutex);

    // Unload existing model
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;

    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model: %s (threads=%d, gpu_layers=%d, ctx=%d, mmap=%d, flash_attn=%d)",
         path.c_str(), nThreads, nGpuLayers, contextSize, useMmap, flashAttn);

    // Model params — use_mmap is the critical setting that prevents OOM.
    // With mmap, the OS pages model data in/out on demand instead of loading
    // the entire model into contiguous RAM. This is why off-grid doesn't crash.
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;
    model_params.use_mmap = useMmap;
    model_params.use_mlock = false;  // Never lock pages — let OS manage memory pressure

    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    // Context params — flash attention reduces memory usage significantly.
    // KV cache quantization: q8_0 halves memory vs f16, enabling larger contexts.
    // Off-grid supports f16/q8_0/q4_0; we use q8_0 on CPU-only (safe), f16 with GPU
    // (quantized KV + Android GPU causes SIGABRT).
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.n_batch = 512;  // Process prompt in 512-token chunks (off-grid default)
    ctx_params.flash_attn_type = flashAttn ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (nGpuLayers == 0) {
        // CPU-only: safe to use q8_0 KV cache (2x memory savings)
        ctx_params.type_k = GGML_TYPE_Q8_0;
        ctx_params.type_v = GGML_TYPE_Q8_0;
    } else {
        // GPU offloading: stick with f16 to avoid native crashes
        ctx_params.type_k = GGML_TYPE_F16;
        ctx_params.type_v = GGML_TYPE_F16;
    }

    // Progressive context fallback: try requested size down to 512.
    // On low-RAM devices, even 1024 can fail. Always attempt smaller sizes.
    int ctx_sizes[] = { contextSize, 2048, 1024, 512 };
    int n_sizes = 4;
    // Skip sizes larger than requested
    int start_idx = 0;
    while (start_idx < n_sizes - 1 && ctx_sizes[start_idx] > contextSize) start_idx++;

    for (int attempt = start_idx; attempt < n_sizes; attempt++) {
        int try_ctx = ctx_sizes[attempt];
        ctx_params.n_ctx = try_ctx;

        LOGI("Context init attempt %d/%d with ctx=%d, kv=%s",
             attempt - start_idx + 1, n_sizes - start_idx, try_ctx,
             (nGpuLayers == 0) ? "q8_0" : "f16");

        g_ctx = llama_init_from_model(g_model, ctx_params);
        if (g_ctx) {
            LOGI("Model loaded successfully. Context: %d tokens, mmap: %s, flash_attn: %s, kv: %s",
                 try_ctx, useMmap ? "on" : "off", flashAttn ? "on" : "off",
                 (nGpuLayers == 0) ? "q8_0" : "f16");
            return JNI_TRUE;
        }
        LOGE("Context init failed with ctx=%d", try_ctx);
    }

    // Last resort: try f16 KV cache with ctx=512 (most compatible, least memory)
    LOGI("All q8_0 attempts failed — trying f16 KV with ctx=512 as last resort");
    ctx_params.type_k = GGML_TYPE_F16;
    ctx_params.type_v = GGML_TYPE_F16;
    ctx_params.n_ctx = 512;
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx) {
        LOGI("Model loaded with f16 KV fallback, ctx=512");
        return JNI_TRUE;
    }

    // All attempts failed — clean up
    LOGE("Failed to create context after all attempts");
    llama_model_free(g_model);
    g_model = nullptr;
    g_vocab = nullptr;
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetTotalMemory(
    JNIEnv *env, jobject /* this */
) {
    struct sysinfo info;
    if (sysinfo(&info) == 0) {
        return (jlong)(info.totalram * info.mem_unit);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeUnloadModel(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeIsModelLoaded(
    JNIEnv *env, jobject /* this */
) {
    // Brief lock — does not contend with generation
    std::lock_guard<std::mutex> lock(g_model_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGenerate(
    JNIEnv *env, jobject /* this */,
    jstring jPrompt, jint maxTokens, jfloat temperature,
    jfloat topP, jint topK, jfloat repeatPenalty,
    jobjectArray jStopSequences, jobject callback
) {
    // Snapshot model pointers under model_mutex (brief hold), then release.
    // Generation itself is serialised by g_gen_mutex but does NOT block
    // isModelLoaded / getModelInfo / tokenCount etc.
    llama_model *model;
    llama_context *ctx;
    const llama_vocab *vocab;
    {
        std::lock_guard<std::mutex> lock(g_model_mutex);
        model = g_model;
        ctx = g_ctx;
        vocab = g_vocab;
    }

    if (!model || !ctx || !vocab) {
        return env->NewStringUTF("Error: No model loaded");
    }

    // Only one generation at a time
    std::lock_guard<std::mutex> gen_lock(g_gen_mutex);

    std::string prompt = jstring_to_string(env, jPrompt);

    // Collect stop sequences
    std::vector<std::string> stop_sequences;
    if (jStopSequences) {
        int count = env->GetArrayLength(jStopSequences);
        for (int i = 0; i < count; i++) {
            auto js = (jstring)env->GetObjectArrayElement(jStopSequences, i);
            stop_sequences.push_back(jstring_to_string(env, js));
            env->DeleteLocalRef(js);
        }
    }

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken",
        "(Ljava/lang/String;)Z");

    // ── Step 1: Tokenize ─────────────────────────────────────────────────
    auto t_start = std::chrono::high_resolution_clock::now();
    LOGI("[STEP 1/5] Tokenizing prompt (%d chars)...", (int)prompt.size());

    std::vector<llama_token> tokens(prompt.size() + 128);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                                   tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                                   tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    auto t_tokenize = std::chrono::high_resolution_clock::now();
    auto ms_tokenize = std::chrono::duration_cast<std::chrono::milliseconds>(t_tokenize - t_start).count();
    LOGI("[STEP 1/5] Tokenized: %d tokens in %lldms. Max gen: %d", n_tokens, (long long)ms_tokenize, maxTokens);

    // ── Step 2: Clear KV cache ──────────────────────────────────────────
    LOGI("[STEP 2/5] Clearing KV cache...");
    llama_memory_clear(llama_get_memory(ctx), true);

    // ── Step 3: Prefill (decode prompt in batches) ──────────────────────
    const int n_batch = llama_n_batch(ctx);
    LOGI("[STEP 3/5] Prefill: %d tokens in batches of %d...", n_tokens, n_batch);
    auto t_prefill_start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < n_tokens; i += n_batch) {
        int n_eval = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_init(n_eval, 0, 1);

        for (int j = 0; j < n_eval; j++) {
            bool is_last = (i + j == n_tokens - 1);
            common_batch_add(batch, tokens[i + j], i + j, {0}, is_last);
        }

        auto t_batch_start = std::chrono::high_resolution_clock::now();
        int decode_result = llama_decode(ctx, batch);
        auto t_batch_end = std::chrono::high_resolution_clock::now();
        auto ms_batch = std::chrono::duration_cast<std::chrono::milliseconds>(t_batch_end - t_batch_start).count();

        llama_batch_free(batch);

        if (decode_result != 0) {
            LOGE("[STEP 3/5] FAILED at batch offset %d after %lldms", i, (long long)ms_batch);
            return env->NewStringUTF("Error: Prompt decode failed");
        }
        LOGI("[STEP 3/5] Batch %d-%d decoded in %lldms", i, i + n_eval - 1, (long long)ms_batch);
    }

    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    auto ms_prefill = std::chrono::duration_cast<std::chrono::milliseconds>(t_prefill_end - t_prefill_start).count();
    double prefill_tps = (ms_prefill > 0) ? (n_tokens * 1000.0 / ms_prefill) : 0;
    LOGI("[STEP 3/5] Prefill done: %d tokens in %lldms (%.1f t/s)", n_tokens, (long long)ms_prefill, prefill_tps);

    // ── Step 4: Setup sampler ───────────────────────────────────────────
    LOGI("[STEP 4/5] Setting up sampler (temp=%.2f)...", temperature);
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);

    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(0));
    }

    // ── Step 5: Generate tokens ─────────────────────────────────────────
    LOGI("[STEP 5/5] Generating (max %d tokens)...", maxTokens);
    llama_batch next_batch = llama_batch_init(1, 0, 1);
    std::string full_response;
    int n_cur = n_tokens;

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, ctx, -1);

        // Check EOS
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Decode token to text
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        std::string piece;
        if (n < 0) {
            std::vector<char> big_buf(-n);
            llama_token_to_piece(vocab, new_token, big_buf.data(), big_buf.size(), 0, true);
            piece.assign(big_buf.data(), big_buf.size());
        } else {
            piece.assign(buf, n);
        }
        full_response += piece;

        jstring jPiece = env->NewStringUTF(piece.c_str());
        jboolean cont = env->CallBooleanMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        // Check for JNI exceptions (e.g. callback threw)
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        if (!cont) break;

        // Check stop sequences using compare() instead of substr()
        bool should_stop = false;
        for (const auto &stop : stop_sequences) {
            if (full_response.length() >= stop.length() &&
                full_response.compare(full_response.length() - stop.length(),
                                      stop.length(), stop) == 0) {
                full_response.resize(full_response.length() - stop.length());
                should_stop = true;
                break;
            }
        }
        if (should_stop) break;

        // Reuse pre-allocated batch for next token
        next_batch.n_tokens = 0;
        common_batch_add(next_batch, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(ctx, next_batch) != 0) {
            LOGE("Decode failed at position %d", n_cur);
            break;
        }
    }

    llama_batch_free(next_batch);
    llama_sampler_free(sampler);

    auto t_end = std::chrono::high_resolution_clock::now();
    auto ms_total = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    int gen_tokens = n_cur - n_tokens;
    auto ms_gen = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_prefill_end).count();
    double gen_tps = (ms_gen > 0) ? (gen_tokens * 1000.0 / ms_gen) : 0;
    LOGI("[DONE] %d tokens generated in %lldms (%.1f t/s). Total: %lldms. Prefill: %lldms",
         gen_tokens, (long long)ms_gen, gen_tps, (long long)ms_total, (long long)ms_prefill);
    return env->NewStringUTF(full_response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetModelInfo(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (!g_model) return env->NewStringUTF("No model loaded");

    std::string info = "Model loaded";
    // Additional metadata can be extracted from the model if needed
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetContextLength(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (!g_ctx) return 0;
    return (jint)llama_n_ctx(g_ctx);
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeTokenCount(
    JNIEnv *env, jobject /* this */,
    jstring jText
) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (!g_vocab) return 0;
    std::string text = jstring_to_string(env, jText);

    std::vector<llama_token> tokens(text.size() + 128);
    int n = llama_tokenize(g_vocab, text.c_str(), text.size(),
                            tokens.data(), tokens.size(), false, false);
    return (jint)(n > 0 ? n : -n);
}

JNIEXPORT jlong JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetAvailableMemory(
    JNIEnv *env, jobject /* this */
) {
    struct sysinfo info;
    if (sysinfo(&info) == 0) {
        return (jlong)(info.freeram * info.mem_unit);
    }
    return 0;
}

} // extern "C"

#else // LLAMA_STUB

// ── Stub implementations when llama.cpp is not available ─────────────────────

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeIsRealBuild(
    JNIEnv *, jobject
) {
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeLoadModel(
    JNIEnv *env, jobject, jstring, jint, jint, jint, jboolean, jboolean
) {
    LOGE("llama.cpp not compiled in this build");
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetTotalMemory(JNIEnv *, jobject) {
    struct sysinfo info;
    if (sysinfo(&info) == 0) return (jlong)(info.totalram * info.mem_unit);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeUnloadModel(JNIEnv *, jobject) {}

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeIsModelLoaded(JNIEnv *, jobject) {
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGenerate(
    JNIEnv *env, jobject, jstring, jint, jfloat, jfloat, jint, jfloat,
    jobjectArray, jobject
) {
    return env->NewStringUTF("Error: Local inference not available in this build.");
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetModelInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF("Not available");
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetContextLength(JNIEnv *, jobject) {
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeTokenCount(JNIEnv *, jobject, jstring) {
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetAvailableMemory(JNIEnv *, jobject) {
    struct sysinfo info;
    if (sysinfo(&info) == 0) return (jlong)(info.freeram * info.mem_unit);
    return 0;
}

} // extern "C"

#endif // LLAMA_STUB
