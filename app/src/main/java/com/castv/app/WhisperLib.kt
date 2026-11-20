package com.castv.app

// [IA-2a/IA-2b] Puente a whisper.cpp (código nativo en src/main/cpp)
object WhisperLib {
    init {
        System.loadLibrary("castv_whisper")
    }

    /** Texto de sistema de Whisper (por ejemplo "NEON = 1 | ..."). */
    external fun systemInfo(): String

    /** Carga el modelo de [path]; devuelve 0 si no se pudo. */
    external fun initContext(path: String): Long
    external fun freeContext(ctx: Long)

    /** Pone o quita la orden de cancelar la transcripción en curso. */
    external fun setCancel(flag: Boolean)

    /** Transcribe [audio] (16 kHz, mono, entre -1 y 1). 0 = bien. */
    external fun transcribe(ctx: Long, threads: Int, lang: String, audio: FloatArray): Int

    external fun segmentCount(ctx: Long): Int
    external fun segmentT0(ctx: Long, i: Int): Long   // centésimas de segundo
    external fun segmentT1(ctx: Long, i: Int): Long
    external fun segmentTextBytes(ctx: Long, i: Int): ByteArray

    fun segmentText(ctx: Long, i: Int): String = String(segmentTextBytes(ctx, i), Charsets.UTF_8)
}
