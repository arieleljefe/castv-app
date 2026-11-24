package com.castv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [QR-2a] Pruebas de la puerta para enviar una lista (sin Android). */
class QrSendTest {
    @Test fun quePuedeEnviarse() {
        assertNull(QrSend.check("https://ejemplo.com/lista.m3u"))
        assertNull(QrSend.check("http://ejemplo.com/lista.m3u8"))
        assertNotNull(QrSend.check(""))
        assertNotNull(QrSend.check("ftp://ejemplo.com/lista.m3u"))
        assertNotNull(QrSend.check("xtream://abc123"))
        assertNotNull(QrSend.check("http://ejemplo.com/get.php?username=u&password=p&type=m3u"))
        assertNotNull(QrSend.check("http://usuario:clave@ejemplo.com/lista.m3u"))
        assertNotNull(QrSend.check("http://ejemplo.com/a b.m3u"))
    }

    @Test fun listaConClave_noAbre() {
        assertNull(QrSend.open("x", "xtream://abc") { })
        assertFalse(QrSend.isOpen())
    }

    @Test fun unSoloUso() {
        var hecho = false
        val t = QrSend.open(" Mi lista ", "https://ejemplo.com/lista.m3u") { hecho = true }
        assertNotNull(t)
        val tok = t!!
        assertEquals(24, tok.length)
        assertTrue(QrSend.isOpen())
        assertTrue(QrSend.accept(tok, "192.168.1.5"))
        assertTrue(QrSend.isOpen())   // mirar la página no gasta la clave
        assertFalse(QrSend.accept(tok, "8.8.8.8"))
        assertNull(QrSend.take("otra-clave"))
        assertTrue(QrSend.isOpen())
        assertEquals(Pair("Mi lista", "https://ejemplo.com/lista.m3u"), QrSend.take(tok))
        assertTrue(hecho)
        assertFalse(QrSend.isOpen())
        assertNull(QrSend.take(tok))
        assertFalse(QrSend.accept(tok, "192.168.1.5"))
    }

    @Test fun cincoClavesMalasCierran() {
        QrSend.open("x", "https://ejemplo.com/lista.m3u") { }
        for (i in 1..5) assertFalse(QrSend.accept("mala", "192.168.1.5"))
        assertFalse(QrSend.isOpen())
    }

    @Test fun cerrarAMano() {
        QrSend.open("x", "https://ejemplo.com/lista.m3u") { }
        assertTrue(QrSend.isOpen())
        QrSend.close("prueba")
        assertFalse(QrSend.isOpen())
    }

    @Test fun paginas() {
        val ask = QrSend.askHtml("abc")
        assertTrue(ask.contains("/send/abc"))
        assertTrue(ask.contains("name='go'"))
        val lista = QrSend.listHtml("<b>Mi</b>", "http://e.com/a?x=1&y=2")
        assertTrue(lista.contains("&lt;b&gt;Mi&lt;/b&gt;"))
        assertTrue(lista.contains("&amp;y=2"))
        assertFalse(lista.contains("<b>Mi"))
    }
}
