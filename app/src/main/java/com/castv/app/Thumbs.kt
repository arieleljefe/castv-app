package com.castv.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Miniaturas de video y carátulas de música, con caché en memoria y carga en segundo plano. */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).toInt().coerceAtLeast(1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun key(item: MediaItem) = (if (item.isMusic) "a" else "v") + item.id

    fun cached(item: MediaItem): Bitmap? = cache.get(key(item))

    fun isKnownMissing(item: MediaItem) = failed.contains(key(item))

    /** Carga en segundo plano y devuelve el resultado en el hilo principal (null si no hay imagen). */
    fun load(context: Context, item: MediaItem, onDone: (Bitmap?) -> Unit) {
        val k = key(item)
        val app = context.applicationContext
        pool.execute {
            val bmp = try { fetch(app, item) } catch (e: Throwable) { null }
            if (bmp != null) cache.put(k, bmp) else failed.add(k)
            main.post { onDone(bmp) }
        }
    }

    @Suppress("DEPRECATION")
    private fun fetch(ctx: Context, item: MediaItem): Bitmap? {
        val cr = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val base = if (item.isMusic) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                       else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            return cr.loadThumbnail(ContentUris.withAppendedId(base, item.id), Size(240, 180), null)
        }
        return if (item.isMusic) {
            if (item.albumId <= 0) return null
            val art = ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"), item.albumId)
            cr.openInputStream(art)?.use { BitmapFactory.decodeStream(it) }
        } else {
            MediaStore.Video.Thumbnails.getThumbnail(
                cr, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        }
    }
}
