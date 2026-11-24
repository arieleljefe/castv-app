package com.castv.app

import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** [ZAP-1] Cambiar de canal con ⏮ ⏭ mientras un canal de una lista M3U está en el TV.
 *  Guarda en memoria la lista de canales que se veía al mandar el canal (con búsqueda, grupo, ánimo y ocultos
 *  ya aplicados) y la posición actual. No toca el servidor ni lo ya guardado en disco. */
object Zap {
    private class Ctx(val list: List<M3uChannel>, val index: Int)

    @Volatile private var active: Ctx? = null    // la lista del canal que está en el TV
    @Volatile private var pending: Ctx? = null   // la lista del último canal enviado, a la espera de que el TV lo acepte
    @Volatile private var busy = false

    // ------------------------------------------------- [ZAP-2] ⏮ ⏭ cuando el canal suena en el teléfono
    // La lista va por memoria (no por Intent: una lista grande pasaría el límite de ~1 MB) y se entrega una sola vez.

    @Volatile private var phoneList: List<M3uChannel>? = null

    /** Se llama al abrir el canal [url] en el teléfono, con [shown] como lista visible. */
    fun rememberPhone(shown: List<M3uChannel>, url: String) {
        phoneList = if (shown.size > 1 && shown.any { it.url == url }) ArrayList(shown) else null
    }

    /** El reproductor del teléfono recoge la lista (y se borra, para no pegarla a otra reproducción). null si no hay. */
    fun takePhoneChannels(url: String?): List<M3uChannel>? {
        val l = phoneList
        phoneList = null
        return if (l != null && url != null && l.size > 1 && l.any { it.url == url }) l else null
    }

    /** Se llama al mandar [current] al TV con [shown] como lista visible. */
    fun remember(shown: List<M3uChannel>, current: M3uChannel) {
        val i = shown.indexOfFirst { it.url == current.url }
        pending = if (i >= 0 && shown.size > 1) Ctx(ArrayList(shown), i) else null
    }

    /** Se llama cuando el TV acepta un canal ([title]): si es el último enviado desde una lista, esa lista pasa a ser la activa. */
    fun confirm(title: String) {
        val p = pending ?: return
        if (p.list[p.index].name == title) { active = p; pending = null }
    }

    /** true si lo que suena en el TV es el canal de la lista activa y hay a dónde cambiar. */
    fun matches(title: String?): Boolean {
        val a = active ?: return false
        return title != null && a.list.size > 1 && a.list[a.index].name == title
    }

    /** Cambia al canal vecino ([delta] = +1 siguiente, -1 anterior; da la vuelta). Si un canal no se puede preparar,
     *  prueba el siguiente (máx. 5). [onResult] se llama desde un hilo de fondo: canal nuevo (o null) y un mensaje. */
    fun zap(ctx: Context, delta: Int, onResult: (M3uChannel?, String) -> Unit) {
        val a = active ?: return
        if (busy) return
        busy = true
        thread {
            try {
                var i = a.index
                var tries = 0
                while (tries < 5) {
                    tries++
                    i = Math.floorMod(i + delta, a.list.size)
                    if (i == a.index) break
                    val c = a.list[i]
                    val why = send(ctx, c)
                    if (why == null) {
                        active = Ctx(a.list, i)
                        pending = null
                        onResult(c, "📺 ${c.name}")
                        return@thread
                    }
                    DebugLog.add("ZAP: ${c.name}: $why")
                    onResult(null, "⚠️ ${c.name} no se puede preparar, probando el siguiente")
                }
                onResult(null, "❌ No pude cambiar de canal")
            } finally {
                busy = false
            }
        }
    }

    /** Manda [c] al TV con la misma lógica que M3uActivity.sendToTv. null = listo; si no, el motivo. */
    private fun send(ctx: Context, c: M3uChannel): String? {
        if (TvController.tvIp == null) return "TV no encontrado todavía"
        val hls = c.url.substringBefore('?').lowercase().endsWith(".m3u8")
        val phoneIp = CastvNet.localIpAddress(TvController.tvIp)
        val live = hls && phoneIp != null
        if (live) {
            val why = MediaServer.probeLive(c.url)
            if (why != null) { Store.markChFail(c.url); return why }   // [FUENTES-1]
        }
        ctx.sendBroadcast(Intent(MainActivity.ACT_STOP_LOCAL).setPackage(ctx.packageName))   // [LOCAL-1]
        val mime = if (live) "video/mp2t" else if (hls) "application/vnd.apple.mpegurl" else "video/mp4"
        val sendUrl = if (phoneIp == null) c.url else if (live) TvSend.liveUrl(phoneIp, c.url) else TvSend.relayUrl(phoneIp, c.url, hls)
        DebugLog.add("ZAP: ${c.url} -> $sendUrl")
        if (!TvController.setUriAndPlay(sendUrl, c.name, mime, false)) return "El TV no aceptó el canal"
        Store.markChOk(c.url)   // [FUENTES-1]
        ctx.sendBroadcast(Intent(MainActivity.ACT_CHANNEL_ON_TV).setPackage(ctx.packageName)
            .putExtra("title", c.name).putExtra("live", live).putExtra("url", c.url))   // [NOW-1] [FAV-REPRO-3]
        return null
    }
}
