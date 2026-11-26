package com.castv.app

import android.content.Context
import java.io.File
import java.text.Normalizer

/**
 * [AUTO-FUENTE-1] Busca otra fuente del mismo canal en las listas que ya están guardadas en `cacheDir`
 * (no descarga nada) y prueba cuál se puede preparar para el TV. Se usa cuando un canal no se puede preparar:
 * en vez de avisar de inmediato, se intenta con otra fuente del mismo nombre.
 * Todo esto va en hilo de fondo (lee archivos y abre conexiones).
 */
object AltSource {
    private val TAGS = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265", "h264", "1080p", "720p", "576p", "480p", "360p")
    private val MARKS = Regex("\\p{Mn}+")
    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?\\]")
    private val NONWORD = Regex("[^\\p{L}\\p{N}]+")

    /** Nombre sin acentos, paréntesis ni etiquetas de calidad (HD, FHD, 4K, 1080p…), para reconocer el mismo canal en otra lista. */
    fun base(name: String): String {
        val n = MARKS.replace(Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD), "")
        val s = NONWORD.replace(BRACKETS.replace(n, " "), " ")
        val t = s.split(' ').filter { it.isNotEmpty() && !TAGS.contains(it) }.joinToString(" ")
        return if (t.isNotEmpty()) t else s.trim()
    }

    /** Otras fuentes de [name] (hasta [max]), distintas de [url]: salen de las listas guardadas; sin Xtream ni ocultos. */
    fun find(ctx: Context, name: String, url: String, max: Int = 6): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        val seen = HashSet<String>()
        seen.add(url)
        val hid = Store.hiddenUrls()
        val want = base(name)
        if (want.isEmpty()) return out
        val leak = Xtream.leakFilter(ctx)
        for (l in Store.m3uLists()) {
            if (out.size >= max) break
            if (Xtream.isXtream(l.url)) continue
            val f = File(ctx.cacheDir, "m3u_" + l.url.hashCode() + ".txt")
            if (!f.exists()) continue
            try {
                for (x in M3uParser.parse(f.readText())) {
                    if (out.size >= max) break
                    if (hid.contains(x.url) || leak(x.url) || !seen.add(x.url)) continue
                    if (base(x.name) == want) out.add(x)
                }
            } catch (e: Exception) { }
        }
        return out
    }

    /** Prueba hasta [tries] otras fuentes y devuelve la primera que se puede preparar (HLS: `probeLive`; otros enlaces: `checkChannel`), o null. */
    fun firstWorking(ctx: Context, name: String, url: String, tries: Int = 3): M3uChannel? {
        var n = 0
        for (c in find(ctx, name, url, tries * 2)) {
            if (n >= tries) break
            n++
            val hls = c.url.substringBefore('?').lowercase().endsWith(".m3u8")
            val why = if (hls) MediaServer.probeLive(c.url) else MediaServer.checkChannel(c.url)
            if (why == null) return c
            Store.markChFail(c.url)
            DebugLog.add("AUTO-FUENTE: ${c.name} tampoco sirve: $why")
        }
        return null
    }
}
