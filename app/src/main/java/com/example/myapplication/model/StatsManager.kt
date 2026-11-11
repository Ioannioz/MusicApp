package com.example.myapplication.model

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StatsManager {

    private const val PREFS_NAME = "stats_prefs"

    private fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getCurrentWeekKey(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.getDefault(), "%d-W%02d", year, week)
    }

    private fun getCurrentMonthKey(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    fun addPlaybackTime(context: Context, seconds: Int) {
        if (seconds <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = getTodayDateKey()
        val currentSecs = prefs.getInt("usage_sec_$todayKey", 0)
        prefs.edit().putInt("usage_sec_$todayKey", currentSecs + seconds).apply()
    }

    fun getTodayListeningSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = getTodayDateKey()
        return prefs.getInt("usage_sec_$todayKey", 0)
    }

    fun getWeeklyListeningSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        var totalSecs = 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0 until 7) {
            val dateKey = dateFormat.format(cal.time)
            totalSecs += prefs.getInt("usage_sec_$dateKey", 0)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return totalSecs
    }

    fun recordSongPlay(context: Context, songId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weekKey = getCurrentWeekKey()
        val monthKey = getCurrentMonthKey()

        val weekPlayKey = "play_cnt_w_${weekKey}_$songId"
        val currentWeekCnt = prefs.getInt(weekPlayKey, 0)
        val editor = prefs.edit()
        editor.putInt(weekPlayKey, currentWeekCnt + 1)

        val monthPlayKey = "play_cnt_m_${monthKey}_$songId"
        val currentMonthCnt = prefs.getInt(monthPlayKey, 0)
        editor.putInt(monthPlayKey, currentMonthCnt + 1)

        editor.apply()
    }

    fun getTopSongOfWeek(context: Context, allSongs: List<Song>): Pair<Song, Int>? {
        if (allSongs.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weekKey = getCurrentWeekKey()
        val prefix = "play_cnt_w_${weekKey}_"

        val songMap = allSongs.associateBy { it.id }
        var maxPlays = 0
        var topSong: Song? = null

        val allKeys = prefs.all
        for ((key, value) in allKeys) {
            if (key.startsWith(prefix) && value is Int) {
                val songIdStr = key.substring(prefix.length)
                val songId = songIdStr.toLongOrNull() ?: continue
                val song = songMap[songId]
                if (song != null && value > maxPlays) {
                    maxPlays = value
                    topSong = song
                }
            }
        }

        return if (topSong != null && maxPlays > 0) Pair(topSong, maxPlays) else null
    }

    fun getTopSongOfMonth(context: Context, allSongs: List<Song>): Pair<Song, Int>? {
        if (allSongs.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val monthKey = getCurrentMonthKey()
        val prefix = "play_cnt_m_${monthKey}_"

        val songMap = allSongs.associateBy { it.id }
        var maxPlays = 0
        var topSong: Song? = null

        val allKeys = prefs.all
        for ((key, value) in allKeys) {
            if (key.startsWith(prefix) && value is Int) {
                val songIdStr = key.substring(prefix.length)
                val songId = songIdStr.toLongOrNull() ?: continue
                val song = songMap[songId]
                if (song != null && value > maxPlays) {
                    maxPlays = value
                    topSong = song
                }
            }
        }

        return if (topSong != null && maxPlays > 0) Pair(topSong, maxPlays) else null
    }

    fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60

        return when {
            hours > 0 -> "$hours hr ${mins} min"
            mins > 0 -> "$mins mins"
            totalSeconds > 0 -> "$totalSeconds secs"
            else -> "0 mins"
        }
    }
}
