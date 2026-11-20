package com.castv.app

// [IA-2a] Puente a whisper.cpp (código nativo en src/main/cpp)
object WhisperLib {
    init {
        System.loadLibrary("castv_whisper")
    }

    /** Texto de sistema de Whisper (por ejemplo "NEON = 1 | ..."). */
    external fun systemInfo(): String
}
