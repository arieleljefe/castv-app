package com.castv.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * [CANAL-HORA-1] «⏰ Avisarme a una hora»: a la hora elegida suena una notificación y al tocarla el canal se abre por el
 * mismo camino que el aviso de un programa (`remind_url` / `remind_title` en MainActivity). No abre nada solo (Android
 * no deja a una app abrir pantallas desde segundo plano). Archivo propio `castv_alarms` (fuera de respaldos: R9);
 * no se admiten canales con usuario y clave. Usa setAlarmClock: hora exacta y sin permisos nuevos.
 * Límite: Android borra las alarmas al reiniciar el teléfono.
 */
object ChannelAlarm {
    private const val PREFS = "castv_alarms"
    private const val KEY = "list"
    private const val CH_ID = "castv_alarm"
    private const val MAX = 20

    class Item(val id: Int, val url: String, val title: String, val at: Long)

    private fun read(ctx: Context): MutableList<Item> {
        val out = ArrayList<Item>()
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getInt("i"), o.getString("u"), o.optString("n", "Canal"), o.getLong("t")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun write(ctx: Context, l: List<Item>) {
        try {
            val arr = JSONArray()
            for (x in l) arr.put(JSONObject().put("i", x.id).put("u", x.url).put("n", x.title).put("t", x.at))
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun firePi(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx.applicationContext, id,
            Intent(ctx.applicationContext, ChannelAlarmReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** El aviso pendiente de este canal (o null). Los ya vencidos no cuentan. */
    @Synchronized
    fun find(ctx: Context, url: String): Item? {
        val now = System.currentTimeMillis()
        return read(ctx).firstOrNull { it.url == url && it.at > now }
    }

    /** Programa el aviso de [url] a la hora [at] (ms). Un canal tiene un solo aviso: el nuevo reemplaza al anterior. false si no se pudo. */
    @Synchronized
    fun schedule(ctx: Context, url: String, title: String, at: Long): Boolean {
        val app = ctx.applicationContext
        try {
            val now = System.currentTimeMillis()
            val l = read(app).filter { it.at > now }.toMutableList()
            l.filter { it.url == url }.forEach { cancelAlarm(app, it.id) }
            l.removeAll { it.url == url }
            if (l.size >= MAX) return false
            val id = ((now / 1000L) % 1_000_000_000L).toInt() + l.size
            val show = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), firePi(app, id))
            l.add(Item(id, url, title, at))
            write(app, l)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun cancelAlarm(app: Context, id: Int) {
        try { (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(firePi(app, id)) } catch (e: Exception) { }
    }

    @Synchronized
    fun cancel(ctx: Context, id: Int) {
        val app = ctx.applicationContext
        cancelAlarm(app, id)
        write(app, read(app).filter { it.id != id })
    }

    /** Lo llama el receptor: saca el aviso de la lista y lo devuelve (null si ya no existe). */
    @Synchronized
    internal fun take(ctx: Context, id: Int): Item? {
        val l = read(ctx)
        val it = l.firstOrNull { x -> x.id == id } ?: return null
        write(ctx, l.filter { x -> x.id != id })
        return it
    }

    internal fun notifyNow(ctx: Context, it: Item) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH_ID, "Avisos de canales", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            ctx, it.id,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("remind_url", it.url)
                .putExtra("remind_title", it.title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("⏰ Es hora de " + it.title)
            .setContentText("Toca para verlo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(it.id, n)
    }
}

class ChannelAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val id = intent.getIntExtra("id", -1)
            if (id < 0) return
            val item = ChannelAlarm.take(context, id) ?: return
            ChannelAlarm.notifyNow(context, item)
        } catch (e: Exception) { }
    }
}
