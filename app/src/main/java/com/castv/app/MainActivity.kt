package com.castv.app

import android.Manifest
import android.app.NotificationManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.content.res.ColorStateList
import android.net.Uri
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
import java.net.Inet4Address
import java.text.Normalizer
import java.net.NetworkInterface
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
    private lateinit var btnQuick: ImageButton
    private lateinit var btnExpand: ImageButton
    private lateinit var playerExpanded: View
    private var lastQuickPlaying: Boolean? = null
    private lateinit var btnNowFav: Button
    private lateinit var btnOutput: ImageButton
    private lateinit var nowThumbBox: FrameLayout
    private lateinit var nowIcon: ImageView
    private lateinit var nowThumb: ImageView

    private enum class Sort(val label: String) {
        NAME_ASC("↕ A-Z"), NAME_DESC("↕ Z-A"), NEWEST("↕ Recientes"),
        LONGEST("↕ Más largos"), SHORTEST("↕ Más cortos")
    }

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
    @Volatile private var playingItem: MediaItem? = null
    private var showingMusic = false
    private var autoNext = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
        btnQuick = findViewById(R.id.btnQuick)
        btnExpand = findViewById(R.id.btnExpand)
        playerExpanded = findViewById(R.id.playerExpanded)

        sortMode = Sort.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("sort", 0)) { Sort.NAME_ASC }

        outPhone = getSharedPreferences("castv", MODE_PRIVATE).getBoolean("outPhone", false)
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
            setStatus(if (outPhone) "📱 La música sonará en el teléfono (los videos siguen yendo al TV)"
                      else "📺 La música sonará en el TV")
        }

        adapter = FileAdapter()
        listFiles.adapter = adapter

        navVideo.setOnClickListener { showMovies() }
        navAudio.setOnClickListener { showMusic() }
        navFolders.setOnClickListener { pickFolder() }
        navQueue.setOnClickListener { showQueue() }
        tabAll.setOnClickListener { viewMode = ViewMode.ALL; applyFilters() }
        tabFavs.setOnClickListener { viewMode = ViewMode.FAVS; applyFilters() }
        tabResume.setOnClickListener { viewMode = ViewMode.RESUME; applyFilters() }
        findViewById<View>(R.id.fabShuffle).setOnClickListener { shuffleAndPlay() }
        btnSearch.setOnClickListener { toggleSearch() }
        btnMenu.setOnClickListener { showMainMenu() }
        btnQuick.setOnClickListener { if (playingItem == null) step(+1) else togglePlayPause() }
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
        findViewById<View>(R.id.btnPause).setOnClickListener {
            if (localActive) {
                withLocal { it.pause() }
                saveProgress()
            } else thread { TvController.pause() }
        }
        findViewById<View>(R.id.btnPlay).setOnClickListener {
            manualStop = false
            if (localActive) {
                pausedByFocus = false
                withLocal { it.start() }
            } else thread { TvController.play() }
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            manualStop = true
            saveProgress()
            if (localActive) {
                withLocal { it.pause(); it.seekTo(0) }
            } else {
                thread { TvController.stop() }
                resetProgress()
            }
            adapter.notifyDataSetChanged()
        }
        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(+1) }
        findViewById<View>(R.id.btnShuffle).setOnClickListener { shuffleAndPlay() }
        btnAuto.setOnClickListener { toggleAuto() }

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
            if (idx in currentList.indices) playFromList(idx)
        }
        listFiles.setOnItemLongClickListener { _, _, position, _ ->
            val idx = position - listFiles.headerViewsCount
            if (idx in currentList.indices) showItemMenu(currentList[idx])
            true
        }

        btnAuto.alpha = 0.5f
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
        // Con música sonando en el teléfono, "atrás" minimiza en vez de cerrar y cortar la música
        if (localActive) moveTaskToBack(true) else super.onBackPressed()
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
        showMovies()
        monitoring = true
        thread { monitorLoop() }
    }

    private fun showMovies() {
        showingMusic = false
        allItems = MediaScanner.scanVideos(this)
        computeDups()
        selectedFolder = null
        applyFilters()
        updateTabs()
        if (allItems.isEmpty()) setStatus("No hay videos encontrados.")
    }

    private fun showMusic() {
        showingMusic = true
        allItems = MediaScanner.scanMusic(this)
        computeDups()
        selectedFolder = null
        applyFilters()
        updateTabs()
        if (allItems.isEmpty()) setStatus("No hay música encontrada.")
    }

    // ------------------------------------------- Buscar, ordenar, carpetas

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    /** Mismo nombre y misma duración = copia del mismo archivo. Se conserva la más antigua. */
    private fun computeDups() {
        val d = HashSet<Long>()
        for (g in allItems.groupBy { norm(it.name) + "|" + it.durationSec }.values) {
            if (g.size < 2) continue
            val keep = g.minWithOrNull(compareBy<MediaItem>({ it.dateAdded }, { it.id }))!!
            for (m in g) if (m.id != keep.id) d.add(m.id)
        }
        dupIds = d
    }

    /** Compara "Cap 2" antes que "Cap 10" (números por valor, no letra a letra). */
    private fun naturalCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei; j = ej
            } else {
                if (a[i] != b[j]) return a[i].compareTo(b[j])
                i++; j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun applyFilters() {
        var list = allItems
        selectedFolder?.let { f -> list = list.filter { it.folder == f } }
        if (Store.hideDups) list = list.filter { it.id !in dupIds }
        when (viewMode) {
            ViewMode.FAVS -> list = list.filter { Store.isFav(it) }
            ViewMode.RESUME -> list = list.filter { Store.resumeAt(it) >= 10 }
            ViewMode.ALL -> {}
        }
        if (query.isNotEmpty()) {
            val q = norm(query)
            list = list.filter { norm(it.name).contains(q) }
        }
        currentList = when (sortMode) {
            Sort.NAME_ASC -> list.sortedWith { a, b -> naturalCompare(norm(a.name), norm(b.name)) }
            Sort.NAME_DESC -> list.sortedWith { a, b -> naturalCompare(norm(b.name), norm(a.name)) }
            Sort.NEWEST -> list.sortedByDescending { it.dateAdded }
            Sort.LONGEST -> list.sortedByDescending { it.durationSec }
            Sort.SHORTEST -> list.sortedBy { it.durationSec }
        }
        refreshList()
        updateTabs()
        if (allItems.isNotEmpty() && currentList.isEmpty()) {
            setStatus(when (viewMode) {
                ViewMode.FAVS -> "Aún no hay favoritos: toca ⋮ en un archivo y elige ☆."
                ViewMode.RESUME -> "Nada por continuar todavía."
                ViewMode.ALL -> "Sin resultados."
            })
        }
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
        val on = 0xFF22D3EE.toInt()
        val off = 0xFF8B98A5.toInt()
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

    private fun showMainMenu() {
        val pm = PopupMenu(this, btnMenu)
        pm.menu.add(0, 1, 0, "Ordenar: " + sortMode.label.removePrefix("↕ "))
        pm.menu.add(0, 2, 1, when {
            dupIds.isEmpty() -> "Duplicados: ninguno"
            Store.hideDups -> "Mostrar duplicados (${dupIds.size})"
            else -> "Ocultar duplicados (${dupIds.size})"
        })
        pm.menu.add(0, 3, 2, if (outPhone) "Música en: Teléfono" else "Música en: TV")
        pm.menu.add(0, 4, 3, "Mis redes y contacto")
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> pickSort()
                2 -> if (dupIds.isNotEmpty()) {
                    Store.hideDups = !Store.hideDups
                    applyFilters()
                }
                3 -> btnOutput.performClick()
                4 -> showSocials()
            }
            true
        }
        pm.show()
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
        }
    }

    private fun setStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun toggleAuto() {
        autoNext = !autoNext
        btnAuto.setBackgroundResource(if (autoNext) R.drawable.ctrl_on else R.drawable.ctrl_off)
        btnAuto.alpha = if (autoNext) 1f else 0.5f
        setStatus(if (autoNext) "🔁 Auto-avance activado" else "🔁 Auto-avance desactivado")
    }

    private fun fmt(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

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

    private fun startPlayback(item: MediaItem, resumeAt: Int = 0) {
        playingItem = item
        pendingResume = resumeAt
        resumeTries = 0
        manualStop = false
        wasPlaying = false
        playStartedAt = SystemClock.elapsedRealtime()
        tvNowPlaying.text = prettyTitle(item.name)
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
        stopLocal()
        setStatus("🚀 Enviando al TV...")

        thread {
            val phoneIp = getLocalIpAddress()
            if (phoneIp == null || TvController.tvIp == null) {
                runOnUiThread { setStatus("⚠️ No se pudo determinar la red.") }
                return@thread
            }
            val kind = if (item.isMusic) "audio" else "video"
            val ext = MediaScanner.extFor(item.name, item.isMusic)
            val mime = MediaScanner.mimeFor(item.name, item.isMusic)
            val mediaUrl = "http://$phoneIp:8000/$kind/${item.id}.$ext"
            val ok = TvController.setUriAndPlay(mediaUrl, item.name, mime, item.isMusic)
            playStartedAt = SystemClock.elapsedRealtime()
            runOnUiThread {
                setStatus(
                    if (!ok) "❌ Error al enviar al TV"
                    else if (resumeAt > 0) "⏩ Retomando en ${fmt(resumeAt)}"
                    else "✅ Reproduciendo en el TV"
                )
            }
        }
    }

    /** IP del teléfono en la interfaz que realmente llega al TV (no la de datos móviles). */
    private fun getLocalIpAddress(): String? {
        val tv = TvController.tvIp
        if (tv != null) {
            try {
                java.net.DatagramSocket().use { s ->
                    s.connect(java.net.InetAddress.getByName(tv), 9) // no envía nada
                    val ip = s.localAddress.hostAddress
                    if (ip != null && ip != "0.0.0.0" && !ip.startsWith("127.")) return ip
                }
            } catch (e: Exception) { }
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.name.startsWith("wlan")) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (e: Exception) { }
        return null
    }

    // ------------------------------------------- Monitor: progreso + auto-avance

    private fun monitorLoop() {
        var tick = 0
        while (monitoring) {
            try {
                if (localActive) {
                    pollLocal(tick)
                } else if (TvController.tvIp == null) {
                    if (tick % 5 == 0) {
                        val found = TvController.discoverTv()
                        runOnUiThread {
                            setStatus(
                                if (found) "✅ TV encontrado: ${TvController.tvIp}"
                                else "⚠️ No se encontró el TV. Reintentando..."
                            )
                        }
                    }
                } else {
                    poll(tick)
                }
            } catch (e: Exception) { }
            tick++
            try { Thread.sleep(1000) } catch (e: InterruptedException) { return }
        }
    }

    private fun poll(tick: Int) {
        val info = TvController.getPositionInfo()
        if (info != null) {
            val (pos, rawDur) = info
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
                    if (autoNext || queue.isNotEmpty()) step(+1)
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
            } else {
                pausedByFocus = false
                withLocal { it.start() }
            }
        } else {
            val playingNow = tvPlaying
            manualStop = false
            thread { if (playingNow) TvController.pause() else TvController.play() }
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

        val title = prettyTitle(item.name)
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
        btnOutput.imageTintList = ColorStateList.valueOf(
            if (outPhone) 0xFF22D3EE.toInt() else 0xFFFFFFFF.toInt())
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
                    setStatus("❌ No se pudo reproducir en el teléfono")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopLocal()
            adapter.playingLocal = false
            adapter.notifyDataSetChanged()
            setStatus("❌ No se pudo reproducir en el teléfono")
        }
    }

    @Suppress("DEPRECATION")
    private fun stopLocal() {
        val mp = localPlayer
        localPlayer = null
        localActive = false
        localPrepared = false
        pausedByFocus = false
        if (mp != null) {
            try { mp.release() } catch (e: Exception) { }
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun onLocalCompleted() {
        playingItem?.let { Store.clearResume(it) }
        wasPlaying = false
        adapter.notifyDataSetChanged()
        if (autoNext || queue.isNotEmpty()) step(+1)
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
        btnNowFav.setTextColor(if (fav) 0xFFFFC107.toInt() else 0xFF8B98A5.toInt())
    }

    private fun toggleFav(item: MediaItem) {
        Store.toggleFav(item)
        updateNowFav()
        if (viewMode == ViewMode.FAVS) applyFilters() else adapter.notifyDataSetChanged()
    }

    private fun updateQueueBadge() {
        navQueue.text = if (queue.isEmpty()) "Cola" else "Cola (${queue.size})"
        val c = if (queue.isEmpty()) 0xFF8B98A5.toInt() else 0xFF22D3EE.toInt()
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
                .setTitle(item.name)
                .setItems(arrayOf("⏩ Continuar desde ${fmt(saved)}", "↺ Desde el inicio")) { _, which ->
                    go(if (which == 0) saved else 0)
                }
                .show()
        } else {
            go(0)
        }
    }

    /** Pulsación larga en una fila. */
    private fun showItemMenu(item: MediaItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add("➕ Añadir a la cola")
        actions.add {
            queue.add(item); updateQueueBadge(); toast("➕ En cola: ${item.name}")
        }
        labels.add("⏭ Reproducir a continuación")
        actions.add {
            queue.add(0, item); updateQueueBadge(); toast("⏭ Sonará después de este")
        }
        labels.add(if (Store.isFav(item)) "★ Quitar de favoritos" else "☆ Añadir a favoritos")
        actions.add { toggleFav(item) }
        if (Store.resumeAt(item) >= 10) {
            labels.add("↺ Olvidar el punto guardado")
            actions.add { Store.clearResume(item); applyFilters() }
        }
        AlertDialog.Builder(this)
            .setTitle(item.name)
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
                v.findViewById<TextView>(R.id.qTitle).text = "${position + 1}. ${prettyTitle(queue[position].name)}"
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
            v.findViewById<TextView>(R.id.itemName).text = prettyTitle(item.name)
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
                action.setTextColor(0xFF22D3EE.toInt())
            } else {
                val saved = Store.resumeAt(item)
                val parts = ArrayList<String>()
                parts.add(if (item.artist.isNotBlank()) item.artist else item.folder.substringAfterLast('/'))
                if (saved >= 10 && item.durationSec > 0) parts.add("⏩ ${fmt(saved)} / ${fmt(item.durationSec)}")
                else if (item.durationSec > 0) parts.add(fmt(item.durationSec))
                if (item.id in dupIds) parts.add("duplicado")
                action.text = (if (isFav) "★ " else "") + parts.joinToString("  ·  ")
                action.setTextColor(if (saved >= 10) 0xFF22D3EE.toInt() else 0xFF8B98A5.toInt())
            }
            action.visibility = View.VISIBLE
            v.findViewById<View>(R.id.itemRow).setBackgroundResource(
                if (isPlaying) R.drawable.item_bg_playing else R.drawable.item_bg)
            return v
        }
    }
}
