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
        rehydrate(ctx)   // [XTFAV-1] si venían entradas «portátiles» de un respaldo, se completan con este acceso
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

    // ------------------------------------------------- [XTFAV-1] Enlaces de canales «portátiles» (sin usuario ni clave)
    // En un respaldo, un enlace de canal Xtream `http://srv/live/usuario/clave/123.m3u8` se guarda como
    // `xtream://<id>/live/123.m3u8` (o `xtream://<id>@http://otro-host/live/123.m3u8` si el host no es el del acceso).
    // Al restaurar, si ese acceso existe en el teléfono, vuelve a ser el enlace real; si no, queda así hasta que se
    // vuelva a poner el acceso de esa lista (🔑 Xtream con el mismo id), momento en que [rehydrate] lo completa.

    /** Claves de castv_lib con enlaces de canales que se vuelven portátiles. («chhealth» no: se descarta.) */
    internal val PORTABLE_KEYS = setOf("chfav", "chfavmeta", "chhidden", "links", "chrecent")

    internal class Login(val id: String, val s: String, val u: String, val p: String)

    private val CH_URL = Regex("""^(.*?)/(live|movie|series|timeshift)/([^/\s]+)/([^/\s]+)/(\S+)$""", RegexOption.IGNORE_CASE)
    private val CH_BACK = Regex("""^(.*?)/(live|movie|series|timeshift)/(\S+)$""", RegexOption.IGNORE_CASE)

    internal fun logins(ctx: Context): List<Login> {
        val out = ArrayList<Login>()
        for ((id, v) in sp(ctx).all) {
            val raw = v as? String ?: continue
            try {
                val o = JSONObject(raw)
                val u = o.getString("u")
                val p = o.getString("p")
                if (u.isNotEmpty() && p.isNotEmpty()) out.add(Login(id, o.getString("s"), u, p))
            } catch (e: Exception) { }
        }
        return out
    }

    private fun dec(s: String): String = try { java.net.URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    /** Enlace real de un canal con usuario y clave → forma portátil; null si no es de un acceso guardado aquí. */
    internal fun toPortable(url: String, ls: List<Login>): String? {
        val m = CH_URL.find(url) ?: return null
        val base = m.groupValues[1]
        val kind = m.groupValues[2]
        val user = m.groupValues[3]
        val pass = m.groupValues[4]
        val rest = m.groupValues[5]
        val lg = ls.firstOrNull { (it.u == user && it.p == pass) || (it.u == dec(user) && it.p == dec(pass)) } ?: return null
        val same = base.equals(lg.s, ignoreCase = true)
        return PREFIX + lg.id + (if (same) "" else "@$base") + "/" + kind + "/" + rest
    }

    /** Forma portátil → enlace real; null si no es portátil o no hay acceso con ese id. */
    internal fun fromPortable(p: String, ls: List<Login>): String? {
        if (!p.startsWith(PREFIX)) return null
        val body = p.removePrefix(PREFIX)
        val cut = body.indexOfFirst { it == '@' || it == '/' }
        if (cut <= 0) return null
        val id = body.substring(0, cut)
        val lg = ls.firstOrNull { it.id == id } ?: return null
        val tail = body.substring(cut)
        val base: String
        val kind: String
        val rest: String
        if (tail.startsWith("@")) {
            val m = CH_BACK.find(tail.substring(1)) ?: return null
            base = m.groupValues[1]; kind = m.groupValues[2]; rest = m.groupValues[3]
        } else {
            val m = CH_BACK.find(tail) ?: return null
            if (m.groupValues[1].isNotEmpty()) return null
            base = lg.s; kind = m.groupValues[2]; rest = m.groupValues[3]
        }
        return "$base/$kind/${lg.u}/${lg.p}/$rest"
    }

    /** Aplica [f] al enlace de cada elemento (texto suelto o campo "u" de un objeto) del JSON [v] de la clave [k]. Si algo falla devuelve [v] igual. */
    internal fun mapUrls(k: String, v: String, f: (String) -> String): String {
        if (k !in PORTABLE_KEYS) return v
        return try {
            val a = org.json.JSONArray(v)
            val out = org.json.JSONArray()
            for (i in 0 until a.length()) {
                val e = a.get(i)
                if (e is JSONObject) {
                    val c = JSONObject(e.toString())
                    c.put("u", f(e.getString("u")))
                    out.put(c)
                } else out.put(f(e.toString()))
            }
            out.toString()
        } catch (ex: Exception) { v }
    }

    /** Para el respaldo: los enlaces con usuario y clave de un acceso conocido pasan a forma portátil. */
    internal fun portabilize(k: String, v: String, ls: List<Login>): String =
        if (ls.isEmpty()) v else mapUrls(k, v) { toPortable(it, ls) ?: it }

    /** Completa las entradas portátiles de castv_lib cuyo acceso ya existe (se llama al guardar un acceso). */
    fun rehydrate(ctx: Context) {
        try {
            val ls = logins(ctx)
            if (ls.isEmpty()) return
            val lib = ctx.applicationContext.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)
            val ed = lib.edit()
            var changed = false
            for (k in PORTABLE_KEYS) {
                val cur = lib.getString(k, null) ?: continue
                if (!cur.contains(PREFIX)) continue
                val nv = mapUrls(k, cur) { fromPortable(it, ls) ?: it }
                if (nv != cur) { ed.putString(k, nv); changed = true }
            }
            if (changed) ed.commit()
        } catch (e: Exception) { }
    }
}
