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

    companion object {
        /** [AVISO-HLS] Revisa, antes de mandar un canal HLS al TV, si se puede preparar (misma lectura de lista que
         *  usa /live). Devuelve null si todo va bien, o el motivo en español. No abre fragmentos ni deja nada abierto. */
        fun probeLive(url: String): String? {
            val s = LiveHlsStream(url)
            return try {
                s.prepare()
                if (s.hasSegments()) null
                else "La lista del canal no trae fragmentos ahora mismo (puede estar vacía o fuera de emisión)."
            } catch (e: HttpFail) {
                when (e.code) {
                    401, 403 -> "El servidor rechazó la petición (${e.code}): el canal puede estar bloqueado para tu país o pedir permiso."
                    404, 410 -> "El canal ya no existe en ese enlace (${e.code})."
                    else -> "El servidor del canal respondió con error (${e.code})."
                }
            } catch (e: LiveUnsupported) {
                "Este canal usa un formato que el TV no puede recibir por ahora (${e.message})."
            } catch (e: java.net.SocketTimeoutException) {
                "El servidor del canal tardó demasiado en responder."
            } catch (e: java.net.UnknownHostException) {
                "No se encontró el servidor del canal (¿sin internet o dirección caída?)."
            } catch (e: java.io.IOException) {
                "No se pudo conectar con el servidor del canal (${e.javaClass.simpleName})."
            } catch (e: Exception) {
                "No se pudo leer la lista del canal (${e.javaClass.simpleName}: ${e.message})."
            }
        }

        /** [CHECK-1] Comprobación ligera de un canal: ¿responde su servidor? Devuelve null si responde bien, o el motivo
         *  corto en español. En listas .m3u8 lee solo el comienzo y exige "#EXTM3U"; en otros enlaces basta un 200.
         *  No reproduce nada ni deja conexiones abiertas (5 s para conectar y 5 s para leer). */
        fun checkChannel(url: String): String? {
            return try {
                val c = LiveHttp.open(url, 5000, 5000).first
                try {
                    if (url.substringBefore('?').lowercase().endsWith(".m3u8")) {
                        val src: InputStream = if (c.contentEncoding.equals("gzip", ignoreCase = true))
                            java.util.zip.GZIPInputStream(c.inputStream) else c.inputStream
                        val buf = ByteArray(512)
                        var n = 0
                        while (n < buf.size) {
                            val r = src.read(buf, n, buf.size - n)
                            if (r < 0) break
                            n += r
                        }
                        val head = String(buf, 0, n, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
                        if (head.startsWith("#EXTM3U")) null else "La respuesta no es una lista de canal válida"
                    } else null
                } finally {
                    try { c.disconnect() } catch (_: Exception) {}
                }
            } catch (e: HttpFail) {
                when (e.code) {
                    401, 403 -> "Bloqueado o pide permiso (${e.code})"
                    404, 410 -> "Ya no existe (${e.code})"
                    else -> "Error del servidor (${e.code})"
                }
            } catch (e: java.net.SocketTimeoutException) {
                "No respondió a tiempo"
            } catch (e: java.net.UnknownHostException) {
                "Servidor no encontrado"
            } catch (e: Exception) {
                "No se pudo conectar (${e.javaClass.simpleName})"
            }
        }
    }

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
        fun open(url: String, readTimeout: Int, connectTimeout: Int = 10000): Pair<HttpURLConnection, String> {
            var current = url
            var hops = 0
            while (true) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = connectTimeout
                c.readTimeout = readTimeout
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
                c.setRequestProperty("Accept", "*/*")
                // [GZIP-1] sin compresión: evita el error "ISIZE ... 0xffffffff" del GZIP automático de Android
                c.setRequestProperty("Accept-Encoding", "identity")
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
                // [GZIP-1] si el servidor comprime aunque se pidió identity, se descomprime a mano
                val src: InputStream = if (c.contentEncoding.equals("gzip", ignoreCase = true))
                    java.util.zip.GZIPInputStream(c.inputStream) else c.inputStream
                src.use { ins ->
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
        // [MUS-1] audio en una lista aparte (#EXT-X-MEDIA:TYPE=AUDIO): se baja y se une al video fragmento a fragmento
        private var audioListUrl: String? = null
        private val audioBySeq = LinkedHashMap<Long, String>()   // número de secuencia -> URL del fragmento de audio
        private val segSeq = HashMap<String, Long>()             // URL del fragmento de video -> número de secuencia
        private val tsMux = TsMux()
        private var muxLogged = 0
        private var muxFails = 0

        /** Primera lectura de la lista (antes de contestarle al TV): lanza LiveUnsupported si no se puede. */
        fun prepare() { refresh() }

        /** [AVISO-HLS] true si, tras la primera lectura, hay al menos un fragmento por entregar. */
        fun hasSegments(): Boolean = pending.isNotEmpty()

        private fun resolveMedia() {
            val (text, base) = LiveHttp.text(startUrl)
            if (!text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) throw LiveUnsupported("no es una lista HLS")
            if (!text.contains("#EXT-X-STREAM-INF")) { mediaUrl = base; return }
            val lines = text.lines().map { it.trim() }
            // candidatos: (bandwidth, url, esVideoAvc)
            val cands = ArrayList<Triple<Long, String, Boolean>>()
            val audioGroups = HashMap<String, String>()   // [MUS-1] URL de la variante -> AUDIO="grupo"
            var i = 0
            while (i < lines.size) {
                val l = lines[i]
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: (Long.MAX_VALUE - 1)
                    val codecs = Regex("CODECS=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                    val videoOk = codecs == null || codecs.contains("avc1")
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                    if (j < lines.size) {
                        val vurl = URL(URL(base), lines[j]).toString()
                        cands.add(Triple(bw, vurl, videoOk))
                        val ag = Regex("AUDIO=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                        if (ag != null) audioGroups[vurl] = ag
                    }
                    i = j
                }
                i++
            }
            if (cands.isEmpty()) throw LiveUnsupported("lista maestra sin variantes")
            val pool = cands.filter { it.third }.ifEmpty { cands }
            val best = pool.minByOrNull { it.first }!!
            mediaUrl = best.second
            // [MUS-1] si el audio va en una lista aparte, se anota para unirlo al video
            audioListUrl = null
            val grp = audioGroups[best.second]
            if (grp != null) {
                var chosen: String? = null
                for (ml in lines) {
                    if (!ml.startsWith("#EXT-X-MEDIA") || !ml.contains("TYPE=AUDIO")) continue
                    if (Regex("GROUP-ID=\"([^\"]*)\"").find(ml)?.groupValues?.get(1) != grp) continue
                    val uri = Regex("URI=\"([^\"]*)\"").find(ml)?.groupValues?.get(1) ?: continue
                    val abs = URL(URL(base), uri).toString()
                    if (ml.contains("DEFAULT=YES")) { chosen = abs; break }
                    if (chosen == null) chosen = abs
                }
                audioListUrl = chosen
                if (chosen != null) DebugLog.add("  LIVE audio aparte (grupo $grp): $chosen")
            }
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
                    if (abs !in sent) segSeq[abs] = mediaSeq + segs.size   // [MUS-1]
                    segs.add(abs)
                }
            }
            endList = sawEnd
            var fresh = segs.filter { it !in sent }
            if (first) {
                first = false
                // en vivo se arranca cerca del final de la lista; en una lista cerrada, desde el principio
                if (!sawEnd && fresh.size > 3) fresh = fresh.takeLast(3)
                // [MUS-1] los viejos que no se entregan se marcan como ya vistos (antes salían como "nuevos" en la
                // lectura siguiente y se entregaban fuera de orden)
                val keep = fresh.toHashSet()
                for (s in segs) if (s !in keep) { sent.add(s); segSeq.remove(s) }
                DebugLog.add("  LIVE lista: ${segs.size} fragmentos, empieza con ${fresh.size}, fin=${sawEnd}, duracion objetivo=${targetMs / 1000}s")
            }
            for (u in fresh) { pending.addLast(u); sent.add(u) }
            // [MUS-1] solo hace falta recordar los fragmentos que siguen en la lista (los demás ya no reaparecen)
            if (sent.size > 600) sent.retainAll(segs.toHashSet())
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
                val vu = pending.removeFirst()
                if (!openSegment(vu)) return false
                if (audioListUrl != null) joinAudio(vu)   // [MUS-1]
                return true
            }
            return false
        }

        private fun readAll(s: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            s.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 20_000_000) break
                }
            }
            return out.toByteArray()
        }

        /** [MUS-1] Lee la lista de audio y apunta sus fragmentos por número de secuencia. */
        private fun refreshAudio() {
            val au = audioListUrl ?: return
            try {
                val (text, base) = LiveHttp.text(au)
                if (!text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) throw LiveUnsupported("la lista de audio no es válida")
                var seq = 0L
                var idx = 0
                for (raw in text.lines()) {
                    val l = raw.trim()
                    if (l.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                        seq = l.substringAfter(':').trim().toLongOrNull() ?: 0L
                    } else if (l.startsWith("#EXT-X-KEY")) {
                        if (!l.contains("METHOD=NONE")) throw LiveUnsupported("audio cifrado")
                    } else if (l.startsWith("#EXT-X-MAP")) {
                        throw LiveUnsupported("audio fMP4")
                    } else if (l.isNotBlank() && !l.startsWith("#")) {
                        val abs = URL(URL(base), l).toString()
                        val ext = abs.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
                        if (ext != "ts") throw LiveUnsupported("audio .$ext")
                        audioBySeq[seq + idx] = abs
                        idx++
                    }
                }
                while (audioBySeq.size > 40) {
                    val iter = audioBySeq.keys.iterator()
                    iter.next()
                    iter.remove()
                }
            } catch (e: LiveUnsupported) {
                DebugLog.add("  LIVE audio aparte no soportado (${e.message}): solo video")
                audioListUrl = null
            } catch (e: Exception) {
                DebugLog.add("  LIVE audio: no se pudo leer la lista (${e.javaClass.simpleName}: ${e.message})")
            }
        }

        /** [MUS-1] El fragmento de video [videoUrl] ya está abierto en [cur]: se baja entero, se baja el de audio
         *  de la misma secuencia y [cur] pasa a ser un solo TS con los dos. Si algo falla, queda solo el video. */
        private fun joinAudio(videoUrl: String) {
            val seq = segSeq.remove(videoUrl)
            val src = cur ?: return
            val vb: ByteArray = try { readAll(src) } catch (e: Exception) {
                DebugLog.add("  LIVE error leyendo fragmento $count: ${e.javaClass.simpleName}: ${e.message}")
                closeCur()
                return
            }
            closeCur()
            var out = vb
            try {
                if (seq == null) throw IllegalStateException("sin número de secuencia")
                var au = audioBySeq[seq]
                var t = 0
                while (au == null && t < 4 && audioListUrl != null && !closed) {
                    refreshAudio()
                    au = audioBySeq[seq]
                    t++
                    if (au == null) { try { Thread.sleep(500) } catch (_: InterruptedException) {} }
                }
                if (au == null) throw IllegalStateException("el audio de la secuencia $seq no está publicado")
                var ab: ByteArray? = null
                var tries = 0
                while (ab == null && tries < 3 && !closed) {
                    tries++
                    try {
                        val opened = LiveHttp.open(au, 15000)
                        try { ab = readAll(opened.first.inputStream) } finally { try { opened.first.disconnect() } catch (_: Exception) {} }
                    } catch (e: Exception) {
                        DebugLog.add("  LIVE audio falló ($tries/3): ${e.javaClass.simpleName}: ${e.message}")
                        try { Thread.sleep(300) } catch (_: InterruptedException) {}
                    }
                }
                if (ab == null) throw IllegalStateException("no se pudo bajar el audio")
                out = tsMux.join(vb, ab)
                if (muxLogged < 3) {
                    muxLogged++
                    DebugLog.add("  LIVE audio+video: ${videoUrl.substringAfterLast('/')} + ${au.substringAfterLast('/')} " +
                        "(video ${vb.size} + audio ${ab.size} -> ${out.size} bytes)")
                }
            } catch (e: Exception) {
                muxFails++
                if (muxFails <= 5) DebugLog.add("  LIVE mezcla falló en el fragmento $count (${e.javaClass.simpleName}: ${e.message}): se entrega solo video")
                out = vb
            }
            cur = ByteArrayInputStream(out)
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

    /** [MUS-1] Une un fragmento TS que solo trae video con el de solo audio del mismo momento y devuelve un solo TS
     *  con los dos flujos: PAT y PMT nuevas, el audio cambia de PID si choca con el del video, y los paquetes de los
     *  dos se entrelazan por tiempo (PTS/DTS de las cabeceras PES). Todo en memoria, un fragmento a la vez. */
    private class TsMux {
        private class Es(val type: Int, val pid: Int, val info: ByteArray)
        private class Psi(val tsid: Int, val program: Int, val pmtPid: Int, val pcrPid: Int, val es: List<Es>)

        private var patCc = 0
        private var pmtCc = 0

        private fun u(b: ByteArray, i: Int): Int = b[i].toInt() and 0xff
        private fun pidOf(b: ByteArray, o: Int): Int = ((u(b, o + 1) and 0x1f) shl 8) or u(b, o + 2)

        /** Posición del contenido del paquete que empieza en [o], o -1 si no trae. */
        private fun payloadPos(b: ByteArray, o: Int): Int {
            val afc = (u(b, o + 3) shr 4) and 3
            val p = when (afc) {
                1 -> o + 4
                3 -> o + 5 + u(b, o + 4)
                else -> return -1
            }
            return if (p < o + 188) p else -1
        }

        private fun syncCheck(b: ByteArray): Int {
            val n = b.size / 188
            if (n == 0) throw IllegalStateException("fragmento TS vacío")
            for (k in 0 until n) {
                if (u(b, k * 188) != 0x47) throw IllegalStateException("fragmento TS sin sincronía (paquete $k)")
            }
            return n
        }

        private fun ts33(b: ByteArray, p: Int): Long =
            ((b[p].toLong() and 0x0e) shl 29) or ((b[p + 1].toLong() and 0xff) shl 22) or
                ((b[p + 2].toLong() and 0xfe) shl 14) or ((b[p + 3].toLong() and 0xff) shl 7) or
                ((b[p + 4].toLong() and 0xff) shr 1)

        private fun parsePsi(b: ByteArray, n: Int): Psi {
            var tsid = 0
            var program = -1
            var pmtPid = -1
            for (k in 0 until n) {
                val o = k * 188
                if (pidOf(b, o) != 0 || (u(b, o + 1) and 0x40) == 0) continue
                val p = payloadPos(b, o)
                if (p < 0) continue
                val s = p + 1 + u(b, p)
                if (s + 8 > o + 188 || u(b, s) != 0) continue
                val end = s + 3 + (((u(b, s + 1) and 0x0f) shl 8) or u(b, s + 2)) - 4
                if (end > o + 188) throw IllegalStateException("PAT en varios paquetes")
                tsid = (u(b, s + 3) shl 8) or u(b, s + 4)
                var q = s + 8
                while (q + 4 <= end) {
                    val prog = (u(b, q) shl 8) or u(b, q + 1)
                    if (prog != 0) {
                        program = prog
                        pmtPid = ((u(b, q + 2) and 0x1f) shl 8) or u(b, q + 3)
                        break
                    }
                    q += 4
                }
                if (program >= 0) break
            }
            if (program < 0) throw IllegalStateException("sin PAT")
            for (k in 0 until n) {
                val o = k * 188
                if (pidOf(b, o) != pmtPid || (u(b, o + 1) and 0x40) == 0) continue
                val p = payloadPos(b, o)
                if (p < 0) continue
                val s = p + 1 + u(b, p)
                if (s + 12 > o + 188 || u(b, s) != 2) continue
                val end = s + 3 + (((u(b, s + 1) and 0x0f) shl 8) or u(b, s + 2)) - 4
                if (end > o + 188) throw IllegalStateException("PMT en varios paquetes")
                val pcr = ((u(b, s + 8) and 0x1f) shl 8) or u(b, s + 9)
                var q = s + 12 + (((u(b, s + 10) and 0x0f) shl 8) or u(b, s + 11))
                val list = ArrayList<Es>()
                while (q + 5 <= end) {
                    val len = ((u(b, q + 3) and 0x0f) shl 8) or u(b, q + 4)
                    if (q + 5 + len > end) break
                    list.add(Es(u(b, q), ((u(b, q + 1) and 0x1f) shl 8) or u(b, q + 2), b.copyOfRange(q + 5, q + 5 + len)))
                    q += 5 + len
                }
                if (list.isEmpty()) throw IllegalStateException("PMT sin flujos")
                return Psi(tsid, program, pmtPid, pcr, list)
            }
            throw IllegalStateException("sin PMT")
        }

        /** Marca de tiempo (90 kHz) de cada paquete: la del último PES de su PID (DTS si hay, si no PTS). */
        private fun keysOf(b: ByteArray, n: Int): LongArray {
            val keys = LongArray(n) { -1L }
            val last = HashMap<Int, Long>()
            for (k in 0 until n) {
                val o = k * 188
                val pid = pidOf(b, o)
                if ((u(b, o + 1) and 0x40) != 0) {
                    val p = payloadPos(b, o)
                    if (p >= 0 && p + 14 <= o + 188 && u(b, p) == 0 && u(b, p + 1) == 0 && u(b, p + 2) == 1) {
                        val fl = (u(b, p + 7) shr 6) and 3
                        if (fl == 3 && p + 19 <= o + 188) last[pid] = ts33(b, p + 14)
                        else if (fl >= 2) last[pid] = ts33(b, p + 9)
                    }
                }
                keys[k] = last[pid] ?: -1L
            }
            var first = -1L
            for (v in keys) { if (v >= 0) { first = v; break } }
            if (first < 0) throw IllegalStateException("sin marcas de tiempo")
            for (k in 0 until n) { if (keys[k] < 0) keys[k] = first }
            return keys
        }

        /** CRC-32 de MPEG (polinomio 0x04C11DB7, valor inicial 0xFFFFFFFF, sin inversión). */
        private fun crc32(b: ByteArray, len: Int): Int {
            var crc = -1
            for (i in 0 until len) {
                crc = crc xor (u(b, i) shl 24)
                for (k in 0 until 8) {
                    crc = if ((crc and Int.MIN_VALUE) != 0) (crc shl 1) xor 0x04C11DB7 else (crc shl 1)
                }
            }
            return crc
        }

        private fun withCrc(body: ByteArray): ByteArray {
            val out = ByteArray(body.size + 4)
            System.arraycopy(body, 0, out, 0, body.size)
            val c = crc32(body, body.size)
            out[body.size] = (c ushr 24).toByte()
            out[body.size + 1] = (c ushr 16).toByte()
            out[body.size + 2] = (c ushr 8).toByte()
            out[body.size + 3] = c.toByte()
            return out
        }

        private fun psiPacket(pid: Int, cc: Int, section: ByteArray): ByteArray {
            if (section.size > 183) throw IllegalStateException("sección PSI demasiado grande")
            val pk = ByteArray(188) { 0xFF.toByte() }
            pk[0] = 0x47
            pk[1] = (0x40 or (pid shr 8)).toByte()
            pk[2] = (pid and 0xff).toByte()
            pk[3] = (0x10 or (cc and 0x0f)).toByte()
            pk[4] = 0
            System.arraycopy(section, 0, pk, 5, section.size)
            return pk
        }

        private fun patSection(psi: Psi): ByteArray {
            val body = byteArrayOf(
                0x00, 0xB0.toByte(), 13,
                (psi.tsid shr 8).toByte(), psi.tsid.toByte(),
                0xC1.toByte(), 0, 0,
                (psi.program shr 8).toByte(), psi.program.toByte(),
                (0xE0 or (psi.pmtPid shr 8)).toByte(), psi.pmtPid.toByte()
            )
            return withCrc(body)
        }

        private fun pmtSection(psi: Psi, streams: List<Es>): ByteArray {
            var esLen = 0
            for (e in streams) esLen += 5 + e.info.size
            val secLen = 9 + esLen + 4
            val out = ByteArrayOutputStream()
            out.write(0x02)
            out.write(0xB0 or (secLen shr 8))
            out.write(secLen and 0xff)
            out.write(psi.program shr 8)
            out.write(psi.program and 0xff)
            out.write(0xC1)
            out.write(0)
            out.write(0)
            out.write(0xE0 or (psi.pcrPid shr 8))
            out.write(psi.pcrPid and 0xff)
            out.write(0xF0)
            out.write(0)
            for (e in streams) {
                out.write(e.type)
                out.write(0xE0 or (e.pid shr 8))
                out.write(e.pid and 0xff)
                out.write(0xF0 or (e.info.size shr 8))
                out.write(e.info.size and 0xff)
                out.write(e.info, 0, e.info.size)
            }
            return withCrc(out.toByteArray())
        }

        fun join(vb: ByteArray, ab: ByteArray): ByteArray {
            val nv = syncCheck(vb)
            val na = syncCheck(ab)
            val vp = parsePsi(vb, nv)
            val ap = parsePsi(ab, na)
            // PIDs del audio: se conservan salvo que choquen con los del video
            val used = HashSet<Int>()
            used.add(0); used.add(0x1fff); used.add(vp.pmtPid); used.add(vp.pcrPid)
            for (e in vp.es) used.add(e.pid)
            val aPids = HashSet<Int>()
            for (e in ap.es) aPids.add(e.pid)
            val audioMap = HashMap<Int, Int>()
            val audioEs = ArrayList<Es>()
            var free = 0x100
            for (e in ap.es) {
                var np = e.pid
                if (np in used) {
                    while (free in used || free in aPids) free++
                    np = free
                    free++
                }
                used.add(np)
                audioMap[e.pid] = np
                audioEs.add(Es(e.type, np, e.info))
            }
            val kv = keysOf(vb, nv)
            val ka = keysOf(ab, na)
            val vIdx = ArrayList<Int>()
            for (k in 0 until nv) {
                val pid = pidOf(vb, k * 188)
                if (pid != 0 && pid != vp.pmtPid && pid != 0x1fff) vIdx.add(k)
            }
            val aIdx = ArrayList<Int>()
            for (k in 0 until na) {
                if (audioMap.containsKey(pidOf(ab, k * 188))) aIdx.add(k)
            }
            if (aIdx.isEmpty()) throw IllegalStateException("el fragmento de audio no trae paquetes de audio")

            val out = ByteArrayOutputStream(vb.size + ab.size + 400)
            out.write(psiPacket(0, patCc, patSection(vp)))
            patCc = (patCc + 1) and 0x0f
            out.write(psiPacket(vp.pmtPid, pmtCc, pmtSection(vp, vp.es + audioEs)))
            pmtCc = (pmtCc + 1) and 0x0f

            val tmp = ByteArray(188)
            var i = 0
            var j = 0
            while (i < vIdx.size || j < aIdx.size) {
                val takeVideo = if (i >= vIdx.size) false else if (j >= aIdx.size) true else kv[vIdx[i]] <= ka[aIdx[j]]
                if (takeVideo) {
                    out.write(vb, vIdx[i] * 188, 188)
                    i++
                } else {
                    System.arraycopy(ab, aIdx[j] * 188, tmp, 0, 188)
                    val old = pidOf(tmp, 0)
                    val np = audioMap[old] ?: old
                    if (np != old) {
                        tmp[1] = ((u(tmp, 1) and 0xE0) or (np shr 8)).toByte()
                        tmp[2] = (np and 0xff).toByte()
                    }
                    out.write(tmp, 0, 188)
                    j++
                }
            }
            return out.toByteArray()
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

    /** [LIVE-1] Entrega un canal HLS (.m3u8) como un solo flujo video/mp2t. [LIVE-2] Se comporta como un archivo
     *  de [LIVE_FAKE_LENGTH] bytes atendido por Range desde un buffer compartido por canal (ver LiveHub). */
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
        // [LIVE-2] la TV trata el flujo como un archivo de LIVE_FAKE_LENGTH bytes y lo pide por Range
        var start = 0L
        var end = LIVE_FAKE_LENGTH - 1
        var partial = false
        val range = session.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(",").trim()
            val s = spec.substringBefore("-")
            val e = spec.substringAfter("-", "")
            try {
                if (s.isEmpty()) { // sufijo: últimos N bytes
                    start = maxOf(0L, LIVE_FAKE_LENGTH - e.toLong())
                } else {
                    start = s.toLong()
                    if (e.isNotEmpty()) end = minOf(e.toLong(), LIVE_FAKE_LENGTH - 1)
                }
                partial = true
            } catch (_: NumberFormatException) {
                start = 0L; end = LIVE_FAKE_LENGTH - 1; partial = false
            }
        }
        if (start >= LIVE_FAKE_LENGTH || start > end) {
            val r = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
            r.addHeader("Content-Range", "bytes */$LIVE_FAKE_LENGTH")
            return r
        }
        val length = end - start + 1
        val hub = try {
            synchronized(hubs) { findOrCreateHub(token, target, start) }
        } catch (e: LiveUnsupported) {
            DebugLog.add("  LIVE no soportado: ${e.message}")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "LIVE no soportado: ${e.message}")
        } catch (e: Exception) {
            DebugLog.add("  LIVE ERROR al preparar: ${e.javaClass.simpleName}: ${e.message}")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
        val body: InputStream = if (hub != null && hub.canServe(start)) HubStream(hub, start, length)
            else { hub?.release(); NullTsStream(start, length) }
        DebugLog.add("  -> LIVE2 ${if (partial) "206" else "200"} bytes $start-$end " +
                if (body is HubStream) "del buffer (${hub?.window()})" else "relleno de TS nulo (fuera del buffer)")
        val response = newFixedLengthResponse(
            if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK, "video/mp2t", body, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "no-cache")
        if (partial) response.addHeader("Content-Range", "bytes $start-$end/$LIVE_FAKE_LENGTH")
        response.addHeader("transferMode.dlna.org", "Streaming")
        response.addHeader("contentFeatures.dlna.org",
            "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        response.addHeader("Connection", "close")
        return response
    }

    // ===================== [LIVE-2] Un solo flujo por canal, con buffer, atendido por Range =====================

    /** Canales en marcha, por el token del enlace. Se toca siempre dentro de synchronized(hubs). */
    private val hubs = HashMap<String, LiveHub>()

    /** Hasta dónde (en bytes) se acepta abrir un canal nuevo: los sondeos al final del "archivo" no lo abren. */
    private val LIVE_REACH = 8L * 1024 * 1024

    /** [GZIP-1] Crea el flujo del canal y lee la primera lista; ante un fallo pasajero reintenta una vez. */
    private fun openLiveStream(target: String): LiveHlsStream {
        var stream = LiveHlsStream(target)
        try {
            stream.prepare()
        } catch (e: LiveUnsupported) {
            stream.close()
            throw e
        } catch (e: Exception) {
            DebugLog.add("  LIVE falló al preparar (${e.javaClass.simpleName}: ${e.message}): se reintenta una vez")
            stream.close()
            try { Thread.sleep(500) } catch (_: InterruptedException) {}
            stream = LiveHlsStream(target)
            try {
                stream.prepare()
            } catch (e2: Exception) {
                stream.close()
                throw e2
            }
        }
        return stream
    }

    /** Devuelve el flujo en marcha del canal (o lo abre). null = pedido lejano sin flujo abierto (se rellena).
     *  Si devuelve un flujo, ya contó un cliente: quien lo use debe llamar a release() al terminar. */
    private fun findOrCreateHub(token: String, target: String, start: Long): LiveHub? {
        val now = System.currentTimeMillis()
        val iter = hubs.entries.iterator()
        while (iter.hasNext()) {
            val h = iter.next().value
            if (h.closed || (h.clients.get() == 0 && now - h.lastUse > 30_000L)) { h.close(); iter.remove() }
        }
        var h = hubs[token]
        // un pedido desde el principio, con el flujo viejo o ya reciclado: se empieza de nuevo (si no, la TV vería lo de hace rato)
        if (h != null && start == 0L && (!h.hasFrom0() || now - h.lastUse > 10_000L)) {
            DebugLog.add("  LIVE2 se reinicia el canal (el flujo anterior estaba viejo)")
            h.close()
            hubs.remove(token)
            h = null
        }
        if (h == null) {
            if (start > LIVE_REACH) return null
            h = LiveHub(openLiveStream(target))
            h.start()
            hubs[token] = h
            DebugLog.add("  LIVE2 flujo nuevo del canal")
        }
        h.clients.incrementAndGet()
        h.lastUse = now
        return h
    }

    /** Baja el canal (LiveHlsStream) en un hilo propio y guarda los bytes en un buffer circular numerado desde 0,
     *  como si fuera un archivo. Varias conexiones de la TV leen del mismo buffer, cada una desde su posición. */
    private class LiveHub(private val src: LiveHlsStream) {
        private val cap = 16 * 1024 * 1024            // bytes que se conservan atrás
        private val look = 6L * 1024 * 1024           // cuánto se adelanta a lo ya entregado a la TV
        private val ring = ByteArray(cap)
        private val lock = java.util.concurrent.locks.ReentrantLock()
        private val cond = lock.newCondition()
        private var head = 0L                         // bytes producidos hasta ahora
        private var served = 0L                       // posición más alta entregada a la TV
        private var ended = false
        @Volatile var closed = false
            private set
        @Volatile var lastUse = System.currentTimeMillis()
        val clients = java.util.concurrent.atomic.AtomicInteger(0)

        private fun idleTooLong(): Boolean = clients.get() == 0 && System.currentTimeMillis() - lastUse > 30_000L

        fun hasFrom0(): Boolean {
            lock.lock()
            try { return head <= cap } finally { lock.unlock() }
        }

        /** true si [start] está en el buffer o a poca distancia por delante (se espera a que llegue). */
        fun canServe(start: Long): Boolean {
            lock.lock()
            try { return start >= maxOf(0L, head - cap) && start <= head + look } finally { lock.unlock() }
        }

        fun window(): String {
            lock.lock()
            try { return "${maxOf(0L, head - cap)}-$head" } finally { lock.unlock() }
        }

        fun release() {
            clients.decrementAndGet()
            lastUse = System.currentTimeMillis()
        }

        fun start() {
            val t = Thread {
                val tmp = ByteArray(32768)
                try {
                    while (!closed) {
                        if (idleTooLong()) {
                            DebugLog.add("  LIVE2 canal sin clientes por 30 s: se cierra")
                            close()
                            break
                        }
                        // freno: no se adelanta más de [look] bytes a lo que ya recibió la TV
                        lock.lock()
                        try {
                            while (!closed && head - served >= look && !idleTooLong()) {
                                cond.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                            }
                        } finally { lock.unlock() }
                        if (closed) break
                        val n = src.read(tmp, 0, tmp.size)
                        if (n < 0) break
                        lock.lock()
                        try {
                            var o = 0
                            while (o < n) {
                                val p = (head % cap).toInt()
                                val c = minOf(n - o, cap - p)
                                System.arraycopy(tmp, o, ring, p, c)
                                head += c
                                o += c
                            }
                            cond.signalAll()
                        } finally { lock.unlock() }
                    }
                } catch (e: Exception) {
                    DebugLog.add("  LIVE2 error del flujo: ${e.javaClass.simpleName}: ${e.message}")
                }
                lock.lock()
                try { ended = true; cond.signalAll() } finally { lock.unlock() }
            }
            t.isDaemon = true
            t.name = "castv-live-hub"
            t.start()
        }

        /** Copia hasta [len] bytes desde la posición [pos]; espera si aún no llegan. -1 = fin; -2 = ya salió del buffer. */
        fun readAt(pos: Long, b: ByteArray, off: Int, len: Int): Int {
            lock.lock()
            try {
                while (true) {
                    if (closed) return -1
                    if (pos < head - cap) return -2
                    if (pos < head) {
                        val n = minOf(len.toLong(), head - pos).toInt()
                        var o = 0
                        while (o < n) {
                            val p = ((pos + o) % cap).toInt()
                            val c = minOf(n - o, cap - p)
                            System.arraycopy(ring, p, b, off + o, c)
                            o += c
                        }
                        if (pos + n > served) {
                            served = pos + n
                            cond.signalAll()
                        }
                        lastUse = System.currentTimeMillis()
                        return n
                    }
                    if (ended) return -1
                    cond.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    lastUse = System.currentTimeMillis()
                }
            } finally { lock.unlock() }
        }

        fun close() {
            if (closed) return
            closed = true
            lock.lock()
            try { cond.signalAll() } finally { lock.unlock() }
            src.close()
        }
    }

    /** Lo que lee una conexión de la TV: [remaining] bytes del buffer del canal a partir de [pos]. */
    private class HubStream(private val hub: LiveHub, private var pos: Long, private var remaining: Long) : InputStream() {
        private var done = false
        private var released = false

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (done || remaining <= 0) return -1
            val n = hub.readAt(pos, b, off, minOf(len.toLong(), remaining).toInt())
            if (n <= 0) { done = true; return -1 }
            pos += n
            remaining -= n
            return n
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else (one[0].toInt() and 0xff)
        }

        override fun close() {
            if (!released) { released = true; hub.release() }
        }
    }

    /** Relleno para lo que la TV pide lejos de lo ya bajado (sondeos al final del "archivo"):
     *  paquetes TS nulos (PID 0x1FFF, sin reloj), válidos y sin abrir el origen. */
    private class NullTsStream(private var pos: Long, private var remaining: Long) : InputStream() {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (remaining <= 0) return -1
            val n = minOf(len.toLong(), remaining).toInt()
            for (i in 0 until n) {
                val v = when (((pos + i) % 188).toInt()) { 0 -> 0x47; 1 -> 0x1F; 3 -> 0x10; else -> 0xFF }
                b[off + i] = v.toByte()
            }
            pos += n
            remaining -= n
            return n
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else (one[0].toInt() and 0xff)
        }
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
