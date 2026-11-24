package com.castv.app

import android.content.Context
import java.io.File

/**
 * [AUTORESPALDO-1] Copia automática diaria de los datos del usuario.
 *
 * Al abrir la app se guarda `castv_auto_AAAA-MM-DD.json` en la carpeta interna de la app
 * (`filesDir/autobackup`; nada va a Download), como mucho una vez al día, y se conservan las últimas 3.
 * Usa el mismo formato que el respaldo completo (`Backup.build`), así que se restaura con `Backup.restore`.
 *
 * Seguro: si la copia nueva pesa menos del 30 % de la más reciente (señal de datos vacíos o dañados)
 * NO se guarda y se avisa para el /log.
 */
object AutoBackup {
    private const val DIR = "autobackup"
    private const val KEEP = 3
    private const val MIN_RATIO = 0.30
    private val NAME = Regex("""castv_auto_\d{4}-\d{2}-\d{2}\.json""")

    /** Una copia guardada: archivo, día (AAAA-MM-DD) y tamaño en bytes. */
    data class Copy(val file: File, val day: String, val size: Long) {
        val kb: Long get() = maxOf(1L, size / 1024)
    }

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR)

    /** Copias guardadas, la más reciente primero. */
    fun list(ctx: Context): List<Copy> {
        val files = dir(ctx).listFiles() ?: return emptyList()
        return files.filter { it.isFile && NAME.matches(it.name) }
            .sortedByDescending { it.name }
            .map { Copy(it, it.name.removePrefix("castv_auto_").removeSuffix(".json"), it.length()) }
    }

    /**
     * Guarda la copia de hoy si hace falta. Devuelve un texto para el /log cuando pasa algo que valga
     * la pena anotar (copia hecha o descartada), o null si no hubo nada que hacer.
     */
    fun runIfNeeded(ctx: Context): String? {
        val d = dir(ctx)
        if (!d.exists() && !d.mkdirs()) return "RESPALDO-AUTO: no se pudo crear la carpeta"
        File(d, "castv_auto.tmp").delete()   // resto de una copia interrumpida

        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val target = File(d, "castv_auto_$day.json")
        if (target.exists()) return null   // ya hay copia de hoy

        val bytes = Backup.build(ctx).toByteArray(Charsets.UTF_8)
        val last = list(ctx).firstOrNull()
        if (last != null && last.size > 0 && bytes.size < last.size * MIN_RATIO) {
            return "RESPALDO-AUTO: copia de $day NO guardada: pesa ${bytes.size} B y la última (${last.day}) ${last.size} B (¿datos vacíos?)"
        }

        val tmp = File(d, "castv_auto.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) { tmp.delete(); return "RESPALDO-AUTO: no se pudo guardar la copia de $day" }

        for (old in list(ctx).drop(KEEP)) old.file.delete()   // quedan las 3 más recientes
        return "RESPALDO-AUTO: copia de $day guardada (${bytes.size} B)"
    }
}
