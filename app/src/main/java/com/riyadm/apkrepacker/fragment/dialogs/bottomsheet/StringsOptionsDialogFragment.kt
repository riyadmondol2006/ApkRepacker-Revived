package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetStringsOptionsBinding
import com.riyadm.apkrepacker.ui.resourceeditor.findListener

class StringsOptionsDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val views = BottomSheetStringsOptionsBinding.inflate(inflater, container, false)
        listOf(
            views.openWith,
            views.addNewString,
            views.addNewLanguage,
            views.autoTranslateLanguage,
            views.autoTranslateLanguageWith,
            views.saveAsDictionary,
        ).forEach { action ->
            action.setOnClickListener {
                findListener<ItemClickListener>()?.onItemClick(action.id)
                dismiss()
            }
        }
        return views.root
    }

    fun interface ItemClickListener {
        /** [item] is one of `open_with`, `add_new_string`, `add_new_language`, `auto_translate_language`, `auto_translate_language_with`, `save_as_dictionary`. */
        fun onItemClick(@IdRes item: Int)
    }

    companion object {
        const val TAG = "StringsOptionsDialogFragment"

        @JvmStatic
        fun newInstance() = StringsOptionsDialogFragment()
    }
}
