package com.castv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [EPG-1b] Pruebas de la elección de guías por país (sin Android). */
class EpgPickTest {
    private fun g(code: String) = "https://epgshare01.online/epgshare01/epg_ripper_$code.xml.gz"

    private fun ch(id: String): M3uChannel = M3uChannel("c", "http://x/$id", "g").also { it.tvgId = id }

    private fun many(id: String, n: Int): List<M3uChannel> = (1..n).map { ch(id) }

    private val header = listOf("AL1", "DE1", "ES1", "FR1", "IT1", "IT2", "US1", "UK1").map { g(it) }

    @Test fun paisDelId() {
        assertEquals("it", M3uParser.idCountry("Rai1.it"))
        assertEquals("es", M3uParser.idCountry("La1.es@SD"))
        assertEquals("", M3uParser.idCountry("SinPais"))
        assertEquals("", M3uParser.idCountry("Canal."))
        assertEquals("", M3uParser.idCountry(""))
        assertEquals("", M3uParser.idCountry("Algo.1234"))
    }

    @Test fun nombreDeGuia() {
        assertEquals("IT1", M3uParser.guideName(g("IT1")))
        assertEquals("guia", M3uParser.guideName("https://ejemplo.com/a/guia.xml?x=1"))
        assertEquals("tv", M3uParser.guideName("https://ejemplo.com/tv.gz"))
    }

    @Test fun eligeElPaisDeLosCanales() {
        val chs = many("Rai1.it", 6) + many("La1.es", 2)
        // es solo tiene 2 canales: no cuenta; it tiene 6 y sus 2 guías entran
        assertEquals(listOf(g("IT1"), g("IT2")), M3uParser.pickGuides(header, chs))
    }

    @Test fun variosPaisesPorPeso() {
        val chs = many("a.es", 5) + many("b.it", 9) + many("c.fr", 4)
        val got = M3uParser.pickGuides(header, chs)
        assertEquals(listOf(g("IT1"), g("IT2"), g("ES1"), g("FR1")), got)
    }

    @Test fun topeDeGuias() {
        val ids = listOf("de", "es", "fr", "it", "uk", "us")
        val chs = ids.flatMap { many("x.$it", 5) }
        val got = M3uParser.pickGuides(header, chs, 6)
        assertTrue(got.size <= 6)
        assertTrue(got.size >= 3)
    }

    @Test fun pocasGuiasSeQuedanComoAntes() {
        val tres = header.take(3)
        assertEquals(tres, M3uParser.pickGuides(tres, many("a.it", 9)))
    }

    @Test fun sinCoincidenciaSonLasTresPrimeras() {
        assertEquals(header.take(3), M3uParser.pickGuides(header, many("a.pt", 9)))
        assertEquals(header.take(3), M3uParser.pickGuides(header, many("SinPais", 9)))
        assertEquals(header.take(3), M3uParser.pickGuides(header, emptyList()))
    }

    @Test fun cabeceraConMuchasGuias() {
        val text = "#EXTM3U x-tvg-url=\"" + header.joinToString(", ") + "\"\n#EXTINF:-1,a\nhttp://x/1\n"
        assertEquals(3, M3uParser.epgUrls(text).size)
        assertEquals(header, M3uParser.epgUrls(text, 300))
    }

    // [EPG-REGION-1] guías de varias listas de una región
    @Test fun unirGuiasSinRepetirYEnOrden() {
        val a = listOf(g("IT1"), g("IT2"))
        val b = listOf(g("IT2"), g("ES1"))
        assertEquals(listOf(g("IT1"), g("IT2"), g("ES1")), M3uParser.mergeGuides(listOf(a, b)))
        assertEquals(emptyList<String>(), M3uParser.mergeGuides(listOf(emptyList(), emptyList())))
        assertEquals(emptyList<String>(), M3uParser.mergeGuides(emptyList()))
    }
}
