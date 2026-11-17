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

    // ----------------------------------------------------- Continuar donde lo dejé

    fun resumeAt(item: MediaItem): Int = resumePrefs.getInt(key(item), 0)

    fun saveResume(item: MediaItem, sec: Int) {
        resumePrefs.edit().putInt(key(item), sec).apply()
    }

    fun clearResume(item: MediaItem) {
        val k = key(item)
        if (resumePrefs.contains(k)) resumePrefs.edit().remove(k).apply()
    }

    /** Borra todo rastro de un archivo eliminado: favoritos, listas, punto guardado y estadísticas. */
    fun forget(item: MediaItem) {
        val k = key(item)
        if (favs.remove(k)) prefs.edit().putStringSet("fav", HashSet(favs)).apply()
        var changed = false
        for (l in lists.values) if (l.remove(k)) changed = true
        if (changed) saveLists()
        resumePrefs.edit().remove(k).apply()
        statsPrefs.edit().remove("c|$k").remove("t|$k").apply()
    }

    // ------------------------------------------------------------ Duplicados

    var hideDups: Boolean
        get() = prefs.getBoolean("hideDups", true)
        set(v) { prefs.edit().putBoolean("hideDups", v).apply() }

    // ------------------------------------------------- Estadísticas de uso

    fun playCount(item: MediaItem): Int = statsPrefs.getInt("c|" + key(item), 0)

    fun lastPlayed(item: MediaItem): Long = statsPrefs.getLong("t|" + key(item), 0L)

    fun registerPlay(item: MediaItem) {
        val k = key(item)
        statsPrefs.edit()
            .putInt("c|$k", statsPrefs.getInt("c|$k", 0) + 1)
            .putLong("t|$k", System.currentTimeMillis())
            .apply()
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
