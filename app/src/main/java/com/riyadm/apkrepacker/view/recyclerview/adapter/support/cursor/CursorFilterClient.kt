package com.riyadm.apkrepacker.view.recyclerview.adapter.support.cursor

import android.database.Cursor

/**
 * @author Created by cz
 * @date 2020-03-17 22:39
 * @email bingo110@126.com
 *
 * What a [CursorFilter] needs from the adapter it filters for.
 */
interface CursorFilterClient {

    fun convertToString(cursor: Cursor?): CharSequence?

    fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor?

    fun getCursor(): Cursor?

    fun changeCursor(cursor: Cursor?)
}
