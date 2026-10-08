package com.riyadm.apkrepacker.ui.colorslist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemColorBinding
import com.riyadm.apkrepacker.ui.resourceeditor.morphCorners
import com.riyadm.apkrepacker.ui.resourceeditor.pressMorph
import com.riyadm.apkrepacker.utils.StringUtils

/**
 * Swatch cards for a color list. [resolve] turns a value (`#fff`, `@color/x`…) into an ARGB int, or null.
 * The card being edited stays "selected": its corners spring rounder and it is tinted.
 */
class ColorsAdapter(
    private val resolve: (String?) -> Int?,
) : ListAdapter<ColorMeta, ColorsAdapter.ViewHolder>(Differ) {

    private var listener: OnItemInteractionListener? = null
    private var selectedLabel: String? = null

    fun setInteractionListener(listener: OnItemInteractionListener?) {
        this.listener = listener
    }

    fun setData(colors: List<ColorMeta>?) = submitList(colors?.toList())

    /** Marks the card at [position] as the one being edited; `null` clears the selection. */
    fun setSelectedPosition(position: Int?) {
        val old = selectedLabel
        selectedLabel = position?.let { currentList.getOrNull(it)?.label }
        currentList.forEachIndexed { index, meta ->
            if (meta.label == old || meta.label == selectedLabel) notifyItemChanged(index, SELECTION)
        }
    }

    /** ARGB of [colorValue], or 0 (transparent) when it can't be resolved. */
    fun getColor(colorValue: String?): Int = resolve(colorValue) ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemColorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), animate = false)

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION)) holder.bind(getItem(position), animate = true) else super.onBindViewHolder(holder, position, payloads)
    }

    interface OnItemInteractionListener {
        fun onColorClicked(color: ColorMeta, id: Int)

        /** A long press copied the name of [color]. */
        fun onColorNameCopied(name: String) {}
    }

    inner class ViewHolder(private val binding: ItemColorBinding) : RecyclerView.ViewHolder(binding.root) {

        private val resources = binding.root.resources
        private val cornerRest = resources.getDimension(R.dimen.shape_corner_large)
        private val cornerPressed = resources.getDimension(R.dimen.shape_corner_small)
        private val cornerSelected = resources.getDimension(R.dimen.shape_corner_extra_large_increased)
        private val swatch = SwatchDrawable.create(binding.root)
        private var item: ColorMeta? = null

        init {
            binding.tvColorIcon.background = swatch
            binding.appItem.pressMorph(cornerRest, cornerPressed)
            binding.appItem.setOnClickListener {
                val current = item ?: return@setOnClickListener
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) listener?.onColorClicked(current, bindingAdapterPosition)
            }
            binding.appItem.setOnLongClickListener {
                val name = item?.label ?: return@setOnLongClickListener false
                StringUtils.setClipboard(binding.root.context, name, false)
                listener?.onColorNameCopied(name)
                true
            }
        }

        fun bind(meta: ColorMeta, animate: Boolean) {
            item = meta
            val selected = meta.label != null && meta.label == selectedLabel
            binding.tvColorName.text = meta.label
            binding.tvColorValue.text = meta.value
            swatch.color = resolve(meta.value) ?: 0
            binding.appItem.isChecked = selected
            val radius = if (selected) cornerSelected else cornerRest
            if (animate) {
                binding.appItem.morphCorners(radius)
            } else {
                binding.appItem.shapeAppearanceModel = binding.appItem.shapeAppearanceModel.withCornerSize(radius)
            }
        }
    }

    private object Differ : DiffUtil.ItemCallback<ColorMeta>() {
        override fun areItemsTheSame(oldItem: ColorMeta, newItem: ColorMeta) = oldItem.label == newItem.label

        override fun areContentsTheSame(oldItem: ColorMeta, newItem: ColorMeta) = oldItem.value == newItem.value
    }

    private companion object {
        const val SELECTION = "selection"
    }
}
