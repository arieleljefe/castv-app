package com.castv.app

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/** [Etapa 14-C] Aviso de puerto ocupado, sacado de MainActivity. Mismo texto y mismo comportamiento. */
object PortDialog {
    /** Si el servidor interno no pudo abrir el puerto 8000, lo dice claramente. */
    fun showIfBusy(activity: Activity) {
        val err = ServerState.error ?: return
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle("⚠️ Puerto 8000 ocupado")
            .setMessage(
                "Otro programa está usando el puerto 8000, así que el TV no podrá leer tus archivos.\n\n" +
                "Si usas Termux, ciérralo con:\npkill -f castv_app.py\n\n" +
                "Luego fuerza la detención de CASTV y ábrelo de nuevo.\n\n($err)"
            )
            .setPositiveButton("Entendido", null)
            .show()
    }
}
