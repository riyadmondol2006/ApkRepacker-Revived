package com.riyadm.apkrepacker.view.recyclerview.adapter.support.expand

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.WrapperAdapter
import com.riyadm.apkrepacker.view.recyclerview.adapter.listener.OnExpandItemClickListener

/**
 * Created by cz
 * @date 2020-03-18 20:58
 * @email bingo110@126.com
 *
 * Two-level expandable list (groups with children), like [android.widget.ExpandableListAdapter].
 * Expanding or collapsing a group inserts / removes its children with item animations. It can be
 * wrapped by a [com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter]
 * to get header and footer views:
 *
 * ```
 * val wrapper = HeaderWrapperAdapter(adapter)
 * wrapper.addHeaderView(header)
 * recyclerView.adapter = wrapper
 * expandAllButton.setOnClickListener { adapter.expandAll() }
 * ```
 *
 * Main functions: [expandAll], [collapseAll], [addGroup], [removeGroup], [setOnExpandItemClickListener].
 */
abstract class ExpandAdapter<K, E> private constructor(items: List<Entry<K, List<E>>>?, expand: Boolean) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var wrapperAdapter: WrapperAdapter? = null

    /** The groups, expanded or not. */
    private val expandList = mutableListOf<Entry<K, List<E>>>()

    /** For each group, the list position of its header. */
    private var expandStepArray = IntArray(0)
    private var listener: OnExpandItemClickListener? = null

    init {
        if (items != null) {
            expandList += items
            expandList.forEach { it.isExpand = expand }
            updateGroupItemInfo()
        }
    }

    /** Creates an adapter from an ordered map of group to children. */
    @JvmOverloads
    constructor(map: LinkedHashMap<K, List<E>>, expand: Boolean = false) : this(convertLinkedHashMap(map), expand)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // When wrapped (header / footer views) positions have to be corrected by the wrapper.
        wrapperAdapter = recyclerView.adapter as? WrapperAdapter
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        wrapperAdapter = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /** Recomputes where each group header sits in the flat list. */
    private fun updateGroupItemInfo() {
        expandStepArray = IntArray(expandList.size)
        var count = 0
        expandList.forEachIndexed { i, entry ->
            expandStepArray[i] = count
            count += 1 + entry.visibleChildCount()
        }
    }

    private fun Entry<K, List<E>>.visibleChildCount(): Int = if (isExpand) children?.size ?: 0 else 0

    open fun getGroupCount(): Int = expandList.size

    open fun getChildrenCount(position: Int): Int = expandList[position].children.orEmpty().size

    open fun getGroup(position: Int): K = expandList[position].k

    open fun getGroupItems(groupPosition: Int): List<E>? = expandList[groupPosition].children

    open fun getChild(groupPosition: Int, childPosition: Int): E = getGroupItems(groupPosition)!![childPosition]

    open fun getGroupExpand(position: Int): Boolean = expandList[position].isExpand

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = checkNotNull(
        when (viewType) {
            HEADER_ITEM -> createGroupHolder(parent)
            else -> createChildHolder(parent)
        },
    ) { "createGroupHolder / createChildHolder returned null" }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val groupPosition = getGroupPosition(position)
        when (getItemViewType(position)) {
            HEADER_ITEM -> {
                onBindGroupHolder(holder, groupPosition)
                holder.itemView.setOnClickListener {
                    // Subtract header views a wrapper put in front of us.
                    var adapterPosition = holder.bindingAdapterPosition
                    if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    wrapperAdapter?.let { adapterPosition -= it.getExtraViewCount(adapterPosition) }
                    val group = getGroupPosition(adapterPosition)
                    val entry = expandList[group]
                    entry.isExpand = !entry.isExpand
                    onGroupExpand(holder, entry.isExpand, group)
                    expandGroup(adapterPosition, group, entry.isExpand)
                }
            }
            else -> {
                val childPosition = getChildPosition(position)
                onBindChildHolder(holder, groupPosition, childPosition)
                holder.itemView.setOnClickListener { v -> listener?.onItemClick(v, groupPosition, childPosition) }
            }
        }
    }

    /** Called after a group header was tapped and is now [expand]ed or collapsed. */
    protected open fun onGroupExpand(holder: RecyclerView.ViewHolder, expand: Boolean, groupPosition: Int) = Unit

    private fun expandGroup(position: Int, groupPosition: Int, expand: Boolean) {
        val childCount = getGroupItems(groupPosition)?.size ?: 0
        updateGroupItemInfo()
        if (expand) {
            notifyItemRangeInserted(position + 1, childCount)
        } else {
            notifyItemRangeRemoved(position + 1, childCount)
        }
    }

    abstract fun createGroupHolder(parent: ViewGroup): RecyclerView.ViewHolder?

    abstract fun createChildHolder(parent: ViewGroup): RecyclerView.ViewHolder?

    abstract fun onBindGroupHolder(holder: RecyclerView.ViewHolder, groupPosition: Int)

    abstract fun onBindChildHolder(holder: RecyclerView.ViewHolder, groupPosition: Int, position: Int): RecyclerView.ViewHolder?

    override fun getItemCount(): Int = expandList.sumOf { 1 + it.visibleChildCount() }

    override fun getItemViewType(position: Int): Int =
        if (position - expandStepArray[getSelectPosition(expandStepArray, position)] > 0) CHILD_ITEM else HEADER_ITEM

    private fun getGroupPosition(position: Int): Int = getSelectPosition(expandStepArray, position)

    private fun getChildPosition(position: Int): Int =
        position - expandStepArray[getSelectPosition(expandStepArray, position)] - 1

    open fun addGroup(item: K, items: List<E>?) = addGroup(item, items, getGroupCount(), false)

    open fun addGroup(item: K, children: List<E>?, index: Int, expand: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val entry = Entry(item, children).also { it.isExpand = expand } as Entry<K, List<E>>
        expandList.add(index, entry)
        updateGroupItemInfo()
        notifyItemRangeInserted(expandStepArray[index], 1 + entry.visibleChildCount())
    }

    open fun removeGroup(position: Int) {
        if (position !in expandList.indices) return
        val index = expandStepArray[position]
        val removed = expandList.removeAt(position)
        updateGroupItemInfo()
        notifyItemRangeRemoved(index, 1 + removed.visibleChildCount())
    }

    /** Removes the child at [childPosition] of the group at [position]. */
    open fun removeGroup(position: Int, childPosition: Int) {
        if (expandList.isEmpty()) return
        val groupPosition = expandStepArray[position]
        val entry = expandList[position]
        val children = entry.children as? MutableList<*>
        if (!children.isNullOrEmpty()) {
            children.removeAt(childPosition)
            if (entry.isExpand) {
                updateGroupItemInfo()
                notifyItemRemoved(groupPosition + 1 + childPosition)
            }
        }
        // The group header may show a count.
        notifyItemChanged(groupPosition)
    }

    open fun swap(items: LinkedHashMap<K, List<E>>) = swap(convertLinkedHashMap(items), false)

    open fun swap(items: List<Entry<K, List<E>>>, expand: Boolean) {
        if (items.isEmpty()) return
        expandList.clear()
        expandList += items
        expandList.forEach { it.isExpand = expand }
        updateGroupItemInfo()
        notifyDataSetChanged()
    }

    open fun expandAll() = setAllExpanded(true)

    open fun collapseAll() = setAllExpanded(false)

    private fun setAllExpanded(expand: Boolean) {
        expandList.forEach { it.isExpand = expand }
        updateGroupItemInfo()
        notifyDataSetChanged()
    }

    open fun setOnExpandItemClickListener(listener: OnExpandItemClickListener?) {
        this.listener = listener
    }

    /** A group: its header object [k], its [children] and whether it is currently open. */
    class Entry<K, E>(val k: K, val children: E?) {
        var isExpand = false
    }

    companion object {
        private const val HEADER_ITEM = 0
        private const val CHILD_ITEM = 1

        private fun <K, E> convertLinkedHashMap(items: LinkedHashMap<K, List<E>>): List<Entry<K, List<E>>> =
            items.map { (key, value) -> Entry(key, value) }

        /** Binary search: index of the last entry of [positions] that is <= [firstVisiblePosition]. */
        @JvmStatic
        fun getSelectPosition(positions: IntArray, firstVisiblePosition: Int): Int {
            var start = 0
            var end = positions.size
            while (end - start > 1) {
                val middle = (start + end) shr 1
                val middleValue = positions[middle]
                when {
                    firstVisiblePosition > middleValue -> start = middle
                    firstVisiblePosition < middleValue -> end = middle
                    else -> return middle
                }
            }
            return start
        }
    }
}
