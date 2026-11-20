package com.castv.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [IA-2b] Modelo de voz de Whisper (ggml-base, multilingüe): dónde se guarda y cómo se baja.
 * Se baja a un archivo .part y solo se renombra al terminar, así nunca queda un modelo a medias.
 */
object WhisperModel {

    const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    const val MIN_BYTES = 100_000_000L

    class Result(
        val ok: Boolean,
        val message: String,
        val bytes: Long = 0,
        val cancelled: Boolean = false
    )

    fun file(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "ggml-base.bin")
    }

    fun isReady(context: Context): Boolean {
        val f = file(context)
        return f.exists() && f.length() > MIN_BYTES
    }

    /** [onProgress] recibe (porcentaje o -1 si no se sabe el total, megas bajados). */
    fun download(
        context: Context,
        onProgress: (Int, Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        val dest = file(context)
        val part = File(dest.parentFile, dest.name + ".part")
        var conn: HttpURLConnection? = null
        var success = false
        try {
            conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "CASTV")
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code != 200) return Result(false, "El servidor respondió HTTP $code")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -2
            var lastMb = -1L
            conn.inputStream.use { ins ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        if (isCancelled()) return Result(false, "Cancelado", cancelled = true)
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else -1
                        val mb = done / 1_000_000L
                        if (pct != lastPct || mb != lastMb) {
                            lastPct = pct
                            lastMb = mb
                            onProgress(pct, mb)
                        }
                    }
                }
            }
            if (part.length() < MIN_BYTES) {
                return Result(false, "La descarga quedó incompleta (${part.length() / 1_000_000L} MB)")
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) return Result(false, "No se pudo guardar el modelo")
            success = true
            return Result(true, "Modelo listo (${dest.length() / 1_000_000L} MB)", dest.length())
        } catch (e: Exception) {
            return Result(false, "Error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
            if (!success) {
                try { part.delete() } catch (e: Exception) { }
            }
        }
    }
}
