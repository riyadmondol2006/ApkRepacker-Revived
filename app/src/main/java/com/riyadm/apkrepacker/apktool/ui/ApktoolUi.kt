package com.riyadm.apkrepacker.apktool.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.content.getSystemService
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.DecodeMode
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/** UI-only preferences for the apktool option sheets. */
object ApktoolUiPrefs {
    /** Show the decompile options sheet before every decompile (otherwise the saved defaults are used). */
    const val KEY_ASK_DECODE_OPTIONS = "pref_apktool_ask_decode_options"

    /** Show the build options sheet before every build (otherwise the saved defaults are used). */
    const val KEY_ASK_BUILD_OPTIONS = "pref_apktool_ask_build_options"

    @JvmStatic
    fun askDecodeOptions(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ASK_DECODE_OPTIONS, true)

    @JvmStatic
    fun askBuildOptions(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ASK_BUILD_OPTIONS, true)
}

/** Stores [DecodeOptions] in a [Bundle] field by field (no Parcelable needed). */
object DecodeOptionsArgs {
    private const val MODE = "mode"
    private const val ONLY_MAIN_CLASSES = "onlyMainClasses"
    private const val NO_DEBUG_INFO = "noDebugInfo"
    private const val NO_ASSETS = "noAssets"
    private const val FORCE_MANIFEST = "forceManifest"
    private const val KEEP_BROKEN_RES = "keepBrokenResources"
    private const val ANALYSIS_MODE = "analysisMode"
    private const val FORCE = "force"
    private const val MERGE_SPLITS = "mergeSplits"

    @JvmStatic
    fun toBundle(options: DecodeOptions): Bundle = Bundle().apply {
        putString(MODE, options.mode.name)
        putBoolean(ONLY_MAIN_CLASSES, options.onlyMainClasses)
        putBoolean(NO_DEBUG_INFO, options.noDebugInfo)
        putBoolean(NO_ASSETS, options.noAssets)
        putBoolean(FORCE_MANIFEST, options.forceManifest)
        putBoolean(KEEP_BROKEN_RES, options.keepBrokenResources)
        putBoolean(ANALYSIS_MODE, options.analysisMode)
        putBoolean(FORCE, options.force)
        putBoolean(MERGE_SPLITS, options.mergeSplits)
    }

    /** Returns null when [bundle] is null or wasn't written by [toBundle]. */
    @JvmStatic
    fun fromBundle(bundle: Bundle?): DecodeOptions? {
        if (bundle == null) return null
        val mode = bundle.getString(MODE)
            ?.let { name -> DecodeMode.entries.firstOrNull { it.name == name } }
            ?: return null
        val defaults = DecodeOptions()
        return DecodeOptions(
            mode = mode,
            onlyMainClasses = bundle.getBoolean(ONLY_MAIN_CLASSES, defaults.onlyMainClasses),
            noDebugInfo = bundle.getBoolean(NO_DEBUG_INFO, defaults.noDebugInfo),
            noAssets = bundle.getBoolean(NO_ASSETS, defaults.noAssets),
            forceManifest = bundle.getBoolean(FORCE_MANIFEST, defaults.forceManifest),
            keepBrokenResources = bundle.getBoolean(KEEP_BROKEN_RES, defaults.keepBrokenResources),
            analysisMode = bundle.getBoolean(ANALYSIS_MODE, defaults.analysisMode),
            force = bundle.getBoolean(FORCE, defaults.force),
            mergeSplits = bundle.getBoolean(MERGE_SPLITS, defaults.mergeSplits),
        )
    }

    /**
     * Maps the old DecodeTask action bitmask (2 = decode resources, 1 = decode smali;
     * 3 = both, 0 = nothing) to a [DecodeMode].
     */
    @JvmStatic
    fun modeFromLegacyAction(action: Int): DecodeMode {
        val resources = action and 2 != 0
        val sources = action and 1 != 0
        return when {
            resources && sources -> DecodeMode.ALL
            resources -> DecodeMode.RESOURCES_ONLY
            sources -> DecodeMode.SOURCES_ONLY
            else -> DecodeMode.NONE
        }
    }
}

/**
 * One apktool flag (layout `apktool_option_row`): headline + supporting text + a switch, a row of a
 * rounded group. Call [roundGroup] after adding the rows of one group so the outer corners are big
 * and the corners between neighbours are small.
 */
class OptionRow private constructor(
    val view: View,
    private val card: MaterialCardView,
    private val switch: MaterialSwitch,
) {

    var isChecked: Boolean
        get() = switch.isChecked
        set(value) {
            switch.isChecked = value
        }

    /** Disabled rows keep their value; they just don't apply to the selected mode. */
    var isEnabled: Boolean
        get() = switch.isEnabled
        set(value) = setEnabledState(value, animate = true)

    fun setEnabledState(enabled: Boolean, animate: Boolean) {
        switch.isEnabled = enabled
        card.isEnabled = enabled
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        if (animate) view.springTo(DynamicAnimation.ALPHA, alpha, MotionSpring.FastEffects) else view.alpha = alpha
    }

    private fun setCorners(top: Boolean, bottom: Boolean) {
        val res = card.resources
        val outer = res.getDimension(R.dimen.shape_corner_large_increased)
        val inner = res.getDimension(R.dimen.shape_corner_extra_small)
        card.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, inner)
            .setTopLeftCornerSize(if (top) outer else inner)
            .setTopRightCornerSize(if (top) outer else inner)
            .setBottomLeftCornerSize(if (bottom) outer else inner)
            .setBottomRightCornerSize(if (bottom) outer else inner)
            .build()
    }

    companion object {
        private const val DISABLED_ALPHA = 0.5f

        fun inflate(container: ViewGroup, @StringRes title: Int, @StringRes summary: Int, checked: Boolean): OptionRow {
            val view = LayoutInflater.from(container.context).inflate(R.layout.apktool_option_row, container, false)
            val card = view.findViewById<MaterialCardView>(R.id.apktool_option_card)
            val switch = view.findViewById<MaterialSwitch>(R.id.apktool_option_switch)
            val titleText = container.context.getString(title)
            view.findViewById<TextView>(R.id.apktool_option_title).text = titleText
            view.findViewById<TextView>(R.id.apktool_option_summary).setText(summary)
            switch.contentDescription = titleText
            // Several rows share this id: the sheets restore their own state, so skip view-state saving.
            switch.isSaveEnabled = false
            switch.isChecked = checked
            card.setOnClickListener { if (switch.isEnabled) switch.toggle() }
            container.addView(view)
            return OptionRow(view, card, switch)
        }

        /** Rounds the outer corners of the first/last row of [rows] (one visual group). */
        fun roundGroup(rows: List<OptionRow>) {
            rows.forEachIndexed { index, row ->
                row.setCorners(top = index == 0, bottom = index == rows.lastIndex)
            }
        }
    }
}

/** Copies [text] to the clipboard; below Android 13 (no system confirmation) a Snackbar confirms. */
fun View.copyToClipboard(text: CharSequence) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Snackbar.make(this, R.string.toast_copy_to_clipboard, Snackbar.LENGTH_SHORT).show()
    }
}

/** Springs the card's container color to [target] (effects spring: fades, never overshoots). */
fun MaterialCardView.springCardColor(@ColorInt target: Int) {
    val from = cardBackgroundColor.defaultColor
    if (from == target) return
    val spring = MotionSpring.DefaultEffects
    SpringAnimation(FloatValueHolder(0f)).apply {
        this.spring = SpringForce(1f).apply {
            stiffness = spring.stiffness
            dampingRatio = spring.dampingRatio
        }
        setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
        addUpdateListener { _, value, _ ->
            setCardBackgroundColor(ColorUtils.blendARGB(from, target, value.coerceIn(0f, 1f)))
        }
        addEndListener { _, _, _, _ -> setCardBackgroundColor(target) }
        start()
    }
}
