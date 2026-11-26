package com.castv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Listas M3U (canales de TV por internet): se cargan por enlace, se buscan y se filtran por grupo.
 * Al tocar un canal se reproduce sin cerrar esta pantalla (📺 TV o 📱 móvil) y se puede marcar con ★.
 */
class M3uActivity : AppCompatActivity() {

    private lateinit var listBtn: TextView
    private lateinit var groupBtn: TextView
    private lateinit var searchBox: EditText
    private lateinit var statusView: TextView
    private lateinit var channelList: ListView
    private lateinit var chAdapter: ChannelAdapter
    // [CANAL-MINI-1] barra de abajo con el canal que está en el TV (⏮ ⏯ ⏭ ★ ✕)
    private lateinit var nowBar: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowFav: TextView
    private var nowDismissed: String? = null
    private var nowRecv: BroadcastReceiver? = null
    private lateinit var sections: LinearLayout   // [ESTILO-2] cabecera del ListView: Continuar y Favoritos

    private var txtColor = Color.BLACK
    private var mutedColor = Color.GRAY
    private var current: M3uList? = null
    private var channels: List<M3uChannel> = emptyList()
    private var normNames: List<String> = emptyList()
    private var shown: List<M3uChannel> = emptyList()
    private var groupFilter: String? = null
    private var moodFilter = -1   // [ESTILO-3] índice en MOODS (-1 = ningún ánimo)
    private lateinit var moodScroll: HorizontalScrollView
    private lateinit var moodRow: LinearLayout
    private var moodCounts = IntArray(MOODS.size)
    private var moodSig = ""     // de qué lista/ocultos son moodCounts
    private var moodDrawn = ""   // qué se dibujó la última vez (lista + ánimo activo)
    private val moodMaskCache = HashMap<String, Int>()
    private var loadGen = 0
    private var fromCache = false
    private var cacheNote = " · sin conexión (copia guardada)"   // [LISTA-VAL-1] texto del pie cuando se usa la copia
    private var favs: Set<String> = emptySet()
    private var favOnly = false
    private var favChannels: List<M3uChannel> = emptyList()   // [FAV-1] favoritos de todas las listas
    private var favNorm: List<String> = emptyList()
    private lateinit var favBtn: TextView
    private lateinit var hiddenBtn: TextView   // [OCULTAR-1] "👁 N": canales ocultos (solo se ve si hay alguno)
    @Volatile private var hidden: Set<String> = emptySet()   // [OCULTAR-1] enlaces de los canales ocultos
    private lateinit var guideBtn: TextView   // [EPG-1] "📅": guía de programación (solo se ve con la guía activada en Ajustes)
    private lateinit var failBtn: TextView   // [FUENTES-1] "⚠ N": canales que fallan seguido (solo se ve si hay alguno)
    @Volatile private var failed: Map<String, Int> = emptyMap()   // [FUENTES-1] enlace → fallos seguidos (2 o más)
    private val reqPlayer = 4715
    private val reqSave = 4716   // [M3U-EXPORT-1] «Guardar…» el .m3u de favoritos
    private var exportPending: String? = null   // [M3U-EXPORT-1] texto del .m3u mientras se elige dónde guardarlo
    private lateinit var exportBtn: TextView   // [M3U-EXPORT-1]
    private var regionName: String? = null   // [EPG-REGION-1] región abierta (null con una lista suelta)
    private var regionLists: List<M3uList> = emptyList()   // [EPG-REGION-1]
    private val regionMax = 40000   // [REGIONES-1b] tope de canales al unir una región
    // [CHECK-1] revisar qué canales responden
    private lateinit var checkBtn: TextView
    @Volatile private var checkGen = 0   // sube al detener o salir: los hilos viejos dejan de contar
    private var checking = false
    private var checkPool: java.util.concurrent.ExecutorService? = null

    private val freeTvUrl = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    /** [VISTA-1] Fondo claro redondeado detrás del logo: así los logos oscuros se ven con el tema oscuro. Sin logo (icono gris de TV) no lleva fondo. */
    private fun styleLogo(logo: ImageView, url: String) {   // [VISTA-5] el fondo depende del brillo del logo
        if (url.isNotEmpty()) {
            logo.background = GradientDrawable().apply {
                setColor(Logos.bgFor(url))
                cornerRadius = dp(6).toFloat()
            }
            logo.setPadding(dp(3), dp(2), dp(3), dp(2))
        } else {
            logo.background = null
            logo.setPadding(0, 0, 0, 0)
        }
    }

    private fun norm(s: String): String =
        diacritics.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    private fun accent(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.castvAccent, tv, true)
        return tv.data
    }

    /** [ICONOS-2] Pone «icono + texto» en un botón (el icono va dentro del texto, así se centra con él). [tint] por defecto = color del texto. */
    private fun iconLabel(btn: TextView, res: Int, label: String, tint: Int = btn.currentTextColor) {
        val d = ContextCompat.getDrawable(this, res)?.mutate()
        if (d == null) { btn.text = label; return }
        d.setTint(tint)
        val s = (btn.textSize * 1.2f).toInt()
        d.setBounds(0, 0, s, s)
        val sp = android.text.SpannableString("\u00A0 $label")
        sp.setSpan(android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BASELINE), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        btn.text = sp
    }

    /** [ICONOS-1] Botón con icono vectorial del mismo color del texto (alternativa a un emoji). */
    private fun iconBtn(res: Int, desc: String, onClick: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(txtColor)
        contentDescription = desc
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(9), dp(9), dp(9), dp(9))
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = ta.getDrawable(0)
        ta.recycle()
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
    }

    private fun chip(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(txtColor)
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            setColor(0x33888888)
            cornerRadius = dp(18).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [ACENTO-1] aplica el color de acento elegido en Ajustes (igual que MainActivity)
        theme.applyStyle(when (getSharedPreferences("castv", MODE_PRIVATE).getInt("accent", 0)) {
            1 -> R.style.AccentOverlayGreen
            2 -> R.style.AccentOverlayOrange
            3 -> R.style.AccentOverlayPurple
            else -> R.style.AccentOverlayCyan
        }, true)
        Store.init(this)
        favs = Store.chFavs()
        hidden = Store.hiddenUrls()   // [OCULTAR-1]
        failed = Store.failedChannels()   // [FUENTES-1]
        txtColor = ContextCompat.getColor(this, R.color.vlc_text)
        mutedColor = ContextCompat.getColor(this, R.color.vlc_muted)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.vlc_bg))
        root.setPadding(dp(12), dp(16), dp(12), 0)

        // [PULIDO-3e] Encabezado: ← Canales (igual que Ajustes)
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(0, 0, 0, 0)   // [CANALES-1]
        val back = TextView(this)
        back.text = "←"
        back.textSize = 26f
        back.setTextColor(txtColor)
        back.gravity = Gravity.CENTER
        back.contentDescription = "Atrás"
        back.setPadding(dp(8), dp(4), dp(16), dp(4))
        back.setOnClickListener { finish() }
        header.addView(back)
        // [CANALES-1] el botón de la lista actual va en la misma fila que la flecha (antes: fila «Canales» + fila de lista)

        // Fila 1: lista actual, añadir, quitar
        // [VISTA-3] Fila 1a: la lista actual con su nombre completo, a lo ancho
        listBtn = chip("📡 Sin lista ▾") { chooseList() }
        listBtn.maxLines = 1
        listBtn.ellipsize = TextUtils.TruncateAt.END
        (listBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            weight = 1f
            leftMargin = 0
        }
        header.addView(listBtn)   // [CANALES-1]
        root.addView(header)
        // [VISTA-3] Fila 1b: acciones con texto, repartidas a partes iguales
        val row1 = LinearLayout(this)
        row1.gravity = Gravity.CENTER_VERTICAL
        row1.setPadding(0, dp(8), 0, 0)
        favBtn = chip("☆ Favs") { toggleFavOnly() }
        hiddenBtn = chip("👁") { showHiddenDialog() }   // [OCULTAR-1]
        hiddenBtn.contentDescription = "Canales ocultos"
        val addBtn = chip("＋ Lista") { addListDialog() }
        val delBtn = chip("🗑 Quitar") { removeCurrent() }
        for (b in listOf(favBtn, hiddenBtn, addBtn, delBtn)) {
            b.textSize = 13f
            b.maxLines = 1
            b.setPadding(dp(6), dp(8), dp(6), dp(8))
            (b.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0
                weight = 1f
            }
            row1.addView(b)
        }
        (favBtn.layoutParams as LinearLayout.LayoutParams).leftMargin = 0
        iconLabel(favBtn, R.drawable.ic_star_border, "Favs")   // [ICONOS-2]
        iconLabel(addBtn, R.drawable.ic_add, "Lista")
        iconLabel(delBtn, R.drawable.ic_delete, "Quitar")
        // [M3U-EXPORT-1] solo se ve con ★ Favs activo: exporta los favoritos como .m3u
        exportBtn = chip("📤") { exportFavsDialog() }
        exportBtn.contentDescription = "Exportar favoritos como .m3u"
        exportBtn.textSize = 13f
        exportBtn.maxLines = 1
        exportBtn.setPadding(dp(6), dp(8), dp(6), dp(8))
        (exportBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            weight = 1f
        }
        exportBtn.visibility = View.GONE
        row1.addView(exportBtn, 1)
        updateHiddenBtn()
        root.addView(row1)

        // Fila 2: buscador y grupo
        val row2 = LinearLayout(this)
        row2.gravity = Gravity.CENTER_VERTICAL
        row2.setPadding(0, dp(10), 0, 0)
        searchBox = EditText(this)
        searchBox.hint = "Buscar canal…"
        searchBox.setSingleLine(true)
        searchBox.setTextColor(txtColor)
        searchBox.setHintTextColor(mutedColor)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
        row2.addView(searchBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        groupBtn = chip("Grupo: Todos ▾") { pickGroup() }
        row2.addView(groupBtn)
        root.addView(row2)

        // [ESTILO-3] Fila de ánimos (chips desplazables); se llena en refreshMoods()
        moodRow = LinearLayout(this)
        moodRow.gravity = Gravity.CENTER_VERTICAL
        moodScroll = HorizontalScrollView(this)
        moodScroll.isHorizontalScrollBarEnabled = false
        moodScroll.setPadding(0, dp(8), 0, 0)
        moodScroll.visibility = View.GONE
        moodScroll.isHorizontalFadingEdgeEnabled = true   // [VISTA-4] el borde se difumina: se ve que sigue hacia el lado
        moodScroll.setFadingEdgeLength(dp(24))
        moodScroll.addView(moodRow)
        val moodHolder = LinearLayout(this)   // [CANALES-1] los ánimos van dentro de la lista: se van al bajar y dejan más sitio a los canales
        moodHolder.orientation = LinearLayout.VERTICAL
        moodHolder.addView(moodScroll)

        statusView = TextView(this)
        statusView.setTextColor(mutedColor)
        statusView.textSize = 13f
        statusView.setPadding(dp(4), dp(8), dp(4), dp(4))
        // [CHECK-1] el estado y, a la derecha, el botón 🔎 Revisar
        val statusRow = LinearLayout(this)
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusRow.addView(statusView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        checkBtn = chip("🔎 Revisar") { onCheckClick() }
        checkBtn.textSize = 13f
        checkBtn.setPadding(dp(10), dp(4), dp(10), dp(4))
        iconLabel(checkBtn, R.drawable.ic_search, "Revisar")   // [ICONOS-2]
        failBtn = chip("⚠") { showFailedDialog() }   // [FUENTES-1]
        failBtn.contentDescription = "Canales que fallan"
        failBtn.textSize = 13f
        failBtn.setPadding(dp(10), dp(4), dp(10), dp(4))
        failBtn.visibility = View.GONE
        guideBtn = chip("📅 Guía") { guideDialog() }   // [CANALES-1] con texto   // [EPG-1]
        guideBtn.contentDescription = "Guía de programación"
        guideBtn.textSize = 13f
        guideBtn.setPadding(dp(10), dp(4), dp(10), dp(4))
        iconLabel(guideBtn, R.drawable.ic_event, "Guía")   // [ICONOS-2]
        guideBtn.visibility = if (Epg.enabled(this)) View.VISIBLE else View.GONE
        statusRow.addView(guideBtn)
        statusRow.addView(failBtn)
        statusRow.addView(checkBtn)
        statusRow.setPadding(0, dp(6), 0, dp(2))   // [VISTA-4] "Revisar" con más aire respecto a los chips de ánimo
        root.addView(statusRow)

        chAdapter = ChannelAdapter()
        channelList = ListView(this)
        sections = LinearLayout(this)   // [ESTILO-2] se llena en refreshSections()
        sections.orientation = LinearLayout.VERTICAL
        channelList.addHeaderView(moodHolder, null, false)   // [CANALES-1]
        channelList.addHeaderView(sections, null, false)   // antes del adapter; la cabecera desplaza los índices
        channelList.adapter = chAdapter
        channelList.divider = null
        channelList.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - channelList.headerViewsCount   // [ESTILO-2]
            if (i in shown.indices) openChannel(shown[i])
        }
        channelList.setOnItemLongClickListener { _, _, pos, _ ->   // [OCULTAR-1] mantener pulsado el canal = quitarlo
            val i = pos - channelList.headerViewsCount   // [ESTILO-2]
            if (i in shown.indices) { if (favOnly) favRowMenu(i) else confirmHide(shown[i]) }   // [FAV-ORDEN-1]
            true
        }
        root.addView(
            channelList,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // [CANAL-MINI-1] los botones mandan las mismas órdenes que la notificación; las atiende la pantalla principal
        nowBar = LinearLayout(this)
        nowBar.gravity = Gravity.CENTER_VERTICAL
        nowBar.setPadding(dp(12), dp(10), dp(4), dp(8))
        nowBar.background = ContextCompat.getDrawable(this, R.drawable.bg_mini_player)
        nowBar.visibility = View.GONE
        nowTitle = TextView(this)
        nowTitle.setTextColor(txtColor)
        nowTitle.textSize = 14f
        nowTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        nowTitle.maxLines = 1
        nowTitle.ellipsize = TextUtils.TruncateAt.END
        nowBar.addView(nowTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val tvCmd = { act: String -> sendBroadcast(Intent(act).setPackage(packageName)) }
        nowFav = chip("☆") {
            tvCmd(MainActivity.ACT_FAV)
            Handler(Looper.getMainLooper()).postDelayed({ favs = Store.chFavs(); updateNowBar(); chAdapter.notifyDataSetChanged() }, 400)
        }
        // [ICONOS-1] ⏮ ⏯ ⏭ con los mismos iconos de la pantalla principal (los emojis se ven distinto en cada teléfono)
        nowBar.addView(iconBtn(R.drawable.ic_prev, "Canal anterior") { tvCmd(MainActivity.ACT_PREV) })
        nowBar.addView(iconBtn(R.drawable.ic_pause, "Pausar o continuar") { tvCmd(MainActivity.ACT_TOGGLE) })
        nowBar.addView(iconBtn(R.drawable.ic_next, "Canal siguiente") { tvCmd(MainActivity.ACT_NEXT) })
        for (b in listOf(nowFav, chip("✕") { nowDismissed = Zap.nowTitle; updateNowBar() })) {
            b.textSize = 16f
            b.setPadding(dp(10), dp(6), dp(10), dp(6))
            nowBar.addView(b)
        }
        root.addView(nowBar)
        setContentView(root)
        nowRecv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                nowDismissed = null
                Zap.nowTitle = i?.getStringExtra("title")
                Zap.nowUrl = i?.getStringExtra("url")
                updateNowBar()
            }
        }
        val nowFilter = IntentFilter(MainActivity.ACT_CHANNEL_ON_TV)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(nowRecv, nowFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(nowRecv, nowFilter)
        updateNowBar()

        val added = Store.installCatalog(Catalog.VERSION, Catalog.items)   // [CATALOGO-1] listas de fábrica, una sola vez
        if (added > 0) toast("📡 Se añadieron $added listas de canales")
        val saved = Store.m3uLists()
        val last = Store.lastM3u
        val start = saved.firstOrNull { it.url == last } ?: saved.firstOrNull()
        intent.getStringExtra("q")?.takeIf { it.isNotBlank() }?.let { searchBox.setText(it) }   // [BUSCA-1] viene del buscador de la pantalla principal
        if (start != null) {
            loadList(start)
        } else {
            statusView.text = "Toca ＋ para añadir una lista M3U (enlace)."
            addListDialog()
        }
    }

    // ------------------------------------------------------------ Listas

    private fun loadList(l: M3uList) {
        if (checking) stopCheck(null)   // [CHECK-1]
        current = l
        regionName = null   // [EPG-REGION-1]
        regionLists = emptyList()
        Epg.clear()   // [EPG-1] la guía de la lista anterior no se mezcla con esta
        Store.lastM3u = l.url
        listBtn.text = "📡 ${l.name} ▾"
        statusView.text = "Descargando lista…"
        channels = emptyList()
        normNames = emptyList()
        shown = emptyList()
        groupFilter = null
        moodFilter = -1   // [ESTILO-3]
        groupBtn.text = "Grupo: Todos ▾"
        chAdapter.notifyDataSetChanged()
        refreshMoods()   // [ESTILO-3] lista vacía: la fila se esconde
        val real = if (Xtream.isXtream(l.url)) Xtream.playlistUrl(this, l.url) else l.url   // [XTREAM-1] enlace con usuario y clave solo para descargar
        if (real == null) {
            statusView.text = "🔑 Esta lista necesita tu usuario y clave otra vez (no viajan en los respaldos)."
            xtreamDialog(l.name, l.url)
            return
        }
        val gen = ++loadGen
        val cache = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
        thread {
            var text: String? = null
            var offline = false
            var bad = false                          // [LISTA-VAL-1] la descarga llegó vacía o dañada
            var pre: List<M3uChannel>? = null        // [LISTA-VAL-1] ya parseada (no se parsea dos veces)
            try {
                val t = M3uParser.download(real)
                val p = M3uParser.parse(t)
                if (p.isNotEmpty()) {
                    text = t
                    pre = p
                    try { cache.writeText(t) } catch (e: Exception) { }
                } else {
                    // [LISTA-VAL-1] no se pisa la copia buena con algo vacío o dañado
                    bad = true
                    DebugLog.add("M3U: la lista nueva llegó vacía o dañada, se conserva la anterior: ${l.url}")
                }
            } catch (e: Exception) {
                DebugLog.add("M3U: no se pudo descargar ${l.url}: ${e.javaClass.simpleName}: ${e.message}")
            }
            if (text == null && cache.exists()) {
                try { text = cache.readText(); offline = true } catch (x: Exception) { }
            }
            val body = text
            val parsed = pre ?: (if (body != null) M3uParser.parse(body) else emptyList())
            val list = parsed.distinctBy { it.url }   // [CATALOGO-1] sin enlaces repetidos
            Store.backfillChFavs(list)   // [FAV-1] completa el nombre de los favoritos de esta lista
            if (list.size != parsed.size) DebugLog.add("M3U: ${l.name}: ${parsed.size - list.size} repetidos quitados")
            val names = list.map { norm(it.name) }
            runOnUiThread {
                if (isFinishing || gen != loadGen) return@runOnUiThread
                channels = list
                normNames = names
                fromCache = offline
                cacheNote = if (bad) " · lista nueva dañada (copia guardada)" else " · sin conexión (copia guardada)"   // [LISTA-VAL-1]
                applyFilter()
                if (body == null) statusView.text = if (bad) "❌ La lista llegó vacía o dañada. Revisa el enlace."
                    else "❌ No se pudo descargar la lista. Revisa el enlace y tu conexión."
                else if (list.isEmpty()) statusView.text = "La lista no tiene canales http/https."
            }
            loadEpg(l, body, list, gen, false)   // [EPG-1] ya en el hilo aparte, después de mostrar la lista
        }
    }

    /** [EPG-1] Carga la guía (copia reciente o descarga). Llamar desde un hilo aparte. Con datos móviles solo se descarga si [force]. */
    private fun loadEpg(l: M3uList, body: String?, list: List<M3uChannel>, gen: Int, force: Boolean): String {
        if (!Epg.enabled(this)) return "apagada"
        if (list.isEmpty()) return "sin canales"
        val urls = Epg.sources(this, l.url, body)
        val res = Epg.load(this, l.url, urls, list, force, force || !onMobileData()) { gen == loadGen && !isFinishing }
        runOnUiThread {
            if (!isFinishing && gen == loadGen) {
                chAdapter.notifyDataSetChanged()
                refreshSections()
            }
        }
        return res
    }

    /** [EPG-1] Recuadro 📅: de dónde sale la guía, pegar una URL XMLTV propia y actualizar ya. */
    private fun guideDialog() {
        val l = current
        if (l == null) {
            val rn = regionName
            if (rn != null) regionGuideDialog(rn, regionLists) else toast("Elige una lista o una región para ver su guía")   // [EPG-REGION-1]
            return
        }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val info = TextView(this)
        val src = if (Epg.lastSource.isEmpty()) "" else "Fuente: ${Epg.lastSource}.\n"
        val res = if (Epg.lastResult.isEmpty()) "" else "Último intento: ${Epg.lastResult}.\n"
        info.text = src + res + Epg.info()
        info.textSize = 13f
        info.setTextColor(mutedColor)
        box.addView(info)
        val urlIn = EditText(this)
        urlIn.hint = "URL de guía propia (.xml o .xml.gz), opcional"
        urlIn.setSingleLine(true)
        urlIn.inputType = InputType.TYPE_TEXT_VARIATION_URI
        urlIn.setText(Epg.manualUrl(this, l.url))
        box.addView(urlIn)
        AlertDialog.Builder(this)
            .setTitle("📅 Guía de programación")
            .setView(box)
            .setPositiveButton("Guardar y actualizar") { _, _ ->
                val u = urlIn.text.toString().trim()
                if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
                    toast("El enlace debe empezar con http:// o https://")
                } else {
                    Epg.setManualUrl(this, l.url, u)
                    refreshEpg(l)
                }
            }
            .setNeutralButton("Actualizar ya") { _, _ -> refreshEpg(l) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** [EPG-1] Descarga la guía otra vez (sin usar la copia reciente, también con datos móviles). */
    private fun refreshEpg(l: M3uList) {
        if (!Epg.enabled(this)) { toast("Activa la guía en Ajustes › Video"); return }
        val gen = loadGen
        val list = channels
        toast("📅 Actualizando la guía…")
        thread {
            var body: String? = null
            try {
                val f = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
                if (f.exists()) body = f.readText()
            } catch (e: Exception) { }
            val res = loadEpg(l, body, list, gen, true)
            runOnUiThread { if (!isFinishing) toast("📅 " + res) }
        }
    }

    /** [EPG-REGION-1] Guía de «toda la región»: junta las guías de las cabeceras de sus listas. Llamar desde un hilo aparte. */
    private fun loadEpgRegion(name: String, heads: List<List<String>>, list: List<M3uChannel>, gen: Int, force: Boolean): String {
        if (!Epg.enabled(this)) return "apagada"
        if (list.isEmpty()) return "sin canales"
        val urls = Epg.regionSources(heads)
        val res = if (urls.isEmpty()) {
            val r = "las listas de esta región no traen guía"
            Epg.lastResult = r
            DebugLog.add("EPG: $r")
            r
        } else {
            Epg.load(this, "region:" + name, urls, list, force, force || !onMobileData()) { gen == loadGen && !isFinishing }
        }
        runOnUiThread {
            if (!isFinishing && gen == loadGen) {
                chAdapter.notifyDataSetChanged()
                refreshSections()
            }
        }
        return res
    }

    /** [EPG-REGION-1] Recuadro 📅 de una región: de dónde sale la guía y «Actualizar ya». Sin URL propia (eso es por lista). */
    private fun regionGuideDialog(name: String, lists: List<M3uList>) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val info = TextView(this)
        val src = if (Epg.lastSource.isEmpty()) "" else "Fuente: ${Epg.lastSource}.\n"
        val res = if (Epg.lastResult.isEmpty()) "" else "Último intento: ${Epg.lastResult}.\n"
        info.text = src + res + Epg.info() + "\n\nPara pegar una URL de guía propia, abre una sola lista."
        info.textSize = 13f
        info.setTextColor(mutedColor)
        box.addView(info)
        AlertDialog.Builder(this)
            .setTitle("📅 Guía de la región")
            .setView(box)
            .setPositiveButton("Actualizar ya") { _, _ -> refreshEpgRegion(name, lists) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** [EPG-REGION-1] Descarga otra vez la guía de la región (sin la copia reciente, también con datos móviles). */
    private fun refreshEpgRegion(name: String, lists: List<M3uList>) {
        if (!Epg.enabled(this)) { toast("Activa la guía en Ajustes › Video"); return }
        val gen = loadGen
        val list = channels
        toast("📅 Actualizando la guía…")
        thread {
            val heads = lists.map { l ->
                try {
                    val f = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
                    if (f.exists()) M3uParser.epgUrls(f.readText(), 300) else emptyList()
                } catch (e: Exception) { emptyList<String>() }
            }
            val res = loadEpgRegion(name, heads, list, gen, true)
            runOnUiThread { if (!isFinishing) toast("📅 " + res) }
        }
    }

    private fun chooseList() {
        val saved = Store.m3uLists()
        if (saved.isEmpty()) {
            addListDialog()
            return
        }
        // [REGIONES-1a] nivel 1: regiones y categorías; nivel 2: las listas de la región elegida
        val groups = Regions.ORDER
            .map { r -> r to saved.filter { Regions.regionOf(it.url) == r } }
            .filter { it.second.isNotEmpty() }
        if (groups.size <= 1) {
            showListsOf(saved, "Mis listas M3U", false)
            return
        }
        val labels = ArrayList<String>()
        for ((r, l) in groups) labels.add("$r (${l.size})")
        labels.add("📋 Ver todas (${saved.size})")
        labels.add("🔎 Buscar en todas mis listas")   // [BUSCA-TODO-1]
        AlertDialog.Builder(this)
            .setTitle("Regiones y categorías")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == groups.size + 1) loadAllCached(saved)   // [BUSCA-TODO-1]
                else if (which == groups.size) showListsOf(saved, "Todas mis listas", true)
                else showListsOf(groups[which].second, groups[which].first, true, groups[which].first)   // [REGIONES-1b]
            }
            .setNeutralButton("＋ Nueva") { _, _ -> addListDialog() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** [REGIONES-1a] Lista de listas; con "← Regiones" vuelve al nivel 1. */
    private fun showListsOf(lists: List<M3uList>, title: String, canGoBack: Boolean, regionName: String? = null) {
        // [REGIONES-1b2] "Toda la región" va como primera fila (arriba) para que Cerrar y ← Regiones quepan en una fila
        val names = lists.map { it.name }.toMutableList()
        if (regionName != null) names.add(0, "🌐 Toda la región (${lists.size} listas)")
        val b = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names.toTypedArray()) { _, which ->
                if (regionName != null && which == 0) loadRegion(lists, regionName)
                else loadList(lists[if (regionName != null) which - 1 else which])
            }
            .setNegativeButton("Cerrar", null)
        if (canGoBack) b.setNeutralButton("← Regiones") { _, _ -> chooseList() }
        else b.setNeutralButton("＋ Nueva") { _, _ -> addListDialog() }
        b.show()
    }

    /** [REGIONES-1b] Descarga y une todas las listas de una región o categoría, sin repetidos por enlace de stream. */
    private fun loadRegion(lists: List<M3uList>, name: String) {
        if (lists.isEmpty()) return
        if (checking) stopCheck(null)   // [CHECK-1]
        current = null
        regionName = name   // [EPG-REGION-1] la guía de la región se arma con las cabeceras de sus listas
        regionLists = lists
        Epg.clear()   // [EPG-1] la guía de la lista anterior no se mezcla con esta
        groupFilter = null
        moodFilter = -1   // [ESTILO-3]
        groupBtn.text = "Grupo: Todos ▾"
        listBtn.text = "🌐 ${name.trimStart { !it.isLetterOrDigit() }} · toda ▾"   // [PULIDO-3a] un solo icono
        channels = emptyList()
        normNames = emptyList()
        shown = emptyList()
        fromCache = false
        refreshMoods()   // [ESTILO-3]
        statusView.text = "Descargando 0 de ${lists.size} listas…"
        chAdapter.notifyDataSetChanged()
        val gen = ++loadGen
        val total = lists.size
        val results = arrayOfNulls<List<M3uChannel>>(total)
        val heads = arrayOfNulls<List<String>>(total)   // [EPG-REGION-1] guías de la cabecera de cada lista
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        val kept = java.util.concurrent.atomic.AtomicInteger(0)   // [LISTA-VAL-1] listas que llegaron dañadas y usaron su copia
        val normCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val applied = IntArray(1)   // solo se toca en el hilo de la interfaz
        val pool = Executors.newFixedThreadPool(4)
        for (idx in 0 until total) {
            pool.execute {
                if (gen != loadGen) return@execute
                val l = lists[idx]
                val cache = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
                var text: String? = null
                var pre: List<M3uChannel>? = null   // [LISTA-VAL-1] ya parseada
                try {
                    val t = M3uParser.download(l.url)
                    val p = M3uParser.parse(t)
                    if (p.isNotEmpty()) {
                        text = t
                        pre = p
                        try { cache.writeText(t) } catch (e: Exception) { }
                    } else {
                        // [LISTA-VAL-1] no se pisa la copia buena con algo vacío o dañado
                        kept.incrementAndGet()
                        DebugLog.add("M3U región: la lista nueva llegó vacía o dañada, se conserva la anterior: ${l.url}")
                    }
                } catch (e: Exception) {
                    DebugLog.add("M3U región: no se pudo descargar ${l.url}: ${e.javaClass.simpleName}: ${e.message}")
                }
                if (text == null && cache.exists()) {
                    try { text = cache.readText() } catch (x: Exception) { }
                }
                val body = text
                if (body == null) failed.incrementAndGet()
                val parsed = pre ?: (if (body != null) M3uParser.parse(body) else emptyList())
                synchronized(results) {
                    results[idx] = parsed
                    heads[idx] = if (body != null) M3uParser.epgUrls(body, 300) else emptyList()   // [EPG-REGION-1]
                }
                val n = done.incrementAndGet()
                // Une todo lo que ya llegó, en el orden de las listas (el primero que trae un enlace gana)
                val merged = LinkedHashMap<String, M3uChannel>()
                var dupes = 0
                var capped = false
                synchronized(results) {
                    loop@ for (r in results) {
                        if (r == null) continue
                        for (c in r) {
                            if (merged.containsKey(c.url)) { dupes++; continue }
                            if (merged.size >= regionMax) { capped = true; break@loop }
                            merged[c.url] = c
                        }
                    }
                }
                val list = ArrayList(merged.values)
                val names = list.map { normCache.getOrPut(it.url) { norm(it.name) } }
                if (n == total) {
                    DebugLog.add("M3U región $name: $total listas, ${list.size} canales, $dupes repetidos quitados" +
                        (if (failed.get() > 0) ", ${failed.get()} sin conexión" else "") + (if (capped) ", tope de $regionMax" else ""))
                    Store.backfillChFavs(list)   // [FAV-1]
                    pool.shutdown()
                }
                runOnUiThread {
                    if (isFinishing || gen != loadGen || n <= applied[0]) return@runOnUiThread
                    applied[0] = n
                    channels = list
                    normNames = names
                    applyFilter()
                    if (!favOnly) {
                        val vn = visibleCount(list)   // [OCULTAR-1]
                        statusView.text = if (n < total) "Descargando $n de $total listas… · $vn canales"
                            else "$vn canales de $total listas (sin repetidos)" +
                                (if (failed.get() > 0) " · ${failed.get()} sin conexión" else "") +
                                (if (kept.get() > 0) " · ${kept.get()} con copia anterior" else "") +   // [LISTA-VAL-1]
                                (if (capped) " · tope de $regionMax" else "")
                    }
                }
                // [EPG-REGION-1] ya mostrada la región completa, se carga su guía (sigue en este hilo aparte)
                if (n == total && gen == loadGen) {
                    val hs = synchronized(results) { heads.map { it ?: emptyList() } }
                    loadEpgRegion(name, hs, list, gen, false)
                }
            }
        }
        pool.shutdown()   // deja terminar lo ya pedido y no acepta más
    }

    /**
     * [BUSCA-TODO-1] Une, SIN descargar nada, las copias guardadas de todas mis listas (las que ya se abrieron alguna vez)
     * para buscar en todas a la vez con el buscador de siempre. Sin repetidos por enlace; mismo tope que una región.
     * La guía solo se lee de su copia (nunca se descarga sola); con «Actualizar ya» en 📅 se baja, como en una región.
     */
    private fun loadAllCached(lists: List<M3uList>) {
        if (lists.isEmpty()) return
        if (checking) stopCheck(null)   // [CHECK-1]
        val name = "Todas mis listas"
        current = null
        regionName = name
        regionLists = lists
        Epg.clear()
        groupFilter = null
        moodFilter = -1
        groupBtn.text = "Grupo: Todos ▾"
        listBtn.text = "🔎 $name ▾"
        channels = emptyList()
        normNames = emptyList()
        shown = emptyList()
        fromCache = false
        refreshMoods()
        statusView.text = "Leyendo las copias guardadas…"
        chAdapter.notifyDataSetChanged()
        val gen = ++loadGen
        thread {
            val merged = LinkedHashMap<String, M3uChannel>()
            val heads = ArrayList<List<String>>()
            var withCopy = 0
            var dupes = 0
            var capped = false
            for (l in lists) {
                if (gen != loadGen) return@thread
                val f = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
                if (!f.exists()) continue
                val body: String = try { f.readText() } catch (e: Exception) { continue }
                val parsed = M3uParser.parse(body)
                if (parsed.isEmpty()) continue
                withCopy++
                heads.add(M3uParser.epgUrls(body, 300))
                for (c in parsed) {
                    if (merged.containsKey(c.url)) { dupes++; continue }
                    if (merged.size >= regionMax) { capped = true; break }
                    merged[c.url] = c
                }
            }
            val missing = lists.size - withCopy
            val list = ArrayList(merged.values)
            val names = list.map { norm(it.name) }
            DebugLog.add("M3U todas: $withCopy listas con copia, ${list.size} canales, $dupes repetidos quitados" +
                (if (missing > 0) ", $missing sin copia" else "") + (if (capped) ", tope de $regionMax" else ""))
            runOnUiThread {
                if (isFinishing || gen != loadGen) return@runOnUiThread
                channels = list
                normNames = names
                fromCache = list.isNotEmpty()
                cacheNote = " · de $withCopy listas" + (if (missing > 0) " ($missing sin copia)" else "") + (if (capped) " · tope de $regionMax" else "")
                applyFilter()
                if (list.isEmpty()) {
                    statusView.text = "Ninguna lista tiene copia guardada todavía: abre una lista (o «Toda la región») una vez para poder buscar en ella."
                } else {
                    searchBox.requestFocus()
                    try {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                            .showSoftInput(searchBox, 0)
                    } catch (e: Exception) { }
                }
            }
            if (list.isNotEmpty() && Epg.enabled(this)) {
                // la guía solo de su copia guardada (allowNet = false): así se puede buscar también por programa
                Epg.load(this, "region:" + name, Epg.regionSources(heads), list, false, false) { gen == loadGen && !isFinishing }
                runOnUiThread {
                    if (!isFinishing && gen == loadGen) applyFilter()
                }
            }
            Store.backfillChFavs(list)   // [FAV-1]
        }
    }

    private fun addListDialog() {
        val firstTime = Store.m3uLists().isEmpty()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val nameIn = EditText(this)
        nameIn.hint = "Nombre (ej. Canales gratis)"
        nameIn.setSingleLine(true)
        val urlIn = EditText(this)
        urlIn.hint = "https://…/lista.m3u"
        urlIn.setSingleLine(true)
        urlIn.inputType = InputType.TYPE_TEXT_VARIATION_URI
        if (firstTime) {
            // Lista de canales gratuitos (emisión abierta) para probar
            nameIn.setText("Free-TV (gratis)")
            urlIn.setText(freeTvUrl)
        }
        box.addView(nameIn)
        box.addView(urlIn)
        var addDlg: AlertDialog? = null
        val qrLink = TextView(this)   // [QR-1] pasar la lista desde otro dispositivo
        qrLink.text = "📲 Desde otro dispositivo (QR)"
        qrLink.textSize = 15f
        qrLink.setTextColor(accent())
        qrLink.setPadding(0, dp(16), 0, dp(8))
        qrLink.setOnClickListener { addDlg?.dismiss(); startQr() }
        box.addView(qrLink)
        val curList = current
        if (curList != null) {   // [QR-2a] enviar la lista abierta a otro móvil
            val sendLink = TextView(this)
            sendLink.text = "📤 Enviar esta lista a otro teléfono"
            sendLink.textSize = 15f
            sendLink.setTextColor(accent())
            sendLink.setPadding(0, dp(8), 0, dp(8))
            sendLink.setOnClickListener { addDlg?.dismiss(); sendList(curList) }
            box.addView(sendLink)
        }
        addDlg = AlertDialog.Builder(this)
            .setTitle("Añadir lista M3U")
            .setView(box)
            .setPositiveButton("Guardar y abrir") { _, _ ->
                val u = urlIn.text.toString().trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    toast("El enlace debe empezar con http:// o https://")
                } else {
                    var n = nameIn.text.toString().trim()
                    if (n.isEmpty()) n = android.net.Uri.parse(u).host ?: "Lista"
                    Store.addM3uList(n, u)
                    loadList(M3uList(n, u))
                }
            }
            .setNeutralButton("🔑 Xtream") { _, _ -> xtreamDialog(null, null) }   // [XTREAM-1]
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** [QR-1] Comprueba que el servidor de CASTV responde y, si sí, abre el recuadro con el QR. */
    private fun startQr(onReady: (String) -> Unit = { showQrDialog(it) }) {   // [QR-2a] onReady: qué recuadro abrir
        val ip = CastvNet.localIpAddress(null)
        if (ip == null) { toast("Conéctate a un Wi-Fi para usar esto"); return }
        thread {
            val ok = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", ServerPort.port), 700) }
                true
            } catch (e: Exception) { false }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (ok) onReady(ip) else toast("El servidor de CASTV no está activo. Abre la pantalla principal y vuelve a intentarlo.")
            }
        }
    }

    /** [QR-1] Recuadro con el QR y la dirección. Se cierra solo al recibir la lista, a los 5 minutos o con «Cerrar». */
    private fun showQrDialog(ip: String) {
        var dlg: AlertDialog? = null
        val tok = QrAdd.open { name, url ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                dlg?.dismiss()
                val n = if (name.isEmpty()) (android.net.Uri.parse(url).host ?: "Lista") else name
                Store.addM3uList(n, url)
                loadList(M3uList(n, url))
                toast("📲 Lista recibida: $n")
            }
        }
        val addr = "http://$ip:${ServerPort.port}/qr/$tok"
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val bmp = QrImage.make(addr, dp(240))
        if (bmp != null) {
            val img = ImageView(this)
            img.setImageBitmap(bmp)
            box.addView(img, LinearLayout.LayoutParams(dp(240), dp(240)))
        }
        val t = TextView(this)
        t.text = "Con otro teléfono u ordenador del mismo Wi-Fi, escanea el QR o abre:\n$addr\n\nSe cierra solo al recibir la lista o en 5 minutos."
        t.textSize = 13f
        t.setTextColor(mutedColor)
        t.setPadding(0, dp(12), 0, 0)
        t.setTextIsSelectable(true)
        box.addView(t)
        dlg = AlertDialog.Builder(this)
            .setTitle("📲 Desde otro dispositivo")
            .setView(box)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener { QrAdd.close("recuadro cerrado") }
            .show()
        val d = dlg
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (d != null && d.isShowing) d.dismiss() }, 5L * 60_000L)
    }

    /** [QR-2a] Envía la lista abierta a otro móvil. Las listas con usuario y clave no se envían: se explica por qué. */
    private fun sendList(l: M3uList) {
        val why = QrSend.check(l.url)
        if (why != null) {
            AlertDialog.Builder(this)
                .setTitle("No se puede enviar esta lista")
                .setMessage(why)
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        startQr { ip -> showSendDialog(ip, l) }
    }

    /** [QR-2a] Recuadro con el QR y la dirección de envío. Se cierra solo al entregar la lista, a los 5 minutos o con «Cerrar». */
    private fun showSendDialog(ip: String, l: M3uList) {
        var dlg: AlertDialog? = null
        val tok = QrSend.open(l.name, l.url) {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                dlg?.dismiss()
                toast("📤 Lista enviada: ${l.name}")
            }
        }
        if (tok == null) { toast("Esta lista no se puede enviar"); return }
        val addr = "http://$ip:${ServerPort.port}/send/$tok"
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val bmp = QrImage.make(addr, dp(240))
        if (bmp != null) {
            val img = ImageView(this)
            img.setImageBitmap(bmp)
            box.addView(img, LinearLayout.LayoutParams(dp(240), dp(240)))
        }
        val t = TextView(this)
        t.text = "Con el otro teléfono del mismo Wi-Fi, escanea el QR o abre:\n$addr\n\nSe cierra sola al entregar la lista o en 5 minutos."
        t.textSize = 13f
        t.setTextColor(mutedColor)
        t.setPadding(0, dp(12), 0, 0)
        t.setTextIsSelectable(true)
        box.addView(t)
        dlg = AlertDialog.Builder(this)
            .setTitle("📤 Enviar «${l.name}»")
            .setView(box)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener { QrSend.close("recuadro cerrado") }
            .show()
        val d = dlg
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (d != null && d.isShowing) d.dismiss() }, 5L * 60_000L)
    }

    /** [XTREAM-1] Lista de un proveedor Xtream: servidor, usuario y clave. Con [reuse] se vuelve a poner el acceso de una lista existente. */
    private fun xtreamDialog(name: String?, reuse: String?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val nameIn = EditText(this)
        nameIn.hint = "Nombre (ej. Mi proveedor)"
        nameIn.setSingleLine(true)
        if (name != null) nameIn.setText(name)
        val srvIn = EditText(this)
        srvIn.hint = "Servidor: http://servidor:puerto"
        srvIn.setSingleLine(true)
        srvIn.inputType = InputType.TYPE_TEXT_VARIATION_URI
        val userIn = EditText(this)
        userIn.hint = "Usuario"
        userIn.setSingleLine(true)
        userIn.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val passIn = EditText(this)
        passIn.hint = "Clave"
        passIn.setSingleLine(true)
        passIn.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(nameIn)
        box.addView(srvIn)
        box.addView(userIn)
        box.addView(passIn)
        AlertDialog.Builder(this)
            .setTitle("🔑 Lista Xtream")
            .setMessage("El usuario y la clave se guardan solo en este teléfono: no entran en respaldos ni en el /log.")
            .setView(box)
            .setPositiveButton("Guardar y abrir") { _, _ ->
                val s = srvIn.text.toString()
                val u = userIn.text.toString().trim()
                val p = passIn.text.toString()
                if (!Xtream.validServer(s)) {
                    toast("El servidor debe empezar con http:// o https://")
                } else if (u.isEmpty() || p.isEmpty()) {
                    toast("Falta el usuario o la clave")
                } else {
                    val url = Xtream.save(this, s, u, p, reuse)
                    var n = nameIn.text.toString().trim()
                    if (n.isEmpty()) n = android.net.Uri.parse(Xtream.cleanServer(s)).host ?: "Xtream"
                    Store.addM3uList(n, url)
                    loadList(M3uList(n, url))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun removeCurrent() {
        val cur = current ?: run { toast("Elige una lista para quitarla"); return }   // [REGIONES-1b]
        AlertDialog.Builder(this)
            .setTitle("¿Quitar la lista?")
            .setMessage("«${cur.name}» se quitará de tus listas (los canales no se borran de internet).")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Quitar") { _, _ ->
                Store.removeM3uList(cur.url)
                if (Xtream.isXtream(cur.url)) Xtream.remove(this, cur.url)   // [XTREAM-1] también se borra su usuario y clave
                try { File(cacheDir, "m3u_" + cur.url.hashCode() + ".txt").delete() } catch (e: Exception) { }
                Epg.forget(this, cur.url)   // [EPG-1] su URL de guía y su copia
                current = null
                val next = Store.m3uLists().firstOrNull()
                if (next != null) {
                    loadList(next)
                } else {
                    loadGen++
                    channels = emptyList()
                    normNames = emptyList()
                    shown = emptyList()
                    listBtn.text = "📡 Sin lista ▾"
                    statusView.text = "Toca ＋ para añadir una lista M3U (enlace)."
                    chAdapter.notifyDataSetChanged()
                }
            }
            .show()
    }

    // ------------------------------------------------------------ Filtros

    private fun applyFilter() {
        val toks = norm(searchBox.text.toString()).split(' ').filter { it.isNotEmpty() }
        val g = groupFilter
        val mood = moodFilter   // [ESTILO-3]
        val out = ArrayList<M3uChannel>()
        val byProg = toks.isNotEmpty() && Epg.enabled(this)   // [EPG-BUSCAR-1] también se busca en el programa de ahora
        // [FAV-1] con ★ Favs activo la base son los favoritos de TODAS las listas
        val base = if (favOnly) favChannels else channels
        val baseNames = if (favOnly) favNorm else normNames
        for (i in base.indices) {
            val c = base[i]
            if (hidden.isNotEmpty() && hidden.contains(c.url)) continue   // [OCULTAR-1]
            if (!favOnly && g != null && !groupKeys(c).contains(g)) continue   // [GRUPOS-1]
            if (!favOnly && mood >= 0 && (moodMask(c.group) and (1 shl mood)) == 0) continue   // [ESTILO-3]
            val n = baseNames[i]
            if (toks.all { n.contains(it) }) out.add(c)
            else if (byProg) {   // [EPG-BUSCAR-1]
                val pt = Epg.nowTitle(c)
                if (pt != null) {
                    val pn = norm(pt)
                    if (toks.all { pn.contains(it) }) out.add(c)
                }
            }
        }
        // Favoritos primero (el orden es estable: el resto conserva su orden)
        shown = if (favs.isEmpty()) out else out.sortedByDescending { favs.contains(it.url) }
        chAdapter.notifyDataSetChanged()
        if (favOnly) {
            statusView.text = if (favChannels.isEmpty()) "Aún no tienes favoritos: marca ★ en un canal"
                else "${shown.size} de ${favChannels.size} favoritos (de todas las listas)"
        } else if (channels.isNotEmpty()) {
            statusView.text = "${shown.size} de ${visibleCount(channels)} canales" +
                (if (fromCache) cacheNote else "")
        }
        refreshSections()   // [ESTILO-2]
        refreshMoods()   // [ESTILO-3]
        updateFailBtn()   // [FUENTES-1]
    }

    // ------------------------------------------------------------ [ESTILO-3] Ánimos

    /** Máscara de ánimos (un bit por ánimo) de un texto de grupos "Movies;Music"; se guarda por texto, no por canal. */
    private fun moodMask(group: String): Int = moodMaskCache.getOrPut(group) {
        var m = 0
        for (k in group.split(';')) {
            val n = norm(k.trim())
            for (i in MOODS.indices) if (MOODS[i].keys.contains(n)) m = m or (1 shl i)
        }
        m
    }

    private fun toggleMood(i: Int) {
        moodFilter = if (moodFilter == i) -1 else i
        if (moodFilter >= 0) {   // ánimo y grupo no se mezclan
            groupFilter = null
            groupBtn.text = "Grupo: Todos ▾"
        }
        applyFilter()
    }

    /** Dibuja la fila de ánimos: solo los que tienen canales en la lista abierta (y el activo). Se esconde con ★ Favs. */
    private fun refreshMoods() {
        if (!::moodRow.isInitialized) return
        if (favOnly) {
            moodScroll.visibility = View.GONE
            moodDrawn = ""
            return
        }
        val sig = System.identityHashCode(channels).toString() + "|" + channels.size + "|" + hidden.size
        val drawn = sig + "|" + moodFilter
        if (drawn == moodDrawn) return   // nada cambió (p. ej. solo se escribió en el buscador)
        if (sig != moodSig) {
            val cnt = IntArray(MOODS.size)
            val h = hidden
            for (c in channels) {
                if (h.isNotEmpty() && h.contains(c.url)) continue   // [OCULTAR-1]
                val m = moodMask(c.group)
                if (m == 0) continue
                for (i in MOODS.indices) if ((m and (1 shl i)) != 0) cnt[i]++
            }
            moodCounts = cnt
            moodSig = sig
        }
        moodDrawn = drawn
        moodRow.removeAllViews()
        var any = false
        for (i in MOODS.indices) {
            if (moodCounts[i] == 0 && i != moodFilter) continue
            any = true
            val t = chip(MOODS[i].label) { toggleMood(i) }
            t.textSize = 14f
            t.setPadding(dp(12), dp(6), dp(12), dp(6))
            if (i == moodFilter) {
                t.setTextColor(accent())
                t.setTypeface(null, android.graphics.Typeface.BOLD)
                t.background = GradientDrawable().apply {
                    setColor(0x33888888)
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(2), accent())
                }
            }
            moodRow.addView(t)
        }
        moodScroll.visibility = if (any) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------ [ESTILO-2] Continuar y Favoritos

    /** [VOLUMEN-TV-1] Con un canal en el TV, los botones de volumen del teléfono suben/bajan el volumen del TV (de 5 en 5). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            TvController.tvIp != null && Zap.nowTitle != null) {
            val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5
            thread {
                val base = if (TvController.lastVol >= 0) TvController.lastVol else (TvController.getVolume() ?: 0).let { if (it > 0) it else 20 }
                val v = (base + delta).coerceIn(0, 100)
                val ok = TvController.setVolume(v)
                runOnUiThread { toast(if (ok) "🔊 Volumen del TV: $v" else "⚠️ El TV no cambió el volumen") }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        favs = Store.chFavs()   // [FAV-REPRO-1] la ★ pudo cambiar en el reproductor
        failed = Store.failedChannels()   // [FUENTES-1] el reproductor pudo anotar fallos o aciertos
        if (::chAdapter.isInitialized) { if (favOnly) { reloadFavChannels(); applyFilter() } else { chAdapter.notifyDataSetChanged(); updateFailBtn() } }
        refreshSections()   // al volver del reproductor, "Continuar" ya trae el canal visto
        if (::guideBtn.isInitialized) guideBtn.visibility = if (Epg.enabled(this)) View.VISIBLE else View.GONE   // [EPG-1] pudo cambiar en Ajustes
        updateNowBar()   // [CANAL-MINI-1]
    }

    /** [CANAL-MINI-1] Muestra u oculta la barra del canal que está en el TV. */
    private fun updateNowBar() {
        if (!::nowBar.isInitialized) return
        val t = Zap.nowTitle
        if (t == null || t == nowDismissed) { nowBar.visibility = View.GONE; return }
        nowTitle.text = "📺 $t"
        nowFav.text = if (Zap.nowUrl?.let { favs.contains(it) } == true) "★" else "☆"
        nowBar.visibility = View.VISIBLE
    }

    /** Arma las dos filas de tarjetas de arriba. Se esconden al buscar, filtrar por grupo o con ★ Favs; una fila vacía no se muestra. */
    private fun refreshSections() {
        if (!::sections.isInitialized) return
        sections.removeAllViews()
        val plain = !favOnly && groupFilter == null && moodFilter < 0 && searchBox.text.isNullOrEmpty()   // [ESTILO-3]
        if (!plain) return
        val h = hidden
        val recent = Store.recentChannels().filter { !h.contains(it.url) }
        val favList = Store.chFavChannels().filter { !h.contains(it.url) }.take(20)
        addSection("🕘 Continuar", recent)
        addSection("★ Favoritos", favList)   // [VISTA-4] misma estrella (color de acento) que la lista
        val top = Store.topChannels().filter { !h.contains(it.first.url) }   // [TOP-CANALES-1]
        addSection("🔥 Más vistos", top.map { it.first }, top.associate { it.first.url to it.second }) { confirmResetTop() }
    }

    /** [TOP-CANALES-1] Mantener pulsado el título «Más vistos» ofrece reiniciar solo el contador. */
    private fun confirmResetTop() {
        AlertDialog.Builder(this)
            .setTitle("¿Reiniciar «Más vistos»?")
            .setMessage("Solo se borra el contador de veces. Tus favoritos, listas e historial no cambian.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Reiniciar") { _, _ ->
                Store.clearTopChannels()
                refreshSections()
            }
            .show()
    }

    private fun addSection(title: String, list: List<M3uChannel>, plays: Map<String, Int>? = null, onHeaderLong: (() -> Unit)? = null) {
        if (list.isEmpty()) return
        val t = TextView(this)
        val sp = android.text.SpannableString(title)
        if (title.startsWith("★")) {   // [VISTA-4]
            sp.setSpan(android.text.style.ForegroundColorSpan(accent()), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        t.text = sp
        t.textSize = 14f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(txtColor)
        t.setPadding(dp(4), dp(6), dp(4), dp(2))   // [VISTA-2]
        if (onHeaderLong != null) t.setOnLongClickListener { onHeaderLong(); true }   // [TOP-CANALES-1]
        sections.addView(t)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (c in list) row.addView(card(c, plays?.get(c.url)), LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        sections.addView(scroll)
    }

    /** Tarjeta pequeña: logo y nombre. Tocar = mismo menú 📺 / 📱; mantener pulsado = quitar (como en la lista). */
    private fun card(c: M3uChannel, plays: Int? = null): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(4), dp(4), dp(4), dp(4))   // [VISTA-2]
        val logo = ImageView(this)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        Logos.load(logo, c.logo)
        if (c.logo.isEmpty()) {
            logo.setImageResource(R.drawable.ic_tv)
            logo.setColorFilter(mutedColor)
        } else {
            logo.clearColorFilter()
        }
        styleLogo(logo, c.logo)   // [VISTA-1] [VISTA-5]
        box.addView(logo, LinearLayout.LayoutParams(dp(64), dp(36)))   // [VISTA-2]
        val name = TextView(this)
        name.text = (if (failed.containsKey(c.url)) "⚠ " else "") + splitName(c.name).first   // [FUENTES-1]
        name.setTextColor(txtColor)
        name.textSize = 11f   // [VISTA-2] tarjeta más ancha y letra algo menor: caben más nombres completos
        name.gravity = Gravity.CENTER_HORIZONTAL
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        box.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val nowT = Epg.nowTitle(c)   // [EPG-1] programa en curso
        if (nowT != null) {
            val g = TextView(this)
            g.text = "▶ $nowT"
            g.setTextColor(mutedColor)
            g.textSize = 9f
            g.gravity = Gravity.CENTER_HORIZONTAL
            g.maxLines = 2
            g.ellipsize = TextUtils.TruncateAt.END
            box.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (plays != null) {   // [TOP-CANALES-1]
            val p = TextView(this)
            p.text = if (plays == 1) "1 vez" else "$plays veces"
            p.setTextColor(accent())
            p.textSize = 10f
            p.gravity = Gravity.CENTER_HORIZONTAL
            p.maxLines = 1
            box.addView(p, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        box.setOnClickListener { openChannel(c) }
        box.setOnLongClickListener { confirmHide(c); true }
        return box
    }

    private fun pickGroup() {
        val vis = visible(channels)   // [OCULTAR-1] los ocultos no cuentan
        // [GRUPOS-1] cada categoría aparece una sola vez (un canal puede estar en varias)
        val counts = HashMap<String, Int>()
        for (c in vis) for (k in groupKeys(c)) counts[k] = (counts[k] ?: 0) + 1
        val keys = counts.keys.sortedWith(
            compareByDescending<String> { counts[it] ?: 0 }.thenBy { groupLabel(it) })
        if (keys.size <= 1) {
            toast("Esta lista no tiene grupos")
            return
        }
        val labels = ArrayList<String>()
        labels.add("Todos (${vis.size})")
        for (k in keys) labels.add("${groupLabel(k)} (${counts[k]})")
        AlertDialog.Builder(this)
            .setTitle("Filtrar por grupo")
            .setItems(labels.toTypedArray()) { _, which ->
                groupFilter = if (which == 0) null else keys[which - 1]
                if (groupFilter != null) moodFilter = -1   // [ESTILO-3] ánimo y grupo no se mezclan
                groupBtn.text = "Grupo: " + (groupFilter?.let { groupLabel(it) } ?: "Todos") + " ▾"
                applyFilter()
            }
            .show()
    }

    /** [GRUPOS-1] Las categorías sueltas de un canal ("Movies;Music" → Movies, Music); sin grupo → "". */
    private fun groupKeys(c: M3uChannel): List<String> {
        val l = c.group.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return if (l.isEmpty()) listOf("") else l
    }

    /** [GRUPOS-1] / [PULIDO-3b] Nombre de la categoría en español para mostrar. */
    private fun groupLabel(k: String): String =
        if (k.isEmpty()) "Sin grupo" else (GROUP_ES[k.lowercase()] ?: k)

    /** [PULIDO-3c] Separa del nombre las etiquetas "[Geo-blocked]", "[Not 24/7]" y "(720p)". */
    private fun splitName(name: String): Pair<String, String> {
        val tags = ArrayList<String>()
        val clean = TAG_RE.replace(name) { m ->
            tags.add((m.groups[1]?.value ?: m.groups[2]?.value ?: "").trim())
            ""
        }.trim()
        return Pair(if (clean.isEmpty()) name else clean, tags.filter { it.isNotEmpty() }.joinToString(" · "))
    }

    // ------------------------------------------------------------ Ocultar canales [OCULTAR-1]

    /** Los canales de [l] que no están ocultos. */
    private fun visible(l: List<M3uChannel>): List<M3uChannel> {
        val h = hidden
        return if (h.isEmpty()) l else l.filter { !h.contains(it.url) }
    }

    private fun visibleCount(l: List<M3uChannel>): Int {
        val h = hidden
        return if (h.isEmpty()) l.size else l.count { !h.contains(it.url) }
    }

    private fun updateHiddenBtn() {
        val n = hidden.size
        iconLabel(hiddenBtn, R.drawable.ic_visibility, "$n")   // [ICONOS-2]
        hiddenBtn.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    /** [DESHACER-1] Ya no pregunta: quita el canal al momento y ofrece DESHACER unos segundos (también sigue el 👁). */
    /** [FAV-ORDEN-1] Con ★ Favs activo, mantener pulsado un canal ofrece ponerlo primero, subirlo, bajarlo u ocultarlo. */
    private fun favRowMenu(i: Int) {
        if (i !in shown.indices) return
        val c = shown[i]
        val opts = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        fun moved(done: Boolean) { if (done) { reloadFavChannels(); applyFilter() } }
        if (i > 0) {
            val prev = shown[i - 1].url
            opts.add("⤒ Poner el primero"); acts.add { moved(Store.moveChFavFirst(c.url)) }
            opts.add("▲ Subir"); acts.add { moved(Store.moveChFavNear(c.url, prev, false)) }
        }
        if (i < shown.size - 1) {
            val next = shown[i + 1].url
            opts.add("▼ Bajar"); acts.add { moved(Store.moveChFavNear(c.url, next, true)) }
        }
        opts.add("Ocultar…"); acts.add { confirmHide(c) }
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(opts.toTypedArray()) { _, which -> if (which in acts.indices) acts[which]() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmHide(c: M3uChannel) {
        val wasFav = favs.contains(c.url)
        hideChannel(c)
        Snackbar.make(findViewById<View>(android.R.id.content), "🗑 «${c.name}» quitado" + (if (wasFav) " (y de favoritos)" else ""), Snackbar.LENGTH_LONG)
            .setAction("DESHACER") { undoHide(c, wasFav) }
            .show()
    }

    private fun undoHide(c: M3uChannel, wasFav: Boolean) {
        Store.unhideChannels(listOf(c.url))
        if (wasFav && !Store.chFavs().contains(c.url)) {
            Store.toggleChFav(c.url)   // vuelve la ★ que se quitó al ocultarlo
            Store.rememberChFav(c)
        }
        hidden = Store.hiddenUrls()
        favs = Store.chFavs()
        if (favOnly) reloadFavChannels()
        applyFilter()
        updateHiddenBtn()
    }

    private fun hideChannel(c: M3uChannel) {
        Store.hideChannel(c)          // también le quita la ★ si la tenía
        hidden = Store.hiddenUrls()
        favs = Store.chFavs()
        if (favOnly) reloadFavChannels()
        applyFilter()
        updateHiddenBtn()
    }

    private fun showHiddenDialog() {
        val hl = Store.hiddenChannels()
        if (hl.isEmpty()) {
            toast("No hay canales ocultos")
            return
        }
        val labels = Array(hl.size) { i -> if (hl[i].group.isBlank()) hl[i].name else "${hl[i].name} · ${hl[i].group}" }
        val checked = BooleanArray(hl.size)
        AlertDialog.Builder(this)
            .setTitle("Canales ocultos (${hl.size})")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Restaurar") { _, _ ->
                val urls = ArrayList<String>()
                for (i in hl.indices) if (checked[i]) urls.add(hl[i].url)
                if (urls.isEmpty()) toast("Marca los canales que quieres devolver") else restoreChannels(urls)
            }
            .setNeutralButton("Todos") { _, _ -> restoreChannels(hl.map { it.url }) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun restoreChannels(urls: List<String>) {
        Store.unhideChannels(urls)
        hidden = Store.hiddenUrls()
        applyFilter()
        updateHiddenBtn()
        toast("👁 Canales restaurados: ${urls.size}")
    }

    // ------------------------------------------------------------ Salud de canales [FUENTES-1]

    /** Canales de lo que se ve ahora (lista actual, o favoritos si ★ Favs está activo) que no están ocultos y fallaron 2 veces o más seguidas. */
    private fun failedNow(): List<M3uChannel> {
        val f = failed
        if (f.isEmpty()) return emptyList()
        val base = if (favOnly) favChannels else channels
        return visible(base).filter { f.containsKey(it.url) }.sortedByDescending { f[it.url] ?: 0 }
    }

    private fun updateFailBtn() {
        if (!::failBtn.isInitialized) return
        val n = failedNow().size
        iconLabel(failBtn, R.drawable.ic_warning, if (n == 1) "1 falla" else "$n fallan")   // [CANALES-1] [ICONOS-2]
        failBtn.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    /** Lista de los canales que fallan seguido, con todos desmarcados: se marcan a mano los que se quieran ocultar. */
    private fun showFailedDialog() {
        val fl = failedNow()
        if (fl.isEmpty()) {
            toast("No hay canales con fallos")
            return
        }
        val labels = Array(fl.size) { i -> "${fl[i].name} · falló ${failed[fl[i].url] ?: 2} veces seguidas" }
        val checked = BooleanArray(fl.size)
        AlertDialog.Builder(this)
            .setTitle("⚠ Fallan seguido (${fl.size}) · marca los que quieras ocultar")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Ocultar marcados") { _, _ ->
                val sel = ArrayList<M3uChannel>()
                for (i in fl.indices) if (checked[i]) sel.add(fl[i])
                if (sel.isEmpty()) toast("No marcaste ningún canal") else hideFailed(sel)
            }
            .setNeutralButton("Olvidar avisos") { _, _ ->
                Store.clearHealth()
                failed = emptyMap()
                chAdapter.notifyDataSetChanged()
                refreshSections()
                updateFailBtn()
                toast("Avisos ⚠ borrados (los canales siguen igual)")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // ------------------------------------------------------------ Revisar canales [CHECK-1]

    private class CheckFail(val ch: M3uChannel, val why: String)

    /** Toca 🔎: pide confirmación y revisa los canales que se ven en pantalla; si ya está revisando, lo detiene. */
    private fun onCheckClick() {
        if (checking) {
            stopCheck("⏹ Revisión detenida")
            return
        }
        val list = ArrayList(shown)
        if (list.isEmpty()) {
            toast("No hay canales para revisar")
            return
        }
        val mins = (list.size * 1.5 / 8 / 60).toInt() + 1   // cálculo grueso: 8 a la vez, ~1,5 s por canal
        AlertDialog.Builder(this)
            .setTitle("🔎 Revisar ${list.size} canales")
            .setMessage("El teléfono pide la lista de cada canal para ver si su servidor responde. No se reproduce nada y se gastan pocos datos.\n\n" +
                "Puede tardar " + (if (mins <= 1) "un minuto" else "unos $mins minutos") + ". Nada se oculta sin que tú aceptes.\n\n" +
                "Ojo: un canal que no responde no siempre está caído; puede estar bloqueado para tu país o tener un enlace que caduca.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Revisar") { _, _ -> startCheck(list) }
            .show()
    }

    private fun startCheck(list: List<M3uChannel>) {
        val gen = ++checkGen
        checking = true
        val total = list.size
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val fails = java.util.concurrent.ConcurrentLinkedQueue<CheckFail>()
        val oks = java.util.concurrent.ConcurrentLinkedQueue<String>()   // [FUENTES-1]
        val pool = Executors.newFixedThreadPool(8)
        checkPool = pool
        iconLabel(checkBtn, R.drawable.ic_stop, "0/$total")   // [ICONOS-2]
        statusView.text = "🔎 Revisando $total canales…"
        val listName = listBtn.text.toString()
        for (c in list) {
            pool.execute {
                if (gen != checkGen) return@execute
                val why = MediaServer.checkChannel(c.url)
                if (gen != checkGen) return@execute
                if (why != null) fails.add(CheckFail(c, why)) else oks.add(c.url)
                val n = done.incrementAndGet()
                if (n == total) {
                    Store.recordHealth(fails.map { it.ch.url }, oks.toList())   // [FUENTES-1] una sola escritura al terminar
                    runOnUiThread { if (!isFinishing && gen == checkGen) finishCheck(total, fails.toList(), listName) }
                } else if (n % 5 == 0) {
                    runOnUiThread {
                        if (!isFinishing && gen == checkGen) {
                            iconLabel(checkBtn, R.drawable.ic_stop, "$n/$total")
                            statusView.text = "🔎 Revisando… $n de $total · ${fails.size} sin respuesta"
                        }
                    }
                }
            }
        }
        pool.shutdown()   // deja terminar lo ya pedido y no acepta más
    }

    /** Detiene la revisión (los hilos que sigan en curso terminan solos y su resultado se ignora). */
    private fun stopCheck(msg: String?) {
        checkGen++
        checkPool?.shutdownNow()
        checkPool = null
        checking = false
        iconLabel(checkBtn, R.drawable.ic_search, "Revisar")
        if (msg != null) {
            toast(msg)
            applyFilter()
        }
    }

    private fun finishCheck(total: Int, fails: List<CheckFail>, listName: String) {
        checkPool = null
        checking = false
        iconLabel(checkBtn, R.drawable.ic_search, "Revisar")
        DebugLog.add("CHECK-1: $listName: $total revisados, ${fails.size} sin respuesta")
        failed = Store.failedChannels()   // [FUENTES-1]
        applyFilter()
        if (fails.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("✅ Todo responde")
                .setMessage("Los $total canales revisados respondieron bien.")
                .setPositiveButton("Cerrar", null)
                .show()
            return
        }
        val byWhy = LinkedHashMap<String, Int>()
        for (f in fails) {
            val k = f.why.substringBefore(" (")
            byWhy[k] = (byWhy[k] ?: 0) + 1
        }
        val resumen = byWhy.entries.sortedByDescending { it.value }.joinToString("\n") { "• ${it.key}: ${it.value}" }
        val favN = fails.count { favs.contains(it.ch.url) }
        AlertDialog.Builder(this)
            .setTitle("🔎 ${fails.size} de $total no responden")
            .setMessage(resumen +
                (if (favN > 0) "\n\n$favN de ellos son favoritos ★ y también se quitarían de tus favoritos." else "") +
                "\n\nPuedes ocultarlos todos (se devuelven con el botón 👁) o elegir cuáles.")
            .setPositiveButton("Ocultar") { _, _ -> hideFailed(fails.map { it.ch }) }
            .setNeutralButton("Ver cuáles") { _, _ -> pickFailed(fails) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Lista con todos marcados; se desmarcan los que se quieran conservar y se ocultan los demás. */
    private fun pickFailed(fails: List<CheckFail>) {
        val labels = Array(fails.size) { i -> "${fails[i].ch.name} · ${fails[i].why}" }
        val checked = BooleanArray(fails.size) { true }
        AlertDialog.Builder(this)
            .setTitle("Sin respuesta (${fails.size})")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Ocultar marcados") { _, _ ->
                val sel = ArrayList<M3uChannel>()
                for (i in fails.indices) if (checked[i]) sel.add(fails[i].ch)
                if (sel.isEmpty()) toast("No marcaste ningún canal") else hideFailed(sel)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun hideFailed(cs: List<M3uChannel>) {
        Store.hideChannels(cs)
        hidden = Store.hiddenUrls()
        favs = Store.chFavs()
        if (favOnly) reloadFavChannels()
        applyFilter()
        updateHiddenBtn()
        toast("🗑 ${cs.size} canales ocultos (👁 para restaurar)")
    }

    override fun onDestroy() {
        checkGen++   // [CHECK-1] si se sale de la pantalla, la revisión se detiene
        try { nowRecv?.let { unregisterReceiver(it) } } catch (e: Exception) { }   // [CANAL-MINI-1]
        QrAdd.close("pantalla cerrada")   // [QR-1]
        QrSend.close("pantalla cerrada")   // [QR-2a]
        checkPool?.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------ Canales

    private fun toggleFavOnly() {
        favOnly = !favOnly
        if (favOnly) iconLabel(favBtn, R.drawable.ic_star, "Favs", accent()) else iconLabel(favBtn, R.drawable.ic_star_border, "Favs")   // [ICONOS-2]
        if (favOnly) reloadFavChannels()   // [FAV-1]
        exportBtn.visibility = if (favOnly) View.VISIBLE else View.GONE   // [M3U-EXPORT-1]
        applyFilter()
    }

    // ------------------------------------------------------------ [M3U-EXPORT-1] Exportar ★ Favoritos como .m3u

    /** Arma el .m3u con los favoritos de todas las listas. Los canales con usuario y clave (Xtream) y los que no son http(s) se omiten; devuelve el texto y cuántos se omitieron. */
    private fun favM3u(): Pair<String, Int> {
        val leak = Xtream.leakFilter(this)
        val sb = StringBuilder("#EXTM3U\n")
        var skipped = 0
        for (c in Store.chFavChannels()) {
            val low = c.url.lowercase()
            if (!(low.startsWith("http://") || low.startsWith("https://")) || leak(c.url)) { skipped++; continue }
            val name = c.name.replace(Regex("[\\r\\n]+"), " ").trim().ifEmpty { "Canal" }
            sb.append("#EXTINF:-1")
            if (c.logo.startsWith("http", ignoreCase = true)) sb.append(" tvg-logo=\"").append(c.logo.replace("\"", "%22").replace(Regex("\\s"), "")).append("\"")
            val g = c.group.replace(Regex("[\\r\\n]+"), " ").replace('"', '\'').trim()
            if (g.isNotEmpty()) sb.append(" group-title=\"").append(g).append("\"")
            sb.append(',').append(name).append('\n').append(c.url).append('\n')
        }
        return Pair(sb.toString(), skipped)
    }

    private fun exportFavsDialog() {
        val (text, skipped) = favM3u()
        val n = text.lineSequence().count { it.startsWith("#EXTINF") }
        if (n == 0) {
            toast(if (skipped > 0) "Tus favoritos son de Xtream (llevan usuario y clave): no se exportan" else "Aún no tienes favoritos")
            return
        }
        val extra = if (skipped > 0) "\n\n$skipped canal(es) no se incluyen (Xtream: llevan usuario y clave)." else ""
        AlertDialog.Builder(this)
            .setTitle("📤 Exportar favoritos")
            .setMessage("$n canales en un archivo .m3u.$extra")
            .setPositiveButton("Compartir") { _, _ -> shareFavM3u(text) }
            .setNeutralButton("Guardar…") { _, _ -> saveFavM3u(text) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareFavM3u(text: String) {
        try {
            val dir = File(cacheDir, "share")
            dir.mkdirs()
            val f = File(dir, "castv-favoritos.m3u")
            f.writeText(text, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.shareprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "audio/x-mpegurl"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Compartir favoritos"))
        } catch (e: Exception) {
            toast("No se pudo compartir el archivo")
        }
    }

    @Suppress("DEPRECATION")
    private fun saveFavM3u(text: String) {
        exportPending = text
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "castv-favoritos.m3u")
        }
        try {
            startActivityForResult(i, reqSave)
        } catch (e: Exception) {
            exportPending = null
            toast("No se pudo abrir el selector de archivos")
        }
    }

    /** [FAV-1] Vuelve a leer los favoritos de todas las listas (con su nombre). */
    private fun reloadFavChannels() {
        favChannels = Store.chFavChannels()
        favNorm = favChannels.map { norm(it.name) }
    }

    private fun toggleFav(c: M3uChannel) {
        val on = Store.toggleChFav(c.url)
        if (on) Store.rememberChFav(c) else Store.forgetChFav(c.url)   // [FAV-1]
        favs = Store.chFavs()
        if (favOnly) { reloadFavChannels(); applyFilter() } else { chAdapter.notifyDataSetChanged(); refreshSections() }   // [ESTILO-2]
    }

    private fun isHls(url: String) = url.substringBefore('?').lowercase().endsWith(".m3u8")

    private fun openChannel(c: M3uChannel) {
        val b = AlertDialog.Builder(this)   // [ICONOS-6]
        val ic = b.context
        val labels = arrayOf<CharSequence>(
            CastvIcons.item(ic, R.drawable.ic_vp_tv, "Al TV"),   // [HLS-TEXTO-1] los canales HLS ya van bien al TV
            CastvIcons.item(ic, R.drawable.ic_vp_phone, "En el teléfono"))
        b
            .setTitle(c.name)
            .setItems(labels) { _, which -> playChannel(c, which == 0) }
            .show()
    }

    /** Reproduce el canal y esta pantalla se queda abierta para cambiar de canal. */
    private fun playChannel(c: M3uChannel, toTv: Boolean) {
        Store.addLink(c.url, c.name)   // queda en el 🕘 Historial
        Store.rememberRecentChannel(c)   // [ESTILO-2] fila "Continuar"
        if (toTv) {
            // [ZAP-1] la lista que se ve ahora queda como contexto para cambiar de canal con ⏮ ⏭
            Zap.remember(if (shown.any { it.url == c.url }) shown else visible(channels), c)
            sendToTv(c.url, c.name)
        } else playOnPhone(c.url, c.name)
    }

    /** [RED-1b] true si la conexión activa es de datos móviles (no Wi-Fi ni cable); también con VPN sobre datos. */
    private fun onMobileData(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 23) return false
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) &&
                (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || cm.isActiveNetworkMetered)
        } catch (e: Exception) { false }
    }

    private fun playOnPhone(url: String, title: String, skipWarn: Boolean = false) {
        // [RED-1b] con datos móviles se pregunta antes de gastar datos (Ajustes › Red)
        if (!skipWarn && getSharedPreferences("castv", MODE_PRIVATE).getBoolean("warn_mobile", true) && onMobileData()) {
            AlertDialog.Builder(this)
                .setTitle("📶 Estás usando datos móviles")
                .setMessage("Ver «$title» en el teléfono gastará datos de tu plan. ¿Seguir?")
                .setPositiveButton("Reproducir") { _, _ -> playOnPhone(url, title, true) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        Zap.rememberPhone(if (shown.any { it.url == url }) shown else visible(channels), url)   // [ZAP-2] ⏮ ⏭ en el reproductor del teléfono
        val i = Intent(this, VideoPlayerActivity::class.java)
            .putExtra("url", url).putExtra("urlTitle", title)
        @Suppress("DEPRECATION")
        startActivityForResult(i, reqPlayer)
    }

    /** [AVISO-HLS] El canal no se pudo preparar: se dice por qué y se ofrece verlo en el móvil o probar igual en el TV. */
    private fun warnChannel(url: String, title: String, why: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Este canal puede no verse en el TV")
            .setMessage("«$title»\n\n$why\n\nPuedes verlo en el teléfono o probar en el TV de todas formas.")
            .setPositiveButton("📱 En el teléfono") { _, _ -> playOnPhone(url, title) }
            .setNeutralButton("📺 Enviar igual") { _, _ -> sendToTv(url, title, true) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendToTv(url: String, title: String, skipCheck: Boolean = false) {
        toast("🚀 Enviando al TV…")
        thread {
            if (TvController.tvIp == null) {
                runOnUiThread { toast("⚠️ TV no encontrado todavía. Espera unos segundos o usa ⋮ › Elegir TV.") }
                return@thread
            }
            val hls = isHls(url)
            val phoneIp = CastvNet.localIpAddress(TvController.tvIp)
            val live = hls && phoneIp != null
            // [AVISO-HLS] antes de tocar nada (ni detener lo local), se revisa que el canal se pueda preparar
            if (live && !skipCheck) {
                val why = MediaServer.probeLive(url)
                if (why != null) {
                    Store.markChFail(url)   // [FUENTES-1]
                    DebugLog.add("AVISO-HLS: $title: $why")
                    runOnUiThread { if (!isFinishing) warnChannel(url, title, why) }
                    return@thread
                }
            }
            sendBroadcast(Intent(MainActivity.ACT_STOP_LOCAL).setPackage(packageName))   // [LOCAL-1] detiene lo local del teléfono
            val mime = if (live) "video/mp2t" else if (hls) "application/vnd.apple.mpegurl" else "video/mp4"
            val sendUrl = if (phoneIp == null) url else if (live) TvSend.liveUrl(phoneIp, url) else TvSend.relayUrl(phoneIp, url, hls)
            DebugLog.add("ENLACE (M3U): $url -> $sendUrl")
            val ok = TvController.setUriAndPlay(sendUrl, title, mime, false)
            if (ok) Store.markChOk(url)   // [FUENTES-1]
            if (ok) sendBroadcast(Intent(MainActivity.ACT_CHANNEL_ON_TV).setPackage(packageName).putExtra("title", title).putExtra("live", live).putExtra("url", url))   // [NOW-1] [FAV-REPRO-3] la barra y la notificación muestran el canal (y su ★)
            runOnUiThread {
                toast(if (ok) "✅ Canal en el TV" else "❌ El TV no aceptó el canal")
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqSave) {   // [M3U-EXPORT-1]
            val text = exportPending
            exportPending = null
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null && text != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    toast("✅ Favoritos guardados")
                } catch (e: Exception) {
                    toast("No se pudo guardar el archivo")
                }
            }
            return
        }
        // 📺 pulsado dentro del reproductor del móvil: se manda ese mismo canal al TV
        if (requestCode == reqPlayer && resultCode == RESULT_OK && data != null && data.hasExtra("tvUrl")) {
            val u = data.getStringExtra("tvUrl") ?: return
            shown.firstOrNull { it.url == u }?.let { Zap.remember(shown, it) }   // [ZAP-2] ⏮ ⏭ también en el TV
            sendToTv(u, data.getStringExtra("tvName") ?: "Canal")
        }
    }

    private inner class ChannelAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = (convertView as? LinearLayout) ?: LinearLayout(this@M3uActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@M3uActivity).apply {
                    textSize = 22f
                    gravity = Gravity.CENTER
                    isFocusable = false
                    setPadding(dp(8), dp(8), dp(12), dp(8))
                })
                addView(ImageView(this@M3uActivity).apply {   // [REGIONES-1c] logo del canal
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isFocusable = false
                }, LinearLayout.LayoutParams(dp(44), dp(32)).apply { rightMargin = dp(10) })
                addView(LinearLayout(this@M3uActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(10), dp(8), dp(10))
                    addView(TextView(this@M3uActivity).apply {
                        setTextColor(txtColor)
                        textSize = 16f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(TextView(this@M3uActivity).apply {
                        setTextColor(mutedColor)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            val c = shown[position]
            val star = v.getChildAt(0) as TextView
            val logo = v.getChildAt(1) as ImageView
            val box = v.getChildAt(2) as LinearLayout
            val on = favs.contains(c.url)
            star.text = if (on) "★" else "☆"
            star.setTextColor(if (on) accent() else mutedColor)
            star.setOnClickListener { toggleFav(c) }
            logo.visibility = View.VISIBLE
            Logos.load(logo, c.logo)
            if (c.logo.isEmpty()) {   // [PULIDO-3d] sin logo: icono genérico de TV en gris
                logo.setImageResource(R.drawable.ic_tv)
                logo.setColorFilter(mutedColor)
            } else {
                logo.clearColorFilter()
            }
            styleLogo(logo, c.logo)   // [VISTA-1] [VISTA-5]
            // [PULIDO-3b/3c] nombre limpio; categorías en español y etiquetas en la línea gris
            val (shownName, badges) = splitName(c.name)
            val cats = if (c.group.isBlank()) "" else groupKeys(c).joinToString(" · ") { groupLabel(it) }
            (box.getChildAt(0) as TextView).text = (if (failed.containsKey(c.url)) "⚠ " else "") + shownName   // [FUENTES-1]
            (box.getChildAt(1) as TextView).text = Epg.nowLine(c)   // [EPG-1] con guía: «▶ programa · hasta HH:MM»; si no, categorías y etiquetas
                ?: listOf(cats, badges).filter { it.isNotEmpty() }.joinToString(" · ")
            return v
        }
    }
}

// [ESTILO-3] Ánimos: cada uno agrupa categorías (en minúsculas y sin acentos, en inglés de iptv-org y en español).
private class Mood(val label: String, val keys: Set<String>)

private val MOODS = listOf(
    Mood("😴 Relajarme", setOf("relax", "lifestyle", "travel", "outdoor", "relajacion", "estilo de vida", "viajes", "aire libre")),
    Mood("🎭 Entretenimiento", setOf("comedy", "entertainment", "comedia", "entretenimiento")),
    Mood("🧠 Aprender", setOf("documentary", "education", "science", "culture", "documentales", "documental", "educacion", "ciencia", "cultura")),
    Mood("👨‍👩‍👧 Familia", setOf("kids", "family", "animation", "infantil", "familia", "animacion")),
    Mood("🎬 Cine y series", setOf("movies", "series", "classic", "peliculas", "pelicula", "cine", "clasicos")),
    Mood("⚽ Deportes", setOf("sports", "sport", "deportes", "deporte")),
    Mood("📰 Informarme", setOf("news", "weather", "business", "legislative", "noticias", "clima", "negocios", "legislativo")),
    Mood("🎵 Música", setOf("music", "musica"))
)

// [PULIDO-3b] Categorías de iptv-org en español (clave en minúsculas); las que no estén salen tal cual.
private val GROUP_ES = mapOf(
    "animation" to "Animación", "auto" to "Autos", "business" to "Negocios", "classic" to "Clásicos",
    "comedy" to "Comedia", "cooking" to "Cocina", "culture" to "Cultura", "documentary" to "Documentales",
    "education" to "Educación", "entertainment" to "Entretenimiento", "family" to "Familia",
    "general" to "General", "kids" to "Infantil", "legislative" to "Legislativo",
    "lifestyle" to "Estilo de vida", "movies" to "Películas", "music" to "Música", "news" to "Noticias",
    "outdoor" to "Aire libre", "relax" to "Relax", "religious" to "Religión", "science" to "Ciencia",
    "series" to "Series", "shop" to "Compras", "sports" to "Deportes", "travel" to "Viajes",
    "weather" to "Clima", "undefined" to "Sin grupo"
)

// [PULIDO-3c] "[algo]" (cualquiera) o "(720p)" / "(1080i)" al nombre del canal.
private val TAG_RE = Regex("""\s*(?:\[([^\]]+)]|\((\d{3,4}[pi])\))""")
