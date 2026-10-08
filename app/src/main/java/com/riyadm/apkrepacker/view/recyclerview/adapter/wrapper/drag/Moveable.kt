package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.drag

/**
 * @author Created by cz
 * @date 2020-03-17 19:56
 * @email bingo110@126.com
 *
 * Implemented by an adapter wrapped by a [DragWrapperAdapter]: the wrapper tells it to swap the
 * underlying data whenever an item is dragged to a new position.
 */
fun interface Moveable {
    fun move(from: Int, to: Int)
}
