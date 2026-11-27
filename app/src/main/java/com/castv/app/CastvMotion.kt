package com.castv.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap

/**
 * [ANIM-1b] Animaciones sutiles: el panel del mini reproductor se despliega deslizando y las tarjetas aparecen con fundido.
 * Todo respeta «Quitar animaciones» de Android (escala 0 = cambio inmediato, como antes). Solo es estético: no toca la reproducción.
 */
object CastvMotion {
    private val anims = WeakHashMap<View, ValueAnimator>()
    private val wanted = WeakHashMap<View, Boolean>()
    private val origH = WeakHashMap<View, Int>()

    /** Escala de animaciones del sistema (1 = normal, 0 = apagadas). */
    fun scale(ctx: Context): Float = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) { 1f }

    /** ¿Está abierto (o en camino de abrirse)? Sirve para que un segundo toque durante la animación haga lo correcto. */
    fun isOpen(v: View): Boolean = wanted[v] ?: (v.visibility == View.VISIBLE)

    /** Muestra u oculta [v] deslizando su altura y con fundido. Sin animación si no se puede medir o si Android la tiene apagada. */
    fun slide(v: View, show: Boolean) {
        anims.remove(v)?.cancel()
        wanted[v] = show
        val lp = v.layoutParams
        val parent = v.parent as? View
        val s = scale(v.context)
        val orig = origH.getOrPut(v) { lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT }
        fun finish() {
            v.alpha = 1f
            if (lp != null) { lp.height = orig; v.layoutParams = lp }
            v.visibility = if (show) View.VISIBLE else View.GONE
        }
        if (s <= 0f || lp == null || parent == null || parent.width <= 0 || !v.isAttachedToWindow || !parent.isShown) { finish(); return }
        val from: Int
        val to: Int
        if (show) {
            v.measure(
                View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            to = if (orig >= 0) orig else v.measuredHeight
            from = if (v.visibility == View.VISIBLE) v.height else 0
        } else {
            to = 0
            from = if (v.visibility == View.VISIBLE) v.height else 0
        }
        if (to <= 0 && show || from == to) { finish(); return }
        if (show) { lp.height = from; v.layoutParams = lp; v.visibility = View.VISIBLE }
        val a0 = v.alpha
        val a1 = if (show) 1f else 0f
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = (220 * s).toLong()
        va.addUpdateListener {
            val f = it.animatedValue as Float
            lp.height = (from + (to - from) * f).toInt()
            v.layoutParams = lp
            v.alpha = a0 + (a1 - a0) * f
        }
        va.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                anims.remove(v)
                finish()
            }
        })
        anims[v] = va
        va.start()
    }

    /** Gira [v] (la flecha ˄) hasta [deg] grados. */
    fun rotate(v: View, deg: Float) {
        val s = scale(v.context)
        if (s <= 0f || !v.isAttachedToWindow) { v.rotation = deg; return }
        v.animate().rotation(deg).setDuration((200 * s).toLong()).start()
    }

    /** Aparece con fundido; las primeras tarjetas entran una tras otra ([index]). */
    fun fadeIn(v: View, index: Int) {
        val s = scale(v.context)
        v.animate().cancel()
        if (s <= 0f) { v.alpha = 1f; return }
        v.alpha = 0f
        v.animate().alpha(1f)
            .setStartDelay((index.coerceIn(0, 8) * 30 * s).toLong())
            .setDuration((220 * s).toLong())
            .start()
    }

    /** Para vistas recicladas: quita un fundido a medias. */
    fun reset(v: View) {
        v.animate().cancel()
        v.alpha = 1f
    }
}
