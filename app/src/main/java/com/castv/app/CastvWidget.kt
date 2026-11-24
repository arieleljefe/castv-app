package com.castv.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * [WIDGET-1] Widget de pantalla de inicio con ⏮ ⏯ ⏭.
 * Los botones envían las mismas acciones ACT_PREV / ACT_TOGGLE / ACT_NEXT que la notificación,
 * así que solo responden con la app viva (la reproducción vive en MainActivity).
 * MainActivity le pasa el título y el estado con push() y lo deja en reposo con reset().
 */
class CastvWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { manager.updateAppWidget(id, build(context)) } catch (e: Exception) { }
        }
    }

    companion object {
        private var title = "CASTV"
        private var sub = "Abre la app para reproducir"
        private var playing = false

        /** Estado actual (título, línea pequeña y si suena) y repintado de todos los widgets. */
        fun push(ctx: Context, newTitle: String, newSub: String, isPlaying: Boolean) {
            title = newTitle
            sub = newSub
            playing = isPlaying
            refresh(ctx)
        }

        /** Vuelve al estado de reposo (app cerrada o sin nada sonando). */
        fun reset(ctx: Context) = push(ctx, "CASTV", "Abre la app para reproducir", false)

        private fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, CastvWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(ctx)
                for (id in ids) mgr.updateAppWidget(id, rv)
            } catch (e: Exception) { }
        }

        private fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_castv)
            rv.setTextViewText(R.id.wdgTitle, title)
            rv.setTextViewText(R.id.wdgSub, sub)
            rv.setImageViewResource(R.id.wdgToggle, if (playing) R.drawable.wdg_pause else R.drawable.wdg_play)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            fun act(action: String, req: Int) =
                PendingIntent.getBroadcast(ctx, req, Intent(action).setPackage(ctx.packageName), flags)
            rv.setOnClickPendingIntent(R.id.wdgPrev, act(MainActivity.ACT_PREV, 1))
            rv.setOnClickPendingIntent(R.id.wdgToggle, act(MainActivity.ACT_TOGGLE, 2))
            rv.setOnClickPendingIntent(R.id.wdgNext, act(MainActivity.ACT_NEXT, 3))
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags
            )
            rv.setOnClickPendingIntent(R.id.wdgInfo, open)
            return rv
        }
    }
}
