package com.riyadm.apkrepacker.ui.projectview.treeview.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    /** Horizontal padding that indents a row by its depth in the tree. */
    protected fun indentFor(depth: Int): Int {
        val res = itemView.resources
        return res.getDimensionPixelSize(com.riyadm.apkrepacker.R.dimen.space_2) +
            depth * res.getDimensionPixelSize(com.riyadm.apkrepacker.R.dimen.space_3)
    }
}
