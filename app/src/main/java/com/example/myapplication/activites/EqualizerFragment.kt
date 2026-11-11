package com.example.myapplication.activites

import android.content.Context
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentEqualizerBinding
import com.example.myapplication.util.GraphicEqView
import com.example.myapplication.util.ToastHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject

class EqualizerFragment : Fragment() {

    private var _binding: FragmentEqualizerBinding? = null
    private val binding get() = _binding!!

    private var equalizer: Equalizer? = null
    private val customPresets = mutableMapOf<String, FloatArray>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupEqualizerInstance()
        setupMasterSwitch()
        setupGraphicEqView()
        setupPostGainSeekBar()
        setupTopBarActions()

        binding.swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#1DB954"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            setupEqualizerInstance()
            ToastHelper.showToast(requireContext(), "Equalizer refreshed")
            binding.swipeRefreshLayout.isRefreshing = false
        }

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext()))
    }

    private fun setupEqualizerInstance() {
        val mainAct = activity as? MainActivity ?: return
        val srv = mainAct.getAudioService()
        val sessionId = srv?.getAudioSessionId() ?: 0

        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId)
            }
        } catch (_: Exception) {}
    }

    private fun setupMasterSwitch() {
        val eq = equalizer
        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("eq_enabled", true)

        try {
            eq?.enabled = isEnabled
        } catch (_: Exception) {}

        binding.switchEqualizerMaster.isChecked = isEnabled
        updateUiEnabledState(isEnabled)

        binding.switchEqualizerMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("eq_enabled", isChecked).apply()
            try {
                equalizer?.enabled = isChecked
            } catch (_: Exception) {}
            updateUiEnabledState(isChecked)
        }
    }

    private fun updateUiEnabledState(isEnabled: Boolean) {
        val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(requireContext())
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)
        val offColor = android.graphics.Color.parseColor("#888888")

        binding.graphicEqView.isEnabled = isEnabled
        binding.graphicEqView.alpha = if (isEnabled) 1.0f else 0.4f
        binding.seekBarPostGain.isEnabled = isEnabled
        binding.seekBarPostGain.alpha = if (isEnabled) 1.0f else 0.4f

        if (isEnabled) {
            binding.switchEqualizerMaster.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)
            binding.switchEqualizerMaster.trackTintList = android.content.res.ColorStateList.valueOf(accentColor)
        } else {
            binding.switchEqualizerMaster.thumbTintList = android.content.res.ColorStateList.valueOf(offColor)
            binding.switchEqualizerMaster.trackTintList = android.content.res.ColorStateList.valueOf(offColor)
        }
    }

    private fun setupGraphicEqView() {
        val eq = equalizer
        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val numBands = eq?.numberOfBands?.toInt() ?: 5
        val range = eq?.bandLevelRange

        val savedLevels = FloatArray(10) { 0.0f }
        for (i in 0 until 10) {
            val dbVal = prefs.getFloat("eq_graphic_band_$i", 0.0f)
            savedLevels[i] = dbVal

            if (eq != null && i < numBands && range != null) {
                try {
                    val mB = (dbVal * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                    eq.setBandLevel(i.toShort(), mB)
                } catch (_: Exception) {}
            }
        }

        val activePreset = prefs.getString("eq_active_preset_name", "Flat") ?: "Flat"
        binding.textCurrentPreset.text = activePreset

        binding.graphicEqView.setBandLevels(savedLevels)

        binding.graphicEqView.onBandLevelChangedListener = { bandIndex, dbLevel ->
            prefs.edit().putFloat("eq_graphic_band_$bandIndex", dbLevel)
                .putString("eq_active_preset_name", "Custom").apply()
            binding.textCurrentPreset.text = "Custom"

            if (eq != null && bandIndex < numBands && range != null) {
                try {
                    val mB = (dbLevel * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                    eq.setBandLevel(bandIndex.toShort(), mB)
                } catch (_: Exception) {}
            }
        }
    }

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    private fun setupPostGainSeekBar() {
        val mainAct = activity as? MainActivity
        val srv = mainAct?.getAudioService()
        if (srv != null) {
            try {
                loudnessEnhancer = srv.getLoudnessEnhancerInstance()
            } catch (_: Exception) {}
        }

        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val savedPostGain = prefs.getFloat("eq_post_gain", 0.0f)

        applyPostGainToEngine(savedPostGain)

        // Range: -15.0 dB to +15.0 dB -> SeekBar 0 to 1000 (500 = 0.0 dB)
        val progressVal = ((savedPostGain + 15.0f) / 30.0f * 1000f).toInt().coerceIn(0, 1000)
        binding.seekBarPostGain.progress = progressVal
        updatePostGainLabel(savedPostGain)

        var wasSnappedToZero = progressVal in 470..530

        binding.seekBarPostGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && seekBar != null) {
                    val rawDbVal = ((progress.toFloat() / 1000f) * 30.0f) - 15.0f
                    val isNearZero = progress in 470..530
                    val finalProgress: Int
                    val roundedDb: Float

                    if (isNearZero) {
                        finalProgress = 500
                        roundedDb = 0.0f
                        if (!wasSnappedToZero) {
                            wasSnappedToZero = true
                            performSnapVibration(seekBar)
                        }
                    } else {
                        wasSnappedToZero = false
                        finalProgress = progress
                        roundedDb = Math.round(rawDbVal * 10f) / 10f
                    }

                    if (seekBar.progress != finalProgress) {
                        seekBar.progress = finalProgress
                    }

                    prefs.edit().putFloat("eq_post_gain", roundedDb).apply()
                    updatePostGainLabel(roundedDb)
                    applyPostGainToEngine(roundedDb)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasSnappedToZero = binding.seekBarPostGain.progress in 470..530
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun performSnapVibration(view: View) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Exception) {
            try {
                val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(15)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyPostGainToEngine(dbVal: Float) {
        try {
            val mainAct = activity as? MainActivity ?: return
            val srv = mainAct.getAudioService() ?: return
            val enhancer = loudnessEnhancer ?: srv.getLoudnessEnhancerInstance()

            if (enhancer != null) {
                enhancer.enabled = binding.switchEqualizerMaster.isChecked
                val gainMb = (dbVal.coerceAtLeast(0f) * 100f).toInt()
                enhancer.setTargetGain(gainMb)
            }

            val player = srv.player
            if (player != null) {
                if (dbVal < 0f) {
                    val linearVolume = Math.pow(10.0, dbVal / 20.0).toFloat().coerceIn(0.1f, 1.0f)
                    player.volume = linearVolume
                } else {
                    player.volume = 1.0f
                }
            }
        } catch (_: Exception) {}
    }

    private fun updatePostGainLabel(dbVal: Float) {
        val str = if (dbVal > 0) String.format("+%.1fdB", dbVal) else String.format("%.1fdB", dbVal)
        binding.textPostGainVal.text = str
    }

    private fun setupTopBarActions() {
        // Reset Button
        binding.btnResetEq.setOnClickListener {
            val zeroLevels = FloatArray(10) { 0.0f }
            binding.graphicEqView.setBandLevels(zeroLevels)

            val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (i in 0 until 10) {
                editor.putFloat("eq_graphic_band_$i", 0.0f)
            }
            editor.putFloat("eq_post_gain", 0.0f)
            editor.putString("eq_active_preset_name", "Flat")
            editor.apply()

            binding.textCurrentPreset.text = "Flat"
            binding.seekBarPostGain.progress = 500
            updatePostGainLabel(0.0f)
            applyPostGainToEngine(0.0f)

            val eq = equalizer
            if (eq != null) {
                try {
                    for (i in 0 until eq.numberOfBands.toInt()) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                } catch (_: Exception) {}
            }

            ToastHelper.showToast(requireContext(), "Equalizer reset to Flat (0.0 dB)")
        }

        // Preset Dropdown Click
        binding.btnPresetDropdown.setOnClickListener {
            showPresetsBottomSheet()
        }
    }

    private fun showPresetsBottomSheet() {
        loadCustomPresetsFromPrefs()

        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_select_preset_sheet, null)

        val recyclerView = sheetView.findViewById<RecyclerView>(R.id.recyclerViewPresetsList)
        val btnSaveCustom = sheetView.findViewById<Button>(R.id.btnSaveCustomPreset)

        val presetList = mutableListOf<String>()
        val eq = equalizer
        if (eq != null) {
            val numPresets = eq.numberOfPresets.toInt()
            for (i in 0 until numPresets) {
                presetList.add(eq.getPresetName(i.toShort()))
            }
        } else {
            presetList.addAll(listOf("Normal", "Flat", "Bass Booster", "Treble Booster", "Rock", "Pop", "Jazz", "Classical", "Heavy Metal", "Dance"))
        }

        presetList.addAll(customPresets.keys)

        val currentActivePreset = binding.textCurrentPreset.text.toString()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = PresetListAdapter(presetList, currentActivePreset) { selectedPreset ->
            dialog.dismiss()
            applyPresetByName(selectedPreset)
        }

        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnSaveCustom, requireContext())

        btnSaveCustom.setOnClickListener {
            dialog.dismiss()
            showSaveCustomPresetDialog()
        }

        dialog.setContentView(sheetView)
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, requireContext())
        dialog.show()
    }

    private fun applyPresetByName(presetName: String) {
        val eq = equalizer
        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("eq_active_preset_name", presetName).apply()
        binding.textCurrentPreset.text = presetName

        if (customPresets.containsKey(presetName)) {
            val levels = customPresets[presetName]!!
            binding.graphicEqView.setBandLevels(levels)

            val editor = prefs.edit()
            for (i in levels.indices) {
                val dbVal = levels[i]
                editor.putFloat("eq_graphic_band_$i", dbVal)
                if (eq != null && i < eq.numberOfBands.toInt()) {
                    try {
                        val mB = (dbVal * 100f).toInt().toShort()
                        eq.setBandLevel(i.toShort(), mB)
                    } catch (_: Exception) {}
                }
            }
            editor.apply()
            ToastHelper.showToast(requireContext(), "Applied preset \"$presetName\"")
        } else if (eq != null) {
            val numPresets = eq.numberOfPresets.toInt()
            for (i in 0 until numPresets) {
                if (eq.getPresetName(i.toShort()) == presetName) {
                    try {
                        eq.usePreset(i.toShort())
                        val numBands = eq.numberOfBands.toInt()
                        val newLevels = FloatArray(10) { 0.0f }
                        val editor = prefs.edit()
                        for (b in 0 until numBands) {
                            val mB = eq.getBandLevel(b.toShort()).toFloat()
                            val dbVal = mB / 100f
                            newLevels[b] = dbVal
                            editor.putFloat("eq_graphic_band_$b", dbVal)
                        }
                        editor.apply()
                        binding.graphicEqView.setBandLevels(newLevels)
                        ToastHelper.showToast(requireContext(), "Applied preset \"$presetName\"")
                    } catch (_: Exception) {}
                    break
                }
            }
        }
    }

    private fun showSaveCustomPresetDialog() {
        val dialog = AlertDialog.Builder(requireContext()).create()
        val view = layoutInflater.inflate(R.layout.dialog_save_preset, null)

        val input = view.findViewById<EditText>(R.id.editPresetName)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelPreset)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmPreset)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                dialog.dismiss()
                saveCurrentCurveAsPreset(name)
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

    private fun saveCurrentCurveAsPreset(presetName: String) {
        val currentLevels = binding.graphicEqView.bandLevels.clone()
        customPresets[presetName] = currentLevels
        saveCustomPresetsToPrefs()
        ToastHelper.showToast(requireContext(), "Preset \"$presetName\" saved!")
    }

    private fun loadCustomPresetsFromPrefs() {
        customPresets.clear()
        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_presets_json", null) ?: return

        try {
            val jsonObj = JSONObject(jsonStr)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val jsonArr = jsonObj.getJSONArray(name)
                val levels = FloatArray(jsonArr.length()) { i -> jsonArr.getDouble(i).toFloat() }
                customPresets[name] = levels
            }
        } catch (_: Exception) {}
    }

    private fun saveCustomPresetsToPrefs() {
        val prefs = requireContext().getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val jsonObj = JSONObject()

        for ((name, levels) in customPresets) {
            val jsonArr = JSONArray()
            for (level in levels) {
                jsonArr.put(level.toDouble())
            }
            jsonObj.put(name, jsonArr)
        }

        prefs.edit().putString("custom_presets_json", jsonObj.toString()).apply()
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
        binding.cardGraphicEq.setCardBackgroundColor(cardColor)
        binding.graphicEqView.setAccentColor(accentColor)
        binding.textPostGainVal.setTextColor(accentColor)
        updateUiEnabledState(binding.switchEqualizerMaster.isChecked)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
        _binding = null
    }

    private class PresetListAdapter(
        private val presets: List<String>,
        private val activePresetName: String,
        private val onPresetSelected: (String) -> Unit
    ) : RecyclerView.Adapter<PresetListAdapter.ViewHolder>() {

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 15f
                setTextColor(android.graphics.Color.WHITE)
                val density = parent.resources.displayMetrics.density
                val pad = (14 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundResource(android.R.attr.selectableItemBackground.let {
                    val typedValue = android.util.TypedValue()
                    parent.context.theme.resolveAttribute(it, typedValue, true)
                    typedValue.resourceId
                })
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val name = presets[position]
            val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(holder.itemView.context)
            val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)

            val isSelected = name == activePresetName
            if (isSelected) {
                holder.textView.setTextColor(accentColor)
                holder.textView.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.textView.text = "✓ $name"
            } else {
                holder.textView.setTextColor(android.graphics.Color.WHITE)
                holder.textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.textView.text = name
            }

            holder.textView.setOnClickListener {
                onPresetSelected(name)
            }
        }

        override fun getItemCount(): Int = presets.size
    }
}
