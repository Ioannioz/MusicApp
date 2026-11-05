package com.example.myapplication.model

import java.io.Serializable

data class Song(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val albumId: Long
) : Serializable
