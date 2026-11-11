package com.example.myapplication.model

import java.io.Serializable

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
) : Serializable
