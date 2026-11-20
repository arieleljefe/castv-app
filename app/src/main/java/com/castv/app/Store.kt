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
    private var favs: MutableSet<String> = HashSet()
    private var lists: LinkedHashMap<String, MutableList<String>> = LinkedHashMap()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)
        resumePrefs = app.getSharedPreferences("castv_resume", Context.MODE_PRIVATE)
        statsPrefs = app.getSharedPreferences("castv_stats", Context.MODE_PRIVATE)
        favs = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        loadLists()
    }

    /** Vuelve a leer favoritos y listas desde las preferencias (se usa tras importar un respaldo). */
    fun reload() {
        if (!::prefs.isInitialized) return
        favs = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        loadLists()
    }

    fun key(item: MediaItem): String =
        (if (item.isMusic) "a" else "v") + "|" + item.name.lowercase() + "|" + item.durationSec

    // ------------------------------------------------------------ Favoritos

    fun isFav(item: MediaItem): Boolean = favs.contains(key(item))

    /** Devuelve true si quedó como favorito. */
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
        } catch (e: Exception) { emptyList() }
    }

    private fun saveMarks(item: MediaItem, list: List<VideoMark>) {
        val k = "bm|" + key(item)
        if (list.isEmpty()) { prefs.edit().remove(k).apply(); return }
        val arr = JSONArray()
        for (m in list) arr.put(JSONObject().put("t", m.sec).put("n", m.name))
        prefs.edit().putString(k, arr.toString()).apply()
    }

    /** Devuelve false si no se guardó (modo incógnito o demasiados marcadores). */
    fun addMark(item: MediaItem, sec: Int, name: String): Boolean {
        if (incognito) return false
        val list = marks(item).filter { it.sec != sec }
        if (list.size >= MAX_MARKS) return false
        saveMarks(item, list + VideoMark(sec, name))
        return true
    }

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
        try {
            val raw = prefs.getString("links", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(SavedLink(o.getString("u"), o.optString("n", ""), o.optBoolean("f", false), o.optLong("t", 0L)))
            }
        } catch (e: Exception) { }
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
    fun toggleLinkFav(url: String): Boolean {
        var now = false
        val out = readLinks().map {
            if (it.url == url) { now = !it.fav; it.copy(fav = now) } else it
        }
        writeLinks(out)
        return now
    }

    fun removeLink(url: String) {
        writeLinks(readLinks().filter { it.url != url })
    }

    /** Borra los enlaces que no son favoritos. */
    fun clearRecentLinks() {
        writeLinks(readLinks().filter { it.fav })
    }

    // ------------------------------------------------- Listas M3U

    fun m3uLists(): List<M3uList> {
        val out = ArrayList<M3uList>()
        try {
            val raw = prefs.getString("m3u", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uList(o.optString("n", ""), o.getString("u")))
            }
        } catch (e: Exception) { }
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
    fun addM3uList(name: String, url: String) {
        val l = m3uLists().filter { it.url != url }.toMutableList()
        l.add(M3uList(name, url))
        writeM3u(l)
    }

    fun removeM3uList(url: String) {
        writeM3u(m3uLists().filter { it.url != url })
    }

    /** Enlace de la última lista M3U abierta. */
    var lastM3u: String?
        get() = prefs.getString("m3uLast", null)
        set(v) { prefs.edit().putString("m3uLast", v).apply() }

    // ------------------------------------------------- Catálogo preinstalado (CATALOGO-1)

    /**
     * Añade, una sola vez por versión, las listas del catálogo que no estén ya guardadas.
     * No toca las listas del usuario y no vuelve a poner las que el usuario quitó después.
     * Devuelve cuántas listas añadió.
     */
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
        try {
            val raw = prefs.getString("chfav", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Exception) { }
        return out
    }

    /** Devuelve true si el canal quedó como favorito. */
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
        try {
            val raw = prefs.getString("chfavmeta", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uChannel(o.optString("n", ""), o.getString("u"), o.optString("g", ""), o.optString("l", "")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun writeChFavMeta(l: List<M3uChannel>) {
        try {
            val arr = JSONArray()
            for (c in l) arr.put(JSONObject().put("u", c.url).put("n", c.name).put("g", c.group).put("l", c.logo))
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
    fun rememberChFav(c: M3uChannel) {
        val l = readChFavMeta()
        val i = l.indexOfFirst { it.url == c.url }
        if (i >= 0) { if (l[i] == c) return; l[i] = c } else l.add(c)
        writeChFavMeta(l)
    }

    /** Borra el nombre guardado de un canal que ya no es favorito. */
    fun forgetChFav(url: String) {
        val l = readChFavMeta()
        if (l.removeAll { it.url == url }) writeChFavMeta(l)
    }

    /** Al cargar una lista: completa el nombre de los favoritos que salgan en ella (una sola escritura, solo si cambió algo). */
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

    // ------------------------------------------------- Canales ocultos [OCULTAR-1]
    // Clave nueva "chhidden" (JSON con enlace, nombre y grupo). Las listas no se modifican: la pantalla de
    // canales solo deja de mostrar estos enlaces. Vive en castv_lib, así que el respaldo la incluye sola.

    private fun readHidden(): MutableList<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        try {
            val raw = prefs.getString("chhidden", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(M3uChannel(o.optString("n", ""), o.getString("u"), o.optString("g", "")))
            }
        } catch (e: Exception) { }
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
    fun hideChannel(c: M3uChannel) {
        val l = readHidden()
        if (l.none { it.url == c.url }) { l.add(c); writeHidden(l) }
        if (chFavs().contains(c.url)) toggleChFav(c.url)
        forgetChFav(c.url)
    }

    /** Devuelve a la vista los canales con esos enlaces. */
    fun unhideChannels(urls: Collection<String>) {
        val set = HashSet<String>(urls)
        val l = readHidden()
        if (l.removeAll { set.contains(it.url) }) writeHidden(l)
    }

    // ------------------------------------------------- Listas de reproducción

    private fun loadLists() {
        lists = LinkedHashMap()
        try {
            val raw = prefs.getString("lists", null) ?: return
            val obj = JSONObject(raw)
            val names = obj.keys()
            while (names.hasNext()) {
                val n = names.next()
                val arr = obj.getJSONArray(n)
                val l = ArrayList<String>()
                for (i in 0 until arr.length()) l.add(arr.getString(i))
                lists[n] = l
            }
        } catch (e: Exception) { }
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

    fun listNames(): List<String> = ArrayList(lists.keys)

    fun listSize(name: String): Int = lists[name]?.size ?: 0

    fun createList(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || lists.containsKey(n)) return false
        lists[n] = ArrayList()
        saveLists()
        return true
    }

    fun deleteList(name: String) {
        if (lists.remove(name) != null) saveLists()
    }

    fun inList(name: String, item: MediaItem): Boolean = lists[name]?.contains(key(item)) == true

    /** Devuelve true si se añadió (false si ya estaba). */
    fun addToList(name: String, item: MediaItem): Boolean {
        val l = lists[name] ?: return false
        val k = key(item)
        if (l.contains(k)) return false
        l.add(k)
        saveLists()
        return true
    }

    fun removeFromList(name: String, item: MediaItem) {
        if (lists[name]?.remove(key(item)) == true) saveLists()
    }
}
