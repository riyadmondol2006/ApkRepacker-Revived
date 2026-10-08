package com.riyadm.apkrepacker.view.recyclerview.adapter.support.tree

import java.util.Objects

/**
 * @author Created by cz
 * @date 2020-03-17 20:20
 * @email bingo110@126.com
 *
 * A node of the tree shown by a [TreeAdapter].
 */
open class TreeNode<E>(parent: TreeNode<E>?, item: E) {

    /** Sub-nodes. */
    @JvmField
    var children: MutableList<TreeNode<E>> = ArrayList(1)

    @JvmField
    var parent: TreeNode<E>? = parent

    /** The data. */
    @JvmField
    var item: E = item

    /** Depth in the tree; the root is 0. */
    @JvmField
    var depth: Int = if (parent != null) parent.depth + 1 else 0

    @JvmField
    var isExpand = false

    /** For lazily loaded trees: whether the children were already loaded. */
    @JvmField
    var isLoad = false

    constructor(item: E) : this(null, item)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as TreeNode<*>
        return depth == other.depth && parent == other.parent && item == other.item
    }

    // Must agree with equals; children and parent are left out (they refer back to this node).
    override fun hashCode(): Int = Objects.hash(item, depth)
}
