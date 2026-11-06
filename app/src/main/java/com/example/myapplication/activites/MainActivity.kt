package com.example.myapplication.activites

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Song

class MainActivity : AppCompatActivity(), SongAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter
    private var songList: List<Song> = mutableListOf()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                loadSongs()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        checkPermissionAndLoadSong()
    }

    private fun checkPermissionAndLoadSong() {
        val persmission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, persmission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            requestPermissionLauncher.launch(persmission)
        }
    }

    private fun loadSongs() {
        songList = getSongs()
        adapter = SongAdapter(songList, this)
        binding.recyclerView.adapter = adapter
    }

    private fun getSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(uri, null, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumns = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumns)
                val data = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)

                songs.add(Song(id, title, artist, data, albumId))
            }
        }
        return songs
    }

    override fun onItemClick(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("songList", ArrayList(songList))
            putExtra("position", position)
        }
        startActivity(intent)
    }
}
