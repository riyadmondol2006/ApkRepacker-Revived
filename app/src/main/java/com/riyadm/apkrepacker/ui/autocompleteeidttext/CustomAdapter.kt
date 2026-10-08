package com.riyadm.apkrepacker.ui.autocompleteeidttext

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.riyadm.apkrepacker.databinding.AutocompleteTextViewBinding
import java.util.Locale

/**
 * Suggestions for the find / replace fields: earlier queries filtered by prefix, each with a
 * button that removes it from the history. [dataList] is the whole history (what gets saved),
 * whatever the field is currently filtering by.
 */
class CustomAdapter(@Suppress("UNUSED_PARAMETER") context: Context?, storeDataLst: MutableList<String>?) :
    BaseAdapter(), Filterable {

    val dataList: MutableList<String> = storeDataLst ?: mutableListOf()
    private var shown: List<String> = dataList.toList()

    override fun getCount(): Int = shown.size

    override fun getItem(position: Int): String = shown[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = convertView?.let { AutocompleteTextViewBinding.bind(it) }
            ?: AutocompleteTextViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val value = getItem(position)
        binding.itemAutocomplete.text = value
        binding.deleteItemAutocomplete.setOnClickListener {
            dataList.remove(value)
            shown = shown - value
            notifyDataSetChanged()
        }
        return binding.root
    }

    /** Remembers [value] as the most recent entry. */
    fun addValue(value: String) {
        dataList.remove(value)
        dataList.add(value)
        shown = dataList.toList()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(prefix: CharSequence?): FilterResults {
            val lower = prefix?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val matches = dataList.toList().filter { lower.isEmpty() || it.lowercase(Locale.getDefault()).startsWith(lower) }
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            shown = (results?.values as? List<String>).orEmpty()
            if (shown.isNotEmpty()) notifyDataSetChanged() else notifyDataSetInvalidated()
        }
    }
}
