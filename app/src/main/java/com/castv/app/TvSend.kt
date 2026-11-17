package com.castv.app

/** [Etapa 14-C] Arma las URL que se mandan al TV, sacado de MainActivity. Mismas URL que antes. */
object TvSend {
    /** Enlace de internet (mp4 o m3u8) servido por /relay del teléfono. */
    fun relayUrl(phoneIp: String, url: String, hls: Boolean): String {
        val token = android.util.Base64.encodeToString(url.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        return "http://$phoneIp:${ServerPort.port}/relay/$token.${if (hls) "m3u8" else "mp4"}"
    }

    /** Archivo local (música o video) servido por /audio o /video del teléfono. */
    fun mediaUrl(phoneIp: String, kind: String, id: Long, ext: String): String =
        "http://$phoneIp:${ServerPort.port}/$kind/$id.$ext"
}
