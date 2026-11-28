package com.castv.app

import android.content.Context

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
    )

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

    private fun partNo(m: MediaItem): Int =
        partRe.find(m.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Busca en la biblioteca lo que CASTV generó; lo más nuevo primero. */
    fun detect(items: List<MediaItem>, hidden: Set<String>): List<Project> {
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
