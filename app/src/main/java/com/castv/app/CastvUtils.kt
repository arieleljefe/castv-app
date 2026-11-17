package com.castv.app

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.Normalizer

/** [Etapa 14-A] Funciones de texto sin estado, sacadas de MainActivity.kt (mismo comportamiento). */
object CastvText {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Minúsculas y sin acentos, para buscar y ordenar. */
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    /** Compara "Cap 2" antes que "Cap 10" (números por valor, no letra a letra). */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei; j = ej
            } else {
                if (a[i] != b[j]) return a[i].compareTo(b[j])
                i++; j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    /** 75 -> "1:15"; 3725 -> "1:02:05". */
    fun fmtTime(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}

/** [Etapa 14-A] Red: IP del teléfono, sacada de MainActivity.kt (mismo comportamiento). */
object CastvNet {
    /** IP del teléfono en la interfaz que realmente llega al TV (no la de datos móviles). */
    fun localIpAddress(tvIp: String?): String? {
        if (tvIp != null) {
            try {
                DatagramSocket().use { s ->
                    s.connect(InetAddress.getByName(tvIp), 9) // no envía nada
                    val ip = s.localAddress.hostAddress
                    if (ip != null && ip != "0.0.0.0" && !ip.startsWith("127.")) return ip
                }
            } catch (e: Exception) { }
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.name.startsWith("wlan")) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
