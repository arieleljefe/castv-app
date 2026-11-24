package com.castv.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
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
    private val reqPlayer = 4715
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
    private fun styleLogo(logo: ImageView, hasLogo: Boolean) {
        if (hasLogo) {
            logo.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2F2F2"))
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
        txtColor = ContextCompat.getColor(this, R.color.vlc_text)
        mutedColor = ContextCompat.getColor(this, R.color.vlc_muted)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.vlc_bg))
        root.setPadding(dp(12), dp(16), dp(12), 0)

        // [PULIDO-3e] Encabezado: ← Canales (igual que Ajustes)
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(0, 0, 0, dp(8))
        val back = TextView(this)
        back.text = "←"
        back.textSize = 26f
        back.setTextColor(txtColor)
        back.gravity = Gravity.CENTER
        back.contentDescription = "Atrás"
        back.setPadding(dp(8), dp(4), dp(16), dp(4))
        back.setOnClickListener { finish() }
        header.addView(back)
        val headerTitle = TextView(this)
        headerTitle.text = "Canales"
        headerTitle.textSize = 22f
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        headerTitle.setTextColor(txtColor)
        header.addView(headerTitle)
        root.addView(header)

        // Fila 1: lista actual, añadir, quitar
        val row1 = LinearLayout(this)
        row1.gravity = Gravity.CENTER_VERTICAL
        listBtn = chip("📡 Sin lista ▾") { chooseList() }
        listBtn.maxLines = 1
        listBtn.ellipsize = TextUtils.TruncateAt.END
        (listBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            weight = 1f
            leftMargin = 0
        }
        row1.addView(listBtn)
        favBtn = chip("☆ Favs") { toggleFavOnly() }
        row1.addView(favBtn)
        hiddenBtn = chip("👁") { showHiddenDialog() }   // [OCULTAR-1]
        row1.addView(hiddenBtn)
        updateHiddenBtn()
        row1.addView(chip("＋") { addListDialog() })
        row1.addView(chip("🗑") { removeCurrent() })
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
        moodScroll.addView(moodRow)
        root.addView(moodScroll)

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
        statusRow.addView(checkBtn)
        root.addView(statusRow)

        chAdapter = ChannelAdapter()
        channelList = ListView(this)
        sections = LinearLayout(this)   // [ESTILO-2] se llena en refreshSections()
        sections.orientation = LinearLayout.VERTICAL
        channelList.addHeaderView(sections, null, false)   // antes del adapter; la cabecera desplaza los índices
        channelList.adapter = chAdapter
        channelList.divider = null
        channelList.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - channelList.headerViewsCount   // [ESTILO-2]
            if (i in shown.indices) openChannel(shown[i])
        }
        channelList.setOnItemLongClickListener { _, _, pos, _ ->   // [OCULTAR-1] mantener pulsado el canal = quitarlo
            val i = pos - channelList.headerViewsCount   // [ESTILO-2]
            if (i in shown.indices) confirmHide(shown[i])
            true
        }
        root.addView(
            channelList,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val added = Store.installCatalog(Catalog.VERSION, Catalog.items)   // [CATALOGO-1] listas de fábrica, una sola vez
        if (added > 0) toast("📡 Se añadieron $added listas de canales")
        val saved = Store.m3uLists()
        val last = Store.lastM3u
        val start = saved.firstOrNull { it.url == last } ?: saved.firstOrNull()
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
        val gen = ++loadGen
        val cache = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
        thread {
            var text: String? = null
            var offline = false
            var bad = false                          // [LISTA-VAL-1] la descarga llegó vacía o dañada
            var pre: List<M3uChannel>? = null        // [LISTA-VAL-1] ya parseada (no se parsea dos veces)
            try {
                val t = M3uParser.download(l.url)
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
        AlertDialog.Builder(this)
            .setTitle("Regiones y categorías")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == groups.size) showListsOf(saved, "Todas mis listas", true)
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
                synchronized(results) { results[idx] = parsed }
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
            }
        }
        pool.shutdown()   // deja terminar lo ya pedido y no acepta más
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
        AlertDialog.Builder(this)
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
                try { File(cacheDir, "m3u_" + cur.url.hashCode() + ".txt").delete() } catch (e: Exception) { }
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

    override fun onResume() {
        super.onResume()
        refreshSections()   // al volver del reproductor, "Continuar" ya trae el canal visto
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
        addSection("⭐ Favoritos", favList)
    }

    private fun addSection(title: String, list: List<M3uChannel>) {
        if (list.isEmpty()) return
        val t = TextView(this)
        t.text = title
        t.textSize = 14f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(txtColor)
        t.setPadding(dp(4), dp(6), dp(4), dp(2))   // [VISTA-2]
        sections.addView(t)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (c in list) row.addView(card(c), LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        sections.addView(scroll)
    }

    /** Tarjeta pequeña: logo y nombre. Tocar = mismo menú 📺 / 📱; mantener pulsado = quitar (como en la lista). */
    private fun card(c: M3uChannel): View {
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
        styleLogo(logo, c.logo.isNotEmpty())   // [VISTA-1]
        box.addView(logo, LinearLayout.LayoutParams(dp(64), dp(36)))   // [VISTA-2]
        val name = TextView(this)
        name.text = splitName(c.name).first
        name.setTextColor(txtColor)
        name.textSize = 11f   // [VISTA-2] tarjeta más ancha y letra algo menor: caben más nombres completos
        name.gravity = Gravity.CENTER_HORIZONTAL
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        box.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        hiddenBtn.text = "👁 $n"
        hiddenBtn.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    /** Siempre se pide confirmación antes de quitar un canal. */
    private fun confirmHide(c: M3uChannel) {
        val isFav = favs.contains(c.url)
        AlertDialog.Builder(this)
            .setTitle("¿Quitar este canal?")
            .setMessage("«${c.name}» dejará de salir en tus listas, aunque la lista se vuelva a descargar." +
                (if (isFav) "\n\nTambién se quitará de tus favoritos ★." else "") +
                "\n\nPodrás devolverlo con el botón 👁.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Quitar") { _, _ -> hideChannel(c) }
            .show()
    }

    private fun hideChannel(c: M3uChannel) {
        Store.hideChannel(c)          // también le quita la ★ si la tenía
        hidden = Store.hiddenUrls()
        favs = Store.chFavs()
        if (favOnly) reloadFavChannels()
        applyFilter()
        updateHiddenBtn()
        toast("🗑 Canal quitado (👁 para restaurar)")
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
        val pool = Executors.newFixedThreadPool(8)
        checkPool = pool
        checkBtn.text = "⏹ 0/$total"
        statusView.text = "🔎 Revisando $total canales…"
        val listName = listBtn.text.toString()
        for (c in list) {
            pool.execute {
                if (gen != checkGen) return@execute
                val why = MediaServer.checkChannel(c.url)
                if (gen != checkGen) return@execute
                if (why != null) fails.add(CheckFail(c, why))
                val n = done.incrementAndGet()
                if (n == total) {
                    runOnUiThread { if (!isFinishing && gen == checkGen) finishCheck(total, fails.toList(), listName) }
                } else if (n % 5 == 0) {
                    runOnUiThread {
                        if (!isFinishing && gen == checkGen) {
                            checkBtn.text = "⏹ $n/$total"
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
        checkBtn.text = "🔎 Revisar"
        if (msg != null) {
            toast(msg)
            applyFilter()
        }
    }

    private fun finishCheck(total: Int, fails: List<CheckFail>, listName: String) {
        checkPool = null
        checking = false
        checkBtn.text = "🔎 Revisar"
        DebugLog.add("CHECK-1: $listName: $total revisados, ${fails.size} sin respuesta")
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
        checkPool?.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------ Canales

    private fun toggleFavOnly() {
        favOnly = !favOnly
        favBtn.text = if (favOnly) "★ Favs" else "☆ Favs"
        if (favOnly) reloadFavChannels()   // [FAV-1]
        applyFilter()
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
        val labels = arrayOf(
            if (isHls(c.url)) "📺 Al TV (HLS: puede no funcionar)" else "📺 Al TV",
            "📱 En el teléfono")
        AlertDialog.Builder(this)
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
            if (ok) sendBroadcast(Intent(MainActivity.ACT_CHANNEL_ON_TV).setPackage(packageName).putExtra("title", title).putExtra("live", live))   // [NOW-1] la barra y la notificación muestran el canal
            runOnUiThread {
                toast(if (ok) "✅ Canal en el TV" else "❌ El TV no aceptó el canal")
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
            styleLogo(logo, c.logo.isNotEmpty())   // [VISTA-1]
            // [PULIDO-3b/3c] nombre limpio; categorías en español y etiquetas en la línea gris
            val (shownName, badges) = splitName(c.name)
            val cats = if (c.group.isBlank()) "" else groupKeys(c).joinToString(" · ") { groupLabel(it) }
            (box.getChildAt(0) as TextView).text = shownName
            (box.getChildAt(1) as TextView).text =
                listOf(cats, badges).filter { it.isNotEmpty() }.joinToString(" · ")
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
