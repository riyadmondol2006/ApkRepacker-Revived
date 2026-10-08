package com.riyadm.apkrepacker.view.recyclerview.adapter.support.swipe

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.riyadm.apkrepacker.view.recyclerview.adapter.BaseAdapter

/**
 * @author Created by cz
 * @date 2020-03-25 18:27
 * @email bingo110@126.com
 *
 * Adapter whose rows reveal a swipe menu (see [onCreateSwipeMenuView]) behind them. The gesture
 * and the spring motion live in [SwipeItemTouchHelperCallback].
 */
abstract class SwipeAdapter<VH : RecyclerView.ViewHolder, E>(itemList: List<E>?) :
    BaseAdapter<VH, E>(itemList), SwipeCallback {

    /** Optional override of what can be swiped (e.g. only a range of positions). */
    private var delegateCallback: SwipeCallback? = null

    private var swipeFlag = ItemTouchHelper.ACTION_STATE_IDLE

    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        swipeFlag = when (val layoutManager = recyclerView.layoutManager) {
            is GridLayoutManager, is StaggeredGridLayoutManager ->
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            is LinearLayoutManager ->
                if (layoutManager.orientation == RecyclerView.VERTICAL) {
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                } else {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN
                }
            else -> swipeFlag
        }
        val callback = SwipeItemTouchHelperCallback(this)
        callback.attachedToRecyclerView(recyclerView)
        itemTouchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recyclerView) }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /**
     * View type of the swipe menu for [position]; override when rows have different menu layouts.
     */
    open fun getSwipeMenuViewType(position: Int): Int = 0

    /** Creates the swipe menu view for [viewType]. */
    abstract fun onCreateSwipeMenuView(context: Context, parent: ViewGroup, viewType: Int): View

    /** Binds data to the swipe menu [view] of the row at [position]. */
    abstract fun onBindSwipeMenuView(context: Context, view: View, viewType: Int, position: Int)

    override fun getMoveFlag(): Int = delegateCallback?.getMoveFlag() ?: swipeFlag

    override fun isSwipeEnable(position: Int): Boolean = delegateCallback?.isSwipeEnable(position) ?: true

    override fun isSwipeDirectionEnabled(direction: Int): Boolean =
        delegateCallback?.isSwipeDirectionEnabled(direction) ?: true

    open fun setDelegateCallback(delegateCallback: SwipeCallback?) {
        this.delegateCallback = delegateCallback
    }
}
