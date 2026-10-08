package com.riyadm.apkrepacker.ui.imageviewer

import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max

/**
 * Page transformers for the image pager. ViewPager2 settles with its own scroller, so the
 * "springy" feel comes from the depth/scale response to the drag position.
 */
object ViewPagerTransformers {

    /** The incoming page slides in normally while the outgoing one sinks back, shrinking and fading. */
    @JvmField
    val DEPTH = ViewPager2.PageTransformer { page, position ->
        when {
            position < -1f || position > 1f -> page.alpha = 0f
            position <= 0f -> {
                page.alpha = 1f
                page.translationX = 0f
                page.scaleX = 1f
                page.scaleY = 1f
            }
            else -> {
                page.alpha = 1f - position
                page.translationX = page.width * -position
                val scale = DEPTH_MIN_SCALE + (1f - DEPTH_MIN_SCALE) * (1f - abs(position))
                page.scaleX = scale
                page.scaleY = scale
            }
        }
    }

    /** Both pages shrink toward the screen center while sliding. */
    @JvmField
    val ZOOM_OUT = ViewPager2.PageTransformer { page, position ->
        if (position < -1f || position > 1f) {
            page.alpha = 0f
            return@PageTransformer
        }
        val scale = max(ZOOM_MIN_SCALE, 1f - abs(position))
        val verticalMargin = page.height * (1f - scale) / 2f
        val horizontalMargin = page.width * (1f - scale) / 2f
        page.translationX = if (position < 0f) horizontalMargin - verticalMargin / 2f else -horizontalMargin + verticalMargin / 2f
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = ZOOM_MIN_ALPHA + (scale - ZOOM_MIN_SCALE) / (1f - ZOOM_MIN_SCALE) * (1f - ZOOM_MIN_ALPHA)
    }

    private const val DEPTH_MIN_SCALE = 0.8f
    private const val ZOOM_MIN_SCALE = 0.85f
    private const val ZOOM_MIN_ALPHA = 0.5f
}
