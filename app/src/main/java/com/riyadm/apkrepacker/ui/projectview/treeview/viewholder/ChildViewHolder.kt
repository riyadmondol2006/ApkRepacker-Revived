package com.riyadm.apkrepacker.ui.projectview.treeview.viewholder

import com.google.android.material.R as MaterialR
import com.riyadm.apkrepacker.databinding.ListTreeItemFileBinding
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemFileClickListener
import com.riyadm.apkrepacker.ui.projectview.treeview.model.ItemData
import com.riyadm.apkrepacker.utils.FileUtil
import java.io.File

/** A file row: tonal type icon + name; tap opens, long-press shows info. */
class ChildViewHolder(private val binding: ListTreeItemFileBinding) : BaseViewHolder(binding.root) {

    private var itemFileClickListener: ItemFileClickListener? = null

    fun setItemFileClickListener(listener: ItemFileClickListener?) {
        itemFileClickListener = listener
    }

    fun bindView(itemData: ItemData, position: Int) {
        val path = itemData.path.orEmpty()
        val file = File(path)
        binding.container.setPaddingRelative(
            indentFor(itemData.treeDepth), 0, binding.container.paddingEnd, 0,
        )
        binding.listItemName.text = itemData.text

        val (container, onContainer) = when (FileUtil.FileType.getFileType(file)) {
            FileUtil.FileType.IMAGE, FileUtil.FileType.VIDEO, FileUtil.FileType.AUDIO ->
                MaterialR.attr.colorPrimaryContainer to MaterialR.attr.colorOnPrimaryContainer
            FileUtil.FileType.XML, FileUtil.FileType.SMALI, FileUtil.FileType.JSON, FileUtil.FileType.JS,
            FileUtil.FileType.HTML, FileUtil.FileType.HTM, FileUtil.FileType.INI, FileUtil.FileType.TXT ->
                MaterialR.attr.colorTertiaryContainer to MaterialR.attr.colorOnTertiaryContainer
            else -> MaterialR.attr.colorSurfaceContainerHighest to MaterialR.attr.colorOnSurfaceVariant
        }
        binding.imgIcon.setTone(container, onContainer)
        binding.imgIcon.setIconRes(FileUtil.getImageResource(file))

        binding.container.setOnClickListener { itemFileClickListener?.onFileClick(path) }
        binding.container.setOnLongClickListener {
            itemFileClickListener?.onFileLongClick(path)
            true
        }
    }
}
