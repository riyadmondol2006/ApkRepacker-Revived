package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper

import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.WrapperAdapter

/**
 * Forwards every change of the wrapped adapter to its [WrapperAdapter], which translates the
 * positions into its own coordinates when it needs to.
 *
 * @see com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter
 * @see com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.dynamic.DynamicWrapperAdapter
 */
open class WrapperAdapterDataObserver<E : WrapperAdapter>(private val wrapperAdapter: E) :
    RecyclerView.AdapterDataObserver() {

    override fun onChanged() = wrapperAdapter.onChanged()

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
        wrapperAdapter.itemRangeInsert(positionStart, itemCount)

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) =
        wrapperAdapter.itemRangeChanged(positionStart, itemCount)

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
        wrapperAdapter.itemRangeChanged(positionStart, itemCount, payload)

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
        wrapperAdapter.itemRangeRemoved(positionStart, itemCount)

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
        wrapperAdapter.itemRangeMoved(fromPosition, toPosition, itemCount)
}
