package com.riyadm.apkrepacker.ui.filemanager

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.recycler.OnItemSelectedListener
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder

/**
 * Directory listing with multi-selection. Selection is kept by path, so it survives a rescan
 * (rows that disappear are dropped from it); listing changes are diffed so rows animate in and out.
 */
class FileAdapter : RecyclerView.Adapter<FileListViewHolder>() {

    private var items: List<FileHolder> = emptyList()
    private val selectedPaths = LinkedHashSet<String>()

    private var onItemClickListener: FileListViewHolder.OnItemClickListener? = null
    private var onItemSelectedListener: OnItemSelectedListener? = null
    private var projectMode = false

    fun setProjectMode(enable: Boolean) {
        projectMode = enable
    }

    fun setOnItemClickListener(listener: FileListViewHolder.OnItemClickListener?) {
        onItemClickListener = listener
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        onItemSelectedListener = listener
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FileListViewHolder(parent)

    override fun onBindViewHolder(holder: FileListViewHolder, position: Int) {
        val listener = onItemClickListener ?: return
        holder.bind(items[position].file, position, listener, isSelected(position), projectMode)
        holder.bind(position, items.size)
    }

    override fun onBindViewHolder(holder: FileListViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION) || payloads.contains(PAYLOAD_APPEARANCE)) {
            if (payloads.contains(PAYLOAD_SELECTION)) holder.bindSelection(isSelected(position), animate = true)
            holder.bind(position, items.size)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** Replaces the listing, animating the difference. Selected rows that vanished are deselected. */
    fun submit(files: List<FileHolder>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = files.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].file.path == files[newPos].file.path
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = old[oldPos].file
                val b = files[newPos].file
                return a.lastModified() == b.lastModified() && a.length() == b.length()
            }
        })
        items = ArrayList(files)
        diff.dispatchUpdatesTo(this)
        // The segment shape (first / middle / last) of the rows next to the edges may have changed.
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, minOf(2, items.size), PAYLOAD_APPEARANCE)
            val tail = minOf(2, items.size)
            notifyItemRangeChanged(items.size - tail, tail, PAYLOAD_APPEARANCE)
        }
        val changed = selectedPaths.retainAll(items.mapTo(HashSet()) { it.file.path })
        if (changed) onItemSelectedListener?.onItemSelected()
    }

    fun selectAll() {
        selectedPaths.clear()
        items.mapTo(selectedPaths) { it.file.path }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onItemSelectedListener?.onItemSelected()
    }

    fun clearSelection() {
        if (selectedPaths.isEmpty()) {
            onItemSelectedListener?.onItemSelected()
            return
        }
        val positions = getSelectedPositions()
        selectedPaths.clear()
        positions.forEach { notifyItemChanged(it, PAYLOAD_SELECTION) }
        onItemSelectedListener?.onItemSelected()
    }

    fun select(positions: List<Int>) {
        selectedPaths.clear()
        positions.mapNotNullTo(selectedPaths) { items.getOrNull(it)?.file?.path }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onItemSelectedListener?.onItemSelected()
    }

    fun toggle(position: Int) {
        val path = items.getOrNull(position)?.file?.path ?: return
        if (!selectedPaths.remove(path)) selectedPaths.add(path)
        notifyItemChanged(position, PAYLOAD_SELECTION)
        onItemSelectedListener?.onItemSelected()
    }

    fun anySelected() = selectedPaths.isNotEmpty()

    fun getSelectedItemCount() = selectedPaths.size

    fun getSelectedItems(): ArrayList<FileHolder> = items.filterTo(ArrayList()) { it.file.path in selectedPaths }

    fun getSelectedPositions(): ArrayList<Int> =
        items.indices.filterTo(ArrayList()) { items[it].file.path in selectedPaths }

    fun indexOf(file: FileHolder?): Int = items.indexOf(file)

    fun get(index: Int): FileHolder = items[index]

    private fun isSelected(position: Int) = items[position].file.path in selectedPaths

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
        const val PAYLOAD_APPEARANCE = "appearance"
    }
}
