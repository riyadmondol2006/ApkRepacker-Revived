package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.databinding.BottomSheetTranslateOptionsBinding
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.resourceeditor.findListener

/** Options for translating with a dictionary file; the file path travels in the fragment arguments. */
class TranslateOptionsDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val views = BottomSheetTranslateOptionsBinding.inflate(inflater, container, false)
        val helper = PreferenceHelper.getInstance(requireContext())
        views.swSkipTranslated.isChecked = helper.isSkipTranslated
        views.swSkipSupportLines.isChecked = helper.isSkipSupportLines
        views.swReverseDictionary.isChecked = helper.isReverseDictionary
        views.btnOk.setOnClickListener {
            helper.isSkipTranslated = views.swSkipTranslated.isChecked
            helper.isSkipSupportLines = views.swSkipSupportLines.isChecked
            helper.isReverseDictionary = views.swReverseDictionary.isChecked
            findListener<ItemClickListener>()?.onOkClick(arguments?.getString(DICTIONARY_PATH))
            dismiss()
        }
        return views.root
    }

    fun interface ItemClickListener {
        fun onOkClick(path: String?)
    }

    companion object {
        const val TAG = "TranslateOptionsDialogFragment"
        private const val DICTIONARY_PATH = "key_dictionary_path"

        @JvmStatic
        fun newInstance(path: String?) = TranslateOptionsDialogFragment().apply {
            arguments = Bundle().apply { putString(DICTIONARY_PATH, path) }
        }
    }
}
