package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag

import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/**
 * @author Created by cz
 * @date 2020-03-19 15:32
 * @email bingo110@126.com
 *
 * Drag-only [ItemTouchHelper.Callback] for a [DragWrapperAdapter]. The picked-up row lifts with
 * springs: it scales up slightly and rises on the z axis, and a [MaterialCardView] additionally
 * switches to its dragged (elevated, tonal) appearance. Dropping springs everything back.
 */
open class DragItemTouchHelperCallback(private val adapter: DragWrapperAdapter?) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = adapter?.isLongPressDragEnabled() ?: super.isLongPressDragEnabled()

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        if (adapter != null && adapter.isDragEnable(viewHolder.bindingAdapterPosition)) {
            makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, adapter.getDragFlag())
        } else {
            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.DOWN)
        }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val adapter = adapter ?: return false
        val targetPosition = target.bindingAdapterPosition
        if (!adapter.isDragEnable(targetPosition)) return false
        adapter.move(viewHolder.bindingAdapterPosition, targetPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            lift(viewHolder, lifted = true)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        lift(viewHolder, lifted = false)
    }

    private fun lift(viewHolder: RecyclerView.ViewHolder, lifted: Boolean) {
        val view = viewHolder.itemView
        (view as? MaterialCardView)?.isDragged = lifted
        val scale = if (lifted) LIFT_SCALE else 1f
        val z = if (lifted) LIFT_DP * view.resources.displayMetrics.density else 0f
        view.springTo(DynamicAnimation.SCALE_X, scale, MotionSpring.FastSpatial)
        view.springTo(DynamicAnimation.SCALE_Y, scale, MotionSpring.FastSpatial)
        view.springTo(DynamicAnimation.TRANSLATION_Z, z, MotionSpring.FastSpatial)
    }

    private companion object {
        const val LIFT_SCALE = 1.03f
        const val LIFT_DP = 8f
    }
}
