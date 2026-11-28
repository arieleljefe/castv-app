package com.castv.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * [TALLER-1] Reconoce por el nombre lo que la propia app generó (cortes, partes, uniones, reducidos, sin audio)
 * y guarda, en preferencias propias (`castv_work`, fuera de respaldos, R9), solo los proyectos que se quitan de la lista.
 * No toca ni borra ningún archivo.
 */
object Workshop {
    /** Un proyecto: un archivo (corte, unión, reducido, sin audio) o todas las partes de un video dividido. */
    class Project(
        val key: String,
        val tool: String,
        val base: String,
        val parts: List<MediaItem>,
        val newest: Long,
        val total: Int
    ) {
        var bytes: Long = 0L   // [TALLER-2] peso de todas sus partes (se llena al buscar)
    }

    private val suffixes = listOf(
        " (corte)" to "✂️ Cortado",
        " (unido)" to "🔗 Unido",
        " (reducido)" to "📉 Reducido",
        " (sin audio)" to "🔇 Sin audio"
    )
    private val partRe = Regex(" \\(parte (\\d+) de (\\d+)\\)")

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("castv_work", Context.MODE_PRIVATE)

    fun hidden(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("hidden", emptySet()) ?: emptySet()

    fun hide(ctx: Context, key: String) {
        val s = HashSet(hidden(ctx))
        s.add(key)
        prefs(ctx).edit().putStringSet("hidden", s).apply()
    }

    fun clearHidden(ctx: Context) {
        prefs(ctx).edit().remove("hidden").apply()
    }

    /** [TALLER-1b] Registro de lo guardado sin sufijo en el nombre (MP3 convertidos, descargas): enlace del archivo, herramienta, nombre y fecha. */
    class Rec(val uri: String, val tool: String, val name: String, val ms: Long)

    fun records(ctx: Context): List<Rec> {
        val raw = prefs(ctx).getString("records", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { val o = a.getJSONObject(it); Rec(o.getString("u"), o.getString("t"), o.getString("n"), o.getLong("d")) }
        } catch (e: Exception) { emptyList() }
    }

    /** Anota un archivo guardado por la app (últimos 100). Lo llaman `SavedFile.offer` y `LinkJobNotifier.done`. No guarda enlaces de origen (R9). */
    @Synchronized
    fun record(ctx: Context, icon: String, name: String, uri: Uri?) {
        if (uri == null) return
        try {
            val tool = when (icon) {
                "🎵" -> "🎵 MP3"
                "⬇" -> "⬇ Descargado"
                else -> return   // lo demás ya se reconoce por el nombre
            }
            val list = ArrayList(records(ctx).filter { it.uri != uri.toString() })
            list.add(0, Rec(uri.toString(), tool, name, System.currentTimeMillis()))
            val a = JSONArray()
            for (r in list.take(100)) a.put(JSONObject().put("u", r.uri).put("t", r.tool).put("n", r.name).put("d", r.ms))
            prefs(ctx).edit().putString("records", a.toString()).apply()
        } catch (e: Exception) { }
    }

    /** [TALLER-2] Peso en bytes de todas las partes de un proyecto (0 si no se puede leer). */
    fun sizeOf(ctx: Context, p: Project): Long {
        var sum = 0L
        for (m in p.parts) {
            try {
                val base = if (m.isMusic) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ctx.contentResolver.query(ContentUris.withAppendedId(base, m.id), arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) sum += c.getLong(0)
                }
            } catch (e: Exception) { }
        }
        return sum
    }

    private fun partNo(m: MediaItem): Int =
        partRe.find(m.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Busca en la biblioteca lo que CASTV generó; lo más nuevo primero. */
    fun detect(items: List<MediaItem>, hidden: Set<String>, recs: List<Rec> = emptyList()): List<Project> {
        val out = ArrayList<Project>()
        val groups = LinkedHashMap<String, MutableList<MediaItem>>()
        val bases = HashMap<String, String>()
        for (m in items) {
            val n = m.name
            val pm = partRe.find(n)
            if (pm != null) {
                val base = n.substring(0, pm.range.first)
                val key = "p|" + (if (m.isMusic) "a" else "v") + "|" + base.lowercase() + "|" + pm.groupValues[2]
                groups.getOrPut(key) { ArrayList() }.add(m)
                bases[key] = base
                continue
            }
            for ((suf, label) in suffixes) {
                val i = n.indexOf(suf)
                if (i > 0) {
                    val key = "c|" + Store.key(m)
                    if (key !in hidden) out.add(Project(key, label, n.substring(0, i), listOf(m), m.dateAdded.toLong(), 1))
                    break
                }
            }
        }
        // [TALLER-1b] MP3 y descargas registrados: se busca su archivo por el número de MediaStore (sin repetir lo ya reconocido)
        val seen = HashSet<Long>()
        for (p in out) for (m in p.parts) seen.add(m.id)
        for (g in groups.values) for (m in g) seen.add(m.id)
        for (r in recs) {
            val id = try { ContentUris.parseId(Uri.parse(r.uri)) } catch (e: Exception) { continue }
            val music = r.uri.contains("/audio/")
            val m = items.firstOrNull { it.id == id && it.isMusic == music } ?: continue
            val key = "r|" + r.uri
            if (key in hidden || !seen.add(m.id)) continue
            out.add(Project(key, r.tool, r.name.substringBeforeLast('.'), listOf(m), r.ms, 1))
        }
        for ((key, list) in groups) {
            if (key in hidden) continue
            val sorted = list.sortedBy { partNo(it) }
            val total = key.substringAfterLast('|').toIntOrNull() ?: sorted.size
            out.add(Project(key, "📦 Dividido", bases[key] ?: "", sorted, sorted.maxOf { it.dateAdded.toLong() }, total))
        }
        out.sortByDescending { it.newest }
        return out
    }
}
