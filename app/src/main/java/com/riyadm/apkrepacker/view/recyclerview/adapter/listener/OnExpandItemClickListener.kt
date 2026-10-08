package com.riyadm.apkrepacker.view.recyclerview.adapter.listener

import android.view.View

/**
 * @author Created by cz
 * @date 2020-03-28 11:21
 * @email bingo110@126.com
 *
 * Child click listener of [com.riyadm.apkrepacker.view.recyclerview.adapter.support.expand.ExpandAdapter].
 */
fun interface OnExpandItemClickListener {
    fun onItemClick(v: View?, groupPosition: Int, childPosition: Int)
}
