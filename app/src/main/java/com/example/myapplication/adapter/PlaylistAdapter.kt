package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemPlaylistBinding
import com.example.myapplication.databinding.ItemPlaylistGridBinding
import com.example.myapplication.model.Playlist
import java.io.File
import java.util.Collections

enum class PlaylistViewMode {
    LIST, GRID
}

class PlaylistAdapter(
    val playlists: MutableList<Playlist>,
    private val listener: OnPlaylistClickListener,
    private val optionsListener: OnPlaylistOptionsClickListener? = null,
    var viewMode: PlaylistViewMode = PlaylistViewMode.LIST
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in playlists.indices && toPosition in playlists.indices) {
            Collections.swap(playlists, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    interface OnPlaylistClickListener {
        fun onPlaylistClick(position: Int)
    }

    interface OnPlaylistOptionsClickListener {
        fun onPlaylistOptionsClick(position: Int, view: View)
    }

    class ListPlaylistViewHolder(
        val binding: ItemPlaylistBinding,
        private val listener: OnPlaylistClickListener,
        private val optionsListener: OnPlaylistOptionsClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onPlaylistClick(pos)
                }
            }
            binding.btnPlaylistOptions.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    optionsListener?.onPlaylistOptionsClick(pos, binding.btnPlaylistOptions)
                }
            }
        }
    }

    class GridPlaylistViewHolder(
        val binding: ItemPlaylistGridBinding,
        private val listener: OnPlaylistClickListener,
        private val optionsListener: OnPlaylistOptionsClickListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            val clickHandler = View.OnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onPlaylistClick(pos)
                }
            }
            binding.root.setOnClickListener(clickHandler)
            binding.cardPlaylistIcon.setOnClickListener(clickHandler)
            binding.btnPlaylistOptions.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    optionsListener?.onPlaylistOptionsClick(pos, binding.btnPlaylistOptions)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == PlaylistViewMode.GRID) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            val binding = ItemPlaylistGridBinding.inflate(inflater, parent, false)
            GridPlaylistViewHolder(binding, listener, optionsListener)
        } else {
            val binding = ItemPlaylistBinding.inflate(inflater, parent, false)
            ListPlaylistViewHolder(binding, listener, optionsListener)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val playlist = playlists[position]
        val context = holder.itemView.context

        val nameView: TextView
        val countView: TextView
        val iconView: ImageView

        if (holder is GridPlaylistViewHolder) {
            nameView = holder.binding.textPlaylistName
            countView = holder.binding.textSongCount
            iconView = holder.binding.imagePlaylistIcon
        } else if (holder is ListPlaylistViewHolder) {
            nameView = holder.binding.textPlaylistName
            countView = holder.binding.textSongCount
            iconView = holder.binding.imagePlaylistIcon
        } else return

        nameView.text = playlist.name
        val count = playlist.songIds.size
        countView.text = if (count == 1) "1 Song" else "$count Songs"

        val customIconFile = File(context.filesDir, "playlist_icon_${playlist.id}.jpg")
        val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(context)
        val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)
        val density = context.resources.displayMetrics.density

        if (playlist.id == com.example.myapplication.model.PlaylistManager.LIKED_SONGS_PLAYLIST_ID && !customIconFile.exists()) {
            iconView.setImageResource(R.drawable.baseline_favorite_24)
            iconView.setBackgroundColor(cardColor)
            val pad = if (viewMode == PlaylistViewMode.GRID) (24 * density).toInt() else (12 * density).toInt()
            iconView.setPadding(pad, pad, pad, pad)
            iconView.setColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN)
        } else if (customIconFile.exists()) {
            iconView.background = null
            iconView.setPadding(0, 0, 0, 0)
            iconView.imageTintList = null
            iconView.clearColorFilter()
            Glide.with(context)
                .load(customIconFile)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(iconView)
        } else {
            iconView.setImageResource(R.drawable.baseline_music_note_24)
            iconView.setBackgroundColor(cardColor)
            val pad = if (viewMode == PlaylistViewMode.GRID) (24 * density).toInt() else (12 * density).toInt()
            iconView.setPadding(pad, pad, pad, pad)
            iconView.setColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }
    }

    override fun getItemCount(): Int = playlists.size
}
