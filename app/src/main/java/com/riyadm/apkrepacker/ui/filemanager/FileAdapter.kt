package com.riyadm.apkrepacker.ui.filemanager

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.recycler.OnItemSelectedListener
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Directory listing with multi-selection. Selection is kept by path, so it survives a rescan
 * (rows that disappear are dropped from it); listing changes are diffed so rows animate in and out.
 */
class FileAdapter : RecyclerView.Adapter<FileListViewHolder>() {

    private var items: List<FileHolder> = emptyList()
    private var submitGeneration = 0
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
        holder.bind(items[position], position, listener, isSelected(position), projectMode)
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

    /**
     * Replaces the listing, animating the difference. Selected rows that vanished are deselected.
     * Moving to another folder (no row in common) swaps the list at once; a rescan of the same
     * folder is diffed on a background thread from the holders' cached attributes and applied
     * when ready, unless a newer listing came in meanwhile.
     */
    fun submit(files: List<FileHolder>) {
        val old = items
        val next: List<FileHolder> = ArrayList(files)
        val generation = ++submitGeneration
        if (old.isEmpty() || next.isEmpty() || !sharesAnyPath(old, next)) {
            items = next
            if (old.isNotEmpty()) notifyItemRangeRemoved(0, old.size)
            if (next.isNotEmpty()) notifyItemRangeInserted(0, next.size)
            onListCommitted()
            return
        }
        if (old.size + next.size <= SYNC_DIFF_MAX_ITEMS) {
            commit(next, calculateDiff(old, next))
            return
        }
        DIFF_EXECUTOR.execute {
            val diff = calculateDiff(old, next)
            mainHandler.post { if (generation == submitGeneration) commit(next, diff) }
        }
    }

    private fun commit(next: List<FileHolder>, diff: DiffUtil.DiffResult) {
        items = next
        diff.dispatchUpdatesTo(this)
        onListCommitted()
    }

    private fun onListCommitted() {
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

        /** Up to this many rows (old + new) a diff takes well under a millisecond: no thread hop. */
        const val SYNC_DIFF_MAX_ITEMS = 300

        val mainHandler = Handler(Looper.getMainLooper())

        val DIFF_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FileListDiff").apply { isDaemon = true }
        }

        fun sharesAnyPath(old: List<FileHolder>, next: List<FileHolder>): Boolean {
            val paths = old.mapTo(HashSet(old.size * 2)) { it.file.path }
            return next.any { it.file.path in paths }
        }

        /** Compares only cached attributes ([FileHolder.loadAttributes]), never the disk. */
        fun calculateDiff(old: List<FileHolder>, next: List<FileHolder>): DiffUtil.DiffResult =
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].file.path == next[newPos].file.path
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val a = old[oldPos]
                    val b = next[newPos]
                    return a.lastModified == b.lastModified && a.size == b.size && a.isDirectory == b.isDirectory
                }
            })
    }
}
