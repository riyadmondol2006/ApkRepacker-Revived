package com.riyadm.apkrepacker.ui.themelist

import android.content.Context
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ViewThemeCardBinding
import com.riyadm.apkrepacker.ui.motion.pressSpring
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.utils.Theme

/**
 * A card that previews an app theme as a miniature M3 screen, drawn with that theme's real
 * palette (Material You themes use the current wallpaper colours).
 */
class ThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding = ViewThemeCardBinding.inflate(LayoutInflater.from(context), this)
    private var themeSelected = false
    private var shownThemeId = NO_THEME

    init {
        pressSpring()
    }

    fun setTheme(theme: Theme.ThemeDescriptor) {
        binding.themeTitle.text = theme.getName(context)
        binding.themeModeIcon.setImageResource(if (theme.isDark) R.drawable.ic_dark_theme_2 else R.drawable.ic_light_theme)

        // Re-inflating the preview is cheap but pointless when the same theme is bound again.
        if (shownThemeId != theme.id) {
            shownThemeId = theme.id
            binding.themePreviewContainer.removeAllViews()
            LayoutInflater.from(previewContext(theme)).inflate(R.layout.view_theme_preview, binding.themePreviewContainer, true)
        }
        contentDescription = theme.getName(context)
    }

    /** Marks this card as the active choice: primary outline plus a check badge. */
    fun setThemeSelected(selected: Boolean) {
        if (selected == themeSelected) return
        themeSelected = selected
        strokeWidth = resources.getDimensionPixelSize(if (selected) R.dimen.theme_card_stroke_selected else R.dimen.theme_card_stroke)
        strokeColor = MaterialColors.getColor(
            this,
            if (selected) R.attr.colorPrimary else R.attr.colorOutlineVariant,
        )
        val badge = binding.themeBadge
        if (selected) {
            badge.springIn(fromScale = 0.4f)
        } else {
            badge.springOut(toScale = 0.4f) { badge.visibility = View.GONE }
        }
    }

    fun setMessage(message: CharSequence?) {
        binding.themeMessage.apply {
            visibility = if (message == null) GONE else VISIBLE
            text = message
        }
    }

    fun setMessage(@StringRes message: Int) {
        binding.themeMessage.visibility = VISIBLE
        binding.themeMessage.setText(message)
    }

    private fun previewContext(theme: Theme.ThemeDescriptor): Context {
        val themed = ContextThemeWrapper(context, theme.theme)
        if (!theme.isDynamic) return themed
        val overlay = if (theme.isDark) {
            R.style.ThemeOverlay_Material3_DynamicColors_Dark
        } else {
            R.style.ThemeOverlay_Material3_DynamicColors_Light
        }
        return DynamicColors.wrapContextIfAvailable(themed, overlay)
    }

    private companion object {
        const val NO_THEME = -1
    }
}
