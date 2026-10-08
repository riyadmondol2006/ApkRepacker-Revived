package com.riyadm.apkrepacker.view.recyclerview.adapter.listener

import android.view.View
import com.riyadm.apkrepacker.view.recyclerview.adapter.support.tree.TreeNode

/**
 * @author Created by cz
 * @date 2020-03-17 22:04
 * @email bingo110@126.com
 *
 * Click listener for leaf nodes of a [com.riyadm.apkrepacker.view.recyclerview.adapter.support.tree.TreeAdapter].
 * Parent nodes are opened/closed by the adapter itself; override
 * `TreeAdapter.onNodeExpand` to react to that.
 */
fun interface OnTreeNodeClickListener<E> {
    fun onNodeItemClick(node: TreeNode<E>?, item: E?, v: View?, position: Int)
}
