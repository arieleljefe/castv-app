package com.castv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * [FONDO-IMG-1] Imagen de fondo elegida por la persona. Se copia (reducida, lado largo <= 1600 px) a
 * files/bg/bg.jpg, fuera de los respaldos; las claves bgimg y bgdim viven en las preferencias castv.
 */
object BgImage {
    private const val MAX_SIDE = 1600
    private var cacheKey = ""
    private var cacheBmp: Bitmap? = null

    private fun prefs(c: Context) = c.getSharedPreferences("castv", Context.MODE_PRIVATE)
    private fun file(c: Context) = File(File(c.filesDir, "bg"), "bg.jpg")

    fun has(c: Context): Boolean = prefs(c).getBoolean("bgimg", false) && file(c).isFile

    /** Cuánto se oscurece la imagen, 0 a 90 (por defecto 50). */
    fun dim(c: Context): Int = prefs(c).getInt("bgdim", 50).coerceIn(0, 90)

    fun setDim(c: Context, v: Int) { prefs(c).edit().putInt("bgdim", v.coerceIn(0, 90)).apply() }

    /** Número que cambia cuando cambia la imagen o el oscurecido (0 = sin imagen); lo usa Themes.styleKey. */
    fun stamp(c: Context): Int {
        if (!has(c)) return 0
        return (((file(c).lastModified() / 1000L) % 10000L).toInt() + 1) * 10 + dim(c) / 10
    }

    /** Quita la imagen guardada. */
    fun clear(c: Context) {
        prefs(c).edit().putBoolean("bgimg", false).apply()
        try { file(c).delete() } catch (e: Exception) { }
        cacheKey = ""
        cacheBmp = null
    }

    /** Copia la imagen elegida (con su giro EXIF) a la carpeta propia. Llamar fuera del hilo principal. */
    fun save(c: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val orient = try {
                c.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
            val deg = when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix().apply { postRotate(deg) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val big = maxOf(bmp.width, bmp.height)
            if (big > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / big
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
            }
            val dir = File(c.filesDir, "bg")
            dir.mkdirs()
            val tmp = File(dir, "bg.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            val dst = file(c)
            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) { tmp.delete(); return false }
            prefs(c).edit().putBoolean("bgimg", true).apply()
            cacheKey = ""
            cacheBmp = null
            true
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }

    /** Fondo de ventana: la imagen (recortada al centro para llenar la pantalla) con una capa que la oscurece. */
    fun drawable(c: Context): Drawable? {
        if (!has(c)) return null
        return try {
            val f = file(c)
            val key = f.lastModified().toString() + ":" + f.length()
            var bmp = if (key == cacheKey) cacheBmp else null
            if (bmp == null) {
                bmp = BitmapFactory.decodeFile(f.path) ?: return null
                cacheBmp = bmp
                cacheKey = key
            }
            val a = dim(c) * 255 / 100
            val veil = ColorDrawable(if (Themes.isNight(c)) Color.argb(a, 0, 0, 0) else Color.argb(a, 255, 255, 255))
            LayerDrawable(arrayOf(CropDrawable(bmp), veil))
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private class CropDrawable(private val bmp: Bitmap) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val s = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
            val w = bmp.width * s
            val h = bmp.height * s
            val l = b.left + (b.width() - w) / 2f
            val t = b.top + (b.height() - h) / 2f
            canvas.save()
            canvas.clipRect(b)
            canvas.drawBitmap(bmp, null, RectF(l, t, l + w, t + h), paint)
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
