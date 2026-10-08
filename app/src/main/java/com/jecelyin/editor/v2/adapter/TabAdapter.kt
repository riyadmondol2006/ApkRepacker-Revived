/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jecelyin.editor.v2.common.TabInfo
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemEditorTabBinding
import com.riyadm.apkrepacker.ui.motion.pressSpring

/**
 * The open files of the editor, shown as tonal cards in the start drawer. The selected tab is
 * the checked card.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class TabAdapter : ListAdapter<TabInfo, TabAdapter.ViewHolder>(DIFF) {
    private var onTabClick: ((Int) -> Unit)? = null
    private var onTabClose: ((Int) -> Unit)? = null
    private var currentTab = 0

    fun setOnTabClickListener(onClick: ((Int) -> Unit)?, onClose: ((Int) -> Unit)?) {
        onTabClick = onClick
        onTabClose = onClose
    }

    fun setTabInfoList(tabInfoList: Array<TabInfo?>?) {
        submitList(tabInfoList.orEmpty().filterNotNull())
    }

    fun setCurrentTab(index: Int) {
        val previous = currentTab
        currentTab = index
        for (position in intArrayOf(previous, index)) {
            if (position in 0 until itemCount) notifyItemChanged(position, PAYLOAD_SELECTION)
        }
    }

    public override fun getItem(position: Int): TabInfo = super.getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditorTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding).also { holder ->
            binding.root.pressSpring()
            binding.root.setOnClickListener {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onTabClick?.invoke(it) }
            }
            binding.btnClose.setOnClickListener {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onTabClose?.invoke(it) }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = getItem(position)
        val title = tab.title.orEmpty()
        with(holder.binding) {
            listItemIcon.setImageResource(if (title.endsWith(".java")) R.drawable.ic_java else R.drawable.ic_txt)
            titleTextView.text = title
            fileTextView.text = tab.path
            unsavedDot.visibility = if (tab.hasChanged()) View.VISIBLE else View.GONE
            root.isChecked = position == currentTab
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.binding.root.isChecked = position == currentTab
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class ViewHolder(val binding: ItemEditorTabBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val PAYLOAD_SELECTION = "selection"

        val DIFF = object : DiffUtil.ItemCallback<TabInfo>() {
            override fun areItemsTheSame(oldItem: TabInfo, newItem: TabInfo) =
                oldItem.path == newItem.path && oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: TabInfo, newItem: TabInfo) =
                oldItem.hasChanged() == newItem.hasChanged()
        }
    }
}
