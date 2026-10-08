package com.jaredrummler.android.colorpicker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.res.use
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Lets the user pick a colour with Material sliders for hue, saturation, brightness and (optionally) opacity.
 * Each slider rides on a live gradient showing what the control will produce.
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    fun interface OnColorChangedListener {
        fun onColorChanged(newColor: Int)
    }

    private var opacity = 0xFF
    private var hue = 360f
    private var sat = 0f
    private var value = 0f

    private var showAlphaPanel = false
    private var alphaSliderText: String? = null
    private var sliderTrackerColor: Int? = null
    private var borderColor: Int? = null

    private var listener: OnColorChangedListener? = null
    private var interactionListener: ((Boolean) -> Unit)? = null
    private var updating = false

    private val hueRow: Row
    private val satRow: Row
    private val valueRow: Row
    private val alphaRow: Row

    init {
        orientation = VERTICAL
        // State is saved by this view itself; the inner sliders must not restore stale values over it.
        isSaveFromParentEnabled = false
        context.obtainStyledAttributes(attrs, R.styleable.ColorPickerView).use {
            showAlphaPanel = it.getBoolean(R.styleable.ColorPickerView_cpv_alphaChannelVisible, false)
            alphaSliderText = it.getString(R.styleable.ColorPickerView_cpv_alphaChannelText)
            if (it.hasValue(R.styleable.ColorPickerView_cpv_sliderColor)) {
                sliderTrackerColor = it.getColor(R.styleable.ColorPickerView_cpv_sliderColor, Color.TRANSPARENT)
            }
            if (it.hasValue(R.styleable.ColorPickerView_cpv_borderColor)) {
                borderColor = it.getColor(R.styleable.ColorPickerView_cpv_borderColor, Color.TRANSPARENT)
            }
        }
        LayoutInflater.from(context).inflate(R.layout.cpv_view_color_picker, this, true)

        hueRow = Row(findViewById(R.id.cpv_row_hue), R.string.cpv_hue, 0f, 360f, 0f, checker = false)
        satRow = Row(findViewById(R.id.cpv_row_saturation), R.string.cpv_saturation, 0f, 100f, 0f, checker = false)
        valueRow = Row(findViewById(R.id.cpv_row_brightness), R.string.cpv_brightness, 0f, 100f, 0f, checker = false)
        alphaRow = Row(findViewById(R.id.cpv_row_alpha), R.string.cpv_opacity, 0f, 255f, 1f, checker = true)

        hueRow.slider.addOnChangeListener { _, v, fromUser -> if (fromUser) onUserChange { hue = v } }
        satRow.slider.addOnChangeListener { _, v, fromUser -> if (fromUser) onUserChange { sat = v / 100f } }
        valueRow.slider.addOnChangeListener { _, v, fromUser -> if (fromUser) onUserChange { value = v / 100f } }
        alphaRow.slider.addOnChangeListener { _, v, fromUser -> if (fromUser) onUserChange { opacity = v.roundToInt() } }

        val touch = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                interactionListener?.invoke(true)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                interactionListener?.invoke(false)
            }
        }
        listOf(hueRow, satRow, valueRow, alphaRow).forEach { it.slider.addOnSliderTouchListener(touch) }

        applyAlphaVisibility()
        applyColors()
        refresh()
    }

    private inline fun onUserChange(change: () -> Unit) {
        if (updating) return
        change()
        refresh()
        listener?.onColorChanged(getColor())
    }

    private fun applyColors() {
        listOf(hueRow, satRow, valueRow, alphaRow).forEach { row ->
            sliderTrackerColor?.let { row.slider.thumbTintList = ColorStateList.valueOf(it) }
            borderColor?.let { row.slider.thumbStrokeColor = ColorStateList.valueOf(it) }
        }
        alphaRow.label.text = alphaSliderText ?: context.getString(R.string.cpv_opacity)
    }

    private fun applyAlphaVisibility() {
        alphaRow.root.visibility = if (showAlphaPanel) VISIBLE else GONE
    }

    /** Syncs sliders, value labels and gradients with the current HSV + alpha state. */
    private fun refresh() {
        updating = true
        val rgb = Color.HSVToColor(floatArrayOf(hue, sat, value))
        hueRow.set(hue, "${hue.roundToInt()}°")
        satRow.set(sat * 100f, "${(sat * 100f).roundToInt()}%")
        valueRow.set(value * 100f, "${(value * 100f).roundToInt()}%")
        alphaRow.set(opacity.toFloat(), "${(opacity * 100f / 255f).roundToInt()}%")

        hueRow.gradient(*HUE_STOPS)
        satRow.gradient(Color.HSVToColor(floatArrayOf(hue, 0f, value)), Color.HSVToColor(floatArrayOf(hue, 1f, value)))
        valueRow.gradient(Color.BLACK, Color.HSVToColor(floatArrayOf(hue, sat, 1f)))
        alphaRow.gradient(rgb and 0x00FFFFFF, rgb or 0xFF000000.toInt())
        updating = false
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("instanceState", super.onSaveInstanceState())
        putInt("alpha", opacity)
        putFloat("hue", hue)
        putFloat("sat", sat)
        putFloat("val", value)
        putBoolean("show_alpha", showAlphaPanel)
        putString("alpha_text", alphaSliderText)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val restored = if (state is Bundle) {
            opacity = state.getInt("alpha")
            hue = state.getFloat("hue")
            sat = state.getFloat("sat")
            value = state.getFloat("val")
            showAlphaPanel = state.getBoolean("show_alpha")
            alphaSliderText = state.getString("alpha_text")
            applyAlphaVisibility()
            applyColors()
            refresh()
            @Suppress("DEPRECATION")
            state.getParcelable("instanceState")
        } else {
            state
        }
        super.onRestoreInstanceState(restored)
    }

    fun setOnColorChangedListener(listener: OnColorChangedListener?) {
        this.listener = listener
    }

    /** Invoked with true when the user starts dragging a slider and false when they let go. */
    fun setOnInteractionListener(listener: ((Boolean) -> Unit)?) {
        interactionListener = listener
    }

    /** Current colour including alpha. */
    @ColorInt
    fun getColor(): Int = Color.HSVToColor(opacity, floatArrayOf(hue, sat, value))

    fun setColor(@ColorInt color: Int) = setColor(color, false)

    /** Sets the colour; if [callback] is true the colour listener is notified too. */
    fun setColor(@ColorInt color: Int, callback: Boolean) {
        val hsv = FloatArray(3)
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv)
        opacity = Color.alpha(color)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        refresh()
        if (callback) listener?.onColorChanged(getColor())
    }

    fun setAlphaSliderVisible(visible: Boolean) {
        if (showAlphaPanel != visible) {
            showAlphaPanel = visible
            applyAlphaVisibility()
        }
    }

    fun setSliderTrackerColor(@ColorInt color: Int) {
        sliderTrackerColor = color
        applyColors()
    }

    @ColorInt
    fun getSliderTrackerColor(): Int =
        sliderTrackerColor ?: alphaRow.slider.thumbTintList.defaultColor

    fun setBorderColor(@ColorInt color: Int) {
        borderColor = color
        applyColors()
    }

    @ColorInt
    fun getBorderColor(): Int =
        borderColor ?: alphaRow.slider.thumbStrokeColor?.defaultColor ?: Color.TRANSPARENT

    fun setAlphaSliderText(@StringRes res: Int) = setAlphaSliderText(context.getString(res))

    fun setAlphaSliderText(text: String?) {
        alphaSliderText = text
        applyColors()
    }

    fun getAlphaSliderText(): String? = alphaSliderText

    /** One labelled slider on top of a rounded gradient bar. */
    private class Row(
        val root: View,
        @StringRes labelRes: Int,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        checker: Boolean
    ) {
        val label: TextView = root.findViewById(R.id.cpv_row_label)
        private val valueText: TextView = root.findViewById(R.id.cpv_row_value)
        private val bar: View = root.findViewById(R.id.cpv_row_bar)
        val slider: Slider = root.findViewById(R.id.cpv_row_slider)
        private val gradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, 0))

        init {
            label.setText(labelRes)
            slider.valueFrom = valueFrom
            slider.valueTo = valueTo
            slider.stepSize = stepSize
            slider.value = valueFrom
            slider.contentDescription = root.context.getString(labelRes)
            bar.background = if (checker) {
                LayerDrawable(arrayOf(AlphaPatternDrawable(DrawingUtils.dpToPx(root.context, 6f).toInt()), gradient))
            } else {
                gradient
            }
            bar.clipToOutline = true
            bar.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                }
            }
            slider.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignBar() }
        }

        /** Makes the gradient span exactly the slider's track. */
        private fun alignBar() {
            val params = bar.layoutParams as FrameLayout.LayoutParams
            val side = slider.trackSidePadding
            val height = slider.trackHeight
            if (params.marginStart != side || params.marginEnd != side || params.height != height) {
                params.marginStart = side
                params.marginEnd = side
                params.height = height
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                bar.layoutParams = params
            }
        }

        fun set(position: Float, text: String) {
            val snapped = if (slider.stepSize > 0f) position.roundToInt().toFloat() else position
            slider.value = snapped.coerceIn(slider.valueFrom, slider.valueTo)
            valueText.text = text
        }

        fun gradient(vararg colors: Int) {
            gradient.colors = colors
        }
    }

    private companion object {
        val HUE_STOPS: IntArray = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
    }
}
