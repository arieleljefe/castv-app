package com.castv.app

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [CORTAR-1a] ✂️ Cortar audio: saca un tramo (inicio y fin) de una pista o del audio de un video del teléfono y lo
 * guarda como MP3 nuevo en Descargas; el original no se toca. Usa Mp3Converter con rango (mismo LAME, 192 kbps) y el
 * mismo aviso con % y Cancelar de LinkJobNotifier. No guarda ni escribe en el /log el nombre del archivo (R9).
 */
object CutAudio {
    private val cancelFlag = AtomicBoolean(false)

    /**
     * [CORTAR-2] Quien abre el recuadro puede dejar aquí qué hacer antes de «Oír el tramo» (p. ej. pausar lo que suena
     * en CASTV). `show` lo toma una sola vez y lo borra; si nadie lo pone, no se pausa nada.
     */
    @Volatile var pauseHook: (() -> Unit)? = null

    /** [TONO-1] Nombres de los destinos, en el mismo orden que Mp3Converter.DEST_*. */
    private val destLabels = arrayOf("Descargas", "Tonos de llamada", "Notificaciones", "Alarmas")

    /** [TONO-1] Dónde se elige el sonido ya guardado (nombres de Android; pueden variar según la marca). */
    private fun destHint(dest: Int): String = when (dest) {
        Mp3Converter.DEST_RINGTONE -> "Para usarlo: Ajustes de Android › Sonido › Tono del teléfono."
        Mp3Converter.DEST_NOTIFICATION -> "Para usarlo: Ajustes de Android › Sonido › Sonido de notificación."
        else -> "Para usarlo: en la app Reloj, al editar una alarma › Sonido (o Ajustes de Android › Sonido)."
    }

    /** [TONO-1] Aviso al terminar un corte guardado como tono, notificación o alarma: Abrir / Compartir y dónde elegirlo. */
    private fun offerTone(act: Activity, dest: Int, savedAs: String, uri: Uri?) {
        if (act.isFinishing || act.isDestroyed) return
        val b = AlertDialog.Builder(act)
            .setTitle("✂️ Guardado en " + destLabels[dest])
            .setMessage(savedAs + "\n\n" + destHint(dest))
            .setNegativeButton("Cerrar", null)
        if (uri != null) {
            b.setPositiveButton("Abrir") { _, _ ->
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/mpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) {
                    Toast.makeText(act.applicationContext, "No hay una app para abrirlo", Toast.LENGTH_SHORT).show()
                }
            }
            b.setNeutralButton("Compartir") { _, _ ->
                try {
                    val send = Intent(Intent.ACTION_SEND).setType("audio/mpeg").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    act.startActivity(Intent.createChooser(send, "Compartir"))
                } catch (e: Exception) { }
            }
        }
        b.show()
    }

    /** [CORTAR-2] Vista previa del tramo: un MediaPlayer propio sobre el mismo archivo; salta al inicio y para al llegar al fin. */
    private class Preview(val act: Activity, val uri: Uri, val onStop: (Boolean) -> Unit) {
        private val ui = Handler(Looper.getMainLooper())
        private var mp: MediaPlayer? = null
        private var endMs = 0L
        val playing: Boolean get() = mp != null

        private val tick = object : Runnable {
            override fun run() {
                val p = mp ?: return
                val pos = try { p.currentPosition } catch (e: Exception) { stop(true); return }
                if (pos >= endMs) { stop(true); return }
                ui.postDelayed(this, 100)
            }
        }

        fun start(startMs: Long, end: Long) {
            stop(true)
            endMs = end
            val p = MediaPlayer()
            mp = p
            try {
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                p.setDataSource(act, uri)
                p.setOnPreparedListener { q ->
                    if (mp === q) {
                        q.setOnSeekCompleteListener { s ->
                            if (mp === s) {
                                try { s.start() } catch (e: Exception) { stop(false); return@setOnSeekCompleteListener }
                                ui.postDelayed(tick, 100)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 26) q.seekTo(startMs, MediaPlayer.SEEK_CLOSEST) else q.seekTo(startMs.toInt())
                    }
                }
                p.setOnCompletionListener { q -> if (mp === q) stop(true) }
                p.setOnErrorListener { q, _, _ -> if (mp === q) stop(false); true }
                p.prepareAsync()
            } catch (e: Exception) {
                stop(false)
            }
        }

        fun stop(ok: Boolean) {
            ui.removeCallbacks(tick)
            val p = mp ?: return
            mp = null
            try { p.release() } catch (e: Exception) { }
            onStop(ok)
        }
    }

    /** Milisegundos como m:ss (o h:mm:ss). */
    fun fmt(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        val ss = s.toString().padStart(2, '0')
        return if (h > 0L) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    /** «1:30», «1:02:03», «90» o «90.5» (segundos) → milisegundos; null si no se entiende. */
    fun parse(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val parts = t.split(':')
        if (parts.size > 3) return null
        var sec = 0.0
        for (p in parts) {
            val v = p.trim().toDoubleOrNull() ?: return null
            if (v < 0.0) return null
            sec = sec * 60.0 + v
        }
        return Math.round(sec * 1000.0)
    }

    /** Recuadro para elegir inicio y fin. [posMs] da la posición actual de lo que suena (botones «Aquí»). */
    fun show(act: Activity, uri: Uri, name: String, durationMs: Long, video: Boolean = false, posMs: () -> Long) {   // [CORTAR-1b] video = corte de video (.mp4)
        if (act.isFinishing || act.isDestroyed) return
        val hook = pauseHook   // [CORTAR-2]
        pauseHook = null
        if (durationMs < 2000L) {
            Toast.makeText(act.applicationContext, "No sé cuánto dura este archivo", Toast.LENGTH_SHORT).show()
            return
        }
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
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
        info.text = "Duración total: " + fmt(durationMs) + "\nMueve las barras, escribe los tiempos como m:ss o toca «Aquí» para usar donde va ahora." +
            (if (video) "\nSin recodificar: el inicio puede adelantarse hasta el fotograma clave anterior." else "")
        info.textSize = 14f
        box.addView(info, LinearLayout.LayoutParams(match, wrap))

        fun row(label: String, initial: String): EditText {
            val r = LinearLayout(act)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val lb = TextView(act)
            lb.text = label
            lb.textSize = 16f
            val et = EditText(act)
            et.setText(initial)
            et.inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            et.setSingleLine(true)
            et.setSelectAllOnFocus(true)
            val here = Button(act)
            here.text = "Aquí"
            here.setOnClickListener { et.setText(fmt(posMs())) }
            r.addView(lb, LinearLayout.LayoutParams(dp(64), wrap))
            r.addView(et, LinearLayout.LayoutParams(0, wrap, 1f))
            r.addView(here, LinearLayout.LayoutParams(wrap, wrap))
            box.addView(r, LinearLayout.LayoutParams(match, wrap))
            return et
        }

        // [CORTAR-2] una barra bajo cada campo (de a 1 s), sincronizada con el texto
        val maxSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
        var syncing = false
        fun addBar(initial: Int): SeekBar {
            val sb = SeekBar(act)
            sb.max = maxSec
            sb.progress = initial.coerceIn(0, maxSec)
            box.addView(sb, LinearLayout.LayoutParams(match, wrap))
            return sb
        }

        val etStart = row("Inicio", "0:00")
        val sbStart = addBar(0)
        val etEnd = row("Fin", fmt(durationMs))
        val sbEnd = addBar(maxSec)

        // [TONO-1] «Guardar en»: solo para audio y con Android 10 o más (antes no hay MediaStore de sonidos)
        var dest = Mp3Converter.DEST_DOWNLOADS
        if (!video && Build.VERSION.SDK_INT >= 29) {
            val dr = LinearLayout(act)
            dr.orientation = LinearLayout.HORIZONTAL
            dr.gravity = Gravity.CENTER_VERTICAL
            val dl = TextView(act)
            dl.text = "Guardar en"
            dl.textSize = 16f
            val sp = Spinner(act)
            sp.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, destLabels)
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) { dest = position }
                override fun onNothingSelected(parent: AdapterView<*>?) { }
            }
            dr.addView(dl, LinearLayout.LayoutParams(wrap, wrap))
            dr.addView(sp, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = dp(12) })
            box.addView(dr, LinearLayout.LayoutParams(match, wrap))
        }
        val res = TextView(act)
        res.textSize = 14f
        box.addView(res, LinearLayout.LayoutParams(match, wrap))

        fun range(): Pair<Long, Long>? {
            val a = parse(etStart.text.toString()) ?: return null
            val b = parse(etEnd.text.toString()) ?: return null
            val e = if (b > durationMs) durationMs else b
            return if (e - a >= 1000L) Pair(a, e) else null
        }
        fun refresh() {
            val r = range()
            res.text = if (r == null) "Revisa los tiempos: el fin debe ir al menos 1 s después del inicio."
            else "Se guardarán " + fmt(r.second - r.first) + " (de " + fmt(r.first) + " a " + fmt(r.second) + ")."
        }
        sbStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                val endSec = ((parse(etEnd.text.toString()) ?: durationMs) / 1000L).toInt()
                var v = p
                if (v > endSec - 1) { v = (endSec - 1).coerceAtLeast(0); sbStart.progress = v }
                syncing = true
                etStart.setText(fmt(v * 1000L))
                syncing = false
                refresh()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { }
            override fun onStopTrackingTouch(sb: SeekBar?) { }
        })
        sbEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                val startSec = ((parse(etStart.text.toString()) ?: 0L) / 1000L).toInt()
                var v = p
                if (v < startSec + 1) { v = (startSec + 1).coerceAtMost(maxSec); sbEnd.progress = v }
                syncing = true
                etEnd.setText(fmt(v * 1000L))
                syncing = false
                refresh()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { }
            override fun onStopTrackingTouch(sb: SeekBar?) { }
        })

        // [CORTAR-2] ▶ Oír el tramo: antes pausa lo que suene en CASTV (si quien abrió el recuadro lo pidió)
        val play = Button(act)
        play.text = "▶ Oír el tramo"
        val preview = Preview(act, uri) { ok ->
            play.text = "▶ Oír el tramo"
            if (!ok) Toast.makeText(act.applicationContext, "No se pudo oír el tramo", Toast.LENGTH_SHORT).show()
        }
        play.setOnClickListener {
            if (preview.playing) {
                preview.stop(true)
            } else {
                val r = range()
                if (r == null) {
                    refresh()
                } else {
                    try { hook?.invoke() } catch (e: Exception) { }
                    play.text = "■ Parar"
                    preview.start(r.first, r.second)
                }
            }
        }
        box.addView(play, LinearLayout.LayoutParams(match, wrap))

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!syncing) {
                    syncing = true
                    parse(etStart.text.toString())?.let { sbStart.progress = (it / 1000L).toInt().coerceIn(0, maxSec) }
                    parse(etEnd.text.toString())?.let { sbEnd.progress = (it / 1000L).toInt().coerceIn(0, maxSec) }
                    syncing = false
                }
                refresh()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        }
        etStart.addTextChangedListener(watcher)
        etEnd.addTextChangedListener(watcher)
        refresh()

        val scroll = ScrollView(act)
        scroll.addView(box, ViewGroup.LayoutParams(match, wrap))
        val dlg = AlertDialog.Builder(act)
            .setTitle(if (video) "✂️ Cortar video → MP4" else "✂️ Cortar audio → MP3")
            .setView(scroll)
            .setPositiveButton("Cortar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnDismissListener { preview.stop(true) }   // [CORTAR-2] cerrar o cortar apaga la vista previa
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val r = range()
            if (r == null) {
                refresh()
            } else {
                dlg.dismiss()
                start(act, uri, name, r.first, r.second, video, dest)   // [TONO-1]
            }
        }
    }

    /** Corta [startMs]..[endMs] y lo guarda en Descargas; avance y Cancelar por notificación (o recuadro si están apagadas). */
    fun start(act: Activity, uri: Uri, name: String, startMs: Long, endMs: Long, video: Boolean = false, dest: Int = Mp3Converter.DEST_DOWNLOADS) {   // [CORTAR-1b] [TONO-1] dest
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val app = act.applicationContext
        val title = if (video) "✂️ Cortar video" else "✂️ Cortar audio"   // [CORTAR-1b]
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
            val res = try {
                val prog = { pct: Int ->
                    if (dlg == null) LinkJobNotifier.progress(app, title, "Cortando… $pct %", pct)
                    else act.runOnUiThread { if (!act.isFinishing && !act.isDestroyed) dlg.setMessage("Cortando… $pct %") }
                }
                val stop = { cancelFlag.get() || LinkJobNotifier.cancel }
                if (video) VideoCut.cut(app, uri, name, startMs * 1000L, endMs * 1000L, prog, stop)   // [CORTAR-1b]
                else Mp3Converter.convert(app, uri, name, prog, stop, startMs * 1000L, endMs * 1000L, " (corte)", dest)   // [TONO-1]
            } catch (e: Throwable) {
                Mp3Converter.Result(false, "Error: " + e.javaClass.simpleName)
            }
            Handler(Looper.getMainLooper()).post {
                if (dlg != null) {
                    try { dlg.dismiss() } catch (e: Exception) { }
                }
                val front = !act.isFinishing && !act.isDestroyed &&
                    ((act as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                        ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true)
                if (res.cancelled) {
                    if (dlg == null) LinkJobNotifier.clear(app)
                    Toast.makeText(app, "Corte cancelado", Toast.LENGTH_SHORT).show()
                } else if (res.ok) {
                    if (dlg != null || front) {
                        if (dlg == null) LinkJobNotifier.clear(app)
                        if (!video && dest != Mp3Converter.DEST_DOWNLOADS) offerTone(act, dest, res.savedAs, res.uri)   // [TONO-1]
                        else SavedFile.offer(act, "✂️", res.savedAs, res.uri)
                    } else {
                        LinkJobNotifier.done(app, "✂️ Guardado en " + (if (video) destLabels[0] else destLabels[dest]), res.savedAs, res.uri)   // [TONO-1]
                    }
                } else {
                    if (dlg == null) LinkJobNotifier.done(app, "✂️ No se pudo completar", res.message, null)
                    Toast.makeText(app, "No se pudo cortar: " + res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
