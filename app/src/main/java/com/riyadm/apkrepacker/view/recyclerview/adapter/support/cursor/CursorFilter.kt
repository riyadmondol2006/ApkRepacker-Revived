package com.riyadm.apkrepacker.view.recyclerview.adapter.support.cursor

import android.database.Cursor
import android.widget.Filter

/**
 * @author Created by cz
 * @date 2020-03-17 22:37
 * @email bingo110@126.com
 *
 * Runs the client's query off the main thread and swaps the resulting cursor in.
 */
open class CursorFilter(private val filterClient: CursorFilterClient) : Filter() {

    override fun convertResultToString(resultValue: Any?): CharSequence? =
        filterClient.convertToString(resultValue as Cursor?)

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val cursor = filterClient.runQueryOnBackgroundThread(constraint)
        return FilterResults().apply {
            count = cursor?.count ?: 0
            values = cursor
        }
    }

    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
        val cursor = results?.values as Cursor?
        if (cursor != null && cursor !== filterClient.getCursor()) filterClient.changeCursor(cursor)
    }
}
