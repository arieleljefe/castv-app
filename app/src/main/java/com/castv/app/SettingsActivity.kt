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
import androidx.activity.OnBackPressedCallback
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
    private lateinit var headerTitle: TextView
    private var openSection: String? = null   // [AJUSTES-3] sección abierta (null = portada)
    private val sectionBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showSection(null) }
    }
    private var unlocked = false   // [PARENTAL-1] PIN ya escrito en esta sesión de Ajustes
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
        Store.init(this)   // [AJUSTES-2b] por si Android recrea Ajustes sin pasar por MainActivity
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
        back.setOnClickListener { if (openSection != null) showSection(null) else finish() }   // [AJUSTES-3]
        header.addView(back)
        val title = TextView(this)
        headerTitle = title
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
        onBackPressedDispatcher.addCallback(this, sectionBack)   // [AJUSTES-3]
        openSection = savedInstanceState?.getString("section")   // se conserva al cambiar color o tema
        render()

        // [PARENTAL-1] con PIN activo, Ajustes solo se abre con el PIN
        unlocked = savedInstanceState?.getBoolean("unlocked") ?: false
        if (pinSet() && !unlocked) {
            content.visibility = View.INVISIBLE
            askPin("Ajustes bloqueados", exitOnCancel = true) {
                unlocked = true
                content.visibility = View.VISIBLE
            }
        } else {
            unlocked = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
        outState.putString("section", openSection)
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

        // [RED-1] Red
        l.add(Entry("Red", "Avisar con datos móviles",
            if (warnMobile()) "Sí: pregunta antes de reproducir un enlace en el móvil" else "No",
            { toggleWarnMobile() }))

        // [PUERTO-1] Puerto real del servidor interno (solo informativo)
        val srvErr = ServerState.error
        l.add(Entry("Red", "Puerto del servidor",
            if (srvErr != null) "Sin puerto libre (se prueba del 8000 al 8010)"
            else "${ServerPort.port} (se busca uno libre del 8000 al 8010)", null))

        // [GESTO-1] Vídeo
        l.add(Entry("Vídeo", "Salto con doble toque", "${dtapSec()} segundos (en el reproductor de video)", { pickDtap() }))
        // [GESTOS-AJ-1] cada gesto se puede activar o desactivar; velocidad, controles y botones
        l.add(Entry("Vídeo", "Deslizar de lado: adelantar", gState("g_seek"), { toggleG("g_seek", "Deslizar de lado") }))
        l.add(Entry("Vídeo", "Deslizar a la derecha: brillo", gState("g_bright"), { toggleG("g_bright", "Brillo") }))
        l.add(Entry("Vídeo", "Deslizar a la izquierda: volumen", gState("g_vol"), { toggleG("g_vol", "Volumen") }))
        l.add(Entry("Vídeo", "Doble toque", gState("g_dtap") + " (con el doble toque apagado, dos toques muestran u ocultan los controles)", { toggleG("g_dtap", "Doble toque") }))
        l.add(Entry("Vídeo", "Mantener pulsado: acelerar", gState("g_hold"), { toggleG("g_hold", "Mantener pulsado") }))
        l.add(Entry("Vídeo", "Velocidad al mantener pulsado", fastNames[fastIdx()], { pickFast() }))
        l.add(Entry("Vídeo", "Zoom con pellizco", gState("g_pinch"), { toggleG("g_pinch", "Zoom con pellizco") }))
        l.add(Entry("Vídeo", "Tiempo de los controles", "Se esconden a los ${hideLabels[hideIdx()]}", { pickHide() }))
        l.add(Entry("Vídeo", "Salto de los botones −N / +N", btnLabel(btnSecPref()), { pickBtn() }))
        l.add(Entry("Vídeo", "Recordar brillo",   // [GESTO-2]
            if (rememberBright()) "Sí: el video se abre con el último brillo que pusiste"
            else "No: el video usa el brillo del teléfono", { toggleRememberBright() }))
        l.add(Entry("Vídeo", "Consejos de gestos", "Cómo usar los gestos del reproductor de video", { sendAction("tips") }))   // [NOVEDADES-1]

        // [BIBLIO-1] Biblioteca
        l.add(Entry("Biblioteca", "Re-escanear ahora", "Vuelve a leer tus videos y tu música", { sendAction("rescan") }))
        l.add(Entry("Biblioteca", "Re-escanear al volver a la app",
            if (rescanOnResume()) "Sí: actualiza la lista si pasó más de un minuto"
            else "No: solo al abrir la app o cambiar de pestaña",
            { toggleRescan() }))
        val hiddenF = hiddenFolders()   // [CARPETAS-1]
        val onHiddenF: (() -> Unit)? = if (hiddenF.isEmpty()) null else ({ pickHiddenFolders() })
        l.add(Entry("Biblioteca", "Carpetas ocultas",
            if (hiddenF.isEmpty()) "Ninguna (mantén pulsada una carpeta en el selector 📁 para ocultarla)"
            else "${hiddenF.size} ocultas (toca para elegir cuáles mostrar de nuevo)", onHiddenF))

        // [AUDIO-1] Audio
        val bassOn = prefs().getBoolean("bassOn", false)
        l.add(Entry("Audio", "Ecualizador (teléfono)",
            "Graves reforzados: ${if (bassOn) "Sí" else "No"}. Pon una pista en el teléfono antes de abrirlo",
            { sendAction("equalizer") }))

        // [PARENTAL-1] Control parental
        l.add(Entry("Control parental", "PIN de bloqueo",
            if (pinSet()) "Activado: se pide para abrir Ajustes (toca para cambiarlo o quitarlo)"
            else "Sin PIN (toca para crear uno de 4 a 6 números)",
            { onPinRow() }))
        if (pinSet()) {   // [PARENTAL-2] qué protege el PIN además de Ajustes
            l.add(Entry("Control parental", "Pedir PIN para borrar archivos",
                if (pinFlag("pin_delete", true)) "Sí" else "No", { togglePinFlag("pin_delete", true) }))
            l.add(Entry("Control parental", "Pedir PIN para abrir Canales",
                if (pinFlag("pin_channels", false)) "Sí" else "No", { togglePinFlag("pin_channels", false) }))
        }

        // [AJUSTES-2b] Emisión al TV (solo informativo)
        val tvIp = TvController.tvIp
        l.add(Entry("Emisión al TV", "TV detectada", tvIp?.toString() ?: "Ninguna todavía", null))
        l.add(Entry("Emisión al TV", "IP del teléfono", CastvNet.localIpAddress(tvIp) ?: "Sin conexión Wi-Fi", null))

        // [AJUSTES-2b] Historial
        val recentLinks = Store.links().count { !it.fav }
        val onLinks: (() -> Unit)? = if (recentLinks == 0) null else ({ confirmClearLinks(recentLinks) })
        l.add(Entry("Historial", "Historial de enlaces",
            if (recentLinks == 0) "Vacío (los favoritos se conservan)"
            else "$recentLinks recientes (los favoritos se conservan)", onLinks))
        val resumes = Store.resumeCount()
        val onResume: (() -> Unit)? = if (resumes == 0) null else ({ confirmClearResume(resumes) })
        l.add(Entry("Historial", "Continuar donde lo dejé",
            if (resumes == 0) "Sin puntos guardados" else "$resumes puntos guardados en videos y canciones", onResume))
        val hidden = Store.hiddenChannels().size
        val onHidden: (() -> Unit)? = if (hidden == 0) null else ({ confirmUnhideAll(hidden) })
        l.add(Entry("Historial", "Canales ocultos",
            if (hidden == 0) "Ninguno" else "$hidden ocultos (tocar para mostrarlos todos)", onHidden))

        if (Build.VERSION.SDK_INT >= 30) {
            l.add(Entry("Almacenamiento", "Borrado",
                if (useTrash()) "A la papelera de Android" else "Permanente (no se puede deshacer)",
                { toggleTrash() }))
        }

        // [AJUSTES-2c] Avanzado: registro de la app
        l.add(Entry("Avanzado", "Ver registro", "Eventos de la app y del servidor, para diagnosticar fallos", { showLog() }))
        l.add(Entry("Avanzado", "Limpiar registro", "Borra el registro actual", { confirmClearLog() }))

        l.add(Entry("Respaldo", "Exportar o importar respaldo",
            "Favoritos, listas, historial de enlaces, listas M3U y ajustes", { sendAction("backup") }))

        l.add(Entry("Acerca de", "Acerca de CASTV", "Versión, redes y contacto, librerías y licencia",
            { startActivity(Intent(this, AboutActivity::class.java)) }))   // [ACERCA-1]
        l.add(Entry("Acerca de", "Versión", versionText(), null))
        return l
    }

    /** [AJUSTES-3] Abre una sección (o vuelve a la portada con null). */
    private fun showSection(name: String?) {
        openSection = name
        render()
    }

    private fun sectionSummary(name: String): String? = when (name) {
        "Interfaz" -> "Tema y color de acento"
        "Subtítulos" -> "Tamaño, color y fondo"
        "Red" -> "Datos móviles y puerto del servidor"
        "Vídeo" -> "Gestos, velocidad, controles y brillo"
        "Biblioteca" -> "Re-escanear y carpetas ocultas"
        "Audio" -> "Ecualizador"
        "Control parental" -> "PIN de bloqueo"
        "Emisión al TV" -> "TV detectada e IP del teléfono"
        "Historial" -> "Enlaces, puntos guardados y canales ocultos"
        "Almacenamiento" -> "Borrado: papelera o permanente"
        "Avanzado" -> "Registro de la app"
        "Respaldo" -> "Exportar o importar"
        "Acerca de" -> "Versión, autor, redes y licencia"
        else -> null
    }

    private fun render() {
        content.removeAllViews()
        val q = norm(query.trim())
        val all = entries()
        val open = openSection
        sectionBack.isEnabled = open != null
        headerTitle.text = if (q.isEmpty() && open != null) open else "Ajustes"

        // [AJUSTES-3] portada: una fila por sección (sin buscar y sin sección abierta)
        if (q.isEmpty() && open == null) {
            for (name in all.map { it.section }.distinct()) homeRow(name, sectionSummary(name))
            return
        }

        val list = all.filter {
            if (q.isEmpty()) it.section == open
            else norm(it.section + " " + it.title + " " + (it.subtitle ?: "")).contains(q)
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
            // con la búsqueda se siguen viendo los títulos de sección; dentro de una sección no hacen falta
            if (q.isNotEmpty() && e.section != last) {
                section(e.section, first = last.isEmpty())
                last = e.section
            }
            row(e.title, e.subtitle, e.onClick)
        }
    }

    private fun homeRow(name: String, summary: String?) {
        val box = LinearLayout(this)
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(20), dp(14), dp(16), dp(14))
        val sel = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
        box.setBackgroundResource(sel.resourceId)
        box.setOnClickListener { showSection(name) }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = name
        t.textSize = 16f
        t.setTextColor(txtColor)
        col.addView(t)
        if (summary != null) {
            val s = TextView(this)
            s.text = summary
            s.textSize = 13f
            s.setTextColor(mutedColor)
            s.setPadding(0, dp(2), 0, 0)
            col.addView(s)
        }
        box.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = TextView(this)
        arrow.text = "›"
        arrow.textSize = 24f
        arrow.setTextColor(accent())
        box.addView(arrow)
        content.addView(box)
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

    // ------------------------------------------------- [AJUSTES-2b] Historial

    private fun confirmClearLinks(n: Int) {
        AlertDialog.Builder(this)
            .setTitle("Borrar historial de enlaces")
            .setMessage("Se borrarán $n enlaces recientes. Los favoritos se conservan.")
            .setPositiveButton("Borrar") { _, _ ->
                Store.clearRecentLinks()
                toast("🕘 Historial de enlaces borrado")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmClearResume(n: Int) {
        AlertDialog.Builder(this)
            .setTitle("Borrar puntos de reanudación")
            .setMessage("Se olvidará dónde lo dejaste en $n videos y canciones; volverán a empezar desde el principio.")
            .setPositiveButton("Borrar") { _, _ ->
                Store.clearAllResume()
                toast("⏱ Puntos de reanudación borrados")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmUnhideAll(n: Int) {
        AlertDialog.Builder(this)
            .setTitle("Mostrar canales ocultos")
            .setMessage("Volverán a verse los $n canales que ocultaste.")
            .setPositiveButton("Mostrar") { _, _ ->
                Store.unhideChannels(Store.hiddenUrls())
                toast("👁 Canales ocultos devueltos a la vista")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------- [AJUSTES-2c] Avanzado: registro

    private fun showLog() {
        val full = try { DebugLog.dump() } catch (e: Exception) { "" }
        val tv = TextView(this)
        tv.text = if (full.isBlank()) "(El registro está vacío)" else full
        tv.textSize = 11f
        tv.typeface = android.graphics.Typeface.MONOSPACE
        tv.setTextColor(txtColor)
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Registro")
            .setView(sv)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Copiar") { _, _ -> copyLog(full) }
            .show()
    }

    private fun copyLog(text: String) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Registro CASTV", text))
            toast("📋 Registro copiado")
        } catch (e: Exception) {
            toast("No se pudo copiar el registro (es muy grande)")
        }
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar registro")
            .setMessage("Se borrará el registro actual. Después solo se anotará lo nuevo.")
            .setPositiveButton("Limpiar") { _, _ ->
                try { DebugLog.clear() } catch (e: Exception) { }
                toast("🧹 Registro limpio")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------- [RED-1] Red

    private fun warnMobile(): Boolean = prefs().getBoolean("warn_mobile", true)

    private fun toggleWarnMobile() {
        val now = !warnMobile()
        prefs().edit().putBoolean("warn_mobile", now).apply()
        toast(if (now) "📶 Se avisará al usar datos móviles" else "📶 Sin aviso con datos móviles")
        render()
    }

    // ------------------------------------------------- [BIBLIO-1] Biblioteca

    private fun rescanOnResume(): Boolean = prefs().getBoolean("rescan_resume", false)

    private fun toggleRescan() {
        val now = !rescanOnResume()
        prefs().edit().putBoolean("rescan_resume", now).apply()
        toast(if (now) "🔄 Se actualizará la lista al volver a la app" else "🔄 Sin re-escaneo automático")
        render()
    }

    // ------------------------------------------------- [PARENTAL-1] Control parental

    private fun pinHash(pin: String): String {
        val b = java.security.MessageDigest.getInstance("SHA-256").digest(("castv:" + pin).toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun pinSet(): Boolean = !prefs().getString("pin_hash", "").isNullOrEmpty()

    private fun pinBox(): EditText {
        val et = EditText(this)
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        et.hint = "PIN"
        et.setSingleLine(true)
        return et
    }

    /** Pide el PIN actual y, si es correcto, ejecuta onOk. Con exitOnCancel, Salir cierra Ajustes. */
    private fun askPin(title: String, exitOnCancel: Boolean = false, onOk: () -> Unit) {
        val et = pinBox()
        val d = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Escribe tu PIN")
            .setView(et)
            .setCancelable(false)
            .setPositiveButton("Entrar", null)
            .setNegativeButton(if (exitOnCancel) "Salir" else "Cancelar") { _, _ ->
                if (exitOnCancel) finish()
            }
            .setNeutralButton("Olvidé el PIN") { _, _ -> showForgotPin(exitOnCancel) }
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (pinHash(et.text.toString()) == prefs().getString("pin_hash", "")) {
                    d.dismiss()
                    onOk()
                } else {
                    et.setText("")
                    toast("PIN incorrecto")
                }
            }
        }
        d.show()
    }

    private fun showForgotPin(exitAfter: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Olvidé el PIN")
            .setMessage(
                "CASTV no puede recuperar el PIN. Para quitarlo: Ajustes de Android › Apps › CASTV › " +
                "Almacenamiento › Borrar datos. Se pierden favoritos, listas e historial " +
                "(si tienes un respaldo exportado, impórtalo después)."
            )
            .setCancelable(false)
            .setPositiveButton("Entendido") { _, _ -> if (exitAfter) finish() }
            .show()
    }

    private fun onPinRow() {
        if (!pinSet()) {
            createPin()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("PIN de bloqueo")
            .setItems(arrayOf("Cambiar PIN", "Quitar PIN")) { _, which ->
                askPin(if (which == 0) "Cambiar PIN" else "Quitar PIN") {
                    if (which == 0) {
                        createPin()
                    } else {
                        prefs().edit().remove("pin_hash").apply()
                        toast("🔓 PIN quitado")
                        render()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createPin() {
        val a = pinBox()
        a.hint = "PIN nuevo (4 a 6 números)"
        val b = pinBox()
        b.hint = "Repite el PIN"
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(a)
        box.addView(b)
        AlertDialog.Builder(this)
            .setTitle("Crear PIN")
            .setMessage("CASTV no puede recuperar un PIN olvidado. Anótalo en un lugar seguro y, si quieres, exporta antes un respaldo.")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val p1 = a.text.toString()
                val p2 = b.text.toString()
                when {
                    p1.length < 4 -> toast("El PIN debe tener de 4 a 6 números")
                    p1 != p2 -> toast("Los PIN no coinciden")
                    else -> {
                        prefs().edit().putString("pin_hash", pinHash(p1)).apply()
                        toast("🔒 PIN guardado")
                        render()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------- [PARENTAL-2] Qué protege el PIN

    private fun pinFlag(key: String, def: Boolean): Boolean = prefs().getBoolean(key, def)

    private fun togglePinFlag(key: String, def: Boolean) {
        val now = !pinFlag(key, def)
        prefs().edit().putBoolean(key, now).apply()
        toast(if (now) "🔒 Se pedirá el PIN" else "🔓 Ya no se pedirá el PIN aquí")
        render()
    }

    // ------------------------------------------------- [CARPETAS-1] Carpetas ocultas

    private fun hiddenFolders(): Set<String> = prefs().getStringSet("hidden_folders", emptySet()) ?: emptySet()

    private fun pickHiddenFolders() {
        val hidden = hiddenFolders().sorted()
        if (hidden.isEmpty()) return
        val checked = BooleanArray(hidden.size) { true }
        AlertDialog.Builder(this)
            .setTitle("Carpetas ocultas")
            .setMultiChoiceItems(hidden.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Guardar") { _, _ ->
                val keep = HashSet<String>()
                hidden.forEachIndexed { i, f -> if (checked[i]) keep.add(f) }
                prefs().edit().putStringSet("hidden_folders", keep).apply()
                toast(if (keep.size < hidden.size) "📁 Carpetas mostradas de nuevo" else "Sin cambios")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------- [GESTO-1] Vídeo

    private val dtapOptions = intArrayOf(5, 10, 15, 30)

    private fun dtapSec(): Int = prefs().getInt("dtap_sec", 10).let { if (it in dtapOptions) it else 10 }

    private fun pickDtap() {
        val labels = dtapOptions.map { "$it segundos" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Salto con doble toque")
            .setSingleChoiceItems(labels, dtapOptions.indexOf(dtapSec())) { d, which ->
                prefs().edit().putInt("dtap_sec", dtapOptions[which]).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    // [GESTO-2] Recordar el brillo que se ajusta deslizando en el video
    private fun rememberBright(): Boolean = prefs().getBoolean("video_bright_on", true)

    private fun toggleRememberBright() {
        val now = !rememberBright()
        val ed = prefs().edit().putBoolean("video_bright_on", now)
        if (!now) ed.remove("video_bright")
        ed.apply()
        toast(if (now) "☀ Se recordará el brillo del video" else "☀ El video usará el brillo del teléfono")
        render()
    }

    // ------------------------------------------------- [GESTOS-AJ-1] Gestos y controles del video

    private val fastNames = arrayOf("1.5x", "2x", "3x")
    private val hideOptions = intArrayOf(2000, 3500, 5000, 8000)
    private val hideLabels = arrayOf("2 s", "3,5 s", "5 s", "8 s")
    private val btnOptions = intArrayOf(0, 5, 10, 15, 30)   // 0 = igual que el doble toque

    private fun gOn(key: String): Boolean = prefs().getBoolean(key, true)
    private fun gState(key: String): String = if (gOn(key)) "Activado" else "Desactivado"

    private fun toggleG(key: String, name: String) {
        val now = !gOn(key)
        prefs().edit().putBoolean(key, now).apply()
        toast(if (now) "✅ $name: activado" else "🚫 $name: desactivado")
        render()
    }

    private fun fastIdx(): Int = prefs().getInt("fast_i", 1).coerceIn(0, 2)

    private fun pickFast() {
        AlertDialog.Builder(this)
            .setTitle("Velocidad al mantener pulsado")
            .setSingleChoiceItems(fastNames, fastIdx()) { d, which ->
                prefs().edit().putInt("fast_i", which).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    private fun hideIdx(): Int = hideOptions.indexOf(prefs().getInt("hide_ms", 3500)).let { if (it < 0) 1 else it }

    private fun pickHide() {
        AlertDialog.Builder(this)
            .setTitle("Tiempo de los controles")
            .setSingleChoiceItems(hideLabels, hideIdx()) { d, which ->
                prefs().edit().putInt("hide_ms", hideOptions[which]).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    private fun btnSecPref(): Int = prefs().getInt("btn_sec", 0).let { if (it in btnOptions) it else 0 }

    private fun btnLabel(v: Int): String =
        if (v == 0) "Igual que el doble toque (${dtapSec()} s)" else "$v segundos"

    private fun pickBtn() {
        val labels = btnOptions.map { btnLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Salto de los botones")
            .setSingleChoiceItems(labels, btnOptions.indexOf(btnSecPref())) { d, which ->
                prefs().edit().putInt("btn_sec", btnOptions[which]).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    /** Cierra Ajustes y le pide a MainActivity que abra respaldo o redes. */
    private fun sendAction(action: String) {
        setResult(RESULT_OK, Intent().putExtra("action", action))
        finish()
    }
}
