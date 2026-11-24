package com.castv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [QR-1] Pruebas de la puerta para recibir listas (sin Android). */
class QrAddTest {
    @Test fun direccionesLocales() {
        assertTrue(QrAdd.isLocal("192.168.100.5"))
        assertTrue(QrAdd.isLocal("10.0.0.2"))
        assertTrue(QrAdd.isLocal("172.16.5.1"))
        assertTrue(QrAdd.isLocal("127.0.0.1"))
        assertTrue(QrAdd.isLocal("::1"))
        assertFalse(QrAdd.isLocal("172.32.0.1"))
        assertFalse(QrAdd.isLocal("8.8.8.8"))
        assertFalse(QrAdd.isLocal("999.1.1.1"))
        assertFalse(QrAdd.isLocal(""))
    }

    @Test fun enlaces() {
        assertNull(QrAdd.checkUrl("https://ejemplo.com/lista.m3u"))
        assertNull(QrAdd.checkUrl("http://ejemplo.com/lista.m3u8"))
        assertNotNull(QrAdd.checkUrl(""))
        assertNotNull(QrAdd.checkUrl("ftp://ejemplo.com/lista.m3u"))
        assertNotNull(QrAdd.checkUrl("http://ejemplo.com/a b.m3u"))
        assertNotNull(QrAdd.checkUrl("http://ejemplo.com/get.php?username=u&password=p&type=m3u"))
    }

    @Test fun unSoloUso() {
        var got: Pair<String, String>? = null
        val t = QrAdd.open { n, u -> got = Pair(n, u) }
        assertEquals(24, t.length)
        assertTrue(QrAdd.isOpen())
        assertTrue(QrAdd.accept(t, "192.168.1.5"))
        assertFalse(QrAdd.accept(t, "8.8.8.8"))
        assertNull(QrAdd.submit(t, " Mi lista ", "https://ejemplo.com/lista.m3u"))
        assertEquals(Pair("Mi lista", "https://ejemplo.com/lista.m3u"), got)
        assertFalse(QrAdd.isOpen())
        assertFalse(QrAdd.accept(t, "192.168.1.5"))
        assertNotNull(QrAdd.submit(t, "x", "https://ejemplo.com/otra.m3u"))
    }

    @Test fun enlaceMalo_noCierra() {
        val t = QrAdd.open { _, _ -> }
        assertNotNull(QrAdd.submit(t, "x", "ftp://no"))
        assertTrue(QrAdd.isOpen())
        QrAdd.close("prueba")
        assertFalse(QrAdd.isOpen())
    }

    @Test fun clavesEquivocadas_cierran() {
        val t = QrAdd.open { _, _ -> }
        val mala = "z".repeat(24)
        for (i in 1..5) assertFalse(QrAdd.accept(mala, "192.168.1.5"))
        assertFalse(QrAdd.isOpen())
        assertFalse(QrAdd.accept(t, "192.168.1.5"))
    }
}
