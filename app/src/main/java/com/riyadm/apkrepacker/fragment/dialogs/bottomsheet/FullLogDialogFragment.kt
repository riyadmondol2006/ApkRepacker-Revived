package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetDialogLogBinding
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Shows the log passed as the "log" argument, or the last build's `compile_log.txt`. */
class FullLogDialogFragment : BottomSheetDialogFragment() {

    private var binding: BottomSheetDialogLogBinding? = null
    private var log: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetDialogLogBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = binding ?: return

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        ui.btnCopyLog.setOnClickListener { copyLog(it) }

        arguments?.let { args ->
            showLog(args.getString(ARG_LOG))
            return
        }
        // Resolve the path on the main thread: requireContext() throws once the sheet is dismissed.
        val decodingPath = PreferenceHelper.getInstance(requireContext()).decodingPath
        viewLifecycleOwner.lifecycleScope.launch {
            val text = readCompileLog(decodingPath)
            if (isAdded) showLog(text)
        }
    }

    private suspend fun readCompileLog(decodingPath: String): String? = withContext(Dispatchers.IO) {
        try {
            File(decodingPath, "compile_log.txt").readText()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun showLog(text: String?) {
        log = text
        binding?.apply {
            btnCopyLog.isVisible = !text.isNullOrEmpty()
            this.log.text = text.takeUnless { it.isNullOrEmpty() } ?: getString(R.string.log_empty)
        }
    }

    private fun copyLog(anchor: View) {
        StringUtils.setClipboard(requireContext(), log, false)
        // Android 13+ confirms clipboard writes itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(anchor, R.string.toast_copy_to_clipboard, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val TAG = "FullLogDialogFragment"
        private const val ARG_LOG = "log"

        @JvmStatic
        fun newInstance(): FullLogDialogFragment {
            return FullLogDialogFragment()
        }

        @JvmStatic
        fun newInstance(log: String?): FullLogDialogFragment {
            return FullLogDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_LOG, log) }
            }
        }
    }
}
