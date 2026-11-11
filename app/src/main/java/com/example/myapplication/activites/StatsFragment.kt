package com.example.myapplication.activites

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentStatsBinding
import com.example.myapplication.model.Song
import com.example.myapplication.model.StatsManager
import com.example.myapplication.util.ToastHelper

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateStatsUI()

        binding.swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#1DB954"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            updateStatsUI()
            ToastHelper.showToast(requireContext(), "Stats refreshed")
            binding.swipeRefreshLayout.isRefreshing = false
        }

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext()))
    }

    override fun onResume() {
        super.onResume()
        updateStatsUI()
    }

    private fun updateStatsUI() {
        if (_binding == null) return
        val context = requireContext()
        val mainAct = activity as? MainActivity

        // 1. Listening Usage Stats
        val todaySecs = StatsManager.getTodayListeningSeconds(context)
        val weeklySecs = StatsManager.getWeeklyListeningSeconds(context)

        binding.textTodayUsage.text = StatsManager.formatDuration(todaySecs)
        binding.textWeeklyUsage.text = StatsManager.formatDuration(weeklySecs)

        val allSongs = mainAct?.getSongList() ?: emptyList()

        // 2. Top Song of the Week
        val topWeekPair = StatsManager.getTopSongOfWeek(context, allSongs)
        if (topWeekPair != null) {
            val (song, playCount) = topWeekPair
            binding.layoutTopWeekSong.visibility = View.VISIBLE
            binding.textTopWeekEmpty.visibility = View.GONE

            binding.textTopWeekTitle.text = song.title ?: "Unknown Song"
            binding.textTopWeekArtist.text = song.artist ?: "Unknown Artist"
            binding.textTopWeekPlays.text = if (playCount == 1) "Played 1 time this week" else "Played $playCount times this week"

            Glide.with(this)
                .load(song.getAlbumArtTarget(context))
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(binding.imageTopWeekAlbum)

            binding.layoutTopWeekSong.setOnClickListener {
                playSelectedSong(song, allSongs)
            }
        } else {
            binding.layoutTopWeekSong.visibility = View.GONE
            binding.textTopWeekEmpty.visibility = View.VISIBLE
        }

        // 3. Top Song of the Month
        val topMonthPair = StatsManager.getTopSongOfMonth(context, allSongs)
        if (topMonthPair != null) {
            val (song, playCount) = topMonthPair
            binding.layoutTopMonthSong.visibility = View.VISIBLE
            binding.textTopMonthEmpty.visibility = View.GONE

            binding.textTopMonthTitle.text = song.title ?: "Unknown Song"
            binding.textTopMonthArtist.text = song.artist ?: "Unknown Artist"
            binding.textTopMonthPlays.text = if (playCount == 1) "Played 1 time this month" else "Played $playCount times this month"

            Glide.with(this)
                .load(song.getAlbumArtTarget(context))
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(binding.imageTopMonthAlbum)

            binding.layoutTopMonthSong.setOnClickListener {
                playSelectedSong(song, allSongs)
            }
        } else {
            binding.layoutTopMonthSong.visibility = View.GONE
            binding.textTopMonthEmpty.visibility = View.VISIBLE
        }
    }

    private fun playSelectedSong(targetSong: Song, songList: List<Song>) {
        val index = songList.indexOfFirst { it.id == targetSong.id }
        val position = if (index != -1) index else 0

        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("songList", ArrayList(songList))
            putExtra("position", position)
        }
        startActivity(intent)
    }

    private val themeChangeListener: (com.example.myapplication.model.AppTheme) -> Unit = { theme ->
        activity?.runOnUiThread {
            applyTheme(theme)
        }
    }

    private fun applyTheme(theme: com.example.myapplication.model.AppTheme) {
        if (_binding == null) return
        val bgColor = android.graphics.Color.parseColor(theme.bgColorHex)
        val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)

        binding.root.setBackgroundColor(bgColor)
        binding.cardTodayUsage.setCardBackgroundColor(cardColor)
        binding.cardWeeklyUsage.setCardBackgroundColor(cardColor)
        binding.cardTopWeekSong.setCardBackgroundColor(cardColor)
        binding.cardTopMonthSong.setCardBackgroundColor(cardColor)

        binding.textTodayUsage.setTextColor(accentColor)
        binding.textTopWeekPlays.setTextColor(accentColor)
        binding.textTopMonthPlays.setTextColor(accentColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
        _binding = null
    }
}
