package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.databinding.BottomSheetFileOptionsBinding
import com.riyadm.apkrepacker.utils.IntentUtils
import com.riyadm.apkrepacker.utils.StringUtils
import java.io.File

/** Actions for a file mentioned in a build/log message: open in the editor, open with, copy path. */
class FileOptionsDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_file_options, container, false)

    private val filePath: String? get() = arguments?.getString(ARG_PATH)
    private val lineNumber: Int get() = arguments?.getInt(ARG_LINE) ?: 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = BottomSheetFileOptionsBinding.bind(view)
        val path = filePath
        binding.sheetTitle.text = path?.let { File(it).name }.orEmpty()

        binding.openInEditor.setOnClickListener {
            val intent = Intent(requireContext(), CodeEditorActivity::class.java)
                .putExtra("filePath", path)
                .putExtra("offset", lineNumber)
            startActivity(intent)
            dismiss()
        }
        binding.openWith.setOnClickListener {
            path?.let { startActivity(IntentUtils.openFileWithIntent(File(it))) }
            dismiss()
        }
        binding.actionCopyPath.setOnClickListener {
            StringUtils.setClipboard(requireContext(), path, false)
            // Android 13+ confirms clipboard writes itself; older versions get a Snackbar.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                requireActivity().findViewById<View>(android.R.id.content)?.let {
                    Snackbar.make(it, R.string.tree_path_copied, Snackbar.LENGTH_SHORT).show()
                }
            }
            dismiss()
        }
    }

    companion object {
        const val TAG = "FileOptionsDialogFragment"
        private const val ARG_PATH = "filePath"
        private const val ARG_LINE = "lineNumber"

        @JvmStatic
        fun newInstance(filePath: String?, lineNumber: Int) = FileOptionsDialogFragment().apply {
            arguments = bundleOf(ARG_PATH to filePath, ARG_LINE to lineNumber)
        }
    }
}
