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

class MainActivity : CastvActivity() {   // [TEXTO-TAM-1]

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
    private lateinit var tabWork: TextView   // [TALLER-1]
    private lateinit var tvIncog: TextView   // [INCOG-2]
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
    private lateinit var nowBigArt: ImageView   // [UI-3a]
    private var fullDialog: android.app.Dialog? = null   // [UI-3b] pantalla completa "Reproduciendo"
    private val fullHandler = android.os.Handler(android.os.Looper.getMainLooper())   // [UI-3b]
    private lateinit var tvFilter: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvEmptyAction: TextView   // [VACIO-1]
    private lateinit var tvSearchChannels: TextView   // [BUSCA-1]
    private lateinit var btnPlay: ImageButton
    private lateinit var fab: View
    private lateinit var footerView: View
    private var footerAttached = true
    private var fabShown = true

    private enum class Sort(val label: String) {
        NAME_ASC("↕ A-Z"), NAME_DESC("↕ Z-A"), NEWEST("↕ Recientes"),
        LONGEST("↕ Más largos"), SHORTEST("↕ Más cortos"),
        MOST_PLAYED("↕ Más reproducidas"), RECENT_PLAYED("↕ Reproducidas hace poco"),
        NEVER_PLAYED("↕ Sin reproducir primero"),   // [LISTAS-INTEL-1] al final, para no mover los números ya guardados
        MOST_MONTH("↕ Más escuchadas del mes")   // [MES-1] también al final
    }

    private enum class Repeat { OFF, ALL, ONE }

    private enum class ViewMode(val label: String) {
        ALL("Ver: Todos"), FAVS("Ver: ⭐ Favoritos"), RESUME("Ver: ⏩ Continuar"),
        GROUPS("Ver: Álbumes / carpetas"), LISTS("Ver: Listas"),   // [TABS-1]
        WORKSHOP("Ver: Taller")   // [TALLER-1]
    }

    // Salida de la música: false = TV, true = el propio teléfono (los videos siempre van al TV)
    private var outPhone = false
    // [SERVICIO-1a] El reproductor y su estado viven ahora en PlayerCore (fuera de la pantalla); aquí solo se leen y escriben.
    private var localPlayer: MediaPlayer?
        get() = PlayerCore.player
        set(v) { PlayerCore.player = v }
    private var localActive: Boolean
        get() = PlayerCore.active
        set(v) { PlayerCore.active = v }
    private var localPrepared: Boolean
        get() = PlayerCore.prepared
        set(v) { PlayerCore.prepared = v }
    private var pausedByFocus: Boolean
        get() = PlayerCore.pausedByFocus
        set(v) { PlayerCore.pausedByFocus = v }
    private lateinit var audioManager: AudioManager

    private val focusListener = PlayerCore.focusListener   // [SERVICIO-1a]

    // Controles de la notificación
    companion object {
        const val ACT_PREV = "com.castv.app.ACT_PREV"
        const val ACT_TOGGLE = "com.castv.app.ACT_TOGGLE"
        const val ACT_NEXT = "com.castv.app.ACT_NEXT"
        const val ACT_STOP_LOCAL = "com.castv.app.ACT_STOP_LOCAL"   // [LOCAL-1]
        const val ACT_CHANNEL_ON_TV = "com.castv.app.ACT_CHANNEL_ON_TV"   // [NOW-1]
        const val ACT_FAV = "com.castv.app.ACT_FAV"   // [FAV-REPRO-3] ★ desde la notificación del canal
        const val SC_RESUME = "com.castv.app.SC_RESUME"       // [ATAJOS-1] atajos del icono
        const val SC_MUSIC = "com.castv.app.SC_MUSIC"
        const val SC_CHANNELS = "com.castv.app.SC_CHANNELS"
        const val CHANNEL_ID = "castv_channel"
        const val FB_URL = "https://www.facebook.com/share/19x6GMsWUM/"
        const val IG_URL = "https://www.instagram.com/arieldejesusbuen"
        const val TT_URL = "https://www.tiktok.com/@arieldejesusbueno0"
    }
    @Volatile private var tvPlaying = false
    private var lastNotifKey = ""
    private var notifReceiverRegistered = false
    // [FOCO-2] la música del teléfono se pausa al desconectar audífonos / Bluetooth
    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (localActive && localIsPlaying()) togglePlayPause()   // solo música del teléfono; el TV no se toca
        }
    }
    private var mediaSession: MediaSessionCompat? = null

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACT_PREV -> step(-1)
                ACT_NEXT -> step(+1)
                ACT_TOGGLE -> togglePlayPause()
                ACT_STOP_LOCAL -> if (localActive) stopCurrentPlayback()   // [LOCAL-1]
                ACT_FAV -> tvChannelNow()?.let { ch -> toggleTvChannelFav(ch) }   // [FAV-REPRO-3]
                ACT_CHANNEL_ON_TV -> {   // [NOW-1] un canal de Listas M3U acaba de empezar en el TV
                    if (playingItem != null) {   // había algo propio en el TV: el canal lo reemplaza (sin TvController.stop())
                        playingItem = null
                        manualStop = true
                        adapter.playingId = -1
                        adapter.notifyDataSetChanged()
                    }
                    val t = intent?.getStringExtra("title") ?: "Canal"
                    Zap.confirm(t)   // [ZAP-1]
                    linkOnTv = true; linkDurSec = 0; linkGen++
                    reSeenPlaying = false; reStopAt = 0L   // [AUTO-REENVIO-1] canal nuevo (o reenviado): se empieza a vigilar de cero
                    linkTitleNow = t
                    linkUrlNow = intent?.getStringExtra("url")   // [FAV-REPRO-3]
                    Zap.nowTitle = t; Zap.nowUrl = linkUrlNow   // [CANAL-MINI-1]
                    linkIsLive = intent?.getBooleanExtra("live", false) ?: false
                    resetProgress()
                    tvNowPlaying.text = t
                    nowBigArt.tag = null; nowBigArt.visibility = View.GONE   // [UI-3a] un canal no tiene carátula
                    lastNotifKey = ""
                    updateNowFav(); updateMiniCompact()   // [FAV-REPRO-3] la ★ del mini reproductor sigue al canal
                    refreshNotification()
                }
            }
        }
    }

    private var viewMode = ViewMode.ALL
    private var groupReturn: ViewMode? = null   // [TABS-1] pestaña (Álbumes/Carpetas/Listas) a la que vuelve ✕ o Atrás
    private var groupsShown = false             // [TABS-1] la lista muestra grupos y no archivos
    private val groupAdapter by lazy { GroupAdapter() }   // [TABS-1]
    private var onlyUnseen = false   // [NOVISTOS-1] filtro "solo no vistos" (solo videos; no se guarda)
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
    @Volatile private var linkTitleNow: String? = null   // [NOW-1] título del canal/enlace que está en el TV
    @Volatile private var linkIsLive = false             // [NOW-1] ese enlace es un canal en vivo (HLS)
    @Volatile private var linkUrlNow: String? = null     // [FAV-REPRO-3] enlace original del canal que está en el TV (para la ★)
    @Volatile private var tvFavNow = false               // [FAV-REPRO-3] ese canal es favorito (guardado aquí para no leer el disco en cada tic)
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
    private var pendingShortcut: String? = null   // [ATAJOS-1] atajo pulsado que aún no se aplicó
    private var volBeforeMute = -1   // [REMOTO-1b] volumen del TV antes de silenciarlo (-1 = no lo silenció la app)
    @Volatile private var lastPos = 0
    @Volatile private var lastDur = 0

    @Volatile private var manualStop = false
    @Volatile private var wasPlaying = false
    @Volatile private var playStartedAt = 0L
    // [AUTO-REENVIO-1] reenvío automático de un canal en vivo que la TV cortó tras un atasco
    @Volatile private var reSeenPlaying = false   // el canal actual ya llegó a sonar en la TV
    @Volatile private var reStopAt = 0L           // desde cuándo (ms) la TV lo muestra detenido; 0 = no
    @Volatile private var reBusy = false
    private val reTimes = ArrayList<Long>()       // cuándo se reenvió (solo lo toca el hilo del monitor)
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

    /** Color de acento guardado (0 a 9, ver Themes.names). */
    private fun savedAccent(): Int = Themes.saved(this)   // [TEMAS-1]

    private val accentNames get() = Themes.names

    private fun pickAccent() {
        Themes.pick(this) { recreate() }   // [TEMAS-1] vuelve a dibujar la pantalla con el color elegido
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
        Themes.apply(this)   // [TEMAS-1]
        accentAtStart = savedAccent()   // [UI-1b]
        bgAtStart = Themes.savedBg(this)   // [TEMAS-2]
        textAtStart = CastvTextSize.saved(this)   // [TEXTO-TAM-1]
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
        val nf = IntentFilter().apply { addAction(ACT_PREV); addAction(ACT_TOGGLE); addAction(ACT_NEXT); addAction(ACT_STOP_LOCAL); addAction(ACT_CHANNEL_ON_TV); addAction(ACT_FAV) }   // [NOW-1b] [FAV-REPRO-3]
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(notifReceiver, nf, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(notifReceiver, nf)
        notifReceiverRegistered = true
        try {   // [FOCO-2]
            val nz = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, nz, Context.RECEIVER_EXPORTED)
            else registerReceiver(noisyReceiver, nz)
            noisyRegistered = true
        } catch (e: Exception) { }
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
        // [VENT-1] espacio al final de la lista para que el botón 🔀 no tape el ⋮ de la última fila
        listFiles.clipToPadding = false
        listFiles.setPadding(listFiles.paddingLeft, listFiles.paddingTop, listFiles.paddingRight,
            maxOf(listFiles.paddingBottom, (88 * resources.displayMetrics.density).toInt()))
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
        nowBigArt = findViewById(R.id.nowBigArt)   // [UI-3a]
        btnSearch = findViewById(R.id.btnSearch)
        btnMenu = findViewById(R.id.btnMenu)
        searchBar = findViewById(R.id.searchBar)
        tabAll = findViewById(R.id.tabAll)
        tabFavs = findViewById(R.id.tabFavs)
        tabResume = findViewById(R.id.tabResume)
        tabWork = findViewById(R.id.tabWork)   // [TALLER-1]
        tvIncog = findViewById(R.id.tvIncog)   // [INCOG-2]
        // [VERSION-2] La versión de la cabecera sale de la app instalada (ya no es un texto fijo)
        // [VERSION-5] en la cabecera solo el número (sin « (build N)»); Acerca de y Ajustes lo muestran completo
        findViewById<TextView>(R.id.tvVersion).text = try {
            (packageManager.getPackageInfo(packageName, 0).versionName ?: "").substringBefore(" ")
        } catch (e: Exception) { "" }
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
        tvEmptyAction = findViewById(R.id.tvEmptyAction)   // [VACIO-1]
        tvSearchChannels = findViewById(R.id.tvSearchChannels)   // [BUSCA-1]
        tvSearchChannels.setOnClickListener { openChannelsSearch() }
        btnPlay = findViewById(R.id.btnPlay)
        fab = findViewById(R.id.fabShuffle)
        window.decorView.postDelayed({ maybeWelcome() }, 900)   // [BIENVENIDA-1]

        sortMode = Sort.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("sort", 0)) { Sort.NAME_ASC }

        // [SALIDA-INI-1] Salida al abrir la app: 0 = Teléfono (por defecto), 1 = TV, 2 = recordar la última
        val spOut = getSharedPreferences("castv", MODE_PRIVATE)
        outPhone = when (spOut.getInt("out_start", 0)) {
            1 -> false
            2 -> spOut.getBoolean("outPhone", false)
            else -> true
        }
        repeatMode = Repeat.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("repeat", 0)) { Repeat.OFF }
        eqPreset = getSharedPreferences("castv", MODE_PRIVATE).getInt("eqPreset", -1)
        bassOn = getSharedPreferences("castv", MODE_PRIVATE).getBoolean("bassOn", false)
        tvIncog.setOnClickListener { toast("🕵️‍♂️ Incógnito activado. Para quitarlo: Más › Modo incógnito") }   // [INCOG-2]
        updateIncogBadge()
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
            if (!outPhone) offerTvPicker()   // [TV-2]
            if (outPhone && !localActive && playingItem != null && !linkOnTv && Zap.nowTitle == null) offerBringToPhone()   // [TRAER-1]
        }

        adapter = FileAdapter()
        listFiles.adapter = adapter

        navVideo.setOnClickListener { showMovies() }
        navAudio.setOnClickListener { showMusic() }
        navFolders.setOnClickListener { runMenuAction(16) }   // [UI-5] Canales
        navQueue.setOnClickListener { showQueue() }
        navMore.setOnClickListener { showMoreSheet() }   // [UI-1]
        tabAll.setOnClickListener { groupReturn = null; viewMode = ViewMode.ALL; applyFilters() }
        tabFavs.setOnClickListener { groupReturn = null; resetFilters(); workTool = null; viewMode = ViewMode.GROUPS; applyFilters() }   // [TABS-1] Álbumes / Carpetas
        tabResume.setOnClickListener { groupReturn = null; resetFilters(); workTool = null; viewMode = ViewMode.LISTS; applyFilters() }   // [TABS-1] Listas
        tabWork.setOnClickListener { groupReturn = null; resetFilters(); workTool = null; workLastScan = 0L; viewMode = ViewMode.WORKSHOP; applyFilters() }   // [TALLER-1]
        fab.setOnClickListener { shuffleAndPlay() }
        tvFilter.setOnClickListener { clearFilters() }
        listFiles.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                setFabShown(scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && !groupsShown)
            }
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {}
        })
        btnSearch.setOnClickListener { toggleSearch() }
        btnMenu.setOnClickListener {   // [UI-1b] el ⋮ abre la pantalla Ajustes
            startActivityForResult(Intent(this, SettingsActivity::class.java), reqSettings)
        }
        btnQuick.setOnClickListener { if (playingItem == null && !linkOnTv) step(+1) else togglePlayPause() }
        val toggleExpand = View.OnClickListener { setPlayerExpanded(!CastvMotion.isOpen(playerExpanded)) }   // [ANIM-1b]
        btnExpand.setOnClickListener(toggleExpand)
        tvNowPlaying.setOnClickListener(toggleExpand)
        nowThumbBox.setOnClickListener(toggleExpand)
        // [MINI-GESTOS-1] Deslizar sobre el título o la carátula del mini reproductor:
        // ← siguiente, → anterior, ↑ abre el panel, ↓ lo cierra. Los toques normales siguen igual.
        var swX = 0f; var swY = 0f
        val miniSwipe = View.OnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { swX = ev.x; swY = ev.y }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = ev.x - swX
                    val dy = ev.y - swY
                    val min = 80 * resources.displayMetrics.density
                    if (playingItem != null || linkOnTv) {
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > min) {
                            findViewById<View>(if (dx < 0) R.id.btnNext else R.id.btnPrev).performClick()
                        } else if (kotlin.math.abs(dy) > min) {
                            setPlayerExpanded(dy < 0)
                        }
                    }
                }
            }
            false
        }
        tvNowPlaying.setOnTouchListener(miniSwipe)
        nowThumbBox.setOnTouchListener(miniSwipe)
        // [VIBRA-1] Vibración suave al tocar ▶, ⏮ ⏭ y ★ del mini reproductor
        for (id in intArrayOf(R.id.btnPlay, R.id.btnPrev, R.id.btnNext, R.id.btnNowFav)) {
            findViewById<View>(id).setOnTouchListener { v, ev ->
                if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) haptic(v)
                false
            }
        }
        setPlayerExpanded(false)
        applySofa()   // [SOFA-1]
        updateMiniCompact()   // [PULIDO-2b]
        nowBigArt.setOnClickListener { showFullPlayer() }   // [UI-3b] tocar la carátula grande
        btnExpand.setOnLongClickListener { showFullPlayer(); true }   // [UI-3b] o mantener la flecha ˄

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) recentPop?.dismiss()   // [BUSQ-RECIENTES-1]
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                tvSearchChannels.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE   // [BUSCA-1]
                tvSearchChannels.text = "📡 Buscar «$query» en Canales ›"
                applyFilters()
            }
        })
        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Store.addRecentSearch(query)   // [BUSQ-RECIENTES-1]
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }
        btnClearSearch.setOnClickListener { etSearch.setText("") }
        // [BUSQ-RECIENTES-1] Al tocar el buscador vacío salen las últimas búsquedas
        etSearch.setOnClickListener { showRecentSearches() }
        etSearch.setOnFocusChangeListener { _, has -> if (has) etSearch.post { showRecentSearches() } }

        btnNowFav.setOnClickListener {   // [FAV-REPRO-3] con un canal en el TV, la ★ marca el canal
            val cur = playingItem
            if (cur != null) toggleFav(cur) else tvChannelNow()?.let { ch -> toggleTvChannelFav(ch) }
            if (btnNowFav.text.toString() == "★") StarPop.pop(btnNowFav)   // [ANIM-1]
        }
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
                Zap.nowTitle = null; linkOnTv = false; linkDurSec = 0   // [PAUSA-1] tras Detener ya no hay enlace que pausar
            }
            adapter.notifyDataSetChanged()
        }
        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(+1) }
        findViewById<View>(R.id.btnShuffle).setOnClickListener { shuffleAndPlay() }
        btnAuto.setOnClickListener { cycleRepeat() }
        // [PLAYER-TIPS-1] mantener pulsado un botón del reproductor dice qué hace (btnExpand ya tiene su pulsación larga)
        for ((id, tip) in listOf(
            R.id.btnShuffle to "Mezclar y reproducir", R.id.btnPrev to "Anterior", R.id.btnPlay to "Reproducir o pausar",
            R.id.btnStop to "Detener", R.id.btnNext to "Siguiente", R.id.btnBack10 to "Atrás 10 segundos",
            R.id.btnFwd10 to "Adelantar 10 segundos", R.id.btnMute to "Silencio", R.id.btnQuick to "Reproducir o pausar",
            R.id.btnNowFav to "Favorito")) {
            findViewById<View>(id)?.let { androidx.appcompat.widget.TooltipCompat.setTooltipText(it, tip) }
        }

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
            if (localActive) applyLocalVolume()
            else if (m) {   // [REMOTO-1b] la TV ignora SetMute: el silencio es volumen 0 y se guarda el anterior
                volBeforeMute = volSeek.progress
                volSeek.progress = 0
                thread { TvController.setVolume(0) }
            } else if (volBeforeMute >= 0) {
                val back = if (volBeforeMute > 0) volBeforeMute else 10
                volBeforeMute = -1
                volSeek.progress = back
                thread { TvController.setVolume(back) }
            }
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
                } else {
                    leaveMuteIfRaised(v)   // [REMOTO-1b]
                    thread { TvController.setVolume(v) }
                }
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
            if (groupsShown) {   // [TABS-1]
                if (!groupAdapter.grid) groupAdapter.rows.getOrNull(idx)?.let { openGroup(it) }   // [ALBUMES-2] en la cuadrícula toca cada tarjeta
                return@setOnItemClickListener
            }
            if (idx in currentList.indices) {
                if (workTool != null && !selecting) { workPicked(currentList[idx]); return@setOnItemClickListener }   // [TALLER-1]
                if (selecting) toggleSelect(currentList[idx]) else playFromList(idx)
            }
        }
        listFiles.setOnItemLongClickListener { _, _, position, _ ->
            val idx = position - listFiles.headerViewsCount
            if (groupsShown) {   // [TABS-1] mantener pulsada una lista: Abrir / Quitar lista
                groupAdapter.rows.getOrNull(idx)?.let { if (it.kind == 2) openPlaylistMenu(it.key) else if (it.kind == 5) workProjectMenu(it.key) }   // [TALLER-1]
                return@setOnItemLongClickListener true
            }
            if (idx in currentList.indices) {
                if (selecting) toggleSelect(currentList[idx]) else startSelection(currentList[idx])
            }
            true
        }

        updateRepeatButton()
        updateTabs()
        updateQueueBadge()
        if (savedInstanceState == null) {   // [ATAJOS-1] abierta desde un atajo del icono (no al recrearse)
            pendingShortcut = intent?.action?.takeIf { it.startsWith("com.castv.app.SC_") }
            handleReminderTap(intent)   // [AVISO-TV-1]
            handleShareIn(intent)   // [SHARE-IN-1]
        }
        registerShortcuts()   // [ATAJOS-1]
        checkPermissionsAndStart()
    }

    // ------------------------------------------- [ATAJOS-1] Atajos al mantener pulsado el icono

    /** Registra los 3 atajos (Android 7.1+). Se hace al abrir la app; no cambia nada si falla. */
    @android.annotation.TargetApi(Build.VERSION_CODES.N_MR1)
    private fun registerShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
            fun mk(id: String, short: Int, long: Int, icon: Int, act: String, rank: Int) =
                android.content.pm.ShortcutInfo.Builder(this, id)
                    .setShortLabel(getString(short))
                    .setLongLabel(getString(long))
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, icon))
                    .setIntent(Intent(this, MainActivity::class.java).setAction(act))
                    .setRank(rank)
                    .build()
            sm.dynamicShortcuts = listOf(
                mk("sc_resume", R.string.sc_resume_short, R.string.sc_resume_long, R.drawable.ic_sc_video, SC_RESUME, 0),
                mk("sc_music", R.string.sc_music_short, R.string.sc_music_long, R.drawable.ic_sc_music, SC_MUSIC, 1),
                mk("sc_channels", R.string.sc_live_short, R.string.sc_live_long, R.drawable.ic_sc_tv, SC_CHANNELS, 2)
            )
        } catch (e: Exception) {
            DebugLog.add("ATAJOS no se pudieron registrar: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Aplica el atajo pendiente: Continuar (lista "Continuar" de videos), Música o Canales (con PIN si lo hay). */
    private fun applyPendingShortcut() {
        val a = pendingShortcut ?: return
        pendingShortcut = null
        when (a) {
            SC_RESUME -> { viewMode = ViewMode.RESUME; showMovies() }
            SC_MUSIC -> { viewMode = ViewMode.ALL; showMusic() }
            SC_CHANNELS -> runMenuAction(16)
        }
    }

    /** Con la app ya abierta, un atajo llega aquí (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val a = intent.action
        if (a != null && a.startsWith("com.castv.app.SC_")) {
            pendingShortcut = a
            if (monitoring) applyPendingShortcut()
        }
        handleReminderTap(intent)   // [AVISO-TV-1]
        handleShareIn(intent)   // [SHARE-IN-1]
    }

    /** [SHARE-IN-1] «Compartir › CASTV» desde otra app: toma el primer enlace http(s) del texto y deja elegir 📺 TV · 📱 teléfono ·
     *  🎵 A MP3 · ⬇ Descargar (estas dos solo si el enlace es un archivo directo, igual que «Enlace → MP3 / descargar»).
     *  Los enlaces con usuario y clave se rechazan y el enlace no se muestra entero ni va al /log (R9): el título solo lleva el sitio. */
    private fun handleShareIn(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        i.action = null   // que no se repita al girar o volver
        val m = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)
        if (m == null) { toast("No encontré un enlace http(s) en lo compartido"); return }
        val url = m.value.trimEnd('.', ',', ';', ')', ']', '>', '"', '\'')
        val u = try { Uri.parse(url) } catch (e: Exception) { null }
        val host = u?.host
        if (u == null || host.isNullOrEmpty()) { toast("Enlace no válido"); return }
        if (!u.userInfo.isNullOrEmpty()) { toast("Los enlaces con usuario y clave no se admiten"); return }
        val opts = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        opts.add("📺 Al TV"); acts.add { playLink(url, true) }
        opts.add("📱 En el teléfono"); acts.add { playLink(url, false) }
        if (LinkDownload.check(url) == null) {
            opts.add("🎵 A MP3"); acts.add { runLinkJob(url, true) }
            opts.add("⬇ Descargar"); acts.add { runLinkJob(url, false) }
        }
        AlertDialog.Builder(this)
            .setTitle("Enlace de $host")
            .setItems(opts.toTypedArray()) { _, which -> if (which in acts.indices) acts[which]() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [AVISO-TV-1] Se tocó el aviso de un programa (EPG-RECORDA-1). Con la salida en TV el canal se manda al TV (si el TV
     *  no aparece en 6 s o no lo acepta, se abre en el teléfono y se avisa); con la salida en Teléfono, igual que antes. */
    private fun handleReminderTap(i: Intent?) {
        if (i == null) return
        val url = i.getStringExtra("remind_url") ?: return
        val ch = i.getStringExtra("remind_title") ?: "Canal"
        i.removeExtra("remind_url")   // que no se repita al girar o volver
        if (outPhone) { openReminderOnPhone(url, ch); return }
        toast("📺 Abriendo $ch en el TV…")
        thread {
            var tries = 0
            while (TvController.tvIp == null && tries < 12) { try { Thread.sleep(500) } catch (e: InterruptedException) { return@thread }; tries++ }
            val why = if (TvController.tvIp == null) "TV no encontrado" else Zap.sendUrl(applicationContext, ch, url)
            if (why != null) {
                DebugLog.add("AVISO-TV: $ch: $why")
                runOnUiThread { toast("⚠️ $why. Se abre en el teléfono"); openReminderOnPhone(url, ch) }
            }
        }
    }

    private fun openReminderOnPhone(url: String, ch: String) {
        try { startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra("url", url).putExtra("urlTitle", ch)) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        cancelSleepFade()   // [FUNDIDO-1] que el reproductor no se quede con el volumen a medias al pasar a modo sin pantalla
        fullDialog?.dismiss()   // [UI-3b]
        monitoring = false
        // [SERVICIO-1b] con música del teléfono sonando, la música sigue sin pantalla (PlayerCore); si no, se detiene como siempre
        abReset()   // [AB-MUSICA-1] sin pantalla no hay A-B
        gapAbort()   // [GAPLESS-1] PlayerCore lleva su propio sin pausa y fundido
        var handedOff = false
        val curSong = playingItem
        if (localActive && localPrepared && localPlayer != null && curSong != null && curSong.isMusic) {
            try {
                saveProgress()
                releaseEffects()
                PlayerCore.handOff(
                    applicationContext, curSong, playlist, playIndex, queue,
                    repeatMode.ordinal, playingFromQueue, sleepAfterTrack, sleepAtMs,
                    speed, speedTouched   // [SINPANTALLA-1]
                )
                handedOff = true
            } catch (e: Exception) { }
        }
        if (!handedOff) stopLocal()
        if (noisyRegistered) {   // [FOCO-2]
            try { unregisterReceiver(noisyReceiver) } catch (e: Exception) { }
            noisyRegistered = false
        }
        if (notifReceiverRegistered) {
            try { unregisterReceiver(notifReceiver) } catch (e: Exception) { }
            notifReceiverRegistered = false
        }
        try { mediaSession?.isActive = false; mediaSession?.release() } catch (e: Exception) { }
        mediaSession = null
        if (!handedOff) postDefaultNotification() else CastvWidget.reset(this)   // [WIDGET-1]
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // Primero se cierra lo que esté abierto; con música en el teléfono, "atrás" minimiza
        // en vez de cerrar y cortar la música
        when {
            selecting -> actionMode?.finish()
            searchBar.visibility == View.VISIBLE -> toggleSearch()
            CastvMotion.isOpen(playerExpanded) -> setPlayerExpanded(false)   // [ANIM-1b]
            hasActiveFilter() -> clearFilters()
            localActive -> moveTaskToBack(true)
            else -> super.onBackPressed()
        }
    }

    /** Con algo sonando en el TV (música o video del teléfono, o un canal o enlace), los botones de volumen del teléfono suben/bajan el volumen del TV. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            !localActive && TvController.tvIp != null && (playingItem != null || linkOnTv)) {   // [VOLUMEN-TV-1] linkOnTv
            val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5
            val v = (volSeek.progress + delta).coerceIn(0, 100)
            volSeek.progress = v
            leaveMuteIfRaised(v)   // [REMOTO-1b]
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

    /** [SERVICIO-1b] Recoge la música que siguió sonando sin pantalla: lista, cola, repetir y temporizador. */
    private fun adoptHeadless() {
        val s = PlayerCore.adopt() ?: return
        val mp = PlayerCore.player ?: return
        outPhone = true
        updateOutputButton()
        playlist = s.list
        playIndex = s.index
        queue.clear(); queue.addAll(s.queue)
        updateQueueBadge()
        playingFromQueue = s.fromQueue
        repeatMode = Repeat.values().getOrElse(s.repeat) { Repeat.OFF }
        sleepAfterTrack = s.sleepAfter
        sleepAtMs = s.sleepAt
        speed = s.speed; speedTouched = s.speedTouched   // [SINPANTALLA-1]
        val item = s.current
        playingItem = item
        Zap.nowTitle = null; linkOnTv = false; linkDurSec = 0
        manualStop = false
        wasPlaying = true
        tvNowPlaying.text = prettyTitle(item)
        bindNowPlaying(item)
        resetProgress()
        adapter.playingId = item.id
        adapter.playingMusic = true
        adapter.playingLocal = true
        adapter.notifyDataSetChanged()
        // los avisos del reproductor vuelven a esta pantalla
        mp.setOnPreparedListener { p ->
            if (localPlayer === p) {
                localPrepared = true
                p.start()
                attachEffects(p)
                applySpeed()
                wasPlaying = true
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
        if (localPrepared) { attachEffects(mp); applySpeed(); gapStart() }   // [GAPLESS-1]
        lastNotifKey = ""
        refreshNotification()
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
        adoptHeadless()   // [SERVICIO-1b] si la música seguía sin pantalla, se recoge aquí
        monitoring = true
        applyPendingShortcut()   // [ATAJOS-1]
        thread { autoBackupNow() }   // [AUTORESPALDO-1]
        thread { try { WeeklyCheck.runIfNeeded(applicationContext) } catch (e: Exception) { } }   // [REVISION-SEMANAL-1]
        thread { monitorLoop() }
        window.decorView.postDelayed({ warnIfPortBusy() }, 2000)
        window.decorView.postDelayed({ showWhatsNewOnce() }, 2600)   // [NOVEDADES-1]
    }

    /** [Etapa 14-C] El diálogo vive en PortDialog.kt. */
    private fun warnIfPortBusy() = PortDialog.showIfBusy(this)

    // ------------------------------------------------------------ [NOVEDADES-1] Novedades y consejos de gestos

    /** Súbelo cuando haya algo nuevo que contar: el cuadro vuelve a salir una sola vez. */
    private val whatsNewId = 1

    private fun showWhatsNewOnce() {
        if (isFinishing || isDestroyed) return
        val p = getSharedPreferences("castv", MODE_PRIVATE)
        if (p.getInt("whatsnew_seen", 0) >= whatsNewId) return
        p.edit().putInt("whatsnew_seen", whatsNewId).apply()   // se marca al mostrarlo: no vuelve a salir al girar la pantalla
        showGestureTips()
    }

    private fun showGestureTips() {
        AlertDialog.Builder(this)
            .setTitle("✨ Novedades y consejos")
            .setMessage(
                "En el reproductor de video:\n\n" +
                "• Desliza hacia los lados: adelantas o retrocedes.\n" +
                "• Desliza hacia arriba o abajo: a la derecha cambia el brillo y a la izquierda el volumen.\n" +
                "• Doble toque a los lados: salta unos segundos; en el centro: pausa o reanuda.\n" +
                "• Mantén pulsado: se ve al doble de velocidad hasta que sueltes.\n" +
                "• Pellizca con dos dedos: acercas la imagen.\n" +
                "• 😴 (barra de arriba): temporizador de apagado.\n\n" +
                "Puedes volver a verlo en Ajustes › Video › Consejos de gestos.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private var loadGen = 0
    private var hiddenSig = ""   // [CARPETAS-1] carpetas ocultas con las que se leyó la lista
    private var lastScanMs = 0L   // [BIBLIO-1] cuándo terminó la última lectura de la biblioteca

    /** Lee la lista en segundo plano para que la pantalla no se congele al abrir. */
    private fun loadMedia(music: Boolean, keepFilters: Boolean = false) {
        if (!keepFilters) actionMode?.finish()
        showingMusic = music
        getSharedPreferences("castv", MODE_PRIVATE).edit().putBoolean("lastMusic", music).apply()
        nameKeyCache.clear()
        val gen = ++loadGen
        updateTabs()
        thread {
            val scanned = try {
                if (music) MediaScanner.scanMusic(this) else MediaScanner.scanVideos(this)
            } catch (e: Exception) {
                DebugLog.add("ERROR leyendo la lista: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
            val hide = hiddenFolderSet()   // [CARPETAS-1] carpetas que Ariel ocultó
            hiddenSig = hide.sorted().joinToString("|")
            val items = if (hide.isEmpty()) scanned else scanned.filter { it.folder !in hide }
            val dups = computeDups(items)
            runOnUiThread {
                if (gen != loadGen || isFinishing) return@runOnUiThread
                allItems = items
                dupIds = dups
                lastScanMs = System.currentTimeMillis()
                if (!keepFilters) {   // [BIBLIO-1] el re-escaneo automático conserva carpeta/artista/álbum
                    selectedFolder = null
                    selectedArtist = null
                    selectedAlbum = null
                    groupReturn = null   // [TABS-1]
                }
                applyFilters()
                updateTabs()
            }
        }
    }

    private fun hiddenFolderSet(): Set<String> =
        getSharedPreferences("castv", MODE_PRIVATE).getStringSet("hidden_folders", emptySet()) ?: emptySet()

    private fun showMovies() = loadMedia(false)

    private fun showMusic() = loadMedia(true)

    // ------------------------------------------- Buscar, ordenar, carpetas

    // [Etapa 14-A] la lógica vive en CastvUtils.kt
    private fun norm(s: String): String = CastvText.norm(s)

    private fun nameKey(m: MediaItem): String = nameKeyCache.getOrPut(m.id) { norm(m.name) }

    // [DUP-1] sufijos de copia al final del nombre: "_1" o " (1)"
    private val dupSuffixRe = Regex("(_\\d{1,2}|\\s\\(\\d{1,2}\\))$")

    private fun dupKey(name: String): String =
        norm(prettyTitle(name).replace(dupSuffixRe, "").trimEnd('_', '-', ' '))

    /** Mismo título (sin extensión, etiquetas tipo "(MP3_160K)" ni sufijo de copia "_1" / " (1)")
     *  y duración casi igual (±2 s) = copia del mismo archivo. Se conserva la más antigua. */
    private fun computeDups(items: List<MediaItem>): Set<Long> {
        val d = HashSet<Long>()
        for (g in items.groupBy { dupKey(it.name) }.values) {
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
        if (viewMode == ViewMode.WORKSHOP) { showWorkshop(); return }   // [TALLER-1]
        // [TABS-1] con un filtro elegido (artista, carpeta, lista…) se sale de las pestañas de grupos
        if (inGroups() && groupReturn == null &&
            (selectedFolder != null || selectedArtist != null || selectedAlbum != null || selectedPlaylist != null || onlyUnseen)) {
            viewMode = ViewMode.ALL
        }
        if (inGroups()) { showGroups(); return }
        if (groupsShown) {
            groupsShown = false
            listFiles.adapter = adapter
            setFabShown(true)
        }
        var list = allItems
        selectedFolder?.let { f -> list = list.filter { it.folder == f } }
        selectedArtist?.let { ar -> list = list.filter { it.artist == ar } }
        selectedAlbum?.let { al -> list = list.filter { it.album == al } }
        selectedPlaylist?.let { pl -> list = list.filter { Store.inList(pl, it) } }
        if (Store.hideDups) list = list.filter { it.id !in dupIds }
        if (onlyUnseen && !showingMusic) list = list.filter { !Store.isSeen(it) }   // [NOVISTOS-1]
        when (viewMode) {
            ViewMode.FAVS -> list = list.filter { Store.isFav(it) }
            ViewMode.RESUME -> list = list.filter { Store.resumeAt(it) >= 10 }
            else -> {}
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
            // [LISTAS-INTEL-1] primero lo que nunca se ha reproducido (lo más nuevo arriba); después, lo ya reproducido
            Sort.NEVER_PLAYED -> list.sortedWith(
                compareBy<MediaItem> { Store.playCount(it) > 0 }.thenByDescending { it.dateAdded })
            // [MES-1] las más reproducidas este mes primero; a igualdad, las más reproducidas en total
            Sort.MOST_MONTH -> list.sortedWith(
                compareByDescending<MediaItem> { Store.monthPlays(it) }.thenByDescending { Store.playCount(it) }.thenBy { nameKey(it) })
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

    /** [VACIO-1] Muestra el botón bajo el mensaje de lista vacía. */
    private fun setEmptyAction(label: String, onClick: () -> Unit) {
        tvEmptyAction.text = label
        tvEmptyAction.setOnClickListener { onClick() }
        tvEmptyAction.visibility = View.VISIBLE
    }

    /** [BUSCA-1] Abre Canales con lo que se escribió en el buscador (respeta el PIN de Canales). */
    private fun openChannelsSearch() {
        val q = query
        val open = { startActivityForResult(Intent(this, M3uActivity::class.java).putExtra("q", q), reqM3u) }
        if (ParentalPin.guardsChannels(this)) ParentalPin.ask(this, "PIN para abrir Canales") { open() } else open()
    }

    /** [BUSCA-OTRA-1] Sin resultados: ofrece seguir la búsqueda en Canales o en la otra biblioteca (Música / Videos), conservando lo escrito. */
    private fun searchElsewhere() {
        val other = if (showingMusic) "🎬 Videos" else "🎵 Música"
        AlertDialog.Builder(this)
            .setTitle("Buscar «$query»")
            .setItems(arrayOf<CharSequence>("📡 En Canales", "En $other")) { _, which ->
                if (which == 0) openChannelsSearch()
                else {
                    resetFilters()
                    viewMode = ViewMode.ALL
                    groupReturn = null
                    if (showingMusic) showMovies() else showMusic()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Mensaje en el centro de la lista cuando no hay nada que mostrar (ya no ensucia la línea de estado). */
    private fun updateEmptyState() {
        if (currentList.isNotEmpty()) {
            tvEmpty.visibility = View.GONE
            tvEmptyAction.visibility = View.GONE   // [VACIO-1]
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
            viewMode == ViewMode.RESUME -> "Nada por continuar todavía.\nLo que veas o escuches aparecerá aquí."   // [VACIO-1]
            onlyUnseen && !showingMusic -> "Ya viste todos los videos.\nToca el filtro de arriba para quitarlo."   // [NOVISTOS-1]
            query.isNotEmpty() -> "Sin resultados para «$query»."
            selectedPlaylist != null -> "Esta lista no tiene " + (if (showingMusic) "audios" else "videos") +
                    ".\nToca ⋮ en un archivo y elige 📃 Añadir a lista."
            else -> "Sin resultados."
        }
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
        // [VACIO-1] un botón con la salida más útil de cada caso
        if (hasMediaPermission() && allItems.isNotEmpty()) {
            when {
                viewMode == ViewMode.FAVS -> setEmptyAction("Ver todos") { viewMode = ViewMode.ALL; applyFilters() }
                viewMode == ViewMode.RESUME -> setEmptyAction("📡 Abrir Canales") { runMenuAction(16) }
                onlyUnseen && !showingMusic -> tvEmptyAction.visibility = View.GONE
                query.isNotEmpty() -> setEmptyAction("🔎 Buscar en otro sitio…") { searchElsewhere() }   // [BUSCA-1] [BUSCA-OTRA-1]
                else -> tvEmptyAction.visibility = View.GONE
            }
        } else tvEmptyAction.visibility = View.GONE
        if (footerAttached) {
            listFiles.removeFooterView(footerView)
            footerAttached = false
        }
    }

    private fun hasActiveFilter(): Boolean =
        selectedFolder != null || selectedArtist != null || selectedAlbum != null || selectedPlaylist != null ||
            viewMode == ViewMode.FAVS || viewMode == ViewMode.RESUME || viewMode == ViewMode.WORKSHOP || workTool != null   // [TABS-1] [TALLER-1]

    private fun resetFilters() {
        selectedFolder = null
        selectedArtist = null
        selectedAlbum = null
        selectedPlaylist = null
        onlyUnseen = false   // [NOVISTOS-1]
    }

    private fun clearFilters() {
        resetFilters()
        workTool = null   // [TALLER-1]
        if (viewMode == ViewMode.FAVS || viewMode == ViewMode.RESUME || viewMode == ViewMode.WORKSHOP) viewMode = ViewMode.ALL   // [TABS-1] [TALLER-1]
        groupReturn?.let { viewMode = it }   // [TABS-1] volver a Álbumes / Carpetas / Listas
        groupReturn = null
        applyFilters()
    }

    // ------------------------------------------- [TABS-1] Álbumes, Carpetas y Listas

    private class GroupRow(val kind: Int, val title: String, val sub: String, val rep: MediaItem?, val key: String)

    private fun inGroups(): Boolean = viewMode == ViewMode.GROUPS || viewMode == ViewMode.LISTS

    private fun countLabel(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"

    /** Arma las filas de la pestaña Álbumes (música), Carpetas (videos) o Listas. */
    private fun showGroups() {
        val base = if (Store.hideDups) allItems.filter { it.id !in dupIds } else allItems
        val unit1 = if (showingMusic) "pista" else "video"
        val unitN = if (showingMusic) "pistas" else "videos"
        var rows = ArrayList<GroupRow>()
        if (viewMode == ViewMode.LISTS) {
            for (n in Store.listNames()) {
                val members = base.filter { Store.inList(n, it) }
                rows.add(GroupRow(2, n, if (members.isEmpty()) "Vacía" else countLabel(members.size, unit1, unitN), members.firstOrNull(), n))
            }
        } else if (showingMusic) {
            val byAlbum = base.filter { it.album.isNotBlank() }.groupBy { it.album }
            for (name in byAlbum.keys.sortedWith { a, b -> naturalCompare(norm(a), norm(b)) }) {
                val items = byAlbum[name] ?: continue
                val artists = items.map { it.artist }.filter { it.isNotBlank() }.distinct()
                val total = items.sumOf { it.durationSec }
                val parts = ArrayList<String>()
                if (artists.size == 1) parts.add(artists[0]) else if (artists.size > 1) parts.add("Varios artistas")
                parts.add(countLabel(items.size, unit1, unitN))
                if (total > 0) parts.add(fmt(total))
                rows.add(GroupRow(0, name, parts.joinToString("  ·  "), items.first(), name))
            }
        } else {
            val byFolder = base.groupBy { it.folder }
            for (f in byFolder.keys.sortedWith { a, b -> naturalCompare(norm(a), norm(b)) }) {
                val items = byFolder[f] ?: continue
                rows.add(GroupRow(1, folderLabel(f), countLabel(items.size, unit1, unitN) + "  ·  " + folderPathPretty(f), items.first(), f))   // [CARPETA-RUTA-1]
            }
        }
        val q = norm(query).trim()
        if (q.isNotEmpty()) rows = ArrayList(rows.filter { norm(it.title + " " + it.sub).contains(q) })
        if (viewMode == ViewMode.LISTS && q.isEmpty()) rows.add(GroupRow(3, "＋ Nueva lista", "Crea una lista de reproducción", null, ""))

        if (!groupsShown) {
            groupsShown = true
            listFiles.adapter = groupAdapter
        }
        groupAdapter.rows = rows
        groupAdapter.grid = viewMode == ViewMode.GROUPS && showingMusic   // [ALBUMES-2] los álbumes van en cuadrícula de dos columnas
        groupAdapter.notifyDataSetChanged()
        tvEmptyAction.visibility = View.GONE
        if (rows.isEmpty()) {
            tvEmpty.text = when {
                !hasMediaPermission() -> "Falta el permiso para leer tus archivos.\nActívalo en Ajustes › Apps › CASTV › Permisos."
                q.isNotEmpty() -> "Sin resultados para «$query»."
                viewMode == ViewMode.GROUPS && showingMusic -> "Tus archivos no traen el dato del álbum."
                else -> "No se encontraron videos en el teléfono."
            }
            tvEmpty.visibility = View.VISIBLE
            if (footerAttached) {
                listFiles.removeFooterView(footerView)
                footerAttached = false
            }
        } else {
            tvEmpty.visibility = View.GONE
            if (!footerAttached) {
                listFiles.addFooterView(footerView, null, false)
                footerAttached = true
            }
        }
        setFabShown(false)
        updateTabs()
        updateFilterChip()
    }

    /** Toca una fila de grupo: abre sus archivos (Atrás o ✕ vuelve a la pestaña). */
    private fun openGroup(g: GroupRow) {
        if (g.kind == 4) { workNew(); return }   // [TALLER-1]
        if (g.kind == 5) { workProjectMenu(g.key); return }
        if (g.kind == 7) { workCleanPick(); return }   // [TALLER-LIMPIA-1]
        if (g.kind == 8) { workSharePick(); return }   // [TALLER-COMPARTIR-1]
        if (g.kind == 6) { Workshop.clearHidden(this); workLastScan = 0L; applyFilters(); return }
        when (g.kind) {
            0 -> selectedAlbum = g.key
            1 -> selectedFolder = g.key
            2 -> selectedPlaylist = g.key
            else -> {
                promptNewList { n ->
                    toast("Lista «$n» creada. Añade pistas con ⋮ › Añadir a lista")
                    applyFilters()
                }
                return
            }
        }
        groupReturn = viewMode
        viewMode = ViewMode.ALL
        applyFilters()
    }

    /** Etiqueta con los filtros activos; al tocarla se quitan todos. */
    private fun updateFilterChip() {
        val parts = ArrayList<String>()
        if (viewMode == ViewMode.FAVS) parts.add("⭐ Favoritos")   // [TABS-1]
        if (viewMode == ViewMode.RESUME) parts.add("⏩ Continuar")   // [TABS-1]
        selectedFolder?.let { parts.add("📁 " + folderLabel(it)) }   // [CARPETA-RUTA-1]
        selectedArtist?.let { parts.add("🎤 $it") }
        selectedAlbum?.let { parts.add("💿 $it") }
        selectedPlaylist?.let { parts.add("📃 $it") }
        if (onlyUnseen && !showingMusic) parts.add("👁 No vistos")   // [NOVISTOS-1]
        workTool?.let { parts.add(workToolLabel(it) + ": toca un archivo") }   // [TALLER-1]
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
        // [VENT-1] los de más pistas primero (empate: por nombre), «?» sueltos fuera, y un buscador arriba
        val names = counts.keys.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenComparator { a, b -> naturalCompare(norm(a), norm(b)) })
        val labels = ArrayList<String>()
        labels.add("💿 Todos los álbumes")
        names.forEach { labels.add("💿 ${cleanQ(it)} (${counts[it]})") }
        pickWithSearch("Álbum", labels) { which ->
            selectedAlbum = if (which == 0) null else names[which - 1]
            applyFilters()
        }
    }

    /** [VENT-1] Quita los «?» seguidos (emojis que Android no pudo leer) y deja espacios normales. Solo para mostrar. */
    private fun cleanQ(s: String): String = s.replace(Regex("\\?{2,}"), " ").replace(Regex("\\s{2,}"), " ").trim().ifEmpty { s }

    /** [VENT-1] Lista de opciones con buscador arriba, filas de hasta 2 líneas y alto máximo de ~60 % de la pantalla.
     *  `onPick` recibe la posición en la lista ORIGINAL (aunque se esté filtrando). */
    private fun pickWithSearch(
        title: String,
        labels: List<String>,
        compact: Boolean = false,                          // [CARPETA-2] filas más juntas
        onLong: ((Int, AlertDialog) -> Unit)? = null,      // [CARPETA-2] pulsación larga (índice original, diálogo)
        onPick: (Int) -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val box = EditText(this).apply { hint = "🔎 Buscar…"; setSingleLine(true) }
        val lv = ListView(this)
        var shown: List<Int> = labels.indices.toList()
        val ad = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList(labels)) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent)
                (v as? android.widget.TextView)?.apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    if (compact) {   // [CARPETA-2]
                        minHeight = (40 * dp).toInt()
                        setPadding(paddingLeft, (6 * dp).toInt(), paddingRight, (6 * dp).toInt())
                    }
                }
                return v
            }
        }
        lv.adapter = ad
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(box)
            addView(lv, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6).toInt()))
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(col)
            .setNegativeButton("Cancelar", null)
            .create()
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                val q = norm(e?.toString() ?: "")
                shown = if (q.isEmpty()) labels.indices.toList() else labels.indices.filter { norm(labels[it]).contains(q) }
                ad.clear()
                ad.addAll(shown.map { labels[it] })
                ad.notifyDataSetChanged()
            }
        })
        lv.setOnItemClickListener { _, _, pos, _ ->
            val idx = shown.getOrNull(pos) ?: return@setOnItemClickListener
            dlg.dismiss()
            onPick(idx)
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->   // [CARPETA-2]
            val idx = shown.getOrNull(pos)
            val ol = onLong
            if (idx != null && ol != null) { ol(idx, dlg); true } else false
        }
        dlg.show()
    }

    private fun pickFolder() {
        if (allItems.isEmpty()) return
        val counts = allItems.groupingBy { it.folder }.eachCount()
        // [CARPETA-RUTA-1] nombres legibles (la ruta cruda solo se usa por dentro para filtrar y ocultar)
        val folders = counts.keys.sortedWith { a, b -> naturalCompare(norm(folderLabel(a)), norm(folderLabel(b))) }
        val shown = folders.map { folderLabel(it) }
        val repeated = shown.groupingBy { it }.eachCount()
        val labels = ArrayList<String>()
        labels.add("📁 Todas (${allItems.size})")
        folders.forEachIndexed { i, f ->
            val name = if ((repeated[shown[i]] ?: 0) > 1) folderPathPretty(f) else shown[i]   // dos con el mismo nombre: se ve la ruta
            labels.add("📁 $name (${counts[f]})")
        }
        // [CARPETA-2] filas más juntas y buscador; [CARPETAS-1] mantener pulsada una carpeta la oculta de la biblioteca
        pickWithSearch("Carpeta", labels, compact = true, onLong = { idx, dlg ->
            if (idx > 0) confirmHideFolder(folders[idx - 1], dlg)
        }) { which ->
            selectedFolder = if (which == 0) null else folders[which - 1]
            applyFilters()
        }
    }

    // [CARPETA-RUTA-1] Nombre legible de una carpeta. «Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video»
    // se ve como «WhatsApp Business Video»; si termina en un nombre genérico (Sent, Private, Video…) se le suma lo que lo explica.
    private val folderGeneric = setOf("sent", "private", "enviados", "privado", "video", "videos", "audio", "audios", "media", "files", "archivos")

    private fun folderNiceName(seg: String): String = when (seg.lowercase()) {
        "com.whatsapp.w4b" -> "WhatsApp Business"
        "com.whatsapp" -> "WhatsApp"
        else -> seg
    }

    private fun folderLabel(path: String): String {
        val segs = path.split('/').filter { it.isNotBlank() }
        if (segs.isEmpty() || path.trim().trim('/') == "(raíz)") return "Raíz del almacenamiento"
        var i = segs.size - 1
        while (i > 0 && segs[i].lowercase() in folderGeneric) i--
        val base = folderNiceName(segs[i])
        val tail = segs.drop(i + 1).filter { it.lowercase() != "media" }
        return if (tail.isEmpty()) base else base + " › " + tail.joinToString(" › ")
    }

    /** Ruta completa pero legible (sin «Android/media», con «›»), para distinguir carpetas que se llaman igual. */
    private fun folderPathPretty(path: String): String {
        val segs = path.split('/').filter { it.isNotBlank() }.toMutableList()
        if (segs.isEmpty() || path.trim().trim('/') == "(raíz)") return "Raíz del almacenamiento"
        if (segs.size > 2 && segs[0] == "Android" && segs[1].lowercase() == "media") {
            segs.removeAt(0)
            segs.removeAt(0)
        }
        segs[0] = folderNiceName(segs[0])
        return segs.joinToString(" › ")
    }

    private fun confirmHideFolder(folder: String, picker: AlertDialog) {
        AlertDialog.Builder(this)
            .setTitle("¿Ocultar esta carpeta?")
            .setMessage("📁 ${folderLabel(folder)} dejará de salir en la biblioteca de CASTV. Los archivos no se borran. Se puede mostrar de nuevo en Ajustes › Biblioteca › Carpetas ocultas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Ocultar") { _, _ ->
                val sp = getSharedPreferences("castv", MODE_PRIVATE)
                val now = HashSet(sp.getStringSet("hidden_folders", emptySet()) ?: emptySet())
                now.add(folder)
                sp.edit().putStringSet("hidden_folders", now).apply()
                picker.dismiss()
                selectedFolder = null
                loadMedia(showingMusic)
                toast("📁 Carpeta oculta")
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
        nav(navFolders, false)   // [UI-5] ahora es "Canales"; el filtro de carpeta lo avisa tvFilter
        fun tab(t: TextView, active: Boolean) {
            t.setBackgroundResource(if (active) R.drawable.tab_active else R.drawable.tab_off)
            t.setTextColor(if (active) on else off)
        }
        tabAll.text = if (showingMusic) "PISTAS" else "VIDEOS"
        tab(tabAll, viewMode == ViewMode.ALL)
        tabFavs.text = if (showingMusic) "ÁLBUMES" else "CARPETAS"   // [TABS-1] antes FAVORITOS
        tabResume.text = "LISTAS"                                     // [TABS-1] antes CONTINUAR
        tab(tabFavs, viewMode == ViewMode.GROUPS)
        tab(tabResume, viewMode == ViewMode.LISTS)
        tabWork.text = "TALLER"   // [TALLER-1]
        tab(tabWork, viewMode == ViewMode.WORKSHOP)
        updateWorkChips()   // [TALLER-FILTRO-1] se oculta fuera del Taller
    }

    private var recentPop: android.widget.ListPopupWindow? = null

    /** [BUSQ-RECIENTES-1] Lista flotante bajo el buscador con las últimas búsquedas y «Borrar historial». */
    private fun showRecentSearches() {
        if (searchBar.visibility != View.VISIBLE || etSearch.text.isNotEmpty()) return
        val list = Store.recentSearches()
        if (list.isEmpty()) return
        recentPop?.dismiss()
        val rows = list + "🗑 Borrar historial de búsquedas"
        val pop = android.widget.ListPopupWindow(this)
        pop.anchorView = etSearch
        pop.setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, rows))
        pop.isModal = true
        pop.inputMethodMode = android.widget.ListPopupWindow.INPUT_METHOD_NEEDED
        pop.setOnItemClickListener { _, _, pos, _ ->
            pop.dismiss()
            if (pos < list.size) {
                etSearch.setText(list[pos])
                etSearch.setSelection(etSearch.text.length)
            } else {
                Store.clearRecentSearches()
                toast("Historial de búsquedas borrado")
            }
        }
        recentPop = pop
        try { pop.show() } catch (e: Exception) { recentPop = null }
    }

    private fun toggleSearch() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (searchBar.visibility == View.VISIBLE) {
            Store.addRecentSearch(etSearch.text.toString())   // [BUSQ-RECIENTES-1]
            recentPop?.dismiss()
            etSearch.setText("")
            searchBar.visibility = View.GONE
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        } else {
            searchBar.visibility = View.VISIBLE
            etSearch.requestFocus()
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** [VIBRA-1] Vibración suave (si está activada en Ajustes › Interfaz y el teléfono permite vibrar al tocar). */
    private fun haptic(v: View) {
        if (!getSharedPreferences("castv", MODE_PRIVATE).getBoolean("haptic", true)) return
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** [SOFA-1] Botones del mini reproductor y del panel más grandes (×1,35) con «Botones grandes»; medidas base en dp, así se puede apagar sin acumular. */
    private fun applySofa() {
        val f = CastvSofa.scale(this)
        val d = resources.displayMetrics.density
        fun size(id: Int, w: Int, h: Int) {   // w = 0: el ancho lo da el peso y no se toca
            val v = findViewById<View>(id) ?: return
            val lp = v.layoutParams ?: return
            if (w > 0) lp.width = (w * d * f).toInt()
            lp.height = (h * d * f).toInt()
            v.layoutParams = lp
        }
        size(R.id.btnNowFav, 40, 40); size(R.id.btnQuick, 40, 40); size(R.id.btnExpand, 36, 40)
        size(R.id.btnShuffle, 0, 44); size(R.id.btnPrev, 0, 44); size(R.id.btnPlay, 52, 52)
        size(R.id.btnStop, 0, 44); size(R.id.btnNext, 0, 44); size(R.id.btnAuto, 0, 44)
        size(R.id.btnBack10, 56, 40); size(R.id.btnFwd10, 56, 40); size(R.id.btnMute, 40, 40)
    }

    private fun setPlayerExpanded(expanded: Boolean) {
        // [ANIM-1b] el panel se despliega deslizando y la flecha gira; con «Quitar animaciones» de Android es inmediato
        CastvMotion.slide(playerExpanded, expanded)
        CastvMotion.rotate(btnExpand, if (expanded) 180f else 0f)
    }

    // [PULIDO-2b] Mientras no haya sonado nada, el mini reproductor se compacta: sin barra de progreso ni ☆.
    @Volatile private var lastMiniIdle: Boolean? = null

    private fun updateMiniCompact() {
        val idle = playingItem == null && !linkOnTv && !localActive
        if (lastMiniIdle == idle) return
        lastMiniIdle = idle
        runOnUiThread {
            val v = if (idle) View.GONE else View.VISIBLE
            seekBar.visibility = v
            btnNowFav.visibility = v
        }
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
        menu.add(0, 4, 3, "Listas de reproducción")   // [LISTA-NOMBRE-1]
        if (showingMusic) {
            menu.add(0, 5, 4, "Filtrar por artista")
            menu.add(0, 6, 5, "Filtrar por álbum")
        }
        menu.add(0, 7, 6, sleepLabel())
        menu.add(0, 8, 7, "Velocidad: " + speedShort() + " (teléfono)")
        menu.add(0, 9, 8, "Ecualizador (teléfono)")
        menu.add(0, 10, 9, "Elegir TV")
        menu.add(0, 12, 10, "Tema: " + themeLabel())
        menu.add(0, 13, 11, "Color: " + accentNames[savedAccent()])
        if (Build.VERSION.SDK_INT >= 30) {
            menu.add(0, 14, 11, if (useTrash()) "Eliminado: a la papelera" else "Eliminado: permanente")
        }
        menu.add(0, 15, 11, "Reproducir enlace…")   // [Etapa 13-A]
        menu.add(0, 25, 11, "Enlace → MP3 / descargar…")   // [VIDEO-MP3-1b]
        menu.add(0, 16, 11, "Listas M3U (canales)…")   // [Etapa 13-B]
        menu.add(0, 17, 11, "Respaldo (exportar/importar)…")   // [Respaldo JSON]
        menu.add(0, 18, 11, "📁 Carpetas…")   // [UI-5]
        menu.add(0, 19, 11, "🕵️‍♂️ Modo incógnito: " + (if (Store.incognito) "Activado" else "Desactivado"))   // [INCOG-1]
        if (!showingMusic) menu.add(0, 20, 11, "👁 No vistos: " + (if (onlyUnseen) "Solo no vistos" else "Todos"))   // [NOVISTOS-1]
        menu.add(0, 21, 11, "Lo más visto")   // [TOP-1]
        menu.add(0, 26, 11, "Tu resumen")   // [RESUMEN-1]
        menu.add(0, 22, 11, "Favoritos: " + allItems.count { Store.isFav(it) })   // [TABS-1]
        menu.add(0, 23, 11, "Continuar: " + allItems.count { Store.resumeAt(it) >= 10 })   // [TABS-1]
        menu.add(0, 24, 11, "Informe de diagnóstico")   // [DIAG-1]
        menu.add(0, 27, 12, "👋 Guía de inicio")   // [BIENVENIDA-1]
        menu.add(0, 11, 12, "Mis redes y contacto")
    }

    /** [BIENVENIDA-1] Primer arranque guiado: solo en una instalación NUEVA (no al actualizar) y una sola vez.
     *  La marca vive en sus propias preferencias (`castv_welcome`), fuera de los respaldos. */
    private fun maybeWelcome() {
        if (isFinishing || isDestroyed) return
        val sp = getSharedPreferences("castv_welcome", MODE_PRIVATE)
        if (sp.getBoolean("seen", false)) return
        sp.edit().putBoolean("seen", true).apply()
        val fresh = try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            Math.abs(pi.lastUpdateTime - pi.firstInstallTime) < 10_000L
        } catch (e: Exception) { false }
        if (fresh) showWelcome(0)
    }

    /** [BIENVENIDA-1] Tres pantallas cortas; también se abre desde Más › «👋 Guía de inicio». */
    private fun showWelcome(page: Int) {
        val titles = arrayOf("👋 Bienvenido a CASTV", "📺 Mandar a la TV", "✂️ Taller, canales y más")
        val texts = arrayOf(
            "Abajo eliges 🎬 Video o 🎵 Audio. Toca un archivo para reproducirlo; mantenlo pulsado para marcar varios (borrar, añadir a una lista, unir…). Arriba están la búsqueda y las pestañas de la biblioteca.",
            "El botón del teléfono, arriba, cambia la salida entre Teléfono y TV. Elige tu televisor en Más › «Elegir TV». Con la salida en TV, lo que reproduzcas se manda a la pantalla.",
            "✂️ La pestaña TALLER corta, une, divide, reduce el peso y convierte videos a MP3. 📡 Canales abre tus listas M3U. «Más» tiene los ajustes, el respaldo y tu resumen. Esta guía vuelve a salir en Más › «Guía de inicio».")
        val last = page == titles.size - 1
        val b = AlertDialog.Builder(this)
            .setTitle(titles[page])
            .setMessage(texts[page])
            .setPositiveButton(if (last) "Empezar" else "Siguiente") { _, _ -> if (!last) showWelcome(page + 1) }
        if (!last) b.setNegativeButton("Saltar", null)
        b.show()
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
            25 -> askLinkToMp3()   // [VIDEO-MP3-1b]
            16 -> {
                val open = { startActivityForResult(Intent(this, M3uActivity::class.java), reqM3u) }
                if (ParentalPin.guardsChannels(this)) {   // [PARENTAL-2]
                    ParentalPin.ask(this, "PIN para abrir Canales") { open() }
                } else {
                    open()
                }
            }
            17 -> showBackupMenu()
            18 -> pickFolder()   // [UI-5]
            19 -> {   // [INCOG-1]
                Store.incognito = !Store.incognito
                toast(if (Store.incognito) "🕵️‍♂️ Incógnito activado: no se guardan reproducciones, puntos de reanudación, historial de enlaces ni «visto»"
                      else "🕵️‍♂️ Incógnito desactivado")
                updateIncogBadge()   // [INCOG-2]
            }
            21 -> showTop()   // [TOP-1]
            26 -> showSummary()   // [RESUMEN-1]
            22 -> { groupReturn = null; viewMode = ViewMode.FAVS; applyFilters() }   // [TABS-1]
            23 -> { groupReturn = null; viewMode = ViewMode.RESUME; applyFilters() }   // [TABS-1]
            24 -> showDiagReport()   // [DIAG-1]
            27 -> showWelcome(0)   // [BIENVENIDA-1]
            20 -> {   // [NOVISTOS-1]
                if (showingMusic) toast("«No vistos» solo sirve en videos")
                else { onlyUnseen = !onlyUnseen; applyFilters() }
            }
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
        groups.add("Reproducir y canales" to listOf(15, 25, 18, 10, 3))
        groups.add("Biblioteca" to listOf(1, 2, 4, 5, 6, 20, 21, 26, 22, 23))
        groups.add("Reproductor" to listOf(7, 8, 9))
        val known = groups.flatMap { it.second }.toSet()
        val inSettings = setOf(11, 12, 13, 14, 16, 17)   // [UI-1b] viven en la pantalla Ajustes
        val extra = titles.keys.filter { it !in known && it !in inSettings }.sorted()
        if (extra.isNotEmpty()) groups.add("Más" to extra)

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val r = dp(20).toFloat()
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(20))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Themes.color(this@MainActivity, R.attr.castvSurface))   // [TEMAS-2]
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
            for (pairIds in shown.chunked(2)) {   // [MENUS-2] dos opciones por fila: la hoja ocupa la mitad de alto
              val pair = android.widget.LinearLayout(this).apply {
                  orientation = android.widget.LinearLayout.HORIZONTAL
                  setPadding(dp(8), 0, dp(8), 0)
              }
              for (id in pairIds) {
                // [MAS-1] Fila con icono propio, título y estado en texto gris pequeño debajo.
                val full = (titles[id] ?: "").removePrefix("📁 ")
                val cut = full.indexOf(": ")
                val label = if (cut > 0) full.substring(0, cut) else full
                val state = if (cut > 0) full.substring(cut + 2) else ""
                val iconRes = when (id) {
                    1 -> R.drawable.ic_mas_sort
                    2 -> R.drawable.ic_mas_copy
                    3 -> if (outPhone) R.drawable.ic_phone else R.drawable.ic_tv
                    4 -> R.drawable.ic_mas_list
                    5 -> R.drawable.ic_mas_person
                    6 -> R.drawable.ic_mas_album
                    7 -> R.drawable.ic_mas_timer
                    8 -> R.drawable.ic_mas_speed
                    9 -> R.drawable.ic_mas_eq
                    10 -> R.drawable.ic_tv
                    15 -> R.drawable.ic_mas_link
                    25 -> R.drawable.ic_vp_mp3   // [VIDEO-MP3-1b]
                    18 -> R.drawable.ic_mas_folder
                    19 -> R.drawable.ic_mas_incognito   // [ICONOS-MAS-1]
                    20 -> R.drawable.ic_mn_visibility   // [ICONOS-MAS-1] No vistos
                    21 -> R.drawable.ic_mas_trending   // [TOP-1] [ICONOS-MAS-1]
                    24 -> R.drawable.ic_vp_info   // [ICONOS-MAS-1] Informe de diagnóstico
                    27 -> R.drawable.ic_vp_info   // [BIENVENIDA-1]
                    26 -> R.drawable.ic_mas_chart   // [RESUMEN-1] [ICONOS-MAS-1]
                    22 -> R.drawable.ic_vp_star   // [TABS-1]
                    23 -> R.drawable.ic_vp_play   // [TABS-1]
                    else -> R.drawable.ic_nav_more
                }
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(56)
                    setPadding(dp(12), dp(8), dp(8), dp(8))   // [MENUS-2]
                    setBackgroundResource(sel.resourceId)
                    setOnClickListener {
                        sheet.dismiss()
                        runMenuAction(id)
                    }
                }
                row.addView(android.widget.ImageView(this).apply {
                    setImageResource(iconRes)
                    setColorFilter(col(R.color.vlc_muted))
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(24), dp(24))
                })
                val texts = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }   // [MENUS-2]
                }
                texts.addView(TextView(this).apply {
                    text = label
                    textSize = 14f   // [MENUS-2]
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(col(R.color.vlc_text))
                })
                if (state.isNotEmpty()) {
                    texts.addView(TextView(this).apply {
                        text = state
                        textSize = 11f   // [MENUS-2]
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(col(R.color.vlc_muted))
                    })
                }
                row.addView(texts)
                pair.addView(row, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))   // [MENUS-2]
              }
              if (pairIds.size == 1) pair.addView(View(this), android.widget.LinearLayout.LayoutParams(0, 1, 1f))   // [MENUS-2] hueco para que la opción suelta no se estire
              root.addView(pair)
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

    // ------------------------------------------- [DIAG-1] Informe de diagnóstico

    /** [DIAG-1] Más › «Informe de diagnóstico»: junta versión, teléfono, TV y las últimas líneas del /log en un texto
     *  que se comparte o se copia. Tapa las claves más comunes de los enlaces. No cambia nada de lo que se envía. */
    private fun showDiagReport() {
        val rep = buildDiagReport()
        AlertDialog.Builder(this)
            .setTitle("Informe de diagnóstico")
            .setMessage("Incluye la versión, el teléfono, el TV y las últimas líneas del registro. Se tapan las claves más comunes de los enlaces, pero revísalo antes de compartirlo.")
            .setPositiveButton("Compartir") { _, _ ->
                try {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, rep), "Compartir informe"))
                } catch (e: Exception) { toast("No se pudo abrir el selector para compartir") }
            }
            .setNeutralButton("Copiar") { _, _ ->
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("CASTV informe", rep))
                    toast("📋 Informe copiado")
                } catch (e: Exception) { toast("No se pudo copiar") }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun buildDiagReport(): String {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
        val phoneIp = try { getLocalIpAddress() } catch (e: Exception) { null }
        val sb = StringBuilder()
        sb.append("CASTV ").append(ver).append('\n')
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Teléfono IP: ").append(phoneIp ?: "no detectada").append('\n')
        sb.append("Salida: ").append(if (outPhone) "Teléfono" else "TV").append('\n')
        sb.append("TV: ").append(TvController.tvName.ifEmpty { "(sin elegir)" })
            .append(" · IP ").append(TvController.tvIp ?: "no encontrada")
            .append(":").append(TvController.tvPort)
            .append(" · ").append(if (tvOnline) "conectada" else "sin conexión").append('\n')
        sb.append("Control: ").append(TvController.controlPath)
            .append(" · render: ").append(TvController.renderPath ?: "-").append('\n')
        sb.append("Calidad en vivo: ").append(MediaServer.liveQuality).append('\n')
        sb.append("Incógnito: ").append(if (Store.incognito) "sí" else "no").append("\n\n")
        sb.append("--- Registro (últimas líneas) ---\n")
        sb.append(diagMask(DebugLog.dump().lines().takeLast(150).joinToString("\n")))
        return sb.toString()
    }

    /** [DIAG-1] Tapa claves en el texto: parámetros como token=, key=, password= y los usuario/clave de rutas Xtream. */
    private fun diagMask(t: String): String =
        t.replace(Regex("(?i)([?&;](?:token|key|apikey|api_key|password|pass|pwd|auth|sig|signature|user|username)=)[^&\\s]+"), "\$1•••")
            .replace(Regex("(?i)(/(?:live|movie|series)/)[^/\\s]+/[^/\\s]+/"), "\$1•••/•••/")

    // ------------------------------------------- [TOP-1] Lo más visto

    /** [TOP-1] Más › «Lo más visto»: los 10 de música y los 10 de video del teléfono con más reproducciones
     *  (`castv_stats`). Los canales no se cuentan. Tocar uno lo reproduce. */
    private fun showTop() {
        toast("Buscando lo más visto…")
        thread {
            val hide = hiddenFolderSet()
            fun top(all: List<MediaItem>): List<MediaItem> = all
                .filter { it.folder !in hide && Store.playCount(it) > 0 }
                .distinctBy { Store.key(it) }
                .sortedWith(compareByDescending<MediaItem> { Store.playCount(it) }.thenByDescending { Store.watchSec(it) })
                .take(10)
            val m = try { top(MediaScanner.scanMusic(this)) } catch (e: Exception) { emptyList() }
            val v = try { top(MediaScanner.scanVideos(this)) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (m.isEmpty() && v.isEmpty()) {
                    toast("Aún no hay reproducciones guardadas (en incógnito no se cuentan)")
                } else {
                    val startMusic = if (showingMusic) (m.isNotEmpty() || v.isEmpty()) else v.isEmpty()
                    showTopDialog(startMusic, m, v)
                }
            }
        }
    }

    // ------------------------------------------- [RESUMEN-1] Tu resumen

    /** [RESUMEN-1] Más › «Tu resumen»: lo escuchado y visto, el artista y el canal más abiertos y la racha de días.
     *  Lee `castv_stats` y `castv_top` sin cambiar lo guardado. El tiempo de música es un estimado (reproducciones × duración). */
    private fun showSummary() {
        toast("Armando tu resumen…")
        thread {
            val hide = hiddenFolderSet()
            fun uniq(all: List<MediaItem>): List<MediaItem> = all.filter { it.folder !in hide }.distinctBy { Store.key(it) }
            val m = try { uniq(MediaScanner.scanMusic(this)) } catch (e: Exception) { emptyList() }
            val v = try { uniq(MediaScanner.scanVideos(this)) } catch (e: Exception) { emptyList() }
            fun dur(sec: Long): String {
                val h = sec / 3600
                val mi = (sec % 3600) / 60
                return if (h > 0) "$h h $mi min" else "$mi min"
            }
            val mp = m.filter { Store.playCount(it) > 0 }
            val mPlays = mp.sumOf { Store.playCount(it) }
            val mSec = mp.sumOf { Store.playCount(it).toLong() * it.durationSec.toLong() }
            val topArtist = mp.filter { it.artist.isNotBlank() }.groupBy { it.artist }
                .mapValues { e -> e.value.sumOf { Store.playCount(it) } }.maxByOrNull { it.value }
            val vp = v.filter { Store.playCount(it) > 0 || Store.watchSec(it) > 0 }
            val vSec = vp.sumOf { Store.watchSec(it).toLong() }
            val topVideo = vp.maxByOrNull { Store.watchSec(it) }?.takeIf { Store.watchSec(it) > 0 }
            val chTotal = Store.chViews().values.sum()
            val topCh = Store.topChannels(1, 1).firstOrNull()
            val days = Store.activeDays()
            val today = Store.today()
            val week = (0..6).count { (today - it) in days }
            val streak = Store.streakDays()
            // [MES-1] lo más reproducido este mes (solo cuenta desde que se activó el registro)
            val mMonth = m.map { it to Store.monthPlays(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
            val vMonth = v.map { it to Store.monthPlays(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
            val since = Store.monthSince()
            val sb = StringBuilder()
            if (mPlays > 0) {
                sb.append("🎵 Música\n• ").append(if (mPlays == 1) "1 reproducción" else "$mPlays reproducciones")
                    .append(" · ").append(if (mp.size == 1) "1 pista" else "${mp.size} pistas distintas").append("\n")
                sb.append("• ≈ ${dur(mSec)} escuchados (estimado)\n")
                topArtist?.let { sb.append("• Artista más escuchado: ${it.key} (${it.value}×)\n") }
                sb.append("\n")
            }
            if (vp.isNotEmpty()) {
                sb.append("🎬 Videos\n• ${if (vp.size == 1) "1 video visto" else "${vp.size} videos vistos"} · ${dur(vSec)} en total\n")
                topVideo?.let { sb.append("• Más visto: ${prettyTitle(it)}\n") }
                sb.append("\n")
            }
            if (chTotal > 0) {
                sb.append("📺 Canales\n• ${if (chTotal == 1) "1 apertura" else "$chTotal aperturas"}")
                topCh?.let { sb.append(" · más abierto: ${it.first.name} (${it.second}×)") }
                sb.append("\n\n")
            }
            if (mMonth.isNotEmpty() || vMonth.isNotEmpty()) {   // [MES-1]
                sb.append("📆 Este mes\n")
                if (mMonth.isNotEmpty()) {
                    sb.append("• Más escuchadas:\n")
                    mMonth.take(3).forEachIndexed { i, p -> sb.append("   ${i + 1}. ${prettyTitle(p.first)} (${p.second}×)\n") }
                }
                vMonth.firstOrNull()?.let { sb.append("• Video más visto: ${prettyTitle(it.first)} (${it.second}×)\n") }
                if (since > 0) sb.append("(se cuenta desde el ${java.text.SimpleDateFormat("d MMM", java.util.Locale("es")).format(java.util.Date(since))})\n")
                sb.append("\n")
            } else if (since == 0L) {
                sb.append("📆 «Más escuchadas del mes» se cuenta desde esta versión: se llena con el uso.\n\n")
            }
            if (streak > 0) sb.append("📅 Racha: $streak ${if (streak == 1) "día" else "días"} seguidos · activo $week de los últimos 7 días")
            else sb.append("📅 La racha se cuenta desde esta versión: usa la app hoy y mañana la verás.")
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (mPlays == 0 && vp.isEmpty() && chTotal == 0 && streak == 0) {
                    toast("Aún no hay datos para tu resumen (en incógnito no se cuentan)")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("📊 Tu resumen")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("Cerrar", null)
                        .setNeutralButton("Lo más visto") { _, _ -> showTop() }
                        .show()
                }
            }
        }
    }

    /** [VENT-1] Título para «Lo más escuchado/visto»: sin «(Official Video)», «1080p», «_1» ni guiones bajos sueltos. Solo para mostrar. */
    private fun topTitle(p: MediaItem): String {
        var t = prettyTitle(p)
        t = t.replace(Regex("\\s*[\\(\\[](official|video oficial)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\b(2160p|1440p|1080p|720p|480p|4k)\\b", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("_\\d{1,2}$"), "")
        t = t.replace('_', ' ')
        t = t.replace(Regex("\\s{2,}"), " ").trim().trimEnd('-', ' ')
        return t.ifEmpty { prettyTitle(p) }
    }

    private fun showTopDialog(music: Boolean, m: List<MediaItem>, v: List<MediaItem>) {
        // [VENT-1] título limpio solo para mostrar; la misma canción con «_1», «_2»… se suma en una fila;
        // el conteo va en la misma línea («19 ×»); filas de hasta 2 líneas con «…»
        val raw = if (music) m else v
        val groups = LinkedHashMap<String, ArrayList<MediaItem>>()
        raw.forEach { p -> groups.getOrPut(topTitle(p).lowercase()) { ArrayList() }.add(p) }
        val merged = groups.values.sortedByDescending { g -> g.sumOf { Store.playCount(it) } }
        val list = merged.map { it[0] }
        val labels = merged.mapIndexed { i, g ->
            val n = g.sumOf { Store.playCount(it) }
            val w = g.sumOf { Store.watchSec(it) }
            "${i + 1}. ${topTitle(g[0])}  ·  $n ×" + (if (w > 0) " · ${fmt(w)}" else "")
        }
        val ad = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val tv = super.getView(position, convertView, parent)
                (tv as? android.widget.TextView)?.apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                return tv
            }
        }
        val b = AlertDialog.Builder(this)
            .setTitle(if (music) "🎵 Lo más escuchado" else "🎬 Lo más visto")
            .setAdapter(ad) { _, idx ->
                playlist = ArrayList(list)
                playIndex = idx
                playingFromQueue = false
                startPlayback(list[idx], 0)
            }
            .setNegativeButton("Cerrar", null)
        val other = if (music) v else m
        if (other.isNotEmpty()) b.setNeutralButton(if (music) "🎬 Videos" else "🎵 Música") { _, _ -> showTopDialog(!music, m, v) }
        b.show()
    }

    // ------------------------------------------- Respaldo (exportar / importar)

    @Suppress("DEPRECATION")
    private fun showBackupMenu() {
        val bldBk = AlertDialog.Builder(this)   // [ICONOS-9]
        val icBk = bldBk.context
        bldBk
            .setTitle("Respaldo")
            .setItems(arrayOf<CharSequence>(
                CastvIcons.item(icBk, R.drawable.ic_mn_upload, "Exportar respaldo"),
                CastvIcons.item(icBk, R.drawable.ic_mn_download, "Importar respaldo"),
                CastvIcons.item(icBk, R.drawable.ic_mn_share, "Compartir…"),
                CastvIcons.item(icBk, R.drawable.ic_mn_history, "Restaurar copia automática…"))) { _, which ->
                when (which) {
                    2 -> showShareMenu()        // [AUTORESPALDO-1]
                    3 -> showAutoRestore()      // [AUTORESPALDO-1]
                    else -> try {
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

    /** Lee el archivo elegido (en un hilo) y pregunta antes de importarlo. */
    private fun confirmImport(uri: Uri) {
        thread {
            val text = try {
                contentResolver.openInputStream(uri)?.use { Backup.readLimited(it) }
            } catch (e: Exception) {
                DebugLog.add("RESPALDO: error al leer: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (text == null) toast("❌ No se pudo leer el archivo (o es demasiado grande).")
                else confirmImportText(text)
            }
        }
    }

    /** [AUTORESPALDO-1] El aviso dice si el archivo REEMPLAZA (respaldo completo) o AÑADE (parcial). */
    private fun confirmImportText(text: String) {
        val kind = Backup.describe(text)
        if (kind == null) {
            toast("❌ Este archivo no es un respaldo de CASTV.")
            return
        }
        val b = AlertDialog.Builder(this)
        if (kind == "full") {
            b.setTitle("¿Importar respaldo?")
                .setMessage("Esto REEMPLAZA tus favoritos, listas, historial de enlaces, listas M3U y ajustes actuales por los del archivo. Los puntos de reanudación, las estadísticas y el TV elegido no cambian. Si suena algo en el teléfono, se detendrá.")
                .setPositiveButton("Importar") { _, _ -> doImport(text) }
        } else {
            val what = when (kind) {
                "history" -> "el historial de enlaces y los canales vistos recientemente"
                "favs" -> "canales favoritos"
                else -> "datos parciales"
            }
            b.setTitle("¿Añadir lo recibido?")
                .setMessage("Este archivo trae solo $what. Se AÑADE a lo que ya tienes: no se borra nada y no se repite lo que ya esté. Tus listas, ajustes y demás datos no cambian.")
                .setPositiveButton("Añadir") { _, _ -> doImport(text) }
        }
        b.setNegativeButton("Cancelar", null).show()
    }

    private fun doImport(text: String) {
        thread {
            var err: String? = null
            try {
                err = Backup.restore(this, text)
            } catch (e: Exception) {
                DebugLog.add("RESPALDO: error al importar: ${e.javaClass.simpleName}: ${e.message}")
                err = "No se pudo leer el archivo."
            }
            val msg = err
            val partial = Backup.describe(text).let { it != null && it != "full" }   // [AUTORESPALDO-1]
            val note = Backup.lastNote
            runOnUiThread {
                if (msg != null) {
                    toast("❌ $msg")
                } else if (partial) {
                    Store.reload()
                    if (!isFinishing && !isDestroyed) {
                        AlertDialog.Builder(this)
                            .setTitle("✅ Listo")
                            .setMessage(note)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    Store.reload()
                    toast("✅ Respaldo importado")
                    recreate()   // aplica tema, color, orden y demás ajustes
                }
            }
        }
    }

    // ------------------------------------------- [AUTORESPALDO-1] Copia automática y compartir

    private fun autoBackupNow() {
        try {
            AutoBackup.runIfNeeded(applicationContext)?.let { DebugLog.add(it) }
        } catch (e: Exception) {
            DebugLog.add("RESPALDO-AUTO: error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun showAutoRestore() {
        val copies = AutoBackup.list(this)
        if (copies.isEmpty()) {
            toast("Todavía no hay copias automáticas. Se crea una al abrir la app, una vez al día.")
            return
        }
        val labels = copies.map { "${it.day} · ${it.kb} KB" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🔁 Copias automáticas")
            .setItems(labels) { _, i -> loadAutoCopy(copies[i]) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun loadAutoCopy(c: AutoBackup.Copy) {
        thread {
            val text = try { c.file.readText(Charsets.UTF_8) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (text == null) toast("❌ No se pudo leer la copia") else confirmImportText(text)
            }
        }
    }

    private fun showShareMenu() {
        val bldSh = AlertDialog.Builder(this)   // [ICONOS-9]
        val icSh = bldSh.context
        bldSh
            .setTitle("📨 Compartir")
            .setItems(arrayOf<CharSequence>(
                CastvIcons.item(icSh, R.drawable.ic_mn_archive, "Respaldo completo"),
                CastvIcons.item(icSh, R.drawable.ic_mn_history, "Solo historial"),
                CastvIcons.item(icSh, R.drawable.ic_vp_star, "Solo canales favoritos"))) { _, which ->
                confirmShare(when (which) { 0 -> "full"; 1 -> "history"; else -> "favs" })
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun confirmShare(kind: String) {
        AlertDialog.Builder(this)
            .setTitle("Antes de compartir")
            .setMessage("El archivo puede traer enlaces con usuario o clave: compártelo solo con gente de confianza.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Compartir") { _, _ -> shareBackup(kind) }
            .show()
    }

    /** Arma el .json en la carpeta temporal y lo manda por la hoja de compartir de Android. */
    private fun shareBackup(kind: String) {
        thread {
            var uri: Uri? = null
            try {
                val json = if (kind == "full") Backup.build(this) else Backup.buildPart(this, kind)
                if (json != null) {
                    val dir = java.io.File(cacheDir, "share")
                    dir.mkdirs()
                    dir.listFiles()?.forEach { it.delete() }
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    val name = when (kind) {
                        "full" -> "castv_respaldo_$day.json"
                        "history" -> "castv_historial_$day.json"
                        else -> "castv_favoritos_$day.json"
                    }
                    val f = java.io.File(dir, name)
                    f.writeText(json, Charsets.UTF_8)
                    uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.shareprovider", f)
                }
            } catch (e: Exception) {
                DebugLog.add("RESPALDO: error al compartir: ${e.javaClass.simpleName}: ${e.message}")
                uri = null
            }
            val u = uri
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (u == null) {
                    toast("No hay nada para compartir")
                } else {
                    try {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, u)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        send.clipData = android.content.ClipData.newRawUri("", u)
                        startActivity(Intent.createChooser(send, "Compartir respaldo"))
                    } catch (e: Exception) {
                        toast("No se pudo abrir la hoja de compartir")
                    }
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

    // [PULIDO-2a] El aviso de estado ya no queda fijo: aparece y se esconde solo a los 6 s.
    private val hideStatus = Runnable { tvStatus.visibility = View.GONE }
    @Volatile private var tvMissingShown = false

    private fun setStatus(msg: String) {
        runOnUiThread {
            tvStatus.text = msg
            tvStatus.visibility = View.VISIBLE
            tvStatus.removeCallbacks(hideStatus)
            tvStatus.postDelayed(hideStatus, 6000)
        }
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
        val rep = when (repeatMode) {   // [PLAYER-TIPS-1] el texto del botón sigue el modo
            Repeat.OFF -> "Repetir: apagado"
            Repeat.ALL -> "Repetir: toda la lista"
            Repeat.ONE -> "Repetir: una pista"
        }
        btnAuto.contentDescription = rep
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnAuto, rep)
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
        // [ZAP-1] con un canal de una lista en el TV, ⏮ ⏭ cambian de canal (y no tocan la música ni el video)
        if (linkOnTv && Zap.matches(linkTitleNow)) {
            toast("Cambiando de canal…")
            Zap.zap(this, delta) { ch, msg ->
                runOnUiThread {
                    if (ch != null) {
                        Store.addLink(ch.url, ch.name)
                        Store.rememberRecentChannel(ch)
                    }
                    toast(msg)
                }
            }
            return
        }
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
            setStatus("⚠️ Lista vacía")
            return
        }
        playIndex = if (playIndex < 0) 0 else Math.floorMod(playIndex + delta, playlist.size)
        startPlayback(playlist[playIndex])
    }

    private fun shuffleAndPlay() {
        if (currentList.isEmpty()) {
            setStatus("⚠️ Lista vacía")
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
            .setNegativeButton("📱 En el teléfono") { _, _ -> playLink(input.text.toString().trim(), false) }
            .setNeutralButton("🕘 Historial") { _, _ -> showLinkHistory() }
            .show()
    }

    // ------------------------------------------- [VIDEO-MP3-1b] Enlace → MP3 / descargar

    @Volatile private var linkCancel = false
    private var linkDialog: AlertDialog? = null

    /** Pide un enlace directo a un archivo y deja elegir: 🎵 convertirlo a MP3 o ⬇ descargarlo (los dos van a Descargas). */
    private fun askLinkToMp3() {
        val input = EditText(this).apply {
            hint = "https://…/video.mp4"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val c = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (c != null && (c.startsWith("http://") || c.startsWith("https://"))) setText(c)
            } catch (e: Exception) { }
        }
        val box = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Enlace → MP3 / descargar")
            .setMessage("Pega un enlace directo a un archivo (.mp4, .mkv, .mp3…). No sirve con YouTube ni redes, directos, listas .m3u8 ni enlaces con usuario y clave.")
            .setView(box)
            .setPositiveButton("🎵 A MP3") { _, _ -> runLinkJob(input.text.toString().trim(), true) }
            .setNegativeButton("⬇ Descargar") { _, _ -> runLinkJob(input.text.toString().trim(), false) }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun runLinkJob(url: String, toMp3: Boolean) {
        val bad = LinkDownload.check(url)
        if (bad != null) { toast(bad); return }
        if (Mp3Converter.running.get() || LinkDownload.running.get()) { toast("Ya hay una conversión o descarga en curso"); return }
        val app = applicationContext
        val title = if (toMp3) "🎵 Enlace → MP3" else "⬇ Descargar enlace"
        linkCancel = false
        LinkJobNotifier.cancel = false   // [DESCARGA-BG-1]
        // [DESCARGA-BG-1] con las notificaciones permitidas va en segundo plano (notificación con % y Cancelar);
        // si Android las tiene apagadas para CASTV, queda el recuadro de siempre (dlg != null)
        val dlg: AlertDialog? = if (LinkJobNotifier.enabled(app)) null else AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Preparando…")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        if (dlg != null) {
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                linkCancel = true
                dlg.setMessage("Cancelando…")
            }
            linkDialog = dlg
        } else {
            LinkJobNotifier.progress(app, title, "Preparando…", -1)
            toast("En segundo plano: mira la notificación para ver el avance o cancelar")
        }
        val isCancelled = { linkCancel || LinkJobNotifier.cancel }
        val name = LinkDownload.nameFromUrl(url)
        thread {
            var ok = false
            var cancelled = false
            var msg = ""
            var savedUri: Uri? = null   // [MP3-ABRIR-1]
            try {
                if (toMp3) {
                    val r = Mp3Converter.convert(app, Uri.parse(url), name, { pct ->
                        if (dlg == null) LinkJobNotifier.progress(app, title, "Convirtiendo… $pct %", pct)
                        else runOnUiThread { if (!isFinishing && linkDialog === dlg) dlg.setMessage("Convirtiendo… $pct %") }
                    }, isCancelled)
                    ok = r.ok; cancelled = r.cancelled; msg = if (r.ok) r.savedAs else r.message; savedUri = r.uri
                } else {
                    val r = LinkDownload.download(app, url, { pct, mb ->
                        val text = if (pct >= 0) "Descargando… $pct %" else "Descargando… $mb MB"
                        if (dlg == null) LinkJobNotifier.progress(app, title, text, pct)
                        else runOnUiThread { if (!isFinishing && linkDialog === dlg) dlg.setMessage(text) }
                    }, isCancelled)
                    ok = r.ok; cancelled = r.cancelled; msg = if (r.ok) r.savedAs else r.message; savedUri = r.uri
                }
            } catch (e: Throwable) {
                msg = "Error: " + e.javaClass.simpleName
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (dlg != null) {
                    try { if (linkDialog === dlg) dlg.dismiss() } catch (e: Exception) { }
                    if (linkDialog === dlg) linkDialog = null
                }
                val icon = if (toMp3) "🎵" else "⬇"
                if (ok && !cancelled) {
                    // [DESCARGA-BG-1] con la app a la vista sale el recuadro Abrir / Compartir; si no, la notificación final
                    val front = !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                    if (dlg != null || front) {
                        if (dlg == null) LinkJobNotifier.clear(app)
                        SavedFile.offer(this, icon, msg, savedUri)   // [MP3-ABRIR-1] Abrir / Compartir
                    } else {
                        LinkJobNotifier.done(app, "$icon Guardado en Descargas", msg, savedUri)
                    }
                } else {
                    val text = if (cancelled) "Cancelado" else "No se pudo: " + msg.replace(url, "(enlace)")   // R9: el enlace no se muestra
                    if (dlg == null) {
                        if (cancelled) LinkJobNotifier.clear(app)
                        else LinkJobNotifier.done(app, "$icon No se pudo completar", text.removePrefix("No se pudo: "), null)
                    }
                    Toast.makeText(app, text, Toast.LENGTH_LONG).show()
                }
            }
        }
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
            .setNeutralButton("Eliminar historial") { _, _ ->
                Store.clearRecentLinks()
                toast("Historial borrado (los favoritos se conservan)")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showLinkActions(l: SavedLink) {
        val b = AlertDialog.Builder(this)   // [ICONOS-7]
        val ic = b.context
        val labels = arrayOf<CharSequence>(
            CastvIcons.item(ic, R.drawable.ic_vp_tv, "Al TV"),
            CastvIcons.item(ic, R.drawable.ic_vp_phone, "En el teléfono"),
            if (l.fav) CastvIcons.item(ic, R.drawable.ic_vp_star_border, "Quitar de favoritos") else CastvIcons.item(ic, R.drawable.ic_vp_star, "Marcar favorito"),
            CastvIcons.item(ic, R.drawable.ic_vp_delete, "Quitar del historial"))
        b
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

    /** [AVISO-HLS-2] El canal no se pudo preparar: se dice por qué y se ofrece verlo en el móvil o probar igual en el TV. */
    private fun warnLink(url: String, title: String, why: String, titleOverride: String?) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Este canal puede no verse en el TV")
            .setMessage("«$title»\n\n$why\n\nPuedes verlo en el teléfono o probar en el TV de todas formas.")
            .setPositiveButton("📱 En el teléfono") { _, _ -> playLink(url, false, titleOverride) }
            .setNeutralButton("📺 Enviar igual") { _, _ -> playLink(url, true, titleOverride, true) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [RED-1] true si la conexión activa es solo de datos móviles (no Wi-Fi). */
    private fun onMobileData(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null &&   // [RED-1b] también datos móviles con VPN encima
                !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) &&
                (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || cm.isActiveNetworkMetered)
        } catch (e: Exception) { false }
    }

    private fun playLink(url: String, toTv: Boolean, titleOverride: String? = null, skipCheck: Boolean = false) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            setStatus("⚠️ El enlace debe empezar con http:// o https://")
            return
        }
        val title = titleOverride?.takeIf { it.isNotBlank() } ?: linkTitle(url)
        // [RED-1] enlace en el móvil con datos móviles: se pregunta antes de gastar datos (Ajustes › Red)
        if (!toTv && !skipCheck && getSharedPreferences("castv", MODE_PRIVATE).getBoolean("warn_mobile", true) && onMobileData()) {
            AlertDialog.Builder(this)
                .setTitle("📶 Estás usando datos móviles")
                .setMessage("Reproducir \"$title\" en el teléfono gastará datos de tu plan. ¿Seguir?")
                .setPositiveButton("Reproducir") { _, _ -> playLink(url, false, titleOverride, true) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        // [AVISO-HLS-2] canal HLS al TV: antes de tocar nada (historial, lo que suena), se revisa que se pueda preparar
        if (toTv && !skipCheck && isHls(url)) {
            setStatus("🔎 Revisando el canal…")
            thread {
                val why = if (TvController.tvIp != null && getLocalIpAddress() != null) MediaServer.probeLive(url) else null
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (why == null) {
                        playLink(url, true, titleOverride, true)
                    } else {
                        DebugLog.add("AVISO-HLS: $title: $why")
                        setStatus("⚠️ $title: no se pudo preparar")
                        warnLink(url, title, why, titleOverride)
                    }
                }
            }
            return
        }
        Store.addLink(url, title)   // historial de enlaces
        Zap.nowTitle = null; linkOnTv = false   // [PAUSA-1] se vuelve a marcar solo si el TV acepta este enlace
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
        // Directo al TV por DLNA: no pasa por el servidor interno
        if (url.startsWith("https://")) setStatus("🚀 Enviando enlace https al TV (algunos TV lo rechazan)…")
        else setStatus("🚀 Enviando enlace al TV…")
        thread {
            if (TvController.tvIp == null) {
                runOnUiThread { setStatus("⚠️ TV no encontrado todavía. Espera unos segundos o usa ⋮ › Elegir TV.") }
                return@thread
            }
            val live = isHls(url)   // [LIVE-1b] canal en vivo: el teléfono lo entrega como un solo flujo TS (/live)
            val mime = if (live) "video/mp2t" else "video/mp4"
            // [Etapa 13-A.2] mp4: el TV pide el video al teléfono (/relay) y el teléfono lo baja del enlace
            // (https, redirecciones).
            // [Etapa 13-A.3] HLS (.m3u8) también va por /relay: el teléfono reescribe la lista y sirve los
            // trozos, así el TV solo habla http con el teléfono. La extensión (.m3u8 o .mp4) le dice al relay qué es.
            val phoneIp = getLocalIpAddress()
            val sendUrl = if (phoneIp != null) (if (live) TvSend.liveUrl(phoneIp, url) else TvSend.relayUrl(phoneIp, url, false)) else url   // [Etapa 14-C] [LIVE-1b]
            DebugLog.add("ENLACE: $url -> $sendUrl")
            val ok = TvController.setUriAndPlay(sendUrl, title, mime, false)
            if (ok) linkOnTv = true   // [PAUSA-1] ⏸/▶ ahora controlan el enlace del TV
            if (ok && !live) readLinkDuration(sendUrl, gen)   // [PROG-1d] (en vivo no hay duración: se evita abrir un segundo flujo)
            runOnUiThread {
                if (ok) {
                    tvNowPlaying.text = title   // [PROG-1] título del enlace en el reproductor
                    nowBigArt.tag = null; nowBigArt.visibility = View.GONE   // [UI-3a]
                    linkTitleNow = title; linkIsLive = live; lastNotifKey = ""   // [NOW-1] la notificación también lo muestra
                    linkUrlNow = url; updateNowFav(); updateMiniCompact()   // [FAV-REPRO-3]
                }
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
        if (Themes.savedBg(this) != bgAtStart) { recreate(); return }   // [TEMAS-2] fondo cambiado en Ajustes
        if (CastvTextSize.saved(this) != textAtStart) { recreate(); return }   // [TEXTO-TAM-1] tamaño de texto cambiado en Ajustes
        updateIncogBadge()   // [INCOG-2]
        applySofa()   // [SOFA-1] «Botones grandes» cambiado en Ajustes
        if (::adapter.isInitialized) {   // refresca "Continuar" (y "No vistos") al volver del reproductor
            if (onlyUnseen && !showingMusic) applyFilters() else adapter.notifyDataSetChanged()
        }
        // [CARPETAS-1] si en Ajustes se mostraron carpetas de nuevo, relee la lista conservando los filtros
        if (lastScanMs != 0L && actionMode == null && hiddenFolderSet().sorted().joinToString("|") != hiddenSig) {
            loadMedia(showingMusic, keepFilters = true)
        }
        // [BIBLIO-1] re-escaneo opcional (Ajustes › Biblioteca): solo si pasó más de un minuto y no hay selección activa
        if (getSharedPreferences("castv", MODE_PRIVATE).getBoolean("rescan_resume", false) &&
            lastScanMs != 0L && actionMode == null &&
            System.currentTimeMillis() - lastScanMs > 60_000L) {
            loadMedia(showingMusic, keepFilters = true)
        }
        if (!updateAsked) { updateAsked = true; maybeAnnounceUpdate() }   // [UPDATE-2]
    }

    // ------------------------------------------------- [UPDATE-2] Aviso de versión nueva al abrir la app

    private var updateAsked = false

    /** Una vez al abrir la app (y como mucho cada 6 h) mira el último Release; si es más nuevo, avisa. Sin conexión: silencio. */
    private fun maybeAnnounceUpdate() {
        val sp = getSharedPreferences("castv", MODE_PRIVATE)
        if (!sp.getBoolean("upd_notify", true)) return
        val now = System.currentTimeMillis()
        if (now - sp.getLong("upd_last", 0L) < 6L * 3600_000L) return
        thread {
            val r = try { Updater.check(this) } catch (e: Exception) { return@thread }
            val rel = r.release
            runOnUiThread {
                if (isFinishing || isDestroyed || !r.ok || rel == null) return@runOnUiThread
                if (!r.newer) {
                    sp.edit().putLong("upd_last", now).apply()
                    return@runOnUiThread
                }
                // "Más tarde" reciente para esta misma versión: no molestar
                if (sp.getString("upd_later_tag", "") == rel.tag && now < sp.getLong("upd_later_until", 0L)) {
                    sp.edit().putLong("upd_last", now).apply()
                    return@runOnUiThread
                }
                // si ya se salió de esta pantalla (p. ej. a un video), no se muestra y se intenta en la próxima apertura
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@runOnUiThread
                sp.edit().putLong("upd_last", now).apply()
                showUpdateNotice(rel, sp)
            }
        }
    }

    private fun showUpdateNotice(rel: Updater.Release, sp: android.content.SharedPreferences) {
        val installed = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
        val mb = String.format(java.util.Locale.US, "%.1f", rel.size / 1_000_000.0)
        val later = {
            sp.edit().putString("upd_later_tag", rel.tag)
                .putLong("upd_later_until", System.currentTimeMillis() + 24L * 3600_000L).apply()
        }
        AlertDialog.Builder(this)
            .setTitle("🆕 Hay una versión nueva")
            .setMessage("Instalada: $installed\nNueva: ${rel.tag} ($mb MB)\n\n¿Quieres actualizar ahora?")
            .setPositiveButton("Actualizar ahora") { _, _ ->
                // Ajustes abre directo la búsqueda de actualización (muestra tamaño, notas y aviso de datos móviles)
                startActivityForResult(
                    Intent(this, SettingsActivity::class.java).putExtra("autoUpdate", true), reqSettings)
            }
            .setNegativeButton("Más tarde") { _, _ -> later() }
            .setOnCancelListener { later() }
            .show()
    }

    private fun startPlayback(item: MediaItem, resumeAt: Int = 0) {
        if (!item.isMusic && outPhone) {
            playVideoOnPhone(item, resumeAt)
            return
        }
        playingItem = item
        Zap.nowTitle = null; linkOnTv = false; linkDurSec = 0   // [PAUSA-1] lo nuevo reemplaza al enlace
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
            setStatus("⚠️ Sin puerto libre (8000-8010): el TV no podrá leer el archivo")
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

    /** [TRAER-1] Al cambiar la salida a Teléfono con una pista o video sonando en el TV: ofrece seguir aquí desde el mismo punto. */
    private fun offerBringToPhone() {
        val item = playingItem ?: return
        val pos = if (lastDur > 0 && lastPos >= lastDur - 5) 0 else lastPos
        AlertDialog.Builder(this)
            .setTitle(if (item.isMusic) "¿Seguir escuchando aquí?" else "¿Seguir viendo aquí?")
            .setMessage("«${prettyTitle(item)}» está en el TV (${fmt(pos)}). ¿Pasarlo al teléfono desde ese punto?")
            .setPositiveButton("Sí, pasar") { _, _ ->
                // el video detiene el TV en playVideoOnPhone; la música, en startLocal
                if (item.isMusic) startPlayback(item, pos) else playVideoOnPhone(item, pos)
            }
            .setNegativeButton("No", null)
            .show()
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
                            tvMissingShown = false
                        }
                        val label = if (TvController.tvName.isNotEmpty()) TvController.tvName
                                    else (TvController.tvIp ?: "")
                        // [PULIDO-2a] "No se encontró el TV" se avisa una sola vez, no cada 5 s.
                        // [SALIDA-INI-1] En modo Teléfono se sigue buscando el TV, pero sin avisos en pantalla.
                        val say = !outPhone && (found || !tvMissingShown)
                        if (!found && !outPhone) tvMissingShown = true
                        runOnUiThread {
                            if (found) updateOutputButton()
                            if (say) setStatus(
                                if (found) "✅ TV encontrado: $label"
                                else "⚠️ No se encontró el TV. Reintentando..."
                            )
                        }
                    }
                } else {
                    poll(tick)
                }
                updateMiniCompact()   // [PULIDO-2b]
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
                        tvDuration.text = if (linkOnTv && linkIsLive) "En vivo" else "--:--"   // [NOW-1]
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
        checkAutoResend(state)   // [AUTO-REENVIO-1]

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

    /** [AUTO-REENVIO-1] Si un canal en vivo que sonaba en el TV se detiene justo después de un atasco del canal en el
     *  teléfono (corte de red, pantalla bloqueada…), se reenvía solo, una vez por corte (tope: 2 cada 10 min).
     *  No actúa si la TV se detuvo por otra razón (control remoto, cambio de entrada), si hay pausa, o si tú detuviste
     *  el canal. Se llama desde poll(), cada 2 s, en el hilo del monitor. */
    private fun checkAutoResend(state: String) {
        val url = linkUrlNow
        val title = linkTitleNow
        if (!linkOnTv || !linkIsLive || url.isNullOrEmpty() || title == null || outPhone || localActive) {
            reSeenPlaying = false; reStopAt = 0L
            return
        }
        if (state == "PLAYING") { reSeenPlaying = true; reStopAt = 0L; return }
        if (state != "STOPPED") { if (state != "UNKNOWN") reStopAt = 0L; return }   // pausa u otro estado: no se toca
        if (!reSeenPlaying || reBusy) return
        val nowMs = System.currentTimeMillis()
        if (reStopAt == 0L) reStopAt = nowMs
        if (nowMs - reStopAt < 3000L) return   // el instante entre Stop y Play de un cambio de canal no cuenta
        val stall = MediaServer.lastLiveStallMs
        if (stall == 0L || stall < reStopAt - 60_000L) {
            // sin atasco a la vista: se espera un poco por si el aviso llega después; pasado ese tiempo, se deja en paz
            if (nowMs - reStopAt > 45_000L) {
                DebugLog.add("AUTO-REENVIO: la TV se detuvo sin atasco del canal; no se reenvía")
                reSeenPlaying = false; reStopAt = 0L
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        reTimes.removeAll { now - it > 600_000L }
        if (reTimes.size >= 2) {
            DebugLog.add("AUTO-REENVIO: ya se reenvió 2 veces en 10 min; no se insiste")
            reSeenPlaying = false; reStopAt = 0L
            return
        }
        reTimes.add(now)
        reBusy = true; reSeenPlaying = false; reStopAt = 0L
        DebugLog.add("AUTO-REENVIO: la TV cortó '$title' tras un atasco del canal; se reenvía")
        runOnUiThread { setStatus("🔄 Reconectando el canal…") }
        thread {
            try {
                val why = Zap.sendUrl(this, title, url)
                if (why != null) {
                    DebugLog.add("AUTO-REENVIO: falló: $why")
                    runOnUiThread { setStatus("⚠️ No pude reconectar el canal: $why") }
                }
            } finally { reBusy = false }
        }
    }

    // ------------------------------------- Notificación con controles

    private fun localIsPlaying(): Boolean =
        try { localPlayer?.isPlaying == true } catch (e: Exception) { false }

    /** Pausa / reanuda, ya sea en el teléfono o en el TV. */
    private fun togglePlayPause() {
        cancelSleepFade()   // [FUNDIDO-1]
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
        val item = playingItem
        if (item == null) {   // [NOW-1] canal o enlace en el TV: no hay MediaItem, pero la notificación debe mostrarlo
            if (linkOnTv) refreshLinkNotification()
            return
        }
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
        CastvWidget.push(this, title, if (playing) "Sonando en el $where" else "En pausa · $where", playing)   // [WIDGET-1]
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

    /** [NOW-1] Notificación de un canal o enlace que está en el TV (sin carátula; solo Pausa/Reproducir). */
    private fun refreshLinkNotification() {
        val playing = tvPlaying
        updateQuickIcon(playing)
        updateSessionState(playing)
        val title = linkTitleNow ?: "Enlace en el TV"
        val zapOn = Zap.matches(linkTitleNow)   // [ZAP-1] ⏮ ⏭ en la notificación del canal
        val hasCh = linkOnTv && linkUrlNow != null   // [FAV-REPRO-3]
        val key = "link|$title|$playing|$linkIsLive|$zapOn|$hasCh|$tvFavNow"
        if (key == lastNotifKey) return
        lastNotifKey = key
        CastvWidget.push(this, title, if (playing) (if (linkIsLive) "En vivo en el TV" else "Sonando en el TV") else "En pausa · TV", playing)   // [WIDGET-1]
        try {
            val md = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "CASTV · TV")
            mediaSession?.setMetadata(md.build())
            mediaSession?.isActive = true
        } catch (e: Exception) { }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val toggle = PendingIntent.getBroadcast(this, 2, Intent(ACT_TOGGLE).setPackage(packageName), flags)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val nb = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(if (playing) (if (linkIsLive) "En vivo en el TV" else "Sonando en el TV") else "En pausa · TV")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (zapOn) nb.addAction(android.R.drawable.ic_media_previous, "Anterior",
            PendingIntent.getBroadcast(this, 1, Intent(ACT_PREV).setPackage(packageName), flags))
        nb.addAction(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "Pausa" else "Reproducir", toggle
        )
        if (zapOn) nb.addAction(android.R.drawable.ic_media_next, "Siguiente",
            PendingIntent.getBroadcast(this, 3, Intent(ACT_NEXT).setPackage(packageName), flags))
        if (hasCh) nb.addAction(   // [FAV-REPRO-3] ★ en la notificación (cuarto botón; se ve al expandirla)
            if (tvFavNow) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
            if (tvFavNow) "Quitar de favoritos" else "Favorito",
            PendingIntent.getBroadcast(this, 4, Intent(ACT_FAV).setPackage(packageName), flags))
        val n = nb.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(*(if (zapOn) intArrayOf(0, 1, 2) else intArrayOf(0)))
            )
            .build()
        try {
            getSystemService(NotificationManager::class.java).notify(1, n)
        } catch (e: Exception) { }
    }

    /** Al cerrar la app, la notificación vuelve a ser la sencilla del servicio. */
    private fun postDefaultNotification() {
        CastvWidget.reset(this)   // [WIDGET-1]
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

    /** [INCOG-2] Muestra u oculta el 🕵️‍♂️ de la barra superior según el modo incógnito. */
    private fun updateIncogBadge() {
        if (::tvIncog.isInitialized) tvIncog.visibility = if (Store.incognito) View.VISIBLE else View.GONE
    }

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

    /** [REMOTO-1b] Si se sube el volumen con el TV en silencio, se quita el silencio (el icono vuelve a 🔊). */
    private fun leaveMuteIfRaised(v: Int) {
        if (!localActive && muted && v > 0) {
            muted = false
            volBeforeMute = -1
            btnMute.setImageResource(R.drawable.ic_vol_on)
        }
    }

    private fun applyLocalVolume() {
        val v = if (muted) 0f else 1f
        try { localPlayer?.setVolume(v, v) } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun startLocal(item: MediaItem, resumeAt: Int) {
        val xo = gapXfOld.takeIf { it != null && it === localPlayer }   // [GAPLESS-1] viene de un fundido
        gapXfOld = null
        val pre = if (resumeAt == 0) nextTrack.take(item, localPlayer) else { nextTrack.discard(localPlayer); null }   // [GAPLESS-1]
        stopLocal(if (pre != null) xo else null)
        // Si el TV estaba reproduciendo algo, se detiene: ahora suena en el teléfono
        thread { try { if (TvController.tvIp != null) TvController.stop() } catch (e: Exception) { } }
        audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        pausedByFocus = false
        pendingResume = 0   // el salto se hace aquí mismo, no hace falta esperar al TV

        if (pre != null) {   // [GAPLESS-1] sin pausa o fundido: la pista ya está preparada (y, sin pausa, ya sonando)
            localPlayer = pre
            localPrepared = true
            localActive = true
            try {
                pre.setOnCompletionListener { p -> if (localPlayer === p) onLocalCompleted() }
                pre.setOnErrorListener { p, _, _ ->
                    if (localPlayer === p) {
                        stopLocal()
                        adapter.playingLocal = false
                        adapter.notifyDataSetChanged()
                        setStatus("❌ No se pudo reproducir en el teléfono (formato no compatible o archivo dañado)")
                    }
                    true
                }
                if (xo != null) pre.setVolume(0f, 0f) else applyLocalVolume()
                if (!pre.isPlaying) pre.start()
                attachEffects(pre)
                applySpeed()
                wasPlaying = true
                if (xo != null) PlayerCore.startXfade(xo, pre, PlayerCore.xfadeSec(this) * 1000L) { if (muted) 0f else 1f }
                setStatus("✅ Reproduciendo en el teléfono")
                gapStart()
                return
            } catch (e: Exception) {
                try { pre.release() } catch (e2: Exception) { }
                localPlayer = null
                localPrepared = false
                localActive = false
                try { xo?.release() } catch (e2: Exception) { }
            }
        }

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
                    gapStart()   // [GAPLESS-1]
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
    private fun stopLocal(keep: MediaPlayer? = null) {   // [GAPLESS-1] keep = el que baja en un fundido (lo suelta el fundido)
        abReset()   // [AB-MUSICA-1] cambiar de pista, detener o pasar al TV apaga el A-B
        gapStop()   // [GAPLESS-1]
        val mp = localPlayer
        nextTrack.discard(mp)   // [GAPLESS-1] la siguiente preparada (y su enlace) se sueltan antes que esta
        PlayerCore.cancelXfade()
        localPlayer = null
        localActive = false
        localPrepared = false
        pausedByFocus = false
        releaseEffects()
        if (mp != null && mp !== keep) {
            try { mp.release() } catch (e: Exception) { }
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun onLocalCompleted() {
        gapSettle()   // [GAPLESS-1]
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
        bindBigArt(item)   // [UI-3a]
        updateNowFav()
    }

    /** [UI-3a] Carátula grande del panel expandido: la nítida si ya está, o la miniatura mientras llega. */
    private fun bindBigArt(item: MediaItem) {
        val tkey = Thumbs.key(item)
        nowBigArt.tag = tkey
        val big = Thumbs.cachedBig(item)
        val first = big ?: Thumbs.cached(item)
        if (first != null) {
            nowBigArt.setImageBitmap(first)
            nowBigArt.visibility = View.VISIBLE
        } else {
            nowBigArt.setImageDrawable(null)
            nowBigArt.visibility = View.GONE
        }
        if (big != null) return
        Thumbs.loadBig(this, item) { bmp ->
            if (nowBigArt.tag != tkey) return@loadBig   // ya cambió la canción o llegó un canal
            val show = bmp ?: Thumbs.cached(item)
            if (show != null) {
                nowBigArt.setImageBitmap(show)
                nowBigArt.visibility = View.VISIBLE
            }
        }
    }

    /**
     * [UI-3b] Pantalla completa "Reproduciendo": una ventana propia que refleja el panel del mini reproductor
     * (título, carátula, progreso, ▶/⏸, ★, repetir) y manda sus toques a los mismos botones, así que la
     * reproducción no cambia. Se cierra con ⌄ o con "atrás".
     */
    private fun showFullPlayer() {
        if (playingItem == null && !linkOnTv) { toast("No hay nada reproduciéndose"); return }
        fullDialog?.dismiss()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val bgTv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, bgTv, true)
        val ripple = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        val lin = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(bgTv.data)
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(20) + b.left, dp(12) + b.top, dp(20) + b.right, dp(20) + b.bottom)
            ins
        }

        fun imgBtn(res: Int, desc: String, h: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            contentDescription = desc
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(ripple.resourceId)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, dp(h), 1f)
            setOnClickListener { haptic(it); onClick() }   // [VIBRA-1]
        }
        fun txtBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t
            gravity = android.view.Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(col(R.color.vlc_text))
            setBackgroundResource(ripple.resourceId)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener { haptic(it); onClick() }   // [VIBRA-1]
        }

        val dlg = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar)

        // Arriba: ⌄ para cerrar
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_expand)
            rotation = 180f
            contentDescription = "Cerrar"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(ripple.resourceId)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { dlg.dismiss() }
        }
        root.addView(close)

        // Carátula grande (ocupa el espacio que sobra)
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            setBackgroundResource(R.drawable.bg_thumb)
            clipToOutline = true
        }
        // [LRC-1] La carátula y la letra comparten el mismo espacio: se ve una u otra
        val artBox = android.widget.FrameLayout(this)
        artBox.addView(art, android.widget.FrameLayout.LayoutParams(lin, lin))
        val lyrBox = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(8), dp(110), dp(8), dp(110))
        }
        val lyrScroll = android.widget.ScrollView(this).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = false
            setBackgroundResource(R.drawable.bg_thumb)
            clipToOutline = true
            addView(lyrBox, ViewGroup.LayoutParams(lin, wrap))
        }
        artBox.addView(lyrScroll, android.widget.FrameLayout.LayoutParams(lin, lin))
        root.addView(artBox, android.widget.LinearLayout.LayoutParams(lin, 0, 1f).apply { setMargins(0, dp(8), 0, dp(16)) })
        var lyrDoc: Lrc.Doc? = null
        var lyrKey: String? = null
        var lyrRev = -1
        var showLyr = false
        var lyrIdx = -2
        var tintAcc = accentColor()
        val lyrViews = ArrayList<TextView>()

        val title = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (Build.VERSION.SDK_INT >= 23) breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED   // [TITULO-BAL-1]
            setTextColor(col(R.color.vlc_text))
        }
        root.addView(title, android.widget.LinearLayout.LayoutParams(lin, wrap))

        // Progreso
        val seek = SeekBar(this).apply {
            max = 100
            isEnabled = false
            progressTintList = ColorStateList.valueOf(accentColor())
            thumbTintList = ColorStateList.valueOf(accentColor())
            progressBackgroundTintList = ColorStateList.valueOf(0xFF555555.toInt())
            setPadding(0, 0, 0, 0)
        }
        root.addView(seek, android.widget.LinearLayout.LayoutParams(lin, wrap).apply { topMargin = dp(16) })
        val times = android.widget.RelativeLayout(this)
        val el = TextView(this).apply { text = "0:00"; textSize = 12f; setTextColor(col(R.color.vlc_muted)) }
        val du = TextView(this).apply { text = "--:--"; textSize = 12f; setTextColor(col(R.color.vlc_muted)) }
        times.addView(el, android.widget.RelativeLayout.LayoutParams(wrap, wrap).apply { addRule(android.widget.RelativeLayout.ALIGN_PARENT_START) })
        times.addView(du, android.widget.RelativeLayout.LayoutParams(wrap, wrap).apply { addRule(android.widget.RelativeLayout.ALIGN_PARENT_END) })
        root.addView(times, android.widget.LinearLayout.LayoutParams(lin, wrap))

        var dragging = false
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) el.text = fmt(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true; userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val target = sb.progress
                dragging = false
                userSeeking = false
                if (localActive) withLocal { it.seekTo(target * 1000) }
                else thread { TvController.seek(target) }
            }
        })

        // Fila 1: −10, anterior, ▶/⏸, siguiente, +10
        val row1 = android.widget.LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        row1.addView(txtBtn("−10") { findViewById<View>(R.id.btnBack10).performClick() })
        row1.addView(imgBtn(R.drawable.ic_prev, "Anterior", 52) { findViewById<View>(R.id.btnPrev).performClick() })
        val play = ImageButton(this).apply {
            setBackgroundResource(R.drawable.ctrl_play)
            contentDescription = "Reproducir o pausar"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(68), dp(68))
            setOnClickListener { haptic(it); btnPlay.performClick() }   // [VIBRA-1]
        }
        row1.addView(play)
        row1.addView(imgBtn(R.drawable.ic_next, "Siguiente", 52) { findViewById<View>(R.id.btnNext).performClick() })
        row1.addView(txtBtn("+10") { findViewById<View>(R.id.btnFwd10).performClick() })
        root.addView(row1, android.widget.LinearLayout.LayoutParams(lin, wrap).apply { topMargin = dp(8) })

        // Fila 2: mezclar, detener, ★, repetir
        val row2 = android.widget.LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        row2.addView(imgBtn(R.drawable.ic_shuffle, "Mezclar", 48) { findViewById<View>(R.id.btnShuffle).performClick() })
        row2.addView(imgBtn(R.drawable.ic_stop, "Detener", 48) {
            findViewById<View>(R.id.btnStop).performClick()
            dlg.dismiss()
        })
        val fav = txtBtn("☆") { }.apply { textSize = 24f }
        row2.addView(fav)
        fav.setOnClickListener {   // [ANIM-1] igual que antes (vibración + ★ del mini reproductor), y la ★ grande también «late»
            haptic(it)
            btnNowFav.performClick()
            if (btnNowFav.text.toString() == "★") StarPop.pop(fav)
        }
        val auto = imgBtn(R.drawable.ic_repeat, "Auto-avance", 48) { btnAuto.performClick() }
        row2.addView(auto)
        val lyrBtn = txtBtn("🎤") { }.apply {   // [LRC-1] los toques se ponen más abajo, cuando ya existen las funciones de la letra
            textSize = 20f
            visibility = View.GONE
        }
        row2.addView(lyrBtn)
        val cutBtn = txtBtn("✂️") { }.apply {   // [CORTAR-1a] los toques se ponen más abajo; solo se ve con algo sonando
            textSize = 20f
            visibility = View.GONE
        }
        row2.addView(cutBtn)
        val abBtn = txtBtn("A-B") { }.apply {   // [AB-MUSICA-1] los toques se ponen más abajo; solo se ve con música en el teléfono
            textSize = 14f
            visibility = View.GONE
        }
        row2.addView(abBtn)
        root.addView(row2, android.widget.LinearLayout.LayoutParams(lin, wrap).apply { topMargin = dp(8) })

        // [REMOTO-1a] Fila 3: silencio + volumen (reflejan y mandan lo mismo que el mini reproductor)
        val row3 = android.widget.LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val muteBtn = ImageButton(this).apply {
            setImageResource(if (muted) R.drawable.ic_vol_off else R.drawable.ic_vol_on)
            contentDescription = "Silenciar"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(ripple.resourceId)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { btnMute.performClick() }
        }
        row3.addView(muteBtn)
        val volFull = SeekBar(this).apply {
            max = 100
            progress = volSeek.progress
            progressTintList = ColorStateList.valueOf(accentColor())
            thumbTintList = ColorStateList.valueOf(accentColor())
            progressBackgroundTintList = ColorStateList.valueOf(0xFF555555.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(0, wrap, 1f)
        }
        volFull.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) { volTouching = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val v = sb.progress
                volSeek.progress = v
                volTouching = false
                if (localActive) {
                    val mx = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * mx + 50) / 100, 0)
                } else {
                    leaveMuteIfRaised(v)   // [REMOTO-1b]
                    thread { TvController.setVolume(v) }
                }
            }
        })
        row3.addView(volFull)
        root.addView(row3, android.widget.LinearLayout.LayoutParams(lin, wrap).apply { topMargin = dp(8) })

        // [UI-ART-1] El fondo y las barras toman el color de la carátula (sin carátula, los de siempre)
        fun applyArtTint(bmp: android.graphics.Bitmap?) {
            val dom = if (bmp != null) dominantColor(bmp) else null
            val bg = if (dom != null) androidx.core.graphics.ColorUtils.blendARGB(bgTv.data, dom, 0.30f) else bgTv.data
            root.setBackgroundColor(bg)
            val acc = if (dom != null) readableAccent(dom, bgTv.data) else accentColor()
            for (sb in arrayOf(seek, volFull)) {
                sb.progressTintList = ColorStateList.valueOf(acc)
                sb.thumbTintList = ColorStateList.valueOf(acc)
            }
            tintAcc = acc   // [LRC-1] la línea de la letra que suena usa el mismo color
            lyrIdx = -2
        }

        // [LRC-1] Letra sincronizada (.lrc): se ve en lugar de la carátula, con la línea que suena resaltada
        fun showLyrics(on: Boolean) {
            showLyr = on
            val vis = on && lyrDoc != null
            lyrScroll.visibility = if (vis) View.VISIBLE else View.GONE
            art.visibility = if (vis) View.GONE else View.VISIBLE
            lyrBtn.alpha = if (lyrDoc == null) 0.45f else if (vis) 1f else 0.75f
            lyrIdx = -2
        }

        fun buildLyrics() {
            lyrBox.removeAllViews()
            lyrViews.clear()
            val d = lyrDoc ?: return
            for (ln in d.lines) {
                val tv = TextView(this).apply {
                    text = if (ln.text.isEmpty()) "♪" else ln.text
                    gravity = android.view.Gravity.CENTER
                    textSize = 19f
                    setPadding(dp(6), dp(7), dp(6), dp(7))
                    setTextColor(col(R.color.vlc_muted))
                }
                lyrBox.addView(tv, android.widget.LinearLayout.LayoutParams(lin, wrap))
                lyrViews.add(tv)
            }
            lyrScroll.scrollTo(0, 0)
        }

        fun loadLyrics(item: MediaItem?) {
            lyrDoc = if (item != null && item.isMusic) Lrc.load(this, item) else null
            buildLyrics()
            showLyrics(showLyr)
        }

        fun paintLyrics(posMs: Int) {
            val d = lyrDoc ?: return
            if (lyrScroll.height == 0) return   // todavía no se dibujó: se reintenta en la próxima vuelta
            val i = Lrc.indexAt(d, posMs + 250)
            if (i == lyrIdx) return
            lyrIdx = i
            for (k in lyrViews.indices) lyrViews[k].setTextColor(if (k == i) tintAcc else col(R.color.vlc_muted))
            if (i >= 0 && i < lyrViews.size) {
                val tv = lyrViews[i]
                lyrScroll.smoothScrollTo(0, maxOf(tv.top + tv.height / 2 - lyrScroll.height / 2, 0))
            }
        }

        fun pickLrc() {
            val item = playingItem ?: return
            lrcPending = item
            try {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), reqLrc)
            } catch (e: Exception) {
                lrcPending = null
                toast("No pude abrir el selector de archivos")
            }
        }

        fun askLrc() {
            AlertDialog.Builder(this)
                .setTitle("Letra de la canción")
                .setMessage("Elige el archivo .lrc (letra con tiempos) de esta canción. CASTV guarda una copia y la muestra aquí, siguiendo la música.")
                .setPositiveButton("Elegir archivo…") { _, _ -> pickLrc() }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        fun onLyrTap() {
            if (playingItem?.isMusic != true) return
            if (lyrDoc != null) showLyrics(!showLyr) else askLrc()
        }

        fun onLyrMenu() {
            val item = playingItem ?: return
            if (!item.isMusic) return
            val opts = ArrayList<String>()
            opts.add("Elegir otro archivo .lrc…")
            if (Lrc.hasSaved(this, item)) opts.add("Quitar la letra guardada")
            AlertDialog.Builder(this)
                .setTitle("Letra")
                .setItems(opts.toTypedArray()) { _, which ->
                    if (which == 0) pickLrc()
                    else { Lrc.remove(this, item); lrcRev++; toast("Letra quitada") }
                }
                .show()
        }

        lyrBtn.setOnClickListener { haptic(it); onLyrTap() }
        lyrBtn.setOnLongClickListener { onLyrMenu(); true }
        cutBtn.setOnClickListener { haptic(it); cutPlaying() }   // [CORTAR-1a]
        fun paintAb() {   // [AB-MUSICA-1]
            abBtn.text = when (abMode) { 0 -> "A-B"; 1 -> "A·"; else -> "A⇄B" }
            abBtn.setTextColor(if (abMode == 0) col(R.color.vlc_text) else accentColor())
            val it0 = playingItem
            abBtn.visibility = if (it0 != null && it0.isMusic && localActive) View.VISIBLE else View.GONE
        }
        abBtn.setOnClickListener { haptic(it); abTap(); paintAb() }
        art.setOnClickListener { if (lyrDoc != null) showLyrics(true) }
        lyrScroll.setOnClickListener { showLyrics(false) }

        // Refleja el panel cada medio segundo mientras la ventana está abierta
        var shownKey: Any? = null
        val sync = object : Runnable {
            override fun run() {
                if (!dlg.isShowing) return
                title.text = tvNowPlaying.text
                val bmp = (nowBigArt.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?.takeIf { nowBigArt.visibility == View.VISIBLE }
                val key: Any = bmp ?: (if (playingItem?.isMusic == true) "m" else "v")
                if (key != shownKey) {
                    shownKey = key
                    if (bmp != null) {
                        art.clearColorFilter()
                        art.setPadding(0, 0, 0, 0)
                        art.setImageBitmap(bmp)
                        applyArtTint(bmp)   // [UI-ART-1]
                    } else {
                        applyArtTint(null)   // [UI-ART-1]
                        art.setPadding(dp(64), dp(64), dp(64), dp(64))
                        art.setColorFilter(col(R.color.vlc_muted))
                        art.setImageResource(if (playingItem?.isMusic == true) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
                    }
                }
                // [LRC-1] letra: se carga al cambiar de canción (o al elegir/quitar el archivo) y se resalta la línea que suena
                val curLy = playingItem
                val lk = if (curLy != null && curLy.isMusic) Store.key(curLy) else null
                if (lk != lyrKey || lrcRev != lyrRev) {
                    lyrKey = lk
                    lyrRev = lrcRev
                    loadLyrics(curLy)
                    if (lyrDoc != null && lrcShowNext) { lrcShowNext = false; showLyrics(true) }
                }
                lyrBtn.visibility = if (lk != null) View.VISIBLE else View.GONE
                cutBtn.visibility = if (curLy != null) View.VISIBLE else View.GONE   // [CORTAR-1a]
                paintAb()   // [AB-MUSICA-1]
                if (lyrDoc != null && lyrScroll.visibility == View.VISIBLE) {
                    var posMs = seekBar.progress * 1000
                    withLocal { posMs = it.currentPosition }
                    paintLyrics(posMs)
                }
                if (!dragging) {
                    seek.isEnabled = seekBar.isEnabled
                    seek.max = seekBar.max
                    seek.progress = seekBar.progress
                    el.text = tvElapsed.text
                    du.text = tvDuration.text
                }
                btnPlay.drawable?.constantState?.newDrawable()?.let { play.setImageDrawable(it) }
                fav.text = btnNowFav.text
                fav.setTextColor(btnNowFav.currentTextColor)
                btnAuto.background?.constantState?.newDrawable()?.let { auto.background = it }
                muteBtn.setImageResource(if (muted) R.drawable.ic_vol_off else R.drawable.ic_vol_on)   // [REMOTO-1a]
                if (!volTouching) volFull.progress = volSeek.progress
                fullHandler.postDelayed(this, if (lyrScroll.visibility == View.VISIBLE) 250L else 500L)   // [LRC-1] con letra, más seguido
            }
        }

        dlg.setContentView(root)
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dlg.setOnDismissListener {
            fullHandler.removeCallbacks(sync)
            if (fullDialog === dlg) fullDialog = null
        }
        fullDialog = dlg
        dlg.show()
        sync.run()
    }

    /** [LRC-1] Guarda como letra de [item] el .lrc elegido (con marcas de tiempo); si no las trae, avisa y no guarda nada. */
    private fun importLrc(u: Uri, item: MediaItem) {
        val text = try { contentResolver.openInputStream(u)?.use { Lrc.readText(it) } } catch (e: Exception) { null }
        val doc = if (text != null) Lrc.parse(text) else null
        if (text == null || doc == null) {
            toast("No encontré tiempos tipo [01:23.45] en ese archivo (¿es un .lrc?)")
            return
        }
        if (Lrc.save(this, item, text)) {
            lrcRev++
            lrcShowNext = true
            toast("🎤 Letra guardada (${doc.lines.size} líneas)")
        } else {
            toast("No pude guardar la letra")
        }
    }

    /** [UI-ART-1] Color dominante de una carátula: promedio de una versión de 24×24 donde los colores vivos pesan más. Null si es gris o no se puede leer. */
    private fun dominantColor(src: android.graphics.Bitmap): Int? {
        return try {
            val s = android.graphics.Bitmap.createScaledBitmap(src, 24, 24, true)
            var r = 0.0; var g = 0.0; var b = 0.0; var wSum = 0.0
            val hsv = FloatArray(3)
            for (y in 0 until 24) for (x in 0 until 24) {
                val p = s.getPixel(x, y)
                android.graphics.Color.colorToHSV(p, hsv)
                val w = (hsv[1] * hsv[2]).toDouble()
                r += android.graphics.Color.red(p) * w
                g += android.graphics.Color.green(p) * w
                b += android.graphics.Color.blue(p) * w
                wSum += w
            }
            if (wSum < 24 * 24 * 0.05) null
            else android.graphics.Color.rgb((r / wSum).toInt(), (g / wSum).toInt(), (b / wSum).toInt())
        } catch (e: Exception) { null }
    }

    /** [UI-ART-1] Aclara (fondo oscuro) u oscurece (fondo claro) el color para que las barras se vean. */
    private fun readableAccent(dom: Int, bg: Int): Int {
        val bgDark = androidx.core.graphics.ColorUtils.calculateLuminance(bg) < 0.5
        var c = dom
        var n = 0
        while (n < 8) {
            val lum = androidx.core.graphics.ColorUtils.calculateLuminance(c)
            if (if (bgDark) lum >= 0.35 else lum <= 0.35) break
            c = androidx.core.graphics.ColorUtils.blendARGB(c, if (bgDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.15f)
            n++
        }
        return c
    }

    private fun updateNowFav() {
        val item = playingItem
        val chUrl = if (item == null && linkOnTv) linkUrlNow else null   // [FAV-REPRO-3] canal en el TV
        val fav = if (item != null) Store.isFav(item) else if (chUrl != null) Store.chFavs().contains(chUrl) else false
        tvFavNow = chUrl != null && fav
        btnNowFav.text = if (fav) "★" else "☆"
        btnNowFav.setTextColor(if (fav) 0xFFFFC107.toInt() else col(R.color.vlc_muted))
    }

    /** [FAV-REPRO-3] El canal que está en el TV (null si no hay, o si suena algo propio). Con logo y grupo si ya estaba en «Continuar». */
    private fun tvChannelNow(): M3uChannel? {
        if (!linkOnTv || playingItem != null) return null
        val u = linkUrlNow ?: return null
        return Store.recentChannels().firstOrNull { it.url == u } ?: M3uChannel(linkTitleNow ?: "Canal", u, "")
    }

    /** [FAV-REPRO-3] Marca o quita la ★ del canal del TV (mini reproductor, pantalla «Reproduciendo» y notificación). */
    private fun toggleTvChannelFav(c: M3uChannel) {
        val on = Store.toggleChFav(c.url)
        if (on) Store.rememberChFav(c) else Store.forgetChFav(c.url)
        updateNowFav()
        lastNotifKey = ""
        refreshNotification()
        toast(if (on) "★ En Favoritos: " + c.name else "☆ Quitado de Favoritos")
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

    private var lastToast: Toast? = null   // [AVISO-COLA-1] el último aviso, para quitarlo al salir de esta pantalla

    // [AVISO-MINI-1] Aviso propio: una pastilla oscura con borde del color de acento que sale SIEMPRE justo encima del
    // mini reproductor (el Toast de Android se montaba sobre él). Se quita sola, o al tocarla.
    private var noteView: TextView? = null
    private val noteHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val noteHide = Runnable { hideNote() }

    private fun toast(msg: String) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { toast(msg) }
            return
        }
        val host: ViewGroup? = findViewById(android.R.id.content)
        val mini: View? = if (::nowThumbBox.isInitialized && ::navMore.isInitialized) (nowThumbBox.parent?.parent as? View) else null
        if (host == null || mini == null || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            // sin pantalla lista (arranque) o con la app detrás de otra: el aviso normal de Android, como antes
            val t = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
            lastToast = t
            t.show()
            return
        }
        showNote(msg, host, mini)
    }

    private fun showNote(msg: String, host: ViewGroup, mini: View) {
        hideNote(false)
        val d = resources.displayMetrics.density
        val tv = TextView(this)
        tv.text = msg
        tv.setTextColor(0xFFF2F5F9.toInt())
        tv.textSize = 14f
        tv.maxLines = 3
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        tv.gravity = android.view.Gravity.CENTER
        tv.setPadding((18 * d).toInt(), (11 * d).toInt(), (18 * d).toInt(), (11 * d).toInt())
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(0xF2151A21.toInt())
        bg.cornerRadius = 24 * d
        bg.setStroke(maxOf(1, (1.2f * d).toInt()), accentColor())
        tv.background = bg
        tv.elevation = 12 * d
        tv.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        tv.setOnClickListener { hideNote() }
        if (host.width > 0) tv.maxWidth = host.width - (32 * d).toInt()
        // justo encima del mini reproductor (o de la barra de abajo si el mini no se ve)
        val ref: View = if (mini.isShown && mini.height > 0) mini else navMore
        val hp = IntArray(2)
        val rp = IntArray(2)
        host.getLocationOnScreen(hp)
        ref.getLocationOnScreen(rp)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = if (host.height > 0) maxOf(0, host.height - (rp[1] - hp[1])) + (10 * d).toInt() else (150 * d).toInt()
        tv.alpha = 0f
        tv.translationY = 12 * d
        host.addView(tv, lp)
        tv.animate().alpha(1f).translationY(0f).setDuration(180).start()
        noteView = tv
        noteHandler.postDelayed(noteHide, (1800L + msg.length * 45L).coerceIn(2200L, 5000L))
    }

    private fun hideNote(animate: Boolean = true) {
        noteHandler.removeCallbacks(noteHide)
        val v = noteView ?: return
        noteView = null
        if (!animate) {
            (v.parent as? ViewGroup)?.removeView(v)
            return
        }
        v.animate().alpha(0f).translationY(8 * resources.displayMetrics.density).setDuration(160)
            .withEndAction { (v.parent as? ViewGroup)?.removeView(v) }.start()
    }

    /** [AVISO-COLA-1] Un aviso (ej. «La cola está vacía…») no se queda encima de otra pantalla (Ajustes, reproductor de video…). */
    override fun onPause() {
        lastToast?.cancel()
        lastToast = null
        hideNote(false)   // [AVISO-MINI-1]
        super.onPause()
    }

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
            val bldRs = AlertDialog.Builder(this)   // [ICONOS-10]
            val icRs = bldRs.context
            bldRs
                .setTitle(prettyTitle(item))
                .setItems(arrayOf<CharSequence>(
                    CastvIcons.item(icRs, R.drawable.ic_vp_play, "Continuar desde ${fmt(saved)}"),
                    CastvIcons.item(icRs, R.drawable.ic_mn_replay, "Desde el inicio"))) { _, which ->
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
    private val reqLrc = 4719         // [LRC-1] elegir el archivo .lrc de la canción
    private var lrcPending: MediaItem? = null   // [LRC-1] canción a la que va el archivo que se está eligiendo
    private var lrcRev = 0                      // [LRC-1] sube al guardar o quitar una letra: «Reproduciendo» la vuelve a leer
    private var lrcShowNext = false             // [LRC-1] al guardar una letra, mostrarla enseguida
    private var accentAtStart = 0     // [UI-1b] color con el que se dibujó esta pantalla
    private var bgAtStart = 0         // [TEMAS-2] fondo con el que se dibujó esta pantalla
    private var textAtStart = 1       // [TEXTO-TAM-1] tamaño de texto con el que se dibujó esta pantalla
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
                menu.add(0, 2, 1, "🗑 Eliminar").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                // [SEL-1] acciones en bloque (van al menú ⋮ de la barra)
                menu.add(0, 6, 2, "▶ Reproducir selección").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)   // [SEL-2]
                menu.add(0, 3, 2, "➕ Añadir a la cola").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 4, 3, "★ Añadir a favoritos").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 5, 4, "📃 Añadir a lista…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 7, 5, "📤 Compartir").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)   // [SEL-2]
                menu.add(0, 8, 6, "👁 Marcar como visto").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)   // [VISTO-1]
                menu.add(0, 9, 7, "🎵 Convertir a MP3").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)   // [MP3-LOTE-1]
                menu.add(0, 10, 8, "🔗 Unir").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)   // [UNIR-1]
                mode.title = "1 seleccionado"
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                // [SEL-1] ★ si falta alguno por marcar; ☆ solo cuando todos ya son favoritos
                val sel = selectedItems()
                val allFav = sel.isNotEmpty() && sel.all { Store.isFav(it) }
                menu.findItem(4)?.title = if (allFav) "☆ Quitar de favoritos" else "★ Añadir a favoritos"
                val vids = sel.filter { !it.isMusic }   // [VISTO-1]
                menu.findItem(8)?.title = if (vids.isNotEmpty() && vids.all { Store.isSeen(it) }) "👁 Marcar como no visto" else "👁 Marcar como visto"
                menu.findItem(9)?.isVisible = vids.isNotEmpty()   // [MP3-LOTE-1] solo si hay videos en la selección
                menu.findItem(10)?.isVisible = sel.size >= 2 && (sel.all { !it.isMusic } || sel.all { it.isMusic })   // [UNIR-1] 2 o más videos, o 2 o más audios
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
                    6 -> bulkPlay()      // [SEL-2]
                    7 -> bulkShare()     // [SEL-2]
                    8 -> bulkSeen()      // [VISTO-1]
                    9 -> bulkMp3()       // [MP3-LOTE-1]
                    10 -> bulkJoin()     // [UNIR-1]
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

    // ------------------------------------------- [GAPLESS-1] Sin pausa y fundido entre canciones (música del teléfono)

    private val nextTrack = PlayerCore.NextTrack()
    private var gapXfOld: MediaPlayer? = null   // el reproductor que baja durante un fundido; startLocal lo recoge
    private var gapRunning = false
    private val gapHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gapTick = object : Runnable {
        override fun run() {
            if (!gapRunning) return
            try { gapStep() } catch (e: Exception) { }
            gapHandler.postDelayed(this, 300)
        }
    }

    /** Se vigila la pista siguiente solo si «Sin pausa» o el fundido están encendidos (los dos apagados por defecto). */
    private fun gapStart() {
        gapHandler.removeCallbacks(gapTick)
        if (!PlayerCore.gaplessOn(this) && PlayerCore.xfadeSec(this) == 0) { gapRunning = false; return }
        gapRunning = true
        gapHandler.postDelayed(gapTick, 300)
    }

    private fun gapStop() {
        gapRunning = false
        gapHandler.removeCallbacks(gapTick)
    }

    /** Al cerrar la pantalla con música sonando sin pantalla: PlayerCore lleva lo suyo, aquí no queda nada. */
    private fun gapAbort() {
        gapStop()
        nextTrack.discard(localPlayer)
        PlayerCore.cancelXfade()
    }

    /** Qué sonaría después, igual que step(+1) (cola primero, luego la lista si se repite todo). Null si no hay o si hay que parar. */
    private fun gapPeek(): MediaItem? {
        val cur = playingItem ?: return null
        if (!cur.isMusic || sleepAfterTrack || repeatMode == Repeat.ONE) return null
        if (queue.isNotEmpty()) return queue[0]
        if (repeatMode == Repeat.ALL && playlist.isNotEmpty()) {
            val i = if (playIndex < 0) 0 else Math.floorMod(playIndex + 1, playlist.size)
            val n = playlist[i]
            return if (n.isMusic) n else null
        }
        return null
    }

    private fun gapStep() {
        val mp = localPlayer
        val cur = playingItem
        if (!localActive || !localPrepared || mp == null || cur == null || !cur.isMusic) return
        nextTrack.tick(applicationContext, mp, gapPeek()) { gapCrossfade() }
    }

    /** Faltan N segundos: se pasa a la siguiente con las reglas de siempre (step); startLocal arranca la ya preparada con volumen 0 y empieza el fundido. */
    private fun gapCrossfade() {
        val old = localPlayer ?: return
        playingItem?.let { Store.clearResume(it) }
        gapXfOld = old
        try { step(+1) } catch (e: Exception) { }
        gapXfOld = null
    }

    /** Al terminar una pista: si lo preparado ya no es lo que toca (cambió la cola, el modo repetir, el temporizador…), se suelta. */
    private fun gapSettle() {
        if (nextTrack.player == null) return
        val pk = gapPeek()
        if (pk == null || nextTrack.item?.uriString != pk.uriString) nextTrack.discard(null)
    }

    // ------------------------------------------- [AB-MUSICA-1] Repetir un tramo (A-B) de la música del teléfono

    private var abMode = 0      // 0 apagado · 1 marcado A · 2 repitiendo de A a B
    private var abA = 0         // ms
    private var abB = 0         // ms
    private val abHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val abTick = object : Runnable {
        override fun run() {
            if (abMode != 2) return
            val mp = localPlayer
            if (!localActive || mp == null) { abReset(); return }
            try { if (localPrepared && mp.currentPosition >= abB) seekMs(mp, abA) } catch (e: Exception) { }
            abHandler.postDelayed(this, 80)
        }
    }

    private fun seekMs(mp: MediaPlayer, ms: Int) {
        if (Build.VERSION.SDK_INT >= 26) mp.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) else mp.seekTo(ms)
    }

    private fun abReset() {
        abMode = 0
        abHandler.removeCallbacks(abTick)
    }

    /** Un toque en «A-B»: 1.º marca A (donde va ahora), 2.º marca B y repite el tramo, 3.º lo apaga. */
    private fun abTap() {
        val item = playingItem
        if (item == null || !item.isMusic || !localActive || !localPrepared) {
            toast("A-B solo funciona con música en el teléfono")
            return
        }
        var pos = 0
        withLocal { pos = it.currentPosition }
        when (abMode) {
            0 -> {
                abA = pos
                abMode = 1
                toast("A marcado en ${fmt(pos / 1000)}. Toca A-B otra vez para marcar B")
            }
            1 -> {
                if (pos < abA + 1000) {
                    toast("B debe ir al menos 1 s después de A")
                    return
                }
                abB = pos
                abMode = 2
                withLocal { seekMs(it, abA) }
                abHandler.removeCallbacks(abTick)
                abHandler.post(abTick)
                toast("🔁 Repitiendo de ${fmt(abA / 1000)} a ${fmt(abB / 1000)}")
            }
            else -> {
                abReset()
                toast("A-B apagado")
            }
        }
    }

    // ------------------------------------------- [TALLER-1] Taller: todo lo que crea o altera archivos, en un solo lugar

    private var workTool: Int? = null            // herramienta elegida: el próximo archivo que se toque la usa (1 cortar, 2 dividir, 4 reducir, 5 quitar audio, 6 MP3, 7 tono)
    private var workProjects: List<Workshop.Project> = emptyList()
    private var workScanning = false

    // [TALLER-FILTRO-1] 0 = Todos; solo cambia lo que se ve
    private var workFilter = 0
    private val workFilterNames = arrayOf("Todos", "Cortes", "Partes", "Unidos", "MP3", "Descargas", "Otros")

    // [TALLER-ORDENAR-1] orden de las tarjetas; se recuerda en `castv_work`
    private val workSortNames = arrayOf("Recientes", "Antiguos", "Más pesados", "Nombre")

    private fun workSortMode(): Int =
        getSharedPreferences("castv_work", MODE_PRIVATE).getInt("sort", 0).coerceIn(0, workSortNames.size - 1)

    private fun workSorted(): List<Workshop.Project> = when (workSortMode()) {
        1 -> workProjects.sortedBy { workMs(it.newest) }
        2 -> workProjects.sortedByDescending { it.bytes }
        3 -> workProjects.sortedWith { a, b -> naturalCompare(norm(a.base), norm(b.base)) }
        else -> workProjects.sortedByDescending { workMs(it.newest) }
    }

    private fun workSortPick() {
        AlertDialog.Builder(this)
            .setTitle("↕ Ordenar proyectos")
            .setSingleChoiceItems(arrayOf<CharSequence>(*workSortNames), workSortMode()) { dialog, which ->
                getSharedPreferences("castv_work", MODE_PRIVATE).edit().putInt("sort", which).apply()
                dialog.dismiss()
                if (viewMode == ViewMode.WORKSHOP) { showWorkshop(); listFiles.setSelection(0) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun workCategory(p: Workshop.Project): Int = when (p.tool) {
        "✂️ Cortado" -> 1
        "📦 Dividido" -> 2
        "🔗 Unido" -> 3
        "🎵 MP3" -> 4
        "⬇ Descargado" -> 5
        else -> 6   // reducidos, sin audio y cualquier otro
    }

    private fun updateWorkChips() {
        val scroll = findViewById<View>(R.id.workChipsScroll)
        val show = viewMode == ViewMode.WORKSHOP && workProjects.isNotEmpty()
        scroll.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val box = findViewById<android.widget.LinearLayout>(R.id.workChips)
        val d = resources.displayMetrics.density
        if (box.childCount == 0) {
            // la posición 0 es «Ordenar»; los filtros van de la 1 en adelante
            for (i in -1 until workFilterNames.size) {
                val chip = TextView(this).apply {
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    minHeight = (36 * d).toInt()
                    setOnClickListener {
                        if (i < 0) workSortPick() else { workFilter = i; showWorkshop(); listFiles.setSelection(0) }
                    }
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (8 * d).toInt()
                box.addView(chip, lp)
            }
        }
        val sortChip = box.getChildAt(0) as TextView
        sortChip.text = "↕ " + workSortNames[workSortMode()]
        sortChip.setBackgroundResource(R.drawable.bg_chip)
        sortChip.setTextColor(accentColor())
        sortChip.setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
        for (i in workFilterNames.indices) {
            val chip = box.getChildAt(i + 1) as TextView
            val n = if (i == 0) workProjects.size else workProjects.count { workCategory(it) == i }
            val on = i == workFilter
            chip.text = workFilterNames[i] + " (" + n + ")"
            chip.isSelected = on
            if (on) {
                chip.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 18 * d
                    setColor(accentColor())
                }
                chip.setTextColor(android.graphics.Color.BLACK)
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip)
                chip.setTextColor(col(R.color.vlc_muted))
            }
            chip.setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)   // después del fondo, que puede cambiar el relleno
        }
    }
    private var workLastScan = 0L
    private var workGen = 0

    private fun workToolLabel(t: Int): String = when (t) {
        1 -> "✂️ Cortar"
        2 -> "📦 Dividir"
        3 -> "🔗 Unir"
        4 -> "📉 Reducir peso"
        5 -> "🔇 Quitar audio"
        6 -> "🎵 Convertir a MP3"
        7 -> "🔔 Tono"
        else -> "Taller"
    }

    private fun workWhen(added: Long): String {
        val ms = if (added < 100_000_000_000L) added * 1000L else added
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            ms, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS).toString()
    }

    /** Pestaña Taller: «＋ Nuevo proyecto» y, debajo, lo que CASTV generó (se reconoce por el nombre; se busca en Videos y Audio). */
    private fun showWorkshop() {
        if (!groupsShown) {
            groupsShown = true
            listFiles.adapter = groupAdapter
        }
        groupAdapter.grid = false
        val q = norm(query).trim()
        val rows = ArrayList<GroupRow>()
        rows.add(GroupRow(4, "＋ Nuevo proyecto", if (workProjects.isEmpty()) "Cortar, dividir, unir, reducir peso, quitar audio, MP3 y tonos"
            else countLabel(workProjects.size, "proyecto", "proyectos") + " · ocupan " + android.text.format.Formatter.formatShortFileSize(this, workProjects.sumOf { it.bytes }), null, ""))   // [TALLER-2]
        val nOld = workOld().size   // [TALLER-LIMPIA-1]
        if (nOld > 0 && q.isEmpty()) rows.add(GroupRow(7, "🧹 Limpiar antiguos",
            countLabel(nOld, "proyecto", "proyectos") + " de más de 30 días · tú eliges qué borrar", null, ""))
        if (workProjects.isNotEmpty() && q.isEmpty()) rows.add(GroupRow(8, "📤 Compartir varios",
            "Marca varios proyectos y envíalos juntos", null, ""))   // [TALLER-COMPARTIR-1]
        for (p in workSorted()) {   // [TALLER-ORDENAR-1]
            if (workFilter != 0 && workCategory(p) != workFilter) continue   // [TALLER-FILTRO-1]
            val bits = ArrayList<String>()
            bits.add(p.tool)
            if (p.parts.size > 1) {
                bits.add(if (p.parts.size == p.total) p.parts.size.toString() + " partes" else p.parts.size.toString() + " de " + p.total + " partes")
            } else if (p.parts[0].durationSec > 0) {
                bits.add(fmt(p.parts[0].durationSec))
            }
            if (p.bytes > 0) bits.add(android.text.format.Formatter.formatShortFileSize(this, p.bytes))   // [TALLER-2]
            bits.add(workWhen(p.newest))
            val sub = bits.joinToString("  ·  ")
            if (q.isNotEmpty() && !norm(p.base + " " + sub).contains(q)) continue
            rows.add(GroupRow(5, p.base, sub, p.parts.firstOrNull(), p.key))
        }
        val nHidden = Workshop.hidden(this).size
        if (nHidden > 0 && q.isEmpty()) rows.add(GroupRow(6, "Mostrar los quitados ($nHidden)", "Vuelven a la lista; los archivos nunca se borraron", null, ""))
        groupAdapter.rows = rows
        groupAdapter.notifyDataSetChanged()
        tvEmptyAction.visibility = View.GONE
        if (workProjects.isEmpty()) {
            tvEmpty.text = if (workScanning || workLastScan == 0L) "Buscando tus proyectos…"
                else "Aún no hay proyectos.\nLo que cortes, unas, reduzcas o dividas con CASTV aparecerá aquí."
            tvEmpty.visibility = View.VISIBLE
        } else if (workFilter != 0 && workProjects.none { workCategory(it) == workFilter }) {   // [TALLER-FILTRO-1]
            tvEmpty.text = "No hay proyectos en «" + workFilterNames[workFilter] + "»."
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
        if (workProjects.isEmpty()) {   // [PIE-1] sin proyectos manda el mensaje; el pie de redes queda en Acerca de
            if (footerAttached) {
                listFiles.removeFooterView(footerView)
                footerAttached = false
            }
        } else if (!footerAttached) {
            listFiles.addFooterView(footerView, null, false)
            footerAttached = true
        }
        setFabShown(false)
        updateTabs()
        updateFilterChip()
        // se vuelve a buscar al entrar (no a cada letra del buscador)
        val now = System.currentTimeMillis()
        if (workScanning || now - workLastScan < 5000L) return
        workScanning = true
        workLastScan = now
        val gen = ++workGen
        thread {
            val found = try {
                val all = ArrayList<MediaItem>()
                all.addAll(MediaScanner.scanVideos(this))
                all.addAll(MediaScanner.scanMusic(this))
                val det = Workshop.detect(all, Workshop.hidden(this), Workshop.records(this))
                for (p in det) p.bytes = Workshop.sizeOf(this, p)   // [TALLER-2]
                det
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                workScanning = false
                if (gen != workGen || isFinishing) return@runOnUiThread
                workProjects = found
                if (viewMode == ViewMode.WORKSHOP) showWorkshop()
            }
        }
    }

    // ------------------------------------------- [TALLER-LIMPIA-1] limpiar proyectos antiguos

    /** Segundos o milisegundos → milisegundos. */
    private fun workMs(t: Long): Long = if (t < 100_000_000_000L) t * 1000L else t

    /** Proyectos de más de 30 días: cuentan la fecha del proyecto y la de TODOS sus archivos; sin fecha no cuenta. */
    private fun workOld(): List<Workshop.Project> {
        val limit = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return workProjects.filter { p ->
            p.newest > 0 && workMs(p.newest) < limit &&
                p.parts.all { it.dateAdded > 0 && workMs(it.dateAdded) < limit }
        }
    }

    /** Lista sin nada marcado; el borrado pasa por `confirmDelete` (PIN, papelera y confirmación de siempre). */
    private fun workCleanPick() {
        val old = workOld().sortedBy { workMs(it.newest) }   // lo más antiguo primero
        if (old.isEmpty()) { toast("No hay proyectos de más de 30 días"); return }
        val chosen = LinkedHashSet<String>()
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        val side = (16 * resources.displayMetrics.density).toInt()
        val summary = TextView(ctx).apply { text = "Nada marcado"; textSize = 13f }
        val rows = WorkCleanAdapter(old, chosen)
        val list = ListView(ctx).apply { adapter = rows }
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(side, 0, side, 0)
            addView(summary)
            addView(list, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5f).toInt(), 1f))
        }
        val dlg = builder
            .setTitle("🧹 Limpiar antiguos")
            .setView(box)
            .setPositiveButton("Borrar…", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        val ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        fun picked() = old.filter { it.key in chosen }
        list.setOnItemClickListener { _, _, pos, _ ->
            val key = old[pos].key
            if (!chosen.remove(key)) chosen.add(key)
            rows.notifyDataSetChanged()
            val sel = picked()
            ok.isEnabled = sel.isNotEmpty()
            summary.text = if (sel.isEmpty()) "Nada marcado" else
                "Marcado: " + countLabel(sel.size, "proyecto", "proyectos") + " · " +
                countLabel(sel.sumOf { it.parts.size }, "archivo", "archivos") + " · " +
                android.text.format.Formatter.formatShortFileSize(this, sel.sumOf { it.bytes })
        }
        ok.setOnClickListener {
            val files = picked().flatMap { it.parts }
            if (files.isNotEmpty()) {
                dlg.dismiss()
                confirmDelete(files)
            }
        }
    }

    private inner class WorkCleanAdapter(private val items: List<Workshop.Project>, private val chosen: Set<String>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = parent.context
            fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
            val p = items[position]
            val first = p.parts[0]
            val row = (convertView as? android.widget.LinearLayout) ?: android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                val pic = FrameLayout(ctx).apply {
                    setBackgroundResource(R.drawable.bg_thumb)
                    clipToOutline = true
                    addView(ImageView(ctx), FrameLayout.LayoutParams(dp(28), dp(28), android.view.Gravity.CENTER))
                    addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP },
                        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
                addView(pic, android.widget.LinearLayout.LayoutParams(dp(80), dp(56)))
                addView(TextView(ctx).apply { textSize = 13f; setPadding(dp(10), 0, dp(8), 0) },
                    android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(android.widget.CheckBox(ctx).apply {
                    isClickable = false
                    isFocusable = false
                })
            }
            val pic = row.getChildAt(0) as FrameLayout
            (pic.getChildAt(0) as ImageView).setImageResource(if (first.isMusic) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
            bindThumb(first, pic.getChildAt(1) as ImageView)
            // nombre real del archivo y carpeta, para saber exactamente qué se borra
            val name = if (p.parts.size > 1) p.base + " (" + p.parts.size + " archivos)" else first.name
            var info = p.tool + "  ·  " + shortDate(workMs(p.newest) / 1000)
            if (p.bytes > 0) info += "  ·  " + android.text.format.Formatter.formatShortFileSize(this@MainActivity, p.bytes)
            val folders = p.parts.map { folderLabel(it.folder) }.distinct().joinToString(", ")   // [CARPETA-RUTA-1]
            (row.getChildAt(1) as TextView).text = name + "\n" + info + "\n📁 " + folders
            (row.getChildAt(2) as android.widget.CheckBox).isChecked = p.key in chosen
            return row
        }
    }

    /** «＋ Nuevo proyecto»: elegir la herramienta. */
    private fun workNew() {
        AlertDialog.Builder(this)
            .setTitle("＋ Nuevo proyecto")
            .setItems(arrayOf<CharSequence>(
                "✂️ Cortar (audio o video)",
                "📦 Dividir un video",   // [VENT-1]
                "🔗 Unir (2 o más videos o audios)",
                "📉 Reducir el peso de un video",
                "🔇 Quitar el audio de un video",
                "🎵 Convertir un video a MP3",
                "🔔 Tono / notificación / alarma",   // [VENT-1]
                "🌐 Enlace → MP3 / descargar")) { _, which ->
                if (which == 7) askLinkToMp3() else workStart(which + 1)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Lleva a la biblioteca para elegir el archivo (un toque lo usa); «Unir» pide marcar varios con pulsación larga. */
    private fun workStart(tool: Int) {
        if (tool == 3) { workJoinPick(); return }   // [TALLER-2] Unir con lista para marcar
        workTool = tool
        groupReturn = null
        resetFilters()
        viewMode = ViewMode.ALL
        val needVideo = tool == 2 || tool == 4 || tool == 5 || tool == 6
        when {
            needVideo && showingMusic -> showMovies()
            tool == 7 && !showingMusic -> showMusic()
            else -> applyFilters()
        }
        toast(workToolLabel(tool) + ": toca el archivo (✕ arriba para cancelar)")
    }

    /** Se tocó un archivo con una herramienta elegida en el Taller. */
    private fun workPicked(item: MediaItem) {
        val tool = workTool ?: return
        if (item.isMusic && (tool == 2 || tool == 4 || tool == 5 || tool == 6)) {
            toast("Esta herramienta es para videos")
            return
        }
        workTool = null
        updateFilterChip()
        val uri = uriOf(item)
        val durMs = item.durationSec * 1000L
        val pos: () -> Long = { 0L }
        when (tool) {
            1 -> {
                if (item.isMusic) {
                    CutAudio.pauseHook = { pauseAll() }
                    CutAudio.show(this, uri, item.name, durMs, false, pos)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("✂️ Cortar")
                        .setItems(arrayOf<CharSequence>("🎵 Solo el audio → MP3", "🎬 El video → MP4 (sin recodificar)")) { _, which ->
                            CutAudio.pauseHook = { pauseAll() }
                            CutAudio.show(this, uri, item.name, durMs, which == 1, pos)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            2 -> VideoSplit.show(this, uri, item.name)
            4 -> VideoReduce.show(this, uri, item.name)
            5 -> VideoMute.show(this, uri, item.name)
            6 -> bulkMp3(listOf(item))
            7 -> {
                toast("En «Guardar en:» elige Tonos de llamada, Notificaciones o Alarmas")
                CutAudio.pauseHook = { pauseAll() }
                CutAudio.show(this, uri, item.name, durMs, false, pos)
            }
        }
    }

    /** Tarjeta de un proyecto: Reproducir · Compartir · Ubicación · Quitar de la lista (no borra el archivo). */
    private fun workProjectMenu(key: String) {
        val p = workProjects.firstOrNull { it.key == key } ?: return
        val many = p.parts.size > 1
        AlertDialog.Builder(this)
            .setTitle(p.base)
            .setItems(arrayOf<CharSequence>(
                if (many) "▶ Reproducir las partes en orden" else "▶ Reproducir",
                if (many) "📤 Compartir todas" else "📤 Compartir",
                "📂 Ubicación",
                "🔁 Usar con otra herramienta…",
                "✏️ Cambiar el nombre…",   // [RENOMBRAR-1]
                "🗑 Quitar de la lista",
                if (many) "❌ Eliminar los archivos…" else "❌ Eliminar el archivo…")) { _, which ->
                when (which) {
                    0 -> {
                        playlist = ArrayList(p.parts)
                        playIndex = 0
                        playingFromQueue = false
                        startPlayback(p.parts[0])
                    }
                    1 -> workShare(p)
                    2 -> workFolder(p)
                    3 -> workReuse(p)   // [TALLER-2]
                    4 -> workRename(p)   // [RENOMBRAR-1]
                    6 -> {   // [TALLER-2] usa la misma confirmación, papelera y PIN que Eliminar de siempre
                        workLastScan = 0L
                        confirmDelete(p.parts)
                    }
                    else -> {
                        Workshop.hide(this, p.key)
                        workProjects = workProjects.filter { it.key != p.key }
                        showWorkshop()
                        toast("Quitado de la lista (el archivo sigue en el teléfono)")
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** [TALLER-2] Unir desde el Taller: lista de la biblioteca actual (videos o audios) para marcar 2 o más; se unen en el orden en que se marcan. */
    private fun workJoinPick() {
        val all = allItems.sortedByDescending { it.dateAdded }
        if (all.size < 2) { toast("No hay suficientes archivos para unir"); return }
        val recent = all.take(300)
        val chosen = ArrayList<MediaItem>()
        var shown: List<MediaItem> = recent
        // [BUSCA-UNIR-1] textos de búsqueda de toda la biblioteca (se preparan al escribir por 1.ª vez)
        val keys by lazy { all.map { norm(prettyTitle(it) + " " + it.name) } }
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        val search = EditText(ctx).apply {
            hint = "🔎 Buscar · mantén pulsado para ver"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val none = TextView(ctx).apply {
            text = "Sin resultados"
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }
        val rows = JoinPickAdapter(chosen)   // [UNIR-MINI-1]
        val list = ListView(ctx).apply { adapter = rows }
        val fill = {
            rows.items = shown
            rows.notifyDataSetChanged()
            none.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        }
        fill()
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val side = (16 * resources.displayMetrics.density).toInt()
            setPadding(side, 0, side, 0)
            addView(search)
            addView(none)
            addView(list, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.45f).toInt(), 1f))
        }
        val dlg = builder
            .setTitle(if (showingMusic) "🔗 Unir audios (.m4a/AAC)" else "🔗 Unir videos")
            .setView(box)
            .setPositiveButton("Siguiente", null)
            .setNegativeButton("Cancelar", null)
            .create()
        list.setOnItemClickListener { _, _, pos, _ ->
            val m = shown[pos]
            if (!chosen.remove(m)) chosen.add(m)
            rows.notifyDataSetChanged()   // [UNIR-MINI-1] renumera 1, 2, 3…
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).text =
                if (chosen.isEmpty()) "Siguiente" else "Siguiente (${chosen.size})"
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->   // [UNIR-VER-1] no cambia las marcas
            workPreview(shown[pos])
            true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = norm(s?.toString()?.trim() ?: "")
                shown = if (q.isEmpty()) recent else all.filterIndexed { i, _ -> keys[i].contains(q) }
                fill()
            }
        })
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (chosen.size < 2) { toast("Marca 2 o más, en el orden en que quieres unirlos"); return@setOnClickListener }
            if (chosen.size > VideoJoin.MAX_FILES) { toast("Máximo ${VideoJoin.MAX_FILES} archivos a la vez"); return@setOnClickListener }
            if (Mp3Converter.running.get() || LinkDownload.running.get()) { toast("Ya hay una conversión o descarga en curso"); return@setOnClickListener }
            // [UNIR-ORDEN-1] la lista de marcar sigue abierta detrás: «Volver» regresa sin perder las marcas
            workJoinOrder(chosen, onClose = { rows.notifyDataSetChanged() }) {
                if (Mp3Converter.running.get() || LinkDownload.running.get()) {
                    toast("Ya hay una conversión o descarga en curso")
                } else {
                    val vid = !showingMusic
                    askJoinName(chosen[0], vid) { nm ->   // [UNIR-NOMBRE-1] si se cancela, la lista de marcar sigue abierta
                        dlg.dismiss()
                        runJoin(ArrayList(chosen), vid, nm)
                    }
                }
            }
        }
    }

    /** [UNIR-ORDEN-1] Revisar y cambiar el orden con ↑ ↓ antes de unir. Cambia directamente la lista `order`. */
    private fun workJoinOrder(order: ArrayList<MediaItem>, onClose: () -> Unit, onJoin: () -> Unit) {
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val wrap = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        val rows = object : BaseAdapter() {
            override fun getCount() = order.size
            override fun getItem(position: Int) = order[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val adapterRef = this
                val item = order[position]
                // filas nuevas cada vez: son 20 como mucho y así los botones nunca apuntan a otra posición
                val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                val pic = FrameLayout(ctx).apply {
                    setBackgroundResource(R.drawable.bg_thumb)
                    clipToOutline = true
                    addView(ImageView(ctx).apply {
                        setImageResource(if (item.isMusic) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
                    }, FrameLayout.LayoutParams(dp(24), dp(24), android.view.Gravity.CENTER))
                    addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
                bindThumb(item, thumb)
                val name = TextView(ctx).apply {
                    textSize = 13f
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(8), 0, dp(4), 0)
                    text = "${position + 1}. " + prettyTitle(item) +
                        (if (item.durationSec > 0) "  ·  " + fmt(item.durationSec) else "")
                }
                fun arrow(label: String, to: Int, desc: String) = TextView(ctx).apply {
                    text = label
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    contentDescription = desc
                    setTextColor(accentColor())
                    val can = to >= 0 && to < order.size
                    isEnabled = can
                    alpha = if (can) 1f else 0.25f
                    setOnClickListener {
                        java.util.Collections.swap(order, position, to)
                        adapterRef.notifyDataSetChanged()
                    }
                }
                return android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                    addView(pic, android.widget.LinearLayout.LayoutParams(dp(if (item.isMusic) 48 else 72), dp(48)))
                    addView(name, android.widget.LinearLayout.LayoutParams(0, wrap, 1f))
                    addView(arrow("↑", position - 1, "Subir"), android.widget.LinearLayout.LayoutParams(dp(44), dp(48)))
                    addView(arrow("↓", position + 1, "Bajar"), android.widget.LinearLayout.LayoutParams(dp(44), dp(48)))
                }
            }
        }
        val note = TextView(ctx).apply {
            text = "Se unirán de arriba abajo. Sin recodificar: solo funciona si todos son iguales (mismo códec, tamaño y giro)."
            textSize = 13f
        }
        val list = ListView(ctx).apply { adapter = rows }
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(note)
            addView(list, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5f).toInt(), 1f))
        }
        val d = builder
            .setTitle("🔗 Unir " + order.size + (if (showingMusic) " audios" else " videos"))
            .setView(box)
            .setPositiveButton("Unir") { _, _ -> onJoin() }
            .setNegativeButton("Volver", null)
            .create()
        d.setOnDismissListener { onClose() }
        d.show()
    }

    /** [UNIR-VER-1] Ver u oír un archivo sin salir de la lista de Unir (reproductor propio; se detiene al cerrar). */
    private fun workPreview(item: MediaItem) {
        if (localActive) pauseAll()   // solo lo que suena en el teléfono; el TV no se toca
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val match = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        val video = android.widget.VideoView(ctx)
        val stage = FrameLayout(ctx).apply {
            addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER))
        }
        val time = TextView(ctx).apply { text = "Cargando…"; gravity = android.view.Gravity.CENTER }
        val bar = SeekBar(ctx).apply { isEnabled = false }
        val play = Button(ctx).apply { text = "⏸ Pausa"; isEnabled = false }
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(stage, android.widget.LinearLayout.LayoutParams(match, dp(if (item.isMusic) 64 else 260)))
            addView(bar, android.widget.LinearLayout.LayoutParams(match, wrap))
            addView(time, android.widget.LinearLayout.LayoutParams(match, wrap))
            addView(play, android.widget.LinearLayout.LayoutParams(match, wrap))
        }
        val d = builder.setTitle(prettyTitle(item)).setView(box).setNegativeButton("Cerrar", null).create()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var ready = false
        var touching = false
        val tick = object : Runnable {
            override fun run() {
                if (!d.isShowing) return
                if (ready) {
                    if (!touching) bar.progress = video.currentPosition
                    time.text = fmt(video.currentPosition / 1000) + " / " + fmt(maxOf(video.duration, 0) / 1000)
                    play.text = if (video.isPlaying) "⏸ Pausa" else "▶ Reproducir"
                }
                handler.postDelayed(this, 400)
            }
        }
        video.setOnPreparedListener {
            ready = true
            bar.max = maxOf(video.duration, 0)
            bar.isEnabled = true
            play.isEnabled = true
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            ready = false
            bar.isEnabled = false
            play.isEnabled = false
            time.text = "No se puede reproducir este archivo aquí"
            true
        }
        play.setOnClickListener { if (video.isPlaying) video.pause() else video.start() }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) video.seekTo(p) }
            override fun onStartTrackingTouch(sb: SeekBar) { touching = true }
            override fun onStopTrackingTouch(sb: SeekBar) { touching = false }
        })
        d.setOnDismissListener {
            handler.removeCallbacks(tick)
            video.stopPlayback()
        }
        d.show()
        video.setVideoURI(uriOf(item))
        handler.post(tick)
    }

    /** [UNIR-MINI-1] Filas de «Unir»: miniatura con duración, nombre, carpeta y fecha, y el número de orden al marcar. */
    private inner class JoinPickAdapter(private val chosen: List<MediaItem>) : BaseAdapter() {
        var items: List<MediaItem> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? android.widget.LinearLayout) ?: newRow(parent.context)
            val h = row.tag as JoinRow
            val item = items[position]
            h.icon.setImageResource(if (item.isMusic) R.drawable.ic_nav_audio else R.drawable.ic_nav_video)
            bindThumb(item, h.thumb)
            h.time.text = if (item.durationSec > 0) fmt(item.durationSec) else ""
            h.time.visibility = if (item.durationSec > 0) View.VISIBLE else View.GONE
            h.name.text = prettyTitle(item)
            val info = ArrayList<String>()
            if (item.folder != "(raíz)") info.add(folderLabel(item.folder))   // [CARPETA-RUTA-1]
            if (item.dateAdded > 0) info.add(shortDate(item.dateAdded))
            h.info.text = info.joinToString("  ·  ")
            val n = chosen.indexOf(item) + 1
            h.mark.text = if (n > 0) n.toString() else ""
            h.mark.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                if (n > 0) setColor(accentColor())
                else setStroke((2 * resources.displayMetrics.density).toInt(), col(R.color.vlc_muted))
            }
            row.contentDescription = h.name.text.toString() + if (n > 0) ", marcado, número $n" else ", sin marcar"
            return row
        }

        private fun newRow(ctx: Context): android.widget.LinearLayout {
            fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
            val match = FrameLayout.LayoutParams.MATCH_PARENT
            val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
            val icon = ImageView(ctx)
            val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val time = TextView(ctx).apply {
                textSize = 11f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xB0000000.toInt())
                setPadding(dp(4), 0, dp(4), 0)
            }
            val pic = FrameLayout(ctx).apply {
                setBackgroundResource(R.drawable.bg_thumb)
                clipToOutline = true
                addView(icon, FrameLayout.LayoutParams(dp(28), dp(28), android.view.Gravity.CENTER))
                addView(thumb, FrameLayout.LayoutParams(match, match))
                addView(time, FrameLayout.LayoutParams(wrap, wrap, android.view.Gravity.BOTTOM or android.view.Gravity.END))
            }
            val name = TextView(ctx).apply {
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val info = TextView(ctx).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(col(R.color.vlc_muted))
            }
            val texts = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                addView(name)
                addView(info)
            }
            val mark = TextView(ctx).apply {
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            return android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                // los videos van con miniatura ancha; los audios, con carátula cuadrada
                addView(pic, android.widget.LinearLayout.LayoutParams(dp(if (showingMusic) 60 else 104), dp(60)))
                addView(texts, android.widget.LinearLayout.LayoutParams(0, wrap, 1f))
                addView(mark, android.widget.LinearLayout.LayoutParams(dp(28), dp(28)))
                tag = JoinRow(icon, thumb, time, name, info, mark)
            }
        }
    }

    private class JoinRow(val icon: ImageView, val thumb: ImageView, val time: TextView,
                          val name: TextView, val info: TextView, val mark: TextView)

    /** [TALLER-2] «Usar con otra herramienta»: el mismo archivo (la 1.ª parte, si hay varias) sin volver a buscarlo. */
    private fun workReuse(p: Workshop.Project) {
        val item = p.parts[0]
        val tools = if (item.isMusic) intArrayOf(1, 7) else intArrayOf(1, 2, 4, 5, 6)
        AlertDialog.Builder(this)
            .setTitle(p.base)
            .setItems(Array<CharSequence>(tools.size) { workToolLabel(tools[it]) }) { _, which ->
                workTool = tools[which]
                workPicked(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun workShare(p: Workshop.Project) {
        if (p.parts.size > 50) { toast("Se pueden compartir hasta 50 a la vez"); return }
        val uris = ArrayList<Uri>()
        for (m in p.parts) uris.add(uriOf(m))
        val type = if (p.parts.all { it.isMusic }) "audio/*" else "video/*"
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(i, "Compartir"))
        } catch (e: Exception) {
            toast("No se pudo compartir")
        }
    }

    // ------------------------------------------- [TALLER-COMPARTIR-1] compartir varios proyectos

    /** Lista sin nada marcado para elegir varios proyectos y enviarlos juntos (máximo 50 archivos en total). No borra ni cambia nada. */
    private fun workSharePick() {
        val all = workSorted()
        if (all.isEmpty()) { toast("Aún no hay proyectos"); return }
        val chosen = LinkedHashSet<String>()
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        val side = (16 * resources.displayMetrics.density).toInt()
        val summary = TextView(ctx).apply { text = "Nada marcado"; textSize = 13f }
        val rows = WorkCleanAdapter(all, chosen)
        val list = ListView(ctx).apply { adapter = rows }
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(side, 0, side, 0)
            addView(summary)
            addView(list, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5f).toInt(), 1f))
        }
        val dlg = builder
            .setTitle("📤 Compartir varios")
            .setView(box)
            .setPositiveButton("Compartir", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        val ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        fun picked() = all.filter { it.key in chosen }
        list.setOnItemClickListener { _, _, pos, _ ->
            val key = all[pos].key
            if (!chosen.remove(key)) chosen.add(key)
            rows.notifyDataSetChanged()
            val sel = picked()
            val n = sel.sumOf { it.parts.size }
            ok.isEnabled = sel.isNotEmpty() && n <= 50
            summary.text = if (sel.isEmpty()) "Nada marcado" else
                "Marcado: " + countLabel(sel.size, "proyecto", "proyectos") + " · " +
                countLabel(n, "archivo", "archivos") + " · " +
                android.text.format.Formatter.formatShortFileSize(this, sel.sumOf { it.bytes }) +
                (if (n > 50) "\nMáximo 50 archivos a la vez: desmarca alguno" else "")
        }
        ok.setOnClickListener {
            val files = picked().flatMap { it.parts }
            if (files.isNotEmpty() && files.size <= 50) {
                dlg.dismiss()
                workShareFiles(files)
            }
        }
    }

    /** Envía varios archivos juntos con el menú de compartir de Android (audio y video mezclados: tipo genérico). */
    private fun workShareFiles(files: List<MediaItem>) {
        val uris = ArrayList<Uri>()
        for (m in files) uris.add(uriOf(m))
        val type = when {
            files.all { it.isMusic } -> "audio/*"
            files.none { it.isMusic } -> "video/*"
            else -> "*/*"
        }
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(i, "Compartir"))
        } catch (e: Exception) {
            toast("No se pudo compartir")
        }
    }

    // ------------------------------------------- [RENOMBRAR-1] cambiar el nombre de un proyecto

    private val workPartRe = Regex(" \\(parte (\\d+) de (\\d+)\\)")
    private val workReservedRe = Regex(" ?\\((corte|unido|reducido|sin audio)\\)| ?\\(parte \\d+ de \\d+\\)")

    /** Nombre nuevo de un archivo: la base nueva + lo que iba después de la base (sufijo y extensión, o « (parte N de M)»). null si no se puede calcular. */
    private fun workNewName(kind: String, oldBase: String, m: MediaItem, newBase: String): String? {
        val n = m.name
        val rest: String? = when (kind) {
            "p" -> workPartRe.find(n)?.let { n.substring(it.range.first) }
            "r" -> n.lastIndexOf('.').let { if (it > 0) n.substring(it) else null }
            else -> if (n.startsWith(oldBase) && n.length > oldBase.length) n.substring(oldBase.length) else null
        }
        return if (rest.isNullOrEmpty()) null else newBase + rest
    }

    /** ✏️ en la tarjeta: cambia el nombre del archivo (o de todas las partes). Conserva el sufijo que el Taller reconoce y la extensión. */
    private fun workRename(p: Workshop.Project) {
        if (Build.VERSION.SDK_INT < 29) { toast("Cambiar el nombre necesita Android 10 o más"); return }
        val cur = playingItem
        if (cur != null && p.parts.any { it.id == cur.id && it.isMusic == cur.isMusic }) {
            toast("Detén la reproducción de este archivo antes de cambiarle el nombre")
            return
        }
        val kind = p.key.substringBefore('|')
        val first = p.parts[0]
        val oldBase = if (kind == "r") first.name.substringBeforeLast('.') else p.base
        if (p.parts.any { workNewName(kind, oldBase, it, "x") == null }) { toast("No se puede cambiar el nombre de este proyecto"); return }
        val sample = workNewName(kind, oldBase, first, "") ?: ""
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        val side = (20 * resources.displayMetrics.density).toInt()
        val et = EditText(ctx).apply {
            setText(oldBase)
            setSingleLine(true)
            hint = "Nombre"
            setSelection(text.length)
        }
        val box = android.widget.LinearLayout(ctx).apply {
            setPadding(side, side / 2, side, 0)
            addView(et, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dlg = builder
            .setTitle("✏️ Cambiar el nombre")
            .setMessage("Se guardará como «nombre" + sample + "»" +
                (if (p.parts.size > 1) " (y las otras " + (p.parts.size - 1) + " partes igual)." else ".") +
                "\nLa app conserva el final para reconocer el proyecto. Favoritos, listas y marcadores se mantienen.")
            .setView(box)
            .setPositiveButton("Cambiar") { _, _ ->
                var nb = et.text.toString().replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").replace(workReservedRe, "").trim()
                val ext = first.name.substringAfterLast('.', "")
                if (ext.isNotEmpty() && nb.endsWith("." + ext, ignoreCase = true)) nb = nb.dropLast(ext.length + 1)
                nb = nb.trim().trimEnd('.', ' ').take(100).trim()
                if (nb.isEmpty()) { toast("Escribe un nombre"); return@setPositiveButton }
                if (nb == oldBase) { toast("Es el mismo nombre"); return@setPositiveButton }
                workRenameRun(p, kind, oldBase, nb)
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        et.requestFocus()
    }

    private fun workRenameRun(p: Workshop.Project, kind: String, oldBase: String, newBase: String) {
        val pairs = ArrayList<Pair<MediaItem, String>>()
        for (m in p.parts) pairs.add(m to (workNewName(kind, oldBase, m, newBase) ?: return))
        val recUri = if (kind == "r") p.key.removePrefix("r|") else null
        thread {
            val err = workRenameFiles(pairs, recUri)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (err != null) { toast(err); return@runOnUiThread }
                toast("Nombre cambiado")
                workLastScan = 0L
                loadMedia(showingMusic, true)   // la biblioteca también ve el nombre nuevo
                if (viewMode == ViewMode.WORKSHOP) showWorkshop()
            }
        }
    }

    /** Renombra en MediaStore (Android 10+; solo archivos que CASTV creó) y pasa a la clave nueva lo guardado. null = bien; si no, el mensaje. Si uno falla, deshace los anteriores. */
    private fun workRenameFiles(pairs: List<Pair<MediaItem, String>>, recUri: String?): String? {
        for ((m, nn) in pairs) if (workNameTaken(m, nn)) return "Ya hay un archivo llamado «$nn» en esa carpeta"
        val done = ArrayList<MediaItem>()
        for ((m, nn) in pairs) {
            try {
                val v = android.content.ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, nn) }
                if (contentResolver.update(uriOf(m), v, null, null) <= 0) throw IllegalStateException()
                done.add(m)
            } catch (e: Exception) {
                for (dm in done) {
                    try {
                        val b = android.content.ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, dm.name) }
                        contentResolver.update(uriOf(dm), b, null, null)
                    } catch (x: Exception) { }
                }
                return if (e is SecurityException) "Android no deja cambiar el nombre de este archivo (no lo creó CASTV)"
                       else "No se pudo cambiar el nombre"
            }
        }
        for ((m, nn) in pairs) {
            val fin = workDisplayName(m) ?: nn
            val oldK = Store.key(m)
            val newK = Store.keyFor(m, fin)
            Store.renameKey(oldK, newK)
            Lrc.moveSaved(this, oldK, newK)
            if (recUri != null) Workshop.renameRecord(this, recUri, fin)
        }
        return null
    }

    /** ¿Ya hay otro archivo (de cualquier tipo) con ese nombre en la misma carpeta? */
    private fun workNameTaken(m: MediaItem, name: String): Boolean {
        if (name.equals(m.name, ignoreCase = true)) return false
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val rel = if (m.folder == "(raíz)") "" else m.folder.trimEnd('/') + "/"
            contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name, rel), null)?.use { it.count > 0 } ?: false
        } catch (e: Exception) { false }
    }

    /** Nombre que Android dejó al archivo tras renombrarlo (puede ajustarlo). */
    private fun workDisplayName(m: MediaItem): String? = try {
        contentResolver.query(uriOf(m), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) { null }

    private fun workFolder(p: Workshop.Project) {
        val folder = p.parts[0].folder
        if (!folder.contains("download", ignoreCase = true)) {
            toast("Está en la carpeta «" + folder.trimEnd('/').substringAfterLast('/') + "»")
            return
        }
        try {
            startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("No hay una app de archivos para abrir la carpeta")
        }
    }

    // ------------------------------------------- [CORTAR-1a] Cortar audio de lo que suena

    /** ✂️ en «Reproduciendo»: elige inicio y fin de la pista (o video) actual y guarda ese tramo como MP3 en Descargas. */
    private fun cutPlaying() {
        val item = playingItem
        if (item == null) { toast("No hay nada reproduciéndose"); return }
        val uri = uriOf(item)
        val durMs = item.durationSec * 1000L
        val pos: () -> Long = {
            var p = seekBar.progress * 1000L
            withLocal { mp -> p = mp.currentPosition.toLong() }
            p
        }
        if (item.isMusic) {
            CutAudio.pauseHook = { pauseAll() }   // [CORTAR-2] «Oír el tramo» pausa antes lo que suene en CASTV
            CutAudio.show(this, uri, item.name, durMs, false, pos)
        } else {   // [CORTAR-1b] un video: audio a MP3 o el tramo de video en .mp4
            AlertDialog.Builder(this)
                .setTitle("✂️ Cortar")
                .setItems(arrayOf<CharSequence>("🎵 Solo el audio → MP3", "🎬 El video → MP4 (sin recodificar)", "📦 Dividir por tamaño (partes de X MB)", "📉 Reducir peso (recodifica a X MB)", "🔇 Quitar el audio (copia sin sonido)")) { _, which ->
                    if (which == 2) {   // [DIVIDIR-1]
                        VideoSplit.show(this, uri, item.name)
                    } else if (which == 3) {   // [REDUCIR-1]
                        VideoReduce.show(this, uri, item.name)
                    } else if (which == 4) {   // [QUITAR-AUDIO-1]
                        VideoMute.show(this, uri, item.name)
                    } else {
                        CutAudio.pauseHook = { pauseAll() }   // [CORTAR-2]
                        CutAudio.show(this, uri, item.name, durMs, which == 1, pos)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ------------------------------------------- [MP3-LOTE-1] Varios videos → MP3

    @Volatile private var batchCancel = false

    /** Convierte a MP3 los videos seleccionados, uno tras otro (Descargas). Si uno falla sigue con el siguiente; Cancelar corta el lote. */
    private fun bulkMp3(src: List<MediaItem> = selectedItems()) {   // [TALLER-1] el Taller lo llama con un solo video
        val vids = src.filter { !it.isMusic }
        if (vids.isEmpty()) { toast("Selecciona videos"); return }
        if (Mp3Converter.running.get() || LinkDownload.running.get()) { toast("Ya hay una conversión o descarga en curso"); return }
        val app = applicationContext
        val total = vids.size
        val uris = vids.map { uriOf(it) }
        val title = "🎵 Convertir a MP3"
        batchCancel = false
        LinkJobNotifier.cancel = false   // [DESCARGA-BG-2]
        actionMode?.finish()
        // [DESCARGA-BG-2] con las notificaciones permitidas va en segundo plano (notificación con % y Cancelar);
        // si Android las tiene apagadas para CASTV, queda el recuadro de siempre (dlg != null)
        val dlg: AlertDialog? = if (LinkJobNotifier.enabled(app)) null else AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Preparando… 1 de $total")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        if (dlg != null) {
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                batchCancel = true
                dlg.setMessage("Cancelando…")
            }
        } else {
            LinkJobNotifier.progress(app, title, "Preparando… 1 de $total", -1)
            toast("En segundo plano: mira la notificación para ver el avance o cancelar")
        }
        val isCancelled = { batchCancel || LinkJobNotifier.cancel }
        thread {
            var okN = 0
            var failN = 0
            var cancelled = false
            var lastName = ""
            var lastUri: Uri? = null
            for ((k, m) in vids.withIndex()) {
                if (isCancelled()) { cancelled = true; break }
                val r = try {
                    Mp3Converter.convert(app, uris[k], m.name, { pct ->
                        val text = "Convirtiendo ${k + 1} de $total… $pct %"
                        if (dlg == null) LinkJobNotifier.progress(app, title, text, (k * 100 + pct) / total)
                        else runOnUiThread { if (!isFinishing && dlg.isShowing && !batchCancel) dlg.setMessage(text) }
                    }, isCancelled)
                } catch (e: Throwable) {
                    Mp3Converter.Result(false, "Error: " + e.javaClass.simpleName)
                }
                if (r.cancelled) { cancelled = true; break }
                if (r.ok) { okN++; lastName = r.savedAs; lastUri = r.uri } else failN++
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (dlg != null) { try { dlg.dismiss() } catch (e: Exception) { } }
                val summary = "🎵 $okN de $total en Descargas" +
                    (if (failN > 0) " · $failN no se pudieron convertir (sin audio o formato no compatible)" else "") +
                    (if (cancelled) " · cancelado" else "")
                // [DESCARGA-BG-2] con la app a la vista, lo de siempre; en segundo plano, notificación final
                val front = !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                if (dlg != null || front || cancelled) {
                    if (dlg == null) LinkJobNotifier.clear(app)
                    if (okN == 1 && failN == 0 && !cancelled) {
                        SavedFile.offer(this, "🎵", lastName, lastUri)   // [MP3-ABRIR-1]
                    } else {
                        Toast.makeText(app, summary, Toast.LENGTH_LONG).show()
                    }
                } else if (okN == 1 && failN == 0) {
                    LinkJobNotifier.done(app, "🎵 Guardado en Descargas", lastName, lastUri)
                } else {
                    LinkJobNotifier.done(app, if (okN > 0) "🎵 Conversión terminada" else "🎵 No se pudo completar", summary.removePrefix("🎵 "), null, folder = okN > 0)   // [CARPETA-1]
                }
            }
        }
    }

    // ------------------------------------------- [UNIR-1] Varios videos (o audios) → uno solo

    /** Une los videos (o audios .m4a/AAC) seleccionados en un solo archivo en Descargas, en el orden de la lista y sin recodificar. */
    private fun bulkJoin() {
        val sel = selectedItems()
        val allVid = sel.all { !it.isMusic }
        val allMus = sel.all { it.isMusic }
        if (sel.size < 2 || (!allVid && !allMus)) { toast("Selecciona 2 o más videos, o 2 o más audios (no mezclados)"); return }
        if (sel.size > VideoJoin.MAX_FILES) { toast("Máximo ${VideoJoin.MAX_FILES} archivos a la vez"); return }
        if (Mp3Converter.running.get() || LinkDownload.running.get()) { toast("Ya hay una conversión o descarga en curso"); return }
        val order = sel.mapIndexed { i, m -> "${i + 1}. ${m.name}" }.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("🔗 Unir " + sel.size + (if (allVid) " videos" else " audios"))
            .setMessage("Se unirán en este orden (el de la lista; cámbialo con «Ordenar»):\n\n" + order +
                "\n\nSin recodificar: no pierde calidad, pero solo funciona si todos son iguales (mismo códec, tamaño y giro)." +
                (if (allMus) " Para audios, solo .m4a/AAC." else ""))
            .setPositiveButton("Unir") { _, _ -> askJoinName(sel[0], allVid) { nm -> runJoin(sel, allVid, nm) } }   // [UNIR-NOMBRE-1]
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * [UNIR-NOMBRE-1] Pide el nombre del resultado antes de unir. Vacío = el de siempre (nombre del 1.º). La app añade « (unido)»
     * (así el Taller lo reconoce) y la extensión; los caracteres que no valen en un archivo se cambian por «_». Cancelar no une nada.
     * Entrega el nombre con extensión para que `Mp3Converter.cleanTitle` solo quite esa (un nombre como «Fiesta 12.30» no se corta).
     */
    private fun askJoinName(first: MediaItem, video: Boolean, go: (String?) -> Unit) {
        val ext = if (video) ".mp4" else ".m4a"
        val tail = Regex("\\s*\\(unido\\)\\s*$")
        val def = Mp3Converter.cleanTitle(first.name).replace(tail, "")
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context
        val side = (20 * resources.displayMetrics.density).toInt()
        val et = EditText(ctx).apply {
            setText(def)
            setSingleLine(true)
            hint = "Nombre del archivo"
            setSelection(text.length)
        }
        val box = android.widget.LinearLayout(ctx).apply {
            setPadding(side, side / 2, side, 0)
            addView(et, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dlg = builder
            .setTitle("🔗 Nombre del resultado")
            .setMessage("Se guardará en Descargas como «nombre (unido)$ext». Déjalo vacío para usar el del primer archivo.")
            .setView(box)
            .setPositiveButton("Unir") { _, _ ->
                val typed = et.text.toString().trim().replace(tail, "").trim()
                go(if (typed.isEmpty()) null else typed + ext)
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        et.requestFocus()
    }

    private fun runJoin(sel: List<MediaItem>, video: Boolean, typedName: String? = null) {
        val app = applicationContext
        val uris = sel.map { uriOf(it) }
        val names = if (typedName.isNullOrBlank()) sel.map { it.name } else listOf(typedName) + sel.drop(1).map { it.name }   // [UNIR-NOMBRE-1]
        val title = "🔗 Unir"
        batchCancel = false
        LinkJobNotifier.cancel = false
        actionMode?.finish()
        val dlg: AlertDialog? = if (LinkJobNotifier.enabled(app)) null else AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Preparando…")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        if (dlg != null) {
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                batchCancel = true
                dlg.setMessage("Cancelando…")
            }
        } else {
            LinkJobNotifier.progress(app, title, "Preparando…", -1)
            toast("En segundo plano: mira la notificación para ver el avance o cancelar")
        }
        val isCancelled = { batchCancel || LinkJobNotifier.cancel }
        thread {
            val r = try {
                VideoJoin.join(app, uris, names, video, { pct ->
                    val text = "Uniendo… $pct %"
                    if (dlg == null) LinkJobNotifier.progress(app, title, text, pct)
                    else runOnUiThread { if (!isFinishing && dlg.isShowing && !batchCancel) dlg.setMessage(text) }
                }, isCancelled)
            } catch (e: Throwable) {
                VideoJoin.Result(false, "Error: " + e.javaClass.simpleName)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (dlg != null) { try { dlg.dismiss() } catch (e: Exception) { } }
                val front = !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                if (r.cancelled) {
                    if (dlg == null) LinkJobNotifier.clear(app)
                    Toast.makeText(app, "Unión cancelada", Toast.LENGTH_LONG).show()
                } else if (r.ok) {
                    if (dlg != null || front) {
                        if (dlg == null) LinkJobNotifier.clear(app)
                        SavedFile.offer(this, "🔗", r.savedAs, r.uri)
                    } else {
                        LinkJobNotifier.done(app, "🔗 Guardado en Descargas", r.savedAs, r.uri)
                    }
                } else {
                    if (dlg == null) LinkJobNotifier.done(app, "🔗 No se pudo unir", r.message, null)
                    Toast.makeText(app, "No se pudo unir: " + r.message, Toast.LENGTH_LONG).show()
                }
            }
        }
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

    /** [NOVISTOS-1] Tras cambiar una marca "visto": con el filtro activo hay que volver a filtrar la lista. */
    private fun refreshSeen() {
        if (onlyUnseen && !showingMusic) applyFilters() else adapter.notifyDataSetChanged()
    }

    /** [VISTO-1] Marca (o desmarca) como vistos los videos seleccionados. */
    private fun bulkSeen() {
        val vids = selectedItems().filter { !it.isMusic }
        if (vids.isEmpty()) { toast("Selecciona al menos un video"); return }
        val unmark = vids.all { Store.isSeen(it) }
        for (m in vids) Store.markSeen(m, !unmark)
        actionMode?.finish()
        refreshSeen()   // [NOVISTOS-1]
        toast("👁 " + (if (unmark) countText(vids.size, "marcado como no visto", "marcados como no vistos")
                       else countText(vids.size, "marcado como visto", "marcados como vistos")))
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
        val bldAl = AlertDialog.Builder(this)   // [ICONOS-10]
        val icAl = bldAl.context
        val labels = ArrayList<CharSequence>()
        labels.addAll(names)
        labels.add(CastvIcons.item(icAl, R.drawable.ic_vp_add, "Nueva lista…"))
        bldAl
            .setTitle("Añadir ${sel.size} a lista")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == names.size) promptNewList { n -> addAllTo(n) }
                else addAllTo(names[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [SEL-2] Suena la primera ya; las demás van al principio de la cola (suenan en orden aunque la repetición esté apagada). */
    private fun bulkPlay() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        val first = sel[0]
        val rest = sel.drop(1)
        actionMode?.finish()
        queue.removeAll { q -> rest.any { it.id == q.id && it.isMusic == q.isMusic } }   // sin repetidos en la cola
        queue.addAll(0, rest)
        updateQueueBadge()
        playlist = ArrayList(currentList)
        playIndex = currentList.indexOfFirst { it.id == first.id }
        playingFromQueue = false
        startPlayback(first)
        toast("▶ " + countText(sel.size, "elemento", "elementos") + " en reproducción" +
            (if (rest.isNotEmpty()) " (el resto, en la cola)" else ""))
    }

    /** [SEL-2] Comparte los archivos seleccionados con otra app (WhatsApp, Drive…), por su enlace de MediaStore. */
    private fun bulkShare() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        if (sel.size > 50) {
            toast("Se pueden compartir hasta 50 a la vez")
            return
        }
        val uris = ArrayList<Uri>()
        for (m in sel) uris.add(uriOf(m))
        val type = if (sel.all { it.isMusic }) "audio/*" else if (sel.none { it.isMusic }) "video/*" else "*/*"
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris[0])
                else Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(i, "Compartir " + countText(sel.size, "archivo", "archivos")))
            actionMode?.finish()
        } catch (e: Exception) {
            toast("No se pudo compartir: ${e.message}")
        }
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
        toast(if (now) "🗑 Lo eliminado irá a la papelera de Android"
              else "⚠️ Lo eliminado se perderá para siempre")   // [VOCAB-4] igual que en Ajustes
    }

    private fun confirmDelete(items: List<MediaItem>, pinOk: Boolean = false) {
        if (items.isEmpty()) return
        if (!pinOk && ParentalPin.guardsDelete(this)) {   // [PARENTAL-2]
            ParentalPin.ask(this, "PIN para borrar") { confirmDelete(items, pinOk = true) }
            return
        }
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
        Zap.nowTitle = null; linkOnTv = false; linkDurSec = 0   // [PAUSA-1]
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
            reqLrc -> {   // [LRC-1]
                val u = data?.data
                val item = lrcPending
                lrcPending = null
                if (resultCode == RESULT_OK && u != null && item != null) importLrc(u, item)
            }
            reqSettings -> {
                // [UI-1b] Ajustes pide abrir el respaldo o las redes
                when (data?.getStringExtra("action")) {
                    "backup" -> showBackupMenu()
                    "socials" -> showSocials()
                    "rescan" -> loadMedia(showingMusic)   // [BIBLIO-1]
                    "equalizer" -> showEqualizer()   // [AUDIO-1]
                    "tips" -> showGestureTips()   // [NOVEDADES-1]
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
        // [TALLER-LIMPIA-1] el Taller quita ya lo borrado y vuelve a buscar
        workProjects = workProjects.filter { p -> p.parts.none { isGone(it) } }
        workLastScan = 0L
        for (g in gone) {
            if (allItems.none { Store.key(it) == Store.key(g) }) Store.forget(g)
        }
        dupIds = computeDups(allItems)
        applyFilters()
    }

    /** Menú de una fila (botón ⋮). */
    private fun showItemMenu(item: MediaItem) {
        val bldIt = AlertDialog.Builder(this)   // [ICONOS-10]
        val icIt = bldIt.context
        val labels = ArrayList<CharSequence>()
        val actions = ArrayList<() -> Unit>()
        labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_queue_add, "Añadir a la cola"))
        actions.add {
            queue.add(item); updateQueueBadge(); toast("➕ En cola: ${prettyTitle(item)}")
        }
        labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_skip_next, "Reproducir a continuación"))
        actions.add {
            queue.add(0, item); updateQueueBadge(); toast("⏭ Sonará después de este")
        }
        labels.add(if (Store.isFav(item)) CastvIcons.item(icIt, R.drawable.ic_vp_star_border, "Quitar de favoritos") else CastvIcons.item(icIt, R.drawable.ic_vp_star, "Añadir a favoritos"))
        actions.add { toggleFav(item) }
        labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_queue_music, "Añadir a lista…"))
        actions.add { addToListDialog(item) }
        selectedPlaylist?.let { pl ->
            labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_remove, "Quitar de «$pl»"))
            actions.add { Store.removeFromList(pl, item); applyFilters() }
        }
        labels.add(CastvIcons.item(icIt, R.drawable.ic_vp_delete, "Eliminar del teléfono…"))
        actions.add { confirmDelete(listOf(item)) }
        if (Store.resumeAt(item) >= 10) {
            labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_replay, "Olvidar el punto guardado"))
            actions.add { Store.clearResume(item); applyFilters() }
        }
        if (!item.isMusic) {   // [VISTO-1]
            val seen = Store.isSeen(item)
            labels.add(CastvIcons.item(icIt, R.drawable.ic_mn_visibility, if (seen) "Marcar como no visto" else "Marcar como visto"))
            actions.add { Store.markSeen(item, !seen); refreshSeen() }   // [NOVISTOS-1]
            val later = Store.isWatchLater(item)   // [VER-DESPUES-1]
            labels.add(CastvIcons.item(icIt, R.drawable.ic_mas_timer, if (later) "Quitar de «Ver más tarde»" else "Ver más tarde"))
            actions.add {
                val now = Store.toggleWatchLater(item)
                toast(if (now) "🕓 Guardado en «Ver más tarde» (Listas)" else "Quitado de «Ver más tarde»")
                applyFilters()
            }
        }
        bldIt
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
            .setNegativeButton("Guardar como lista") { _, _ -> saveQueueAsList() }   // [COLA-LISTA-1]
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

    /** [COLA-LISTA-1] Guarda lo que hay en la cola en una lista (nueva o existente). La cola no se vacía. */
    private fun saveQueueAsList() {
        if (queue.isEmpty()) { toast("La cola está vacía"); return }
        val snap = ArrayList(queue)
        fun addAllTo(listName: String) {
            var added = 0
            for (m in snap) if (Store.addToList(listName, m)) added++
            val skipped = snap.size - added
            toast("➕ " + countText(added, "añadida", "añadidas") + " a «$listName»" +
                (if (skipped > 0) " · $skipped ya estaban" else ""))
        }
        val names = Store.listNames()
        val labels = ArrayList<CharSequence>()
        labels.addAll(names)
        labels.add("＋ Nueva lista…")
        AlertDialog.Builder(this)
            .setTitle("Guardar ${snap.size} de la cola en una lista")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == names.size) promptNewList { n -> addAllTo(n) }
                else addAllTo(names[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            runOnUiThread { sleepFadeAndPause() }   // [FUNDIDO-1]
        }
    }

    // ------------------------------------------- [FUNDIDO-1] El temporizador baja el volumen poco a poco antes de pausar

    private val fadeSecs = 10
    @Volatile private var fadeGen = 0          // cambia al cancelar: el hilo del fundido se detiene
    @Volatile private var fadeActive = false
    private var fadeTvVol = -1                 // volumen del TV al empezar el fundido (para devolverlo)

    /** Se cumplió el temporizador: ~10 s bajando el volumen (teléfono o TV) y luego pausa; después el volumen vuelve a como estaba. */
    private fun sleepFadeAndPause() {
        val phone = localActive
        val tvVol = volSeek.progress
        val canFade = if (phone) localIsPlaying() else (tvPlaying && tvVol > 0)
        if (!canFade) {   // sin nada sonando o volumen del TV desconocido: pausa directa, como siempre
            pauseAll()
            toast("😴 Temporizador: reproducción en pausa")
            return
        }
        val gen = ++fadeGen
        fadeActive = true
        fadeTvVol = if (phone) -1 else tvVol
        toast("😴 Temporizador: bajando el volumen…")
        thread {
            for (i in 1..fadeSecs) {
                try { Thread.sleep(1000) } catch (e: InterruptedException) { return@thread }
                if (gen != fadeGen) return@thread
                val f = 1f - i / fadeSecs.toFloat()
                if (phone) runOnUiThread { if (gen == fadeGen) try { localPlayer?.setVolume(f, f) } catch (e: Exception) { } }
                else TvController.setVolume((tvVol * f).toInt())
            }
            if (gen != fadeGen) return@thread
            runOnUiThread {
                if (gen == fadeGen) {
                    pauseAll()
                    if (phone) applyLocalVolume()
                    toast("😴 Temporizador: reproducción en pausa")
                }
            }
            if (!phone) {   // el TV ya recibió la pausa: se devuelve el volumen para la próxima vez
                try { Thread.sleep(1500) } catch (e: InterruptedException) { }
                if (gen == fadeGen) TvController.setVolume(tvVol)
            }
            if (gen == fadeGen) fadeActive = false
        }
    }

    /** Si hay un fundido en marcha lo cancela y devuelve el volumen (pausar/reanudar a mano, nuevo temporizador, cerrar la pantalla). */
    private fun cancelSleepFade() {
        if (!fadeActive) return
        fadeGen++
        fadeActive = false
        if (localActive) applyLocalVolume()
        else { val v = fadeTvVol; if (v > 0) thread { TvController.setVolume(v) } }
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
        cancelSleepFade()   // [FUNDIDO-1]
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
            val up = activeUserPreset()   // [EQ-PRE-1] preset propio activo
            if (up != null) {
                eq.setEnabled(true)
                val lo = eq.bandLevelRange[0].toInt()
                val hi = eq.bandLevelRange[1].toInt()
                for (b in 0 until eq.numberOfBands.toInt()) {
                    if (b < up.second.size) eq.setBandLevel(b.toShort(), up.second[b].coerceIn(lo, hi).toShort())
                }
            } else if (eqPreset in 0 until n) {
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
        val upName = activeUserPreset()?.first   // [EQ-PRE-1]
        labels.add("🎛 Mis presets" + (if (upName != null) ": $upName" else "") + "…")
        labels.add(if (bassOn) "🔊 Graves reforzados: SÍ (toca para quitar)" else "🔊 Reforzar graves")
        AlertDialog.Builder(this)
            .setTitle("🎚 Ecualizador")
            .setSingleChoiceItems(labels.toTypedArray(), if (upName != null) eqNames.size + 1 else eqPreset + 1) { d, which ->
                if (which == labels.size - 1) {
                    bassOn = !bassOn
                } else if (which == labels.size - 2) {   // [EQ-PRE-1]
                    d.dismiss()
                    showUserPresets()
                    return@setSingleChoiceItems
                } else {
                    eqPreset = which - 1
                    getSharedPreferences("castv", MODE_PRIVATE).edit().putString("eq_user_on", "").apply()   // [EQ-PRE-1] uno del sistema apaga el propio
                }
                getSharedPreferences("castv", MODE_PRIVATE).edit()
                    .putInt("eqPreset", eqPreset).putBoolean("bassOn", bassOn).apply()
                if (localActive && localPrepared) localPlayer?.let { attachEffects(it) }
                d.dismiss()
            }
            .show()
    }

    // ------------------------------------------- [EQ-PRE-1] Presets propios del ecualizador

    private fun eqPrefs() = getSharedPreferences("castv", MODE_PRIVATE)

    /** Lee los presets propios: nombre y nivel (en milibelios) de cada banda. */
    private fun readUserPresets(): MutableList<Pair<String, IntArray>> {
        val out = ArrayList<Pair<String, IntArray>>()
        try {
            val arr = org.json.JSONArray(eqPrefs().getString("eq_user", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val l = o.getJSONArray("l")
                out.add(o.getString("n") to IntArray(l.length()) { j -> l.getInt(j) })
            }
        } catch (e: Exception) { }
        return out
    }

    private fun writeUserPresets(list: List<Pair<String, IntArray>>) {
        val arr = org.json.JSONArray()
        for (p in list) {
            val o = org.json.JSONObject()
            o.put("n", p.first)
            val la = org.json.JSONArray()
            for (v in p.second) la.put(v)
            o.put("l", la)
            arr.put(o)
        }
        eqPrefs().edit().putString("eq_user", arr.toString()).apply()
    }

    private fun activeUserPreset(): Pair<String, IntArray>? {
        val name = eqPrefs().getString("eq_user_on", "") ?: ""
        if (name.isEmpty()) return null
        return readUserPresets().firstOrNull { it.first == name }
    }

    private fun setActiveUserPreset(name: String) {
        eqPrefs().edit().putString("eq_user_on", name).apply()
        if (localActive && localPrepared) localPlayer?.let { attachEffects(it) }
    }

    private fun showUserPresets() {
        val list = readUserPresets()
        val on = activeUserPreset()?.first
        val bldPs = AlertDialog.Builder(this)   // [ICONOS-10]
        val icPs = bldPs.context
        val labels = ArrayList<CharSequence>()
        for (p in list) labels.add((if (p.first == on) "✓ " else "") + p.first)
        labels.add(CastvIcons.item(icPs, R.drawable.ic_vp_add, "Nuevo preset…"))
        if (on != null) labels.add(CastvIcons.item(icPs, R.drawable.ic_mn_stop, "Dejar de usar mi preset"))
        bldPs
            .setTitle("🎛 Mis presets")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < list.size -> userPresetMenu(list[which].first)
                    which == list.size -> editUserPreset(null)
                    else -> { setActiveUserPreset(""); toast("Mi preset desactivado") }
                }
            }
            .show()
    }

    private fun userPresetMenu(name: String) {
        val bldUp = AlertDialog.Builder(this)   // [ICONOS-9]
        val icUp = bldUp.context
        bldUp
            .setTitle(name)
            .setItems(arrayOf<CharSequence>(
                CastvIcons.item(icUp, R.drawable.ic_mn_check, "Usar"),
                CastvIcons.item(icUp, R.drawable.ic_mn_edit, "Editar"),
                CastvIcons.item(icUp, R.drawable.ic_vp_delete, "Eliminar"))) { _, which ->
                when (which) {
                    0 -> { setActiveUserPreset(name); toast("🎛 Usando «$name»") }
                    1 -> editUserPreset(name)
                    else -> {
                        writeUserPresets(readUserPresets().filter { it.first != name })
                        if (eqPrefs().getString("eq_user_on", "") == name) setActiveUserPreset("")
                        toast("Preset eliminado")
                    }
                }
            }
            .show()
    }

    /** Crea (name == null) o edita un preset propio con un deslizador por banda; se oye mientras lo mueves. */
    private fun editUserPreset(name: String?) {
        val eq = equalizer
        if (eq == null) {
            toast("Pon una pista en el teléfono y vuelve a abrir el ecualizador")
            return
        }
        val bands = eq.numberOfBands.toInt()
        val lo = eq.bandLevelRange[0].toInt()
        val hi = eq.bandLevelRange[1].toInt()
        val old = if (name != null) readUserPresets().firstOrNull { it.first == name }?.second else null
        val levels = IntArray(bands) { b ->
            old?.getOrNull(b) ?: try { eq.getBandLevel(b.toShort()).toInt() } catch (e: Exception) { 0 }
        }
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val nameEt = EditText(this)
        nameEt.hint = "Nombre del preset"
        nameEt.setSingleLine(true)
        nameEt.setText(name ?: "")
        box.addView(nameEt)
        for (b in 0 until bands) {
            val hz = try { eq.getCenterFreq(b.toShort()) / 1000 } catch (e: Exception) { 0 }
            val tv = TextView(this)
            tv.setPadding(0, dp(12), 0, 0)
            val upd = {
                val f = if (hz >= 1000) java.util.Locale.US.let { String.format(it, "%.1f kHz", hz / 1000.0) } else "$hz Hz"
                tv.text = f + "  ·  " + String.format(java.util.Locale.US, "%+.1f dB", levels[b] / 100.0)
            }
            val sb = SeekBar(this)
            sb.max = hi - lo
            sb.progress = (levels[b] - lo).coerceIn(0, hi - lo)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    levels[b] = lo + p
                    upd()
                    if (fromUser) {
                        try { eq.setEnabled(true); eq.setBandLevel(b.toShort(), levels[b].toShort()) } catch (e: Exception) { }
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            upd()
            box.addView(tv)
            box.addView(sb)
        }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle(if (name == null) "🎛 Nuevo preset" else "🎛 Editar preset")
            .setView(scroll)
            .setPositiveButton("Guardar") { _, _ ->
                val n = nameEt.text.toString().trim()
                if (n.isEmpty()) {
                    toast("Escribe un nombre para guardarlo")
                } else {
                    val all = readUserPresets().filter { it.first != name && it.first != n }.toMutableList()
                    all.add(n to levels.copyOf())
                    writeUserPresets(all)
                    setActiveUserPreset(n)
                    toast("🎛 Preset «$n» guardado y activado")
                }
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener {
                // vuelve al sonido guardado (si se canceló, deshace lo que se oyó mientras se movían los deslizadores)
                if (localActive && localPrepared) localPlayer?.let { attachEffects(it) }
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
        val bldPl2 = AlertDialog.Builder(this)   // [ICONOS-10]
        val icPl2 = bldPl2.context
        val labels = ArrayList<CharSequence>()
        for (n in names) labels.add(CastvIcons.item(icPl2, R.drawable.ic_mn_queue_music, "$n (${Store.listSize(n)})"))
        labels.add(CastvIcons.item(icPl2, R.drawable.ic_vp_add, "Nueva lista…"))
        bldPl2
            .setTitle("Mis listas de reproducción")   // [LISTA-NOMBRE-1]
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
        val bldPl = AlertDialog.Builder(this)   // [ICONOS-9]
        val icPl = bldPl.context
        bldPl
            .setTitle(name + " (" + Store.listSize(name) + ")")   // [LISTAS-1]
            .setItems(arrayOf<CharSequence>(
                CastvIcons.item(icPl, R.drawable.ic_mn_folder_open, "Abrir"),
                CastvIcons.item(icPl, R.drawable.ic_vp_play, "Reproducir"),
                CastvIcons.item(icPl, R.drawable.ic_shuffle, "Mezclar"),
                CastvIcons.item(icPl, R.drawable.ic_mn_queue_add, "Añadir a la cola"),
                CastvIcons.item(icPl, R.drawable.ic_mn_edit, "Renombrar…"),
                CastvIcons.item(icPl, R.drawable.ic_vp_delete, "Quitar lista"))) { _, which ->
                when (which) {
                    0 -> {
                        selectedPlaylist = name
                        groupReturn = if (inGroups()) viewMode else null   // [TABS-1]
                        viewMode = ViewMode.ALL
                        applyFilters()
                    }
                    1 -> playPlaylist(name, false)
                    2 -> playPlaylist(name, true)
                    3 -> queuePlaylist(name)
                    4 -> renamePlaylist(name)
                    else -> confirmRemovePlaylist(name)
                }
            }
            .show()
    }

    /** [LISTAS-1] Abre la lista y la reproduce en orden (o mezclada). El orden es el de Ordenar. */
    private fun playPlaylist(name: String, shuffle: Boolean) {
        if (allItems.none { Store.inList(name, it) }) {
            toast("Esta lista no tiene " + (if (showingMusic) "audios" else "videos"))
            return
        }
        val back = if (inGroups()) viewMode else null   // [TABS-1]
        resetFilters()
        selectedPlaylist = name
        groupReturn = back
        viewMode = ViewMode.ALL
        applyFilters()
        if (currentList.isEmpty()) return
        if (shuffle) shuffleAndPlay() else playFromList(0)
    }

    /** [LISTAS-1] Pone todas las pistas de la lista en la cola (suenan después de la actual). */
    private fun queuePlaylist(name: String) {
        val items = allItems.filter { Store.inList(name, it) }
        if (items.isEmpty()) {
            toast("Esta lista no tiene " + (if (showingMusic) "audios" else "videos"))
            return
        }
        queue.addAll(items)
        updateQueueBadge()
        toast("➕ " + countText(items.size, "añadida", "añadidas") + " a la cola")
    }

    /** [LISTAS-1] Cambiar el nombre de una lista. */
    private fun renamePlaylist(old: String) {
        val et = EditText(this)
        et.setText(old)
        et.setSelection(old.length)
        et.setSingleLine(true)
        AlertDialog.Builder(this)
            .setTitle("Renombrar lista")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty()) {
                    toast("Escribe un nombre")
                } else if (n != old) {
                    if (!Store.renameList(old, n)) {
                        toast("Ya existe una lista con ese nombre")
                    } else {
                        if (selectedPlaylist == old) selectedPlaylist = n
                        applyFilters()
                        toast("Lista renombrada a «$n»")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [LISTAS-1] Antes se quitaba al instante; ahora se confirma (las pistas no se borran del teléfono). */
    private fun confirmRemovePlaylist(name: String) {
        AlertDialog.Builder(this)
            .setTitle("¿Quitar la lista «$name»?")
            .setMessage("Tiene " + Store.listSize(name) + " elementos. Los archivos no se borran del teléfono.")
            .setPositiveButton("Quitar") { _, _ ->
                Store.deleteList(name)
                if (selectedPlaylist == name) {
                    selectedPlaylist = null
                    applyFilters()
                } else if (groupsShown) {
                    applyFilters()   // [TABS-1] repinta la pestaña Listas
                }
                toast("Lista quitada")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addToListDialog(item: MediaItem) {
        val names = Store.listNames()
        val bldAd = AlertDialog.Builder(this)   // [ICONOS-10]
        val icAd = bldAd.context
        val labels = ArrayList<CharSequence>()
        for (n in names) labels.add((if (Store.inList(n, item)) "✓ " else "") + n)
        labels.add(CastvIcons.item(icAd, R.drawable.ic_vp_add, "Nueva lista…"))
        bldAd
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
        setStatus("🔎 Buscando dispositivos en la red...")
        thread {
            val tvs = TvController.scanAll()
            runOnUiThread {
                if (tvs.isEmpty()) {
                    setStatus("⚠️ No se encontró ningún TV en esta red. ¿Está encendido y en el mismo Wi-Fi?")
                } else {
                    val labels = tvs.map { t ->
                        (if (t.ip == TvController.tvIp) "✓ " else "") + t.name + "  (" + t.ip + ")"
                    }
                    setStatus("Elige el dispositivo donde quieres reproducir")
                    AlertDialog.Builder(this)
                        .setTitle("Elegir TV")
                        .setItems(labels.toTypedArray()) { _, which -> connectTv(tvs[which]) }   // [TV-2]
                        .show()
                }
            }
        }
    }

    /** [TV-2] Usa el TV elegido: lo recuerda, lee sus rutas UPnP y actualiza el botón de salida. */
    private fun connectTv(tv: TvController.Tv) {
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

    // ------------------------------------------- [TV-2] Recuadro de TVs al activar el modo TV

    @Volatile private var tvScanBusy = false

    /**
     * Al pasar a modo TV busca los TVs del Wi-Fi.
     * Uno solo y aún sin TV elegido → se usa; el mismo ya elegido → solo se confirma;
     * varios (o uno distinto del elegido) → recuadro para elegir; ninguno → aviso con Reintentar
     * (si ya había un TV elegido, solo se avisa en la barra de estado y se sigue con ese).
     */
    private fun offerTvPicker() {
        if (tvScanBusy) return
        tvScanBusy = true
        setStatus("🔎 Buscando TVs en la red...")
        thread {
            val tvs = try { TvController.scanAll() } catch (e: Exception) { emptyList() }
            tvScanBusy = false
            runOnUiThread {
                if (isFinishing || isDestroyed || outPhone) return@runOnUiThread   // ya volvió a modo teléfono
                val cur = TvController.tvIp
                when {
                    tvs.isEmpty() -> {
                        if (cur != null) {
                            setStatus("⚠️ No se encontró ningún TV ahora; se sigue con " +
                                TvController.tvName.ifEmpty { cur })
                        } else {
                            showNoTvDialog()
                        }
                    }
                    tvs.size == 1 && cur == null -> connectTv(tvs[0])
                    tvs.size == 1 && tvs[0].ip == cur ->
                        setStatus("✅ TV: " + TvController.tvName.ifEmpty { tvs[0].name })
                    else -> showTvDialog(tvs)
                }
            }
        }
    }

    private fun showTvDialog(tvs: List<TvController.Tv>) {
        val labels = tvs.map { t ->
            (if (t.ip == TvController.tvIp) "✓ " else "") + t.name + "  (" + t.ip + ")"
        }
        setStatus("Elige el TV donde quieres reproducir")
        AlertDialog.Builder(this)
            .setTitle("📺 TVs en tu Wi-Fi")
            .setItems(labels.toTypedArray()) { _, which -> connectTv(tvs[which]) }
            .setNeutralButton("Reintentar") { _, _ -> offerTvPicker() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showNoTvDialog() {
        setStatus("⚠️ No se encontró ningún TV en esta red")
        AlertDialog.Builder(this)
            .setTitle("No encontré TVs")
            .setMessage("Revisa que el TV esté encendido y en el mismo Wi-Fi que el teléfono.")
            .setPositiveButton("Reintentar") { _, _ -> offerTvPicker() }
            .setNegativeButton("Cerrar", null)
            .show()
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
            v.findViewById<TextView>(R.id.itemName).apply {
                text = (if (sel) "✓ " else "") + prettyTitle(item)
                // [TITULO-BAL-1] reparte el título entre las líneas: no queda una palabra suelta en la segunda
                if (Build.VERSION.SDK_INT >= 23) breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            }
            val section = v.findViewById<TextView>(R.id.itemSection)
            val byName = sortMode == Sort.NAME_ASC || sortMode == Sort.NAME_DESC
            val letter = sectionLetter(item.name)
            // [PULIDO-2e] La fila "#" (números y símbolos) no se muestra; las letras A-Z siguen igual.
            if (byName && letter != "#" && (position == 0 || sectionLetter(items[position - 1].name) != letter)) {
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
                if (!item.isMusic && Store.isSeen(item)) parts.add("✓ visto")   // [VISTO-1]
                if (item.id in dupIds) parts.add("duplicado")
                if (sortMode == Sort.MOST_PLAYED) {
                    val c = Store.playCount(item)
                    if (c > 0) parts.add("▶ ${c}×")
                }
                if (sortMode == Sort.NEVER_PLAYED && Store.playCount(item) == 0) parts.add("🆕 sin reproducir")   // [LISTAS-INTEL-1]
                if (sortMode == Sort.MOST_MONTH) {   // [MES-1]
                    val mc = Store.monthPlays(item)
                    if (mc > 0) parts.add("▶ ${mc}× este mes")
                }
                action.text = (if (isFav) "★ " else "") + parts.joinToString("  ·  ")
                action.setTextColor(if (saved >= 10) accentColor() else col(R.color.vlc_muted))
            }
            action.visibility = View.VISIBLE
            v.findViewById<View>(R.id.itemRow).setBackgroundResource(
                if (isPlaying || sel) R.drawable.item_bg_playing else R.drawable.item_bg)
            applyCompactRow(v, CastvCompact.on(this@MainActivity))   // [COMPACTO-1]
            return v
        }
    }

    /** [COMPACTO-1] Fila de Pistas/Videos normal o compacta. Se pone en cada fila porque ListView reutiliza las vistas (y va después del fondo, que podría reiniciar el relleno). */
    private fun applyCompactRow(v: View, compact: Boolean) {
        fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
        val row = v.findViewById<View>(R.id.itemRow)
        row.minimumHeight = dp(if (compact) 50 else 68)
        row.setPadding(dp(16), dp(if (compact) 3 else 8), dp(4), dp(if (compact) 3 else 8))
        val box = v.findViewById<View>(R.id.itemThumbBox)
        val side = dp(if (compact) 40 else 52)
        val lp = box.layoutParams
        if (lp.width != side || lp.height != side) {
            lp.width = side
            lp.height = side
            box.layoutParams = lp
        }
        v.findViewById<TextView>(R.id.itemSection).setPadding(dp(16), dp(if (compact) 8 else 14), 0, dp(if (compact) 2 else 4))
        v.findViewById<TextView>(R.id.itemName).textSize = if (compact) 15f else 16f
        val act = v.findViewById<TextView>(R.id.itemAction)
        act.textSize = if (compact) 12f else 13f
        val alp = act.layoutParams as? ViewGroup.MarginLayoutParams
        val top = dp(if (compact) 0 else 2)
        if (alp != null && alp.topMargin != top) {
            alp.topMargin = top
            act.layoutParams = alp
        }
    }

    /** [TABS-1] Filas de Álbumes / Carpetas / Listas. */
    private inner class GroupAdapter : BaseAdapter() {
        var rows: List<GroupRow> = emptyList()
        var grid = false   // [ALBUMES-2] dos tarjetas por fila

        override fun getCount() = if (grid) (rows.size + 1) / 2 else rows.size
        override fun getItem(position: Int): Any = if (grid) position else rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (grid) 1 else 0

        /** [ALBUMES-2] Lado de la carátula cuadrada: la mitad del ancho menos márgenes. */
        private fun albumSide(): Int {
            val m = resources.displayMetrics
            return ((m.widthPixels - 20 * m.density) / 2 - 12 * m.density).toInt()
        }

        private fun bindAlbumCard(card: View, g: GroupRow?) {
            if (g == null) {
                card.visibility = View.INVISIBLE
                card.setOnClickListener(null)
                card.isClickable = false
                return
            }
            card.visibility = View.VISIBLE
            card.setOnClickListener { openGroup(g) }
            val cover = card.findViewById<View>(R.id.albCover)
            val lp = cover.layoutParams
            lp.height = albumSide()
            cover.layoutParams = lp
            card.findViewById<TextView>(R.id.albTitle).text = g.title
            card.findViewById<TextView>(R.id.albSub).text = g.sub
            card.findViewById<ImageView>(R.id.albIcon).setColorFilter(col(R.color.vlc_muted))
            val thumb = card.findViewById<ImageView>(R.id.albThumb)
            val rep = g.rep
            if (rep == null) {
                thumb.tag = null
                thumb.setImageDrawable(null)
                thumb.visibility = View.GONE
                return
            }
            val tkey = Thumbs.key(rep)
            thumb.tag = tkey
            val big = Thumbs.cachedBig(rep)
            val first = big ?: Thumbs.cached(rep)
            if (first != null) {
                thumb.setImageBitmap(first)
                thumb.visibility = View.VISIBLE
            } else {
                thumb.setImageDrawable(null)
                thumb.visibility = View.GONE
            }
            if (big == null) {
                Thumbs.loadBig(this@MainActivity, rep) { bmp ->
                    if (thumb.tag != tkey) return@loadBig
                    val show = bmp ?: Thumbs.cached(rep)
                    if (show != null) {
                        thumb.setImageBitmap(show)
                        thumb.visibility = View.VISIBLE
                    }
                }
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            if (grid) {   // [ALBUMES-2]
                val gv = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_group_grid, parent, false)
                bindAlbumCard(gv.findViewById<View>(R.id.cardA), rows.getOrNull(position * 2))
                bindAlbumCard(gv.findViewById<View>(R.id.cardB), rows.getOrNull(position * 2 + 1))
                // [ANIM-1b] las tarjetas nuevas de la primera pantalla aparecen con fundido; las recicladas quedan como estaban
                if (convertView == null && position < 4) CastvMotion.fadeIn(gv, position) else CastvMotion.reset(gv)
                return gv
            }
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group, parent, false)
            val g = rows[position]
            v.findViewById<TextView>(R.id.groupTitle).text = g.title
            v.findViewById<TextView>(R.id.groupSub).text = g.sub
            val icon = v.findViewById<ImageView>(R.id.groupIcon)
            icon.setImageResource(when (g.kind) {
                0 -> R.drawable.ic_mas_album
                1 -> R.drawable.ic_mas_folder
                3, 4 -> R.drawable.ic_vp_add   // [TALLER-1]
                5 -> if (g.rep?.isMusic == true) R.drawable.ic_nav_audio else R.drawable.ic_nav_video
                else -> R.drawable.ic_mas_list
            })
            icon.setColorFilter(if (g.kind == 3 || g.kind == 4) accentColor() else col(R.color.vlc_muted))
            val thumb = v.findViewById<ImageView>(R.id.groupThumb)
            val rep = g.rep
            if (rep != null) {
                bindThumb(rep, thumb)
            } else {
                thumb.tag = null
                thumb.setImageDrawable(null)
                thumb.visibility = View.GONE
            }
            return v
        }
    }
}
