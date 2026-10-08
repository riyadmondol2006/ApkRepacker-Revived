package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.dynamic.DynamicWrapperAdapter

/**
 * @author Created by cz
 * @date 2020-03-17 19:31
 * @email bingo110@126.com
 *
 * Makes the wrapped adapter's items (and fixed views) draggable. The drag gesture is handled by
 * [DragItemTouchHelperCallback], which lifts the dragged row with springs; what may be dragged
 * is decided by [DragCallback] (this adapter, or a delegate set with [setDragDelegate]).
 */
open class DragWrapperAdapter(adapter: RecyclerView.Adapter<*>) : DynamicWrapperAdapter(adapter), DragCallback {

    private val itemTouchHelper = ItemTouchHelper(DragItemTouchHelperCallback(this))

    /** Optional override of what may be dragged (e.g. only a range of positions). */
    private var delegateCallback: DragCallback? = null

    private var dragFlag = ItemTouchHelper.ACTION_STATE_IDLE

    private var fixedViewDragEnabled = true

    override fun isDragEnable(position: Int): Boolean = when {
        findPosition(position) != RecyclerView.NO_POSITION -> fixedViewDragEnabled
        else -> delegateCallback?.isDragEnable(position - getExtraViewCount(position)) ?: true
    }

    override fun isLongPressDragEnabled(): Boolean = delegateCallback?.isLongPressDragEnabled() ?: true

    open fun isSwipeDirectionEnabled(): Boolean = delegateCallback?.isLongPressDragEnabled() ?: true

    override fun getDragFlag(): Int = delegateCallback?.getDragFlag() ?: dragFlag

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        dragFlag = when (recyclerView.layoutManager) {
            is GridLayoutManager, is StaggeredGridLayoutManager ->
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            else -> ItemTouchHelper.UP or ItemTouchHelper.DOWN
        }
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(null)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /** Moves the item at [oldPosition] to [newPosition], updating the data and animating the move. */
    open fun move(oldPosition: Int, newPosition: Int) {
        movePosition(oldPosition, newPosition)
        notifyItemMoved(oldPosition, newPosition)
    }

    private fun movePosition(oldPosition: Int, newPosition: Int) {
        val touchesFixedEnd = isHeaderPosition(oldPosition) || isFooterPosition(oldPosition) ||
            isHeaderPosition(newPosition) || isFooterPosition(newPosition)
        if (touchesFixedEnd) return
        // A long drag is a series of single-step swaps.
        val step = if (oldPosition < newPosition) 1 else -1
        var position = oldPosition
        while (position != newPosition) {
            moveInternal(position, position + step)
            position += step
        }
    }

    /** Swaps two adjacent positions, whichever of them hold fixed views. */
    private fun moveInternal(oldPosition: Int, newPosition: Int) {
        val oldIndex = findPosition(oldPosition)
        val newIndex = findPosition(newPosition)
        val oldIsFixed = oldIndex != RecyclerView.NO_POSITION
        val newIsFixed = newIndex != RecyclerView.NO_POSITION
        when {
            oldIsFixed && newIsFixed -> {
                setFixedViewPosition(oldIndex, newPosition)
                setFixedViewPosition(newIndex, oldPosition)
            }
            oldIsFixed -> setFixedViewPosition(oldIndex, newPosition)
            newIsFixed -> setFixedViewPosition(newIndex, oldPosition)
            else -> (getAdapter() as? Moveable)?.move(
                oldPosition - getExtraViewCount(oldPosition),
                newPosition - getExtraViewCount(newPosition),
            )
        }
    }

    open fun setFixedViewDragEnabled(fixedViewDragEnabled: Boolean) {
        this.fixedViewDragEnabled = fixedViewDragEnabled
    }

    /**
     * Replaces the drag rules.
     * @see DragCallback.getDragFlag
     * @see DragCallback.isLongPressDragEnabled
     * @see DragCallback.isDragEnable
     */
    open fun setDragDelegate(callback: DragCallback?) {
        delegateCallback = callback
    }
}
