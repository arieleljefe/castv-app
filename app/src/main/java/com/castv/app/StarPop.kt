package com.castv.app

import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * [ANIM-1] «Latido» de la ★ al marcar un favorito: crece un poco y vuelve con un rebote suave.
 * Respeta «Quitar animaciones» de Android (escala de animación en 0 = no se anima nada).
 */
object StarPop {
    fun pop(v: View) {
        try {
            val scale = Settings.Global.getFloat(v.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            if (scale == 0f) return
        } catch (e: Exception) { }
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.animate().scaleX(1.45f).scaleY(1.45f).setDuration(120).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }
}
