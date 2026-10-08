package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetProjectFileOptionsBinding

/** "Add to this folder" actions for a folder of the project tree. */
class ProjectFileOptionDialog : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_project_file_options, container, false)

    private var listener: ItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = (parentFragment ?: context) as ItemClickListener
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = BottomSheetProjectFileOptionsBinding.bind(view)
        listOf(binding.createClassFile, binding.createXmlFile, binding.addNewFolder, binding.selectFile)
            .forEach { action ->
                action.setOnClickListener {
                    listener?.onFileItemClick(action.id)
                    dismiss()
                }
            }
    }

    fun interface ItemClickListener {
        fun onFileItemClick(item: Int?)
    }

    companion object {
        const val TAG = "ProjectFileOptionDialogFragment"

        @JvmStatic
        fun newInstance() = ProjectFileOptionDialog()
    }
}
