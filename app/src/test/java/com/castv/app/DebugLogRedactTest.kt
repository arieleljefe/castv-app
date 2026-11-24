package com.castv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [TESTS-1] Pruebas de DebugLog.redact (LOG-1): oculta claves y deja el resto legible. */
class DebugLogRedactTest {

    @Test
    fun token_en_parametro() {
        assertEquals(
            "ZAP: http://h.tv/c.m3u8?token=[OCULTO]&x=1",
            DebugLog.redact("ZAP: http://h.tv/c.m3u8?token=abc123&x=1")
        )
    }

    @Test
    fun token_codificado_dentro_de_otro_enlace() {
        val out = DebugLog.redact("http://192.168.100.43:8001/live?u=http%3A%2F%2Fh.tv%2Fc.m3u8%3Ftoken%3Dabc123%26q%3D2")
        assertEquals(
            "http://192.168.100.43:8001/live?u=http%3A%2F%2Fh.tv%2Fc.m3u8%3Ftoken%3D[OCULTO]%26q%3D2",
            out
        )
        assertFalse(out.contains("abc123"))
    }

    @Test
    fun usuario_y_clave_en_el_enlace() {
        assertEquals(
            "http://[OCULTO]@host.tv:8080/live/1.ts",
            DebugLog.redact("http://bob:s3cr3t@host.tv:8080/live/1.ts")
        )
    }

    @Test
    fun usuario_y_clave_codificados() {
        assertEquals(
            "http%3A%2F%2F[OCULTO]%40host.tv%2Fx.ts",
            DebugLog.redact("http%3A%2F%2Fbob%3Apw%40host.tv%2Fx.ts")
        )
    }

    @Test
    fun bearer() {
        assertEquals(
            "Authorization: Bearer [OCULTO]",
            DebugLog.redact("Authorization: Bearer eyJ.abc-d_e")
        )
    }

    @Test
    fun nombres_con_prefijo_y_el_resto_queda() {
        assertEquals(
            "GET /x?access_token=[OCULTO]&api_key=[OCULTO]&user=[OCULTO]&pass=[OCULTO]&canal=TV5&puerto=8001",
            DebugLog.redact("GET /x?access_token=zzz&api_key=qq&user=ana&pass=p&canal=TV5&puerto=8001")
        )
    }

    @Test
    fun lo_que_no_es_secreto_no_se_toca() {
        val a = "error HTTP 403 en 192.168.100.14:38400 canal=Telemundo"
        val b = "design=ok keyboard=1 monkey=2"
        val c = "linea normal sin nada raro"
        assertEquals(a, DebugLog.redact(a))
        assertEquals(b, DebugLog.redact(b))
        assertEquals(c, DebugLog.redact(c))
    }
}
