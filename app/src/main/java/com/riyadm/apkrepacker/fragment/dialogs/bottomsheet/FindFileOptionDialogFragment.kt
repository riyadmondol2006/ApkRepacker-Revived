package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.databinding.BottomSheetFindFileOptionsBinding

/** Actions for a search result: open with, open in editor, copy path and (for string results) replace. */
class FindFileOptionDialogFragment : BottomSheetDialogFragment() {

    private var binding: BottomSheetFindFileOptionsBinding? = null
    private var listener: ItemClickListener? = null
    private var stringsMode = false

    fun setItemClickListener(listener: ItemClickListener?) {
        this.listener = listener
    }

    fun setIsStringMode(mode: Boolean) {
        stringsMode = mode
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetFindFileOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.replaceInFile.isVisible = stringsMode
        listOf(b.openWith, b.openInEditor, b.copyPath, b.replaceInFile).forEach { action ->
            action.setOnClickListener {
                listener?.onFileItemClick(it.id)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun interface ItemClickListener {
        fun onFileItemClick(@IdRes item: Int)
    }

    companion object {
        const val TAG = "FindFileOptionDialogFragment"

        @JvmStatic
        fun newInstance(): FindFileOptionDialogFragment = FindFileOptionDialogFragment()
    }
}
