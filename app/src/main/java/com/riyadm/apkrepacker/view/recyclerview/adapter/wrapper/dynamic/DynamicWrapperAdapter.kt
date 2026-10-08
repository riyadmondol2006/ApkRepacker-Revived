package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.dynamic

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter
import kotlin.math.min

/**
 * @author Created by cz
 * @date 2020-03-17 11:36
 * @email bingo110@126.com
 *
 * Like [HeaderWrapperAdapter], but extra views can be inserted at any position. The extra views
 * live in this wrapper's own list of (view, position) pairs; the wrapped adapter's data never
 * changes:
 *
 * ```
 * wrapped adapter:  ["a","b","c","d","e","f"]
 * extra view "xx" at position 2:
 * wrapper shows:    ["a","b", xx ,"c","d","e","f"]
 * ```
 *
 * Asking for position 3 yields "c"; to map back, the extra views in front of a position are
 * subtracted ([getExtraViewCount]). Useful for ads in a feed, or dragging arbitrary views with
 * [com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag.DragWrapperAdapter].
 */
open class DynamicWrapperAdapter(adapter: RecyclerView.Adapter<*>?) : HeaderWrapperAdapter(adapter) {

    private val fixedViews = mutableListOf<FixedViewInfo>()

    /** Counts the views ever added, so each one gets a unique view type. */
    private var dynamicCount = 0

    override fun findView(id: Int): View? =
        super.findView(id) ?: fixedViews.firstNotNullOfOrNull { it.view?.findViewById<View>(id) }

    /** Inserts [view] so that it ends up at [position] of this adapter. */
    open fun addAdapterView(view: View, position: Int) {
        // Everything at or after the insertion point shifts down by one.
        fixedViews.filter { it.position >= position }.forEach { it.position++ }
        fixedViews += FixedViewInfo(TYPE_DYNAMIC + dynamicCount++, view, position)
        fixedViews.sortBy { it.position }
        notifyItemInserted(position)
    }

    open fun removeAdapterView(view: View?) {
        val index = fixedViews.indexOfFirst { it.view === view }
        if (index != -1) removeAdapterView(index)
    }

    /** Removes the extra view at [position] in the list of extra views (not the adapter position). */
    open fun removeAdapterView(position: Int) {
        val removed = fixedViews.removeAt(position)
        // Everything behind the removed view moves up by one.
        fixedViews.filter { it.position > removed.position }.forEach { it.position-- }
        notifyItemRemoved(removed.position)
    }

    /** Index in the extra-view list of the view sitting at adapter [position], or [RecyclerView.NO_POSITION]. */
    open fun findPosition(position: Int): Int =
        fixedViews.binarySearchBy(position) { it.position }.takeIf { it >= 0 } ?: RecyclerView.NO_POSITION

    protected open fun setFixedViewPosition(index: Int, position: Int) {
        fixedViews.getOrNull(index)?.position = position
        fixedViews.sortBy { it.position }
    }

    /** Number of extra views added with [addAdapterView]. */
    open fun getExtraViewCount(): Int = fixedViews.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = fixedViews.lastOrNull { it.viewType == viewType }?.view
        return if (view != null) object : RecyclerView.ViewHolder(view) {} else super.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (findPosition(position) == RecyclerView.NO_POSITION) super.onBindViewHolder(holder, position)
    }

    override fun getItemViewType(position: Int): Int {
        val index = findPosition(position)
        return if (index == RecyclerView.NO_POSITION) super.getItemViewType(position) else fixedViews[index].viewType
    }

    override fun getItemCount(): Int = fixedViews.size + super.getItemCount()

    override fun itemRangeInsert(positionStart: Int, itemCount: Int) {
        val position = getOffsetPosition(positionStart)
        fixedViews.filter { position < it.position }.forEach { it.position += itemCount }
        // If an extra view sits at the insertion point it stays in front of the new items.
        if (findPosition(position) != RecyclerView.NO_POSITION) {
            notifyItemRangeInserted(position + 1, itemCount)
        } else {
            notifyItemRangeInserted(position, itemCount)
        }
    }

    override fun itemRangeRemoved(positionStart: Int, itemCount: Int) {
        val start = getOffsetPosition(positionStart)
        // The wrapped adapter already dropped its items, so our count is short by itemCount.
        val end = min(getItemCount() + itemCount, getOffsetPosition(start + itemCount))
        val removedCount = end - start
        // Extra views inside the removed range go with it.
        fixedViews.removeAll { it.position in start until end }
        fixedViews.filter { end <= it.position }.forEach { it.position -= removedCount }
        notifyItemRangeRemoved(start, removedCount)
    }

    /**
     * Converts a position of the wrapped adapter into a position of this adapter by stepping
     * over every extra view in front of it. The change callbacks of the wrapped adapter
     * ([itemRangeRemoved], [itemRangeInsert]) report positions in its own, smaller, coordinates.
     */
    override fun getOffsetPosition(position: Int): Int {
        var skipped = 0
        var total = position
        val target = getExtraViewCount(position)
        while (skipped < target) {
            if (findPosition(total) == RecyclerView.NO_POSITION) skipped++
            total++
        }
        return total
    }

    override fun getExtraViewCount(position: Int): Int {
        val found = fixedViews.binarySearchBy(position) { it.position }
        // Found: its index equals the number of extra views before it. Missing: -(insertionPoint) - 1.
        val before = if (found >= 0) found else -found - 1
        return super.getExtraViewCount(position) + before
    }

    override fun isExtraPosition(position: Int): Boolean =
        super.isExtraPosition(position) || findPosition(position) != RecyclerView.NO_POSITION

    open class FixedViewInfo(
        @JvmField val viewType: Int,
        @JvmField val view: View?,
        @JvmField var position: Int,
    )

    private companion object {
        const val TYPE_DYNAMIC = -1 shl 8
    }
}
