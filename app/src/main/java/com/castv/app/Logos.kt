package com.castv.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * [REGIONES-1c] Logos de canales (tvg-logo): descarga pequeña con código propio, sin librerías.
 * Caché en memoria (6 MB), 3 descargas a la vez, y solo se baja lo que de verdad se ve en pantalla.
 */
object Logos {
    private const val MAX_BYTES = 400 * 1024

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failed = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** Pone el logo en la vista; si aún no está, lo baja y lo pone al llegar (si la fila no se reutilizó). */
    fun load(iv: ImageView, url: String) {
        iv.tag = url
        val hit = cache.get(url)
        if (hit != null) {
            iv.setImageBitmap(hit)
            return
        }
        iv.setImageDrawable(null)
        val low = url.lowercase()
        if (!(low.startsWith("http://") || low.startsWith("https://"))) return
        synchronized(failed) { if (failed.contains(url)) return }
        pool.execute {
            if (iv.tag != url) return@execute   // la fila ya se reutilizó para otro canal
            val bmp = fetch(url)
            main.post {
                if (bmp == null) {
                    synchronized(failed) {
                        if (failed.size > 5000) failed.clear()
                        failed.add(url)
                    }
                } else {
                    cache.put(url, bmp)
                    if (iv.tag == url) iv.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun fetch(url: String): Bitmap? {
        try {
            var cur = url
            for (hop in 0..4) {
                val c = URL(cur).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 5000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CASTV")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc == null) return null
                    cur = URL(URL(cur), loc).toString()
                    continue
                }
                if (code != 200) {
                    c.disconnect()
                    return null
                }
                val bytes: ByteArray? = try {
                    c.inputStream.use { readLimited(it) }
                } finally {
                    c.disconnect()
                }
                if (bytes == null) return null
                return decode(bytes)
            }
        } catch (e: Exception) { }
        return null
    }

    private fun readLimited(ins: InputStream): ByteArray? {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            bos.write(buf, 0, n)
            if (bos.size() > MAX_BYTES) return null
        }
        return bos.toByteArray()
    }

    /** Decodifica reduciendo el tamaño (lado mayor cerca de 128 px) para gastar poca memoria. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null   // no es una imagen que Android lea (p. ej. SVG)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 128 && o.outHeight / (sample * 2) >= 128) sample *= 2
        val d = BitmapFactory.Options()
        d.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d)
    }
}
