package com.castv.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.ImageView
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
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
    private lateinit var btnMovies: Button
    private lateinit var btnMusic: Button
    private lateinit var btnAuto: Button
    private lateinit var btnMute: Button
    private lateinit var volSeek: SeekBar

    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: TextView
    private lateinit var btnSort: Button
    private lateinit var btnFolder: Button

    private enum class Sort(val label: String) {
        NAME_ASC("↕ A-Z"), NAME_DESC("↕ Z-A"), NEWEST("↕ Recientes"),
        LONGEST("↕ Más largos"), SHORTEST("↕ Más cortos")
    }

    private var allItems: List<MediaItem> = emptyList()    // todo lo escaneado de la pestaña
    private var sortMode = Sort.NAME_ASC
    private var selectedFolder: String? = null             // null = todas las carpetas
    private var query = ""
    private var currentList: List<MediaItem> = emptyList() // lo que se ve en pantalla
    private var playlist: List<MediaItem> = emptyList()    // lista con la que se avanza
    private var playIndex = -1
    private var playingItem: MediaItem? = null
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
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // ---------------------------------------------------------------- UI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listFiles = findViewById(R.id.listFiles)
        val inflater = LayoutInflater.from(this)
        val header = inflater.inflate(R.layout.header_player, listFiles, false)
        val footer = inflater.inflate(R.layout.footer_credits, listFiles, false)
        listFiles.addHeaderView(header, null, false)
        listFiles.addFooterView(footer, null, false)

        tvStatus = header.findViewById(R.id.tvStatus)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        tvElapsed = findViewById(R.id.tvElapsed)
        tvDuration = findViewById(R.id.tvDuration)
        seekBar = findViewById(R.id.seekBar)
        btnMovies = header.findViewById(R.id.btnMovies)
        btnMusic = header.findViewById(R.id.btnMusic)
        btnAuto = findViewById(R.id.btnAuto)
        btnMute = findViewById(R.id.btnMute)
        volSeek = findViewById(R.id.volSeek)
        etSearch = header.findViewById(R.id.etSearch)
        btnClearSearch = header.findViewById(R.id.btnClearSearch)
        btnSort = header.findViewById(R.id.btnSort)
        btnFolder = header.findViewById(R.id.btnFolder)

        sortMode = Sort.values().getOrElse(
            getSharedPreferences("castv", MODE_PRIVATE).getInt("sort", 0)) { Sort.NAME_ASC }
        btnSort.text = sortMode.label

        adapter = FileAdapter()
        listFiles.adapter = adapter

        btnMovies.setOnClickListener { showMovies() }

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

        btnSort.setOnClickListener {
            val all = Sort.values()
            sortMode = all[(sortMode.ordinal + 1) % all.size]
            getSharedPreferences("castv", MODE_PRIVATE).edit().putInt("sort", sortMode.ordinal).apply()
            btnSort.text = sortMode.label
            applyFilters()
        }
        btnFolder.setOnClickListener { pickFolder() }
        btnMusic.setOnClickListener { showMusic() }
        findViewById<Button>(R.id.btnPause).setOnClickListener { thread { TvController.pause() } }
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            manualStop = false
            thread { TvController.play() }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            manualStop = true
            thread { TvController.stop() }
            resetProgress()
        }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { step(+1) }
        findViewById<Button>(R.id.btnShuffle).setOnClickListener { shuffleAndPlay() }
        btnAuto.setOnClickListener { toggleAuto() }

        findViewById<Button>(R.id.btnBack10).setOnClickListener {
            val t = maxOf(0, lastPos - 10)
            thread { TvController.seek(t) }
        }
        findViewById<Button>(R.id.btnFwd10).setOnClickListener {
            val t = if (lastDur > 0) minOf(lastDur, lastPos + 10) else lastPos + 10
            thread { TvController.seek(t) }
        }
        btnMute.setOnClickListener {
            muted = !muted
            btnMute.text = if (muted) "🔇" else "🔊"
            val m = muted
            thread { TvController.setMute(m) }
        }
        volSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) { volTouching = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val v = sb.progress
                volTouching = false
                thread { TvController.setVolume(v) }
            }
        })

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
                thread { TvController.seek(target) }
            }
        })

        listFiles.setOnItemClickListener { _, _, position, _ ->
            val idx = position - listFiles.headerViewsCount
            if (idx in currentList.indices) {
                playlist = ArrayList(currentList)
                playIndex = idx
                startPlayback(playlist[idx])
            }
        }

        btnAuto.alpha = 0.5f
        updateTabs()
        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        monitoring = false
        super.onDestroy()
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
        selectedFolder = null
        applyFilters()
        updateTabs()
        if (allItems.isEmpty()) setStatus("No hay videos encontrados.")
    }

    private fun showMusic() {
        showingMusic = true
        allItems = MediaScanner.scanMusic(this)
        selectedFolder = null
        applyFilters()
        updateTabs()
        if (allItems.isEmpty()) setStatus("No hay música encontrada.")
    }

    // ------------------------------------------- Buscar, ordenar, carpetas

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()

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
        btnFolder.text = "📁 " + (selectedFolder?.substringAfterLast('/') ?: "Todas") +
                " (${currentList.size})"
        if (allItems.isNotEmpty() && currentList.isEmpty()) setStatus("Sin resultados.")
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
        styleTab(btnMovies, !showingMusic)
        styleTab(btnMusic, showingMusic)
    }

    private fun styleTab(b: Button, active: Boolean) {
        b.setBackgroundResource(if (active) R.drawable.tab_active else R.drawable.tab_off)
        b.setTextColor(if (active) 0xFFFF8800.toInt() else 0xFF9E9E9E.toInt())
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
        refreshList()
        startPlayback(playlist[0])
    }

    private fun startPlayback(item: MediaItem) {
        playingItem = item
        manualStop = false
        wasPlaying = false
        playStartedAt = SystemClock.elapsedRealtime()
        tvNowPlaying.text = item.name
        resetProgress()
        adapter.playingId = item.id
        adapter.playingMusic = item.isMusic
        adapter.notifyDataSetChanged()
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
                setStatus(if (ok) "✅ Reproduciendo en el TV" else "❌ Error al enviar al TV")
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
                if (TvController.tvIp == null) {
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
        if (SystemClock.elapsedRealtime() - playStartedAt < 4000) return

        if (state == "PLAYING") {
            wasPlaying = true
        } else if (state == "STOPPED" && wasPlaying) {
            wasPlaying = false
            if (autoNext && !manualStop) {
                runOnUiThread { step(+1) }
            }
        }
    }

    // ---------------------------------------------------------- Adaptador

    private inner class FileAdapter : BaseAdapter() {
        var items: List<MediaItem> = emptyList()
        var playingId: Long = -1
        var playingMusic: Boolean = false

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            val item = items[position]
            val isPlaying = item.id == playingId && item.isMusic == playingMusic
            v.findViewById<TextView>(R.id.itemName).text = item.name
            val icon = v.findViewById<TextView>(R.id.itemIcon)
            val thumb = v.findViewById<ImageView>(R.id.itemThumb)
            icon.text = if (item.isMusic) "🎵" else "🎬"
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
                    Thumbs.load(this@MainActivity, item) { bmp ->
                        if (bmp != null && thumb.tag == tkey) {
                            thumb.setImageBitmap(bmp)
                            thumb.visibility = View.VISIBLE
                        }
                    }
                }
            }
            val action = v.findViewById<TextView>(R.id.itemAction)
            if (isPlaying) {
                action.text = "▶ Reproduciendo en el TV"
                action.setTextColor(0xFFFF8800.toInt())
                action.visibility = View.VISIBLE
            } else if (item.durationSec > 0) {
                action.text = fmt(item.durationSec) + "  ·  " + item.folder.substringAfterLast('/')
                action.setTextColor(0xFF9E9E9E.toInt())
                action.visibility = View.VISIBLE
            } else {
                action.visibility = View.GONE
            }
            v.setBackgroundResource(if (isPlaying) R.drawable.item_bg_playing else R.drawable.item_bg)
            return v
        }
    }
}
