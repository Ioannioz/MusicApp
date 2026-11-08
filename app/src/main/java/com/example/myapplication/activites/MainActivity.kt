package com.example.myapplication.activites

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.R
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.google.android.exoplayer2.Player
import java.io.File

class MainActivity : AppCompatActivity(), SongAdapter.OnItemClickListener, SongAdapter.OnItemLongClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter
    private var songList: List<Song> = mutableListOf()

    private var audioService: AudioService? = null
    private var isBound = false

    private var tempSelectedImageUri: Uri? = null
    private var activeDialogCoverPreview: ImageView? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                tempSelectedImageUri = uri
                activeDialogCoverPreview?.let { imgView ->
                    Glide.with(this)
                        .load(uri)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .centerCrop()
                        .into(imgView)
                }
            }
        }

    private val webCoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = result.data?.getStringExtra("web_image_uri")
                if (!uriString.isNullOrEmpty()) {
                    val uri = Uri.parse(uriString)
                    tempSelectedImageUri = uri
                    activeDialogCoverPreview?.let { imgView ->
                        Glide.with(this)
                            .load(uri)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .centerCrop()
                            .into(imgView)
                    }
                }
            }
        }

    private data class PendingEdit(
        val position: Int,
        val newTitle: String,
        val newArtist: String,
        val newImageUri: Uri?
    )
    private var pendingEditSong: PendingEdit? = null

    private val editMediaLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingEditSong?.let { edit ->
                    applySongEditToApp(edit.position, edit.newTitle, edit.newArtist, edit.newImageUri)
                }
            } else {
                Toast.makeText(this, "Edit cancelled", Toast.LENGTH_SHORT).show()
            }
            pendingEditSong = null
        }

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

        val albumArtTarget = song.getAlbumArtTarget(this)

        Glide.with(this)
            .asBitmap()
            .load(albumArtTarget)
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
        adapter = SongAdapter(songList, this, this)
        binding.recyclerView.adapter = adapter
    }

    private fun saveSongMetadataToPrefs(songId: Long, title: String, artist: String) {
        val prefs = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("title_$songId", title)
            .putString("artist_$songId", artist)
            .apply()
    }

    private fun getSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val prefs = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)

        contentResolver.query(uri, null, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumns = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn)
                var artist = cursor.getString(artistColumns)
                val data = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val savedTitle = prefs.getString("title_$id", null)
                val savedArtist = prefs.getString("artist_$id", null)

                if (!savedTitle.isNullOrEmpty()) {
                    title = savedTitle
                }
                if (!savedArtist.isNullOrEmpty()) {
                    artist = savedArtist
                }

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

    override fun onItemLongClick(position: Int): Boolean {
        showEditSongDialog(position)
        return true
    }

    private fun showEditSongDialog(position: Int) {
        if (position !in songList.indices) return
        val song = songList[position]
        tempSelectedImageUri = null

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(50, 30, 50, 10)
        }

        val coverPreview = ImageView(this).apply {
            val params = LinearLayout.LayoutParams(160, 160).apply {
                bottomMargin = 20
            }
            layoutParams = params
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        activeDialogCoverPreview = coverPreview

        Glide.with(this)
            .load(song.getAlbumArtTarget(this))
            .placeholder(R.drawable.baseline_music_note_24)
            .into(coverPreview)

        val inputTitle = EditText(this).apply {
            hint = "Song Title"
            setText(song.title)
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.light_gray))
        }

        val inputArtist = EditText(this).apply {
            hint = "Artist Name"
            setText(song.artist)
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.light_gray))
        }

        val changeArtButton = Button(this).apply {
            text = "Change Cover Image"
            setTextColor(getColor(R.color.white))
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setOnClickListener {
                val options = arrayOf("Search Device Files", "Search Web")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Choose Image Source")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> pickImageLauncher.launch("image/*")
                            1 -> {
                                val currentTitle = inputTitle.text.toString().trim()
                                val currentArtist = inputArtist.text.toString().trim()
                                val query = "$currentTitle $currentArtist album cover".trim()
                                val webIntent = Intent(this@MainActivity, WebCoverPickerActivity::class.java).apply {
                                    putExtra("search_query", query)
                                }
                                webCoverLauncher.launch(webIntent)
                            }
                        }
                    }
                    .show()
            }
        }

        layout.addView(coverPreview)
        layout.addView(changeArtButton)
        layout.addView(inputTitle)
        layout.addView(inputArtist)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Song Info")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = inputTitle.text.toString().trim()
                val newArtist = inputArtist.text.toString().trim()
                val imageToSave = tempSelectedImageUri
                if (newTitle.isNotEmpty()) {
                    updateSongMetadata(position, newTitle, newArtist, imageToSave)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.white))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.white))
    }

    private fun updateSongMetadata(position: Int, newTitle: String, newArtist: String, newImageUri: Uri?) {
        if (position !in songList.indices) return
        val song = songList[position]

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
        }
        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

        try {
            val updatedRows = contentResolver.update(songUri, values, null, null)
            if (updatedRows > 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                applySongEditToApp(position, newTitle, newArtist, newImageUri)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                val writeRequest = MediaStore.createWriteRequest(contentResolver, listOf(songUri))
                editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest.intentSender).build())
            } else {
                applySongEditToApp(position, newTitle, newArtist, newImageUri)
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                val intentSender = recoverableSecurityException?.userAction?.actionIntent?.intentSender
                if (intentSender != null) {
                    pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                    editMediaLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                    val writeRequest = MediaStore.createWriteRequest(contentResolver, listOf(songUri))
                    editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest.intentSender).build())
                } else {
                    Toast.makeText(this, "Permission required to edit song", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to edit song", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCustomAlbumArt(songId: Long, albumId: Long, imageUri: Uri) {
        try {
            val oldSharedFile = File(filesDir, "albumart_$albumId.jpg")
            if (oldSharedFile.exists()) {
                oldSharedFile.delete()
            }

            val songFile = File(filesDir, "albumart_$songId.jpg")
            contentResolver.openInputStream(imageUri)?.use { input ->
                val bytes = input.readBytes()
                songFile.writeBytes(bytes)
            }
            Glide.get(applicationContext).clearMemory()
            Thread {
                Glide.get(applicationContext).clearDiskCache()
            }.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save cover image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySongEditToApp(position: Int, newTitle: String, newArtist: String, newImageUri: Uri?) {
        val oldSong = songList[position]

        saveSongMetadataToPrefs(oldSong.id, newTitle, newArtist)

        if (newImageUri != null) {
            saveCustomAlbumArt(oldSong.id, oldSong.albumId, newImageUri)
        }

        val updatedSong = Song(oldSong.id, newTitle, newArtist, oldSong.data, oldSong.albumId)
        (songList as? MutableList)?.set(position, updatedSong)
        adapter.notifyItemChanged(position)

        val srv = audioService
        if (srv != null) {
            srv.updateActiveSongMetadata(updatedSong)
        }

        Toast.makeText(this, "Song updated permanently", Toast.LENGTH_SHORT).show()
    }
}
