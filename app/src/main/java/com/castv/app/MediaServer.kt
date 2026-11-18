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
            // [LIVE-1] /live/<base64 del enlace .m3u8>.ts: canal HLS entregado como un solo flujo TS continuo
            if (parts.size == 2 && parts[0] == "live") return serveLive(session, parts[1].substringBeforeLast('.'))
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

    // ===================== [LIVE-1] Canal HLS como un solo flujo TS continuo =====================

    private class HttpFail(val code: Int) : Exception("HTTP $code")
    private class LiveUnsupported(msg: String) : Exception(msg)

    private object LiveHttp {
        /** Abre [url] siguiendo redirecciones (igual que serveRelay). Devuelve la conexión y la URL final. */
        fun open(url: String, readTimeout: Int): Pair<HttpURLConnection, String> {
            var current = url
            var hops = 0
            while (true) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 10000
                c.readTimeout = readTimeout
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
                c.setRequestProperty("Accept", "*/*")
                val code = c.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    hops++
                    if (loc == null || hops > 8) throw HttpFail(code)
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code != 200) {
                    c.disconnect()
                    throw HttpFail(code)
                }
                return Pair(c, current)
            }
        }

        /** Baja una lista de texto (máx. 2 MB). Devuelve el texto y la URL final. */
        fun text(url: String): Pair<String, String> {
            val opened = open(url, 15000)
            val c = opened.first
            try {
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
                return Pair(String(out.toByteArray(), Charsets.UTF_8), opened.second)
            } finally {
                try { c.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /** InputStream que va bajando los fragmentos .ts de una lista HLS, uno tras otro, y los entrega
     *  como un solo flujo continuo. Si la lista es maestra elige la variante de menor BANDWIDTH. */
    private class LiveHlsStream(private val startUrl: String) : InputStream() {
        private var mediaUrl: String? = null
        private val sent = LinkedHashSet<String>()
        private val pending = ArrayDeque<String>()
        private var first = true
        private var endList = false
        private var targetMs = 6000L
        private var cur: InputStream? = null
        private var curConn: HttpURLConnection? = null
        @Volatile private var closed = false
        private var count = 0
        private var keyLogged = false
        private var total = 0L            // [LIVE-1e] bytes entregados al TV
        private var segFirst = false      // [LIVE-1e] falta mirar el primer byte del fragmento actual
        // [LIVE-1d] cifrado AES-128: por fragmento -> (URL de la clave, IV en hexadecimal o null, número de secuencia)
        private val segKey = HashMap<String, Triple<String, String?, Long>>()
        private val keyCache = HashMap<String, ByteArray>()

        /** Primera lectura de la lista (antes de contestarle al TV): lanza LiveUnsupported si no se puede. */
        fun prepare() { refresh() }

        private fun resolveMedia() {
            val (text, base) = LiveHttp.text(startUrl)
            if (!text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) throw LiveUnsupported("no es una lista HLS")
            if (!text.contains("#EXT-X-STREAM-INF")) { mediaUrl = base; return }
            val lines = text.lines().map { it.trim() }
            // candidatos: (bandwidth, url, esVideoAvc)
            val cands = ArrayList<Triple<Long, String, Boolean>>()
            var i = 0
            while (i < lines.size) {
                val l = lines[i]
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: (Long.MAX_VALUE - 1)
                    val codecs = Regex("CODECS=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                    val videoOk = codecs == null || codecs.contains("avc1")
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                    if (j < lines.size) cands.add(Triple(bw, URL(URL(base), lines[j]).toString(), videoOk))
                    i = j
                }
                i++
            }
            if (cands.isEmpty()) throw LiveUnsupported("lista maestra sin variantes")
            val pool = cands.filter { it.third }.ifEmpty { cands }
            val best = pool.minByOrNull { it.first }!!
            mediaUrl = best.second
            DebugLog.add("  LIVE variante elegida BANDWIDTH=${if (best.first >= Long.MAX_VALUE - 1) "?" else best.first.toString()} -> ${best.second}")
        }

        /** Lee la lista hija y agrega a [pending] los fragmentos nuevos. */
        private fun refresh() {
            var retried = false
            while (true) {
                try {
                    if (mediaUrl == null) resolveMedia()
                    val (text, base) = LiveHttp.text(mediaUrl!!)
                    parse(text, base)
                    return
                } catch (e: HttpFail) {
                    if (!retried && (e.code == 403 || e.code == 404 || e.code == 410)) {
                        retried = true
                        mediaUrl = null
                        DebugLog.add("  LIVE la lista dio ${e.code}: se vuelve a resolver desde el enlace original")
                        continue
                    }
                    throw e
                }
            }
        }

        private fun parse(text: String, base: String) {
            if (!text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) throw LiveUnsupported("la lista hija no es válida")
            val segs = ArrayList<String>()
            var sawEnd = false
            var mediaSeq = 0L
            var curKey: String? = null
            var curIv: String? = null
            for (raw in text.lines()) {
                val l = raw.trim()
                if (l.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    mediaSeq = l.substringAfter(':').trim().toLongOrNull() ?: 0L
                } else if (l.startsWith("#EXT-X-KEY")) {
                    val method = Regex("METHOD=([A-Z0-9-]+)").find(l)?.groupValues?.get(1) ?: "?"
                    if (method == "NONE") { curKey = null; curIv = null }
                    else if (method == "AES-128") {
                        val uri = Regex("URI=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                            ?: throw LiveUnsupported("AES-128 sin URI de clave")
                        curKey = URL(URL(base), uri).toString()
                        curIv = Regex("IV=0[xX]([0-9a-fA-F]+)").find(l)?.groupValues?.get(1)
                        if (!keyLogged) { keyLogged = true; DebugLog.add("  LIVE cifrado AES-128 (se descifra en el teléfono)") }
                    } else throw LiveUnsupported("cifrado $method (solo se soporta AES-128)")
                } else if (l.startsWith("#EXT-X-MAP")) {
                    throw LiveUnsupported("fragmentos fMP4 (#EXT-X-MAP)")
                } else if (l.startsWith("#EXT-X-TARGETDURATION:")) {
                    targetMs = (l.substringAfter(':').trim().toLongOrNull() ?: 6L) * 1000L
                } else if (l.startsWith("#EXT-X-ENDLIST")) {
                    sawEnd = true
                } else if (l.isNotBlank() && !l.startsWith("#")) {
                    val abs = URL(URL(base), l).toString()
                    val ext = abs.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
                    if (ext == "m4s" || ext == "mp4" || ext == "cmfv" || ext == "cmfa") throw LiveUnsupported("fragmentos .$ext")
                    if (curKey != null) segKey[abs] = Triple(curKey, curIv, mediaSeq + segs.size)
                    segs.add(abs)
                }
            }
            endList = sawEnd
            var fresh = segs.filter { it !in sent }
            if (first) {
                first = false
                // en vivo se arranca cerca del final de la lista; en una lista cerrada, desde el principio
                if (!sawEnd && fresh.size > 3) fresh = fresh.takeLast(3)
                DebugLog.add("  LIVE lista: ${segs.size} fragmentos, empieza con ${fresh.size}, fin=${sawEnd}, duracion objetivo=${targetMs / 1000}s")
            }
            for (u in fresh) { pending.addLast(u); sent.add(u) }
            if (sent.size > 600) {
                val it = sent.iterator()
                var k = 0
                while (it.hasNext() && k < 200) { it.next(); it.remove(); k++ }
            }
        }

        private fun closeCur() {
            try { cur?.close() } catch (_: Exception) {}
            try { curConn?.disconnect() } catch (_: Exception) {}
            cur = null
            curConn = null
        }

        private fun fetchKey(uri: String): ByteArray {
            val opened = LiveHttp.open(uri, 15000)
            try {
                val k = ByteArray(16)
                var got = 0
                opened.first.inputStream.use { ins ->
                    while (got < 16) { val n = ins.read(k, got, 16 - got); if (n < 0) break; got += n }
                }
                if (got != 16) throw LiveUnsupported("clave AES de $got bytes (se esperaban 16)")
                return k
            } finally { try { opened.first.disconnect() } catch (_: Exception) {} }
        }

        private fun openSegment(u: String): Boolean {
            for (t in 1..3) {
                if (closed) return false
                try {
                    val opened = LiveHttp.open(u, 20000)
                    curConn = opened.first
                    val ki = segKey.remove(u)
                    cur = if (ki == null) opened.first.inputStream else {
                        val key = keyCache.getOrPut(ki.first) { fetchKey(ki.first) }
                        val iv = ByteArray(16)
                        if (ki.second != null) {
                            val hex = ki.second!!.padStart(32, '0').takeLast(32)
                            for (b in 0 until 16) iv[b] = hex.substring(b * 2, b * 2 + 2).toInt(16).toByte()
                        } else {
                            var n = ki.third
                            for (b in 15 downTo 8) { iv[b] = (n and 0xff).toByte(); n = n shr 8 }
                        }
                        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"),
                            javax.crypto.spec.IvParameterSpec(iv))
                        javax.crypto.CipherInputStream(opened.first.inputStream, cipher)
                    }
                    count++
                    segFirst = true
                    if (count <= 5 || count % 20 == 0) DebugLog.add("  LIVE fragmento $count")
                    return true
                } catch (e: Exception) {
                    DebugLog.add("  LIVE fragmento falló ($t/3): ${e.javaClass.simpleName}: ${e.message}")
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                }
            }
            return false
        }

        /** Deja abierto el siguiente fragmento. false = no hay más (o se cortó). */
        private fun nextSegment(): Boolean {
            var idleMs = 0L
            while (!closed) {
                if (pending.isEmpty()) {
                    if (endList) return false
                    refresh()
                    if (pending.isEmpty()) {
                        if (endList) return false
                        if (idleMs > 90_000L) {
                            DebugLog.add("  LIVE sin fragmentos nuevos por 90 s: se corta")
                            return false
                        }
                        val wait = maxOf(1000L, targetMs / 2)
                        var slept = 0L
                        while (slept < wait && !closed) {
                            try { Thread.sleep(250) } catch (_: InterruptedException) {}
                            slept += 250
                        }
                        idleMs += wait
                        continue
                    }
                }
                return openSegment(pending.removeFirst())
            }
            return false
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            try {
                while (!closed) {
                    val s = cur
                    if (s != null) {
                        val n = try { s.read(b, off, len) } catch (e: Exception) {
                            DebugLog.add("  LIVE error leyendo fragmento $count: ${e.javaClass.simpleName}: ${e.message}"); -1 }
                        if (n > 0) {
                            total += n
                            if (segFirst) {
                                segFirst = false
                                if (count <= 5) DebugLog.add("  LIVE fragmento $count primer byte=0x" +
                                    String.format("%02x", b[off].toInt() and 0xff) + " (TS valido = 0x47)")
                            }
                            return n
                        }
                        closeCur()
                        continue
                    }
                    if (!nextSegment()) {
                        DebugLog.add("  LIVE fin del flujo ($count fragmentos entregados)")
                        return -1
                    }
                }
            } catch (e: Exception) {
                DebugLog.add("  LIVE ERROR: ${e.javaClass.simpleName}: ${e.message}")
            }
            return -1
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else (one[0].toInt() and 0xff)
        }

        override fun close() {
            if (!closed) DebugLog.add("  LIVE cerrado (el TV cortó o terminó): $count fragmentos, $total bytes enviados")
            closed = true
            closeCur()
        }
    }

    /** [LIVE-1f] Tamaño anunciado al TV para el flujo en vivo (unos 2 GB, cabe en 32 bits con signo). */
    private val LIVE_FAKE_LENGTH = 2000000000L

    private fun addLiveHeaders(r: Response) {
        r.addHeader("Accept-Ranges", "none")
        r.addHeader("Cache-Control", "no-cache")
        r.addHeader("transferMode.dlna.org", "Streaming")
        r.addHeader("contentFeatures.dlna.org",
            "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
    }

    /** [LIVE-1] Entrega un canal HLS (.m3u8) como un solo flujo video/mp2t sin tamaño (chunked). */
    private fun serveLive(session: IHTTPSession, token: String): Response {
        val target = try {
            String(android.util.Base64.decode(token,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8)
        } catch (e: Exception) { null }
        if (target == null || !(target.startsWith("http://") || target.startsWith("https://"))) {
            DebugLog.add("  LIVE: enlace no válido")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Enlace no válido")
        }
        if (session.method == Method.HEAD) {
            val r = newFixedLengthResponse(Response.Status.OK, "video/mp2t", "")
            addLiveHeaders(r)
            return r
        }
        val stream = LiveHlsStream(target)
        try {
            stream.prepare()
        } catch (e: LiveUnsupported) {
            DebugLog.add("  LIVE no soportado: ${e.message}")
            stream.close()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "LIVE no soportado: ${e.message}")
        } catch (e: Exception) {
            DebugLog.add("  LIVE ERROR al preparar: ${e.javaClass.simpleName}: ${e.message}")
            stream.close()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
        // [LIVE-1f] sin chunked: Content-Length "falso" y grande (el cuerpo termina cuando se cierra el flujo)
        DebugLog.add("  -> LIVE video/mp2t continuo (LIVE-1f: sin chunked, Content-Length $LIVE_FAKE_LENGTH)")
        val response = newFixedLengthResponse(Response.Status.OK, "video/mp2t", stream, LIVE_FAKE_LENGTH)
        addLiveHeaders(response)
        response.addHeader("Connection", "close")
        return response
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
