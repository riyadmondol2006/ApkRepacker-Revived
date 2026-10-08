package com.riyadm.apkrepacker.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import kotlin.math.min

/**
 * Tonal leading tile of an Expressive list row. A rounded-square container with a type icon that
 * morphs into a fully round, primary-colored check when [setTileSelected] is called: corner radius,
 * container color and the icon/check cross-fade all ride one spring, so the morph overshoots a bit.
 *
 * Use [iconView] directly for thumbnails (it fills the tile and is clipped to its shape).
 */
class SelectableIconTile @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewFilledStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    val iconView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER }
    private val checkView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.ic_check)
        alpha = 0f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @AttrRes private var containerAttr = R.attr.colorSecondaryContainer
    @AttrRes private var onContainerAttr = R.attr.colorOnSecondaryContainer

    private val restRadius = resources.getDimension(R.dimen.shape_corner_large)
    private var progress = 0f
    private var spring: SpringAnimation? = null
    var isTileSelected = false
        private set

    init {
        cardElevation = 0f
        strokeWidth = 0
        isClickable = false
        isFocusable = false
        preventCornerOverlap = false
        useCompatPadding = false
        val a = context.obtainStyledAttributes(attrs, R.styleable.SelectableIconTile, defStyleAttr, 0)
        val size = a.getDimensionPixelSize(R.styleable.SelectableIconTile_tileSize, resources.getDimensionPixelSize(R.dimen.list_item_leading_size))
        a.recycle()
        addView(iconView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(checkView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        minimumWidth = size
        minimumHeight = size
        tintCheck()
        render(0f)
    }

    private fun tintCheck() {
        checkView.setImageDrawable(
            checkView.drawable?.mutate()?.let { DrawableCompat.wrap(it) }?.also {
                DrawableCompat.setTint(it, color(R.attr.colorOnPrimary))
            },
        )
    }

    /** Sets the tonal pair (container + on-container theme attributes) this tile rests in. */
    fun setTone(@AttrRes container: Int, @AttrRes onContainer: Int) {
        containerAttr = container
        onContainerAttr = onContainer
        render(progress)
    }

    /** Shows a type icon tinted with the tone's on-container color. */
    fun setIconRes(@DrawableRes res: Int) {
        val drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, res)?.mutate()
        iconView.scaleType = ImageView.ScaleType.CENTER
        iconView.setImageDrawable(drawable?.let {
            DrawableCompat.wrap(it).also { wrapped -> DrawableCompat.setTint(wrapped, color(onContainerAttr)) }
        })
    }

    fun setTileSelected(selected: Boolean, animate: Boolean = true) {
        isTileSelected = selected
        val target = if (selected) 1f else 0f
        spring?.cancel()
        if (!animate || !isAttachedToWindow) {
            progress = target
            render(target)
            return
        }
        spring = SpringAnimation(this, PROGRESS).apply {
            this.spring = SpringForce(target).apply {
                stiffness = MotionSpring.FastSpatial.stiffness
                dampingRatio = MotionSpring.FastSpatial.dampingRatio
            }
            minimumVisibleChange = 1f / 500f
            start()
        }
    }

    private fun render(p: Float) {
        progress = p
        val clamped = p.coerceIn(0f, 1f)
        val full = min(width, height).takeIf { it > 0 }?.div(2f) ?: (minimumHeight / 2f)
        val radius = (restRadius + (full - restRadius) * clamped).coerceAtMost(full)
        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(radius).build()
        setCardBackgroundColor(ColorUtils.blendARGB(color(containerAttr), color(R.attr.colorPrimary), clamped))
        iconView.alpha = 1f - clamped
        checkView.alpha = clamped
        val checkScale = 0.4f + 0.6f * p
        checkView.scaleX = checkScale
        checkView.scaleY = checkScale
        val iconScale = 1f - 0.4f * clamped
        iconView.scaleX = iconScale
        iconView.scaleY = iconScale
    }

    private fun color(@AttrRes attr: Int) = MaterialColors.getColor(this, attr)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        render(progress)
    }

    override fun onDetachedFromWindow() {
        spring?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        val PROGRESS = object : FloatPropertyCompat<SelectableIconTile>("tileSelection") {
            override fun getValue(view: SelectableIconTile) = view.progress
            override fun setValue(view: SelectableIconTile, value: Float) = view.render(value)
        }
    }
}
