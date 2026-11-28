package com.castv.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Datos del usuario que sobreviven al cerrar la app: favoritos, progreso de reproducción
 * y la opción de ocultar duplicados.
 *
 * La clave se arma con tipo + nombre + duración (no con el _ID de MediaStore, que puede
 * cambiar si el sistema reindexa los archivos).
 */
/** Enlace guardado en el historial de "Reproducir enlace". */
data class SavedLink(val url: String, val title: String, val fav: Boolean, val t: Long)

/** [MARCAS-1] Marcador guardado en un video: segundo y nombre (puede ir vacío). */
data class VideoMark(val sec: Int, val name: String)

/** Lista M3U guardada (canales de TV por internet). */
data class M3uList(val name: String, val url: String)

object Store {
    private lateinit var prefs: SharedPreferences
    private lateinit var resumePrefs: SharedPreferences
    private lateinit var statsPrefs: SharedPreferences
    private lateinit var badPrefs: SharedPreferences   // [ATOMIC-1] copias de texto ilegible
    private lateinit var topPrefs: SharedPreferences   // [TOP-CANALES-1] veces que se abrió cada canal (fuera de respaldos)
    private var favs: MutableSet<String> = HashSet()
    private var lists: LinkedHashMap<String, MutableList<String>> = LinkedHashMap()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)
        resumePrefs = app.getSharedPreferences("castv_resume", Context.MODE_PRIVATE)
        statsPrefs = app.getSharedPreferences("castv_stats", Context.MODE_PRIVATE)
        badPrefs = app.getSharedPreferences("castv_bad", Context.MODE_PRIVATE)
        topPrefs = app.getSharedPreferences("castv_top", Context.MODE_PRIVATE)
        favs = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        loadLists()
    }

    /** Vuelve a leer favoritos y listas desde las preferencias (se usa tras importar un respaldo). */
    @Synchronized
    fun reload() {
        if (!::prefs.isInitialized) return
        favs = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        loadLists()
    }

    fun key(item: MediaItem): String =
        (if (item.isMusic) "a" else "v") + "|" + item.name.lowercase() + "|" + item.durationSec

    /**
     * [ATOMIC-1] Si un dato guardado no se puede leer (JSON dañado), se guarda aparte una copia del texto
     * original (archivo "castv_bad", fuera del respaldo) antes de que una escritura posterior lo pise.
     * Solo se vuelve a guardar si el texto ilegible es distinto del que ya se copió.
     */
    private fun rescue(name: String, raw: String?, e: Exception) {
        if (raw == null) return
        try {
            if (::badPrefs.isInitialized && badPrefs.getString(name, null) != raw) {
                badPrefs.edit().putString(name, raw).putLong("t|" + name, System.currentTimeMillis()).apply()
                DebugLog.add("STORE: no pude leer '" + name + "' (" + e.javaClass.simpleName + "), copia guardada")
            }
        } catch (x: Exception) { }
    }

    // ------------------------------------------------------------ Favoritos

    @Synchronized
    fun isFav(item: MediaItem): Boolean = favs.contains(key(item))

    /** Devuelve true si quedó como favorito. */
    @Synchronized
    fun toggleFav(item: MediaItem): Boolean {
        val k = key(item)
        val now = if (favs.contains(k)) { favs.remove(k); false } else { favs.add(k); true }
        prefs.edit().putStringSet("fav", HashSet(favs)).apply()
        return now
    }

    // ------------------------------------------------- [INCOG-1] Modo incógnito

    /** Con el modo incógnito activado no se guardan puntos de reanudación, reproducciones, historial de enlaces ni "visto". */
    var incognito: Boolean
        get() = prefs.getBoolean("incog", false)
        set(v) { prefs.edit().putBoolean("incog", v).apply() }

    // ----------------------------------------------------- Continuar donde lo dejé

    fun resumeAt(item: MediaItem): Int = resumePrefs.getInt(key(item), 0)

    fun saveResume(item: MediaItem, sec: Int) {
        if (incognito) return   // [INCOG-1]
        resumePrefs.edit().putInt(key(item), sec).apply()
    }

    fun clearResume(item: MediaItem) {
        val k = key(item)
        if (resumePrefs.contains(k)) resumePrefs.edit().remove(k).apply()
    }

    /** [AJUSTES-2b] Cuántos puntos guardados hay en "Continuar donde lo dejé". */
    fun resumeCount(): Int = resumePrefs.all.size

    /** [AJUSTES-2b] Borra todos los puntos guardados de "Continuar donde lo dejé". */
    fun clearAllResume() {
        resumePrefs.edit().clear().apply()
    }

    /** Borra todo rastro de un archivo eliminado: favoritos, listas, punto guardado y estadísticas. */
    @Synchronized
    fun forget(item: MediaItem) {
        val k = key(item)
        if (favs.remove(k)) prefs.edit().putStringSet("fav", HashSet(favs)).apply()
        var changed = false
        for (l in lists.values) if (l.remove(k)) changed = true
        if (changed) saveLists()
        resumePrefs.edit().remove(k).apply()
        statsPrefs.edit().remove("c|$k").remove("t|$k").remove("v|$k").remove("w|$k").apply()
        if (prefs.contains("bm|$k")) prefs.edit().remove("bm|$k").apply()   // [MARCAS-1]
    }

    /** [RENOMBRAR-1] Clave que tendría [item] con otro nombre (misma regla que [key]). */
    fun keyFor(item: MediaItem, newName: String): String =
        (if (item.isMusic) "a" else "v") + "|" + newName.lowercase() + "|" + item.durationSec

    /**
     * [RENOMBRAR-1] Un archivo cambió de nombre: pasa a la clave nueva todo lo que estaba guardado con la vieja (favorito, listas,
     * punto de «continuar», vistas y reproducciones, tiempo visto, marcadores y reproducciones por mes). Si la clave nueva ya
     * tenía algo, se conserva lo que ya había.
     */
    @Synchronized
    fun renameKey(oldK: String, newK: String) {
        if (oldK == newK) return
        if (favs.remove(oldK)) { favs.add(newK); prefs.edit().putStringSet("fav", HashSet(favs)).apply() }
        var changed = false
        for (l in lists.values) {
            val i = l.indexOf(oldK)
            if (i < 0) continue
            if (l.contains(newK)) l.removeAt(i) else l[i] = newK
            changed = true
        }
        if (changed) saveLists()
        if (resumePrefs.contains(oldK)) {
            val v = resumePrefs.getInt(oldK, 0)
            val ed = resumePrefs.edit().remove(oldK)
            if (!resumePrefs.contains(newK)) ed.putInt(newK, v)
            ed.apply()
        }
        val all = statsPrefs.all
        val se = statsPrefs.edit()
        for (pre in arrayOf("c|", "t|", "v|", "w|")) {
            val ov = all[pre + oldK] ?: continue
            se.remove(pre + oldK)
            if (all.containsKey(pre + newK)) continue
            when (ov) {
                is Int -> se.putInt(pre + newK, ov)
                is Long -> se.putLong(pre + newK, ov)
                is Boolean -> se.putBoolean(pre + newK, ov)
                is String -> se.putString(pre + newK, ov)
                else -> { }
            }
        }
        se.apply()
        val bm = prefs.getString("bm|$oldK", null)
        if (bm != null) {
            val e = prefs.edit().remove("bm|$oldK")
            if (!prefs.contains("bm|$newK")) e.putString("bm|$newK", bm)
            e.apply()
        }
        if (::topPrefs.isInitialized) {
            try {
                val data = monthData()
                var ch = false
                for (h in data.values) {
                    val c = h.remove(oldK) ?: continue
                    h[newK] = (h[newK] ?: 0) + c
                    ch = true
                }
                if (ch) {
                    val o = JSONObject()
                    for ((m, mh) in data) {
                        val mo = JSONObject()
                        for ((kk, v) in mh) mo.put(kk, v)
                        o.put(m, mo)
                    }
                    topPrefs.edit().putString("mp", o.toString()).apply()
                }
            } catch (e: Exception) { }
        }
    }

    // ------------------------------------------------- [VISTO-1] Marca "visto"

    fun isSeen(item: MediaItem): Boolean = statsPrefs.getBoolean("v|" + key(item), false)

    fun markSeen(item: MediaItem, seen: Boolean) {
        val k = "v|" + key(item)
        if (seen) statsPrefs.edit().putBoolean(k, true).apply()
        else if (statsPrefs.contains(k)) statsPrefs.edit().remove(k).apply()
    }

    // ------------------------------------------------- [MARCAS-1] Marcadores de un video
    // Se guardan en castv_lib (clave bm|<clave del video>), así que viajan en el respaldo.

    private const val MAX_MARKS = 50

    fun marks(item: MediaItem): List<VideoMark> {
        val raw = prefs.getString("bm|" + key(item), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VideoMark(o.getInt("t"), o.optString("n", ""))
            }.sortedBy { it.sec }
        } catch (e: Exception) { rescue("bm|" + key(item), raw, e); emptyList() }
    }

    private fun saveMarks(item: MediaItem, list: List<VideoMark>) {
        val k = "bm|" + key(item)
        if (list.isEmpty()) { prefs.edit().remove(k).apply(); return }
        val arr = JSONArray()
        for (m in list) arr.put(JSONObject().put("t", m.sec).put("n", m.name))
        prefs.edit().putString(k, arr.toString()).apply()
    }

    /** Devuelve false si no se guardó (modo incógnito o demasiados marcadores). */
    @Synchronized
    fun addMark(item: MediaItem, sec: Int, name: String): Boolean {
        if (incognito) return false
        val list = marks(item).filter { it.sec != sec }
        if (list.size >= MAX_MARKS) return false
        saveMarks(item, list + VideoMark(sec, name))
        return true
    }

    @Synchronized
    fun removeMark(item: MediaItem, sec: Int) {
        saveMarks(item, marks(item).filter { it.sec != sec })
    }

    // ------------------------------------------------------------ Duplicados

    var hideDups: Boolean
        get() = prefs.getBoolean("hideDups", true)
        set(v) { prefs.edit().putBoolean("hideDups", v).apply() }

    // ------------------------------------------------- Estadísticas de uso

    fun playCount(item: MediaItem): Int = statsPrefs.getInt("c|" + key(item), 0)

    fun lastPlayed(item: MediaItem): Long = statsPrefs.getLong("t|" + key(item), 0L)

    fun registerPlay(item: MediaItem) {
        if (incognito) return   // [INCOG-1]
        val k = key(item)
        statsPrefs.edit()
            .putInt("c|$k", statsPrefs.getInt("c|$k", 0) + 1)
            .putLong("t|$k", System.currentTimeMillis())
            .apply()
        touchDay()   // [RESUMEN-1]
        bumpMonth(k)   // [MES-1]
    }

    // ------------------------------------------------- [MES-1] Reproducciones por mes
    // Clave "mp" en castv_top ({"2026-11": {clave del archivo: veces}}): últimos 12 meses, fuera de respaldos. registerPlay ya
    // descarta el modo incógnito. "mp_since" = desde cuándo se registra. Se lee una vez y se guarda en memoria (el orden la consulta muchas veces).

    private var mpCache: HashMap<String, HashMap<String, Int>>? = null

    fun monthKey(ms: Long = System.currentTimeMillis()): String =
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(ms))

    @Synchronized
    private fun monthData(): HashMap<String, HashMap<String, Int>> {
        mpCache?.let { return it }
        val out = HashMap<String, HashMap<String, Int>>()
        if (::topPrefs.isInitialized) {
            val raw = topPrefs.getString("mp", null)
            if (raw != null) {
                try {
                    val o = JSONObject(raw)
                    for (m in o.keys()) {
                        val mo = o.getJSONObject(m)
                        val h = HashMap<String, Int>()
                        for (k in mo.keys()) h[k] = mo.getInt(k)
                        out[m] = h
                    }
                } catch (e: Exception) { rescue("mp", raw, e); out.clear() }
            }
        }
        mpCache = out
        return out
    }

    @Synchronized
    private fun bumpMonth(k: String) {
        if (incognito || !::topPrefs.isInitialized) return
        try {
            val data = monthData()
            val h = data.getOrPut(monthKey()) { HashMap() }
            h[k] = (h[k] ?: 0) + 1
            if (h.size > 600) {   // tope: se quedan las 500 más escuchadas del mes
                val keep = h.entries.sortedByDescending { it.value }.take(500).map { it.key }.toSet()
                h.keys.retainAll(keep)
            }
            for (old in data.keys.sortedDescending().drop(12)) data.remove(old)   // solo los últimos 12 meses
            val o = JSONObject()
            for ((m, mh) in data) {
                val mo = JSONObject()
                for ((kk, v) in mh) mo.put(kk, v)
                o.put(m, mo)
            }
            val ed = topPrefs.edit().putString("mp", o.toString())
            if (!topPrefs.contains("mp_since")) ed.putLong("mp_since", System.currentTimeMillis())
            ed.apply()
        } catch (e: Exception) { }
    }

    /** Veces que se reprodujo [item] en [month] («aaaa-mm»; por defecto, este mes). Solo cuenta desde [monthSince]. */
    fun monthPlays(item: MediaItem, month: String = monthKey()): Int = monthData()[month]?.get(key(item)) ?: 0

    /** Desde cuándo se registran las reproducciones por mes (0 = aún no hay ninguna). */
    fun monthSince(): Long = if (::topPrefs.isInitialized) topPrefs.getLong("mp_since", 0L) else 0L

    // ------------------------------------------------- [RESUMEN-1] Días con actividad (para la racha de «Tu resumen»)
    // Solo guarda qué días se abrió algo (números de día, últimos 60) en castv_top: fuera de respaldos; en incógnito no se cuenta.

    private fun dayIndex(ms: Long): Long = (ms + java.util.TimeZone.getDefault().getOffset(ms)) / 86_400_000L

    fun today(): Long = dayIndex(System.currentTimeMillis())

    fun activeDays(): Set<Long> {
        if (!::topPrefs.isInitialized) return emptySet()
        val raw = topPrefs.getString("days", "") ?: ""
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    @Synchronized
    private fun touchDay() {
        if (incognito || !::topPrefs.isInitialized) return
        try {
            val t = today()
            val days = activeDays()
            if (t in days) return
            topPrefs.edit().putString("days", (days + t).sortedDescending().take(60).joinToString(",")).apply()
        } catch (e: Exception) { }
    }

    /** Días seguidos con actividad hasta hoy (si hoy aún no hay, se cuenta hasta ayer). */
    fun streakDays(): Int {
        val d = activeDays()
        var day = today()
        if (day !in d) day -= 1
        var n = 0
        while (day in d) { n++; day-- }
        return n
    }

    // ------------------------------------------------- [TIEMPO-1] Tiempo total visto de un video

    /** Segundos que se ha estado reproduciendo este video (suma de todas las veces). */
    fun watchSec(item: MediaItem): Int = statsPrefs.getInt("w|" + key(item), 0)

    fun addWatch(item: MediaItem, sec: Int) {
        if (incognito || sec <= 0) return
        val k = "w|" + key(item)
        statsPrefs.edit().putInt(k, statsPrefs.getInt(k, 0) + sec).apply()
    }

    // ------------------------------------------------- Historial de enlaces

    private const val MAX_RECENT_LINKS = 20

    private fun readLinks(): MutableList<SavedLink> {
        val out = ArrayList<SavedLink>()
        val raw = prefs.getString("links", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(SavedLink(o.getString("u"), o.optString("n", ""), o.optBoolean("f", false), o.optLong("t", 0L)))
            }
        } catch (e: Exception) { rescue("links", raw, e) }
        return out
    }

    private fun writeLinks(l: List<SavedLink>) {
        try {
            val arr = JSONArray()
            for (x in l) arr.put(JSONObject().put("u", x.url).put("n", x.title).put("f", x.fav).put("t", x.t))
            prefs.edit().putString("links", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Favoritos primero y luego los más recientes. */
    fun links(): List<SavedLink> =
        readLinks().sortedWith(compareByDescending<SavedLink> { it.fav }.thenByDescending { it.t })

    /** Guarda (o sube al principio) un enlace. Los favoritos no se borran; de los demás quedan los 20 más recientes. */
    @Synchronized
    fun addLink(url: String, title: String) {
        if (incognito) return   // [INCOG-1]
        val all = readLinks()
        val old = all.firstOrNull { it.url == url }
        all.removeAll { it.url == url }
        all.add(SavedLink(url, title, old?.fav ?: false, System.currentTimeMillis()))
        val starred = all.filter { it.fav }
        val recent = all.filter { !it.fav }.sortedByDescending { it.t }.take(MAX_RECENT_LINKS)
        writeLinks(starred + recent)
    }

    /** Devuelve true si quedó como favorito. */
    @Synchronized
    fun toggleLinkFav(url: String): Boolean {
        var now = false
        val out = readLinks().map {
            if (it.url == url) { now = !it.fav; it.copy(fav = now) } else it
        }
        writeLinks(out)
        return now
    }

    @Synchronized
    fun removeLink(url: String) {
        writeLinks(readLinks().filter { it.url != url })
    }

    /** Borra los enlaces que no son favoritos. */
    @Synchronized
    fun clearRecentLinks() {
        writeLinks(readLinks().filter { it.fav })
    }

    // ------------------------------------------------- Listas M3U

    // ------------------------------------------------- [BUSQ-RECIENTES-1] Búsquedas recientes

    /** Las últimas 8 búsquedas de la biblioteca. Van en castv_top (fuera de respaldos); el modo incógnito no las guarda. */
    fun recentSearches(): List<String> {
        val raw = topPrefs.getString("recentsearch", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun addRecentSearch(q: String) {
        val t = q.trim()
        if (incognito || t.length < 2 || t.length > 60) return
        val out = ArrayList<String>()
        out.add(t)
        for (s in recentSearches()) if (!s.equals(t, ignoreCase = true) && out.size < 8) out.add(s)
        val arr = JSONArray()
        for (s in out) arr.put(s)
        topPrefs.edit().putString("recentsearch", arr.toString()).apply()
    }

    fun clearRecentSearches() {
        topPrefs.edit().remove("recentsearch").apply()
    }

    fun m3uLists(): List<M3uList> {
        val out = ArrayList<M3uList>()
        val raw = prefs.getString("m3u", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uList(o.optString("n", ""), o.getString("u")))
            }
        } catch (e: Exception) { rescue("m3u", raw, e) }
        return out
    }

    private fun writeM3u(l: List<M3uList>) {
        try {
            val arr = JSONArray()
            for (x in l) arr.put(JSONObject().put("n", x.name).put("u", x.url))
            prefs.edit().putString("m3u", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Añade la lista (si el enlace ya existía, se reemplaza el nombre). */
    @Synchronized
    fun addM3uList(name: String, url: String) {
        val l = m3uLists().filter { it.url != url }.toMutableList()
        l.add(M3uList(name, url))
        writeM3u(l)
    }

    @Synchronized
    fun removeM3uList(url: String) {
        writeM3u(m3uLists().filter { it.url != url })
    }

    /** Enlace de la última lista M3U abierta. */
    var lastM3u: String?
        get() = prefs.getString("m3uLast", null)
        set(v) { prefs.edit().putString("m3uLast", v).apply() }

    /** [FAV-INICIO-1] Si Canales abre directo en ★ Favs (clave nueva "favstart" en castv_lib; el respaldo la incluye sola). */
    var favStart: Boolean
        get() = prefs.getBoolean("favstart", false)
        set(v) { prefs.edit().putBoolean("favstart", v).apply() }

    /** [NUEVOS-DIAS-1] Cuántos días un favorito cuenta como «nuevo» (3, 7, 14 o 30; clave nueva "favnewdays" en castv_lib; el respaldo la incluye sola). */
    var favNewDays: Int
        get() = prefs.getInt("favnewdays", 7).let { if (it == 3 || it == 7 || it == 14 || it == 30) it else 7 }
        set(v) { prefs.edit().putInt("favnewdays", v).apply() }

    // ------------------------------------------------- Catálogo preinstalado (CATALOGO-1)

    /**
     * Añade, una sola vez por versión, las listas del catálogo que no estén ya guardadas.
     * No toca las listas del usuario y no vuelve a poner las que el usuario quitó después.
     * Devuelve cuántas listas añadió.
     */
    @Synchronized
    fun installCatalog(version: Int, items: List<M3uList>): Int {
        if (prefs.getInt("catV", 0) >= version) return 0
        val seen = HashSet<String>()
        try {
            val raw = prefs.getString("catSeen", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) seen.add(arr.getString(i))
            }
        } catch (e: Exception) { }
        val cur = m3uLists().toMutableList()
        val have = HashSet<String>()
        for (x in cur) have.add(x.url)
        var added = 0
        for (x in items) {
            if (!seen.contains(x.url) && !have.contains(x.url)) {
                cur.add(x)
                have.add(x.url)
                added++
            }
            seen.add(x.url)
        }
        if (added > 0) writeM3u(cur)
        try {
            val arr = JSONArray()
            for (u in seen) arr.put(u)
            prefs.edit().putInt("catV", version).putString("catSeen", arr.toString()).apply()
        } catch (e: Exception) { }
        return added
    }

    // ------------------------------------------------- Canales favoritos (M3U)

    /** Enlaces de los canales marcados con ★ en Listas M3U. */
    fun chFavs(): Set<String> {
        val out = HashSet<String>()
        val raw = prefs.getString("chfav", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Exception) { rescue("chfav", raw, e) }
        return out
    }

    /** Devuelve true si el canal quedó como favorito. */
    @Synchronized
    fun toggleChFav(url: String): Boolean {
        val s = chFavs().toMutableSet()
        val now = if (s.contains(url)) { s.remove(url); false } else { s.add(url); true }
        try {
            val arr = JSONArray()
            for (x in s) arr.put(x)
            prefs.edit().putString("chfav", arr.toString()).apply()
        } catch (e: Exception) { }
        return now
    }

    // [FAV-1] Nombre y grupo de los canales favoritos, para verlos desde cualquier lista.
    // Clave nueva "chfavmeta"; "chfav" (solo enlaces) no cambia.

    private fun readChFavMeta(): MutableList<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        val raw = prefs.getString("chfavmeta", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uChannel(o.optString("n", ""), o.getString("u"), o.optString("g", ""), o.optString("l", "")))
            }
        } catch (e: Exception) { rescue("chfavmeta", raw, e) }
        return out
    }

    private fun writeChFavMeta(l: List<M3uChannel>, groups: Map<String, String> = readChFavGroups(), dates: Map<String, Long> = readChFavDates()) {   // [FAV-GRUPOS-1] "fg" se conserva; [NUEVOS-FAV-1] "d" también
        try {
            val arr = JSONArray()
            for (c in l) {
                val o = JSONObject().put("u", c.url).put("n", c.name).put("g", c.group).put("l", c.logo)
                val fg = groups[c.url]
                if (!fg.isNullOrEmpty()) o.put("fg", fg)
                val d = dates[c.url]
                if (d != null && d > 0L) o.put("d", d)
                arr.put(o)
            }
            prefs.edit().putString("chfavmeta", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Todos los canales favoritos (de cualquier lista) con su nombre; los viejos sin nombre salen con el servidor. */
    fun chFavChannels(): List<M3uChannel> {
        val urls = chFavs()
        val out = ArrayList<M3uChannel>()
        val seen = HashSet<String>()
        for (c in readChFavMeta()) if (urls.contains(c.url) && seen.add(c.url)) out.add(c)
        for (u in urls) if (seen.add(u)) {
            val host = u.substringAfter("://").substringBefore('/')
            out.add(M3uChannel("Canal guardado · $host", u, ""))
        }
        return out
    }

    /** Guarda (o actualiza) el nombre y el grupo de un canal favorito. */
    @Synchronized
    fun rememberChFav(c: M3uChannel) {
        val l = readChFavMeta()
        val i = l.indexOfFirst { it.url == c.url }
        if (i >= 0) { if (l[i] == c) return; l[i] = c; writeChFavMeta(l); return }
        l.add(c)
        val dates = readChFavDates()   // [NUEVOS-FAV-1] favorito nuevo: se anota cuándo se añadió
        dates[c.url] = System.currentTimeMillis()
        writeChFavMeta(l, readChFavGroups(), dates)
    }

    /** [IMPORTAR-GRUPO-1] Agrega de una sola vez [list] a favoritos y los pone en el grupo [group] (una sola escritura). Los que ya eran favoritos no cambian ni de grupo. Devuelve cuántos eran nuevos. */
    @Synchronized
    fun addChFavsToGroup(list: List<M3uChannel>, group: String): Int {
        val g = cleanGroupName(group)
        val urls = chFavs().toMutableSet()
        val meta = readChFavMeta()
        val groups = readChFavGroups()
        val dates = readChFavDates()
        val now = System.currentTimeMillis()
        var added = 0
        for (c in list) {
            if (urls.contains(c.url)) continue
            urls.add(c.url)
            val i = meta.indexOfFirst { it.url == c.url }
            if (i >= 0) meta[i] = c else meta.add(c)
            if (g.isNotEmpty()) groups[c.url] = g
            dates[c.url] = now
            added++
        }
        if (added == 0) return 0
        try {
            val arr = JSONArray()
            for (x in urls) arr.put(x)
            prefs.edit().putString("chfav", arr.toString()).apply()
        } catch (e: Exception) { return 0 }
        writeChFavMeta(meta, groups, dates)
        return added
    }

    // [NUEVOS-FAV-1] Fecha (milisegundos) en que se añadió cada favorito: campo "d" dentro de "chfavmeta" (igual que "fg": no hay
    // clave nueva, así que el respaldo, compartir favoritos y el filtro de claves Xtream siguen igual). Los favoritos de antes no
    // tienen fecha y nunca salen como nuevos.

    private fun readChFavDates(): MutableMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val raw = prefs.getString("chfavmeta", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = o.optLong("d", 0L)
                if (d > 0L) out[o.getString("u")] = d
            }
        } catch (e: Exception) { }
        return out
    }

    /** Favoritos añadidos hace menos de [days] días: enlace → fecha de alta (ms). Solo los que siguen siendo favoritos. */
    fun chFavNew(days: Int = favNewDays): Map<String, Long> {
        val urls = chFavs()
        val since = System.currentTimeMillis() - days * 86_400_000L
        val out = LinkedHashMap<String, Long>()
        for ((u, d) in readChFavDates()) if (d >= since && urls.contains(u)) out[u] = d
        return out
    }

    // [DESHACER-UNIR-1] Foto de los favoritos de canales (enlaces, nombres, grupos, fechas, orden, notas y respaldos)
    // para devolverlos tal cual tras «Unir repetidos». Guarda los textos tal como están, sin interpretarlos.
    private val FAV_SNAP_KEYS = arrayOf("chfav", "chfavmeta", "chnotes", "chalts")

    @Synchronized
    fun snapshotChFavs(): Map<String, String?> {
        val m = LinkedHashMap<String, String?>()
        for (k in FAV_SNAP_KEYS) m[k] = prefs.getString(k, null)
        return m
    }

    @Synchronized
    fun restoreChFavs(snap: Map<String, String?>) {
        val e = prefs.edit()
        for (k in FAV_SNAP_KEYS) {
            val v = snap[k]
            if (v == null) e.remove(k) else e.putString(k, v)
        }
        e.apply()
    }

    /** Borra el nombre guardado de un canal que ya no es favorito. */
    @Synchronized
    fun forgetChFav(url: String) {
        val l = readChFavMeta()
        if (l.removeAll { it.url == url }) writeChFavMeta(l)
    }

    // [FAV-ORDEN-1] El orden de los favoritos es el de la lista guardada en "chfavmeta" (la clave no cambia).

    /** Pone el canal favorito [url] justo antes (o después, con [after]) del favorito [neighborUrl]. true si el orden cambió. */
    @Synchronized
    fun moveChFavNear(url: String, neighborUrl: String, after: Boolean): Boolean {
        if (url == neighborUrl) return false
        val l = ArrayList(chFavChannels())
        val i = l.indexOfFirst { it.url == url }
        if (i < 0) return false
        val c = l.removeAt(i)
        val n = l.indexOfFirst { it.url == neighborUrl }
        if (n < 0) return false
        l.add(if (after) n + 1 else n, c)
        writeChFavMeta(l)
        return true
    }

    /** Pone el canal favorito [url] el primero. true si el orden cambió. */
    @Synchronized
    fun moveChFavFirst(url: String): Boolean {
        val l = ArrayList(chFavChannels())
        val i = l.indexOfFirst { it.url == url }
        if (i <= 0) return false
        l.add(0, l.removeAt(i))
        writeChFavMeta(l)
        return true
    }

    // [FAV-GRUPOS-1] Grupo propio de cada favorito (campo "fg" dentro de "chfavmeta"; no hay clave nueva, así que respaldo,
    // compartir favoritos y el filtro de claves Xtream siguen igual). Un grupo existe mientras tenga algún canal.

    private fun readChFavGroups(): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val raw = prefs.getString("chfavmeta", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fg = o.optString("fg", "")
                if (fg.isNotEmpty()) out[o.getString("u")] = fg
            }
        } catch (e: Exception) { }
        return out
    }

    private fun cleanGroupName(s: String): String = s.replace(Regex("[\\r\\n\"]+"), " ").trim().take(30)

    /** Enlace del favorito → su grupo (solo los que tienen grupo y siguen siendo favoritos). */
    fun chFavGroupMap(): Map<String, String> {
        val urls = chFavs()
        return readChFavGroups().filterKeys { urls.contains(it) }
    }

    fun chFavGroup(url: String): String = chFavGroupMap()[url] ?: ""

    /** Pone el favorito [url] en el grupo [name] (vacío = quitarlo del grupo). true si cambió algo. */
    @Synchronized
    fun setChFavGroup(url: String, name: String): Boolean {
        val g = cleanGroupName(name)
        val groups = readChFavGroups()
        if ((groups[url] ?: "") == g) return false
        if (g.isEmpty()) groups.remove(url) else groups[url] = g
        writeChFavMeta(ArrayList(chFavChannels()), groups)
        return true
    }

    /** Cambia el nombre del grupo [old] a [new] (si [new] ya existe, se unen). Devuelve cuántos canales cambiaron. */
    @Synchronized
    fun renameChFavGroup(old: String, new: String): Int {
        val g = cleanGroupName(new)
        if (g.isEmpty() || g == old) return 0
        val groups = readChFavGroups()
        var n = 0
        for (k in groups.keys.toList()) if (groups[k] == old) { groups[k] = g; n++ }
        if (n > 0) {
            writeChFavMeta(ArrayList(chFavChannels()), groups)
            writeChFavGroupOrder(readChFavGroupOrder().map { if (it == old) g else it }.distinct())   // [GRUPOS-ORDEN-1]
        }
        return n
    }

    /** Quita el grupo [name]: sus canales siguen en favoritos, sin grupo. Devuelve cuántos eran. */
    @Synchronized
    fun deleteChFavGroup(name: String): Int {
        val groups = readChFavGroups()
        var n = 0
        for (k in groups.keys.toList()) if (groups[k] == name) { groups.remove(k); n++ }
        if (n > 0) writeChFavMeta(ArrayList(chFavChannels()), groups)
        return n
    }

    // [GRUPOS-ORDEN-1] Orden de los chips de grupos: clave nueva "chfavgrporder" (lista de nombres) en castv_lib, así que el respaldo completo la incluye sola.

    private fun readChFavGroupOrder(): List<String> {
        val raw = prefs.getString("chfavgrporder", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeChFavGroupOrder(l: List<String>) {
        try {
            val a = JSONArray()
            for (x in l) a.put(x)
            prefs.edit().putString("chfavgrporder", a.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Nombres de los grupos en el orden elegido; los que aún no tienen lugar van al final, por orden alfabético. */
    fun chFavGroupNames(): List<String> {
        val names = chFavGroupMap().values.toSet()
        val ordered = readChFavGroupOrder().filter { names.contains(it) }.distinct()
        return ordered + names.filter { !ordered.contains(it) }.sortedBy { it.lowercase() }
    }

    /** Mueve el grupo [name] una posición ([delta] = -1 antes, +1 después). true si cambió. */
    @Synchronized
    fun moveChFavGroup(name: String, delta: Int): Boolean {
        val l = chFavGroupNames().toMutableList()
        val i = l.indexOf(name)
        val j = i + delta
        if (i < 0 || j < 0 || j >= l.size) return false
        l.add(j, l.removeAt(i))
        writeChFavGroupOrder(l)
        return true
    }

    // [NOTA-CANAL-1] Nota corta por canal (máx. 60 caracteres): clave nueva "chnotes" (objeto enlace → nota) en castv_lib,
    // así que el respaldo completo la incluye sola. Los canales de Xtream no llevan nota (sus enlaces tienen usuario y clave).

    private const val MAX_NOTE = 60

    fun chNotes(): Map<String, String> {
        val out = HashMap<String, String>()
        val raw = prefs.getString("chnotes", null) ?: return out
        try {
            val o = JSONObject(raw)
            val ks = o.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val v = o.optString(k, "")
                if (v.isNotEmpty()) out[k] = v
            }
        } catch (e: Exception) { rescue("chnotes", raw, e) }
        return out
    }

    /** Guarda la nota del canal [url] (vacía = borrarla). true si cambió algo. */
    @Synchronized
    fun setChNote(url: String, note: String): Boolean {
        val n = note.replace(Regex("[\\r\\n]+"), " ").trim().take(MAX_NOTE)
        val m = LinkedHashMap(chNotes())
        if ((m[url] ?: "") == n) return false
        if (n.isEmpty()) m.remove(url) else m[url] = n
        try {
            val o = JSONObject()
            for ((k, v) in m) o.put(k, v)
            prefs.edit().putString("chnotes", o.toString()).apply()
        } catch (e: Exception) { return false }
        return true
    }

    // [FUENTE-ALT-1] Cambia un favorito por otra fuente del mismo canal: la nueva ocupa el mismo lugar y hereda el grupo ("fg") y la nota.
    /** Reemplaza el favorito [oldUrl] por [alt]. false si [oldUrl] no es favorito, si es el mismo enlace o si [alt] ya es favorito. */
    @Synchronized
    fun replaceChFav(oldUrl: String, alt: M3uChannel): Boolean {
        if (oldUrl == alt.url) return false
        val urls = chFavs().toMutableSet()
        if (!urls.contains(oldUrl) || urls.contains(alt.url)) return false
        val groups = readChFavGroups()
        val fg = groups.remove(oldUrl)
        if (!fg.isNullOrEmpty()) groups[alt.url] = fg
        val meta = ArrayList(chFavChannels())
        val i = meta.indexOfFirst { it.url == oldUrl }
        if (i < 0) return false
        meta[i] = alt
        val dates = readChFavDates()   // [NUEVOS-FAV-1] la nueva fuente hereda la fecha de alta
        val fd = dates.remove(oldUrl)
        if (fd != null) dates[alt.url] = fd
        writeChFavMeta(meta, groups, dates)
        urls.remove(oldUrl)
        urls.add(alt.url)
        try {
            val arr = JSONArray()
            for (x in urls) arr.put(x)
            prefs.edit().putString("chfav", arr.toString()).apply()
        } catch (e: Exception) { return false }
        val note = chNotes()[oldUrl]
        if (!note.isNullOrEmpty()) { setChNote(alt.url, note); setChNote(oldUrl, "") }
        val al = readAlts()   // [FUENTES-FAV-1] los respaldos del favorito viejo pasan al nuevo
        val moved = al.remove(oldUrl)
        if (moved != null) {
            val rest = moved.filter { it.url != alt.url }
            if (rest.isNotEmpty()) al[alt.url] = ArrayList(rest)
            writeAlts(al)
        }
        return true
    }

    // [FUENTES-FAV-1] Hasta 2 fuentes de respaldo por favorito: clave nueva "chalts" (objeto enlace del favorito → lista de
    // {n, u, g, l}) en castv_lib. Los canales de Xtream no se guardan como respaldo (sus enlaces llevan usuario y clave).

    private const val MAX_ALTS = 2

    private fun readAlts(): MutableMap<String, MutableList<M3uChannel>> {
        val out = LinkedHashMap<String, MutableList<M3uChannel>>()
        val raw = prefs.getString("chalts", null) ?: return out
        try {
            val o = JSONObject(raw)
            val ks = o.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val arr = o.optJSONArray(k) ?: continue
                val l = ArrayList<M3uChannel>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val u = e.optString("u", "")
                    if (u.isNotEmpty()) l.add(M3uChannel(e.optString("n", ""), u, e.optString("g", ""), e.optString("l", "")))
                }
                if (l.isNotEmpty()) out[k] = l
            }
        } catch (e: Exception) { rescue("chalts", raw, e) }
        return out
    }

    private fun writeAlts(m: Map<String, List<M3uChannel>>): Boolean {
        return try {
            val favsNow = chFavs()
            val o = JSONObject()
            for ((k, l) in m) {
                if (l.isEmpty() || !favsNow.contains(k)) continue
                val arr = JSONArray()
                for (c in l) arr.put(JSONObject().put("n", c.name).put("u", c.url).put("g", c.group).put("l", c.logo))
                o.put(k, arr)
            }
            prefs.edit().putString("chalts", o.toString()).apply()
            true
        } catch (e: Exception) { false }
    }

    /** Respaldos guardados del favorito [url] (hasta 2), en el orden en que se guardaron. */
    @Synchronized
    fun chAlts(url: String): List<M3uChannel> = readAlts()[url] ?: emptyList()

    /** Guarda [alt] como respaldo del favorito [url]. false si [url] no es favorito, si ya está o si ya hay 2. */
    @Synchronized
    fun addChAlt(url: String, alt: M3uChannel): Boolean {
        if (url == alt.url || !chFavs().contains(url)) return false
        val m = readAlts()
        val l = m.getOrPut(url) { ArrayList() }
        if (l.size >= MAX_ALTS || l.any { it.url == alt.url }) return false
        l.add(alt)
        return writeAlts(m)
    }

    /** Quita el respaldo [altUrl] del favorito [url]. true si estaba. */
    @Synchronized
    fun removeChAlt(url: String, altUrl: String): Boolean {
        val m = readAlts()
        val l = m[url] ?: return false
        if (!l.removeAll { it.url == altUrl }) return false
        if (l.isEmpty()) m.remove(url)
        return writeAlts(m)
    }

    /** Al cargar una lista: completa el nombre de los favoritos que salgan en ella (una sola escritura, solo si cambió algo). */
    @Synchronized
    fun backfillChFavs(list: List<M3uChannel>) {
        val urls = chFavs()
        if (urls.isEmpty()) return
        val meta = readChFavMeta()
        var changed = false
        for (c in list) {
            if (!urls.contains(c.url)) continue
            val i = meta.indexOfFirst { it.url == c.url }
            if (i < 0) { meta.add(c); changed = true }
            else if (meta[i] != c) { meta[i] = c; changed = true }
        }
        if (changed) writeChFavMeta(meta)
    }

    // ------------------------------------------------- [ESTILO-2] Canales vistos recientemente
    // Clave nueva "chrecent" (JSON con enlace, nombre, grupo y logo), solo canales de listas M3U (no enlaces sueltos).
    // Vive en castv_lib, así que el respaldo la incluye sola.

    private const val MAX_RECENT_CH = 12

    /** Últimos canales reproducidos, el más reciente primero. */
    fun recentChannels(): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        val raw = prefs.getString("chrecent", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uChannel(o.optString("n", ""), o.getString("u"), o.optString("g", ""), o.optString("l", "")))
            }
        } catch (e: Exception) { rescue("chrecent", raw, e) }
        return out
    }

    /** Sube el canal al principio de "Continuar" (en modo incógnito no se guarda). */
    @Synchronized
    fun rememberRecentChannel(c: M3uChannel) {
        if (incognito) return
        countChannel(c)   // [TOP-CANALES-1]
        val l = recentChannels().filter { it.url != c.url }.toMutableList()
        l.add(0, c)
        try {
            val arr = JSONArray()
            for (x in l.take(MAX_RECENT_CH)) arr.put(JSONObject().put("u", x.url).put("n", x.name).put("g", x.group).put("l", x.logo))
            prefs.edit().putString("chrecent", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    // ------------------------------------------------- [TOP-CANALES-1] Canales más vistos
    // Archivo propio "castv_top" (JSON {enlace: {c: veces, t: última vez, n/g/l: nombre, grupo, logo}}). No entra en respaldos
    // ni en la copia automática (los enlaces Xtream llevan usuario y clave). Tope de 300 canales; en incógnito no se cuenta.

    private const val MAX_TOP_CH = 300

    private fun readTopCh(): JSONObject {
        if (!::topPrefs.isInitialized) return JSONObject()
        val raw = topPrefs.getString("chtop", null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { rescue("chtop", raw, e); JSONObject() }
    }

    /** Suma una vez al canal [c] (se llama cada vez que se abre en el teléfono o en el TV). */
    @Synchronized
    fun countChannel(c: M3uChannel) {
        if (incognito || c.url.isEmpty() || !::topPrefs.isInitialized) return
        touchDay()   // [RESUMEN-1]
        try {
            val o = readTopCh()
            val e = o.optJSONObject(c.url) ?: JSONObject()
            e.put("c", e.optInt("c", 0) + 1).put("t", System.currentTimeMillis())
                .put("n", c.name).put("g", c.group).put("l", c.logo)
            o.put(c.url, e)
            if (o.length() > MAX_TOP_CH) {
                val keys = ArrayList<String>()
                val ks = o.keys()
                while (ks.hasNext()) keys.add(ks.next())
                val drop = keys.sortedWith(compareBy<String>({ o.getJSONObject(it).optInt("c", 0) }, { o.getJSONObject(it).optLong("t", 0L) }))
                    .take(o.length() - MAX_TOP_CH)
                for (k in drop) o.remove(k)
            }
            topPrefs.edit().putString("chtop", o.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Los canales más abiertos (con [min] veces o más), el más visto primero, con su número de veces. */
    @Synchronized
    fun topChannels(limit: Int = 10, min: Int = 2): List<Pair<M3uChannel, Int>> {
        val o = readTopCh()
        val all = ArrayList<Triple<M3uChannel, Int, Long>>()
        val ks = o.keys()
        while (ks.hasNext()) {
            val u = ks.next()
            val e = o.optJSONObject(u) ?: continue
            val n = e.optInt("c", 0)
            if (n >= min) all.add(Triple(M3uChannel(e.optString("n", ""), u, e.optString("g", ""), e.optString("l", "")), n, e.optLong("t", 0L)))
        }
        return all.sortedWith(compareByDescending<Triple<M3uChannel, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(limit).map { Pair(it.first, it.second) }
    }

    /** [FAV-TOP-1] Cuántas veces se abrió cada canal (enlace → veces; solo los que tienen 1 o más). Lo usa el orden «Más vistos» de ★ Favs. */
    @Synchronized
    fun chViews(): Map<String, Int> {
        val o = readTopCh()
        val out = HashMap<String, Int>()
        val ks = o.keys()
        while (ks.hasNext()) {
            val u = ks.next()
            val n = o.optJSONObject(u)?.optInt("c", 0) ?: 0
            if (n > 0) out[u] = n
        }
        return out
    }

    /** Reinicia solo el contador de «Más vistos». */
    @Synchronized
    fun clearTopChannels() {
        if (::topPrefs.isInitialized) topPrefs.edit().remove("chtop").apply()
    }

    // ------------------------------------------------- Canales ocultos [OCULTAR-1]
    // Clave nueva "chhidden" (JSON con enlace, nombre y grupo). Las listas no se modifican: la pantalla de
    // canales solo deja de mostrar estos enlaces. Vive en castv_lib, así que el respaldo la incluye sola.

    private fun readHidden(): MutableList<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        val raw = prefs.getString("chhidden", null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uChannel(o.optString("n", ""), o.getString("u"), o.optString("g", "")))
            }
        } catch (e: Exception) { rescue("chhidden", raw, e) }
        return out
    }

    private fun writeHidden(l: List<M3uChannel>) {
        try {
            val arr = JSONArray()
            for (c in l) arr.put(JSONObject().put("u", c.url).put("n", c.name).put("g", c.group))
            prefs.edit().putString("chhidden", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Canales ocultos, en el orden en que se ocultaron. */
    fun hiddenChannels(): List<M3uChannel> = readHidden()

    /** Solo los enlaces ocultos (para filtrar rápido). */
    fun hiddenUrls(): Set<String> {
        val out = HashSet<String>()
        for (c in readHidden()) out.add(c.url)
        return out
    }

    /** Oculta un canal (por enlace de stream). Si era favorito, también se le quita la ★. */
    @Synchronized
    fun hideChannel(c: M3uChannel) {
        val l = readHidden()
        if (l.none { it.url == c.url }) { l.add(c); writeHidden(l) }
        if (chFavs().contains(c.url)) toggleChFav(c.url)
        forgetChFav(c.url)
    }

    /** [CHECK-1] Oculta varios canales de una vez (una sola escritura). A los que eran favoritos también se les quita la ★. */
    @Synchronized
    fun hideChannels(cs: List<M3uChannel>) {
        if (cs.isEmpty()) return
        val l = readHidden()
        val have = HashSet<String>()
        for (x in l) have.add(x.url)
        for (c in cs) if (have.add(c.url)) l.add(c)
        writeHidden(l)
        val favSet = chFavs()
        for (c in cs) if (favSet.contains(c.url)) {
            toggleChFav(c.url)
            forgetChFav(c.url)
        }
    }

    /** Devuelve a la vista los canales con esos enlaces. */
    @Synchronized
    fun unhideChannels(urls: Collection<String>) {
        val set = HashSet<String>(urls)
        val l = readHidden()
        if (l.removeAll { set.contains(it.url) }) writeHidden(l)
    }

    // ------------------------------------------------- Listas de reproducción

    // ------------------------------------------------- Salud de canales [FUENTES-1]
    // Clave "chhealth" (JSON {enlace: [fallos seguidos, ms del último fallo]}). Un acierto borra la entrada.
    // No oculta nada: solo sirve para marcar con ⚠ los canales que fallan seguido. Tope de 600 enlaces.

    private fun readHealth(): MutableMap<String, LongArray> {
        val out = LinkedHashMap<String, LongArray>()
        val raw = prefs.getString("chhealth", null) ?: return out
        try {
            val o = JSONObject(raw)
            val ks = o.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val a = o.getJSONArray(k)
                out[k] = longArrayOf(a.getLong(0), a.getLong(1))
            }
        } catch (e: Exception) { rescue("chhealth", raw, e) }
        return out
    }

    private fun writeHealth(m: Map<String, LongArray>) {
        try {
            val src = if (m.size > 600) m.entries.sortedByDescending { it.value[1] }.take(600).associate { it.key to it.value } else m
            val o = JSONObject()
            for ((k, v) in src) o.put(k, JSONArray().put(v[0]).put(v[1]))
            prefs.edit().putString("chhealth", o.toString()).apply()
        } catch (e: Exception) { }
    }

    /** Anota de una vez qué enlaces fallaron (+1 seguido) y cuáles respondieron bien (se borra su historial). No anota en incógnito. */
    @Synchronized
    fun recordHealth(fails: Collection<String>, oks: Collection<String>) {
        if (incognito || (fails.isEmpty() && oks.isEmpty())) return
        val m = readHealth()
        var changed = false
        for (u in oks) if (m.remove(u) != null) changed = true
        val now = System.currentTimeMillis()
        for (u in fails) {
            val a = m[u]
            m[u] = longArrayOf((a?.get(0) ?: 0L) + 1L, now)
            changed = true
        }
        if (changed) writeHealth(m)
    }

    fun markChFail(url: String) = recordHealth(listOf(url), emptyList())

    fun markChOk(url: String) = recordHealth(emptyList(), listOf(url))

    /** Canales con al menos [min] fallos seguidos: enlace → número de fallos. */
    @Synchronized
    fun failedChannels(min: Int = 2): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((k, v) in readHealth()) if (v[0] >= min) out[k] = v[0].toInt()
        return out
    }

    /** Borra todos los avisos ⚠ (los canales no se tocan). */
    @Synchronized
    fun clearHealth() {
        prefs.edit().remove("chhealth").apply()
    }

    private fun loadLists() {
        lists = LinkedHashMap()
        val raw = prefs.getString("lists", null) ?: return
        try {
            val obj = JSONObject(raw)
            val names = obj.keys()
            while (names.hasNext()) {
                val n = names.next()
                val arr = obj.getJSONArray(n)
                val l = ArrayList<String>()
                for (i in 0 until arr.length()) l.add(arr.getString(i))
                lists[n] = l
            }
        } catch (e: Exception) { rescue("lists", raw, e) }
    }

    private fun saveLists() {
        try {
            val obj = JSONObject()
            for ((n, l) in lists) {
                val arr = JSONArray()
                for (k in l) arr.put(k)
                obj.put(n, arr)
            }
            prefs.edit().putString("lists", obj.toString()).apply()
        } catch (e: Exception) { }
    }

    @Synchronized
    fun listNames(): List<String> = ArrayList(lists.keys)

    @Synchronized
    fun listSize(name: String): Int = lists[name]?.size ?: 0

    @Synchronized
    fun createList(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || lists.containsKey(n)) return false
        lists[n] = ArrayList()
        saveLists()
        return true
    }

    @Synchronized
    fun deleteList(name: String) {
        if (lists.remove(name) != null) saveLists()
    }

    /** [LISTAS-1] Cambia el nombre de una lista sin mover su lugar ni sus pistas. false si el nombre está vacío, ya existe o la lista no existe. */
    @Synchronized
    fun renameList(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty() || !lists.containsKey(old)) return false
        if (n == old) return true
        if (lists.containsKey(n)) return false
        val rebuilt = LinkedHashMap<String, MutableList<String>>()
        for ((k, v) in lists) rebuilt[if (k == old) n else k] = v
        lists = rebuilt
        saveLists()
        return true
    }

    @Synchronized
    fun inList(name: String, item: MediaItem): Boolean = lists[name]?.contains(key(item)) == true

    /** Devuelve true si se añadió (false si ya estaba). */
    @Synchronized
    fun addToList(name: String, item: MediaItem): Boolean {
        val l = lists[name] ?: return false
        val k = key(item)
        if (l.contains(k)) return false
        l.add(k)
        saveLists()
        return true
    }

    @Synchronized
    fun removeFromList(name: String, item: MediaItem) {
        if (lists[name]?.remove(key(item)) == true) saveLists()
    }

    // ------------------------------------------------- [VER-DESPUES-1] «Ver más tarde»
    // Es una lista de reproducción más (clave "lists" de castv_lib, así que viaja en el respaldo y se ve en Listas); la app la crea
    // sola al guardar el primer video y el reproductor lo quita cuando se termina de ver.

    const val WATCH_LATER = "🕓 Ver más tarde"

    @Synchronized
    fun isWatchLater(item: MediaItem): Boolean = lists[WATCH_LATER]?.contains(key(item)) == true

    /** Guarda [item] en «Ver más tarde» (o lo quita si ya estaba). Devuelve true si quedó guardado. */
    @Synchronized
    fun toggleWatchLater(item: MediaItem): Boolean {
        val l = lists.getOrPut(WATCH_LATER) { ArrayList() }
        val k = key(item)
        val now = if (l.remove(k)) false else { l.add(k); true }
        saveLists()
        return now
    }

    /** Lo quita de «Ver más tarde» si estaba (se llama al terminar de verlo). */
    @Synchronized
    fun doneWatchLater(item: MediaItem) {
        if (lists[WATCH_LATER]?.remove(key(item)) == true) saveLists()
    }
}
