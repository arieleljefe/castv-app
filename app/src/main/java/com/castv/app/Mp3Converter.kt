package com.castv.app

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [VIDEO-MP3-1] Convierte el audio de un video del teléfono a MP3 (192 kbps estéreo, 128 kbps si es mono)
 * y lo guarda en Descargas. Decodifica con MediaExtractor + MediaCodec (Android) y codifica con LAME.
 * Solo usa la MediaStore de Descargas (sin permisos nuevos); en Android 9 o anterior queda en la carpeta
 * Descargas propia de la app.
 */
object Mp3Converter {

    class Result(val ok: Boolean, val message: String, val savedAs: String = "", val cancelled: Boolean = false)

    val running = AtomicBoolean(false)

    private const val CHUNK = 4096   // cuadros por vuelta a LAME

    private fun toS16(f: Float): Short {
        var x = (f * 32767f).toInt()
        if (x > 32767) x = 32767 else if (x < -32768) x = -32768
        return x.toShort()
    }

    /** Une decodificador y LAME: recibe cuadros (izq, der) y escribe el MP3 por bloques. */
    private class Enc(val h: Long, val out: OutputStream) {
        private val pcm = ShortArray(CHUNK * 2)
        private val mp3 = ByteArray((CHUNK * 1.25).toInt() + 7200)
        private var n = 0

        fun push(l: Short, r: Short) {
            pcm[2 * n] = l
            pcm[2 * n + 1] = r
            n++
            if (n == CHUNK) drain()
        }

        private fun drain() {
            if (n == 0) return
            val k = LameEncoder.nEncode(h, pcm, n, mp3)
            n = 0
            if (k < 0) throw IllegalStateException("LAME error $k")
            if (k > 0) out.write(mp3, 0, k)
        }

        fun finish() {
            drain()
            val k = LameEncoder.nFlush(h, mp3)
            if (k > 0) out.write(mp3, 0, k)
        }
    }

    /** Destino del MP3: MediaStore › Descargas (Android 10+) o carpeta Descargas de la app. */
    private class Sink(
        val stream: OutputStream,
        val resolver: ContentResolver?,
        val uri: Uri?,
        val file: File?
    ) {
        fun commit(): String {
            try { stream.close() } catch (e: Exception) { }
            if (uri != null && resolver != null && Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues()
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, cv, null, null)
                try {
                    resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) return c.getString(0) ?: "MP3"
                    }
                } catch (e: Exception) { }
                return "MP3"
            }
            return file?.name ?: "MP3"
        }

        fun abort() {
            try { stream.close() } catch (e: Exception) { }
            try { if (uri != null) resolver?.delete(uri, null, null) } catch (e: Exception) { }
            try { file?.delete() } catch (e: Exception) { }
        }
    }

    private fun openSink(context: Context, displayName: String): Sink {
        if (Build.VERSION.SDK_INT >= 29) {
            val cr = context.contentResolver
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")
            val s = cr.openOutputStream(u)
            if (s == null) {
                try { cr.delete(u, null, null) } catch (e: Exception) { }
                throw IllegalStateException("No se pudo abrir el archivo en Descargas")
            }
            return Sink(s, cr, u, null)
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        dir.mkdirs()
        var f = File(dir, displayName)
        var i = 1
        val base = displayName.removeSuffix(".mp3")
        while (f.exists()) { f = File(dir, "$base ($i).mp3"); i++ }
        return Sink(FileOutputStream(f), null, null, f)
    }

    /** Nombre del video sin extensión ni caracteres que no valen en un nombre de archivo. */
    fun cleanTitle(name: String): String {
        var t = name.trim().replace(Regex("\\.[A-Za-z0-9]{2,4}$"), "")
        t = t.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim()
        if (t.length > 100) t = t.substring(0, 100).trim()
        return if (t.isEmpty()) "audio" else t
    }

    /**
     * Convierte el audio de [src] (un video del teléfono) a MP3. [onProgress] recibe 0..100 (si el video informa su
     * duración). [isCancelled] se consulta en cada vuelta. Si falla o se cancela, no queda ningún archivo a medias.
     */
    fun convert(
        context: Context,
        src: Uri,
        videoName: String,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (!running.compareAndSet(false, true)) return Result(false, "Ya hay una conversión en curso")
        val title = cleanTitle(videoName)
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var sink: Sink? = null
        var enc: Enc? = null
        var success = false
        var savedAs = ""
        try {
            ex.setDataSource(context, src, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) { track = i; fmt = f; break }
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
                return Result(false, "Este teléfono no puede decodificar el audio ($mime)")
            }
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            val s = openSink(context, "$title.mp3")
            sink = s

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var idle = 0
            var lastPct = -1

            while (!outDone) {
                if (isCancelled()) return Result(false, "Cancelado", cancelled = true)

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
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
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
                        ob.order(ByteOrder.LITTLE_ENDIAN)
                        var e = enc
                        if (e == null) {
                            val mono = ch == 1
                            val h = LameEncoder.nInit(srcHz, mono, if (mono) 128 else 192, title)
                            if (h == 0L) return Result(false, "No se pudo iniciar el codificador MP3")
                            e = Enc(h, s.stream)
                            enc = e
                        }
                        val bps = if (pcmFloat) 4 else 2
                        while (ob.remaining() >= bps * ch) {
                            var l: Short = 0
                            var r: Short = 0
                            for (c in 0 until ch) {
                                val v = if (pcmFloat) toS16(ob.getFloat()) else ob.getShort()
                                if (c == 0) l = v else if (c == 1) r = v
                            }
                            if (ch == 1) r = l
                            e.push(l, r)
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
                        nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER && inDone) {
                    idle++
                    if (idle > 500) return Result(false, "El decodificador de audio no responde")
                }
            }
            val e = enc ?: return Result(false, "No se obtuvo audio del video")
            e.finish()
            onProgress(100)
            savedAs = s.commit()
            success = true
            return Result(true, "OK", savedAs)
        } catch (e: Throwable) {
            return Result(false, "Error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
            try { enc?.let { LameEncoder.nClose(it.h) } } catch (e: Throwable) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
            running.set(false)
        }
    }
}
