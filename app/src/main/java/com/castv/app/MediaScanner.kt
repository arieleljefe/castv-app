package com.castv.app

import android.content.Context
import android.provider.MediaStore

data class MediaItem(val id: Long, val name: String, val uriString: String, val isMusic: Boolean)

object MediaScanner {

    fun scanVideos(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val uri = "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/$id"
                items.add(MediaItem(id, name, uri, false))
            }
        }
        return items
    }

    fun scanMusic(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id"
                items.add(MediaItem(id, name, uri, true))
            }
        }
        return items
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
