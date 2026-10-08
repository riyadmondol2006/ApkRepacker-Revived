package com.riyadm.apkrepacker.ui.filemanager

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.PathButtonLayoutBinding
import java.io.File

/** Breadcrumb of the current location: one chip per folder, the last one highlighted. */
class PathButtonAdapter : RecyclerView.Adapter<PathButtonAdapter.ViewHolder>() {

    private val pathList = ArrayList<File>()
    private var onItemClickListener: OnItemClickListener? = null

    fun getItem(position: Int): File = pathList[position]

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        onItemClickListener = listener
    }

    override fun getItemCount() = pathList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(PathButtonLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chip = holder.binding.pathChip
        val isCurrent = position == pathList.lastIndex
        chip.text = pathList[position].name.ifEmpty { "/" }
        holder.binding.pathSeparator.isVisible = !isCurrent
        val container = if (isCurrent) R.attr.colorSecondaryContainer else R.attr.colorSurfaceContainerHigh
        val content = if (isCurrent) R.attr.colorOnSecondaryContainer else R.attr.colorOnSurface
        chip.chipBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(chip, container))
        chip.setTextColor(MaterialColors.getColor(chip, content))
        chip.setOnClickListener { view ->
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) onItemClickListener?.onItemClick(current, view)
        }
    }

    /**
     * @param projectMode only folders at or below the project directory (the part of the path
     * after `projects/`) are shown.
     */
    fun setPath(path: File?, projectMode: Boolean) {
        pathList.clear()
        var current = path
        while (current != null) {
            if (!projectMode || "projects/" in current.absolutePath) pathList.add(current)
            current = current.parentFile
        }
        pathList.reverse()
        notifyDataSetChanged()
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int, view: View)
    }

    class ViewHolder(val binding: PathButtonLayoutBinding) : RecyclerView.ViewHolder(binding.root)
}
