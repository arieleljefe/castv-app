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
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
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

    private var currentList: List<MediaItem> = emptyList() // lo que se ve en pantalla
    private var playlist: List<MediaItem> = emptyList()    // lista con la que se avanza
    private var playIndex = -1
    private var playingItem: MediaItem? = null
    private var showingMusic = false
    private var autoNext = false
    private var userSeeking = false

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
        tvNowPlaying = header.findViewById(R.id.tvNowPlaying)
        tvElapsed = header.findViewById(R.id.tvElapsed)
        tvDuration = header.findViewById(R.id.tvDuration)
        seekBar = header.findViewById(R.id.seekBar)
        btnMovies = header.findViewById(R.id.btnMovies)
        btnMusic = header.findViewById(R.id.btnMusic)
        btnAuto = header.findViewById(R.id.btnAuto)

        adapter = FileAdapter()
        listFiles.adapter = adapter

        btnMovies.setOnClickListener { showMovies() }
        btnMusic.setOnClickListener { showMusic() }
        header.findViewById<Button>(R.id.btnPause).setOnClickListener { thread { TvController.pause() } }
        header.findViewById<Button>(R.id.btnPlay).setOnClickListener {
            manualStop = false
            thread { TvController.play() }
        }
        header.findViewById<Button>(R.id.btnStop).setOnClickListener {
            manualStop = true
            thread { TvController.stop() }
            resetProgress()
        }
        header.findViewById<Button>(R.id.btnPrev).setOnClickListener { step(-1) }
        header.findViewById<Button>(R.id.btnNext).setOnClickListener { step(+1) }
        header.findViewById<Button>(R.id.btnShuffle).setOnClickListener { shuffleAndPlay() }
        btnAuto.setOnClickListener { toggleAuto() }

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
        currentList = MediaScanner.scanVideos(this)
        refreshList()
        updateTabs()
        if (currentList.isEmpty()) setStatus("No hay videos encontrados.")
    }

    private fun showMusic() {
        showingMusic = true
        currentList = MediaScanner.scanMusic(this)
        refreshList()
        updateTabs()
        if (currentList.isEmpty()) setStatus("No hay música encontrada.")
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
        b.setBackgroundResource(if (active) R.drawable.btn_accent else R.drawable.btn_off)
        b.setTextColor(if (active) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt())
    }

    private fun setStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun toggleAuto() {
        autoNext = !autoNext
        btnAuto.setBackgroundResource(if (autoNext) R.drawable.btn_green else R.drawable.btn_off)
        btnAuto.setTextColor(if (autoNext) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        btnAuto.text = if (autoNext) "🔁 Auto: ON" else "🔁 Auto: OFF"
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
            val (pos, dur) = info
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
            v.findViewById<TextView>(R.id.itemAction).text =
                if (isPlaying) "▶️ Reproduciendo" else "Reproducir ➔"
            v.setBackgroundResource(if (isPlaying) R.drawable.item_bg_playing else R.drawable.item_bg)
            return v
        }
    }
}
