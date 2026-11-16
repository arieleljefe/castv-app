package com.castv.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var listFiles: ListView
    private var currentList: List<MediaItem> = emptyList()
    private var showingMusic = false

    private val requiredPermissions by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        listFiles = findViewById(R.id.listFiles)

        findViewById<Button>(R.id.btnMovies).setOnClickListener { showMovies() }
        findViewById<Button>(R.id.btnMusic).setOnClickListener { showMusic() }
        findViewById<Button>(R.id.btnPause).setOnClickListener { thread { TvController.pause() } }
        findViewById<Button>(R.id.btnPlay).setOnClickListener { thread { TvController.play() } }
        findViewById<Button>(R.id.btnStop).setOnClickListener { thread { TvController.stop() } }

        listFiles.setOnItemClickListener { _, _, position, _ ->
            val item = currentList[position]
            playItem(item)
        }

        checkPermissionsAndStart()
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
        // Arranca el servicio en primer plano con el servidor HTTP embebido
        val intent = Intent(this, CastvService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        showMovies()

        thread {
            val found = TvController.discoverTv()
            runOnUiThread {
                tvStatus.text = if (found) {
                    "✅ TV encontrado: ${TvController.tvIp}"
                } else {
                    "⚠️ No se encontró el TV. Revisa la red WiFi."
                }
            }
        }
    }

    private fun showMovies() {
        showingMusic = false
        currentList = MediaScanner.scanVideos(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, currentList.map { it.name })
        listFiles.adapter = adapter
    }

    private fun showMusic() {
        showingMusic = true
        currentList = MediaScanner.scanMusic(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, currentList.map { it.name })
        listFiles.adapter = adapter
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

    private fun playItem(item: MediaItem) {
        thread {
            val phoneIp = getLocalIpAddress()
            if (phoneIp == null || TvController.tvIp == null) {
                runOnUiThread { tvStatus.text = "⚠️ No se pudo determinar la red." }
                return@thread
            }
            val kind = if (item.isMusic) "audio" else "video"
            val ext = MediaScanner.extFor(item.name, item.isMusic)
            val mime = MediaScanner.mimeFor(item.name, item.isMusic)
            val mediaUrl = "http://$phoneIp:8000/$kind/${item.id}.$ext"
            val ok = TvController.setUriAndPlay(mediaUrl, item.name, mime, item.isMusic)
            runOnUiThread {
                tvStatus.text = if (ok) "▶️ Reproduciendo: ${item.name}" else "❌ Error al enviar al TV"
            }
        }
    }
}
