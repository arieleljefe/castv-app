package com.castv.app

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** [CAPITULOS-1] Un capítulo de un video: dónde empieza (milisegundos) y su título. */
data class Chapter(val startMs: Long, val title: String)

/**
 * [CAPITULOS-1] Lee los capítulos de un video del teléfono. Solo LEE el archivo (nunca lo cambia).
 * - MKV / WebM: elemento "Chapters" de Matroska (se busca por el índice SeekHead y, si no está, recorriendo el archivo).
 * - MP4 / M4V / MOV: átomo "chpl" (el que escriben ffmpeg y mp4box).
 * Si el archivo no tiene capítulos, no se entiende o algo falla, devuelve una lista vacía.
 */
object Chapters {

    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEKHEAD = 0x114D9B74L
    private const val ID_SEEK = 0x4DBBL
    private const val ID_SEEKID = 0x53ABL
    private const val ID_SEEKPOS = 0x53ACL
    private const val ID_CHAPTERS = 0x1043A770L
    private const val ID_EDITION = 0x45B9L
    private const val ID_ATOM = 0xB6L
    private const val ID_TIME_START = 0x91L
    private const val ID_HIDDEN = 0x98L
    private const val ID_ENABLED = 0x4598L
    private const val ID_DISPLAY = 0x80L
    private const val ID_CHAP_STRING = 0x85L
    private const val MAX_BLOCK = 1 shl 20   // no se leen bloques de más de 1 MB

    fun read(ctx: Context, uriString: String): List<Chapter> {
        return try {
            val pfd = ctx.contentResolver.openFileDescriptor(Uri.parse(uriString), "r") ?: return emptyList()
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { readChannel(it.channel) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readChannel(ch: FileChannel): List<Chapter> {
        val size = ch.size()
        val head = readAt(ch, 0L, 12) ?: return emptyList()
        if (u(head, 0, 4) == ID_EBML) return readMkv(ch, size)
        if (String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp") return readMp4(ch, size)
        return emptyList()
    }

    // ------------------------------------------------------------ utilidades

    private fun readAt(ch: FileChannel, pos: Long, n: Int): ByteArray? {
        if (pos < 0 || n < 0) return null
        val bb = ByteBuffer.allocate(n)
        var p = pos
        while (bb.hasRemaining()) {
            val r = ch.read(bb, p)
            if (r < 0) return null
            p += r
        }
        return bb.array()
    }

    /** Entero sin signo de n bytes (big-endian). */
    private fun u(b: ByteArray, o: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    // ------------------------------------------------------------ MKV / WebM (EBML)

    private class El(val id: Long, val start: Int, val end: Int)
    private class Head(val id: Long, val size: Long, val hl: Int)   // hl = largo de la cabecera

    /** ID de un elemento EBML (conserva el bit marcador). Devuelve (id, largo). */
    private fun vId(b: ByteArray, o: Int): Pair<Long, Int>? {
        if (o < 0 || o >= b.size) return null
        val first = b[o].toInt() and 0xFF
        if (first == 0) return null
        val ln = Integer.numberOfLeadingZeros(first) - 24 + 1
        if (ln > 4 || o + ln > b.size) return null
        return Pair(u(b, o, ln), ln)
    }

    /** Tamaño de un elemento EBML. Devuelve (tamaño, largo); tamaño -1 = desconocido. */
    private fun vSize(b: ByteArray, o: Int): Pair<Long, Int>? {
        if (o < 0 || o >= b.size) return null
        val first = b[o].toInt() and 0xFF
        if (first == 0) return null
        val ln = Integer.numberOfLeadingZeros(first) - 24 + 1
        if (ln > 8 || o + ln > b.size) return null
        val mask = (0x80 shr (ln - 1)) - 1
        var v = (first and mask).toLong()
        var allOnes = (first and mask) == mask
        for (i in 1 until ln) {
            val x = b[o + i].toInt() and 0xFF
            v = (v shl 8) or x.toLong()
            if (x != 0xFF) allOnes = false
        }
        return Pair(if (allOnes) -1L else v, ln)
    }

    /** Hijos directos de un bloque ya cargado en memoria. */
    private fun kids(b: ByteArray, start: Int, end: Int): List<El> {
        val out = ArrayList<El>()
        var o = start
        while (o < end) {
            val i = vId(b, o) ?: break
            val s = vSize(b, o + i.second) ?: break
            val ds = o + i.second + s.second
            if (ds > end) break
            val de = if (s.first < 0) end else minOf(ds.toLong() + s.first, end.toLong()).toInt()
            out.add(El(i.first, ds, de))
            o = de
        }
        return out
    }

    private fun num(b: ByteArray, k: El): Long {
        val n = k.end - k.start
        return if (n in 1..8) u(b, k.start, n) else 0L
    }

    /** Cabecera de un elemento que está en el archivo (no en memoria). */
    private fun header(ch: FileChannel, size: Long, pos: Long): Head? {
        val n = minOf(12L, size - pos).toInt()
        if (n < 2) return null
        val b = readAt(ch, pos, n) ?: return null
        val i = vId(b, 0) ?: return null
        val s = vSize(b, i.second) ?: return null
        return Head(i.first, s.first, i.second + s.second)
    }

    private fun parseChapters(b: ByteArray): List<Chapter> {
        val out = ArrayList<Chapter>()
        fun atom(s: Int, e: Int) {
            var t = -1L
            var title = ""
            var hidden = false
            var enabled = true
            for (k in kids(b, s, e)) {
                when (k.id) {
                    ID_TIME_START -> t = num(b, k) / 1_000_000L   // nanosegundos -> ms
                    ID_HIDDEN -> hidden = num(b, k) == 1L
                    ID_ENABLED -> enabled = num(b, k) != 0L
                    ID_DISPLAY -> if (title.isEmpty()) {
                        for (d in kids(b, k.start, k.end)) {
                            if (d.id == ID_CHAP_STRING) {
                                title = String(b, d.start, d.end - d.start, Charsets.UTF_8)
                                break
                            }
                        }
                    }
                    ID_ATOM -> atom(k.start, k.end)   // subcapítulos: se aplanan
                }
            }
            if (t >= 0 && !hidden && enabled) out.add(Chapter(t, title.trim()))
        }
        for (k in kids(b, 0, b.size)) {
            if (k.id != ID_EDITION) continue
            for (a in kids(b, k.start, k.end)) {
                if (a.id == ID_ATOM) atom(a.start, a.end)
            }
            if (out.isNotEmpty()) break   // se usa la primera edición con capítulos
        }
        out.sortBy { it.startMs }
        return out
    }

    private fun readMkv(ch: FileChannel, size: Long): List<Chapter> {
        val h0 = header(ch, size, 0L) ?: return emptyList()
        if (h0.id != ID_EBML || h0.size < 0) return emptyList()
        val segHeadPos = h0.hl + h0.size
        val hs = header(ch, size, segHeadPos) ?: return emptyList()
        if (hs.id != ID_SEGMENT) return emptyList()
        val seg = segHeadPos + hs.hl
        val segEnd = if (hs.size < 0) size else minOf(seg + hs.size, size)

        var chapPos = -1L
        var o = seg
        var n = 0
        while (o < segEnd && n < 100000) {
            n++
            val h = header(ch, size, o) ?: break
            val ds = o + h.hl
            if (h.id == ID_CHAPTERS) { chapPos = o; break }
            if (h.id == ID_SEEKHEAD && h.size in 1..65535) {
                val b = readAt(ch, ds, h.size.toInt())
                if (b != null) {
                    for (e in kids(b, 0, b.size)) {
                        if (e.id != ID_SEEK) continue
                        var sid = -1L
                        var spos = -1L
                        for (k in kids(b, e.start, e.end)) {
                            if (k.id == ID_SEEKID) sid = num(b, k)
                            else if (k.id == ID_SEEKPOS) spos = num(b, k)
                        }
                        if (sid == ID_CHAPTERS && spos >= 0) chapPos = seg + spos
                    }
                }
                if (chapPos >= 0) break
            }
            if (h.size < 0) break
            o = ds + h.size
        }
        if (chapPos < 0) return emptyList()
        val hc = header(ch, size, chapPos) ?: return emptyList()
        if (hc.id != ID_CHAPTERS || hc.size <= 0 || hc.size > MAX_BLOCK) return emptyList()
        val b = readAt(ch, chapPos + hc.hl, hc.size.toInt()) ?: return emptyList()
        return parseChapters(b)
    }

    // ------------------------------------------------------------ MP4 / M4V / MOV (átomo chpl)

    /** Busca un átomo hijo entre [start, end). Devuelve [inicio de datos, fin] o null. */
    private fun findAtom(ch: FileChannel, size: Long, start: Long, end: Long, type: String): LongArray? {
        var o = start
        var n = 0
        while (o + 8 <= end && n < 100000) {
            n++
            val b = readAt(ch, o, minOf(16L, size - o).toInt()) ?: return null
            if (b.size < 8) return null
            var sz = u(b, 0, 4)
            var hl = 8
            if (sz == 1L) {
                if (b.size < 16) return null
                sz = u(b, 8, 8)
                hl = 16
            } else if (sz == 0L) {
                sz = end - o
            }
            if (sz < hl) return null
            if (String(b, 4, 4, Charsets.ISO_8859_1) == type) return longArrayOf(o + hl, minOf(o + sz, end))
            o += sz
        }
        return null
    }

    private fun readMp4(ch: FileChannel, size: Long): List<Chapter> {
        val moov = findAtom(ch, size, 0L, size, "moov") ?: return emptyList()
        val udta = findAtom(ch, size, moov[0], moov[1], "udta") ?: return emptyList()
        val chpl = findAtom(ch, size, udta[0], udta[1], "chpl") ?: return emptyList()
        val n = (chpl[1] - chpl[0]).toInt()
        if (n < 9 || n > MAX_BLOCK) return emptyList()
        val b = readAt(ch, chpl[0], n) ?: return emptyList()
        var o = 4                                   // versión (1) + banderas (3)
        if ((b[0].toInt() and 0xFF) != 0) o += 4    // versión 1: 4 bytes reservados
        val count = b[o].toInt() and 0xFF
        o += 1
        val out = ArrayList<Chapter>()
        for (i in 0 until count) {
            if (o + 9 > b.size) break
            val t = u(b, o, 8) / 10000L             // unidades de 100 ns -> ms
            o += 8
            val len = b[o].toInt() and 0xFF
            o += 1
            if (o + len > b.size) break
            out.add(Chapter(t, String(b, o, len, Charsets.UTF_8).trim()))
            o += len
        }
        out.sortBy { it.startMs }
        return out
    }
}
