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
 */

#include <jni.h>
#include <string>
#include <mutex>
#include <android/log.h>
#include <sys/sysinfo.h>

#define TAG "llama_bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifndef LLAMA_STUB

#include "llama.h"
#include "common.h"

// ── Global state ────────────────────────────────────────────────────────────

static std::mutex g_mutex;
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
    jstring modelPath, jint nThreads, jint nGpuLayers, jint contextSize
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    // Unload existing model
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;

    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model: %s (threads=%d, gpu_layers=%d, ctx=%d)",
         path.c_str(), nThreads, nGpuLayers, contextSize);

    // Model params
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    // Context params
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully. Context: %d tokens", contextSize);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeUnloadModel(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeIsModelLoaded(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGenerate(
    JNIEnv *env, jobject /* this */,
    jstring jPrompt, jint maxTokens, jfloat temperature,
    jfloat topP, jint topK, jfloat repeatPenalty,
    jobjectArray jStopSequences, jobject callback
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx || !g_vocab) {
        return env->NewStringUTF("Error: No model loaded");
    }

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

    // Tokenize prompt
    std::vector<llama_token> tokens(prompt.size() + 128);
    int n_tokens = llama_tokenize(g_vocab, prompt.c_str(), prompt.size(),
                                   tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(g_vocab, prompt.c_str(), prompt.size(),
                                   tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    LOGI("Prompt tokens: %d, generating up to %d tokens", n_tokens, maxTokens);

    // Clear KV cache and decode prompt
    llama_kv_cache_clear(g_ctx);

    // Process prompt in batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Failed to process prompt");
    }
    llama_batch_free(batch);

    // Sampling setup
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

    // Allocate a single batch for token-by-token generation (reused in loop)
    llama_batch next_batch = llama_batch_init(1, 0, 1);

    // Generate tokens
    std::string full_response;
    int n_cur = n_tokens;

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);

        // Check EOS
        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        // Decode token to text
        char buf[256];
        int n = llama_token_to_piece(g_vocab, new_token, buf, sizeof(buf), 0, true);
        std::string piece;
        if (n < 0) {
            std::vector<char> big_buf(-n);
            llama_token_to_piece(g_vocab, new_token, big_buf.data(), big_buf.size(), 0, true);
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
        llama_batch_add(next_batch, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(g_ctx, next_batch) != 0) {
            LOGE("Decode failed at position %d", n_cur);
            break;
        }
    }

    llama_batch_free(next_batch);
    llama_sampler_free(sampler);

    LOGI("Generated %d chars", (int)full_response.size());
    return env->NewStringUTF(full_response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetModelInfo(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return env->NewStringUTF("No model loaded");

    std::string info = "Model loaded";
    // Additional metadata can be extracted from the model if needed
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeGetContextLength(
    JNIEnv *env, jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) return 0;
    return (jint)llama_n_ctx(g_ctx);
}

JNIEXPORT jint JNICALL
Java_com_openclaw_android_llm_LlamaBridge_nativeTokenCount(
    JNIEnv *env, jobject /* this */,
    jstring jText
) {
    std::lock_guard<std::mutex> lock(g_mutex);
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
    JNIEnv *env, jobject, jstring, jint, jint, jint
) {
    LOGE("llama.cpp not compiled in this build");
    return JNI_FALSE;
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
