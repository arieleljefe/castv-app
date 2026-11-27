package com.castv.app

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * [TEMAS-1] Colores de acento: la lista, el estilo de cada uno y el selector con bolitas de color.
 * Los números 0 a 3 son los de siempre (Cian, Verde, Naranja, Morado); los nuevos siguen del 4 al 9.
 * El color se guarda en la clave `accent` de las preferencias `castv`.
 */
object Themes {
    val names = arrayOf(
        "Cian", "Verde", "Naranja", "Morado",
        "Rosa", "Rojo Quisqueya", "Azul", "Dorado", "Menta", "Índigo"
    )

    private val overlays = intArrayOf(
        R.style.AccentOverlayCyan, R.style.AccentOverlayGreen,
        R.style.AccentOverlayOrange, R.style.AccentOverlayPurple,
        R.style.AccentOverlayPink, R.style.AccentOverlayRed,
        R.style.AccentOverlayBlue, R.style.AccentOverlayGold,
        R.style.AccentOverlayMint, R.style.AccentOverlayIndigo
    )

    private val swatches = intArrayOf(
        R.color.accent_cyan, R.color.accent_green,
        R.color.accent_orange, R.color.accent_purple,
        R.color.accent_pink, R.color.accent_red,
        R.color.accent_blue, R.color.accent_gold,
        R.color.accent_mint, R.color.accent_indigo
    )

    /** Acento guardado (un valor fuera de rango vuelve al Cian). */
    fun saved(a: Activity): Int {
        val v = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("accent", 0)
        return if (v in names.indices) v else 0
    }

    /** Aplica el acento guardado al tema de la pantalla (llamar en onCreate). */
    fun apply(a: Activity) {
        a.theme.applyStyle(overlays[saved(a)], true)
    }

    /** Diálogo con una bolita de color por opción; al elegir guarda y llama a [onPicked]. */
    fun pick(a: Activity, onPicked: () -> Unit) {
        val cur = saved(a)
        val dp = a.resources.displayMetrics.density
        val adapter = object : ArrayAdapter<String>(a, 0, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(a)
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(a, swatches[position]))
                    setSize((28 * dp).toInt(), (28 * dp).toInt())
                }
                tv.text = if (position == cur) names[position] + "  ✓" else names[position]
                tv.textSize = 16f
                tv.setTextColor(ContextCompat.getColor(a, R.color.vlc_text))
                tv.gravity = Gravity.CENTER_VERTICAL
                tv.compoundDrawablePadding = (16 * dp).toInt()
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
                tv.setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
                return tv
            }
        }
        AlertDialog.Builder(a)
            .setTitle("Color de acento")
            .setAdapter(adapter) { d, which ->
                a.getSharedPreferences("castv", Context.MODE_PRIVATE).edit().putInt("accent", which).apply()
                d.dismiss()
                onPicked()
            }
            .show()
    }
}
