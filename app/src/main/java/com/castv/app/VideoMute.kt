package com.castv.app

import android.app.Activity
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [QUITAR-AUDIO-1] Guarda una copia del video del teléfono SIN sonido (`nombre (sin audio).mp4` en Descargas), sin
 * recodificar (MediaExtractor + MediaMuxer): rápido y sin pérdida de calidad; el original no se toca. Conserva el giro.
 * Mismo candado (Mp3Converter.running), destino (Mp3Converter.openSink) y avisos (LinkJobNotifier) que VideoCut.
 * No guarda ni escribe en el /log el nombre del archivo (R9).
 */
object VideoMute {
    private val cancelFlag = AtomicBoolean(false)

    class Result(val ok: Boolean, val message: String, val savedAs: String = "", val cancelled: Boolean = false, val uri: Uri? = null)

    private fun rotationOf(context: Context, src: Uri): Int = try {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(context, src)
            val rot = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 180 || rot == 270) rot else 0
        } finally { try { mr.release() } catch (e: Exception) { } }
    } catch (e: Exception) { 0 }

    /** Copia solo la pista de video. [onPct] recibe 0..100. Si falla o se cancela, no queda archivo a medias. */
    fun mute(context: Context, src: Uri, videoName: String, onPct: (Int) -> Unit, isCancelled: () -> Boolean): Result {
        if (!Mp3Converter.running.compareAndSet(false, true)) return Result(false, "Ya hay una conversión en curso")
        val ex = MediaExtractor()
        var sink: Mp3Converter.Sink? = null
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null
        var started = false
        var success = false
        try {
            ex.setDataSource(context, src, null)
            var vt = -1
            var vf: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mt = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("video/")) { vt = i; vf = f; break }
            }
            if (vt < 0 || vf == null) return Result(false, "Este archivo no tiene video")
            val durUs = try { if (vf.containsKey(MediaFormat.KEY_DURATION)) vf.getLong(MediaFormat.KEY_DURATION) else -1L } catch (e: Exception) { -1L }

            val s = Mp3Converter.openSink(context, Mp3Converter.cleanTitle(videoName) + " (sin audio).mp4", "video/mp4")
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
            val out = try { m.addTrack(vf) } catch (e: Exception) { return Result(false, "Este formato de video no cabe en un .mp4 sin recodificar") }
            val rot = rotationOf(context, src)
            if (rot != 0) { try { m.setOrientationHint(rot) } catch (e: Exception) { } }

            var bufSize = 2 * 1024 * 1024
            if (vf.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                val z = vf.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                if (z > bufSize) bufSize = z
            }
            if (bufSize > 16 * 1024 * 1024) bufSize = 16 * 1024 * 1024
            val buf = ByteBuffer.allocate(bufSize)
            val info = MediaCodec.BufferInfo()
            ex.selectTrack(vt)
            m.start()
            started = true
            var wrote = 0
            var lastPct = -1
            while (true) {
                if (isCancelled()) return Result(false, "Cancelado", cancelled = true)
                if (ex.sampleTrackIndex < 0) break
                val t = ex.sampleTime
                if (t < 0L) break
                val sync = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                info.set(0, n, t, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                m.writeSampleData(out, buf, info)
                wrote++
                if (durUs > 0L) {
                    val pct = ((t * 100L) / durUs).toInt().coerceIn(0, 100)
                    if (pct != lastPct) { lastPct = pct; onPct(pct) }
                }
                if (!ex.advance()) break
            }
            if (wrote == 0) return Result(false, "No se obtuvo video")
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
            try { ex.release() } catch (e: Exception) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
            Mp3Converter.running.set(false)
        }
    }

    /** Pide confirmación y guarda el video [uri] sin audio; avance y Cancelar por notificación (o recuadro si están apagadas). */
    fun show(act: Activity, uri: Uri, name: String) {
        if (act.isFinishing || act.isDestroyed) return
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(act)
            .setTitle("🔇 Quitar el audio")
            .setMessage("Se guarda una copia de este video sin sonido en Descargas. Sin recodificar: no pierde calidad y el original no se toca.")
            .setPositiveButton("Quitar audio") { _, _ -> start(act, uri, name) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun start(act: Activity, uri: Uri, name: String) {
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val app = act.applicationContext
        val title = "🔇 Quitar el audio"
        cancelFlag.set(false)
        LinkJobNotifier.cancel = false
        val dlg: AlertDialog? = if (LinkJobNotifier.enabled(app)) null else AlertDialog.Builder(act)
            .setTitle(title)
            .setMessage("Preparando… No cierres esta pantalla.")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        if (dlg != null) {
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                cancelFlag.set(true)
                dlg.setMessage("Cancelando…")
            }
        } else {
            LinkJobNotifier.progress(app, title, "Preparando…", -1)
            Toast.makeText(app, "En segundo plano: mira la notificación para ver el avance o cancelar", Toast.LENGTH_SHORT).show()
        }
        thread {
            val res: Result = try {
                val stop: () -> Boolean = { cancelFlag.get() || LinkJobNotifier.cancel }
                mute(app, uri, name, { pct ->
                    val text = "Guardando… $pct %"
                    if (dlg == null) LinkJobNotifier.progress(app, title, text, pct)
                    else act.runOnUiThread { if (!act.isFinishing && !act.isDestroyed) dlg.setMessage(text) }
                }, stop)
            } catch (e: Throwable) {
                Result(false, "Error: " + e.javaClass.simpleName)
            }
            Handler(Looper.getMainLooper()).post {
                if (dlg != null) { try { dlg.dismiss() } catch (e: Exception) { } }
                val front = !act.isFinishing && !act.isDestroyed &&
                    ((act as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                        ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true)
                if (res.cancelled) {
                    if (dlg == null) LinkJobNotifier.clear(app)
                    Toast.makeText(app, "Cancelado", Toast.LENGTH_LONG).show()
                } else if (res.ok) {
                    if (dlg != null || front) {
                        if (dlg == null) LinkJobNotifier.clear(app)
                        SavedFile.offer(act, "🔇", res.savedAs, res.uri)
                    } else {
                        LinkJobNotifier.done(app, "🔇 Guardado en Descargas", res.savedAs, res.uri)
                    }
                } else {
                    if (dlg == null) LinkJobNotifier.done(app, "🔇 No se pudo completar", res.message, null)
                    Toast.makeText(app, "No se pudo quitar el audio: " + res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
