package com.riyadm.apkrepacker.view.recyclerview.adapter.listener

import android.view.View

/**
 * @author Created by cz
 * @date 2020-03-17 20:20
 * @email bingo110@126.com
 *
 * Item click listener; [position] is relative to the wrapped adapter, [adapterPosition] to the
 * wrapper. See [com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter.setOnItemClickListener].
 */
fun interface OnItemClickListener {
    fun onItemClick(v: View, position: Int, adapterPosition: Int)
}
