package com.castv.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

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
        l.add(Entry("Subtítulos", "Idioma de los subtítulos con IA", WhisperModel.langName(this),
            { pickIaLang() }))   // [IA-4]
        l.add(Entry("Subtítulos", "Modelo de voz (IA)", iaModelStatus(),
            { pickIaModel() }))   // [IA-4]
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
        l.add(Entry("Vídeo", "Preguntar antes de continuar",   // [CONFIRMAR-1]
            if (resumeAsk()) "Sí: al abrir un video con punto guardado pregunta si seguir o empezar de nuevo"
            else "No: continúa solo desde donde lo dejaste", { toggleResumeAsk() }))
        l.add(Entry("Vídeo", "Ventana flotante al pulsar Inicio",   // [PIP-2]
            if (pipAuto()) "Sí: el video sigue en una ventana flotante al salir con Inicio"
            else "No: el video se pausa al salir (la ventana manual sigue en 🛠 › 🪟)", { togglePipAuto() }))
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
        l.add(Entry("Emisión al TV", "Salida al abrir la app", outStartNames[outStartIdx()], { pickOutStart() }))   // [SALIDA-INI-1]
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
        l.add(Entry("Avanzado", "Probar extracción de audio (IA)",
            "Prueba para los subtítulos con IA: saca el audio de un video y mide cuánto tarda",
            { pickIaVideo.launch(arrayOf("video/*")) }))   // [IA-1]
        l.add(Entry("Avanzado", "Probar Whisper (IA)",
            "Comprueba que el motor de subtítulos con IA carga en este teléfono (todavía no transcribe)",
            { runWhisperTest() }))   // [IA-2a]
        l.add(Entry("Avanzado", "Probar transcripción (IA)",
            "Baja el modelo de voz elegido (una vez) y transcribe un video; mide la velocidad. Todavía no crea subtítulos",
            { startIaTranscribe() }))   // [IA-2b]

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
        "Subtítulos" -> "Tamaño, color, fondo e IA"
        "Red" -> "Datos móviles y puerto del servidor"
        "Vídeo" -> "Gestos, velocidad, controles y brillo"
        "Biblioteca" -> "Re-escanear y carpetas ocultas"
        "Audio" -> "Ecualizador"
        "Control parental" -> "PIN de bloqueo"
        "Emisión al TV" -> "Salida al abrir, TV detectada e IP del teléfono"
        "Historial" -> "Enlaces, puntos guardados y canales ocultos"
        "Almacenamiento" -> "Borrado: papelera o permanente"
        "Avanzado" -> "Registro de la app y pruebas"
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

    // ------------------------------------------------- [IA-4] Idioma y modelo de la transcripción con IA

    private fun iaModelStatus(): String {
        val sp = WhisperModel.selected(this)
        return if (WhisperModel.isReady(this, sp)) "${sp.fullLabel}: listo"
        else "${sp.fullLabel}: falta bajarlo, ${sp.sizeText}"
    }

    private fun pickIaLang() {
        val names = WhisperModel.LANGS.map { it.second }.toTypedArray()
        val cur = WhisperModel.LANGS.indexOfFirst { it.first == WhisperModel.lang(this) }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Idioma de los subtítulos con IA")
            .setSingleChoiceItems(names, cur) { d, which ->
                WhisperModel.setLang(this, WhisperModel.LANGS[which].first)
                d.dismiss()
                render()
            }
            .show()
    }

    private fun pickIaModel() {
        val specs = WhisperModel.MODELS
        val names = specs.map {
            it.fullLabel + if (WhisperModel.isReady(this, it)) "" else " · falta bajarlo, ${it.sizeText}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Modelo de voz (IA)")
            .setSingleChoiceItems(names, WhisperModel.selected(this).id) { d, which ->
                d.dismiss()
                val sp = specs[which]
                if (sp.id != WhisperModel.selected(this).id) {
                    if (WhisperModel.isReady(this, sp)) {
                        WhisperModel.setModel(this, sp.id)
                        render()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Falta el modelo ${sp.label}")
                            .setMessage("Hay que bajar el modelo de voz (${sp.shortName}, ${sp.sizeText}, una sola vez). Conviene estar con Wi-Fi. ¿Descargarlo ahora?")
                            .setPositiveButton("Descargar") { _, _ -> downloadWhisperModel(sp, false) }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
            .show()
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

    // ------------------------------------------------- [IA-1] Prueba de extracción de audio

    private val pickIaVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runIaAudioTest(uri)
    }

    private fun runIaAudioTest(uri: Uri) {
        var shown: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) shown = c.getString(0)
            }
        } catch (e: Exception) { }
        val name = shown ?: (uri.lastPathSegment ?: "video")
        val outFile = File(cacheDir, "ia_audio.pcm")
        val cancel = AtomicBoolean(false)
        val msg = TextView(this)
        msg.text = "Preparando…"
        msg.setTextColor(txtColor)
        msg.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Extrayendo audio")
            .setView(msg)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ -> cancel.set(true) }
            .create()
        dlg.show()
        val act = this@SettingsActivity
        Thread {
            val r = AudioExtractor.extract(act, uri, outFile, { pct ->
                runOnUiThread { msg.text = "Avance: $pct %" }
            }, { cancel.get() })
            try { outFile.delete() } catch (e: Exception) { }
            val secs = r.tookMs / 1000.0
            val line = if (r.ok) {
                "IA-1: $name: ${r.mime} ${r.srcHz} Hz ${r.srcChannels}ch -> ${AudioExtractor.TARGET_HZ} Hz mono; " +
                    "${r.samples} muestras; ${String.format(java.util.Locale.US, "%.1f", r.durationSec)} s de audio; " +
                    "${r.tookMs} ms"
            } else {
                "IA-1: $name: ${r.message}"
            }
            try { DebugLog.add(line) } catch (e: Exception) { }
            runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                try { if (dlg.isShowing) dlg.dismiss() } catch (e: Exception) { }
                val title: String
                val text: String
                if (r.ok) {
                    val mins = String.format(java.util.Locale.US, "%.1f", r.durationSec / 60.0)
                    val took = String.format(java.util.Locale.US, "%.1f", secs)
                    val speed = if (secs > 0) String.format(java.util.Locale.US, "%.0f", r.durationSec / secs) else "-"
                    title = "✅ Audio extraído"
                    text = "$name\n\n$mins min de audio en $took s (unas ${speed}x más rápido que el video).\n" +
                        "Pista: ${r.mime}, ${r.srcHz} Hz, ${r.srcChannels} canales → ${AudioExtractor.TARGET_HZ} Hz mono."
                } else if (r.cancelled) {
                    title = "Cancelado"
                    text = "Se detuvo la extracción y se borró el archivo temporal."
                } else {
                    title = "⚠️ No se pudo extraer el audio"
                    text = r.message
                }
                AlertDialog.Builder(act)
                    .setTitle(title)
                    .setMessage(text)
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }.start()
    }

    // ------------------------------------------------- [IA-2a] Prueba de Whisper

    private fun runWhisperTest() {
        val ok: Boolean
        val info: String
        try {
            info = WhisperLib.systemInfo()
            ok = true
        } catch (t: Throwable) {
            val msg = "${t.javaClass.simpleName}: ${t.message}"
            try { DebugLog.add("IA-2a: no cargó Whisper: $msg") } catch (e: Exception) { }
            AlertDialog.Builder(this)
                .setTitle("⚠️ Whisper no cargó")
                .setMessage(msg)
                .setPositiveButton("Cerrar", null)
                .show()
            return
        }
        try { DebugLog.add("IA-2a: Whisper cargó. ${info.trim()}") } catch (e: Exception) { }
        val tv = TextView(this)
        tv.text = if (ok) info.trim() else ""
        tv.textSize = 12f
        tv.typeface = android.graphics.Typeface.MONOSPACE
        tv.setTextColor(txtColor)
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(24), dp(16), dp(24), dp(8))
        AlertDialog.Builder(this)
            .setTitle("✅ Whisper cargó")
            .setView(tv)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // ------------------------------------------------- [IA-2b] Modelo y transcripción de prueba

    private class IaSeg(val t0: Long, val t1: Long, val text: String)

    private val pickIaTranscribe = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runIaTranscribe(uri)
    }

    private fun fmtCs(cs: Long): String {
        val s = cs / 100
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun startIaTranscribe() {
        if (WhisperModel.isReady(this)) {
            pickIaTranscribe.launch(arrayOf("video/*"))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Falta el modelo de voz")
            .setMessage("Para transcribir hace falta bajar el modelo de voz (${WhisperModel.selected(this).shortName}, ${WhisperModel.selected(this).sizeText}, una sola vez). Conviene estar con Wi-Fi. ¿Descargarlo ahora?")
            .setPositiveButton("Descargar") { _, _ -> downloadWhisperModel() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [IA-4] [spec] = modelo a bajar; [pickAfter] = abrir el selector de video al terminar (la prueba de Avanzado). */
    private fun downloadWhisperModel(
        spec: WhisperModel.Spec = WhisperModel.selected(this),
        pickAfter: Boolean = true
    ) {
        val cancel = AtomicBoolean(false)
        val msg = TextView(this)
        msg.text = "Conectando…"
        msg.setTextColor(txtColor)
        msg.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Descargando modelo")
            .setView(msg)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ -> cancel.set(true) }
            .create()
        dlg.show()
        dlg.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val act = this@SettingsActivity
        Thread {
            val r = WhisperModel.download(act, spec, { pct, mb ->
                runOnUiThread {
                    msg.text = if (pct >= 0) "Avance: $pct % ($mb MB)" else "Descargado: $mb MB"
                }
            }, { cancel.get() })
            try { DebugLog.add("IA-2b: modelo ${spec.shortName}: ${r.message}") } catch (e: Exception) { }
            runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                try { if (dlg.isShowing) dlg.dismiss() } catch (e: Exception) { }
                if (r.ok) {
                    if (pickAfter) {
                        pickIaTranscribe.launch(arrayOf("video/*"))
                    } else {
                        WhisperModel.setModel(act, spec.id)   // [IA-4] solo se cambia si la descarga terminó bien
                        toast("Modelo ${spec.fullLabel} listo")
                        render()
                    }
                } else if (r.cancelled) {
                    toast("Descarga cancelada")
                } else {
                    AlertDialog.Builder(act)
                        .setTitle("⚠️ No se pudo bajar el modelo")
                        .setMessage(r.message)
                        .setPositiveButton("Cerrar", null)
                        .show()
                }
            }
        }.start()
    }

    private fun runIaTranscribe(uri: Uri) {
        var shown: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) shown = c.getString(0)
            }
        } catch (e: Exception) { }
        val name = shown ?: (uri.lastPathSegment ?: "video")
        val pcm = File(cacheDir, "ia_audio.pcm")
        val cancel = AtomicBoolean(false)
        val msg = TextView(this)
        msg.text = "Preparando…"
        msg.setTextColor(txtColor)
        msg.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Transcribiendo")
            .setView(msg)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ ->
                cancel.set(true)
                try { WhisperLib.setCancel(true) } catch (e: Throwable) { }
            }
            .create()
        dlg.show()
        dlg.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val act = this@SettingsActivity
        Thread {
            var ctx = 0L
            var errTitle = ""
            var errText = ""
            val segs = ArrayList<IaSeg>()
            var extractMs = 0L
            var audioSec = 0.0
            var transMs = 0L
            var cancelled = false
            try {
                WhisperLib.setCancel(false)
                runOnUiThread { msg.text = "Extrayendo audio…" }
                val r = AudioExtractor.extract(act, uri, pcm, { pct ->
                    runOnUiThread { msg.text = "Extrayendo audio: $pct %" }
                }, { cancel.get() })
                extractMs = r.tookMs
                if (!r.ok) {
                    if (r.cancelled) {
                        cancelled = true
                    } else {
                        errTitle = "⚠️ No se pudo extraer el audio"
                        errText = r.message
                    }
                } else {
                    audioSec = r.durationSec
                    runOnUiThread { msg.text = "Cargando el modelo…" }
                    ctx = WhisperLib.initContext(WhisperModel.file(act).absolutePath)
                    if (ctx == 0L) {
                        try { WhisperModel.file(act).delete() } catch (e: Exception) { }
                        errTitle = "⚠️ No se pudo cargar el modelo"
                        errText = "El archivo del modelo parece dañado y se borró. Vuelve a tocar la prueba para bajarlo otra vez."
                    } else {
                        val chunkSamples = 5 * 60 * 16000
                        val totalSamples = pcm.length() / 2
                        val chunks = ((totalSamples + chunkSamples - 1) / chunkSamples).toInt()
                        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                        val tStart = System.currentTimeMillis()
                        java.io.FileInputStream(pcm).use { fin ->
                            val bytes = ByteArray(chunkSamples * 2)
                            var idx = 0
                            while (idx < chunks) {
                                if (cancel.get()) {
                                    cancelled = true
                                    break
                                }
                                val num = idx + 1
                                runOnUiThread { msg.text = "Transcribiendo bloque $num de $chunks…" }
                                var got = 0
                                while (got < bytes.size) {
                                    val rd = fin.read(bytes, got, bytes.size - got)
                                    if (rd < 0) break
                                    got += rd
                                }
                                val ns = got / 2
                                if (ns <= 0) break
                                // Whisper pide al menos 1 s de audio: se rellena con silencio
                                val len = if (ns < 17600) 17600 else ns
                                val audio = FloatArray(len)
                                for (k in 0 until ns) {
                                    val lo = bytes[2 * k].toInt() and 0xFF
                                    val hi = bytes[2 * k + 1].toInt()
                                    audio[k] = ((hi shl 8) or lo) / 32768f
                                }
                                val rc = WhisperLib.transcribe(ctx, threads, WhisperModel.lang(act), audio)
                                if (cancel.get()) {
                                    cancelled = true
                                    break
                                }
                                if (rc != 0) {
                                    errTitle = "⚠️ Falló la transcripción"
                                    errText = "Whisper devolvió el código $rc en el bloque $num."
                                    break
                                }
                                val offCs = idx.toLong() * chunkSamples / 160
                                val cnt = WhisperLib.segmentCount(ctx)
                                for (sIdx in 0 until cnt) {
                                    val tx = WhisperLib.segmentText(ctx, sIdx).trim()
                                    if (tx.isNotEmpty()) {
                                        segs.add(IaSeg(
                                            offCs + WhisperLib.segmentT0(ctx, sIdx),
                                            offCs + WhisperLib.segmentT1(ctx, sIdx),
                                            tx
                                        ))
                                    }
                                }
                                idx++
                            }
                        }
                        transMs = System.currentTimeMillis() - tStart
                    }
                }
            } catch (e: Throwable) {
                errTitle = "⚠️ Error en la transcripción"
                errText = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                if (ctx != 0L) {
                    try { WhisperLib.freeContext(ctx) } catch (e: Throwable) { }
                }
                try { pcm.delete() } catch (e: Exception) { }
            }
            val secs = transMs / 1000.0
            val speed = if (secs > 0) audioSec / secs else 0.0
            val f1 = { v: Double -> String.format(java.util.Locale.US, "%.1f", v) }
            val line = when {
                cancelled -> "IA-2b: $name: cancelado"
                errTitle.isNotEmpty() -> "IA-2b: $name: $errText"
                else -> "IA-2b: $name: ${segs.size} frases; ${f1(audioSec)} s de audio en ${f1(secs)} s " +
                    "(${f1(speed)}x); modelo ${WhisperModel.selected(act).shortName}; idioma ${WhisperModel.lang(act)}; extracción $extractMs ms"
            }
            try { DebugLog.add(line) } catch (e: Exception) { }
            runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                try { if (dlg.isShowing) dlg.dismiss() } catch (e: Exception) { }
                val dTitle: String
                val dBody: String
                if (cancelled) {
                    dTitle = "Cancelado"
                    dBody = "Se detuvo la transcripción y se borró el audio temporal."
                } else if (errTitle.isNotEmpty()) {
                    dTitle = errTitle
                    dBody = errText
                } else {
                    dTitle = "✅ Transcripción lista"
                    val sb = StringBuilder()
                    sb.append(name).append("\n\n")
                    sb.append("${f1(audioSec / 60.0)} min de audio en ${f1(secs)} s (${f1(speed)}x el tiempo real).\n")
                    sb.append("${segs.size} frases.\n\n")
                    if (segs.isEmpty()) {
                        sb.append("No se detectó voz.")
                    } else {
                        for (sg in segs.take(8)) {
                            sb.append("[").append(fmtCs(sg.t0)).append("] ").append(sg.text).append("\n")
                        }
                    }
                    dBody = sb.toString()
                }
                AlertDialog.Builder(act)
                    .setTitle(dTitle)
                    .setMessage(dBody)
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }.start()
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

    // [CONFIRMAR-1] preguntar antes de reanudar un video
    private fun resumeAsk(): Boolean = prefs().getBoolean("resume_ask", false)

    private fun toggleResumeAsk() {
        val now = !resumeAsk()
        prefs().edit().putBoolean("resume_ask", now).apply()
        toast(if (now) "⏩ Se preguntará antes de continuar un video" else "⏩ Los videos continúan solos")
        render()
    }

    // [PIP-2] ventana flotante automática al pulsar Inicio
    private fun pipAuto(): Boolean = prefs().getBoolean("pip_auto", true)

    private fun togglePipAuto() {
        val now = !pipAuto()
        prefs().edit().putBoolean("pip_auto", now).apply()
        toast(if (now) "🪟 Con Inicio, el video pasa a ventana flotante" else "🪟 Con Inicio, el video se pausa")
        render()
    }

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

    // [SALIDA-INI-1] Salida al abrir la app: 0 = Teléfono, 1 = TV, 2 = recordar la última
    private val outStartNames = arrayOf(
        "Teléfono (la música y los videos suenan en el móvil)",
        "TV (como antes)",
        "Recordar la última que usé")

    private fun outStartIdx(): Int = prefs().getInt("out_start", 0).let { if (it in 0..2) it else 0 }

    private fun pickOutStart() {
        AlertDialog.Builder(this)
            .setTitle("Salida al abrir la app")
            .setSingleChoiceItems(outStartNames, outStartIdx()) { d, which ->
                prefs().edit().putInt("out_start", which).apply()
                d.dismiss()
                toast("Se aplica la próxima vez que abras la app")
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
