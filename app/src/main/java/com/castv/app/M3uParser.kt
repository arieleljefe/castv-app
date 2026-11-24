package com.castv.app

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Un canal de una lista M3U. */
data class M3uChannel(val name: String, val url: String, val group: String, val logo: String = "") {
    /** [EPG-1] `tvg-id` del canal (para emparejar con la guía). Fuera del constructor: no cambia `equals` ni `copy`. */
    var tvgId: String = ""
}

/** Lee listas .m3u / .m3u8 de canales (formato #EXTINF). Solo se aceptan enlaces http(s). */
object M3uParser {

    private const val MAX_BYTES = 25 * 1024 * 1024

    private val groupRe = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)   // [REGIONES-1c]
    private val attrRe = Regex("[\\w-]+=\"[^\"]*\"")
    private val tvgIdRe = Regex("tvg-id=\"([^\"]*)\"", RegexOption.IGNORE_CASE)   // [EPG-1]
    private val tvgUrlRe = Regex("(?:url-tvg|x-tvg-url)=\"([^\"]*)\"", RegexOption.IGNORE_CASE)   // [EPG-1]

    fun parse(text: String): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        var name = ""
        var group = ""
        var extGroup = ""
        var logo = ""
        var tvgId = ""   // [EPG-1]
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                group = groupRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                logo = logoRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                tvgId = tvgIdRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()   // [EPG-1]
                name = line.replace(attrRe, "").substringAfter(',', "").trim()
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                extGroup = line.substringAfter(':').trim()
            } else if (line.startsWith("#")) {
                continue
            } else {
                val low = line.lowercase()
                if (low.startsWith("http://") || low.startsWith("https://")) {
                    val g = if (group.isNotEmpty()) group else extGroup
                    val n = if (name.isNotEmpty()) name
                            else line.substringBefore('?').trimEnd('/').substringAfterLast('/')
                    out.add(M3uChannel(n, line, if (g.isEmpty()) "Sin grupo" else g, logo).also { it.tvgId = tvgId })   // [EPG-1]
                }
                name = ""
                group = ""
                extGroup = ""
                logo = ""
                tvgId = ""   // [EPG-1]
            }
        }
        return out
    }

    /** [EPG-1] Enlaces de guía XMLTV de la cabecera (`#EXTM3U url-tvg="…"`, varios separados por coma); por defecto máximo 3 ([EPG-1b]: [limit] sube el tope). */
    fun epgUrls(text: String, limit: Int = 3): List<String> {
        var seen = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                val v = tvgUrlRe.find(line)?.groupValues?.get(1).orEmpty()
                return v.split(',').map { it.trim() }
                    .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    .take(limit)
            }
            if (++seen >= 5) break
        }
        return emptyList()
    }

    /** [EPG-REGION-1] Une las guías de las cabeceras de varias listas, sin repetidas y en el orden de las listas. */
    fun mergeGuides(heads: List<List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        for (h in heads) out.addAll(h)
        return out.toList()
    }

    /** [EPG-1b] País de un `tvg-id` (`Rai1.it` → `it`, `Canal.es@SD` → `es`); "" si no trae. */
    fun idCountry(tvgId: String): String {
        val id = tvgId.trim().substringBefore('@')
        val i = id.lastIndexOf('.')
        if (i < 0 || i == id.length - 1) return ""
        val cc = id.substring(i + 1).lowercase()
        return if (cc.length in 2..3 && cc.all { it in 'a'..'z' }) cc else ""
    }

    /** [EPG-1b] Nombre corto de una guía (archivo sin parámetros, `.gz`, `.xml` ni `epg_ripper_`), para el /log. */
    fun guideName(url: String): String {
        var n = url.trim().substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        for (ext in listOf(".gz", ".xml")) {
            if (n.lowercase().endsWith(ext)) n = n.substring(0, n.length - ext.length)
        }
        return n.removePrefix("epg_ripper_")
    }

    /** [EPG-1b] Códigos de país que lleva el nombre de una guía (`IT1` → it, `US_LOCALS1` → us). */
    private fun guideCodes(url: String): Set<String> {
        val out = HashSet<String>()
        for (tok in guideName(url).split(Regex("[^A-Za-z0-9]+"))) {
            val letters = tok.trimEnd { it.isDigit() }.lowercase()
            if (letters.length in 2..3) out.add(letters)
        }
        return out
    }

    /**
     * [EPG-1b] Con una cabecera de más de 3 guías (p. ej. Free-TV trae más de 100), elige las de los países
     * que más pesan en los `tvg-id` de los canales: hasta 3 países (con al menos 3 canales), hasta 2 guías por
     * país y [max] en total. Sin coincidencias (o con 3 guías o menos) se queda como antes: las 3 primeras.
     */
    fun pickGuides(urls: List<String>, channels: List<M3uChannel>, max: Int = 6): List<String> {
        if (urls.size <= 3) return urls
        val count = HashMap<String, Int>()
        for (c in channels) {
            val cc = idCountry(c.tvgId)
            if (cc.isNotEmpty()) count[cc] = (count[cc] ?: 0) + 1
        }
        val top = count.toList().filter { it.second >= 3 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(3).map { it.first }
        val out = ArrayList<String>()
        for (cc in top) {
            var n = 0
            for (u in urls) {
                if (out.size >= max || n >= 2) break
                if (!out.contains(u) && guideCodes(u).contains(cc)) { out.add(u); n++ }
            }
        }
        return if (out.isEmpty()) urls.take(3) else out
    }

    /** Descarga una lista (sigue hasta 5 redirecciones, incluso de http a https). Lanza excepción si falla. */
    fun download(url: String): String {
        var cur = url
        for (hop in 0..5) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw Exception("redirección sin destino")
                cur = URL(URL(cur), loc).toString()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw Exception("HTTP $code")
            }
            val bos = ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(16384)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    if (bos.size() > MAX_BYTES) throw Exception("lista demasiado grande")
                }
            }
            c.disconnect()
            return String(bos.toByteArray(), Charsets.UTF_8).removePrefix("﻿")
        }
        throw Exception("demasiadas redirecciones")
    }
}
