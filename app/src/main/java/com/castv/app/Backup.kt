package com.castv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Respaldo de los datos del usuario en un archivo JSON (favoritos, listas, historial de enlaces,
 * listas M3U, favoritos de canales y ajustes). No incluye la IP del TV, ni los puntos de
 * reanudación ni las estadísticas de uso.
 *
 * Importar REEMPLAZA los datos actuales. Antes de tocar nada, todo el archivo se lee y se valida.
 */
object Backup {
    private const val APP = "castv"
    private const val VERSION = 1
    private val FILES = listOf("castv_lib", "castv")
    private val SKIP = setOf("tvIp")   // dato propio de cada red/TV: no se respalda

    /** Arma el JSON con las preferencias de la app. */
    fun build(ctx: Context): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("v", VERSION)
        root.put("time", System.currentTimeMillis())
        val prefsObj = JSONObject()
        for (name in FILES) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            loop@ for ((k, v) in sp.all) {
                if (name == "castv" && k in SKIP) continue@loop
                val e = JSONObject()
                when (v) {
                    is String -> e.put("t", "s").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue@loop
                }
                o.put(k, e)
            }
            prefsObj.put(name, o)
        }
        root.put("prefs", prefsObj)
        return root.toString(2)
    }

    /** Lee un archivo como texto UTF-8; devuelve null si pasa del límite (evita archivos gigantes por error). */
    fun readLimited(ins: InputStream, max: Int = 5_000_000): String? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(chunk)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(chunk, 0, n)
        }
        return out.toString("UTF-8")
    }

    /** Devuelve null si todo salió bien, o un mensaje de error (y en ese caso no se cambió nada). */
    fun restore(ctx: Context, text: String): String? {
        val root = try { JSONObject(text) } catch (e: Exception) { return "El archivo no es un respaldo válido." }
        if (root.optString("app") != APP) return "Este archivo no es un respaldo de CASTV."
        val ver = root.optInt("v", 0)
        if (ver < 1) return "El respaldo no tiene una versión válida."
        if (ver > VERSION) return "El respaldo es de una versión más nueva de CASTV. Actualiza la app."
        val prefs = root.optJSONObject("prefs") ?: return "El respaldo está vacío."

        // 1) Leer y validar todo en memoria
        val staged = LinkedHashMap<String, Map<String, Any>>()
        try {
            for (name in FILES) {
                val o = prefs.optJSONObject(name) ?: continue
                val m = LinkedHashMap<String, Any>()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (name == "castv" && k in SKIP) continue
                    val e = o.getJSONObject(k)
                    val v: Any = when (e.getString("t")) {
                        "s" -> e.getString("v")
                        "i" -> e.getInt("v")
                        "l" -> e.getLong("v")
                        "f" -> e.getDouble("v").toFloat()
                        "b" -> e.getBoolean("v")
                        "ss" -> {
                            val a = e.getJSONArray("v")
                            val set = HashSet<String>()
                            for (i in 0 until a.length()) set.add(a.getString(i))
                            set
                        }
                        else -> throw IllegalArgumentException("tipo desconocido")
                    }
                    m[k] = v
                }
                staged[name] = m
            }
        } catch (e: Exception) {
            return "El respaldo está dañado."
        }
        if (staged.isEmpty()) return "El respaldo está vacío."

        // 2) Aplicar: reemplaza lo actual (salvo lo que no se respalda)
        for ((name, m) in staged) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val ed = sp.edit()
            for (k in sp.all.keys) {
                if (name == "castv" && k in SKIP) continue
                ed.remove(k)
            }
            for ((k, v) in m) {
                when (v) {
                    is String -> ed.putString(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Float -> ed.putFloat(k, v)
                    is Boolean -> ed.putBoolean(k, v)
                    is Set<*> -> ed.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            ed.commit()
        }
        return null
    }
}
