package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.databinding.BottomSheetApkOptionsBinding
import com.riyadm.apkrepacker.ui.motion.springIn

/** Actions for an APK file chosen in the file manager. */
class ApkOptionsDialogFragment : BottomSheetDialogFragment() {

    /** Set on the first accepted tap: the sheet stays tappable while it animates out. */
    private var handled = false

    private var listener: ItemClickListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetApkOptionsBinding.inflate(inflater, container, false).also { bindSheet(it) }.root

    private fun bindSheet(binding: BottomSheetApkOptionsBinding) {
        arguments?.getString(ARG_TITLE)?.takeIf { it.isNotEmpty() }?.let { binding.sheetTitle.text = it }
        arguments?.getString(ARG_SUBTITLE)?.takeIf { it.isNotEmpty() }?.let {
            binding.sheetSubtitle.text = it
            binding.sheetSubtitle.visibility = View.VISIBLE
        }

        val click = View.OnClickListener { view ->
            if (handled) return@OnClickListener
            handled = true
            listener?.onApkItemClick(view.id)
            dismiss()
        }
        listOf(
            binding.decompileApp,
            binding.simpleEditApk,
            binding.installApp,
            binding.signApp,
            binding.setAsFrameworkApp,
            binding.deleteItem,
        ).forEachIndexed { index, button ->
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
        fun onApkItemClick(item: Int?)
    }

    companion object {
        const val TAG = "ApkOptionsDialogFragment"
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ACTION_STAGGER_MS = 35L

        @JvmStatic
        @JvmOverloads
        fun newInstance(title: String? = null, subtitle: String? = null): ApkOptionsDialogFragment =
            ApkOptionsDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                }
            }
    }
}
