package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.databinding.BottomSheetAppsBinding
import com.riyadm.apkrepacker.ui.motion.springIn

/** Actions for one installed app: decompile, simple edit, AntiSplit (split apps), open in settings, uninstall. */
class AppsOptionsItemDialogFragment : BottomSheetDialogFragment() {

    /** Set on the first accepted tap: the sheet stays tappable while it animates out. */
    private var handled = false

    private var listener: ItemClickListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetAppsBinding.inflate(inflater, container, false).also { bindSheet(it) }.root

    private fun bindSheet(binding: BottomSheetAppsBinding) {
        val title = arguments?.getString(ARG_TITLE)?.takeIf { it.isNotEmpty() }
        title?.let { binding.sheetTitle.text = it }
        // An app without a label is titled with its package name; don't repeat it underneath.
        arguments?.getString(ARG_SUBTITLE)?.takeIf { it.isNotEmpty() && it != title }?.let {
            binding.sheetSubtitle.text = it
            binding.sheetSubtitle.visibility = View.VISIBLE
        }

        val click = View.OnClickListener { view ->
            if (handled) return@OnClickListener
            handled = true
            listener?.onAppsItemClick(view.id)
            dismiss()
        }
        val split = arguments?.getBoolean(ARG_SPLIT) == true
        binding.antisplitSave.visibility = if (split) View.VISIBLE else View.GONE
        listOfNotNull(binding.decompileApp, binding.simpleEditApk, binding.antisplitSave.takeIf { split }, binding.gotoSettingsApp, binding.uninstallApp)
            .forEachIndexed { index, button ->
                button.setOnClickListener(click)
                button.springIn(delayMs = index * ACTION_STAGGER_MS, fromScale = 0.94f, fromTranslationY = 24f)
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = (parentFragment ?: context) as ItemClickListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    fun interface ItemClickListener {
        fun onAppsItemClick(item: Int?)
    }

    companion object {
        const val TAG = "ApkOptionsDialogFragment"
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_SPLIT = "split"
        private const val ACTION_STAGGER_MS = 35L

        @JvmStatic
        @JvmOverloads
        fun newInstance(title: String? = null, subtitle: String? = null, split: Boolean = false): AppsOptionsItemDialogFragment =
            AppsOptionsItemDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                    putBoolean(ARG_SPLIT, split)
                }
            }
    }
}
