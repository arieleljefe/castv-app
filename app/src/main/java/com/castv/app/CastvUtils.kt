package com.castv.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.Spanned
import android.text.SpannableString
import android.text.style.ImageSpan
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

/** [ICONOS-3] Filas de menú con «icono vectorial + texto» (el icono va dentro del texto, así se alinea y toma el color del texto). */
object CastvIcons {
    /** [ctx] debe ser el contexto del diálogo (`builder.context`) para que el icono tome el color de sus textos. */
    fun item(ctx: Context, res: Int, label: String): CharSequence {
        val d = ContextCompat.getDrawable(ctx, res)?.mutate() ?: return label
        val attrs = intArrayOf(android.R.attr.textColorAlertDialogListItem, android.R.attr.textColorPrimary)
        var color = Color.GRAY
        for (a in attrs) {
            val ta = ctx.obtainStyledAttributes(intArrayOf(a))
            val c = ta.getColorStateList(0)
            ta.recycle()
            if (c != null) { color = c.defaultColor; break }
        }
        d.setTint(color)
        val px = (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f * 1.2f, ctx.resources.displayMetrics)).toInt()
        d.setBounds(0, 0, px, px)
        val sp = SpannableString("\u00A0 $label")
        sp.setSpan(ImageSpan(d, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }
}

/** [TEXTO-TAM-1] Tamaño del texto de la app: 0 = Pequeño, 1 = Normal (el del sistema, sin tocar), 2 = Grande. Se guarda en `castv` › `text_size`. */
object CastvTextSize {
    val names = arrayOf("Pequeño", "Normal (el del sistema)", "Grande")
    private val factors = floatArrayOf(0.9f, 1f, 1.15f)

    fun saved(ctx: Context): Int =
        ctx.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("text_size", 1).let { if (it in 0..2) it else 1 }

    /** Con «Normal» devuelve el mismo contexto; con otro tamaño, uno cuyo `fontScale` es el del sistema por el factor elegido. */
    fun wrap(base: Context): Context {
        val idx = saved(base)
        if (idx == 1) return base
        val cfg = Configuration(base.resources.configuration)
        cfg.fontScale = cfg.fontScale * factors[idx]
        return base.createConfigurationContext(cfg)
    }
}

/** [TEXTO-TAM-1] Base de las pantallas: aplican el tamaño de texto elegido en Ajustes. */
open class CastvActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CastvTextSize.wrap(newBase))
    }

    // Receta conocida de AppCompat: que el cambio de tema (uiMode) no pierda el fontScale del contexto base
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }
}
