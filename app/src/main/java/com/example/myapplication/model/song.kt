package com.example.myapplication.model

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.Serializable

data class Song(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val albumId: Long
) : Serializable {

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
