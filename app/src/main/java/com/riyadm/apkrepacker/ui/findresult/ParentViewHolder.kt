package com.riyadm.apkrepacker.ui.findresult

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.thoughtbot.expandablerecyclerview.models.ExpandableGroup
import com.thoughtbot.expandablerecyclerview.viewholders.GroupViewHolder

/**
 * File header of the string search results. The whole row toggles the group (handled by
 * [GroupViewHolder]); a long press opens the file actions.
 */
class ParentViewHolder(itemView: View, private val listener: ItemClickListener?) : GroupViewHolder(itemView) {

    private val title: TextView = itemView.findViewById(R.id.file_text_view)
    private val replacedMark: ImageView = itemView.findViewById(R.id.mark)
    private val chevron: ImageView = itemView.findViewById(R.id.chevron)

    private var groupTitle: String? = null

    /** Told when this row's file has been replaced, so the adapter can keep the mark across rebinds. */
    var onReplacedMarked: ((String) -> Unit)? = null

    override fun expand() {
        chevron.springTo(DynamicAnimation.ROTATION, 180f, MotionSpring.FastSpatial)
    }

    override fun collapse() {
        chevron.springTo(DynamicAnimation.ROTATION, 0f, MotionSpring.FastSpatial)
    }

    /** Snaps the chevron to its state without animating (used when the row is bound for another group). */
    fun setExpandedImmediately(expanded: Boolean) {
        chevron.springTo(DynamicAnimation.ROTATION, if (expanded) 180f else 0f, MotionSpring.FastSpatial).cancel()
        chevron.rotation = if (expanded) 180f else 0f
    }

    fun changeTextColor() {
        showReplaced(true)
        groupTitle?.let { onReplacedMarked?.invoke(it) }
    }

    fun showReplaced(replaced: Boolean) {
        replacedMark.visibility = if (replaced) View.VISIBLE else View.GONE
        val colorAttr = if (replaced) R.attr.colorPrimary else R.attr.colorOnSurface
        title.setTextColor(MaterialColors.getColor(title, colorAttr))
    }

    fun setGroupName(group: ExpandableGroup<*>) {
        groupTitle = group.title
        title.text = group.title
        itemView.setOnLongClickListener {
            listener?.onTitleClick(ProjectUtils.getProjectPath() + "/" + group.title, bindingAdapterPosition, this)
            true
        }
    }

    interface ItemClickListener {
        fun onTitleClick(file: String, id: Int, holder: ParentViewHolder)
    }
}
