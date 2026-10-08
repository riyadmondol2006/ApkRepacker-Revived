package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.view.View
import android.view.ViewGroup

/**
 * @author Created by cz
 * @date 2020-03-24 17:02
 * @email bingo110@126.com
 *
 * Creates and binds the sticky header views. Implemented by [StickyAdapter].
 */
interface StickyCallback<E> {
    /** The data object at [position]. */
    fun getItem(position: Int): E

    fun getGroupingStrategy(): StickyGroupingStrategy<*, *>

    /**
     * For a GridLayoutManager the sticky header can fill a whole row. [position] is the
     * position of the item in the grouped list.
     */
    fun isFillStickyHeader(position: Int): Boolean

    /** View type of the sticky header for [position]; different types can use different layouts. */
    fun getStickyViewType(position: Int): Int

    /** Creates the sticky header view for [viewType]. */
    fun onCreateStickyView(view: ViewGroup, viewType: Int): View

    /** Binds the group header at [position] to [view]. */
    fun onBindStickyView(view: View, viewType: Int, position: Int)
}
