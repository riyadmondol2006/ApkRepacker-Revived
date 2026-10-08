package com.jaredrummler.android.colorpicker

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/** Grid of colour swatches; the selected one morphs and shows a check mark. */
internal class ColorPaletteAdapter(
    private val onColorSelected: (Int) -> Unit,
    var colors: IntArray,
    selectedPosition: Int,
    @ColorShape.Type private val colorShape: Int
) : RecyclerView.Adapter<ColorPaletteAdapter.SwatchHolder>() {

    var selectedPosition = selectedPosition
        private set

    private val popped = HashSet<Int>()

    fun selectNone() {
        val old = selectedPosition
        selectedPosition = -1
        if (old in colors.indices) notifyItemChanged(old)
    }

    /** Re-applies [alpha] to every swatch. */
    fun setAlpha(alpha: Int) {
        colors = IntArray(colors.size) { Color.argb(alpha, Color.red(colors[it]), Color.green(colors[it]), Color.blue(colors[it])) }
        notifyItemRangeChanged(0, colors.size, PAYLOAD_COLOR)
    }

    override fun getItemCount(): Int = colors.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwatchHolder {
        val context = parent.context
        val cell = context.resources.getDimensionPixelSize(R.dimen.cpv_swatch_cell)
        val size = context.resources.getDimensionPixelSize(R.dimen.cpv_swatch_size)
        val panel = ColorPanelView(context).apply {
            setShape(colorShape)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            isClickable = true
            isFocusable = true
        }
        val root = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell)
            addView(panel)
        }
        return SwatchHolder(root, panel)
    }

    override fun onBindViewHolder(holder: SwatchHolder, position: Int) {
        holder.panel.setColor(colors[position])
        holder.panel.isChecked = position == selectedPosition
        holder.panel.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selectedPosition != adapterPosition) {
                val old = selectedPosition
                selectedPosition = adapterPosition
                if (old in colors.indices) notifyItemChanged(old)
                notifyItemChanged(adapterPosition)
            }
            onColorSelected(colors[adapterPosition])
        }
    }

    override fun onBindViewHolder(holder: SwatchHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_COLOR)) {
            holder.panel.setColor(colors[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewAttachedToWindow(holder: SwatchHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION || !popped.add(position)) return
        val view = holder.panel
        view.scaleX = 0.4f
        view.scaleY = 0.4f
        view.alpha = 0f
        view.postDelayed({
            if (!view.isAttachedToWindow) return@postDelayed
            spring(view, DynamicAnimation.SCALE_X, 1f)
            spring(view, DynamicAnimation.SCALE_Y, 1f)
            spring(view, DynamicAnimation.ALPHA, 1f)
        }, position * STAGGER_MS)
    }

    override fun onViewDetachedFromWindow(holder: SwatchHolder) {
        holder.panel.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    private fun spring(view: View, property: DynamicAnimation.ViewProperty, target: Float) {
        SpringAnimation(view, property).apply {
            spring = SpringForce(target).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.6f)
            setMinimumVisibleChange(0.002f)
            start()
        }
    }

    class SwatchHolder(root: View, val panel: ColorPanelView) : RecyclerView.ViewHolder(root)

    private companion object {
        const val PAYLOAD_COLOR = "color"
        const val STAGGER_MS = 14L
    }
}
