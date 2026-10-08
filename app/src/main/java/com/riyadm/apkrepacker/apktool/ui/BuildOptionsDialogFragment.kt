package com.riyadm.apkrepacker.apktool.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.BuildOptions
import com.riyadm.apkrepacker.databinding.ApktoolBuildOptionsSheetBinding

/**
 * Bottom sheet with apktool `b` flags, prefilled from [ApktoolOptionsStore.loadBuildOptions].
 * Confirming saves the options to the store (the build reads them from there) and posts a
 * fragment result [REQUEST_KEY] with [RESULT_CONFIRMED] = true; cancelling posts false.
 */
class BuildOptionsDialogFragment : BottomSheetDialogFragment() {

    private class Rows(
        val debuggable: OptionRow,
        val netSecConf: OptionRow,
        val copyOriginal: OptionRow,
        val noCrunch: OptionRow,
        val force: OptionRow,
        val sign: OptionRow,
        val removeSplitRequirement: OptionRow,
    ) {
        fun current() = BuildOptions(
            debuggable = debuggable.isChecked,
            netSecConf = netSecConf.isChecked,
            copyOriginal = copyOriginal.isChecked,
            noCrunch = noCrunch.isChecked,
            force = force.isChecked,
            sign = sign.isChecked,
            removeSplitRequirement = removeSplitRequirement.isChecked,
        )
    }

    private var binding: ApktoolBuildOptionsSheetBinding? = null
    private var rows: Rows? = null
    private var resultSent = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ApktoolBuildOptionsSheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        val stored = ApktoolOptionsStore.loadBuildOptions(requireContext())
        val o = savedInstanceState?.let {
            BuildOptions(
                debuggable = it.getBoolean(STATE_DEBUGGABLE, stored.debuggable),
                netSecConf = it.getBoolean(STATE_NET_SEC_CONF, stored.netSecConf),
                copyOriginal = it.getBoolean(STATE_COPY_ORIGINAL, stored.copyOriginal),
                noCrunch = it.getBoolean(STATE_NO_CRUNCH, stored.noCrunch),
                force = it.getBoolean(STATE_FORCE, stored.force),
                sign = it.getBoolean(STATE_SIGN, stored.sign),
                removeSplitRequirement = it.getBoolean(STATE_REMOVE_SPLIT_REQUIREMENT, stored.removeSplitRequirement),
            )
        } ?: stored

        val output = b.apktoolGroupOutput
        val sign = OptionRow.inflate(output, R.string.apktool_build_sign, R.string.apktool_build_sign_summary, o.sign)
        val force = OptionRow.inflate(output, R.string.apktool_build_force, R.string.apktool_build_force_summary, o.force)
        val noCrunch = OptionRow.inflate(output, R.string.apktool_build_no_crunch, R.string.apktool_build_no_crunch_summary, o.noCrunch)
        OptionRow.roundGroup(listOf(sign, force, noCrunch))

        val manifest = b.apktoolGroupManifest
        val debuggable = OptionRow.inflate(manifest, R.string.apktool_build_debuggable, R.string.apktool_build_debuggable_summary, o.debuggable)
        val netSecConf = OptionRow.inflate(manifest, R.string.apktool_build_net_sec_conf, R.string.apktool_build_net_sec_conf_summary, o.netSecConf)
        val copyOriginal = OptionRow.inflate(manifest, R.string.apktool_build_copy_original, R.string.apktool_build_copy_original_summary, o.copyOriginal)
        val removeSplit = OptionRow.inflate(
            manifest, R.string.apktool_build_remove_split_requirement,
            R.string.apktool_build_remove_split_requirement_summary, o.removeSplitRequirement,
        )
        OptionRow.roundGroup(listOf(debuggable, netSecConf, copyOriginal, removeSplit))

        val rows = Rows(debuggable, netSecConf, copyOriginal, noCrunch, force, sign, removeSplit)
        this.rows = rows

        b.apktoolBtnCancel.setOnClickListener {
            sendResult(false)
            dismiss()
        }
        b.apktoolBtnConfirm.setOnClickListener {
            ApktoolOptionsStore.saveBuildOptions(requireContext(), rows.current())
            sendResult(true)
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
        val o = rows?.current() ?: return
        outState.putBoolean(STATE_DEBUGGABLE, o.debuggable)
        outState.putBoolean(STATE_NET_SEC_CONF, o.netSecConf)
        outState.putBoolean(STATE_COPY_ORIGINAL, o.copyOriginal)
        outState.putBoolean(STATE_NO_CRUNCH, o.noCrunch)
        outState.putBoolean(STATE_FORCE, o.force)
        outState.putBoolean(STATE_SIGN, o.sign)
        outState.putBoolean(STATE_REMOVE_SPLIT_REQUIREMENT, o.removeSplitRequirement)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        rows = null
    }

    /** Swiping the sheet away or pressing back counts as cancel. */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        sendResult(false)
    }

    private fun sendResult(confirmed: Boolean) {
        if (resultSent) return
        resultSent = true
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to confirmed))
    }

    companion object {
        const val TAG = "BuildOptionsDialogFragment"
        const val REQUEST_KEY = "apktool_build_options"
        const val RESULT_CONFIRMED = "confirmed"

        private const val STATE_DEBUGGABLE = "debuggable"
        private const val STATE_NET_SEC_CONF = "netSecConf"
        private const val STATE_COPY_ORIGINAL = "copyOriginal"
        private const val STATE_NO_CRUNCH = "noCrunch"
        private const val STATE_FORCE = "force"
        private const val STATE_SIGN = "sign"
        private const val STATE_REMOVE_SPLIT_REQUIREMENT = "removeSplitRequirement"

        @JvmStatic
        fun newInstance(): BuildOptionsDialogFragment = BuildOptionsDialogFragment()
    }
}
