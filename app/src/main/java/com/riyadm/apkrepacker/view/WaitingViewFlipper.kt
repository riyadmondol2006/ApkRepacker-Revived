package com.riyadm.apkrepacker.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ViewFlipper
import com.riyadm.apkrepacker.ui.motion.springIn

/**
 * Switches between content / loading / permission-denied pages. The newly shown page springs in
 * instead of using the framework's flip animations. Children must follow the PAGE_INDEX_* indexing.
 */
open class WaitingViewFlipper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewFlipper(context, attrs) {

    private var pendingSwitch: Runnable? = null

    open fun setDisplayedChildDelayed(child: Int) {
        pendingSwitch?.let(::removeCallbacks)
        val switch = Runnable { setDisplayedChild(child) }
        pendingSwitch = switch
        postDelayed(switch, ANIM_START_DELAY.toLong())
    }

    override fun setDisplayedChild(whichChild: Int) {
        pendingSwitch?.let(::removeCallbacks)
        pendingSwitch = null

        if (displayedChild == whichChild) return
        super.setDisplayedChild(whichChild)
        if (isAttachedToWindow) getChildAt(displayedChild)?.springIn(fromScale = PAGE_FROM_SCALE)
    }

    companion object {
        const val PAGE_INDEX_CONTENT = 0
        const val PAGE_INDEX_LOADING = 1
        const val PAGE_INDEX_PERMISSION_DENIED = 2
        const val ANIM_START_DELAY = 0
        private const val PAGE_FROM_SCALE = 0.96f
    }
}
