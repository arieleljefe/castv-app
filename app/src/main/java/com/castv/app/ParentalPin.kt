package com.castv.app

import android.app.Activity
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * [PARENTAL-2] PIN compartido con Ajustes (mismo hash: castv › pin_hash).
 * Ajustes decide qué protege el PIN: borrar archivos (pin_delete, Sí por defecto) y
 * abrir Canales (pin_channels, No por defecto).
 */
object ParentalPin {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("castv", Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = !prefs(ctx).getString("pin_hash", "").isNullOrEmpty()

    fun guardsDelete(ctx: Context): Boolean = isSet(ctx) && prefs(ctx).getBoolean("pin_delete", true)

    fun guardsChannels(ctx: Context): Boolean = isSet(ctx) && prefs(ctx).getBoolean("pin_channels", false)

    private fun hash(pin: String): String {
        val b = java.security.MessageDigest.getInstance("SHA-256").digest(("castv:" + pin).toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Pide el PIN y, si es correcto, ejecuta onOk. Cancelar no hace nada. */
    fun ask(activity: Activity, title: String, onOk: () -> Unit) {
        if (activity.isFinishing) return
        val et = EditText(activity)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.filters = arrayOf(InputFilter.LengthFilter(6))
        et.hint = "PIN"
        et.setSingleLine(true)
        val d = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("Escribe tu PIN")
            .setView(et)
            .setPositiveButton("Entrar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hash(et.text.toString()) == prefs(activity).getString("pin_hash", "")) {
                    d.dismiss()
                    onOk()
                } else {
                    et.setText("")
                    Toast.makeText(activity, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                }
            }
        }
        d.show()
    }
}
