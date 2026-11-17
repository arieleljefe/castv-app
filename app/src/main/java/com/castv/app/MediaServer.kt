package com.castv.app

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.io.InputStream

/** Servidor HTTP embebido. URLs de la forma /video/123.mp4 o /audio/45.mp3.
 *  Soporta Content-Length, HEAD y Range (206), que los TV (VIDAA/DLNA) exigen. */
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

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/log") {
            if (session.parameters.containsKey("clear")) DebugLog.clear()
            return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", DebugLog.dump())
        }
        DebugLog.add("TV pide ${session.method} ${session.uri} range=${session.headers["range"] ?: "-"} " +
                "de ${session.headers["remote-addr"] ?: "?"} ua=${session.headers["user-agent"] ?: "-"}")
        try {
            val parts = session.uri.trim('/').split("/")
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
