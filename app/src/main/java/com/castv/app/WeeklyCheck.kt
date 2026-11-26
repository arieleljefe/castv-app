package com.castv.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [REVISION-SEMANAL-1] Una vez por semana, al abrir la app, revisa en segundo plano los canales favoritos
 * (los mismos que revisa «🔎 Revisar»: `MediaServer.checkChannel`) y avisa con una notificación si alguno ya no responde.
 *
 * Seguro: solo con Wi-Fi (no gasta datos móviles), no en incógnito, nunca canales de Xtream (llevan usuario y clave),
 * máximo 80 canales y 4 minutos. Un canal solo cuenta como caído si falla dos veces seguidas en la misma revisión
 * (la segunda, 10 s después), así que aparece con ⚠ en Canales y se puede buscar otra fuente con mantener pulsado.
 * Si fallan casi todos (más del 70 %) se entiende que no hay conexión y no se anota nada.
 */
object WeeklyCheck {
    private const val EVERY_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_CHANNELS = 80
    private const val KEY_LAST = "wk_last"
    private const val NOTIF_CHANNEL = "castv_weekly"
    @Volatile private var running = false

    /** Llamar desde un hilo de fondo. No hace nada si no toca, si no hay Wi-Fi o si no hay favoritos. */
    fun runIfNeeded(ctx: Context) {
        if (running || Store.incognito) return
        val sp = ctx.getSharedPreferences("castv", Context.MODE_PRIVATE)
        val last = sp.getLong(KEY_LAST, 0L)
        if (last != 0L && System.currentTimeMillis() - last < EVERY_MS) return
        if (!onUnmeteredWifi(ctx)) return
        val leak = Xtream.leakFilter(ctx)
        val list = Store.chFavChannels().filter { !Xtream.isXtream(it.url) && !leak(it.url) }.take(MAX_CHANNELS)
        if (list.isEmpty()) return
        running = true
        try {
            val first = checkAll(list, System.currentTimeMillis() + 3 * 60 * 1000L)
            val f1 = first.first
            if (list.size >= 5 && f1.size * 10 > list.size * 7) {
                DebugLog.add("REVISION-SEMANAL: fallaron ${f1.size} de ${list.size}; parece que no hay conexión, se intentará otro día")
                return
            }
            var f2: List<M3uChannel> = emptyList()
            var ok2: List<String> = emptyList()
            if (f1.isNotEmpty()) {
                Thread.sleep(10000)
                val second = checkAll(f1, System.currentTimeMillis() + 60 * 1000L)
                f2 = second.first
                ok2 = second.second
            }
            Store.recordHealth(f2.map { it.url }, first.second + ok2)
            Store.recordHealth(f2.map { it.url }, emptyList())   // dos fallos seguidos en esta revisión: ya sale con ⚠
            sp.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            DebugLog.add("REVISION-SEMANAL: ${list.size} favoritos revisados, ${f2.size} sin respuesta")
            if (f2.isNotEmpty()) notifyFails(ctx, f2)
        } catch (e: Exception) {
            DebugLog.add("REVISION-SEMANAL: error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            running = false
        }
    }

    @Suppress("DEPRECATION")
    private fun onUnmeteredWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.activeNetworkInfo?.isConnected == true && !cm.isActiveNetworkMetered
    }

    /** Revisa [list] con 4 hilos; devuelve (los que no respondieron, los enlaces que respondieron bien). */
    private fun checkAll(list: List<M3uChannel>, deadline: Long): Pair<List<M3uChannel>, List<String>> {
        val fails = ConcurrentLinkedQueue<M3uChannel>()
        val oks = ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(4)
        for (c in list) {
            pool.execute {
                if (System.currentTimeMillis() > deadline) return@execute
                val why = MediaServer.checkChannel(c.url)
                if (why != null) fails.add(c) else oks.add(c.url)
            }
        }
        pool.shutdown()
        try { pool.awaitTermination(5, TimeUnit.MINUTES) } catch (e: InterruptedException) { }
        return Pair(fails.toList(), oks.toList())
    }

    private fun notifyFails(ctx: Context, fails: List<M3uChannel>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(NOTIF_CHANNEL, "Revisión semanal", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 77, Intent(ctx, M3uActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val names = fails.take(3).joinToString(", ") { it.name }
        val more = if (fails.size > 3) " y ${fails.size - 3} más" else ""
        val text = "$names$more. Toca para abrir Canales: mantén pulsado uno con ⚠ y busca otra fuente."
        val n = NotificationCompat.Builder(ctx, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔎 ${fails.size} favoritos ya no responden")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(7001, n) } catch (e: SecurityException) { }
    }
}
