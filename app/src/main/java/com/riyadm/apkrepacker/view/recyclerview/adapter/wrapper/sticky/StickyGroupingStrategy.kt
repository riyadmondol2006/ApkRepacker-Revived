package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group.CompareGroupCondition
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group.GroupCondition

/**
 * @author Created by cz
 * @date 2020-03-23 21:13
 * @email bingo110@126.com
 *
 * Works out which adapter positions start a group (and so show a sticky header), from either a
 * [GroupCondition] or a [CompareGroupCondition]. The group start positions are kept sorted in
 * [groupPositions] and refreshed whenever the adapter's data changes.
 */
open class StickyGroupingStrategy<A, T>(private val adapter: A)
    where A : RecyclerView.Adapter<*>, A : StickyCallback<T> {

    private var groupPositions: IntArray? = null
    private var compareCondition: CompareGroupCondition<T>? = null
    private var condition: GroupCondition<T>? = null
    private var groupingListener: OnAdapterGroupingListener? = null
    private var dataChangeListener: OnAdapterDataChangeListener? = null

    init {
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = refresh()

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = refresh()

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = refresh()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = refresh()

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = refresh()

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = refresh()
        })
    }

    private fun refresh() {
        updateAdapterGroup()
        dataChangeListener?.onDataChanged()
    }

    open fun setCompareCondition(compareCondition: CompareGroupCondition<T>?) {
        this.compareCondition = compareCondition
        updateAdapterGroup()
    }

    open fun setCondition(condition: GroupCondition<T>?) {
        this.condition = condition
        updateAdapterGroup()
    }

    /** Recomputes the group starts; does nothing until a condition is set. */
    internal fun updateAdapterGroup() {
        val starts = when {
            compareCondition != null -> groupStartsByComparison(compareCondition!!)
            condition != null -> groupStartsByCondition(condition!!)
            else -> return
        }
        groupPositions = starts.map { groupingListener?.onAdapterGroup(it) ?: it }.toIntArray()
    }

    private fun groupStartsByComparison(compare: CompareGroupCondition<T>): List<Int> {
        val starts = mutableListOf<Int>()
        if (adapter.itemCount == 0) return starts
        var previous = adapter.getItem(0)
        for (i in 1 until adapter.itemCount) {
            val item = adapter.getItem(i)
            if (compare.group(previous, item)) starts += i
            previous = item
        }
        return starts
    }

    private fun groupStartsByCondition(condition: GroupCondition<T>): List<Int> =
        (0 until adapter.itemCount).filter { condition.group(adapter.getItem(it), it) }

    open fun isGroupPosition(position: Int): Boolean = groupPositions?.binarySearch(position)?.let { it >= 0 } ?: false

    open fun getGroupCount(): Int = groupPositions?.size ?: 0

    /**
     * Start position of the group [adapterPosition] belongs to. With group starts [1,5,10],
     * position 8 lies in the group starting at 5.
     */
    open fun getGroupStartPosition(adapterPosition: Int): Int = getGroupPosition(getGroupIndex(adapterPosition))

    /** Start position of the group with the given [groupIndex]. */
    open fun getGroupPosition(groupIndex: Int): Int = groupPositions?.getOrNull(groupIndex) ?: 0

    /**
     * Index of the group [position] belongs to: with group starts [1,5,10], position 3 and 4 map
     * to index 0, 5 to 9 map to index 1.
     */
    open fun getGroupIndex(position: Int): Int {
        val starts = groupPositions ?: return 0
        var low = 0
        var high = starts.size
        while (high - low > 1) {
            val middle = (low + high) / 2
            when {
                position > starts[middle] -> low = middle
                position < starts[middle] -> high = middle
                else -> return middle
            }
        }
        return low
    }

    /**
     * Number of group starts in front of [position]: with starts [1,5,7] and position 6 that is 2.
     * A position that is itself a group start returns that start's index.
     */
    open fun getGroupCount(position: Int): Int {
        val found = groupPositions?.binarySearch(position) ?: return 0
        return if (found >= 0) found else -found - 1
    }

    internal fun setOnAdapterGroupingListener(listener: OnAdapterGroupingListener) {
        groupingListener = listener
    }

    internal fun setOnAdapterDataChangeListener(listener: OnAdapterDataChangeListener?) {
        dataChangeListener = listener
    }

    internal fun interface OnAdapterGroupingListener {
        fun onAdapterGroup(position: Int): Int
    }

    internal fun interface OnAdapterDataChangeListener {
        fun onDataChanged()
    }

    companion object {
        @JvmStatic
        fun <A, T> of(adapter: A): StickyGroupingStrategy<A, T> where A : RecyclerView.Adapter<*>, A : StickyCallback<T> =
            StickyGroupingStrategy(adapter)
    }
}
