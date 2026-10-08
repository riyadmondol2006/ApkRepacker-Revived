package com.riyadm.apkrepacker.view.recyclerview.adapter.support.swipe

import androidx.recyclerview.widget.ItemTouchHelper

/**
 * @author Created by cz
 * @date 2020-03-19 22:35
 * @email bingo110@126.com
 *
 * Controls which rows of a [SwipeAdapter] can be swiped and in which directions.
 */
interface SwipeCallback {

    fun isSwipeEnable(position: Int): Boolean

    fun isSwipeDirectionEnabled(direction: Int): Boolean

    /**
     * Directions rows can be swiped in.
     * @see ItemTouchHelper.DOWN
     * @see ItemTouchHelper.UP
     * @see ItemTouchHelper.LEFT
     * @see ItemTouchHelper.RIGHT
     */
    fun getMoveFlag(): Int
}
