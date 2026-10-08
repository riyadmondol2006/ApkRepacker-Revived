package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetAddNewStringBinding
import com.riyadm.apkrepacker.ui.resourceeditor.findListener

class AddNewStringDialogFragment : BottomSheetDialogFragment() {

    private var binding: BottomSheetAddNewStringBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetAddNewStringBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        views.newKey.doAfterTextChanged { views.newKeyLayout.error = null }
        views.btnAddLangOk.setOnClickListener {
            val key = views.newKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) {
                views.newKeyLayout.error = getString(R.string.cannot_be_empty)
                return@setOnClickListener
            }
            findListener<ItemClickListener>()?.onAddStringClicked(key, views.newValue.text?.toString().orEmpty())
            dismiss()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun interface ItemClickListener {
        fun onAddStringClicked(key: String, value: String)
    }

    companion object {
        const val TAG = "AddNewStringDialogFragment"

        @JvmStatic
        fun newInstance() = AddNewStringDialogFragment()
    }
}
