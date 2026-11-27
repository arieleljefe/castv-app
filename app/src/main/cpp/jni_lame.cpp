// [VIDEO-MP3-1] Puente JNI propio con LAME 3.100 (LGPL, código en cpp/lame/). Solo codifica PCM a MP3.
#include <jni.h>
#include <lame.h>
#include <cstdint>
#include <vector>

extern "C" {

// Crea un codificador. Entrada siempre estéreo entrelazado de 16 bits; si la fuente es mono la salida es mono.
JNIEXPORT jlong JNICALL Java_com_castv_app_LameEncoder_nInit(
        JNIEnv* env, jobject, jint inHz, jboolean mono, jint kbps, jstring title) {
    lame_t g = lame_init();
    if (!g) return 0;
    lame_set_in_samplerate(g, inHz);
    lame_set_num_channels(g, 2);
    lame_set_mode(g, mono ? MONO : JOINT_STEREO);
    lame_set_brate(g, kbps);
    lame_set_quality(g, 5);
    lame_set_bWriteVbrTag(g, 0);   // se escribe en flujo: sin marco de cabecera
    id3tag_init(g);
    id3tag_add_v2(g);
    id3tag_v2_only(g);
    if (title != nullptr) {
        const jchar* c = env->GetStringChars(title, nullptr);
        jsize n = env->GetStringLength(title);
        if (c != nullptr) {
            std::vector<unsigned short> t;
            t.reserve((size_t) n + 2);
            t.push_back(0xFEFF);   // BOM: UTF-16
            for (jsize i = 0; i < n; i++) t.push_back((unsigned short) c[i]);
            t.push_back(0);
            id3tag_set_textinfo_utf16(g, "TIT2", t.data());
            env->ReleaseStringChars(title, c);
        }
    }
    if (lame_init_params(g) < 0) {
        lame_close(g);
        return 0;
    }
    return (jlong) (intptr_t) g;
}

// pcm: estéreo entrelazado (2 * frames valores). out: debe medir al menos 1.25 * frames + 7200.
JNIEXPORT jint JNICALL Java_com_castv_app_LameEncoder_nEncode(
        JNIEnv* env, jobject, jlong h, jshortArray pcm, jint frames, jbyteArray out) {
    lame_t g = (lame_t) (intptr_t) h;
    if (!g) return -100;
    jshort* p = env->GetShortArrayElements(pcm, nullptr);
    jbyte* o = env->GetByteArrayElements(out, nullptr);
    if (!p || !o) {
        if (p) env->ReleaseShortArrayElements(pcm, p, JNI_ABORT);
        if (o) env->ReleaseByteArrayElements(out, o, JNI_ABORT);
        return -101;
    }
    int n = lame_encode_buffer_interleaved(g, (short*) p, frames,
            (unsigned char*) o, env->GetArrayLength(out));
    env->ReleaseShortArrayElements(pcm, p, JNI_ABORT);
    env->ReleaseByteArrayElements(out, o, 0);
    return n;
}

JNIEXPORT jint JNICALL Java_com_castv_app_LameEncoder_nFlush(
        JNIEnv* env, jobject, jlong h, jbyteArray out) {
    lame_t g = (lame_t) (intptr_t) h;
    if (!g) return -100;
    jbyte* o = env->GetByteArrayElements(out, nullptr);
    if (!o) return -101;
    int n = lame_encode_flush(g, (unsigned char*) o, env->GetArrayLength(out));
    env->ReleaseByteArrayElements(out, o, 0);
    return n;
}

JNIEXPORT void JNICALL Java_com_castv_app_LameEncoder_nClose(JNIEnv*, jobject, jlong h) {
    lame_t g = (lame_t) (intptr_t) h;
    if (g) lame_close(g);
}

}
