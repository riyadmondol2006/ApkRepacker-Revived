package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter

/**
 * Created by cz
 * @date 2020-03-25 22:34
 * @email bingo110@126.com
 *
 * Sticky group headers as a wrapper: instead of every item layout carrying a (hidden) header
 * view like with [StickyAdapter], the headers are injected as their own view type between the
 * items. The wrapped adapter only has to implement [StickyCallback]. Since this extends
 * [HeaderWrapperAdapter], fixed header and footer views work as well.
 *
 * @see StickyCallback creates and binds the header views
 */
open class StickyWrapperAdapter<A>(private val stickyAdapter: A) : HeaderWrapperAdapter(stickyAdapter)
    where A : RecyclerView.Adapter<*>, A : StickyCallback<*> {

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        stickyAdapter.getGroupingStrategy().apply {
            setOnAdapterGroupingListener { position -> position + getExtraViewCount(position) }
            updateAdapterGroup()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType <= STICKY_HEADER_ITEM) {
            object : RecyclerView.ViewHolder(stickyAdapter.onCreateStickyView(parent, STICKY_HEADER_ITEM - viewType)) {}
        } else {
            super.onCreateViewHolder(parent, viewType)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) <= STICKY_HEADER_ITEM) {
            stickyAdapter.onBindStickyView(holder.itemView, stickyAdapter.getStickyViewType(position), position)
        } else {
            super.onBindViewHolder(holder, position)
        }
    }

    override fun getItemCount(): Int = super.getItemCount() + stickyAdapter.getGroupingStrategy().getGroupCount()

    override fun getItemViewType(position: Int): Int =
        if (stickyAdapter.getGroupingStrategy().isGroupPosition(position)) {
            // Header view types count down from STICKY_HEADER_ITEM.
            STICKY_HEADER_ITEM - stickyAdapter.getStickyViewType(position)
        } else {
            super.getItemViewType(position)
        }

    override fun getExtraViewCount(position: Int): Int =
        super.getExtraViewCount(position) + stickyAdapter.getGroupingStrategy().getGroupCount(position)

    override fun getOffsetPosition(position: Int): Int = position + getExtraViewCount(position)

    private companion object {
        const val STICKY_HEADER_ITEM = -1 shl 7
    }
}
