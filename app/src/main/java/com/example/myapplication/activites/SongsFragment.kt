package com.example.myapplication.activites

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.example.myapplication.util.ToastHelper
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.adapter.SongViewMode
import com.example.myapplication.databinding.FragmentSongsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class SongsFragment : Fragment(), SongAdapter.OnItemClickListener, SongAdapter.OnOptionsClickListener {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    var songAdapter: SongAdapter? = null
    private var isGridMode = false

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, ItemTouchHelper.RIGHT
    ) {
        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
            return 0.40f
        }

        override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
            return defaultValue * 0.4f
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                songAdapter?.moveItem(fromPos, toPos)
                val mainAct = activity as? MainActivity
                mainAct?.onSongReordered(fromPos, toPos)
                return true
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val pos = viewHolder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && direction == ItemTouchHelper.RIGHT) {
                val mainAct = activity as? MainActivity
                mainAct?.addSongToQueueByPosition(pos)
                viewHolder.itemView.translationX = 0f
                songAdapter?.notifyItemChanged(pos)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.translationX = 0f
            viewHolder.itemView.scaleX = 1f
            viewHolder.itemView.scaleY = 1f
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                val itemView = viewHolder.itemView
                val itemWidth = itemView.width.toFloat()
                val itemHeight = itemView.height.toFloat()
                val maxSwipeDistance = itemWidth * 0.65f
                val threshold40 = itemWidth * 0.40f
                val limitedDX = if (!isCurrentlyActive && viewHolder.itemView.translationX == 0f) 0f else dX.coerceAtMost(maxSwipeDistance)

                if (limitedDX > 2f) {
                    viewHolder.itemView.translationX = limitedDX

                    val isPastThreshold = limitedDX >= threshold40

                    if (isGridMode) {
                        // Distinct Card Swipe Feedback for Big Icons (Grid Mode)
                        val swipeProgress = (limitedDX / maxSwipeDistance).coerceIn(0f, 1f)
                        val cardScale = 1f - (swipeProgress * 0.06f)
                        viewHolder.itemView.scaleX = cardScale
                        viewHolder.itemView.scaleY = cardScale

                        val backgroundColor = if (isPastThreshold) Color.parseColor("#1DB954") else Color.parseColor("#331DB954")
                        val iconTintColor = if (isPastThreshold) Color.WHITE else Color.parseColor("#B3FFFFFF")

                        val cornerRadius = 12f * recyclerView.resources.displayMetrics.density
                        val bgRect = RectF(
                            itemView.left.toFloat() + 8f,
                            itemView.top.toFloat() + 8f,
                            itemView.left.toFloat() + limitedDX,
                            itemView.bottom.toFloat() - 8f
                        )

                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = backgroundColor
                            style = Paint.Style.FILL
                        }

                        c.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

                        val icon = ContextCompat.getDrawable(recyclerView.context, R.drawable.baseline_playlist_add_24)?.mutate()
                        if (icon != null) {
                            icon.setTint(iconTintColor)
                            val iconSize = (28 * recyclerView.resources.displayMetrics.density * (if (isPastThreshold) 1.25f else 1.0f)).toInt()
                            val iconLeft = (itemView.left + limitedDX / 2f - iconSize / 2f).toInt()
                            val iconTop = (itemView.top + itemHeight / 2f - iconSize / 2f).toInt()

                            if (iconLeft > itemView.left) {
                                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                                icon.draw(c)
                            }
                        }
                    } else {
                        // Standard Row Swipe Feedback for List Mode
                        viewHolder.itemView.scaleX = 1f
                        viewHolder.itemView.scaleY = 1f

                        val backgroundColor = if (isPastThreshold) Color.parseColor("#1DB954") else Color.parseColor("#2A2A2A")
                        val iconTintColor = if (isPastThreshold) Color.WHITE else Color.parseColor("#80CCCCCC")

                        val bgPaint = Paint().apply {
                            color = backgroundColor
                        }

                        val background = RectF(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            itemView.left.toFloat() + limitedDX,
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, bgPaint)

                        val icon = ContextCompat.getDrawable(recyclerView.context, R.drawable.baseline_playlist_add_24)?.mutate()
                        if (icon != null) {
                            icon.setTint(iconTintColor)
                            val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                            val iconTop = itemView.top + iconMargin
                            val iconBottom = iconTop + icon.intrinsicHeight
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + icon.intrinsicWidth

                            if (limitedDX > iconMargin) {
                                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                                icon.draw(c)
                            }
                        }
                    }
                } else {
                    viewHolder.itemView.translationX = 0f
                    viewHolder.itemView.scaleX = 1f
                    viewHolder.itemView.scaleY = 1f
                }
            } else {
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("is_grid_mode", false)

        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.btnSortSongs.setOnClickListener {
            showSortOptionsBottomSheet()
        }

        binding.btnToggleLayout.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("is_grid_mode", isGridMode).apply()
            updateViewModeUI()
            refreshSongsList()
        }

        updateViewModeUI()
        updateSortLabel()
        refreshSongsList()
        setupSearch()

        binding.swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1DB954"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            val mainAct = activity as? MainActivity
            mainAct?.reloadSongs()
            ToastHelper.showToast(requireContext(), "Library refreshed")
            binding.swipeRefreshLayout.isRefreshing = false
        }

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext()))
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            if (binding.searchBarContainer.visibility == View.VISIBLE) {
                binding.searchBarContainer.visibility = View.GONE
                binding.editSearchSongs.setText("")
            } else {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.editSearchSongs.requestFocus()
            }
        }

        binding.editSearchSongs.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterSongs(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterSongs(query: String) {
        val mainAct = activity as? MainActivity ?: return
        val allSongs = mainAct.getSongList()

        val filtered = if (query.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { song ->
                (song.title?.contains(query, ignoreCase = true) == true) ||
                (song.artist?.contains(query, ignoreCase = true) == true)
            }
        }

        val mode = if (isGridMode) SongViewMode.GRID else SongViewMode.LIST
        songAdapter = SongAdapter(filtered.toMutableList(), this, this, mode)
        binding.recyclerView.adapter = songAdapter
    }

    private fun updateViewModeUI() {
        if (isGridMode) {
            binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.btnToggleLayout.setImageResource(R.drawable.baseline_view_list_24)
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.btnToggleLayout.setImageResource(R.drawable.baseline_grid_view_24)
        }
    }

    private fun updateSortLabel() {
        val prefs = requireContext().getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        val mode = prefs.getString("current_sort_mode", "name")
        binding.textSortSelected.text = if (mode == "artist") "Sort by Artist" else "Sort by Name"
    }

    private fun showSortOptionsBottomSheet() {
        val mainAct = activity as? MainActivity ?: return
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_sort_options_bottom_sheet, null)

        val optionByName = sheetView.findViewById<LinearLayout>(R.id.optionSortByName)
        val optionByArtist = sheetView.findViewById<LinearLayout>(R.id.optionSortByArtist)

        optionByName.setOnClickListener {
            dialog.dismiss()
            mainAct.sortSongsByName()
            updateSortLabel()
            refreshSongsList()
            ToastHelper.showToast(requireContext(), "Sorted by name")
        }

        optionByArtist.setOnClickListener {
            dialog.dismiss()
            mainAct.sortSongsByArtist()
            updateSortLabel()
            refreshSongsList()
            ToastHelper.showToast(requireContext(), "Sorted by artist")
        }

        dialog.setContentView(sheetView)
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, requireContext())
        dialog.show()
    }

    fun refreshSongsList() {
        val mainAct = activity as? MainActivity ?: return
        val songs = mainAct.getSongList()
        val mode = if (isGridMode) SongViewMode.GRID else SongViewMode.LIST
        songAdapter = SongAdapter(songs.toMutableList(), this, this, mode)
        binding.recyclerView.adapter = songAdapter
        mainAct.updateMiniPlayerUI()
    }

    override fun onItemClick(position: Int) {
        val mainAct = activity as? MainActivity ?: return
        val adapterSongs = songAdapter?.songs ?: return
        if (position !in adapterSongs.indices) return

        val srv = mainAct.getAudioService()
        if (srv != null) {
            val targetSong = adapterSongs[position]
            val isSameSongAndList = srv.getCurrentSong()?.id == targetSong.id &&
                    srv.songList.size == adapterSongs.size &&
                    srv.songList.map { it.id } == adapterSongs.map { it.id }

            if (!isSameSongAndList) {
                srv.userQueue.clear()
                srv.playNewList(adapterSongs, position)
            }
        }

        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("songList", ArrayList(adapterSongs))
            putExtra("position", position)
            putExtra("playlistName", "Device Files")
        }
        startActivity(intent)
    }

    override fun onOptionsClick(position: Int, view: View) {
        val mainAct = activity as? MainActivity ?: return
        mainAct.showSongOptionsMenu(position, view)
    }

    private val themeChangeListener: (com.example.myapplication.model.AppTheme) -> Unit = { theme ->
        activity?.runOnUiThread {
            applyTheme(theme)
        }
    }

    private fun applyTheme(theme: com.example.myapplication.model.AppTheme) {
        if (_binding == null) return
        val bgColor = Color.parseColor(theme.bgColorHex)
        val accentColor = Color.parseColor(theme.accentColorHex)

        binding.root.setBackgroundColor(bgColor)
        binding.btnSearch.setColorFilter(accentColor)
        binding.btnToggleLayout.setColorFilter(accentColor)

        com.example.myapplication.util.DialogThemeHelper.styleEditText(binding.editSearchSongs, requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
        _binding = null
    }
}
