package com.riyadm.apkrepacker.ui.filemanager.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.listitem.ListItemViewHolder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ListItemFileBinding
import com.riyadm.apkrepacker.ui.filemanager.FileTone
import com.riyadm.apkrepacker.ui.filemanager.misc.ThumbnailHelper
import com.riyadm.apkrepacker.ui.motion.pressSpring
import com.riyadm.apkrepacker.utils.FileUtil
import java.io.File

/**
 * Expressive file row: a segmented list item with a tonal leading tile that morphs into a check
 * while the row is selected. Subclasses may override [bind] to change the supporting text.
 */
open class FileListViewHolder private constructor(private val binding: ListItemFileBinding) :
    ListItemViewHolder(binding.root) {

    constructor(parent: ViewGroup) : this(ListItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    @JvmField
    var mFileSize: TextView = binding.listItemSize

    init {
        binding.fileRow.pressSpring(0.98f)
        // The tile is a touch shortcut for the row's long-press; keep it out of TalkBack as an unlabeled button.
        binding.listItemImage.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    open fun bind(filePath: File, position: Int, listener: OnItemClickListener, selected: Boolean, projectMode: Boolean) {
        val context = itemView.context
        val item = FileHolder(filePath, context)
        val type = FileUtil.FileType.getFileType(filePath)
        val isDirectory = filePath.isDirectory

        val tile = binding.listItemImage
        val tone = FileTone.of(type)
        tile.setTone(tone.container, tone.onContainer)
        tile.setIconRes(FileUtil.getImageResource(filePath))
        ThumbnailHelper.requestIcon(item, tile.iconView, projectMode)

        binding.listItemName.text = item.name
        binding.listItemSize.text = if (isDirectory) context.getString(R.string.directory) else item.getFormattedSize(context, false)
        binding.listItemDate.text = item.getFormattedModificationDate(context)
        binding.listItemChevron.isVisible = isDirectory

        bindSelection(selected, animate = false)

        fun currentPosition() = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position
        binding.fileRow.setOnClickListener { listener.onFileClick(item, currentPosition()) }
        binding.fileRow.setOnLongClickListener {
            listener.onLongClick(item, currentPosition())
            true
        }
        tile.setOnClickListener { listener.onLongClick(item, currentPosition()) }
        binding.fileRow.contentDescription = if (selected) {
            context.getString(R.string.files_item_selected, item.name)
        } else {
            null
        }
    }

    /** Applies only the selection look; used for payload-only rebinds so the morph can animate. */
    fun bindSelection(selected: Boolean, animate: Boolean) {
        binding.fileRow.isChecked = selected
        binding.listItemImage.setTileSelected(selected, animate)
    }

    interface OnItemClickListener {
        fun onFileClick(item: FileHolder, position: Int)

        fun onLongClick(item: FileHolder, position: Int)
    }
}
