package com.example.myapplication.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

object PlaylistManager {

    private const val PREFS_NAME = "playlist_prefs"
    private const val KEY_PLAYLISTS = "playlists_json"

    const val LIKED_SONGS_PLAYLIST_ID = -100L
    const val LIKED_SONGS_PLAYLIST_NAME = "Favourites"

    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        if (!changeListeners.contains(listener)) {
            changeListeners.add(listener)
        }
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    private fun notifyChanged() {
        for (listener in changeListeners) {
            listener.invoke()
        }
    }

    fun getPlaylists(context: Context): MutableList<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PLAYLISTS, null)

        val list = mutableListOf<Playlist>()
        if (!jsonString.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    var name = obj.getString("name")
                    if (id == LIKED_SONGS_PLAYLIST_ID && name != LIKED_SONGS_PLAYLIST_NAME) {
                        name = LIKED_SONGS_PLAYLIST_NAME
                    }
                    val songIdsArray = obj.getJSONArray("songIds")
                    val songIds = mutableListOf<Long>()
                    for (j in 0 until songIdsArray.length()) {
                        songIds.add(songIdsArray.getLong(j))
                    }
                    list.add(Playlist(id, name, songIds))
                }
            } catch (e: Exception) {
                // Ignore parse exception
            }
        }

        var likedPlaylist = list.find { it.id == LIKED_SONGS_PLAYLIST_ID }
        if (likedPlaylist == null) {
            likedPlaylist = Playlist(
                id = LIKED_SONGS_PLAYLIST_ID,
                name = LIKED_SONGS_PLAYLIST_NAME,
                songIds = mutableListOf()
            )
            list.add(0, likedPlaylist)
            savePlaylists(context, list)
        } else if (likedPlaylist.name != LIKED_SONGS_PLAYLIST_NAME) {
            val updatedPlaylist = Playlist(
                id = LIKED_SONGS_PLAYLIST_ID,
                name = LIKED_SONGS_PLAYLIST_NAME,
                songIds = likedPlaylist.songIds
            )
            val idx = list.indexOf(likedPlaylist)
            if (idx != -1) {
                list[idx] = updatedPlaylist
            }
            savePlaylists(context, list)
        }

        return list
    }

    fun savePlaylists(context: Context, playlists: List<Playlist>, notify: Boolean = true) {
        val jsonArray = JSONArray()
        for (pl in playlists) {
            val obj = JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                val idsArray = JSONArray()
                for (sId in pl.songIds) {
                    idsArray.put(sId)
                }
                put("songIds", idsArray)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        if (notify) {
            notifyChanged()
        }
    }

    fun isSongLiked(context: Context, songId: Long): Boolean {
        val playlists = getPlaylists(context)
        val likedPlaylist = playlists.find { it.id == LIKED_SONGS_PLAYLIST_ID }
        return likedPlaylist?.songIds?.contains(songId) == true
    }

    fun toggleLikeSong(context: Context, songId: Long): Boolean {
        val playlists = getPlaylists(context)
        var likedPlaylist = playlists.find { it.id == LIKED_SONGS_PLAYLIST_ID }
        if (likedPlaylist == null) {
            likedPlaylist = Playlist(
                id = LIKED_SONGS_PLAYLIST_ID,
                name = LIKED_SONGS_PLAYLIST_NAME,
                songIds = mutableListOf()
            )
            playlists.add(0, likedPlaylist)
        }

        val isLiked: Boolean
        if (likedPlaylist.songIds.contains(songId)) {
            likedPlaylist.songIds.remove(songId)
            isLiked = false
        } else {
            likedPlaylist.songIds.add(songId)
            isLiked = true
        }

        savePlaylists(context, playlists)
        return isLiked
    }
}
