package com.example.myapplication.activites

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.FragmentThemesBinding
import com.example.myapplication.databinding.ItemThemeBinding
import com.example.myapplication.model.AppTheme
import com.example.myapplication.model.ThemeManager
import com.example.myapplication.util.ToastHelper

class ThemesFragment : Fragment() {

    private var _binding: FragmentThemesBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeAdapter: ThemeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemesBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val themeChangeListener: (AppTheme) -> Unit = { theme ->
        activity?.runOnUiThread {
            applyTheme(theme)
        }
    }

    private fun applyTheme(theme: AppTheme) {
        if (_binding == null) return
        val bgColor = Color.parseColor(theme.bgColorHex)
        binding.root.setBackgroundColor(bgColor)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val availableThemes = ThemeManager.getAvailableThemes()
        val activeThemeId = ThemeManager.getSelectedThemeId(requireContext())

        themeAdapter = ThemeAdapter(availableThemes, activeThemeId) { selectedTheme ->
            ThemeManager.setSelectedThemeId(requireContext(), selectedTheme.id)
            themeAdapter.setActiveThemeId(selectedTheme.id)
            ToastHelper.showToast(requireContext(), "Selected \"${selectedTheme.name}\" theme!")
        }

        binding.recyclerViewThemes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewThemes.adapter = themeAdapter

        ThemeManager.addThemeChangeListener(themeChangeListener)
        applyTheme(ThemeManager.getSelectedTheme(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ThemeManager.removeThemeChangeListener(themeChangeListener)
        _binding = null
    }

    private class ThemeAdapter(
        private val themes: List<AppTheme>,
        private var activeThemeId: String,
        private val onThemeSelected: (AppTheme) -> Unit
    ) : RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemThemeBinding) : RecyclerView.ViewHolder(binding.root)

        fun setActiveThemeId(id: String) {
            activeThemeId = id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = themes[position]
            holder.binding.textThemeName.text = theme.name
            holder.binding.textThemeDesc.text = theme.description

            val isSelected = theme.id == activeThemeId
            holder.binding.badgeActiveTheme.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.binding.cardTheme.setCardBackgroundColor(Color.parseColor(theme.cardColorHex))

            // Set Palette Color Dots
            setDotColor(holder.binding.dotBg, theme.bgColorHex)
            setDotColor(holder.binding.dotCard, theme.cardColorHex)
            setDotColor(holder.binding.dotAccent, theme.accentColorHex)

            holder.binding.cardTheme.setOnClickListener {
                onThemeSelected(theme)
            }
        }

        private fun setDotColor(view: View, hexColor: String) {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hexColor))
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
            view.background = drawable
        }

        override fun getItemCount(): Int = themes.size
    }
}
