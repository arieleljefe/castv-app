package com.castv.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * [MP3-ABRIR-1] Aviso de «Guardado en Descargas» con Abrir y Compartir. Solo hay botones si Android dio el archivo
 * (Android 10+, MediaStore); si no, queda el aviso de siempre. No guarda ni escribe en el /log el nombre ni el enlace.
 */
object SavedFile {
    /** [CARPETA-1] ¿El archivo quedó en Descargas? (Tonos, Notificaciones y Alarmas van a otra carpeta: ahí no se ofrece la ubicación.) */
    fun inDownloads(ctx: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        return try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "").startsWith(Environment.DIRECTORY_DOWNLOADS) else true
            } ?: true
        } catch (e: Exception) { true }
    }

    /** [CARPETA-1] Abre la carpeta Descargas en la app de archivos del teléfono. Devuelve false si no hay ninguna que la abra. */
    fun openFolder(ctx: Context): Boolean {
        val flags = if (ctx is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        val tries = listOf(
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS),
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"),
                "vnd.android.document/directory")
        )
        for (i in tries) {
            try { ctx.startActivity(i.addFlags(flags)); return true } catch (e: Exception) { }
        }
        return false
    }

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
            .apply {
                // [CARPETA-1] tercer botón: «📂 Ubicación» (abre Descargas); fuera de Descargas queda «Cerrar»
                if (inDownloads(act, uri)) {
                    setNegativeButton("📂 Ubicación") { _, _ ->
                        if (!openFolder(act)) Toast.makeText(act.applicationContext, "No hay una app de archivos para abrir Descargas", Toast.LENGTH_SHORT).show()
                    }
                } else setNegativeButton("Cerrar", null)
            }
            .show()
    }
}
