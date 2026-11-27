package com.castv.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * [FAV-WIDGET-1] Widget de inicio con los primeros 4 canales favoritos (★). Tocar uno lo abre con el mismo camino que
 * el aviso de un programa (`remind_url` / `remind_title` en MainActivity): al TV si la salida es TV, si no en el teléfono.
 * Lee los favoritos directamente de Store (no copia enlaces a ningún otro sitio). Se repinta al salir de Canales y cada 30 min.
 */
class CastvFavsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { manager.updateAppWidget(id, build(context)) } catch (e: Exception) { }
        }
    }

    companion object {
        private val rows = intArrayOf(R.id.wfav1, R.id.wfav2, R.id.wfav3, R.id.wfav4)

        /** Repinta todos los widgets de favoritos (se llama al salir de Canales, donde se cambian las ★). */
        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, CastvFavsWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(ctx)
                for (id in ids) mgr.updateAppWidget(id, rv)
            } catch (e: Exception) { }
        }

        private fun build(ctx: Context): RemoteViews {
            Store.init(ctx)
            val favs = try { Store.chFavChannels() } catch (e: Exception) { emptyList() }
            val rv = RemoteViews(ctx.packageName, R.layout.widget_favs)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            fun open(req: Int, extra: Intent.() -> Unit = {}) = PendingIntent.getActivity(
                ctx, req,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .apply(extra),
                flags
            )
            rv.setOnClickPendingIntent(R.id.wfavTitle, open(100))
            for (i in rows.indices) {
                val c = favs.getOrNull(i)
                if (c != null) {
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(rows[i], "▶ " + c.name.ifBlank { "Canal" })
                    rv.setOnClickPendingIntent(rows[i], open(101 + i) {
                        putExtra("remind_url", c.url)
                        putExtra("remind_title", c.name.ifBlank { "Canal" })
                    })
                } else if (i == 0) {
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(rows[i], "Sin favoritos · abre CASTV")
                    rv.setOnClickPendingIntent(rows[i], open(101))
                } else {
                    rv.setViewVisibility(rows[i], View.INVISIBLE)
                }
            }
            return rv
        }
    }
}
