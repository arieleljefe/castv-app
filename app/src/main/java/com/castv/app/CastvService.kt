package com.castv.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat

class CastvService : Service() {

    private var server: MediaServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val channelId = "castv_channel"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CASTV activo")
            .setContentText("Listo para transmitir al TV")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)

        // Mantiene CPU y Wi-Fi despiertos con la pantalla apagada
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castv:server").apply {
                setReferenceCounted(false)
                acquire()
            }
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                       else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "castv:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            DebugLog.add("No se pudieron activar los bloqueos de energía: ${e.message}")
        }

        // [Etapa 13-A.1] Prueba del 8000 al 8010 hasta encontrar un puerto libre
        var started = false
        var lastErr: Exception? = null
        for (p in 8000..8010) {
            try {
                val s = MediaServer(this, p)
                s.start()
                server = s
                ServerPort.port = p
                started = true
                break
            } catch (e: Exception) { lastErr = e }
        }
        try {
            if (!started) throw (lastErr ?: Exception("sin puerto libre"))
            ServerState.error = null
            DebugLog.add("Servidor iniciado en el puerto ${ServerPort.port}")
        } catch (e: Exception) {
            ServerState.error = "Puerto 8000 ocupado: ${e.message}"
            DebugLog.add("ERROR al iniciar el servidor: ${e.javaClass.simpleName}: ${e.message}")
            val warn = NotificationCompat.Builder(this, channelId)
                .setContentTitle("CASTV: puerto 8000 ocupado")
                .setContentText("Otro programa usa el puerto. Ciérralo y reinicia CASTV.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build()
            getSystemService(NotificationManager::class.java).notify(1, warn)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        try { wakeLock?.release() } catch (e: Exception) { }
        try { wifiLock?.release() } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "CASTV", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
