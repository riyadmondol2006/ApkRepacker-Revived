package com.riyadm.apkrepacker.ui.motion

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlin.math.min

/**
 * RecyclerView item animator built on springs instead of fixed durations (M3 Expressive).
 *
 * - **add**: fades in (default effects spring) while scaling up from [ADD_FROM_SCALE] and rising
 *   [ADD_RISE_DP] (default spatial spring). Items appearing in the same pass are staggered by
 *   [STAGGER_MS], capped at [MAX_STAGGERED_ITEMS] so long lists don't lag.
 * - **remove**: fast effects fade plus a fast spatial scale down.
 * - **move / change**: the item springs from its old position to the new one (default spatial).
 *   Changes that swap view holders cross-fade.
 *
 * Every animation is a group of [SpringAnimation]s owned by exactly one view holder; a group
 * always finishes exactly once (spring end, or [endAnimation]/[endAnimations] cancelling it),
 * which is where the matching `dispatch*Finished` is called, so the RecyclerView can't get stuck.
 */
class SpringItemAnimator : SimpleItemAnimator() {

    private val pendingRemovals = ArrayList<RecyclerView.ViewHolder>()
    private val pendingAdditions = ArrayList<RecyclerView.ViewHolder>()
    private val pendingMoves = ArrayList<MoveInfo>()
    private val pendingChanges = ArrayList<ChangeInfo>()

    /** Running spring groups by holder; a holder runs at most one animation at a time. */
    private val running = HashMap<RecyclerView.ViewHolder, SpringGroup>()

    init {
        // Nominal values for code that sizes things by animator durations (ItemTouchHelper, scrolling).
        addDuration = NOMINAL_DURATION_MS
        removeDuration = NOMINAL_DURATION_MS / 2
        moveDuration = NOMINAL_DURATION_MS
        changeDuration = NOMINAL_DURATION_MS
    }

    override fun canReuseUpdatedViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        payloads: MutableList<Any>,
    ): Boolean = payloads.isNotEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads)

    // region request

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)
        if (!holder.itemView.animationsEnabled()) {
            dispatchRemoveFinished(holder)
            return false
        }
        pendingRemovals += holder
        return true
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)
        if (!holder.itemView.animationsEnabled()) {
            dispatchAddFinished(holder)
            return false
        }
        holder.itemView.apply {
            alpha = 0f
            scaleX = ADD_FROM_SCALE
            scaleY = ADD_FROM_SCALE
            translationY = ADD_RISE_DP * resources.displayMetrics.density
        }
        pendingAdditions += holder
        return true
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
    ): Boolean {
        val view = holder.itemView
        val startX = fromX + view.translationX.toInt()
        val startY = fromY + view.translationY.toInt()
        endAnimation(holder)
        val dx = toX - startX
        val dy = toY - startY
        if (dx == 0 && dy == 0 || !view.animationsEnabled()) {
            dispatchMoveFinished(holder)
            return false
        }
        view.translationX = -dx.toFloat()
        view.translationY = -dy.toFloat()
        pendingMoves += MoveInfo(holder)
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder?,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
    ): Boolean {
        // Same holder re-bound in place: only its position can animate.
        if (oldHolder === newHolder) return animateMove(oldHolder, fromX, fromY, toX, toY)

        val oldView = oldHolder.itemView
        val prevTranslationX = oldView.translationX
        val prevTranslationY = oldView.translationY
        val prevAlpha = oldView.alpha
        endAnimation(oldHolder)
        if (newHolder != null) endAnimation(newHolder)
        if (!oldView.animationsEnabled()) {
            dispatchChangeFinished(oldHolder, true)
            if (newHolder != null) dispatchChangeFinished(newHolder, false)
            return false
        }
        val dx = (toX - fromX - prevTranslationX).toInt()
        val dy = (toY - fromY - prevTranslationY).toInt()
        oldView.translationX = prevTranslationX
        oldView.translationY = prevTranslationY
        oldView.alpha = prevAlpha
        newHolder?.itemView?.apply {
            translationX = -dx.toFloat()
            translationY = -dy.toFloat()
            alpha = 0f
        }
        pendingChanges += ChangeInfo(oldHolder, newHolder)
        return true
    }

    // endregion

    // region run

    override fun runPendingAnimations() {
        val hasRemovals = pendingRemovals.isNotEmpty()
        val hasMoves = pendingMoves.isNotEmpty()
        val hasChanges = pendingChanges.isNotEmpty()
        val hasAdditions = pendingAdditions.isNotEmpty()
        if (!hasRemovals && !hasMoves && !hasAdditions && !hasChanges) return

        val removals = pendingRemovals.toList().also { pendingRemovals.clear() }
        val moves = pendingMoves.toList().also { pendingMoves.clear() }
        val changes = pendingChanges.toList().also { pendingChanges.clear() }
        val additions = pendingAdditions.sortedBy { it.layoutPosition }.also { pendingAdditions.clear() }

        removals.forEach(::startRemove)
        moves.forEach { startMove(it.holder) }
        changes.forEach(::startChange)

        // New rows wait a beat for rows that are leaving so the list doesn't overlap itself.
        val baseDelay = if (hasRemovals) REMOVAL_LEAD_MS else 0L
        additions.forEachIndexed { index, holder ->
            startAdd(holder, baseDelay + min(index, MAX_STAGGERED_ITEMS) * STAGGER_MS)
        }
    }

    private fun startRemove(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val group = SpringGroup(view) {
            view.resetAnimatedValues()
            dispatchRemoveFinished(holder)
            running.remove(holder)
            dispatchFinishedWhenDone()
        }
        running[holder] = group
        dispatchRemoveStarting(holder)
        group.spring(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects)
        group.spring(DynamicAnimation.SCALE_X, REMOVE_TO_SCALE, MotionSpring.FastSpatial)
        group.spring(DynamicAnimation.SCALE_Y, REMOVE_TO_SCALE, MotionSpring.FastSpatial)
        group.start()
    }

    private fun startAdd(holder: RecyclerView.ViewHolder, delayMs: Long) {
        val view = holder.itemView
        val group = SpringGroup(view) {
            view.resetAnimatedValues()
            dispatchAddFinished(holder)
            running.remove(holder)
            dispatchFinishedWhenDone()
        }
        running[holder] = group
        dispatchAddStarting(holder)
        group.spring(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
        group.spring(DynamicAnimation.SCALE_X, 1f, MotionSpring.DefaultSpatial)
        group.spring(DynamicAnimation.SCALE_Y, 1f, MotionSpring.DefaultSpatial)
        group.spring(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
        group.start(delayMs)
    }

    private fun startMove(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val group = SpringGroup(view) {
            view.resetAnimatedValues()
            dispatchMoveFinished(holder)
            running.remove(holder)
            dispatchFinishedWhenDone()
        }
        running[holder] = group
        dispatchMoveStarting(holder)
        group.spring(DynamicAnimation.TRANSLATION_X, 0f, MotionSpring.DefaultSpatial)
        group.spring(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
        group.start()
    }

    private fun startChange(info: ChangeInfo) {
        info.oldHolder?.let { holder ->
            val view = holder.itemView
            val group = SpringGroup(view) {
                view.resetAnimatedValues()
                dispatchChangeFinished(holder, true)
                running.remove(holder)
                dispatchFinishedWhenDone()
            }
            running[holder] = group
            dispatchChangeStarting(holder, true)
            group.spring(DynamicAnimation.ALPHA, 0f, MotionSpring.FastEffects)
            group.spring(DynamicAnimation.TRANSLATION_X, 0f, MotionSpring.DefaultSpatial)
            group.spring(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
            group.start()
        }
        info.newHolder?.let { holder ->
            val view = holder.itemView
            val group = SpringGroup(view) {
                view.resetAnimatedValues()
                dispatchChangeFinished(holder, false)
                running.remove(holder)
                dispatchFinishedWhenDone()
            }
            running[holder] = group
            dispatchChangeStarting(holder, false)
            group.spring(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
            group.spring(DynamicAnimation.TRANSLATION_X, 0f, MotionSpring.DefaultSpatial)
            group.spring(DynamicAnimation.TRANSLATION_Y, 0f, MotionSpring.DefaultSpatial)
            group.start()
        }
    }

    // endregion

    // region end / state

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        val view = item.itemView

        for (i in pendingMoves.indices.reversed()) {
            if (pendingMoves[i].holder === item) {
                view.resetAnimatedValues()
                dispatchMoveFinished(item)
                pendingMoves.removeAt(i)
            }
        }
        endPendingChange(item)
        if (pendingRemovals.remove(item)) {
            view.resetAnimatedValues()
            dispatchRemoveFinished(item)
        }
        if (pendingAdditions.remove(item)) {
            view.resetAnimatedValues()
            dispatchAddFinished(item)
        }
        // Cancelling a running group runs its finish action, which dispatches the right callback.
        running[item]?.cancel()
        dispatchFinishedWhenDone()
    }

    private fun endPendingChange(item: RecyclerView.ViewHolder) {
        for (i in pendingChanges.indices.reversed()) {
            val info = pendingChanges[i]
            val isOld = info.oldHolder === item
            val isNew = info.newHolder === item
            if (!isOld && !isNew) continue
            if (isOld) info.oldHolder = null else info.newHolder = null
            item.itemView.resetAnimatedValues()
            dispatchChangeFinished(item, isOld)
            if (info.oldHolder == null && info.newHolder == null) pendingChanges.removeAt(i)
        }
    }

    override fun endAnimations() {
        for (i in pendingMoves.indices.reversed()) {
            val holder = pendingMoves[i].holder
            holder.itemView.resetAnimatedValues()
            dispatchMoveFinished(holder)
            pendingMoves.removeAt(i)
        }
        for (i in pendingRemovals.indices.reversed()) {
            dispatchRemoveFinished(pendingRemovals[i])
            pendingRemovals.removeAt(i)
        }
        for (i in pendingAdditions.indices.reversed()) {
            val holder = pendingAdditions[i]
            holder.itemView.resetAnimatedValues()
            dispatchAddFinished(holder)
            pendingAdditions.removeAt(i)
        }
        for (i in pendingChanges.indices.reversed()) {
            val info = pendingChanges[i]
            info.oldHolder?.let { endPendingChange(it) }
            info.newHolder?.let { endPendingChange(it) }
        }
        pendingChanges.clear()

        if (running.isEmpty()) return
        // Each group removes itself from [running] while finishing, so iterate a snapshot.
        running.values.toList().forEach(SpringGroup::cancel)
        dispatchAnimationsFinished()
    }

    override fun isRunning(): Boolean =
        pendingRemovals.isNotEmpty() ||
            pendingAdditions.isNotEmpty() ||
            pendingMoves.isNotEmpty() ||
            pendingChanges.isNotEmpty() ||
            running.isNotEmpty()

    private fun dispatchFinishedWhenDone() {
        if (!isRunning) dispatchAnimationsFinished()
    }

    // endregion

    private class MoveInfo(val holder: RecyclerView.ViewHolder)

    private class ChangeInfo(
        var oldHolder: RecyclerView.ViewHolder?,
        var newHolder: RecyclerView.ViewHolder?,
    )

    /**
     * Springs several properties of one item view and runs [onFinished] exactly once: when all
     * of them settle, or when [cancel] is called (by `endAnimation`) at any point, including
     * during the start delay.
     */
    private class SpringGroup(private val view: View, private val onFinished: () -> Unit) {
        private val springs = ArrayList<SpringAnimation>()
        private var remaining = 0
        private var finished = false
        private var delayed: Runnable? = null

        fun spring(property: DynamicAnimation.ViewProperty, target: Float, spec: MotionSpring) {
            val animation = SpringAnimation(view, property).apply {
                spring = SpringForce(target).apply {
                    stiffness = spec.stiffness
                    dampingRatio = spec.dampingRatio
                }
                minimumVisibleChange = when (property) {
                    DynamicAnimation.ALPHA -> DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA
                    DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y -> DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
                    else -> DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
                }
                addEndListener { _, canceled, _, _ ->
                    if (!canceled && --remaining == 0) finish()
                }
            }
            remaining++
            springs += animation
        }

        fun start(delayMs: Long = 0L) {
            if (springs.isEmpty()) {
                finish()
                return
            }
            if (delayMs <= 0L) {
                springs.forEach(SpringAnimation::start)
            } else {
                delayed = Runnable {
                    delayed = null
                    if (!finished) springs.forEach(SpringAnimation::start)
                }.also { view.postDelayed(it, delayMs) }
            }
        }

        fun cancel() {
            if (finished) return
            delayed?.let(view::removeCallbacks)
            delayed = null
            // Cancel callbacks are ignored by the end listeners; finish() below is the single exit.
            springs.forEach { if (it.isRunning) it.cancel() }
            finish()
        }

        private fun finish() {
            if (finished) return
            finished = true
            onFinished()
        }
    }

    private companion object {
        const val NOMINAL_DURATION_MS = 350L
        const val STAGGER_MS = 24L
        const val MAX_STAGGERED_ITEMS = 8
        const val REMOVAL_LEAD_MS = 60L
        const val ADD_FROM_SCALE = 0.9f
        const val ADD_RISE_DP = 12f
        const val REMOVE_TO_SCALE = 0.9f

        fun View.resetAnimatedValues() {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }

        fun View.animationsEnabled(): Boolean = MotionPreferences.animationsEnabled(context)
    }
}
