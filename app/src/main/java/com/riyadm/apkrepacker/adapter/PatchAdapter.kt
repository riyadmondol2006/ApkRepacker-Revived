package com.riyadm.apkrepacker.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ListItemPatchBinding

class PatchAdapter(@Suppress("UNUSED_PARAMETER") context: Context?) : RecyclerView.Adapter<PatchAdapter.ViewHolder>() {

    private val mPatchData = mutableListOf<PatchItem>()
    private val selectedItems = mutableSetOf<PatchItem>()

    /** Called when the user taps the remove button of a row. */
    var onRemoveClickListener: ((PatchItem) -> Unit)? = null

    val patchData: List<PatchItem>
        get() = mPatchData

    val selectedPositions: ArrayList<Int>
        get() = mPatchData.indices.filterTo(ArrayList()) { isSelected(it) }

    val selectedItemCount: Int
        get() = selectedItems.size

    fun setData(data: List<PatchItem>) {
        val start = mPatchData.size
        mPatchData.addAll(data)
        notifyItemRangeInserted(start, data.size)
    }

    fun addItem(item: PatchItem) {
        mPatchData.add(item)
        notifyItemInserted(mPatchData.lastIndex)
    }

    /** Refreshes a row after its patch metadata finished loading off the main thread. */
    fun refreshItem(item: PatchItem) {
        val index = mPatchData.indexOf(item)
        if (index != -1) notifyItemChanged(index)
    }

    fun deleteItem(item: PatchItem?) {
        val index = mPatchData.indexOf(item)
        if (index != -1) deleteItem(index)
    }

    fun deleteItem(index: Int) {
        selectedItems.remove(mPatchData.removeAt(index))
        notifyItemRemoved(index)
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(mPatchData)
        notifyItemRangeChanged(0, itemCount)
    }

    fun clearSelection() {
        val previous = selectedPositions
        selectedItems.clear()
        previous.forEach { notifyItemChanged(it) }
    }

    fun toggle(position: Int) {
        val item = mPatchData.getOrNull(position) ?: return
        if (!selectedItems.remove(item)) selectedItems.add(item)
        notifyItemChanged(position)
    }

    fun anySelected(): Boolean = selectedItems.isNotEmpty()

    private fun isSelected(position: Int): Boolean = mPatchData[position] in selectedItems

    override fun getItemCount(): Int = mPatchData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemPatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding).also { holder ->
            holder.binding.btnRemove.setOnClickListener {
                holder.bindingAdapterPosition
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { onRemoveClickListener?.invoke(mPatchData[it]) }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(mPatchData[position], isSelected(position))
    }

    class ViewHolder internal constructor(internal val binding: ListItemPatchBinding) : RecyclerView.ViewHolder(binding.root) {

        internal fun bindTo(item: PatchItem, selected: Boolean) {
            binding.root.isChecked = selected
            binding.patchName.text = item.mPatchName

            val context = binding.root.context
            val info = item.info
            val subtitle = buildList {
                info?.author?.let { add(it) }
                if (info != null && info.ruleCount > 0) {
                    add(context.resources.getQuantityString(R.plurals.patcher_rule_count, info.ruleCount, info.ruleCount))
                }
                info?.packageName?.takeIf { it != "*" && !it.equals("ALL", ignoreCase = true) }?.let { add(it) }
            }.joinToString("  ·  ")
            binding.patchSubtitle.text = subtitle
            binding.patchSubtitle.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
            binding.patchChipSmali.visibility = if (info?.needsSmali == true) View.VISIBLE else View.GONE
        }
    }
}
