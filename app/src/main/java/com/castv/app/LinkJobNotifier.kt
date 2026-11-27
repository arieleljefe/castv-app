package com.castv.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * [DESCARGA-BG-1] Notificación con % y «Cancelar» para «⬇ Descargar» y «🎵 A MP3» de enlace, así la pantalla no queda
 * bloqueada. Al terminar, tocar la notificación abre el archivo. No lleva el enlace (R9); solo el nombre del archivo al final.
 */
object LinkJobNotifier {
    private const val CHANNEL = "castv_jobs"
    private const val ID = 4207
    const val ACT_CANCEL = "com.castv.app.LINKJOB_CANCEL"

    /** Lo pone en true el botón «Cancelar» de la notificación; el trabajo lo lee junto con el de la pantalla. */
    @Volatile var cancel = false

    fun enabled(ctx: Context): Boolean = try { NotificationManagerCompat.from(ctx).areNotificationsEnabled() } catch (e: Exception) { false }

    private fun immutable(): Int = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas y conversiones", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    /** [pct] -1 = sin porcentaje (barra indeterminada). */
    fun progress(ctx: Context, title: String, text: String, pct: Int) {
        try {
            ensureChannel(ctx)
            val cancelPi = PendingIntent.getBroadcast(
                ctx, ID, Intent(ctx, LinkJobReceiver::class.java).setAction(ACT_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or immutable())
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct.coerceAtLeast(0), pct < 0)
                .addAction(0, "Cancelar", cancelPi)
                .build()
            NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (e: Exception) { }
    }

    /** Aviso final (sin barra). Con [uri], tocarlo abre el archivo y salen los botones «Abrir» y «Abrir ubicación»;
     *  sin él, abre la app. [CARPETA-1] [folder] = true sin [uri] (varios archivos): solo «Abrir ubicación». */
    fun done(ctx: Context, title: String, text: String, uri: Uri?, folder: Boolean = false) {
        try {
            ensureChannel(ctx)
            val tap: Intent? = if (uri != null) {
                val mime = try { ctx.contentResolver.getType(uri) } catch (e: Exception) { null } ?: "*/*"
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val b = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setOngoing(false)
            if (tap != null) b.setContentIntent(PendingIntent.getActivity(ctx, ID, tap, PendingIntent.FLAG_UPDATE_CURRENT or immutable()))
            // [CARPETA-1] botones del aviso final: Abrir (el archivo) y Abrir ubicación (carpeta Descargas)
            if (uri != null && tap != null) {
                b.addAction(0, "Abrir", PendingIntent.getActivity(ctx, ID + 1, tap, PendingIntent.FLAG_UPDATE_CURRENT or immutable()))
            }
            if ((uri != null && SavedFile.inDownloads(ctx, uri)) || (uri == null && folder)) {
                val view = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                b.addAction(0, "Abrir ubicación", PendingIntent.getActivity(ctx, ID + 2, view, PendingIntent.FLAG_UPDATE_CURRENT or immutable()))
            }
            NotificationManagerCompat.from(ctx).notify(ID, b.build())
        } catch (e: Exception) { }
    }

    fun clear(ctx: Context) {
        try { NotificationManagerCompat.from(ctx).cancel(ID) } catch (e: Exception) { }
    }
}

/** Recibe el toque en «Cancelar» de la notificación (receptor explícito, sin IntentFilter). */
class LinkJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == LinkJobNotifier.ACT_CANCEL) LinkJobNotifier.cancel = true
    }
}
