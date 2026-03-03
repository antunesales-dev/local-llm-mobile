#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <functional>
#include "llama.h"
#include "common.h"

#define TAG "LocalLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
    struct LlmSession {
        llama_model *model = nullptr;
        llama_context *ctx = nullptr;
        llama_sampler *sampler = nullptr;
        std::vector<llama_token> prompt_tokens;
        std::mutex mtx;
        std::atomic<bool> cancel_generation{false};
    };

    LlmSession g_session;

    void cleanup_sampler() {
        if (g_session.sampler) {
            llama_sampler_free(g_session.sampler);
            g_session.sampler = nullptr;
        }
    }

    void cleanup_context() {
        if (g_session.ctx) {
            llama_free(g_session.ctx);
            g_session.ctx = nullptr;
        }
    }

    void cleanup_model() {
        if (g_session.model) {
            llama_model_free(g_session.model);
            g_session.model = nullptr;
        }
    }

    llama_sampler *create_sampler(float temperature, float top_p, int top_k, float repeat_penalty) {
        auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(repeat_penalty, 0.0f, 0.0f, 0, false));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        return smpl;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_localllm_chat_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject, jstring model_path, jint n_ctx, jint n_gpu_layers) {
    std::lock_guard<std::mutex> lock(g_session.mtx);

    cleanup_sampler();
    cleanup_context();
    cleanup_model();

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    g_session.model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_session.model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;

    g_session.ctx = llama_init_from_model(g_session.model, ctx_params);
    if (!g_session.ctx) {
        LOGE("Failed to create context");
        cleanup_model();
        return JNI_FALSE;
    }

    g_session.sampler = create_sampler(0.7f, 0.9f, 40, 1.1f);

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_localllm_chat_llm_LlamaBridge_unloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mtx);
    cleanup_sampler();
    cleanup_context();
    cleanup_model();
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_localllm_chat_llm_LlamaBridge_isModelLoaded(JNIEnv *, jobject) {
    return g_session.model != nullptr && g_session.ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_localllm_chat_llm_LlamaBridge_updateSamplerParams(
        JNIEnv *, jobject, jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty) {
    std::lock_guard<std::mutex> lock(g_session.mtx);
    cleanup_sampler();
    g_session.sampler = create_sampler(temperature, top_p, top_k, repeat_penalty);
}

JNIEXPORT jstring JNICALL
Java_com_localllm_chat_llm_LlamaBridge_generate(
        JNIEnv *env, jobject, jstring prompt, jint max_tokens, jobject callback) {
    std::lock_guard<std::mutex> lock(g_session.mtx);
    g_session.cancel_generation = false;

    if (!g_session.model || !g_session.ctx || !g_session.sampler) {
        return env->NewStringUTF("");
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    const llama_vocab *vocab = llama_model_get_vocab(g_session.model);

    std::vector<llama_token> tokens = common_tokenize(vocab, prompt_str, true, true);

    llama_kv_cache_clear(g_session.ctx);

    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, i == tokens.size() - 1);
    }

    if (llama_decode(g_session.ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }
    llama_batch_free(batch);

    jclass callback_class = nullptr;
    jmethodID on_token_method = nullptr;
    if (callback != nullptr) {
        callback_class = env->GetObjectClass(callback);
        on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
    }

    std::string result;
    int n_cur = tokens.size();

    for (int i = 0; i < max_tokens; i++) {
        if (g_session.cancel_generation) {
            break;
        }

        llama_token new_token = llama_sampler_sample(g_session.sampler, g_session.ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            result += piece;

            if (callback != nullptr && on_token_method != nullptr) {
                jstring token_str = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(callback, on_token_method, token_str);
                env->DeleteLocalRef(token_str);
            }
        }

        llama_batch single = llama_batch_init(1, 0, 1);
        common_batch_add(single, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(g_session.ctx, single) != 0) {
            LOGE("Failed to decode token");
            llama_batch_free(single);
            break;
        }
        llama_batch_free(single);
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_localllm_chat_llm_LlamaBridge_cancelGeneration(JNIEnv *, jobject) {
    g_session.cancel_generation = true;
}

JNIEXPORT jstring JNICALL
Java_com_localllm_chat_llm_LlamaBridge_getModelInfo(JNIEnv *env, jobject) {
    if (!g_session.model) {
        return env->NewStringUTF("{}");
    }

    char desc[256];
    llama_model_desc(g_session.model, desc, sizeof(desc));

    int n_params = static_cast<int>(llama_model_n_params(g_session.model) / 1000000);

    std::string info = "{\"description\":\"" + std::string(desc) +
                       "\",\"parameters_m\":" + std::to_string(n_params) + "}";
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_localllm_chat_llm_LlamaBridge_applyChatTemplate(
        JNIEnv *env, jobject, jobjectArray roles, jobjectArray contents) {
    if (!g_session.model) {
        return env->NewStringUTF("");
    }

    int n_messages = env->GetArrayLength(roles);
    std::vector<llama_chat_msg> messages(n_messages);
    std::vector<std::string> role_strings(n_messages);
    std::vector<std::string> content_strings(n_messages);

    for (int i = 0; i < n_messages; i++) {
        auto role_jstr = (jstring) env->GetObjectArrayElement(roles, i);
        auto content_jstr = (jstring) env->GetObjectArrayElement(contents, i);

        const char *role = env->GetStringUTFChars(role_jstr, nullptr);
        const char *content = env->GetStringUTFChars(content_jstr, nullptr);

        role_strings[i] = role;
        content_strings[i] = content;
        messages[i].role = role_strings[i].c_str();
        messages[i].content = content_strings[i].c_str();

        env->ReleaseStringUTFChars(role_jstr, role);
        env->ReleaseStringUTFChars(content_jstr, content);
    }

    const llama_vocab *vocab = llama_model_get_vocab(g_session.model);
    std::string formatted = common_chat_apply_template(vocab, "", messages, true);

    return env->NewStringUTF(formatted.c_str());
}

} // extern "C"
