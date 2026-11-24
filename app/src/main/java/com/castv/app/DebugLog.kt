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
        "token|key|apikey|pass|password|secret|auth|sig|session|user|usuario"

    // usuario:clave@ dentro de un enlace (normal y codificado)
    private val RE_USERINFO = Regex("""(://)[^/\s@:]+:[^/\s@]+@""")
    private val RE_USERINFO_ENC =
        Regex("""(%3A%2F%2F)(?:(?!%2F|%40)[^\s/@])+%40""", RegexOption.IGNORE_CASE)
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
        if (msg.indexOf('=') < 0 && msg.indexOf('%') < 0 && msg.indexOf('@') < 0 &&
            !msg.contains("bearer", ignoreCase = true)
        ) return msg
        return try {
            var r = RE_USERINFO.replace(msg) { m -> m.groupValues[1] + OCULTO + "@" }
            r = RE_USERINFO_ENC.replace(r) { m -> m.groupValues[1] + OCULTO + "%40" }
            r = RE_BEARER.replace(r) { m -> m.groupValues[1] + " " + OCULTO }
            RE_PARAM.replace(r) { m -> m.groupValues[1] + m.groupValues[2] + OCULTO }
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
