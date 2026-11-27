package com.castv.app

import android.app.Activity
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

/**
 * [REDUCIR-1] Baja el peso de un video del teléfono RECODIFICÁNDOLO (H.264 + AAC) con la librería oficial Media3 Transformer
 * hasta cerca de un peso objetivo en MB (p. ej. 28 MB para subirlo o pasarlo al chat). Calcula el bitrate de video con
 * la duración y el objetivo, y baja la resolución si el bitrate quedaría muy corto para la imagen. PIERDE algo de calidad.
 * Trabaja en un archivo temporal de la caché y al terminar lo copia a Descargas (MediaStore); si falla o se cancela, no
 * queda nada a medias. Mismo candado (Mp3Converter.running) y avisos (LinkJobNotifier) que el resto. Transformer debe
 * usarse desde el hilo principal (tiene Looper), por eso el avance se vigila con un Handler y solo la copia final va en un hilo.
 * No guarda ni escribe en el /log el nombre del archivo (R9).
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoReduce {
    private const val AUDIO_BPS = 128_000L      // reserva para el audio AAC
    private const val MARGIN = 0.93             // el video se calcula para el 93 % del objetivo (contenedor y variaciones del codificador)
    private const val MIN_VIDEO_BPS = 150_000L  // por debajo de esto la imagen no sirve
    private const val BPP_MIN = 0.07            // bits por pixel y fotograma mínimos antes de bajar la resolución (30 fps supuestos)

    @Volatile private var cancelFlag = false

    /** Datos del video para calcular el plan. */
    class Meta(val durMs: Long, val w: Int, val h: Int, val sizeBytes: Long)

    /** Lo que se va a hacer: bitrate de video y alto de salida (0 = el mismo). */
    class Plan(val videoBps: Long, val outHeight: Int, val tooLow: Boolean)

    private fun fmtMb(bytes: Long): String = String.format(java.util.Locale.forLanguageTag("es"), "%.1f MB", bytes / 1_000_000.0)

    private fun sizeOf(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    } catch (e: Exception) { -1L }

    /** Duración, tamaño en pantalla (ya con el giro aplicado) y peso. null si no se puede leer. */
    fun meta(ctx: Context, uri: Uri): Meta? {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(ctx, uri)
            val dur = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            var w = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            var h = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val size = sizeOf(ctx, uri)
            if (dur <= 0L || w <= 0 || h <= 0 || size <= 0L) return null
            return Meta(dur, w, h, size)
        } catch (e: Exception) {
            return null
        } finally {
            try { mr.release() } catch (e: Exception) { }
        }
    }

    /** Bitrate de video y resolución de salida para llegar a [targetBytes]. */
    fun plan(m: Meta, targetBytes: Long): Plan {
        val sec = m.durMs / 1000.0
        val totalBps = (targetBytes * 8.0 * MARGIN / sec).toLong()
        val vBps = totalBps - AUDIO_BPS
        if (vBps < MIN_VIDEO_BPS) return Plan(MIN_VIDEO_BPS, 0, true)
        val shortSide = minOf(m.w, m.h)
        var outShort = shortSide
        for (s in intArrayOf(shortSide, 720, 540, 480, 360, 240)) {
            if (s > shortSide) continue
            val px = s.toDouble() * (maxOf(m.w, m.h).toDouble() / shortSide * s)   // lado corto × lado largo, en la proporción original
            outShort = s
            if (vBps / (px * 30.0) >= BPP_MIN) break
        }
        if (outShort == shortSide) return Plan(vBps, 0, false)
        // alto de salida: si es vertical, el alto crece con la proporción; si es horizontal, el alto es el lado corto
        var oh = if (m.h > m.w) (outShort.toDouble() * m.h / m.w).toInt() else outShort
        if (oh % 2 != 0) oh -= 1
        return Plan(vBps, oh, false)
    }

    // ------------------------------------------------------------ Pantalla

    /** Recuadro para elegir el peso objetivo (MB); al aceptar, recodifica el video [uri]. */
    fun show(act: Activity, uri: Uri, name: String) {
        if (act.isFinishing || act.isDestroyed) return
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val m = meta(act.applicationContext, uri)
        if (m == null) {
            Toast.makeText(act.applicationContext, "No pude leer este video", Toast.LENGTH_SHORT).show()
            return
        }
        val dens = act.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * dens).toInt()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))

        val info = TextView(act)
        info.text = "Peso del video: " + fmtMb(m.sizeBytes) + " · " + m.w + "×" + m.h + "\nSe vuelve a comprimir (H.264): el peso baja pero se pierde algo de calidad, y tarda " +
            "(puede ser varias veces la duración del video). El original no se toca."
        info.textSize = 14f
        box.addView(info, LinearLayout.LayoutParams(match, wrap))

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lb = TextView(act)
        lb.text = "Peso objetivo en MB"
        lb.textSize = 16f
        val et = EditText(act)
        et.setText("28")
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        et.setSingleLine(true)
        et.setSelectAllOnFocus(true)
        row.addView(lb, LinearLayout.LayoutParams(wrap, wrap))
        row.addView(et, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = dp(12) })
        box.addView(row, LinearLayout.LayoutParams(match, wrap))

        val chips = LinearLayout(act)
        chips.orientation = LinearLayout.HORIZONTAL
        for (v in intArrayOf(10, 25, 28, 49)) {
            val bt = Button(act)
            bt.text = v.toString()
            bt.setOnClickListener { et.setText(v.toString()) }
            chips.addView(bt, LinearLayout.LayoutParams(0, wrap, 1f))
        }
        box.addView(chips, LinearLayout.LayoutParams(match, wrap))

        val res = TextView(act)
        res.textSize = 14f
        box.addView(res, LinearLayout.LayoutParams(match, wrap))

        fun target(): Long? {
            val v = et.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: return null
            return if (v >= 1.0) (v * 1_000_000.0).toLong() else null
        }
        fun refresh() {
            val t = target()
            res.text = if (t == null) "Escribe un peso de 1 MB o más."
            else if (t >= m.sizeBytes) "Este video ya pesa menos que eso: no hace falta reducirlo."
            else {
                val p = plan(m, t)
                if (p.tooLow) "Ese peso es muy bajo para ${m.durMs / 1000} s de video: la imagen no sería usable. Sube el objetivo."
                else "Quedará cerca de " + fmtMb(t) + " (puede variar un poco)" +
                    (if (p.outHeight > 0) " y se baja la resolución a ${p.outHeight} px de alto." else " con la resolución original.")
            }
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        refresh()

        val scroll = ScrollView(act)
        scroll.addView(box, ViewGroup.LayoutParams(match, wrap))
        val dlg = AlertDialog.Builder(act)
            .setTitle("📉 Reducir peso")
            .setView(scroll)
            .setPositiveButton("Reducir", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = target()
            if (t == null || t >= m.sizeBytes || plan(m, t).tooLow) {
                refresh()
            } else {
                dlg.dismiss()
                start(act, uri, name, m, t)
            }
        }
    }

    // ------------------------------------------------------------ Trabajo

    private fun finish(app: Context, act: Activity, dlg: AlertDialog?, ok: Boolean, cancelled: Boolean, message: String, savedAs: String, uri: Uri?) {
        Mp3Converter.running.set(false)
        if (dlg != null) { try { dlg.dismiss() } catch (e: Exception) { } }
        val front = !act.isFinishing && !act.isDestroyed &&
            ((act as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true)
        if (cancelled) {
            if (dlg == null) LinkJobNotifier.clear(app)
            Toast.makeText(app, "Reducción cancelada", Toast.LENGTH_LONG).show()
        } else if (ok) {
            if (dlg != null || front) {
                if (dlg == null) LinkJobNotifier.clear(app)
                SavedFile.offer(act, "📉", savedAs, uri)
            } else {
                LinkJobNotifier.done(app, "📉 Guardado en Descargas", savedAs, uri)
            }
        } else {
            if (dlg == null) LinkJobNotifier.done(app, "📉 No se pudo reducir", message, null)
            Toast.makeText(app, "No se pudo reducir: $message", Toast.LENGTH_LONG).show()
        }
    }

    /** Copia el temporal a Descargas (en un hilo) y avisa en el principal. */
    private fun saveTemp(app: Context, act: Activity, dlg: AlertDialog?, tmp: File, name: String) {
        thread {
            var sink: Mp3Converter.Sink? = null
            var ok = false
            var savedAs = ""
            var uri: Uri? = null
            var msg = "No se pudo guardar en Descargas"
            try {
                val s = Mp3Converter.openSink(app, Mp3Converter.cleanTitle(name) + " (reducido).mp4", "video/mp4")
                sink = s
                FileInputStream(tmp).use { input ->
                    val b = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(b)
                        if (n < 0) break
                        s.stream.write(b, 0, n)
                    }
                }
                s.stream.flush()
                savedAs = s.commit()
                uri = s.savedUri()
                ok = true
            } catch (e: Throwable) {
                msg = "No se pudo guardar en Descargas (¿falta espacio?)"
                try { sink?.abort() } catch (x: Exception) { }
            } finally {
                try { tmp.delete() } catch (e: Exception) { }
            }
            Handler(Looper.getMainLooper()).post { finish(app, act, dlg, ok, false, msg, savedAs, uri) }
        }
    }

    private fun start(act: Activity, uri: Uri, name: String, m: Meta, targetBytes: Long) {
        if (!Mp3Converter.running.compareAndSet(false, true)) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val app = act.applicationContext
        val title = "📉 Reducir peso"
        cancelFlag = false
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
                cancelFlag = true
                dlg.setMessage("Cancelando…")
            }
        } else {
            LinkJobNotifier.progress(app, title, "Preparando…", -1)
            Toast.makeText(app, "En segundo plano: mira la notificación para ver el avance o cancelar", Toast.LENGTH_SHORT).show()
        }
        val tmp = File(app.cacheDir, "castv_reducir.mp4")
        try { tmp.delete() } catch (e: Exception) { }
        val main = Handler(Looper.getMainLooper())
        try {
            val p = plan(m, targetBytes)
            val videoEffects = ArrayList<Effect>()
            if (p.outHeight > 0) videoEffects.add(Presentation.createForHeight(p.outHeight))
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(ArrayList<AudioProcessor>(), videoEffects))
                .build()
            val encoder = DefaultEncoderFactory.Builder(app)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(p.videoBps.toInt()).build())
                .setEnableFallback(true)
                .build()
            var finished = false
            lateinit var transformer: Transformer
            val poll = object : Runnable {
                override fun run() {
                    if (finished) return
                    if (cancelFlag || LinkJobNotifier.cancel) {
                        finished = true
                        try { transformer.cancel() } catch (e: Exception) { }
                        try { tmp.delete() } catch (e: Exception) { }
                        finish(app, act, dlg, false, true, "Cancelado", "", null)
                        return
                    }
                    val holder = ProgressHolder()
                    val st = try { transformer.getProgress(holder) } catch (e: Exception) { Transformer.PROGRESS_STATE_NOT_STARTED }
                    if (st == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val text = "Reduciendo… ${holder.progress} %"
                        if (dlg == null) LinkJobNotifier.progress(app, title, text, holder.progress)
                        else if (!act.isFinishing && !act.isDestroyed) dlg.setMessage(text)
                    }
                    main.postDelayed(this, 500)
                }
            }
            transformer = Transformer.Builder(app)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoder)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (finished) return
                        finished = true
                        main.removeCallbacks(poll)
                        if (dlg == null) LinkJobNotifier.progress(app, title, "Guardando en Descargas…", -1)
                        else if (!act.isFinishing && !act.isDestroyed) dlg.setMessage("Guardando en Descargas…")
                        saveTemp(app, act, dlg, tmp, name)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (finished) return
                        finished = true
                        main.removeCallbacks(poll)
                        try { tmp.delete() } catch (e: Exception) { }
                        finish(app, act, dlg, false, false, "formato no compatible o error del codificador (${exportException.errorCodeName})", "", null)
                    }
                })
                .build()
            transformer.start(edited, tmp.absolutePath)
            main.postDelayed(poll, 500)
        } catch (e: Throwable) {
            try { tmp.delete() } catch (x: Exception) { }
            finish(app, act, dlg, false, false, "Error: " + e.javaClass.simpleName, "", null)
        }
    }
}
