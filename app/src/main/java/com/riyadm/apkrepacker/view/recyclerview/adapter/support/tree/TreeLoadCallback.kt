package com.riyadm.apkrepacker.view.recyclerview.adapter.support.tree

/**
 * @author Created by cz
 * @date 2020-03-17 20:54
 * @email bingo110@126.com
 *
 * Loads the children of a [TreeNode] the first time it is expanded, for trees too big to build up
 * front (like a file system).
 * @see TreeAdapter.setLoadCallback
 */
fun interface TreeLoadCallback<E> {
    /** Returns the child nodes of [node]. */
    fun onLoad(node: TreeNode<E>): List<TreeNode<E>>
}
