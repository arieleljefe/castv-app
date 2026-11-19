package com.castv.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.Normalizer

/**
 * [UI-1b] Pantalla de Ajustes (botón ⋮), organizada por categorías como en VLC.
 * Tema, color y borrado se cambian aquí mismo. Respaldo y "Mis redes y contacto" le piden a
 * MainActivity que los abra (extra "action": "backup" o "socials") y esta pantalla se cierra.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private var txtColor = Color.BLACK
    private var mutedColor = Color.GRAY
    private var lineColor = Color.GRAY

    private val accentNames = arrayOf("Cian", "Verde", "Naranja", "Morado")

    // [AJUSTES-2a] buscador y estilo de subtítulos
    private lateinit var search: EditText
    private var query = ""
    private val subSizeNames = arrayOf("Pequeño", "Mediano", "Grande", "Muy grande")
    private val subColorNames = arrayOf("Blanco", "Amarillo", "Cian", "Verde")
    private val subBgNames = arrayOf("Semitransparente", "Opaco", "Sin fondo")

    private class Entry(
        val section: String, val title: String, val subtitle: String?, val onClick: (() -> Unit)?)

    private fun prefs() = getSharedPreferences("castv", MODE_PRIVATE)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun accent(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.castvAccent, tv, true)
        return tv.data
    }

    /** Tema guardado: 0 = Sistema, 1 = Claro, 2 = Oscuro (por defecto Oscuro, igual que MainActivity). */
    private fun savedTheme(): Int = prefs().getInt("theme", 2)

    /** Color de acento guardado: 0 Cian, 1 Verde, 2 Naranja, 3 Morado. */
    private fun savedAccent(): Int = prefs().getInt("accent", 0)

    private fun themeLabel(): String = when (savedTheme()) {
        0 -> "Sistema"
        1 -> "Claro"
        else -> "Oscuro"
    }

    private fun applyThemeMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_YES
        })
    }

    /** Android 11+: se puede mandar a la papelera del sistema. Misma regla que MainActivity. */
    private fun useTrash(): Boolean =
        Build.VERSION.SDK_INT >= 30 && prefs().getBoolean("trash", true)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeMode(savedTheme())
        super.onCreate(savedInstanceState)
        theme.applyStyle(when (savedAccent()) {
            1 -> R.style.AccentOverlayGreen
            2 -> R.style.AccentOverlayOrange
            3 -> R.style.AccentOverlayPurple
            else -> R.style.AccentOverlayCyan
        }, true)

        txtColor = ContextCompat.getColor(this, R.color.vlc_text)
        mutedColor = ContextCompat.getColor(this, R.color.vlc_muted)
        lineColor = ContextCompat.getColor(this, R.color.vlc_stroke)

        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.vlc_bg))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        // Encabezado: ← Ajustes
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(8), dp(8), dp(16), dp(8))
        val back = TextView(this)
        back.text = "←"
        back.textSize = 26f
        back.setTextColor(txtColor)
        back.gravity = Gravity.CENTER
        back.contentDescription = "Atrás"
        back.setPadding(dp(12), dp(4), dp(12), dp(4))
        back.setOnClickListener { finish() }
        header.addView(back)
        val title = TextView(this)
        title.text = "Ajustes"
        title.textSize = 22f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setTextColor(txtColor)
        header.addView(title)
        root.addView(header)

        // [AJUSTES-2a] Buscador de ajustes (fuera del ScrollView para no perder el foco al redibujar)
        search = EditText(this)
        search.hint = "🔍 Buscar ajustes"
        search.isSingleLine = true
        search.textSize = 15f
        search.setTextColor(txtColor)
        search.setHintTextColor(mutedColor)
        search.setPadding(dp(16), dp(10), dp(16), dp(10))
        val boxBg = GradientDrawable()
        boxBg.setColor(ContextCompat.getColor(this, R.color.vlc_surface))
        boxBg.cornerRadius = dp(10).toFloat()
        search.background = boxBg
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                render()
            }
        })
        val searchLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        searchLp.setMargins(dp(16), 0, dp(16), dp(4))
        root.addView(search, searchLp)

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        render()
    }

    // ------------------------------------------------------------ Contenido

    private fun norm(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    private fun entries(): List<Entry> {
        val l = ArrayList<Entry>()
        l.add(Entry("Interfaz", "Tema", themeLabel(), { pickTheme() }))
        l.add(Entry("Interfaz", "Color de acento", accentNames[savedAccent().coerceIn(0, 3)], { pickAccent() }))

        l.add(Entry("Subtítulos", "Tamaño", subSizeNames[stSize()],
            { pickSub("Tamaño", subSizeNames, stSize(), "style_size") }))
        l.add(Entry("Subtítulos", "Negrita", if (stBold()) "Sí" else "No", { toggleSubBold() }))
        l.add(Entry("Subtítulos", "Color del texto", subColorNames[stColor()],
            { pickSub("Color del texto", subColorNames, stColor(), "style_color") }))
        l.add(Entry("Subtítulos", "Fondo", subBgNames[stBg()],
            { pickSub("Fondo", subBgNames, stBg(), "style_bg") }))
        l.add(Entry("Subtítulos", "Restablecer estilo",
            "Tamaño mediano, texto blanco, fondo semitransparente", { resetSubStyle() }))

        if (Build.VERSION.SDK_INT >= 30) {
            l.add(Entry("Almacenamiento", "Borrado",
                if (useTrash()) "A la papelera de Android" else "Permanente (no se puede deshacer)",
                { toggleTrash() }))
        }

        l.add(Entry("Respaldo", "Exportar o importar respaldo",
            "Favoritos, listas, historial de enlaces, listas M3U y ajustes", { sendAction("backup") }))

        l.add(Entry("Acerca de", "Mis redes y contacto", "Creado por Ariel De Jesus Bueno",
            { sendAction("socials") }))
        l.add(Entry("Acerca de", "Versión", versionText(), null))
        return l
    }

    private fun render() {
        content.removeAllViews()
        val q = norm(query.trim())
        val list = entries().filter {
            q.isEmpty() || norm(it.section + " " + it.title + " " + (it.subtitle ?: "")).contains(q)
        }
        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Sin resultados"
            tv.textSize = 15f
            tv.setTextColor(mutedColor)
            tv.setPadding(dp(20), dp(24), dp(20), dp(8))
            content.addView(tv)
            return
        }
        var last = ""
        for (e in list) {
            if (e.section != last) {
                section(e.section, first = last.isEmpty())
                last = e.section
            }
            row(e.title, e.subtitle, e.onClick)
        }
    }

    private fun section(name: String, first: Boolean = false) {
        if (!first) {
            val line = View(this)
            line.setBackgroundColor(lineColor)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            lp.topMargin = dp(8)
            content.addView(line, lp)
        }
        val tv = TextView(this)
        tv.text = name
        tv.textSize = 13f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(accent())
        tv.setPadding(dp(20), dp(16), dp(20), dp(4))
        content.addView(tv)
    }

    private fun row(title: String, subtitle: String?, onClick: (() -> Unit)?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(14), dp(20), dp(14))
        if (onClick != null) {
            val sel = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
            box.setBackgroundResource(sel.resourceId)
            box.setOnClickListener { onClick() }
        }
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(txtColor)
        box.addView(t)
        if (subtitle != null) {
            val s = TextView(this)
            s.text = subtitle
            s.textSize = 13f
            s.setTextColor(mutedColor)
            s.setPadding(0, dp(2), 0, 0)
            box.addView(s)
        }
        content.addView(box)
    }

    private fun versionText(): String = try {
        "CASTV " + (packageManager.getPackageInfo(packageName, 0).versionName ?: "")
    } catch (e: Exception) {
        "CASTV"
    }

    // -------------------------------------------------------------- Acciones

    private fun pickTheme() {
        val labels = arrayOf("Sistema", "Claro", "Oscuro")
        AlertDialog.Builder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(labels, savedTheme()) { d, which ->
                prefs().edit().putInt("theme", which).apply()
                d.dismiss()
                render()
                applyThemeMode(which)   // recrea las pantallas con el tema elegido
            }
            .show()
    }

    private fun pickAccent() {
        AlertDialog.Builder(this)
            .setTitle("Color de acento")
            .setSingleChoiceItems(accentNames, savedAccent().coerceIn(0, 3)) { d, which ->
                prefs().edit().putInt("accent", which).apply()
                d.dismiss()
                recreate()   // esta pantalla se redibuja con el color; MainActivity lo hace al volver
            }
            .show()
    }

    private fun toggleTrash() {
        val now = !useTrash()
        prefs().edit().putBoolean("trash", now).apply()
        toast(if (now) "🗑 Lo borrado irá a la papelera de Android"
              else "⚠️ Lo borrado se eliminará para siempre")
        render()
    }

    // ------------------------------------------- [AJUSTES-2a] Estilo de subtítulos
    // Mismas preferencias que usa VideoPlayerActivity (castv_subs); se aplican al abrir un video.

    private fun subPrefs() = getSharedPreferences("castv_subs", MODE_PRIVATE)
    private fun stSize() = subPrefs().getInt("style_size", 1).coerceIn(0, subSizeNames.size - 1)
    private fun stColor() = subPrefs().getInt("style_color", 0).coerceIn(0, subColorNames.size - 1)
    private fun stBg() = subPrefs().getInt("style_bg", 0).coerceIn(0, subBgNames.size - 1)
    private fun stBold() = subPrefs().getBoolean("style_bold", false)

    private fun pickSub(title: String, names: Array<String>, current: Int, key: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names, current) { d, which ->
                subPrefs().edit().putInt(key, which).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    private fun toggleSubBold() {
        subPrefs().edit().putBoolean("style_bold", !stBold()).apply()
        render()
    }

    private fun resetSubStyle() {
        subPrefs().edit().remove("style_size").remove("style_bold")
            .remove("style_color").remove("style_bg").apply()
        toast("↩ Estilo de subtítulos restablecido")
        render()
    }

    /** Cierra Ajustes y le pide a MainActivity que abra respaldo o redes. */
    private fun sendAction(action: String) {
        setResult(RESULT_OK, Intent().putExtra("action", action))
        finish()
    }
}
