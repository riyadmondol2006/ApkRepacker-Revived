package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group

/**
 * Created by cz on 2017/5/20.
 *
 * Decides whether the item [t] at [position] starts a new group (and so gets a sticky header).
 */
fun interface GroupCondition<T> {
    fun group(t: T, position: Int): Boolean
}
