package com.riyadm.apkrepacker.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.AnyRes
import androidx.annotation.AttrRes
import androidx.annotation.Dimension
import androidx.annotation.Px
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.use
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/** Small view / resource helpers shared by the screens. Visibility fades are effects springs. */
object ViewUtils {

    // region Visibility fades (springs, no fixed durations)

    @JvmStatic
    @JvmOverloads
    fun fadeOut(view: View, gone: Boolean = true, next: Runnable? = null) {
        val hiddenVisibility = if (gone) View.GONE else View.INVISIBLE
        if (view.visibility != View.VISIBLE || view.alpha == 0f) {
            view.alpha = 0f
            view.visibility = hiddenVisibility
            next?.run()
            return
        }
        view.springTo(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects) {
            view.visibility = hiddenVisibility
            next?.run()
        }
    }

    @JvmStatic
    fun fadeIn(view: View) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        if (view.visibility != View.VISIBLE) view.alpha = 0f
        view.visibility = View.VISIBLE
        view.springTo(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeToVisibility(view: View, visible: Boolean, gone: Boolean = true) {
        if (visible) fadeIn(view) else fadeOut(view, gone)
    }

    @JvmStatic
    @JvmOverloads
    fun crossfade(fromView: View, toView: View, gone: Boolean = true) {
        fadeOut(fromView, gone)
        fadeIn(toView)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeOutThenFadeIn(fromView: View, toView: View, gone: Boolean = true) {
        fadeOut(fromView, gone) { fadeIn(toView) }
    }

    // endregion

    // region Units and sizes

    @JvmStatic
    @Dimension
    fun dpToPx(@Dimension(unit = Dimension.DP) dp: Float, context: Context): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)

    @JvmStatic
    @Px
    fun dpToPxOffset(@Dimension(unit = Dimension.DP) dp: Float, context: Context): Int = dpToPx(dp, context).toInt()

    @JvmStatic
    @Px
    fun dpToPxSize(@Dimension(unit = Dimension.DP) dp: Float, context: Context): Int {
        val value = dpToPx(dp, context)
        val size = (if (value >= 0) value + 0.5f else value - 0.5f).toInt()
        return when {
            size != 0 -> size
            value == 0f -> 0
            value > 0 -> 1
            else -> -1
        }
    }

    @JvmStatic
    @Dimension(unit = Dimension.DP)
    fun pxToDp(@Dimension px: Float, context: Context): Float = px / context.resources.displayMetrics.density

    @JvmStatic
    @Dimension(unit = Dimension.DP)
    fun pxToDpInt(@Dimension px: Float, context: Context): Int = Math.round(pxToDp(px, context))

    @JvmStatic
    fun getDisplayWidth(context: Context): Int = context.resources.displayMetrics.widthPixels

    @JvmStatic
    fun getDisplayHeight(context: Context): Int = context.resources.displayMetrics.heightPixels

    /** Window width class checks matching the `layout-w600dp` / `w960dp` resource qualifiers. */
    @JvmStatic
    fun hasW600Dp(context: Context): Boolean = context.resources.configuration.screenWidthDp >= 600

    @JvmStatic
    fun hasW960Dp(context: Context): Boolean = context.resources.configuration.screenWidthDp >= 960

    @JvmStatic
    fun hasSw600Dp(context: Context): Boolean = context.resources.configuration.smallestScreenWidthDp >= 600

    @JvmStatic
    fun isInPortait(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    @JvmStatic
    fun isInLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // endregion

    // region Theme attribute resolvers

    @JvmStatic
    fun getBooleanFromAttrRes(@AttrRes attrRes: Int, defaultValue: Boolean, context: Context): Boolean =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getBoolean(0, defaultValue) }

    @JvmStatic
    fun getColorFromAttrRes(@AttrRes attrRes: Int, defaultValue: Int, context: Context): Int =
        getColorStateListFromAttrRes(attrRes, context)?.defaultColor ?: defaultValue

    @JvmStatic
    fun getColorStateListFromAttrRes(@AttrRes attrRes: Int, context: Context): ColorStateList? =
        getResIdFromAttrRes(attrRes, 0, context).takeIf { it != 0 }
            ?.let { AppCompatResources.getColorStateList(context, it) }

    @JvmStatic
    @Dimension
    fun getDimensionFromAttrRes(@AttrRes attrRes: Int, defaultValue: Float, context: Context): Float =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getDimension(0, defaultValue) }

    @JvmStatic
    @Px
    fun getDimensionPixelOffsetFromAttrRes(@AttrRes attrRes: Int, defaultValue: Int, context: Context): Int =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getDimensionPixelOffset(0, defaultValue) }

    @JvmStatic
    @Px
    fun getDimensionPixelSizeFromAttrRes(@AttrRes attrRes: Int, defaultValue: Int, context: Context): Int =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getDimensionPixelSize(0, defaultValue) }

    @JvmStatic
    fun getDrawableFromAttrRes(@AttrRes attrRes: Int, context: Context): Drawable? =
        getResIdFromAttrRes(attrRes, 0, context).takeIf { it != 0 }
            ?.let { AppCompatResources.getDrawable(context, it) }

    @JvmStatic
    fun getFloatFromAttrRes(@AttrRes attrRes: Int, defaultValue: Float, context: Context): Float =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getFloat(0, defaultValue) }

    @JvmStatic
    @AnyRes
    fun getResIdFromAttrRes(@AttrRes attrRes: Int, defaultValue: Int, context: Context): Int =
        context.obtainStyledAttributes(intArrayOf(attrRes)).use { it.getResourceId(0, defaultValue) }

    /** Resolves a color theme attribute (also references to colors / state lists) to its color. */
    @JvmStatic
    fun getThemeColor(c: Context?, @AttrRes attribute: Int): Int {
        val context = requireNotNull(c) { "Context is required to resolve a theme color" }
        val value = TypedValue()
        context.theme.resolveAttribute(attribute, value, true)
        return value.data
    }

    /** True when the current theme's surface is a light color. */
    @JvmStatic
    fun isLightTheme(context: Context): Boolean =
        MaterialColors.isColorLight(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0))

    // endregion

    // region System animation durations (for library APIs that want a duration, e.g. Glide cross-fades)

    @JvmStatic
    fun getShortAnimTime(resources: Resources): Int = resources.getInteger(android.R.integer.config_shortAnimTime)

    @JvmStatic
    fun getShortAnimTime(view: View): Int = getShortAnimTime(view.resources)

    @JvmStatic
    fun getShortAnimTime(context: Context): Int = getShortAnimTime(context.resources)

    @JvmStatic
    fun getMediumAnimTime(resources: Resources): Int = resources.getInteger(android.R.integer.config_mediumAnimTime)

    @JvmStatic
    fun getMediumAnimTime(view: View): Int = getMediumAnimTime(view.resources)

    @JvmStatic
    fun getMediumAnimTime(context: Context): Int = getMediumAnimTime(context.resources)

    @JvmStatic
    fun getLongAnimTime(resources: Resources): Int = resources.getInteger(android.R.integer.config_longAnimTime)

    @JvmStatic
    fun getLongAnimTime(view: View): Int = getLongAnimTime(view.resources)

    @JvmStatic
    fun getLongAnimTime(context: Context): Int = getLongAnimTime(context.resources)

    // endregion

    // region Inflation and visibility

    @JvmStatic
    fun inflate(resource: Int, context: Context): View = LayoutInflater.from(context).inflate(resource, null, false)

    @JvmStatic
    fun inflate(resource: Int, parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(resource, parent, false)

    @JvmStatic
    fun inflateWithTheme(resource: Int, context: Context, @StyleRes themeRes: Int): View =
        inflate(resource, themed(context, themeRes))

    @JvmStatic
    fun inflateWithTheme(resource: Int, parent: ViewGroup, @StyleRes themeRes: Int): View =
        LayoutInflater.from(themed(parent.context, themeRes)).inflate(resource, parent, false)

    @JvmStatic
    fun inflateInto(resource: Int, parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(resource, parent, true)

    @JvmStatic
    fun inflateIntoWithTheme(resource: Int, parent: ViewGroup, @StyleRes themeRes: Int): View =
        LayoutInflater.from(ContextThemeWrapper(parent.context, themeRes)).inflate(resource, parent, true)

    private fun themed(context: Context, @StyleRes themeRes: Int): Context =
        if (themeRes != 0) ContextThemeWrapper(context, themeRes) else context

    @JvmStatic
    fun isVisible(view: View): Boolean = view.visibility == View.VISIBLE

    @JvmStatic
    fun setVisibleOrGone(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @JvmStatic
    fun setVisibleOrInvisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    // endregion

    /** Clears [textInputLayout]'s error as soon as the user edits [editText]. */
    @JvmStatic
    fun hideTextInputLayoutErrorOnTextChange(editText: EditText, textInputLayout: TextInputLayout) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                textInputLayout.error = null
            }
        })
    }
}
