package com.riyadm.apkrepacker.ui.rows

import android.content.Context
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.MotionSpring

/**
 * Grouped-list shape: the first and last row of a list get large outer corners, rows in between get
 * small ones, so a run of cards reads as one segmented surface.
 */
fun MaterialCardView.applyGroupedShape(position: Int, count: Int, grouped: Boolean = true) {
    val outer = resources.getDimension(R.dimen.b_group_corner_outer)
    val inner = resources.getDimension(R.dimen.b_group_corner_inner)
    val top = if (!grouped || position == 0) outer else inner
    val bottom = if (!grouped || position == count - 1) outer else inner
    shapeAppearanceModel = ShapeAppearanceModel.builder()
        .setTopLeftCorner(CornerFamily.ROUNDED, top)
        .setTopRightCorner(CornerFamily.ROUNDED, top)
        .setBottomLeftCorner(CornerFamily.ROUNDED, bottom)
        .setBottomRightCorner(CornerFamily.ROUNDED, bottom)
        .build()
}

/** The "Use circular icons" preference (key `pref_icon`, default on). */
fun Context.useCircularIcons(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_icon", true)

/**
 * Shape-morphing icon container. The resting shape follows the circular-icons preference
 * (circle, or a squircle-ish rounded square); [setActive] springs to the opposite shape.
 */
class IconShapeMorpher(private val icon: ShapeableImageView) {
    private var animation: SpringAnimation? = null
    private var currentRadius = -1f

    private fun circleRadius() = icon.layoutParams.width.takeIf { it > 0 }?.div(2f)
        ?: icon.resources.getDimension(R.dimen.b_icon_size) / 2f

    private fun squircleRadius() = icon.resources.getDimension(R.dimen.shape_corner_medium)

    fun setActive(active: Boolean, animate: Boolean) {
        val circular = icon.context.useCircularIcons()
        val target = if (circular != active) circleRadius() else squircleRadius()
        morphTo(target, animate)
    }

    private fun morphTo(target: Float, animate: Boolean) {
        animation?.cancel()
        if (!animate || currentRadius < 0f || !icon.isAttachedToWindow) {
            apply(target)
            return
        }
        animation = SpringAnimation(FloatValueHolder(currentRadius)).also { spring ->
            spring.setSpring(
                SpringForce(target)
                    .setStiffness(MotionSpring.FastSpatial.stiffness)
                    .setDampingRatio(MotionSpring.FastSpatial.dampingRatio),
            )
            spring.setMinimumVisibleChange(0.5f)
            spring.addUpdateListener { _, value, _ -> apply(value) }
            spring.start()
        }
    }

    private fun apply(radius: Float) {
        val safe = radius.coerceAtLeast(0f)
        currentRadius = safe
        icon.shapeAppearanceModel = icon.shapeAppearanceModel.withCornerSize(safe)
    }
}
