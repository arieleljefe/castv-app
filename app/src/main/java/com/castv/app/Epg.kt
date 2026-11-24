package com.castv.app

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * [EPG-1] Guía de programación (XMLTV): qué dan «ahora» y «después» en cada canal de la lista.
 *
 * - La fuente es, en este orden: la URL que Ariel pegó a mano para esa lista, el acceso Xtream (`xmltv.php`, armado
 *   solo al descargar) o el `url-tvg` de la cabecera de la lista M3U.
 * - Se lee en flujo (no se carga el XML entero), acepta `.gz`, y solo se guardan los canales de la lista y las
 *   próximas 24 horas. El resultado queda en `cacheDir` (no entra en respaldos) y se refresca cada 12 horas.
 * - Emparejado: primero `tvg-id` del canal con el `id` de la guía; si falta, el nombre normalizado ([key]).
 * - Apagada por defecto (clave `epg_on` en las preferencias `castv`). La URL manual vive en `castv_epg`, que NO está
 *   en la lista de archivos de [Backup].
 */
object Epg {
    class Prog(val start: Long, val stop: Long, val title: String)
    private class Guide(val progs: Map<String, List<Prog>>, val names: Map<String, String>, val at: Long)

    private const val PREFS = "castv_epg"
    private const val FRESH_MS = 12L * 3600_000L
    private const val HORIZON_MS = 24L * 3600_000L
    private const val MAX_PROGS = 150_000
    private const val MAX_BYTES = 250L * 1024 * 1024
    private const val MAX_URLS = 6   // [EPG-1b] antes 3

    @Volatile private var guide: Guide? = null

    /** De dónde salió la guía de la última carga (para el recuadro 📅). */
    @Volatile var lastSource: String = ""
    /** Qué pasó en la última carga (para el recuadro 📅 y el aviso). */
    @Volatile var lastResult: String = ""

    fun enabled(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences("castv", Context.MODE_PRIVATE).getBoolean("epg_on", false)

    fun clear() { guide = null }

    fun info(): String {
        val g = guide ?: return "Sin guía cargada."
        return "${g.progs.size} canales con programación (actualizada a las ${hhmm(g.at)})."
    }

    // ------------------------------------------------------------ URL manual por lista

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun manualUrl(ctx: Context, listUrl: String): String = sp(ctx).getString("m_" + listUrl.hashCode(), "") ?: ""

    fun setManualUrl(ctx: Context, listUrl: String, url: String) {
        val e = sp(ctx).edit()
        if (url.isBlank()) e.remove("m_" + listUrl.hashCode()) else e.putString("m_" + listUrl.hashCode(), url.trim())
        e.apply()
    }

    /** Al quitar una lista: se borra su URL manual y su copia de la guía. */
    fun forget(ctx: Context, listUrl: String) {
        setManualUrl(ctx, listUrl, "")
        try { File(ctx.cacheDir, "epg_" + listUrl.hashCode() + ".txt").delete() } catch (e: Exception) { }
    }

    /** Enlaces de guía de una lista. [m3uText] es el texto de la lista (para su `url-tvg`); puede ser null. */
    fun sources(ctx: Context, listUrl: String, m3uText: String?): List<String> {
        val man = manualUrl(ctx, listUrl)
        if (man.isNotEmpty()) { lastSource = "la URL que pegaste"; return listOf(man) }
        if (Xtream.isXtream(listUrl)) {
            val x = Xtream.epgUrl(ctx, listUrl)
            lastSource = "tu proveedor Xtream"
            return if (x == null) emptyList() else listOf(x)
        }
        val h = if (m3uText != null) M3uParser.epgUrls(m3uText, 300) else emptyList()   // [EPG-1b] todas; se eligen al cargar
        lastSource = if (h.isEmpty()) "ninguna (la lista no trae guía)" else "la cabecera de la lista"
        return h
    }

    /** [EPG-REGION-1] Guías de «toda la región»: las cabeceras (`url-tvg`) de todas sus listas, sin repetidas. */
    fun regionSources(heads: List<List<String>>): List<String> {
        val h = M3uParser.mergeGuides(heads)
        lastSource = if (h.isEmpty()) "ninguna (las listas de la región no traen guía)" else "las cabeceras de las listas de la región"
        return h
    }

    // ------------------------------------------------------------ Cargar

    /**
     * Carga la guía de la lista [listUrl]. Llamar desde un hilo aparte. Con [force] no se usa la copia reciente.
     * Sin [allowNet] (datos móviles) solo se usa la copia guardada. [stillCurrent] evita publicar la guía de una
     * lista que ya no es la que se ve. Devuelve el mismo texto que queda en [lastResult].
     */
    fun load(ctx: Context, listUrl: String, urls: List<String>, channels: List<M3uChannel>,
             force: Boolean, allowNet: Boolean, stillCurrent: () -> Boolean): String {
        val res = loadInner(ctx, listUrl, urls, channels, force, allowNet, stillCurrent)
        lastResult = res
        DebugLog.add("EPG: $res")
        return res
    }

    private fun loadInner(ctx: Context, listUrl: String, urls: List<String>, channels: List<M3uChannel>,
                          force: Boolean, allowNet: Boolean, stillCurrent: () -> Boolean): String {
        val cache = File(ctx.cacheDir, "epg_" + listUrl.hashCode() + ".txt")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < FRESH_MS
        if (!force && fresh) {
            val g = readCache(cache)
            if (g != null) { if (stillCurrent()) guide = g; return "copia guardada (menos de 12 h)" }
        }
        if (urls.isEmpty()) {
            val g = if (cache.exists()) readCache(cache) else null
            if (g != null && stillCurrent()) guide = g
            return "esta lista no trae guía: pega una URL XMLTV en 📅"
        }
        if (!allowNet) {
            val g = if (cache.exists()) readCache(cache) else null
            if (g != null && stillCurrent()) guide = g
            return "con datos móviles no se descarga sola: usa «Actualizar ya» en 📅"
        }
        val wantIds = HashSet<String>()
        val wantNames = HashSet<String>()
        for (c in channels) {
            if (c.tvgId.isNotEmpty()) wantIds.add(c.tvgId.lowercase())
            val k = key(c.name)
            if (k.isNotEmpty()) wantNames.add(k)
        }
        val progs = HashMap<String, ArrayList<Prog>>()
        val names = HashMap<String, String>()
        var err: String? = null
        // [EPG-1b] con muchas guías en la cabecera, solo las de los países de los canales
        val use = M3uParser.pickGuides(urls, channels, MAX_URLS).take(MAX_URLS)
        if (use.size < urls.size) {
            DebugLog.add("EPG: guías elegidas (${use.size} de ${urls.size}): " + use.joinToString { M3uParser.guideName(it) })
        }
        for (u in use) {
            try {
                fetch(u, wantIds, wantNames, progs, names)
            } catch (e: Exception) {
                err = e.javaClass.simpleName + ": " + (e.message ?: "")
                DebugLog.add("EPG: no se pudo leer la guía $u: $err")
            }
        }
        if (progs.isEmpty()) {
            val g = if (cache.exists()) readCache(cache) else null
            if (g != null && stillCurrent()) guide = g
            return if (err != null) "no se pudo descargar la guía ($err)" else "la guía no trae programas para los canales de esta lista"
        }
        val sorted = HashMap<String, List<Prog>>()
        for ((k, v) in progs) sorted[k] = v.sortedBy { it.start }
        val g = Guide(sorted, names, System.currentTimeMillis())
        if (stillCurrent()) guide = g
        writeCache(cache, g)
        return "guía descargada: ${sorted.size} canales con programación"
    }

    // ------------------------------------------------------------ Descarga y lectura del XML

    private class LimitStream(private val ins: InputStream, private val max: Long) : InputStream() {
        private var n = 0L
        override fun read(): Int {
            val r = ins.read()
            if (r >= 0 && ++n > max) throw Exception("guía demasiado grande")
            return r
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = ins.read(b, off, len)
            if (r > 0) { n += r; if (n > max) throw Exception("guía demasiado grande") }
            return r
        }
        override fun close() { ins.close() }
    }

    private fun fetch(url: String, wantIds: Set<String>, wantNames: Set<String>,
                      progs: HashMap<String, ArrayList<Prog>>, names: HashMap<String, String>) {
        var cur = url
        for (hop in 0..5) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw Exception("redirección sin destino")
                cur = URL(URL(cur), loc).toString()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw Exception("HTTP $code")
            }
            try {
                val bin = BufferedInputStream(c.inputStream, 65536)
                bin.mark(4)
                val b0 = bin.read()
                val b1 = bin.read()
                bin.reset()
                val raw: InputStream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(bin, 65536) else bin
                parse(LimitStream(raw, MAX_BYTES), wantIds, wantNames, progs, names)
            } finally {
                c.disconnect()
            }
            return
        }
        throw Exception("demasiadas redirecciones")
    }

    private fun parse(ins: InputStream, wantIds: Set<String>, wantNames: Set<String>,
                      progs: HashMap<String, ArrayList<Prog>>, names: HashMap<String, String>) {
        val now = System.currentTimeMillis()
        val p = Xml.newPullParser()
        p.setInput(ins, null)
        val keepIds = HashSet<String>()
        var inChan = false
        var chanId = ""
        val dn = ArrayList<String>()
        var inProg = false
        var pid = ""
        var pStart = -1L
        var pStop = -1L
        var pTitle: String? = null
        var total = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> {
                        inChan = true
                        chanId = (p.getAttributeValue(null, "id") ?: "").trim().lowercase()
                        dn.clear()
                    }
                    "display-name" -> if (inChan) dn.add(p.nextText())
                    "programme" -> {
                        pid = (p.getAttributeValue(null, "channel") ?: "").trim().lowercase()
                        inProg = pid.isNotEmpty() && (keepIds.contains(pid) || wantIds.contains(pid))
                        if (inProg) {
                            pStart = parseTime(p.getAttributeValue(null, "start"))
                            pStop = parseTime(p.getAttributeValue(null, "stop"))
                            pTitle = null
                        }
                    }
                    "title" -> if (inProg && pTitle == null) pTitle = p.nextText()
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "channel" -> {
                        if (inChan && chanId.isNotEmpty()) {
                            val byName = dn.any { wantNames.contains(key(it)) }
                            if (wantIds.contains(chanId) || byName) {
                                keepIds.add(chanId)
                                for (t in dn) {
                                    val k = key(t)
                                    if (k.isNotEmpty() && wantNames.contains(k) && !names.containsKey(k)) names[k] = chanId
                                }
                            }
                        }
                        inChan = false
                    }
                    "programme" -> {
                        if (inProg && pStart >= 0 && pStop > pStart && pStop > now &&
                            pStart < now + HORIZON_MS && total < MAX_PROGS) {
                            val t = (pTitle ?: "").replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
                            if (t.isNotEmpty()) {
                                progs.getOrPut(pid) { ArrayList() }.add(Prog(pStart, pStop, t.take(90)))
                                total++
                            }
                        }
                        inProg = false
                    }
                }
            }
            ev = p.next()
        }
    }

    /** `20261124180000 +0100` → milisegundos (sin zona se toma UTC, como pide XMLTV). -1 si no se entiende. */
    private fun parseTime(s: String?): Long {
        if (s == null) return -1L
        return try {
            val t = s.trim()
            if (t.length < 12) return -1L
            val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(t.substring(0, 4).toInt(), t.substring(4, 6).toInt() - 1, t.substring(6, 8).toInt(),
                t.substring(8, 10).toInt(), t.substring(10, 12).toInt(),
                if (t.length >= 14) t.substring(12, 14).toInt() else 0)
            var ms = cal.timeInMillis
            val rest = t.substring(if (t.length >= 14) 14 else 12).trim()
            if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
                val off = rest.substring(1, 3).toInt() * 60 + rest.substring(3, 5).toInt()
                ms -= (if (rest[0] == '+') 1 else -1) * off * 60_000L
            }
            ms
        } catch (e: Exception) { -1L }
    }

    // ------------------------------------------------------------ Copia guardada

    private fun writeCache(f: File, g: Guide) {
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                for ((k, id) in g.names) w.write("N\t$k\t$id\n")
                for ((id, l) in g.progs) for (p in l) w.write("P\t$id\t${p.start}\t${p.stop}\t${p.title}\n")
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) {
            DebugLog.add("EPG: no se pudo guardar la copia: ${e.javaClass.simpleName}")
        }
    }

    private fun readCache(f: File): Guide? {
        return try {
            val now = System.currentTimeMillis()
            val progs = HashMap<String, ArrayList<Prog>>()
            val names = HashMap<String, String>()
            f.bufferedReader(Charsets.UTF_8).useLines { seq ->
                for (line in seq) {
                    val a = line.split('\t')
                    if (a.size >= 3 && a[0] == "N") names[a[1]] = a[2]
                    else if (a.size >= 5 && a[0] == "P") {
                        val st = a[2].toLong()
                        val sp = a[3].toLong()
                        if (sp > now) progs.getOrPut(a[1]) { ArrayList() }.add(Prog(st, sp, a[4]))
                    }
                }
            }
            if (progs.isEmpty()) return null
            val sorted = HashMap<String, List<Prog>>()
            for ((k, v) in progs) sorted[k] = v.sortedBy { it.start }
            Guide(sorted, names, f.lastModified())
        } catch (e: Exception) { null }
    }

    // ------------------------------------------------------------ Consultar

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val quality = Regex("\\b(uhd|fhd|hd|sd|4k|hevc|h265|\\d{3,4}p)\\b")
    private val nonAlnum = Regex("[^a-z0-9]+")

    /** Nombre de canal comparable: minúsculas, sin tildes, sin «HD/FHD/4K/1080p» y sin signos ni espacios. */
    fun key(name: String): String {
        var s = Normalizer.normalize(name, Normalizer.Form.NFD).replace(diacritics, "").lowercase()
        s = s.replace(quality, " ")
        return s.replace(nonAlnum, "")
    }

    /** Programa de ahora y el siguiente de un canal; null si no hay guía para él. */
    fun nowNext(c: M3uChannel): Pair<Prog?, Prog?>? {
        val g = guide ?: return null
        val tid = c.tvgId.lowercase()
        val id = (if (tid.isNotEmpty() && g.progs.containsKey(tid)) tid else g.names[key(c.name)]) ?: return null
        val l = g.progs[id] ?: return null
        val now = System.currentTimeMillis()
        var cur: Prog? = null
        var nxt: Prog? = null
        for (p in l) {
            if (p.stop <= now) continue
            if (p.start <= now) { if (cur == null) cur = p }
            else if (nxt == null) nxt = p
            if (cur != null && nxt != null) break
        }
        return if (cur == null && nxt == null) null else Pair(cur, nxt)
    }

    /** «▶ Programa · hasta 21:30» para la lista de canales; null si no hay programa en curso. */
    fun nowLine(c: M3uChannel): String? {
        val cur = nowNext(c)?.first ?: return null
        return "▶ ${cur.title} · hasta ${hhmm(cur.stop)}"
    }

    /** Solo el título del programa en curso (tarjetas pequeñas). */
    fun nowTitle(c: M3uChannel): String? = nowNext(c)?.first?.title

    fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
