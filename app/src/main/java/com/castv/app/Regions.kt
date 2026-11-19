package com.castv.app

/** [REGIONES-1a] Agrupa las listas M3U por región o categoría según su enlace (iptv-org: /countries/xx.m3u y /categories/xx.m3u). */
object Regions {
    const val LATAM = "🌎 Latinoamérica"
    const val NORTE = "🇺🇸 Norteamérica"
    const val EUROPA = "🇪🇺 Europa"
    const val ASIA = "🌏 Asia y Medio Oriente"
    const val AFRICA_OCEANIA = "🌍 África y Oceanía"
    const val CATEG = "🎬 Categorías"
    const val OTRAS = "📋 Mis listas y otras"

    /** Orden en que se muestran las regiones. */
    val ORDER = listOf(LATAM, NORTE, EUROPA, ASIA, AFRICA_OCEANIA, CATEG, OTRAS)

    private val byCountry: Map<String, String> = HashMap<String, String>().apply {
        for (c in "do ar bo br cl co cr cu ec sv gt hn mx ni pa py pe pr uy ve".split(' ')) put(c, LATAM)
        for (c in "us ca".split(' ')) put(c, NORTE)
        for (c in "es fr gb de it ru pt".split(' ')) put(c, EUROPA)
        for (c in "jp kr cn in ae".split(' ')) put(c, ASIA)
        for (c in "za au".split(' ')) put(c, AFRICA_OCEANIA)
    }

    private val pathRe = Regex("/(countries|categories)/([a-z0-9_-]+)\\.m3u", RegexOption.IGNORE_CASE)

    /** Región de una lista; cualquier enlace que no siga el formato (Free-TV, listas propias) va a "Mis listas y otras". */
    fun regionOf(url: String): String {
        val m = pathRe.find(url) ?: return OTRAS
        if (m.groupValues[1].equals("categories", ignoreCase = true)) return CATEG
        return byCountry[m.groupValues[2].lowercase()] ?: OTRAS
    }
}
