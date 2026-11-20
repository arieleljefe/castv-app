package com.castv.app

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.util.Rational
import android.util.TypedValue
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.concurrent.thread
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Reproduce videos en el propio teléfono (pantalla completa).
 * Pasa solo al siguiente video, puede mandar el actual al TV y muestra subtítulos .srt / .vtt.
 */
class VideoPlayerActivity : AppCompatActivity() {

    private class Cue(val start: Int, val end: Int, val text: String)

    private lateinit var video: SizedVideoView   // [UI-2c]
    private lateinit var subText: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    // [UI-2b] barra de control propia (reemplaza al MediaController del sistema)
    private lateinit var bottomBar: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var playBtn: ImageView
    private lateinit var backSkip: TextView
    private lateinit var fwdSkip: TextView
    private var dragging = false
    private var hideAt = 0L
    // [GESTOS-AJ-1] tiempo que tardan en esconderse los controles (Ajustes › Vídeo); por defecto 3,5 s
    private val hideControlsMs: Long
        get() = getSharedPreferences("castv", MODE_PRIVATE).getInt("hide_ms", 3500)
            .let { if (it in 2000..8000) it.toLong() else 3500L }

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var pendingStart = 0
    private var subs: List<Cue> = emptyList()
    private var subOffset = 0          // ms; positivo = los subtítulos salen más tarde
    private var shownCue: Cue? = null
    private var resumeOnReturn = false
    // [FOCO-1] foco de audio: pausa con llamadas/alarmas y cuando otra app toma el sonido
    private var pausedByFocus = false
    // [FOCO-2] pausa al desconectar audífonos / Bluetooth (ACTION_AUDIO_BECOMING_NOISY)
    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (!::video.isInitialized) return
            resumeOnReturn = false
            pausedByFocus = false
            try { if (video.isPlaying) video.pause() } catch (e: Exception) { }
            updatePlayIcon()
        }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) }
    private val handler = Handler(Looper.getMainLooper())
    private val reqSub = 5101
    // [IA-3] generación de subtítulos con IA
    @Volatile private var aiCancel = false
    private var aiDialog: AlertDialog? = null
    private var previewing = false     // [UI-S1] muestra un subtítulo de ejemplo al cambiar el estilo
    private val hidePreview = Runnable {
        previewing = false
        shownCue = null
        if (::subText.isInitialized) subText.visibility = View.GONE
    }

    // Gestos: brillo (derecha), volumen (izquierda), doble toque lateral ±10 s
    private lateinit var hud: TextView
    private lateinit var gesture: GestureDetector
    private lateinit var audio: AudioManager
    private var gMode = 0          // 0 sin decidir, 1 brillo, 2 volumen, -1 ignorado
    private var gDownX = 0f
    private var gDownY = 0f
    private var gBright = 0.5f
    private var gVol = 0f
    private val hideHud = Runnable { if (::hud.isInitialized) hud.visibility = View.GONE }

    // [GESTO-2] deslizar horizontal (gMode 3), mantener pulsado = 2x (gMode 4) y pellizco = zoom
    private lateinit var scale: ScaleGestureDetector
    private var gSeekStart = 0
    private var gSeekTo = 0
    private var mpRef: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null   // [VOL-1] volumen extra (más de 100 %)
    private var abA = -1               // [AB-1] inicio de la repetición A-B (ms; -1 = sin marcar)
    private var abB = -1               // [AB-1] fin (ms; -1 = sin marcar)
    private var fastOn = false
    // [GESTO-2] temporizador de apagado
    private lateinit var sleepChip: TextView
    private var sleepAt = 0L           // uptimeMillis en que se detiene (0 = sin temporizador por tiempo)
    private var sleepEnd = false       // detener al terminar este video

    // [UI-2a] bloqueo de controles y giro de pantalla
    private var locked = false
    private var sizeMode = 0       // [UI-2c] 0 ajustar, 1 llenar, 2 estirar, 3 16:9, 4 4:3
    private var rotMode = 0        // 0 automático, 1 horizontal, 2 vertical
    private lateinit var lockBtn: TextView
    private lateinit var backLock: OnBackPressedCallback
    private val hideLockBtn = Runnable { if (::lockBtn.isInitialized) lockBtn.visibility = View.GONE }

    // [TIEMPO-1] tiempo realmente reproducido (reloj de pared) de cada video del teléfono
    private var watchLastMs = 0L
    private var watchAccMs = 0L

    private fun trackWatch() {
        val playing = ::video.isInitialized && (try { video.isPlaying } catch (e: Exception) { false })
        val now = SystemClock.uptimeMillis()
        if (playing && watchLastMs > 0L) watchAccMs += minOf(now - watchLastMs, 1000L)
        watchLastMs = if (playing) now else 0L
        if (watchAccMs >= 10_000L) flushWatch()
    }

    private fun flushWatch() {
        val sec = (watchAccMs / 1000L).toInt()
        if (sec <= 0) return
        watchAccMs -= sec * 1000L
        val cur = items.getOrNull(index) ?: return
        if (cur.id != 0L && !Store.incognito) Store.addWatch(cur, sec)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (sleepAt > 0L && SystemClock.uptimeMillis() >= sleepAt) { sleepNow(); return }   // [GESTO-2]
            abLoop()   // [AB-1]
            trackWatch()   // [TIEMPO-1]
            updateSub()
            if (controlsShowing()) {   // [UI-2b]
                updateSleepChip()   // [GESTO-2]
                updateProgress()
                updatePlayIcon()
                if (!dragging && !locked && video.isPlaying && SystemClock.uptimeMillis() > hideAt) hideControls()
            }
            handler.postDelayed(this, 250)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(when (getSharedPreferences("castv", MODE_PRIVATE).getInt("accent", 0)) {   // [UI-2d] color de acento
            1 -> R.style.AccentOverlayGreen
            2 -> R.style.AccentOverlayOrange
            3 -> R.style.AccentOverlayPurple
            else -> R.style.AccentOverlayCyan
        }, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        Store.init(this)
        // [GESTO-2] brillo guardado del video (Ajustes › Vídeo › Recordar brillo)
        val cp = getSharedPreferences("castv", MODE_PRIVATE)
        if (cp.getBoolean("video_bright_on", true)) {
            val sb = cp.getFloat("video_bright", -1f)
            if (sb >= 0.01f) {
                val blp = window.attributes
                blp.screenBrightness = sb.coerceAtMost(1f)
                window.attributes = blp
            }
        }

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
        video = SizedVideoView(this)   // [UI-2c]
        // [FOCO-1] el foco de audio lo maneja esta actividad (no el VideoView), para no pelearse con su propio pedido
        if (Build.VERSION.SDK_INT >= 26) { try { video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE) } catch (e: Exception) { } }
        video.onStartHook = { focusRequest() }
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
        applySubStyle()   // [UI-S1] tamaño, negrita, color y fondo guardados

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
        topBar.addView(chip("🔄") { rotate() })   // [UI-2a]
        topBar.addView(chip("🔒") { lock() })     // [UI-2a]
        topBar.addView(chip("🖼") { cycleSize() })   // [UI-2c]
        sleepChip = chip("😴") { showSleepMenu() }   // [GESTO-2]
        topBar.addView(sleepChip)
        topBar.addView(chip("🛠") { showToolsMenu() })   // [SALTO-1]
        topBar.addView(chip("💬") { showSubtitleMenu() })
        topBar.addView(chip("📺 TV") { sendToTv() })
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        // [UI-2a] botón 🔓 (solo aparece al tocar la pantalla mientras está bloqueado)
        lockBtn = TextView(this).apply {
            text = "🔓"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(0x88000000.toInt())
                cornerRadius = dp(28).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { unlock() }
        }
        root.addView(
            lockBtn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(24) })
        backLock = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                toast("🔒 Desbloquea primero: toca la pantalla y pulsa 🔓")
            }
        }
        onBackPressedDispatcher.addCallback(this, backLock)
        setContentView(root)
        // [UI-2d] la barra de control nunca queda debajo de los botones de navegación ni del notch
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (::bottomBar.isInitialized) bottomBar.setPadding(dp(16) + b.left, dp(24), dp(16) + b.right, dp(10) + b.bottom)
            insets
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars = WindowInsetsControllerCompat(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())

        buildControls(root)   // [UI-2b]
        setupGestures(root)

        video.setOnPreparedListener { mp ->
            mpRef = mp   // [GESTO-2]
            applyBoost(mp)   // [VOL-1]
            fastOn = false
            video.setVideoDims(mp.videoWidth, mp.videoHeight)   // [UI-2c]
            val st = pendingStart
            pendingStart = 0
            if (st > 0 && getSharedPreferences("castv", MODE_PRIVATE).getBoolean("resume_ask", false)) {
                askResume(st)   // [CONFIRMAR-1] no arranca hasta que se elija
            } else {
                if (st > 0) video.seekTo(st * 1000)
                video.start()
                updatePlayIcon()
            }
        }
        video.setOnCompletionListener {
            items[index].let { if (it.id != 0L && !Store.incognito) Store.markSeen(it, true) }   // [VISTO-1]
            Store.clearResume(items[index])
            if (sleepEnd) {   // [GESTO-2] "al terminar este video"
                sleepEnd = false
                toast("😴 Temporizador: reproducción detenida")
                finish()
                return@setOnCompletionListener
            }
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

    // ------------------------------------------------------------ Gestos

    /** [GESTO-2] Con zoom la vista del video es más grande que la pantalla: los gestos se miden sobre la pantalla. */
    private fun gw(): Float = if (video.zoom > 1f) (video.parent as View).width.toFloat() else video.width.toFloat()
    private fun gh(): Float = if (video.zoom > 1f) (video.parent as View).height.toFloat() else video.height.toFloat()

    private fun inEdge(x: Float, y: Float): Boolean {
        val m = dp(32)   // márgenes seguros: evita toques accidentales y gestos del sistema
        return x < m || y < m || x > gw() - m || y > gh() - m
    }

    private fun showHud(text: String) {
        hud.text = text
        hud.visibility = View.VISIBLE
        handler.removeCallbacks(hideHud)
        handler.postDelayed(hideHud, 800)
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) { 0.5f }
    }

    /** [GESTO-1] Segundos que salta el doble toque lateral (Ajustes › Vídeo); por defecto 10. */
    private fun dtapSec(): Int = getSharedPreferences("castv", MODE_PRIVATE).getInt("dtap_sec", 10).let { if (it in 5..30) it else 10 }

    /** [GESTOS-AJ-1] Gesto activado en Ajustes › Vídeo (todos vienen activados). */
    private fun gOn(key: String): Boolean = getSharedPreferences("castv", MODE_PRIVATE).getBoolean(key, true)

    /** [GESTOS-AJ-1] Salto de los botones −N / +N; sin elegir, igual que el doble toque. */
    private fun btnSec(): Int =
        getSharedPreferences("castv", MODE_PRIVATE).getInt("btn_sec", 0).let { if (it in 5..30) it else dtapSec() }

    /** [GESTOS-AJ-1] Velocidad al mantener pulsado: 1.5x, 2x o 3x (por defecto 2x). */
    private val fastVals = floatArrayOf(1.5f, 2f, 3f)
    private val fastLabels = arrayOf("1.5x", "2x", "3x")
    private fun fastIdx(): Int = getSharedPreferences("castv", MODE_PRIVATE).getInt("fast_i", 1).coerceIn(0, 2)

    private fun seekBy(sec: Int) {
        val dur = try { video.duration } catch (e: Exception) { -1 }
        if (dur <= 0) return   // canal en vivo: no se puede adelantar
        val pos = try { video.currentPosition } catch (e: Exception) { return }
        video.seekTo((pos + sec * 1000).coerceIn(0, maxOf(dur - 500, 0)))
        showHud(if (sec > 0) "⏩ +$sec s" else "⏪ −${-sec} s")
    }

    /** [GESTO-2] Mensaje que se queda mientras el dedo sigue pulsado. */
    private fun showHudHold(text: String) {
        hud.text = text
        hud.visibility = View.VISIBLE
        handler.removeCallbacks(hideHud)
    }

    /** [GESTO-2] Velocidad 2x. Solo se activa con el video sonando (en pausa algunos teléfonos lo arrancan solo). */
    private fun setFast(on: Boolean) {
        val mp = mpRef ?: return
        val playing = try { video.isPlaying } catch (e: Exception) { false }
        if (on && !playing) return
        if (!on && !fastOn) return
        try {
            mp.playbackParams = PlaybackParams().setSpeed(if (on) fastVals[fastIdx()] else 1f)
            if (!on && !playing) video.pause()
            fastOn = on
        } catch (e: Exception) { }
    }

    /** [GESTO-2] Al levantar el dedo: aplica el avance, guarda el brillo o vuelve a 1x. */
    private fun endGesture(up: Boolean) {
        when (gMode) {
            3 -> {
                if (up) {
                    video.seekTo(gSeekTo)
                    showHud(fmt(gSeekTo))
                } else {
                    hud.visibility = View.GONE
                }
            }
            1 -> {
                val p = getSharedPreferences("castv", MODE_PRIVATE)
                if (p.getBoolean("video_bright_on", true)) p.edit().putFloat("video_bright", gBright).apply()
            }
            4 -> {
                setFast(false)
                showHud("▶ 1x")
            }
        }
        gMode = 0
    }

    private fun setupGestures(root: FrameLayout) {
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(0xAA000000.toInt())
                cornerRadius = dp(12).toFloat()
            }
            visibility = View.GONE
        }
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gMode = 0
                gDownX = e.x
                gDownY = e.y
                return true
            }

            // El VideoView ya no recibe el toque: mostrar/ocultar controles se hace aquí
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) {   // [UI-2a] solo muestra el 🔓 unos segundos
                    lockBtn.visibility = View.VISIBLE
                    handler.removeCallbacks(hideLockBtn)
                    handler.postDelayed(hideLockBtn, 2500)
                    return true
                }
                if (controlsShowing()) hideControls() else showControls()   // [UI-2b]
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true   // [UI-2a]
                if (!gOn("g_dtap")) return onSingleTapConfirmed(e)   // [GESTOS-AJ-1] sin doble toque: dos toques = uno
                if (inEdge(e.x, e.y)) return false
                val w = gw()
                if (e.x < w / 3f) seekBy(-dtapSec())
                else if (e.x > w * 2f / 3f) seekBy(dtapSec())
                else {   // [GESTO-2] centro: pausar / reanudar
                    togglePlay()
                    showHud(if (video.isPlaying) "▶ Reanudar" else "⏸ Pausa")
                }
                return true
            }

            // [GESTO-2] mantener pulsado: velocidad 2x hasta soltar
            override fun onLongPress(e: MotionEvent) {
                if (locked || gMode != 0 || inEdge(e.x, e.y) || !gOn("g_hold")) return   // [GESTOS-AJ-1]
                val d = try { video.duration } catch (ex: Exception) { -1 }
                if (d <= 0) return   // canal en vivo
                setFast(true)
                if (fastOn) {
                    gMode = 4
                    showHudHold("⏩ " + fastLabels[fastIdx()])
                }
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked) return true   // [UI-2a]
                if (gMode == -1 || gMode == 4) return true   // [GESTO-2] 4 = mantener pulsado
                val h = gh()
                val w = gw()
                if (h <= 0f || w <= 0f) return true
                if (gMode == 0) {
                    if (inEdge(gDownX, gDownY)) { gMode = -1; return true }
                    val tx = e2.x - gDownX
                    val ty = e2.y - gDownY
                    if (abs(tx) < dp(12) && abs(ty) < dp(12)) return true   // aún no se decide
                    if (abs(ty) <= abs(tx)) {   // [GESTO-2] deslizar horizontal: adelantar / retroceder
                        val d = try { video.duration } catch (ex: Exception) { -1 }
                        if (d <= 0 || !gOn("g_seek")) { gMode = -1; return true }   // canal en vivo / [GESTOS-AJ-1] desactivado
                        gMode = 3
                        gSeekStart = try { video.currentPosition } catch (ex: Exception) { 0 }
                        gSeekTo = gSeekStart
                    } else if (gDownX >= w / 2f) {
                        if (!gOn("g_bright")) { gMode = -1; return true }   // [GESTOS-AJ-1]
                        gMode = 1
                        gBright = currentBrightness()
                    } else {
                        if (!gOn("g_vol")) { gMode = -1; return true }   // [GESTOS-AJ-1]
                        gMode = 2
                        gVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
                if (gMode == 3) {   // [GESTO-2] la pantalla completa equivale a 90 segundos
                    val d = try { video.duration } catch (ex: Exception) { -1 }
                    gSeekTo = (gSeekStart + ((e2.x - gDownX) / w * 90000f).toInt()).coerceIn(0, maxOf(d - 500, 0))
                    val diff = (gSeekTo - gSeekStart) / 1000
                    showHud((if (diff >= 0) "⏩ +$diff s" else "⏪ −${-diff} s") + "\n" + fmt(gSeekTo))
                    return true
                }
                val frac = dy / (h * 0.8f)   // dy > 0: el dedo sube
                if (gMode == 1) {
                    gBright = (gBright + frac).coerceIn(0.01f, 1f)
                    val lp = window.attributes
                    lp.screenBrightness = gBright
                    window.attributes = lp
                    showHud("☀ ${(gBright * 100).toInt()} %")
                } else {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    gVol = (gVol + frac * max).coerceIn(0f, max.toFloat())
                    val v = Math.round(gVol)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                    showHud((if (v == 0) "🔇 " else "🔊 ") + "$v/$max")
                }
                return true
            }
        })
        // [GESTO-2] pellizco = zoom (se suma a los modos del 🖼); sin "zoom rápido" para no chocar con el doble toque
        scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (locked || !gOn("g_pinch")) return true   // [GESTOS-AJ-1]
                video.zoom = (video.zoom * d.scaleFactor).coerceIn(1f, 4f)
                video.requestLayout()
                showHud("🔍 " + String.format(Locale.US, "%.1f", video.zoom) + "x")
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                if (video.zoom < 1.05f) {
                    video.zoom = 1f
                    video.requestLayout()
                }
            }
        })
        scale.isQuickScaleEnabled = false
        video.setOnTouchListener { _, ev ->
            // con zoom, las coordenadas se pasan a las de la pantalla (la vista es más grande que ella)
            if (video.zoom > 1f) ev.offsetLocation(video.left.toFloat(), video.top.toFloat())
            when (ev.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> { endGesture(false); gMode = -1 }   // segundo dedo: pellizco
                MotionEvent.ACTION_UP -> endGesture(true)
                MotionEvent.ACTION_CANCEL -> endGesture(false)
            }
            scale.onTouchEvent(ev)
            gesture.onTouchEvent(ev)
            true
        }
    }

    // ------------------------------------------------------------ [UI-2b] Barra de control propia

    private fun accent(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.castvAccent, tv, true)
        return tv.data
    }

    private fun fmt(ms: Int): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%d:%02d", m, ss)
    }

    private fun controlsShowing(): Boolean = ::bottomBar.isInitialized && bottomBar.visibility == View.VISIBLE

    private fun keepControls() {
        hideAt = SystemClock.uptimeMillis() + hideControlsMs
    }

    private fun showControls() {
        if (locked) return
        backSkip.text = "−${btnSec()}"   // [GESTOS-AJ-1]
        fwdSkip.text = "+${btnSec()}"
        bottomBar.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        keepControls()
        updateProgress()
        updatePlayIcon()
        moveSubtitles(up = true)
    }

    private fun hideControls() {
        bottomBar.visibility = View.GONE
        topBar.visibility = View.GONE
        moveSubtitles(up = false)
    }

    /** Sube los subtítulos mientras se ve la barra, para que no queden tapados. */
    private fun moveSubtitles(up: Boolean) {
        val lp = subText.layoutParams as FrameLayout.LayoutParams
        lp.bottomMargin = if (up) dp(140) else dp(72)
        subText.layoutParams = lp
    }

    // ------------------------------------------------------------ [FOCO-1] Foco de audio

    @Suppress("DEPRECATION")
    private fun focusRequest() {
        pausedByFocus = false
        try {
            (getSystemService(AUDIO_SERVICE) as AudioManager)
                .requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun focusAbandon() {
        pausedByFocus = false
        try { (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocus(focusListener) } catch (e: Exception) { }
    }

    private fun onFocusChange(change: Int) {
        if (!::video.isInitialized) return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {   // otra app de música o video: pausa y no reanuda sola
                resumeOnReturn = false
                pausedByFocus = false
                try { if (video.isPlaying) video.pause() } catch (e: Exception) { }
                updatePlayIcon()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {   // llamada, alarma: pausa y reanuda al volver el foco
                try { if (video.isPlaying) { pausedByFocus = true; video.pause() } } catch (e: Exception) { }
                updatePlayIcon()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus) {
                    pausedByFocus = false
                    try { video.start() } catch (e: Exception) { }
                    updatePlayIcon()
                }
            }
        }
    }

    private fun togglePlay() {
        pausedByFocus = false   // [FOCO-1] si pausa o reanuda a mano, el foco ya no decide
        if (video.isPlaying) { video.pause(); focusAbandon() } else video.start()
        updatePlayIcon()
        keepControls()
    }

    private fun updatePlayIcon() {
        if (!::playBtn.isInitialized) return
        playBtn.setImageResource(if (video.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_w)
    }

    private fun updateProgress() {
        val dur = try { video.duration } catch (e: Exception) { -1 }
        val pos = try { video.currentPosition } catch (e: Exception) { 0 }
        if (dur > 0) {
            seek.isEnabled = true
            if (!dragging) {
                seek.progress = (pos.toLong() * 1000 / dur).toInt()
                tvPos.text = fmt(pos)
            }
            tvDur.text = fmt(dur)
        } else {   // canal en vivo: sin barra de avance
            seek.isEnabled = false
            seek.progress = 0
            tvPos.text = fmt(pos)
            tvDur.text = "EN VIVO"
        }
    }

    private fun skipBtn(onClick: () -> Unit): TextView = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick(); keepControls() }
    }

    private fun iconBtn(res: Int, onClick: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick(); keepControls() }
    }

    private fun buildControls(root: FrameLayout) {
        val acc = accent()
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xCC000000.toInt()))
            isClickable = true   // los toques sobre la barra no pasan al video
            visibility = View.GONE
        }
        // Fila 1: tiempo actual · barra · duración
        val timeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tvPos = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "0:00" }
        tvDur = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "0:00" }
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(acc)
            thumbTintList = ColorStateList.valueOf(acc)
            progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val dur = try { video.duration } catch (e: Exception) { -1 }
                    if (dur > 0) tvPos.text = fmt((dur.toLong() * p / 1000).toInt())
                    keepControls()
                }
                override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    dragging = false
                    val dur = try { video.duration } catch (e: Exception) { -1 }
                    if (dur > 0) video.seekTo((dur.toLong() * sb.progress / 1000).toInt())
                    keepControls()
                }
            })
        }
        timeRow.addView(tvPos)
        timeRow.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(8); rightMargin = dp(8)
        })
        timeRow.addView(tvDur)
        bottomBar.addView(timeRow)

        // Fila 2: anterior · −N · play/pausa · +N · siguiente
        val btnRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        btnRow.addView(iconBtn(R.drawable.ic_prev) { playAt(index - 1) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        backSkip = skipBtn { seekBy(-btnSec()) }   // [GESTOS-AJ-1]
        btnRow.addView(backSkip)
        playBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_play_w)
            setColorFilter(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(acc) }
            setOnClickListener { togglePlay() }
        }
        btnRow.addView(playBtn, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            leftMargin = dp(8); rightMargin = dp(8)
        })
        fwdSkip = skipBtn { seekBy(btnSec()) }   // [GESTOS-AJ-1]
        btnRow.addView(fwdSkip)
        btnRow.addView(iconBtn(R.drawable.ic_next) { playAt(index + 1) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bottomBar.addView(btnRow)

        root.addView(bottomBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    // Teclas multimedia (audífonos, teclado): el VideoView solo las atendía con MediaController
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_SPACE -> {
                if (!locked) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!locked && !video.isPlaying) togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (!locked && video.isPlaying) togglePlay(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------ [UI-2a] Bloqueo y giro

    private fun lock() {
        locked = true
        hideControls()
        hud.visibility = View.GONE
        backLock.isEnabled = true
        toast("🔒 Controles bloqueados · toca la pantalla para ver 🔓")
    }

    private fun unlock() {
        locked = false
        handler.removeCallbacks(hideLockBtn)
        lockBtn.visibility = View.GONE
        backLock.isEnabled = false
        toast("🔓 Controles desbloqueados")
        showControls()
    }

    private fun rotate() {
        rotMode = (rotMode + 1) % 3
        when (rotMode) {
            1 -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; toast("↔ Horizontal") }
            2 -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT; toast("↕ Vertical") }
            else -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR; toast("🔄 Giro automático") }
        }
    }

    // ------------------------------------------------------------ [GESTO-2] Temporizador de apagado

    private fun sleepLeftMin(): Long =
        ((sleepAt - SystemClock.uptimeMillis() + 59_999L) / 60_000L).coerceAtLeast(1L)

    private fun showSleepMenu() {
        val labels = arrayOf(
            "Desactivado", "En 15 minutos", "En 30 minutos", "En 45 minutos", "En 60 minutos",
            "Al terminar este video")
        val cur = if (sleepEnd) 5 else if (sleepAt > 0L) -1 else 0
        AlertDialog.Builder(this)
            .setTitle(if (sleepAt > 0L) "😴 Se detiene en ${sleepLeftMin()} min" else "😴 Temporizador de apagado")
            .setSingleChoiceItems(labels, cur) { d, which ->
                d.dismiss()
                setSleep(which)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun setSleep(which: Int) {
        sleepEnd = false
        sleepAt = 0L
        when (which) {
            0 -> toast("😴 Temporizador desactivado")
            5 -> {
                sleepEnd = true
                toast("😴 Se detendrá al terminar este video")
            }
            else -> {
                val min = which * 15
                sleepAt = SystemClock.uptimeMillis() + min * 60_000L
                toast("😴 Se detendrá en $min minutos")
            }
        }
        updateSleepChip()
        keepControls()
    }

    private fun updateSleepChip() {
        if (!::sleepChip.isInitialized) return
        val t = when {
            sleepEnd -> "😴 fin"
            sleepAt > 0L -> "😴 ${sleepLeftMin()}m"
            else -> "😴"
        }
        if (sleepChip.text.toString() != t) sleepChip.text = t
    }

    /** Se acabó el tiempo: se cierra el video (onPause guarda el punto de reanudación). */
    private fun sleepNow() {
        sleepAt = 0L
        sleepEnd = false
        toast("😴 Temporizador: reproducción detenida")
        finish()
    }

    // ------------------------------------------------------------ [UI-2c] Tamaño de pantalla

    private fun cycleSize() {
        sizeMode = (sizeMode + 1) % 5
        video.sizeMode = sizeMode
        video.requestLayout()
        toast(when (sizeMode) {
            1 -> "🖼 Llenar (recorta los bordes)"
            2 -> "🖼 Estirar"
            3 -> "🖼 16:9"
            4 -> "🖼 4:3"
            else -> "🖼 Ajustar"
        })
    }

    /** VideoView que además puede llenar, estirar o forzar una proporción. Modo 0 = comportamiento de siempre. */
    private class SizedVideoView(ctx: android.content.Context) : VideoView(ctx) {
        var sizeMode = 0
        var zoom = 1f   // [GESTO-2] pellizco; se suma al modo de tamaño
        var onStartHook: (() -> Unit)? = null   // [FOCO-1] se llama antes de cada start()
        private var vw = 0
        private var vh = 0

        fun setVideoDims(w: Int, h: Int) {
            vw = w; vh = h
            requestLayout()
        }

        override fun start() {   // [FOCO-1]
            try { onStartHook?.invoke() } catch (e: Exception) { }
            super.start()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if ((sizeMode == 0 && zoom == 1f) || vw <= 0 || vh <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val pw = View.MeasureSpec.getSize(widthMeasureSpec)
            val ph = View.MeasureSpec.getSize(heightMeasureSpec)
            var w = pw
            var h = ph
            when (sizeMode) {
                0 -> {   // ajustar (solo se llega aquí con zoom)
                    val sc = minOf(pw.toFloat() / vw, ph.toFloat() / vh)
                    w = (vw * sc).toInt()
                    h = (vh * sc).toInt()
                }
                1 -> {   // llenar: escala hasta cubrir todo y recorta lo que sobre
                    val sc = maxOf(pw.toFloat() / vw, ph.toFloat() / vh)
                    w = (vw * sc).toInt()
                    h = (vh * sc).toInt()
                }
                3, 4 -> {   // proporción fija dentro de la pantalla
                    val ar = if (sizeMode == 3) 16f / 9f else 4f / 3f
                    if (pw.toFloat() / ph > ar) { h = ph; w = (ph * ar).toInt() } else { w = pw; h = (pw / ar).toInt() }
                }
            }
            if (zoom != 1f) {   // [GESTO-2]
                w = (w * zoom).toInt()
                h = (h * zoom).toInt()
            }
            setMeasuredDimension(w, h)
        }
    }

    private fun chip(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))   // [SALTO-1] un poco más compactos: ahora caben 7 chips
        background = GradientDrawable().apply {
            setColor(0x55FFFFFF)
            cornerRadius = dp(18).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(4)
        }
    }

    private fun uriFor(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

    // ------------------------------------------------------------ Reproducción

    private fun playAt(i: Int, startAt: Int = 0, saveCurrent: Boolean = true) {
        if (i !in items.indices) return
        if (saveCurrent) saveProgress()
        flushWatch(); watchAccMs = 0L; watchLastMs = 0L   // [TIEMPO-1] lo visto va al video que termina
        if (SubtitleGen.running) { aiCancel = true; SubtitleGen.requestCancel() }   // [IA-3] cambia de video
        index = i
        val it = items[i]
        pendingStart = startAt
        subs = emptyList()
        subOffset = 0
        shownCue = null
        subText.visibility = View.GONE
        titleView.text = it.name.substringBeforeLast('.')
        mpRef = null   // [GESTO-2]
        fastOn = false
        abA = -1   // [AB-1]
        abB = -1
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
        if (dur > 0 && pos >= dur * 9 / 10 && !Store.incognito) Store.markSeen(cur, true)   // [VISTO-1] casi terminado = visto
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
        subOffset = subPrefs().getInt(offKey(m), 0)   // [SUB-DELAY-1] retraso recordado por video
        val u = subPrefs().getString(subKey(m), null) ?: return
        loadSub(Uri.parse(u), false)
    }

    // [SUB-DELAY-1] retraso de subtítulos recordado por video
    private fun offKey(m: MediaItem) = "d|" + Store.key(m)

    private fun saveOffset() {
        val cur = items.getOrNull(index) ?: return
        if (cur.id == 0L) return   // los enlaces no recuerdan nada
        if (subOffset == 0) subPrefs().edit().remove(offKey(cur)).apply()
        else subPrefs().edit().putInt(offKey(cur), subOffset).apply()
    }

    private fun offText(): String {
        val sign = if (subOffset > 0) "+" else ""
        return (sign + (subOffset / 1000.0).toString() + " s").replace('.', ',')
    }

    private fun changeOffset(deltaMs: Int, reset: Boolean = false) {
        subOffset = if (reset) 0 else subOffset + deltaMs
        saveOffset()
        shownCue = null
        updateSub()
    }

    private fun showDelayDialog() {
        val d = AlertDialog.Builder(this)
            .setTitle("Retraso de subtítulos")
            .setMessage("Actual: " + offText() + "\n(+ = salen más tarde, − = más temprano)")
            .setNegativeButton("−0,1 s", null)
            .setPositiveButton("+0,1 s", null)
            .setNeutralButton("Reiniciar", null)
            .create()
        d.show()
        // Los botones no cierran el diálogo, para poder tocar varias veces
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            changeOffset(-100); d.setMessage("Actual: " + offText() + "\n(+ = salen más tarde, − = más temprano)")
        }
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            changeOffset(100); d.setMessage("Actual: " + offText() + "\n(+ = salen más tarde, − = más temprano)")
        }
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            changeOffset(0, reset = true); d.setMessage("Actual: " + offText() + "\n(+ = salen más tarde, − = más temprano)")
        }
    }

    private fun showSubtitleMenu() {
        val labels = arrayOf(
            "📂 Elegir archivo (.srt / .vtt)…",
            "⏱ Retraso de subtítulos…",
            "🚫 Quitar subtítulos",
            "🎨 Estilo del subtítulo…",
            "✨ Generar subtítulos con IA…")
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
                    1 -> showDelayDialog()
                    2 -> {
                        subs = emptyList()
                        shownCue = null
                        subText.visibility = View.GONE
                        subPrefs().edit().remove(subKey(items[index])).apply()
                        subOffset = 0
                        saveOffset()
                        toast("Subtítulos quitados")
                    }
                    3 -> { previewSub(); showStyleMenu() }
                    4 -> startAiSubs()   // [IA-3]
                }
            }
            .show()
    }

    // ------------------------------------------------------------ [IA-3] Subtítulos con IA

    private fun startAiSubs() {
        val cur = items.getOrNull(index) ?: return
        if (cur.id == 0L) { toast("Solo videos del teléfono"); return }
        if (SubtitleGen.running) { toast("Ya hay una generación en curso"); return }
        if (subs.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Este video ya tiene subtítulos")
                .setMessage("¿Reemplazarlos por los generados con IA?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Reemplazar") { _, _ -> aiAskModel(cur) }
                .show()
        } else aiAskModel(cur)
    }

    private fun aiAskModel(cur: MediaItem) {
        if (WhisperModel.isReady(this)) { aiRun(cur); return }
        AlertDialog.Builder(this)
            .setTitle("Falta el modelo de voz")
            .setMessage("Hay que bajar el modelo de Whisper (${WhisperModel.selected(this).sizeText}, una sola vez). Conviene estar con Wi-Fi.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Bajar y generar") { _, _ -> aiRun(cur) }
            .show()
    }

    private fun aiRun(cur: MediaItem) {
        if (!SubtitleGen.tryStart()) { toast("Ya hay una generación en curso"); return }
        val idx = index
        val app = applicationContext
        aiCancel = false
        val dlg = AlertDialog.Builder(this)
            .setTitle("✨ Subtítulos con IA")
            .setMessage("Preparando…")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            aiCancel = true
            SubtitleGen.requestCancel()
            dlg.setMessage("Cancelando…")
        }
        aiDialog = dlg
        val srt = File(File(filesDir, "subs"), "ia_" + Integer.toHexString(Store.key(cur).hashCode()) + ".srt")
        thread {
            var res: SubtitleGen.Out? = null
            try {
                if (!WhisperModel.isReady(app)) {
                    val d = WhisperModel.download(app, { pct, mb ->
                        val t = if (pct >= 0) "Bajando el modelo… $pct %" else "Bajando el modelo… $mb MB"
                        SubtitleGen.notifyProgress(app, if (pct >= 0) pct else 0, t)
                        runOnUiThread { if (!isFinishing && aiDialog === dlg) dlg.setMessage(t) }
                    }, { aiCancel })
                    if (!d.ok) res = SubtitleGen.Out(false, d.message, cancelled = d.cancelled)
                }
                if (res == null) {
                    res = SubtitleGen.run(app, Uri.parse(cur.uriString), srt) { pct, text ->
                        val t = "$text ($pct %)"
                        SubtitleGen.notifyProgress(app, pct, text)
                        runOnUiThread { if (!isFinishing && aiDialog === dlg) dlg.setMessage(t) }
                    }
                }
            } catch (e: Throwable) {
                res = SubtitleGen.Out(false, "Error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                SubtitleGen.finish()
            }
            val out = res ?: SubtitleGen.Out(false, "Sin resultado")
            val msg = when {
                out.cancelled -> null
                out.ok -> "Subtítulos listos (${out.segments} frases)"
                else -> "No se pudieron generar: ${out.message}"
            }
            SubtitleGen.notifyDone(app, msg)
            runOnUiThread {
                try { if (aiDialog === dlg) dlg.dismiss() } catch (e: Exception) { }
                if (aiDialog === dlg) aiDialog = null
                if (isFinishing || isDestroyed) {
                    try { srt.delete() } catch (e: Exception) { }
                    return@runOnUiThread
                }
                if (out.cancelled) {
                    toast("Generación cancelada")
                } else if (!out.ok) {
                    AlertDialog.Builder(this)
                        .setTitle("✨ Subtítulos con IA")
                        .setMessage(out.message)
                        .setPositiveButton("OK", null)
                        .show()
                } else if (idx != index) {
                    try { srt.delete() } catch (e: Exception) { }   // cambió de video: no se aplica
                } else {
                    val min = out.audioSec / 60.0
                    toast("✨ ${out.segments} frases (" + String.format(Locale.US, "%.1f", min) + " min de audio)")
                    loadSub(Uri.fromFile(srt), true)
                }
            }
        }
    }

    // ------------------------------------------------------------ [UI-S1] Estilo de subtítulos

    private val sizeNames = arrayOf("Pequeño", "Mediano", "Grande", "Muy grande")
    private val sizeSp = floatArrayOf(14f, 18f, 24f, 30f)       // Mediano = 18 (el de siempre)
    private val colorNames = arrayOf("Blanco", "Amarillo", "Cian", "Verde")
    private val colorVals = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFF69F0AE.toInt())
    private val bgNames = arrayOf("Semitransparente", "Opaco", "Sin fondo")
    private val bgVals = intArrayOf(0xAA000000.toInt(), 0xFF000000.toInt(), 0x00000000)

    private fun stSize() = subPrefs().getInt("style_size", 1).coerceIn(0, sizeNames.size - 1)
    private fun stColor() = subPrefs().getInt("style_color", 0).coerceIn(0, colorNames.size - 1)
    private fun stBg() = subPrefs().getInt("style_bg", 0).coerceIn(0, bgNames.size - 1)
    private fun stBold() = subPrefs().getBoolean("style_bold", false)

    private fun applySubStyle() {
        subText.textSize = sizeSp[stSize()]
        subText.setTextColor(colorVals[stColor()])
        subText.setTypeface(Typeface.DEFAULT, if (stBold()) Typeface.BOLD else Typeface.NORMAL)
        val bg = bgVals[stBg()]
        subText.background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(6).toFloat()
        }
        // Sin fondo: una sombra negra mantiene el texto legible
        if (stBg() == 2) subText.setShadowLayer(6f, 2f, 2f, Color.BLACK)
        else subText.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun previewSub() {
        previewing = true
        subText.text = "Así se verán los subtítulos"
        subText.visibility = View.VISIBLE
        handler.removeCallbacks(hidePreview)
        handler.postDelayed(hidePreview, 3000)
    }

    private fun styleChanged() {
        applySubStyle()
        previewSub()
        showStyleMenu()
    }

    private fun pickStyle(title: String, names: Array<String>, current: Int, key: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names, current) { d, which ->
                subPrefs().edit().putInt(key, which).apply()
                d.dismiss()
                styleChanged()
            }
            .show()
    }

    private fun showStyleMenu() {
        val labels = arrayOf(
            "🔠 Tamaño: ${sizeNames[stSize()]}",
            "🅱 Negrita: ${if (stBold()) "sí" else "no"}",
            "🎨 Color del texto: ${colorNames[stColor()]}",
            "◼ Fondo: ${bgNames[stBg()]}",
            "↩ Restablecer estilo")
        AlertDialog.Builder(this)
            .setTitle("Estilo del subtítulo")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> pickStyle("Tamaño", sizeNames, stSize(), "style_size")
                    1 -> {
                        subPrefs().edit().putBoolean("style_bold", !stBold()).apply()
                        styleChanged()
                    }
                    2 -> pickStyle("Color del texto", colorNames, stColor(), "style_color")
                    3 -> pickStyle("Fondo", bgNames, stBg(), "style_bg")
                    4 -> {
                        subPrefs().edit().remove("style_size").remove("style_bold")
                            .remove("style_color").remove("style_bg").apply()
                        styleChanged()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
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
        if (previewing) return
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

    // ------------------------------------------------------------ [SALTO-1] Herramientas del video

    private fun curPos(): Int = try { video.currentPosition } catch (e: Exception) { 0 }

    private fun showToolsMenu() {
        val nMarks = items.getOrNull(index)?.let { if (it.id != 0L) Store.marks(it).size else 0 } ?: 0   // [MARCAS-1]
        val labels = arrayOf(
            "⏱ Saltar a un tiempo…",
            "🔁 Marcar A (inicio) aquí",
            "🔁 Marcar B (fin) aquí",
            "❌ Quitar repetición A-B",
            "🔊 Volumen extra: " + boostPct() + " %",
            "ℹ️ Información del video",
            "🪟 Ventana flotante (PiP)",
            "🔖 Marcadores (" + nMarks + ")…")   // [MARCAS-1]
        AlertDialog.Builder(this)
            .setTitle("Herramientas · " + abText())
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> showJumpDialog()
                    1 -> markA()
                    2 -> markB()
                    3 -> clearAb()
                    4 -> showBoostDialog()
                    5 -> showInfo()
                    6 -> if (!enterPip()) toast("Este teléfono no permite la ventana flotante")
                    7 -> showMarks()   // [MARCAS-1]
                }
            }
            .show()
    }

    // [INFO-1] información del video (versión sencilla de "estadísticas")
    private fun showInfo() {
        flushWatch()   // [TIEMPO-1] incluye lo que va de esta sesión
        val mp = mpRef
        val w = try { mp?.videoWidth ?: 0 } catch (e: Exception) { 0 }
        val h = try { mp?.videoHeight ?: 0 } catch (e: Exception) { 0 }
        val dur = try { video.duration } catch (e: Exception) { -1 }
        val cur = items.getOrNull(index)
        val local = cur != null && cur.id != 0L
        // [STATS-2] tamaño del archivo, tasa de bits aproximada y estadísticas de uso (solo videos del teléfono)
        val size = if (local) fileSize(cur!!.id) else -1L
        var txt = "Archivo: " + (cur?.name ?: "?") +
            "\nResolución: " + (if (w > 0 && h > 0) "$w × $h" else "?") +
            "\nDuración: " + (if (dur > 0) fmt(dur) else "?") +
            "\nPosición: " + fmt(curPos())
        if (size > 0) {
            txt += "\nTamaño: " + fmtSize(size)
            if (dur > 0) txt += "\nTasa de bits aprox.: " +
                String.format(Locale.US, "%.1f Mb/s", size * 8.0 / (dur / 1000.0) / 1_000_000.0)
        }
        txt += "\nVolumen extra: " + boostPct() + " %" +
            "\nRetraso de subtítulos: " + offText() +
            "\nRepetición: " + abText()
        if (local) {
            val last = Store.lastPlayed(cur!!)
            txt += "\nVeces reproducido: " + Store.playCount(cur) +
                "\nÚltima vez: " + (if (last > 0L)
                    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(java.util.Date(last))
                    else "—") +
                "\nTiempo total visto: " + fmtWatch(Store.watchSec(cur)) +
                "\nVisto: " + (if (Store.isSeen(cur)) "Sí" else "No")
        }
        AlertDialog.Builder(this)
            .setTitle("ℹ️ Información del video")
            .setMessage(txt)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    /** [STATS-2] Tamaño en bytes de un video del teléfono, o -1 si no se pudo leer. */
    private fun fileSize(id: Long): Long = try {
        contentResolver.query(uriFor(id), arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
    } catch (e: Exception) { -1L }

    private fun fmtWatch(sec: Int): String = when {   // [TIEMPO-1]
        sec >= 3600 -> String.format(Locale.US, "%d h %02d min", sec / 3600, (sec % 3600) / 60)
        sec >= 60 -> String.format(Locale.US, "%d min %02d s", sec / 60, sec % 60)
        else -> "$sec s"
    }

    private fun fmtSize(b: Long): String =
        if (b >= 1_073_741_824L) String.format(Locale.US, "%.2f GB", b / 1_073_741_824.0)
        else String.format(Locale.US, "%.1f MB", b / 1_048_576.0)

    // [VOL-1] volumen extra con LoudnessEnhancer (100 / 150 / 200 / 300 %)
    private val boostVals = intArrayOf(100, 150, 200, 300)

    private fun boostPct(): Int =
        getSharedPreferences("castv", MODE_PRIVATE).getInt("vboost", 100).let { if (it in boostVals) it else 100 }

    private fun applyBoost(mp: MediaPlayer?) {
        try { enhancer?.release() } catch (e: Exception) { }
        enhancer = null
        val pct = boostPct()
        if (pct <= 100 || mp == null) return
        try {
            val e = LoudnessEnhancer(mp.audioSessionId)
            e.setTargetGain((2000 * Math.log10(pct / 100.0)).toInt())
            e.setEnabled(true)
            enhancer = e
        } catch (ex: Exception) {
            toast("Este teléfono no permite subir más el volumen")
        }
    }

    private fun showBoostDialog() {
        val labels = Array(boostVals.size) { i -> if (boostVals[i] == 100) "100 % (normal)" else boostVals[i].toString() + " %" }
        AlertDialog.Builder(this)
            .setTitle("🔊 Volumen extra (puede distorsionar)")
            .setSingleChoiceItems(labels, boostVals.indexOf(boostPct())) { d, which ->
                d.dismiss()
                getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("vboost", boostVals[which]).apply()
                applyBoost(mpRef)
                showHud("🔊 Volumen extra: " + boostVals[which] + " %")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // [AB-1] repetición A-B: al llegar a B vuelve a A
    private fun abLoop() {
        if (abA < 0 || abB <= abA || !::video.isInitialized) return
        val pos = try { video.currentPosition } catch (e: Exception) { return }
        if (pos >= abB) video.seekTo(abA)
    }

    private fun abText(): String = when {
        abA < 0 -> "A-B sin marcar"
        abB < 0 -> "A: " + fmt(abA) + " (falta B)"
        else -> "A: " + fmt(abA) + "  B: " + fmt(abB)
    }

    private fun markA() {
        abA = curPos()
        if (abB <= abA) abB = -1
        showHud("🔁 A: " + fmt(abA))
    }

    private fun markB() {
        if (abA < 0) { toast("Marca primero A"); return }
        val p = curPos()
        if (p <= abA + 500) { toast("B debe ir después de A"); return }
        abB = p
        showHud("🔁 " + fmt(abA) + " → " + fmt(abB))
    }

    private fun clearAb() {
        abA = -1
        abB = -1
        showHud("🔁 Repetición A-B quitada")
    }

    /** Acepta "90", "12:30" o "1:05:30". Devuelve milisegundos o null si no se entiende. */
    private fun parseTime(s: String): Int? {
        val parts = s.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        var total = 0L
        for (p in parts) {
            val n = p.trim().toLongOrNull() ?: return null
            if (n < 0) return null
            total = total * 60 + n
        }
        return if (total > 172800L) null else (total * 1000).toInt()
    }

    private fun showJumpDialog() {
        val dur = try { video.duration } catch (e: Exception) { -1 }
        val input = EditText(this).apply {
            hint = "Ej.: 12:30"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            setSingleLine(true)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Saltar a… (dura " + (if (dur > 0) fmt(dur) else "?") + ")")
            .setView(box)
            .setPositiveButton("Ir") { _, _ ->
                val ms = parseTime(input.text.toString())
                if (ms == null) {
                    toast("Escribe el tiempo como 12:30 o 1:05:00")
                } else {
                    val d2 = try { video.duration } catch (e: Exception) { -1 }
                    val target = if (d2 > 0) ms.coerceIn(0, maxOf(d2 - 500, 0)) else ms
                    video.seekTo(target)
                    showHud("⏩ " + fmt(target))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ [CONFIRMAR-1] Confirmar antes de reanudar

    private fun askResume(sec: Int) {
        val d = AlertDialog.Builder(this)
            .setTitle("Continuar")
            .setMessage("¿Continuar desde " + fmt(sec * 1000) + "?")
            .setPositiveButton("Continuar") { _, _ ->
                video.seekTo(sec * 1000)
                video.start()
                updatePlayIcon()
            }
            .setNegativeButton("Desde el inicio") { _, _ ->
                video.start()
                updatePlayIcon()
            }
            .setOnCancelListener { finish() }   // Atrás en la pregunta: se sale del reproductor
            .show()
        d.setCanceledOnTouchOutside(false)
    }

    // ------------------------------------------------------------ [MARCAS-1] Marcadores

    private fun showMarks() {
        val cur = items.getOrNull(index) ?: return
        if (cur.id == 0L) { toast("Los marcadores solo sirven en videos del teléfono"); return }
        val list = Store.marks(cur)
        val labels = ArrayList<String>()
        labels.add("➕ Añadir marcador aquí (" + fmt(curPos()) + ")")
        for (m in list) labels.add("🔖 " + fmt(m.sec * 1000) + (if (m.name.isNotBlank()) " · " + m.name else ""))
        AlertDialog.Builder(this)
            .setTitle("🔖 Marcadores")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) askMarkName(cur) else markActions(cur, list[which - 1])
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun askMarkName(cur: MediaItem) {
        val sec = curPos() / 1000
        val input = EditText(this).apply {
            hint = "Nombre (opcional)"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Marcador en " + fmt(sec * 1000))
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                if (Store.addMark(cur, sec, input.text.toString().trim())) showHud("🔖 Marcador en " + fmt(sec * 1000))
                else toast(if (Store.incognito) "🕵️‍♂️ Incógnito: no se guardan marcadores"
                           else "Ya hay demasiados marcadores en este video (máximo 50)")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun markActions(cur: MediaItem, m: VideoMark) {
        AlertDialog.Builder(this)
            .setTitle("🔖 " + fmt(m.sec * 1000) + (if (m.name.isNotBlank()) " · " + m.name else ""))
            .setItems(arrayOf("▶ Saltar aquí", "🗑 Borrar marcador")) { _, which ->
                if (which == 0) {
                    video.seekTo(m.sec * 1000)
                    showHud("⏩ " + fmt(m.sec * 1000))
                } else {
                    Store.removeMark(cur, m.sec)
                    toast("🗑 Marcador borrado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ Ciclo de vida

    // [PIP-1] ventana flotante (picture-in-picture)
    private var inPip = false
    private var pipEntering = false

    private fun enterPip(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !::video.isInitialized) return false
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return try {
            val mp = mpRef
            var w = try { mp?.videoWidth ?: 0 } catch (e: Exception) { 0 }
            var h = try { mp?.videoHeight ?: 0 } catch (e: Exception) { 0 }
            if (w <= 0 || h <= 0) { w = 16; h = 9 }
            val ratio = (w.toFloat() / h).coerceIn(0.5f, 2.3f)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                .build()
            pipEntering = true
            enterPictureInPictureMode(params)
            true
        } catch (e: Exception) {
            pipEntering = false
            false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val playing = try { video.isPlaying } catch (e: Exception) { false }
        // [PIP-2] se puede apagar en Ajustes › Vídeo (pip_auto; por defecto Sí, como antes)
        val auto = getSharedPreferences("castv", MODE_PRIVATE).getBoolean("pip_auto", true)
        if (playing && !locked && auto) enterPip()   // al salir con Inicio, el video sigue en una ventana flotante
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        pipEntering = false
        if (inPip) {
            hideControls()
            if (::hud.isInitialized) hud.visibility = View.GONE
        }
    }

    override fun onStart() {   // [FOCO-2]
        super.onStart()
        if (!noisyRegistered) {
            try {
                registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                noisyRegistered = true
            } catch (e: Exception) { }
        }
    }

    override fun onStop() {
        // Si se cierra la ventana flotante (o se apaga la pantalla), se detiene el sonido
        if (inPip && ::video.isInitialized) { try { video.pause() } catch (e: Exception) { } }
        if (noisyRegistered) {   // [FOCO-2]
            try { unregisterReceiver(noisyReceiver) } catch (e: Exception) { }
            noisyRegistered = false
        }
        super.onStop()
    }

    override fun onPause() {
        flushWatch()   // [TIEMPO-1]
        saveProgress()
        if (::video.isInitialized) {
            if (fastOn) setFast(false)   // [GESTO-2] vuelve a 1x antes de pausar
            resumeOnReturn = try { video.isPlaying } catch (e: Exception) { false }
            if (!(pipEntering || inPip)) {   // [PIP-1] en la ventana flotante sigue reproduciendo
                try { video.pause() } catch (e: Exception) { }
            }
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
        flushWatch()   // [TIEMPO-1]
        if (SubtitleGen.running) { aiCancel = true; SubtitleGen.requestCancel() }   // [IA-3]
        try { aiDialog?.dismiss() } catch (e: Exception) { }
        aiDialog = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(hideHud)
        handler.removeCallbacks(hidePreview)
        handler.removeCallbacks(hideLockBtn)   // [UI-2a]
        try { enhancer?.release() } catch (e: Exception) { }   // [VOL-1]
        enhancer = null
        if (::video.isInitialized) {
            try { video.stopPlayback() } catch (e: Exception) { }
        }
        focusAbandon()   // [FOCO-1]
        super.onDestroy()
    }
}
