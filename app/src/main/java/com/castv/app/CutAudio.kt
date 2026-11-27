package com.castv.app

import android.app.Activity
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [CORTAR-1a] ✂️ Cortar audio: saca un tramo (inicio y fin) de una pista o del audio de un video del teléfono y lo
 * guarda como MP3 nuevo en Descargas; el original no se toca. Usa Mp3Converter con rango (mismo LAME, 192 kbps) y el
 * mismo aviso con % y Cancelar de LinkJobNotifier. No guarda ni escribe en el /log el nombre del archivo (R9).
 */
object CutAudio {
    private val cancelFlag = AtomicBoolean(false)

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
    fun show(act: Activity, uri: Uri, name: String, durationMs: Long, posMs: () -> Long) {
        if (act.isFinishing || act.isDestroyed) return
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
        info.text = "Duración total: " + fmt(durationMs) + "\nEscribe los tiempos como m:ss, o toca «Aquí» para usar donde va ahora."
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

        val etStart = row("Inicio", "0:00")
        val etEnd = row("Fin", fmt(durationMs))
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
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        }
        etStart.addTextChangedListener(watcher)
        etEnd.addTextChangedListener(watcher)
        refresh()

        val scroll = ScrollView(act)
        scroll.addView(box, ViewGroup.LayoutParams(match, wrap))
        val dlg = AlertDialog.Builder(act)
            .setTitle("✂️ Cortar audio → MP3")
            .setView(scroll)
            .setPositiveButton("Cortar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val r = range()
            if (r == null) {
                refresh()
            } else {
                dlg.dismiss()
                start(act, uri, name, r.first, r.second)
            }
        }
    }

    /** Corta [startMs]..[endMs] y lo guarda en Descargas; avance y Cancelar por notificación (o recuadro si están apagadas). */
    fun start(act: Activity, uri: Uri, name: String, startMs: Long, endMs: Long) {
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val app = act.applicationContext
        val title = "✂️ Cortar audio"
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
                Mp3Converter.convert(app, uri, name, { pct ->
                    if (dlg == null) LinkJobNotifier.progress(app, title, "Cortando… $pct %", pct)
                    else act.runOnUiThread { if (!act.isFinishing && !act.isDestroyed) dlg.setMessage("Cortando… $pct %") }
                }, { cancelFlag.get() || LinkJobNotifier.cancel }, startMs * 1000L, endMs * 1000L, " (corte)")
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
                        SavedFile.offer(act, "✂️", res.savedAs, res.uri)
                    } else {
                        LinkJobNotifier.done(app, "✂️ Guardado en Descargas", res.savedAs, res.uri)
                    }
                } else {
                    if (dlg == null) LinkJobNotifier.done(app, "✂️ No se pudo completar", res.message, null)
                    Toast.makeText(app, "No se pudo cortar: " + res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
