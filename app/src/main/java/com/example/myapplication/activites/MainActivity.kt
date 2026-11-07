package com.example.myapplication.activites

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.google.android.exoplayer2.Player

class MainActivity : AppCompatActivity(), SongAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter
    private var songList: List<Song> = mutableListOf()

    private var audioService: AudioService? = null
    private var isBound = false

    private val stateListener = {
        runOnUiThread {
            updateMiniPlayerUI()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            val srv = binder.getService()
            audioService = srv
            isBound = true

            srv.addStateChangeListener(stateListener)
            updateMiniPlayerUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService?.removeStateChangeListener(stateListener)
            audioService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
            } else {
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            }

            if (audioGranted) {
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
        setupMiniPlayerControls()
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, AudioService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        if (isBound) {
            audioService?.addStateChangeListener(stateListener)
            updateMiniPlayerUI()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            audioService?.removeStateChangeListener(stateListener)
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupMiniPlayerControls() {
        binding.miniPlayerLayout.setOnClickListener {
            val srv = audioService ?: return@setOnClickListener
            if (srv.getCurrentSong() == null) return@setOnClickListener
            val activeList = srv.getActiveList()
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("songList", ArrayList(activeList))
                putExtra("position", srv.currentIndex)
            }
            startActivity(intent)
        }

        binding.miniBtnPlay.setOnClickListener {
            audioService?.togglePlayPause()
            updateMiniPlayerUI()
        }

        binding.miniBtnPrev.setOnClickListener {
            audioService?.playPrevious()
            updateMiniPlayerUI()
        }

        binding.miniBtnNext.setOnClickListener {
            audioService?.playNext()
            updateMiniPlayerUI()
        }
    }

    private fun updateMiniPlayerUI() {
        val srv = audioService ?: run {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::adapter.isInitialized) {
                adapter.setPlayingSongId(null)
            }
            return
        }

        val song = srv.getCurrentSong()
        if (song == null) {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::adapter.isInitialized) {
                adapter.setPlayingSongId(null)
            }
            return
        }

        binding.miniPlayerLayout.visibility = View.VISIBLE
        if (::adapter.isInitialized) {
            adapter.setPlayingSongId(song.id)
        }

        val newTitle = song.title ?: ""
        if (binding.miniTextTitle.text != newTitle) {
            binding.miniTextTitle.text = newTitle
            binding.miniTextTitle.isSelected = true
        }
        binding.miniTextArtist.text = song.artist ?: ""

        val isPlaying = srv.player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
        binding.miniBtnPlay.setImageResource(
            if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        )

        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )

        Glide.with(this)
            .asBitmap()
            .load(albumArtUri)
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .into(binding.miniAlbumArt)
    }

    private fun checkPermissionAndLoadSong() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (permissionsToRequest.isEmpty()) {
                loadSongs()
            } else {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                loadSongs()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
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
