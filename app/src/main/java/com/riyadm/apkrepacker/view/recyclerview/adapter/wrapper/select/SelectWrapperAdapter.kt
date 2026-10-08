package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.select

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * @author Created by cz
 * @date 2020-03-17 21:48
 * @email bingo110@126.com
 *
 * Selection on top of [HeaderWrapperAdapter] (so headers and footers keep working), in one of
 * four modes: plain [CLICK], [SINGLE_SELECT], [MULTI_SELECT] or [RECTANGLE_SELECT] (a range: the
 * first click sets the start, the second the end). The wrapped adapter shows the state by
 * implementing [Selectable].
 *
 * See also https://proandroiddev.com/a-guide-to-recyclerview-selection-3ed9f2381504 and
 * recyclerview-selection for a library alternative.
 */
open class SelectWrapperAdapter(adapter: RecyclerView.Adapter<*>?) : HeaderWrapperAdapter(adapter) {

    private var selectPosition = INVALID_POSITION
    private var start = INVALID_POSITION
    private var end = INVALID_POSITION
    private var mode = CLICK

    /** Multi-choice result. */
    private val multiSelectItems = mutableListOf<Int>()

    /** Largest number of items multi-choice allows. */
    private var selectMaxCount = Int.MAX_VALUE

    private var singleSelectListener: OnSingleSelectListener? = null
    private var multiSelectListener: OnMultiSelectListener? = null
    private var rectangleSelectListener: OnRectangleSelectListener? = null

    /**
     * Changes the choice mode (default [CLICK]) and clears the current selection.
     *
     * @see CLICK
     * @see SINGLE_SELECT
     * @see MULTI_SELECT
     * @see RECTANGLE_SELECT
     */
    open fun setSelectMode(newMode: Int) {
        when (mode) {
            SINGLE_SELECT -> {
                val last = selectPosition
                if (last != INVALID_POSITION) notifyItemChanged(last)
            }
            MULTI_SELECT -> {
                multiSelectItems.forEach { notifyItemChanged(it) }
                multiSelectItems.clear()
            }
            RECTANGLE_SELECT -> {
                val first = min(start, end)
                notifyItemRangeChanged(first, max(start, end) - first + 1)
            }
        }
        start = INVALID_POSITION
        end = INVALID_POSITION
        selectPosition = INVALID_POSITION
        mode = newMode
    }

    override fun addHeaderView(view: View) {
        super.addHeaderView(view)
        // Selected positions are adapter positions, so a new header shifts all of them.
        shiftSelection(1)
    }

    override fun removeHeaderView(view: View?) {
        super.removeHeaderView(view)
        shiftSelection(-1)
    }

    private fun shiftSelection(offset: Int) {
        when (mode) {
            SINGLE_SELECT -> selectPosition += offset
            MULTI_SELECT -> for (i in multiSelectItems.indices) multiSelectItems[i] += offset
            RECTANGLE_SELECT -> {
                if (start != INVALID_POSITION) start += offset
                if (end != INVALID_POSITION) end += offset
            }
        }
    }

    open fun setSelectMaxCount(count: Int) {
        selectMaxCount = count
    }

    open fun setMultiSelectItems(list: List<Int>?) {
        multiSelectItems.clear()
        list?.let(multiSelectItems::addAll)
        multiSelectItems.forEach { notifyItemChanged(it) }
    }

    /** Selects [position] (relative to the wrapped adapter) in single-choice mode. */
    open fun setSingleSelectPosition(position: Int) {
        val last = selectPosition
        selectPosition = position
        val headers = getHeaderViewCount()
        if (last in 0 until itemCount) notifyItemChanged(last + headers)
        if (position in 0 until itemCount) notifyItemChanged(position + headers)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val selected = when (mode) {
            SINGLE_SELECT -> selectPosition == position
            MULTI_SELECT -> position in multiSelectItems
            RECTANGLE_SELECT -> when {
                start == INVALID_POSITION -> false
                end == INVALID_POSITION -> position == start
                else -> position in min(start, end)..max(start, end)
            }
            else -> false
        }
        setSelectPosition(holder, position, getHeaderViewCount(), getFooterViewCount(), selected)
    }

    protected open fun setSelectPosition(
        holder: RecyclerView.ViewHolder,
        position: Int,
        headerCount: Int,
        footerCount: Int,
        select: Boolean,
    ) {
        val inItems = position in headerCount..(itemCount - footerCount)
        @Suppress("UNCHECKED_CAST")
        val selectable = getAdapter() as? Selectable<RecyclerView.ViewHolder> ?: return
        if (inItems) selectable.onSelectItem(holder, position - headerCount, select)
    }

    override fun onItemClick(v: View, position: Int, adapterPosition: Int): Boolean {
        when (mode) {
            MULTI_SELECT -> {
                var lastCount = multiSelectItems.size
                if (adapterPosition in multiSelectItems) {
                    lastCount--
                    multiSelectItems.remove(adapterPosition)
                    notifyItemChanged(adapterPosition)
                } else if (multiSelectItems.size < selectMaxCount) {
                    multiSelectItems.add(adapterPosition)
                    notifyItemChanged(adapterPosition)
                }
                multiSelectListener?.onMultiSelect(v, multiSelectItems, lastCount, selectMaxCount)
            }
            RECTANGLE_SELECT -> when {
                // A finished range: the next click clears it.
                start != INVALID_POSITION && end != INVALID_POSITION -> {
                    val (first, last) = start to end
                    start = INVALID_POSITION
                    end = INVALID_POSITION
                    notifyItemRangeChanged(min(first, last), abs(first - last) + 1)
                }
                start == INVALID_POSITION -> {
                    start = adapterPosition
                    notifyItemChanged(adapterPosition)
                }
                else -> {
                    end = adapterPosition
                    rectangleSelectListener?.onRectangleSelect(start, end)
                    notifyItemRangeChanged(min(start, end), abs(start - end) + 1)
                }
            }
            SINGLE_SELECT -> {
                val last = selectPosition
                selectPosition = adapterPosition
                singleSelectListener?.onSingleSelect(v, adapterPosition, last)
                if (last != INVALID_POSITION) notifyItemChanged(last) // deselect the previous row
                notifyItemChanged(adapterPosition)
            }
        }
        return mode == CLICK
    }

    /** The selected position in single-choice mode. */
    open fun getSelectPosition(): Int = selectPosition

    /** Selects the range [start]..[end] in rectangle mode. */
    open fun setRectangleSelectPosition(start: Int, end: Int) {
        this.start = start
        this.end = end
        notifyItemRangeChanged(start, end - start)
    }

    open fun setOnSingleSelectListener(singleSelectListener: OnSingleSelectListener?) {
        this.singleSelectListener = singleSelectListener
    }

    open fun setOnMultiSelectListener(multiSelectListener: OnMultiSelectListener?) {
        this.multiSelectListener = multiSelectListener
    }

    open fun setOnRectangleSelectListener(rectangleSelectListener: OnRectangleSelectListener?) {
        this.rectangleSelectListener = rectangleSelectListener
    }

    fun interface OnSingleSelectListener {
        fun onSingleSelect(v: View?, newPosition: Int, oldPosition: Int)
    }

    fun interface OnMultiSelectListener {
        fun onMultiSelect(v: View?, selectPositions: List<Int>?, lastSelectCount: Int, maxCount: Int)
    }

    fun interface OnRectangleSelectListener {
        fun onRectangleSelect(startPosition: Int, endPosition: Int)
    }

    companion object {
        const val INVALID_POSITION = -1
        const val CLICK = 0
        const val SINGLE_SELECT = 1
        const val MULTI_SELECT = 2
        const val RECTANGLE_SELECT = 3
    }
}
