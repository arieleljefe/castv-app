package com.castv.app

import android.content.ContentUris
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.concurrent.thread

/**
 * Reproduce videos en el propio teléfono (pantalla completa).
 * Pasa solo al siguiente video, puede mandar el actual al TV y muestra subtítulos .srt / .vtt.
 */
class VideoPlayerActivity : AppCompatActivity() {

    private class Cue(val start: Int, val end: Int, val text: String)

    private lateinit var video: VideoView
    private lateinit var subText: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var controller: MediaController

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var pendingStart = 0
    private var subs: List<Cue> = emptyList()
    private var subOffset = 0          // ms; positivo = los subtítulos salen más tarde
    private var shownCue: Cue? = null
    private var resumeOnReturn = false
    private val handler = Handler(Looper.getMainLooper())
    private val reqSub = 5101

    private val tick = object : Runnable {
        override fun run() {
            updateSub()
            handler.postDelayed(this, 250)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        Store.init(this)

        // [Etapa 13-A] Enlace directo (mp4 / HLS .m3u8): un solo "video" con id 0 y la URL en uriString
        val streamUrl = intent.getStringExtra("url")
        if (streamUrl != null) {
            items = listOf(
                MediaItem(
                    id = 0L, name = intent.getStringExtra("urlTitle") ?: "Enlace",
                    uriString = streamUrl, isMusic = false, durationSec = 0))
        } else {
            val ids = intent.getLongArrayExtra("ids") ?: LongArray(0)
            val names = intent.getStringArrayExtra("names") ?: emptyArray()
            val durs = intent.getIntArrayExtra("durs") ?: IntArray(0)
            if (ids.isEmpty() || names.size != ids.size) {
                finish()
                return
            }
            items = ids.indices.map { i ->
                MediaItem(
                    id = ids[i], name = names[i], uriString = uriFor(ids[i]).toString(),
                    isMusic = false, durationSec = durs.getOrElse(i) { 0 })
            }
        }
        index = intent.getIntExtra("index", 0).coerceIn(0, items.size - 1)
        pendingStart = intent.getIntExtra("start", 0)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        video = VideoView(this)
        root.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        subText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(0xAA000000.toInt())
                cornerRadius = dp(6).toFloat()
            }
            visibility = View.GONE
        }
        root.addView(
            subText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(dp(24), 0, dp(24), dp(72))
            })

        // Barra superior: título + 💬 + 📺 (solo se ve cuando se muestran los controles)
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(20), dp(12), dp(10))
            setBackgroundColor(0x66000000)
            visibility = View.GONE
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(chip("💬") { showSubtitleMenu() })
        topBar.addView(chip("📺 TV") { sendToTv() })
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        setContentView(root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars = WindowInsetsControllerCompat(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())

        controller = object : MediaController(this@VideoPlayerActivity) {
            override fun show(timeout: Int) {
                super.show(timeout)
                topBar.visibility = View.VISIBLE
            }

            override fun hide() {
                super.hide()
                topBar.visibility = View.GONE
            }
        }
        controller.setPrevNextListeners({ playAt(index + 1) }, { playAt(index - 1) })
        video.setMediaController(controller)

        video.setOnPreparedListener {
            if (pendingStart > 0) video.seekTo(pendingStart * 1000)
            pendingStart = 0
            video.start()
        }
        video.setOnCompletionListener {
            Store.clearResume(items[index])
            if (index + 1 < items.size) playAt(index + 1, saveCurrent = false) else finish()
        }
        video.setOnErrorListener { _, _, _ ->
            toast("No se pudo reproducir: ${items[index].name}")
            if (index + 1 < items.size) playAt(index + 1, saveCurrent = false) else finish()
            true
        }
        playAt(index, startAt = pendingStart, saveCurrent = false)
        handler.post(tick)
    }

    private fun chip(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            setColor(0x55FFFFFF)
            cornerRadius = dp(18).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        }
    }

    private fun uriFor(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

    // ------------------------------------------------------------ Reproducción

    private fun playAt(i: Int, startAt: Int = 0, saveCurrent: Boolean = true) {
        if (i !in items.indices) return
        if (saveCurrent) saveProgress()
        index = i
        val it = items[i]
        pendingStart = startAt
        subs = emptyList()
        subOffset = 0
        shownCue = null
        subText.visibility = View.GONE
        titleView.text = it.name.substringBeforeLast('.')
        video.setVideoURI(Uri.parse(it.uriString))
        loadSavedSub(it)
    }

    private fun saveProgress() {
        val cur = items.getOrNull(index) ?: return
        if (cur.id == 0L) return   // [Etapa 13-A] los enlaces no guardan punto de reanudación
        if (!::video.isInitialized) return
        val pos = try { video.currentPosition / 1000 } catch (e: Exception) { 0 }
        val dur = if (cur.durationSec > 0) cur.durationSec
                  else try { video.duration / 1000 } catch (e: Exception) { 0 }
        if (dur > 0 && pos >= dur - 10) Store.clearResume(cur)
        else if (pos >= 10) Store.saveResume(cur, pos)
    }

    /** 📺 TV: vuelve a la lista y manda este video al TV desde el punto actual. */
    private fun sendToTv() {
        val cur = items[index]
        if (cur.id == 0L) {   // [Etapa 13-A] enlace: se manda la URL tal cual al TV
            val pos = try { video.currentPosition / 1000 } catch (e: Exception) { 0 }
            setResult(
                RESULT_OK,
                Intent().putExtra("tvUrl", cur.uriString).putExtra("tvName", cur.name)
                    .putExtra("tvPos", pos))
            finish()
            return
        }
        val ext = cur.name.substringAfterLast('.', "").lowercase()
        if (ext != "mp4" && ext != "m4v") {
            toast("⚠️ El TV solo lee mp4 (este es .$ext)")
            return
        }
        val pos = try { video.currentPosition / 1000 } catch (e: Exception) { 0 }
        setResult(
            RESULT_OK,
            Intent().putExtra("tvId", cur.id).putExtra("tvName", cur.name)
                .putExtra("tvDur", cur.durationSec).putExtra("tvPos", pos))
        finish()
    }

    // ------------------------------------------------------------ Subtítulos

    private fun subPrefs() = getSharedPreferences("castv_subs", MODE_PRIVATE)

    private fun subKey(m: MediaItem) = "s|" + Store.key(m)

    private fun loadSavedSub(m: MediaItem) {
        if (m.id == 0L) return   // [Etapa 13-A] enlaces sin subtítulo recordado
        val u = subPrefs().getString(subKey(m), null) ?: return
        loadSub(Uri.parse(u), false)
    }

    private fun showSubtitleMenu() {
        val labels = arrayOf(
            "📂 Elegir archivo (.srt / .vtt)…",
            "⏩ Subtítulos más tarde (+0,5 s)",
            "⏪ Subtítulos más temprano (−0,5 s)",
            "🚫 Quitar subtítulos")
        AlertDialog.Builder(this)
            .setTitle("Subtítulos")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*")
                        try { startActivityForResult(i, reqSub) } catch (e: Exception) { toast("No hay selector de archivos") }
                    }
                    1 -> { subOffset += 500; toast("Desfase: ${subOffset / 1000.0} s") }
                    2 -> { subOffset -= 500; toast("Desfase: ${subOffset / 1000.0} s") }
                    3 -> {
                        subs = emptyList()
                        shownCue = null
                        subText.visibility = View.GONE
                        subPrefs().edit().remove(subKey(items[index])).apply()
                        toast("Subtítulos quitados")
                    }
                }
            }
            .show()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqSub && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            loadSub(uri, true)
        }
    }

    private fun loadSub(uri: Uri, remember: Boolean) {
        val idx = index
        thread {
            val cues = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                var text = String(bytes, Charsets.UTF_8)
                if (text.contains('�')) text = String(bytes, Charsets.ISO_8859_1)
                parseSubs(text.removePrefix("﻿"))
            } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing || idx != index) return@runOnUiThread
                subs = cues
                shownCue = null
                if (cues.isEmpty()) {
                    toast("No se pudieron leer los subtítulos")
                } else {
                    toast("💬 Subtítulos cargados (${cues.size} líneas)")
                    if (remember) subPrefs().edit().putString(subKey(items[idx]), uri.toString()).apply()
                }
            }
        }
    }

    private val timeRe = Regex(
        """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val tagRe = Regex("<[^>]*>|\\{[^}]*\\}")

    private fun ms(h: String, m: String, s: String, f: String): Int =
        ((h.ifEmpty { "0" }.toInt() * 60 + m.toInt()) * 60 + s.toInt()) * 1000 +
            f.padEnd(3, '0').take(3).toInt()

    private fun parseSubs(raw: String): List<Cue> {
        val out = ArrayList<Cue>()
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        for (block in text.split(Regex("\n[ \t]*\n"))) {
            val lines = block.split('\n')
            var t = -1
            var m: MatchResult? = null
            for (k in lines.indices) {
                val r = timeRe.find(lines[k])
                if (r != null) { t = k; m = r; break }
            }
            if (m == null) continue
            val g = m.groupValues
            val body = lines.drop(t + 1).joinToString("\n") { it.trim() }
                .replace(tagRe, "").trim()
            if (body.isEmpty()) continue
            out.add(Cue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), body))
        }
        out.sortBy { it.start }
        return out
    }

    private fun updateSub() {
        if (subs.isEmpty() || !::video.isInitialized) return
        val pos = (try { video.currentPosition } catch (e: Exception) { 0 }) - subOffset
        val cue = subs.firstOrNull { pos >= it.start && pos <= it.end }
        if (cue === shownCue) return
        shownCue = cue
        if (cue == null) {
            subText.visibility = View.GONE
        } else {
            subText.text = cue.text
            subText.visibility = View.VISIBLE
        }
    }

    // ------------------------------------------------------------ Ciclo de vida

    override fun onPause() {
        saveProgress()
        if (::video.isInitialized) {
            resumeOnReturn = try { video.isPlaying } catch (e: Exception) { false }
            try { video.pause() } catch (e: Exception) { }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeOnReturn && ::video.isInitialized) {
            resumeOnReturn = false
            try { video.start() } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::video.isInitialized) {
            try { video.stopPlayback() } catch (e: Exception) { }
        }
        super.onDestroy()
    }
}
