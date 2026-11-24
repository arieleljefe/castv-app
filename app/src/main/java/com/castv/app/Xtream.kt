package com.castv.app

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/**
 * [XTREAM-1] Listas de proveedores tipo Xtream (servidor + usuario + clave).
 *
 * En la lista guardada (clave "m3u") solo queda `xtream://<id>`. El usuario y la clave viven aparte, en el archivo
 * de preferencias `castv_xt`, que NO está en la lista de archivos de [Backup]: no entra en respaldos, copias
 * automáticas ni archivos para compartir. El enlace real (`get.php?username=…&password=…`) se arma solo en el
 * momento de descargar y no se guarda ni se anota en el /log.
 *
 * Los enlaces de cada canal de Xtream también llevan usuario y clave dentro (…/live/usuario/clave/123.m3u8);
 * [leakFilter] los reconoce para que [Backup] no los incluya.
 */
object Xtream {
    private const val FILE = "castv_xt"
    const val PREFIX = "xtream://"

    fun isXtream(url: String): Boolean = url.startsWith(PREFIX)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cleanServer(s: String): String = s.trim().trimEnd('/')

    fun validServer(s: String): Boolean {
        val t = cleanServer(s).lowercase()
        return (t.startsWith("http://") || t.startsWith("https://")) && t.length > 10 && !t.contains(' ')
    }

    /** Guarda el acceso y devuelve el enlace interno de la lista (`xtream://id`). Con [reuse] se vuelve a poner el acceso de una lista que ya existe. */
    @Synchronized
    fun save(ctx: Context, server: String, user: String, pass: String, reuse: String? = null): String {
        val id = if (reuse != null && isXtream(reuse)) reuse.removePrefix(PREFIX) else java.util.UUID.randomUUID().toString().take(8)
        val o = JSONObject().put("s", cleanServer(server)).put("u", user.trim()).put("p", pass)
        sp(ctx).edit().putString(id, o.toString()).apply()
        return PREFIX + id
    }

    private fun read(ctx: Context, url: String): JSONObject? {
        val raw = sp(ctx).getString(url.removePrefix(PREFIX), null) ?: return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    fun hasLogin(ctx: Context, url: String): Boolean = read(ctx, url) != null

    /** Enlace real de la lista, con usuario y clave: solo para descargar. null si no hay acceso guardado (por ejemplo tras restaurar un respaldo). */
    fun playlistUrl(ctx: Context, url: String): String? {
        val o = read(ctx, url) ?: return null
        return try {
            o.getString("s") + "/get.php?username=" + URLEncoder.encode(o.getString("u"), "UTF-8") +
                "&password=" + URLEncoder.encode(o.getString("p"), "UTF-8") + "&type=m3u_plus&output=m3u8"
        } catch (e: Exception) { null }
    }

    /** [EPG-1] Enlace de la guía XMLTV del proveedor, con usuario y clave: solo para descargar (no se guarda; el /log lo tapa). */
    fun epgUrl(ctx: Context, url: String): String? {
        val o = read(ctx, url) ?: return null
        return try {
            o.getString("s") + "/xmltv.php?username=" + URLEncoder.encode(o.getString("u"), "UTF-8") +
                "&password=" + URLEncoder.encode(o.getString("p"), "UTF-8")
        } catch (e: Exception) { null }
    }

    @Synchronized
    fun remove(ctx: Context, url: String) {
        sp(ctx).edit().remove(url.removePrefix(PREFIX)).apply()
    }

    // ------------------------------------------------- Reconocer enlaces con usuario y clave

    private val XT_PATH = Regex("""/(?:live|movie|series|timeshift)/[^/\s]+/[^/\s]+/\d""", RegexOption.IGNORE_CASE)

    /** Devuelve una función que dice si un enlace lleva usuario y clave de Xtream (forma típica o los accesos guardados aquí). */
    fun leakFilter(ctx: Context): (String) -> Boolean {
        val logins = ArrayList<Pair<String, String>>()
        for ((_, v) in sp(ctx).all) {
            val raw = v as? String ?: continue
            try {
                val o = JSONObject(raw)
                val u = o.getString("u")
                val p = o.getString("p")
                if (u.isNotEmpty() && p.isNotEmpty()) logins.add(Pair(u, p))
            } catch (e: Exception) { }
        }
        return { url -> leaks(url, logins) }
    }

    private fun leaks(url: String, logins: List<Pair<String, String>>): Boolean {
        if (XT_PATH.containsMatchIn(url)) return true
        val low = url.lowercase()
        if (low.contains("username=") && low.contains("password=")) return true
        for ((u, p) in logins) {
            if (url.contains("/$u/$p/")) return true
            try {
                if (url.contains(URLEncoder.encode(u, "UTF-8") + "&password=" + URLEncoder.encode(p, "UTF-8"))) return true
            } catch (e: Exception) { }
        }
        return false
    }
}
