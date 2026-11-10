package com.example.myapplication.model

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.Serializable
import java.util.Locale

data class Song(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val albumId: Long,
    val duration: Long = 0L
) : Serializable {

    fun formatDuration(): String {
        if (duration <= 0) return ""
        val seconds = (duration / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    fun getAlbumArtTarget(context: Context): Any {
        val customSongFile = File(context.filesDir, "albumart_$id.jpg")
        return if (customSongFile.exists()) {
            customSongFile
        } else {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }
    }
}
