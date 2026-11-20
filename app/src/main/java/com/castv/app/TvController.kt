package com.castv.app

import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.regex.Pattern

object TvController {

    var tvIp: String? = null
    var tvPort: Int = 38400
    var controlPath: String = "/upnp/control/mingusavtr"
    var renderPath: String? = null

    var tvName: String = ""

    // [COMPAT-TV-1] diagnóstico de la TV (solo escribe en el /log; no cambia nada de lo que se envía)
    var connPath: String? = null             // controlURL de ConnectionManager (para GetProtocolInfo)
    private var diagIp: String? = null       // TV de la que ya se anotó el diagnóstico
    @Volatile private var watchGen = 0       // cancela la vigilancia anterior al mandar algo nuevo

    data class Tv(val name: String, val ip: String, val port: Int, val path: String)

    private const val DEFAULT_CONTROL = "/upnp/control/mingusavtr"

    private val locPattern: Pattern =
        Pattern.compile("LOCATION:\\s*http://([^:/]+):(\\d+)(/\\S*)", Pattern.CASE_INSENSITIVE)

    private val ssdpMsg = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"

    private fun sendSsdp(socket: DatagramSocket) {
        val packet = DatagramPacket(
            ssdpMsg.toByteArray(), ssdpMsg.length,
            InetSocketAddress("239.255.255.250", 1900)
        )
        socket.send(packet)
    }

    /** Usa un TV ya encontrado (lee su descripción UPnP para sacar las rutas de control). */
    fun select(tv: Tv) {
        tvIp = tv.ip
        tvPort = tv.port
        tvName = tv.name
        controlPath = DEFAULT_CONTROL
        renderPath = null
        connPath = null
        fetchControlPath("http://${tv.ip}:${tv.port}${tv.path}")
    }

    /** Busca el TV en la red local por SSDP (igual que hicimos con el script ssdp.py),
     *  para no tener que hardcodear la IP del televisor.
     *  Si hay varios aparatos, prefiere el de [preferIp] (el último que elegiste). */
    fun discoverTv(timeoutMs: Int = 4000, preferIp: String? = null): Boolean {
        val socket = DatagramSocket()
        socket.soTimeout = 1000
        var fallback: Tv? = null
        try {
            sendSsdp(socket)
            val buf = ByteArray(2048)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val resp = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(resp)
                } catch (e: java.net.SocketTimeoutException) {
                    if (fallback != null) break
                    continue
                }
                val text = String(resp.data, 0, resp.length)
                val m = locPattern.matcher(text)
                if (!m.find()) continue
                val ip = m.group(1) ?: continue
                val port = m.group(2)?.toIntOrNull() ?: 38400
                val path = m.group(3) ?: continue
                val tv = Tv(ip, ip, port, path)
                if (preferIp == null || preferIp == ip) {
                    select(tv)
                    return true
                }
                if (fallback == null) fallback = tv
            }
            val fb = fallback
            if (fb != null) {
                select(fb)
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        } finally {
            if (!socket.isClosed) socket.close()
        }
    }

    /** ¿Responde el TV en su puerto? (más fiable que un SOAP, que puede dar error si no hay nada cargado). */
    fun isReachable(timeoutMs: Int = 1500): Boolean {
        val ip = tvIp ?: return false
        return try {
            java.net.Socket().use { sk ->
                sk.connect(InetSocketAddress(ip, tvPort), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Lista todos los aparatos de la red que aceptan reproducir (TVs, bocinas, etc.). */
    fun scanAll(timeoutMs: Int = 3500): List<Tv> {
        val found = LinkedHashMap<String, Tv>()
        val socket = DatagramSocket()
        socket.soTimeout = 700
        try {
            sendSsdp(socket)
            val buf = ByteArray(2048)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val resp = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(resp)
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                }
                val text = String(resp.data, 0, resp.length)
                val m = locPattern.matcher(text)
                if (!m.find()) continue
                val ip = m.group(1) ?: continue
                if (found.containsKey(ip)) continue
                val port = m.group(2)?.toIntOrNull() ?: 38400
                val path = m.group(3) ?: continue
                val xml = fetchDescription("http://$ip:$port$path")
                val name = if (xml != null) friendlyOf(xml) else null
                found[ip] = Tv(name ?: ip, ip, port, path)
            }
        } catch (e: Exception) {
        } finally {
            if (!socket.isClosed) socket.close()
        }
        return ArrayList(found.values)
    }

    private fun fetchDescription(descUrl: String): String? {
        return try {
            val conn = URL(descUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val xml = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            xml
        } catch (e: Exception) {
            null
        }
    }

    private fun friendlyOf(xml: String): String? =
        Regex("<friendlyName>\\s*([^<]*?)\\s*</friendlyName>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** Lee la descripción UPnP del TV y saca el controlURL de AVTransport.
     *  Si falla, se queda con la ruta por defecto. */
    private fun fetchControlPath(descUrl: String) {
        try {
            val xml = fetchDescription(descUrl) ?: return
            friendlyOf(xml)?.let { tvName = it }
            val m = Pattern.compile(
                "<service>(?:(?!</service>).)*?AVTransport(?:(?!</service>).)*?<controlURL>\\s*([^<\\s]+)\\s*</controlURL>",
                Pattern.DOTALL
            ).matcher(xml)
            if (m.find()) {
                var path = m.group(1) ?: return
                if (path.startsWith("http://")) path = path.substringAfter("://").substringAfter("/").let { "/$it" }
                if (!path.startsWith("/")) path = "/$path"
                controlPath = path
            }
            val r = Pattern.compile(
                "<service>(?:(?!</service>).)*?RenderingControl(?:(?!</service>).)*?<controlURL>\\s*([^<\\s]+)\\s*</controlURL>",
                Pattern.DOTALL
            ).matcher(xml)
            if (r.find()) {
                var rp = r.group(1) ?: return
                if (rp.startsWith("http://")) rp = rp.substringAfter("://").substringAfter("/").let { "/$it" }
                if (!rp.startsWith("/")) rp = "/$rp"
                renderPath = rp
            }
            // [COMPAT-TV-1] ruta de ConnectionManager y diagnóstico de esta TV
            val cmm = Pattern.compile(
                "<service>(?:(?!</service>).)*?ConnectionManager(?:(?!</service>).)*?<controlURL>\\s*([^<\\s]+)\\s*</controlURL>",
                Pattern.DOTALL
            ).matcher(xml)
            if (cmm.find()) {
                var cp = cmm.group(1) ?: ""
                if (cp.startsWith("http://")) cp = cp.substringAfter("://").substringAfter("/").let { "/$it" }
                if (cp.isNotEmpty() && !cp.startsWith("/")) cp = "/$cp"
                if (cp.isNotEmpty()) connPath = cp
            }
            diagnoseTv(xml)
        } catch (e: Exception) { }
    }

    /** [COMPAT-TV-1] Anota en el /log qué TV es, si está en la misma red que el teléfono y qué formatos dice aceptar.
     *  Una sola vez por TV y por sesión. Solo lee; no cambia nada de lo que se manda. */
    private fun diagnoseTv(xml: String) {
        val ip = tvIp ?: return
        if (diagIp == ip) return
        diagIp = ip
        fun f(t: String): String =
            Regex("<$t>\\s*([^<]*?)\\s*</$t>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1) ?: "?"
        DebugLog.add("TV INFO: nombre='${f("friendlyName")}' fabricante='${f("manufacturer")}' modelo='${f("modelName")}' numero='${f("modelNumber")}' ip=$ip:$tvPort")
        val phoneIp = try { CastvNet.localIpAddress(ip) } catch (e: Exception) { null }
        val same24 = phoneIp != null && phoneIp.substringBeforeLast('.') == ip.substringBeforeLast('.')
        DebugLog.add("TV RED: telefono=${phoneIp ?: "?"} TV=$ip misma red /24=${if (phoneIp == null) "?" else if (same24) "si" else "NO"}")
        val cp = connPath
        if (cp == null) {
            DebugLog.add("TV FORMATOS: la TV no publica ConnectionManager")
            return
        }
        Thread {
            try {
                val body = cmCall("GetProtocolInfo", "", cp)
                if (body == null) {
                    DebugLog.add("TV FORMATOS: GetProtocolInfo sin respuesta")
                    return@Thread
                }
                val entries = (tag(body, "Sink") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (entries.isEmpty()) {
                    DebugLog.add("TV FORMATOS: la TV no informó formatos (Sink vacío)")
                    return@Thread
                }
                val mimes = LinkedHashSet<String>()
                val profiles = LinkedHashSet<String>()
                for (e in entries) {
                    val parts = e.split(':')
                    if (parts.size >= 3) mimes.add(parts[2].lowercase())
                    Regex("DLNA\\.ORG_PN=([^;:,]+)").find(e)?.let { profiles.add(it.groupValues[1]) }
                }
                DebugLog.add("TV FORMATOS: ${entries.size} entradas; tipos: ${mimes.joinToString(", ").take(600)}")
                if (profiles.isNotEmpty()) DebugLog.add("TV PERFILES DLNA: ${profiles.joinToString(", ").take(600)}")
                val ts = mimes.any { it.contains("mp2t") || it.contains("mpeg-tts") }
                val hls = mimes.any { it.contains("mpegurl") }
                val mp4 = mimes.any { it.contains("mp4") }
                DebugLog.add("TV ACEPTA: ts=${if (ts) "si" else "NO"} hls=${if (hls) "si" else "NO"} mp4=${if (mp4) "si" else "NO"}")
            } catch (e: Exception) {
                DebugLog.add("TV FORMATOS EXCEPCION ${e.javaClass.simpleName}: ${e.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    /** [COMPAT-TV-1] Llamada SOAP a ConnectionManager (solo para el diagnóstico). */
    private fun cmCall(action: String, inner: String, path: String): String? {
        val ip = tvIp ?: return null
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">$inner</u:$action></s:Body>
</s:Envelope>"""
        return try {
            val conn = URL("http://$ip:$tvPort$path").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:ConnectionManager:1#$action\"")
            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                DebugLog.add("TV FORMATOS: $action -> HTTP ${conn.responseCode}")
                conn.disconnect()
                null
            } else {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                body
            }
        } catch (e: Exception) {
            DebugLog.add("TV FORMATOS: $action EXCEPCION ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** [COMPAT-TV-1] Tras mandar un canal o enlace, mira 12 s qué dice la TV (estado y error) y lo anota solo cuando cambia. */
    private fun watchAfterPlay() {
        val gen = ++watchGen
        Thread {
            var last = ""
            for (t in 1..12) {
                try { Thread.sleep(1000) } catch (e: InterruptedException) { return@Thread }
                if (gen != watchGen) return@Thread
                val body = soapCall("GetTransportInfo", "<InstanceID>0</InstanceID>")
                val st = if (body == null) "sin respuesta" else
                    (tag(body, "CurrentTransportState") ?: "?") + " / " + (tag(body, "CurrentTransportStatus") ?: "?")
                if (st != last) {
                    last = st
                    DebugLog.add("TV ESTADO t+${t}s: $st")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun xmlEscape(t: String): String =
        t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    private fun sendSoap(action: String, innerXml: String): Boolean {
        val ip = tvIp ?: return false
        val url = URL("http://$ip:$tvPort$controlPath")
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
$innerXml
</u:$action>
</s:Body>
</s:Envelope>"""
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            val os: OutputStream = conn.outputStream
            os.write(envelope.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()
            val code = conn.responseCode
            if (code == 200) {
                DebugLog.add("SOAP $action -> 200 OK")
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
                DebugLog.add("SOAP $action -> HTTP $code ${err?.replace(Regex("\\s+"), " ")?.take(400) ?: ""}")
            }
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            DebugLog.add("SOAP $action EXCEPCION ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Hace una llamada SOAP y devuelve el cuerpo de la respuesta (o null si falla). */
    private fun soapCall(action: String, innerXml: String): String? {
        val ip = tvIp ?: return null
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$innerXml</u:$action></s:Body>
</s:Envelope>"""
        return try {
            val conn = URL("http://$ip:$tvPort$controlPath").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                null
            } else {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                body
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tag(body: String, name: String): String? =
        Regex("<$name>([^<]*)</$name>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)

    /** PLAYING, PAUSED, STOPPED (incluye NO_MEDIA_PRESENT) o UNKNOWN. */
    fun getTransportState(): String {
        val body = soapCall("GetTransportInfo", "<InstanceID>0</InstanceID>") ?: return "UNKNOWN"
        return when (tag(body, "CurrentTransportState")?.uppercase()) {
            "PLAYING" -> "PLAYING"
            "PAUSED_PLAYBACK" -> "PAUSED"
            "STOPPED", "NO_MEDIA_PRESENT" -> "STOPPED"
            else -> "UNKNOWN"
        }
    }

    private fun parseTime(t: String?): Int {
        if (t == null) return -1
        val parts = t.trim().substringBefore('.').split(":")
        if (parts.size != 3) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        val sec = parts[2].toIntOrNull() ?: return -1
        return h * 3600 + m * 60 + sec
    }

    private var lastPosRaw: String? = null

    /** Devuelve (posición, duración) en segundos, o null si el TV no responde. */
    fun getPositionInfo(): Pair<Int, Int>? {
        val body = soapCall("GetPositionInfo", "<InstanceID>0</InstanceID>") ?: return null
        val pos = parseTime(tag(body, "RelTime"))
        val dur = parseTime(tag(body, "TrackDuration"))
        // PROG-1c (diagnóstico): dato crudo de duración, solo cuando cambia
        val rawDur = tag(body, "TrackDuration")
        val rawMeta = tag(body, "TrackMetaData")?.take(200)
        val rawKey = "$rawDur|$rawMeta"
        if (rawKey != lastPosRaw) {
            lastPosRaw = rawKey
            val extra = if (rawDur == null) " cuerpo='${body.replace(Regex("\\s+"), " ").take(300)}'" else ""
            DebugLog.add("POS crudo: dur='$rawDur' meta='$rawMeta'$extra")
        }
        return Pair(maxOf(pos, 0), maxOf(dur, 0))
    }

    /** Salta a un segundo concreto de la pista (si el TV lo soporta). */
    fun seek(seconds: Int): Boolean {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val target = String.format("%d:%02d:%02d", h, m, s)
        return sendSoap("Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>$target</Target>")
    }

    fun setUriAndPlay(mediaUrl: String, title: String, mime: String, isMusic: Boolean): Boolean {
        DebugLog.add("ENVIANDO al TV $tvIp:$tvPort -> $mediaUrl ($mime)")
        val upnpClass = if (isMusic) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        val protocolInfo = "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;" +
                "DLNA.ORG_FLAGS=01700000000000000000000000000000"
        // DIDL bien formado y escapado UNA sola vez al meterlo en el SOAP
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"1\" parentID=\"0\" restricted=\"1\">" +
                "<dc:title>${xmlEscape(title)}</dc:title>" +
                "<upnp:class>$upnpClass</upnp:class>" +
                "<res protocolInfo=\"${xmlEscape(protocolInfo)}\">${xmlEscape(mediaUrl)}</res>" +
                "</item></DIDL-Lite>"
        val diag = mediaUrl.contains("/live/") || mediaUrl.contains("/relay/")   // [COMPAT-TV-1] canales y enlaces
        if (diag) DebugLog.add("TV protocolInfo enviado: $protocolInfo")
        val inner = "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
                "<CurrentURIMetaData>${xmlEscape(didl)}</CurrentURIMetaData>"

        // Stop previo: evita que el TV se quede con la URI/caché anterior
        sendSoap("Stop", "<InstanceID>0</InstanceID>")
        Thread.sleep(400)
        val ok1 = sendSoap("SetAVTransportURI", inner)
        if (!ok1) {
            DebugLog.add("TV rechazó SetAVTransportURI (mime=$mime)")   // [COMPAT-TV-1]
            return false
        }
        Thread.sleep(800)
        val okPlay = sendSoap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
        if (diag) {   // [COMPAT-TV-1]
            if (!okPlay) DebugLog.add("TV rechazó Play (mime=$mime)") else watchAfterPlay()
        }
        return okPlay
    }

    fun pause() = sendSoap("Pause", "<InstanceID>0</InstanceID>")
    fun play() = sendSoap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    fun stop() = sendSoap("Stop", "<InstanceID>0</InstanceID>")

    // ------------------------------------------------ Volumen (RenderingControl)

    private fun rcCall(action: String, inner: String): String? {
        val ip = tvIp ?: return null
        val path = renderPath ?: return null
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">$inner</u:$action></s:Body>
</s:Envelope>"""
        return try {
            val conn = URL("http://$ip:$tvPort$path").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:RenderingControl:1#$action\"")
            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                null
            } else {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                body
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getVolume(): Int? {
        val body = rcCall("GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>") ?: return null
        return tag(body, "CurrentVolume")?.trim()?.toIntOrNull()
    }

    fun setVolume(v: Int): Boolean =
        rcCall("SetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>${v.coerceIn(0, 100)}</DesiredVolume>") != null

    fun setMute(mute: Boolean): Boolean =
        rcCall("SetMute", "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredMute>${if (mute) 1 else 0}</DesiredMute>") != null
}
