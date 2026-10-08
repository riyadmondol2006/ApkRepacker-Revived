package com.riyadm.apkrepacker.ui.findresult.files

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import java.io.File

/** File-name search results. */
class SearchListAdapter(private val onItemClickListener: FileListViewHolder.OnItemClickListener) :
    RecyclerView.Adapter<SearchListViewHolder>() {

    private var data: MutableList<File> = mutableListOf()

    fun notifyDataUpdated(updatedData: MutableList<File>) {
        val firstUpdatedIndex = Utils.firstDifferentItemIndex(data, updatedData)
        if (firstUpdatedIndex == -1) return // lists are equal
        val oldCount = data.size
        data = updatedData
        if (firstUpdatedIndex == oldCount) {
            notifyItemRangeInserted(firstUpdatedIndex, updatedData.size - oldCount)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchListViewHolder = SearchListViewHolder(parent)

    override fun onBindViewHolder(holder: SearchListViewHolder, position: Int) {
        holder.bind(data[position].absoluteFile, position, onItemClickListener, false, true)
    }

    override fun getItemCount(): Int = data.size

    fun clear() {
        val count = data.size
        data = mutableListOf()
        notifyItemRangeRemoved(0, count)
    }
}
