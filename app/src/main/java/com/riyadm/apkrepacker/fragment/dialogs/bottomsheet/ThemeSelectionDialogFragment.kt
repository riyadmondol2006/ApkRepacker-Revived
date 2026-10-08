package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.adapter.ThemeAdapter
import com.riyadm.apkrepacker.databinding.BottomSheetDialogThemeSelectionBinding
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.Theme

/**
 * Sheet with a live preview of every available theme: Light, Dark, Rena Light, Rena and, on
 * Android 12+, Material You Light / Dark. Both light and dark themes are always selectable.
 */
class ThemeSelectionDialogFragment : BottomSheetDialogFragment(), ThemeAdapter.OnThemeInteractionListener {

    @IntDef(flag = true, value = [MODE_APPLY, MODE_CHOOSE])
    annotation class Mode

    private var mode = MODE_APPLY
    private var binding: BottomSheetDialogThemeSelectionBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getInt(EXTRA_MODE, MODE_APPLY) ?: MODE_APPLY
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetDialogThemeSelectionBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val themeManager = Theme.getInstance(requireContext())
        val columns = if (resources.configuration.screenWidthDp >= WIDE_SCREEN_DP) 3 else 2

        val adapter = ThemeAdapter(requireContext()).apply {
            setThemes(themeManager.themes)
            setOnThemeInteractionListener(this@ThemeSelectionDialogFragment)
            selected = currentSelection(themeManager)
        }
        binding?.recyclerThemes?.apply {
            layoutManager = GridLayoutManager(requireContext(), columns)
            this.adapter = adapter
            applyExpressiveMotion()
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val owner = FragmentUtils.getParentAs(this, OnThemeDialogDismissListener::class.java)
        tag?.let { owner?.onThemeDialogDismissed(it) }
    }

    /** MODE_APPLY edits the single app theme; MODE_CHOOSE highlights the theme passed to [newInstance], if any. */
    private fun currentSelection(themeManager: Theme): Theme.ThemeDescriptor? {
        val preselected = arguments?.getInt(EXTRA_SELECTED_ID, NO_THEME) ?: NO_THEME
        return when {
            preselected != NO_THEME -> themeManager.themes.firstOrNull { it.id == preselected }
            mode == MODE_APPLY -> themeManager.concreteTheme
            else -> null
        }
    }

    override fun onThemeClicked(theme: Theme.ThemeDescriptor?) {
        when (mode) {
            MODE_APPLY -> {
                Theme.getInstance(context).concreteTheme = checkNotNull(theme)
                dismiss()
            }
            MODE_CHOOSE -> {
                FragmentUtils.getParentAs(this, OnThemeChosenListener::class.java)?.onThemeChosen(tag, theme)
                dismiss()
            }
            else -> error("Unknown mode")
        }
    }

    fun interface OnThemeChosenListener {
        fun onThemeChosen(tag: String?, theme: Theme.ThemeDescriptor?)
    }

    fun interface OnThemeDialogDismissListener {
        fun onThemeDialogDismissed(tag: String)
    }

    companion object {
        const val MODE_APPLY = 0
        const val MODE_CHOOSE = 1

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SELECTED_ID = "selected_id"
        private const val NO_THEME = -1
        private const val WIDE_SCREEN_DP = 600

        /** Same as [newInstance] with [MODE_APPLY]. */
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun newInstance(context: Context?): ThemeSelectionDialogFragment = newInstance(MODE_APPLY)

        @JvmStatic
        fun newInstance(@Mode mode: Int): ThemeSelectionDialogFragment =
            ThemeSelectionDialogFragment().apply {
                arguments = Bundle().apply { putInt(EXTRA_MODE, mode) }
            }

        /** Picker that opens with [selectedThemeId] highlighted. */
        @JvmStatic
        fun newInstance(@Mode mode: Int, selectedThemeId: Int): ThemeSelectionDialogFragment =
            ThemeSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(EXTRA_MODE, mode)
                    putInt(EXTRA_SELECTED_ID, selectedThemeId)
                }
            }
    }
}
