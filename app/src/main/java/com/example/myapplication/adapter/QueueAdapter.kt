package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemQueueDividerBinding
import com.example.myapplication.databinding.ItemQueueSongBinding
import com.example.myapplication.model.Song

import java.util.Collections

sealed class QueueListItem {
    data class SongItem(val song: Song) : QueueListItem()
    data class DividerItem(val title: String) : QueueListItem()
}

class QueueAdapter(
    var items: MutableList<QueueListItem>,
    private var activeSongId: Long?,
    private val listener: OnQueueItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateData(newItems: List<QueueListItem>, newActiveSongId: Long?) {
        items = newItems.toMutableList()
        activeSongId = newActiveSongId
        notifyDataSetChanged()
    }

    fun isPlayingSong(song: Song): Boolean {
        return activeSongId != null && song.id == activeSongId
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices) {
            Collections.swap(items, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    interface OnQueueItemClickListener {
        fun onQueueItemClick(song: Song)
    }

    companion object {
        const val TYPE_SONG = 0
        const val TYPE_DIVIDER = 1
    }

    class SongViewHolder(val binding: ItemQueueSongBinding) : RecyclerView.ViewHolder(binding.root)
    class DividerViewHolder(val binding: ItemQueueDividerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is QueueListItem.SongItem -> TYPE_SONG
            is QueueListItem.DividerItem -> TYPE_DIVIDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SONG) {
            SongViewHolder(ItemQueueSongBinding.inflate(inflater, parent, false))
        } else {
            DividerViewHolder(ItemQueueDividerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is QueueListItem.SongItem -> {
                val songViewHolder = holder as SongViewHolder
                val song = item.song
                songViewHolder.binding.queueTextTitle.text = song.title ?: "Unknown Song"

                val artistText = song.artist ?: "Unknown Artist"
                val durationFormatted = song.formatDuration()
                val durationText = if (durationFormatted.isNotEmpty()) " • $durationFormatted" else ""
                songViewHolder.binding.queueTextArtist.text = "$artistText$durationText"

                val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(songViewHolder.binding.root.context)
                val activeBgColor = Color.parseColor(theme.cardColorHex)
                val activeTitleColor = Color.parseColor(theme.accentColorHex)

                if (activeSongId != null && song.id == activeSongId) {
                    songViewHolder.binding.root.setBackgroundColor(activeBgColor)
                    songViewHolder.binding.queueTextTitle.setTextColor(activeTitleColor)
                } else {
                    songViewHolder.binding.root.setBackgroundColor(Color.TRANSPARENT)
                    songViewHolder.binding.queueTextTitle.setTextColor(Color.WHITE)
                }

                Glide.with(songViewHolder.binding.root.context)
                    .load(song.getAlbumArtTarget(songViewHolder.binding.root.context))
                    .centerCrop()
                    .placeholder(R.drawable.baseline_music_note_24)
                    .error(R.drawable.baseline_music_note_24)
                    .into(songViewHolder.binding.queueAlbumArt)

                songViewHolder.binding.root.setOnClickListener {
                    listener.onQueueItemClick(song)
                }
            }
            is QueueListItem.DividerItem -> {
                val dividerViewHolder = holder as DividerViewHolder
                dividerViewHolder.binding.textDividerTitle.text = item.title
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
