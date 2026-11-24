package com.castv.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/** Registro en memoria. Se puede leer desde Termux con: curl http://127.0.0.1:8000/log */
object DebugLog {
    private val lines = LinkedList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    // [LOG-1] Ocultar claves antes de guardar cada línea.
    private const val OCULTO = "[OCULTO]"
    private const val NAMES =
        "token|key|apikey|pass|password|secret|auth|sig|session|user|usuario|username"

    // usuario:clave@ dentro de un enlace (normal y codificado)
    private val RE_USERINFO = Regex("""(://)[^/\s@:]+:[^/\s@]+@""")
    private val RE_USERINFO_ENC =
        Regex("""(%3A%2F%2F)(?:(?!%2F|%40)[^\s/@])+%40""", RegexOption.IGNORE_CASE)
    // [XTREAM-1] usuario y clave dentro de la ruta: /live/USUARIO/CLAVE/123.m3u8
    private val RE_XT_PATH = Regex("""(/(?:live|movie|series|timeshift)/)[^/\s]+/[^/\s]+/""", RegexOption.IGNORE_CASE)
    // [XTREAM-1] /relay/<token> y /live/<token>: el token es el enlace en base64; si por dentro lleva claves, se tapa entero
    private val RE_TOKEN = Regex("""(/(?:relay|live)/)([A-Za-z0-9_-]{16,})""")
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Decodifica base64 de enlace (URL-safe, sin relleno) sin usar clases de Android. null si no es válido. */
    private fun b64url(s: String): String? {
        return try {
            val out = java.io.ByteArrayOutputStream()
            var buf = 0
            var bits = 0
            for (ch in s) {
                val v = B64.indexOf(ch)
                if (v < 0) return null
                buf = ((buf shl 6) or v) and 0xFFFFFF
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buf shr bits) and 0xFF)
                }
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    // Bearer <valor>
    private val RE_BEARER =
        Regex("""\b(Bearer)\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    // nombre=valor y nombre%3Dvalor (también access_token, api_key, token_id...)
    private val RE_PARAM = Regex(
        """(?:(?<![a-z0-9])|(?<=%[0-9a-f]{2}))((?:[a-z0-9]+[_-])?(?:$NAMES)(?:[_-][a-z0-9]+)?)(=|%3D)((?:(?!%26)[^&\s"'#])+)""",
        RegexOption.IGNORE_CASE
    )

    /** Cambia por [OCULTO] los valores secretos de una línea de registro. */
    fun redact(msg: String): String {
        if (msg.indexOf('=') < 0 && msg.indexOf('%') < 0 && msg.indexOf('@') < 0 && msg.indexOf('/') < 0 &&
            !msg.contains("bearer", ignoreCase = true)
        ) return msg
        return try {
            var r = RE_USERINFO.replace(msg) { m -> m.groupValues[1] + OCULTO + "@" }
            r = RE_USERINFO_ENC.replace(r) { m -> m.groupValues[1] + OCULTO + "%40" }
            r = RE_BEARER.replace(r) { m -> m.groupValues[1] + " " + OCULTO }
            r = RE_PARAM.replace(r) { m -> m.groupValues[1] + m.groupValues[2] + OCULTO }
            r = RE_XT_PATH.replace(r) { m -> m.groupValues[1] + OCULTO + "/" + OCULTO + "/" }   // [XTREAM-1]
            RE_TOKEN.replace(r) { m ->   // [XTREAM-1] solo se tapa el token si el enlace de dentro lleva claves
                val dec = b64url(m.groupValues[2])
                if (dec != null && redact(dec) != dec) m.groupValues[1] + OCULTO else m.value
            }
        } catch (e: Exception) {
            "(línea omitida: no se pudo ocultar claves)"
        }
    }

    @Synchronized
    fun add(msg: String) {
        val safe = redact(msg)
        lines.addLast(fmt.format(Date()) + " " + safe)
        while (lines.size > 400) lines.removeFirst()
        android.util.Log.d("CASTV", safe)
    }

    @Synchronized
    fun dump(): String =
        if (lines.isEmpty()) "(sin registros todavia)\n" else lines.joinToString("\n") + "\n"

    @Synchronized
    fun clear() { lines.clear() }
}

/** Estado del servidor HTTP interno (puerto 8000). */
object ServerState {
    @Volatile var error: String? = null
}

/** [Etapa 13-A.1] Puerto en el que quedó abierto el servidor. */
object ServerPort { @Volatile var port = 8000 }
