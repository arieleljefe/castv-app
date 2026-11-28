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
import android.graphics.Typeface
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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

    val bgNames = arrayOf(
        "Medianoche", "AMOLED (negro)", "Pizarra", "Bosque", "Vino",
        "Papel (modo claro)", "Neón", "Cassette", "Quisqueya",   // [TEMAS-3b]
        "Sakura (modo claro)", "Tokio", "Acero", "Kawaii (modo claro)"   // [TEMAS-5]
    )

    /** [TEMAS-3b] Papel es el único fondo para modo claro; el resto, para modo oscuro. */
    const val PAPER_BG = 5

    /** [TEMAS-5] Fondos pensados para modo claro: Papel (5), Sakura (9) y Kawaii (12). */
    private val lightBgs = setOf(5, 9, 12)

    // 0 = Medianoche: no lleva estilo propio (usa los colores de values-night)
    private val bgOverlays = intArrayOf(
        0, R.style.BgOverlayAmoled, R.style.BgOverlaySlate,
        R.style.BgOverlayForest, R.style.BgOverlayWine,
        R.style.BgOverlayPaper, R.style.BgOverlayNeon, R.style.BgOverlayCassette, R.style.BgOverlayQuisqueya,
        R.style.BgOverlaySakura, R.style.BgOverlayTokio, R.style.BgOverlayAcero, R.style.BgOverlayKawaii
    )

    private val bgSwatches = intArrayOf(
        R.color.bgs_midnight_surface, R.color.bgs_amoled_surface, R.color.bgs_slate_surface,
        R.color.bgs_forest_surface, R.color.bgs_wine_surface,
        R.color.bgs_paper_surface, R.color.bgs_neon_surface, R.color.bgs_cassette_surface, R.color.bgs_quisqueya_surface,
        R.color.bgs_sakura_surface, R.color.bgs_tokio_surface, R.color.bgs_acero_surface, R.color.bgs_kawaii_surface
    )

    // [TEMAS-3a] Forma y letra. 0 = la de siempre (sin estilo). Claves `shape` y `font` en `castv`.
    val shapeNames = arrayOf("Redondeada (la de siempre)", "Cuadrada", "Píldora")
    private val shapeOverlays = intArrayOf(0, R.style.ShapeOverlaySquare, R.style.ShapeOverlayPill)
    val fontNames = arrayOf("Normal", "Serif", "Monoespaciada", "Condensada", "Divertida")
    private val fontOverlays = intArrayOf(
        0, R.style.FontOverlaySerif, R.style.FontOverlayMono, R.style.FontOverlayCondensed, R.style.FontOverlayCasual
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

    /** [TEMAS-3b] ¿Este fondo se pinta con el modo actual? (Papel en claro; los demás en oscuro; 0 = el de siempre.) */
    fun bgApplies(c: Context, b: Int): Boolean =
        b != 0 && (if (b in lightBgs) !isNight(c) else isNight(c))

    /** Aviso para Ajustes cuando el fondo elegido no se ve con el modo actual. */
    fun bgNote(c: Context): String {
        val b = savedBg(c)
        if (b == 0 || bgApplies(c, b)) return ""
        return if (b in lightBgs) " · solo en modo claro" else " · solo en modo oscuro"
    }

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
        if (bgApplies(a, b)) {   // [TEMAS-2] Medianoche = sin cambios; en modo claro no se usa
            a.theme.applyStyle(bgOverlays[b], true)
            if (paintWindow) {
                a.window.setBackgroundDrawable(ColorDrawable(color(a, R.attr.castvBg)))
                a.window.statusBarColor = color(a, R.attr.castvBg)
                a.window.navigationBarColor = color(a, R.attr.castvSurface)
            }
        }
    }

    // ------------------------------------------------------------------ [TEMAS-3b] Temas
    /** Un tema = modo + acento + fondo + forma + letra. Los tres últimos números son colores solo para la vista previa. */
    class Pack(
        val name: String, val desc: String,
        val mode: Int, val accent: Int, val bg: Int, val shape: Int, val font: Int,
        val pBg: Int, val pAccent: Int,
        val kind: Int = 0   // [TEMAS-6] 0 = de fábrica, 1 = mío, 2 = fila «Guardar el tema de ahora»
    )

    val packs = listOf(
        Pack("CASTV", "El de siempre: azul noche y cian", 2, 0, 0, 0, 0, 0xFF0B0F14.toInt(), 0xFF22D3EE.toInt()),
        Pack("Cine", "Negro total, dorado y letra serif", 2, 7, 1, 1, 1, 0xFF000000.toInt(), 0xFFE5B93C.toInt()),
        Pack("Neón", "Morado oscuro, rosa y esquinas de píldora", 2, 4, 6, 2, 3, 0xFF0D0620.toInt(), 0xFFF472B6.toInt()),
        Pack("Cassette", "Marrón oscuro, naranja y letra de máquina", 2, 2, 7, 1, 2, 0xFF1A120B.toInt(), 0xFFFB923C.toInt()),
        Pack("Papel", "Claro y cálido, tinta índigo y serif", 1, 9, 5, 0, 1, 0xFFF5EFE0.toInt(), 0xFF4F46E5.toInt()),
        Pack("Quisqueya", "Azul marino y rojo", 2, 5, 8, 0, 0, 0xFF0A1226.toInt(), 0xFFEF4444.toInt()),
        // [TEMAS-5] Estilo anime (solo colores, formas y letra; sin personajes ni marcas)
        Pack("Sakura", "🌸 Claro, rosa de cerezo y esquinas de píldora", 1, 4, 9, 2, 0, 0xFFFFF1F5.toInt(), 0xFFDB2777.toInt()),
        Pack("Neo Tokio", "🌃 Noche de ciudad, cian y letra condensada", 2, 0, 10, 1, 3, 0xFF070A1F.toInt(), 0xFF22D3EE.toInt()),
        Pack("Shonen", "⚡ Pizarra oscura, naranja de energía y esquinas rectas", 2, 2, 2, 1, 3, 0xFF16181D.toInt(), 0xFFFB923C.toInt()),
        Pack("Mecha", "🤖 Acero oscuro, rojo y letra de consola", 2, 5, 11, 1, 2, 0xFF101417.toInt(), 0xFFEF4444.toInt()),
        Pack("Kawaii", "🍬 Claro lavanda, morado y letra divertida", 1, 3, 12, 2, 4, 0xFFF5F0FF.toInt(), 0xFF7C3AED.toInt()),
        Pack("Samurái", "⚔️ Vino oscuro, rojo y serif", 2, 5, 4, 1, 1, 0xFF140A10.toInt(), 0xFFEF4444.toInt()),
        Pack("Espíritu del bosque", "🍃 Bosque oscuro, menta y esquinas suaves", 2, 8, 3, 0, 0, 0xFF0A130F.toInt(), 0xFF2DD4BF.toInt())
    )

    private fun savedMode(a: Context): Int = a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("theme", 2)

    private fun matches(a: Context, p: Pack): Boolean =
        savedMode(a) == p.mode && a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("accent", 0) == p.accent &&
            savedBg(a) == p.bg && savedShape(a) == p.shape && savedFont(a) == p.font

    /** Nombre del tema que coincide con lo guardado, o «Personalizado» si se retocó algo a mano. */
    fun packLabel(a: Context): String =
        packs.firstOrNull { matches(a, it) }?.name
            ?: myThemes(a).firstOrNull { matchesMy(a, it) }?.name
            ?: "Personalizado"

    private fun typefaceOf(font: Int): Typeface = when (font) {
        1 -> Typeface.SERIF
        2 -> Typeface.MONOSPACE
        3 -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        4 -> Typeface.create("casual", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }

    // ------------------------------------------------------------------ [TEMAS-6] Mis temas
    class MyTheme(val name: String, val mode: Int, val accent: Int, val bg: Int, val shape: Int, val font: Int)

    private fun myPrefs(a: Context) = a.getSharedPreferences("castv_themes", Context.MODE_PRIVATE)

    fun myThemes(a: Context): MutableList<MyTheme> {
        val out = ArrayList<MyTheme>()
        try {
            val arr = org.json.JSONArray(myPrefs(a).getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(MyTheme(
                    o.optString("name", "Mi tema"), o.optInt("mode", 2).coerceIn(0, 2),
                    o.optInt("accent", 0), o.optInt("bg", 0), o.optInt("shape", 0), o.optInt("font", 0)
                ))
            }
        } catch (e: Exception) {
            // lista dañada: se ignora y se empieza vacía
        }
        return out
    }

    private fun saveMyThemes(a: Context, l: List<MyTheme>) {
        val arr = org.json.JSONArray()
        for (t in l) {
            arr.put(org.json.JSONObject()
                .put("name", t.name).put("mode", t.mode).put("accent", t.accent)
                .put("bg", t.bg).put("shape", t.shape).put("font", t.font))
        }
        myPrefs(a).edit().putString("list", arr.toString()).apply()
    }

    private fun currentAsMy(a: Context, name: String): MyTheme = MyTheme(
        name, savedMode(a), a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("accent", 0),
        savedBg(a), savedShape(a), savedFont(a)
    )

    private fun matchesMy(a: Context, t: MyTheme): Boolean =
        savedMode(a) == t.mode && a.getSharedPreferences("castv", Context.MODE_PRIVATE).getInt("accent", 0) == t.accent &&
            savedBg(a) == t.bg && savedShape(a) == t.shape && savedFont(a) == t.font

    private fun applyValues(a: Context, mode: Int, accent: Int, bg: Int, shape: Int, font: Int) {
        a.getSharedPreferences("castv", Context.MODE_PRIVATE).edit()
            .putInt("theme", mode).putInt("accent", accent).putInt("bgstyle", bg)
            .putInt("shape", shape).putInt("font", font).apply()
    }

    /** Añade o reemplaza (mismo nombre) un tema propio; máximo 20. */
    private fun addMy(a: Activity, t: MyTheme, onSaved: () -> Unit): Boolean {
        val l = myThemes(a)
        if (l.size >= 20 && l.none { it.name == t.name }) {
            android.widget.Toast.makeText(a, "Máximo 20 temas propios: borra alguno primero", android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        l.removeAll { it.name == t.name }
        l.add(t)
        saveMyThemes(a, l)
        onSaved()
        android.widget.Toast.makeText(a, "✓ Guardado: " + t.name, android.widget.Toast.LENGTH_SHORT).show()
        return true
    }

    private fun askName(a: Activity, title: String, initial: String, onOk: (String) -> Unit) {
        val dp = a.resources.displayMetrics.density
        val et = android.widget.EditText(a).apply {
            setText(initial)
            setSelectAllOnFocus(true)
            isSingleLine = true
            filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(24))
            setTextColor(ContextCompat.getColor(a, R.color.vlc_text))
        }
        val box = LinearLayout(a).apply {
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Guardar") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Mantener pulsado en la galería: copia de un tema de fábrica, o editar uno propio. */
    private fun editMenu(a: Activity, p: Pack, onSaved: () -> Unit, reopen: () -> Unit) {
        if (p.kind == 0) {
            AlertDialog.Builder(a)
                .setTitle(p.name)
                .setItems(arrayOf("Guardar una copia en Mis temas", "Cancelar")) { _, w ->
                    if (w == 0) {
                        askName(a, "Nombre de la copia", p.name + " (mío)") { n ->
                            val name = if (n.isEmpty()) p.name + " (mío)" else n
                            addMy(a, MyTheme(name, p.mode, p.accent, p.bg, p.shape, p.font), onSaved)
                            reopen()
                        }
                    } else reopen()
                }
                .show()
            return
        }
        AlertDialog.Builder(a)
            .setTitle(p.name)
            .setItems(arrayOf("Actualizar con lo de ahora", "Renombrar", "Borrar", "Cancelar")) { _, w ->
                val l = myThemes(a)
                val i = l.indexOfFirst { it.name == p.name }
                when {
                    w == 0 && i >= 0 -> {
                        l[i] = currentAsMy(a, p.name)
                        saveMyThemes(a, l); onSaved()
                        android.widget.Toast.makeText(a, "✓ Actualizado: " + p.name, android.widget.Toast.LENGTH_SHORT).show()
                        reopen()
                    }
                    w == 1 && i >= 0 -> askName(a, "Nuevo nombre", p.name) { n ->
                        val cur = myThemes(a)
                        val j = cur.indexOfFirst { it.name == p.name }
                        if (n.isEmpty() || j < 0) {
                            reopen()
                        } else if (n != p.name && cur.any { it.name == n }) {
                            android.widget.Toast.makeText(a, "Ya hay un tema con ese nombre", android.widget.Toast.LENGTH_SHORT).show()
                            reopen()
                        } else {
                            val t = cur[j]
                            cur[j] = MyTheme(n, t.mode, t.accent, t.bg, t.shape, t.font)
                            saveMyThemes(a, cur); onSaved(); reopen()
                        }
                    }
                    w == 2 && i >= 0 -> AlertDialog.Builder(a)
                        .setTitle("¿Borrar «" + p.name + "»?")
                        .setPositiveButton("Borrar") { _, _ ->
                            val cur = myThemes(a)
                            cur.removeAll { it.name == p.name }
                            saveMyThemes(a, cur); onSaved(); reopen()
                        }
                        .setNegativeButton("Cancelar") { _, _ -> reopen() }
                        .show()
                    else -> reopen()
                }
            }
            .show()
    }

    /**
     * Galería de temas con vista previa; al elegir guarda los cinco ajustes y llama a [onPicked].
     * [TEMAS-6] Arriba: «＋ Guardar el tema de ahora» y «Mis temas»; mantener pulsado = copiar, renombrar, actualizar o borrar.
     * [onSaved] se llama cuando cambia la lista o el nombre que muestra Ajustes.
     */
    fun pickPack(a: Activity, onSaved: () -> Unit = {}, onPicked: () -> Unit) {
        val mine = myThemes(a)
        val rows = ArrayList<Pack>()
        rows.add(Pack("＋ Guardar el tema de ahora", "Con el nombre que quieras: modo, acento, fondo, forma y letra", 2, 0, 0, 0, 0, 0, 0, 2))
        for (t in mine) {
            rows.add(Pack(
                t.name, "Mi tema · mantén pulsado para editarlo", t.mode, t.accent, t.bg, t.shape, t.font,
                ContextCompat.getColor(a, bgSwatches[t.bg.coerceIn(0, bgSwatches.size - 1)]),
                ContextCompat.getColor(a, swatches[t.accent.coerceIn(0, swatches.size - 1)]), 1
            ))
        }
        rows.addAll(packs)
        val cur = rows.indexOfFirst { it.kind != 2 && matches(a, it) }
        val dp = a.resources.displayMetrics.density
        val adapter = object : ArrayAdapter<Pack>(a, 0, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val p = rows[position]
                val tf = typefaceOf(p.font)
                val fg = if (p.kind == 2) ContextCompat.getColor(a, R.color.vlc_text) else p.pAccent
                val prev = TextView(a).apply {
                    text = if (p.kind == 2) "＋" else "Aa"
                    textSize = 18f
                    typeface = if (p.kind == 2) Typeface.DEFAULT else tf
                    gravity = Gravity.CENTER
                    setTextColor(fg)
                    background = GradientDrawable().apply {
                        setColor(if (p.kind == 2) 0 else p.pBg)
                        cornerRadius = (when (p.shape) { 1 -> 3f; 2 -> 22f; else -> 12f }) * dp
                        setStroke((2 * dp).toInt(), fg)
                    }
                    layoutParams = LinearLayout.LayoutParams((64 * dp).toInt(), (44 * dp).toInt())
                }
                val name = TextView(a).apply {
                    text = if (position == cur) p.name + "  ✓" else p.name
                    textSize = 16f
                    typeface = if (p.kind == 2) Typeface.DEFAULT_BOLD else tf
                    setTextColor(ContextCompat.getColor(a, R.color.vlc_text))
                }
                val desc = TextView(a).apply {
                    text = p.desc
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(a, R.color.vlc_muted))
                }
                val box = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((16 * dp).toInt(), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(name)
                    addView(desc)
                }
                return LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((24 * dp).toInt(), (10 * dp).toInt(), (24 * dp).toInt(), (10 * dp).toInt())
                    addView(prev)
                    addView(box)
                }
            }
        }
        val reopen = { pickPack(a, onSaved, onPicked) }
        val dlg = AlertDialog.Builder(a)
            .setTitle("Tema")
            .setAdapter(adapter) { d, which ->
                val p = rows[which]
                d.dismiss()
                if (p.kind == 2) {
                    val n0 = "Mi tema " + (mine.size + 1)
                    askName(a, "Nombre de tu tema", n0) { n ->
                        addMy(a, currentAsMy(a, if (n.isEmpty()) n0 else n), onSaved)
                        reopen()
                    }
                } else {
                    applyValues(a, p.mode, p.accent, p.bg, p.shape, p.font)
                    onPicked()
                }
            }
            .show()
        dlg.listView.setOnItemLongClickListener { _, _, pos, _ ->
            val p = rows[pos]
            if (p.kind != 2) {
                dlg.dismiss()
                editMenu(a, p, onSaved, reopen)
            }
            true
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
            .setTitle("Fondo")
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
