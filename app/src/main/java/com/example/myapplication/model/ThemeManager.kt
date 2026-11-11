package com.example.myapplication.model

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

data class AppTheme(
    val id: String,
    val name: String,
    val description: String,
    val bgColorHex: String,
    val cardColorHex: String,
    val accentColorHex: String
)

object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_ID = "current_app_theme"

    private val themeChangeListeners = CopyOnWriteArrayList<(AppTheme) -> Unit>()

    val THEME_BLACK_WHITE = AppTheme(
        id = "black_white",
        name = "Monochrome",
        description = "Black, Dark Gray, White",
        bgColorHex = "#000000",
        cardColorHex = "#121212",
        accentColorHex = "#FFFFFF"
    )

    val THEME_NEON_EMERALD = AppTheme(
        id = "neon_emerald",
        name = "Forest & Ember",
        description = "Forest Green, Dark Emerald, Golden Yellow",
        bgColorHex = "#0C3B2E",
        cardColorHex = "#184A3B",
        accentColorHex = "#FFBA00"
    )

    val THEME_GRENADINE_LATTE = AppTheme(
        id = "grenadine_latte",
        name = "Grenadine & Latte",
        description = "Espresso Brown, Cafe Latte, Beeswax Gold",
        bgColorHex = "#2A1A14",
        cardColorHex = "#4A2C21",
        accentColorHex = "#EAB05C"
    )

    val THEME_AMETHYST_DARK = AppTheme(
        id = "amethyst_dark",
        name = "Velvet Amethyst",
        description = "Velvet Purple, Slate Purple, Soft Lavender",
        bgColorHex = "#2A2438",
        cardColorHex = "#352F44",
        accentColorHex = "#DBD8E3"
    )

    val THEME_OBSIDIAN_CRIMSON = AppTheme(
        id = "obsidian_crimson",
        name = "Obsidian Crimson",
        description = "Obsidian Black, Deep Maroon, Fiery Crimson",
        bgColorHex = "#2B0128",
        cardColorHex = "#6D0122",
        accentColorHex = "#EC0123"
    )

    val THEME_OCEAN_ABYSS = AppTheme(
        id = "ocean_abyss",
        name = "Ocean Abyss",
        description = "Midnight Abyss, Royal Navy, Luminous Cobalt",
        bgColorHex = "#000521",
        cardColorHex = "#020C47",
        accentColorHex = "#2078CF"
    )

    fun addThemeChangeListener(listener: (AppTheme) -> Unit) {
        if (!themeChangeListeners.contains(listener)) {
            themeChangeListeners.add(listener)
        }
    }

    fun removeThemeChangeListener(listener: (AppTheme) -> Unit) {
        themeChangeListeners.remove(listener)
    }

    private fun notifyThemeChanged(theme: AppTheme) {
        for (listener in themeChangeListeners) {
            listener.invoke(theme)
        }
    }

    fun getAvailableThemes(): List<AppTheme> {
        return listOf(
            THEME_BLACK_WHITE,
            THEME_NEON_EMERALD,
            THEME_GRENADINE_LATTE,
            THEME_AMETHYST_DARK,
            THEME_OBSIDIAN_CRIMSON,
            THEME_OCEAN_ABYSS
        )
    }

    fun getSelectedThemeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_ID, THEME_BLACK_WHITE.id) ?: THEME_BLACK_WHITE.id
    }

    fun getSelectedTheme(context: Context): AppTheme {
        val id = getSelectedThemeId(context)
        return getAvailableThemes().find { it.id == id } ?: THEME_BLACK_WHITE
    }

    fun setSelectedThemeId(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, themeId).apply()
        val theme = getAvailableThemes().find { it.id == themeId } ?: THEME_BLACK_WHITE
        notifyThemeChanged(theme)
    }
}
