package com.riyadm.apkrepacker.ui.projectview.treeview.interfaces

import com.riyadm.apkrepacker.ui.projectview.treeview.model.ItemData

/** Expand / collapse requests from a folder row. */
interface ItemDataClickListener {

    fun onExpandChildren(itemData: ItemData)

    fun onHideChildren(itemData: ItemData)
}
