package com.castv.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * [MP3-ABRIR-1] Aviso de «Guardado en Descargas» con Abrir y Compartir. Solo hay botones si Android dio el archivo
 * (Android 10+, MediaStore); si no, queda el aviso de siempre. No guarda ni escribe en el /log el nombre ni el enlace.
 */
object SavedFile {
    fun offer(act: Activity, icon: String, name: String, uri: Uri?) {
        if (uri == null || act.isFinishing || act.isDestroyed) {
            Toast.makeText(act.applicationContext, "$icon Guardado en Descargas: $name", Toast.LENGTH_LONG).show()
            return
        }
        val mime = try { act.contentResolver.getType(uri) } catch (e: Exception) { null } ?: "*/*"
        AlertDialog.Builder(act)
            .setTitle("$icon Guardado en Descargas")
            .setMessage(name)
            .setPositiveButton("Abrir") { _, _ ->
                try {
                    act.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(act.applicationContext, "No hay una app para abrir este archivo", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { }
            }
            .setNeutralButton("Compartir") { _, _ ->
                try {
                    val send = Intent(Intent.ACTION_SEND).setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    act.startActivity(Intent.createChooser(send, "Compartir"))
                } catch (e: Exception) { }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
