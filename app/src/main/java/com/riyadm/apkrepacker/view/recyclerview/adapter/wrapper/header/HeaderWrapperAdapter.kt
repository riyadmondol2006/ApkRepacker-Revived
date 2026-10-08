package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.riyadm.apkrepacker.view.recyclerview.adapter.WrapperAdapter
import com.riyadm.apkrepacker.view.recyclerview.adapter.listener.OnItemClickListener
import com.riyadm.apkrepacker.view.recyclerview.adapter.listener.OnItemLongClickListener
import com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.WrapperAdapterDataObserver
import kotlin.math.max

/**
 * Created by cz
 * @date 2020-02-29 20:29
 * @email bingo110@126.com
 *
 * Wraps an adapter to add fixed header and footer views in front of / behind its items. Changes
 * of the wrapped adapter reach this adapter through [WrapperAdapterDataObserver].
 *
 * @see addHeaderView
 * @see addFooterView
 * @see setOnItemClickListener
 */
open class HeaderWrapperAdapter(adapter: RecyclerView.Adapter<*>?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), WrapperAdapter {

    private val adapterDataObserver: RecyclerView.AdapterDataObserver = WrapperAdapterDataObserver(this)
    private val headerViews = mutableListOf<FixedViewInfo>()
    private val footerViews = mutableListOf<FixedViewInfo>()

    /** Counts the fixed views ever added, so each gets a unique (negative) view type. */
    private var fixedViewCount = 0

    private var wrappedAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
    private var itemClickListener: OnItemClickListener? = null
    private var longClickListener: OnItemLongClickListener? = null

    init {
        adapter?.let { attachAdapter(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun attachAdapter(adapter: RecyclerView.Adapter<*>) {
        wrappedAdapter = adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        adapter.registerAdapterDataObserver(adapterDataObserver)
    }

    /** Replaces the wrapped adapter. */
    open fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        wrappedAdapter?.unregisterAdapterDataObserver(adapterDataObserver)
        attachAdapter(adapter)
        notifyDataSetChanged()
    }

    override fun getAdapter(): RecyclerView.Adapter<RecyclerView.ViewHolder>? = wrappedAdapter

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        wrappedAdapter?.onAttachedToRecyclerView(recyclerView)
        val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val original = manager.spanSizeLookup
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val span = if (isExtraPosition(position)) manager.spanCount else 1
                // A grid usually gives some positions the full row; keep what the original says.
                return max(span, original.getSpanSize(position))
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        wrappedAdapter?.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val params = holder.itemView.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams && isExtraPosition(holder.bindingAdapterPosition)) {
            params.isFullSpan = true
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        wrappedAdapter?.onViewRecycled(holder)
    }

    /**
     * Whether [position] holds a view that should fill a whole row in a grid. Header and footer
     * views do; override to extend that to your own rows.
     */
    protected open fun isExtraPosition(position: Int): Boolean = isHeaderPosition(position) || isFooterPosition(position)

    protected open fun isHeaderPosition(position: Int): Boolean = position < getHeaderViewCount()

    protected open fun isFooterPosition(position: Int): Boolean = getFooterStartPosition() <= position

    private fun getFooterStartPosition(): Int = itemCount - getFooterViewCount()

    open fun getHeaderViewCount(): Int = headerViews.size

    open fun getFooterViewCount(): Int = footerViews.size

    // region header / footer views

    open fun addHeaderView(view: View) {
        headerViews += FixedViewInfo(nextViewType(), view)
        notifyItemInserted(headerViews.lastIndex)
    }

    /** Adds a footer at [index] among the footers (e.g. to keep a refreshing footer last). */
    protected open fun addFooterView(view: View, index: Int) {
        require(index in 0..getFooterViewCount()) { "The footer index is out of bound!" }
        footerViews.add(index, FixedViewInfo(nextViewType(), view))
        notifyItemInserted(getFooterStartPosition() + index)
    }

    open fun addFooterView(view: View) = addFooterView(view, getFooterViewCount())

    private fun nextViewType() = TYPE_EXTRAS - fixedViewCount++

    open fun getHeaderView(index: Int): View? = headerViews.getOrNull(index)?.view

    open fun getFooterView(index: Int): View? = footerViews.getOrNull(index)?.view

    /**
     * Finds a view by id among the header and footer views. Subclasses may extend the search to
     * other fixed views.
     */
    open fun findView(@IdRes id: Int): View? =
        (headerViews.asSequence() + footerViews.asSequence()).firstNotNullOfOrNull { it.view?.findViewById<View>(id) }

    open fun indexOfHeaderView(view: View?): Int = headerViews.indexOfFirst { it.view === view }

    open fun removeHeaderView(view: View?) {
        view ?: return
        removeHeaderView(indexOfHeaderView(view))
    }

    open fun removeHeaderView(position: Int) {
        if (isHeaderPosition(position)) {
            headerViews.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    open fun clearHeaderViews() {
        val count = getHeaderViewCount()
        headerViews.clear()
        notifyItemRangeRemoved(0, count)
    }

    open fun removeFooterView(view: View?) {
        view ?: return
        removeFooterView(footerViews.indexOfFirst { it.view === view })
    }

    open fun removeFooterView(position: Int) {
        if (position in 0 until getFooterViewCount()) {
            val start = getFooterStartPosition()
            footerViews.removeAt(position)
            notifyItemRemoved(start + position)
        }
    }

    private fun extraViewFor(viewType: Int): View? =
        (headerViews.asSequence() + footerViews.asSequence()).firstOrNull { it.viewType == viewType }?.view

    // endregion

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType <= TYPE_EXTRAS) {
            object : RecyclerView.ViewHolder(requireNotNull(extraViewFor(viewType)) { "No fixed view for type $viewType" }) {}
        } else {
            checkNotNull(wrappedAdapter) { "No adapter set" }.onCreateViewHolder(parent, viewType)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (isExtraPosition(position)) return
        val adapter = wrappedAdapter ?: return
        adapter.onBindViewHolder(holder, position - getExtraViewCount(position))

        // Default click handling, unless the wrapped adapter installed its own.
        if (!holder.itemView.hasOnClickListeners()) {
            holder.itemView.setOnClickListener { v ->
                val adapterPosition = holder.bindingAdapterPosition
                val itemPosition = adapterPosition - getExtraViewCount(adapterPosition)
                // Subclasses get the first chance to consume the click.
                if (onItemClick(v, itemPosition, adapterPosition)) {
                    itemClickListener?.onItemClick(v, itemPosition, adapterPosition)
                }
            }
        }
        if (!holder.itemView.isLongClickable) {
            holder.itemView.setOnLongClickListener { v ->
                val adapterPosition = holder.bindingAdapterPosition
                val itemPosition = adapterPosition - getExtraViewCount(adapterPosition)
                longClickListener?.onLongItemClick(v, itemPosition, adapterPosition) ?: false
            }
        }
    }

    /** Called before the click listener; return false to consume the click. */
    protected open fun onItemClick(v: View, position: Int, adapterPosition: Int): Boolean = true

    open fun getAdapterItemCount(): Int = wrappedAdapter?.itemCount ?: 0

    override fun getItemCount(): Int = getHeaderViewCount() + getAdapterItemCount() + getFooterViewCount()

    override fun getItemViewType(position: Int): Int = when {
        isHeaderPosition(position) -> headerViews[position].viewType
        isFooterPosition(position) -> footerViews[position - getFooterStartPosition()].viewType
        else -> {
            val adapterPosition = position - getExtraViewCount(position)
            val adapter = wrappedAdapter
            if (adapter != null && adapterPosition < getAdapterItemCount()) adapter.getItemViewType(adapterPosition) else TYPE_EXTRAS
        }
    }

    override fun getItemId(position: Int): Long {
        val adapter = wrappedAdapter
        if (adapter != null && !isExtraPosition(position)) {
            val adapterPosition = position - getExtraViewCount(position)
            if (adapterPosition in 0 until adapter.itemCount) return adapter.getItemId(adapterPosition)
        }
        return RecyclerView.NO_ID
    }

    open fun setOnItemClickListener(listener: OnItemClickListener?) {
        itemClickListener = listener
    }

    open fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        longClickListener = listener
    }

    // region WrapperAdapter

    override fun getOffsetPosition(position: Int): Int = getHeaderViewCount() + position

    override fun getExtraViewCount(position: Int): Int = getHeaderViewCount()

    override fun onChanged() = notifyDataSetChanged()

    override fun itemRangeInsert(positionStart: Int, itemCount: Int) =
        notifyItemRangeInserted(getOffsetPosition(positionStart), itemCount)

    override fun itemRangeChanged(positionStart: Int, itemCount: Int) =
        notifyItemRangeChanged(getOffsetPosition(positionStart), itemCount)

    override fun itemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
        notifyItemRangeChanged(getOffsetPosition(positionStart), itemCount, payload)

    override fun itemRangeRemoved(positionStart: Int, itemCount: Int) =
        notifyItemRangeRemoved(getOffsetPosition(positionStart), itemCount)

    override fun itemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
        notifyItemMoved(getOffsetPosition(fromPosition), getOffsetPosition(toPosition))

    // endregion

    open class FixedViewInfo(
        @JvmField val viewType: Int,
        @JvmField val view: View?,
    )

    private companion object {
        /** Fixed views take view types -1, -2, ... */
        const val TYPE_EXTRAS = -1
    }
}
