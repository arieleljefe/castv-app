package com.castv.app

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Un canal de una lista M3U. */
data class M3uChannel(val name: String, val url: String, val group: String, val logo: String = "")

/** Lee listas .m3u / .m3u8 de canales (formato #EXTINF). Solo se aceptan enlaces http(s). */
object M3uParser {

    private const val MAX_BYTES = 25 * 1024 * 1024

    private val groupRe = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)   // [REGIONES-1c]
    private val attrRe = Regex("[\\w-]+=\"[^\"]*\"")

    fun parse(text: String): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        var name = ""
        var group = ""
        var extGroup = ""
        var logo = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                group = groupRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                logo = logoRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()
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
                    out.add(M3uChannel(n, line, if (g.isEmpty()) "Sin grupo" else g, logo))
                }
                name = ""
                group = ""
                extGroup = ""
                logo = ""
            }
        }
        return out
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
