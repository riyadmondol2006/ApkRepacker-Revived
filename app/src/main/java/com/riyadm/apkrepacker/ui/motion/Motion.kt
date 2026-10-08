package com.riyadm.apkrepacker.ui.motion

import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Material 3 Expressive motion: every movement in the app is a spring, not a fixed-duration curve.
 *
 * Two families, as in the M3 Expressive motion spec:
 * - **spatial** springs move things (position, size, scale, rotation) and may overshoot a little,
 *   which is what gives Expressive its bouncy feel;
 * - **effects** springs fade/tint things (alpha, color) and never overshoot.
 *
 * Fast = small components (switches, icon buttons), default = most components, slow = full screen.
 */
enum class MotionSpring(val stiffness: Float, val dampingRatio: Float) {
    FastSpatial(1400f, 0.6f),
    DefaultSpatial(700f, 0.8f),
    SlowSpatial(300f, 0.8f),
    FastEffects(3800f, 1f),
    DefaultEffects(1600f, 1f),
    SlowEffects(800f, 1f),
}

/** Cancels any running spring on [property] and springs the view to [target]. */
fun View.springTo(
    property: DynamicAnimation.ViewProperty,
    target: Float,
    spring: MotionSpring = MotionSpring.DefaultSpatial,
    startVelocity: Float = 0f,
    onEnd: (() -> Unit)? = null,
): SpringAnimation {
    val key = property.hashCode()
    (getTag(SPRING_TAG_BASE + key) as? SpringAnimation)?.cancel()
    val animation = SpringAnimation(this, property).apply {
        // The DynamicAnimation setters are builder-style (they return the animation), so Kotlin
        // can't treat them as properties: call them explicitly.
        setSpring(SpringForce(target).setStiffness(spring.stiffness).setDampingRatio(spring.dampingRatio))
        setStartVelocity(startVelocity)
        // Don't wait for the last pixel of the tail: alpha 1/256, scale 1/500, translation 0.5px.
        setMinimumVisibleChange(when (property) {
            DynamicAnimation.ALPHA -> DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA
            DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y -> DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
            DynamicAnimation.ROTATION, DynamicAnimation.ROTATION_X, DynamicAnimation.ROTATION_Y -> DynamicAnimation.MIN_VISIBLE_CHANGE_ROTATION_DEGREES
            else -> DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
        })
        if (onEnd != null) addEndListener { _, canceled, _, _ -> if (!canceled) onEnd() }
    }
    setTag(SPRING_TAG_BASE + key, animation)
    animation.start()
    return animation
}

/** Appear: fades in and scales up from [fromScale] with a bouncy spatial spring. */
fun View.springIn(delayMs: Long = 0L, fromScale: Float = 0.88f, fromTranslationY: Float = 0f) {
    alpha = 0f
    scaleX = fromScale
    scaleY = fromScale
    translationY = fromTranslationY
    visibility = View.VISIBLE
    val start = Runnable {
        springTo(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
        springTo(DynamicAnimation.SCALE_X, 1f, MotionSpring.DefaultSpatial)
        springTo(DynamicAnimation.SCALE_Y, 1f, MotionSpring.DefaultSpatial)
        if (fromTranslationY != 0f) springTo(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
    }
    if (delayMs > 0) postDelayed(start, delayMs) else start.run()
}

/** Disappear: fades and scales down, then calls [onEnd] (typically to set GONE). */
fun View.springOut(toScale: Float = 0.88f, onEnd: (() -> Unit)? = null) {
    springTo(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects)
    springTo(DynamicAnimation.SCALE_X, toScale, MotionSpring.FastSpatial)
    springTo(DynamicAnimation.SCALE_Y, toScale, MotionSpring.FastSpatial, onEnd = onEnd)
}

/**
 * Press feedback for custom clickable surfaces (cards, list rows): squishes to [pressedScale]
 * while the finger is down and springs back with overshoot on release. Doesn't consume touches.
 */
fun View.pressSpring(pressedScale: Float = 0.97f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.springTo(DynamicAnimation.SCALE_X, pressedScale, MotionSpring.FastSpatial)
                v.springTo(DynamicAnimation.SCALE_Y, pressedScale, MotionSpring.FastSpatial)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.springTo(DynamicAnimation.SCALE_X, 1f, MotionSpring.FastSpatial)
                v.springTo(DynamicAnimation.SCALE_Y, 1f, MotionSpring.FastSpatial)
            }
        }
        false
    }
}

private const val SPRING_TAG_BASE = 0x7f0f0000
