package com.castv.app

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [VIDEO-MP3-1b] Descarga de un enlace directo a un archivo (.mp4, .mkv, .mp3…) a Descargas, y reglas de qué enlaces
 * se admiten (también para convertir a MP3). El enlace nunca se guarda ni se escribe en el /log (R9).
 */
object LinkDownload {

    class Result(val ok: Boolean, val message: String, val savedAs: String = "", val cancelled: Boolean = false)

    val running = AtomicBoolean(false)

    private val blockedHosts = listOf(
        "youtube.com", "youtu.be", "tiktok.com", "instagram.com", "facebook.com", "fb.watch",
        "twitter.com", "x.com", "twitch.tv", "netflix.com", "spotify.com"
    )

    /** null = el enlace sirve; si no, el motivo en español. */
    fun check(raw: String): String? {
        val url = raw.trim()
        if (!(url.startsWith("http://", true) || url.startsWith("https://", true)))
            return "Pega un enlace que empiece por http:// o https://"
        val u = try { Uri.parse(url) } catch (e: Exception) { return "Enlace no válido" }
        val host = u.host?.lowercase() ?: return "Enlace no válido"
        if (!u.userInfo.isNullOrEmpty()) return "Los enlaces con usuario y clave no se admiten"
        if (blockedHosts.any { host == it || host.endsWith(".$it") })
            return "Ese sitio no se admite: solo enlaces directos a un archivo"
        val path = (u.path ?: "").lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd"))
            return "Las listas y los directos (.m3u8) no se pueden convertir ni descargar"
        return null
    }

    private fun safeName(s: String): String {
        var t = s.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim()
        if (t.length > 120) {
            val dot = t.lastIndexOf('.')
            t = if (dot in 1..10) t.substring(0, 120 - (t.length - dot)) + t.substring(dot) else t.substring(0, 120)
        }
        return t.trim().ifEmpty { "descarga" }
    }

    /** Nombre del archivo según el enlace (sin consultar la red). */
    fun nameFromUrl(url: String): String {
        val last = try { Uri.parse(url.trim()).lastPathSegment } catch (e: Exception) { null }
        return safeName(if (last.isNullOrBlank()) "enlace" else last)
    }

    private fun nameFromHeaders(disposition: String?, url: String, contentType: String): String {
        var name: String? = null
        if (!disposition.isNullOrBlank()) {
            val m = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)
            if (m != null) name = Uri.decode(m.groupValues[1].trim())
        }
        var n = safeName(name ?: nameFromUrl(url))
        if (!n.substringAfterLast('.', "").let { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType.substringBefore(';').trim())
            if (!ext.isNullOrEmpty()) n = "$n.$ext"
        }
        return n
    }

    private fun open(u: String): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
        return c
    }

    /**
     * Descarga [url] a Descargas. [onProgress] recibe (porcentaje, MB): el porcentaje es -1 si el servidor no dice
     * el tamaño. Si falla o se cancela no queda ningún archivo a medias.
     */
    fun download(
        context: Context,
        url: String,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (!running.compareAndSet(false, true)) return Result(false, "Ya hay una descarga en curso")
        var conn: HttpURLConnection? = null
        var sink: Mp3Converter.Sink? = null
        var success = false
        try {
            var cur = url.trim()
            var c = open(cur)
            conn = c
            var redirects = 0
            while (c.responseCode in 300..399) {
                val loc = c.getHeaderField("Location") ?: return Result(false, "Redirección sin destino")
                c.disconnect()
                redirects++
                if (redirects > 5) return Result(false, "Demasiadas redirecciones")
                cur = URL(URL(cur), loc).toString()
                val bad = check(cur)
                if (bad != null) return Result(false, bad)
                c = open(cur)
                conn = c
            }
            if (c.responseCode != 200) return Result(false, "El servidor respondió " + c.responseCode)
            val ctype = (c.contentType ?: "").lowercase()
            if (ctype.startsWith("text/")) return Result(false, "El enlace es una página web, no un archivo")
            val total = c.contentLengthLong
            val fname = nameFromHeaders(c.getHeaderField("Content-Disposition"), cur, ctype)
            val ext = fname.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val s = Mp3Converter.openSink(context, fname, mime)
            sink = s

            val buf = ByteArray(64 * 1024)
            var done = 0L
            var lastKey = -2
            c.inputStream.use { ins ->
                while (true) {
                    if (isCancelled()) return Result(false, "Cancelado", cancelled = true)
                    val n = ins.read(buf)
                    if (n < 0) break
                    s.stream.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else -1
                    val mb = (done / 1048576L).toInt()
                    val key = if (pct >= 0) pct else mb
                    if (key != lastKey) { lastKey = key; onProgress(pct, mb) }
                }
            }
            if (total > 0 && done < total) return Result(false, "La descarga quedó incompleta")
            if (done == 0L) return Result(false, "El enlace no trajo datos")
            val saved = s.commit()
            success = true
            return Result(true, "OK", saved)
        } catch (e: Throwable) {
            return Result(false, "Error: " + e.javaClass.simpleName)   // sin e.message: puede traer el enlace (R9)
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
            running.set(false)
        }
    }
}
