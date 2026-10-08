package com.riyadm.apkrepacker.ui.projectlist

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.ItemProjectViewBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.pressSpring
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.ui.rows.IconShapeMorpher
import com.riyadm.apkrepacker.ui.rows.applyGroupedShape

class ProjectViewHolder(
    private val binding: ItemProjectViewBinding,
) : RecyclerView.ViewHolder(binding.root) {

    private val iconMorpher = IconShapeMorpher(binding.iconApp)
    private var expandAnimation: SpringAnimation? = null
    private var boundItem: ProjectItem? = null

    init {
        binding.projectCard.pressSpring()
    }

    internal fun bind(
        item: ProjectItem,
        icon: Drawable?,
        listener: OnItemClickListener?,
        position: Int,
        count: Int,
        grouped: Boolean,
    ) {
        boundItem = item
        with(binding) {
            appName.text = item.appName
            appPkg.text = item.appPackage
            appPkg.visibility = if (item.appPackage.isNullOrEmpty()) View.GONE else View.VISIBLE
            appVersion.text = formatVersion(item)
            appVersion.visibility = if (appVersion.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            appPatch.text = item.appProjectPath
            iconApp.setImageDrawable(icon)
        }
        applyShape(position, count, grouped)
        setExpanded(item.isChecked, animate = false)

        fun currentPosition() = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position

        binding.projectCard.setOnClickListener { listener?.onProjectClick(item, currentPosition()) }
        binding.projectCard.setOnLongClickListener {
            toggle(listener)
            true
        }
        binding.btnExpand.setOnClickListener { toggle(listener) }

        val menuClick = View.OnClickListener { v -> listener?.onProjectMenuClick(v, item, currentPosition()) }
        binding.actionBuild.setOnClickListener(menuClick)
        binding.actionAboutProject.setOnClickListener(menuClick)
        binding.actionDelete.setOnClickListener(menuClick)
        binding.actionExport.setOnClickListener(menuClick)
    }

    internal fun applyShape(position: Int, count: Int, grouped: Boolean) {
        binding.projectCard.applyGroupedShape(position, count, grouped)
    }

    private fun toggle(listener: OnItemClickListener?) {
        val item = boundItem ?: return
        item.isChecked = !item.isChecked
        setExpanded(item.isChecked, animate = true)
        val position = bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) listener?.onProjectLongClick(item, position)
    }

    private fun formatVersion(item: ProjectItem): String {
        val name = item.appVersionName
        val code = item.appVersionCode
        return when {
            !name.isNullOrEmpty() && !code.isNullOrEmpty() -> "$name ($code)"
            !name.isNullOrEmpty() -> name
            !code.isNullOrEmpty() -> code
            else -> ""
        }
    }

    /** Reveals or hides the Build / About / Delete group: the container's height springs, the chevron flips. */
    private fun setExpanded(expanded: Boolean, animate: Boolean) {
        val container = binding.menuExpandContainer
        expandAnimation?.cancel()
        binding.btnExpand.contentDescription = binding.root.context.getString(R.string.project_actions_toggle)
        iconMorpher.setActive(expanded, animate)

        if (!animate) {
            container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            container.visibility = if (expanded) View.VISIBLE else View.GONE
            container.alpha = 1f
            binding.btnExpand.rotation = if (expanded) 180f else 0f
            return
        }

        binding.btnExpand.springTo(DynamicAnimation.ROTATION, if (expanded) 180f else 0f, MotionSpring.FastSpatial)
        val width = (binding.projectCard.width - binding.projectCard.paddingLeft - binding.projectCard.paddingRight)
            .coerceAtLeast(1)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val full = container.measuredHeight.toFloat()
        val startHeight = if (container.visibility == View.VISIBLE) container.height.toFloat() else 0f
        val target = if (expanded) full else 0f

        container.visibility = View.VISIBLE
        container.layoutParams.height = startHeight.toInt()
        container.requestLayout()
        val params = container.layoutParams
        expandAnimation = SpringAnimation(FloatValueHolder(startHeight)).apply {
            setSpring(
                SpringForce(target)
                    .setStiffness(MotionSpring.DefaultSpatial.stiffness)
                    .setDampingRatio(0.9f),
            )
            setMinimumVisibleChange(1f)
            addUpdateListener { _, value, _ ->
                params.height = value.toInt().coerceAtLeast(0)
                container.alpha = if (full > 0f) (value / full).coerceIn(0f, 1f) else 1f
                container.requestLayout()
            }
            addEndListener { _, canceled, _, _ ->
                if (!canceled) {
                    if (expanded) {
                        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        container.alpha = 1f
                        container.requestLayout()
                    } else {
                        container.visibility = View.GONE
                    }
                }
            }
            start()
        }
    }

    internal fun recycle() {
        expandAnimation?.cancel()
        expandAnimation = null
    }

    interface OnItemClickListener {
        fun onProjectClick(item: ProjectItem, position: Int)

        fun onProjectMenuClick(view: View, item: ProjectItem, position: Int)

        fun onProjectLongClick(item: ProjectItem, position: Int)
    }
}
