@file:Suppress("UNCHECKED_CAST")

package com.riyadm.apkrepacker.utils

import android.os.Bundle
import android.view.View
import androidx.annotation.AnimRes
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.riyadm.apkrepacker.ui.motion.ExpressiveTransitions
import com.riyadm.apkrepacker.ui.motion.ScreenTransition

/**
 * Fragment helpers. Pushed screens (add/replace) animate with Material transitions: a depth
 * shared axis ([ScreenTransition.SHARED_AXIS_Z]) by default, a container transform when a
 * shared element is passed, and back (including predictive back) plays the reverse. The
 * overloads that take `@AnimRes` ids still honor them for callers that need a custom animation.
 */
object FragmentUtils {

    @JvmStatic
    fun getArgumentsBuilder(fragment: Fragment): BundleBuilder {
        val arguments = fragment.arguments ?: Bundle().also { fragment.arguments = it }
        return BundleBuilder.buildUpon(arguments)
    }

    // region find

    @Deprecated("Prefer the activity / parent fragment overloads")
    @JvmStatic
    fun <T> findById(fragmentManager: FragmentManager, @IdRes id: Int): T? =
        fragmentManager.findFragmentById(id) as T?

    @Suppress("DEPRECATION")
    @JvmStatic
    fun <T> findById(activity: FragmentActivity, @IdRes id: Int): T? =
        findById(activity.supportFragmentManager, id)

    @Suppress("DEPRECATION")
    @JvmStatic
    fun <T> findById(parentFragment: Fragment, @IdRes id: Int): T? =
        findById(parentFragment.childFragmentManager, id)

    @Deprecated("Prefer the activity / parent fragment overloads")
    @JvmStatic
    fun <T> findByTag(fragmentManager: FragmentManager, tag: String): T? =
        fragmentManager.findFragmentByTag(tag) as T?

    @Suppress("DEPRECATION")
    @JvmStatic
    fun <T> findByTag(activity: FragmentActivity, tag: String): T? =
        findByTag(activity.supportFragmentManager, tag)

    @Suppress("DEPRECATION")
    @JvmStatic
    fun <T> findByTag(parentFragment: Fragment, tag: String): T? =
        findByTag(parentFragment.childFragmentManager, tag)

    // endregion

    // region add

    /** Custom resource animations; use the [ScreenTransition] / shared element overloads for Material transitions. */
    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun add(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        back_stack: String?,
        @AnimRes anim_in: Int,
        @AnimRes anim_out: Int,
        @AnimRes anim_popIn: Int,
        @AnimRes anim_popOut: Int,
    ) {
        transact(
            fragmentManager, fragment, containerViewId, tag, isReplace = false, backStackName = back_stack, addToBackStack = true,
            anims = intArrayOf(anim_in, anim_out, anim_popIn, anim_popOut),
        )
    }

    /** Pushes [fragment] over the current screen with the given [motion] (null = no animation). */
    @JvmStatic
    fun add(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        motion: ScreenTransition?,
    ) {
        transact(fragmentManager, fragment, containerViewId, tag, isReplace = false, addToBackStack = true, motion = motion)
    }

    /**
     * Pushes [fragment] and grows it out of [sharedElement] with a container transform. The
     * fragment's root view receives [transitionName] automatically.
     */
    @JvmStatic
    fun add(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        sharedElement: View,
        transitionName: String,
    ) {
        transact(
            fragmentManager, fragment, containerViewId, tag, isReplace = false, addToBackStack = true,
            shared = SharedElement(sharedElement, transitionName),
        )
    }

    @JvmStatic
    fun add(fragment: Fragment, fragmentManager: FragmentManager, @IdRes containerViewId: Int) {
        add(fragment, fragmentManager, containerViewId, null, ScreenTransition.SHARED_AXIS_Z)
    }

    @JvmStatic
    fun add(fragment: Fragment, fragmentManager: FragmentManager, @IdRes containerViewId: Int, tag: String) {
        add(fragment, fragmentManager, containerViewId, tag, ScreenTransition.SHARED_AXIS_Z)
    }

    @JvmStatic
    fun add(fragment: Fragment, activity: FragmentActivity, @IdRes containerViewId: Int) {
        add(fragment, activity.supportFragmentManager, containerViewId)
    }

    @JvmStatic
    fun add(fragment: Fragment, parentFragment: Fragment, @IdRes containerViewId: Int) {
        add(fragment, parentFragment.childFragmentManager, containerViewId)
    }

    @JvmStatic
    fun add(fragment: Fragment, parentFragment: Fragment, @IdRes containerViewId: Int, tag: String) {
        add(fragment, parentFragment.childFragmentManager, containerViewId, tag)
    }

    /** Adds a headless (no container) fragment under [tag]. */
    @JvmStatic
    fun add(fragment: Fragment, fragmentManager: FragmentManager, tag: String, back_stack: String?) {
        transact(fragmentManager, fragment, 0, tag, isReplace = false, backStackName = back_stack, addToBackStack = true)
    }

    @JvmStatic
    fun add(fragment: Fragment, activity: FragmentActivity, tag: String, back_stack: String?) {
        add(fragment, activity.supportFragmentManager, tag, back_stack)
    }

    @JvmStatic
    fun add(fragment: Fragment, parentFragment: Fragment, tag: String, back_stack: String?) {
        add(fragment, parentFragment.childFragmentManager, tag, back_stack)
    }

    /** @deprecated Always use an id or tag for restoration. */
    @JvmStatic
    fun add(fragment: Fragment, activity: FragmentActivity) {
        transact(activity.supportFragmentManager, fragment, 0, null, isReplace = false, addToBackStack = true, motion = ScreenTransition.SHARED_AXIS_Z)
    }

    /** @deprecated Always use an id or tag for restoration. */
    @JvmStatic
    fun add(fragment: Fragment, parentFragment: Fragment) {
        transact(parentFragment.childFragmentManager, fragment, 0, null, isReplace = false, addToBackStack = true, motion = ScreenTransition.SHARED_AXIS_Z)
    }

    // endregion

    /** Pops the activity's back stack (the screen on top animates out with its return transition). */
    @JvmStatic
    fun remove(fragment: Fragment) {
        // A repeated tap on Close/back must not pop the screen underneath: act only while this one is still showing.
        if (!fragment.isAdded || fragment.isRemoving) return
        fragment.activity?.supportFragmentManager?.popBackStack()
    }

    // region replace

    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        back_stack: String?,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        @AnimRes popEnter: Int,
        @AnimRes popExit: Int,
    ) {
        transact(
            fragmentManager, fragment, containerViewId, tag, isReplace = true, backStackName = back_stack, addToBackStack = true,
            anims = intArrayOf(enter, exit, popEnter, popExit),
        )
    }

    /** Replaces the screen in the container with [fragment] using [motion] (null = no animation). */
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        motion: ScreenTransition?,
        addToBackStack: Boolean = true,
    ) {
        transact(fragmentManager, fragment, containerViewId, tag, isReplace = true, addToBackStack = addToBackStack, motion = motion)
    }

    /** Replaces the screen in the container with [fragment], growing it out of [sharedElement] (container transform). */
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        sharedElement: View,
        transitionName: String,
    ) {
        transact(
            fragmentManager, fragment, containerViewId, tag, isReplace = true, addToBackStack = true,
            shared = SharedElement(sharedElement, transitionName),
        )
    }

    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        addToBackStack: Boolean,
    ) {
        replace(fragment, fragmentManager, containerViewId, tag, ScreenTransition.SHARED_AXIS_Z, addToBackStack)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
    ) {
        replace(fragment, fragmentManager, containerViewId, tag, null, enter, exit, enter, exit)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        tag: String?,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        @AnimRes popEnter: Int,
        @AnimRes popExit: Int,
    ) {
        replace(fragment, fragmentManager, containerViewId, tag, null, enter, exit, popEnter, popExit)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
    ) {
        replace(fragment, fragmentManager, containerViewId, null, null, enter, exit, enter, exit)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use the ScreenTransition overload for Material transitions")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        fragmentManager: FragmentManager,
        @IdRes containerViewId: Int,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        @AnimRes popEnter: Int,
        @AnimRes popExit: Int,
    ) {
        replace(fragment, fragmentManager, containerViewId, null, null, enter, exit, popEnter, popExit)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        activity: FragmentActivity,
        @IdRes containerViewId: Int,
        tag: String?,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        @AnimRes popEnter: Int,
        @AnimRes popExit: Int,
    ) {
        replace(fragment, activity.supportFragmentManager, containerViewId, tag, enter, exit, popEnter, popExit)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun replace(
        fragment: Fragment,
        parentFragment: Fragment,
        @IdRes containerViewId: Int,
        tag: String?,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        @AnimRes popEnter: Int,
        @AnimRes popExit: Int,
    ) {
        replace(fragment, parentFragment.childFragmentManager, containerViewId, tag, enter, exit, popEnter, popExit)
    }

    /** Headless (no container) replace under [tag]. */
    @Suppress("DEPRECATION")
    @Deprecated("Always use a container id for restoration")
    @JvmStatic
    fun replace(fragment: Fragment, fragmentManager: FragmentManager, tag: String, back_stack: String?) {
        transact(fragmentManager, fragment, 0, tag, isReplace = true, backStackName = back_stack, addToBackStack = true)
    }

    @JvmStatic
    fun replace(fragment: Fragment, fragmentManager: FragmentManager, @IdRes containerViewId: Int, tag: String) {
        replace(fragment, fragmentManager, containerViewId, tag, ScreenTransition.SHARED_AXIS_Z)
    }

    @JvmStatic
    fun replace(fragment: Fragment, fragmentManager: FragmentManager, @IdRes containerViewId: Int) {
        replace(fragment, fragmentManager, containerViewId, null, ScreenTransition.SHARED_AXIS_Z)
    }

    @JvmStatic
    fun replace(fragment: Fragment, activity: FragmentActivity, @IdRes containerViewId: Int) {
        replace(fragment, activity.supportFragmentManager, containerViewId)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun replace(fragment: Fragment, activity: FragmentActivity, tag: String, back_stack: String?) {
        replace(fragment, activity.supportFragmentManager, tag, back_stack)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun replace(fragment: Fragment, parentFragment: Fragment, tag: String, back_stack: String?) {
        replace(fragment, parentFragment.childFragmentManager, tag, back_stack)
    }

    @JvmStatic
    fun replace(fragment: Fragment, activity: FragmentActivity) {
        transact(activity.supportFragmentManager, fragment, 0, null, isReplace = true, addToBackStack = true, motion = ScreenTransition.SHARED_AXIS_Z)
    }

    @JvmStatic
    fun replace(fragment: Fragment, parentFragment: Fragment) {
        transact(parentFragment.childFragmentManager, fragment, 0, null, isReplace = true, addToBackStack = true, motion = ScreenTransition.SHARED_AXIS_Z)
    }

    // endregion

    @JvmStatic
    fun executePendingTransactions(activity: FragmentActivity) {
        activity.supportFragmentManager.executePendingTransactions()
    }

    @JvmStatic
    fun executePendingTransactions(fragment: Fragment) {
        fragment.parentFragmentManager.executePendingTransactions()
    }

    @JvmStatic
    fun <T> getParentAs(fragment: Fragment, asClass: Class<T>): T? {
        val parent: Any? = fragment.parentFragment ?: fragment.activity
        return if (asClass.isInstance(parent)) asClass.cast(parent) else null
    }

    private class SharedElement(val view: View, val name: String)

    private fun transact(
        fm: FragmentManager,
        fragment: Fragment,
        containerId: Int,
        tag: String?,
        isReplace: Boolean,
        addToBackStack: Boolean,
        backStackName: String? = null,
        anims: IntArray? = null,
        motion: ScreenTransition? = null,
        shared: SharedElement? = null,
    ) {
        fm.commit {
            setReorderingAllowed(true)
            when {
                anims != null -> {
                    if (anims.any { it != 0 }) setCustomAnimations(anims[0], anims[1], anims[2], anims[3])
                }
                containerId != 0 && shared != null ->
                    ExpressiveTransitions.applyContainerTransform(fm, this, fragment, containerId, shared.view, shared.name)
                containerId != 0 && motion != null -> {
                    ExpressiveTransitions.coveredFragments(fm, containerId, fragment)
                        .forEach { ExpressiveTransitions.applyOutgoing(it, motion) }
                    ExpressiveTransitions.applyIncoming(fragment, motion)
                    ExpressiveTransitions.prepareRootView(fm, fragment)
                }
            }
            if (isReplace) replace(containerId, fragment, tag) else add(containerId, fragment, tag)
            if (addToBackStack) addToBackStack(backStackName)
        }
    }
}
