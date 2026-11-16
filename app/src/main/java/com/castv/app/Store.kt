package com.castv.app

import android.content.Context
import android.content.SharedPreferences

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
    private var favs: MutableSet<String> = HashSet()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)
        resumePrefs = app.getSharedPreferences("castv_resume", Context.MODE_PRIVATE)
        favs = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
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

    // ------------------------------------------------------------ Duplicados

    var hideDups: Boolean
        get() = prefs.getBoolean("hideDups", true)
        set(v) { prefs.edit().putBoolean("hideDups", v).apply() }
}
