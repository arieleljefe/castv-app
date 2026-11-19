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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.Normalizer
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

    private var txtColor = Color.BLACK
    private var mutedColor = Color.GRAY
    private var current: M3uList? = null
    private var channels: List<M3uChannel> = emptyList()
    private var normNames: List<String> = emptyList()
    private var shown: List<M3uChannel> = emptyList()
    private var groupFilter: String? = null
    private var loadGen = 0
    private var fromCache = false
    private var favs: Set<String> = emptySet()
    private var favOnly = false
    private lateinit var favBtn: TextView
    private val reqPlayer = 4715

    private val freeTvUrl = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

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
        Store.init(this)
        favs = Store.chFavs()
        txtColor = ContextCompat.getColor(this, R.color.vlc_text)
        mutedColor = ContextCompat.getColor(this, R.color.vlc_muted)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.vlc_bg))
        root.setPadding(dp(12), dp(16), dp(12), 0)

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

        statusView = TextView(this)
        statusView.setTextColor(mutedColor)
        statusView.textSize = 13f
        statusView.setPadding(dp(4), dp(8), dp(4), dp(4))
        root.addView(statusView)

        chAdapter = ChannelAdapter()
        channelList = ListView(this)
        channelList.adapter = chAdapter
        channelList.divider = null
        channelList.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) openChannel(shown[pos])
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
        current = l
        Store.lastM3u = l.url
        listBtn.text = "📡 ${l.name} ▾"
        statusView.text = "Descargando lista…"
        channels = emptyList()
        normNames = emptyList()
        shown = emptyList()
        groupFilter = null
        groupBtn.text = "Grupo: Todos ▾"
        chAdapter.notifyDataSetChanged()
        val gen = ++loadGen
        val cache = File(cacheDir, "m3u_" + l.url.hashCode() + ".txt")
        thread {
            var text: String? = null
            var offline = false
            try {
                val t = M3uParser.download(l.url)
                text = t
                try { cache.writeText(t) } catch (e: Exception) { }
            } catch (e: Exception) {
                DebugLog.add("M3U: no se pudo descargar ${l.url}: ${e.javaClass.simpleName}: ${e.message}")
                if (cache.exists()) {
                    try { text = cache.readText(); offline = true } catch (x: Exception) { }
                }
            }
            val body = text
            val parsed = if (body != null) M3uParser.parse(body) else emptyList()
            val list = parsed.distinctBy { it.url }   // [CATALOGO-1] sin enlaces repetidos
            if (list.size != parsed.size) DebugLog.add("M3U: ${l.name}: ${parsed.size - list.size} repetidos quitados")
            val names = list.map { norm(it.name) }
            runOnUiThread {
                if (isFinishing || gen != loadGen) return@runOnUiThread
                channels = list
                normNames = names
                fromCache = offline
                applyFilter()
                if (body == null) statusView.text = "❌ No se pudo descargar la lista. Revisa el enlace y tu conexión."
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
                else showListsOf(groups[which].second, groups[which].first, true)
            }
            .setNeutralButton("＋ Nueva") { _, _ -> addListDialog() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** [REGIONES-1a] Lista de listas; con "← Regiones" vuelve al nivel 1. */
    private fun showListsOf(lists: List<M3uList>, title: String, canGoBack: Boolean) {
        val b = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(lists.map { it.name }.toTypedArray()) { _, which -> loadList(lists[which]) }
            .setNegativeButton("Cerrar", null)
        if (canGoBack) b.setNeutralButton("← Regiones") { _, _ -> chooseList() }
        else b.setNeutralButton("＋ Nueva") { _, _ -> addListDialog() }
        b.show()
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
        val cur = current ?: return
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
        val out = ArrayList<M3uChannel>()
        for (i in channels.indices) {
            val c = channels[i]
            if (g != null && c.group != g) continue
            if (favOnly && !favs.contains(c.url)) continue
            val n = normNames[i]
            if (toks.all { n.contains(it) }) out.add(c)
        }
        // Favoritos primero (el orden es estable: el resto conserva su orden)
        shown = if (favs.isEmpty()) out else out.sortedByDescending { favs.contains(it.url) }
        chAdapter.notifyDataSetChanged()
        if (channels.isNotEmpty()) {
            statusView.text = "${shown.size} de ${channels.size} canales" +
                (if (fromCache) " · sin conexión (copia guardada)" else "")
        }
    }

    private fun pickGroup() {
        val counts = channels.groupingBy { it.group }.eachCount()
        val groups = counts.keys.sorted()
        if (groups.size <= 1) {
            toast("Esta lista no tiene grupos")
            return
        }
        val labels = ArrayList<String>()
        labels.add("Todos (${channels.size})")
        for (g in groups) labels.add("$g (${counts[g]})")
        AlertDialog.Builder(this)
            .setTitle("Filtrar por grupo")
            .setItems(labels.toTypedArray()) { _, which ->
                groupFilter = if (which == 0) null else groups[which - 1]
                groupBtn.text = "Grupo: " + (groupFilter ?: "Todos") + " ▾"
                applyFilter()
            }
            .show()
    }

    // ------------------------------------------------------------ Canales

    private fun toggleFavOnly() {
        favOnly = !favOnly
        favBtn.text = if (favOnly) "★ Favs" else "☆ Favs"
        applyFilter()
    }

    private fun toggleFav(c: M3uChannel) {
        Store.toggleChFav(c.url)
        favs = Store.chFavs()
        if (favOnly) applyFilter() else chAdapter.notifyDataSetChanged()
    }

    private fun isHls(url: String) = url.substringBefore('?').lowercase().endsWith(".m3u8")

    private fun openChannel(c: M3uChannel) {
        val labels = arrayOf(
            if (isHls(c.url)) "📺 Al TV (HLS: puede no funcionar)" else "📺 Al TV",
            "📱 En el móvil")
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(labels) { _, which -> playChannel(c, which == 0) }
            .show()
    }

    /** Reproduce el canal y esta pantalla se queda abierta para cambiar de canal. */
    private fun playChannel(c: M3uChannel, toTv: Boolean) {
        Store.addLink(c.url, c.name)   // queda en el 🕘 Historial
        if (toTv) sendToTv(c.url, c.name) else playOnPhone(c.url, c.name)
    }

    private fun playOnPhone(url: String, title: String) {
        val i = Intent(this, VideoPlayerActivity::class.java)
            .putExtra("url", url).putExtra("urlTitle", title)
        @Suppress("DEPRECATION")
        startActivityForResult(i, reqPlayer)
    }

    private fun sendToTv(url: String, title: String) {
        toast("🚀 Enviando al TV…")
        thread {
            if (TvController.tvIp == null) {
                runOnUiThread { toast("⚠️ TV no encontrado todavía. Espera unos segundos o usa ⋮ › Elegir TV.") }
                return@thread
            }
            sendBroadcast(Intent(MainActivity.ACT_STOP_LOCAL).setPackage(packageName))   // [LOCAL-1] detiene lo local del teléfono
            val hls = isHls(url)
            val phoneIp = CastvNet.localIpAddress(TvController.tvIp)
            val live = hls && phoneIp != null
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
            val box = v.getChildAt(1) as LinearLayout
            val on = favs.contains(c.url)
            star.text = if (on) "★" else "☆"
            star.setTextColor(if (on) accent() else mutedColor)
            star.setOnClickListener { toggleFav(c) }
            (box.getChildAt(0) as TextView).text = c.name
            (box.getChildAt(1) as TextView).text = c.group
            return v
        }
    }
}
