package com.castv.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer

/**
 * [CORTAR-1b] Corta un tramo de un video del teléfono SIN recodificar (MediaExtractor + MediaMuxer → .mp4 en Descargas):
 * rápido y sin pérdida de calidad. El inicio salta al fotograma clave anterior (puede adelantarse unos segundos) y el
 * final no es exacto al fotograma. Si el formato no cabe en un .mp4, avisa y no deja ningún archivo a medias.
 * Usa el mismo candado que las conversiones (Mp3Converter.running) y el mismo destino (Mp3Converter.openSink).
 */
object VideoCut {

    fun cut(
        context: Context,
        src: Uri,
        videoName: String,
        startUs: Long,
        endUs: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): Mp3Converter.Result {
        if (!Mp3Converter.running.compareAndSet(false, true)) return Mp3Converter.Result(false, "Ya hay una conversión en curso")
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null
        var sink: Mp3Converter.Sink? = null
        var started = false
        var success = false
        try {
            ex.setDataSource(context, src, null)
            // Pistas que caben en un .mp4: video y audio (se dejan los subtítulos y datos)
            val map = HashMap<Int, Int>()          // pista del origen → pista del muxer
            var videoTrack = -1
            val fmts = ArrayList<MediaFormat>()
            val srcTracks = ArrayList<Int>()
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mt = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("video/") && videoTrack < 0) { videoTrack = i; srcTracks.add(i); fmts.add(f) }
                else if (mt.startsWith("audio/")) { srcTracks.add(i); fmts.add(f) }
            }
            if (videoTrack < 0) return Mp3Converter.Result(false, "Este archivo no tiene video: usa «Cortar audio»")

            val title = Mp3Converter.cleanTitle(videoName) + " (corte)"
            val s = Mp3Converter.openSink(context, "$title.mp4", "video/mp4")
            sink = s
            try { s.stream.close() } catch (e: Exception) { }   // el muxer escribe por su cuenta (necesita un archivo con búsqueda)
            val m: MediaMuxer = if (s.uri != null && s.resolver != null && Build.VERSION.SDK_INT >= 29) {
                val p = s.resolver.openFileDescriptor(s.uri, "rw") ?: return Mp3Converter.Result(false, "No se pudo abrir el archivo en Descargas")
                pfd = p
                MediaMuxer(p.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val f = s.file ?: return Mp3Converter.Result(false, "No se pudo abrir el archivo en Descargas")
                MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            muxer = m

            for (k in srcTracks.indices) {   // un audio que no cabe en .mp4 se deja fuera; si es el video, se avisa
                try {
                    map[srcTracks[k]] = m.addTrack(fmts[k])
                } catch (e: Exception) {
                    if (srcTracks[k] == videoTrack) return Mp3Converter.Result(false, "Este formato de video no cabe en un .mp4 sin recodificar")
                }
            }
            // Giro del video (fotos o videos grabados en vertical)
            try {
                val mr = MediaMetadataRetriever()
                try {
                    mr.setDataSource(context, src)
                    val rot = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 0 || rot == 90 || rot == 180 || rot == 270) m.setOrientationHint(rot)
                } finally { try { mr.release() } catch (e: Exception) { } }
            } catch (e: Exception) { }

            // 1) con solo el video, buscar el fotograma clave anterior al inicio: ahí empieza el corte (vt)
            ex.selectTrack(videoTrack)
            if (startUs > 0L) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var vt = ex.sampleTime
            if (vt < 0L) vt = 0L
            // 2) sumar el audio y volver a ese punto: el audio de antes de vt se descarta
            for (i in srcTracks) if (i != videoTrack && map.containsKey(i)) ex.selectTrack(i)
            ex.seekTo(vt, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var bufSize = 2 * 1024 * 1024
            for (f in fmts) {
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val z = f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (z > bufSize) bufSize = z
                }
            }
            if (bufSize > 16 * 1024 * 1024) bufSize = 16 * 1024 * 1024
            val buf = ByteBuffer.allocate(bufSize)
            val info = MediaCodec.BufferInfo()
            val span = endUs - vt
            var lastPct = -1
            var wrote = 0

            m.start()
            started = true
            while (true) {
                if (isCancelled()) return Mp3Converter.Result(false, "Cancelado", cancelled = true)
                val ti = ex.sampleTrackIndex
                if (ti < 0) break
                val t = ex.sampleTime
                if (t < 0L) break
                if (t > endUs) break
                val out = map[ti]
                val rel = t - vt
                if (out != null && rel >= 0L) {
                    buf.clear()
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    info.set(0, n, rel, if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    m.writeSampleData(out, buf, info)
                    wrote++
                    if (span > 0L) {
                        val pct = ((rel * 100L) / span).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                }
                if (!ex.advance()) break
            }
            if (wrote == 0) return Mp3Converter.Result(false, "No se obtuvo video en ese tramo")
            m.stop()
            started = false
            onProgress(100)
            try { pfd?.close() } catch (e: Exception) { }
            pfd = null
            val savedAs = s.commit()
            success = true
            return Mp3Converter.Result(true, "OK", savedAs, uri = s.savedUri())
        } catch (e: Throwable) {
            return Mp3Converter.Result(false, "Error: ${e.javaClass.simpleName}")
        } finally {
            try { if (started) muxer?.stop() } catch (e: Exception) { }
            try { muxer?.release() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
            Mp3Converter.running.set(false)
        }
    }
}
