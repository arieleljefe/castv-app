package com.castv.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat

class CastvService : Service() {

    private var server: MediaServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wifiLockHigh: WifiManager.WifiLock? = null   // [SEGUNDO-PLANO-1] segundo candado Wi-Fi (alto rendimiento)
    private var screenReceiver: BroadcastReceiver? = null    // [SEGUNDO-PLANO-1] solo anota en el /log
    private val channelId = "castv_channel"

    @Suppress("DEPRECATION")
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
            // [SEGUNDO-PLANO-1] el modo de baja latencia solo actúa con la app a la vista y la pantalla encendida;
            // el de alto rendimiento (el que había antes de Android 10) sigue activo con la pantalla apagada
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    wifiLockHigh = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castv:wifihigh").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                } catch (e: Exception) {
                    DebugLog.add("SEGUNDO PLANO: no se pudo pedir el candado Wi-Fi de alto rendimiento: ${e.message}")
                }
            }
            logPowerState("al iniciar el servicio", pm)
        } catch (e: Exception) {
            DebugLog.add("No se pudieron activar los bloqueos de energía: ${e.message}")
        }

        // [SEGUNDO-PLANO-1] anota en el /log cuándo se apaga y se enciende la pantalla (solo diagnóstico)
        try {
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> logPowerState("PANTALLA apagada", null)
                        Intent.ACTION_SCREEN_ON -> logPowerState("PANTALLA encendida", null)
                    }
                }
            }
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(r, f)
            screenReceiver = r
        } catch (e: Exception) {
            DebugLog.add("SEGUNDO PLANO: no se pudo vigilar la pantalla: ${e.message}")
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
            ServerState.error = "Sin puerto libre del 8000 al 8010: ${e.message}"
            DebugLog.add("ERROR al iniciar el servidor: ${e.javaClass.simpleName}: ${e.message}")
            val warn = NotificationCompat.Builder(this, channelId)
                .setContentTitle("CASTV: sin puerto libre (8000-8010)")
                .setContentText("Otro programa usa el puerto. Ciérralo y reinicia CASTV.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build()
            getSystemService(NotificationManager::class.java).notify(1, warn)
        }
    }

    /** [SEGUNDO-PLANO-1] Una línea en el /log con el estado de energía: versión de Android, candados y ahorro de batería. */
    private fun logPowerState(motivo: String, pmIn: PowerManager?) {
        try {
            val pm = pmIn ?: (getSystemService(POWER_SERVICE) as PowerManager)
            val sinRestriccion = try { pm.isIgnoringBatteryOptimizations(packageName) } catch (e: Exception) { null }
            DebugLog.add("SEGUNDO PLANO ($motivo): Android ${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL}; " +
                    "cpu=${wakeLock?.isHeld} wifi=${wifiLock?.isHeld} wifiAlto=${wifiLockHigh?.isHeld}; " +
                    "sinRestriccionDeBateria=$sinRestriccion; ahorroDeBateria=${pm.isPowerSaveMode}; pantallaEncendida=${pm.isInteractive}")
        } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        DebugLog.add("SEGUNDO PLANO: el servicio se detiene (onDestroy)")   // [SEGUNDO-PLANO-1]
        server?.stop()
        try { screenReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) { }
        try { wakeLock?.release() } catch (e: Exception) { }
        try { wifiLock?.release() } catch (e: Exception) { }
        try { wifiLockHigh?.release() } catch (e: Exception) { }
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
