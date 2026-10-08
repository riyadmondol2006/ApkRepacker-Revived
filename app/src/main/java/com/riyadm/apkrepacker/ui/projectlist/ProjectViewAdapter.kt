package com.riyadm.apkrepacker.ui.projectlist

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.riyadm.apkrepacker.databinding.ItemProjectViewBinding
import com.riyadm.apkrepacker.utils.ProjectUtils

class ProjectViewAdapter(context: Context) :
    ListAdapter<ProjectItem, ProjectViewHolder>(Diff) {

    private val appContext: Context = context.applicationContext
    private var onItemClickListener: ProjectViewHolder.OnItemClickListener? = null

    // Project icons are base64 strings; decode once per project, not once per bind.
    private val iconCache = LruCache<String, Drawable.ConstantState>(64)

    /** Grouped rows (segmented corners) in a single column; separate cards in a grid. */
    var grouped = true
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SHAPE)
            }
        }

    fun setData(data: List<ProjectItem?>?) {
        val previous = currentList.associateBy { it.appProjectPath }
        val items = data.orEmpty().filterNotNull().map { fresh ->
            val old = previous[fresh.appProjectPath] ?: return@map fresh
            // Unchanged project: keep the instance the bound holder already references, so the
            // expanded state it toggles stays in the list. Changed project: carry the state over.
            if (sameProject(old, fresh)) old else fresh.also { it.isChecked = old.isChecked }
        }
        submitList(items) { refreshGroupEdges() }
    }

    private fun sameProject(a: ProjectItem, b: ProjectItem) =
        a.appProjectPath == b.appProjectPath &&
            a.appName == b.appName &&
            a.appPackage == b.appPackage &&
            a.appIcon == b.appIcon &&
            a.apkPatch == b.apkPatch &&
            a.appVersionName == b.appVersionName &&
            a.appVersionCode == b.appVersionCode

    fun clear() {
        submitList(emptyList())
    }

    fun setOnItemClickListener(listener: ProjectViewHolder.OnItemClickListener?) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder =
        ProjectViewHolder(ItemProjectViewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, iconFor(item), onItemClickListener, position, itemCount, grouped)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_SHAPE in payloads) {
            holder.applyShape(position, itemCount, grouped)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ProjectViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    /** Neighbours of an inserted/removed row change which corner is "outer"; rebind just the shape. */
    private fun refreshGroupEdges() {
        val count = itemCount
        if (count == 0) return
        notifyItemRangeChanged(0, minOf(2, count), PAYLOAD_SHAPE)
        if (count > 2) notifyItemRangeChanged(count - 2, 2, PAYLOAD_SHAPE)
    }

    private fun iconFor(item: ProjectItem): Drawable? {
        val key = item.appProjectPath + '#' + item.appIcon.hashCode()
        iconCache.get(key)?.let { return it.newDrawable(appContext.resources) }
        val drawable = ProjectUtils.getProjectIconDrawable(item.appIcon, appContext) ?: return null
        drawable.constantState?.let { iconCache.put(key, it) }
        return drawable
    }

    private object Diff : DiffUtil.ItemCallback<ProjectItem>() {
        override fun areItemsTheSame(oldItem: ProjectItem, newItem: ProjectItem) =
            oldItem.appProjectPath == newItem.appProjectPath

        override fun areContentsTheSame(oldItem: ProjectItem, newItem: ProjectItem) =
            oldItem.appName == newItem.appName &&
                oldItem.appPackage == newItem.appPackage &&
                oldItem.appIcon == newItem.appIcon &&
                oldItem.appVersionName == newItem.appVersionName &&
                oldItem.appVersionCode == newItem.appVersionCode &&
                oldItem.isChecked == newItem.isChecked
    }

    private companion object {
        const val PAYLOAD_SHAPE = "shape"
    }
}
