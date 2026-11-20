package com.castv.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * [IA-3] Genera subtítulos con IA para un video del teléfono: audio (AudioExtractor) -> Whisper
 * (idioma y modelo según Ajustes, IA-4) en bloques de 5 min -> archivo .srt. Solo usa AudioExtractor, WhisperModel y WhisperLib tal cual.
 * Usa su propio temporal (ia3_audio.pcm) y una marca para no lanzar dos a la vez, porque
 * WhisperLib.setCancel es global.
 */
object SubtitleGen {

    private const val CHUNK_SAMPLES = 300 * AudioExtractor.TARGET_HZ   // 5 min
    private const val MIN_SAMPLES = 17600
    private const val CHANNEL_ID = "castv_ia3"
    private const val NOTIF_ID = 7303

    private val lock = Any()
    private var busy = false
    @Volatile private var cancelFlag = false
    @Volatile private var inTranscribe = false
    private var lastNotifAt = 0L

    class Out(
        val ok: Boolean,
        val message: String,
        val cancelled: Boolean = false,
        val segments: Int = 0,
        val audioSec: Double = 0.0,
        val tookMs: Long = 0
    )

    private class Seg(val startMs: Long, val endMs: Long, val text: String)

    val running: Boolean
        get() = synchronized(lock) { busy }

    /** Reserva el turno. false = ya hay una generación en curso. Llamar a [finish] al terminar. */
    fun tryStart(): Boolean = synchronized(lock) {
        if (busy) false else { busy = true; cancelFlag = false; true }
    }

    fun finish() {
        inTranscribe = false
        synchronized(lock) { busy = false }
    }

    fun requestCancel() {
        cancelFlag = true
        if (inTranscribe) {
            try { WhisperLib.setCancel(true) } catch (e: Throwable) { }
        }
    }

    // ------------------------------------------------------------ Proceso

    /** [onProgress] recibe (0..100, texto). Corre en el hilo que la llama (no en el principal). */
    fun run(context: Context, uri: Uri, outSrt: File, onProgress: (Int, String) -> Unit): Out {
        val t0 = System.currentTimeMillis()
        val appCtx = context.applicationContext
        val pcm = File(appCtx.cacheDir, "ia3_audio.pcm")
        val part = File(outSrt.parentFile, outSrt.name + ".part")
        val spec = WhisperModel.selected(appCtx)   // [IA-4]
        val lang = WhisperModel.lang(appCtx)       // [IA-4]
        var ctx = 0L
        try {
            onProgress(0, "Extrayendo el audio…")
            val ex = AudioExtractor.extract(
                appCtx, uri, pcm,
                { p -> onProgress(p.coerceIn(0, 100) * 30 / 100, "Extrayendo el audio…") },
                { cancelFlag })
            if (ex.cancelled || cancelFlag) return Out(false, "Cancelado", cancelled = true)
            if (!ex.ok) return Out(false, ex.message)
            val totalSamples = pcm.length() / 2
            if (totalSamples < 8000L) return Out(false, "El video casi no tiene audio")

            onProgress(30, "Cargando el modelo de voz…")
            ctx = WhisperLib.initContext(WhisperModel.file(appCtx, spec).absolutePath)
            if (ctx == 0L) return Out(false, "No se pudo cargar el modelo de voz")
            WhisperLib.setCancel(false)

            val nChunks = ((totalSamples + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES).toInt()
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val segs = ArrayList<Seg>()
            FileInputStream(pcm).use { ins ->
                val bytes = ByteArray(CHUNK_SAMPLES * 2)
                for (c in 0 until nChunks) {
                    if (cancelFlag) return Out(false, "Cancelado", cancelled = true)
                    val got = readFully(ins, bytes)
                    val n = got / 2
                    if (n <= 0) break
                    val audio = FloatArray(if (n < MIN_SAMPLES) MIN_SAMPLES else n)
                    for (i in 0 until n) {
                        val lo = bytes[2 * i].toInt() and 0xFF
                        val hi = bytes[2 * i + 1].toInt()          // con signo
                        audio[i] = ((hi shl 8) or lo) / 32768f
                    }
                    onProgress(30 + c * 70 / nChunks, "Transcribiendo… parte ${c + 1} de $nChunks")
                    inTranscribe = true
                    if (cancelFlag) WhisperLib.setCancel(true)
                    val rc = WhisperLib.transcribe(ctx, threads, lang, audio)
                    inTranscribe = false
                    if (cancelFlag) return Out(false, "Cancelado", cancelled = true)
                    if (rc != 0) return Out(false, "Whisper falló (código $rc)")
                    val off = c.toLong() * CHUNK_SAMPLES / 160L   // centésimas
                    val cnt = WhisperLib.segmentCount(ctx)
                    for (i in 0 until cnt) {
                        val txt = WhisperLib.segmentText(ctx, i).trim()
                            .replace(Regex("\\s*\\n+\\s*"), " ")
                        if (txt.isEmpty()) continue
                        val a = WhisperLib.segmentT0(ctx, i) + off
                        var b = WhisperLib.segmentT1(ctx, i) + off
                        if (b <= a) b = a + 100
                        segs.add(Seg(a * 10L, b * 10L, txt))
                    }
                }
            }
            if (segs.isEmpty()) return Out(false, "No se detectó voz en el video")

            onProgress(100, "Guardando subtítulos…")
            outSrt.parentFile?.mkdirs()
            part.bufferedWriter(Charsets.UTF_8).use { w ->
                for (k in segs.indices) {
                    val s = segs[k]
                    w.write("${k + 1}\n${fmt(s.startMs)} --> ${fmt(s.endMs)}\n${s.text}\n\n")
                }
            }
            if (outSrt.exists()) outSrt.delete()
            if (!part.renameTo(outSrt)) return Out(false, "No se pudo guardar el .srt")
            val took = System.currentTimeMillis() - t0
            try {
                DebugLog.add("IA-4: idioma $lang, modelo ${spec.shortName}, ${segs.size} frases, audio " +
                    String.format(Locale.US, "%.1f", ex.durationSec) + " s en " +
                    String.format(Locale.US, "%.1f", took / 1000.0) + " s")
            } catch (e: Throwable) { }
            return Out(true, "${segs.size} frases", segments = segs.size,
                audioSec = ex.durationSec, tookMs = took)
        } catch (e: Throwable) {
            return Out(false, "Error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            inTranscribe = false
            if (ctx != 0L) { try { WhisperLib.freeContext(ctx) } catch (e: Throwable) { } }
            try { WhisperLib.setCancel(false) } catch (e: Throwable) { }
            try { pcm.delete() } catch (e: Exception) { }
            try { part.delete() } catch (e: Exception) { }
        }
    }

    private fun readFully(ins: FileInputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = ins.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3_600_000L
        val m = (ms / 60_000L) % 60L
        val s = (ms / 1000L) % 60L
        val r = ms % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, r)
    }

    // ------------------------------------------------------------ Notificación

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun post(ctx: Context, b: NotificationCompat.Builder) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, b.build())
        } catch (e: Throwable) { }
    }

    private fun builder(ctx: Context): NotificationCompat.Builder? {
        if (!canNotify(ctx)) return null
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Subtítulos con IA", NotificationManager.IMPORTANCE_LOW))
            }
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("✨ Subtítulos con IA")
        } catch (e: Throwable) {
            return null
        }
    }

    /** Notificación con avance (como máximo una cada medio segundo). */
    fun notifyProgress(ctx: Context, pct: Int, text: String) {
        val now = SystemClock.elapsedRealtime()
        if (pct < 100 && now - lastNotifAt < 500L) return
        lastNotifAt = now
        val b = builder(ctx.applicationContext) ?: return
        b.setContentText(text).setProgress(100, pct.coerceIn(0, 100), false)
            .setOngoing(true).setOnlyAlertOnce(true)
        post(ctx.applicationContext, b)
    }

    /** Reemplaza la notificación de avance por un aviso final (o la quita si [text] es null). */
    fun notifyDone(ctx: Context, text: String?) {
        val app = ctx.applicationContext
        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (e: Throwable) { }
        if (text == null) return
        val b = builder(app) ?: return
        b.setContentText(text).setOngoing(false).setAutoCancel(true)
        post(app, b)
    }
}
