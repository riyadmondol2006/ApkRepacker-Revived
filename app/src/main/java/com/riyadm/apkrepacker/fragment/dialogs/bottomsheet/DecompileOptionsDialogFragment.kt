package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeMode
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.DecodeOptionsArgs
import com.riyadm.apkrepacker.apktool.ui.OptionRow
import com.riyadm.apkrepacker.databinding.ApktoolDecodeOptionsSheetBinding
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.springTo

/**
 * Bottom sheet with apktool `d` options: what to decode (a connected segmented choice) plus the
 * decode flags, prefilled from [ApktoolOptionsStore.loadDecodeOptions] (optionally with a preset mode).
 * "Remember as default" saves the choice back to the store.
 *
 * The parent fragment (or the activity) must implement [ItemClickListener].
 */
class DecompileOptionsDialogFragment : BottomSheetDialogFragment() {

    private class Rows(
        val onlyMainClasses: OptionRow,
        val noDebugInfo: OptionRow,
        val noAssets: OptionRow,
        val forceManifest: OptionRow,
        val keepBrokenRes: OptionRow,
        val analysisMode: OptionRow,
        val mergeSplits: OptionRow,
    )

    private var listener: ItemClickListener? = null
    private var binding: ApktoolDecodeOptionsSheetBinding? = null
    private var rows: Rows? = null

    /** Not shown in the sheet; carried over from the initial options. */
    private var force = DecodeOptions().force

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ApktoolDecodeOptionsSheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        val initial = DecodeOptionsArgs.fromBundle(savedInstanceState?.getBundle(STATE_OPTIONS)) ?: initialOptions()
        force = initial.force

        val code = b.apktoolGroupCode
        val onlyMain = OptionRow.inflate(code, R.string.apktool_only_main_classes, R.string.apktool_only_main_classes_summary, initial.onlyMainClasses)
        val noDebug = OptionRow.inflate(code, R.string.apktool_no_debug_info, R.string.apktool_no_debug_info_summary, initial.noDebugInfo)
        OptionRow.roundGroup(listOf(onlyMain, noDebug))

        val resources = b.apktoolGroupResources
        val keepBroken = OptionRow.inflate(resources, R.string.apktool_keep_broken_res, R.string.apktool_keep_broken_res_summary, initial.keepBrokenResources)
        val forceManifest = OptionRow.inflate(resources, R.string.apktool_force_manifest, R.string.apktool_force_manifest_summary, initial.forceManifest)
        val noAssets = OptionRow.inflate(resources, R.string.apktool_no_assets, R.string.apktool_no_assets_summary, initial.noAssets)
        OptionRow.roundGroup(listOf(keepBroken, forceManifest, noAssets))

        val general = b.apktoolGroupGeneral
        val analysis = OptionRow.inflate(general, R.string.apktool_analysis_mode, R.string.apktool_analysis_mode_summary, initial.analysisMode)
        val merge = OptionRow.inflate(general, R.string.apktool_merge_splits, R.string.apktool_merge_splits_summary, initial.mergeSplits)
        OptionRow.roundGroup(listOf(analysis, merge))

        rows = Rows(onlyMain, noDebug, noAssets, forceManifest, keepBroken, analysis, merge)

        b.apktoolDecodeModeGroup.check(buttonIdFor(initial.mode))
        applyMode(initial.mode, animate = false)
        b.apktoolDecodeModeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) applyMode(selectedMode(), animate = true)
        }

        b.apktoolBtnCancel.setOnClickListener { dismiss() }
        b.apktoolBtnConfirm.setOnClickListener {
            val options = currentOptions() ?: return@setOnClickListener
            if (b.apktoolRememberDefault.isChecked) {
                ApktoolOptionsStore.saveDecodeOptions(requireContext(), options)
            }
            listener?.onDecodeOptionsChosen(options)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentOptions()?.let { outState.putBundle(STATE_OPTIONS, DecodeOptionsArgs.toBundle(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        rows = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = (parentFragment as? ItemClickListener) ?: (context as? ItemClickListener)
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun initialOptions(): DecodeOptions {
        val stored = ApktoolOptionsStore.loadDecodeOptions(requireContext())
        val preset = arguments?.getString(ARG_PRESET_MODE)
            ?.let { name -> DecodeMode.entries.firstOrNull { it.name == name } }
        return if (preset != null) stored.copy(mode = preset) else stored
    }

    private fun selectedMode(): DecodeMode = when (binding?.apktoolDecodeModeGroup?.checkedButtonId) {
        R.id.apktool_decode_mode_resources -> DecodeMode.RESOURCES_ONLY
        R.id.apktool_decode_mode_sources -> DecodeMode.SOURCES_ONLY
        R.id.apktool_decode_mode_none -> DecodeMode.NONE
        else -> DecodeMode.ALL
    }

    @IdRes
    private fun buttonIdFor(mode: DecodeMode): Int = when (mode) {
        DecodeMode.ALL -> R.id.apktool_decode_mode_all
        DecodeMode.RESOURCES_ONLY -> R.id.apktool_decode_mode_resources
        DecodeMode.SOURCES_ONLY -> R.id.apktool_decode_mode_sources
        DecodeMode.NONE -> R.id.apktool_decode_mode_none
    }

    @StringRes
    private fun descriptionFor(mode: DecodeMode): Int = when (mode) {
        DecodeMode.ALL -> R.string.apktool_decode_mode_all
        DecodeMode.RESOURCES_ONLY -> R.string.apktool_decode_mode_resources
        DecodeMode.SOURCES_ONLY -> R.string.apktool_decode_mode_sources
        DecodeMode.NONE -> R.string.apktool_decode_mode_none
    }

    /** Updates the mode description and greys out flags that don't apply (their values are kept). */
    private fun applyMode(mode: DecodeMode, animate: Boolean) {
        val b = binding ?: return
        b.apktoolDecodeModeDescription.setText(descriptionFor(mode))
        if (animate) {
            b.apktoolDecodeModeDescription.alpha = 0f
            b.apktoolDecodeModeDescription.springTo(DynamicAnimation.ALPHA, 1f, MotionSpring.DefaultEffects)
        }
        val rows = rows ?: return
        val smali = mode == DecodeMode.ALL || mode == DecodeMode.SOURCES_ONLY
        val res = mode == DecodeMode.ALL || mode == DecodeMode.RESOURCES_ONLY
        rows.onlyMainClasses.setEnabledState(smali, animate)
        rows.noDebugInfo.setEnabledState(smali, animate)
        rows.keepBrokenRes.setEnabledState(res, animate)
        rows.forceManifest.setEnabledState(!res, animate)
    }

    private fun currentOptions(): DecodeOptions? {
        val rows = rows ?: return null
        return DecodeOptions(
            mode = selectedMode(),
            onlyMainClasses = rows.onlyMainClasses.isChecked,
            noDebugInfo = rows.noDebugInfo.isChecked,
            noAssets = rows.noAssets.isChecked,
            forceManifest = rows.forceManifest.isChecked,
            keepBrokenResources = rows.keepBrokenRes.isChecked,
            analysisMode = rows.analysisMode.isChecked,
            force = force,
            mergeSplits = rows.mergeSplits.isChecked,
        )
    }

    interface ItemClickListener {
        /**
         * Legacy callback: [item] is R.id.decompile_all (resources + smali), R.id.decompile_all_res
         * (resources only), R.id.decompile_all_dex (smali only) or R.id.apktool_decompile_none.
         */
        fun onModeItemClick(item: Int?)

        /**
         * Called with everything chosen in the sheet. The default implementation forwards only the
         * mode to [onModeItemClick]; override it to pass the full [DecodeOptions] to
         * DecompileFragment.newInstance(name, selected, f, options).
         */
        fun onDecodeOptionsChosen(options: DecodeOptions) {
            onModeItemClick(legacyItemId(options.mode))
        }
    }

    companion object {
        const val TAG = "DecompileOptionsDialogFragment"
        private const val ARG_PRESET_MODE = "presetMode"
        private const val STATE_OPTIONS = "options"

        /** Sheet prefilled with the saved default options. */
        @JvmStatic
        fun newInstance(): DecompileOptionsDialogFragment = newInstance(null)

        /** Sheet prefilled with the saved default options, but with [preset] selected as mode. */
        @JvmStatic
        fun newInstance(preset: DecodeMode?): DecompileOptionsDialogFragment {
            val fragment = DecompileOptionsDialogFragment()
            if (preset != null) {
                fragment.arguments = Bundle().apply { putString(ARG_PRESET_MODE, preset.name) }
            }
            return fragment
        }

        /** The legacy [ItemClickListener.onModeItemClick] id for [mode]. */
        @JvmStatic
        fun legacyItemId(mode: DecodeMode): Int = when (mode) {
            DecodeMode.ALL -> R.id.decompile_all
            DecodeMode.RESOURCES_ONLY -> R.id.decompile_all_res
            DecodeMode.SOURCES_ONLY -> R.id.decompile_all_dex
            DecodeMode.NONE -> R.id.apktool_decompile_none
        }

        /** Maps a legacy [ItemClickListener.onModeItemClick] id to a [DecodeMode] preset, or null. */
        @JvmStatic
        fun modeForLegacyItemId(item: Int?): DecodeMode? = when (item) {
            R.id.decompile_all -> DecodeMode.ALL
            R.id.decompile_all_res -> DecodeMode.RESOURCES_ONLY
            R.id.decompile_all_dex -> DecodeMode.SOURCES_ONLY
            R.id.apktool_decompile_none -> DecodeMode.NONE
            else -> null
        }
    }
}
