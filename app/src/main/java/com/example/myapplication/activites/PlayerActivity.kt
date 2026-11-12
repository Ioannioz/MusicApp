package com.example.myapplication.activites

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PorterDuff
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import com.example.myapplication.util.ToastHelper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.adapter.QueueAdapter
import com.example.myapplication.adapter.QueueListItem
import com.example.myapplication.databinding.ActivityPlayerBinding
import com.example.myapplication.model.PlaylistManager
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.frolo.waveformseekbar.WaveformSeekBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import java.util.Locale
import kotlin.random.Random

class PlayerActivity : AppCompatActivity() {
    private var audioService: AudioService? = null
    private var isBound = false
    private val player: ExoPlayer? get() = audioService?.player

    private lateinit var binding: ActivityPlayerBinding
    private var songList: List<Song> = mutableListOf()
    private var currentIndex = 0
    private var wasPlayingBeforeTracking = false
    private val handle = Handler(Looper.getMainLooper())

    private lateinit var queueBehavior: BottomSheetBehavior<LinearLayout>
    private var targetTopSpacerPx = 0
    private var isUserScrubbingPlayerMini = false
    private var queueAdapter: QueueAdapter? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayPauseButtonOnIcon()
            if (playbackState == Player.STATE_READY) {
                player?.let { p ->
                    binding.textDuration.text = formatTime((p.duration / 1000).toInt())
                    updateAllProgressViews(p.currentPosition, p.duration)
                }
                handle.postDelayed(updateRunnable, 0)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButtonOnIcon()
            updatePlayerMiniPlayerUI()
            handle.removeCallbacks(updateRunnable)
            if (isPlaying) {
                handle.post(updateRunnable)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            audioService?.getCurrentSong()?.let { updateUI(it) }
            refreshEmbeddedQueueData()
            updatePlayerMiniPlayerUI()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            audioService?.getCurrentSong()?.let { updateUI(it) }
            refreshEmbeddedQueueData()
            updatePlayerMiniPlayerUI()
        }

        override fun onPlayerError(error: PlaybackException) {
            ToastHelper.showToast(this@PlayerActivity, "Error playing song: ${error.message}")
        }
    }

    private val stateListener = {
        runOnUiThread {
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            audioService?.getCurrentSong()?.let { updateUI(it) }
            refreshEmbeddedQueueData()
            updatePlayerMiniPlayerUI()

            if (player?.isPlaying == true) {
                handle.removeCallbacks(updateRunnable)
                handle.post(updateRunnable)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            val srv = binder.getService()
            audioService = srv
            isBound = true

            srv.player?.addListener(playerListener)
            srv.addStateChangeListener(stateListener)

            val currentSong = srv.getCurrentSong()
            val isPlayingOrPrepared = srv.player?.let { it.playbackState != Player.STATE_IDLE } == true
            val targetSong = if (currentIndex in songList.indices) songList[currentIndex] else null

            val isSameSongPlaying = isPlayingOrPrepared && currentSong != null && targetSong != null && currentSong.id == targetSong.id

            if (!isSameSongPlaying) {
                srv.playNewList(songList, currentIndex)
            }

            val activeSong = srv.getCurrentSong() ?: targetSong
            if (activeSong != null) {
                updateUI(activeSong)
            }

            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            refreshEmbeddedQueueData()
            updatePlayerMiniPlayerUI()

            srv.player?.let { p ->
                if (p.duration > 0) {
                    updateAllProgressViews(p.currentPosition, p.duration)
                }
            }

            handle.removeCallbacks(updateRunnable)
            handle.post(updateRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService?.removeStateChangeListener(stateListener)
            audioService?.player?.removeListener(playerListener)
            audioService = null
            isBound = false
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                val currentPosition = p.currentPosition
                val duration = p.duration
                if (duration > 0 && !isUserScrubbingPlayerMini) {
                    updateAllProgressViews(currentPosition, duration)
                }
                if (p.isPlaying) {
                    handle.postDelayed(this, 30)
                }
            }
        }
    }

    private fun updateAllProgressViews(currentPosition: Long, duration: Long) {
        if (duration <= 0) return
        val ratio = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

        binding.waveformSeekBar.setProgressInPercentage(ratio)
        binding.textElapsed.text = formatTime((currentPosition / 1000).toInt())
        binding.textDuration.text = formatTime((duration / 1000).toInt())

        if (!isUserScrubbingPlayerMini) {
            binding.playerMiniSeekBar.progress = (ratio * 1000).toInt()
            val seconds = (currentPosition / 1000).toInt()
            val minutes = seconds / 60
            val secs = seconds % 60
            binding.playerMiniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.miniPlayerLayoutPlayer.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            val density = resources.displayMetrics.density
            targetTopSpacerPx = systemBars.top + (72 * density).toInt()
            insets
        }

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val songs = intent.getSerializableExtra("songList") as? List<Song>
        currentIndex = intent.getIntExtra("position", 0)

        if (songs == null || songs.isEmpty()) {
            ToastHelper.showToast(this, "No songs found")
            finish()
            return
        }

        songList = songs
        binding.waveformSeekBar.setWaveProgressColor(getColor(R.color.white))
        binding.waveformSeekBar.setWaveform(createWaveform())
        setupControls()
        setupEmbeddedQueueSheet()

        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyAppTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(this))
    }

    private fun setupEmbeddedQueueSheet() {
        val sheet = binding.embeddedQueueSheet
        queueBehavior = BottomSheetBehavior.from(sheet)
        queueBehavior.peekHeight = 140
        queueBehavior.isHideable = false
        queueBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.recyclerViewQueue.layoutManager = LinearLayoutManager(this)

        val queueItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return makeMovementFlags(0, 0)

                val item = queueAdapter?.items?.getOrNull(pos)
                if (item is QueueListItem.SongItem && queueAdapter?.isPlayingSong(item.song) == true) {
                    return makeMovementFlags(0, 0)
                }

                return if (viewHolder.itemViewType == QueueAdapter.TYPE_SONG) {
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else {
                    makeMovementFlags(0, 0)
                }
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (target.itemViewType != QueueAdapter.TYPE_SONG) return false
                val targetPos = target.bindingAdapterPosition
                if (targetPos == RecyclerView.NO_POSITION) return false

                val targetItem = queueAdapter?.items?.getOrNull(targetPos)
                if (targetItem is QueueListItem.SongItem && queueAdapter?.isPlayingSong(targetItem.song) == true) {
                    return false
                }

                return true
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                    val fromItem = queueAdapter?.items?.getOrNull(fromPos)
                    val toItem = queueAdapter?.items?.getOrNull(toPos)
                    if (fromItem is QueueListItem.SongItem && toItem is QueueListItem.SongItem) {
                        if (queueAdapter?.isPlayingSong(fromItem.song) == false && queueAdapter?.isPlayingSong(toItem.song) == false) {
                            queueAdapter?.moveItem(fromPos, toPos)
                            return true
                        }
                    }
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                updateQueueFromAdapter()
            }
        })
        queueItemTouchHelper.attachToRecyclerView(binding.recyclerViewQueue)

        binding.playerMiniBtnPrev.setOnClickListener {
            playPrevious()
        }

        binding.playerMiniBtnPlay.setOnClickListener {
            togglePlayPause()
        }

        binding.playerMiniBtnNext.setOnClickListener {
            playNext()
        }

        binding.playerMiniSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.let { p ->
                        val duration = p.duration
                        if (duration > 0) {
                            val seekMs = (progress.toFloat() / 1000f * duration).toLong()
                            val progressRatio = progress.toFloat() / 1000f
                            binding.waveformSeekBar.setProgressInPercentage(progressRatio)
                            val seconds = (seekMs / 1000).toInt()
                            val minutes = seconds / 60
                            val secs = seconds % 60
                            binding.playerMiniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                            binding.textElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingPlayerMini = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player?.let { p ->
                    val duration = p.duration
                    if (duration > 0 && seekBar != null) {
                        val targetMs = (seekBar.progress.toFloat() / 1000f * duration).toLong()
                        p.seekTo(targetMs)
                        val progressRatio = seekBar.progress.toFloat() / 1000f
                        binding.waveformSeekBar.setProgressInPercentage(progressRatio)
                        binding.textElapsed.text = formatTime((targetMs / 1000).toInt())
                    }
                }
                handle.postDelayed({
                    isUserScrubbingPlayerMini = false
                }, 200)
            }
        })

        queueBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    binding.miniPlayerLayoutPlayer.visibility = View.VISIBLE
                    binding.miniPlayerLayoutPlayer.alpha = 1.0f
                    updatePlayerMiniPlayerUI()
                    refreshEmbeddedQueueData()
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    binding.miniPlayerLayoutPlayer.visibility = View.GONE
                    refreshEmbeddedQueueData()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val clampedOffset = slideOffset.coerceIn(0f, 1f)

                if (targetTopSpacerPx > 0) {
                    val spacerHeight = (targetTopSpacerPx * clampedOffset).toInt()
                    if (binding.sheetTopSpacer.height != spacerHeight) {
                        binding.sheetTopSpacer.updateLayoutParams<LinearLayout.LayoutParams> {
                            height = spacerHeight
                        }
                    }
                }

                if (clampedOffset > 0.7f) {
                    binding.miniPlayerLayoutPlayer.visibility = View.VISIBLE
                    binding.miniPlayerLayoutPlayer.alpha = (clampedOffset - 0.7f) / 0.3f
                    updatePlayerMiniPlayerUI()
                } else {
                    binding.miniPlayerLayoutPlayer.visibility = View.GONE
                }
            }
        })

        refreshEmbeddedQueueData()
    }

    private fun updatePlayerMiniPlayerUI() {
        val srv = audioService ?: return
        val currentSong = srv.getCurrentSong() ?: return
        val p = srv.player

        val newTitle = currentSong.title ?: "Unknown Song"
        if (binding.playerMiniTitle.text != newTitle) {
            binding.playerMiniTitle.text = newTitle
            binding.playerMiniTitle.isSelected = true
        } else if (!binding.playerMiniTitle.isSelected) {
            binding.playerMiniTitle.isSelected = true
        }
        binding.playerMiniArtist.text = currentSong.artist ?: "Unknown Artist"

        val isPlaying = p?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
        binding.playerMiniBtnPlay.setImageResource(
            if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        )

        if (p != null && p.duration > 0 && !isUserScrubbingPlayerMini) {
            updateAllProgressViews(p.currentPosition, p.duration)
        }

        Glide.with(this)
            .load(currentSong.getAlbumArtTarget(this))
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .error(R.drawable.baseline_music_note_24)
            .into(binding.playerMiniAlbumArt)
    }

    private fun refreshEmbeddedQueueData() {
        val srv = audioService ?: return
        val activeList = srv.getActiveList()
        val currentSong = srv.getCurrentSong()

        val sourceName = intent.getStringExtra("playlistName") ?: "Device Files"
        val queueItems = mutableListOf<QueueListItem>()
        var totalSongsCount = 0

        // 1. Now Playing Section (YouTube Music Style)
        if (currentSong != null) {
            queueItems.add(QueueListItem.DividerItem("Now Playing"))
            queueItems.add(QueueListItem.SongItem(currentSong))
            totalSongsCount++
        }

        // 2. User Queue (Manually added songs)
        if (srv.userQueue.isNotEmpty()) {
            queueItems.add(QueueListItem.DividerItem("From User Queue"))
            srv.userQueue.forEach {
                queueItems.add(QueueListItem.SongItem(it))
                totalSongsCount++
            }
        }

        // 3. Up Next Section
        if (activeList.isNotEmpty() && srv.currentIndex in activeList.indices) {
            queueItems.add(QueueListItem.DividerItem("Up Next • $sourceName"))
            val currIdx = srv.currentIndex

            for (i in (currIdx + 1) until activeList.size) {
                queueItems.add(QueueListItem.SongItem(activeList[i]))
                totalSongsCount++
            }

            for (i in 0 until currIdx) {
                queueItems.add(QueueListItem.SongItem(activeList[i]))
                totalSongsCount++
            }
        } else if (activeList.isNotEmpty()) {
            queueItems.add(QueueListItem.DividerItem("Up Next • $sourceName"))
            activeList.forEach {
                if (it.id != currentSong?.id) {
                    queueItems.add(QueueListItem.SongItem(it))
                    totalSongsCount++
                }
            }
        }

        binding.textQueueCount.text = if (totalSongsCount == 1) "1 Song" else "$totalSongsCount Songs"

        if (queueAdapter == null) {
            queueAdapter = QueueAdapter(queueItems, currentSong?.id, object : QueueAdapter.OnQueueItemClickListener {
                override fun onQueueItemClick(song: Song) {
                    queueBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    val targetIndex = activeList.indexOfFirst { it.id == song.id }
                    if (targetIndex != -1) {
                        srv.playSongAtIndex(targetIndex)
                        updateUI(song)
                    } else {
                        srv.playSingleSong(song)
                        updateUI(song)
                    }
                    refreshEmbeddedQueueData()
                }
            })
            binding.recyclerViewQueue.adapter = queueAdapter
        } else {
            queueAdapter?.updateData(queueItems, currentSong?.id)
        }
    }

    private fun updateQueueFromAdapter() {
        val adapter = queueAdapter ?: return
        val srv = audioService ?: return
        val items = adapter.items

        val userQueueSongs = mutableListOf<Song>()
        val activeSongs = mutableListOf<Song>()

        var foundDivider = false
        for (item in items) {
            if (item is QueueListItem.DividerItem) {
                foundDivider = true
                continue
            }
            if (item is QueueListItem.SongItem) {
                if (srv.userQueue.isNotEmpty() && !foundDivider) {
                    userQueueSongs.add(item.song)
                } else {
                    activeSongs.add(item.song)
                }
            }
        }

        if (srv.userQueue.isNotEmpty()) {
            srv.userQueue.clear()
            srv.userQueue.addAll(userQueueSongs)
        }

        if (activeSongs.isNotEmpty()) {
            val currentSong = srv.getCurrentSong()
            if (srv.isShuffle && srv.shuffledList.isNotEmpty()) {
                srv.shuffledList.clear()
                srv.shuffledList.addAll(activeSongs)
                if (currentSong != null) {
                    val newIdx = srv.shuffledList.indexOfFirst { it.id == currentSong.id }
                    if (newIdx != -1) {
                        srv.currentIndex = newIdx
                    }
                }
            } else if (srv.songList.isNotEmpty()) {
                (srv.songList as? MutableList)?.let { list ->
                    list.clear()
                    list.addAll(activeSongs)
                }
                if (currentSong != null) {
                    val newIdx = srv.songList.indexOfFirst { it.id == currentSong.id }
                    if (newIdx != -1) {
                        srv.currentIndex = newIdx
                    }
                }
            }
        }
    }

    private fun setupControls() {
        binding.apply {
            backBtn.setOnClickListener { finish() }
            favBtn.setOnClickListener {
                val song = audioService?.getCurrentSong() ?: songList.getOrNull(currentIndex) ?: return@setOnClickListener
                val isLiked = PlaylistManager.toggleLikeSong(this@PlayerActivity, song.id)
                updateFavoriteButton(song)
                val msg = if (isLiked) "Added to Favorites" else "Removed from Favorites"
                ToastHelper.showToast(this@PlayerActivity, msg)
            }
            buttonPlay.setOnClickListener { togglePlayPause() }
            buttonNext.setOnClickListener { playNext() }
            buttonPrev.setOnClickListener { playPrevious() }
            ButtonSuffle.setOnClickListener { toggleShuffle() }
            buttonRepeat.setOnClickListener { toggleRepeat() }
            waveformSeekBar.setCallback(object : WaveformSeekBar.Callback {
                override fun onProgressChanged(
                    seekBar: WaveformSeekBar?,
                    percent: Float,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        player?.let { p ->
                            val duration = p.duration
                            if (duration > 0) {
                                val seekpos = (percent * duration).toLong()
                                p.seekTo(seekpos)
                                binding.playerMiniSeekBar.progress = (percent * 1000).toInt()
                                val seconds = (seekpos / 1000).toInt()
                                val minutes = seconds / 60
                                val secs = seconds % 60
                                binding.playerMiniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                                textElapsed.text = formatTime((seekpos / 1000).toInt())
                            }
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: WaveformSeekBar?) {
                    wasPlayingBeforeTracking = player?.isPlaying == true
                    player?.pause()
                    handle.removeCallbacks(updateRunnable)
                    updatePlayPauseButtonOnIcon()
                }

                override fun onStopTrackingTouch(seekBar: WaveformSeekBar?) {
                    if (wasPlayingBeforeTracking) {
                        player?.play()
                        handle.postDelayed(updateRunnable, 0)
                    }
                    updatePlayPauseButtonOnIcon()
                }
            })
        }
    }

    private fun togglePlayPause() {
        audioService?.togglePlayPause()
        updatePlayPauseButtonOnIcon()
        updatePlayerMiniPlayerUI()
        refreshEmbeddedQueueData()

        handle.removeCallbacks(updateRunnable)
        if (player?.isPlaying == true) {
            handle.post(updateRunnable)
        }
    }

    private fun toggleRepeat() {
        audioService?.toggleRepeat()
        updateRepeatShuffleIcons()
    }

    private fun toggleShuffle() {
        audioService?.toggleShuffle()
        updateRepeatShuffleIcons()
        refreshEmbeddedQueueData()
        updatePlayerMiniPlayerUI()
    }

    private fun updateRepeatShuffleIcons() {
        val srv = audioService ?: return
        val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(this)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)
        val isLightAccent = theme.accentColorHex.equals("#FFFFFF", ignoreCase = true) || theme.accentColorHex.equals("#DBD8E3", ignoreCase = true)
        val activeIconTint = if (isLightAccent) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        if (srv.isRepeat) {
            binding.buttonRepeat.setBackgroundResource(R.drawable.circle_white_bg)
            binding.buttonRepeat.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            binding.buttonRepeat.setColorFilter(activeIconTint, PorterDuff.Mode.SRC_IN)
        } else {
            binding.buttonRepeat.background = null
            binding.buttonRepeat.clearColorFilter()
        }

        if (srv.isShuffle) {
            binding.ButtonSuffle.setBackgroundResource(R.drawable.circle_white_bg)
            binding.ButtonSuffle.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            binding.ButtonSuffle.setColorFilter(activeIconTint, PorterDuff.Mode.SRC_IN)
        } else {
            binding.ButtonSuffle.background = null
            binding.ButtonSuffle.clearColorFilter()
        }
    }

    private fun updateUI(song: Song) {
        val newTitle = song.title ?: ""
        if (binding.textTitle1.text != newTitle) {
            binding.textTitle1.text = newTitle
            binding.textTitle1.isSelected = true
        }
        binding.textArtist.text = song.artist ?: ""
        title = song.title

        updateFavoriteButton(song)

        val albumArtTarget = song.getAlbumArtTarget(this)

        Glide.with(this)
            .asBitmap()
            .load(albumArtTarget)
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .error(R.drawable.baseline_music_note_24)
            .into(binding.imageAlbumArtPlayer)

        updatePlayerMiniPlayerUI()
    }

    private fun updateFavoriteButton(song: Song) {
        val isLiked = PlaylistManager.isSongLiked(this, song.id)
        if (isLiked) {
            binding.favBtn.setImageResource(R.drawable.baseline_favorite_24)
            binding.favBtn.setColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        } else {
            binding.favBtn.setImageResource(R.drawable.baseline_favorite_border_24)
            binding.favBtn.setColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun createWaveform(): IntArray {
        val random = Random(System.currentTimeMillis())
        return IntArray(120) { 5 + random.nextInt(50) }
    }

    private fun updatePlayPauseButtonOnIcon() {
        val isPlaying = player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
        binding.buttonPlay.setImageResource(
            if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        )
        updatePlayerMiniPlayerUI()
    }

    private fun playNext() {
        audioService?.playNext()
        audioService?.getCurrentSong()?.let { updateUI(it) }
        refreshEmbeddedQueueData()
        updatePlayerMiniPlayerUI()
    }

    override fun onResume() {
        super.onResume()
        if (isBound) {
            audioService?.player?.let { p ->
                if (p.duration > 0) {
                    updateAllProgressViews(p.currentPosition, p.duration)
                }
            }
            handle.removeCallbacks(updateRunnable)
            handle.post(updateRunnable)
        }
    }

    private fun playPrevious() {
        audioService?.playPrevious()
        audioService?.getCurrentSong()?.let { updateUI(it) }
        refreshEmbeddedQueueData()
        updatePlayerMiniPlayerUI()
    }

    private val themeChangeListener: (com.example.myapplication.model.AppTheme) -> Unit = { theme ->
        runOnUiThread {
            applyAppTheme(theme)
        }
    }

    private fun applyAppTheme(theme: com.example.myapplication.model.AppTheme) {
        val bgColor = android.graphics.Color.parseColor(theme.bgColorHex)
        val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)

        val isLightAccent = theme.accentColorHex.equals("#FFFFFF", ignoreCase = true) || theme.accentColorHex.equals("#DBD8E3", ignoreCase = true)
        val playIconTint = if (isLightAccent) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        binding.PlayerRoot.setBackgroundColor(bgColor)
        binding.miniPlayerLayoutPlayer.setBackgroundColor(cardColor)
        binding.embeddedQueueSheet.setBackgroundColor(cardColor)
        binding.waveformSeekBar.setWaveProgressColor(accentColor)

        binding.buttonPlay.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.buttonPlay.setColorFilter(playIconTint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
        handle.removeCallbacks(updateRunnable)
        if (isBound) {
            audioService?.removeStateChangeListener(stateListener)
            audioService?.player?.removeListener(playerListener)
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
