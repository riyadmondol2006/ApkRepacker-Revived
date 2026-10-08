package com.riyadm.apkrepacker.view.recyclerview.adapter

import androidx.recyclerview.widget.RecyclerView

/**
 * @author Created by cz
 * @date 2020-03-17 20:39
 * @email bingo110@126.com
 *
 * Marks an adapter as a wrapper around another adapter (headers, footers, injected views,
 * dragging...). The wrapped adapter reports its changes through the `item*` functions and the
 * wrapper translates positions into its own coordinates.
 */
interface WrapperAdapter {
    /** The wrapped (original) adapter. */
    fun getAdapter(): RecyclerView.Adapter<*>?

    /**
     * Converts a position in the wrapped adapter into a wrapper position. With two header views,
     * position 1 becomes 3.
     */
    fun getOffsetPosition(position: Int): Int

    /**
     * Number of extra views (headers, injected views...) in front of the wrapper [position]. With
     * two header views and position 1 this is 2. It lets the wrapped adapter map positions back.
     */
    fun getExtraViewCount(position: Int): Int

    fun onChanged()

    fun itemRangeInsert(positionStart: Int, itemCount: Int)

    fun itemRangeChanged(positionStart: Int, itemCount: Int)

    fun itemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?)

    fun itemRangeRemoved(positionStart: Int, itemCount: Int)

    fun itemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int)
}
