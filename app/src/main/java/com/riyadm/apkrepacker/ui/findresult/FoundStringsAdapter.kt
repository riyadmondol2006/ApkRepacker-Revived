package com.riyadm.apkrepacker.ui.findresult

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.thoughtbot.expandablerecyclerview.ExpandableRecyclerViewAdapter
import com.thoughtbot.expandablerecyclerview.models.ExpandableGroup

/** Expandable list of files with their matching lines. */
class FoundStringsAdapter(
    private val context: Context,
    private val listener: ParentViewHolder.ItemClickListener?,
    groups: List<ExpandableGroup<*>>?
) : ExpandableRecyclerViewAdapter<ParentViewHolder, ChildViewHolders>(groups) {

    private val replacedTitles = HashSet<String>()

    fun clearData() {
        replacedTitles.clear()
        clearExpandableList()
        notifyDataSetChanged()
    }

    override fun onCreateGroupViewHolder(parent: ViewGroup, viewType: Int): ParentViewHolder {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.find_in_files_item, parent, false)
        return ParentViewHolder(row, listener).also { holder ->
            holder.onReplacedMarked = { replacedTitles += it }
        }
    }

    override fun onCreateChildViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolders {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.find_in_files_item_child, parent, false)
        return ChildViewHolders(row)
    }

    override fun onBindChildViewHolder(holder: ChildViewHolders, flatPosition: Int, group: ExpandableGroup<*>, childIndex: Int) {
        val child = (group as ParentData).items[childIndex]
        holder.setText(child.getSpannableName(), TextView.BufferType.SPANNABLE)
        holder.view.setOnClickListener {
            runCatching {
                context.startActivity(
                    Intent(context, CodeEditorActivity::class.java)
                        .putExtra("offset", child.offset)
                        .putExtra("filePath", child.path)
                )
            }.onFailure { it.printStackTrace() }
        }
    }

    override fun onBindGroupViewHolder(holder: ParentViewHolder, flatPosition: Int, group: ExpandableGroup<*>) {
        holder.setGroupName(group)
        holder.showReplaced(group.title?.let { it in replacedTitles } == true)
        holder.setExpandedImmediately(isGroupExpanded(group))
    }
}
