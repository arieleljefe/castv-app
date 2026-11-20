package com.castv.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * [ACERCA-1] Pantalla "Acerca de CASTV": cabecera con icono y descripción, tarjeta de versión,
 * filas con icono propio (código fuente, comentarios, librerías, autores, redes) y tarjeta final.
 * Armada en código, como SettingsActivity. Solo usa recursos y textos propios de CASTV.
 */
class AboutActivity : AppCompatActivity() {

    private var txtColor = Color.BLACK
    private var mutedColor = Color.GRAY
    private var lineColor = Color.GRAY

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun accent(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.castvAccent, tv, true)
        return tv.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(when (getSharedPreferences("castv", MODE_PRIVATE).getInt("accent", 0)) {
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

        // Encabezado: ← Acerca de
        val header = LinearLayout(this)
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
        title.text = "Acerca de"
        title.textSize = 22f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setTextColor(txtColor)
        header.addView(title)
        root.addView(header)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), 0, dp(16), dp(24))

        // 1) Cabecera: icono, nombre y descripción
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        top.setPadding(dp(8), dp(12), dp(8), dp(20))
        val icon = ImageView(this)
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(packageName))
        } catch (e: Exception) {
        }
        top.addView(icon, LinearLayout.LayoutParams(dp(96), dp(96)))
        val name = TextView(this)
        name.text = "CASTV"
        name.textSize = 26f
        name.setTypeface(null, android.graphics.Typeface.BOLD)
        name.setTextColor(txtColor)
        name.gravity = Gravity.CENTER   // [ACERCA-2] centrado bajo el icono
        name.setPadding(0, dp(10), 0, dp(4))
        top.addView(name)
        val desc = TextView(this)
        desc.text = "Reproduce los videos y la música de tu teléfono y los manda a tu TV por la red Wi-Fi. " +
            "También abre canales en vivo de listas M3U y enlaces de video."
        desc.textSize = 14f
        desc.gravity = Gravity.CENTER
        desc.setTextColor(mutedColor)
        top.addView(desc)
        content.addView(top)

        // 2) Tarjeta de versión
        val verCard = card()
        verCard.addView(text("Versión", 13f, accent(), bold = true))
        verCard.addView(text("CASTV " + BuildConfig.VERSION_NAME, 17f, txtColor, bold = true, top = 2))
        verCard.addView(text("Compilada el " + BuildConfig.BUILD_DATE, 13f, mutedColor, top = 2))
        content.addView(verCard, cardLp())

        // 3) Filas con icono
        val rows = card()
        rows.setPadding(0, dp(4), 0, dp(4))
        addRow(rows, R.drawable.ic_about_code, "Código fuente", "github.com/arieleljefe/castv-app") {
            openUrl("https://github.com/arieleljefe/castv-app")
        }
        addRow(rows, R.drawable.ic_about_chat, "Enviar comentarios", "Escríbele a Ariel por WhatsApp") {
            openUrl("https://wa.me/18094480441?text=" + Uri.encode("Hola Ariel, te escribo desde la app CASTV: "))
        }
        addRow(rows, R.drawable.ic_about_book, "Librerías", "Las que usa CASTV y su licencia") { showLibs() }
        addRow(rows, R.drawable.ic_about_person, "Autores", "Ariel De Jesus Bueno", null)
        content.addView(rows, cardLp())

        // [REDES-1] Tarjeta "Sígueme": una fila por red, con icono propio sobre círculo de color
        val soc = card()
        soc.setPadding(0, dp(12), 0, dp(4))
        val socTitle = text("Sígueme", 13f, accent(), bold = true)
        socTitle.setPadding(dp(16), 0, dp(16), dp(4))
        soc.addView(socTitle)
        addSocial(soc, R.drawable.ic_social_facebook, 0xFF1877F2.toInt(), "Facebook", "Perfil de Ariel") {
            openUrl(MainActivity.FB_URL)
        }
        addSocial(soc, R.drawable.ic_social_instagram, 0xFFC13584.toInt(), "Instagram", "Perfil de Ariel") {
            openUrl(MainActivity.IG_URL)
        }
        addSocial(soc, R.drawable.ic_social_tiktok, 0xFF111111.toInt(), "TikTok", "Videos de Ariel") {
            openUrl(MainActivity.TT_URL)
        }
        addSocial(soc, R.drawable.ic_social_whatsapp, 0xFF25D366.toInt(), "WhatsApp", "+1 (809) 448-0441") {
            openUrl("https://wa.me/18094480441?text=" +
                Uri.encode("Hola Ariel, vi tu app CASTV y está excelente!"))
        }
        content.addView(soc, cardLp())

        // 4) Tarjeta final
        val end = card()
        end.addView(text("© 2026 Ariel De Jesus Bueno", 14f, txtColor, bold = true))
        end.addView(text("Todos los derechos reservados.", 13f, mutedColor, top = 2))
        content.addView(end, cardLp())

        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val bg = GradientDrawable()
        bg.setColor(ContextCompat.getColor(this, R.color.vlc_surface))
        bg.cornerRadius = dp(14).toFloat()
        bg.setStroke(dp(1), lineColor)
        c.background = bg
        return c
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        return lp
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setPadding(0, dp(top), 0, 0)
        return tv
    }

    private fun addRow(parent: LinearLayout, iconRes: Int, title: String, subtitle: String, onClick: (() -> Unit)?) {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(12), dp(16), dp(12))
        if (onClick != null) {
            val sel = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
            row.setBackgroundResource(sel.resourceId)
            row.setOnClickListener { onClick() }
        }
        val ic = ImageView(this)
        ic.setImageResource(iconRes)
        ic.setColorFilter(accent())
        row.addView(ic, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(text(title, 16f, txtColor))
        box.addView(text(subtitle, 13f, mutedColor, top = 2))
        row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row)
    }

    /** [REDES-1] Fila de red social: icono blanco sobre círculo de color, nombre, texto gris y "›". */
    private fun addSocial(parent: LinearLayout, iconRes: Int, color: Int, title: String, subtitle: String, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        val sel = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
        row.setBackgroundResource(sel.resourceId)
        row.setOnClickListener { onClick() }
        val ic = ImageView(this)
        ic.setImageResource(iconRes)
        ic.setPadding(dp(9), dp(9), dp(9), dp(9))
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        ic.background = bg
        row.addView(ic, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(16) })
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(text(title, 16f, txtColor))
        box.addView(text(subtitle, 13f, mutedColor, top = 2))
        row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 24f, accent()))
        parent.addView(row)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLibs() {
        AlertDialog.Builder(this)
            .setTitle("Librerías")
            .setMessage(
                "AndroidX Core KTX · Apache-2.0\n" +
                "AndroidX Media · Apache-2.0\n" +
                "AndroidX AppCompat · Apache-2.0\n" +
                "AndroidX ConstraintLayout · Apache-2.0\n" +
                "Material Components for Android · Apache-2.0\n" +
                "NanoHTTPD 2.3.1 · BSD de 3 cláusulas")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showSocials() {
        val items = arrayOf("Facebook", "Instagram", "TikTok", "WhatsApp: +1 (809) 448-0441")
        AlertDialog.Builder(this)
            .setTitle("Creado por Ariel De Jesus Bueno")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openUrl(MainActivity.FB_URL)
                    1 -> openUrl(MainActivity.IG_URL)
                    2 -> openUrl(MainActivity.TT_URL)
                    3 -> openUrl("https://wa.me/18094480441?text=" +
                        Uri.encode("Hola Ariel, vi tu app CASTV y está excelente!"))
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
