package com.castv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TESTS-1] Pruebas de M3uParser.parse (lectura de listas .m3u). */
class M3uParserTest {

    @Test
    fun canal_completo_con_grupo_y_logo() {
        val txt = """
            #EXTM3U
            #EXTINF:-1 tvg-id="x" tvg-logo="http://l.tv/logo.png" group-title="Noticias",Canal Uno
            http://s.tv/uno.m3u8
        """.trimIndent()
        assertEquals(
            listOf(M3uChannel("Canal Uno", "http://s.tv/uno.m3u8", "Noticias", "http://l.tv/logo.png")),
            M3uParser.parse(txt)
        )
    }

    @Test
    fun sin_informacion_usa_el_final_del_enlace_y_sin_grupo() {
        val out = M3uParser.parse("#EXTM3U\nhttp://s.tv/path/canal2.m3u8?x=1\n")
        assertEquals(listOf(M3uChannel("canal2.m3u8", "http://s.tv/path/canal2.m3u8?x=1", "Sin grupo", "")), out)
    }

    @Test
    fun grupo_con_extgrp() {
        val txt = "#EXTINF:-1,Canal Tres\n#EXTGRP:Deportes\nhttp://s.tv/3.m3u8\n"
        assertEquals(listOf(M3uChannel("Canal Tres", "http://s.tv/3.m3u8", "Deportes", "")), M3uParser.parse(txt))
    }

    @Test
    fun los_datos_de_un_canal_no_pasan_al_siguiente() {
        val txt = "#EXTINF:-1 group-title=\"A\",Uno\nhttp://s.tv/1\nhttp://s.tv/2\n"
        assertEquals(
            listOf(
                M3uChannel("Uno", "http://s.tv/1", "A", ""),
                M3uChannel("2", "http://s.tv/2", "Sin grupo", "")
            ),
            M3uParser.parse(txt)
        )
    }

    @Test
    fun solo_se_aceptan_enlaces_http_y_https() {
        val txt = "rtmp://s.tv/a\nftp://s.tv/b\nfile:///c\nHTTPS://s.tv/d.m3u8\n"
        val out = M3uParser.parse(txt)
        assertEquals(1, out.size)
        assertEquals("HTTPS://s.tv/d.m3u8", out[0].url)
    }

    @Test
    fun lineas_vacias_y_saltos_de_windows() {
        val txt = "#EXTM3U\r\n\r\n#EXTINF:-1,Canal Win\r\nhttp://s.tv/w.m3u8\r\n\r\n"
        assertEquals(listOf(M3uChannel("Canal Win", "http://s.tv/w.m3u8", "Sin grupo", "")), M3uParser.parse(txt))
    }

    @Test
    fun texto_vacio_o_sin_enlaces() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("esto no es una lista\n#EXTM3U\n").isEmpty())
    }
}
