package com.riyadm.apkrepacker.view.recyclerview.adapter.support.swipe

import android.graphics.Canvas
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo
import java.util.ArrayDeque

private fun <T> SparseArray<T>.getOrPut(key: Int, default: () -> T): T = get(key) ?: default().also { put(key, it) }

/**
 * @author Created by cz
 * @date 2020-03-19 15:32
 * @email bingo110@126.com
 *
 * Reveals a swipe menu behind a row while it is dragged sideways. The menu scales and fades in
 * with the swipe progress and the swiped row lifts (elevation morph on [MaterialCardView]s plus a
 * z spring) for the duration of the gesture; the menu is recycled when the row has settled.
 */
open class SwipeItemTouchHelperCallback(private val adapter: SwipeAdapter<*, *>) : ItemTouchHelper.Callback() {

    private val recyclerBin = RecyclerBin()
    private var overlay: SwipeOverlayViewGroup? = null

    open fun attachedToRecyclerView(recyclerView: RecyclerView) {
        overlay = SwipeOverlayViewGroup(recyclerView)
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        if (adapter.isSwipeEnable(viewHolder.bindingAdapterPosition)) {
            makeFlag(ACTION_STATE_SWIPE, adapter.getMoveFlag())
        } else {
            makeFlag(ACTION_STATE_IDLE, ItemTouchHelper.DOWN)
        }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val menu = overlay?.findAdapterView(viewHolder.bindingAdapterPosition)
        if (menu == null) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }
        val itemView = viewHolder.itemView
        // The row only travels as far as the menu is wide when it is swiped across its full width.
        var translationX = dX
        if (dX < 0 && actionState == ACTION_STATE_SWIPE) {
            translationX = dX * menu.width / itemView.width
        }
        menu.translationX = translationX
        revealMenu(menu, progress = if (menu.width > 0) (-translationX / menu.width).coerceIn(0f, 1f) else 0f)
        overlay?.getOverlayView()?.draw(c)
        super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive)
    }

    /** The menu grows from 85% and fades in as the row slides away. */
    private fun revealMenu(menu: View, progress: Float) {
        val scale = MENU_MIN_SCALE + (1f - MENU_MIN_SCALE) * progress
        menu.alpha = progress
        menu.scaleX = scale
        menu.scaleY = scale
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState != ACTION_STATE_SWIPE || viewHolder == null) return
        val position = viewHolder.bindingAdapterPosition
        val overlay = overlay
        if (position == RecyclerView.NO_POSITION || overlay == null || !adapter.isSwipeEnable(position)) return

        liftRow(viewHolder.itemView, lifted = true)
        if (overlay.findAdapterView(position) != null) return

        // Create the menu behind the row, taking a recycled one when possible.
        val itemView = viewHolder.itemView
        val viewType = adapter.getSwipeMenuViewType(position)
        val menu = recyclerBin.obtain(overlay, position, viewType, adapter)
        overlay.measureChild(
            menu,
            View.MeasureSpec.makeMeasureSpec(ViewGroup.LayoutParams.WRAP_CONTENT, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(itemView.height, View.MeasureSpec.EXACTLY),
        )
        layoutSwipeView(itemView, menu, ItemTouchHelper.RIGHT)
        overlay.add(menu)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        liftRow(viewHolder.itemView, lifted = false)
        val overlay = overlay ?: return
        // The row is back in place: retire its menu.
        for (i in overlay.getChildCount() - 1 downTo 0) {
            val menu = overlay.getChildAt(i) ?: continue
            val params = menu.layoutParams as SwipeOverlayViewGroup.LayoutParams
            if (params.position == viewHolder.bindingAdapterPosition || viewHolder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                overlay.remove(menu)
                recyclerBin.recycle(adapter.getSwipeMenuViewType(params.position), menu)
            }
        }
    }

    /** Elevation morph: cards switch to their dragged appearance, every row gets a z spring. */
    private fun liftRow(itemView: View, lifted: Boolean) {
        (itemView as? MaterialCardView)?.isDragged = lifted
        val z = if (lifted) LIFT_DP * itemView.resources.displayMetrics.density else 0f
        itemView.springTo(DynamicAnimation.TRANSLATION_Z, z, MotionSpring.FastSpatial)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    /** An open menu can't be swiped away: the row has to travel its whole width. */
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float =
        if (overlay?.findAdapterView(viewHolder.bindingAdapterPosition) != null) 1f else super.getSwipeThreshold(viewHolder)

    /** Lays the menu [menuLayout] out next to [view], on the side given by [direction]. */
    open fun layoutSwipeView(view: View, menuLayout: View, direction: Int) {
        val width = menuLayout.measuredWidth
        val height = menuLayout.measuredHeight
        when (direction) {
            ItemTouchHelper.LEFT, ItemTouchHelper.START ->
                menuLayout.layout(view.left - width, view.top, view.left, view.bottom)
            ItemTouchHelper.RIGHT, ItemTouchHelper.END ->
                menuLayout.layout(view.right, view.top, view.right + width, view.bottom)
            ItemTouchHelper.UP -> menuLayout.layout(view.left, view.top - height, view.right, view.top)
            ItemTouchHelper.DOWN -> menuLayout.layout(view.left, view.bottom, view.right, view.bottom + height)
        }
    }

    /** Recycled menu views by view type. */
    private class RecyclerBin {
        private val scrap = SparseArray<ArrayDeque<View>>()

        fun obtain(overlay: SwipeOverlayViewGroup, position: Int, viewType: Int, adapter: SwipeAdapter<*, *>): View {
            val parent = overlay.getOverlayView()
            val view = scrap[viewType]?.pollFirst() ?: adapter.onCreateSwipeMenuView(parent.context, parent, viewType)
            if (view.layoutParams !is SwipeOverlayViewGroup.LayoutParams) {
                view.layoutParams = overlay.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            (view.layoutParams as SwipeOverlayViewGroup.LayoutParams).position = position
            adapter.onBindSwipeMenuView(parent.context, view, viewType, position)
            return view
        }

        fun recycle(viewType: Int, view: View) {
            scrap.getOrPut(viewType) { ArrayDeque() }.add(view)
        }
    }

    private companion object {
        const val MENU_MIN_SCALE = 0.85f
        const val LIFT_DP = 6f
    }
}
