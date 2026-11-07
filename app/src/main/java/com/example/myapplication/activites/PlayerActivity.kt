package com.example.myapplication.activites

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityPlayerBinding
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.frolo.waveformseekbar.WaveformSeekBar
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import java.io.InputStream
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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayPauseButtonOnIcon()
            if (playbackState == Player.STATE_READY) {
                player?.let { p ->
                    binding.textDuration.text = formatTime((p.duration / 1000).toInt())
                }
                handle.postDelayed(updateRunnable, 0)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButtonOnIcon()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            audioService?.getCurrentSong()?.let { updateUI(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity, "Error playing song: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val stateListener = {
        runOnUiThread {
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
            audioService?.getCurrentSong()?.let { updateUI(it) }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            val srv = binder.getService()
            audioService = srv
            isBound = true

            srv.songList = songList
            if (!srv.isShuffle) {
                srv.shuffledList = songList.toMutableList()
            }
            srv.player?.addListener(playerListener)
            srv.addStateChangeListener(stateListener)

            val currentPlayingSong = srv.getCurrentSong()
            val targetSong = if (currentIndex in songList.indices) songList[currentIndex] else null

            if (currentPlayingSong == null || targetSong == null || currentPlayingSong.id != targetSong.id) {
                srv.playSongAtIndex(currentIndex)
            }

            srv.getCurrentSong()?.let { updateUI(it) }
            updatePlayPauseButtonOnIcon()
            updateRepeatShuffleIcons()
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
                if (p.isPlaying) {
                    val currentPosition = p.currentPosition
                    val duration = p.duration
                    if (duration > 0) {
                        val progressPercent = currentPosition.toFloat() / duration
                        binding.waveformSeekBar.setProgressInPercentage(progressPercent)
                        binding.textElapsed.text = formatTime((currentPosition / 1000).toInt())
                        binding.textDuration.text = formatTime((duration / 1000).toInt())
                    }
                    handle.postDelayed(this, 30)
                }
            }
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

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val songs = intent.getSerializableExtra("songList") as? List<Song>
        currentIndex = intent.getIntExtra("position", 0)

        if (songs == null || songs.isEmpty()) {
            Toast.makeText(this, "No songs found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        songList = songs
        binding.waveformSeekBar.setWaveProgressColor(getColor(R.color.white))
        binding.waveformSeekBar.setWaveform(createWaveform())
        setupControls()

        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupControls() {
        binding.apply {
            backBtn.setOnClickListener { finish() }
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
    }

    private fun toggleRepeat() {
        audioService?.toggleRepeat()
        updateRepeatShuffleIcons()
    }

    private fun toggleShuffle() {
        audioService?.toggleShuffle()
        updateRepeatShuffleIcons()
    }

    private fun updateRepeatShuffleIcons() {
        val srv = audioService ?: return
        if (srv.isRepeat) {
            binding.buttonRepeat.setColorFilter(
                getColor(R.color.purple_500),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.buttonRepeat.clearColorFilter()
        }

        if (srv.isShuffle) {
            binding.ButtonSuffle.setColorFilter(
                getColor(R.color.purple_500),
                PorterDuff.Mode.SRC_IN
            )
        } else {
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

        val albumArtUri = getAlbumArtUri(song.albumId)

        if (hasAlbumArt(albumArtUri)) {
            Glide.with(this)
                .asBitmap()
                .load(albumArtUri)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(binding.imageAlbumArtPlayer)
        } else {
            binding.imageAlbumArtPlayer.setImageResource(R.drawable.baseline_music_note_24)
        }
    }

    private fun hasAlbumArt(albumArtUri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(albumArtUri)
            inputStream?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
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
    }

    private fun playNext() {
        audioService?.playNext()
        audioService?.getCurrentSong()?.let { updateUI(it) }
    }

    private fun playPrevious() {
        audioService?.playPrevious()
        audioService?.getCurrentSong()?.let { updateUI(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handle.removeCallbacks(updateRunnable)
        if (isBound) {
            audioService?.removeStateChangeListener(stateListener)
            audioService?.player?.removeListener(playerListener)
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
