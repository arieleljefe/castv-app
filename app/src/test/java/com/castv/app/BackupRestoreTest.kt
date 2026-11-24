package com.castv.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TESTS-4] SharedPreferences en memoria (sin Android) para probar Backup.build/restore y la unión con Xtream. */
class TestPrefs : SharedPreferences {
    val map = LinkedHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap<String, Any?>(map)
    private fun has(key: String?): Boolean = key != null && map.containsKey(key)
    override fun getString(key: String?, defValue: String?): String? =
        if (has(key)) map[key!!] as String? else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (has(key)) map[key!!] as MutableSet<String>? else defValues
    override fun getInt(key: String?, defValue: Int): Int = if (has(key)) map[key!!] as Int else defValue
    override fun getLong(key: String?, defValue: Long): Long = if (has(key)) map[key!!] as Long else defValue
    override fun getFloat(key: String?, defValue: Float): Float = if (has(key)) map[key!!] as Float else defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = if (has(key)) map[key!!] as Boolean else defValue
    override fun contains(key: String?): Boolean = has(key)
    override fun edit(): SharedPreferences.Editor = TestEditor(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

class TestEditor(private val p: TestPrefs) : SharedPreferences.Editor {
    private val ops = ArrayList<() -> Unit>()
    override fun putString(key: String?, value: String?): SharedPreferences.Editor { ops.add { p.map[key!!] = value }; return this }
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { ops.add { p.map[key!!] = values }; return this }
    override fun putInt(key: String?, value: Int): SharedPreferences.Editor { ops.add { p.map[key!!] = value }; return this }
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor { ops.add { p.map[key!!] = value }; return this }
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { ops.add { p.map[key!!] = value }; return this }
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { ops.add { p.map[key!!] = value }; return this }
    override fun remove(key: String?): SharedPreferences.Editor { ops.add { p.map.remove(key!!) }; return this }
    override fun clear(): SharedPreferences.Editor { ops.add { p.map.clear() }; return this }
    override fun commit(): Boolean { for (op in ops) op(); ops.clear(); return true }
    override fun apply() { commit() }
}

/** Un «teléfono» de mentira: cada nombre de preferencias es un TestPrefs en memoria. */
class TestCtx : ContextWrapper(null) {
    private val files = HashMap<String, TestPrefs>()
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = files.getOrPut(name ?: "") { TestPrefs() }
    override fun getApplicationContext(): Context = this
    fun prefs(name: String): TestPrefs = getSharedPreferences(name, 0) as TestPrefs
}

class BackupRestoreTest {
    private val srv = "http://srv.com:8080"
    private val real1 = "$srv/live/juan/clave1/1.m3u8"
    private val real2 = "$srv/live/juan/clave1/2.m3u8"
    private val real3 = "$srv/live/juan/clave1/3.m3u8"
    private val port1 = "xtream://ab12cd34/live/1.m3u8"
    private val NEWER = "El respaldo es de una versión más nueva de CASTV. Actualiza la app."

    private fun s(v: String) = JSONObject().put("t", "s").put("v", v)
    private fun mk(vararg e: Pair<String, JSONObject>): JSONObject { val o = JSONObject(); for ((k, v) in e) o.put(k, v); return o }
    private fun arr(vararg x: String): String { val a = JSONArray(); for (i in x) a.put(i); return a.toString() }
    private fun strs(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }
    private fun lib(c: TestCtx, k: String): String = c.prefs("castv_lib").getString(k, null)!!
    private fun full(vararg files: Pair<String, JSONObject>): String =
        JSONObject().put("app", "castv").put("v", 1).put("prefs", mk(*files)).toString()
    private fun part(name: String, vararg kv: Pair<String, String>): String =
        JSONObject().put("app", "castv").put("v", 2).put("part", name)
            .put("prefs", JSONObject().put("castv_lib", mk(*kv.map { it.first to s(it.second) }.toTypedArray()))).toString()
    private fun withLogin(c: TestCtx) {
        c.prefs("castv_xt").map["ab12cd34"] = JSONObject().put("s", srv).put("u", "juan").put("p", "clave1").toString()
    }

    // ------------------------------------------------- restore: validación

    @Test fun rechazaLoQueNoEsUnRespaldo() {
        val c = TestCtx()
        assertEquals("El archivo no es un respaldo válido.", Backup.restore(c, "hola"))
        assertEquals("Este archivo no es un respaldo de CASTV.", Backup.restore(c, """{"app":"otra","v":1}"""))
        assertEquals("El respaldo no tiene una versión válida.", Backup.restore(c, """{"app":"castv","v":0}"""))
        assertEquals(NEWER, Backup.restore(c, """{"app":"castv","v":3,"prefs":{}}"""))
        assertEquals(NEWER, Backup.restore(c, """{"app":"castv","v":2,"prefs":{"castv":{}}}"""))   // v2 sin «part»
        assertEquals("El respaldo está vacío.", Backup.restore(c, """{"app":"castv","v":1}"""))
        assertEquals("El respaldo está vacío.", Backup.restore(c, """{"app":"castv","v":1,"prefs":{}}"""))
    }

    @Test fun respaldoDanadoNoCambiaNada() {
        val c = TestCtx()
        c.prefs("castv").map["theme"] = "viejo"
        val bad = JSONObject().put("t", "zz").put("v", 1)
        val txt = full("castv" to mk("theme" to s("nuevo")), "castv_lib" to mk("x" to bad))
        assertEquals("El respaldo está dañado.", Backup.restore(c, txt))
        assertEquals("viejo", c.prefs("castv").getString("theme", null))
    }

    // ------------------------------------------------- restore: reemplaza y respeta lo propio del teléfono

    @Test fun reemplazaPeroRespetaIpPinYEstadisticas() {
        val c = TestCtx()
        c.prefs("castv").map.putAll(mapOf("theme" to "viejo", "tvIp" to "1.2.3.4", "pin_hash" to "h", "sobra" to "x"))
        c.prefs("castv_stats").map.putAll(mapOf("v|8" to true, "t|9" to 3L))
        val txt = full(
            "castv" to mk("theme" to s("nuevo"), "tvIp" to s("9.9.9.9"), "pin_hash" to s("otro")),
            "castv_stats" to mk("v|1" to JSONObject().put("t", "b").put("v", true), "t|1" to JSONObject().put("t", "l").put("v", 5))
        )
        assertNull(Backup.restore(c, txt))
        val m = c.prefs("castv").map
        assertEquals("nuevo", m["theme"])
        assertEquals("1.2.3.4", m["tvIp"])     // no se toca
        assertEquals("h", m["pin_hash"])       // no se toca
        assertFalse(m.containsKey("sobra"))    // lo que no viene en el respaldo se quita
        val st = c.prefs("castv_stats").map
        assertEquals(true, st["v|1"])
        assertFalse(st.containsKey("v|8"))     // marca «visto» que no venía: se reemplaza
        assertEquals(3L, st["t|9"])            // no es «visto»: se queda
        assertFalse(st.containsKey("t|1"))     // no se importa
    }

    @Test fun construirYRestaurarDaLoMismo() {
        val a = TestCtx()
        a.prefs("castv").map.putAll(mapOf(
            "theme" to "oscuro", "n" to 7, "big" to 1234567890123L, "f" to 1.5f, "b" to true,
            "ss" to setOf("x", "y"), "tvIp" to "1.2.3.4"
        ))
        a.prefs("castv_lib").map["m3u"] = arr("http://ok.com/lista.m3u")
        a.prefs("castv_stats").map.putAll(mapOf("v|5" to true, "t|5" to 9L))
        val txt = Backup.build(a)

        val b = TestCtx()
        assertNull(Backup.restore(b, txt))
        val m = b.prefs("castv").map
        assertEquals("oscuro", m["theme"])
        assertEquals(7, m["n"])
        assertEquals(1234567890123L, m["big"])
        assertEquals(1.5f, m["f"])
        assertEquals(true, m["b"])
        assertEquals(setOf("x", "y"), m["ss"])
        assertFalse(m.containsKey("tvIp"))
        assertEquals(arr("http://ok.com/lista.m3u"), b.prefs("castv_lib").map["m3u"])
        assertEquals(true, b.prefs("castv_stats").map["v|5"])
        assertFalse(b.prefs("castv_stats").map.containsKey("t|5"))
    }

    // ------------------------------------------------- build: sin claves de Xtream

    @Test fun buildNoLlevaUsuarioNiClave() {
        val c = TestCtx()
        withLogin(c)
        c.prefs("castv").map.putAll(mapOf("theme" to "t", "tvIp" to "1.2.3.4", "pin_hash" to "h"))
        c.prefs("castv_lib").map["chfav"] = arr(real1, "http://ok.com/a.m3u8")
        c.prefs("castv_lib").map["chhidden"] = arr("http://otro.com/live/zed/pw/5.m3u8")   // con clave de un acceso que no está aquí
        c.prefs("castv_stats").map.putAll(mapOf("v|1" to true, "t|1" to 2L))
        val txt = Backup.build(c)
        assertFalse(txt.contains("clave1"))
        assertFalse(txt.contains("juan"))
        assertFalse(txt.contains("pw"))
        val root = JSONObject(txt)
        assertEquals("castv", root.getString("app"))
        assertEquals(1, root.getInt("v"))
        val prefs = root.getJSONObject("prefs")
        assertFalse(prefs.has("castv_xt"))
        val castv = prefs.getJSONObject("castv")
        assertTrue(castv.has("theme"))
        assertFalse(castv.has("tvIp"))
        assertFalse(castv.has("pin_hash"))
        val stats = prefs.getJSONObject("castv_stats")
        assertTrue(stats.has("v|1"))
        assertFalse(stats.has("t|1"))
        val l = prefs.getJSONObject("castv_lib")
        assertEquals(listOf(port1, "http://ok.com/a.m3u8"), strs(JSONArray(l.getJSONObject("chfav").getString("v"))))
        assertEquals(0, JSONArray(l.getJSONObject("chhidden").getString("v")).length())
    }

    // ------------------------------------------------- restore completo con Xtream (withXtream)

    @Test fun portatilVuelveARealYSeUneConLoQueHabia() {
        val c = TestCtx()
        withLogin(c)
        c.prefs("castv_lib").map["chfav"] = arr(real2, "http://viejo.com/b.m3u8")
        val txt = full("castv_lib" to mk("chfav" to s(arr(port1, "http://ok.com/a.m3u8"))))
        assertNull(Backup.restore(c, txt))
        // primero lo del respaldo (ya real), luego lo de Xtream que había; lo que no era de Xtream se reemplaza
        assertEquals(listOf(real1, "http://ok.com/a.m3u8", real2), strs(JSONArray(lib(c, "chfav"))))
    }

    @Test fun enObjetosSeConservanLosCamposYElOrdenPorFecha() {
        val c = TestCtx()
        withLogin(c)
        c.prefs("castv_lib").map["links"] = JSONArray()
            .put(JSONObject().put("u", "http://ok.com/x").put("n", "X").put("t", 9L))
            .put(JSONObject().put("u", real3).put("n", "Tres").put("t", 7L)).toString()
        val incoming = JSONArray().put(JSONObject().put("u", port1).put("n", "Uno").put("t", 5L)).toString()
        assertNull(Backup.restore(c, full("castv_lib" to mk("links" to s(incoming)))))
        val out = JSONArray(lib(c, "links"))
        assertEquals(2, out.length())   // «ok.com/x» no era de Xtream: se reemplaza
        assertEquals(real3, out.getJSONObject(0).getString("u"))
        assertEquals("Tres", out.getJSONObject(0).getString("n"))
        assertEquals(real1, out.getJSONObject(1).getString("u"))
        assertEquals("Uno", out.getJSONObject(1).getString("n"))
    }

    @Test fun sinAccesoElPortatilQuedaInerteYLoXtreamDeAquiSeConserva() {
        val c = TestCtx()
        c.prefs("castv_lib").map["chfav"] = arr("xtream://zz/live/9.m3u8", "http://viejo.com/b.m3u8")
        val txt = full("castv_lib" to mk("chfav" to s(arr("http://ok.com/a.m3u8", port1))))
        assertNull(Backup.restore(c, txt))
        assertEquals(listOf("http://ok.com/a.m3u8", port1, "xtream://zz/live/9.m3u8"), strs(JSONArray(lib(c, "chfav"))))
    }

    @Test fun alVolverAPonerElAccesoSeCompletaElPortatil() {
        val c = TestCtx()
        assertNull(Backup.restore(c, full("castv_lib" to mk("chfav" to s(arr(port1, "http://ok.com/a.m3u8"))))))
        assertEquals(listOf(port1, "http://ok.com/a.m3u8"), strs(JSONArray(lib(c, "chfav"))))
        assertEquals("xtream://ab12cd34", Xtream.save(c, "$srv/", "juan", "clave1", "xtream://ab12cd34"))
        assertEquals(listOf(real1, "http://ok.com/a.m3u8"), strs(JSONArray(lib(c, "chfav"))))
    }

    // ------------------------------------------------- parciales

    @Test fun parcialDeHistorialAnadeSinBorrar() {
        val c = TestCtx()
        c.prefs("castv").map["theme"] = "t"
        c.prefs("castv_lib").map["links"] = JSONArray().put(JSONObject().put("u", "http://a.com").put("n", "A").put("t", 10L)).toString()
        val inc = JSONArray().put(JSONObject().put("u", "http://b.com").put("n", "B").put("t", 20L)).toString()
        assertNull(Backup.restore(c, part("history", "links" to inc)))
        val urls = JSONArray(lib(c, "links")).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("u") } }
        assertEquals(listOf("http://b.com", "http://a.com"), urls)
        assertEquals("Se añadió: 1 enlace. No se borró nada.", Backup.lastNote)
        assertEquals("t", c.prefs("castv").getString("theme", null))   // un parcial no toca otros archivos
    }

    @Test fun parcialDeFavoritosAnadeYAvisaSiNoHabiaNada() {
        val c = TestCtx()
        c.prefs("castv_lib").map["chfav"] = arr("a", "b")
        assertNull(Backup.restore(c, part("favs", "chfav" to arr("b", "c"))))
        assertEquals(listOf("a", "b", "c"), strs(JSONArray(lib(c, "chfav"))))
        assertEquals("Se añadió: 1 canal favorito. No se borró nada.", Backup.lastNote)
        assertNull(Backup.restore(c, part("favs", "chfav" to arr("a", "c"))))
        assertEquals(listOf("a", "b", "c"), strs(JSONArray(lib(c, "chfav"))))
        assertEquals("No había nada nuevo: ya lo tenías todo.", Backup.lastNote)
    }

    @Test fun parcialRaroOVacioODanado() {
        val c = TestCtx()
        c.prefs("castv_lib").map["chfav"] = arr("a")
        assertEquals(NEWER, Backup.restore(c, part("futuro", "chfav" to arr("z"))))
        assertEquals("El respaldo está vacío.", Backup.restore(c, part("favs")))
        assertEquals("El respaldo está vacío.", Backup.restore(c, part("favs", "otra" to arr("z"))))
        val malo = JSONObject().put("app", "castv").put("v", 2).put("part", "favs")
            .put("prefs", JSONObject().put("castv_lib", mk("chfav" to JSONObject().put("t", "i").put("v", 3)))).toString()
        assertEquals("El respaldo está dañado.", Backup.restore(c, malo))
        assertEquals(arr("a"), lib(c, "chfav"))   // nada cambió
    }

    @Test fun buildPartYRestoreDeAmbosLados() {
        val a = TestCtx()
        withLogin(a)
        a.prefs("castv_lib").map["chfav"] = arr("http://ok.com/1", real1, "http://ok.com/2")
        a.prefs("castv_lib").map["chfavmeta"] = JSONArray().put(JSONObject().put("u", "http://ok.com/1").put("g", "x")).toString()
        val txt = Backup.buildPart(a, "favs")
        assertNotNull(txt)
        assertFalse(txt!!.contains("clave1"))   // los enlaces con usuario y clave no salen
        assertEquals("favs", Backup.describe(txt))

        val b = TestCtx()
        b.prefs("castv_lib").map["chfav"] = arr("http://ok.com/2", "http://ok.com/3")
        assertNull(Backup.restore(b, txt))
        assertEquals(listOf("http://ok.com/2", "http://ok.com/3", "http://ok.com/1"), strs(JSONArray(lib(b, "chfav"))))
        assertEquals(1, JSONArray(lib(b, "chfavmeta")).length())
        assertEquals("Se añadió: 1 canal favorito. No se borró nada.", Backup.lastNote)

        assertNull(Backup.buildPart(TestCtx(), "favs"))      // nada que compartir
        assertNull(Backup.buildPart(a, "desconocido"))
    }
}
