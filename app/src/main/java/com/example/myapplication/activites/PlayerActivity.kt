package com.example.myapplication.activites

import android.content.ContentUris
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityPlayerBinding
import com.example.myapplication.model.Song
import com.frolo.waveformseekbar.WaveformSeekBar
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import java.io.InputStream
import java.util.Locale
import kotlin.random.Random

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var binding: ActivityPlayerBinding
    private var songList: List<Song> = mutableListOf()
    private var shuffledList: MutableList<Song> = mutableListOf()
    private var currentIndex = 0
    private var isShuffle = false
    private var isRepeat = false
    private val handle = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            player?.let {
                if (it.isPlaying) {
                    val currentPosition = it.currentPosition
                    val duration = it.duration
                    if (duration > 0) {
                        val progressPercent = currentPosition.toFloat() / duration
                        binding.waveformSeekBar.setProgressInPercentage(progressPercent)
                        binding.textElapsed.text = formatTime((currentPosition / 1000).toInt())
                        binding.textDuration.text = formatTime((duration / 1000).toInt())
                    }
                    handle.postDelayed(this, 1000)
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
        shuffledList = songList.toMutableList()
        binding.waveformSeekBar.setWaveProgressColor(getColor(R.color.white))
        binding.waveformSeekBar.setWaveform(createWaveform())
        setupControls()
        initializePlayer(currentIndex)
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
                            val seekpos = (percent * duration).toLong()
                            p.seekTo(seekpos)
                            textElapsed.text = formatTime((seekpos / 1000).toInt())
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: WaveformSeekBar?) {
                    handle.removeCallbacks(updateRunnable)
                }

                override fun onStopTrackingTouch(seekBar: WaveformSeekBar?) {
                    handle.postDelayed(updateRunnable, 0)
                }
            })
        }
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                handle.removeCallbacks(updateRunnable)
            } else {
                it.play()
                handle.postDelayed(updateRunnable, 0)
            }
            updatePlayPauseButtonOnIcon()
        }
    }

    private fun toggleRepeat() {
        isRepeat = !isRepeat
        player?.repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (isRepeat) {
            binding.buttonRepeat.setColorFilter(
                getColor(R.color.purple_500),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.buttonRepeat.clearColorFilter()
        }
    }

    private fun toggleShuffle() {
        isShuffle = !isShuffle
        if (isShuffle) {
            shuffledList.shuffle()
            binding.ButtonSuffle.setColorFilter(
                getColor(R.color.purple_500),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            shuffledList = songList.toMutableList()
            binding.ButtonSuffle.clearColorFilter()
        }
    }

    private fun initializePlayer(index: Int) {
        val song = if (isShuffle) shuffledList[index] else songList[index]
        player?.release()

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updatePlayPauseButtonOnIcon()
                    if (playbackState == Player.STATE_READY) {
                        binding.textDuration.text = formatTime((duration / 1000).toInt())
                        handle.postDelayed(updateRunnable, 0)
                    } else if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButtonOnIcon()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Error playing song: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

            setMediaItem(MediaItem.fromUri(song.data ?: ""))
            prepare()
            play()
        }
        updatePlayPauseButtonOnIcon()
        updateUI(song)
    }

    private fun updateUI(song: Song) {
        binding.textTitle1.text = song.title ?: ""
        binding.textTitle1.isSelected = true
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
        return IntArray(50) { 5 + random.nextInt(50) }
    }

    private fun updatePlayPauseButtonOnIcon() {
        binding.buttonPlay.setImageResource(
            if (player?.isPlaying == true) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        )
    }

    private fun playNext() {
        val size = if (isShuffle) shuffledList.size else songList.size
        currentIndex = (currentIndex + 1) % size
        initializePlayer(currentIndex)
    }

    private fun playPrevious() {
        val size = if (isShuffle) shuffledList.size else songList.size
        currentIndex = (currentIndex - 1 + size) % size
        initializePlayer(currentIndex)
    }

    override fun onDestroy() {
        super.onDestroy()
        handle.removeCallbacks(updateRunnable)
        player?.release()
        player = null
    }
}
