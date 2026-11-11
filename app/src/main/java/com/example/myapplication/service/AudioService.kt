package com.example.myapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.myapplication.activites.MainActivity
import com.example.myapplication.activites.PlayerActivity
import com.example.myapplication.model.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

class AudioService : Service() {

    private val binder = AudioBinder()
    var player: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    var songList: List<Song> = mutableListOf()
    var shuffledList: MutableList<Song> = mutableListOf()
    val userQueue: ArrayDeque<Song> = ArrayDeque()

    var currentIndex: Int = 0
    var isShuffle: Boolean = false
    var isRepeat: Boolean = false

    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addStateChangeListener(listener: () -> Unit) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
    }

    fun removeStateChangeListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    private var historySongId: Long? = null
    private var lastSelectedListIndex: Int = -1

    private val playedSongIds = mutableSetOf<Long>()
    private var notificationManager: PlayerNotificationManager? = null

    companion object {
        const val CHANNEL_ID = "audio_playback_channel"
        const val NOTIFICATION_ID = 101
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "AudioService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    togglePlayPause()
                }

                override fun onPause() {
                    togglePlayPause()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    player?.seekTo(pos)
                    notifyStateChange()
                }
            })
        }

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && isRepeat) {
                        isRepeat = false
                        repeatMode = Player.REPEAT_MODE_OFF
                    }
                    notifyStateChange()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                    notifyStateChange()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    notifyStateChange()
                }
            })
        }

        statsHandler.post(statsRunnable)

        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    val currentSong = getCurrentSong()
                    return currentSong?.title ?: "Unknown Song"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@AudioService, PlayerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    return PendingIntent.getActivity(
                        this@AudioService,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    val currentSong = getCurrentSong()
                    return currentSong?.artist ?: "Unknown Artist"
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    val currentSong = getCurrentSong() ?: return null
                    return getSongAlbumArtBitmap(currentSong)
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: android.app.Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            .build().apply {
                setMediaSessionToken(mediaSession!!.sessionToken)
                setPlayer(player)
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUsePlayPauseActions(true)
                setUseChronometer(true)
                setColor(Color.BLACK)
                setColorized(true)
            }
    }

    private fun getSongAlbumArtBitmap(song: Song): Bitmap? {
        val target = song.getAlbumArtTarget(this)
        return try {
            when (target) {
                is File -> BitmapFactory.decodeFile(target.absolutePath)
                is Uri -> contentResolver.openInputStream(target)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun notifyStateChange() {
        updateMediaSessionState()
        notificationManager?.invalidate()
        for (listener in stateListeners) {
            listener.invoke()
        }
    }

    private fun updateMediaSessionState() {
        val p = player ?: return
        val playbackState = when {
            p.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            p.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            p.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                playbackState,
                p.currentPosition,
                if (p.isPlaying) 1.0f else 0.0f
            )

        mediaSession?.setPlaybackState(stateBuilder.build())

        val currentSong = getCurrentSong()
        if (currentSong != null) {
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.title ?: "Unknown Song")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.artist ?: "Unknown Artist")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, p.duration.coerceAtLeast(0L))

            val albumArt = getSongAlbumArtBitmap(currentSong)
            if (albumArt != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArt)
            }

            mediaSession?.setMetadata(metadataBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio playback controls"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getActiveList(): List<Song> {
        return if (isShuffle && shuffledList.isNotEmpty()) shuffledList else songList
    }

    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            if (player?.isPlaying == true) {
                com.example.myapplication.model.StatsManager.addPlaybackTime(this@AudioService, 3)
            }
            statsHandler.postDelayed(this, 3000)
        }
    }

    private var currentPlayingSongOverride: Song? = null

    fun getAudioSessionId(): Int {
        return player?.audioSessionId ?: 0
    }

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    fun getLoudnessEnhancerInstance(): android.media.audiofx.LoudnessEnhancer? {
        if (loudnessEnhancer == null) {
            try {
                val sessionId = getAudioSessionId()
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                    enabled = true
                }
            } catch (_: Exception) {}
        }
        return loudnessEnhancer
    }

    fun getCurrentSong(): Song? {
        currentPlayingSongOverride?.let { return it }
        val list = getActiveList()
        return if (list.isNotEmpty() && currentIndex in list.indices) list[currentIndex] else null
    }

    private fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title ?: "Unknown Song")
            .setArtist(song.artist ?: "Unknown Artist")
            .build()

        return MediaItem.Builder()
            .setUri(song.data ?: "")
            .setMediaMetadata(metadata)
            .build()
    }

    fun playNextSongInQueue(song: Song) {
        userQueue.addFirst(song)
        notifyStateChange()
    }

    fun addToQueueEnd(song: Song) {
        userQueue.addLast(song)
        notifyStateChange()
    }

    fun playNewList(newList: List<Song>, initialIndex: Int) {
        if (newList.isEmpty() || initialIndex !in newList.indices) return

        songList = newList.toMutableList()
        val targetSong = newList[initialIndex]

        if (isShuffle) {
            shuffledList = newList.toMutableList()
            shuffledList.removeAll { it.id == targetSong.id }
            shuffledList.shuffle()
            shuffledList.add(0, targetSong)
            playSongAtIndex(0)
        } else {
            shuffledList = newList.toMutableList()
            playSongAtIndex(initialIndex)
        }
    }

    fun playSingleSong(song: Song, addToHistory: Boolean = true) {
        val currentSong = getCurrentSong()
        if (addToHistory && currentSong != null && currentSong.id != song.id) {
            historySongId = currentSong.id
        }
        currentPlayingSongOverride = song
        player?.let { p ->
            val mediaItem = createMediaItem(song)
            p.stop()
            p.setMediaItem(mediaItem)
            p.playWhenReady = true
            p.prepare()
            p.play()
            notifyStateChange()
        }
    }

    fun playSongAtIndex(index: Int, addToHistory: Boolean = true) {
        val list = getActiveList()
        if (list.isEmpty() || index !in list.indices) return

        currentPlayingSongOverride = null
        val currentSong = getCurrentSong()
        val targetSong = list[index]

        if (addToHistory && currentSong != null && currentSong.id != targetSong.id) {
            historySongId = currentSong.id
        }

        currentIndex = index
        lastSelectedListIndex = index
        val song = list[index]

        com.example.myapplication.model.StatsManager.recordSongPlay(this, song.id)

        player?.let { p ->
            val mediaItem = createMediaItem(song)
            p.stop()
            p.setMediaItem(mediaItem)
            p.playWhenReady = true
            p.prepare()
            p.play()
            notifyStateChange()
        }
    }

    fun playNext() {
        val list = getActiveList()

        if (isRepeat) {
            isRepeat = false
            player?.repeatMode = Player.REPEAT_MODE_OFF
        }

        if (userQueue.isNotEmpty()) {
            val queuedSong = userQueue.removeFirst()
            val targetIdx = list.indexOfFirst { it.id == queuedSong.id }
            if (targetIdx != -1) {
                playSongAtIndex(targetIdx, addToHistory = true)
            } else {
                playSingleSong(queuedSong, addToHistory = true)
            }
            return
        }

        if (list.isEmpty()) return

        val nextIndex = (currentIndex + 1) % list.size
        playSongAtIndex(nextIndex, addToHistory = true)
    }

    fun playPrevious() {
        val list = getActiveList()
        if (list.isEmpty()) return

        if (isRepeat) {
            isRepeat = false
            player?.repeatMode = Player.REPEAT_MODE_OFF
        }

        if (userQueue.isNotEmpty()) {
            val queuedSong = userQueue.removeFirst()
            val targetIdx = list.indexOfFirst { it.id == queuedSong.id }
            if (targetIdx != -1) {
                playSongAtIndex(targetIdx, addToHistory = false)
                return
            }
        }

        val prevIndex = (currentIndex - 1 + list.size) % list.size
        playSongAtIndex(prevIndex, addToHistory = false)
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
            notifyStateChange()
        }
    }

    fun toggleRepeat() {
        isRepeat = !isRepeat
        player?.repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        notifyStateChange()
    }

    fun toggleShuffle() {
        val currentSong = getCurrentSong()
        isShuffle = !isShuffle
        historySongId = null
        playedSongIds.clear()

        if (isShuffle) {
            shuffledList = songList.toMutableList()
            if (currentSong != null) {
                shuffledList.removeAll { it.id == currentSong.id }
                shuffledList.shuffle()
                shuffledList.add(0, currentSong)
                currentIndex = 0
            } else {
                shuffledList.shuffle()
                currentIndex = 0
            }
        } else {
            shuffledList.clear()
            if (currentSong != null) {
                val originalIndex = songList.indexOfFirst { it.id == currentSong.id }
                if (originalIndex != -1) {
                    currentIndex = originalIndex
                }
            }
        }

        notifyStateChange()
    }

    fun updateActiveSongMetadata(updatedSong: Song) {
        val mainIdx = songList.indexOfFirst { it.id == updatedSong.id }
        if (mainIdx != -1) {
            (songList as? MutableList)?.set(mainIdx, updatedSong)
        }

        val shuffIdx = shuffledList.indexOfFirst { it.id == updatedSong.id }
        if (shuffIdx != -1) {
            shuffledList[shuffIdx] = updatedSong
        }

        notifyStateChange()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (player?.isPlaying == false) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        statsHandler.removeCallbacks(statsRunnable)
        notificationManager?.setPlayer(null)
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
