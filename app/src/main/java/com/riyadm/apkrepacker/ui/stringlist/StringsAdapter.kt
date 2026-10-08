package com.riyadm.apkrepacker.ui.stringlist

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.databinding.ItemStringValueBinding
import com.riyadm.apkrepacker.ui.resourceeditor.pressMorph
import com.riyadm.apkrepacker.utils.StringUtils

class StringsAdapter : RecyclerView.Adapter<StringsAdapter.ViewHolder>() {

    private val items = ArrayList<TranslateItem>()
    private var listener: OnItemClickListener? = null

    /** Fired after the backing list changed so a mirror adapter (the search results) can follow. */
    var onChanged: (() -> Unit)? = null

    val data: MutableList<TranslateItem>
        get() = items

    fun setInteractionListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    /** Swaps the list with a diff, so rows that did not change keep their state and move with a spring. */
    fun setUpdatedItems(newItems: List<TranslateItem>) {
        if (newItems.size > DIFF_LIMIT || items.size > DIFF_LIMIT) {
            setItems(newItems)
            return
        }
        val diff = DiffUtil.calculateDiff(StringsDiffCallback(items.toList(), newItems), true)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
        onChanged?.invoke()
    }

    fun setItems(newItems: List<TranslateItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onChanged?.invoke()
    }

    fun addItem(item: TranslateItem) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
        onChanged?.invoke()
    }

    fun setUpdateValue(value: String?, position: Int) {
        val item = items.getOrNull(position) ?: return
        item.translatedValue = value
        notifyItemChanged(position)
        onChanged?.invoke()
    }

    fun remove(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        onChanged?.invoke()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemStringValueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    interface OnItemClickListener {
        fun onTranslateClicked(item: TranslateItem, position: Int)

        /** The translation was edited in place in the row. */
        fun onValueEdited(item: TranslateItem, position: Int) {}

        /** The key of a string was copied by a long press. */
        fun onNameCopied(name: String) {}
    }

    inner class ViewHolder(private val binding: ItemStringValueBinding) : RecyclerView.ViewHolder(binding.root) {

        private var item: TranslateItem? = null
        private var isBinding = false
        private val context: Context get() = itemView.context
        private val cornerRest = context.resources.getDimension(R.dimen.shape_corner_large)
        private val cornerPressed = context.resources.getDimension(R.dimen.shape_corner_small)

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isBinding) return
                val current = item ?: return
                current.translatedValue = s?.toString().orEmpty()
                binding.stringStatus.isVisible = !current.translatedValue.isNullOrBlank()
                listener?.onValueEdited(current, bindingAdapterPosition)
            }
        }

        init {
            binding.stringCard.pressMorph(cornerRest, cornerPressed)
            binding.stringCard.setOnClickListener {
                val current = item ?: return@setOnClickListener
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) listener?.onTranslateClicked(current, position)
            }
            binding.stringCard.setOnLongClickListener {
                val current = item ?: return@setOnLongClickListener false
                StringUtils.setClipboard(context, current.name, false)
                listener?.onNameCopied(current.name.orEmpty())
                true
            }
            binding.stringValue.addTextChangedListener(watcher)
        }

        fun bind(translateItem: TranslateItem) {
            item = translateItem
            isBinding = true
            binding.stringKey.text = translateItem.name
            binding.stringName.text = translateItem.originValue
            binding.stringValue.setText(translateItem.translatedValue)
            binding.stringStatus.isVisible = !translateItem.translatedValue.isNullOrBlank()
            isBinding = false
        }
    }

    private companion object {
        const val DIFF_LIMIT = 1500
    }
}
