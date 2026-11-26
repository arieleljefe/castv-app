package com.castv.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** [TESTS-2] Pruebas del respaldo (sin Android): qué se guarda, qué claves se quitan y cómo se unen los parciales. */
class BackupTest {
    private val leaks: (String) -> Boolean = { it.contains("xtream") }

    @Test fun clavesQueSeRespaldan() {
        assertFalse(Backup.keep("castv", "tvIp"))
        assertFalse(Backup.keep("castv", "pin_hash"))
        assertTrue(Backup.keep("castv", "theme"))
        assertTrue(Backup.keep("castv_stats", "v|123"))
        assertFalse(Backup.keep("castv_stats", "t|123"))
        assertTrue(Backup.keep("castv_lib", "links"))
    }

    @Test fun scrubQuitaEnlacesConClave() {
        val v = JSONArray().put("http://ok.com/a").put("http://xtream.x/get").toString()
        val out = JSONArray(Backup.scrub("chfav", v, leaks))
        assertEquals(1, out.length())
        assertEquals("http://ok.com/a", out.getString(0))
    }

    @Test fun scrubEnObjetosConU() {
        val v = JSONArray()
            .put(JSONObject().put("u", "http://ok.com/a").put("n", "A"))
            .put(JSONObject().put("u", "http://xtream.x/b").put("n", "B")).toString()
        val out = JSONArray(Backup.scrub("links", v, leaks))
        assertEquals(1, out.length())
        assertEquals("A", out.getJSONObject(0).getString("n"))
    }

    @Test fun scrubDeSalud() {
        val v = JSONObject().put("http://ok.com/a", 1).put("http://xtream.x/b", 2).toString()
        val out = JSONObject(Backup.scrub("chhealth", v, leaks))
        assertEquals(1, out.length())
        assertTrue(out.has("http://ok.com/a"))
    }

    @Test fun scrubQuitaNotasConClave() {   // [SEGURIDAD-RESP-1]
        val v = JSONObject().put("http://ok.com/a", "nota 1").put("http://xtream.x/b", "nota 2").toString()
        val out = JSONObject(Backup.scrub("chnotes", v, leaks))
        assertEquals(1, out.length())
        assertEquals("nota 1", out.getString("http://ok.com/a"))
    }

    @Test fun scrubQuitaRespaldosConClave() {   // [SEGURIDAD-RESP-1]
        val alt = { u: String -> JSONObject().put("n", "X").put("u", u).put("g", "").put("l", "") }
        val v = JSONObject()
            .put("http://ok.com/a", JSONArray().put(alt("http://ok.com/b")).put(alt("http://xtream.x/c")))
            .put("http://xtream.x/d", JSONArray().put(alt("http://ok.com/e")))
            .put("http://ok.com/f", JSONArray().put(alt("http://xtream.x/g"))).toString()
        val out = JSONObject(Backup.scrub("chalts", v, leaks))
        assertEquals(1, out.length())   // la clave con clave se va; la que se queda sin respaldos también
        val l = out.getJSONArray("http://ok.com/a")
        assertEquals(1, l.length())
        assertEquals("http://ok.com/b", l.getJSONObject(0).getString("u"))
    }

    @Test fun scrubDeRespaldosRotos() {   // [SEGURIDAD-RESP-1]
        assertEquals("{}", Backup.scrub("chalts", "roto xtream", leaks))
        assertEquals("{}", Backup.scrub("chnotes", "roto xtream", leaks))
        assertEquals("roto", Backup.scrub("chnotes", "roto", leaks))
    }

    @Test fun scrubNoTocaOtrasClaves() {
        assertEquals("xtream", Backup.scrub("tema", "xtream", leaks))
    }

    @Test fun scrubConJsonRoto() {
        assertEquals("[]", Backup.scrub("chfav", "no es json xtream", leaks))
        assertEquals("{}", Backup.scrub("chhealth", "roto xtream", leaks))
        assertEquals("roto", Backup.scrub("chfav", "roto", leaks))
    }

    @Test fun unirTextosSinRepetir() {
        val cur = JSONArray().put("a").put("b")
        val inc = JSONArray().put("b").put("c")
        val r = Backup.mergeStrings(cur, inc)
        assertEquals(1, r.second)
        assertEquals(3, r.first.length())
        assertEquals("a", r.first.getString(0))
    }

    @Test fun unirEnlacesConservaLoQueHay() {
        val cur = JSONArray().put(JSONObject().put("u", "http://a").put("n", "A").put("f", true).put("t", 5L))
        val inc = JSONArray()
            .put(JSONObject().put("u", "http://a").put("n", "otro"))
            .put(JSONObject().put("u", "http://b").put("n", "B").put("t", 9L))
        val r = Backup.mergeLinks(cur, inc)
        assertEquals(1, r.second)
        assertEquals(2, r.first.length())
        assertEquals("A", r.first.getJSONObject(0).getString("n"))   // el favorito va primero y no se pisa
    }

    @Test fun unirObjetosConTope() {
        val cur = JSONArray().put(JSONObject().put("u", "x1")).put(JSONObject().put("u", "x2"))
        val inc = JSONArray().put(JSONObject().put("u", "x3")).put(JSONObject().put("u", "x1"))
        val r = Backup.mergeObjects(cur, inc, 2)
        assertEquals(2, r.first.length())   // tope
        assertEquals(0, r.second)           // lo nuevo quedó fuera del tope
        val r2 = Backup.mergeObjects(cur, inc, 10)
        assertEquals(3, r2.first.length())
        assertEquals(1, r2.second)
    }

    @Test fun describeReconoceElTipo() {
        assertNull(Backup.describe("no es json"))
        assertNull(Backup.describe("{\"app\":\"otra\"}"))
        assertEquals("full", Backup.describe("{\"app\":\"castv\",\"v\":1}"))
        assertEquals("part", Backup.describe("{\"app\":\"castv\",\"v\":2,\"part\":\"rara\"}"))
    }

    @Test fun leerConLimite() {
        assertEquals("hola", Backup.readLimited(ByteArrayInputStream("hola".toByteArray()), 10))
        assertNull(Backup.readLimited(ByteArrayInputStream(ByteArray(100)), 10))
    }
}
