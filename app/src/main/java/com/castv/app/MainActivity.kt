package com.castv.app

import android.Manifest
import android.app.NotificationManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.app.RecoverableSecurityException
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.content.res.ColorStateList
import android.net.Uri
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.view.KeyEvent
import android.widget.AbsListView
import android.widget.PopupMenu
import android.os.PowerManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var listFiles: ListView
    private lateinit var adapter: FileAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnAuto: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var volSeek: SeekBar

    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: TextView
    private lateinit var btnSearch: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var searchBar: View
    private lateinit var tabAll: TextView
    private lateinit var tabFavs: TextView
    private lateinit var tabResume: TextView
    private lateinit var navVideo: TextView
    private lateinit var navAudio: TextView
    private lateinit var navFolders: TextView
    private lateinit var navQueue: TextView
    private lateinit var navMore: TextView   // [UI-1]
    private lateinit var btnQuick: ImageButton
    private lateinit var btnExpand: ImageButton
    private lateinit var playerExpanded: View
    private var lastQuickPlaying: Boolean? = null
    private lateinit var btnNowFav: Button
    private lateinit var btnOutput: ImageButton
    private lateinit var nowThumbBox: FrameLayout
    private lateinit var nowIcon: ImageView
    private lateinit var nowThumb: ImageView
    private lateinit var tvFilter: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var fab: View
    private lateinit var footerView: View
    private var footerAttached = true
    private var fabShown = true

    private enum class Sort(val label: String) {
        NAME_ASC("↕ A-Z"), NAME_DESC("↕ Z-A"), NEWEST("↕ Recientes"),
        LONGEST("↕ Más largos"), SHORTEST("↕ Más cortos"),
        MOST_PLAYED("↕ Más reproducidas"), RECENT_PLAYED("↕ Reproducidas hace poco")
    }

    private enum class Repeat { OFF, ALL, ONE }

    private enum class ViewMode(val label: String) {
        ALL("Ver: Todos"), FAVS("Ver: ⭐ Favoritos"), RESUME("Ver: ⏩ Continuar")
    }

    // Salida de la música: false = TV, true = el propio teléfono (los videos siempre van al TV)
    private var outPhone = false
    private var localPlayer: MediaPlayer? = null
    @Volatile private var localActive = false
    @Volatile private var localPrepared = false
    private var pausedByFocus = false
    private lateinit var audioManager: AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (localActive && localPrepared && localPlayer?.isPlaying == true) {
                    pausedByFocus = true
                    try { localPlayer?.pause() } catch (e: Exception) { }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus && localActive) {
                    pausedByFocus = false
                    try { localPlayer?.start() } catch (e: Exception) { }
                }
            }
        }
    }

    // Controles de la notificación
    companion object {
        const val ACT_PREV = "com.castv.app.ACT_PREV"
        const val ACT_TOGGLE = "com.castv.app.ACT_TOGGLE"
        const val ACT_NEXT = "com.castv.app.ACT_NEXT"
        const val CHANNEL_ID = "castv_channel"
        const val FB_URL = "https://www.facebook.com/share/19x6GMsWUM/"
        const val IG_URL = "https://www.instagram.com/arieldejesusbuen"
        const val TT_URL = "https://www.tiktok.com/@arieldejesusbueno0"
    }
    @Volatile private var tvPlaying = false
    private var lastNotifKey = ""
    private var notifReceiverRegistered = false
    private var mediaSession: MediaSessionCompat? = null

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACT_PREV -> step(-1)
                ACT_NEXT -> step(+1)
                ACT_TOGGLE -> togglePlayPause()
            }
        }
    }

    private var viewMode = ViewMode.ALL
    private var dupIds: Set<Long> = emptySet()               // copias repetidas (se conserva la más antigua)
    private val queue = ArrayList<MediaItem>()               // cola: suena antes de seguir con la lista
    private var playingFromQueue = false
    @Volatile private var pendingResume = 0                  // segundo al que saltar cuando el TV ya reproduce
    @Volatile private var resumeTries = 0
    private var allItems: List<MediaItem> = emptyList()    // todo lo escaneado de la pestaña
    private var sortMode = Sort.NAME_ASC
    private var selectedFolder: String? = null             // null = todas las carpetas
    private var query = ""
    private var currentList: List<MediaItem> = emptyList() // lo que se ve en pantalla
    private var playlist: List<MediaItem> = emptyList()    // lista con la que se avanza
    private var playIndex = -1
    private var unsupportedSkips = 0
    @Volatile private var playingItem: MediaItem? = null
    @Volatile private var linkOnTv = false   // [PAUSA-1] hay un enlace de video sonando en el TV (playingItem sigue en null)
    @Volatile private var linkDurSec = 0     // [PROG-1d] duración del enlace en el TV, leída por el teléfono (el TV siempre manda 0)
    @Volatile private var linkGen = 0        // [PROG-1d] número del enlace actual, para descartar lecturas viejas
    private var showingMusic = false
    private var repeatMode = Repeat.OFF
    private var selectedArtist: String? = null
    private var selectedAlbum: String? = null
    private var selectedPlaylist: String? = null
    @Volatile private var sleepAtMs = 0L
    @Volatile private var sleepAfterTrack = false
    private var speed = 1.0f
    private var speedTouched = false
    private val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels = arrayOf("0.5x", "0.75x", "1x (normal)", "1.25x", "1.5x", "1.75x", "2x")
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var eqPreset = -1
    private var bassOn = false
    private var eqNames: List<String> = emptyList()
    @Volatile private var tvFails = 0
    @Volatile private var tvOnline = false
    private val nameKeyCache = HashMap<Long, String>()
    private var userSeeking = false
    private var volTouching = false
    private var muted = false
    @Volatile private var lastPos = 0
    @Volatile private var lastDur = 0

    @Volatile private var manualStop = false
    @Volatile private var wasPlaying = false
    @Volatile private var playStartedAt = 0L
    @Volatile private var monitoring = false

    private val requiredPermissions by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // ---------------------------------------------------------------- UI

    private fun col(id: Int): Int = ContextCompat.getColor(this, id)

    private fun accentColor(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.castvAccent, tv, true)
        return tv.data
    }

    /** Color de acento guardado: 0 Cian, 1 Verde, 2 Naranja, 3 Morado. */
    private fun savedAccent(): Int = getSharedPreferences("castv", MODE_PRIVATE).getInt("accent", 0)

    private val accentNames = arrayOf("Cian", "Verde", "Naranja", "Morado")

    private fun pickAccent() {
        AlertDialog.Builder(this)
            .setTitle("Color de acento")
            .setSingleChoiceItems(accentNames, savedAccent()) { d, which ->
                getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("accent", which).apply()
                d.dismiss()
                recreate() // vuelve a dibujar la pantalla con el color elegido
            }
            .show()
    }

    /** Tema guardado: 0 = Sistema, 1 = Claro, 2 = Oscuro (por defecto Oscuro, como antes). */
    private fun savedTheme(): Int = getSharedPreferences("castv", MODE_PRIVATE).getInt("theme", 2)

    private fun themeLabel(): String = when (savedTheme()) {
        0 -> "Sistema"
        1 -> "Claro"
        else -> "Oscuro"
    }

    private fun applyThemeMode(mode: Int) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (mode) {
            0 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        })
    }

    private fun pickTheme() {
        val labels = arrayOf("Sistema", "Claro", "Oscuro")
        AlertDialog.Builder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(labels, savedTheme()) { d, which ->
                getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("theme", which).apply()
                d.dismiss()
                applyThemeMode(which) // recrea la pantalla con el tema elegido
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeMode(savedTheme())
        super.onCreate(savedInstanceState)
        theme.applyStyle(when (savedAccent()) {
            1 -> R.style.AccentOverlayGreen
            2 -> R.style.AccentOverlayOrange
            3 -> R.style.AccentOverlayPurple
            else -> R.style.AccentOverlayCyan
        }, true)
        accentAtStart = savedAccent()   // [UI-1b]
        setContentView(R.layout.activity_main)
        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        Store.init(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val nf = IntentFilter().apply { addAction(ACT_PREV); addAction(ACT_TOGGLE); addAction(ACT_NEXT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(notifReceiver, nf, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(notifReceiver, nf)
        notifReceiverRegistered = true
        mediaSession = MediaSessionCompat(this, "CASTV").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { if (!currentlyPlaying()) togglePlayPause() }
                override fun onPause() { if (currentlyPlaying()) togglePlayPause() }
                override fun onStop() { if (currentlyPlaying()) togglePlayPause() }
                override fun onSkipToNext() { step(+1) }
                override fun onSkipToPrevious() { step(-1) }
                override fun onSeekTo(pos: Long) { seekToSec((pos / 1000).toInt()) }
            })
        }

        listFiles = findViewById(R.id.listFiles)
        val inflater = LayoutInflater.from(this)
        val footer = inflater.inflate(R.layout.footer_credits, listFiles, false)
        listFiles.addFooterView(footer, null, false)
        footerView = footer
        footerAttached = true

        tvStatus = findViewById(R.id.tvStatus)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        tvElapsed = findViewById(R.id.tvElapsed)
        tvDuration = findViewById(R.id.tvDuration)
        seekBar = findViewById(R.id.seekBar)
        btnAuto = findViewById(R.id.btnAuto)
        btnMute = findViewById(R.id.btnMute)
        volSeek = findViewById(R.id.volSeek)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnNowFav = findViewById(R.id.btnNowFav)
        btnOutput = findViewById(R.id.btnOutput)
        nowThumbBox = findViewById(R.id.nowThumbBox)
        nowIcon = findViewById(R.id.nowIcon)
        nowThumb = findViewById(R.id.nowThumb)
        btnSearch = findViewById(R.id.btnSearch)
        btnMenu = findViewById(R.id.btnMenu)
        searchBar = findViewById(R.id.searchBar)
        tabAll = findViewById(R.id.tabAll)
        tabFavs = findViewById(R.id.tabFavs)
        tabResume = findViewById(R.id.tabResume)
        navVideo = findViewById(R.id.navVideo)
        navAudio = findViewById(R.id.navAudio)
        navFolders = findViewById(R.id.navFolders)
        navQueue = findViewById(R.id.navQueue)
        navMore = findViewById(R.id.navMore)   // [UI-1]
        btnQuick = findViewById(R.id.btnQuick)
        btnExpand = findViewById(R.id.btnExpand)
        playerExpanded = findViewById(R.id.playerExpanded)
        tvFilter = findViewById(R.id.tvFilter)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnPlay = findViewById(R.id.btnPlay)
        fab = findViewById(R.id.fabShuffle)

        sortMode = Sort.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("sort", 0)) { Sort.NAME_ASC }

        outPhone = getSharedPreferences("castv", MODE_PRIVATE).getBoolean("outPhone", false)
        repeatMode = Repeat.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("repeat", 0)) { Repeat.OFF }
        eqPreset = getSharedPreferences("castv", MODE_PRIVATE).getInt("eqPreset", -1)
        bassOn = getSharedPreferences("castv", MODE_PRIVATE).getBoolean("bassOn", false)
        updateOutputButton()
        btnOutput.setOnClickListener {
            outPhone = !outPhone
            getSharedPreferences("castv", MODE_PRIVATE).edit().putBoolean("outPhone", outPhone).apply()
            updateOutputButton()
            if (!outPhone && localActive) {
                stopLocal()
                adapter.playingLocal = false
                adapter.notifyDataSetChanged()
            }
            setStatus(if (outPhone) "📱 La música y los videos se verán en el teléfono"
                      else "📺 La música y los videos irán al TV")
        }

        adapter = FileAdapter()
        listFiles.adapter = adapter

        navVideo.setOnClickListener { showMovies() }
        navAudio.setOnClickListener { showMusic() }
        navFolders.setOnClickListener { pickFolder() }
        navQueue.setOnClickListener { showQueue() }
        navMore.setOnClickListener { showMoreSheet() }   // [UI-1]
        tabAll.setOnClickListener { viewMode = ViewMode.ALL; applyFilters() }
        tabFavs.setOnClickListener { viewMode = ViewMode.FAVS; applyFilters() }
        tabResume.setOnClickListener { viewMode = ViewMode.RESUME; applyFilters() }
        fab.setOnClickListener { shuffleAndPlay() }
        tvFilter.setOnClickListener { clearFilters() }
        listFiles.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                setFabShown(scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
            }
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {}
        })
        btnSearch.setOnClickListener { toggleSearch() }
        btnMenu.setOnClickListener {   // [UI-1b] el ⋮ abre la pantalla Ajustes
            startActivityForResult(Intent(this, SettingsActivity::class.java), reqSettings)
        }
        btnQuick.setOnClickListener { if (playingItem == null && !linkOnTv) step(+1) else togglePlayPause() }
        val toggleExpand = View.OnClickListener { setPlayerExpanded(playerExpanded.visibility != View.VISIBLE) }
        btnExpand.setOnClickListener(toggleExpand)
        tvNowPlaying.setOnClickListener(toggleExpand)
        nowThumbBox.setOnClickListener(toggleExpand)
        setPlayerExpanded(false)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                applyFilters()
            }
        })
        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }
        btnClearSearch.setOnClickListener { etSearch.setText("") }

        btnNowFav.setOnClickListener { playingItem?.let { toggleFav(it) } }
        btnPlay.setOnClickListener {
            if (playingItem == null && !linkOnTv) step(+1) else togglePlayPause()
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            manualStop = true
            saveProgress()
            if (localActive) {
                withLocal { it.pause(); it.seekTo(0) }
            } else {
                thread { TvController.stop() }
                resetProgress()
                linkOnTv = false; linkDurSec = 0   // [PAUSA-1] tras Detener ya no hay enlace que pausar
            }
            adapter.notifyDataSetChanged()
        }
        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(+1) }
        findViewById<View>(R.id.btnShuffle).setOnClickListener { shuffleAndPlay() }
        btnAuto.setOnClickListener { cycleRepeat() }

        findViewById<Button>(R.id.btnBack10).setOnClickListener {
            val t = maxOf(0, lastPos - 10)
            if (localActive) withLocal { it.seekTo(t * 1000) }
            else thread { TvController.seek(t) }
        }
        findViewById<Button>(R.id.btnFwd10).setOnClickListener {
            val t = if (lastDur > 0) minOf(lastDur, lastPos + 10) else lastPos + 10
            if (localActive) withLocal { it.seekTo(t * 1000) }
            else thread { TvController.seek(t) }
        }
        btnMute.setOnClickListener {
            muted = !muted
            btnMute.setImageResource(if (muted) R.drawable.ic_vol_off else R.drawable.ic_vol_on)
            val m = muted
            if (localActive) applyLocalVolume() else thread { TvController.setMute(m) }
        }
        volSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) { volTouching = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val v = sb.progress
                volTouching = false
                if (localActive) {
                    val mx = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * mx + 50) / 100, 0)
                } else thread { TvController.setVolume(v) }
            }
        })

        val fbUrl = "https://www.facebook.com/share/19x6GMsWUM/"
        val igUrl = "https://www.instagram.com/arieldejesusbuen"
        val ttUrl = "https://www.tiktok.com/@arieldejesusbueno0"
        footer.findViewById<View>(R.id.fFb).setOnClickListener { openUrl(fbUrl) }
        footer.findViewById<View>(R.id.fIg).setOnClickListener { openUrl(igUrl) }
        footer.findViewById<View>(R.id.fTt).setOnClickListener { openUrl(ttUrl) }

        footer.findViewById<TextView>(R.id.tvWhatsapp).setOnClickListener {
            val url = "https://wa.me/18094480441?text=" +
                    Uri.encode("Hola Ariel, vi tu app CASTV 2.0 y está excelente!")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) { }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) tvElapsed.text = fmt(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val target = sb.progress
                userSeeking = false
                if (localActive) withLocal { it.seekTo(target * 1000) }
                else thread { TvController.seek(target) }
            }
        })

        listFiles.setOnItemClickListener { _, _, position, _ ->
            val idx = position - listFiles.headerViewsCount
            if (idx in currentList.indices) {
                if (selecting) toggleSelect(currentList[idx]) else playFromList(idx)
            }
        }
        listFiles.setOnItemLongClickListener { _, _, position, _ ->
            val idx = position - listFiles.headerViewsCount
            if (idx in currentList.indices) {
                if (selecting) toggleSelect(currentList[idx]) else startSelection(currentList[idx])
            }
            true
        }

        updateRepeatButton()
        updateTabs()
        updateQueueBadge()
        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        monitoring = false
        stopLocal()
        if (notifReceiverRegistered) {
            try { unregisterReceiver(notifReceiver) } catch (e: Exception) { }
            notifReceiverRegistered = false
        }
        try { mediaSession?.isActive = false; mediaSession?.release() } catch (e: Exception) { }
        mediaSession = null
        postDefaultNotification()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // Primero se cierra lo que esté abierto; con música en el teléfono, "atrás" minimiza
        // en vez de cerrar y cortar la música
        when {
            selecting -> actionMode?.finish()
            searchBar.visibility == View.VISIBLE -> toggleSearch()
            playerExpanded.visibility == View.VISIBLE -> setPlayerExpanded(false)
            hasActiveFilter() -> clearFilters()
            localActive -> moveTaskToBack(true)
            else -> super.onBackPressed()
        }
    }

    /** Con algo sonando en el TV, los botones de volumen del teléfono suben/bajan el volumen del TV. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            !localActive && TvController.tvIp != null && playingItem != null) {
            val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5
            val v = (volSeek.progress + delta).coerceIn(0, 100)
            volSeek.progress = v
            setStatus("🔊 Volumen del TV: $v")
            thread { TvController.setVolume(v) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("No se pudo abrir el enlace")
        }
    }

    private fun checkPermissionsAndStart() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            startEverything()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startEverything()
    }

    private fun startEverything() {
        if (monitoring) return
        val intent = Intent(this, CastvService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (getSharedPreferences("castv", MODE_PRIVATE).getBoolean("lastMusic", false)) showMusic()
        else showMovies()
        monitoring = true
        thread { monitorLoop() }
        window.decorView.postDelayed({ warnIfPortBusy() }, 2000)
    }

    /** [Etapa 14-C] El diálogo vive en PortDialog.kt. */
    private fun warnIfPortBusy() = PortDialog.showIfBusy(this)

    private var loadGen = 0

    /** Lee la lista en segundo plano para que la pantalla no se congele al abrir. */
    private fun loadMedia(music: Boolean) {
        actionMode?.finish()
        showingMusic = music
        getSharedPreferences("castv", MODE_PRIVATE).edit().putBoolean("lastMusic", music).apply()
        nameKeyCache.clear()
        val gen = ++loadGen
        updateTabs()
        thread {
            val items = try {
                if (music) MediaScanner.scanMusic(this) else MediaScanner.scanVideos(this)
            } catch (e: Exception) {
                DebugLog.add("ERROR leyendo la lista: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
            val dups = computeDups(items)
            runOnUiThread {
                if (gen != loadGen || isFinishing) return@runOnUiThread
                allItems = items
                dupIds = dups
                selectedFolder = null
                selectedArtist = null
                selectedAlbum = null
                applyFilters()
                updateTabs()
            }
        }
    }

    private fun showMovies() = loadMedia(false)

    private fun showMusic() = loadMedia(true)

    // ------------------------------------------- Buscar, ordenar, carpetas

    // [Etapa 14-A] la lógica vive en CastvUtils.kt
    private fun norm(s: String): String = CastvText.norm(s)

    private fun nameKey(m: MediaItem): String = nameKeyCache.getOrPut(m.id) { norm(m.name) }

    /** Mismo título (sin extensión ni etiquetas tipo "(MP3_160K)") y duración casi igual
     *  (±2 s) = copia del mismo archivo. Se conserva la más antigua. */
    private fun computeDups(items: List<MediaItem>): Set<Long> {
        val d = HashSet<Long>()
        for (g in items.groupBy { norm(prettyTitle(it.name)) }.values) {
            if (g.size < 2) continue
            val sorted = g.sortedBy { it.durationSec }
            var start = 0
            while (start < sorted.size) {
                var end = start + 1
                while (end < sorted.size && sorted[end].durationSec - sorted[end - 1].durationSec <= 2) end++
                if (end - start >= 2) {
                    val cluster = sorted.subList(start, end)
                    val keep = cluster.minWithOrNull(compareBy<MediaItem>({ it.dateAdded }, { it.id }))!!
                    for (m in cluster) if (m.id != keep.id) d.add(m.id)
                }
                start = end
            }
        }
        return d
    }

    /** Compara "Cap 2" antes que "Cap 10" (números por valor, no letra a letra). */
    private fun naturalCompare(a: String, b: String): Int = CastvText.naturalCompare(a, b)

    private fun applyFilters() {
        var list = allItems
        selectedFolder?.let { f -> list = list.filter { it.folder == f } }
        selectedArtist?.let { ar -> list = list.filter { it.artist == ar } }
        selectedAlbum?.let { al -> list = list.filter { it.album == al } }
        selectedPlaylist?.let { pl -> list = list.filter { Store.inList(pl, it) } }
        if (Store.hideDups) list = list.filter { it.id !in dupIds }
        when (viewMode) {
            ViewMode.FAVS -> list = list.filter { Store.isFav(it) }
            ViewMode.RESUME -> list = list.filter { Store.resumeAt(it) >= 10 }
            ViewMode.ALL -> {}
        }
        if (query.isNotEmpty()) {
            val toks = norm(query).split(' ').filter { it.isNotEmpty() }
            list = list.filter { m ->
                val h = norm(m.name + " " + prettyTitle(m) + " " + m.artist + " " + m.album)
                toks.all { t -> h.contains(t) }
            }
        }
        currentList = when (sortMode) {
            Sort.NAME_ASC -> list.sortedWith { a, b -> naturalCompare(nameKey(a), nameKey(b)) }
            Sort.NAME_DESC -> list.sortedWith { a, b -> naturalCompare(nameKey(b), nameKey(a)) }
            Sort.NEWEST -> list.sortedByDescending { it.dateAdded }
            Sort.LONGEST -> list.sortedByDescending { it.durationSec }
            Sort.SHORTEST -> list.sortedBy { it.durationSec }
            Sort.MOST_PLAYED -> list.sortedWith(
                compareByDescending<MediaItem> { Store.playCount(it) }.thenBy { nameKey(it) })
            Sort.RECENT_PLAYED -> list.sortedByDescending { Store.lastPlayed(it) }
        }
        refreshList()
        updateTabs()
        updateFilterChip()
        updateEmptyState()
    }

    private fun hasMediaPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            (if (showingMusic) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_MEDIA_VIDEO)
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Mensaje en el centro de la lista cuando no hay nada que mostrar (ya no ensucia la línea de estado). */
    private fun updateEmptyState() {
        if (currentList.isNotEmpty()) {
            tvEmpty.visibility = View.GONE
            if (!footerAttached) {
                listFiles.addFooterView(footerView, null, false)
                footerAttached = true
            }
            return
        }
        val msg = when {
            !hasMediaPermission() -> "Falta el permiso para leer tus archivos.\nActívalo en Ajustes › Apps › CASTV › Permisos."
            allItems.isEmpty() -> if (showingMusic) "No se encontró música en el teléfono." else "No se encontraron videos en el teléfono."
            viewMode == ViewMode.FAVS -> "Aún no hay favoritos.\nToca ⋮ en un archivo y elige ☆."
            viewMode == ViewMode.RESUME -> "Nada por continuar todavía."
            query.isNotEmpty() -> "Sin resultados para «$query»."
            selectedPlaylist != null -> "Esta lista no tiene " + (if (showingMusic) "audios" else "videos") +
                    ".\nToca ⋮ en un archivo y elige 📃 Añadir a lista."
            else -> "Sin resultados."
        }
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
        if (footerAttached) {
            listFiles.removeFooterView(footerView)
            footerAttached = false
        }
    }

    private fun hasActiveFilter(): Boolean =
        selectedFolder != null || selectedArtist != null || selectedAlbum != null || selectedPlaylist != null

    private fun clearFilters() {
        selectedFolder = null
        selectedArtist = null
        selectedAlbum = null
        selectedPlaylist = null
        applyFilters()
    }

    /** Etiqueta con los filtros activos; al tocarla se quitan todos. */
    private fun updateFilterChip() {
        val parts = ArrayList<String>()
        selectedFolder?.let { parts.add("📁 " + it.substringAfterLast('/')) }
        selectedArtist?.let { parts.add("🎤 $it") }
        selectedAlbum?.let { parts.add("💿 $it") }
        selectedPlaylist?.let { parts.add("📃 $it") }
        if (parts.isEmpty()) {
            tvFilter.visibility = View.GONE
        } else {
            tvFilter.text = parts.joinToString("  ·  ") + "   ✕"
            tvFilter.visibility = View.VISIBLE
        }
    }

    private fun setFabShown(show: Boolean) {
        if (fabShown == show) return
        fabShown = show
        if (show) {
            fab.visibility = View.VISIBLE
            fab.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        } else {
            fab.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150)
                .withEndAction { if (!fabShown) fab.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun pickArtist() {
        val counts = allItems.filter { it.artist.isNotBlank() }.groupingBy { it.artist }.eachCount()
        if (counts.isEmpty()) {
            toast("Tus archivos no traen el dato del artista")
            return
        }
        val names = counts.keys.sortedWith { a, b -> naturalCompare(norm(a), norm(b)) }
        val labels = ArrayList<String>()
        labels.add("🎤 Todos los artistas")
        names.forEach { labels.add("🎤 $it (${counts[it]})") }
        AlertDialog.Builder(this)
            .setTitle("Artista")
            .setItems(labels.toTypedArray()) { _, which ->
                selectedArtist = if (which == 0) null else names[which - 1]
                applyFilters()
            }
            .show()
    }

    private fun pickAlbum() {
        val counts = allItems.filter { it.album.isNotBlank() }.groupingBy { it.album }.eachCount()
        if (counts.isEmpty()) {
            toast("Tus archivos no traen el dato del álbum")
            return
        }
        val names = counts.keys.sortedWith { a, b -> naturalCompare(norm(a), norm(b)) }
        val labels = ArrayList<String>()
        labels.add("💿 Todos los álbumes")
        names.forEach { labels.add("💿 $it (${counts[it]})") }
        AlertDialog.Builder(this)
            .setTitle("Álbum")
            .setItems(labels.toTypedArray()) { _, which ->
                selectedAlbum = if (which == 0) null else names[which - 1]
                applyFilters()
            }
            .show()
    }

    private fun pickFolder() {
        if (allItems.isEmpty()) return
        val counts = allItems.groupingBy { it.folder }.eachCount()
        val folders = counts.keys.sortedWith { a, b -> naturalCompare(norm(a), norm(b)) }
        val labels = ArrayList<String>()
        labels.add("📁 Todas (${allItems.size})")
        folders.forEach { labels.add("📁 $it (${counts[it]})") }
        AlertDialog.Builder(this)
            .setTitle("Carpeta")
            .setItems(labels.toTypedArray()) { _, which ->
                selectedFolder = if (which == 0) null else folders[which - 1]
                applyFilters()
            }
            .show()
    }

    private fun refreshList() {
        adapter.items = currentList
        adapter.notifyDataSetChanged()
    }

    private fun updateTabs() {
        val on = accentColor()
        val off = col(R.color.vlc_muted)
        fun nav(t: TextView, active: Boolean) {
            val c = if (active) on else off
            t.setTextColor(c)
            t.compoundDrawableTintList = ColorStateList.valueOf(c)
        }
        nav(navVideo, !showingMusic)
        nav(navAudio, showingMusic)
        nav(navFolders, selectedFolder != null)
        navFolders.text = selectedFolder?.substringAfterLast('/') ?: "Carpetas"
        fun tab(t: TextView, active: Boolean) {
            t.setBackgroundResource(if (active) R.drawable.tab_active else R.drawable.tab_off)
            t.setTextColor(if (active) on else off)
        }
        tabAll.text = if (showingMusic) "PISTAS" else "VIDEOS"
        tab(tabAll, viewMode == ViewMode.ALL)
        tab(tabFavs, viewMode == ViewMode.FAVS)
        tab(tabResume, viewMode == ViewMode.RESUME)
    }

    private fun toggleSearch() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (searchBar.visibility == View.VISIBLE) {
            etSearch.setText("")
            searchBar.visibility = View.GONE
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        } else {
            searchBar.visibility = View.VISIBLE
            etSearch.requestFocus()
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setPlayerExpanded(expanded: Boolean) {
        playerExpanded.visibility = if (expanded) View.VISIBLE else View.GONE
        btnExpand.rotation = if (expanded) 180f else 0f
    }

    private fun pickSort() {
        val all = Sort.values()
        AlertDialog.Builder(this)
            .setTitle("Ordenar por")
            .setSingleChoiceItems(
                all.map { it.label.removePrefix("↕ ") }.toTypedArray(), sortMode.ordinal
            ) { d, which ->
                sortMode = all[which]
                getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("sort", sortMode.ordinal).apply()
                applyFilters()
                d.dismiss()
            }
            .show()
    }

    /** Rellena las opciones del menú principal (las usan el ⋮ y la hoja "Más"). */
    private fun fillMainMenu(menu: Menu) {
        menu.add(0, 1, 0, "Ordenar: " + sortMode.label.removePrefix("↕ "))
        menu.add(0, 2, 1, when {
            dupIds.isEmpty() -> "Duplicados: ninguno"
            Store.hideDups -> "Mostrar duplicados (${dupIds.size})"
            else -> "Ocultar duplicados (${dupIds.size})"
        })
        menu.add(0, 3, 2, if (outPhone) "Salida: Teléfono" else "Salida: TV")
        menu.add(0, 4, 3, "Mis listas")
        if (showingMusic) {
            menu.add(0, 5, 4, "Filtrar por artista")
            menu.add(0, 6, 5, "Filtrar por álbum")
        }
        menu.add(0, 7, 6, sleepLabel())
        menu.add(0, 8, 7, "Velocidad: " + speedShort() + " (teléfono)")
        menu.add(0, 9, 8, "Ecualizador (teléfono)")
        menu.add(0, 10, 9, "Elegir TV")
        menu.add(0, 12, 10, "Tema: " + themeLabel())
        menu.add(0, 13, 11, "Color: " + accentNames[savedAccent().coerceIn(0, 3)])
        if (Build.VERSION.SDK_INT >= 30) {
            menu.add(0, 14, 11, if (useTrash()) "Borrado: a la papelera" else "Borrado: permanente")
        }
        menu.add(0, 15, 11, "Reproducir enlace…")   // [Etapa 13-A]
        menu.add(0, 16, 11, "Listas M3U (canales)…")   // [Etapa 13-B]
        menu.add(0, 17, 11, "Respaldo (exportar/importar)…")   // [Respaldo JSON]
        menu.add(0, 11, 12, "Mis redes y contacto")
    }

    /** Ejecuta la acción de una opción del menú principal por su id. */
    private fun runMenuAction(id: Int) {
        when (id) {
            1 -> pickSort()
            2 -> if (dupIds.isNotEmpty()) {
                Store.hideDups = !Store.hideDups
                applyFilters()
            }
            3 -> btnOutput.performClick()
            4 -> showPlaylists()
            5 -> pickArtist()
            6 -> pickAlbum()
            7 -> pickSleepTimer()
            8 -> pickSpeed()
            9 -> showEqualizer()
            10 -> pickTv()
            11 -> showSocials()
            12 -> pickTheme()
            13 -> pickAccent()
            14 -> toggleTrash()
            15 -> askLinkAndPlay()
            16 -> startActivityForResult(Intent(this, M3uActivity::class.java), reqM3u)
            17 -> showBackupMenu()
        }
    }

    // ------------------------------------------- Hoja "Más" [UI-1]

    private fun showMoreSheet() {
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        // Lee las opciones de un menú auxiliar (sin mostrarlo) para no duplicar textos.
        val aux = PopupMenu(this, navMore)
        fillMainMenu(aux.menu)
        val titles = LinkedHashMap<Int, String>()
        for (i in 0 until aux.menu.size()) {
            val mi = aux.menu.getItem(i)
            titles[mi.itemId] = mi.title?.toString() ?: ""
        }

        val groups = ArrayList<Pair<String, List<Int>>>()
        groups.add("Reproducir y canales" to listOf(15, 16, 10, 3))
        groups.add("Biblioteca" to listOf(1, 2, 4, 5, 6))
        groups.add("Reproductor" to listOf(7, 8, 9))
        val known = groups.flatMap { it.second }.toSet()
        val inSettings = setOf(11, 12, 13, 14, 17)   // [UI-1b] viven en la pantalla Ajustes
        val extra = titles.keys.filter { it !in known && it !in inSettings }.sorted()
        if (extra.isNotEmpty()) groups.add("Más" to extra)

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val r = dp(20).toFloat()
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(20))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(col(R.color.vlc_surface))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        val sel = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)

        for ((name, ids) in groups) {
            val shown = ids.filter { titles.containsKey(it) }
            if (shown.isEmpty()) continue
            root.addView(TextView(this).apply {
                text = name
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(accentColor())
                setPadding(dp(20), dp(14), dp(20), dp(4))
            })
            for (id in shown) {
                root.addView(TextView(this).apply {
                    text = titles[id]
                    textSize = 16f
                    setTextColor(col(R.color.vlc_text))
                    setPadding(dp(20), dp(13), dp(20), dp(13))
                    setBackgroundResource(sel.resourceId)
                    setOnClickListener {
                        sheet.dismiss()
                        runMenuAction(id)
                    }
                })
            }
        }

        val scroll = androidx.core.widget.NestedScrollView(this)
        scroll.addView(root)
        sheet.setContentView(scroll)
        (scroll.parent as? View)?.setBackgroundColor(0)   // deja ver las esquinas redondeadas
        sheet.setOnShowListener {
            sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        sheet.show()
    }

    // ------------------------------------------- Respaldo (exportar / importar)

    @Suppress("DEPRECATION")
    private fun showBackupMenu() {
        AlertDialog.Builder(this)
            .setTitle("Respaldo")
            .setItems(arrayOf("📤 Exportar respaldo", "📥 Importar respaldo")) { _, which ->
                try {
                    if (which == 0) {
                        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date())
                        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, "castv_respaldo_$day.json")
                        startActivityForResult(i, reqBackupOut)
                    } else {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*")
                        startActivityForResult(i, reqBackupIn)
                    }
                } catch (e: Exception) {
                    toast("No se pudo abrir el selector de archivos")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun doExport(uri: Uri) {
        thread {
            val ok = try {
                val json = Backup.build(this)
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: Exception) {
                DebugLog.add("RESPALDO: error al exportar: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
            runOnUiThread { toast(if (ok) "✅ Respaldo guardado" else "❌ No se pudo guardar el respaldo") }
        }
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("¿Importar respaldo?")
            .setMessage("Esto REEMPLAZA tus favoritos, listas, historial de enlaces, listas M3U y ajustes actuales por los del archivo. Los puntos de reanudación, las estadísticas y el TV elegido no cambian. Si suena algo en el teléfono, se detendrá.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Importar") { _, _ -> doImport(uri) }
            .show()
    }

    private fun doImport(uri: Uri) {
        thread {
            var err: String? = null
            try {
                val text = contentResolver.openInputStream(uri)?.use { Backup.readLimited(it) }
                err = if (text == null) "No se pudo leer el archivo (o es demasiado grande)." else Backup.restore(this, text)
            } catch (e: Exception) {
                DebugLog.add("RESPALDO: error al importar: ${e.javaClass.simpleName}: ${e.message}")
                err = "No se pudo leer el archivo."
            }
            val msg = err
            runOnUiThread {
                if (msg != null) {
                    toast("❌ $msg")
                } else {
                    Store.reload()
                    toast("✅ Respaldo importado")
                    recreate()   // aplica tema, color, orden y demás ajustes
                }
            }
        }
    }

    private fun openWhatsapp() {
        val url = "https://wa.me/18094480441?text=" +
                Uri.encode("Hola Ariel, vi tu app CASTV 2.0 y está excelente!")
        openUrl(url)
    }

    private fun showSocials() {
        val items = arrayOf("Facebook", "Instagram", "TikTok", "WhatsApp: +1 (809) 448-0441")
        AlertDialog.Builder(this)
            .setTitle("Creado por Ariel De Jesus Bueno")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openUrl(FB_URL)
                    1 -> openUrl(IG_URL)
                    2 -> openUrl(TT_URL)
                    3 -> openWhatsapp()
                }
            }
            .show()
    }

    /** Letra de sección estilo VLC ("#" para números y símbolos). */
    private fun sectionLetter(name: String): String {
        val c = norm(name).firstOrNull { !it.isWhitespace() } ?: return "#"
        return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
    }

    private fun updateQuickIcon(playing: Boolean) {
        if (lastQuickPlaying == playing) return
        lastQuickPlaying = playing
        runOnUiThread {
            btnQuick.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_w)
            btnPlay.setImageResource(if (playing) R.drawable.ic_pause_dark else R.drawable.ic_play)
        }
    }

    private fun setStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            Repeat.OFF -> Repeat.ALL
            Repeat.ALL -> Repeat.ONE
            Repeat.ONE -> Repeat.OFF
        }
        getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("repeat", repeatMode.ordinal).apply()
        updateRepeatButton()
        setStatus(when (repeatMode) {
            Repeat.OFF -> "🔁 Repetición apagada: al terminar se detiene (la cola sigue)"
            Repeat.ALL -> "🔁 Auto-avance: sigue con la lista"
            Repeat.ONE -> "🔂 Repitiendo esta pista"
        })
    }

    private fun updateRepeatButton() {
        btnAuto.setImageResource(if (repeatMode == Repeat.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        btnAuto.setBackgroundResource(if (repeatMode == Repeat.OFF) R.drawable.ctrl_off else R.drawable.ctrl_on)
        btnAuto.alpha = if (repeatMode == Repeat.OFF) 0.5f else 1f
    }

    /** Qué hacer cuando termina una pista (siempre en el hilo principal). */
    private fun advanceAfterEnd() {
        if (consumeSleepAfterTrack()) return
        val cur = playingItem
        if (repeatMode == Repeat.ONE && cur != null) {
            startPlayback(cur)
            return
        }
        if (repeatMode == Repeat.ALL || queue.isNotEmpty()) step(+1)
    }

    private fun fmt(totalSec: Int): String = CastvText.fmtTime(totalSec)

    private fun resetProgress() {
        seekBar.isEnabled = false
        seekBar.max = 100
        seekBar.progress = 0
        tvElapsed.text = "0:00"
        tvDuration.text = "--:--"
    }

    // ---------------------------------------------------------- Reproducción

    private fun step(delta: Int) {
        if (delta > 0 && queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            updateQueueBadge()
            playingFromQueue = true
            startPlayback(next)
            return
        }
        if (delta < 0 && playingFromQueue && playIndex in playlist.indices) {
            // "Anterior" desde la cola: vuelve a lo que sonaba antes de entrar en ella
            playingFromQueue = false
            startPlayback(playlist[playIndex])
            return
        }
        playingFromQueue = false
        if (playlist.isEmpty()) {
            playlist = ArrayList(currentList)
            playIndex = -1
        }
        if (playlist.isEmpty()) {
            setStatus("⚠️ Playlist vacía")
            return
        }
        playIndex = if (playIndex < 0) 0 else Math.floorMod(playIndex + delta, playlist.size)
        startPlayback(playlist[playIndex])
    }

    private fun shuffleAndPlay() {
        if (currentList.isEmpty()) {
            setStatus("⚠️ Playlist vacía")
            return
        }
        currentList = currentList.shuffled()
        playlist = ArrayList(currentList)
        playIndex = 0
        playingFromQueue = false
        refreshList()
        startPlayback(playlist[0])
    }

    /** Video con la salida en "Teléfono": se abre el reproductor a pantalla completa. */
    // ------------------------------------------- [Etapa 13-A] Reproducir enlace

    /** Pide una URL (mp4 o HLS .m3u8) y deja elegir destino: 📺 TV o 📱 móvil. */
    private fun askLinkAndPlay() {
        val input = EditText(this).apply {
            hint = "http://…/video.mp4  o  …/lista.m3u8"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            // Si el portapapeles trae un enlace, se pre-rellena
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val c = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (c != null && (c.startsWith("http://") || c.startsWith("https://"))) setText(c)
            } catch (e: Exception) { }
        }
        val box = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Reproducir enlace")
            .setView(box)
            .setPositiveButton("📺 Al TV") { _, _ -> playLink(input.text.toString().trim(), true) }
            .setNegativeButton("📱 En el móvil") { _, _ -> playLink(input.text.toString().trim(), false) }
            .setNeutralButton("🕘 Historial") { _, _ -> showLinkHistory() }
            .show()
    }

    /** Últimos enlaces usados y favoritos (★). */
    private fun showLinkHistory() {
        val saved = Store.links()
        if (saved.isEmpty()) {
            toast("Aún no hay enlaces guardados")
            return
        }
        val labels = saved.map { (if (it.fav) "★ " else "") + it.title + "  ·  " + (Uri.parse(it.url).host ?: "") }
        AlertDialog.Builder(this)
            .setTitle("Historial de enlaces")
            .setItems(labels.toTypedArray()) { _, which -> showLinkActions(saved[which]) }
            .setNeutralButton("Borrar historial") { _, _ ->
                Store.clearRecentLinks()
                toast("Historial borrado (los favoritos se conservan)")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showLinkActions(l: SavedLink) {
        val labels = arrayOf(
            "📺 Al TV",
            "📱 En el móvil",
            if (l.fav) "☆ Quitar de favoritos" else "★ Marcar favorito",
            "🗑 Quitar del historial")
        AlertDialog.Builder(this)
            .setTitle(l.title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> playLink(l.url, true)
                    1 -> playLink(l.url, false)
                    2 -> { Store.toggleLinkFav(l.url); showLinkHistory() }
                    3 -> { Store.removeLink(l.url); showLinkHistory() }
                }
            }
            .setNegativeButton("Atrás") { _, _ -> showLinkHistory() }
            .show()
    }

    private fun isHls(url: String) = url.substringBefore('?').lowercase().endsWith(".m3u8")

    private fun linkTitle(url: String): String {
        val last = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return if (last.isBlank()) "Enlace" else last
    }

    private fun playLink(url: String, toTv: Boolean, titleOverride: String? = null) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            setStatus("⚠️ El enlace debe empezar con http:// o https://")
            return
        }
        val title = titleOverride?.takeIf { it.isNotBlank() } ?: linkTitle(url)
        Store.addLink(url, title)   // historial de enlaces
        linkOnTv = false   // [PAUSA-1] se vuelve a marcar solo si el TV acepta este enlace
        linkDurSec = 0; val gen = ++linkGen   // [PROG-1d] enlace nuevo: duración en cero
        if (playingItem != null) stopCurrentPlayback()   // detiene lo que suene antes de cambiar de destino
        stopLocal()
        adapter.playingId = -1
        adapter.notifyDataSetChanged()
        if (!toTv) {
            val i = Intent(this, VideoPlayerActivity::class.java)
                .putExtra("url", url).putExtra("urlTitle", title)
            @Suppress("DEPRECATION")
            startActivityForResult(i, reqVideo)
            return
        }
        resetProgress()   // [PROG-1] barra en cero para el enlace nuevo
        // Directo al TV por DLNA: no pasa por el servidor del puerto 8000
        if (url.startsWith("https://")) setStatus("🚀 Enviando enlace https al TV (algunos TV lo rechazan)…")
        else setStatus("🚀 Enviando enlace al TV…")
        thread {
            if (TvController.tvIp == null) {
                runOnUiThread { setStatus("⚠️ TV no encontrado todavía. Espera unos segundos o usa ⋮ › Elegir TV.") }
                return@thread
            }
            val mime = if (isHls(url)) "application/vnd.apple.mpegurl" else "video/mp4"
            // [Etapa 13-A.2] mp4: el TV pide el video al teléfono (/relay) y el teléfono lo baja del enlace
            // (https, redirecciones).
            // [Etapa 13-A.3] HLS (.m3u8) también va por /relay: el teléfono reescribe la lista y sirve los
            // trozos, así el TV solo habla http con el teléfono. La extensión (.m3u8 o .mp4) le dice al relay qué es.
            val phoneIp = getLocalIpAddress()
            val sendUrl = if (phoneIp != null) TvSend.relayUrl(phoneIp, url, isHls(url)) else url   // [Etapa 14-C]
            DebugLog.add("ENLACE: $url -> $sendUrl")
            val ok = TvController.setUriAndPlay(sendUrl, title, mime, false)
            if (ok) linkOnTv = true   // [PAUSA-1] ⏸/▶ ahora controlan el enlace del TV
            if (ok) readLinkDuration(sendUrl, gen)   // [PROG-1d] el TV no informa la duración: la lee el teléfono
            runOnUiThread {
                if (ok) tvNowPlaying.text = title   // [PROG-1] título del enlace en el reproductor
                setStatus(
                    if (ok) "✅ Enlace en el TV"
                    else "❌ El TV no aceptó el enlace. ¿Está encendido y en el mismo Wi-Fi? Prueba otro enlace mp4.")
            }
        }
    }

    // [PROG-1d] El TV Hisense informa siempre 00:00:00 para los enlaces; el teléfono lee la duración
    // del mismo video (por el relay, que ya maneja redirecciones y rangos) sin librerías extra.
    private fun readLinkDuration(src: String, gen: Int) {
        thread {
            var secs = 0
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(src, HashMap<String, String>())
                val ms = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                secs = (ms / 1000).toInt()
            } catch (e: Exception) {
                DebugLog.add("DURACION enlace EXCEPCION ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                try { mmr.release() } catch (e: Exception) { }
            }
            if (gen != linkGen) return@thread   // ya hay otro enlace: se descarta
            DebugLog.add(if (secs > 0) "DURACION enlace: $secs s" else "DURACION enlace: no se pudo leer")
            if (secs > 0) linkDurSec = secs
        }
    }

    private fun playVideoOnPhone(item: MediaItem, resumeAt: Int) {
        Store.registerPlay(item)
        if (playingItem != null) stopCurrentPlayback()   // detiene lo que suene en el TV o en el teléfono
        adapter.playingId = -1
        adapter.notifyDataSetChanged()
        // Lista de videos desde éste en adelante (con algunos anteriores para el botón ⏮)
        val pl = playlist.filter { !it.isMusic }
        var idx = pl.indexOfFirst { it.id == item.id }
        val list: List<MediaItem>
        if (idx < 0) {
            list = listOf(item)
            idx = 0
        } else {
            val from = maxOf(0, idx - 100)
            list = pl.subList(from, minOf(pl.size, idx + 400))
            idx -= from
        }
        val i = Intent(this, VideoPlayerActivity::class.java)
            .putExtra("ids", LongArray(list.size) { list[it].id })
            .putExtra("names", Array(list.size) { list[it].name })
            .putExtra("durs", IntArray(list.size) { list[it].durationSec })
            .putExtra("index", idx)
            .putExtra("start", resumeAt)
        @Suppress("DEPRECATION")
        startActivityForResult(i, reqVideo)
    }

    override fun onResume() {
        super.onResume()
        if (savedAccent() != accentAtStart) { recreate(); return }   // [UI-1b] color cambiado en Ajustes
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()   // refresca "Continuar" al volver del reproductor
    }

    private fun startPlayback(item: MediaItem, resumeAt: Int = 0) {
        if (!item.isMusic && outPhone) {
            playVideoOnPhone(item, resumeAt)
            return
        }
        playingItem = item
        linkOnTv = false; linkDurSec = 0   // [PAUSA-1] lo nuevo reemplaza al enlace
        Store.registerPlay(item)
        pendingResume = resumeAt
        resumeTries = 0
        manualStop = false
        wasPlaying = false
        playStartedAt = SystemClock.elapsedRealtime()
        tvNowPlaying.text = prettyTitle(item)
        bindNowPlaying(item)
        resetProgress()
        adapter.playingId = item.id
        adapter.playingMusic = item.isMusic
        adapter.playingLocal = item.isMusic && outPhone
        adapter.notifyDataSetChanged()
        if (item.isMusic && outPhone) {
            startLocal(item, resumeAt)
            return
        }
        // El TV solo lee mp3 y mp4: los demás formatos (.opus, .m4a, .mkv...) se saltan
        val tvExt = MediaScanner.extFor(item.name, item.isMusic)
        if (tvExt != "mp3" && tvExt != "mp4" && tvExt != "m4v") {
            if (unsupportedSkips < 10) {
                unsupportedSkips++
                step(1)
                setStatus("⚠️ El TV solo lee mp3 y mp4. Se saltó .$tvExt")
            } else {
                unsupportedSkips = 0
                setStatus("⚠️ El TV solo lee mp3 y mp4 (.$tvExt)")
            }
            return
        }
        unsupportedSkips = 0
        if (ServerState.error != null) {
            setStatus("⚠️ Puerto 8000 ocupado: el TV no podrá leer el archivo")
            warnIfPortBusy()
            return
        }
        stopLocal()
        setStatus("🚀 Enviando al TV...")

        thread {
            val phoneIp = getLocalIpAddress()
            if (TvController.tvIp == null) {
                runOnUiThread { setStatus("⚠️ TV no encontrado todavía. Espera unos segundos o usa ⋮ › Elegir TV.") }
                return@thread
            }
            if (phoneIp == null) {
                runOnUiThread { setStatus("⚠️ No se pudo determinar la red. Conéctate al mismo Wi-Fi que el TV.") }
                return@thread
            }
            val kind = if (item.isMusic) "audio" else "video"
            val ext = MediaScanner.extFor(item.name, item.isMusic)
            val mime = MediaScanner.mimeFor(item.name, item.isMusic)
            val mediaUrl = TvSend.mediaUrl(phoneIp, kind, item.id, ext)   // [Etapa 14-C]
            val ok = TvController.setUriAndPlay(mediaUrl, prettyTitle(item), mime, item.isMusic)
            playStartedAt = SystemClock.elapsedRealtime()
            runOnUiThread {
                setStatus(
                    if (!ok) "❌ No se pudo enviar al TV. ¿Está encendido y en el mismo Wi-Fi? Revisa ⋮ › Elegir TV."
                    else if (resumeAt > 0) "⏩ Retomando en ${fmt(resumeAt)}"
                    else "✅ Reproduciendo en el TV"
                )
            }
        }
    }

    /** IP del teléfono en la interfaz que realmente llega al TV (no la de datos móviles). */
    private fun getLocalIpAddress(): String? = CastvNet.localIpAddress(TvController.tvIp)

    // ------------------------------------------- Monitor: progreso + auto-avance

    private fun monitorLoop() {
        var tick = 0
        while (monitoring) {
            try {
                if (localActive) {
                    pollLocal(tick)
                } else if (TvController.tvIp == null) {
                    if (tick % 5 == 0) {
                        val pref = getSharedPreferences("castv", MODE_PRIVATE).getString("tvIp", null)
                        val found = TvController.discoverTv(preferIp = pref)
                        if (found) {
                            tvFails = 0
                            tvOnline = true
                        }
                        val label = if (TvController.tvName.isNotEmpty()) TvController.tvName
                                    else (TvController.tvIp ?: "")
                        runOnUiThread {
                            if (found) updateOutputButton()
                            setStatus(
                                if (found) "✅ TV encontrado: $label"
                                else "⚠️ No se encontró el TV. Reintentando..."
                            )
                        }
                    }
                } else {
                    poll(tick)
                }
                checkSleep()
            } catch (e: Exception) { }
            tick++
            try { Thread.sleep(1000) } catch (e: InterruptedException) { return }
        }
    }

    private fun poll(tick: Int) {
        val info = TvController.getPositionInfo()
        if (info == null) {
            // Sin respuesta: ¿está apagado el TV / cambió de IP? Tras varios intentos se vuelve a buscar.
            if (TvController.isReachable()) {
                tvFails = 0
            } else {
                tvFails++
                if (tvOnline && tvFails >= 3) {
                    tvOnline = false
                    runOnUiThread { updateOutputButton() }
                }
                if (tvFails >= 8) {
                    tvFails = 0
                    TvController.tvIp = null
                    runOnUiThread { setStatus("⚠️ Se perdió la conexión con el TV. Buscándolo de nuevo...") }
                    return
                }
            }
        }
        if (info != null) {
            tvFails = 0
            if (!tvOnline) {
                tvOnline = true
                runOnUiThread { updateOutputButton() }
            }
            val (pos, rawDur) = info
            val dur = if (rawDur > 0) rawDur else (playingItem?.durationSec ?: (if (linkOnTv) linkDurSec else 0))   // [PROG-1d]
            lastPos = pos
            lastDur = dur
            runOnUiThread {
                if (!userSeeking && (playingItem != null || linkOnTv)) {   // [PROG-1] también con un enlace en el TV
                    if (dur > 0) {
                        seekBar.isEnabled = true
                        seekBar.max = dur
                        seekBar.progress = minOf(pos, dur)
                        tvDuration.text = fmt(dur)
                    } else {
                        seekBar.isEnabled = false
                        tvDuration.text = "--:--"
                    }
                    tvElapsed.text = fmt(pos)
                }
            }
        }

        if (tick % 4 == 1 && !volTouching) {
            val v = TvController.getVolume()
            if (v != null) runOnUiThread { if (!volTouching) volSeek.progress = v }
        }

        if (tick % 2 != 0) return
        val state = TvController.getTransportState()
        tvPlaying = state == "PLAYING"
        refreshNotification()

        // Retomar donde se dejó: en cuanto el TV ya está reproduciendo, salta al segundo guardado
        val resume = pendingResume
        if (resume > 0 && state == "PLAYING" &&
            SystemClock.elapsedRealtime() - playStartedAt > 1500) {
            val ok = TvController.seek(resume)
            if (ok || ++resumeTries >= 3) pendingResume = 0
        }

        if (SystemClock.elapsedRealtime() - playStartedAt < 4000) return

        if (state == "PLAYING") {
            wasPlaying = true
            if (tick % 6 == 0) saveProgress()
        } else if (state == "STOPPED" && wasPlaying) {
            wasPlaying = false
            if (!manualStop) {
                playingItem?.let { Store.clearResume(it) }   // terminó: ya no hay nada que continuar
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    advanceAfterEnd()
                }
            }
        }
    }

    // ------------------------------------- Notificación con controles

    private fun localIsPlaying(): Boolean =
        try { localPlayer?.isPlaying == true } catch (e: Exception) { false }

    /** Pausa / reanuda, ya sea en el teléfono o en el TV. */
    private fun togglePlayPause() {
        if (localActive) {
            if (localIsPlaying()) {
                withLocal { it.pause() }
                saveProgress()
                updateQuickIcon(false)
            } else {
                pausedByFocus = false
                withLocal { it.start() }
                applySpeed()
                updateQuickIcon(true)
            }
        } else {
            val playingNow = tvPlaying
            manualStop = false
            thread { if (playingNow) TvController.pause() else TvController.play() }
            updateQuickIcon(!playingNow)
        }
        lastNotifKey = ""   // fuerza refrescar el botón en el próximo ciclo
    }

    private fun currentlyPlaying(): Boolean = if (localActive) localIsPlaying() else tvPlaying

    private fun seekToSec(t: Int) {
        if (localActive) withLocal { it.seekTo(t * 1000) } else thread { TvController.seek(t) }
    }

    /** Nombre sin extensión ni etiquetas tipo "(MP3_160K)". */
    private fun prettyTitle(name: String): String {
        var t = name.substringBeforeLast('.', name)
        t = t.replace(Regex("\\(?MP3_\\d+K\\)?", RegexOption.IGNORE_CASE), "")
        return t.trim().trimEnd('_', '-', ' ').ifEmpty { name }
    }

    private val monthsEs = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val hashNameRe = Regex("^[0-9a-fA-F]{20,}$")
    private val waNameRe = Regex("^(VID|AUD|PTT)-(\\d{4})(\\d{2})(\\d{2})-WA(\\d+)$", RegexOption.IGNORE_CASE)

    private fun shortDate(sec: Long): String {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = sec * 1000 }
        return "${c.get(java.util.Calendar.DAY_OF_MONTH)} ${monthsEs[c.get(java.util.Calendar.MONTH)]} ${c.get(java.util.Calendar.YEAR)}"
    }

    /** Nombre para mostrar: si el archivo se llama con un código largo (cámara, descargas) o es de WhatsApp,
     *  se muestra algo legible como "Video Camera 16 nov 2026 (8bc7)". */
    private fun prettyTitle(item: MediaItem): String {
        val base = prettyTitle(item.name)
        waNameRe.matchEntire(base)?.let { m ->
            val kind = when (m.groupValues[1].uppercase()) {
                "VID" -> "Video"
                "PTT" -> "Nota de voz"
                else -> "Audio"
            }
            val mon = monthsEs.getOrElse((m.groupValues[3].toIntOrNull() ?: 0) - 1) { "?" }
            val day = m.groupValues[4].toIntOrNull() ?: 0
            return "WhatsApp $kind: $day $mon ${m.groupValues[2]} (${m.groupValues[5]})"
        }
        if (hashNameRe.matches(base)) {
            val kind = if (item.isMusic) "Audio" else "Video"
            val folder = item.folder.substringAfterLast('/')
            val where = if (folder.isNotBlank() && folder != "(raíz)") " $folder" else ""
            val date = if (item.dateAdded > 0) " " + shortDate(item.dateAdded) else ""
            return "$kind$where$date (${base.takeLast(4)})"
        }
        return base
    }

    private fun updateSessionState(playing: Boolean) {
        val st = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
        try {
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(st, lastPos * 1000L, if (playing) 1f else 0f)
                    .build()
            )
        } catch (e: Exception) { }
    }

    private fun refreshNotification() {
        val item = playingItem ?: return
        val playing = currentlyPlaying()
        updateQuickIcon(playing)
        updateSessionState(playing)
        val cover = Thumbs.cached(item)
        val dur = if (lastDur > 0) lastDur else item.durationSec
        val key = "${item.id}|${item.isMusic}|$playing|$localActive|${cover != null}|${dur > 0}"
        if (key == lastNotifKey) return
        lastNotifKey = key

        val title = prettyTitle(item)
        val where = if (localActive) "teléfono" else "TV"
        try {
            val md = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "CASTV · $where")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur * 1000L)
            if (cover != null) md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            mediaSession?.setMetadata(md.build())
            mediaSession?.isActive = true
        } catch (e: Exception) { }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun act(action: String, req: Int) =
            PendingIntent.getBroadcast(this, req, Intent(action).setPackage(packageName), flags)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setLargeIcon(cover)
            .setContentText(if (playing) "Sonando en el $where" else "En pausa · $where")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", act(ACT_PREV, 1))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pausa" else "Reproducir", act(ACT_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Siguiente", act(ACT_NEXT, 3))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        try {
            getSystemService(NotificationManager::class.java).notify(1, n)
        } catch (e: Exception) { }
    }

    /** Al cerrar la app, la notificación vuelve a ser la sencilla del servicio. */
    private fun postDefaultNotification() {
        try {
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CASTV activo")
                .setContentText("Listo para transmitir al TV")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
            getSystemService(NotificationManager::class.java).notify(1, n)
        } catch (e: Exception) { }
    }

    // ------------------------------------- Reproducción en el teléfono

    private fun updateOutputButton() {
        btnOutput.setImageResource(if (outPhone) R.drawable.ic_phone else R.drawable.ic_tv)
        // cian = teléfono · blanco = TV conectado · ámbar = buscando / sin conexión con el TV
        btnOutput.imageTintList = ColorStateList.valueOf(
            when {
                outPhone -> accentColor()
                tvOnline -> col(R.color.vlc_text)
                else -> 0xFFFFB74D.toInt()
            })
    }

    private fun withLocal(block: (MediaPlayer) -> Unit) {
        val mp = localPlayer
        if (!localActive || mp == null || !localPrepared) return
        try { block(mp) } catch (e: Exception) { }
    }

    private fun applyLocalVolume() {
        val v = if (muted) 0f else 1f
        try { localPlayer?.setVolume(v, v) } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun startLocal(item: MediaItem, resumeAt: Int) {
        stopLocal()
        // Si el TV estaba reproduciendo algo, se detiene: ahora suena en el teléfono
        thread { try { if (TvController.tvIp != null) TvController.stop() } catch (e: Exception) { } }
        audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        pausedByFocus = false
        pendingResume = 0   // el salto se hace aquí mismo, no hace falta esperar al TV

        val mp = MediaPlayer()
        localPlayer = mp
        localPrepared = false
        localActive = true
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, Uri.parse(item.uriString))
            mp.setOnPreparedListener { p ->
                if (localPlayer === p) {
                    localPrepared = true
                    if (resumeAt > 0) p.seekTo(resumeAt * 1000)
                    applyLocalVolume()
                    p.start()
                    attachEffects(p)
                    applySpeed()
                    wasPlaying = true
                    setStatus(if (resumeAt > 0) "⏩ Retomando en ${fmt(resumeAt)} (teléfono)"
                              else "✅ Reproduciendo en el teléfono")
                }
            }
            mp.setOnCompletionListener { p ->
                if (localPlayer === p) onLocalCompleted()
            }
            mp.setOnErrorListener { p, _, _ ->
                if (localPlayer === p) {
                    stopLocal()
                    adapter.playingLocal = false
                    adapter.notifyDataSetChanged()
                    setStatus("❌ No se pudo reproducir en el teléfono (formato no compatible o archivo dañado)")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopLocal()
            adapter.playingLocal = false
            adapter.notifyDataSetChanged()
            setStatus("❌ No se pudo abrir el archivo en el teléfono. Prueba con otro.")
        }
    }

    @Suppress("DEPRECATION")
    private fun stopLocal() {
        val mp = localPlayer
        localPlayer = null
        localActive = false
        localPrepared = false
        pausedByFocus = false
        releaseEffects()
        if (mp != null) {
            try { mp.release() } catch (e: Exception) { }
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun onLocalCompleted() {
        playingItem?.let { Store.clearResume(it) }
        wasPlaying = false
        adapter.notifyDataSetChanged()
        advanceAfterEnd()
    }

    private fun pollLocal(tick: Int) {
        val mp = localPlayer ?: return
        if (!localPrepared) return
        val pos = mp.currentPosition / 1000
        val rawDur = mp.duration / 1000
        val dur = if (rawDur > 0) rawDur else (playingItem?.durationSec ?: 0)
        lastPos = pos
        lastDur = dur
        runOnUiThread {
            if (!userSeeking && playingItem != null) {
                if (dur > 0) {
                    seekBar.isEnabled = true
                    seekBar.max = dur
                    seekBar.progress = minOf(pos, dur)
                    tvDuration.text = fmt(dur)
                } else {
                    seekBar.isEnabled = false
                    tvDuration.text = "--:--"
                }
                tvElapsed.text = fmt(pos)
            }
        }
        if (tick % 4 == 1 && !volTouching) {
            val mx = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (mx > 0) runOnUiThread { if (!volTouching) volSeek.progress = cur * 100 / mx }
        }
        if (mp.isPlaying) {
            wasPlaying = true
            if (tick % 6 == 0) saveProgress()
        }
        refreshNotification()
    }

    // ------------------------------------- Favoritos, continuar, cola

    private fun bindThumb(item: MediaItem, thumb: ImageView) {
        val tkey = Thumbs.key(item)
        thumb.tag = tkey
        val cached = Thumbs.cached(item)
        if (cached != null) {
            thumb.setImageBitmap(cached)
            thumb.visibility = View.VISIBLE
        } else {
            thumb.setImageDrawable(null)
            thumb.visibility = View.GONE
            if (!Thumbs.isKnownMissing(item)) {
                Thumbs.load(this, item) { bmp ->
                    if (bmp != null && thumb.tag == tkey) {
                        thumb.setImageBitmap(bmp)
                        thumb.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /** Carátula + estrella del mini reproductor de abajo. */
    private fun bindNowPlaying(item: MediaItem) {
        nowThumbBox.visibility = View.VISIBLE
        nowIcon.setImageResource(if (item.isMusic) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
        bindThumb(item, nowThumb)
        updateNowFav()
    }

    private fun updateNowFav() {
        val fav = playingItem?.let { Store.isFav(it) } ?: false
        btnNowFav.text = if (fav) "★" else "☆"
        btnNowFav.setTextColor(if (fav) 0xFFFFC107.toInt() else col(R.color.vlc_muted))
    }

    private fun toggleFav(item: MediaItem) {
        Store.toggleFav(item)
        updateNowFav()
        if (viewMode == ViewMode.FAVS) applyFilters() else adapter.notifyDataSetChanged()
    }

    private fun updateQueueBadge() {
        navQueue.text = if (queue.isEmpty()) "Cola" else "Cola (${queue.size})"
        val c = if (queue.isEmpty()) col(R.color.vlc_muted) else accentColor()
        navQueue.setTextColor(c)
        navQueue.compoundDrawableTintList = ColorStateList.valueOf(c)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Guarda el punto de reproducción de videos largos / mezclas de audio. */
    private fun saveProgress() {
        val item = playingItem ?: return
        if (!wasPlaying) return
        val pos = lastPos
        val dur = lastDur
        val long = if (item.isMusic) dur >= 1200 else dur >= 300
        if (!long || pendingResume > 0) return
        if (pos >= 10 && pos < dur - 30) Store.saveResume(item, pos)
        else if (pos >= dur - 30) Store.clearResume(item)
    }

    private fun playFromList(idx: Int) {
        val item = currentList[idx]
        val saved = Store.resumeAt(item)
        fun go(startAt: Int) {
            playlist = ArrayList(currentList)
            playIndex = idx
            playingFromQueue = false
            startPlayback(item, startAt)
        }
        if (saved >= 10) {
            AlertDialog.Builder(this)
                .setTitle(prettyTitle(item))
                .setItems(arrayOf("⏩ Continuar desde ${fmt(saved)}", "↺ Desde el inicio")) { _, which ->
                    go(if (which == 0) saved else 0)
                }
                .show()
        } else {
            go(0)
        }
    }

    // ---------------------------------------------- Selección y borrado

    private val reqDelete = 4711
    private val reqDeleteOne = 4712
    private val reqVideo = 4713
    private val reqM3u = 4714   // [Etapa 13-B] listas M3U
    private val reqBackupOut = 4716   // [Respaldo JSON] exportar
    private val reqBackupIn = 4717    // [Respaldo JSON] importar
    private val reqSettings = 4718    // [UI-1b] pantalla Ajustes
    private var accentAtStart = 0     // [UI-1b] color con el que se dibujó esta pantalla
    private var selecting = false
    private val selectedIds = HashSet<Long>()
    private var actionMode: ActionMode? = null
    private var pendingDelete: List<MediaItem> = emptyList()   // Android 11+: todo el grupo
    private val deleteQueue = ArrayList<MediaItem>()           // Android 10 o menos: uno por uno
    private val deletedItems = ArrayList<MediaItem>()
    private var failedCount = 0
    private var lastWasTrash = false

    private fun updateSelectionTitle() {
        actionMode?.title = if (selectedIds.size == 1) "1 seleccionado" else "${selectedIds.size} seleccionados"
    }

    private fun toggleSelect(item: MediaItem) {
        if (!selectedIds.remove(item.id)) selectedIds.add(item.id)
        if (selectedIds.isEmpty()) {
            actionMode?.finish()
            return
        }
        updateSelectionTitle()
        actionMode?.invalidate()   // [SEL-1] actualiza el texto ★/☆ de la barra
        adapter.notifyDataSetChanged()
    }

    private fun startSelection(first: MediaItem) {
        selectedIds.clear()
        selectedIds.add(first.id)
        selecting = true
        actionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 1, 0, "Todo").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, 2, 1, "🗑 Borrar").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                // [SEL-1] acciones en bloque (van al menú ⋮ de la barra)
                menu.add(0, 3, 2, "➕ Añadir a la cola").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 4, 3, "★ Añadir a favoritos").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 5, 4, "📃 Añadir a lista…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                mode.title = "1 seleccionado"
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                // [SEL-1] ★ si falta alguno por marcar; ☆ solo cuando todos ya son favoritos
                val sel = selectedItems()
                val allFav = sel.isNotEmpty() && sel.all { Store.isFav(it) }
                menu.findItem(4)?.title = if (allFav) "☆ Quitar de favoritos" else "★ Añadir a favoritos"
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    1 -> {
                        for (m in currentList) selectedIds.add(m.id)
                        updateSelectionTitle()
                        actionMode?.invalidate()
                        adapter.notifyDataSetChanged()
                    }
                    2 -> confirmDelete(currentList.filter { it.id in selectedIds })
                    3 -> bulkToQueue()   // [SEL-1]
                    4 -> bulkFav()       // [SEL-1]
                    5 -> bulkToList()    // [SEL-1]
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                selecting = false
                selectedIds.clear()
                actionMode = null
                adapter.notifyDataSetChanged()
            }
        })
        adapter.notifyDataSetChanged()
    }

    // ------------------------------------------- [SEL-1] Acciones en bloque

    /** Los seleccionados, en el orden en que se ven en la lista. */
    private fun selectedItems(): List<MediaItem> = currentList.filter { it.id in selectedIds }

    private fun countText(n: Int, one: String, many: String): String =
        if (n == 1) "1 $one" else "$n $many"

    private fun bulkToQueue() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        var added = 0
        for (m in sel) {
            if (queue.none { it.id == m.id && it.isMusic == m.isMusic }) {
                queue.add(m)
                added++
            }
        }
        val skipped = sel.size - added
        updateQueueBadge()
        actionMode?.finish()
        toast("➕ " + countText(added, "añadida", "añadidas") + " a la cola" +
            (if (skipped > 0) " · $skipped ya estaban" else ""))
    }

    private fun bulkFav() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        val remove = sel.all { Store.isFav(it) }   // todos ya son favoritos -> se quitan
        var n = 0
        for (m in sel) {
            if (Store.isFav(m) == remove) {
                Store.toggleFav(m)
                n++
            }
        }
        actionMode?.finish()
        updateNowFav()
        if (viewMode == ViewMode.FAVS) applyFilters() else adapter.notifyDataSetChanged()
        toast(if (remove) "☆ " + countText(n, "quitada", "quitadas") + " de favoritos"
              else "★ " + countText(n, "añadida", "añadidas") + " a favoritos")
    }

    private fun bulkToList() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        fun addAllTo(listName: String) {
            var added = 0
            for (m in sel) if (Store.addToList(listName, m)) added++
            val skipped = sel.size - added
            actionMode?.finish()
            toast("➕ " + countText(added, "añadida", "añadidas") + " a «$listName»" +
                (if (skipped > 0) " · $skipped ya estaban" else ""))
        }
        val names = Store.listNames()
        val labels = ArrayList<String>()
        labels.addAll(names)
        labels.add("➕ Nueva lista…")
        AlertDialog.Builder(this)
            .setTitle("Añadir ${sel.size} a lista")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == names.size) promptNewList { n -> addAllTo(n) }
                else addAllTo(names[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun uriOf(item: MediaItem): Uri {
        val base = if (item.isMusic) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                   else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(base, item.id)
    }

    /** Android 11+: se puede mandar a la papelera del sistema (se recupera en Galería / Mis archivos). */
    private fun useTrash(): Boolean =
        Build.VERSION.SDK_INT >= 30 &&
            getSharedPreferences("castv", MODE_PRIVATE).getBoolean("trash", true)

    private fun toggleTrash() {
        val now = !useTrash()
        getSharedPreferences("castv", MODE_PRIVATE).edit().putBoolean("trash", now).apply()
        toast(if (now) "🗑 Lo borrado irá a la papelera de Android"
              else "⚠️ Lo borrado se eliminará para siempre")
    }

    private fun confirmDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val trash = useTrash()
        val what = if (items.size == 1) "«${prettyTitle(items[0])}»" else "${items.size} archivos"
        val msg = if (trash) "$what irá a la papelera de Android. Se puede recuperar desde Galería o Mis archivos durante unos 30 días."
                  else "$what se eliminará del teléfono. No se puede deshacer."
        AlertDialog.Builder(this)
            .setTitle(if (trash) "¿Mover a la papelera?" else "¿Eliminar?")
            .setMessage(msg)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (trash) "A la papelera" else "Eliminar") { _, _ -> deleteItems(items) }
            .show()
    }

    private fun stopCurrentPlayback() {
        manualStop = true
        if (localActive) stopLocal() else thread { TvController.stop() }
        playingItem = null
        linkOnTv = false; linkDurSec = 0   // [PAUSA-1]
        resetProgress()
        lastNotifKey = ""
        postDefaultNotification()
    }

    private fun deleteItems(items: List<MediaItem>) {
        val list = ArrayList(items)
        actionMode?.finish()
        val now = playingItem
        if (now != null && list.any { it.id == now.id && it.isMusic == now.isMusic }) stopCurrentPlayback()
        deletedItems.clear()
        failedCount = 0
        lastWasTrash = false
        if (Build.VERSION.SDK_INT >= 30) {
            // Una sola confirmación del sistema para todo el grupo
            pendingDelete = list
            lastWasTrash = useTrash()
            try {
                val uris = list.map { uriOf(it) }
                val pi = if (lastWasTrash) MediaStore.createTrashRequest(contentResolver, uris, true)
                         else MediaStore.createDeleteRequest(contentResolver, uris)
                startIntentSenderForResult(pi.intentSender, reqDelete, null, 0, 0, 0)
            } catch (e: Exception) {
                toast("No se pudo pedir el permiso: ${e.message}")
            }
        } else {
            deleteQueue.clear()
            deleteQueue.addAll(list)
            deleteNext()
        }
    }

    /** Android 10 o anterior: borra uno por uno; si el sistema pide permiso, se continúa en onActivityResult. */
    private fun deleteNext() {
        while (deleteQueue.isNotEmpty()) {
            val item = deleteQueue[0]
            try {
                contentResolver.delete(uriOf(item), null, null)
                deleteQueue.removeAt(0)
                deletedItems.add(item)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    try {
                        startIntentSenderForResult(
                            e.userAction.actionIntent.intentSender, reqDeleteOne, null, 0, 0, 0)
                        return
                    } catch (x: Exception) { }
                }
                deleteQueue.removeAt(0)
                failedCount++
            } catch (e: Exception) {
                deleteQueue.removeAt(0)
                failedCount++
            }
        }
        finishDelete()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            reqDelete -> {
                if (resultCode == RESULT_OK) deletedItems.addAll(pendingDelete)
                pendingDelete = emptyList()
                finishDelete()
            }
            reqBackupOut -> {
                val u = data?.data
                if (resultCode == RESULT_OK && u != null) doExport(u)
            }
            reqBackupIn -> {
                val u = data?.data
                if (resultCode == RESULT_OK && u != null) confirmImport(u)
            }
            reqSettings -> {
                // [UI-1b] Ajustes pide abrir el respaldo o las redes
                when (data?.getStringExtra("action")) {
                    "backup" -> showBackupMenu()
                    "socials" -> showSocials()
                }
            }
            reqM3u -> {
                // [Etapa 13-B] canal elegido en Listas M3U: se reproduce con "Reproducir enlace"
                if (resultCode == RESULT_OK && data != null && data.hasExtra("m3uUrl")) {
                    playLink(
                        data.getStringExtra("m3uUrl") ?: "",
                        data.getBooleanExtra("m3uTo", false),
                        data.getStringExtra("m3uTitle"))
                }
            }
            reqVideo -> {
                // 📺 TV desde el reproductor del teléfono: se manda ese video al TV desde el mismo punto
                // [Etapa 13-A] 📺 desde un enlace: se manda la misma URL al TV
                if (resultCode == RESULT_OK && data != null && data.hasExtra("tvUrl")) {
                    playLink(data.getStringExtra("tvUrl") ?: "", true)
                } else
                if (resultCode == RESULT_OK && data != null && data.hasExtra("tvId")) {
                    val id = data.getLongExtra("tvId", 0L)
                    val base = MediaItem(
                        id = id, name = data.getStringExtra("tvName") ?: "", uriString = "",
                        isMusic = false, durationSec = data.getIntExtra("tvDur", 0))
                    val item = playlist.firstOrNull { it.id == id && !it.isMusic }
                        ?: base.copy(uriString = uriOf(base).toString())
                    outPhone = false
                    getSharedPreferences("castv", MODE_PRIVATE).edit().putBoolean("outPhone", false).apply()
                    updateOutputButton()
                    playIndex = playlist.indexOfFirst { it.id == id && !it.isMusic }
                    startPlayback(item, data.getIntExtra("tvPos", 0))
                }
            }
            reqDeleteOne -> {
                val item = deleteQueue.firstOrNull()
                if (item != null) {
                    if (resultCode == RESULT_OK) {
                        try {
                            contentResolver.delete(uriOf(item), null, null)
                            deletedItems.add(item)
                        } catch (e: Exception) { failedCount++ }
                    } else {
                        failedCount++
                    }
                    deleteQueue.removeAt(0)
                }
                deleteNext()
            }
        }
    }

    private fun finishDelete() {
        val gone = ArrayList(deletedItems)
        deletedItems.clear()
        val failed = failedCount
        failedCount = 0
        if (gone.isNotEmpty()) afterDeleted(gone)
        toast(
            if (gone.isEmpty()) (if (lastWasTrash) "No se movió nada" else "No se eliminó nada")
            else if (failed > 0) "🗑 Eliminados: ${gone.size} · sin permiso: $failed"
            else if (lastWasTrash) "🗑 En la papelera: ${gone.size}"
            else "🗑 Eliminados: ${gone.size}"
        )
    }

    /** Quita los archivos borrados de la lista, la cola, favoritos, listas, puntos guardados y estadísticas. */
    private fun afterDeleted(gone: List<MediaItem>) {
        val ids = HashSet<String>()
        for (g in gone) ids.add((if (g.isMusic) "a" else "v") + g.id)
        fun isGone(m: MediaItem) = ids.contains((if (m.isMusic) "a" else "v") + m.id)
        queue.removeAll { isGone(it) }
        updateQueueBadge()
        playlist = playlist.filter { !isGone(it) }
        val cur = playingItem
        playIndex = if (cur == null) -1 else playlist.indexOfFirst { it.id == cur.id && it.isMusic == cur.isMusic }
        allItems = allItems.filter { !isGone(it) }
        for (g in gone) {
            if (allItems.none { Store.key(it) == Store.key(g) }) Store.forget(g)
        }
        dupIds = computeDups(allItems)
        applyFilters()
    }

    /** Menú de una fila (botón ⋮). */
    private fun showItemMenu(item: MediaItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add("➕ Añadir a la cola")
        actions.add {
            queue.add(item); updateQueueBadge(); toast("➕ En cola: ${prettyTitle(item)}")
        }
        labels.add("⏭ Reproducir a continuación")
        actions.add {
            queue.add(0, item); updateQueueBadge(); toast("⏭ Sonará después de este")
        }
        labels.add(if (Store.isFav(item)) "★ Quitar de favoritos" else "☆ Añadir a favoritos")
        actions.add { toggleFav(item) }
        labels.add("📃 Añadir a lista…")
        actions.add { addToListDialog(item) }
        selectedPlaylist?.let { pl ->
            labels.add("➖ Quitar de «$pl»")
            actions.add { Store.removeFromList(pl, item); applyFilters() }
        }
        labels.add("🗑 Eliminar del teléfono…")
        actions.add { confirmDelete(listOf(item)) }
        if (Store.resumeAt(item) >= 10) {
            labels.add("↺ Olvidar el punto guardado")
            actions.add { Store.clearResume(item); applyFilters() }
        }
        AlertDialog.Builder(this)
            .setTitle(prettyTitle(item))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    /** Cola de reproducción: tocar = reproducir ya, ▲▼ = reordenar, ✕ = quitar. */
    private fun showQueue() {
        if (queue.isEmpty()) {
            toast("La cola está vacía. Mantén pulsado un archivo para añadirlo.")
            return
        }
        val lv = ListView(this)
        val qAdapter = object : BaseAdapter() {
            override fun getCount() = queue.size
            override fun getItem(position: Int) = queue[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_queue, parent, false)
                v.findViewById<TextView>(R.id.qTitle).text = "${position + 1}. ${prettyTitle(queue[position])}"
                val up = v.findViewById<TextView>(R.id.qUp)
                val down = v.findViewById<TextView>(R.id.qDown)
                val rm = v.findViewById<TextView>(R.id.qRemove)
                up.alpha = if (position == 0) 0.3f else 1f
                down.alpha = if (position == queue.size - 1) 0.3f else 1f
                up.setOnClickListener {
                    if (position > 0) {
                        val it2 = queue.removeAt(position); queue.add(position - 1, it2)
                        notifyDataSetChanged()
                    }
                }
                down.setOnClickListener {
                    if (position < queue.size - 1) {
                        val it2 = queue.removeAt(position); queue.add(position + 1, it2)
                        notifyDataSetChanged()
                    }
                }
                rm.setOnClickListener {
                    queue.removeAt(position)
                    updateQueueBadge()
                    notifyDataSetChanged()
                }
                return v
            }
        }
        lv.adapter = qAdapter
        val dialog = AlertDialog.Builder(this)
            .setTitle("Cola de reproducción")
            .setView(lv)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Vaciar") { _, _ -> queue.clear(); updateQueueBadge() }
            .create()
        lv.setOnItemClickListener { _, _, position, _ ->
            if (position in queue.indices) {
                val item = queue.removeAt(position)
                updateQueueBadge()
                playingFromQueue = true
                startPlayback(item)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { updateQueueBadge() }
        dialog.show()
    }

    // ------------------------------------------- Temporizador de apagado

    private fun pauseAll() {
        if (localActive) {
            withLocal { it.pause() }
            saveProgress()
            updateQuickIcon(false)
        } else {
            thread { TvController.pause() }
        }
        lastNotifKey = ""
    }

    private fun checkSleep() {
        val t = sleepAtMs
        if (t > 0 && SystemClock.elapsedRealtime() >= t) {
            sleepAtMs = 0L
            runOnUiThread {
                pauseAll()
                toast("😴 Temporizador: reproducción en pausa")
            }
        }
    }

    /** true si había que parar al terminar la pista (y ya no se sigue con la siguiente). */
    private fun consumeSleepAfterTrack(): Boolean {
        if (!sleepAfterTrack) return false
        sleepAfterTrack = false
        runOnUiThread { toast("😴 Temporizador: terminó la pista") }
        return true
    }

    private fun sleepLabel(): String {
        if (sleepAfterTrack) return "Temporizador: al terminar la pista"
        val t = sleepAtMs
        if (t > 0) {
            val min = ((t - SystemClock.elapsedRealtime()) / 60000L).toInt() + 1
            return "Temporizador: $min min"
        }
        return "Temporizador de apagado"
    }

    private fun pickSleepTimer() {
        val labels = arrayListOf("15 minutos", "30 minutos", "45 minutos", "1 hora", "1 hora 30 min", "Al terminar la pista")
        val mins = intArrayOf(15, 30, 45, 60, 90)
        if (sleepAtMs > 0 || sleepAfterTrack) labels.add("✕ Cancelar temporizador")
        AlertDialog.Builder(this)
            .setTitle("😴 Temporizador de apagado")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < mins.size -> {
                        sleepAfterTrack = false
                        sleepAtMs = SystemClock.elapsedRealtime() + mins[which] * 60000L
                        toast("😴 Se pausará en " + labels[which])
                    }
                    which == mins.size -> {
                        sleepAtMs = 0L
                        sleepAfterTrack = true
                        toast("😴 Se pausará al terminar esta pista")
                    }
                    else -> {
                        sleepAtMs = 0L
                        sleepAfterTrack = false
                        toast("Temporizador cancelado")
                    }
                }
            }
            .show()
    }

    // ------------------------------------------- Velocidad y ecualizador (teléfono)

    private fun speedShort(): String {
        val i = speedValues.indexOfFirst { it == speed }
        return if (i >= 0) speedLabels[i].substringBefore(' ') else "${speed}x"
    }

    /** Solo se toca el reproductor cuando ya está sonando (en pausa algunos teléfonos lo arrancan solo). */
    private fun applySpeed() {
        if (!speedTouched) return
        withLocal { mp ->
            if (mp.isPlaying) mp.playbackParams = PlaybackParams().setSpeed(speed)
        }
    }

    private fun pickSpeed() {
        if (!outPhone && !localActive) {
            toast("La velocidad solo funciona con la música en el teléfono")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Velocidad de reproducción")
            .setSingleChoiceItems(speedLabels, speedValues.indexOfFirst { it == speed }) { d, which ->
                speed = speedValues[which]
                speedTouched = true
                applySpeed()
                d.dismiss()
            }
            .show()
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        equalizer = null
        bassBoost = null
    }

    private fun attachEffects(mp: MediaPlayer) {
        releaseEffects()
        try {
            val eq = Equalizer(0, mp.audioSessionId)
            val n = eq.numberOfPresets.toInt()
            eqNames = (0 until n).map { i -> eq.getPresetName(i.toShort()) }
            if (eqPreset in 0 until n) {
                eq.usePreset(eqPreset.toShort())
                eq.setEnabled(true)
            } else {
                eq.setEnabled(false)
            }
            equalizer = eq
        } catch (e: Exception) {
            equalizer = null
        }
        try {
            val bb = BassBoost(0, mp.audioSessionId)
            if (bassOn && bb.strengthSupported) {
                bb.setStrength(800.toShort())
                bb.setEnabled(true)
            } else {
                bb.setEnabled(false)
            }
            bassBoost = bb
        } catch (e: Exception) {
            bassBoost = null
        }
    }

    private fun showEqualizer() {
        if (eqNames.isEmpty()) {
            toast("Pon una pista en el teléfono y vuelve a abrir el ecualizador")
            return
        }
        val labels = ArrayList<String>()
        labels.add("Sin ecualizador")
        labels.addAll(eqNames)
        labels.add(if (bassOn) "🔊 Graves reforzados: SÍ (toca para quitar)" else "🔊 Reforzar graves")
        AlertDialog.Builder(this)
            .setTitle("🎚 Ecualizador")
            .setSingleChoiceItems(labels.toTypedArray(), eqPreset + 1) { d, which ->
                if (which == labels.size - 1) {
                    bassOn = !bassOn
                } else {
                    eqPreset = which - 1
                }
                getSharedPreferences("castv", MODE_PRIVATE).edit()
                    .putInt("eqPreset", eqPreset).putBoolean("bassOn", bassOn).apply()
                if (localActive && localPrepared) localPlayer?.let { attachEffects(it) }
                d.dismiss()
            }
            .show()
    }

    // ------------------------------------------- Listas de reproducción

    private fun promptNewList(onCreated: (String) -> Unit) {
        val et = EditText(this)
        et.hint = "Nombre de la lista"
        et.setSingleLine(true)
        AlertDialog.Builder(this)
            .setTitle("Nueva lista")
            .setView(et)
            .setPositiveButton("Crear") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty()) {
                    toast("Escribe un nombre")
                } else if (!Store.createList(n)) {
                    toast("Ya existe una lista con ese nombre")
                } else {
                    onCreated(n)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPlaylists() {
        val names = Store.listNames()
        val labels = ArrayList<String>()
        for (n in names) labels.add("📃 $n (${Store.listSize(n)})")
        labels.add("➕ Nueva lista…")
        AlertDialog.Builder(this)
            .setTitle("Mis listas")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == names.size) {
                    promptNewList { n -> toast("Lista «$n» creada. Añade pistas con ⋮ › Añadir a lista") }
                } else {
                    openPlaylistMenu(names[which])
                }
            }
            .show()
    }

    private fun openPlaylistMenu(name: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("📂 Abrir", "🗑 Eliminar lista")) { _, which ->
                if (which == 0) {
                    selectedPlaylist = name
                    viewMode = ViewMode.ALL
                    applyFilters()
                } else {
                    Store.deleteList(name)
                    if (selectedPlaylist == name) {
                        selectedPlaylist = null
                        applyFilters()
                    }
                    toast("Lista eliminada")
                }
            }
            .show()
    }

    private fun addToListDialog(item: MediaItem) {
        val names = Store.listNames()
        val labels = ArrayList<String>()
        for (n in names) labels.add((if (Store.inList(n, item)) "✓ " else "") + n)
        labels.add("➕ Nueva lista…")
        AlertDialog.Builder(this)
            .setTitle("Añadir a lista")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == names.size) {
                    promptNewList { n ->
                        Store.addToList(n, item)
                        toast("➕ Añadida a «$n»")
                    }
                } else {
                    val n = names[which]
                    if (Store.addToList(n, item)) toast("➕ Añadida a «$n»") else toast("Ya estaba en «$n»")
                }
            }
            .show()
    }

    // ------------------------------------------- Elegir TV

    private fun pickTv() {
        setStatus("🔎 Buscando aparatos en la red...")
        thread {
            val tvs = TvController.scanAll()
            runOnUiThread {
                if (tvs.isEmpty()) {
                    setStatus("⚠️ No se encontró ningún TV en esta red. ¿Está encendido y en el mismo Wi-Fi?")
                } else {
                    val labels = tvs.map { t ->
                        (if (t.ip == TvController.tvIp) "✓ " else "") + t.name + "  (" + t.ip + ")"
                    }
                    setStatus("Elige el aparato donde quieres reproducir")
                    AlertDialog.Builder(this)
                        .setTitle("Elegir TV")
                        .setItems(labels.toTypedArray()) { _, which ->
                            val tv = tvs[which]
                            getSharedPreferences("castv", MODE_PRIVATE).edit().putString("tvIp", tv.ip).apply()
                            setStatus("🔌 Conectando con ${tv.name}...")
                            thread {
                                TvController.select(tv)
                                tvFails = 0
                                tvOnline = true
                                runOnUiThread {
                                    updateOutputButton()
                                    setStatus("✅ TV: " + TvController.tvName.ifEmpty { tv.ip })
                                }
                            }
                        }
                        .show()
                }
            }
        }
    }

    // ---------------------------------------------------------- Adaptador

    private inner class FileAdapter : BaseAdapter() {
        var items: List<MediaItem> = emptyList()
        var playingId: Long = -1
        var playingMusic: Boolean = false
        var playingLocal: Boolean = false

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            val item = items[position]
            val isPlaying = item.id == playingId && item.isMusic == playingMusic
            val sel = selecting && item.id in selectedIds
            v.findViewById<TextView>(R.id.itemName).text = (if (sel) "✓ " else "") + prettyTitle(item)
            val section = v.findViewById<TextView>(R.id.itemSection)
            val byName = sortMode == Sort.NAME_ASC || sortMode == Sort.NAME_DESC
            val letter = sectionLetter(item.name)
            if (byName && (position == 0 || sectionLetter(items[position - 1].name) != letter)) {
                section.text = letter
                section.visibility = View.VISIBLE
            } else {
                section.visibility = View.GONE
            }
            val icon = v.findViewById<ImageView>(R.id.itemIcon)
            val thumb = v.findViewById<ImageView>(R.id.itemThumb)
            icon.setImageResource(if (item.isMusic) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
            bindThumb(item, thumb)
            v.findViewById<View>(R.id.itemMore).setOnClickListener { showItemMenu(item) }
            val isFav = Store.isFav(item)
            val action = v.findViewById<TextView>(R.id.itemAction)
            if (isPlaying) {
                action.text = if (playingLocal) "▶ Reproduciendo en el teléfono" else "▶ Reproduciendo en el TV"
                action.setTextColor(accentColor())
            } else {
                val saved = Store.resumeAt(item)
                val parts = ArrayList<String>()
                parts.add(if (item.artist.isNotBlank()) item.artist else item.folder.substringAfterLast('/'))
                if (saved >= 10 && item.durationSec > 0) parts.add("⏩ ${fmt(saved)} / ${fmt(item.durationSec)}")
                else if (item.durationSec > 0) parts.add(fmt(item.durationSec))
                if (item.id in dupIds) parts.add("duplicado")
                if (sortMode == Sort.MOST_PLAYED) {
                    val c = Store.playCount(item)
                    if (c > 0) parts.add("▶ ${c}×")
                }
                action.text = (if (isFav) "★ " else "") + parts.joinToString("  ·  ")
                action.setTextColor(if (saved >= 10) accentColor() else col(R.color.vlc_muted))
            }
            action.visibility = View.VISIBLE
            v.findViewById<View>(R.id.itemRow).setBackgroundResource(
                if (isPlaying || sel) R.drawable.item_bg_playing else R.drawable.item_bg)
            return v
        }
    }
}
