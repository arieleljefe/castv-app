package com.castv.app

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Reproduce un video en el propio teléfono (pantalla completa) y recuerda dónde se quedó. */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var video: VideoView
    private var item: MediaItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        val uri = intent.getStringExtra("uri")
        if (uri == null) {
            finish()
            return
        }
        Store.init(this)
        val id = intent.getLongExtra("id", 0L)
        val name = intent.getStringExtra("name") ?: ""
        val dur = intent.getIntExtra("dur", 0)
        val startAt = intent.getIntExtra("start", 0)
        item = MediaItem(id = id, name = name, uriString = uri, isMusic = false, durationSec = dur)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        video = VideoView(this)
        root.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        setContentView(root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars = WindowInsetsControllerCompat(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())

        video.setMediaController(MediaController(this))
        video.setOnPreparedListener {
            if (startAt > 0) video.seekTo(startAt * 1000)
            video.start()
        }
        video.setOnCompletionListener {
            item?.let { Store.clearResume(it) }
            finish()
        }
        video.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "No se pudo reproducir este video en el teléfono", Toast.LENGTH_LONG).show()
            finish()
            true
        }
        video.setVideoURI(Uri.parse(uri))
    }

    private fun saveProgress() {
        val it = item ?: return
        if (!::video.isInitialized) return
        val pos = try { video.currentPosition / 1000 } catch (e: Exception) { 0 }
        val dur = if (it.durationSec > 0) it.durationSec else try { video.duration / 1000 } catch (e: Exception) { 0 }
        if (dur > 0 && pos >= dur - 10) Store.clearResume(it)
        else if (pos >= 10) Store.saveResume(it, pos)
    }

    override fun onPause() {
        saveProgress()
        if (::video.isInitialized) {
            try { video.pause() } catch (e: Exception) { }
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::video.isInitialized) {
            try { video.stopPlayback() } catch (e: Exception) { }
        }
        super.onDestroy()
    }
}
