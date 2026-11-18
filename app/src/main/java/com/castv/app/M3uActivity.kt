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
 * Al tocar un canal se devuelve a MainActivity, que lo reproduce con su "Reproducir enlace".
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
            val list = if (body != null) M3uParser.parse(body) else emptyList()
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
        AlertDialog.Builder(this)
            .setTitle("Mis listas M3U")
            .setItems(saved.map { it.name }.toTypedArray()) { _, which -> loadList(saved[which]) }
            .setNeutralButton("＋ Nueva") { _, _ -> addListDialog() }
            .setNegativeButton("Cerrar", null)
            .show()
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
            val n = normNames[i]
            if (toks.all { n.contains(it) }) out.add(c)
        }
        shown = out
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

    private fun openChannel(c: M3uChannel) {
        val hls = c.url.substringBefore('?').lowercase().endsWith(".m3u8")
        val labels = arrayOf(
            if (hls) "📺 Al TV (HLS: puede no funcionar)" else "📺 Al TV",
            "📱 En el móvil")
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(labels) { _, which -> finishWith(c, which == 0) }
            .show()
    }

    private fun finishWith(c: M3uChannel, toTv: Boolean) {
        setResult(
            RESULT_OK,
            Intent().putExtra("m3uUrl", c.url).putExtra("m3uTitle", c.name).putExtra("m3uTo", toTv))
        finish()
    }

    private inner class ChannelAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = (convertView as? LinearLayout) ?: LinearLayout(this@M3uActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
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
            }
            val c = shown[position]
            (v.getChildAt(0) as TextView).text = c.name
            (v.getChildAt(1) as TextView).text = c.group
            return v
        }
    }
}
