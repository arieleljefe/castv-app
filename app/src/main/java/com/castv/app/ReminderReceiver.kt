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

/** [EPG-RECORDA-1] Llega la hora de un aviso de programa («⏰ Avisarme» en la guía del reproductor): muestra la notificación. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        Reminders.fire(
            context,
            intent.getStringExtra("ch") ?: "el canal",
            url,
            intent.getStringExtra("title") ?: "",
            intent.getLongExtra("start", 0L))
    }
}

/** [EPG-RECORDA-1] Avisos de programas. Se anotan en las preferencias `castv_remind` (no entran en los respaldos) y se
 *  programan con una alarma inexacta de Android: no hace falta el permiso de alarmas exactas, así que pueden llegar con
 *  unos minutos de retraso, y se pierden si se reinicia el teléfono. Tocar el aviso abre el canal en el TV (salida TV) o en el reproductor del teléfono [AVISO-TV-1]. */
object Reminders {
    private const val CHANNEL = "castv_avisos"
    private const val PREFS = "castv_remind"
    private const val KEY = "list"
    private const val ACTION = "com.castv.app.REMIND"

    private fun code(url: String, start: Long): Int = 31 * url.hashCode() + start.hashCode()

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun alarmIntent(ctx: Context, url: String, start: Long, ch: String, title: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
            .putExtra("url", url).putExtra("start", start).putExtra("ch", ch).putExtra("title", title)
        return PendingIntent.getBroadcast(ctx, code(url, start), i, flags())
    }

    private fun load(ctx: Context): MutableList<JSONObject> {
        val out = ArrayList<JSONObject>()
        try {
            val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            val old = System.currentTimeMillis() - 2 * 3600_000L   // los de hace más de 2 h ya no importan
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("start", 0L) >= old) out.add(o)
            }
        } catch (_: Exception) {}
        return out
    }

    private fun save(ctx: Context, l: List<JSONObject>) {
        val arr = JSONArray()
        for (o in l) arr.put(o)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun has(ctx: Context, url: String, start: Long): Boolean =
        load(ctx).any { it.optString("url") == url && it.optLong("start", 0L) == start }

    /** Programa el aviso para la hora de inicio del programa. false = Android no dejó programarlo. */
    fun add(ctx: Context, ch: String, url: String, title: String, start: Long): Boolean {
        val at = maxOf(start, System.currentTimeMillis() + 5_000L)
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val p = alarmIntent(ctx, url, start, ch, title)
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.set(AlarmManager.RTC_WAKEUP, at, p)
        } catch (_: Exception) { return false }
        val l = load(ctx)
        l.removeAll { it.optString("url") == url && it.optLong("start", 0L) == start }
        l.add(JSONObject().put("url", url).put("start", start).put("ch", ch).put("title", title))
        save(ctx, l)
        return true
    }

    fun remove(ctx: Context, url: String, start: Long) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(ctx, url, start, "", ""))
        } catch (_: Exception) {}
        val l = load(ctx)
        l.removeAll { it.optString("url") == url && it.optLong("start", 0L) == start }
        save(ctx, l)
    }

    /** Llegó la hora: se borra de la lista y se muestra la notificación (si Android no deja avisar, no pasa nada). */
    fun fire(ctx: Context, ch: String, url: String, title: String, start: Long) {
        val l = load(ctx)
        l.removeAll { it.optString("url") == url && it.optLong("start", 0L) == start }
        save(ctx, l)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de programas", NotificationManager.IMPORTANCE_HIGH))
            }
            // [AVISO-TV-1] el aviso abre MainActivity, que decide: salida TV → el canal va al TV; salida Teléfono → reproductor del teléfono
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra("remind_url", url).putExtra("remind_title", ch)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val tap = PendingIntent.getActivity(ctx, code(url, start), open, flags())
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ " + title)
                .setContentText("A las " + Epg.hhmm(start) + " en " + ch)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            nm.notify(code(url, start), n)
        } catch (_: Exception) {}
    }
}
