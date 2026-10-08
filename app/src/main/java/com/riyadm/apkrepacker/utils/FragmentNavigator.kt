package com.riyadm.apkrepacker.utils

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.riyadm.apkrepacker.ui.motion.ExpressiveTransitions
import com.riyadm.apkrepacker.ui.motion.ScreenTransition

/**
 * Switches between sibling top-level fragments (bottom navigation tabs) by hiding/showing them.
 * Sibling switches use a horizontal Material shared axis whose direction follows the tab order
 * ([setTabOrder]; by default the order in which tabs were first visited). Use
 * [ScreenTransition.FADE_THROUGH] for destinations without a spatial relationship.
 */
class FragmentNavigator(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int,
    private val fragmentFactory: FragmentFactory,
) {

    private var currentFragment: Fragment? = null
    private var wasRestoreStateCalled = false
    private val visitOrder = mutableListOf<String>()
    private var tabOrder: List<String> = emptyList()

    /** Transition between tabs; null disables switch animations. */
    var transition: ScreenTransition? = ScreenTransition.SHARED_AXIS_X

    constructor(
        savedInstanceState: Bundle?,
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        fragmentFactory: FragmentFactory,
    ) : this(fragmentManager, containerId, fragmentFactory) {
        restoreState(savedInstanceState)
    }

    /** Declares the left-to-right order of the tabs so the transition direction is always right. */
    fun setTabOrder(vararg tags: String) {
        tabOrder = tags.toList()
    }

    /** Switches to the fragment with [tag]; [forward] overrides the direction derived from the tab order. */
    @JvmOverloads
    fun switchTo(tag: String, forward: Boolean? = null) {
        ensureStateWasRestored()

        val current = currentFragment
        if (current != null && tag == current.tag) return

        val target = fragmentManager.findFragmentByTag(tag) ?: fragmentFactory.createFragment(tag)
        if (tag !in visitOrder) visitOrder += tag

        transition?.let { type ->
            if (current != null) {
                ExpressiveTransitions.applySwitch(target, current, forward ?: isForward(current.tag, tag), type)
            }
        }

        fragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            current?.let { hide(it) }
            if (target.isAdded) show(target) else add(containerId, target, tag)
        }.commitNow()

        currentFragment = target
    }

    private fun isForward(fromTag: String?, toTag: String): Boolean {
        val order = tabOrder.ifEmpty { visitOrder }
        val from = order.indexOf(fromTag)
        val to = order.indexOf(toTag)
        return from < 0 || to < 0 || to > from
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Fragment> findFragmentByTag(tag: String?): T? {
        ensureStateWasRestored()
        return fragmentManager.findFragmentByTag(tag ?: return null) as T?
    }

    /** Write state of this FragmentNavigator to a Bundle, do this in activity/fragment onSaveInstanceState. */
    fun writeStateToBundle(bundle: Bundle) {
        bundle.putString(STATE_CURRENT_FRAGMENT, currentFragment?.tag)
        bundle.putStringArrayList(STATE_VISIT_ORDER, ArrayList(visitOrder))
    }

    /** Restore state of a FragmentNavigator from a Bundle, do this in activity/fragment onCreate. */
    fun restoreState(bundle: Bundle?) {
        wasRestoreStateCalled = true
        bundle ?: return

        bundle.getString(STATE_CURRENT_FRAGMENT)?.let { tag ->
            currentFragment = fragmentManager.findFragmentByTag(tag)
        }
        bundle.getStringArrayList(STATE_VISIT_ORDER)?.let {
            visitOrder.clear()
            visitOrder += it
        }
    }

    private fun ensureStateWasRestored() {
        check(wasRestoreStateCalled) { "Please call restoreState before using this FragmentNavigator" }
    }

    fun interface FragmentFactory {
        fun createFragment(tag: String): Fragment
    }

    private companion object {
        const val STATE_CURRENT_FRAGMENT = "fragment_navigator_current_fragment"
        const val STATE_VISIT_ORDER = "fragment_navigator_visit_order"
    }
}
