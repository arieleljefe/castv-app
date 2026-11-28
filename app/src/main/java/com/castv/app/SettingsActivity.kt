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
class SettingsActivity : CastvActivity() {   // [TEXTO-TAM-1]

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

    private val accentNames get() = Themes.names

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

    /** Color de acento guardado (0 a 9, ver Themes.names). */
    private fun savedAccent(): Int = Themes.saved(this)   // [TEMAS-1]

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
        Themes.apply(this)   // [TEMAS-1]

        txtColor = ContextCompat.getColor(this, R.color.vlc_text)
        mutedColor = ContextCompat.getColor(this, R.color.vlc_muted)
        lineColor = Themes.color(this, R.attr.castvStroke)   // [TEMAS-2]

        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Themes.color(this, R.attr.castvRootBg))   // [TEMAS-2] [FONDO-IMG-1]
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
        search.setPadding(dp(16), dp(10), dp(44), dp(10))   // [BUSCAR-X-1] deja sitio para la ✕
        val boxBg = GradientDrawable()
        boxBg.setColor(Themes.color(this, R.attr.castvSurface))   // [TEMAS-2]
        boxBg.cornerRadius = dp(10).toFloat()
        search.background = boxBg
        // [BUSCAR-X-1] ✕ para borrar lo escrito en un toque (solo se ve si hay texto)
        val clearBtn = TextView(this)
        clearBtn.text = "✕"
        clearBtn.textSize = 16f
        clearBtn.setTextColor(mutedColor)
        clearBtn.gravity = Gravity.CENTER
        clearBtn.contentDescription = "Borrar búsqueda"
        clearBtn.visibility = View.GONE
        clearBtn.setOnClickListener { search.setText("") }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                clearBtn.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE   // [BUSCAR-X-1]
                render()
            }
        })
        val searchLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        searchLp.setMargins(dp(16), 0, dp(16), dp(4))
        val searchBox = android.widget.FrameLayout(this)   // [BUSCAR-X-1] buscador + ✕ a la derecha
        searchBox.addView(search, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        searchBox.addView(clearBtn, android.widget.FrameLayout.LayoutParams(
            dp(44), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        root.addView(searchBox, searchLp)

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
        // [UPDATE-2] viene del aviso "Hay una versión nueva" → abre directo la búsqueda de actualización
        val autoUpdate = savedInstanceState == null && intent.getBooleanExtra("autoUpdate", false)
        if (pinSet() && !unlocked) {
            content.visibility = View.INVISIBLE
            askPin("Ajustes bloqueados", exitOnCancel = true) {
                unlocked = true
                content.visibility = View.VISIBLE
                if (autoUpdate) checkUpdate()
            }
        } else {
            unlocked = true
            if (autoUpdate) checkUpdate()
        }
    }

    override fun onDestroy() {
        QrSend.close("pantalla cerrada")   // [QR-2b]
        super.onDestroy()
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
        l.add(Entry("Interfaz", "Tema", Themes.packLabel(this), { pickPack() }))   // [TEMAS-3b]
        l.add(Entry("Interfaz", "Modo", themeLabel(), { pickTheme() }))   // [TEMAS-3b] Sistema / Claro / Oscuro
        l.add(Entry("Interfaz", "Color de acento", accentNames[savedAccent()], { pickAccent() }))
        l.add(Entry("Interfaz", "Fondo",   // [TEMAS-2]
            Themes.bgNames[Themes.savedBg(this)] + Themes.bgNote(this),
            { pickBg() }))

        l.add(Entry("Interfaz", "Imagen de fondo",   // [FONDO-IMG-1]
            if (BgImage.has(this)) "Sí: tu imagen, oscurecida ${BgImage.dim(this)} %" else "No: sin imagen",
            { pickBgImage() }))

        l.add(Entry("Interfaz", "Forma", Themes.shapeNames[Themes.savedShape(this)], { pickShape() }))   // [TEMAS-3a]
        l.add(Entry("Interfaz", "Letra", Themes.fontNames[Themes.savedFont(this)], { pickFont() }))   // [TEMAS-3a]
        l.add(Entry("Interfaz", "Tamaño del texto", CastvTextSize.names[CastvTextSize.saved(this)],   // [TEXTO-TAM-1]
            { pickTextSize() }))

        l.add(Entry("Interfaz", "Filas compactas",   // [COMPACTO-1]
            if (CastvCompact.on(this)) "Sí: filas más bajas en Pistas, Videos y Canales" else "No: filas como siempre",
            { toggleCompact() }))

        l.add(Entry("Interfaz", "Botones grandes (modo sofá)",   // [SOFA-1]
            if (CastvSofa.on(this)) "Sí: botones más grandes en el reproductor de música, el de video y el widget"
            else "No: botones como siempre", { toggleSofa() }))

        l.add(Entry("Interfaz", "Vibración al tocar",   // [VIBRA-1]
            if (hapticOn()) "Sí: vibración suave en ▶, ⏮ ⏭ y ★ (si el teléfono tiene activada la vibración al tocar)"
            else "No", { toggleHaptic() }))

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
        l.add(Entry("Subtítulos", "Eliminar modelo de voz (IA)", iaUsedStatus(),
            { pickIaDelete() }))   // [IA-5]
        l.add(Entry("Subtítulos", "Restablecer estilo",
            "Tamaño mediano, texto blanco, fondo semitransparente", { resetSubStyle() }))

        // [RED-1] Red
        l.add(Entry("Red", "Avisar con datos móviles",
            if (warnMobile()) "Sí: pregunta antes de reproducir un enlace en el teléfono" else "No",
            { toggleWarnMobile() }))

        // [PUERTO-1] Puerto real del servidor interno (solo informativo)
        val srvErr = ServerState.error
        l.add(Entry("Red", "Puerto del servidor",
            if (srvErr != null) "Sin puerto libre (se prueba del 8000 al 8010)"
            else "${ServerPort.port} (se busca uno libre del 8000 al 8010)", null))

        // [GESTO-1] Vídeo
        l.add(Entry("Video", "Salto con doble toque", "${dtapSec()} segundos (en el reproductor de video)", { pickDtap() }))
        // [GESTOS-AJ-1] cada gesto se puede activar o desactivar; velocidad, controles y botones
        l.add(Entry("Video", "Deslizar de lado: adelantar", gState("g_seek"), { toggleG("g_seek", "Deslizar de lado") }))
        l.add(Entry("Video", "Deslizar a la derecha: brillo", gState("g_bright"), { toggleG("g_bright", "Brillo") }))
        l.add(Entry("Video", "Deslizar a la izquierda: volumen", gState("g_vol"), { toggleG("g_vol", "Volumen") }))
        l.add(Entry("Video", "Deslizar al centro: cambiar de canal", gState("g_zap") + " (arriba = siguiente, abajo = anterior; solo con canales de una lista)", { toggleG("g_zap", "Cambiar de canal") }))   // [ZAP-3]
        l.add(Entry("Video", "📅 Guía de programación (EPG)", epgState() + " (muestra «Ahora» en Canales y en el reproductor; con datos móviles solo se actualiza a mano desde 📅)", { toggleEpg() }))   // [EPG-1]
        l.add(Entry("Video", "Doble toque", gState("g_dtap") + " (con el doble toque apagado, dos toques muestran u ocultan los controles)", { toggleG("g_dtap", "Doble toque") }))
        l.add(Entry("Video", "Mantener pulsado: acelerar", gState("g_hold"), { toggleG("g_hold", "Mantener pulsado") }))
        l.add(Entry("Video", "Velocidad al mantener pulsado", fastNames[fastIdx()], { pickFast() }))
        l.add(Entry("Video", "Zoom con pellizco", gState("g_pinch"), { toggleG("g_pinch", "Zoom con pellizco") }))
        l.add(Entry("Video", "Tiempo de los controles", "Se esconden a los ${hideLabels[hideIdx()]}", { pickHide() }))
        l.add(Entry("Video", "Salto de los botones −N / +N", btnLabel(btnSecPref()), { pickBtn() }))
        l.add(Entry("Video", "Recordar brillo",   // [GESTO-2]
            if (rememberBright()) "Sí: el video se abre con el último brillo que pusiste"
            else "No: el video usa el brillo del teléfono", { toggleRememberBright() }))
        l.add(Entry("Video", "Preguntar antes de continuar",   // [CONFIRMAR-1]
            if (resumeAsk()) "Sí: al abrir un video con punto guardado pregunta si seguir o empezar de nuevo"
            else "No: continúa solo desde donde lo dejaste", { toggleResumeAsk() }))
        l.add(Entry("Video", "Ventana flotante al pulsar Inicio",   // [PIP-2]
            if (pipAuto()) "Sí: el video sigue en una ventana flotante al salir con Inicio"
            else "No: el video se pausa al salir (la ventana manual sigue en 🛠 › 🪟)", { togglePipAuto() }))
        l.add(Entry("Video", "Consejos de gestos", "Cómo usar los gestos del reproductor de video", { sendAction("tips") }))   // [NOVEDADES-1]

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
        l.add(Entry("Reproducción", "Sin pausa entre canciones",   // [GAPLESS-1]
            if (gaplessOn()) "Sí: la siguiente canción empieza sin silencio (música del teléfono: lista, cola y repetir todo)"
            else "No: como siempre", { toggleGapless() }))
        l.add(Entry("Reproducción", "Fundido entre canciones",   // [GAPLESS-1]
            if (xfadeIdx() == 0) "Apagado"
            else xfadeLabels[xfadeIdx()] + ": una canción baja mientras la otra sube (tiene prioridad sobre «Sin pausa»)", { pickXfade() }))

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
        l.add(Entry("Emisión al TV", "Calidad en TV", tvQualityNames[tvQualityIdx()], { pickTvQuality() }))   // [CALIDAD-1]
        l.add(Entry("Emisión al TV", "TV detectada", tvIp?.toString() ?: "Ninguna todavía", null))
        l.add(Entry("Emisión al TV", "IP del teléfono", CastvNet.localIpAddress(tvIp) ?: "Sin conexión Wi-Fi", null))

        // [AJUSTES-2b] Historial
        val recentLinks = Store.links().count { !it.fav }
        val onLinks: (() -> Unit)? = if (recentLinks == 0) null else ({ confirmClearLinks(recentLinks) })
        l.add(Entry("Historial", "Historial de enlaces",
            if (recentLinks == 0) "Vacío (los favoritos se conservan)"
            else "$recentLinks recientes (los favoritos se conservan)", onLinks))
        val nSearch = Store.recentSearches().size   // [BUSQ-RECIENTES-1]
        val onSearch: (() -> Unit)? = if (nSearch == 0) null else ({ confirmClearSearches(nSearch) })
        l.add(Entry("Historial", "Búsquedas recientes",
            if (nSearch == 0) "Vacío (salen al tocar el buscador de la biblioteca)"
            else "$nSearch guardadas (tocar para borrarlas)", onSearch))
        val resumes = Store.resumeCount()
        val onResume: (() -> Unit)? = if (resumes == 0) null else ({ confirmClearResume(resumes) })
        l.add(Entry("Historial", "Continuar donde lo dejé",
            if (resumes == 0) "Sin puntos guardados" else "$resumes puntos guardados en videos y canciones", onResume))
        val hidden = Store.hiddenChannels().size
        val onHidden: (() -> Unit)? = if (hidden == 0) null else ({ confirmUnhideAll(hidden) })
        l.add(Entry("Historial", "Canales ocultos",
            if (hidden == 0) "Ninguno" else "$hidden ocultos (tocar para mostrarlos todos)", onHidden))

        if (Build.VERSION.SDK_INT >= 30) {
            l.add(Entry("Almacenamiento", "Eliminado",
                if (useTrash()) "A la papelera de Android" else "Permanente (no se puede deshacer)",
                { toggleTrash() }))
        }

        // [AJUSTES-2c] Avanzado: registro de la app
        l.add(Entry("Avanzado", "Ver registro", "Eventos de la app y del servidor, para diagnosticar fallos", { showLog() }))
        l.add(Entry("Avanzado", "Limpiar registro", "Borra el registro actual", { confirmClearLog() }))
        // [AVANZADO-1] las tres pruebas de IA se juntan en una sola entrada (siguen existiendo: [IA-1] [IA-2a] [IA-2b])
        l.add(Entry("Avanzado", "Pruebas de IA…",
            "Extracción de audio, motor Whisper y transcripción (solo para diagnosticar)",
            { showIaTests() }))

        // [UPDATE-1] Actualizaciones desde GitHub
        l.add(Entry("Actualizaciones", "Buscar actualización",
            updateInstalledText() + " · se descarga de GitHub", { checkUpdate() }))
        l.add(Entry("Actualizaciones", "Avisar de versiones nuevas",   // [UPDATE-2]
            if (updateNotify()) "Sí: al abrir la app avisa si hay una versión nueva" else "No: solo buscas a mano",
            { toggleUpdateNotify() }))

        l.add(Entry("Respaldo", "Exportar o importar respaldo",
            "Favoritos, listas, historial de enlaces, listas M3U y ajustes", { sendAction("backup") }))
        l.add(Entry("Respaldo", "Enviar respaldo a otro teléfono",   // [QR-2b]
            "Con QR, por el Wi-Fi: sin cables ni correo", { confirmBackupQr() }))

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
        "Interfaz" -> "Tema, modo, color de acento, fondo, forma, letra, tamaño del texto, filas compactas, botones grandes y vibración"
        "Subtítulos" -> "Tamaño, color, fondo e IA"
        "Red" -> "Datos móviles y puerto del servidor"
        "Video" -> "Gestos, velocidad, controles y brillo"
        "Biblioteca" -> "Re-escanear y carpetas ocultas"
        "Reproducción" -> "Sin pausa y fundido entre canciones"
        "Audio" -> "Ecualizador"
        "Control parental" -> "PIN de bloqueo"
        "Emisión al TV" -> "Salida al abrir, calidad en TV, TV detectada e IP del teléfono"
        "Historial" -> "Enlaces, búsquedas, puntos guardados y canales ocultos"
        "Almacenamiento" -> "Eliminado: papelera o permanente"
        "Avanzado" -> "Registro de la app y pruebas"
        "Actualizaciones" -> "Buscar versiones nuevas, instalarlas y avisos"
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
            row(e.title, e.subtitle, e.onClick, rowIcon(e.section, e.title))   // [AJUSTES-FILAS-1]
        }
    }

    /** [AJUSTES-ICONOS-1] Icono de cada sección de la portada (dibujos que la app ya trae). */
    private fun sectionIcon(name: String): Int = when (name) {
        "Interfaz" -> R.drawable.ic_vp_palette
        "Subtítulos" -> R.drawable.ic_vp_subs
        "Red" -> R.drawable.ic_vp_phone
        "Video" -> R.drawable.ic_nav_video
        "Biblioteca" -> R.drawable.ic_mas_folder
        "Reproducción" -> R.drawable.ic_vp_play
        "Audio" -> R.drawable.ic_mas_eq
        "Control parental" -> R.drawable.ic_vp_lock
        "Emisión al TV" -> R.drawable.ic_tv
        "Historial" -> R.drawable.ic_mn_history
        "Almacenamiento" -> R.drawable.ic_mn_archive
        "Avanzado" -> R.drawable.ic_vp_build
        "Actualizaciones" -> R.drawable.ic_mn_download
        "Respaldo" -> R.drawable.ic_mn_upload
        "Acerca de" -> R.drawable.ic_vp_info
        else -> R.drawable.ic_mas_list
    }

    /** [AJUSTES-ICONOS-1] Fila de la portada: tarjeta redondeada con icono en círculo (color de acento), título, resumen y flecha. */
    private fun homeRow(name: String, summary: String?) {
        val acc = accent()
        val card = LinearLayout(this)
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(14), dp(12), dp(12), dp(12))
        val cardBg = GradientDrawable()
        cardBg.setColor(Themes.color(this, R.attr.castvSurface))
        cardBg.cornerRadius = dp(16).toFloat()
        cardBg.setStroke(dp(1), lineColor)
        card.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((acc and 0x00FFFFFF) or 0x33000000), cardBg, null)
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { showSection(name) }

        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor((acc and 0x00FFFFFF) or 0x2B000000)
        val icon = android.widget.ImageView(this)
        icon.setImageResource(sectionIcon(name))
        icon.setColorFilter(acc)
        icon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        icon.setPadding(dp(10), dp(10), dp(10), dp(10))
        icon.background = circle
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        card.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = name
        t.textSize = 16f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
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
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = dp(14)
        colLp.marginEnd = dp(8)
        card.addView(col, colLp)

        val arrow = TextView(this)
        arrow.text = "›"
        arrow.textSize = 24f
        arrow.setTextColor(acc)
        card.addView(arrow)

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(12), dp(4), dp(12), dp(4))
        content.addView(card, lp)
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

    /** [AJUSTES-FILAS-1] Icono de cada opción dentro de una sección: por palabras del título; si no hay, el de su sección. */
    private fun rowIcon(section: String, title: String): Int {
        val t = norm(title)
        val map = listOf(
            "tema" to R.drawable.ic_vp_palette, "color" to R.drawable.ic_vp_palette, "fondo oscuro" to R.drawable.ic_vp_palette,
            "modelo de voz" to R.drawable.ic_vp_subs, "subtitulos con ia" to R.drawable.ic_vp_subs,
            "eliminar" to R.drawable.ic_delete, "eliminado" to R.drawable.ic_delete, "limpiar" to R.drawable.ic_delete,
            "restablecer" to R.drawable.ic_vp_build, "pruebas" to R.drawable.ic_vp_build,
            "datos moviles" to R.drawable.ic_vp_phone, "ip del telefono" to R.drawable.ic_vp_phone,
            "puerto" to R.drawable.ic_mas_link, "historial de enlaces" to R.drawable.ic_mas_link,
            "guia" to R.drawable.ic_event, "velocidad" to R.drawable.ic_mas_speed,
            "ventana flotante" to R.drawable.ic_vp_aspect, "carpetas" to R.drawable.ic_mas_folder,
            "busquedas" to R.drawable.ic_search, "canales ocultos" to R.drawable.ic_mn_visibility,
            "continuar" to R.drawable.ic_vp_play, "sin pausa" to R.drawable.ic_vp_play, "fundido" to R.drawable.ic_vp_play,
            "ecualizador" to R.drawable.ic_mas_eq, "pin" to R.drawable.ic_vp_lock,
            "tv" to R.drawable.ic_tv, "salida" to R.drawable.ic_tv,
            "actualizacion" to R.drawable.ic_mn_download, "versiones nuevas" to R.drawable.ic_mn_download,
            "registro" to R.drawable.ic_vp_info, "version" to R.drawable.ic_vp_info, "acerca" to R.drawable.ic_vp_info,
            "exportar" to R.drawable.ic_mn_upload, "enviar respaldo" to R.drawable.ic_mn_upload)
        for ((k, res) in map) if (t.contains(k)) return res
        return sectionIcon(section)
    }

    private fun row(title: String, subtitle: String?, onClick: (() -> Unit)?, iconRes: Int = 0) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL   // [AJUSTES-FILAS-1] icono a la izquierda, textos a la derecha
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(12))
        if (onClick != null) {
            val sel = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
            box.setBackgroundResource(sel.resourceId)
            box.setOnClickListener { onClick() }
        }
        if (iconRes != 0) {
            val acc = accent()
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor((acc and 0x00FFFFFF) or 0x2B000000)
            val icon = android.widget.ImageView(this)
            icon.setImageResource(iconRes)
            icon.setColorFilter(acc)
            icon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            icon.setPadding(dp(8), dp(8), dp(8), dp(8))
            icon.background = circle
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val ilp = LinearLayout.LayoutParams(dp(36), dp(36))
            ilp.marginEnd = dp(14)
            box.addView(icon, ilp)
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(txtColor)
        col.addView(t)
        if (subtitle != null) {
            val s = TextView(this)
            s.text = subtitle
            s.textSize = 13f
            s.setTextColor(mutedColor)
            s.setPadding(0, dp(2), 0, 0)
            col.addView(s)
        }
        box.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
            .setTitle("Modo")
            .setSingleChoiceItems(labels, savedTheme()) { d, which ->
                prefs().edit().putInt("theme", which).apply()
                d.dismiss()
                render()
                applyThemeMode(which)   // recrea las pantallas con el tema elegido
            }
            .show()
    }

    private fun pickPack() {   // [TEMAS-3b]
        val before = savedTheme()
        Themes.pickPack(this, { render() }) {
            applyThemeMode(savedTheme())   // si cambia el modo, Android recrea las pantallas
            if (savedTheme() == before) recreate()   // si no, esta pantalla se redibuja; MainActivity lo hace al volver
        }
    }

    private fun pickAccent() {
        Themes.pick(this) {
            recreate()   // [TEMAS-1] esta pantalla se redibuja con el color; MainActivity lo hace al volver
        }
    }

    private fun pickBg() {   // [TEMAS-2]
        Themes.pickBg(this) {
            recreate()   // esta pantalla se redibuja con el fondo; MainActivity lo hace al volver
        }
    }

    // [FONDO-IMG-1] imagen de fondo propia
    private val pickBgImg = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            toast("Preparando la imagen…")
            val app = applicationContext
            Thread {
                val ok = BgImage.save(app, uri)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        if (ok) recreate() else toast("No pude usar esa imagen")
                    }
                }
            }.start()
        }
    }

    private fun pickBgImage() {   // [FONDO-IMG-1]
        val has = BgImage.has(this)
        val items = ArrayList<String>()
        items.add("Elegir imagen…")
        if (has) {
            items.add("Oscurecer: ${BgImage.dim(this)} %")
            items.add("Quitar la imagen")
        }
        AlertDialog.Builder(this)
            .setTitle("Imagen de fondo")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> pickBgImg.launch(arrayOf("image/*"))
                    1 -> pickBgDim()
                    else -> { BgImage.clear(this); recreate() }
                }
            }
            .show()
    }

    private fun pickBgDim() {   // [FONDO-IMG-1]
        val vals = intArrayOf(30, 50, 70)
        val names = arrayOf("Poco · 30 %", "Medio · 50 %", "Mucho · 70 %")
        val cur = vals.indexOf(BgImage.dim(this))
        AlertDialog.Builder(this)
            .setTitle("Oscurecer la imagen")
            .setSingleChoiceItems(names, cur) { d, which ->
                BgImage.setDim(this, vals[which])
                d.dismiss()
                recreate()
            }
            .show()
    }

    private fun pickShape() {   // [TEMAS-3a]
        Themes.pickShape(this) { recreate() }
    }

    private fun pickFont() {   // [TEMAS-3a]
        Themes.pickFont(this) { recreate() }
    }

    private fun toggleTrash() {
        val now = !useTrash()
        prefs().edit().putBoolean("trash", now).apply()
        toast(if (now) "🗑 Lo eliminado irá a la papelera de Android"
              else "⚠️ Lo eliminado se perderá para siempre")
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

    // ------------------------------------------------- [IA-5] Borrar modelos de voz

    private fun mbText(bytes: Long): String = "${bytes / 1_000_000L} MB"

    private fun iaUsedStatus(): String {
        val used = WhisperModel.usedBytes(this)
        return if (used <= 0L) "No hay modelos bajados" else "Ocupan ${mbText(used)}"
    }

    private fun pickIaDelete() {
        if (SubtitleGen.running) {
            toast("Se están generando subtítulos; espera a que terminen")
            return
        }
        val present = WhisperModel.present(this)
        if (present.isEmpty()) {
            val freed = WhisperModel.clearPartials(this)
            if (freed > 0L) {
                toast("Se limpiaron ${mbText(freed)} de descargas incompletas")
                render()
            } else {
                toast("No hay modelos bajados")
            }
            return
        }
        val names = present.map {
            it.fullLabel + " · " + mbText(WhisperModel.file(this, it).length())
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Eliminar modelo de voz (IA)")
            .setItems(names) { d, which ->
                d.dismiss()
                confirmIaDelete(present[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmIaDelete(sp: WhisperModel.Spec) {
        val size = WhisperModel.file(this, sp).length()
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar el modelo ${sp.label}?")
            .setMessage("Se liberan ${mbText(size)}. Si lo necesitas otra vez, habrá que bajarlo de nuevo (${sp.sizeText}).")
            .setPositiveButton("Eliminar") { _, _ ->
                if (SubtitleGen.running) {
                    toast("Se están generando subtítulos; espera a que terminen")
                } else {
                    val ok = WhisperModel.delete(this, sp)
                    WhisperModel.clearPartials(this)
                    try { DebugLog.add("IA-5: modelo ${sp.shortName} borrado (${mbText(size)}): " + (if (ok) "ok" else "no se pudo")) } catch (e: Exception) { }
                    toast(if (ok) "Modelo ${sp.fullLabel} borrado" else "No se pudo borrar el modelo")
                    render()
                }
            }
            .setNegativeButton("Cancelar", null)
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
            .setTitle("Eliminar historial de enlaces")
            .setMessage("Se borrarán $n enlaces recientes. Los favoritos se conservan.")
            .setPositiveButton("Eliminar") { _, _ ->
                Store.clearRecentLinks()
                toast("🕘 Historial de enlaces borrado")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmClearResume(n: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar puntos de reanudación")
            .setMessage("Se olvidará dónde lo dejaste en $n videos y canciones; volverán a empezar desde el principio.")
            .setPositiveButton("Eliminar") { _, _ ->
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

    // ------------------------------------------------- [UPDATE-1] Actualizar desde la app

    private fun updateInstalledText(): String = try {
        "Instalada: " + (packageManager.getPackageInfo(packageName, 0).versionName ?: "?")
    } catch (e: Exception) {
        "Instalada: ?"
    }

    private fun onMobileData(): Boolean = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null &&
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Exception) {
        false
    }

    private fun simpleDialog(title: String, text: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun checkUpdate() {
        val act = this@SettingsActivity
        val msg = TextView(this)
        msg.text = "Consultando GitHub…"
        msg.setTextColor(txtColor)
        msg.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Buscar actualización")
            .setView(msg)
            .setCancelable(false)
            .create()
        dlg.show()
        Thread {
            try { Updater.tidy(act) } catch (e: Exception) { }
            val r = Updater.check(act)
            try { DebugLog.add("UPDATE-1: consulta: ${r.message}") } catch (e: Exception) { }
            runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                try { if (dlg.isShowing) dlg.dismiss() } catch (e: Exception) { }
                showUpdateResult(r)
            }
        }.start()
    }

    private fun showUpdateResult(r: Updater.CheckResult) {
        val rel = r.release
        if (!r.ok || rel == null) {
            simpleDialog("⚠️ No se pudo buscar", r.message)
            return
        }
        if (!r.newer) {
            simpleDialog("✅ Estás al día", updateInstalledText() + "\nÚltima en GitHub: " + rel.tag)
            return
        }
        val mb = String.format(java.util.Locale.US, "%.1f", rel.size / 1_000_000.0)
        val notes = if (rel.notes.length > 1200) rel.notes.substring(0, 1200) + "…" else rel.notes
        val sb = StringBuilder()
        sb.append(updateInstalledText()).append("\nNueva: ").append(rel.tag)
        sb.append("\nTamaño: ").append(mb).append(" MB")
        if (warnMobile() && onMobileData()) {
            sb.append("\n\n⚠️ Estás con datos móviles: la descarga gastará ").append(mb).append(" MB.")
        }
        if (notes.isNotBlank()) sb.append("\n\n").append(notes.trim())
        val sv = ScrollView(this)
        val tv = TextView(this)
        tv.text = sb.toString()
        tv.textSize = 14f
        tv.setTextColor(txtColor)
        tv.setPadding(dp(24), dp(12), dp(24), dp(8))
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Nueva versión disponible")
            .setView(sv)
            .setPositiveButton("Descargar e instalar") { _, _ -> downloadUpdate(rel) }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun downloadUpdate(rel: Updater.Release) {
        val act = this@SettingsActivity
        val cancel = AtomicBoolean(false)
        val msg = TextView(this)
        msg.text = "Conectando…"
        msg.setTextColor(txtColor)
        msg.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Descargando " + rel.tag)
            .setView(msg)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ -> cancel.set(true) }
            .create()
        dlg.show()
        dlg.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread {
            val d = Updater.download(act, rel, { pct, mb ->
                runOnUiThread {
                    msg.text = if (pct >= 0) "Avance: $pct % ($mb MB)" else "Descargado: $mb MB"
                }
            }, { cancel.get() })
            val bad = if (d.ok && d.file != null) Updater.verify(act, d.file) else null
            try {
                DebugLog.add("UPDATE-1: descarga ${rel.tag}: ${d.message}" +
                    (if (d.ok) (if (d.shaChecked) " (SHA-256 correcto)" else " (sin SHA-256 publicado)") else "") +
                    (if (bad != null) " · rechazado: $bad" else ""))
            } catch (e: Exception) { }
            if (bad != null) {
                try { d.file?.delete() } catch (e: Exception) { }
            }
            runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                try { if (dlg.isShowing) dlg.dismiss() } catch (e: Exception) { }
                if (d.cancelled) {
                    toast("Descarga cancelada")
                } else if (!d.ok || d.file == null) {
                    simpleDialog("⚠️ No se pudo descargar", d.message)
                } else if (bad != null) {
                    simpleDialog("⚠️ No se instalará", bad)
                } else {
                    installUpdate(d.file)
                }
            }
        }.start()
    }

    private fun installUpdate(f: File) {
        try {
            if (!Updater.install(this, f)) {
                simpleDialog("Falta un permiso",
                    "Activa «Permitir desde esta fuente» para CASTV, vuelve atrás y toca otra vez " +
                    "Buscar actualización. La descarga se conserva, no hay que bajarla de nuevo.")
            }
        } catch (e: Exception) {
            try { DebugLog.add("UPDATE-1: no se pudo abrir el instalador: ${e.message}") } catch (e2: Exception) { }
            simpleDialog("⚠️ No se pudo abrir el instalador", e.message ?: "Error desconocido")
        }
    }

    // ------------------------------------------------- [UPDATE-2] Aviso al abrir la app

    private fun updateNotify(): Boolean = prefs().getBoolean("upd_notify", true)

    private fun toggleUpdateNotify() {
        val now = !updateNotify()
        prefs().edit().putBoolean("upd_notify", now).apply()
        toast(if (now) "🆕 Se avisará si hay una versión nueva" else "🆕 Sin aviso de versiones nuevas")
        render()
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

    /** [AVANZADO-1] Menú con las tres pruebas de IA que antes ocupaban tres filas de Avanzado. */
    private fun showIaTests() {
        val items = arrayOf("Probar extracción de audio", "Probar Whisper", "Probar transcripción")
        AlertDialog.Builder(this)
            .setTitle("Pruebas de IA")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickIaVideo.launch(arrayOf("video/*"))   // [IA-1]
                    1 -> runWhisperTest()                          // [IA-2a]
                    else -> startIaTranscribe()                    // [IA-2b]
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
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

    // [EPG-1] [EPG-DEFECTO-1] la guía viene encendida por defecto; si Ariel ya eligió, se respeta su valor
    private fun epgState(): String = if (prefs().getBoolean("epg_on", true)) "Activado" else "Desactivado"

    private fun toggleEpg() {
        val now = !prefs().getBoolean("epg_on", true)
        prefs().edit().putBoolean("epg_on", now).apply()
        if (!now) Epg.clear()
        toast(if (now) "✅ Guía de programación: activada" else "🚫 Guía de programación: desactivada")
        render()
    }

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
        "Teléfono (la música y los videos suenan en el teléfono)",
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

    /** [TEXTO-TAM-1] Pequeño / Normal / Grande: multiplica el tamaño de letra del sistema en todas las pantallas de CASTV. */
    private fun pickTextSize() {
        AlertDialog.Builder(this)
            .setTitle("Tamaño del texto")
            .setSingleChoiceItems(CastvTextSize.names, CastvTextSize.saved(this)) { d, which ->
                prefs().edit().putInt("text_size", which).apply()
                d.dismiss()
                recreate()   // esta pantalla se redibuja con el nuevo tamaño; MainActivity lo hace al volver
            }
            .show()
    }

    private fun hapticOn(): Boolean = prefs().getBoolean("haptic", true)   // [VIBRA-1]

    /** [COMPACTO-1] Filas más bajas en Pistas, Videos y Canales (MainActivity y Canales lo leen al volver). */
    private fun toggleCompact() {
        val now = !CastvCompact.on(this)
        prefs().edit().putBoolean("compact", now).apply()
        toast(if (now) "↕ Filas compactas" else "↕ Filas como siempre")
        render()
    }

    /** [SOFA-1] Botones más grandes para usar el teléfono de lejos (MainActivity y el reproductor lo leen al volver; el widget se repinta ya). */
    private fun toggleSofa() {
        val now = !CastvSofa.on(this)
        prefs().edit().putBoolean("sofa", now).apply()
        CastvWidget.repaint(applicationContext)
        toast(if (now) "🛋 Botones grandes" else "🛋 Botones como siempre")
        render()
    }

    // ------------------------------------------------------------ [GAPLESS-1] Reproducción

    private fun gaplessOn(): Boolean = prefs().getBoolean("gapless", false)

    private val xfadeValues = intArrayOf(0, 2, 4, 6)
    private val xfadeLabels = arrayOf("Apagado", "2 segundos", "4 segundos", "6 segundos")

    private fun xfadeIdx(): Int = xfadeValues.indexOf(prefs().getInt("xfade", 0)).let { if (it < 0) 0 else it }

    /** «Sin pausa»: MainActivity y el modo sin pantalla lo leen al empezar la siguiente pista. */
    private fun toggleGapless() {
        val now = !gaplessOn()
        prefs().edit().putBoolean("gapless", now).apply()
        toast(if (now) "▶▶ Sin pausa entre canciones" else "▶▶ Canciones como siempre")
        render()
    }

    private fun pickXfade() {
        AlertDialog.Builder(this)
            .setTitle("Fundido entre canciones")
            .setSingleChoiceItems(xfadeLabels, xfadeIdx()) { d, which ->
                prefs().edit().putInt("xfade", xfadeValues[which]).apply()
                d.dismiss()
                render()
            }
            .show()
    }

    private fun toggleHaptic() {
        val now = !hapticOn()
        prefs().edit().putBoolean("haptic", now).apply()
        toast(if (now) "📳 Vibración suave en los botones" else "📳 Sin vibración en los botones")
        render()
    }

    /** [BUSQ-RECIENTES-1] Confirma antes de borrar las búsquedas guardadas. */
    private fun confirmClearSearches(n: Int) {
        AlertDialog.Builder(this)
            .setTitle("Borrar búsquedas recientes")
            .setMessage("Se borran las $n búsquedas guardadas.")
            .setPositiveButton("Borrar") { _, _ ->
                Store.clearRecentSearches()
                toast("Búsquedas borradas")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // [CALIDAD-1] Calidad de los canales en vivo al TV: 0 = Baja (la de siempre), 1 = Media, 2 = Alta
    private val tvQualityNames = arrayOf(
        "Baja (la de siempre, la más estable)",
        "Media",
        "Alta (puede atascarse con un Wi-Fi flojo)")

    private fun tvQualityIdx(): Int = prefs().getInt("tv_quality", 0).let { if (it in 0..2) it else 0 }

    private fun pickTvQuality() {
        AlertDialog.Builder(this)
            .setTitle("Calidad en TV")
            .setSingleChoiceItems(tvQualityNames, tvQualityIdx()) { d, which ->
                prefs().edit().putInt("tv_quality", which).apply()
                d.dismiss()
                toast("Se aplica al abrir el próximo canal en el TV")
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

    /** [QR-2b] Pide confirmación antes de abrir la puerta: el respaldo es lo más delicado que sale del teléfono. */
    private fun confirmBackupQr() {
        AlertDialog.Builder(this)
            .setTitle("¿Enviar tu respaldo a otro teléfono?")
            .setMessage("Se abre una dirección con QR, solo para tu Wi-Fi y de un solo uso (5 minutos). " +
                "Quien la abra podrá bajar tus favoritos, listas M3U, historial y ajustes (sin claves Xtream). " +
                "Úsalo solo con un teléfono tuyo o de confianza.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> startBackupQr() }
            .show()
    }

    /** [QR-2b] Comprueba que haya Wi-Fi y que el servidor de CASTV responda; si sí, abre el recuadro con el QR. */
    private fun startBackupQr() {
        val ip = CastvNet.localIpAddress(null)
        if (ip == null) { toast("Conéctate a un Wi-Fi para usar esto"); return }
        Thread {
            val ok = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", ServerPort.port), 700) }
                true
            } catch (e: Exception) { false }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) showBackupQr(ip) else toast("El servidor de CASTV no está activo. Abre la pantalla principal y vuelve a intentarlo.")
            }
        }.start()
    }

    /** [QR-2b] Recuadro con el QR y la dirección. Se cierra solo al entregar el respaldo, a los 5 minutos o con «Cerrar». */
    private fun showBackupQr(ip: String) {
        var dlg: AlertDialog? = null
        val tok = QrSend.openBackup({ Backup.build(applicationContext) }) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                dlg?.dismiss()
                toast("📤 Respaldo enviado")
            }
        }
        val addr = "http://$ip:${ServerPort.port}/send/$tok"
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val bmp = QrImage.make(addr, dp(240))
        if (bmp != null) {
            val img = android.widget.ImageView(this)
            img.setImageBitmap(bmp)
            box.addView(img, LinearLayout.LayoutParams(dp(240), dp(240)))
        }
        val t = TextView(this)
        t.text = "Con el otro teléfono del mismo Wi-Fi, escanea el QR o abre:\n$addr\n\n" +
            "Allí pulsa «Descargar respaldo» y, en CASTV de ese teléfono, usa Ajustes › Respaldo para importarlo.\n\n" +
            "Se cierra solo al entregar el respaldo o en 5 minutos."
        t.textSize = 13f
        t.setTextColor(mutedColor)
        t.setPadding(0, dp(12), 0, 0)
        t.setTextIsSelectable(true)
        box.addView(t)
        dlg = AlertDialog.Builder(this)
            .setTitle("📤 Enviar respaldo")
            .setView(box)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener { QrSend.close("recuadro cerrado") }
            .show()
        val d = dlg
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (d != null && d.isShowing) d.dismiss() }, 5L * 60_000L)
    }

    /** Cierra Ajustes y le pide a MainActivity que abra respaldo o redes. */
    private fun sendAction(action: String) {
        setResult(RESULT_OK, Intent().putExtra("action", action))
        finish()
    }
}
