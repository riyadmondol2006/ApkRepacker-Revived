package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetColorOptionsBinding
import com.riyadm.apkrepacker.ide.editor.text.InputMethodManagerCompat
import com.riyadm.apkrepacker.ui.colorslist.SwatchDrawable
import com.riyadm.apkrepacker.ui.resourceeditor.findListener

/**
 * Add a color or edit/delete an existing one. Arguments: name, current ARGB, whether it edits an existing entry, the
 * list position it came from and its raw XML value (kept as-is when the color isn't touched, so `@color/x` references survive).
 */
class ColorOptionsDialogFragment : BottomSheetDialogFragment(), ColorPickerDialogListener {

    private var binding: BottomSheetColorOptionsBinding? = null
    private val swatch by lazy { SwatchDrawable.create(requireView()) }
    private var color = Color.BLACK
    private var colorTouched = false

    private val isChange: Boolean
        get() = arguments?.getBoolean(ARG_CHANGE, false) ?: false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetColorOptionsBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        val args = requireArguments()
        color = savedInstanceState?.getInt(STATE_COLOR) ?: args.getInt(ARG_COLOR, Color.BLACK)
        colorTouched = savedInstanceState?.getBoolean(STATE_TOUCHED) ?: false

        views.tvDialogTitle.setText(if (isChange) R.string.action_change_color else R.string.action_add_new_color)
        views.deleteColor.isVisible = isChange
        views.colorPreview.background = swatch
        swatch.color = color
        if (savedInstanceState == null) views.colorName.setText(args.getString(ARG_NAME))
        views.colorHex.setText(SwatchDrawable.toDisplayHex(color))

        views.colorHex.doAfterTextChanged { text ->
            val parsed = SwatchDrawable.parseHex(text?.toString().orEmpty())
            views.colorHexLayout.error = if (parsed == null) getString(R.string.h_color_hex_invalid) else null
            if (parsed != null && parsed != color) {
                color = parsed
                colorTouched = true
                swatch.color = parsed
            }
        }
        views.colorName.doAfterTextChanged { views.colorNameLayout.error = null }

        views.selectColor.setOnClickListener {
            InputMethodManagerCompat.hideSoftInput(views.selectColor)
            ColorPickerDialog.newBuilder()
                .setDialogTitle(views.colorName.text?.toString())
                .setDialogType(ColorPickerDialog.TYPE_PRESETS)
                .setColorShape(ColorShape.CIRCLE)
                .setPresets(ColorPickerDialog.MATERIAL_COLORS)
                .setAllowPresets(true)
                .setAllowCustom(true)
                .setShowAlphaSlider(true)
                .setShowColorShades(true)
                .setColor(color)
                .setDialogWidth(0.95f)
                .create()
                .show(childFragmentManager, "color_picker_dialog")
        }
        views.deleteColor.setOnClickListener {
            findListener<ItemClickListener>()?.onColorDelete(args.getInt(ARG_POSITION, -1))
            dismiss()
        }
        views.done.setOnClickListener { save(views) }
    }

    private fun save(views: BottomSheetColorOptionsBinding) {
        val name = views.colorName.text?.toString()?.trim().orEmpty()
        when {
            name.isEmpty() -> views.colorNameLayout.error = getString(R.string.enter_color_name)
            SwatchDrawable.parseHex(views.colorHex.text?.toString().orEmpty()) == null ->
                views.colorHexLayout.error = getString(R.string.h_color_hex_invalid)
            else -> {
                val raw = arguments?.getString(ARG_RAW)
                val stored = if (!colorTouched && raw != null) raw else SwatchDrawable.toStoredHex(color)
                findListener<ItemClickListener>()?.onColorSave(arguments?.getInt(ARG_POSITION, -1) ?: -1, name, stored, isChange)
                dismiss()
            }
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        binding?.colorHex?.setText(SwatchDrawable.toDisplayHex(color))
        this.color = color
        colorTouched = true
        swatch.color = color
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, color)
        outState.putBoolean(STATE_TOUCHED, colorTouched)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        findListener<ItemClickListener>()?.onColorSheetDismissed()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    interface ItemClickListener {
        /** [value] is the text to write into the file; [position] is -1 when adding. */
        fun onColorSave(position: Int, name: String, value: String, isChange: Boolean)

        fun onColorDelete(position: Int)

        fun onColorSheetDismissed() {}
    }

    companion object {
        const val TAG = "ColorOptionsDialogFragment"
        private const val ARG_NAME = "color_name"
        private const val ARG_COLOR = "color_value"
        private const val ARG_CHANGE = "color_change"
        private const val ARG_POSITION = "color_position"
        private const val ARG_RAW = "color_raw"
        private const val STATE_COLOR = "state_color"
        private const val STATE_TOUCHED = "state_touched"

        @JvmStatic
        fun newInstance(name: String?, value: Int, change: Boolean, position: Int = -1, rawValue: String? = null) =
            ColorOptionsDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putInt(ARG_COLOR, value)
                    putBoolean(ARG_CHANGE, change)
                    putInt(ARG_POSITION, position)
                    putString(ARG_RAW, rawValue)
                }
            }
    }
}
