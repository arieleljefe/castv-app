package com.castv.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class MediaItem(
    val id: Long, val name: String, val uriString: String, val isMusic: Boolean,
    val durationSec: Int = 0,
    val dateAdded: Long = 0,   // segundos desde epoch
    val folder: String = "",   // ruta relativa, p. ej. "Download/Series"
    val albumId: Long = 0,     // solo audio (carátulas en Android < 10)
    val artist: String = "",   // solo audio
    val album: String = ""     // solo audio
)

object MediaScanner {

    fun scanVideos(context: Context): List<MediaItem> = scan(context, false)

    fun scanMusic(context: Context): List<MediaItem> = scan(context, true)

    @Suppress("DEPRECATION")
    private fun scan(context: Context, music: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val base: Uri = if (music) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val useRel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED
        )
        cols.add(if (useRel) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA)
        if (music) cols.add(MediaStore.Audio.Media.ALBUM_ID)
        if (music) cols.add(MediaStore.Audio.Media.ARTIST)
        if (music) cols.add(MediaStore.Audio.Media.ALBUM)

        val selection = if (music) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null

        context.contentResolver.query(
            base, cols.toTypedArray(), selection, null,
            "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val addCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(
                if (useRel) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA)
            val albumCol = if (music) c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID) else -1
            val artistCol = if (music) c.getColumnIndex(MediaStore.Audio.Media.ARTIST) else -1
            val albumNameCol = if (music) c.getColumnIndex(MediaStore.Audio.Media.ALBUM) else -1
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                val raw = c.getString(pathCol) ?: ""
                items.add(
                    MediaItem(
                        id = id, name = name, uriString = "$base/$id", isMusic = music,
                        durationSec = (c.getLong(durCol) / 1000).toInt(),
                        dateAdded = c.getLong(addCol),
                        folder = folderOf(raw, useRel),
                        albumId = if (albumCol >= 0) c.getLong(albumCol) else 0,
                        artist = if (artistCol >= 0) cleanArtist(c.getString(artistCol)) else "",
                        album = if (albumNameCol >= 0) cleanArtist(c.getString(albumNameCol)) else ""
                    )
                )
            }
        }
        return items
    }

    private fun cleanArtist(a: String?): String =
        if (a == null || a == "<unknown>") "" else a.trim()

    /** Normaliza la ruta a algo como "Download/Series" (sin barras sobrantes ni nombre de archivo). */
    private fun folderOf(raw: String, relative: Boolean): String {
        var dir = if (relative) raw else raw.substringBeforeLast('/', "")
        dir = dir.replace("/storage/emulated/0/", "").replace("/sdcard/", "")
        dir = dir.trim('/')
        return if (dir.isEmpty()) "(raíz)" else dir
    }

    /** MIME correcto según la extensión (no todo lo "audio" es mpeg ni todo el vídeo mp4). */
    fun mimeFor(name: String, isMusic: Boolean): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> if (isMusic) "audio/mpeg" else "video/mp4"
        }
    }

    /** Extensión para la URL (los TV suelen decidir el formato por la extensión). */
    fun extFor(name: String, isMusic: Boolean): String {
        val e = name.substringAfterLast('.', "").lowercase()
        return if (e.isNotEmpty() && e.length <= 5) e else if (isMusic) "mp3" else "mp4"
    }
}
