package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky

import android.view.View

/**
 * @author Created by cz
 * @date 2020-03-24 18:21
 * @email bingo110@126.com
 */
fun interface StickyScrollable<V : View> {
    fun onScrolled(recyclerView: V, dx: Int, dy: Int)
}
