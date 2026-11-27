package com.castv.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer

/**
 * [UNIR-1] Junta varios videos (o varios audios AAC/.m4a) del teléfono en UN solo archivo en Descargas, SIN recodificar
 * (MediaExtractor + MediaMuxer): rápido y sin pérdida de calidad. Solo vale si todos tienen el mismo códec, tamaño,
 * giro y configuración (p. ej. grabados con la misma cámara y ajustes); si no, avisa cuál no cuadra y no deja nada
 * a medias. Usa el mismo candado (Mp3Converter.running) y destino (Mp3Converter.openSink) que VideoCut.
 * No guarda ni escribe en el /log los nombres de los archivos (R9).
 */
object VideoJoin {
    const val MAX_FILES = 20

    class Result(val ok: Boolean, val message: String, val savedAs: String = "", val cancelled: Boolean = false, val uri: Uri? = null)

    private class Src(val vTrack: Int, val aTrack: Int, val vFmt: MediaFormat?, val aFmt: MediaFormat?)

    private fun bytes(f: MediaFormat, key: String): ByteArray? {
        val b = try { f.getByteBuffer(key) } catch (e: Exception) { null } ?: return null
        val d = b.duplicate()
        d.rewind()
        val a = ByteArray(d.remaining())
        d.get(a)
        return a
    }

    private fun intOf(f: MediaFormat, key: String, def: Int = 0): Int =
        try { if (f.containsKey(key)) f.getInteger(key) else def } catch (e: Exception) { def }

    private fun sameBytes(a: MediaFormat, b: MediaFormat, key: String): Boolean {
        val x = bytes(a, key)
        val y = bytes(b, key)
        return if (x == null || y == null) x == null && y == null else x.contentEquals(y)
    }

    /** null si [b] se puede pegar después de [a]; si no, el motivo. */
    private fun mismatch(a: Src, b: Src, video: Boolean): String? {
        if (video) {
            val av = a.vFmt ?: return "sin video"
            val bv = b.vFmt ?: return "no tiene video"
            if (av.getString(MediaFormat.KEY_MIME) != bv.getString(MediaFormat.KEY_MIME)) return "otro códec de video"
            if (intOf(av, MediaFormat.KEY_WIDTH) != intOf(bv, MediaFormat.KEY_WIDTH) ||
                intOf(av, MediaFormat.KEY_HEIGHT) != intOf(bv, MediaFormat.KEY_HEIGHT)) return "otro tamaño de imagen"
            if (intOf(av, MediaFormat.KEY_ROTATION) != intOf(bv, MediaFormat.KEY_ROTATION)) return "otro giro (vertical/horizontal)"
            if (!sameBytes(av, bv, "csd-0") || !sameBytes(av, bv, "csd-1")) return "otra configuración de video"
            if ((a.aFmt == null) != (b.aFmt == null)) return "audio distinto (uno no tiene)"
        }
        val aa = a.aFmt
        val ba = b.aFmt
        if (aa != null && ba != null) {
            if (aa.getString(MediaFormat.KEY_MIME) != ba.getString(MediaFormat.KEY_MIME)) return "otro códec de audio"
            if (intOf(aa, MediaFormat.KEY_SAMPLE_RATE) != intOf(ba, MediaFormat.KEY_SAMPLE_RATE) ||
                intOf(aa, MediaFormat.KEY_CHANNEL_COUNT) != intOf(ba, MediaFormat.KEY_CHANNEL_COUNT)) return "otro formato de audio"
            if (!sameBytes(aa, ba, "csd-0")) return "otra configuración de audio"
        }
        return null
    }

    private fun readSrc(context: Context, uri: Uri, video: Boolean): Src? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var v = -1
            var a = -1
            var vf: MediaFormat? = null
            var af: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mt = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("video/") && v < 0 && video) { v = i; vf = f }
                else if (mt.startsWith("audio/") && a < 0) { a = i; af = f }
            }
            if (video && v < 0) return null
            if (!video && a < 0) return null
            return Src(v, a, vf, af)
        } catch (e: Throwable) {
            return null
        } finally {
            try { ex.release() } catch (e: Exception) { }
        }
    }

    /**
     * Junta [uris] (en ese orden) en un solo archivo: .mp4 si [video], .m4a si no. [names] son los nombres originales
     * (el primero da el nombre del resultado). [onProgress] recibe 0..100 sobre el total.
     */
    fun join(
        context: Context,
        uris: List<Uri>,
        names: List<String>,
        video: Boolean,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (uris.size < 2) return Result(false, "Elige al menos 2 archivos")
        if (uris.size > MAX_FILES) return Result(false, "Máximo $MAX_FILES archivos a la vez")
        if (!Mp3Converter.running.compareAndSet(false, true)) return Result(false, "Ya hay una conversión en curso")
        var sink: Mp3Converter.Sink? = null
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null
        var started = false
        var success = false
        try {
            // 1) leer los formatos y comprobar que todos cuadran con el primero
            val srcs = ArrayList<Src>()
            for (k in uris.indices) {
                val s = readSrc(context, uris[k], video)
                    ?: return Result(false, if (video) "El archivo ${k + 1} no se pudo leer o no tiene video" else "El archivo ${k + 1} no se pudo leer o no tiene audio")
                srcs.add(s)
            }
            if (!video) {
                for (k in srcs.indices) {
                    if (srcs[k].aFmt?.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") {
                        return Result(false, "Solo se pueden unir audios AAC (.m4a); el archivo ${k + 1} no lo es (el MP3 no cabe en ese contenedor)")
                    }
                }
            }
            for (k in 1 until srcs.size) {
                val why = mismatch(srcs[0], srcs[k], video)
                if (why != null) return Result(false, "El archivo ${k + 1} no se puede unir con el 1: $why. Para unir sin recodificar deben ser iguales.")
            }
            val first = srcs[0]

            // 2) destino y muxer
            val base = Mp3Converter.cleanTitle(names.firstOrNull() ?: "unido")
            val s = Mp3Converter.openSink(context, if (video) "$base (unido).mp4" else "$base (unido).m4a", if (video) "video/mp4" else "audio/mp4")
            sink = s
            try { s.stream.close() } catch (e: Exception) { }   // el muxer escribe por su cuenta (necesita un archivo con búsqueda)
            val m: MediaMuxer = if (s.uri != null && s.resolver != null && Build.VERSION.SDK_INT >= 29) {
                val p = s.resolver.openFileDescriptor(s.uri, "rw") ?: return Result(false, "No se pudo abrir el archivo en Descargas")
                pfd = p
                MediaMuxer(p.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val f = s.file ?: return Result(false, "No se pudo abrir el archivo en Descargas")
                MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            muxer = m
            var vOut = -1
            var aOut = -1
            try {
                if (video) vOut = m.addTrack(first.vFmt!!)
                if (first.aFmt != null) aOut = m.addTrack(first.aFmt)
            } catch (e: Exception) {
                return Result(false, "Este formato no cabe en un archivo nuevo sin recodificar")
            }
            if (video) {
                val rot = intOf(first.vFmt!!, MediaFormat.KEY_ROTATION)
                if (rot == 90 || rot == 180 || rot == 270) { try { m.setOrientationHint(rot) } catch (e: Exception) { } }
            }
            var bufSize = 2 * 1024 * 1024
            for (sr in srcs) for (f in listOf(sr.vFmt, sr.aFmt)) {
                if (f != null && f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val z = f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (z > bufSize) bufSize = z
                }
            }
            if (bufSize > 16 * 1024 * 1024) bufSize = 16 * 1024 * 1024
            val buf = ByteBuffer.allocate(bufSize)
            val info = MediaCodec.BufferInfo()
            val aSampleDurUs: Long = first.aFmt?.let { f ->
                val r = intOf(f, MediaFormat.KEY_SAMPLE_RATE, 44100)
                if (r > 0) 1_024_000_000L / r else 23_220L
            } ?: 23_220L
            val vFrameDurUs = 33_333L

            m.start()
            started = true
            var offset = 0L   // dónde empieza cada archivo en la línea de tiempo del resultado
            var wrote = 0
            for (k in srcs.indices) {
                if (isCancelled()) return Result(false, "Cancelado", cancelled = true)
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(context, uris[k], null)
                    val sr = srcs[k]
                    if (sr.vTrack >= 0) ex.selectTrack(sr.vTrack)
                    if (sr.aTrack >= 0 && aOut >= 0) ex.selectTrack(sr.aTrack)
                    val fmtDur = (sr.vFmt ?: sr.aFmt)?.let { f -> try { if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else -1L } catch (e: Exception) { -1L } } ?: -1L
                    var maxEnd = 0L
                    var lastPct = -1
                    while (true) {
                        if (isCancelled()) return Result(false, "Cancelado", cancelled = true)
                        val ti = ex.sampleTrackIndex
                        if (ti < 0) break
                        val t = ex.sampleTime
                        if (t < 0L) break
                        val isV = ti == sr.vTrack
                        val out = if (isV) vOut else aOut
                        val sync = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        buf.clear()
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) break
                        if (out >= 0) {
                            info.set(0, n, offset + t, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            m.writeSampleData(out, buf, info)
                            wrote++
                            val end = t + (if (isV) vFrameDurUs else aSampleDurUs)
                            if (end > maxEnd) maxEnd = end
                            if (fmtDur > 0L) {
                                val inner = ((t * 100L) / fmtDur).toInt().coerceIn(0, 100)
                                val pct = (k * 100 + inner) / srcs.size
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                        if (!ex.advance()) break
                    }
                    if (fmtDur > maxEnd) maxEnd = fmtDur
                    offset += maxEnd
                } finally {
                    try { ex.release() } catch (e: Exception) { }
                }
                onProgress(((k + 1) * 100) / srcs.size)
            }
            if (wrote == 0) return Result(false, "No se obtuvo nada que unir")
            m.stop()
            started = false
            try { pfd?.close() } catch (e: Exception) { }
            pfd = null
            val savedAs = s.commit()
            success = true
            return Result(true, "OK", savedAs, false, s.savedUri())
        } catch (e: Throwable) {
            return Result(false, "Error: " + e.javaClass.simpleName)
        } finally {
            try { if (started) muxer?.stop() } catch (e: Exception) { }
            try { muxer?.release() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
            Mp3Converter.running.set(false)
        }
    }
}
