package com.example.myapplication.adapter

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSongBinding
import com.example.myapplication.model.Song

class SongAdapter(
    private val songs: List<Song>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    class SongViewHolder(
        val binding: ItemSongBinding,
        private val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.textTitle.text = song.title
        holder.binding.textArtist.text = song.artist

        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )

        Glide.with(holder.binding.root.context)
            .load(albumArtUri)
            .circleCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .error(R.drawable.baseline_music_note_24)
            .into(holder.binding.imageAlbumArt)
    }

    override fun getItemCount(): Int = songs.size
}
