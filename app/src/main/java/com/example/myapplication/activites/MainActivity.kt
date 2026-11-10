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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.R
import com.example.myapplication.adapter.MainPagerAdapter
import com.example.myapplication.adapter.PlaylistAdapter
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Playlist
import com.example.myapplication.model.PlaylistManager
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.exoplayer2.Player
import org.json.JSONArray
import java.io.File
import java.util.Collections
import java.util.Locale

class MainActivity : AppCompatActivity(), SongAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter
    private var songList: List<Song> = mutableListOf()

    private var audioService: AudioService? = null
    private var isBound = false
    private var activeQueueToast: Toast? = null
    private val handle = Handler(Looper.getMainLooper())
    private var isUserScrubbingMini = false

    fun getAudioService(): AudioService? = audioService

    private var tempSelectedImageUri: Uri? = null
    private var activeDialogCoverPreview: ImageView? = null

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val srv = audioService
            val player = srv?.player
            if (srv != null && player != null) {
                val currentPosition = player.currentPosition
                val duration = player.duration
                if (duration > 0 && !isUserScrubbingMini) {
                    val progressRatio = currentPosition.toFloat() / duration.toFloat()
                    binding.miniSeekBar.progress = (progressRatio * 1000).toInt()

                    val seconds = (currentPosition / 1000).toInt()
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    binding.miniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                }
                if (player.isPlaying) {
                    handle.postDelayed(this, 100)
                }
            }
        }
    }

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.PlayerRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.miniPlayerLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }
            insets
        }

        checkPermissionAndLoadSong()
        setupMiniPlayerControls()
        setupTabHeaderNavigation()
    }

    private fun setupTabHeaderNavigation() {
        binding.viewPagerMain.isUserInputEnabled = true

        binding.tabDeviceFiles.setOnClickListener {
            binding.viewPagerMain.currentItem = 0
        }

        binding.tabPlaylist.setOnClickListener {
            binding.viewPagerMain.currentItem = 1
        }

        binding.viewPagerMain.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    binding.tabDeviceFiles.setTextColor(getColor(R.color.white))
                    binding.tabPlaylist.setTextColor(getColor(R.color.light_gray))
                } else {
                    binding.tabDeviceFiles.setTextColor(getColor(R.color.light_gray))
                    binding.tabPlaylist.setTextColor(getColor(R.color.white))
                }
            }
        })
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

    override fun onPause() {
        super.onPause()
        activeQueueToast?.cancel()
        handle.removeCallbacks(updateProgressRunnable)
    }

    override fun onStop() {
        super.onStop()
        activeQueueToast?.cancel()
        handle.removeCallbacks(updateProgressRunnable)
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

        binding.miniSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val srv = audioService ?: return
                    val p = srv.player ?: return
                    val duration = p.duration
                    if (duration > 0) {
                        val seekMs = (progress.toFloat() / 1000f * duration).toLong()
                        val seconds = (seekMs / 1000).toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        binding.miniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingMini = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val srv = audioService ?: return
                val p = srv.player ?: return
                val duration = p.duration
                if (duration > 0 && seekBar != null) {
                    val targetMs = (seekBar.progress.toFloat() / 1000f * duration).toLong()
                    p.seekTo(targetMs)
                }
                isUserScrubbingMini = false
            }
        })
    }

    fun updateMiniPlayerUI() {
        val srv = audioService ?: run {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
                pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(null)
            }
            handle.removeCallbacks(updateProgressRunnable)
            return
        }

        val song = srv.getCurrentSong()
        if (song == null) {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
                pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(null)
            }
            handle.removeCallbacks(updateProgressRunnable)
            return
        }

        binding.miniPlayerLayout.visibility = View.VISIBLE
        if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
            pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(song.id)
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

        handle.removeCallbacks(updateProgressRunnable)
        handle.post(updateProgressRunnable)
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
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPagerMain.adapter = pagerAdapter
    }

    private fun saveSongMetadataToPrefs(songId: Long, title: String, artist: String) {
        val prefs = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("title_$songId", title)
            .putString("artist_$songId", artist)
            .apply()
    }

    private fun saveSongOrderToPrefs(songs: List<Song>) {
        val prefs = getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        songs.forEach { jsonArray.put(it.id) }
        prefs.edit().putString("saved_song_id_order", jsonArray.toString()).apply()
    }

    private fun getSongs(): List<Song> {
        val rawSongsMap = mutableMapOf<Long, Song>()
        val rawSongsList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val prefsMetadata = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)

        contentResolver.query(uri, null, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumns = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn)
                var artist = cursor.getString(artistColumns)
                val data = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)

                val savedTitle = prefsMetadata.getString("title_$id", null)
                val savedArtist = prefsMetadata.getString("artist_$id", null)

                if (!savedTitle.isNullOrEmpty()) {
                    title = savedTitle
                }
                if (!savedArtist.isNullOrEmpty()) {
                    artist = savedArtist
                }

                val song = Song(id, title, artist, data, albumId, duration)
                rawSongsMap[id] = song
                rawSongsList.add(song)
            }
        }

        val prefsOrder = getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        val savedOrderJson = prefsOrder.getString("saved_song_id_order", null)

        if (!savedOrderJson.isNullOrEmpty()) {
            try {
                val orderedSongs = mutableListOf<Song>()
                val jsonArray = JSONArray(savedOrderJson)
                for (i in 0 until jsonArray.length()) {
                    val songId = jsonArray.getLong(i)
                    val song = rawSongsMap.remove(songId)
                    if (song != null) {
                        orderedSongs.add(song)
                    }
                }
                orderedSongs.addAll(rawSongsMap.values)
                return orderedSongs
            } catch (e: Exception) {
                // Ignore parse error
            }
        }

        return rawSongsList
    }

    fun getSongList(): List<Song> = songList

    private fun showQueueNotification(message: String) {
        activeQueueToast?.cancel()
        activeQueueToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        activeQueueToast?.show()
    }

    fun addSongToQueueByPosition(position: Int) {
        if (position !in songList.indices) return
        val song = songList[position]
        val srv = audioService
        if (srv != null) {
            srv.addToQueueEnd(song)
            showQueueNotification("Added \"${song.title ?: "Song"}\" to queue")
        }
    }

    fun onSongReordered(fromPos: Int, toPos: Int) {
        if (fromPos in songList.indices && toPos in songList.indices) {
            Collections.swap(songList, fromPos, toPos)
            saveSongOrderToPrefs(songList)
            val srv = audioService
            if (srv != null) {
                srv.songList = songList
                if (!srv.isShuffle) {
                    srv.shuffledList = songList.toMutableList()
                }
            }
        }
    }

    fun sortSongsByName() {
        val sorted = songList.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" })
        songList = sorted.toMutableList()
        saveSongOrderToPrefs(songList)
        getSharedPreferences("custom_song_order", Context.MODE_PRIVATE).edit().putString("current_sort_mode", "name").apply()
        val srv = audioService
        if (srv != null) {
            srv.songList = songList
            if (!srv.isShuffle) {
                srv.shuffledList = songList.toMutableList()
            }
        }
    }

    fun sortSongsByArtist() {
        val sorted = songList.sortedWith(
            compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.artist ?: "" }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" }
        )
        songList = sorted.toMutableList()
        saveSongOrderToPrefs(songList)
        getSharedPreferences("custom_song_order", Context.MODE_PRIVATE).edit().putString("current_sort_mode", "artist").apply()
        val srv = audioService
        if (srv != null) {
            srv.songList = songList
            if (!srv.isShuffle) {
                srv.shuffledList = songList.toMutableList()
            }
        }
    }

    override fun onItemClick(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("songList", ArrayList(songList))
            putExtra("position", position)
        }
        startActivity(intent)
    }

    fun showSongOptionsMenu(position: Int, anchorView: View) {
        if (position !in songList.indices) return
        val song = songList[position]

        val dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_song_options_bottom_sheet, null)

        val sheetAlbumArt = sheetView.findViewById<ImageView>(R.id.sheetAlbumArt)
        val sheetTitle = sheetView.findViewById<TextView>(R.id.sheetSongTitle)
        val sheetArtist = sheetView.findViewById<TextView>(R.id.sheetSongArtist)

        val optionPlayNext = sheetView.findViewById<LinearLayout>(R.id.optionPlayNext)
        val optionAddToQueue = sheetView.findViewById<LinearLayout>(R.id.optionAddToQueue)
        val optionAddToPlaylist = sheetView.findViewById<LinearLayout>(R.id.optionAddToPlaylist)
        val optionEditInfo = sheetView.findViewById<LinearLayout>(R.id.optionEditInfo)

        sheetTitle.text = song.title ?: "Unknown Song"
        sheetArtist.text = song.artist ?: "Unknown Artist"

        Glide.with(this)
            .load(song.getAlbumArtTarget(this))
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .into(sheetAlbumArt)

        optionPlayNext.setOnClickListener {
            dialog.dismiss()
            val srv = audioService
            if (srv != null) {
                srv.playNextSongInQueue(song)
                showQueueNotification("Will play next")
            }
        }

        optionAddToQueue.setOnClickListener {
            dialog.dismiss()
            val srv = audioService
            if (srv != null) {
                srv.addToQueueEnd(song)
                showQueueNotification("Added \"${song.title ?: "Song"}\" to queue")
            }
        }

        optionAddToPlaylist.setOnClickListener {
            dialog.dismiss()
            showAddToPlaylistDialog(song)
        }

        optionEditInfo.setOnClickListener {
            dialog.dismiss()
            showEditSongDialog(position)
        }

        dialog.setContentView(sheetView)
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val playlists = PlaylistManager.getPlaylists(this)

        if (playlists.isEmpty()) {
            val dialog = AlertDialog.Builder(this).create()
            val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

            val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
            val subMsgText = view.findViewById<TextView>(R.id.dialogInputSubMessage)
            val input = view.findViewById<EditText>(R.id.dialogInputField)
            val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
            val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

            titleText.text = "No Playlists Found"
            subMsgText.visibility = View.VISIBLE
            subMsgText.text = "Create a new playlist to add this song:"
            input.hint = "Playlist Name"
            btnConfirm.text = "Create & Add"

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnConfirm.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    dialog.dismiss()
                    val newPlaylist = Playlist(
                        id = System.currentTimeMillis(),
                        name = name,
                        songIds = mutableListOf(song.id)
                    )
                    playlists.add(newPlaylist)
                    PlaylistManager.savePlaylists(this, playlists)
                    if (::pagerAdapter.isInitialized) {
                        pagerAdapter.playlistsFragment.refreshPlaylists()
                    }
                    Toast.makeText(this, "Created \"$name\" and added song", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.setView(view)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        } else {
            val dialog = AlertDialog.Builder(this).create()
            val view = layoutInflater.inflate(R.layout.dialog_select_playlist, null)

            val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewSelectPlaylist)
            val btnCancel = view.findViewById<Button>(R.id.btnSelectPlaylistCancel)

            btnCancel.setOnClickListener { dialog.dismiss() }

            val optionsList = mutableListOf<Playlist>()
            val newPlaylistItem = Playlist(id = -1L, name = "+ New Playlist", songIds = mutableListOf())
            optionsList.add(newPlaylistItem)
            optionsList.addAll(playlists)

            recyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = PlaylistAdapter(optionsList, object : PlaylistAdapter.OnPlaylistClickListener {
                override fun onPlaylistClick(position: Int) {
                    dialog.dismiss()
                    if (position == 0) {
                        val createDialog = AlertDialog.Builder(this@MainActivity).create()
                        val createView = layoutInflater.inflate(R.layout.dialog_input_text, null)

                        val titleText = createView.findViewById<TextView>(R.id.dialogInputTitle)
                        val input = createView.findViewById<EditText>(R.id.dialogInputField)
                        val btnCancelNew = createView.findViewById<Button>(R.id.btnInputCancel)
                        val btnConfirmNew = createView.findViewById<Button>(R.id.btnInputConfirm)

                        titleText.text = "New Playlist"
                        input.hint = "Playlist Name"
                        btnConfirmNew.text = "Create & Add"

                        btnCancelNew.setOnClickListener { createDialog.dismiss() }

                        btnConfirmNew.setOnClickListener {
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                createDialog.dismiss()
                                val newPlaylist = Playlist(
                                    id = System.currentTimeMillis(),
                                    name = name,
                                    songIds = mutableListOf(song.id)
                                )
                                playlists.add(newPlaylist)
                                PlaylistManager.savePlaylists(this@MainActivity, playlists)
                                if (::pagerAdapter.isInitialized) {
                                    pagerAdapter.playlistsFragment.refreshPlaylists()
                                }
                                Toast.makeText(this@MainActivity, "Created \"$name\" and added song", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        }

                        createDialog.setView(createView)
                        createDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        createDialog.show()
                    } else {
                        val selectedPlaylist = playlists[position - 1]
                        if (!selectedPlaylist.songIds.contains(song.id)) {
                            selectedPlaylist.songIds.add(song.id)
                            PlaylistManager.savePlaylists(this@MainActivity, playlists)
                            if (::pagerAdapter.isInitialized) {
                                pagerAdapter.playlistsFragment.refreshPlaylists()
                            }
                            Toast.makeText(this@MainActivity, "Added to \"${selectedPlaylist.name}\"", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Song already in \"${selectedPlaylist.name}\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            recyclerView.adapter = adapter

            dialog.setView(view)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        }
    }

    fun showEditSongDialog(position: Int) {
        if (position !in songList.indices) return
        val song = songList[position]
        tempSelectedImageUri = null

        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_edit_song, null)

        val coverPreview = view.findViewById<ImageView>(R.id.editSongCoverPreview)
        val btnChangeArt = view.findViewById<Button>(R.id.btnChangeCoverImage)
        val inputTitle = view.findViewById<EditText>(R.id.inputEditSongTitle)
        val inputArtist = view.findViewById<EditText>(R.id.inputEditSongArtist)
        val btnCancel = view.findViewById<Button>(R.id.btnEditSongCancel)
        val btnSave = view.findViewById<Button>(R.id.btnEditSongSave)

        activeDialogCoverPreview = coverPreview

        Glide.with(this)
            .load(song.getAlbumArtTarget(this))
            .placeholder(R.drawable.baseline_music_note_24)
            .into(coverPreview)

        inputTitle.setText(song.title)
        inputArtist.setText(song.artist)

        btnChangeArt.setOnClickListener {
            val options = arrayOf("Search Device Files", "Search Web")
            AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*")
                        1 -> {
                            val currentTitle = inputTitle.text.toString().trim()
                            val currentArtist = inputArtist.text.toString().trim()
                            val query = "$currentTitle $currentArtist album cover".trim()
                            val webIntent = Intent(this, WebCoverPickerActivity::class.java).apply {
                                putExtra("search_query", query)
                            }
                            webCoverLauncher.launch(webIntent)
                        }
                    }
                }
                .show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = inputTitle.text.toString().trim()
            val newArtist = inputArtist.text.toString().trim()
            val imageToSave = tempSelectedImageUri
            if (newTitle.isNotEmpty()) {
                dialog.dismiss()
                updateSongMetadata(position, newTitle, newArtist, imageToSave)
            } else {
                Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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
                editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest).build())
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
                    editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest).build())
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

        val updatedSong = Song(oldSong.id, newTitle, newArtist, oldSong.data, oldSong.albumId, oldSong.duration)
        (songList as? MutableList)?.set(position, updatedSong)
        if (::pagerAdapter.isInitialized) {
            val adapterSongs = pagerAdapter.songsFragment.songAdapter?.songs
            if (adapterSongs != null && position in adapterSongs.indices) {
                adapterSongs[position] = updatedSong
                pagerAdapter.songsFragment.songAdapter?.notifyItemChanged(position)
            }
        }

        val srv = audioService
        if (srv != null) {
            srv.updateActiveSongMetadata(updatedSong)
        }

        updateMiniPlayerUI()

        Toast.makeText(this, "Song updated permanently", Toast.LENGTH_SHORT).show()
    }
}
