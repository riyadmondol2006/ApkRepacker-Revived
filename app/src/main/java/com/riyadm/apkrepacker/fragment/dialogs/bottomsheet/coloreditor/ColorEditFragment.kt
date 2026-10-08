package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.coloreditor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentColorEditorMainBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ColorOptionsDialogFragment

/** Inline (non-sheet) color form: name, pick, save, delete. Reports like [ColorOptionsDialogFragment]. */
class ColorEditFragment : Fragment() {

    private var binding: FragmentColorEditorMainBinding? = null

    private val isChange: Boolean
        get() = arguments?.getBoolean(ARG_CHANGE, false) ?: false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentColorEditorMainBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        views.colorName.hint = if (isChange) "" else getString(R.string.color_name_exapmle)
        if (savedInstanceState == null) views.colorName.setText(arguments?.getString(ARG_NAME))
        views.deleteColor.isVisible = isChange
        views.done.setOnClickListener {
            val name = views.colorName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                views.colorName.error = getString(R.string.enter_color_name)
            } else {
                listener()?.onColorSave(arguments?.getInt(ARG_POSITION, -1) ?: -1, name, formatted(), isChange)
            }
        }
        views.deleteColor.setOnClickListener { listener()?.onColorDelete(arguments?.getInt(ARG_POSITION, -1) ?: -1) }
    }

    private fun formatted() = String.format("#%08X", arguments?.getInt(ARG_COLOR) ?: 0)

    private fun listener() = (parentFragment as? ColorOptionsDialogFragment.ItemClickListener)
        ?: (activity as? ColorOptionsDialogFragment.ItemClickListener)

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ColorEditFragment"
        private const val ARG_NAME = "color_name"
        private const val ARG_COLOR = "color_value"
        private const val ARG_CHANGE = "color_change"
        private const val ARG_POSITION = "color_position"

        @JvmStatic
        fun newInstance(name: String?, value: Int, change: Boolean) = ColorEditFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_NAME, name)
                putInt(ARG_COLOR, value)
                putBoolean(ARG_CHANGE, change)
            }
        }
    }
}
