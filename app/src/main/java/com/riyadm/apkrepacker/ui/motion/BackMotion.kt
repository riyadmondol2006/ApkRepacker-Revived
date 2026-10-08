package com.riyadm.apkrepacker.ui.motion

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.motion.MaterialBackHandler
import kotlin.math.min

/**
 * Predictive-back visuals for a surface (full-screen fragment root, sheet, panel): while the user
 * drags, the [view] shrinks toward [MIN_SCALE], follows the finger a little and rounds its
 * corners; every value is a fast no-overshoot spring that tracks the gesture. Releasing without
 * committing springs everything back with the default spatial spring; committing either springs
 * the view away ([animateOut]) or hands over immediately.
 *
 * Drive it from an [OnBackPressedCallback] ([ExpressiveBackCallback]) or a Material
 * [MaterialBackHandler] ([asBackCallback]).
 */
class BackProgressAnimator(
    private val view: View,
    private val animateOut: Boolean = true,
    private val roundCorners: Boolean = true,
) {
    private var startTouchY = 0f
    private var previousOutlineProvider: ViewOutlineProvider? = null
    private var previousClipToOutline = false
    private var cornerRadius = 0f
    private val outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, cornerRadius)
    }

    private val density get() = view.resources.displayMetrics.density

    fun start(event: BackEventCompat) {
        startTouchY = event.touchY
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        if (roundCorners && previousOutlineProvider == null) {
            previousOutlineProvider = view.outlineProvider
            previousClipToOutline = view.clipToOutline
            view.outlineProvider = outlineProvider
            view.clipToOutline = true
        }
        update(event)
    }

    fun update(event: BackEventCompat) {
        val progress = EASE.getInterpolation(event.progress.coerceIn(0f, 1f))
        val scale = 1f - (1f - MIN_SCALE) * progress
        val direction = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        val maxShift = min(view.width * (1f - scale) / 2f, MAX_SHIFT_DP * density)
        val verticalDrag = (event.touchY - startTouchY).coerceIn(-MAX_DRAG_DP * density, MAX_DRAG_DP * density) * VERTICAL_FOLLOW

        view.springTo(DynamicAnimation.SCALE_X, scale, MotionSpring.FastEffects)
        view.springTo(DynamicAnimation.SCALE_Y, scale, MotionSpring.FastEffects)
        view.springTo(DynamicAnimation.TRANSLATION_X, direction * maxShift, MotionSpring.FastEffects)
        view.springTo(DynamicAnimation.TRANSLATION_Y, verticalDrag, MotionSpring.FastEffects)
        if (roundCorners) {
            cornerRadius = CORNER_DP * density * progress
            view.invalidateOutline()
        }
    }

    /** Gesture released without committing: spring back to rest. */
    fun cancel() {
        view.springTo(DynamicAnimation.SCALE_X, 1f, MotionSpring.DefaultSpatial)
        view.springTo(DynamicAnimation.SCALE_Y, 1f, MotionSpring.DefaultSpatial)
        view.springTo(DynamicAnimation.TRANSLATION_X, 0f, MotionSpring.DefaultSpatial)
        view.springTo(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial) { restoreOutline() }
    }

    /** Back committed: spring away ([animateOut]) or reset instantly, then [onDone]. */
    fun commit(onDone: () -> Unit) {
        if (animateOut && MotionPreferences.animationsEnabled(view.context)) {
            view.springTo(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects)
            view.springTo(DynamicAnimation.SCALE_X, MIN_SCALE * 0.96f, MotionSpring.FastSpatial)
            view.springTo(DynamicAnimation.SCALE_Y, MIN_SCALE * 0.96f, MotionSpring.FastSpatial) {
                onDone()
                reset()
            }
        } else {
            onDone()
            reset()
        }
    }

    /** Puts the view back to rest without animating. */
    fun reset() {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        restoreOutline()
    }

    private fun restoreOutline() {
        if (previousOutlineProvider != null || view.clipToOutline) {
            view.outlineProvider = previousOutlineProvider ?: ViewOutlineProvider.BACKGROUND
            view.clipToOutline = previousClipToOutline
            previousOutlineProvider = null
        }
    }

    private companion object {
        const val MIN_SCALE = 0.9f
        const val MAX_SHIFT_DP = 24f
        const val MAX_DRAG_DP = 80f
        const val VERTICAL_FOLLOW = 0.3f
        const val CORNER_DP = 28f
        val EASE = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}

/**
 * [OnBackPressedCallback] that animates [view] with [BackProgressAnimator] during the predictive
 * back gesture and calls [onBack] when the back is committed (also for non-gesture back presses).
 */
class ExpressiveBackCallback(
    view: View,
    enabled: Boolean = true,
    animateOut: Boolean = true,
    private val onBack: () -> Unit,
) : OnBackPressedCallback(enabled) {

    private val animator = BackProgressAnimator(view, animateOut)

    override fun handleOnBackStarted(backEvent: BackEventCompat) = animator.start(backEvent)

    override fun handleOnBackProgressed(backEvent: BackEventCompat) = animator.update(backEvent)

    override fun handleOnBackCancelled() = animator.cancel()

    override fun handleOnBackPressed() = animator.commit(onBack)
}

/**
 * Registers an [ExpressiveBackCallback] for [view] on [dispatcher], tied to [owner]'s lifecycle.
 * Set [ExpressiveBackCallback.isEnabled] to false when the surface is not showing.
 */
fun View.addExpressiveBackCallback(
    owner: LifecycleOwner,
    dispatcher: OnBackPressedDispatcher,
    animateOut: Boolean = true,
    onBack: () -> Unit,
): ExpressiveBackCallback =
    ExpressiveBackCallback(this, true, animateOut, onBack).also { dispatcher.addCallback(owner, it) }

/**
 * Adapts a Material [MaterialBackHandler] (SearchView, SideSheet, bottom sheet behaviours, ...)
 * to an [OnBackPressedCallback] so it can be registered on any [OnBackPressedDispatcher].
 */
fun MaterialBackHandler.asBackCallback(enabled: Boolean = true): OnBackPressedCallback =
    object : OnBackPressedCallback(enabled) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) = startBackProgress(backEvent)

        override fun handleOnBackProgressed(backEvent: BackEventCompat) = updateBackProgress(backEvent)

        override fun handleOnBackCancelled() = cancelBackProgress()

        override fun handleOnBackPressed() {
            handleBackInvoked()
        }
    }
