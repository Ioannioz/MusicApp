package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.model.ThemeManager
import com.example.myapplication.model.UserProfile
import com.example.myapplication.util.ImageUtils

class FriendAdapter(
    private var friends: List<UserProfile>,
    private val onRemoveClick: ((UserProfile) -> Unit)? = null
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgFriendAvatar)
        val textName: TextView = itemView.findViewById(R.id.textFriendName)
        val textStatus: TextView = itemView.findViewById(R.id.textFriendStatus)
        val btnRemove: ImageView = itemView.findViewById(R.id.btnRemoveFriend)
        val layoutTrack: LinearLayout = itemView.findViewById(R.id.layoutFriendNowPlaying)
        val imgTrackArt: ImageView = itemView.findViewById(R.id.imgFriendTrackArt)
        val textTrackTitle: TextView = itemView.findViewById(R.id.textFriendTrackTitle)
        val textTrackArtist: TextView = itemView.findViewById(R.id.textFriendTrackArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_activity, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        val context = holder.itemView.context
        val theme = ThemeManager.getSelectedTheme(context)
        val cardColor = Color.parseColor(theme.cardColorHex)

        holder.cardView.setCardBackgroundColor(cardColor)

        // Load friend's avatar securely (Base64 decode or URL)
        val avatarUrl = friend.avatarUrl
        if (!avatarUrl.isNullOrEmpty()) {
            if (avatarUrl.startsWith("data:image") || avatarUrl.length > 100) {
                val avatarBitmap = ImageUtils.decodeBase64ToBitmap(avatarUrl)
                if (avatarBitmap != null) {
                    Glide.with(context)
                        .load(avatarBitmap)
                        .placeholder(R.drawable.baseline_account_circle_24)
                        .into(holder.imgAvatar)
                } else {
                    holder.imgAvatar.setImageResource(R.drawable.baseline_account_circle_24)
                }
            } else {
                Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.baseline_account_circle_24)
                    .into(holder.imgAvatar)
            }
        } else {
            holder.imgAvatar.setImageResource(R.drawable.baseline_account_circle_24)
        }

        holder.textName.text = friend.username

        holder.btnRemove.setOnClickListener {
            onRemoveClick?.invoke(friend)
        }

        // Set status text to ONLY "Online" (Green) or "Offline" (Red)
        val isOffline = friend.status.equals("Offline", ignoreCase = true)
        if (isOffline) {
            holder.textStatus.text = "Offline"
            holder.textStatus.setTextColor(Color.parseColor("#FF5252"))
            holder.layoutTrack.visibility = View.GONE
        } else {
            holder.textStatus.text = "Online"
            holder.textStatus.setTextColor(Color.parseColor("#1DB954"))

            val hasTrack = !friend.nowPlayingTitle.isNullOrEmpty()
            if (hasTrack) {
                holder.layoutTrack.visibility = View.VISIBLE
                val trackBgColor = ColorUtils.blendARGB(cardColor, Color.WHITE, 0.06f)
                holder.layoutTrack.setBackgroundColor(trackBgColor)

                holder.textTrackTitle.text = friend.nowPlayingTitle ?: ""
                holder.textTrackTitle.setTextColor(Color.WHITE)
                holder.textTrackArtist.text = friend.nowPlayingArtist ?: ""
                holder.textTrackArtist.setTextColor(Color.parseColor("#B3B3B3"))

                // Load friend's active track album art securely (Base64 decode or URL)
                val artUrl = friend.nowPlayingArtUrl
                if (!artUrl.isNullOrEmpty()) {
                    if (artUrl.startsWith("data:image") || artUrl.length > 100) {
                        val artBitmap = ImageUtils.decodeBase64ToBitmap(artUrl)
                        if (artBitmap != null) {
                            Glide.with(context)
                                .load(artBitmap)
                                .placeholder(R.drawable.baseline_music_note_24)
                                .into(holder.imgTrackArt)
                        } else {
                            holder.imgTrackArt.setImageResource(R.drawable.baseline_music_note_24)
                        }
                    } else {
                        Glide.with(context)
                            .load(artUrl)
                            .placeholder(R.drawable.baseline_music_note_24)
                            .into(holder.imgTrackArt)
                    }
                } else {
                    holder.imgTrackArt.setImageResource(R.drawable.baseline_music_note_24)
                }
            } else {
                holder.layoutTrack.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = friends.size
}
