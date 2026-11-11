package com.example.myapplication.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.example.myapplication.model.ThemeManager
import com.google.android.material.bottomsheet.BottomSheetDialog

object DialogThemeHelper {

    fun styleBottomSheet(dialog: BottomSheetDialog, context: Context) {
        val theme = ThemeManager.getSelectedTheme(context)
        val cardColor = Color.parseColor(theme.cardColorHex)
        val density = context.resources.displayMetrics.density
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    24f * density, 24f * density,
                    24f * density, 24f * density,
                    0f, 0f, 0f, 0f
                )
                setColor(cardColor)
            }
            sheet.background = drawable
        }
    }

    fun styleDialogView(view: View, context: Context) {
        val theme = ThemeManager.getSelectedTheme(context)
        val cardColor = Color.parseColor(theme.cardColorHex)
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(cardColor)
        }
        view.background = drawable
    }

    fun styleEditText(editText: EditText, context: Context) {
        val theme = ThemeManager.getSelectedTheme(context)
        val cardColor = Color.parseColor(theme.cardColorHex)
        val strokeColor = Color.parseColor(theme.accentColorHex)
        val density = context.resources.displayMetrics.density

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(cardColor)
            setStroke((1.5f * density).toInt(), strokeColor)
        }
        editText.background = drawable
    }

    fun stylePrimaryButton(button: Button, context: Context) {
        val theme = ThemeManager.getSelectedTheme(context)
        val accentColor = Color.parseColor(theme.accentColorHex)
        button.backgroundTintList = ColorStateList.valueOf(accentColor)
        button.setTextColor(if (theme.id == "black_white") Color.BLACK else Color.WHITE)
    }

    fun styleSecondaryButton(button: Button, context: Context) {
        val theme = ThemeManager.getSelectedTheme(context)
        val cardColor = Color.parseColor(theme.cardColorHex)
        val density = context.resources.displayMetrics.density

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(cardColor)
            setStroke((1.5f * density).toInt(), Color.parseColor("#33FFFFFF"))
        }
        button.background = drawable
        button.backgroundTintList = null
        button.setTextColor(Color.WHITE)
    }
}
