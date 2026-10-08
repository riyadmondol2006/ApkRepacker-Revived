package com.riyadm.apkrepacker.view.recyclerview.adapter

import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView

/**
 * @author Created by cz
 * @date 2020-03-19 21:43
 * @email bingo110@126.com
 *
 * A simple filter adapter. While a query is active, the adapter shows a separate list holding
 * only the items for which [filterObject] returns true; the original list is untouched.
 */
abstract class FilterAdapter<VH : RecyclerView.ViewHolder, E>(itemList: List<E>?) :
    BaseAdapter<VH, E>(itemList), Filterable {

    private val objectFilter = ObjectFilter()

    /** Items matching the current query. */
    private val queryList = mutableListOf<E>()

    private var queryWord: String? = null

    open fun getQueryWord(): String? = queryWord

    override fun getFilter(): Filter = objectFilter

    override fun getItemCount(): Int = if (queryWord.isNullOrEmpty()) super.getItemCount() else queryList.size

    override fun getItem(position: Int): E = if (queryWord.isNullOrEmpty()) super.getItem(position) else queryList[position]

    /** Returns true if [item] matches the search [word]. */
    protected abstract fun filterObject(item: E, word: CharSequence): Boolean

    internal inner class ObjectFilter : Filter() {
        override fun performFiltering(word: CharSequence?): FilterResults {
            queryWord = word?.toString()?.takeIf { it.isNotEmpty() }
            val matches = word
                ?.takeIf { it.isNotEmpty() }
                ?.let { query -> getItemList().filter { filterObject(it, query) } }
                .orEmpty()
            return FilterResults().apply {
                count = matches.size
                values = matches
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val matches = results?.values as? List<E>
            if (!constraint.isNullOrEmpty() && matches != null) {
                queryList.clear()
                queryList.addAll(matches)
            }
            notifyDataSetChanged()
        }
    }
}
