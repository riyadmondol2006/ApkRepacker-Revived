package com.riyadm.apkrepacker.ui.motion

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.transition.Transition
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

/** How two screens relate; picks the Material transition between them. */
enum class ScreenTransition {
    /** Sibling destinations on the same level (bottom-nav tabs): horizontal shared axis. */
    SHARED_AXIS_X,

    /** Vertical relationship (e.g. expanding a detail panel). */
    SHARED_AXIS_Y,

    /** Drill-down / forward-back navigation: depth shared axis. The default for pushed screens. */
    SHARED_AXIS_Z,

    /** Unrelated destinations with no spatial relationship. */
    FADE_THROUGH,
}

/** Factory + fragment wiring for the Material motion system (all springy/emphasized via the theme's motion tokens). */
object ExpressiveTransitions {

    /** [forward] picks the direction of the shared axis; ignored for fade-through. */
    @JvmStatic
    fun create(type: ScreenTransition, forward: Boolean): Transition = when (type) {
        ScreenTransition.SHARED_AXIS_X -> MaterialSharedAxis(MaterialSharedAxis.X, forward)
        ScreenTransition.SHARED_AXIS_Y -> MaterialSharedAxis(MaterialSharedAxis.Y, forward)
        ScreenTransition.SHARED_AXIS_Z -> MaterialSharedAxis(MaterialSharedAxis.Z, forward)
        ScreenTransition.FADE_THROUGH -> MaterialFadeThrough()
    }

    /**
     * Wires [fragment] as the incoming screen of a forward navigation (enter) that animates
     * back out on pop (return).
     */
    @JvmStatic
    fun applyIncoming(fragment: Fragment, type: ScreenTransition = ScreenTransition.SHARED_AXIS_Z) {
        fragment.enterTransition = create(type, true)
        fragment.returnTransition = create(type, false)
    }

    /**
     * Wires [fragment] as the screen that is covered by a forward navigation (exit) and comes
     * back when the new screen is popped (reenter).
     */
    @JvmStatic
    fun applyOutgoing(fragment: Fragment, type: ScreenTransition = ScreenTransition.SHARED_AXIS_Z) {
        fragment.exitTransition = create(type, true)
        fragment.reenterTransition = create(type, false)
    }

    /** Sibling switch (hide/show) in [forward] direction: both fragments get fresh, direction-correct transitions. */
    @JvmStatic
    fun applySwitch(incoming: Fragment, outgoing: Fragment?, forward: Boolean, type: ScreenTransition = ScreenTransition.SHARED_AXIS_X) {
        incoming.enterTransition = create(type, forward)
        incoming.returnTransition = create(type, !forward)
        outgoing?.exitTransition = create(type, forward)
        outgoing?.reenterTransition = create(type, !forward)
    }

    /**
     * Container transform: [fragment] grows out of [sharedElement] (and shrinks back into it on
     * pop). The root view of [fragment] gets [transitionName] automatically when it is created.
     * Fragments already shown in [containerId] get an elevation-scale exit/reenter.
     */
    @JvmStatic
    fun applyContainerTransform(
        fm: FragmentManager,
        transaction: FragmentTransaction,
        fragment: Fragment,
        @IdRes containerId: Int,
        sharedElement: View,
        transitionName: String,
    ) {
        val surface = MaterialColors.getColor(sharedElement, com.google.android.material.R.attr.colorSurface)
        fragment.sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = containerId
            scrimColor = android.graphics.Color.TRANSPARENT
            setAllContainerColors(surface)
        }
        fragment.sharedElementReturnTransition = MaterialContainerTransform().apply {
            drawingViewId = containerId
            scrimColor = android.graphics.Color.TRANSPARENT
            setAllContainerColors(surface)
        }
        // The fragment itself must not also run a regular enter transition on top of the transform.
        fragment.enterTransition = null
        fragment.returnTransition = null
        coveredFragments(fm, containerId, fragment).forEach {
            it.exitTransition = MaterialElevationScale(false)
            it.reenterTransition = MaterialElevationScale(true)
        }
        nameRootView(fm, fragment, transitionName)
        transaction.addSharedElement(sharedElement, transitionName)
    }

    /** Fragments in [containerId] other than [fragment]; they sit under or get replaced by it. */
    @JvmStatic
    fun coveredFragments(fm: FragmentManager, @IdRes containerId: Int, fragment: Fragment): List<Fragment> =
        if (containerId == 0) emptyList() else fm.fragments.filter { it.id == containerId && it !== fragment }

    /**
     * Gives the fragment's root view [transitionName] (when non-null) and an opaque
     * `colorSurface` background if it has none, so a fade through the screen underneath never
     * shows. One-shot: unregisters itself after the fragment's first view.
     */
    @JvmStatic
    fun prepareRootView(fm: FragmentManager, fragment: Fragment, transitionName: String? = null) =
        nameRootView(fm, fragment, transitionName)

    private fun nameRootView(fm: FragmentManager, fragment: Fragment, transitionName: String?) {
        fm.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: android.os.Bundle?) {
                if (f !== fragment) return
                fm.unregisterFragmentLifecycleCallbacks(this)
                if (transitionName != null) v.transitionName = transitionName
                if (v.background == null) {
                    v.setBackgroundColor(MaterialColors.getColor(v, com.google.android.material.R.attr.colorSurface))
                }
            }
        }, false)
    }
}
