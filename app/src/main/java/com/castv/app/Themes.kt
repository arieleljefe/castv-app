package com.castv.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * [TEMAS-1] Colores de acento: la lista, el estilo de cada uno y el selector con bolitas de color.
 * Los números 0 a 3 son los de siempre (Cian, Verde, Naranja, Morado); los nuevos siguen del 4 al 9.
 * El color se guarda en la clave `accent` de las preferencias `castv`.
 *
 * [TEMAS-2] Fondos del modo oscuro: Medianoche (el de siempre), AMOLED, Pizarra, Bosque y Vino.
 * Se guarda en la clave `bgstyle` de `castv`. En modo claro no se aplica. Las pantallas leen los
 * colores de fondo con los atributos castvBg, castvSurface, castvSurface2, castvNav y castvStroke.
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

    val bgNames = arrayOf("Medianoche", "AMOLED (negro)", "Pizarra", "Bosque", "Vino")

    // 0 = Medianoche: no lleva estilo propio (usa los colores de values-night)
    private val bgOverlays = intArrayOf(
        0, R.style.BgOverlayAmoled, R.style.BgOverlaySlate,
        R.style.BgOverlayForest, R.style.BgOverlayWine
    )

    private val bgSwatches = intArrayOf(
        R.color.bgs_midnight_surface, R.color.bgs_amoled_surface, R.color.bgs_slate_surface,
        R.color.bgs_forest_surface, R.color.bgs_wine_surface
    )

    // [TEMAS-3a] Forma y letra. 0 = la de siempre (sin estilo). Claves `shape` y `font` en `castv`.
    val shapeNames = arrayOf("Redondeada (la de siempre)", "Cuadrada", "Píldora")
    private val shapeOverlays = intArrayOf(0, R.style.ShapeOverlaySquare, R.style.ShapeOverlayPill)
    val fontNames = arrayOf("Normal", "Serif", "Monoespaciada", "Condensada")
    private val fontOverlays = intArrayOf(
        0, R.style.FontOverlaySerif, R.style.FontOverlayMono, R.style.FontOverlayCondensed
    )

    fun savedShape(a: Context): Int {
        val v = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("shape", 0)
        return if (v in shapeNames.indices) v else 0
    }

    fun savedFont(a: Context): Int {
        val v = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("font", 0)
        return if (v in fontNames.indices) v else 0
    }

    /** Un solo número con forma y letra, para que MainActivity sepa si cambiaron en Ajustes. */
    fun styleKey(a: Context): Int = savedShape(a) * 10 + savedFont(a)

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Color que tiene ahora un atributo del tema (castvBg, castvSurface...). */
    fun color(c: Context, attr: Int): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** Fondo guardado (un valor fuera de rango vuelve a Medianoche). */
    fun savedBg(a: Context): Int {
        val v = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("bgstyle", 0)
        return if (v in bgNames.indices) v else 0
    }

    /** Acento guardado (un valor fuera de rango vuelve al Cian). */
    fun saved(a: Activity): Int {
        val v = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("accent", 0)
        return if (v in names.indices) v else 0
    }

    /**
     * Aplica el acento y el fondo guardados al tema de la pantalla (llamar en onCreate, después de
     * super.onCreate). Con [paintWindow] en false (reproductor de video) no toca la ventana.
     */
    fun apply(a: Activity, paintWindow: Boolean = true) {
        a.theme.applyStyle(overlays[saved(a)], true)
        val sh = savedShape(a)   // [TEMAS-3a]
        if (sh != 0) a.theme.applyStyle(shapeOverlays[sh], true)
        val fo = savedFont(a)
        if (fo != 0) a.theme.applyStyle(fontOverlays[fo], true)
        val b = savedBg(a)
        if (b != 0 && isNight(a)) {   // [TEMAS-2] Medianoche = sin cambios; en modo claro no se usa
            a.theme.applyStyle(bgOverlays[b], true)
            if (paintWindow) {
                a.window.setBackgroundDrawable(ColorDrawable(color(a, R.attr.castvBg)))
                a.window.statusBarColor = color(a, R.attr.castvBg)
                a.window.navigationBarColor = color(a, R.attr.castvSurface)
            }
        }
    }

    /** [TEMAS-2] Diálogo con una bolita por fondo; al elegir guarda y llama a [onPicked]. */
    fun pickBg(a: Activity, onPicked: () -> Unit) {
        val cur = savedBg(a)
        val dp = a.resources.displayMetrics.density
        val adapter = object : ArrayAdapter<String>(a, 0, bgNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(a)
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(a, bgSwatches[position]))
                    setStroke((2 * dp).toInt(), ContextCompat.getColor(a, R.color.vlc_muted))
                    setSize((28 * dp).toInt(), (28 * dp).toInt())
                }
                tv.text = if (position == cur) bgNames[position] + "  ✓" else bgNames[position]
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
            .setTitle(if (isNight(a)) "Fondo oscuro" else "Fondo oscuro (solo se ve en modo oscuro)")
            .setAdapter(adapter) { d, which ->
                a.getSharedPreferences("castv", Context.MODE_PRIVATE).edit().putInt("bgstyle", which).apply()
                d.dismiss()
                onPicked()
            }
            .show()
    }

    private fun pickList(a: Activity, title: String, items: Array<String>, cur: Int, key: String, onPicked: () -> Unit) {
        AlertDialog.Builder(a)
            .setTitle(title)
            .setSingleChoiceItems(items, cur) { d, which ->
                a.getSharedPreferences("castv", Context.MODE_PRIVATE).edit().putInt(key, which).apply()
                d.dismiss()
                onPicked()
            }
            .show()
    }

    /** [TEMAS-3a] Diálogo de forma (esquinas); al elegir guarda y llama a [onPicked]. */
    fun pickShape(a: Activity, onPicked: () -> Unit) = pickList(a, "Forma", shapeNames, savedShape(a), "shape", onPicked)

    /** [TEMAS-3a] Diálogo de letra; al elegir guarda y llama a [onPicked]. */
    fun pickFont(a: Activity, onPicked: () -> Unit) = pickList(a, "Letra", fontNames, savedFont(a), "font", onPicked)

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
