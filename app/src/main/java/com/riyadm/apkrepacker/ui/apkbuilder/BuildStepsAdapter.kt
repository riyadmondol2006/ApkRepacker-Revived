package com.riyadm.apkrepacker.ui.apkbuilder

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.motion.springIn
import com.google.android.material.R as MaterialR

enum class BuildStepState { Pending, Running, Done }

/** One line of the build step list; [description] is null until the build reports it. */
data class BuildStepRow(val number: Int, val description: String?, val state: BuildStepState)

/**
 * The build step list: pending steps are rings, the running one has a progress indicator, done
 * steps get a check that springs in.
 */
class BuildStepsAdapter : ListAdapter<BuildStepRow, BuildStepsAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.step_icon)
        val running: CircularProgressIndicator = view.findViewById(R.id.step_running)
        val text: TextView = view.findViewById(R.id.step_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_build_step, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = bind(holder, getItem(position), animate = false)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        bind(holder, getItem(position), animate = payloads.isNotEmpty())
    }

    private fun bind(holder: Holder, row: BuildStepRow, animate: Boolean) {
        val view = holder.itemView
        val context = view.context
        holder.text.text = row.description ?: context.getString(R.string.m3d_step_n, row.number)
        val stateDescription = when (row.state) {
            BuildStepState.Pending -> R.string.m3d_step_pending
            BuildStepState.Running -> R.string.m3d_step_running
            BuildStepState.Done -> R.string.m3d_step_done
        }
        view.contentDescription = "${holder.text.text}, ${context.getString(stateDescription)}"

        when (row.state) {
            BuildStepState.Pending -> {
                holder.running.visibility = View.GONE
                holder.icon.visibility = View.VISIBLE
                holder.icon.setImageResource(R.drawable.ic_m3_radio_unchecked)
                holder.icon.imageTintList = tint(view, MaterialR.attr.colorOutline)
                holder.icon.scaleX = 1f
                holder.icon.scaleY = 1f
                holder.icon.alpha = 1f
                holder.text.setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
                holder.text.setTextColor(MaterialColors.getColor(view, MaterialR.attr.colorOnSurfaceVariant))
            }
            BuildStepState.Running -> {
                holder.icon.visibility = View.GONE
                holder.running.visibility = View.VISIBLE
                holder.text.setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge_Emphasized)
                holder.text.setTextColor(MaterialColors.getColor(view, MaterialR.attr.colorOnSurface))
                if (animate) holder.running.springIn(fromScale = 0.5f)
            }
            BuildStepState.Done -> {
                holder.running.visibility = View.GONE
                holder.icon.visibility = View.VISIBLE
                holder.icon.setImageResource(R.drawable.ic_m3_check_circle)
                holder.icon.imageTintList = tint(view, R.attr.colorPrimary)
                holder.text.setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
                holder.text.setTextColor(MaterialColors.getColor(view, MaterialR.attr.colorOnSurface))
                if (animate) {
                    holder.icon.springIn(fromScale = 0.3f)
                } else {
                    holder.icon.scaleX = 1f
                    holder.icon.scaleY = 1f
                    holder.icon.alpha = 1f
                }
            }
        }
    }

    private fun tint(view: View, attr: Int) =
        ColorStateList.valueOf(MaterialColors.getColor(view, attr))

    private object Diff : DiffUtil.ItemCallback<BuildStepRow>() {
        override fun areItemsTheSame(oldItem: BuildStepRow, newItem: BuildStepRow) = oldItem.number == newItem.number

        override fun areContentsTheSame(oldItem: BuildStepRow, newItem: BuildStepRow) = oldItem == newItem

        override fun getChangePayload(oldItem: BuildStepRow, newItem: BuildStepRow): Any? =
            if (oldItem.state != newItem.state) STATE_CHANGED else null
    }

    private companion object {
        const val STATE_CHANGED = "state"
    }
}
