package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.database.ITabDatabase
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.databinding.DialogTextInputEBinding
import com.riyadm.apkrepacker.databinding.FragmentSearchSettingsBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.ui.autocompleteeidttext.CustomAdapter
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.StringUtils
import com.riyadm.apkrepacker.utils.grep.GrepBuilder

/** Search options: query, folder, mode (file names or text), match flags and extension filters. */
class SearchSettingsFragment : Fragment() {

    private var binding: FragmentSearchSettingsBinding? = null
    private lateinit var prefs: PreferenceHelper
    private lateinit var database: ITabDatabase
    private var suggestions: CustomAdapter? = null
    private var listener: ItemClickListener? = null

    private var path: String? = null
    private var filesMode = false
    private var regex = false
    private var wholeWordsOnly = false
    private var matchCase = false
    private var recursively = false
    private var extMap: MutableMap<String, Boolean> = HashMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        path = ProjectUtils.getCurrentPath()
        prefs = PreferenceHelper.getInstance(requireContext())
        database = JsonDatabase.getInstance(requireContext())
        loadPrefs()
        return FragmentSearchSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.path.setText(path)
        b.searchFileCb.isChecked = filesMode
        b.regularExpCb.isChecked = regex
        b.ignoreCaseCb.isChecked = matchCase
        b.recursivelyCb.isChecked = recursively
        b.wholeWordsOnlyCb.isChecked = wholeWordsOnly
        b.searchOptionsLayout.isVisible = !filesMode
        refreshSuggestions()

        b.searchFileCb.setOnCheckedChangeListener { _, checked ->
            filesMode = checked
            prefs.isFilesMode = checked
            refreshSuggestions()
            b.searchOptionsLayout.isVisible = !checked
        }
        b.regularExpCb.setOnCheckedChangeListener { _, checked ->
            regex = checked
            prefs.isRegexMode = checked
            if (checked) showMessage(R.string.use_regex_to_find_tip, Snackbar.LENGTH_LONG)
        }
        b.ignoreCaseCb.setOnCheckedChangeListener { _, checked ->
            matchCase = checked
            prefs.isMatchCaseMode = checked
        }
        b.recursivelyCb.setOnCheckedChangeListener { _, checked ->
            recursively = checked
            prefs.isRecursivelyMode = checked
        }
        b.wholeWordsOnlyCb.setOnCheckedChangeListener { _, checked ->
            wholeWordsOnly = checked
            prefs.isWholeWordsOnlyMode = checked
        }

        extMap.forEach { (ext, checked) -> addExtensionChip(ext, checked) }

        b.pathLayout.setEndIconOnClickListener { pickFolder() }
        b.buttonAddExt.setOnClickListener { askForExtension() }
        b.fabGoSearch.setOnClickListener { startSearch() }
        b.fabGoSearch.springIn(delayMs = 80L, fromScale = 0.6f, fromTranslationY = 48f)
    }

    override fun onResume() {
        super.onResume()
        loadPrefs()
    }

    override fun onDestroyView() {
        checkedExtensions()
        prefs.ext = extMap
        binding = null
        suggestions = null
        super.onDestroyView()
    }

    fun setItemClickListener(listener: ItemClickListener?) {
        this.listener = listener
    }

    private fun loadPrefs() {
        filesMode = prefs.isFilesMode
        regex = prefs.isRegexMode
        matchCase = prefs.isMatchCaseMode
        wholeWordsOnly = prefs.isWholeWordsOnlyMode
        recursively = prefs.isRecursivelyMode
        extMap = prefs.ext
    }

    private fun refreshSuggestions() {
        suggestions = CustomAdapter(requireContext(), database.getFindKeywordsAdnFile(filesMode))
        binding?.searchText?.setAdapter(suggestions)
    }

    private fun startSearch() {
        val b = binding ?: return
        val query = b.searchText.text?.toString().orEmpty()
        val folder = b.path.text?.toString().orEmpty()
        val extensions = checkedExtensions()
        if (query.isEmpty() && extMap.isEmpty() && folder.isEmpty()) {
            showMessage(R.string.toast_error_empty_find)
            return
        }
        StringUtils.hideKeyboard(this)
        prefs.ext = extMap
        rememberQuery(query, filesMode)

        if (filesMode) {
            listener?.let {
                it.onStartSearch(true, folder, query, extensions)
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
        } else {
            val grep = GrepBuilder.start().apply {
                if (!matchCase) ignoreCase()
                if (wholeWordsOnly) wordRegex()
                setRegex(query, regex)
                if (recursively) recurseDirectories()
                setExeption(extensions)
                addFile(path)
            }.build()
            StringUtils.setGreap(grep)
            listener?.let {
                it.onStartSearch(false, folder, query, null)
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
        }
    }

    private fun rememberQuery(text: String, files: Boolean) {
        if (text.isEmpty()) return
        suggestions?.addValue(text)
        database.addFindKeywordAndFiles(text, files)
    }

    private fun pickFolder() {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_directory))
            .setSelectMode(FilePickerDialog.MODE_SINGLE)
            .setSelectType(FilePickerDialog.TYPE_DIR)
            .setRootDir(ProjectUtils.getProjectPath())
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        binding?.path?.setText(filePaths[0])
                    }

                    override fun onCanceled() = Unit
                }
            )
            .show()
    }

    private fun askForExtension() {
        val input = DialogTextInputEBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_extensions)
            .setView(input.root)
            .setPositiveButton(R.string.ok) { _, _ -> addExtension(input.input.text?.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        input.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addExtension(input.input.text?.toString())
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    private fun addExtension(raw: String?) {
        val ext = raw?.trim().orEmpty()
        if (ext.isEmpty()) return
        extMap[ext] = true
        addExtensionChip(ext, true)
    }

    private fun addExtensionChip(ext: String, checked: Boolean) {
        val group = binding?.extGroup ?: return
        val chip = layoutInflater.inflate(R.layout.item_ext_chip_e, group, false) as Chip
        chip.text = ext
        chip.closeIconContentDescription = getString(R.string.m3e_extension_remove)
        chip.isChecked = checked
        val remove = View.OnClickListener {
            extMap.remove(ext)
            group.removeView(chip)
        }
        chip.setOnCloseIconClickListener(remove)
        chip.setOnLongClickListener {
            remove.onClick(chip)
            true
        }
        group.addView(chip)
        chip.springIn(fromScale = 0.8f)
    }

    /** Syncs [extMap] with the chips and returns the checked extensions. */
    private fun checkedExtensions(): ArrayList<String> {
        val group = binding?.extGroup ?: return ArrayList()
        val checked = ArrayList<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            val ext = chip.text.toString()
            extMap[ext] = chip.isChecked
            if (chip.isChecked) checked.add(ext)
        }
        return checked
    }

    private fun showMessage(textRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
        view?.let { Snackbar.make(it, textRes, duration).setAnchorView(binding?.fabGoSearch).show() }
    }

    fun interface ItemClickListener {
        fun onStartSearch(mode: Boolean, path: String?, searchText: String?, ext: ArrayList<String>?)
    }

    companion object {
        const val TAG = "SearchSettingsFragment"
    }
}
