package com.example.myapplication.activites

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.util.ToastHelper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.adapter.PlaylistViewMode
import com.example.myapplication.adapter.PlaylistAdapter
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.FragmentPlaylistBinding
import com.example.myapplication.model.Playlist
import com.example.myapplication.model.PlaylistManager
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Locale

class PlaylistsFragment : Fragment(), PlaylistAdapter.OnPlaylistClickListener, PlaylistAdapter.OnPlaylistOptionsClickListener {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private var playlists = mutableListOf<Playlist>()
    private lateinit var playlistAdapter: PlaylistAdapter
    private var activeEditingPlaylistPosition: Int = -1
    private var isGridMode = false

    private val playlistChangeListener: () -> Unit = {
        activity?.runOnUiThread {
            refreshPlaylists()
        }
    }

    private val pickPlaylistIconLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null && activeEditingPlaylistPosition in playlists.indices) {
                val playlist = playlists[activeEditingPlaylistPosition]
                try {
                    val file = File(requireContext().filesDir, "playlist_icon_${playlist.id}.jpg")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    playlistAdapter.notifyItemChanged(activeEditingPlaylistPosition)
                    ToastHelper.showToast(requireContext(), "Playlist icon updated")
                } catch (e: Exception) {
                    ToastHelper.showToast(requireContext(), "Failed to save icon")
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("is_playlist_grid_mode", false)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                    playlistAdapter.moveItem(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                PlaylistManager.savePlaylists(requireContext(), playlists, notify = false)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewPlaylist)

        PlaylistManager.addChangeListener(playlistChangeListener)

        binding.btnToggleLayout.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("is_playlist_grid_mode", isGridMode).apply()
            updateViewModeUI()
            refreshPlaylists()
        }

        updateViewModeUI()
        refreshPlaylists()
        setupSearch()

        binding.swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1DB954"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshPlaylists()
            ToastHelper.showToast(requireContext(), "Playlists refreshed")
            binding.swipeRefreshLayout.isRefreshing = false
        }

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext()))

        binding.btnCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            if (binding.searchBarContainer.visibility == View.VISIBLE) {
                binding.searchBarContainer.visibility = View.GONE
                binding.editSearchPlaylists.setText("")
            } else {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.editSearchPlaylists.requestFocus()
            }
        }

        binding.editSearchPlaylists.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterPlaylists(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterPlaylists(query: String) {
        if (_binding == null) return
        val allPlaylists = PlaylistManager.getPlaylists(requireContext())

        val filtered = if (query.isEmpty()) {
            allPlaylists
        } else {
            allPlaylists.filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
        }

        val mode = if (isGridMode) PlaylistViewMode.GRID else PlaylistViewMode.LIST
        playlistAdapter = PlaylistAdapter(filtered.toMutableList(), this, this, mode)
        binding.recyclerViewPlaylist.adapter = playlistAdapter
    }

    private fun updateViewModeUI() {
        if (isGridMode) {
            binding.recyclerViewPlaylist.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.btnToggleLayout.setImageResource(R.drawable.baseline_view_list_24)
        } else {
            binding.recyclerViewPlaylist.layoutManager = LinearLayoutManager(requireContext())
            binding.btnToggleLayout.setImageResource(R.drawable.baseline_grid_view_24)
        }
    }

    fun refreshPlaylists() {
        if (_binding == null) return
        playlists = PlaylistManager.getPlaylists(requireContext())
        val mode = if (isGridMode) PlaylistViewMode.GRID else PlaylistViewMode.LIST
        playlistAdapter = PlaylistAdapter(playlists, this, this, mode)
        binding.recyclerViewPlaylist.adapter = playlistAdapter
    }

    private fun showCreatePlaylistDialog() {
        val dialog = AlertDialog.Builder(requireContext()).create()
        val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

        val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
        val input = view.findViewById<EditText>(R.id.dialogInputField)
        val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
        val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

        titleText.text = "New Playlist"
        input.hint = "Playlist Name"
        btnConfirm.text = "Create"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                dialog.dismiss()
                val newPlaylist = Playlist(
                    id = System.currentTimeMillis(),
                    name = name,
                    songIds = mutableListOf()
                )
                playlists.add(newPlaylist)
                PlaylistManager.savePlaylists(requireContext(), playlists)
                playlistAdapter.notifyItemInserted(playlists.size - 1)
                ToastHelper.showToast(requireContext(), "Playlist created")
            } else {
                ToastHelper.showToast(requireContext(), "Name cannot be empty")
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, requireContext())
        com.example.myapplication.util.DialogThemeHelper.styleEditText(input, requireContext())
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, requireContext())
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, requireContext())
        dialog.show()
    }

    override fun onPlaylistClick(position: Int) {
        if (position !in playlists.indices) return
        val playlist = playlists[position]
        showPlaylistDetailBottomSheet(playlist)
    }

    private fun showPlaylistDetailBottomSheet(playlist: Playlist) {
        val mainAct = activity as? MainActivity ?: return
        val allSongs = mainAct.getSongList()
        val songMap = allSongs.associateBy { it.id }
        val playlistSongs = playlist.songIds.mapNotNull { songMap[it] }.toMutableList()

        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_playlist_detail, null)

        val iconImg = sheetView.findViewById<ImageView>(R.id.detailPlaylistIcon)
        val nameText = sheetView.findViewById<TextView>(R.id.detailPlaylistName)
        val countText = sheetView.findViewById<TextView>(R.id.detailPlaylistCount)
        val emptyText = sheetView.findViewById<TextView>(R.id.detailEmptyText)
        val recyclerView = sheetView.findViewById<RecyclerView>(R.id.recyclerViewPlaylistSongs)
        val btnPlay = sheetView.findViewById<ImageView>(R.id.detailBtnPlay)
        val btnShuffle = sheetView.findViewById<ImageView>(R.id.detailBtnShuffle)
        val btnSort = sheetView.findViewById<ImageView>(R.id.detailBtnSort)

        val miniLayout = sheetView.findViewById<ConstraintLayout>(R.id.detailMiniPlayerLayout)
        val miniArt = sheetView.findViewById<ImageView>(R.id.detailMiniAlbumArt)
        val miniTitle = sheetView.findViewById<TextView>(R.id.detailMiniTitle)
        val miniArtist = sheetView.findViewById<TextView>(R.id.detailMiniArtist)
        val miniBtnPlay = sheetView.findViewById<ImageView>(R.id.detailMiniBtnPlay)
        val miniBtnPrev = sheetView.findViewById<ImageView>(R.id.detailMiniBtnPrev)
        val miniBtnNext = sheetView.findViewById<ImageView>(R.id.detailMiniBtnNext)
        val miniSeekBar = sheetView.findViewById<SeekBar>(R.id.detailMiniSeekBar)
        val miniElapsed = sheetView.findViewById<TextView>(R.id.detailMiniTextElapsed)

        var isUserScrubbingSheetMini = false
        val handler = Handler(Looper.getMainLooper())

        val updateSheetMiniProgressRunnable = object : Runnable {
            override fun run() {
                val srv = mainAct.getAudioService()
                val p = srv?.player
                if (dialog.isShowing && srv != null && p != null) {
                    val currentPosition = p.currentPosition
                    val duration = p.duration
                    if (duration > 0 && !isUserScrubbingSheetMini) {
                        val progressRatio = currentPosition.toFloat() / duration.toFloat()
                        miniSeekBar.progress = (progressRatio * 1000).toInt()

                        val seconds = (currentPosition / 1000).toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        miniElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                    }
                    if (p.isPlaying) {
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }

        val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext())
        val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)
        val isLightAccent = theme.accentColorHex.equals("#FFFFFF", ignoreCase = true) || theme.accentColorHex.equals("#DBD8E3", ignoreCase = true)
        val playBtnTint = if (isLightAccent) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        btnPlay.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        btnPlay.setColorFilter(playBtnTint)
        miniLayout.setBackgroundColor(cardColor)

        val density = requireContext().resources.displayMetrics.density

        nameText.text = playlist.name
        val count = playlistSongs.size
        countText.text = if (count == 1) "1 Song" else "$count Songs"

        val customIconFile = File(requireContext().filesDir, "playlist_icon_${playlist.id}.jpg")
        if (playlist.id == PlaylistManager.LIKED_SONGS_PLAYLIST_ID && !customIconFile.exists()) {
            iconImg.setImageResource(R.drawable.baseline_favorite_24)
            iconImg.setBackgroundColor(cardColor)
            val pad = (14 * density).toInt()
            iconImg.setPadding(pad, pad, pad, pad)
            iconImg.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
        } else if (customIconFile.exists()) {
            iconImg.background = null
            iconImg.setPadding(0, 0, 0, 0)
            iconImg.imageTintList = null
            iconImg.clearColorFilter()
            Glide.with(requireContext())
                .load(customIconFile)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(iconImg)
        } else {
            iconImg.setImageResource(R.drawable.baseline_music_note_24)
            iconImg.setBackgroundColor(cardColor)
            val pad = (12 * density).toInt()
            iconImg.setPadding(pad, pad, pad, pad)
            iconImg.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
        }

        val updateControlIcons = {
            val srv = mainAct.getAudioService()
            val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext())
            val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)
            val isLightAccent = theme.accentColorHex.equals("#FFFFFF", ignoreCase = true) || theme.accentColorHex.equals("#DBD8E3", ignoreCase = true)
            val activeIconTint = if (isLightAccent) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val isPlaying = srv?.player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
            val isPlaylistLoaded = srv?.let { service ->
                service.songList.size == playlistSongs.size && service.songList.map { it.id } == playlistSongs.map { it.id }
            } == true

            btnPlay.setImageResource(
                if (isPlaying && isPlaylistLoaded) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
            )

            if (srv?.isShuffle == true && isPlaylistLoaded) {
                btnShuffle.setBackgroundResource(R.drawable.circle_white_bg)
                btnShuffle.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
                btnShuffle.setColorFilter(activeIconTint, PorterDuff.Mode.SRC_IN)
            } else {
                btnShuffle.background = null
                btnShuffle.clearColorFilter()
            }
        }

        val updateSheetMiniUI = {
            val srv = mainAct.getAudioService()
            val song = srv?.getCurrentSong()
            if (srv != null && song != null) {
                miniLayout.visibility = View.VISIBLE
                val newTitle = song.title ?: "Unknown Song"
                if (miniTitle.text != newTitle) {
                    miniTitle.text = newTitle
                    miniTitle.isSelected = true
                }
                miniArtist.text = song.artist ?: "Unknown Artist"

                val isPlaying = srv.player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
                miniBtnPlay.setImageResource(
                    if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
                )

                Glide.with(requireContext())
                    .load(song.getAlbumArtTarget(requireContext()))
                    .centerCrop()
                    .placeholder(R.drawable.baseline_music_note_24)
                    .into(miniArt)

                handler.removeCallbacks(updateSheetMiniProgressRunnable)
                handler.post(updateSheetMiniProgressRunnable)
            } else {
                miniLayout.visibility = View.GONE
                handler.removeCallbacks(updateSheetMiniProgressRunnable)
            }
        }

        val playlistSheetStateListener: () -> Unit = {
            activity?.runOnUiThread {
                updateControlIcons()
                updateSheetMiniUI()
            }
        }

        val srv = mainAct.getAudioService()
        srv?.addStateChangeListener(playlistSheetStateListener)

        dialog.setOnDismissListener {
            srv?.removeStateChangeListener(playlistSheetStateListener)
            handler.removeCallbacks(updateSheetMiniProgressRunnable)
        }

        updateControlIcons()
        updateSheetMiniUI()

        miniLayout.setOnClickListener {
            val currentSrv = mainAct.getAudioService() ?: return@setOnClickListener
            if (currentSrv.getCurrentSong() == null) return@setOnClickListener
            dialog.dismiss()
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("songList", ArrayList(currentSrv.getActiveList()))
                putExtra("position", currentSrv.currentIndex)
            }
            startActivity(intent)
        }

        miniBtnPlay.setOnClickListener {
            mainAct.getAudioService()?.togglePlayPause()
            updateControlIcons()
            updateSheetMiniUI()
        }

        miniBtnPrev.setOnClickListener {
            mainAct.getAudioService()?.playPrevious()
            updateControlIcons()
            updateSheetMiniUI()
        }

        miniBtnNext.setOnClickListener {
            mainAct.getAudioService()?.playNext()
            updateControlIcons()
            updateSheetMiniUI()
        }

        miniSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val currentSrv = mainAct.getAudioService() ?: return
                    val p = currentSrv.player ?: return
                    val duration = p.duration
                    if (duration > 0) {
                        val seekMs = (progress.toFloat() / 1000f * duration).toLong()
                        val seconds = (seekMs / 1000).toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        miniElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingSheetMini = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val currentSrv = mainAct.getAudioService() ?: return
                val p = currentSrv.player ?: return
                val duration = p.duration
                if (duration > 0 && seekBar != null) {
                    val targetMs = (seekBar.progress.toFloat() / 1000f * duration).toLong()
                    p.seekTo(targetMs)
                }
                isUserScrubbingSheetMini = false
            }
        })

        btnPlay.setOnClickListener {
            if (playlistSongs.isNotEmpty()) {
                val serviceIntent = Intent(requireContext(), AudioService::class.java)
                requireContext().startService(serviceIntent)

                val currentSrv = mainAct.getAudioService()
                if (currentSrv != null) {
                    val isPlaylistLoaded = currentSrv.songList.size == playlistSongs.size &&
                            currentSrv.songList.map { it.id } == playlistSongs.map { it.id }

                    if (isPlaylistLoaded && currentSrv.getCurrentSong() != null) {
                        currentSrv.togglePlayPause()
                    } else {
                        currentSrv.userQueue.clear()
                        currentSrv.playNewList(playlistSongs, 0)
                    }
                    updateControlIcons()
                    updateSheetMiniUI()
                } else {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("songList", ArrayList(playlistSongs))
                        putExtra("position", 0)
                        putExtra("playlistName", playlist.name)
                    }
                    startActivity(intent)
                }
            } else {
                ToastHelper.showToast(requireContext(), "Playlist is empty")
            }
        }

        btnShuffle.setOnClickListener {
            if (playlistSongs.isNotEmpty()) {
                val serviceIntent = Intent(requireContext(), AudioService::class.java)
                requireContext().startService(serviceIntent)

                val currentSrv = mainAct.getAudioService()
                if (currentSrv != null) {
                    val isPlaylistLoaded = currentSrv.songList.size == playlistSongs.size &&
                            currentSrv.songList.map { it.id } == playlistSongs.map { it.id }

                    if (!isPlaylistLoaded) {
                        currentSrv.userQueue.clear()
                        currentSrv.isShuffle = true
                        currentSrv.playNewList(playlistSongs, 0)
                    } else {
                        currentSrv.toggleShuffle()
                    }
                    updateControlIcons()
                    updateSheetMiniUI()
                } else {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("songList", ArrayList(playlistSongs))
                        putExtra("position", 0)
                        putExtra("playlistName", playlist.name)
                    }
                    startActivity(intent)
                }
            } else {
                ToastHelper.showToast(requireContext(), "Playlist is empty")
            }
        }

        btnSort.setOnClickListener {
            if (playlistSongs.isNotEmpty()) {
                val sortDialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
                val sortSheetView = layoutInflater.inflate(R.layout.dialog_sort_options_bottom_sheet, null)

                val optionByName = sortSheetView.findViewById<LinearLayout>(R.id.optionSortByName)
                val optionByArtist = sortSheetView.findViewById<LinearLayout>(R.id.optionSortByArtist)

                optionByName.setOnClickListener {
                    sortDialog.dismiss()
                    val sorted = playlistSongs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" })
                    playlistSongs.clear()
                    playlistSongs.addAll(sorted)
                    playlist.songIds.clear()
                    playlist.songIds.addAll(playlistSongs.map { song -> song.id })
                    PlaylistManager.savePlaylists(requireContext(), playlists)
                    recyclerView.adapter?.notifyDataSetChanged()
                    ToastHelper.showToast(requireContext(), "Sorted playlist by name")
                }

                optionByArtist.setOnClickListener {
                    sortDialog.dismiss()
                    val sorted = playlistSongs.sortedWith(
                        compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.artist ?: "" }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" }
                    )
                    playlistSongs.clear()
                    playlistSongs.addAll(sorted)
                    playlist.songIds.clear()
                    playlist.songIds.addAll(playlistSongs.map { song -> song.id })
                    PlaylistManager.savePlaylists(requireContext(), playlists)
                    recyclerView.adapter?.notifyDataSetChanged()
                    ToastHelper.showToast(requireContext(), "Sorted playlist by artist")
                }

                sortDialog.setContentView(sortSheetView)
                val bottomSheet = sortDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.setBackgroundResource(android.R.color.transparent)
                sortDialog.show()
            } else {
                ToastHelper.showToast(requireContext(), "Playlist is empty")
            }
        }

        if (playlistSongs.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = LinearLayoutManager(requireContext())

            var adapter: SongAdapter? = null
            adapter = SongAdapter(playlistSongs, object : SongAdapter.OnItemClickListener {
                override fun onItemClick(position: Int) {
                    dialog.dismiss()
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("songList", ArrayList(playlistSongs))
                        putExtra("position", position)
                        putExtra("playlistName", playlist.name)
                    }
                    startActivity(intent)
                }
            }, object : SongAdapter.OnOptionsClickListener {
                override fun onOptionsClick(position: Int, view: View) {
                    if (position in playlistSongs.indices) {
                        val song = playlistSongs[position]
                        showPlaylistSongOptionsMenu(song, position, playlist, playlistSongs, adapter!!, countText, emptyText, recyclerView)
                    }
                }
            })
            recyclerView.adapter = adapter

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = viewHolder.bindingAdapterPosition
                    val toPos = target.bindingAdapterPosition
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                        adapter.moveItem(fromPos, toPos)
                        return true
                    }
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    playlist.songIds.clear()
                    playlist.songIds.addAll(adapter.songs.map { it.id })
                    PlaylistManager.savePlaylists(requireContext(), playlists)

                    val currentSrv = mainAct.getAudioService()
                    if (currentSrv != null && currentSrv.songList == playlistSongs) {
                        currentSrv.songList = adapter.songs.toMutableList()
                    }
                }
            })
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        dialog.setContentView(sheetView)
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val displayMetrics = requireContext().resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val topOffset = (screenHeight * 0.05f).toInt()
            val sheetHeight = screenHeight - topOffset
            val peekH = (screenHeight * 0.50f).toInt()

            bottomSheet.updateLayoutParams<ViewGroup.LayoutParams> {
                height = sheetHeight
            }

            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isFitToContents = false
            behavior.expandedOffset = 0
            behavior.peekHeight = peekH
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, requireContext())
        dialog.show()
    }

    override fun onPlaylistOptionsClick(position: Int, view: View) {
        if (position !in playlists.indices) return
        val playlist = playlists[position]

        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_playlist_options_bottom_sheet, null)

        val sheetIcon = sheetView.findViewById<ImageView>(R.id.sheetPlaylistIcon)
        val sheetName = sheetView.findViewById<TextView>(R.id.sheetPlaylistName)
        val sheetCount = sheetView.findViewById<TextView>(R.id.sheetPlaylistCount)

        val optionRename = sheetView.findViewById<LinearLayout>(R.id.optionRenamePlaylist)
        val optionChangeIcon = sheetView.findViewById<LinearLayout>(R.id.optionChangePlaylistIcon)
        val optionDelete = sheetView.findViewById<LinearLayout>(R.id.optionDeletePlaylist)

        sheetName.text = playlist.name
        val count = playlist.songIds.size
        sheetCount.text = if (count == 1) "1 Song" else "$count Songs"

        val customIconFile = File(requireContext().filesDir, "playlist_icon_${playlist.id}.jpg")
        if (playlist.id == PlaylistManager.LIKED_SONGS_PLAYLIST_ID && !customIconFile.exists()) {
            sheetIcon.setImageResource(R.drawable.ic_favorites_playlist)
            sheetIcon.background = null
            sheetIcon.setPadding(0, 0, 0, 0)
            sheetIcon.imageTintList = null
            sheetIcon.clearColorFilter()
        } else if (customIconFile.exists()) {
            sheetIcon.background = null
            sheetIcon.setPadding(0, 0, 0, 0)
            sheetIcon.imageTintList = null
            sheetIcon.clearColorFilter()
            Glide.with(requireContext())
                .load(customIconFile)
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(sheetIcon)
        } else {
            val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext())
            val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
            sheetIcon.setImageResource(R.drawable.baseline_music_note_24)
            sheetIcon.setBackgroundColor(cardColor)
            val density = requireContext().resources.displayMetrics.density
            val pad = (12 * density).toInt()
            sheetIcon.setPadding(pad, pad, pad, pad)
            sheetIcon.setColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        }

        optionRename.setOnClickListener {
            dialog.dismiss()
            if (playlist.id == PlaylistManager.LIKED_SONGS_PLAYLIST_ID) {
                ToastHelper.showToast(requireContext(), "Favourites playlist cannot be renamed")
            } else {
                showRenamePlaylistDialog(position)
            }
        }

        optionChangeIcon.setOnClickListener {
            dialog.dismiss()
            activeEditingPlaylistPosition = position
            pickPlaylistIconLauncher.launch("image/*")
        }

        optionDelete.setOnClickListener {
            dialog.dismiss()
            if (playlist.id == PlaylistManager.LIKED_SONGS_PLAYLIST_ID) {
                ToastHelper.showToast(requireContext(), "Favourites playlist cannot be deleted")
            } else {
                showDeletePlaylistDialog(position)
            }
        }

        dialog.setContentView(sheetView)
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, requireContext())
        dialog.show()
    }

    private fun showRenamePlaylistDialog(position: Int) {
        if (position !in playlists.indices) return
        val playlist = playlists[position]

        val dialog = AlertDialog.Builder(requireContext()).create()
        val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

        val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
        val input = view.findViewById<EditText>(R.id.dialogInputField)
        val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
        val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

        titleText.text = "Rename Playlist"
        input.hint = "Playlist Name"
        input.setText(playlist.name)
        btnConfirm.text = "Save"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                dialog.dismiss()
                playlists[position] = Playlist(playlist.id, newName, playlist.songIds)
                PlaylistManager.savePlaylists(requireContext(), playlists)
                playlistAdapter.notifyItemChanged(position)
                ToastHelper.showToast(requireContext(), "Playlist renamed")
            } else {
                ToastHelper.showToast(requireContext(), "Name cannot be empty")
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, requireContext())
        com.example.myapplication.util.DialogThemeHelper.styleEditText(input, requireContext())
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, requireContext())
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, requireContext())
        dialog.show()
    }

    private fun showDeletePlaylistDialog(position: Int) {
        if (position !in playlists.indices) return
        val playlist = playlists[position]

        val dialog = AlertDialog.Builder(requireContext()).create()
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)

        val titleText = view.findViewById<TextView>(R.id.dialogConfirmTitle)
        val messageText = view.findViewById<TextView>(R.id.dialogConfirmMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnConfirmCancel)
        val btnDelete = view.findViewById<Button>(R.id.btnConfirmDelete)

        titleText.text = "Delete Playlist"
        messageText.text = "Are you sure you want to delete \"${playlist.name}\"?"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            val iconFile = File(requireContext().filesDir, "playlist_icon_${playlist.id}.jpg")
            if (iconFile.exists()) {
                iconFile.delete()
            }
            playlists.removeAt(position)
            PlaylistManager.savePlaylists(requireContext(), playlists)
            playlistAdapter.notifyItemRemoved(position)
            ToastHelper.showToast(requireContext(), "Playlist deleted")
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, requireContext())
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, requireContext())
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnDelete, requireContext())
        dialog.show()
    }

    private fun showPlaylistSongOptionsMenu(
        song: Song,
        position: Int,
        playlist: Playlist,
        playlistSongs: MutableList<Song>,
        adapter: SongAdapter,
        countText: TextView,
        emptyText: TextView,
        recyclerView: RecyclerView
    ) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_playlist_song_options_bottom_sheet, null)

        val sheetAlbumArt = sheetView.findViewById<ImageView>(R.id.sheetAlbumArt)
        val sheetTitle = sheetView.findViewById<TextView>(R.id.sheetSongTitle)
        val sheetArtist = sheetView.findViewById<TextView>(R.id.sheetSongArtist)

        val optionRemoveFromPlaylist = sheetView.findViewById<LinearLayout>(R.id.optionRemoveFromPlaylist)

        sheetTitle.text = song.title ?: "Unknown Song"
        sheetArtist.text = song.artist ?: "Unknown Artist"

        Glide.with(this)
            .load(song.getAlbumArtTarget(requireContext()))
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .into(sheetAlbumArt)

        optionRemoveFromPlaylist.setOnClickListener {
            dialog.dismiss()
            if (position in playlistSongs.indices) {
                playlistSongs.removeAt(position)
                playlist.songIds.clear()
                playlist.songIds.addAll(playlistSongs.map { it.id })
                PlaylistManager.savePlaylists(requireContext(), playlists)

                adapter.notifyItemRemoved(position)

                val newCount = playlistSongs.size
                countText.text = if (newCount == 1) "1 Song" else "$newCount Songs"

                if (playlistSongs.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }

                ToastHelper.showToast(requireContext(), "Removed \"${song.title ?: "Song"}\" from playlist")
            }
        }

        dialog.setContentView(sheetView)
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, requireContext())
        dialog.show()
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

        com.example.myapplication.util.DialogThemeHelper.styleEditText(binding.editSearchPlaylists, requireContext())
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(binding.btnCreatePlaylist, requireContext())

        if (::playlistAdapter.isInitialized) {
            playlistAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
        PlaylistManager.removeChangeListener(playlistChangeListener)
        _binding = null
    }
}
