package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag

import androidx.recyclerview.widget.ItemTouchHelper

/**
 * @author Created by cz
 * @date 2020-03-19 20:53
 * @email bingo110@126.com
 *
 * Default [DragCallback]: every item can be dragged in every direction after a long press.
 */
open class DefaultDragCallback : DragCallback {
    override fun isDragEnable(position: Int): Boolean = true

    override fun isLongPressDragEnabled(): Boolean = true

    override fun getDragFlag(): Int =
        ItemTouchHelper.DOWN or ItemTouchHelper.UP or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
}
