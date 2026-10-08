package com.riyadm.apkrepacker.fragment.dialogs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetAddNewColorBinding
import com.riyadm.apkrepacker.ui.colorslist.SwatchDrawable
import com.riyadm.apkrepacker.ui.resourceeditor.findListener

/** Minimal "name + pick a color" sheet; reports to the parent fragment or host activity. */
class AddColorDialogFragment : BottomSheetDialogFragment(), ColorPickerDialogListener {

    private var binding: BottomSheetAddNewColorBinding? = null
    private val swatch by lazy { SwatchDrawable.create(requireView()) }
    private var colorValue = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetAddNewColorBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        colorValue = savedInstanceState?.getInt(STATE_COLOR) ?: 0
        views.colorPreview.background = swatch
        swatch.color = colorValue
        views.colorName.doAfterTextChanged { views.colorNameLayout.error = null }
        views.selectColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setDialogType(ColorPickerDialog.TYPE_PRESETS)
                .setColorShape(ColorShape.CIRCLE)
                .setPresets(ColorPickerDialog.MATERIAL_COLORS)
                .setAllowPresets(true)
                .setAllowCustom(true)
                .setShowAlphaSlider(true)
                .setShowColorShades(true)
                .setColor(if (colorValue == 0) Color.BLACK else colorValue)
                .setDialogWidth(0.95f)
                .create()
                .show(childFragmentManager, "color_picker_dialog")
        }
        views.addColor.setOnClickListener {
            val name = views.colorName.text?.toString()?.trim().orEmpty()
            when {
                name.isEmpty() -> views.colorNameLayout.error = getString(R.string.enter_color_name)
                colorValue == 0 -> Snackbar.make(view, R.string.h_color_pick, Snackbar.LENGTH_SHORT).show()
                else -> {
                    findListener<ItemClickListener>()?.onAddColorClick(name, colorValue)
                    dismiss()
                }
            }
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        colorValue = color
        swatch.color = color
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, colorValue)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun interface ItemClickListener {
        fun onAddColorClick(colorName: String, colorValue: Int)
    }

    companion object {
        const val TAG = "AddColorDialogFragment"
        private const val STATE_COLOR = "state_color"

        @JvmStatic
        fun newInstance() = AddColorDialogFragment()
    }
}
