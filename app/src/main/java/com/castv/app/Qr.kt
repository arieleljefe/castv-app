package com.castv.app

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * [QR-1] Pasar una lista M3U desde otro dispositivo del mismo Wi-Fi.
 *
 * La puerta se abre SOLO a mano (botón «📲 Desde otro dispositivo» de «＋ Lista»), con una clave aleatoria de un solo
 * uso, y se cierra sola al recibir la lista, a los 5 minutos, al cerrar el recuadro, al salir de la pantalla o tras
 * 5 intentos con clave equivocada. Solo se aceptan peticiones de direcciones de red local. Solo entran enlaces
 * http/https de listas normales: el usuario y la clave de Xtream NO se aceptan aquí (viajarían sin cifrar).
 * La clave nunca se escribe en el /log. Este objeto no usa clases de Android (se prueba en el ordenador).
 */
object QrAdd {
    private const val TTL_MS = 5L * 60_000L
    private const val MAX_BAD = 5
    private const val MAX_URL = 2000
    private const val MAX_NAME = 80

    private var token: String? = null
    private var until = 0L
    private var bad = 0
    private var listener: ((String, String) -> Unit)? = null

    private fun newToken(): String {
        val b = ByteArray(12)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Abre la puerta y devuelve la clave. [onAdd] recibe (nombre, enlace) cuando llega una lista válida (en el hilo del servidor). */
    @Synchronized
    fun open(onAdd: (String, String) -> Unit): String {
        val t = newToken()
        token = t
        until = System.currentTimeMillis() + TTL_MS
        bad = 0
        listener = onAdd
        DebugLog.add("QR: puerta abierta (5 minutos, un solo uso)")
        return t
    }

    @Synchronized
    fun close(why: String) {
        if (token != null) DebugLog.add("QR: puerta cerrada ($why)")
        token = null
        listener = null
    }

    @Synchronized
    fun isOpen(): Boolean {
        if (token != null && System.currentTimeMillis() > until) close("pasaron 5 minutos")
        return token != null
    }

    /** ¿La petición puede ver la página? Hace falta puerta abierta, dirección de red local y clave correcta. */
    @Synchronized
    fun accept(tok: String, remote: String): Boolean {
        if (!isOpen()) return false
        if (!isLocal(remote)) {
            DebugLog.add("QR: petición rechazada (no es de la red local)")
            return false
        }
        val t = token ?: return false
        if (MessageDigest.isEqual(t.toByteArray(), tok.toByteArray())) return true
        bad++
        DebugLog.add("QR: clave equivocada ($bad de $MAX_BAD)")
        if (bad >= MAX_BAD) close("demasiados intentos")
        return false
    }

    /** Recibe la lista. Devuelve null si todo bien (y cierra la puerta), o el motivo en español. */
    @Synchronized
    fun submit(tok: String, name: String, url: String): String? {
        val t = token ?: return "La dirección ya no está abierta."
        if (!MessageDigest.isEqual(t.toByteArray(), tok.toByteArray())) return "La dirección ya no está abierta."
        val u = url.trim()
        val err = checkUrl(u)
        if (err != null) return err
        val n = name.trim().take(MAX_NAME)
        val l = listener
        close("lista recibida")
        if (l != null) l(n, u)
        return null
    }

    /** null si el enlace sirve como lista; si no, el motivo. */
    fun checkUrl(u: String): String? {
        if (u.isEmpty()) return "Falta el enlace de la lista."
        if (u.length > MAX_URL) return "El enlace es demasiado largo."
        if (u.any { it.isWhitespace() || it.isISOControl() }) return "El enlace no puede tener espacios."
        val low = u.lowercase()
        if (!low.startsWith("http://") && !low.startsWith("https://")) return "El enlace debe empezar con http:// o https://"
        if (low.contains("username=") && low.contains("password=")) {
            return "Para listas con usuario y clave usa el botón 🔑 Xtream en el teléfono."
        }
        return null
    }

    /** Dirección de red local (privada, de enlace local o del propio teléfono). */
    fun isLocal(addr: String): Boolean {
        val a = addr.trim().lowercase()
        if (a.isEmpty()) return false
        if (a == "::1" || a.startsWith("fe80:") || a.startsWith("fc") || a.startsWith("fd")) return a.contains(':')
        val p = a.split('.')
        if (p.size != 4) return false
        val n = p.map { it.toIntOrNull() ?: return false }
        if (n.any { it < 0 || it > 255 }) return false
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) ||
            (n[0] == 172 && n[1] in 16..31) || (n[0] == 169 && n[1] == 254)
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Página con el formulario (nombre y enlace). [err] es un aviso opcional. */
    fun page(tok: String, err: String?): String {
        val aviso = if (err == null) "" else "<p class=\"e\">" + esc(err) + "</p>"
        return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<meta name=\"referrer\" content=\"no-referrer\"><title>CASTV</title><style>" +
            "body{font-family:sans-serif;margin:0;padding:24px;background:#101418;color:#eee;max-width:520px}" +
            "input{width:100%;box-sizing:border-box;padding:12px;margin:6px 0 16px;font-size:16px;border-radius:8px;border:1px solid #555;background:#1b2128;color:#eee}" +
            "button{padding:12px 20px;font-size:16px;border:0;border-radius:8px;background:#26c6da;color:#000}" +
            ".e{color:#ff8a80}</style></head><body><h2>📺 CASTV: añadir lista</h2>" + aviso +
            "<form method=\"get\" action=\"/qr/" + esc(tok) + "\">" +
            "<label>Nombre (opcional)</label><input name=\"n\" maxlength=\"80\">" +
            "<label>Enlace de la lista (http:// o https://)</label><input name=\"u\" type=\"url\" required maxlength=\"2000\">" +
            "<button type=\"submit\">Enviar al teléfono</button></form></body></html>"
    }

    fun doneHtml(): String =
        "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>CASTV</title></head>" +
            "<body style=\"font-family:sans-serif;padding:24px;background:#101418;color:#eee\">" +
            "<h2>✅ Lista enviada</h2><p>Ya puedes cerrar esta página: la lista se abrió en CASTV.</p></body></html>"
}

/**
 * [QR-2a] Enviar MI lista (nombre y enlace) a otro dispositivo del mismo Wi-Fi.
 *
 * La puerta se abre SOLO a mano (≪📤 Enviar esta lista≫ en ＋ Lista), con una clave aleatoria de un solo uso, y se
 * cierra sola al entregar la lista, a los 5 minutos, al cerrar el recuadro, al salir de la pantalla o tras 5 intentos
 * con clave equivocada. Solo se atienden direcciones de red local. NO se envían listas Xtream ni enlaces con usuario
 * y clave (viajarían sin cifrar). La primera visita solo muestra un botón (así un navegador o escáner que «adelante»
 * la página no gasta la clave); la lista se entrega al pulsarlo. La clave nunca se escribe en el /log.
 * Este objeto no usa clases de Android (se prueba en el ordenador).
 */
object QrSend {
    private const val TTL_MS = 5L * 60_000L
    private const val MAX_BAD = 5
    private const val MAX_NAME = 80

    private var token: String? = null
    private var until = 0L
    private var bad = 0
    private var name = ""
    private var url = ""
    private var onDone: (() -> Unit)? = null

    private fun newToken(): String {
        val b = ByteArray(12)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** null si la lista se puede enviar; si no, el motivo en español. */
    fun check(u: String): String? {
        val t = u.trim()
        if (t.isEmpty()) return "Esta lista no tiene enlace."
        val low = t.lowercase()
        if (low.startsWith("xtream://")) {
            return "Las listas Xtream llevan usuario y clave y no se envían por QR. En el otro móvil añádela con el botón 🔑 Xtream."
        }
        if (!low.startsWith("http://") && !low.startsWith("https://")) return "Solo se pueden enviar listas con enlace http:// o https://."
        val host = low.substringAfter("://").substringBefore('/').substringBefore('?')
        if ((low.contains("username=") && low.contains("password=")) || host.contains('@')) {
            return "Esta lista lleva usuario y clave en el enlace y no se envía por QR (viajaría sin cifrar). En el otro móvil añádela con el botón 🔑 Xtream."
        }
        return QrAdd.checkUrl(t)
    }

    /** Abre la puerta y devuelve la clave, o null si la lista no se puede enviar. [done] se llama al entregarla (en el hilo del servidor). */
    @Synchronized
    fun open(listName: String, listUrl: String, done: () -> Unit): String? {
        if (check(listUrl) != null) return null
        val t = newToken()
        token = t
        until = System.currentTimeMillis() + TTL_MS
        bad = 0
        name = listName.trim().take(MAX_NAME)
        url = listUrl.trim()
        onDone = done
        DebugLog.add("QR: puerta de envío abierta (5 minutos, un solo uso)")
        return t
    }

    @Synchronized
    fun close(why: String) {
        if (token != null) DebugLog.add("QR: puerta de envío cerrada ($why)")
        token = null
        name = ""
        url = ""
        onDone = null
    }

    @Synchronized
    fun isOpen(): Boolean {
        if (token != null && System.currentTimeMillis() > until) close("pasaron 5 minutos")
        return token != null
    }

    /** ¿La petición puede ver la página? Hace falta puerta abierta, dirección de red local y clave correcta. No gasta la clave. */
    @Synchronized
    fun accept(tok: String, remote: String): Boolean {
        if (!isOpen()) return false
        if (!QrAdd.isLocal(remote)) {
            DebugLog.add("QR: petición de envío rechazada (no es de la red local)")
            return false
        }
        val t = token ?: return false
        if (MessageDigest.isEqual(t.toByteArray(), tok.toByteArray())) return true
        bad++
        DebugLog.add("QR: clave de envío equivocada ($bad de $MAX_BAD)")
        if (bad >= MAX_BAD) close("demasiados intentos")
        return false
    }

    /** Entrega (nombre, enlace) UNA sola vez y cierra la puerta. Null si la clave no vale o ya no está abierta. */
    @Synchronized
    fun take(tok: String): Pair<String, String>? {
        if (!isOpen()) return null
        val t = token ?: return null
        if (!MessageDigest.isEqual(t.toByteArray(), tok.toByteArray())) return null
        val out = Pair(name, url)
        val d = onDone
        close("lista entregada")
        if (d != null) d()
        return out
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private const val HEAD = "<!doctype html><html lang='es'><head><meta charset='utf-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<meta name='referrer' content='no-referrer'><title>CASTV</title><style>" +
        "body{font-family:sans-serif;margin:0;padding:24px;background:#101418;color:#eee;max-width:520px}" +
        "textarea{width:100%;box-sizing:border-box;padding:12px;margin:6px 0 16px;font-size:15px;border-radius:8px;border:1px solid #555;background:#1b2128;color:#eee}" +
        "button{padding:12px 20px;font-size:16px;border:0;border-radius:8px;background:#26c6da;color:#000}" +
        ".m{color:#9fd}</style></head><body>"

    /** Primera página: solo un botón (no entrega nada hasta pulsarlo). */
    fun askHtml(tok: String): String =
        HEAD + "<h2>📺 CASTV: lista para ti</h2><p>Otro móvil quiere enviarte una lista.</p>" +
            "<form method='get' action='/send/" + esc(tok) + "'><input type='hidden' name='go' value='1'>" +
            "<button type='submit'>📥 Recibir lista</button></form></body></html>"

    /** Segunda página: nombre y enlace, con botón para copiarlo. */
    fun listHtml(listName: String, listUrl: String): String =
        HEAD + "<h2>✅ " + esc(listName.ifEmpty { "Lista" }) + "</h2>" +
            "<p>Copia el enlace y pégalo en CASTV: ＋ Lista.</p>" +
            "<textarea id='u' rows='4' readonly>" + esc(listUrl) + "</textarea>" +
            "<button type='button' onclick='c()'>Copiar enlace</button><p id='m' class='m'></p>" +
            "<script>function c(){var t=document.getElementById('u');t.focus();t.select();t.setSelectionRange(0,99999);" +
            "var ok=false;try{ok=document.execCommand('copy')}catch(e){}" +
            "document.getElementById('m').textContent=ok?'✅ Copiado':'Mantén pulsado el enlace y elige Copiar';}</script>" +
            "</body></html>"
}

/** [QR-1] Dibuja el código QR de un texto (librería zxing core). Devuelve null si no se pudo. */
object QrImage {
    fun make(text: String, px: Int): Bitmap? {
        return try {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px)
            val w = m.width
            val h = m.height
            val pix = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) pix[y * w + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
            Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) { null }
    }
}
