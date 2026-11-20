package com.castv.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * [IA-1] Saca el audio de un video del teléfono y lo deja listo para el reconocimiento de voz:
 * PCM de 16 bits, mono, 16 000 Hz, escrito por bloques en un archivo (así la memoria no crece
 * con películas largas). Usa solo APIs de Android (MediaExtractor + MediaCodec).
 */
object AudioExtractor {

    const val TARGET_HZ = 16000

    class Result(
        val ok: Boolean,
        val message: String,
        val mime: String = "",
        val srcHz: Int = 0,
        val srcChannels: Int = 0,
        val samples: Long = 0,
        val durationSec: Double = 0.0,
        val tookMs: Long = 0,
        val cancelled: Boolean = false
    )

    /** Pasa de srcHz a 16 kHz promediando (filtro simple contra el aliasing) y escribe PCM16 little-endian. */
    private class Resampler(val srcHz: Int, private val out: OutputStream) {
        private val step = srcHz.toDouble() / AudioExtractor.TARGET_HZ
        private var phase = 0.0
        private var acc = 0f
        private var cnt = 0
        private val buf = ByteArray(65536)
        private var n = 0
        var samples = 0L

        fun push(s: Float) {
            acc += s
            cnt++
            phase += 1.0
            if (phase >= step) {
                val v = acc / cnt
                while (phase >= step) {
                    emit(v)
                    phase -= step
                }
                acc = 0f
                cnt = 0
            }
        }

        private fun emit(v: Float) {
            var x = (v * 32767f).toInt()
            if (x > 32767) x = 32767 else if (x < -32768) x = -32768
            buf[n++] = (x and 0xFF).toByte()
            buf[n++] = ((x shr 8) and 0xFF).toByte()
            samples++
            if (n >= buf.size) {
                out.write(buf, 0, n)
                n = 0
            }
        }

        fun flush() {
            if (n > 0) {
                out.write(buf, 0, n)
                n = 0
            }
        }
    }

    /**
     * Extrae el audio de [uri] a [outFile] (se sobrescribe). [onProgress] recibe 0..100 (solo si el
     * video informa su duración). [isCancelled] se consulta en cada vuelta. Si algo falla o se
     * cancela, el archivo de salida se borra.
     */
    fun extract(
        context: Context,
        uri: Uri,
        outFile: File,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        val t0 = System.currentTimeMillis()
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null
        var success = false
        try {
            ex.setDataSource(context, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return Result(false, "El video no tiene pista de audio")
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            ex.selectTrack(track)
            val totalUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            var srcHz = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            if (ch < 1) ch = 1
            var pcmFloat = false

            val dec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                return Result(false, "Este teléfono no puede decodificar el audio ($mime)", mime)
            }
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            fos = FileOutputStream(outFile)
            var rs: Resampler? = null
            val firstSrcHz = srcHz
            val firstCh = ch
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var idle = 0
            var lastPct = -1

            while (!outDone) {
                if (isCancelled()) return Result(false, "Cancelado", mime, firstSrcHz, firstCh, cancelled = true)

                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = dec.getInputBuffer(ii)
                        val n = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (n < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            val t = ex.sampleTime
                            dec.queueInputBuffer(ii, 0, n, if (t < 0) 0 else t, 0)
                            if (totalUs > 0 && t >= 0) {
                                val pct = ((t * 100L) / totalUs).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                            ex.advance()
                        }
                    }
                }

                val oi = dec.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    idle = 0
                    val ob = dec.getOutputBuffer(oi)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        ob.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        var cur = rs
                        if (cur == null || cur.srcHz != srcHz) {
                            cur?.flush()
                            cur = Resampler(srcHz, fos)
                            rs = cur
                        }
                        val bytesPerSample = if (pcmFloat) 4 else 2
                        while (ob.remaining() >= bytesPerSample * ch) {
                            var sum = 0f
                            for (c in 0 until ch) {
                                sum += if (pcmFloat) ob.getFloat() else ob.getShort() / 32768f
                            }
                            cur.push(sum / ch)
                        }
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = dec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcHz = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (ch < 1) ch = 1
                    pcmFloat = nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER && inDone) {
                    idle++
                    if (idle > 500) return Result(false, "El decodificador de audio no responde", mime, firstSrcHz, firstCh)
                }
            }
            rs?.flush()
            fos.flush()
            val samples = rs?.samples ?: 0L
            success = samples > 0
            val took = System.currentTimeMillis() - t0
            if (!success) return Result(false, "No se obtuvo audio del video", mime, firstSrcHz, firstCh, tookMs = took)
            return Result(
                true, "OK", mime, firstSrcHz, firstCh, samples,
                samples.toDouble() / TARGET_HZ, took
            )
        } catch (e: Exception) {
            return Result(false, "Error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { fos?.close() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
            if (!success) {
                try { outFile.delete() } catch (e: Exception) { }
            }
        }
    }
}
