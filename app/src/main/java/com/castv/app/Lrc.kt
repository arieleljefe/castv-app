package com.castv.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * [LRC-1] Letras sincronizadas (.lrc) para la pantalla «Reproduciendo».
 * La letra de cada canción se guarda como copia propia en la carpeta interna de la app (`files/lrc/`), elegida por el
 * usuario con el selector de archivos del sistema (no hace falta ningún permiso nuevo). Además, si junto a la canción
 * hay un `.lrc` con el mismo nombre y Android deja leerlo, se usa sin pedir nada. No entra en respaldos.
 */
object Lrc {
    class Line(val ms: Int, val text: String)
    class Doc(val lines: List<Line>)

    private const val MAX_BYTES = 512 * 1024
    private const val MAX_LINES = 3000

    /** Una o más marcas al inicio de la línea: [mm:ss], [mm:ss.xx] o [mm:ss.xxx]; después, el texto. */
    private val LEAD = Regex("""^((?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+)(.*)$""")
    private val STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")   // marcas por palabra («LRC mejorado»): se ignoran
    private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    /** Convierte el texto de un `.lrc` en líneas con su tiempo. Null si no trae ninguna marca de tiempo. */
    fun parse(raw: String): Doc? {
        val out = ArrayList<Line>()
        var offset = 0
        for (r in raw.replace("﻿", "").lineSequence()) {
            val line = r.trim()
            if (line.isEmpty()) continue
            val off = OFFSET.find(line)
            if (off != null) { offset = off.groupValues[1].toIntOrNull() ?: 0; continue }
            val m = LEAD.find(line) ?: continue
            val text = WORD_TAG.replace(m.groupValues[2], "").trim()
            for (s in STAMP.findAll(m.groupValues[1])) {
                val min = s.groupValues[1].toInt()
                val sec = s.groupValues[2].toInt()
                val f = s.groupValues[3]
                val frac = when (f.length) { 0 -> 0; 1 -> f.toInt() * 100; 2 -> f.toInt() * 10; else -> f.toInt() }
                val ms = (min * 60 + sec) * 1000 + frac - offset   // [offset:+500] = la letra sale 0,5 s antes
                out.add(Line(maxOf(ms, 0), text))
                if (out.size >= MAX_LINES) break
            }
            if (out.size >= MAX_LINES) break
        }
        if (out.isEmpty()) return null
        return Doc(out.sortedBy { it.ms })   // sortedBy es estable: dos líneas con el mismo tiempo conservan su orden
    }

    /** Posición (índice) de la línea que toca en [posMs]; -1 si todavía no empieza la primera. */
    fun indexAt(doc: Doc, posMs: Int): Int {
        var lo = 0
        var hi = doc.lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (doc.lines[mid].ms <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** Lee el texto de un archivo (hasta 512 KB). UTF-8; si no lo es, ISO-8859-1. Null si es demasiado grande o falla. */
    fun readText(input: InputStream): String? {
        return try {
            val bytes = input.readBytes()
            if (bytes.size > MAX_BYTES) return null
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (e: CharacterCodingException) {
                String(bytes, Charsets.ISO_8859_1)
            }
        } catch (e: Exception) { null }
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun saved(ctx: Context, item: MediaItem): File =
        File(File(ctx.filesDir, "lrc"), hash(Store.key(item)) + ".lrc")

    /** true si el usuario ya guardó una letra para esta canción (la de «Quitar letra»). */
    fun hasSaved(ctx: Context, item: MediaItem): Boolean = try { saved(ctx, item).isFile } catch (e: Exception) { false }

    /** Guarda [text] como la letra de [item]. true si quedó guardada. */
    fun save(ctx: Context, item: MediaItem, text: String): Boolean {
        return try {
            val f = saved(ctx, item)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
            true
        } catch (e: Exception) { false }
    }

    fun remove(ctx: Context, item: MediaItem) {
        try { saved(ctx, item).delete() } catch (e: Exception) { }
    }

    /** Letra de [item]: primero la guardada; si no hay, un `.lrc` con el mismo nombre junto a la canción (si Android lo deja leer). */
    fun load(ctx: Context, item: MediaItem): Doc? {
        try {
            val f = saved(ctx, item)
            if (f.isFile && f.length() <= MAX_BYTES) {
                val d = parse(f.readText(Charsets.UTF_8))
                if (d != null) return d
            }
        } catch (e: Exception) { }
        try {
            if (item.folder.isEmpty() || item.folder == "(raíz)") return null
            val base = item.name.substringBeforeLast('.')
            val dir = File(Environment.getExternalStorageDirectory(), item.folder)
            for (ext in arrayOf(".lrc", ".LRC")) {
                val f = File(dir, base + ext)
                if (f.isFile && f.canRead() && f.length() <= MAX_BYTES) {
                    val t = f.inputStream().use { readText(it) } ?: continue
                    val d = parse(t)
                    if (d != null) return d
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
