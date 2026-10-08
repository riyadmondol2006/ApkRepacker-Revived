package com.riyadm.apkrepacker.ui.appslist

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemAppsBinding
import com.riyadm.apkrepacker.ui.motion.pressSpring
import com.riyadm.apkrepacker.ui.rows.IconShapeMorpher
import com.riyadm.apkrepacker.ui.rows.applyGroupedShape
import com.riyadm.apkrepacker.utils.PackageMeta

class AppsAdapter(@Suppress("unused") context: Context) :
    ListAdapter<PackageMeta, AppsAdapter.ViewHolder>(Diff) {

    private var listener: OnItemInteractionListener? = null

    init {
        setHasStableIds(true)
    }

    fun setData(packages: List<PackageMeta>?) {
        submitList(packages.orEmpty()) { refreshGroupEdges() }
    }

    fun setInteractionListener(listener: OnItemInteractionListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAppsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_SHAPE in payloads) {
            holder.applyShape(position, itemCount)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    private fun refreshGroupEdges() {
        val count = itemCount
        if (count == 0) return
        notifyItemRangeChanged(0, minOf(2, count), PAYLOAD_SHAPE)
        if (count > 2) notifyItemRangeChanged(count - 2, 2, PAYLOAD_SHAPE)
    }

    class ViewHolder(private val binding: ItemAppsBinding) : RecyclerView.ViewHolder(binding.root) {

        private val iconMorpher = IconShapeMorpher(binding.tvAppIcon)

        init {
            binding.appCard.pressSpring()
        }

        fun bind(item: PackageMeta, position: Int, count: Int, listener: OnItemInteractionListener?) {
            binding.tvAppName.text = item.label
            binding.tvAppPackage.text = item.packageName
            binding.tvAppVersion.text = "${item.versionName.orEmpty()} (${item.versionCode})".trim()
            binding.tvSplitBadge.isVisible = item.hasSplits
            applyShape(position, count)
            iconMorpher.setActive(active = false, animate = false)

            binding.appCard.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) listener?.onBackupButtonClicked(item)
            }

            Glide.with(binding.tvAppIcon)
                .load(item.iconDrawable ?: R.drawable.default_app_icon)
                .placeholder(android.R.color.transparent)
                .centerInside()
                .into(binding.tvAppIcon)
        }

        fun applyShape(position: Int, count: Int) {
            binding.appCard.applyGroupedShape(position, count)
        }

        fun recycle() {
            Glide.with(binding.tvAppIcon).clear(binding.tvAppIcon)
        }
    }

    interface OnItemInteractionListener {
        fun onBackupButtonClicked(packageMeta: PackageMeta)
    }

    private object Diff : DiffUtil.ItemCallback<PackageMeta>() {
        override fun areItemsTheSame(oldItem: PackageMeta, newItem: PackageMeta) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: PackageMeta, newItem: PackageMeta) =
            oldItem.label == newItem.label &&
                oldItem.versionCode == newItem.versionCode &&
                oldItem.versionName == newItem.versionName &&
                oldItem.hasSplits == newItem.hasSplits &&
                oldItem.isSystemApp == newItem.isSystemApp
    }

    private companion object {
        const val PAYLOAD_SHAPE = "shape"
    }
}
