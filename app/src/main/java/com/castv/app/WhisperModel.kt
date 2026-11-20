package com.castv.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [IA-2b / IA-4] Modelos de voz de Whisper (ggml, multilingües): cuál está elegido, dónde se guardan
 * y cómo se bajan. Rápido = base, Preciso = small. Se baja a un archivo .part y solo se renombra al
 * terminar, así nunca queda un modelo a medias.
 */
object WhisperModel {

    const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    const val MIN_BYTES = 100_000_000L

    /** Un modelo disponible. [approxBytes] se usa solo para comprobar el espacio libre antes de bajar. */
    class Spec(
        val id: Int,
        val label: String,
        val shortName: String,
        val fileName: String,
        val url: String,
        val minBytes: Long,
        val approxBytes: Long,
        val sizeText: String
    ) {
        val fullLabel: String get() = "$label ($shortName)"
    }

    val MODELS: List<Spec> = listOf(
        Spec(0, "Rápido", "base", "ggml-base.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            100_000_000L, 148_000_000L, "~143 MB"),
        Spec(1, "Preciso", "small", "ggml-small.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            400_000_000L, 488_000_000L, "~466 MB")
    )

    /** Código de Whisper y nombre que se muestra. */
    val LANGS: List<Pair<String, String>> = listOf(
        "es" to "Español",
        "en" to "English",
        "pt" to "Português",
        "fr" to "Français",
        "it" to "Italiano",
        "de" to "Deutsch"
    )

    class Result(
        val ok: Boolean,
        val message: String,
        val bytes: Long = 0,
        val cancelled: Boolean = false
    )

    // ------------------------------------------------------------ Ajustes elegidos

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("castv_subs", Context.MODE_PRIVATE)

    fun selected(context: Context): Spec {
        val id = prefs(context).getInt("ia_model", 0)
        return MODELS.firstOrNull { it.id == id } ?: MODELS[0]
    }

    fun setModel(context: Context, id: Int) {
        prefs(context).edit().putInt("ia_model", id).apply()
    }

    fun lang(context: Context): String {
        val c = prefs(context).getString("ia_lang", "es") ?: "es"
        return if (LANGS.any { it.first == c }) c else "es"
    }

    fun setLang(context: Context, code: String) {
        prefs(context).edit().putString("ia_lang", code).apply()
    }

    fun langName(context: Context): String {
        val c = lang(context)
        return LANGS.firstOrNull { it.first == c }?.second ?: "Español"
    }

    /** Texto corto del modelo elegido, p. ej. "Rápido (base)". */
    fun label(context: Context): String = selected(context).fullLabel

    // ------------------------------------------------------------ Archivos

    fun file(context: Context, spec: Spec): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, spec.fileName)
    }

    fun isReady(context: Context, spec: Spec): Boolean {
        val f = file(context, spec)
        return f.exists() && f.length() > spec.minBytes
    }

    /** Versiones sin modelo: usan el elegido en Ajustes (así el código anterior sigue igual). */
    fun file(context: Context): File = file(context, selected(context))

    fun isReady(context: Context): Boolean = isReady(context, selected(context))

    fun download(
        context: Context,
        onProgress: (Int, Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result = download(context, selected(context), onProgress, isCancelled)

    /** [onProgress] recibe (porcentaje o -1 si no se sabe el total, megas bajados). */
    fun download(
        context: Context,
        spec: Spec,
        onProgress: (Int, Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        val dest = file(context, spec)
        val part = File(dest.parentFile, dest.name + ".part")
        val needed = spec.approxBytes + spec.approxBytes / 10
        val free = try { context.filesDir.usableSpace } catch (e: Exception) { Long.MAX_VALUE }
        if (free < needed) {
            return Result(false,
                "No hay espacio suficiente: hacen falta unos ${needed / 1_000_000L} MB libres y hay ${free / 1_000_000L} MB.")
        }
        var conn: HttpURLConnection? = null
        var success = false
        try {
            conn = URL(spec.url).openConnection() as HttpURLConnection
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
            if (part.length() < spec.minBytes) {
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
