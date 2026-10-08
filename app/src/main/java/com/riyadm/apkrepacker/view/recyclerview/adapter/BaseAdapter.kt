package com.riyadm.apkrepacker.view.recyclerview.adapter

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag.Moveable
import java.util.Collections

/**
 * @author Created by cz
 * @date 2020-03-17 21:55
 * @email bingo110@126.com
 *
 * Basic adapter that owns its data list and keeps RecyclerView notifications in sync with it.
 * Change data through these functions instead of mutating [getItemList] directly:
 * [add], [addAll], [remove], [set], [clear], [removeList], [swap].
 *
 * Each mutator takes a `notifyDataSetChanged` flag (despite the name it fires the precise
 * item notification, so item animations run); pass `false` to batch and notify yourself.
 */
abstract class BaseAdapter<VH : RecyclerView.ViewHolder, E>(itemList: List<E>?) :
    RecyclerView.Adapter<VH>(), Moveable {

    private val itemList: MutableList<E> = itemList?.toMutableList() ?: mutableListOf()

    @CallSuper
    override fun onBindViewHolder(holder: VH, position: Int) = Unit

    open fun getItem(position: Int): E = itemList[position]

    override fun getItemCount(): Int = itemList.size

    open fun getItemList(): MutableList<E> = itemList

    open fun indexOf(item: E): Int = itemList.indexOf(item)

    // region add

    open fun add(item: E) = add(item, itemCount, true)

    open fun add(item: E, index: Int) = add(item, index, true)

    open fun add(item: E, index: Int, notifyDataSetChanged: Boolean) {
        itemList.add(index, item)
        if (notifyDataSetChanged) notifyItemInserted(index)
    }

    open fun addAll(list: List<E>?) = addAll(list, true)

    open fun addAll(list: List<E>?, notifyDataSetChanged: Boolean) = addAll(itemCount, list, notifyDataSetChanged)

    open fun addAll(index: Int, list: List<E>?) = addAll(index, list, true)

    open fun addAll(index: Int, list: List<E>?, notifyDataSetChanged: Boolean) {
        if (list.isNullOrEmpty()) return
        itemList.addAll(index, list)
        if (notifyDataSetChanged) notifyItemRangeInserted(index, list.size)
    }

    // endregion

    // region remove

    open fun remove(start: Int, count: Int) = remove(start, count, true)

    open fun remove(start: Int, count: Int, notifyDataSetChanged: Boolean) {
        if (count <= 0) return
        itemList.subList(start, start + count).clear()
        if (notifyDataSetChanged) notifyItemRangeRemoved(start, count)
    }

    open fun remove(item: E) = remove(item, true)

    open fun remove(item: E, notifyDataSetChanged: Boolean) = remove(indexOf(item), notifyDataSetChanged)

    open fun remove(position: Int, notifyDataSetChanged: Boolean) {
        if (position == -1) return
        itemList.removeAt(position)
        if (notifyDataSetChanged) notifyItemRemoved(position)
    }

    open fun removeList(list: List<E>?) = removeList(list, true)

    open fun removeList(list: List<E>?, notifyDataSetChanged: Boolean) {
        if (list == null) return
        itemList.removeAll(list.toSet())
        if (notifyDataSetChanged) notifyDataSetChanged()
    }

    open fun clear() = clear(true)

    open fun clear(notifyDataSetChanged: Boolean) {
        val removed = itemCount
        itemList.clear()
        if (notifyDataSetChanged && removed > 0) notifyItemRangeRemoved(0, removed)
    }

    // endregion

    open fun set(index: Int, item: E) = set(index, item, true)

    open fun set(index: Int, item: E, notifyDataSetChanged: Boolean) {
        itemList[index] = item
        if (notifyDataSetChanged) notifyItemChanged(index)
    }

    /** Replaces all items with [list]. */
    open fun swap(list: List<E>?) = swap(list, true)

    open fun swap(list: List<E>?, notifyDataSetChanged: Boolean) {
        clear(true)
        addAll(list, notifyDataSetChanged)
    }

    override fun move(from: Int, to: Int) = Collections.swap(itemList, from, to)
}
