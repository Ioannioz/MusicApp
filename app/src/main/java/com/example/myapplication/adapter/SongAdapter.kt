package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSongBinding
import com.example.myapplication.model.Song

class SongAdapter(
    private val songs: List<Song>,
    private val listener: OnItemClickListener,
    private val longClickListener: OnItemLongClickListener? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var playingSongId: Long? = null

    fun setPlayingSongId(id: Long?) {
        if (playingSongId != id) {
            playingSongId = id
            notifyDataSetChanged()
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(position: Int): Boolean
    }

    class SongViewHolder(
        val binding: ItemSongBinding,
        private val listener: OnItemClickListener,
        private val longClickListener: OnItemLongClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(pos)
                }
            }
            binding.root.setOnLongClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    longClickListener?.onItemLongClick(pos) ?: false
                } else false
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding, listener, longClickListener)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.textTitle.text = song.title
        holder.binding.textArtist.text = song.artist

        if (playingSongId != null && song.id == playingSongId) {
            holder.binding.root.setBackgroundColor(Color.parseColor("#121212"))
        } else {
            holder.binding.root.setBackgroundColor(Color.TRANSPARENT)
        }

        val albumArtTarget = song.getAlbumArtTarget(holder.binding.root.context)

        Glide.with(holder.binding.root.context)
            .load(albumArtTarget)
            .circleCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .error(R.drawable.baseline_music_note_24)
            .into(holder.binding.imageAlbumArt)
    }

    override fun getItemCount(): Int = songs.size
}
