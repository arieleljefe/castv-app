// [IA-2a] Puente JNI mínimo: solo devuelve la información de sistema de Whisper.
#include <jni.h>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_castv_app_WhisperLib_systemInfo(JNIEnv *env, jobject /* thiz */) {
    const char *info = whisper_print_system_info();
    return env->NewStringUTF(info ? info : "");
}
