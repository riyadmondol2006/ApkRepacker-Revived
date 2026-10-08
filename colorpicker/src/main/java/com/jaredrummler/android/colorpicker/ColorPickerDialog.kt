package com.jaredrummler.android.colorpicker

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.MaterialFadeThrough
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Material 3 colour picker dialog.
 *
 * Two panes: a tonal presets grid with a connected shade strip, and a custom HSV/opacity slider pane with a hex field.
 * Based on jaredrummler/ColorPicker (Apache 2.0).
 */
class ColorPickerDialog : DialogFragment(), ColorPickerView.OnColorChangedListener {

    private var colorPickerDialogListener: ColorPickerDialogListener? = null

    @ColorInt
    private var color = Color.BLACK
    private var dialogType = TYPE_PRESETS
    private var dialogId = 0
    private var showColorShades = true
    private var showAlphaSlider = false
    private var colorShape = ColorShape.CIRCLE

    private var pane: FrameLayout? = null
    private var modeGroup: MaterialButtonToggleGroup? = null

    // presets pane
    private var presets = IntArray(0)
    private var adapter: ColorPaletteAdapter? = null
    private var shadesLayout: LinearLayout? = null
    private var transparencyText: TextView? = null

    // custom pane
    private var colorPicker: ColorPickerView? = null
    private var newColorPanel: ColorPanelView? = null
    private var hexEditText: TextInputEditText? = null
    private var fromEditText = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        dialogId = args.getInt(ARG_ID)
        showAlphaSlider = args.getBoolean(ARG_ALPHA)
        showColorShades = args.getBoolean(ARG_SHOW_COLOR_SHADES)
        colorShape = args.getInt(ARG_COLOR_SHAPE)
        if (savedInstanceState == null) {
            color = args.getInt(ARG_COLOR)
            dialogType = args.getInt(ARG_TYPE)
        } else {
            color = savedInstanceState.getInt(ARG_COLOR)
            dialogType = savedInstanceState.getInt(ARG_TYPE)
        }

        val allowPresets = args.getBoolean(ARG_ALLOW_PRESETS)
        val allowCustom = args.getBoolean(ARG_ALLOW_CUSTOM)
        if (dialogType == TYPE_PRESETS && !allowPresets && allowCustom) dialogType = TYPE_CUSTOM
        if (dialogType == TYPE_CUSTOM && !allowCustom && allowPresets) dialogType = TYPE_PRESETS

        val builder = MaterialAlertDialogBuilder(requireActivity(), args.getInt(ARG_DIALOG_THEME))
        val content = LayoutInflater.from(builder.context).inflate(R.layout.cpv_dialog_content, null)
        pane = content.findViewById(R.id.cpv_pane)
        modeGroup = content.findViewById(R.id.cpv_mode_group)
        setupModeSwitch(content, allowPresets && allowCustom, args)

        val selectedText = args.getInt(ARG_SELECTED_BUTTON_TEXT).takeIf { it != 0 } ?: R.string.cpv_select
        builder.setView(content)
            .setPositiveButton(selectedText) { _, _ -> notifyColorSelected(color) }
            .setNegativeButton(android.R.string.cancel, null)

        args.getString(ARG_DIALOG_TITLE_STRING)?.let { builder.setTitle(it) }
            ?: args.getInt(ARG_DIALOG_TITLE).takeIf { it != 0 }?.let { builder.setTitle(it) }

        showPane(dialogType, animate = false)
        return builder.create()
    }

    private fun setupModeSwitch(content: View, enabled: Boolean, args: Bundle) {
        val group = content.findViewById<MaterialButtonToggleGroup>(R.id.cpv_mode_group)
        group.visibility = if (enabled) View.VISIBLE else View.GONE
        args.getInt(ARG_PRESETS_BUTTON_TEXT).takeIf { it != 0 }
            ?.let { content.findViewById<TextView>(R.id.cpv_mode_presets).setText(it) }
        args.getInt(ARG_CUSTOM_BUTTON_TEXT).takeIf { it != 0 }
            ?.let { content.findViewById<TextView>(R.id.cpv_mode_custom).setText(it) }
        group.check(if (dialogType == TYPE_PRESETS) R.id.cpv_mode_presets else R.id.cpv_mode_custom)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.cpv_mode_presets) TYPE_PRESETS else TYPE_CUSTOM
            if (type != dialogType) showPane(type, animate = true)
        }
    }

    private fun showPane(type: Int, animate: Boolean) {
        val container = pane ?: return
        dialogType = type
        if (animate) TransitionManager.beginDelayedTransition(container, MaterialFadeThrough())
        container.removeAllViews()
        container.addView(if (type == TYPE_PRESETS) createPresetsView(container) else createPickerView(container))
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val args = requireArguments()
        val params = window.attributes
        if (args.getBoolean(ARG_DIALOG_BOTTOM)) {
            params.horizontalMargin = 0f
            params.y = BOTTOM_OFFSET_PX
            params.gravity = Gravity.BOTTOM
        } else {
            params.gravity = Gravity.CENTER
        }
        window.attributes = params

        val widthFraction = args.getFloat(ARG_DIALOG_WIDTH)
        if (widthFraction != 0f) {
            val maxWidth = resources.getDimensionPixelSize(R.dimen.cpv_dialog_max_width)
            val width = min((resources.displayMetrics.widthPixels * widthFraction).toInt(), maxWidth)
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        notifyDialogDismissed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(ARG_COLOR, color)
        outState.putInt(ARG_TYPE, dialogType)
        super.onSaveInstanceState(outState)
    }

    /**
     * Sets the callback invoked when a colour is selected or the dialog is dismissed. Without it the parent fragment or
     * the host activity is used if it implements [ColorPickerDialogListener].
     */
    fun setColorPickerDialogListener(colorPickerDialogListener: ColorPickerDialogListener?) {
        this.colorPickerDialogListener = colorPickerDialogListener
    }

    // region Custom picker

    private fun createPickerView(parent: ViewGroup): View {
        val content = LayoutInflater.from(parent.context).inflate(R.layout.cpv_dialog_color_picker, parent, false)
        val picker = content.findViewById<ColorPickerView>(R.id.cpv_color_picker_view)
        val oldPanel = content.findViewById<ColorPanelView>(R.id.cpv_color_panel_old)
        val newPanel = content.findViewById<ColorPanelView>(R.id.cpv_color_panel_new)
        val hex = content.findViewById<TextInputEditText>(R.id.cpv_hex)
        colorPicker = picker
        newColorPanel = newPanel
        hexEditText = hex

        oldPanel.setShape(colorShape)
        newPanel.setShape(colorShape)
        picker.setAlphaSliderVisible(showAlphaSlider)
        oldPanel.setColor(requireArguments().getInt(ARG_COLOR))
        picker.setColor(color, true)
        newPanel.setColor(color)
        picker.setOnColorChangedListener(this)
        picker.setOnInteractionListener { newPanel.setMorphed(it) }

        setHex(color)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!hex.isFocused) return
                val parsed = parseColorString(s?.toString().orEmpty())
                if (parsed != null && parsed != picker.getColor()) {
                    fromEditText = true
                    picker.setColor(parsed, true)
                }
            }
        })
        hex.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                view.clearFocus()
                requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
                true
            } else {
                false
            }
        }
        return content
    }

    override fun onColorChanged(newColor: Int) {
        color = newColor
        newColorPanel?.setColor(newColor)
        if (!fromEditText) {
            setHex(newColor)
            hexEditText?.takeIf { it.hasFocus() }?.let {
                requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(it.windowToken, 0)
                it.clearFocus()
            }
        }
        fromEditText = false
    }

    private fun setHex(color: Int) {
        val text = if (showAlphaSlider) String.format(Locale.ROOT, "%08X", color) else String.format(Locale.ROOT, "%06X", 0xFFFFFF and color)
        hexEditText?.setText(text)
    }

    /** Accepts 0-8 hex digits the way the original picker did (short forms are padded), or null when unparseable. */
    private fun parseColorString(input: String): Int? {
        val hex = input.removePrefix("#")
        fun part(from: Int, to: Int) = hex.substring(from, to).toInt(16)
        return try {
            when (hex.length) {
                0 -> Color.argb(255, 0, 0, 0)
                1, 2 -> Color.argb(255, 0, 0, hex.toInt(16))
                3 -> Color.argb(255, part(0, 1), part(1, 2), part(2, 3))
                4 -> Color.argb(255, 0, part(0, 2), part(2, 4))
                5 -> Color.argb(255, part(0, 1), part(1, 3), part(3, 5))
                6 -> Color.argb(255, part(0, 2), part(2, 4), part(4, 6))
                7 -> Color.argb(part(0, 1), part(1, 3), part(3, 5), part(5, 7))
                8 -> Color.argb(part(0, 2), part(2, 4), part(4, 6), part(6, 8))
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    // endregion

    // region Presets

    private fun createPresetsView(parent: ViewGroup): View {
        val content = LayoutInflater.from(parent.context).inflate(R.layout.cpv_dialog_presets, parent, false)
        shadesLayout = content.findViewById(R.id.cpv_shades)
        transparencyText = content.findViewById(R.id.cpv_transparency_text)
        val grid = content.findViewById<RecyclerView>(R.id.cpv_grid)

        loadPresets()

        if (showColorShades) {
            createColorShades(color)
        } else {
            shadesLayout?.visibility = View.GONE
            content.findViewById<View>(R.id.cpv_shades_title).visibility = View.GONE
        }

        val paletteAdapter = ColorPaletteAdapter(
            onColorSelected = { newColor ->
                if (color == newColor) {
                    confirmAndDismiss()
                } else {
                    color = newColor
                    if (showColorShades) createColorShades(color)
                }
            },
            colors = presets,
            selectedPosition = presets.indexOf(color),
            colorShape = colorShape
        )
        adapter = paletteAdapter

        val layoutManager = GridLayoutManager(requireContext(), DEFAULT_SPAN)
        grid.layoutManager = layoutManager
        grid.adapter = paletteAdapter
        grid.itemAnimator = null
        val cell = resources.getDimensionPixelSize(R.dimen.cpv_swatch_cell)
        grid.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
            val span = max(MIN_SPAN, (right - left - v.paddingLeft - v.paddingRight) / cell)
            if (layoutManager.spanCount != span) layoutManager.spanCount = span
        }

        val transparencyLayout = content.findViewById<View>(R.id.cpv_transparency_layout)
        if (showAlphaSlider) {
            setupTransparency(content.findViewById(R.id.cpv_transparency_slider))
        } else {
            transparencyLayout.visibility = View.GONE
        }
        return content
    }

    private fun confirmAndDismiss() {
        notifyColorSelected(color)
        dismiss()
    }

    private fun loadPresets() {
        val alpha = Color.alpha(color)
        val source = requireArguments().getIntArray(ARG_PRESETS) ?: MATERIAL_COLORS
        val isMaterialColors = source.contentEquals(MATERIAL_COLORS)
        var list = if (alpha != 255) {
            // keep the current opacity on every preset without touching the caller's array
            IntArray(source.size) { Color.argb(alpha, Color.red(source[it]), Color.green(source[it]), Color.blue(source[it])) }
        } else {
            source.copyOf()
        }
        if (color !in list) list = intArrayOf(color) + list
        if (isMaterialColors && list.size == MATERIAL_COLORS.size + 1) {
            val black = Color.argb(alpha, 0, 0, 0)
            if (black !in list) list += black
        }
        presets = list
    }

    private fun createColorShades(@ColorInt base: Int) {
        val strip = shadesLayout ?: return
        val shades = shadeColors(base)
        if (strip.childCount == 0) {
            strip.clipToOutline = true
            val corner = resources.getDimension(R.dimen.cpv_shade_corner)
            strip.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, corner)
                }
            }
            shades.forEach { shade ->
                val panel = ColorPanelView(strip.context).apply {
                    isFlat = true
                    setShape(ColorShape.SQUARE)
                    setColor(shade)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener { onShadeClicked(this) }
                }
                strip.addView(panel)
            }
        } else {
            for (i in 0 until strip.childCount) {
                (strip.getChildAt(i) as ColorPanelView).apply {
                    setColor(shades[i])
                    isChecked = false
                }
            }
        }
    }

    private fun onShadeClicked(panel: ColorPanelView) {
        if (panel.isChecked) {
            confirmAndDismiss()
            return
        }
        color = panel.getColor()
        adapter?.selectNone()
        val strip = shadesLayout ?: return
        for (i in 0 until strip.childCount) {
            val other = strip.getChildAt(i) as ColorPanelView
            other.isChecked = other === panel
        }
    }

    private fun setupTransparency(slider: Slider) {
        val transparency = 255 - Color.alpha(color)
        slider.value = transparency.toFloat()
        transparencyText?.text = percent(transparency)
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val transparencyNow = value.roundToInt()
            transparencyText?.text = percent(transparencyNow)
            val alpha = 255 - transparencyNow
            adapter?.setAlpha(alpha)
            val strip = shadesLayout
            if (strip != null) {
                for (i in 0 until strip.childCount) {
                    val panel = strip.getChildAt(i) as ColorPanelView
                    val shade = panel.getColor()
                    panel.setColor(Color.argb(alpha, Color.red(shade), Color.green(shade), Color.blue(shade)))
                }
            }
            color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    private fun percent(transparency: Int) = String.format(Locale.ROOT, "%d%%", (transparency * 100.0 / 255).toInt())

    private fun shadeColor(@ColorInt base: Int, percent: Double): Int {
        val target = if (percent < 0) 0.0 else 255.0
        val p = if (percent < 0) -percent else percent
        fun channel(value: Int) = (Math.round((target - value) * p) + value).toInt()
        return Color.argb(Color.alpha(base), channel(Color.red(base)), channel(Color.green(base)), channel(Color.blue(base)))
    }

    private fun shadeColors(@ColorInt base: Int): IntArray = SHADE_STEPS.map { shadeColor(base, it) }.toIntArray()

    // endregion

    // region Callbacks

    private fun resolveListener(): ColorPickerDialogListener? =
        colorPickerDialogListener
            ?: parentFragment as? ColorPickerDialogListener
            ?: activity as? ColorPickerDialogListener

    private fun notifyColorSelected(color: Int) {
        resolveListener()?.onColorSelected(dialogId, color)
            ?: Log.w(TAG, "No ColorPickerDialogListener: set one, or implement it in the parent fragment or activity")
    }

    private fun notifyDialogDismissed() {
        resolveListener()?.onDialogDismissed(dialogId)
    }

    // endregion

    class Builder internal constructor() {
        @StringRes
        private var dialogTitle = R.string.cpv_default_title
        private var dialogTitleString: String? = null

        @StringRes
        private var presetsButtonText = R.string.cpv_presets

        @StringRes
        private var customButtonText = R.string.cpv_custom

        @StringRes
        private var selectedButtonText = R.string.cpv_select

        @DialogType
        private var dialogType = TYPE_PRESETS
        private var presets = MATERIAL_COLORS

        @ColorInt
        private var color = Color.BLACK
        private var dialogId = 0
        private var dialogTheme = 0
        private var dialogWidth = 0.8f
        private var dialogBottom = false
        private var showAlphaSlider = false
        private var allowPresets = true
        private var allowCustom = true
        private var showColorShades = true

        @ColorShape.Type
        private var colorShape = ColorShape.CIRCLE

        fun setDialogTitle(@StringRes dialogTitle: Int) = apply { this.dialogTitle = dialogTitle }
        fun setDialogTitle(dialogTitleString: String?) = apply { this.dialogTitleString = dialogTitleString }
        fun setSelectedButtonText(@StringRes selectedButtonText: Int) = apply { this.selectedButtonText = selectedButtonText }
        fun setPresetsButtonText(@StringRes presetsButtonText: Int) = apply { this.presetsButtonText = presetsButtonText }
        fun setCustomButtonText(@StringRes customButtonText: Int) = apply { this.customButtonText = customButtonText }
        fun setDialogType(@DialogType dialogType: Int) = apply { this.dialogType = dialogType }
        fun setPresets(presets: IntArray) = apply { this.presets = presets }
        fun setColor(@ColorInt color: Int) = apply { this.color = color }
        fun setDialogId(dialogId: Int) = apply { this.dialogId = dialogId }
        fun setShowAlphaSlider(showAlphaSlider: Boolean) = apply { this.showAlphaSlider = showAlphaSlider }
        fun setAllowPresets(allowPresets: Boolean) = apply { this.allowPresets = allowPresets }
        fun setAllowCustom(allowCustom: Boolean) = apply { this.allowCustom = allowCustom }
        fun setShowColorShades(showColorShades: Boolean) = apply { this.showColorShades = showColorShades }
        fun setColorShape(@ColorShape.Type colorShape: Int) = apply { this.colorShape = colorShape }

        /** Dialog width as a fraction of the screen width (capped at the Material dialog maximum); 0 keeps the default. */
        fun setDialogWidth(dialogWidth: Float) = apply { this.dialogWidth = dialogWidth }
        fun setDialogBottom(dialogBottom: Boolean) = apply { this.dialogBottom = dialogBottom }

        /** A theme overlay such as [R.style.ColorPickerDialog] applied on top of the activity theme. */
        fun setDialogTheme(style: Int) = apply { this.dialogTheme = style }

        fun create(): ColorPickerDialog = ColorPickerDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_ID, dialogId)
                putInt(ARG_TYPE, dialogType)
                putInt(ARG_COLOR, color)
                putIntArray(ARG_PRESETS, presets)
                putBoolean(ARG_ALPHA, showAlphaSlider)
                putBoolean(ARG_ALLOW_CUSTOM, allowCustom)
                putBoolean(ARG_ALLOW_PRESETS, allowPresets)
                putInt(ARG_DIALOG_TITLE, dialogTitle)
                putString(ARG_DIALOG_TITLE_STRING, dialogTitleString)
                putBoolean(ARG_DIALOG_BOTTOM, dialogBottom)
                putFloat(ARG_DIALOG_WIDTH, dialogWidth)
                putInt(ARG_DIALOG_THEME, dialogTheme)
                putBoolean(ARG_SHOW_COLOR_SHADES, showColorShades)
                putInt(ARG_COLOR_SHAPE, colorShape)
                putInt(ARG_PRESETS_BUTTON_TEXT, presetsButtonText)
                putInt(ARG_CUSTOM_BUTTON_TEXT, customButtonText)
                putInt(ARG_SELECTED_BUTTON_TEXT, selectedButtonText)
            }
        }

        fun show(activity: FragmentActivity) {
            create().show(activity.supportFragmentManager, "color-picker-dialog")
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_CUSTOM, TYPE_PRESETS)
    annotation class DialogType

    companion object {
        private const val TAG = "ColorPickerDialog"

        const val TYPE_CUSTOM = 0
        const val TYPE_PRESETS = 1

        /** Material design colours used as the default presets. */
        @JvmField
        val MATERIAL_COLORS = intArrayOf(
            0xFFF44336.toInt(), // RED 500
            0xFFE91E63.toInt(), // PINK 500
            0xFFFF2C93.toInt(), // LIGHT PINK 500
            0xFF9C27B0.toInt(), // PURPLE 500
            0xFF673AB7.toInt(), // DEEP PURPLE 500
            0xFF3F51B5.toInt(), // INDIGO 500
            0xFF2196F3.toInt(), // BLUE 500
            0xFF03A9F4.toInt(), // LIGHT BLUE 500
            0xFF00BCD4.toInt(), // CYAN 500
            0xFF009688.toInt(), // TEAL 500
            0xFF4CAF50.toInt(), // GREEN 500
            0xFF8BC34A.toInt(), // LIGHT GREEN 500
            0xFFCDDC39.toInt(), // LIME 500
            0xFFFFEB3B.toInt(), // YELLOW 500
            0xFFFFC107.toInt(), // AMBER 500
            0xFFFF9800.toInt(), // ORANGE 500
            0xFF795548.toInt(), // BROWN 500
            0xFF607D8B.toInt(), // BLUE GREY 500
            0xFF9E9E9E.toInt() // GREY 500
        )

        private const val ARG_ID = "id"
        private const val ARG_TYPE = "dialogType"
        private const val ARG_COLOR = "color"
        private const val ARG_ALPHA = "alpha"
        private const val ARG_PRESETS = "presets"
        private const val ARG_ALLOW_PRESETS = "allowPresets"
        private const val ARG_ALLOW_CUSTOM = "allowCustom"
        private const val ARG_DIALOG_TITLE = "dialogTitle"
        private const val ARG_DIALOG_TITLE_STRING = "dialogTitleString"
        private const val ARG_DIALOG_BOTTOM = "dialogBottom"
        private const val ARG_DIALOG_WIDTH = "dialogWidth"
        private const val ARG_DIALOG_THEME = "dialogTheme"
        private const val ARG_SHOW_COLOR_SHADES = "showColorShades"
        private const val ARG_COLOR_SHAPE = "colorShape"
        private const val ARG_PRESETS_BUTTON_TEXT = "presetsButtonText"
        private const val ARG_CUSTOM_BUTTON_TEXT = "customButtonText"
        private const val ARG_SELECTED_BUTTON_TEXT = "selectedButtonText"

        private const val DEFAULT_SPAN = 6
        private const val MIN_SPAN = 3
        private const val BOTTOM_OFFSET_PX = 24

        /** Lightening (positive) and darkening (negative) steps for the tonal strip. */
        private val SHADE_STEPS = doubleArrayOf(0.9, 0.7, 0.5, 0.333, 0.166, -0.125, -0.25, -0.375, -0.5, -0.675, -0.7, -0.775)

        @JvmStatic
        fun newBuilder(): Builder = Builder()
    }
}
