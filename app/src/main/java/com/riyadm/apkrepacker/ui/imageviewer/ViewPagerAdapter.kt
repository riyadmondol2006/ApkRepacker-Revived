package com.riyadm.apkrepacker.ui.imageviewer

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/** Base of the pager adapters here: an immutable list of [T] that diffs itself into the pager. */
abstract class ViewPagerAdapter<T : Any, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    protected var items: List<T> = emptyList()
        private set

    init {
        setHasStableIds(true)
    }

    fun replace(newItems: List<T>) {
        val old = items
        val new = newItems.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
        })
        items = new
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].hashCode().toLong()
}
