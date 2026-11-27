package com.castv.app

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [DIVIDIR-1] Parte un video del teléfono en varios .mp4 que pesan menos de un tope en MB (p. ej. 28 MB para subirlos
 * a sitios con límite) o en partes de una duración elegida [DIVIDIR-2], SIN recodificar (MediaExtractor + MediaMuxer): rápido y sin pérdida de calidad. Los cortes caen
 * en fotogramas clave, así las partes quedan seguidas, sin huecos ni solapes. Primero se suma el peso real de cada
 * muestra para decidir dónde cortar (con ~3 % de margen por el contenedor .mp4) y luego se escribe cada parte.
 * Usa el mismo candado (Mp3Converter.running), destino (Mp3Converter.openSink) y avisos (LinkJobNotifier) que VideoCut.
 * No guarda ni escribe en el /log el nombre del archivo (R9).
 */
object VideoSplit {
    private val cancelFlag = AtomicBoolean(false)
    private const val MARGIN = 0.97   // la parte se llena hasta el 97 % del tope; el resto es margen del contenedor

    /** Una parte: del fotograma clave [startUs] hasta el siguiente corte [endUs] (-1 = hasta el final). */
    class Part(val startUs: Long, val endUs: Long, val estBytes: Long)

    class Plan(val parts: List<Part>, val oversize: Boolean, val totalUs: Long)

    class SplitResult(
        val ok: Boolean,
        val message: String,
        val names: List<String> = emptyList(),
        val sizes: List<Long> = emptyList(),
        val uris: List<Uri> = emptyList(),
        val cancelled: Boolean = false,
        val over: Boolean = false
    )

    private class PartOut(val code: Int, val message: String, val name: String = "", val uri: Uri? = null, val size: Long = -1L)

    private fun fmtMb(bytes: Long): String = String.format(java.util.Locale.forLanguageTag("es"), "%.1f MB", bytes / 1_000_000.0)

    private fun sizeOf(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    } catch (e: Exception) { -1L }

    // ------------------------------------------------------------ Plan: dónde cortar

    /**
     * Recorre las muestras de video y audio sumando su peso. Corta siempre en el ÚLTIMO fotograma clave antes de pasar
     * el tope, así cada parte queda lo más llena posible sin pasarse. Si un tramo entre dos fotogramas clave ya pesa más
     * que el tope, se corta en el siguiente fotograma clave y se marca [Plan.oversize] (esa parte pasará el tope).
     */
    fun plan(context: Context, src: Uri, limitBytes: Long, isCancelled: () -> Boolean): Plan? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, src, null)
            var videoTrack = -1
            for (i in 0 until ex.trackCount) {
                val mt = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("video/") && videoTrack < 0) videoTrack = i
            }
            if (videoTrack < 0) return null
            ex.selectTrack(videoTrack)
            for (i in 0 until ex.trackCount) {
                val mt = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("audio/")) ex.selectTrack(i)
            }
            val budget = (limitBytes * MARGIN).toLong()
            val buf: ByteBuffer? = if (Build.VERSION.SDK_INT >= 28) null else ByteBuffer.allocate(8 * 1024 * 1024)   // antes de Android 9 no hay sampleSize
            val parts = ArrayList<Part>()
            var partStart = -1L      // fotograma clave donde empieza la parte en curso
            var cur = 0L             // bytes de la parte en curso
            var candUs = -1L         // último fotograma clave visto (posible corte)
            var candBytes = 0L       // bytes de la parte justo antes de ese fotograma clave
            var over = false
            var lastT = 0L
            while (true) {
                if (isCancelled()) return null
                val ti = ex.sampleTrackIndex
                if (ti < 0) break
                val t = ex.sampleTime
                if (t < 0L) break
                val sz: Long = if (Build.VERSION.SDK_INT >= 28) ex.sampleSize else {
                    val bb = buf ?: return null
                    bb.clear()
                    ex.readSampleData(bb, 0).toLong()
                }
                if (sz < 0L) break
                if (t > lastT) lastT = t
                if (ti == videoTrack && (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    if (partStart < 0L) {
                        partStart = 0L
                    } else {
                        candUs = t
                        candBytes = cur
                        if (cur > budget) {   // la parte ya se había pasado sin poder cortar: se corta en este fotograma clave
                            parts.add(Part(partStart, t, cur))
                            over = true
                            partStart = t
                            cur = 0L
                            candUs = -1L
                            candBytes = 0L
                        }
                    }
                }
                cur += sz
                if (cur > budget) {
                    if (candUs > partStart) {
                        parts.add(Part(partStart, candUs, candBytes))
                        cur -= candBytes
                        partStart = candUs
                        candUs = -1L
                        candBytes = 0L
                        if (cur > budget) over = true
                    } else {
                        over = true
                    }
                }
                if (!ex.advance()) break
            }
            if (partStart < 0L) return null
            parts.add(Part(partStart, -1L, cur))
            return Plan(parts, over, lastT)
        } catch (e: Throwable) {
            return null
        } finally {
            try { ex.release() } catch (e: Exception) { }
        }
    }

    /**
     * [DIVIDIR-2] Cortes por duración: cada parte dura cerca de [segUs] microsegundos. Corta en el ÚLTIMO fotograma
     * clave que no pasa de ese tiempo; si entre dos fotogramas clave hay más que [segUs], corta en el siguiente.
     * Solo mira los fotogramas del video (no lee el audio). No sabe el peso de cada parte ([Part.estBytes] = 0).
     */
    fun planByTime(context: Context, src: Uri, segUs: Long, isCancelled: () -> Boolean): Plan? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, src, null)
            var videoTrack = -1
            for (i in 0 until ex.trackCount) {
                val mt = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mt.startsWith("video/") && videoTrack < 0) videoTrack = i
            }
            if (videoTrack < 0) return null
            ex.selectTrack(videoTrack)
            val parts = ArrayList<Part>()
            var partStart = -1L   // fotograma clave donde empieza la parte en curso
            var cand = -1L        // último fotograma clave que aún cabe en la parte en curso
            var lastT = 0L
            while (true) {
                if (isCancelled()) return null
                if (ex.sampleTrackIndex < 0) break
                val t = ex.sampleTime
                if (t < 0L) break
                if (t > lastT) lastT = t
                if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    if (partStart < 0L) {
                        partStart = 0L
                    } else {
                        while (t - partStart > segUs) {
                            val cut = if (cand > partStart) cand else t
                            parts.add(Part(partStart, cut, 0L))
                            partStart = cut
                            cand = -1L
                        }
                        if (t > partStart) cand = t
                    }
                }
                if (!ex.advance()) break
            }
            if (partStart < 0L) return null
            parts.add(Part(partStart, -1L, 0L))
            return Plan(parts, false, lastT)
        } catch (e: Throwable) {
            return null
        } finally {
            try { ex.release() } catch (e: Exception) { }
        }
    }

    private fun durationMs(context: Context, src: Uri): Long = try {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(context, src)
            mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
        } finally { try { mr.release() } catch (e: Exception) { } }
    } catch (e: Exception) { -1L }

    private fun fmtTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0L)
        return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
    }

    // ------------------------------------------------------------ Escribir una parte

    private fun rotationOf(context: Context, src: Uri): Int = try {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(context, src)
            val rot = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 180 || rot == 270) rot else 0
        } finally { try { mr.release() } catch (e: Exception) { } }
    } catch (e: Exception) { 0 }

    /** Escribe un .mp4 con lo que cae entre [Part.startUs] y [Part.endUs]. Códigos: 0 ok · 1 cancelado · 2 el formato no cabe · 3 sin video · 4 error. */
    private fun writePart(
        context: Context,
        src: Uri,
        displayName: String,
        part: Part,
        totalUs: Long,
        rot: Int,
        onPct: (Int) -> Unit,
        isCancelled: () -> Boolean
    ): PartOut {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null
        var sink: Mp3Converter.Sink? = null
        var started = false
        var success = false
        try {
            ex.setDataSource(context, src, null)
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
            if (videoTrack < 0) return PartOut(3, "Este archivo no tiene video")

            val s = Mp3Converter.openSink(context, "$displayName.mp4", "video/mp4")
            sink = s
            try { s.stream.close() } catch (e: Exception) { }   // el muxer escribe por su cuenta (necesita un archivo con búsqueda)
            val m: MediaMuxer = if (s.uri != null && s.resolver != null && Build.VERSION.SDK_INT >= 29) {
                val p = s.resolver.openFileDescriptor(s.uri, "rw") ?: return PartOut(4, "No se pudo abrir el archivo en Descargas")
                pfd = p
                MediaMuxer(p.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val f = s.file ?: return PartOut(4, "No se pudo abrir el archivo en Descargas")
                MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            muxer = m

            for (k in srcTracks.indices) {   // un audio que no cabe en .mp4 se deja fuera; si es el video, se avisa
                try {
                    map[srcTracks[k]] = m.addTrack(fmts[k])
                } catch (e: Exception) {
                    if (srcTracks[k] == videoTrack) return PartOut(2, "Este formato de video no cabe en un .mp4 sin recodificar")
                }
            }
            if (rot != 0) { try { m.setOrientationHint(rot) } catch (e: Exception) { } }

            // 1) con solo el video, ir al fotograma clave donde empieza la parte (vt)
            ex.selectTrack(videoTrack)
            if (part.startUs > 0L) ex.seekTo(part.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
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
            val endUs = part.endUs
            val span = (if (endUs > 0L) endUs else totalUs) - vt
            var lastPct = -1
            var wrote = 0
            var videoDone = false

            m.start()
            started = true
            while (true) {
                if (isCancelled()) return PartOut(1, "Cancelado")
                val ti = ex.sampleTrackIndex
                if (ti < 0) break
                val t = ex.sampleTime
                if (t < 0L) break
                val isV = ti == videoTrack
                val sync = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                // el video termina al llegar al fotograma clave del siguiente corte (orden de lectura, no de tiempo)
                if (isV && !videoDone && endUs > 0L && sync && t >= endUs) videoDone = true
                if (videoDone && t >= endUs + 2_000_000L) break
                val out = map[ti]
                val rel = t - vt
                val inside = rel >= 0L && (endUs <= 0L || t < endUs) && !(isV && videoDone)
                if (out != null && inside) {
                    buf.clear()
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    info.set(0, n, rel, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    m.writeSampleData(out, buf, info)
                    wrote++
                    if (span > 0L) {
                        val pct = ((rel * 100L) / span).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; onPct(pct) }
                    }
                }
                if (!ex.advance()) break
            }
            if (wrote == 0) return PartOut(3, "No se obtuvo video en ese tramo")
            m.stop()
            started = false
            try { pfd?.close() } catch (e: Exception) { }
            pfd = null
            val savedAs = s.commit()
            success = true
            val u = s.savedUri()
            val size = if (u != null) sizeOf(context, u) else (s.file?.length() ?: -1L)
            return PartOut(0, "OK", savedAs, u, size)
        } catch (e: Throwable) {
            return PartOut(4, "Error: ${e.javaClass.simpleName}")
        } finally {
            try { if (started) muxer?.stop() } catch (e: Exception) { }
            try { muxer?.release() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
            if (!success) { try { sink?.abort() } catch (e: Exception) { } }
        }
    }

    // ------------------------------------------------------------ Dividir todo

    /** Calcula las partes y las escribe una tras otra en Descargas como «nombre (parte 1 de N).mp4». */
    fun split(
        context: Context,
        src: Uri,
        videoName: String,
        limitBytes: Long,
        segUs: Long,   // [DIVIDIR-2] > 0 = dividir por duración (limitBytes no se usa)
        onStatus: (String, Int) -> Unit,
        isCancelled: () -> Boolean
    ): SplitResult {
        if (!Mp3Converter.running.compareAndSet(false, true)) return SplitResult(false, "Ya hay una conversión en curso")
        try {
            onStatus("Calculando las partes…", -1)
            val plan = if (segUs > 0L) planByTime(context, src, segUs, isCancelled) else plan(context, src, limitBytes, isCancelled)
            if (isCancelled()) return SplitResult(false, "Cancelado", cancelled = true)
            if (plan == null) return SplitResult(false, "No se pudo leer este video (¿tiene imagen?)")
            val total = plan.parts.size
            if (total < 2) return SplitResult(false, if (segUs > 0L) "Ya dura menos que eso: no hace falta dividirlo" else "Ya pesa menos de ese tope: no hace falta dividirlo")
            if (total > 100) return SplitResult(false, "Saldrían $total partes (el máximo es 100): elige un tope o una duración mayor")
            val rot = rotationOf(context, src)
            val base = Mp3Converter.cleanTitle(videoName)
            val width = total.toString().length
            val names = ArrayList<String>()
            val sizes = ArrayList<Long>()
            val uris = ArrayList<Uri>()
            var over = plan.oversize
            for (k in 0 until total) {
                val num = (k + 1).toString().padStart(width, '0')
                val o = writePart(
                    context, src, "$base (parte $num de $total)", plan.parts[k], plan.totalUs, rot,
                    { pct -> onStatus("Parte ${k + 1} de $total… $pct %", (k * 100 + pct) / total) },
                    isCancelled
                )
                if (o.code == 1) return SplitResult(false, "Cancelado", names, sizes, uris, cancelled = true)
                if (o.code != 0) {
                    val extra = if (names.isNotEmpty()) " (ya se guardaron ${names.size} partes)" else ""
                    return SplitResult(false, o.message + extra, names, sizes, uris)
                }
                names.add(o.name)
                sizes.add(o.size)
                if (o.uri != null) uris.add(o.uri)
                if (segUs <= 0L && o.size > limitBytes) over = true
            }
            return SplitResult(true, "OK", names, sizes, uris, over = over)
        } catch (e: Throwable) {
            return SplitResult(false, "Error: " + e.javaClass.simpleName)
        } finally {
            Mp3Converter.running.set(false)
        }
    }

    // ------------------------------------------------------------ Pantalla

    /** Recuadro para elegir cómo dividir [uri]: por tamaño (tope en MB) o por duración (segundos por parte). */
    fun show(act: Activity, uri: Uri, name: String) {
        if (act.isFinishing || act.isDestroyed) return
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val total = sizeOf(act.applicationContext, uri)
        if (total <= 0L) {
            Toast.makeText(act.applicationContext, "No pude leer el peso de este video", Toast.LENGTH_SHORT).show()
            return
        }
        val durMs = durationMs(act.applicationContext, uri)
        val dens = act.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * dens).toInt()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))

        var byTime = false   // [DIVIDIR-2] false = por tamaño (como DIVIDIR-1)
        val modes = LinearLayout(act)
        modes.orientation = LinearLayout.HORIZONTAL
        val btSize = Button(act)
        btSize.text = "Por tamaño"
        val btTime = Button(act)
        btTime.text = "Por duración"
        modes.addView(btSize, LinearLayout.LayoutParams(0, wrap, 1f))
        modes.addView(btTime, LinearLayout.LayoutParams(0, wrap, 1f))
        box.addView(modes, LinearLayout.LayoutParams(match, wrap))

        val info = TextView(act)
        info.textSize = 14f
        box.addView(info, LinearLayout.LayoutParams(match, wrap))

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lb = TextView(act)
        lb.textSize = 16f
        val et = EditText(act)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        et.setSingleLine(true)
        et.setSelectAllOnFocus(true)
        row.addView(lb, LinearLayout.LayoutParams(wrap, wrap))
        row.addView(et, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = dp(12) })
        box.addView(row, LinearLayout.LayoutParams(match, wrap))

        val chips = LinearLayout(act)
        chips.orientation = LinearLayout.HORIZONTAL
        box.addView(chips, LinearLayout.LayoutParams(match, wrap))

        val res = TextView(act)
        res.textSize = 14f
        box.addView(res, LinearLayout.LayoutParams(match, wrap))

        fun limit(): Long? {
            val v = et.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: return null
            return if (v >= 1.0) (v * 1_000_000.0).toLong() else null
        }
        fun secs(): Double? {
            val v = et.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: return null
            return if (v >= 5.0) v else null
        }
        fun refresh() {
            if (!byTime) {
                val lim = limit()
                res.text = if (lim == null) "Escribe un tope de 1 MB o más."
                else if (total <= (lim * MARGIN).toLong()) "Este video ya pesa menos de ese tope: no hace falta dividirlo."
                else "Saldrán unas " + Math.ceil(total / (lim * MARGIN)).toInt() + " partes de menos de " + fmtMb(lim) + " (cálculo aproximado)."
            } else {
                val sec = secs()
                res.text = if (sec == null) "Escribe 5 segundos o más."
                else if (durMs <= 0L) "No pude leer la duración: se calculará al dividir."
                else if (durMs <= (sec * 1000.0).toLong()) "Este video dura menos que eso: no hace falta dividirlo."
                else "Saldrán unas " + Math.ceil(durMs / (sec * 1000.0)).toInt() + " partes de unos " + sec.toLong() + " s (cálculo aproximado)."
            }
        }
        fun setMode(t: Boolean) {
            byTime = t
            btSize.isEnabled = t
            btTime.isEnabled = !t
            chips.removeAllViews()
            if (!t) {
                info.text = "Peso del video: " + fmtMb(total) + "\nCada parte pesará menos del tope (1 MB = 1.000.000 bytes). " +
                    "Sin recodificar: no pierde calidad, pero tampoco se hace más liviano; solo se parte en fotogramas clave."
                lb.text = "Tope en MB"
                et.setText("28")
                for (v in intArrayOf(10, 25, 28, 49)) {
                    val bt = Button(act)
                    bt.text = v.toString()
                    bt.setOnClickListener { et.setText(v.toString()) }
                    chips.addView(bt, LinearLayout.LayoutParams(0, wrap, 1f))
                }
            } else {
                info.text = (if (durMs > 0L) "Duración del video: " + fmtTime(durMs) + "\n" else "") +
                    "Cada parte durará cerca de lo que elijas; los cortes caen en fotogramas clave y pueden variar unos segundos. " +
                    "Sin recodificar: no pierde calidad, pero el peso de cada parte no se controla."
                lb.text = "Segundos por parte"
                et.setText("60")
                val opts = arrayOf("30 s" to 30, "1 min" to 60, "2 min" to 120, "5 min" to 300)
                for (o in opts) {
                    val bt = Button(act)
                    bt.text = o.first
                    bt.setOnClickListener { et.setText(o.second.toString()) }
                    chips.addView(bt, LinearLayout.LayoutParams(0, wrap, 1f))
                }
            }
            refresh()
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        btSize.setOnClickListener { setMode(false) }
        btTime.setOnClickListener { setMode(true) }
        setMode(false)

        val scroll = ScrollView(act)
        scroll.addView(box, ViewGroup.LayoutParams(match, wrap))
        val dlg = AlertDialog.Builder(act)
            .setTitle("✂️ Dividir el video")
            .setView(scroll)
            .setPositiveButton("Dividir", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (byTime) {
                val sec = secs()
                if (sec == null || (durMs > 0L && durMs <= (sec * 1000.0).toLong())) {
                    refresh()
                } else {
                    dlg.dismiss()
                    start(act, uri, name, 0L, (sec * 1_000_000.0).toLong())
                }
            } else {
                val lim = limit()
                if (lim == null || total <= (lim * MARGIN).toLong()) {
                    refresh()
                } else {
                    dlg.dismiss()
                    start(act, uri, name, lim, 0L)
                }
            }
        }
    }

    private fun summary(r: SplitResult): String =
        r.names.indices.joinToString("\n") { i -> r.names[i] + (if (r.sizes[i] > 0L) " · " + fmtMb(r.sizes[i]) else "") }

    /** Resultado: Compartir todas las partes (o solo el aviso si Android no dio los archivos). */
    private fun offer(act: Activity, r: SplitResult) {
        if (act.isFinishing || act.isDestroyed) return
        val b = AlertDialog.Builder(act)
            .setTitle("✂️ " + r.names.size + " partes en Descargas")
            .setMessage(summary(r) + (if (r.over) "\n\nOjo: alguna parte pasa el tope (un tramo entre fotogramas clave pesa demasiado)." else ""))
            .setNegativeButton("Cerrar", null)
        if (r.uris.isNotEmpty()) {
            b.setPositiveButton("Compartir todas") { _, _ ->
                try {
                    val list = ArrayList<Uri>(r.uris)
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("video/mp4")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    act.startActivity(Intent.createChooser(send, "Compartir"))
                } catch (e: Exception) { }
            }
            b.setNeutralButton("Abrir la 1.ª") { _, _ ->
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(r.uris[0], "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) {
                    Toast.makeText(act.applicationContext, "No hay una app para abrirlo", Toast.LENGTH_SHORT).show()
                }
            }
        }
        b.show()
    }

    /** Divide y guarda; avance y Cancelar por notificación (o recuadro si Android las tiene apagadas). */
    private fun start(act: Activity, uri: Uri, name: String, limitBytes: Long, segUs: Long) {
        if (Mp3Converter.running.get() || LinkDownload.running.get()) {
            Toast.makeText(act.applicationContext, "Ya hay una conversión o descarga en curso", Toast.LENGTH_SHORT).show()
            return
        }
        val app = act.applicationContext
        val title = "✂️ Dividir el video"
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
            val res: SplitResult = try {
                val status: (String, Int) -> Unit = { text, pct ->
                    if (dlg == null) LinkJobNotifier.progress(app, title, text, pct)
                    else act.runOnUiThread { if (!act.isFinishing && !act.isDestroyed) dlg.setMessage(text) }
                }
                val stop: () -> Boolean = { cancelFlag.get() || LinkJobNotifier.cancel }
                split(app, uri, name, limitBytes, segUs, status, stop)
            } catch (e: Throwable) {
                SplitResult(false, "Error: " + e.javaClass.simpleName)
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
                    Toast.makeText(app, if (res.names.isEmpty()) "División cancelada" else "División cancelada: ya quedaron ${res.names.size} partes en Descargas", Toast.LENGTH_LONG).show()
                } else if (res.ok) {
                    if (dlg != null || front) {
                        if (dlg == null) LinkJobNotifier.clear(app)
                        offer(act, res)
                    } else {
                        LinkJobNotifier.done(app, "✂️ " + res.names.size + " partes en Descargas", summary(res), res.uris.firstOrNull())
                    }
                } else {
                    if (dlg == null) LinkJobNotifier.done(app, "✂️ No se pudo completar", res.message, null)
                    Toast.makeText(app, "No se pudo dividir: " + res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
