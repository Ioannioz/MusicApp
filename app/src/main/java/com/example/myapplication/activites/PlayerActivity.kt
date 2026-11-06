package com.example.myapplication.activites

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityPlayerBinding
import com.example.myapplication.model.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
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
        binding.waveformSeekBar.setWaveform(createWaveform())
        initializePlayer(currentIndex)
    }


    private fun initializePlayer(index: Int) {
        val song = if (isShuffle) shuffledList[index] else songList[index]
        player?.release()

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {

                    if(playbackState==Player.STATE_READY){
                        binding.textDuration.text=formatTime((duration/1000).toInt())
                        handle.postDelayed(updateRunnable,0)
                    }else if(playbackState == Player.STATE_READY) {

                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Error playing song: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun createWaveform(): IntArray {
        val random = Random(System.currentTimeMillis())
        return IntArray(50) { 5 + random.nextInt(50) }
    }
}
