package com.riyadm.apkrepacker.view.recyclerview.adapter.listener

import android.view.View

/**
 * @author Created by cz
 * @date 2020-03-17 20:21
 * @email bingo110@126.com
 *
 * Item long click listener; return true to consume the event.
 * See [com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.header.HeaderWrapperAdapter.setOnItemLongClickListener].
 */
fun interface OnItemLongClickListener {
    fun onLongItemClick(v: View?, position: Int, adapterPosition: Int): Boolean
}
