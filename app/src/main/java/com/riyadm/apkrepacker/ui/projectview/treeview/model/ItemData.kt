package com.riyadm.apkrepacker.ui.projectview.treeview.model

/** One row of the project tree: a folder ([ITEM_TYPE_PARENT]) or a file ([ITEM_TYPE_CHILD]). */
class ItemData(
    var type: Int = ITEM_TYPE_PARENT,
    var text: String? = null,
    var path: String? = null,
    var uuid: String? = null,
    var treeDepth: Int = 0,
    var children: List<ItemData>? = null,
) : Comparable<ItemData> {

    var isExpand: Boolean = false

    override fun compareTo(other: ItemData): Int =
        text.orEmpty().compareTo(other.text.orEmpty(), ignoreCase = true)

    companion object {
        const val ITEM_TYPE_PARENT = 0
        const val ITEM_TYPE_CHILD = 1
    }
}
