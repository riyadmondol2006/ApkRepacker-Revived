package com.riyadm.apkrepacker.view.recyclerview.adapter.support.tree

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.view.recyclerview.adapter.WrapperAdapter
import com.riyadm.apkrepacker.view.recyclerview.adapter.listener.OnTreeNodeClickListener

/**
 * @author Created by cz
 * @date 2020-03-17 20:19
 * @email bingo110@126.com
 *
 * Shows an arbitrarily deep tree as a flat list: expanding a node inserts its descendants below
 * it (animated), collapsing removes them. An alternative to
 * [com.riyadm.apkrepacker.view.recyclerview.adapter.support.expand.ExpandAdapter]. It can be
 * wrapped by a [com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter]
 * to get header and footer views.
 *
 * Main functions: [expandAll], [collapseAll], [add], [addFirst], [setLoadCallback],
 * [setOnTreeNodeClickListener].
 *
 * Big trees (a file system, say) can be loaded lazily with a [TreeLoadCallback]:
 *
 * ```
 * adapter.setLoadCallback { node ->
 *     (node.item as File).listFiles().orEmpty().map { TreeNode(it) }
 * }
 * ```
 *
 * Nodes can only be added at the start or the end of the tree for now.
 */
abstract class TreeAdapter<E>(private val rootNode: TreeNode<E>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** The nodes currently shown, in display order. */
    private val nodeList = mutableListOf<TreeNode<E>>()

    private var wrapperAdapter: WrapperAdapter? = null
    private var loadCallback: TreeLoadCallback<E>? = null
    private var listener: OnTreeNodeClickListener<E>? = null

    init {
        rootNode.isExpand = true
        nodeList += visibleNodes(rootNode)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // When wrapped (header / footer views) positions have to be corrected by the wrapper.
        wrapperAdapter = recyclerView.adapter as? WrapperAdapter
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        wrapperAdapter = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    open fun expandAll() {
        expandNode(rootNode, false)
        notifyDataSetChanged()
    }

    open fun expandNode(parentNode: TreeNode<E>) = expandNode(parentNode, true)

    private fun expandNode(parentNode: TreeNode<E>, notify: Boolean) {
        if (!parentNode.isExpand) {
            parentNode.isExpand = true
            if (parentNode.children.isNotEmpty()) {
                nodeList.addAll(nodeList.indexOf(parentNode) + 1, parentNode.children)
            }
        }
        parentNode.children.forEach { expandNode(it, false) }
        if (notify) notifyDataSetChanged()
    }

    open fun collapseAll() {
        collapseNode(rootNode, false)
        notifyDataSetChanged()
    }

    open fun collapseNode(parentNode: TreeNode<E>) = collapseNode(parentNode, true)

    open fun collapseNode(parentNode: TreeNode<E>, notifyDataSetChanged: Boolean) {
        if (parentNode.isExpand && parentNode !== rootNode) {
            parentNode.isExpand = false
            nodeList.removeAll(parentNode.children.toSet())
        }
        parentNode.children.forEach { collapseNode(it, false) }
        if (notifyDataSetChanged) notifyDataSetChanged()
    }

    /**
     * The nodes below [root] that are visible: [root]'s children and, recursively, those of every
     * expanded node (depth-first, root excluded).
     */
    private fun visibleNodes(root: TreeNode<E>): MutableList<TreeNode<E>> {
        val result = mutableListOf<TreeNode<E>>()
        val pending = ArrayDeque<TreeNode<E>>().apply { add(root) }
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node === this.rootNode || node.isExpand) {
                node.children.asReversed().forEach(pending::addLast)
            }
            if (node !== this.rootNode) result += node
        }
        return result
    }

    abstract fun onBindViewHolder(holder: RecyclerView.ViewHolder, node: TreeNode<E>, item: E, viewType: Int, position: Int)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val node = getNode(position)
        onBindViewHolder(holder, node, node.item, getItemViewType(position), position)
        holder.itemView.setOnClickListener { v -> onNodeClicked(holder, v) }
    }

    private fun onNodeClicked(holder: RecyclerView.ViewHolder, v: View) {
        var adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) return
        // Subtract header views a wrapper put in front of us.
        wrapperAdapter?.let { adapterPosition -= it.getExtraViewCount(adapterPosition) }
        val node = getNode(adapterPosition)

        // Lazily load the children on the first expansion.
        val loader = loadCallback
        if (loader != null && !node.isExpand && !node.isLoad) {
            val loaded = loader.onLoad(node)
            loaded.forEach { it.parent = node }
            node.children.addAll(loaded)
            node.isLoad = true
        }

        val wasExpanded = node.isExpand
        // Count the descendants as if the node were open, then settle on the toggled state.
        node.isExpand = true
        val descendants = visibleNodes(node)
        node.isExpand = !wasExpanded

        if (descendants.isEmpty()) {
            listener?.onNodeItemClick(node, node.item, v, adapterPosition)
            return
        }
        onNodeExpand(node, node.item, holder, !wasExpanded)
        if (wasExpanded) {
            nodeList.removeAll(descendants.toSet())
            notifyItemRangeRemoved(adapterPosition + 1, descendants.size)
        } else {
            nodeList.addAll(adapterPosition + 1, descendants)
            notifyItemRangeInserted(adapterPosition + 1, descendants.size)
        }
    }

    open fun getNode(position: Int): TreeNode<E> = nodeList[position]

    open fun getItem(position: Int): E? = nodeList.getOrNull(position)?.item

    override fun getItemCount(): Int = nodeList.size

    /** Called when [node] is expanded or collapsed; override to e.g. rotate an arrow. */
    protected open fun onNodeExpand(node: TreeNode<E>, item: E, holder: RecyclerView.ViewHolder, expand: Boolean) = Unit

    /** Removes the node shown at [position]. */
    open fun remove(position: Int) = removeNode(nodeList.getOrNull(position))

    /** Removes [node] and, if it is expanded, its descendants. */
    open fun removeNode(node: TreeNode<E>?) {
        node ?: return
        if (node.isExpand) node.children.toList().asReversed().forEach(::removeNode)
        val position = nodeList.indexOf(node)
        if (position >= 0) {
            val removed = nodeList.removeAt(position)
            notifyItemRemoved(position)
            removed.parent?.children?.remove(removed)
        }
    }

    open fun add(e: E) = add(TreeNode(rootNode, e))

    /** Appends [node] (with its visible descendants) to the end of the tree. */
    open fun add(node: TreeNode<E>) {
        node.parent = rootNode
        rootNode.children.add(node)
        val inserted = listOf(node) + visibleNodes(node)
        val start = itemCount
        nodeList.addAll(inserted)
        notifyItemRangeInserted(start, inserted.size)
    }

    open fun addFirst(e: E) = addFirst(TreeNode(rootNode, e))

    /** Inserts [node] (with its visible descendants) at the start of the tree. */
    open fun addFirst(node: TreeNode<E>) {
        node.parent = rootNode
        rootNode.children.add(0, node)
        val inserted = listOf(node) + visibleNodes(node)
        nodeList.addAll(0, inserted)
        notifyItemRangeInserted(0, inserted.size)
    }

    /** Sets the loader that supplies a node's children on first expansion. */
    open fun setLoadCallback(callback: TreeLoadCallback<E>?) {
        loadCallback = callback
    }

    open fun setOnTreeNodeClickListener(listener: OnTreeNodeClickListener<E>?) {
        this.listener = listener
    }
}
