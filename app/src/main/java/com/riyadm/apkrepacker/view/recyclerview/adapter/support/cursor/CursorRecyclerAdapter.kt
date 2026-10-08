package com.riyadm.apkrepacker.view.recyclerview.adapter.support.cursor

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.FilterQueryProvider
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by cz
 * @date 2020-03-19 21:57
 * @email bingo110@126.com
 *
 * A RecyclerView port of the platform `CursorAdapter`: rows are bound straight from a [Cursor]
 * (which needs an `_id` column), and the cursor can be swapped, filtered ([getFilter]) and
 * observed. Differences from the platform class: [swapCursor] notifies with `notifyDataSetChanged`
 * and the cursor is closed by [changeCursor] only.
 */
abstract class CursorRecyclerAdapter<H : RecyclerView.ViewHolder> @JvmOverloads constructor(
    context: Context?,
    cursor: Cursor?,
    flags: Int = FLAG_REGISTER_CONTENT_OBSERVER,
) : RecyclerView.Adapter<H>(), Filterable, CursorFilterClient {

    protected var dataValid = cursor != null
    protected var autoRequery = (flags and FLAG_AUTO_REQUERY) == FLAG_AUTO_REQUERY
    protected var currentCursor: Cursor? = cursor
    protected val context: Context? = context
    protected var rowIdColumn = if (cursor != null) cursor.getColumnIndexOrThrow("_id") else -1
    protected val inflater: LayoutInflater = LayoutInflater.from(context)
    private var filterQueryProvider: FilterQueryProvider? = null
    private var cursorFilter: CursorFilter? = null

    // Auto-requery implies observing content changes.
    private val changeObserver: ChangeObserver? =
        if (autoRequery || (flags and FLAG_REGISTER_CONTENT_OBSERVER) == FLAG_REGISTER_CONTENT_OBSERVER) ChangeObserver() else null
    private val dataSetObserver: DataSetObserver? = changeObserver?.let { CursorDataSetObserver() }

    init {
        cursor?.let(::registerObservers)
    }

    private fun registerObservers(cursor: Cursor) {
        changeObserver?.let(cursor::registerContentObserver)
        dataSetObserver?.let(cursor::registerDataSetObserver)
    }

    private fun unregisterObservers(cursor: Cursor) {
        changeObserver?.let(cursor::unregisterContentObserver)
        dataSetObserver?.let(cursor::unregisterDataSetObserver)
    }

    protected open fun inflateView(parent: ViewGroup?, layout: Int): View = inflater.inflate(layout, parent, false)

    override fun getCursor(): Cursor? = currentCursor

    /** The cursor moved to [position]. */
    open fun getCursor(position: Int): Cursor? = currentCursor?.also { it.moveToPosition(position) }

    /** The cursor moved to [position], or null when the data isn't valid. */
    open fun getItem(position: Int): Any? = currentCursor?.takeIf { dataValid }?.also { it.moveToPosition(position) }

    override fun getItemId(position: Int): Long {
        val cursor = currentCursor?.takeIf { dataValid } ?: return 0
        return if (cursor.moveToPosition(position)) cursor.getLong(rowIdColumn) else 0
    }

    override fun onBindViewHolder(holder: H, position: Int) {
        check(dataValid) { "this should only be called when the cursor is valid" }
        val cursor = checkNotNull(currentCursor) { "this should only be called when the cursor is valid" }
        check(cursor.moveToPosition(position)) { "couldn't move cursor to position $position" }
        onBindViewHolder(holder, cursor, position)
    }

    abstract fun onBindViewHolder(holder: H, cursor: Cursor, position: Int)

    override fun getItemCount(): Int = currentCursor?.takeIf { dataValid }?.count ?: 0

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H

    /** Swaps in [cursor] and closes the old one. */
    override fun changeCursor(cursor: Cursor?) {
        swapCursor(cursor)?.close()
    }

    /**
     * Swaps in [newCursor] and returns the old one, which is *not* closed. Returns null when
     * [newCursor] is the cursor already in use.
     */
    open fun swapCursor(newCursor: Cursor?): Cursor? {
        if (newCursor === currentCursor) return null
        val old = currentCursor
        old?.let(::unregisterObservers)
        currentCursor = newCursor
        if (newCursor != null) {
            registerObservers(newCursor)
            rowIdColumn = newCursor.getColumnIndexOrThrow("_id")
            dataValid = true
        } else {
            rowIdColumn = -1
            dataValid = false
        }
        notifyDataSetChanged()
        return old
    }

    /** Text for [cursor] (used for auto-complete); override to convert your rows. */
    override fun convertToString(cursor: Cursor?): CharSequence? = cursor?.toString() ?: ""

    /**
     * Runs the query for [constraint] on a background thread: the [FilterQueryProvider]'s result,
     * or the current cursor unfiltered when there is none. The result goes to [changeCursor], so
     * the previous cursor gets closed.
     */
    override fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor? =
        filterQueryProvider?.runQuery(constraint) ?: currentCursor

    override fun getFilter(): Filter = cursorFilter ?: CursorFilter(this).also { cursorFilter = it }

    open fun getFilterQueryProvider(): FilterQueryProvider? = filterQueryProvider

    open fun setFilterQueryProvider(filterQueryProvider: FilterQueryProvider?) {
        this.filterQueryProvider = filterQueryProvider
    }

    /** The cursor's content changed; with [FLAG_AUTO_REQUERY] this re-runs its query. */
    protected open fun onContentChanged() {
        val cursor = currentCursor
        if (autoRequery && cursor != null && !cursor.isClosed) {
            @Suppress("DEPRECATION")
            dataValid = cursor.requery()
        }
    }

    protected inner class ChangeObserver : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun deliverSelfNotifications(): Boolean = true

        override fun onChange(selfChange: Boolean) = onContentChanged()
    }

    private inner class CursorDataSetObserver : DataSetObserver() {
        override fun onChanged() {
            dataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            dataValid = false
            notifyDataSetChanged()
        }
    }

    companion object {
        /**
         * Re-run the cursor's query on every content change notification. Implies
         * [FLAG_REGISTER_CONTENT_OBSERVER]. Discouraged: the query runs on the UI thread; load
         * with a `CursorLoader`-style background loader instead.
         */
        @Deprecated("Queries on the UI thread; load the cursor in the background instead.")
        const val FLAG_AUTO_REQUERY = 0x01

        /**
         * Observe the cursor and call [onContentChanged] on change notifications. Unset the
         * cursor from the adapter when done with it, or the observers leak.
         */
        const val FLAG_REGISTER_CONTENT_OBSERVER = 0x02
    }
}
