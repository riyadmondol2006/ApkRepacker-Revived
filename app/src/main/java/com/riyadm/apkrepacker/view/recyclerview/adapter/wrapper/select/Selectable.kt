package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.select

import androidx.recyclerview.widget.RecyclerView

/**
 * @author Created by cz
 * @date 2020-03-17 15:32
 * @email bingo110@126.com
 *
 * Implemented by an adapter wrapped by a [SelectWrapperAdapter]: called for every bound row to
 * show whether it is selected.
 */
fun interface Selectable<VH : RecyclerView.ViewHolder> {
    fun onSelectItem(holder: VH, position: Int, selected: Boolean)
}
