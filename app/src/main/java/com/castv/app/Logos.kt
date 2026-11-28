package com.castv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** [LOGO-PRIORIDAD-1] Cola de descargas «la última en entrar, la primera en salir»: lo que se ve ahora va antes que lo que quedó atrás. */
private class LifoQueue : LinkedBlockingDeque<Runnable>() {
    override fun offer(e: Runnable): Boolean = offerFirst(e)
}

/** [LOGO-FICHA-1] Mientras llega el logo: la inicial del canal sobre la ficha oscura (color fijo por nombre). */
private class InitialDrawable(name: String) : Drawable() {
    private val letter: String = initialOf(name)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = PALETTE[(name.hashCode() and 0x7fffffff) % PALETTE.size]
    }

    override fun draw(canvas: Canvas) {
        val r = bounds
        paint.textSize = r.height() * 0.5f
        val y = r.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, r.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 128
    override fun getIntrinsicHeight(): Int = 128

    companion object {
        private val PALETTE = intArrayOf(
            0xFF7FB7FF.toInt(), 0xFFFF9E80.toInt(), 0xFFA5D6A7.toInt(), 0xFFCE93D8.toInt(),
            0xFFFFD54F.toInt(), 0xFF80DEEA.toInt(), 0xFFF48FB1.toInt(), 0xFFB0BEC5.toInt()
        )

        private fun initialOf(name: String): String {
            for (ch in name) if (Character.isLetterOrDigit(ch)) return ch.toString().uppercase()
            return "•"
        }
    }
}

/**
 * [REGIONES-1c] Logos de canales (tvg-logo): descarga pequeña con código propio, sin librerías.
 * Caché en memoria (6 MB) y solo se baja lo que de verdad se ve en pantalla.
 * [LOGO-CACHE-1] Además se guardan en el teléfono (carpeta de caché, ~40 MB máx., 30 días): la segunda vez salen al instante.
 * [LOGO-FICHA-1] Mientras llega el logo se ve la inicial del canal en una ficha oscura, no un cuadro claro vacío.
 * [LOGO-PRIORIDAD-1] 5 descargas a la vez, lo que se ve ahora va primero, y un logo que falló se reintenta a los 60 s.
 */
object Logos {
    private const val MAX_BYTES = 400 * 1024
    private const val RETRY_MS = 60_000L                      // [LOGO-PRIORIDAD-1] reintento de un logo que falló
    private const val DISK_TTL_MS = 30L * 24 * 3600 * 1000    // [LOGO-CACHE-1]
    private const val DISK_MAX = 40L * 1024 * 1024
    private const val DISK_KEEP = 30L * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failed = HashMap<String, Long>()   // [LOGO-PRIORIDAD-1] url -> cuándo falló
    private val inflight = HashSet<String>()       // [LOGO-PRIORIDAD-1] precargas en curso
    private val lightMap = HashMap<String, Boolean>()   // [VISTA-5] url -> el logo es claro
    private const val BG_LIGHT = 0xFFF2F2F2.toInt()
    private const val BG_DARK = 0xFF2B3040.toInt()
    private val pool = ThreadPoolExecutor(5, 5, 30L, TimeUnit.SECONDS, LifoQueue())
    private val diskPool = Executors.newFixedThreadPool(2)   // [LOGO-CACHE-1] leer del teléfono no espera a la red
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var dir: File? = null
    @Volatile private var writes = 0

    private fun dirFor(ctx: Context): File? {
        dir?.let { return it }
        return try {
            val d = File(ctx.applicationContext.cacheDir, "logos")
            d.mkdirs()
            if (d.isDirectory) { dir = d; d } else null
        } catch (e: Exception) { null }
    }

    /** Pone el logo en la vista; si aún no está, primero mira en el teléfono y luego lo baja, y lo pone al llegar (si la fila no se reutilizó). */
    fun load(iv: ImageView, url: String, name: String = "") {
        iv.tag = url
        val hit = cache.get(url)
        if (hit != null) {
            iv.setImageBitmap(hit)
            return
        }
        val low = url.lowercase()
        if (!(low.startsWith("http://") || low.startsWith("https://"))) {
            iv.setImageDrawable(null)
            return
        }
        // [LOGO-FICHA-1] la inicial del canal mientras llega el logo
        iv.setImageDrawable(if (name.isBlank()) null else InitialDrawable(name))
        if (recentlyFailed(url)) return
        val d = dirFor(iv.context)
        diskPool.execute {
            if (iv.tag != url) return@execute   // la fila ya se reutilizó para otro canal
            val fromDisk = if (d != null) readDisk(d, url) else null
            if (fromDisk != null) {
                deliver(iv, url, fromDisk)
                return@execute
            }
            pool.execute {
                if (iv.tag != url) return@execute
                val bytes = fetchBytes(url)
                val bmp = if (bytes != null) decode(bytes) else null
                if (bmp != null && bytes != null && d != null) writeDisk(d, url, bytes)
                if (bmp == null) markFailed(url) else deliver(iv, url, bmp)
            }
        }
    }

    /** [LOGO-PRIORIDAD-1] Baja por adelantado (sin vista) los logos de Favoritos, Continuar y Más vistos; la lista va de más a menos importante. */
    fun prefetch(ctx: Context, urls: List<String>) {
        val d = dirFor(ctx)
        for (u in urls.asReversed()) {   // la cola es «el último en entrar sale primero»: se mete al revés
            val low = u.lowercase()
            if (!(low.startsWith("http://") || low.startsWith("https://"))) continue
            if (cache.get(u) != null) continue
            val added = synchronized(inflight) { inflight.add(u) }
            if (!added) continue
            pool.execute {
                try {
                    if (cache.get(u) != null) return@execute
                    if (recentlyFailed(u)) return@execute
                    val fromDisk = if (d != null) readDisk(d, u) else null
                    if (fromDisk != null) {
                        cache.put(u, fromDisk)
                        return@execute
                    }
                    val bytes = fetchBytes(u)
                    val bmp = if (bytes != null) decode(bytes) else null
                    if (bmp == null || bytes == null) {
                        markFailed(u)
                    } else {
                        if (d != null) writeDisk(d, u, bytes)
                        cache.put(u, bmp)
                    }
                } finally {
                    synchronized(inflight) { inflight.remove(u) }
                }
            }
        }
    }

    private fun deliver(iv: ImageView, url: String, bmp: Bitmap) {
        main.post {
            cache.put(url, bmp)
            if (iv.tag == url) {
                iv.setImageBitmap(bmp)
                val bg = iv.background   // [VISTA-5] el fondo ya puesto se ajusta al llegar el logo
                if (bg is GradientDrawable) bg.setColor(if (isLight(url, bmp)) BG_DARK else BG_LIGHT)
            }
        }
    }

    private fun recentlyFailed(url: String): Boolean {
        val now = System.currentTimeMillis()
        return synchronized(failed) {
            val t = failed[url]
            t != null && now - t < RETRY_MS
        }
    }

    private fun markFailed(url: String) {
        synchronized(failed) {
            if (failed.size > 5000) failed.clear()
            failed[url] = System.currentTimeMillis()
        }
    }

    /** [ACCESO-3] El logo ya bajado (solo de la memoria; null si aún no está). */
    fun cached(url: String): Bitmap? = if (url.isEmpty()) null else cache.get(url)

    /** [VISTA-5] Color de fondo para el logo: oscuro si el logo es claro (p. ej. blanco); claro en los demás casos. [LOGO-FICHA-1] Mientras no llegó, ficha oscura. */
    fun bgFor(url: String): Int {
        val b = cache.get(url) ?: return BG_DARK
        return if (isLight(url, b)) BG_DARK else BG_LIGHT
    }

    /** [VISTA-5] Promedia el brillo de los píxeles visibles (muestra de ~24x24); más de 185 de 255 = logo claro. */
    private fun isLight(url: String, b: Bitmap): Boolean {
        synchronized(lightMap) { lightMap[url]?.let { return it } }
        var sum = 0L
        var n = 0
        val sx = maxOf(1, b.width / 24)
        val sy = maxOf(1, b.height / 24)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                if ((p ushr 24) > 128) {
                    sum += (299 * ((p shr 16) and 255) + 587 * ((p shr 8) and 255) + 114 * (p and 255)) / 1000
                    n++
                }
                x += sx
            }
            y += sy
        }
        val r = n > 0 && sum / n > 185
        synchronized(lightMap) { lightMap[url] = r }
        return r
    }

    // ---------------------------------------------------------------- [LOGO-CACHE-1] caché en el teléfono

    private fun keyOf(url: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        val sb = StringBuilder()
        for (b in md) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun readDisk(d: File, url: String): Bitmap? {
        try {
            val f = File(d, keyOf(url))
            if (!f.isFile) return null
            if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) return null   // vencido: se baja de nuevo
            val bmp = decode(f.readBytes())
            if (bmp == null) f.delete()
            return bmp
        } catch (e: Exception) { }
        return null
    }

    private fun writeDisk(d: File, url: String, bytes: ByteArray) {
        try {
            val f = File(d, keyOf(url))
            val tmp = File(d, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            if (++writes % 40 == 0) trimDisk(d)
        } catch (e: Exception) { }
    }

    /** Si la carpeta pasa de ~40 MB, borra lo más viejo hasta ~30 MB. */
    private fun trimDisk(d: File) {
        try {
            val files = d.listFiles() ?: return
            var total = 0L
            for (f in files) total += f.length()
            if (total <= DISK_MAX) return
            for (f in files.sortedBy { it.lastModified() }) {
                total -= f.length()
                f.delete()
                if (total <= DISK_KEEP) break
            }
        } catch (e: Exception) { }
    }

    // ---------------------------------------------------------------- red

    private fun fetchBytes(url: String): ByteArray? {
        try {
            var cur = url
            for (hop in 0..4) {
                val c = URL(cur).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 8000   // [LOGO-PRIORIDAD-1] con Internet lento 5 s era poco
                c.readTimeout = 12000
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
                return try {
                    c.inputStream.use { readLimited(it) }
                } finally {
                    c.disconnect()
                }
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
