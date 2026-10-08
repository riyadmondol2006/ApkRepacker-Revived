package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag

import androidx.recyclerview.widget.ItemTouchHelper

/**
 * @author Created by cz
 * @date 2020-03-19 22:35
 * @email bingo110@126.com
 *
 * Controls what a [DragWrapperAdapter] allows to be dragged.
 */
interface DragCallback {

    /** Whether the item at [position] can be dragged. */
    fun isDragEnable(position: Int): Boolean

    /** Whether a long press starts a drag. */
    fun isLongPressDragEnabled(): Boolean

    /**
     * Directions an item can be dragged in.
     * @see ItemTouchHelper.UP
     * @see ItemTouchHelper.DOWN
     * @see ItemTouchHelper.LEFT
     * @see ItemTouchHelper.RIGHT
     */
    fun getDragFlag(): Int
}
