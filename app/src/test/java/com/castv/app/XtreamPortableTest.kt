package com.castv.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TESTS-3] Pruebas de los enlaces «portátiles» de canales Xtream (XTFAV-1), sin Android. */
class XtreamPortableTest {
    private val srv = "http://srv.com:8080"
    private val ls = listOf(
        Xtream.Login("ab12cd34", srv, "juan", "clave1"),
        Xtream.Login("ef56gh78", "http://otro.net", "ana lopez", "p@ss")
    )

    @Test fun enlaceRealPasaAPortatil() {
        assertEquals("xtream://ab12cd34/live/123.m3u8", Xtream.toPortable("$srv/live/juan/clave1/123.m3u8", ls))
        assertEquals("xtream://ab12cd34/movie/7.mp4", Xtream.toPortable("$srv/movie/juan/clave1/7.mp4", ls))
    }

    @Test fun hostDistintoSeConserva() {
        assertEquals(
            "xtream://ab12cd34@http://espejo.com/live/123.m3u8",
            Xtream.toPortable("http://espejo.com/live/juan/clave1/123.m3u8", ls)
        )
    }

    @Test fun hostSeComparaSinMayusculas() {
        assertEquals("xtream://ab12cd34/live/5.ts", Xtream.toPortable("HTTP://SRV.COM:8080/live/juan/clave1/5.ts", ls))
    }

    @Test fun usuarioCodificadoTambienCoincide() {
        assertEquals("xtream://ef56gh78/live/9.ts", Xtream.toPortable("http://otro.net/live/ana%20lopez/p%40ss/9.ts", ls))
    }

    @Test fun sinAccesoConocidoNoSeToca() {
        assertNull(Xtream.toPortable("$srv/live/otro/clave1/123.m3u8", ls))
        assertNull(Xtream.toPortable("$srv/live/juan/otraclave/123.m3u8", ls))
        assertNull(Xtream.toPortable("http://ok.com/lista/canal.m3u8", ls))
        assertNull(Xtream.toPortable("$srv/live/juan/clave1/123.m3u8", emptyList()))
    }

    @Test fun portatilVuelveAEnlaceReal() {
        assertEquals("$srv/live/juan/clave1/123.m3u8", Xtream.fromPortable("xtream://ab12cd34/live/123.m3u8", ls))
        assertEquals(
            "http://espejo.com/live/juan/clave1/123.m3u8",
            Xtream.fromPortable("xtream://ab12cd34@http://espejo.com/live/123.m3u8", ls)
        )
    }

    @Test fun idaYVueltaDaElMismoEnlace() {
        for (u in listOf("$srv/live/juan/clave1/123.m3u8", "http://espejo.com/series/juan/clave1/88.mkv")) {
            assertEquals(u, Xtream.fromPortable(Xtream.toPortable(u, ls)!!, ls))
        }
    }

    @Test fun fromPortableRechazaLoQueNoEs() {
        assertNull(Xtream.fromPortable("http://ok.com/a.m3u8", ls))
        assertNull(Xtream.fromPortable("xtream://noexiste/live/1.m3u8", ls))   // otro teléfono: sin acceso con ese id
        assertNull(Xtream.fromPortable("xtream://ab12cd34", ls))               // es una lista, no un canal
        assertNull(Xtream.fromPortable("xtream://ab12cd34/otra/cosa", ls))
    }

    @Test fun mapUrlsEnTextosYObjetos() {
        val favs = JSONArray().put("$srv/live/juan/clave1/1.m3u8").put("http://ok.com/a.m3u8").toString()
        val a = JSONArray(Xtream.portabilize("chfav", favs, ls))
        assertEquals("xtream://ab12cd34/live/1.m3u8", a.getString(0))
        assertEquals("http://ok.com/a.m3u8", a.getString(1))

        val links = JSONArray().put(JSONObject().put("u", "$srv/live/juan/clave1/2.m3u8").put("n", "Canal").put("t", 5L)).toString()
        val o = JSONArray(Xtream.portabilize("links", links, ls)).getJSONObject(0)
        assertEquals("xtream://ab12cd34/live/2.m3u8", o.getString("u"))
        assertEquals("Canal", o.getString("n"))   // el resto de campos no se pierde
        assertEquals(5L, o.getLong("t"))
    }

    @Test fun portabilizeNoTocaOtrasClavesNiSinAccesos() {
        val v = JSONArray().put("$srv/live/juan/clave1/1.m3u8").toString()
        assertEquals(v, Xtream.portabilize("chhealth", v, ls))   // «chhealth» se descarta, no se convierte
        assertEquals(v, Xtream.portabilize("chfav", v, emptyList()))
        assertFalse("chhealth" in Xtream.PORTABLE_KEYS)
        assertTrue(setOf("chfav", "chfavmeta", "chhidden", "links", "chrecent").all { it in Xtream.PORTABLE_KEYS })
    }

    @Test fun jsonRotoOSinCampoUSeDevuelveIgual() {
        assertEquals("no es json", Xtream.mapUrls("chfav", "no es json") { "X" })
        val sinU = JSONArray().put(JSONObject().put("n", "A")).toString()
        assertEquals(sinU, Xtream.mapUrls("links", sinU) { "X" })
    }
}
