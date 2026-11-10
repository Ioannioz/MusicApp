package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSongBinding
import com.example.myapplication.databinding.ItemSongGridBinding
import com.example.myapplication.model.Song
import java.util.Collections

enum class SongViewMode {
    LIST, GRID
}

class SongAdapter(
    val songs: MutableList<Song>,
    private val listener: OnItemClickListener,
    private val optionsClickListener: OnOptionsClickListener? = null,
    var viewMode: SongViewMode = SongViewMode.LIST
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var playingSongId: Long? = null

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    fun setPlayingSongId(id: Long?) {
        if (playingSongId != id) {
            playingSongId = id
            notifyDataSetChanged()
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in songs.indices && toPosition in songs.indices) {
            Collections.swap(songs, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnOptionsClickListener {
        fun onOptionsClick(position: Int, view: View)
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == SongViewMode.GRID) TYPE_GRID else TYPE_LIST
    }

    class ListSongViewHolder(
        val binding: ItemSongBinding,
        private val listener: OnItemClickListener,
        private val optionsClickListener: OnOptionsClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(pos)
                }
            }
            binding.btnOptions.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    optionsClickListener?.onOptionsClick(pos, binding.btnOptions)
                }
            }
        }
    }

    class GridSongViewHolder(
        val binding: ItemSongGridBinding,
        private val listener: OnItemClickListener,
        private val optionsClickListener: OnOptionsClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            val clickHandler = View.OnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(pos)
                }
            }
            binding.root.setOnClickListener(clickHandler)
            binding.cardAlbumArt.setOnClickListener(clickHandler)
            binding.btnOptions.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    optionsClickListener?.onOptionsClick(pos, binding.btnOptions)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            val binding = ItemSongGridBinding.inflate(inflater, parent, false)
            GridSongViewHolder(binding, listener, optionsClickListener)
        } else {
            val binding = ItemSongBinding.inflate(inflater, parent, false)
            ListSongViewHolder(binding, listener, optionsClickListener)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val song = songs[position]

        if (holder is ListSongViewHolder) {
            holder.binding.textTitle.text = song.title

            val artistText = song.artist ?: "Unknown Artist"
            val durationFormatted = song.formatDuration()
            val durationText = if (durationFormatted.isNotEmpty()) " • $durationFormatted" else ""
            holder.binding.textArtist.text = "$artistText$durationText"

            if (playingSongId != null && song.id == playingSongId) {
                holder.binding.root.setBackgroundColor(Color.parseColor("#121212"))
            } else {
                holder.binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            val albumArtTarget = song.getAlbumArtTarget(holder.binding.root.context)

            Glide.with(holder.binding.root.context)
                .load(albumArtTarget)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .error(R.drawable.baseline_music_note_24)
                .into(holder.binding.imageAlbumArt)
        } else if (holder is GridSongViewHolder) {
            holder.binding.textTitle.text = song.title
            holder.binding.textArtist.text = song.artist ?: "Unknown Artist"

            if (playingSongId != null && song.id == playingSongId) {
                holder.binding.root.setBackgroundColor(Color.parseColor("#121212"))
            } else {
                holder.binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            val albumArtTarget = song.getAlbumArtTarget(holder.binding.root.context)

            Glide.with(holder.binding.root.context)
                .load(albumArtTarget)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .error(R.drawable.baseline_music_note_24)
                .into(holder.binding.imageAlbumArt)
        }
    }

    override fun getItemCount(): Int = songs.size
}
