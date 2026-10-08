package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper

import android.view.View
import android.view.ViewGroup

/**
 * Hit testing for the overlay containers of the sticky and swipe adapters, whose views are drawn
 * by an item decoration and therefore never receive touches from the view system.
 */
internal object OverlayHitTest {

    /** Deepest clickable (or long clickable) view under ([x], [y]) inside [root], honoring translations. */
    fun findClickable(root: View, x: Float, y: Float): View? {
        if (root.isClickable || root.isLongClickable) return root
        val group = root as? ViewGroup ?: return null
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.contains(x, y)) return findClickable(child, x, y)
        }
        return null
    }

    /** The direct child of [overlay] under ([x], [y]), topmost first, resolved to a clickable view. */
    fun findInChildren(overlay: ViewGroup, x: Float, y: Float): View? {
        for (i in overlay.childCount - 1 downTo 0) {
            val child = overlay.getChildAt(i)
            if (child.contains(x, y)) return findClickable(child, x, y)
        }
        return null
    }

    private fun View.contains(x: Float, y: Float): Boolean =
        x >= left + translationX && x <= right + translationX && y >= top + translationY && y <= bottom + translationY
}
