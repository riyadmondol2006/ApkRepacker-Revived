package com.riyadm.apkrepacker.ui.projectview.treeview.viewholder

import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.R as MaterialR
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ListTreeItemFolderBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemDataClickListener
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemFileClickListener
import com.riyadm.apkrepacker.ui.projectview.treeview.model.ItemData

/** A folder row: chevron that springs open, a folder tile, tap to expand, long-press for actions. */
class ParentViewHolder(private val binding: ListTreeItemFolderBinding) : BaseViewHolder(binding.root) {

    private var itemFileClickListener: ItemFileClickListener? = null
    private var chevronSpring: SpringAnimation? = null

    fun setItemFileClickListener(listener: ItemFileClickListener?) {
        itemFileClickListener = listener
    }

    fun bindView(itemData: ItemData, position: Int, imageClickListener: ItemDataClickListener?) {
        val path = itemData.path.orEmpty()
        binding.container.setPaddingRelative(
            indentFor(itemData.treeDepth), 0, binding.container.paddingEnd, 0,
        )
        binding.listItemName.text = itemData.text
        showExpanded(itemData.isExpand, animate = false)

        binding.container.setOnClickListener {
            if (imageClickListener == null) return@setOnClickListener
            if (itemData.isExpand) {
                imageClickListener.onHideChildren(itemData)
                itemData.isExpand = false
            } else {
                imageClickListener.onExpandChildren(itemData)
                itemData.isExpand = true
            }
            showExpanded(itemData.isExpand, animate = true)
        }
        binding.container.setOnLongClickListener {
            itemFileClickListener?.onFileLongClick(path)
            true
        }
    }

    private fun showExpanded(expanded: Boolean, animate: Boolean) {
        val rotation = if (expanded) 90f else 0f
        chevronSpring?.cancel()
        if (animate) {
            chevronSpring = binding.imgArrow.springTo(DynamicAnimation.ROTATION, rotation, MotionSpring.FastSpatial)
        } else {
            binding.imgArrow.rotation = rotation
        }
        if (expanded) {
            binding.imgIcon.setTone(MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer)
            binding.imgIcon.setIconRes(R.drawable.ic_m3_folder_open)
        } else {
            binding.imgIcon.setTone(MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer)
            binding.imgIcon.setIconRes(R.drawable.ic_directory)
        }
    }
}
