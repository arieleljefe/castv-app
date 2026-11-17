package com.castv.app

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Servidor HTTP embebido. URLs de la forma /video/123.mp4 o /audio/45.mp3.
 *  Soporta Content-Length, HEAD y Range (206), que los TV (VIDAA/DLNA) exigen.
 *  [Etapa 13-A.2] También /relay/<enlace en base64>.mp4: intermediario que baja un mp4 de internet
 *  (https, redirecciones) y se lo sirve al TV por http desde el teléfono.
 *  [Etapa 13-A.3] /relay/<enlace en base64>.m3u8: lista HLS. Se baja, se reescriben sus enlaces para que
 *  apunten otra vez a /relay (lista maestra, listas hijas, trozos .ts/.m4s y llaves) y así el TV lo pide todo
 *  por http al teléfono. */
class MediaServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    /** Lee solo [length] bytes de un archivo y cierra el descriptor al terminar. */
    private class BoundedStream(
        private val pfd: ParcelFileDescriptor,
        private val input: FileInputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = input.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun available(): Int = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()

        override fun close() {
            try { input.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    /** [Etapa 13-A.2] Cierra también la conexión al origen cuando el TV deja de leer. */
    private class RelayStream(private val conn: HttpURLConnection) : FilterInputStream(conn.inputStream) {
        override fun close() {
            try { super.close() } catch (_: Exception) {}
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/log") {
            if (session.parameters.containsKey("clear")) DebugLog.clear()
            return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", DebugLog.dump())
        }
        DebugLog.add("TV pide ${session.method} ${session.uri} range=${session.headers["range"] ?: "-"} " +
                "de ${session.headers["remote-addr"] ?: "?"} ua=${session.headers["user-agent"] ?: "-"}")
        try {
            val parts = session.uri.trim('/').split("/")
            // [Etapa 13-A.2] /relay/<base64 del enlace>.mp4
            // [Etapa 13-A.3] la extensión dice qué se pide: mp4, m3u8 (lista HLS), ts/m4s (trozos), key…
            if (parts.size == 2 && parts[0] == "relay") return serveRelay(session,
                parts[1].substringBeforeLast('.'), parts[1].substringAfterLast('.', "").lowercase())
            if (parts.size == 2) {
                val kind = parts[0]
                val id = parts[1].substringBeforeLast('.').toLongOrNull()
                if (id != null && (kind == "video" || kind == "audio")) {
                    val mime = MediaScanner.mimeFor(parts[1], kind == "audio")
                    val contentUri = if (kind == "video")
                        Uri.parse("content://media/external/video/media/$id")
                    else
                        Uri.parse("content://media/external/audio/media/$id")
                    return serveFile(session, contentUri, mime)
                }
            }
        } catch (e: Exception) {
            DebugLog.add("ERROR sirviendo ${session.uri}: ${e.javaClass.simpleName}: ${e.message}")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
        DebugLog.add("404 ${session.uri}")
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado")
    }

    /** [Etapa 13-A.2] Baja el mp4 del enlace original y se lo pasa al TV. Sigue redirecciones
     *  (incluido http -> https) y reenvía la cabecera Range para que el TV pueda adelantar. */
    private fun serveRelay(session: IHTTPSession, token: String, ext: String): Response {
        val target = try {
            String(android.util.Base64.decode(token,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8)
        } catch (e: Exception) { null }
        if (target == null || !(target.startsWith("http://") || target.startsWith("https://"))) {
            DebugLog.add("  RELAY: enlace no válido")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Enlace no válido")
        }
        // [Etapa 13-A.3] a una lista .m3u8 no se le reenvía Range (se baja entera para reescribirla)
        val range = if (ext == "m3u8") null else session.headers["range"]
        var conn: HttpURLConnection? = null
        try {
            var current = target
            var hops = 0
            while (true) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 10000
                c.readTimeout = 30000
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
                c.setRequestProperty("Accept", "*/*")
                if (range != null) c.setRequestProperty("Range", range)
                val code = c.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    hops++
                    if (loc == null || hops > 8) {
                        DebugLog.add("  RELAY: demasiadas redirecciones o sin Location")
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Redirección inválida")
                    }
                    current = URL(URL(current), loc).toString()
                    DebugLog.add("  RELAY redirige ($code) -> $current")
                    continue
                }
                conn = c
                break
            }
            val c = conn!!
            val code = c.responseCode
            if (code != 200 && code != 206) {
                DebugLog.add("  RELAY: el origen respondió $code")
                c.disconnect()
                return newFixedLengthResponse(
                    when (code) {
                        404 -> Response.Status.NOT_FOUND
                        416 -> Response.Status.RANGE_NOT_SATISFIABLE
                        else -> Response.Status.INTERNAL_ERROR
                    }, "text/plain", "El origen respondió $code")
            }
            val upMime = c.contentType?.substringBefore(';')?.trim()?.lowercase()
            // [Etapa 13-A.3] lista HLS: se reescribe y se sirve como lista, no como video
            if (ext == "m3u8" || (upMime != null && upMime.contains("mpegurl"))) {
                return serveHlsPlaylist(session, c, URL(current))
            }
            val mime = when {
                upMime != null && (upMime.startsWith("video/") || upMime.startsWith("audio/")) -> upMime
                ext == "ts" -> "video/mp2t"            // [Etapa 13-A.3] trozos HLS
                ext == "aac" -> "audio/aac"
                ext == "key" -> "application/octet-stream"
                else -> "video/mp4"
            }
            val len = c.contentLengthLong            // -1 si el origen no dice el tamaño
            val contentRange = c.getHeaderField("Content-Range")
            val acceptRanges = if (code == 206 || c.getHeaderField("Accept-Ranges")?.contains("bytes") == true) "bytes" else "none"
            val status = if (code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            DebugLog.add("  -> RELAY $code $mime longitud=$len ${if (contentRange != null) "range=$contentRange" else ""}")

            val response: Response
            if (session.method == Method.HEAD) {
                c.disconnect()
                response = newFixedLengthResponse(status, mime, "")
                if (len >= 0) response.addHeader("Content-Length", len.toString())
            } else {
                val stream = RelayStream(c)
                response = if (len >= 0) newFixedLengthResponse(status, mime, stream, len)
                           else newChunkedResponse(status, mime, stream)
            }
            response.addHeader("Accept-Ranges", acceptRanges)
            if (contentRange != null) response.addHeader("Content-Range", contentRange)
            response.addHeader("transferMode.dlna.org", "Streaming")
            response.addHeader("contentFeatures.dlna.org",
                "DLNA.ORG_OP=${if (acceptRanges == "bytes") "01" else "00"};DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            return response
        } catch (e: Exception) {
            try { conn?.disconnect() } catch (_: Exception) {}
            DebugLog.add("  RELAY ERROR: ${e.javaClass.simpleName}: ${e.message}")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
    }

    /** [Etapa 13-A.3] Baja la lista HLS (.m3u8), reescribe cada enlace para que pase por /relay y se la
     *  sirve al TV. [base] es la URL final (tras redirecciones) para resolver enlaces relativos. */
    private fun serveHlsPlaylist(session: IHTTPSession, c: HttpURLConnection, base: URL): Response {
        val raw = try {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 2_000_000) break
                }
            }
            out.toByteArray()
        } finally {
            try { c.disconnect() } catch (_: Exception) {}
        }
        val text = String(raw, Charsets.UTF_8)
        if (raw.size > 2_000_000 || !text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) {
            DebugLog.add("  RELAY HLS: no parece una lista .m3u8 válida (${raw.size} bytes)")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "No es una lista HLS válida")
        }
        val host = session.headers["host"]      // ip:puerto con el que el TV llegó al teléfono
        val out = rewriteM3u8(text, base, host)
        val links = out.lines().count { it.isNotBlank() && !it.startsWith("#") }
        DebugLog.add("  -> RELAY HLS lista: $links enlaces reescritos (host=${host ?: "relativo"})")
        val bytes = out.toByteArray(Charsets.UTF_8)
        val response = newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl",
            ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("transferMode.dlna.org", "Streaming")
        return response
    }

    /** [Etapa 13-A.3] Cambia cada enlace de la lista (líneas sueltas y URI="…" de #EXT-X-KEY, #EXT-X-MAP,
     *  #EXT-X-MEDIA…) por http://host/relay/<base64>.<ext>. Los relativos se resuelven contra [base]. */
    private fun rewriteM3u8(text: String, base: URL, host: String?): String {
        val prefix = if (host != null) "http://$host" else ""
        fun proxy(ref: String): String {
            val abs = try { URL(base, ref).toString() } catch (e: Exception) { return ref }
            if (!(abs.startsWith("http://") || abs.startsWith("https://"))) return ref
            val tok = android.util.Base64.encodeToString(abs.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            val ext = abs.substringBefore('#').substringBefore('?')
                .substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
            val e = if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext else "ts"
            return "$prefix/relay/$tok.$e"
        }
        val uriAttr = Regex("URI=\"([^\"]*)\"")
        val sb = StringBuilder()
        for (line in text.split("\n")) {
            val l = line.trimEnd('\r')
            when {
                l.isBlank() -> sb.append(l)
                l.startsWith("#") -> sb.append(uriAttr.replace(l) { m -> "URI=\"" + proxy(m.groupValues[1]) + "\"" })
                else -> sb.append(proxy(l.trim()))
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun serveFile(session: IHTTPSession, uri: Uri, mime: String): Response {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado")
        val total = pfd.statSize

        // Range: bytes=start-end
        var start = 0L
        var end = total - 1
        var partial = false
        val range = session.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(",").trim()
            val s = spec.substringBefore("-")
            val e = spec.substringAfter("-", "")
            try {
                if (s.isEmpty()) { // sufijo: últimos N bytes
                    val n = e.toLong()
                    start = maxOf(0L, total - n)
                } else {
                    start = s.toLong()
                    if (e.isNotEmpty()) end = minOf(e.toLong(), total - 1)
                }
                partial = true
            } catch (_: NumberFormatException) {
                start = 0; end = total - 1; partial = false
            }
            if (partial && (start >= total || start > end)) {
                pfd.close()
                val r = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                r.addHeader("Content-Range", "bytes */$total")
                return r
            }
        }
        val length = end - start + 1
        DebugLog.add("  -> ${if (partial) "206" else "200"} $mime bytes $start-$end de $total")

        val response: Response
        if (session.method == Method.HEAD) {
            pfd.close()
            response = newFixedLengthResponse(
                if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK, mime, "")
            response.addHeader("Content-Length", length.toString())
        } else {
            val input = FileInputStream(pfd.fileDescriptor)
            if (start > 0) input.channel.position(start)
            response = newFixedLengthResponse(
                if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                mime, BoundedStream(pfd, input, length), length)
        }
        response.addHeader("Accept-Ranges", "bytes")
        if (partial) response.addHeader("Content-Range", "bytes $start-$end/$total")
        response.addHeader("transferMode.dlna.org", "Streaming")
        response.addHeader("contentFeatures.dlna.org",
            "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        return response
    }
}
