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

    class Result(val ok: Boolean, val message: String, val savedAs: String = "", val cancelled: Boolean = false, val uri: Uri? = null)   // [MP3-ABRIR-1] uri del archivo guardado (Android 10+)

    val running = AtomicBoolean(false)

    // [TONO-1] a dónde va el MP3 (Android 10+; antes de eso siempre Descargas)
    const val DEST_DOWNLOADS = 0
    const val DEST_RINGTONE = 1
    const val DEST_NOTIFICATION = 2
    const val DEST_ALARM = 3

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
    class Sink(
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

        /** [MP3-ABRIR-1] El archivo ya guardado en Descargas (solo Android 10+, donde hay MediaStore); null si no. */
        fun savedUri(): Uri? = if (uri != null && Build.VERSION.SDK_INT >= 29) uri else null

        fun abort() {
            try { stream.close() } catch (e: Exception) { }
            try { if (uri != null) resolver?.delete(uri, null, null) } catch (e: Exception) { }
            try { file?.delete() } catch (e: Exception) { }
        }
    }

    fun openSink(context: Context, displayName: String, mime: String = "audio/mpeg", dest: Int = DEST_DOWNLOADS): Sink {   // [VIDEO-MP3-1b] también lo usa LinkDownload; [TONO-1] dest
        if (Build.VERSION.SDK_INT >= 29) {
            val cr = context.contentResolver
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            val audioDest = dest == DEST_RINGTONE || dest == DEST_NOTIFICATION || dest == DEST_ALARM
            if (audioDest) {   // [TONO-1] colección de audio, carpeta y marca del tipo (sin permisos nuevos: el archivo lo crea CASTV)
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, when (dest) {
                    DEST_RINGTONE -> Environment.DIRECTORY_RINGTONES
                    DEST_NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
                    else -> Environment.DIRECTORY_ALARMS
                })
                cv.put(MediaStore.Audio.Media.TITLE, displayName.substringBeforeLast('.'))
                cv.put(MediaStore.Audio.Media.IS_MUSIC, 0)
                cv.put(MediaStore.Audio.Media.IS_RINGTONE, if (dest == DEST_RINGTONE) 1 else 0)
                cv.put(MediaStore.Audio.Media.IS_NOTIFICATION, if (dest == DEST_NOTIFICATION) 1 else 0)
                cv.put(MediaStore.Audio.Media.IS_ALARM, if (dest == DEST_ALARM) 1 else 0)
            } else {
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val u = cr.insert(if (audioDest) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException(if (audioDest) "No se pudo crear el archivo de sonido" else "No se pudo crear el archivo en Descargas")
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
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
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
        isCancelled: () -> Boolean,
        startUs: Long = 0L,      // [CORTAR-1a] inicio del tramo en microsegundos (0 = desde el principio)
        endUs: Long = -1L,       // [CORTAR-1a] fin del tramo en microsegundos (<= 0 = hasta el final)
        suffix: String = "",     // [CORTAR-1a] se agrega al nombre y al título ID3, p. ej. « (corte)»
        dest: Int = DEST_DOWNLOADS   // [TONO-1] Descargas, Tonos de llamada, Notificaciones o Alarmas
    ): Result {
        if (!running.compareAndSet(false, true)) return Result(false, "Ya hay una conversión en curso")
        val title = cleanTitle(videoName) + suffix
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
            if (startUs > 0L) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)   // [CORTAR-1a]
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

            val s = openSink(context, "$title.mp3", dest = dest)   // [TONO-1]
            sink = s

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var idle = 0
            var lastPct = -1
            val rangeEnd = if (endUs > 0L) endUs else totalUs   // [CORTAR-1a] el avance se mide sobre el tramo
            val span = rangeEnd - startUs
            var reachedEnd = false

            while (!outDone) {
                if (isCancelled()) return Result(false, "Cancelado", cancelled = true)

                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = dec.getInputBuffer(ii)
                        val n = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (n < 0 || (endUs > 0L && ex.sampleTime > endUs)) {   // [CORTAR-1a] pasó el fin del tramo
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            val t = ex.sampleTime
                            dec.queueInputBuffer(ii, 0, n, if (t < 0) 0 else t, 0)
                            if (span > 0L && t >= 0) {
                                val pct = (((t - startUs) * 100L) / span).toInt().coerceIn(0, 100)
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
                        val pts = info.presentationTimeUs   // [CORTAR-1a]
                        var fi = 0L
                        while (ob.remaining() >= bps * ch) {
                            var l: Short = 0
                            var r: Short = 0
                            for (c in 0 until ch) {
                                val v = if (pcmFloat) toS16(ob.getFloat()) else ob.getShort()
                                if (c == 0) l = v else if (c == 1) r = v
                            }
                            if (ch == 1) r = l
                            if (startUs > 0L || endUs > 0L) {   // [CORTAR-1a] recorta cuadro por cuadro al tramo pedido
                                val ft = pts + (fi * 1000000L) / srcHz
                                fi++
                                if (ft < startUs) continue
                                if (endUs > 0L && ft >= endUs) { reachedEnd = true; break }
                            }
                            e.push(l, r)
                        }
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || reachedEnd) outDone = true   // [CORTAR-1a]
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
            return Result(true, "OK", savedAs, uri = s.savedUri())   // [MP3-ABRIR-1]
        } catch (e: Throwable) {
            return Result(false, "Error: ${e.javaClass.simpleName}" + (if (src.scheme == "http" || src.scheme == "https") "" else ": ${e.message}"))   // [VIDEO-MP3-1b] sin el mensaje si es un enlace: puede traerlo (R9)
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
