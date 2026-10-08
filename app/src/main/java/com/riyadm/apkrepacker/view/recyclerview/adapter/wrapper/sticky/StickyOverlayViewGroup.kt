package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.OverlayHitTest

/**
 * Holds the sticky header views drawn on top of the list. It plays the role of a
 * [View.getOverlay], but is drawn by an item decoration instead of being attached to the view
 * tree, and therefore replays touches onto its children itself: press state on down, click /
 * long click on tap / long press.
 *
 * @see getOverlayView the container of the sticky views
 */
open class StickyOverlayViewGroup(hostView: RecyclerView) {

    private val overlayViewGroup = OverlayViewGroup(hostView.context, hostView)

    open fun getOverlayView(): ViewGroup = overlayViewGroup

    open fun add(view: View) = overlayViewGroup.add(view)

    open fun remove(view: View) = overlayViewGroup.remove(view)

    open fun getChildCount(): Int = overlayViewGroup.childCount

    open fun getChildAt(index: Int): View? = overlayViewGroup.getChildAt(index)

    @Suppress("UNCHECKED_CAST")
    open fun <T : View> findViewById(@IdRes id: Int): T? = overlayViewGroup.findViewById<View>(id) as T?

    open fun addView(child: View?) = overlayViewGroup.addView(child, -1)

    open fun addView(child: View?, index: Int) = overlayViewGroup.addView(child, index)

    open fun addView(child: View?, width: Int, height: Int) = overlayViewGroup.addView(child, width, height)

    open fun addView(child: View?, params: ViewGroup.LayoutParams?) = overlayViewGroup.addView(child, params)

    open fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) =
        overlayViewGroup.addView(child, index, params)

    /** The sticky view bound for the group starting at adapter [position], if showing. */
    open fun findStickyView(position: Int): View? =
        (0 until getChildCount()).asSequence()
            .mapNotNull { getChildAt(it) }
            .firstOrNull { (it.layoutParams as LayoutParams).position == position }

    /** The clickable view under ([x], [y]) among the sticky views. */
    internal fun findViewInternal(x: Float, y: Float): View? = OverlayHitTest.findInChildren(overlayViewGroup, x, y)

    open fun removeAllViews() = overlayViewGroup.removeAllViews()

    open fun removeView(view: View?) = overlayViewGroup.removeView(view)

    open fun removeViewAt(index: Int) = overlayViewGroup.removeViewAt(index)

    open fun measureChild(child: View?, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) =
        overlayViewGroup.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec)

    open fun isEmpty(): Boolean = overlayViewGroup.isEmpty()

    open inner class OverlayViewGroup(context: Context, internal val hostView: RecyclerView) :
        ViewGroup(context), GestureDetector.OnGestureListener, RecyclerView.OnItemTouchListener {

        private val gestureDetector = GestureDetectorCompat(context, this)

        init {
            hostView.addOnItemTouchListener(this)
        }

        open fun add(child: View?) {
            requireNotNull(child) { "view must be non-null" }
            (child.parent as? ViewGroup)?.removeView(child)
            super.addView(child)
            invalidate()
        }

        open fun remove(view: View?) {
            requireNotNull(view) { "view must be non-null" }
            super.removeView(view)
            invalidate()
        }

        open fun isEmpty(): Boolean = childCount == 0

        // The overlay is never in the view tree; repaint the host instead.
        override fun invalidate() = hostView.invalidate()

        override fun removeAllViews() {
            super.removeAllViews()
            invalidate()
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit

        override fun childDrawableStateChanged(child: View) {
            super.childDrawableStateChanged(child)
            invalidate()
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        public override fun measureChild(child: View?, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) =
            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec)

        // region touch replay

        override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
            val view = OverlayHitTest.findInChildren(this, event.x, event.y)
            if (view == null || !view.isEnabled) return false
            view.isPressed = true
            invalidate()
            return true
        }

        override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    OverlayHitTest.findInChildren(this, event.x, event.y)?.takeIf { it.isEnabled }?.let {
                        it.isPressed = false
                        invalidate()
                    }
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

        override fun onDown(e: MotionEvent): Boolean = false

        override fun onShowPress(e: MotionEvent) = Unit

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val view = OverlayHitTest.findInChildren(this, e.x, e.y) ?: return false
            view.isPressed = false
            if (view.isEnabled && view.isClickable) return view.performClick()
            invalidate()
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false

        override fun onLongPress(e: MotionEvent) {
            val view = OverlayHitTest.findInChildren(this, e.x, e.y) ?: return
            view.isPressed = false
            if (view.isEnabled && view.isLongClickable) view.performLongClick()
            invalidate()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

        // endregion

        override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

        override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LayoutParams(p)

        override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(context, attrs)
    }

    /** Layout params that remember which adapter position a sticky view belongs to. */
    open inner class LayoutParams : ViewGroup.MarginLayoutParams {
        @JvmField
        var position = 0

        constructor(width: Int, height: Int) : super(width, height)

        constructor(source: ViewGroup.LayoutParams?) : super(source)

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
    }
}
