package com.riyadm.apkrepacker.filepicker

import android.text.format.DateUtils
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.listitem.ListItemViewHolder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemFilePickerBinding
import com.riyadm.apkrepacker.databinding.ItemFilePickerEmptyBinding
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FileUtil.FileType
import java.io.File
import com.google.android.material.R as M

/**
 * Rows of the file picker as segmented Expressive list items. The leading tile is tonal by file
 * kind and morphs into a check when the row is selected.
 */
class FileListAdapter(
    private val selectType: Int,
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        /** A directory (or the parent row) was tapped: navigate into it. */
        fun onOpenDirectory(item: FileListItem)

        /** Selection toggle requested; returns whether it was accepted. */
        fun onToggle(item: FileListItem, checked: Boolean): Boolean

        /** A row that can't be selected in this picker mode was tapped. */
        fun onNotSelectable()
    }

    private val items = ArrayList<FileListItem>()
    private var showEmpty = false

    fun submit(list: List<FileListItem>) {
        items.clear()
        items.addAll(list)
        showEmpty = list.none { !it.isParentRow }
        notifyDataSetChanged()
    }

    /** Rebinds only the selection state of every row (keeps the tile morph animating). */
    fun refreshSelection() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
    }

    override fun getItemCount(): Int = items.size + if (showEmpty) 1 else 0

    override fun getItemViewType(position: Int): Int = if (position < items.size) TYPE_ROW else TYPE_EMPTY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EMPTY) {
            object : RecyclerView.ViewHolder(ItemFilePickerEmptyBinding.inflate(inflater, parent, false).root) {}
        } else {
            RowHolder(ItemFilePickerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = onBindViewHolder(holder, position, mutableListOf())

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is RowHolder) holder.render(items[position], position, payloads.isNotEmpty())
    }

    private fun isSelectable(item: FileListItem): Boolean = !item.isParentRow && when (selectType) {
        FilePickerDialog.TYPE_FILE -> !item.isDirectory
        FilePickerDialog.TYPE_DIR -> item.isDirectory
        else -> true
    }

    private inner class RowHolder(private val binding: ItemFilePickerBinding) : ListItemViewHolder(binding.root) {

        fun render(item: FileListItem, position: Int, selectionOnly: Boolean) {
            bind(position, items.size)
            val selected = MarkedItemList.hasItem(item.path)
            val selectable = isSelectable(item)
            binding.pickerCheck.setOnClickListener(null)
            binding.pickerCheck.isChecked = selected
            binding.pickerTile.setTileSelected(selected, animate = selectionOnly)
            binding.pickerCard.isChecked = selected
            if (selectionOnly) return

            val context = binding.root.context
            val file = File(item.path)
            val (container, onContainer, icon) = when {
                item.isParentRow -> Triple(M.attr.colorSurfaceContainerHighest, M.attr.colorOnSurfaceVariant, R.drawable.ic_arrow_up)
                else -> Triple(containerFor(file), onContainerFor(file), FileUtil.getImageResource(file))
            }
            binding.pickerTile.setTone(container, onContainer)
            binding.pickerTile.setIconRes(icon)
            binding.pickerTile.setTileSelected(selected, animate = false)

            binding.pickerName.text = item.name
            binding.pickerInfo.text = if (item.isParentRow) {
                context.getString(R.string.label_parent_directory)
            } else {
                DateUtils.formatDateTime(
                    context, item.time,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
                )
            }

            binding.pickerCheck.visibility = if (selectable) View.VISIBLE else View.INVISIBLE
            binding.pickerCheck.contentDescription = item.name
            binding.pickerCard.isSwipeEnabled = false
            binding.pickerCheck.setOnClickListener {
                callbacks.onToggle(item, binding.pickerCheck.isChecked)
                refreshSelection()
            }
            binding.pickerCard.setOnClickListener {
                when {
                    item.isDirectory -> callbacks.onOpenDirectory(item)
                    selectable -> {
                        callbacks.onToggle(item, !MarkedItemList.hasItem(item.path))
                        refreshSelection()
                    }
                    else -> callbacks.onNotSelectable()
                }
            }
        }
    }

    private fun containerFor(file: File): Int = when (FileType.getFileType(file)) {
        FileType.DIRECTORY -> M.attr.colorPrimaryContainer
        FileType.APK, FileType.APKS, FileType.ZIP, FileType.DEX, FileType.BAK -> M.attr.colorTertiaryContainer
        FileType.IMAGE, FileType.VIDEO, FileType.AUDIO -> M.attr.colorSecondaryContainer
        else -> M.attr.colorSurfaceContainerHighest
    }

    @AttrRes
    private fun onContainerFor(file: File): Int = when (FileType.getFileType(file)) {
        FileType.DIRECTORY -> M.attr.colorOnPrimaryContainer
        FileType.APK, FileType.APKS, FileType.ZIP, FileType.DEX, FileType.BAK -> M.attr.colorOnTertiaryContainer
        FileType.IMAGE, FileType.VIDEO, FileType.AUDIO -> M.attr.colorOnSecondaryContainer
        else -> M.attr.colorOnSurfaceVariant
    }

    private companion object {
        const val TYPE_ROW = 0
        const val TYPE_EMPTY = 1
        const val PAYLOAD_SELECTION = "selection"
    }
}
