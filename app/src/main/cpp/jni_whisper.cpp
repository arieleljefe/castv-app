// [IA-2a/IA-2b] Puente JNI propio de CASTV con whisper.cpp.
#include <jni.h>
#include <atomic>
#include <cstring>
#include <string>
#include "whisper.h"

static std::atomic<bool> g_cancel(false);

// whisper_full la consulta a menudo; si devuelve true, se detiene.
static bool abort_cb(void * /* data */) {
    return g_cancel.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_castv_app_WhisperLib_systemInfo(JNIEnv *env, jobject /* thiz */) {
    const char *info = whisper_print_system_info();
    return env->NewStringUTF(info ? info : "");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_castv_app_WhisperLib_initContext(JNIEnv *env, jobject /* thiz */, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (!p) return 0;
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_castv_app_WhisperLib_freeContext(JNIEnv * /* env */, jobject /* thiz */, jlong ctxPtr) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_castv_app_WhisperLib_setCancel(JNIEnv * /* env */, jobject /* thiz */, jboolean flag) {
    g_cancel.store(flag == JNI_TRUE);
}

// Devuelve 0 si salió bien; otro valor si falló o se canceló.
extern "C" JNIEXPORT jint JNICALL
Java_com_castv_app_WhisperLib_transcribe(JNIEnv *env, jobject /* thiz */, jlong ctxPtr,
                                         jint threads, jstring lang, jfloatArray audio) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx) return -100;
    const char *l = env->GetStringUTFChars(lang, nullptr);
    std::string language(l ? l : "es");
    if (l) env->ReleaseStringUTFChars(lang, l);

    jsize n = env->GetArrayLength(audio);
    jfloat *data = env->GetFloatArrayElements(audio, nullptr);
    if (!data) return -101;

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = language.c_str();
    wp.detect_language = false;
    wp.translate = false;
    wp.no_context = true;
    wp.print_realtime = false;
    wp.print_progress = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.abort_callback = abort_cb;
    wp.abort_callback_user_data = nullptr;

    int rc = whisper_full(ctx, wp, data, static_cast<int>(n));
    env->ReleaseFloatArrayElements(audio, data, JNI_ABORT);
    return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_castv_app_WhisperLib_segmentCount(JNIEnv * /* env */, jobject /* thiz */, jlong ctxPtr) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return ctx ? whisper_full_n_segments(ctx) : 0;
}

// Tiempos en centésimas de segundo.
extern "C" JNIEXPORT jlong JNICALL
Java_com_castv_app_WhisperLib_segmentT0(JNIEnv * /* env */, jobject /* thiz */, jlong ctxPtr, jint i) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return ctx ? whisper_full_get_segment_t0(ctx, i) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_castv_app_WhisperLib_segmentT1(JNIEnv * /* env */, jobject /* thiz */, jlong ctxPtr, jint i) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return ctx ? whisper_full_get_segment_t1(ctx, i) : 0;
}

// Se devuelven los bytes UTF-8 tal cual (un segmento puede cortar un carácter a la mitad y
// NewStringUTF con bytes inválidos puede cerrar la app); Kotlin los decodifica.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_castv_app_WhisperLib_segmentTextBytes(JNIEnv *env, jobject /* thiz */, jlong ctxPtr, jint i) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    const char *t = ctx ? whisper_full_get_segment_text(ctx, i) : nullptr;
    size_t len = t ? strlen(t) : 0;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(len));
    if (arr && len > 0) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte *>(t));
    }
    return arr;
}
