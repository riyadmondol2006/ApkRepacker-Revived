package com.riyadm.apkrepacker.view.bottomtabs
/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.customview.view.AbsSavedState
import androidx.customview.widget.ViewDragHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.riyadm.apkrepacker.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max

/**
 * An interaction behavior plugin for a child view of [CoordinatorLayout] to make it work as
 * a bottom sheet.
 */
open class ViewPagerBottomSheetBehavior<V : View> : CoordinatorLayout.Behavior<V> {

    /**
     * Callback for monitoring events about bottom sheets.
     */
    abstract class BottomSheetCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of [STATE_DRAGGING],
         *                    [STATE_SETTLING], [STATE_EXPANDED],
         *                    [STATE_COLLAPSED], or [STATE_HIDDEN].
         */
        abstract fun onStateChanged(bottomSheet: View, @State newState: Int)

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset
         *                    increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         *                    is between collapsed and expanded states and from -1 to 0 it is
         *                    between hidden and collapsed states.
         */
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntDef(STATE_EXPANDED, STATE_COLLAPSED, STATE_DRAGGING, STATE_SETTLING, STATE_HIDDEN)
    @Retention(AnnotationRetention.SOURCE)
    annotation class State

    private var mMinimumVelocity = 0f

    private var mMaximumVelocity = 0f

    private var mPeekHeight = 0

    private var mPeekHeightAuto = false

    private var mPeekHeightMin = 0

    internal var mMinOffset = 0

    internal var mMaxOffset = 0

    internal var mHideable = false

    private var mSkipCollapsed = false

    @State
    internal var mState = STATE_COLLAPSED

    internal var mViewDragHelper: ViewDragHelper? = null

    private var mIgnoreEvents = false

    private var mNestedScrolled = false

    internal var mParentHeight = 0

    internal var mViewRef: WeakReference<V>? = null

    internal var mNestedScrollingChildRef: WeakReference<View?>? = null

    private var mCallback: BottomSheetCallback? = null

    private var mVelocityTracker: VelocityTracker? = null

    internal var mActivePointerId = 0

    private var mInitialY = 0

    internal var mTouchingScrollingChild = false

    /**
     * Default constructor for instantiating ViewPagerBottomSheetBehaviors.
     */
    constructor()

    /**
     * Default constructor for inflating ViewPagerBottomSheetBehaviors from layout.
     *
     * @param context The [Context].
     * @param attrs   The [AttributeSet].
     */
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BottomSheetBehavior_Layout)
        val value = a.peekValue(R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight)
        if (value != null && value.data == PEEK_HEIGHT_AUTO) {
            setPeekHeight(value.data)
        } else {
            setPeekHeight(a.getDimensionPixelSize(
                    R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO))
        }
        setHideable(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false))
        setSkipCollapsed(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed,
                false))
        a.recycle()
        val configuration = ViewConfiguration.get(context)
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()
    }

    override fun onSaveInstanceState(parent: CoordinatorLayout, child: V): Parcelable? {
        return SavedState(super.onSaveInstanceState(parent, child)!!, mState)
    }

    override fun onRestoreInstanceState(parent: CoordinatorLayout, child: V, state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(parent, child, ss.superState!!)
        // Intermediate states are restored as collapsed state
        mState = if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            STATE_COLLAPSED
        } else {
            ss.state
        }
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            child.fitsSystemWindows = true
        }
        val savedTop = child.top
        // First let the parent lay it out
        parent.onLayoutChild(child, layoutDirection)
        // Offset the bottom sheet
        mParentHeight = parent.height
        val peekHeight: Int
        if (mPeekHeightAuto) {
            if (mPeekHeightMin == 0) {
                mPeekHeightMin = parent.resources.getDimensionPixelSize(
                        R.dimen.design_bottom_sheet_peek_height_min)
            }
            peekHeight = max(mPeekHeightMin, mParentHeight - parent.width * 9 / 16)
        } else {
            peekHeight = mPeekHeight
        }
        mMinOffset = max(0, mParentHeight - child.height)
        mMaxOffset = max(mParentHeight - peekHeight, mMinOffset)
        if (mState == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mMinOffset)
        } else if (mHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight)
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, mMaxOffset)
        } else if (mState == STATE_DRAGGING || mState == STATE_SETTLING) {
            ViewCompat.offsetTopAndBottom(child, savedTop - child.top)
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback)
        }
        mViewRef = WeakReference(child)
        mNestedScrollingChildRef = WeakReference(findScrollingChild(child))
        return true
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!child.isShown) {
            mIgnoreEvents = true
            return false
        }
        val action = event.actionMasked
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(event)
        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mTouchingScrollingChild = false
                mActivePointerId = MotionEvent.INVALID_POINTER_ID
                // Reset the ignore flag
                if (mIgnoreEvents) {
                    mIgnoreEvents = false
                    return false
                }
            }
            MotionEvent.ACTION_DOWN -> {
                val initialX = event.x.toInt()
                mInitialY = event.y.toInt()
                val scroll = mNestedScrollingChildRef?.get()
                if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.actionIndex)
                    mTouchingScrollingChild = true
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID &&
                        !parent.isPointInChildBounds(child, initialX, mInitialY)
            }
        }
        if (!mIgnoreEvents && mViewDragHelper!!.shouldInterceptTouchEvent(event)) {
            return true
        }
        // We have to handle cases that the ViewDragHelper does not capture the bottom sheet because
        // it is not the top most view of its parent. This is not necessary when the touch event is
        // happening over the scrolling content as nested scrolling logic handles that case.
        val scroll = mNestedScrollingChildRef!!.get()
        return action == MotionEvent.ACTION_MOVE && scroll != null &&
                !mIgnoreEvents && mState != STATE_DRAGGING &&
                !parent.isPointInChildBounds(scroll, event.x.toInt(), event.y.toInt()) &&
                abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!child.isShown) {
            return false
        }
        val action = event.actionMasked
        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true
        }
        mViewDragHelper?.processTouchEvent(event)
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(event)
        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the bottom sheet in case it is not captured and the touch slop is passed.
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop) {
                mViewDragHelper!!.captureChildView(child, event.getPointerId(event.actionIndex))
            }
        }
        return !mIgnoreEvents
    }

    @Deprecated("Deprecated in Java")
    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V,
                                     directTargetChild: View, target: View, nestedScrollAxes: Int): Boolean {
        mNestedScrolled = false
        return (nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    @Deprecated("Deprecated in Java")
    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int,
                                   dy: Int, consumed: IntArray) {
        val scrollingChild = mNestedScrollingChildRef!!.get()
        if (target !== scrollingChild) {
            return
        }
        val currentTop = child.top
        val newTop = currentTop - dy
        if (dy > 0) { // Upward
            if (newTop < mMinOffset) {
                consumed[1] = currentTop - mMinOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) { // Downward
            if (!target.canScrollVertically(-1)) {
                if (newTop <= mMaxOffset || mHideable) {
                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - mMaxOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        dispatchOnSlide(child.top)
        mNestedScrolled = true
    }

    @Deprecated("Deprecated in Java")
    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View) {
        if (child.top == mMinOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }
        if (mNestedScrollingChildRef == null || target !== mNestedScrollingChildRef!!.get()
                || !mNestedScrolled) {
            return
        }

        mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity)
        val xVel = mVelocityTracker!!.getXVelocity(mActivePointerId)
        val yVel = mVelocityTracker!!.getYVelocity(mActivePointerId)

        val top: Int
        val targetState: Int
        if (yVel < 0 && abs(yVel) > mMinimumVelocity && abs(yVel) > abs(xVel)) {
            top = mMinOffset
            targetState = STATE_EXPANDED
        } else if (mHideable && shouldHide(child, yVel)) {
            top = mParentHeight
            targetState = STATE_HIDDEN
        } else if (yVel > 0 && abs(yVel) > mMinimumVelocity && abs(yVel) > abs(xVel)) {
            top = mMaxOffset
            targetState = STATE_COLLAPSED
        } else {
            // not scrolling much, i.e. stationary
            val currentTop = child.top
            if (abs(currentTop - mMinOffset) < abs(currentTop - mMaxOffset)) {
                top = mMinOffset
                targetState = STATE_EXPANDED
            } else {
                top = mMaxOffset
                targetState = STATE_COLLAPSED
            }
        }

        if (mViewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
            setStateInternal(STATE_SETTLING)
            ViewCompat.postOnAnimation(child, SettleRunnable(child, targetState))
        } else {
            setStateInternal(targetState)
        }
        mNestedScrolled = false
    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V, target: View,
                                  velocityX: Float, velocityY: Float): Boolean {
        return target === mNestedScrollingChildRef!!.get() &&
                (mState != STATE_EXPANDED ||
                        super.onNestedPreFling(coordinatorLayout, child, target,
                                velocityX, velocityY))
    }

    fun invalidateScrollingChild() {
        if (mViewRef == null) {
            return
        }
        val scrollingChild = findScrollingChild(mViewRef!!.get()!!)
        mNestedScrollingChildRef = WeakReference(scrollingChild)
    }

    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels, or
     *                   [PEEK_HEIGHT_AUTO] to configure the sheet to peek automatically
     *                   at 16:9 ratio keyline.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    fun setPeekHeight(peekHeight: Int) {
        var layout = false
        if (peekHeight == PEEK_HEIGHT_AUTO) {
            if (!mPeekHeightAuto) {
                mPeekHeightAuto = true
                layout = true
            }
        } else if (mPeekHeightAuto || mPeekHeight != peekHeight) {
            mPeekHeightAuto = false
            mPeekHeight = max(0, peekHeight)
            mMaxOffset = mParentHeight - peekHeight
            layout = true
        }
        if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
            val view = mViewRef!!.get()
            view?.requestLayout()
        }
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet in pixels, or [PEEK_HEIGHT_AUTO]
     * if the sheet is configured to peek automatically at 16:9 ratio keyline
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    fun getPeekHeight(): Int {
        return if (mPeekHeightAuto) PEEK_HEIGHT_AUTO else mPeekHeight
    }

    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable `true` to make this bottom sheet hideable.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    open fun setHideable(hideable: Boolean) {
        mHideable = hideable
    }

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return `true` if this bottom sheet can hide.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    open fun isHideable(): Boolean {
        return mHideable
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once. Setting this to true has no effect unless the sheet is hideable.
     *
     * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    open fun setSkipCollapsed(skipCollapsed: Boolean) {
        mSkipCollapsed = skipCollapsed
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once.
     *
     * @return Whether the bottom sheet should skip the collapsed state.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    open fun getSkipCollapsed(): Boolean {
        return mSkipCollapsed
    }

    /**
     * Sets a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    open fun setBottomSheetCallback(callback: BottomSheetCallback?) {
        mCallback = callback
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of [STATE_COLLAPSED], [STATE_EXPANDED], or
     *              [STATE_HIDDEN].
     */
    fun setState(@State state: Int) {
        if (state == mState) {
            return
        }
        if (mViewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED ||
                    (mHideable && state == STATE_HIDDEN)) {
                mState = state
            }
            return
        }
        val child = mViewRef!!.get() ?: return
        // Start the animation; wait until a pending layout if there is one.
        val parent = child.parent
        if (parent != null && parent.isLayoutRequested && ViewCompat.isAttachedToWindow(child)) {
            child.post { startSettlingAnimation(child, state) }
        } else {
            startSettlingAnimation(child, state)
        }
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of [STATE_EXPANDED], [STATE_COLLAPSED], [STATE_DRAGGING],
     * [STATE_SETTLING], and [STATE_HIDDEN].
     */
    @State
    fun getState(): Int {
        return mState
    }

    internal fun setStateInternal(@State state: Int) {
        if (mState == state) {
            return
        }
        mState = state
        val bottomSheet: View? = mViewRef!!.get()
        if (bottomSheet != null && mCallback != null) {
            mCallback!!.onStateChanged(bottomSheet, state)
        }
    }

    private fun reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    internal fun shouldHide(child: View, yvel: Float): Boolean {
        if (mSkipCollapsed) {
            return true
        }
        if (child.top < mMaxOffset) {
            // It should not hide, but collapse.
            return false
        }
        val newTop = child.top + yvel * HIDE_FRICTION
        return abs(newTop - mMaxOffset) / mPeekHeight.toFloat() > HIDE_THRESHOLD
    }

    @VisibleForTesting
    internal fun findScrollingChild(view: View): View? {
        if (ViewCompat.isNestedScrollingEnabled(view) && view.isShown) {
            return view
        }

        if (view is ViewPager) {
            val currentViewPagerChild = getCurrentViewWithVP(view)
            if (currentViewPagerChild != null) {
                return findScrollingChild(currentViewPagerChild)
            }
        }

        if (view is ViewPager2) {
            val currentViewPagerChild = getCurrentViewWithVP2(view)
            if (currentViewPagerChild != null) {
                return findScrollingChild(currentViewPagerChild)
            }
        }

        if (view is ViewGroup) {
            var i = 0
            val count = view.childCount
            while (i < count) {
                val scrollingChild = this.findScrollingChild(view.getChildAt(i))
                if (scrollingChild != null) {
                    return scrollingChild
                }
                ++i
            }
        }

        return null
    }

    private fun getCurrentViewWithVP(viewPager: ViewPager): View? {
        return BottomSheetVPHelper.getCurrentViewWithVP(viewPager)
    }

    private fun getCurrentViewWithVP2(viewPager: ViewPager2): View? {
        val currentItem = viewPager.currentItem
        var child: View? = viewPager.getChildAt(0)
        if (child is RecyclerView) {
            child = child.layoutManager!!.findViewByPosition(currentItem)
        }

        return child
    }

    private fun getYVelocity(): Float {
        mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity)
        return mVelocityTracker!!.getYVelocity(mActivePointerId)
    }

    internal fun startSettlingAnimation(child: View, state: Int) {
        val top: Int
        if (state == STATE_COLLAPSED) {
            top = mMaxOffset
        } else if (state == STATE_EXPANDED) {
            top = mMinOffset
        } else if (mHideable && state == STATE_HIDDEN) {
            top = mParentHeight
        } else {
            throw IllegalArgumentException("Illegal state argument: $state")
        }
        if (mViewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
            setStateInternal(STATE_SETTLING)
            ViewCompat.postOnAnimation(child, SettleRunnable(child, state))
        } else {
            setStateInternal(state)
        }
    }

    private val mDragCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (mState == STATE_DRAGGING) {
                return false
            }
            if (mTouchingScrollingChild) {
                return false
            }
            if (mState == STATE_EXPANDED && mActivePointerId == pointerId) {
                val scroll = mNestedScrollingChildRef!!.get()
                if (scroll != null && scroll.canScrollVertically(-1)) {
                    // Let the content scroll up
                    return false
                }
            }
            return mViewRef != null && mViewRef!!.get() === child
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            dispatchOnSlide(top)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING)
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val top: Int
            @State val targetState: Int
            if (yvel < 0 && abs(yvel) > mMinimumVelocity && abs(yvel) > abs(xvel)) {
                top = mMinOffset
                targetState = STATE_EXPANDED
            } else if (mHideable && shouldHide(releasedChild, yvel)) {
                top = mParentHeight
                targetState = STATE_HIDDEN
            } else if (yvel > 0 && abs(yvel) > mMinimumVelocity && abs(yvel) > abs(xvel)) {
                top = mMaxOffset
                targetState = STATE_COLLAPSED
            } else {
                // not scrolling much, i.e. stationary
                val currentTop = releasedChild.top
                if (abs(currentTop - mMinOffset) < abs(currentTop - mMaxOffset)) {
                    top = mMinOffset
                    targetState = STATE_EXPANDED
                } else {
                    top = mMaxOffset
                    targetState = STATE_COLLAPSED
                }
            }

            if (mViewDragHelper!!.settleCapturedViewAt(releasedChild.left, top)) {
                setStateInternal(STATE_SETTLING)
                ViewCompat.postOnAnimation(releasedChild,
                        SettleRunnable(releasedChild, targetState))
            } else {
                setStateInternal(targetState)
            }
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return MathUtils.clamp(top, mMinOffset, if (mHideable) mParentHeight else mMaxOffset)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return child.left
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return if (mHideable) {
                mParentHeight - mMinOffset
            } else {
                mMaxOffset - mMinOffset
            }
        }
    }

    internal fun dispatchOnSlide(top: Int) {
        val bottomSheet: View? = mViewRef!!.get()
        if (bottomSheet != null && mCallback != null) {
            if (top > mMaxOffset) {
                mCallback!!.onSlide(bottomSheet, (mMaxOffset - top).toFloat() /
                        (mParentHeight - mMaxOffset))
            } else {
                mCallback!!.onSlide(bottomSheet,
                        (mMaxOffset - top).toFloat() / (mMaxOffset - mMinOffset))
            }
        }
    }

    @VisibleForTesting
    internal fun getPeekHeightMin(): Int {
        return mPeekHeightMin
    }

    private inner class SettleRunnable(
            private val mView: View,
            @State private val mTargetState: Int
    ) : Runnable {

        override fun run() {
            if (mViewDragHelper != null && mViewDragHelper!!.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this)
            } else {
                setStateInternal(mTargetState)
            }
        }
    }

    protected class SavedState : AbsSavedState {
        @State
        @JvmField
        internal val state: Int

        constructor(source: Parcel) : this(source, null)

        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            //noinspection ResourceType
            state = source.readInt()
        }

        constructor(superState: Parcelable, @State state: Int) : super(superState) {
            this.state = state
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(state)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState {
                    return SavedState(source, loader)
                }

                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source, null)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        /**
         * The bottom sheet is dragging.
         */
        const val STATE_DRAGGING = 1

        /**
         * The bottom sheet is settling.
         */
        const val STATE_SETTLING = 2

        /**
         * The bottom sheet is expanded.
         */
        const val STATE_EXPANDED = 3

        /**
         * The bottom sheet is collapsed.
         */
        const val STATE_COLLAPSED = 4

        /**
         * The bottom sheet is hidden.
         */
        const val STATE_HIDDEN = 5

        /**
         * Peek at the 16:9 ratio keyline of its parent.
         *
         * This can be used as a parameter for [setPeekHeight].
         * [getPeekHeight] will return this when the value is set.
         */
        const val PEEK_HEIGHT_AUTO = -1

        private const val HIDE_THRESHOLD = 0.5f

        private const val HIDE_FRICTION = 0.1f

        /**
         * A utility function to get the [ViewPagerBottomSheetBehavior] associated with the `view`.
         *
         * @param view The [View] with [ViewPagerBottomSheetBehavior].
         * @return The [ViewPagerBottomSheetBehavior] associated with the `view`.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <V : View> from(view: V): ViewPagerBottomSheetBehavior<V> {
            val params = view.layoutParams
            if (params !is CoordinatorLayout.LayoutParams) {
                throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            }
            val behavior = params.behavior
            if (behavior !is ViewPagerBottomSheetBehavior<*>) {
                throw IllegalArgumentException(
                        "The view is not associated with ViewPagerBottomSheetBehavior")
            }
            return behavior as ViewPagerBottomSheetBehavior<V>
        }
    }
}
