package com.riyadm.apkrepacker.ui.motion

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.android.material.transition.platform.MaterialElevationScale
import com.google.android.material.transition.platform.MaterialSharedAxis
import java.lang.ref.WeakReference

/** Shared element name of the root container that grows from the tapped view into the next activity. */
const val EXPRESSIVE_CONTAINER = "expressive_container"

private var lastNamedSource: WeakReference<View>? = null

/**
 * Turns on Material activity transitions for this activity: depth shared axis for forward/back
 * navigation and a container transform for shared-element hops. Call it in `BaseActivity.onCreate`
 * right after `super.onCreate(...)` and before `setContentView` (the window feature can only be
 * requested before content is set). Safe to call more than once.
 *
 * The root content view is named [EXPRESSIVE_CONTAINER] automatically at `onStart` (activities
 * that are only launched, never the target of a container transform, are unaffected).
 */
fun Activity.enableExpressiveTransitions() {
    if (!window.hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)) {
        try {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        } catch (_: RuntimeException) {
            return // content was already set: too late for this activity
        }
    }

    window.enterTransition = sharedAxisZ(forward = true)
    window.returnTransition = sharedAxisZ(forward = false)
    window.exitTransition = sharedAxisZ(forward = true)
    window.reenterTransition = sharedAxisZ(forward = false)

    val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT)
    fun containerTransform() = MaterialContainerTransform().apply {
        addTarget(android.R.id.content)
        scrimColor = Color.TRANSPARENT
        setAllContainerColors(surface)
    }
    window.sharedElementEnterTransition = containerTransform()
    window.sharedElementReturnTransition = containerTransform()
    setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    // Keep the system bars persistent while the container morphs.
    window.sharedElementsUseOverlay = false

    (this as? ComponentActivity)?.lifecycle?.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event != Lifecycle.Event.ON_START) return
            findViewById<View>(android.R.id.content)?.transitionName = EXPRESSIVE_CONTAINER
            source.lifecycle.removeObserver(this)
        }
    })
}

/**
 * Starts [intent] with a Material transition. With [sharedElementView] (a card, list row, FAB...)
 * the view's bounds morph into the new screen's content container (container transform); without
 * it the screens slide along the depth axis. Falls back to a plain `startActivity` when the
 * current activity didn't call [enableExpressiveTransitions] or animations are off.
 *
 * [sharedElementView] is temporarily named [EXPRESSIVE_CONTAINER]; it keeps the name until the
 * next expressive launch, which is what lets the return transition find it.
 */
fun Activity.startActivityExpressive(intent: Intent, sharedElementView: View? = null) {
    if (!window.hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS) || !MotionPreferences.animationsEnabled(this)) {
        startActivity(intent)
        return
    }
    if (sharedElementView != null) {
        window.exitTransition = MaterialElevationScale(false)
        window.reenterTransition = MaterialElevationScale(true)
        lastNamedSource?.get()?.takeIf { it !== sharedElementView }?.transitionName = null
        sharedElementView.transitionName = EXPRESSIVE_CONTAINER
        lastNamedSource = WeakReference(sharedElementView)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedElementView, EXPRESSIVE_CONTAINER)
        startActivity(intent, options.toBundle())
    } else {
        window.exitTransition = sharedAxisZ(forward = true)
        window.reenterTransition = sharedAxisZ(forward = false)
        startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle())
    }
}

private fun sharedAxisZ(forward: Boolean) = MaterialSharedAxis(MaterialSharedAxis.Z, forward).apply {
    excludeTarget(android.R.id.statusBarBackground, true)
    excludeTarget(android.R.id.navigationBarBackground, true)
}
