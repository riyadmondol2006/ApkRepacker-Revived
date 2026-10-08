package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.graphics.Canvas
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.riyadm.apkrepacker.view.recyclerview.adapter.BaseAdapter
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group.CompareGroupCondition
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group.GroupCondition

/**
 * @author Created by cz
 * @date 2020-03-24 16:58
 * @email bingo110@126.com
 *
 * Adapter with sticky group headers. Set a [GroupCondition] ([setCondition]) or a
 * [CompareGroupCondition] ([setCompareCondition]) to say where groups start, and implement the
 * [StickyCallback] functions to create and bind the header view.
 */
abstract class StickyAdapter<VH : RecyclerView.ViewHolder, E>(itemList: List<E>?) :
    BaseAdapter<VH, E>(itemList), StickyCallback<E> {

    /** Not the layout manager itself: it may be replaced while the adapter is attached. */
    private var recyclerView: RecyclerView? = null

    private val groupingStrategy: StickyGroupingStrategy<StickyAdapter<VH, E>, E> = StickyGroupingStrategy.of(this)

    open fun setCompareCondition(compareCondition: CompareGroupCondition<E>?) =
        groupingStrategy.setCompareCondition(compareCondition)

    open fun setCondition(condition: GroupCondition<E>?) = groupingStrategy.setCondition(condition)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        (recyclerView.layoutManager as? GridLayoutManager)?.let { manager ->
            manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (isFillStickyHeader(position)) manager.spanCount else 1
            }
        }

        val overlay = StickyOverlayViewGroup(recyclerView)
        val overlayView = overlay.getOverlayView()
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                overlayView.draw(c)
            }
        })
        val scrollListener = StickyRecyclerViewScrollListener(recyclerView, this, overlay)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) =
                scrollListener.onScrolled(recyclerView, dx, dy)
        })
        // New data can move the headers, so refresh them.
        groupingStrategy.setOnAdapterDataChangeListener { scrollListener.onScrolled(recyclerView, 0, 0) }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /**
     * Columns of a grid layout: with several columns more than one group header can be on screen
     * per row, which matters when checking whether two items share a group.
     */
    open fun getSpanCount(): Int = when (val manager = recyclerView?.layoutManager) {
        is GridLayoutManager -> manager.spanCount
        is StaggeredGridLayoutManager -> manager.spanCount
        else -> 1
    }

    override fun isFillStickyHeader(position: Int): Boolean = groupingStrategy.isGroupPosition(position)

    override fun getStickyViewType(position: Int): Int = 0

    override fun getGroupingStrategy(): StickyGroupingStrategy<StickyAdapter<VH, E>, E> = groupingStrategy
}
