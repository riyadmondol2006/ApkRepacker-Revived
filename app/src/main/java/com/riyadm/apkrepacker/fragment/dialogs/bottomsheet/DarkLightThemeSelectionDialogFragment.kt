package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetDialogDarkLightThemeSelectionBinding
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.Theme

/**
 * Auto theme: pick one theme to use while the system is light and one while it is dark.
 * Both slots show a live preview and open the full theme picker when tapped.
 */
class DarkLightThemeSelectionDialogFragment : BottomSheetDialogFragment(),
    ThemeSelectionDialogFragment.OnThemeChosenListener {

    @IntDef(flag = true, value = [MODE_APPLY, MODE_CHOOSE])
    annotation class Mode

    private val viewModel: DarkLightThemeSelectionViewModel by viewModels()
    private var mode = MODE_CHOOSE
    private var binding: BottomSheetDialogDarkLightThemeSelectionBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getInt(EXTRA_MODE, MODE_CHOOSE) ?: MODE_CHOOSE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetDialogDarkLightThemeSelectionBinding.inflate(inflater, container, false)
            .also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = checkNotNull(binding)

        ui.themeViewDlSelectionLight.setMessage(R.string.auto_theme_selection_hint)
        ui.themeViewDlSelectionDark.setMessage(R.string.auto_theme_selection_hint)

        ui.themeViewDlSelectionLight.setOnClickListener {
            pickTheme(TAG_CHOOSE_LIGHT_THEME, viewModel.getLightTheme().value)
        }
        ui.themeViewDlSelectionDark.setOnClickListener {
            pickTheme(TAG_CHOOSE_DARK_THEME, viewModel.getDarkTheme().value)
        }

        viewModel.getLightTheme().observe(viewLifecycleOwner) { ui.themeViewDlSelectionLight.setTheme(it) }
        viewModel.getDarkTheme().observe(viewLifecycleOwner) { ui.themeViewDlSelectionDark.setTheme(it) }

        ui.buttonDlCancel.setOnClickListener { dismiss() }
        ui.buttonDlApply.setOnClickListener {
            val light = viewModel.getLightTheme().value
            val dark = viewModel.getDarkTheme().value
            if (mode == MODE_CHOOSE) {
                FragmentUtils.getParentAs(this, OnDarkLightThemesChosenListener::class.java)
                    ?.onThemesChosen(tag, light, dark)
            } else {
                val themeManager = Theme.getInstance(requireContext())
                light?.let { themeManager.lightTheme = it }
                dark?.let { themeManager.darkTheme = it }
            }
            dismiss()
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
        val owner = FragmentUtils.getParentAs(this, ThemeSelectionDialogFragment.OnThemeDialogDismissListener::class.java)
        tag?.let { owner?.onThemeDialogDismissed(it) }
    }

    private fun pickTheme(chooserTag: String, current: Theme.ThemeDescriptor?) {
        ThemeSelectionDialogFragment.newInstance(ThemeSelectionDialogFragment.MODE_CHOOSE, current?.id ?: -1)
            .show(childFragmentManager, chooserTag)
    }

    override fun onThemeChosen(tag: String?, theme: Theme.ThemeDescriptor?) {
        when (tag) {
            TAG_CHOOSE_LIGHT_THEME -> viewModel.setLightTheme(theme)
            TAG_CHOOSE_DARK_THEME -> viewModel.setDarkTheme(theme)
        }
    }

    fun interface OnDarkLightThemesChosenListener {
        fun onThemesChosen(tag: String?, lightTheme: Theme.ThemeDescriptor?, darkTheme: Theme.ThemeDescriptor?)
    }

    companion object {
        private const val TAG_CHOOSE_LIGHT_THEME = "choose_light"
        private const val TAG_CHOOSE_DARK_THEME = "choose_dark"

        const val MODE_APPLY = 0
        const val MODE_CHOOSE = 1

        private const val EXTRA_MODE = "mode"

        /** Same as [newInstance] with [MODE_CHOOSE]. */
        @JvmStatic
        fun newInstance(): DarkLightThemeSelectionDialogFragment = newInstance(MODE_CHOOSE)

        @JvmStatic
        fun newInstance(@Mode mode: Int): DarkLightThemeSelectionDialogFragment =
            DarkLightThemeSelectionDialogFragment().apply {
                arguments = Bundle().apply { putInt(EXTRA_MODE, mode) }
            }
    }
}
