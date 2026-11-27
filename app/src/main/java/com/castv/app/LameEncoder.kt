package com.castv.app

/** [VIDEO-MP3-1] Codificador MP3 (LAME) dentro de libcastv_lame.so. Entrada: estéreo entrelazado de 16 bits. */
object LameEncoder {
    init { System.loadLibrary("castv_lame") }

    /** Devuelve un identificador (0 si falla). [mono] = la salida es mono. [title] va en la etiqueta ID3. */
    external fun nInit(inHz: Int, mono: Boolean, kbps: Int, title: String?): Long

    /** [pcm] trae 2 valores por cuadro. [out] debe medir al menos 1.25 * frames + 7200. Devuelve bytes escritos (<0 = error). */
    external fun nEncode(h: Long, pcm: ShortArray, frames: Int, out: ByteArray): Int

    external fun nFlush(h: Long, out: ByteArray): Int
    external fun nClose(h: Long)
}
