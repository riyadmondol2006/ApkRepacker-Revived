package com.riyadm.apkrepacker.ui.projectview.treeview.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.databinding.ListTreeItemFileBinding
import com.riyadm.apkrepacker.databinding.ListTreeItemFolderBinding
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemDataClickListener
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemFileClickListener
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.OnScrollToListener
import com.riyadm.apkrepacker.ui.projectview.treeview.model.ItemData
import com.riyadm.apkrepacker.ui.projectview.treeview.viewholder.BaseViewHolder
import com.riyadm.apkrepacker.ui.projectview.treeview.viewholder.ChildViewHolder
import com.riyadm.apkrepacker.ui.projectview.treeview.viewholder.ParentViewHolder
import java.io.File
import java.util.UUID

/**
 * Flat adapter of an expandable file tree: expanding a folder inserts its children right below
 * it, collapsing removes the whole visible subtree.
 *
 * Original tree adapter by Zheng Haibo (http://www.mobctrl.net).
 */
class RecyclerAdapter(context: Context) : RecyclerView.Adapter<BaseViewHolder>() {

    private val data = ArrayList<ItemData>()
    private var onScrollToListener: OnScrollToListener? = null
    private var itemFileClickListener: ItemFileClickListener? = null
    private val showHidden: Boolean = PreferenceHelper.getInstance(context).isShowHiddenFiles

    fun setOnScrollToListener(listener: OnScrollToListener?) {
        onScrollToListener = listener
    }

    fun setItemFileClickListener(listener: ItemFileClickListener?) {
        itemFileClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ItemData.ITEM_TYPE_PARENT -> ParentViewHolder(ListTreeItemFolderBinding.inflate(inflater, parent, false))
            else -> ChildViewHolder(ListTreeItemFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val item = data[position]
        when (holder) {
            is ParentViewHolder -> {
                holder.setItemFileClickListener(itemFileClickListener)
                holder.bindView(item, position, expandListener)
            }
            is ChildViewHolder -> {
                holder.setItemFileClickListener(itemFileClickListener)
                holder.bindView(item, position)
            }
        }
    }

    override fun getItemCount() = data.size

    override fun getItemViewType(position: Int) = data[position].type

    private val expandListener = object : ItemDataClickListener {
        override fun onExpandChildren(itemData: ItemData) {
            val position = positionOf(itemData.uuid)
            val children = listChildren(itemData.path, itemData.treeDepth) ?: return
            addAll(children, position + 1)
            itemData.children = children
        }

        override fun onHideChildren(itemData: ItemData) {
            val position = positionOf(itemData.uuid)
            if (itemData.children == null) return
            removeRange(position + 1, visibleSubtreeSize(itemData) - 1)
            itemData.children = null
        }
    }

    /** Re-reads an expanded folder after something was added to it. No-op for collapsed folders. */
    fun reloadDirectory(path: String) {
        val item = data.firstOrNull { it.type == ItemData.ITEM_TYPE_PARENT && it.path == path && it.isExpand } ?: return
        expandListener.onHideChildren(item)
        expandListener.onExpandChildren(item)
    }

    private fun visibleSubtreeSize(item: ItemData): Int =
        1 + item.children.orEmpty().sumOf(::visibleSubtreeSize)

    /** Lists a folder: sub-folders first, then files, each sorted by name. */
    fun getChildrenByPath(path: String?, treeDepth: Int): List<ItemData> = listChildren(path, treeDepth).orEmpty()

    private fun listChildren(path: String?, treeDepth: Int): List<ItemData>? {
        if (path == null) return null
        return runCatching {
            val depth = treeDepth + 1
            val (dirs, files) = File(path).listFiles().orEmpty()
                .filter { showHidden || !it.isHidden }
                .partition { it.isDirectory }
            fun File.toItem(type: Int) = ItemData(type, name, absolutePath, UUID.randomUUID().toString(), depth, null)
            dirs.map { it.toItem(ItemData.ITEM_TYPE_PARENT) }.sorted() + files.map { it.toItem(ItemData.ITEM_TYPE_CHILD) }.sorted()
        }.getOrNull()
    }

    fun clearData() {
        val count = data.size
        data.clear()
        notifyItemRangeRemoved(0, count)
    }

    private fun removeRange(position: Int, count: Int) {
        if (position < 0 || count <= 0) return
        data.subList(position, position + count).clear()
        notifyItemRangeRemoved(position, count)
    }

    private fun positionOf(uuid: String?) = data.indexOfFirst { it.uuid.equals(uuid, ignoreCase = true) }

    fun add(item: ItemData, position: Int) {
        data.add(position, item)
        notifyItemInserted(position)
    }

    fun addAll(list: List<ItemData>, position: Int) {
        data.addAll(position, list)
        notifyItemRangeInserted(position, list.size)
    }

    fun delete(position: Int) {
        if (position !in data.indices) return
        val item = data[position]
        val count = if (item.type == ItemData.ITEM_TYPE_PARENT && item.isExpand) visibleSubtreeSize(item) else 1
        removeRange(position, count)
    }
}
