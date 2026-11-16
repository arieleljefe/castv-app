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

    /** Busca el TV en la red local por SSDP (igual que hicimos con el script ssdp.py),
     *  para no tener que hardcodear la IP del televisor. */
    fun discoverTv(timeoutMs: Int = 4000): Boolean {
        val socket = DatagramSocket()
        socket.soTimeout = timeoutMs
        val msg = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
        val packet = DatagramPacket(
            msg.toByteArray(), msg.length,
            InetSocketAddress("239.255.255.250", 1900)
        )
        return try {
            socket.send(packet)
            val buf = ByteArray(2048)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                val text = String(resp.data, 0, resp.length)
                val locMatcher = Pattern.compile("LOCATION:\\s*http://([^:/]+):(\\d+)(/\\S*)", Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                if (locMatcher.find()) {
                    tvIp = locMatcher.group(1)
                    tvPort = locMatcher.group(2)?.toIntOrNull() ?: 38400
                    fetchControlPath("http://${tvIp}:${tvPort}${locMatcher.group(3)}")
                    socket.close()
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        } finally {
            if (!socket.isClosed) socket.close()
        }
    }

    /** Lee la descripción UPnP del TV y saca el controlURL de AVTransport.
     *  Si falla, se queda con la ruta por defecto. */
    private fun fetchControlPath(descUrl: String) {
        try {
            val conn = URL(descUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val xml = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
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
        } catch (e: Exception) { }
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
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    fun getTransportState(): String {
        val ip = tvIp ?: return "UNKNOWN"
        return try {
            val url = URL("http://$ip:$tvPort$controlPath")
            val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:GetTransportInfo></s:Body>
</s:Envelope>"""
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"")
            conn.outputStream.use { it.write(envelope.toByteArray()) }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            when {
                body.contains("PLAYING") -> "PLAYING"
                body.contains("STOPPED") -> "STOPPED"
                body.contains("PAUSED_PLAYBACK") -> "PAUSED"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    fun setUriAndPlay(mediaUrl: String, title: String, mime: String, isMusic: Boolean): Boolean {
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
        val inner = "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
                "<CurrentURIMetaData>${xmlEscape(didl)}</CurrentURIMetaData>"

        // Stop previo: evita que el TV se quede con la URI/caché anterior
        sendSoap("Stop", "<InstanceID>0</InstanceID>")
        Thread.sleep(400)
        val ok1 = sendSoap("SetAVTransportURI", inner)
        if (!ok1) return false
        Thread.sleep(800)
        return sendSoap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    fun pause() = sendSoap("Pause", "<InstanceID>0</InstanceID>")
    fun play() = sendSoap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    fun stop() = sendSoap("Stop", "<InstanceID>0</InstanceID>")
}
