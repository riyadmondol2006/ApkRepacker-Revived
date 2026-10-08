package com.riyadm.apkrepacker.ui.dimenslist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemDimensBinding
import com.riyadm.apkrepacker.ui.resourceeditor.pressMorph

/** Rows of dimensions; the leading badge shows the unit (dp, sp, px…) or "ref" for `@dimen/…` references. */
class DimensAdapter : ListAdapter<DimensMeta, DimensAdapter.ViewHolder>(Differ) {

    private var listener: OnItemInteractionListener? = null

    fun setInteractionListener(listener: OnItemInteractionListener?) {
        this.listener = listener
    }

    fun setData(dimens: List<DimensMeta>?) = submitList(dimens?.toList())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemDimensBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    interface OnItemInteractionListener {
        fun onDimensClicked(dimens: DimensMeta, id: Int)
    }

    inner class ViewHolder(private val binding: ItemDimensBinding) : RecyclerView.ViewHolder(binding.root) {

        private var item: DimensMeta? = null

        init {
            val resources = binding.root.resources
            binding.appItem.pressMorph(
                resources.getDimension(R.dimen.shape_corner_large),
                resources.getDimension(R.dimen.shape_corner_small),
            )
            binding.appItem.setOnClickListener {
                val current = item ?: return@setOnClickListener
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) listener?.onDimensClicked(current, bindingAdapterPosition)
            }
        }

        fun bind(meta: DimensMeta) {
            item = meta
            binding.tvDimenName.text = meta.label
            binding.tvDimenValue.text = meta.value
            binding.tvDimenUnit.text = unitOf(meta.value)
        }
    }

    private object Differ : DiffUtil.ItemCallback<DimensMeta>() {
        override fun areItemsTheSame(oldItem: DimensMeta, newItem: DimensMeta) = oldItem.label == newItem.label

        override fun areContentsTheSame(oldItem: DimensMeta, newItem: DimensMeta) = oldItem.value == newItem.value
    }

    private companion object {
        val UNIT = Regex("[a-zA-Z]+$")

        fun unitOf(value: String?): String = when {
            value.isNullOrBlank() -> "?"
            value.startsWith("@") -> "ref"
            else -> UNIT.find(value.trim())?.value ?: "#"
        }
    }
}
