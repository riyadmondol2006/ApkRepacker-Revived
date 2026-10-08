package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.color.MaterialColors
import java.util.ArrayDeque

/**
 * @author Created by cz
 * @date 2020-03-24 18:24
 * @email bingo110@126.com
 *
 * Keeps the sticky header of the first visible group pinned to the top of the list (in the
 * [StickyOverlayViewGroup]) and pushes it out when the next group's header arrives. Headers
 * without a background of their own get a tonal `colorSurfaceContainerHigh` fill so the rows
 * scrolling underneath don't show through.
 */
open class StickyRecyclerViewScrollListener<A>(
    recyclerView: RecyclerView,
    private val adapter: A,
    private val stickyOverlay: StickyOverlayViewGroup,
) : StickyScrollable<RecyclerView> where A : RecyclerView.Adapter<*>, A : StickyCallback<*> {

    private val recycler = StickyRecycler()
    private val orientation = (recyclerView.layoutManager as? LinearLayoutManager)?.orientation ?: RecyclerView.VERTICAL
    private val orientationHelper = OrientationHelper.createOrientationHelper(recyclerView.layoutManager, orientation)
    private var lastStickyItemPosition = RecyclerView.NO_POSITION

    private fun firstVisibleItemPosition(recyclerView: RecyclerView): Int = when (val manager = recyclerView.layoutManager) {
        is GridLayoutManager -> manager.findFirstVisibleItemPosition()
        is StaggeredGridLayoutManager ->
            manager.findFirstVisibleItemPositions(IntArray(manager.spanCount)).minOrNull() ?: RecyclerView.NO_POSITION
        is LinearLayoutManager -> manager.findFirstVisibleItemPosition()
        else -> RecyclerView.NO_POSITION
    }

    private fun lastVisibleItemPosition(recyclerView: RecyclerView): Int = when (val manager = recyclerView.layoutManager) {
        is GridLayoutManager -> manager.findLastVisibleItemPosition()
        is StaggeredGridLayoutManager ->
            manager.findLastVisibleItemPositions(IntArray(manager.spanCount)).maxOrNull() ?: RecyclerView.NO_POSITION
        is LinearLayoutManager -> manager.findLastVisibleItemPosition()
        else -> RecyclerView.NO_POSITION
    }

    private fun spanCount(recyclerView: RecyclerView): Int = when (val manager = recyclerView.layoutManager) {
        is GridLayoutManager -> manager.spanCount
        is StaggeredGridLayoutManager -> manager.spanCount
        else -> 1
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val layoutManager = recyclerView.layoutManager ?: return
        val grouping = adapter.getGroupingStrategy()
        val firstVisible = firstVisibleItemPosition(recyclerView)
        var stickyHeader = stickyOverlay.findStickyView(lastStickyItemPosition)

        val from: Int
        val to: Int
        if (lastStickyItemPosition < firstVisible + 1) {
            // Scrolling forward: look from the current header up to the end of the first row.
            from = lastStickyItemPosition
            to = firstVisible + spanCount(recyclerView)
        } else {
            // Scrolling backward: look at the group before the current one.
            from = grouping.getGroupStartPosition(lastStickyItemPosition - 1)
            to = lastStickyItemPosition
        }

        // Nothing to show any more, or the grouping changed under us: retire the current header.
        if (from == to || !grouping.isGroupPosition(lastStickyItemPosition)) {
            retire(lastStickyItemPosition)
            lastStickyItemPosition = RecyclerView.NO_POSITION
        }

        for (position in from..to) {
            if (lastStickyItemPosition == position || !grouping.isGroupPosition(position)) continue
            val startPosition = grouping.getGroupStartPosition(position)
            val groupView = layoutManager.findViewByPosition(startPosition)
            if (groupView != null) {
                if (orientationHelper.getDecoratedStart(groupView) <= 0) {
                    retire(lastStickyItemPosition)
                    stickyHeader = createStickyView(layoutManager, startPosition)
                    lastStickyItemPosition = startPosition
                }
            } else {
                // The group's first row is off screen already: we got here scrolling backward.
                val firstView = layoutManager.findViewByPosition(firstVisible)
                if (firstView != null && orientationHelper.getDecoratedEnd(firstView) >= 0) {
                    retire(lastStickyItemPosition)
                    stickyHeader = createStickyView(layoutManager, startPosition)
                    lastStickyItemPosition = startPosition
                }
            }
            break
        }

        stickyHeader?.let { pinHeader(it, recyclerView, grouping, firstVisible, layoutManager) }
    }

    /** Pins [header] to the top and slides it away while the next group's header pushes against it. */
    private fun pinHeader(
        header: View,
        recyclerView: RecyclerView,
        grouping: StickyGroupingStrategy<*, *>,
        firstVisible: Int,
        layoutManager: RecyclerView.LayoutManager,
    ) {
        header.translationY = 0f
        if (header.top != 0) header.offsetTopAndBottom(-header.top)
        val nextGroup = (firstVisible + 1..lastVisibleItemPosition(recyclerView)).firstOrNull(grouping::isGroupPosition)
        val nextView = nextGroup?.let(layoutManager::findViewByPosition) ?: return
        if (nextView.top < header.height) header.translationY = (nextView.top - header.height).toFloat()
    }

    private fun retire(position: Int) {
        val view = stickyOverlay.findStickyView(position) ?: return
        recycler.recycle(adapter.getStickyViewType(position), view)
    }

    private fun createStickyView(layoutManager: RecyclerView.LayoutManager, position: Int): View? {
        val viewType = adapter.getStickyViewType(position)
        stickyOverlay.findStickyView(position)?.let {
            adapter.onBindStickyView(it, viewType, position)
            return it
        }
        recycler.fromScrap(position, viewType)?.let {
            // Recycled views are already measured and laid out.
            adapter.onBindStickyView(it, viewType, position)
            stickyOverlay.addView(it)
            return it
        }

        val groupView = layoutManager.findViewByPosition(position) ?: return null
        val view = recycler.create(stickyOverlay.getOverlayView(), position, viewType)
        val otherSize = orientationHelper.getDecoratedMeasurementInOther(groupView)
        if (orientation == RecyclerView.HORIZONTAL) {
            stickyOverlay.measureChild(
                view,
                View.MeasureSpec.makeMeasureSpec(ViewGroup.LayoutParams.WRAP_CONTENT, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(otherSize, View.MeasureSpec.EXACTLY),
            )
            view.layout(groupView.left, groupView.top, groupView.left + view.measuredWidth, groupView.bottom)
        } else {
            stickyOverlay.measureChild(
                view,
                View.MeasureSpec.makeMeasureSpec(otherSize, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ViewGroup.LayoutParams.WRAP_CONTENT, View.MeasureSpec.AT_MOST),
            )
            view.layout(groupView.left, groupView.top, groupView.right, groupView.top + view.measuredHeight)
        }
        adapter.onBindStickyView(view, viewType, position)
        stickyOverlay.addView(view)
        return view
    }

    /** Pool of sticky views by view type. */
    private inner class StickyRecycler {
        private val scrap = SparseArray<ArrayDeque<View>>()

        fun recycle(viewType: Int, view: View) {
            stickyOverlay.remove(view)
            (scrap[viewType] ?: ArrayDeque<View>().also { scrap.put(viewType, it) }).add(view)
        }

        fun fromScrap(position: Int, viewType: Int): View? =
            scrap[viewType]?.pollFirst()?.also { (it.layoutParams as StickyOverlayViewGroup.LayoutParams).position = position }

        fun create(parent: ViewGroup, position: Int, viewType: Int): View {
            val view = scrap[viewType]?.pollFirst() ?: adapter.onCreateStickyView(parent, viewType).also { tonal(it) }
            if (view.layoutParams !is StickyOverlayViewGroup.LayoutParams) {
                view.layoutParams = stickyOverlay.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            (view.layoutParams as StickyOverlayViewGroup.LayoutParams).position = position
            return view
        }

        /** Headers without their own background get the tonal container fill. */
        private fun tonal(view: View) {
            if (view.background == null) {
                view.setBackgroundColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            }
        }
    }
}
