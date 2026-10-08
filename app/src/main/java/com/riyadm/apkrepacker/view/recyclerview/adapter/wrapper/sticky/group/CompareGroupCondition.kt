package com.riyadm.apkrepacker.view.recyclerview.adapter.wrapper.sticky.group

/**
 * Created by cz on 2017/5/20.
 *
 * Decides whether [t2] starts a new group when it follows [t1].
 */
fun interface CompareGroupCondition<T> {
    fun group(t1: T, t2: T): Boolean
}
