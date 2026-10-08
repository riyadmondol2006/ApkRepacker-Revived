package com.riyadm.apkrepacker.fragment.dialogs.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.jecelyin.editor.v2.io.LocalFileWriter
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BottomSheetDialogSearchReplaceBinding
import com.riyadm.apkrepacker.utils.grep.ExtGrep
import com.riyadm.apkrepacker.utils.grep.GrepBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Bottom sheet that replaces text in one file or, in multi mode, in every file of a search result.
 * The work runs on the host activity's scope so it survives the sheet being dismissed.
 */
class ReplaceInFileDialogFragment : BottomSheetDialogFragment() {

    private var binding: BottomSheetDialogSearchReplaceBinding? = null
    private var defaultSearchText: String? = null
    private var inputFile: File? = null
    private var inputFiles: List<String> = emptyList()
    private var multiMode = false
    private var onReplaced: OnReplacedInterface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            defaultSearchText = args.getString(SEARCH_TEXT)
            args.getStringArrayList(FILES)?.let { inputFiles = it.toList() }
            args.getString(FILE)?.let { inputFile = File(it) }
            multiMode = args.getBoolean(MULTIREPLACE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetDialogSearchReplaceBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        if (multiMode) b.titleTextView.setText(R.string.replace_in_files_title)
        if (savedInstanceState == null) b.searchText.setText(defaultSearchText)
        b.searchText.doAfterTextChanged { b.searchTextLayout.error = null }
        b.buttonBottomSheetDialogBaseOk.setOnClickListener { startReplace() }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun setItemClickListener(listener: OnReplacedInterface?) {
        onReplaced = listener
    }

    private fun startReplace() {
        val b = binding ?: return
        val findText = b.searchText.text?.toString().orEmpty()
        val replaceText = b.replaceText.text?.toString().orEmpty()
        if (findText.isEmpty()) {
            b.searchTextLayout.error = getString(R.string.cannot_be_empty)
            return
        }

        val grep = GrepBuilder.start().apply {
            if (!b.ignoreCaseCb.isChecked) ignoreCase()
            if (b.wholeWordsOnlyCb.isChecked) wordRegex()
            setRegex(findText, b.regularExpCb.isChecked)
        }.build()

        val host = requireActivity()
        val callback = onReplaced
        val singleFile = inputFile
        val files = inputFiles
        val multi = multiMode

        host.lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                if (multi) replaceInFiles(host.getString(R.string.error), files, grep, replaceText)
                else replaceInFile(singleFile, grep, replaceText)
            }
            val root = host.findViewById<View>(android.R.id.content)
            when (message) {
                is Outcome.Done -> {
                    if (!multi) callback?.onReplaced()
                    Snackbar.make(root, message.text(host), Snackbar.LENGTH_LONG).show()
                }
                is Outcome.Failed -> Snackbar.make(root, message.text(host), Snackbar.LENGTH_LONG).show()
            }
        }
        dismiss()
    }

    private fun replaceInFile(file: File?, grep: ExtGrep, replacement: String): Outcome {
        file ?: return Outcome.Failed(null)
        return try {
            rewrite(file, grep, replacement)
            Outcome.Done(0)
        } catch (e: IOException) {
            e.printStackTrace()
            Outcome.Failed(null)
        }
    }

    private fun replaceInFiles(errorLabel: String, paths: List<String>, grep: ExtGrep, replacement: String): Outcome {
        var replaced = 0
        val failed = mutableListOf<String>()
        for (path in paths) {
            try {
                rewrite(File(path), grep, replacement)
                replaced++
            } catch (e: IOException) {
                e.printStackTrace()
                failed += File(path).name
            }
        }
        return if (failed.isEmpty()) Outcome.Done(replaced) else Outcome.Failed("$errorLabel: ${failed.joinToString()}")
    }

    @Throws(IOException::class)
    private fun rewrite(file: File, grep: ExtGrep, replacement: String) {
        val result = grep.replaceAll(file.readText(Charsets.UTF_8), replacement)
        LocalFileWriter(file, Charsets.UTF_8.name()).writeToFile(result)
    }

    private sealed interface Outcome {
        /** [count] is the number of files written in multi mode; 0 for the single-file replace. */
        data class Done(val count: Int) : Outcome {
            fun text(host: android.content.Context): String =
                if (count > 0) host.getString(R.string.toast_replace_in_files, count) else host.getString(R.string.replaced_successful)
        }

        data class Failed(val detail: String?) : Outcome {
            fun text(host: android.content.Context): String = detail ?: host.getString(R.string.error)
        }
    }

    fun interface OnReplacedInterface {
        fun onReplaced()
    }

    companion object {
        const val TAG = "ReplaceInFileDialogFragment"
        private const val FILE = "file"
        private const val FILES = "files"
        private const val MULTIREPLACE = "multi_replace_mode"
        private const val SEARCH_TEXT = "search_text"

        @JvmStatic
        fun newInstance(): ReplaceInFileDialogFragment = ReplaceInFileDialogFragment()

        @JvmStatic
        fun newInstance(searchText: String?, file: String?): ReplaceInFileDialogFragment {
            return ReplaceInFileDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(SEARCH_TEXT, searchText)
                    putString(FILE, file)
                }
            }
        }

        @JvmStatic
        fun newInstance(searchText: String?, files: ArrayList<String>?): ReplaceInFileDialogFragment {
            return ReplaceInFileDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(SEARCH_TEXT, searchText)
                    putStringArrayList(FILES, files)
                    putBoolean(MULTIREPLACE, true)
                }
            }
        }
    }
}
