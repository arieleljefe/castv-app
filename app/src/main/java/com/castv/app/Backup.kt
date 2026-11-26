package com.castv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Respaldo de los datos del usuario en un archivo JSON (favoritos, listas, historial de enlaces,
 * listas M3U, favoritos de canales y ajustes). No incluye la IP del TV, ni los puntos de
 * reanudación ni las estadísticas de uso (salvo las marcas de «visto»; el modo incógnito y los
 * presets propios del ecualizador ya viajaban en las preferencias).
 *
 * [XTFAV-1] Los canales Xtream (favoritos, ocultos, Continuar, historial) viajan sin usuario ni clave (`xtream://<id>/…`); al restaurar se
 * conserva lo de Xtream que ya había y se une con lo del respaldo.
 *
 * Importar REEMPLAZA los datos actuales. Antes de tocar nada, todo el archivo se lee y se valida.
 *
 * [AUTORESPALDO-1] Los archivos PARCIALES (v = 2, con la marca "part") traen solo el historial o solo los
 * canales favoritos para compartirlos con otra persona: al importarlos se AÑADEN a lo que ya hay y no se borra nada.
 */
object Backup {
    // [TESTS-2] keep, scrub y merge* son internal (no private) solo para poder probarlos; no cambia nada más
    private const val APP = "castv"
    private const val VERSION = 1         // los respaldos completos siguen siendo v1 (cualquier CASTV los lee)
    private const val MAX_READABLE = 2    // [AUTORESPALDO-1] v2 = parcial; una versión vieja de CASTV lo rechaza
    private val FILES = listOf("castv_lib", "castv", "castv_stats")   // [BACKUP-2] castv_stats: solo las marcas "visto"
    private val SKIP = setOf("tvIp", "pin_hash")   // [BACKUP-1] propios de cada teléfono/red: no se respaldan ni se tocan al importar

    /** [BACKUP-2] Qué claves de cada archivo se respaldan: en castv_stats solo las marcas "visto" (v|...). */
    internal fun keep(name: String, k: String): Boolean = when (name) {
        "castv" -> k !in SKIP
        "castv_stats" -> k.startsWith("v|")
        else -> true
    }

    // [XTREAM-1] Claves de castv_lib con enlaces de canales: los de Xtream llevan usuario y clave, así que no viajan en respaldos
    private val URL_KEYS = setOf("chfav", "chfavmeta", "chhidden", "links", "chrecent", "chhealth", "chalts", "chnotes")   // [SEGURIDAD-RESP-1] chalts y chnotes
    private val OBJ_KEYS = setOf("chhealth", "chnotes", "chalts")   // objetos que tienen el enlace como clave

    /** Quita de [v] (texto JSON de la clave [k]) los enlaces con usuario y clave de Xtream. */
    internal fun scrub(k: String, v: String, leaks: (String) -> Boolean): String {
        if (k !in URL_KEYS) return v
        return try {
            if (k in OBJ_KEYS) {
                val o = JSONObject(v)
                val out = JSONObject()
                val ks = o.keys()
                while (ks.hasNext()) {
                    val u = ks.next()
                    if (leaks(u)) continue
                    if (k == "chalts") {   // [SEGURIDAD-RESP-1] también se quitan los respaldos cuyo enlace lleve usuario y clave
                        val arr = o.optJSONArray(u) ?: continue
                        val keep = JSONArray()
                        for (i in 0 until arr.length()) {
                            val e = arr.optJSONObject(i) ?: continue
                            if (!leaks(e.optString("u"))) keep.put(e)
                        }
                        if (keep.length() > 0) out.put(u, keep)
                    } else out.put(u, o.get(u))
                }
                out.toString()
            } else {
                val a = JSONArray(v)
                val out = JSONArray()
                for (i in 0 until a.length()) {
                    val e = a.get(i)
                    val u = if (e is JSONObject) e.optString("u") else e.toString()
                    if (!leaks(u)) out.put(e)
                }
                out.toString()
            }
        } catch (ex: Exception) {
            if (!leaks(v)) v else if (k in OBJ_KEYS) "{}" else "[]"
        }
    }

    /** Arma el JSON con las preferencias de la app. */
    fun build(ctx: Context): String {
        val leaks = Xtream.leakFilter(ctx)   // [XTREAM-1]
        val xt = Xtream.logins(ctx)          // [XTFAV-1] los canales Xtream viajan en forma portátil (sin usuario ni clave)
        val root = JSONObject()
        root.put("app", APP)
        root.put("v", VERSION)
        root.put("time", System.currentTimeMillis())
        val prefsObj = JSONObject()
        for (name in FILES) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            loop@ for ((k, v) in sp.all) {
                if (!keep(name, k)) continue@loop
                val e = JSONObject()
                when (v) {
                    is String -> e.put("t", "s").put("v", if (name == "castv_lib") scrub(k, Xtream.portabilize(k, v, xt), leaks) else v)   // [XTREAM-1] [XTFAV-1]
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue@loop
                }
                o.put(k, e)
            }
            prefsObj.put(name, o)
        }
        root.put("prefs", prefsObj)
        return root.toString(2)
    }

    /** Lee un archivo como texto UTF-8; devuelve null si pasa del límite (evita archivos gigantes por error). */
    fun readLimited(ins: InputStream, max: Int = 5_000_000): String? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(chunk)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(chunk, 0, n)
        }
        return out.toString("UTF-8")
    }

    /** Devuelve null si todo salió bien, o un mensaje de error (y en ese caso no se cambió nada). */
    fun restore(ctx: Context, text: String): String? {
        val root = try { JSONObject(text) } catch (e: Exception) { return "El archivo no es un respaldo válido." }
        if (root.optString("app") != APP) return "Este archivo no es un respaldo de CASTV."
        val ver = root.optInt("v", 0)
        if (ver < 1) return "El respaldo no tiene una versión válida."
        if (ver > MAX_READABLE) return "El respaldo es de una versión más nueva de CASTV. Actualiza la app."
        if (root.has("part")) return restorePart(ctx, root)   // [AUTORESPALDO-1] parcial: se añade, no reemplaza
        if (ver > VERSION) return "El respaldo es de una versión más nueva de CASTV. Actualiza la app."
        val prefs = root.optJSONObject("prefs") ?: return "El respaldo está vacío."

        // 1) Leer y validar todo en memoria
        val staged = LinkedHashMap<String, Map<String, Any>>()
        try {
            for (name in FILES) {
                val o = prefs.optJSONObject(name) ?: continue
                val m = LinkedHashMap<String, Any>()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (!keep(name, k)) continue
                    val e = o.getJSONObject(k)
                    val v: Any = when (e.getString("t")) {
                        "s" -> e.getString("v")
                        "i" -> e.getInt("v")
                        "l" -> e.getLong("v")
                        "f" -> e.getDouble("v").toFloat()
                        "b" -> e.getBoolean("v")
                        "ss" -> {
                            val a = e.getJSONArray("v")
                            val set = HashSet<String>()
                            for (i in 0 until a.length()) set.add(a.getString(i))
                            set
                        }
                        else -> throw IllegalArgumentException("tipo desconocido")
                    }
                    m[k] = v
                }
                staged[name] = m
            }
        } catch (e: Exception) {
            return "El respaldo está dañado."
        }
        if (staged.isEmpty()) return "El respaldo está vacío."

        // 2) Aplicar: reemplaza lo actual (salvo lo que no se respalda)
        for ((name, m0) in staged) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val m = if (name == "castv_lib") withXtream(ctx, sp, m0) else m0   // [XTFAV-1]
            val ed = sp.edit()
            for (k in sp.all.keys) {
                if (!keep(name, k)) continue
                ed.remove(k)
            }
            for ((k, v) in m) {
                when (v) {
                    is String -> ed.putString(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Float -> ed.putFloat(k, v)
                    is Boolean -> ed.putBoolean(k, v)
                    is Set<*> -> ed.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            ed.commit()
        }
        return null
    }

    // ------------------------------------------------- [XTFAV-1] Canales Xtream al restaurar

    /**
     * Antes de reemplazar castv_lib: (1) los enlaces portátiles del respaldo vuelven a ser reales si su acceso existe aquí;
     * (2) lo de Xtream que ya hay en el teléfono (reales o portátiles) se conserva y se une con lo del respaldo.
     * Si algo falla con una clave, esa clave queda como venía en el respaldo.
     */
    private fun withXtream(ctx: Context, sp: android.content.SharedPreferences, m: Map<String, Any>): Map<String, Any> {
        val ls = Xtream.logins(ctx)
        val leaks = Xtream.leakFilter(ctx)
        val out = LinkedHashMap<String, Any>(m)
        for (k in Xtream.PORTABLE_KEYS) {
            val inc = out[k] as? String
            val dec = if (inc != null) Xtream.mapUrls(k, inc) { Xtream.fromPortable(it, ls) ?: it } else null
            if (dec != null) out[k] = dec
            try {
                val cur = sp.getString(k, null) ?: continue
                val mine = JSONArray(scrub(k, cur) { u -> !(leaks(u) || Xtream.isXtream(u)) })   // solo lo de Xtream
                if (mine.length() == 0) continue
                out[k] = mergeKey(k, JSONArray(dec ?: "[]"), mine).toString()
            } catch (e: Exception) { }
        }
        return out
    }

    private fun mergeKey(k: String, a: JSONArray, b: JSONArray): JSONArray = when (k) {
        "links" -> mergeLinks(a, b).first
        "chfav" -> mergeStrings(a, b).first
        "chrecent" -> mergeObjects(a, b, MAX_RECENT_CH).first
        else -> mergeObjects(a, b, Int.MAX_VALUE).first   // chfavmeta, chhidden
    }

    // ------------------------------------------------- [AUTORESPALDO-1] Archivos parciales para compartir

    /** Qué claves de castv_lib viajan en cada parte. */
    private val PARTS = mapOf(
        "history" to listOf("links", "chrecent"),   // historial de enlaces y canales vistos recientemente
        "favs" to listOf("chfav", "chfavmeta")      // canales favoritos
    )
    private const val MAX_LINKS_RECENT = 20   // igual que Store (los favoritos no cuentan)
    private const val MAX_RECENT_CH = 12      // igual que Store

    /** Resumen del último parcial añadido (para el aviso al importar). */
    @Volatile var lastNote: String = ""
        private set

    /**
     * Qué es este texto: "full" (respaldo completo), "history" o "favs" (parciales), "part" (parcial de un tipo
     * que esta versión no conoce) o null si no es un respaldo de CASTV.
     */
    fun describe(text: String): String? {
        val root = try { JSONObject(text) } catch (e: Exception) { return null }
        if (root.optString("app") != APP) return null
        if (!root.has("part")) return "full"
        val p = root.optString("part")
        return if (PARTS.containsKey(p)) p else "part"
    }

    /** Arma un parcial ("history" o "favs"); devuelve null si no hay nada que compartir. */
    fun buildPart(ctx: Context, part: String): String? {
        val keys = PARTS[part] ?: return null
        val sp = ctx.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)
        val o = JSONObject()
        for (k in keys) {
            val v = scrub(k, sp.getString(k, null) ?: continue, Xtream.leakFilter(ctx))   // [XTREAM-1] sin enlaces con usuario y clave
            val n = try { JSONArray(v).length() } catch (e: Exception) { 0 }
            if (n == 0) continue
            o.put(k, JSONObject().put("t", "s").put("v", v))
        }
        if (o.length() == 0) return null
        val root = JSONObject()
        root.put("app", APP)
        root.put("v", 2)
        root.put("part", part)
        root.put("time", System.currentTimeMillis())
        root.put("prefs", JSONObject().put("castv_lib", o))
        return root.toString(2)
    }

    /** Añade (sin borrar nada) lo que trae un parcial. Devuelve null si salió bien, o el mensaje de error. */
    private fun restorePart(ctx: Context, root: JSONObject): String? {
        val keys = PARTS[root.optString("part")]
            ?: return "El respaldo es de una versión más nueva de CASTV. Actualiza la app."
        val lib = root.optJSONObject("prefs")?.optJSONObject("castv_lib") ?: return "El respaldo está vacío."
        val sp = ctx.getSharedPreferences("castv_lib", Context.MODE_PRIVATE)

        // 1) Leer y validar todo en memoria (si algo falla no se toca nada)
        val out = LinkedHashMap<String, String>()
        val counts = LinkedHashMap<String, Int>()
        var found = false
        try {
            for (k in keys) {
                val e = lib.optJSONObject(k) ?: continue
                if (e.getString("t") != "s") throw IllegalArgumentException("tipo")
                val inc = JSONArray(e.getString("v"))
                found = true
                val cur = try { JSONArray(sp.getString(k, null) ?: "[]") } catch (ex: Exception) { JSONArray() }
                val res = when (k) {
                    "links" -> mergeLinks(cur, inc)
                    "chfav" -> mergeStrings(cur, inc)
                    "chfavmeta" -> mergeObjects(cur, inc, Int.MAX_VALUE)
                    else -> mergeObjects(cur, inc, MAX_RECENT_CH)   // chrecent
                }
                out[k] = res.first.toString()
                counts[k] = res.second
            }
        } catch (e: Exception) {
            return "El respaldo está dañado."
        }
        if (!found) return "El respaldo está vacío."

        // 2) Aplicar: solo claves de este parcial, una sola escritura
        val ed = sp.edit()
        for ((k, v) in out) ed.putString(k, v)
        ed.commit()

        val bits = ArrayList<String>()
        val l = counts["links"] ?: 0
        val r = counts["chrecent"] ?: 0
        val f = counts["chfav"] ?: 0
        if (l > 0) bits.add(if (l == 1) "1 enlace" else "$l enlaces")
        if (r > 0) bits.add(if (r == 1) "1 canal visto" else "$r canales vistos")
        if (f > 0) bits.add(if (f == 1) "1 canal favorito" else "$f canales favoritos")
        lastNote = if (bits.isEmpty()) "No había nada nuevo: ya lo tenías todo."
                   else "Se añadió: " + bits.joinToString(", ") + ". No se borró nada."
        return null
    }

    /** Enlaces: lo que ya hay se queda; se añaden los que no estén (por URL); mismo tope que Store. Devuelve (lista, nuevos). */
    internal fun mergeLinks(cur: JSONArray, inc: JSONArray): Pair<JSONArray, Int> {
        val have = HashSet<String>()
        val all = ArrayList<JSONObject>()
        for (i in 0 until cur.length()) {
            val o = cur.getJSONObject(i)
            if (have.add(o.getString("u"))) all.add(o)
        }
        val fresh = HashSet<String>()
        for (i in 0 until inc.length()) {
            val o = inc.getJSONObject(i)
            val u = o.getString("u")
            if (have.add(u)) {
                fresh.add(u)
                all.add(JSONObject().put("u", u).put("n", o.optString("n", "")).put("f", o.optBoolean("f", false)).put("t", o.optLong("t", 0L)))
            }
        }
        val starred = all.filter { it.optBoolean("f", false) }
        val recent = all.filter { !it.optBoolean("f", false) }.sortedByDescending { it.optLong("t", 0L) }.take(MAX_LINKS_RECENT)
        val arr = JSONArray()
        var added = 0
        for (o in starred + recent) { arr.put(o); if (fresh.contains(o.getString("u"))) added++ }
        return Pair(arr, added)
    }

    /** Lista de textos (enlaces de canales favoritos): unión sin repetir. */
    internal fun mergeStrings(cur: JSONArray, inc: JSONArray): Pair<JSONArray, Int> {
        val have = LinkedHashSet<String>()
        for (i in 0 until cur.length()) have.add(cur.getString(i))
        var added = 0
        for (i in 0 until inc.length()) if (have.add(inc.getString(i))) added++
        val arr = JSONArray()
        for (x in have) arr.put(x)
        return Pair(arr, added)
    }

    /** Lista de objetos con "u": lo que ya hay va primero y se añaden los que no estén; con tope de tamaño. */
    internal fun mergeObjects(cur: JSONArray, inc: JSONArray, max: Int): Pair<JSONArray, Int> {
        val have = HashSet<String>()
        val all = ArrayList<JSONObject>()
        for (i in 0 until cur.length()) {
            val o = cur.getJSONObject(i)
            if (have.add(o.getString("u"))) all.add(o)
        }
        val fresh = HashSet<String>()
        for (i in 0 until inc.length()) {
            val o = inc.getJSONObject(i)
            val u = o.getString("u")
            if (have.add(u)) { fresh.add(u); all.add(o) }
        }
        val arr = JSONArray()
        var added = 0
        for (o in all.take(max)) { arr.put(o); if (fresh.contains(o.getString("u"))) added++ }
        return Pair(arr, added)
    }
}
