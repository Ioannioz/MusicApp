package com.example.myapplication.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication.activites.EqualizerFragment
import com.example.myapplication.activites.PlaylistsFragment
import com.example.myapplication.activites.SongsFragment
import com.example.myapplication.activites.StatsFragment
import com.example.myapplication.activites.ThemesFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val songsFragment = SongsFragment()
    val playlistsFragment = PlaylistsFragment()
    val equalizerFragment = EqualizerFragment()
    val statsFragment = StatsFragment()
    val themesFragment = ThemesFragment()

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> songsFragment
            1 -> playlistsFragment
            2 -> equalizerFragment
            3 -> statsFragment
            else -> themesFragment
        }
    }
}
