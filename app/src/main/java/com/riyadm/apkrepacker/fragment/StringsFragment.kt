package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.autotranslator.translator.TranslateItem
import com.riyadm.apkrepacker.databinding.DialogSingleInputBinding
import com.riyadm.apkrepacker.databinding.DialogTranslateProgressBinding
import com.riyadm.apkrepacker.databinding.FragmentAppStringsBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.AddLanguageDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.AddNewStringDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.StringsOptionsDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.TranslateOptionsDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.TranslateStringDialogFragment
import com.riyadm.apkrepacker.task.TranslateDictionaryTask
import com.riyadm.apkrepacker.task.TranslateTask
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.resourceeditor.ResourceSearch
import com.riyadm.apkrepacker.ui.resourceeditor.shrinkFabOnScroll
import com.riyadm.apkrepacker.ui.resourceeditor.showSnack
import com.riyadm.apkrepacker.ui.stringlist.StringFile
import com.riyadm.apkrepacker.ui.stringlist.StringsAdapter
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.IntentUtils
import com.riyadm.apkrepacker.viewmodel.StringFragmentViewModel
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder

/** Strings tab of the project editor: translate, add and save the `strings.xml` of any language. */
class StringsFragment : Fragment(R.layout.fragment_app_strings),
    AddLanguageDialogFragment.ItemClickListener,
    StringsAdapter.OnItemClickListener,
    TranslateStringDialogFragment.ItemClickListener,
    StringsOptionsDialogFragment.ItemClickListener,
    AddNewStringDialogFragment.ItemClickListener,
    TranslateOptionsDialogFragment.ItemClickListener {

    private val viewModel: StringFragmentViewModel by viewModels()

    private var binding: FragmentAppStringsBinding? = null
    private var mainAdapter: StringsAdapter? = null
    private var searchAdapter: StringsAdapter? = null
    private var search: ResourceSearch? = null

    private var languageFiles: List<StringFile> = emptyList()
    private var languageLabels: List<String> = emptyList()
    private var applyingViewModelData = false

    private var progressDialog: AlertDialog? = null
    private var progressContent: DialogTranslateProgressBinding? = null

    /** Used by the translation tasks to push results into the list. */
    val stringsAdapter: StringsAdapter?
        get() = mainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.startLoad()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = FragmentAppStringsBinding.bind(view)
        binding = views

        val main = StringsAdapter().apply { setInteractionListener(this@StringsFragment) }
        val results = StringsAdapter().apply { setInteractionListener(this@StringsFragment) }
        main.onChanged = {
            results.setItems(main.data)
            if (!applyingViewModelData) viewModel.hasUnsavedChanges = true
        }
        mainAdapter = main
        searchAdapter = results

        setupList(views.stringList, main)
        FastScrollerBuilder(views.stringList).useMd2Style().build()
        setupList(views.searchResults, results)
        views.stringList.shrinkFabOnScroll(views.fabSaveLanguage)

        search = ResourceSearch(views.searchBar, views.searchView, viewModel::filter)

        views.fabSaveLanguage.setOnClickListener { viewModel.saveAll() }
        views.fabMoreOptions.setOnClickListener {
            StringsOptionsDialogFragment.newInstance().show(childFragmentManager, StringsOptionsDialogFragment.TAG)
        }
        views.langDropdown.setOnItemClickListener { _, _, position, _ -> onLanguagePicked(position) }

        observeViewModel(views)
    }

    private fun setupList(list: RecyclerView, adapter: StringsAdapter) {
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        list.applyExpressiveMotion()
    }

    private fun observeViewModel(views: FragmentAppStringsBinding) {
        viewModel.stingsData.observe(viewLifecycleOwner) { items ->
            applyingViewModelData = true
            mainAdapter?.setItems(items)
            applyingViewModelData = false
            updateEmptyState()
        }
        viewModel.loading.observe(viewLifecycleOwner) { updateEmptyState() }
        viewModel.stingsFilesData.observe(viewLifecycleOwner) { files ->
            languageFiles = files
            languageLabels = files.map(::labelOf)
            views.langDropdown.setAdapter(
                ArrayAdapter(requireContext(), com.google.android.material.R.layout.m3_auto_complete_simple_item, languageLabels),
            )
            showCurrentLanguage()
        }
        viewModel.currentLanguage.observe(viewLifecycleOwner) { showCurrentLanguage() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { views.root.showSnack(getString(it), views.fabCluster) }
            }
        }
    }

    private fun updateEmptyState() {
        val views = binding ?: return
        val loading = viewModel.loading.value == true
        val empty = mainAdapter?.data.isNullOrEmpty()
        views.loading.isVisible = loading && empty
        views.emptyText.isVisible = !loading && empty
        views.emptyText.setText(if (search?.query.isNullOrEmpty()) R.string.h_empty_strings else R.string.h_no_results)
    }

    private fun labelOf(file: StringFile): String {
        val code = file.lang().orEmpty()
        val locale = file.locale()
        return if (locale != null) "${locale.displayName} ($code)" else code
    }

    private fun showCurrentLanguage() {
        val current = viewModel.currentLanguage.value ?: return
        val index = languageFiles.indexOfFirst { it.lang() == current }
        binding?.langDropdown?.setText(languageLabels.getOrNull(index) ?: current, false)
    }

    private fun onLanguagePicked(position: Int) {
        val target = languageFiles.getOrNull(position) ?: return
        if (target.lang() == viewModel.currentLangFile?.lang()) return
        if (!viewModel.hasUnsavedChanges) {
            viewModel.parseStings(target)
            return
        }
        promptUnsaved(
            onSave = { viewModel.saveAll(openAfterSave = target) },
            onDiscard = { viewModel.parseStings(target) },
            onCancel = ::showCurrentLanguage,
        )
    }

    /** Save / discard / cancel before anything that would replace the strings being edited. */
    private fun promptUnsaved(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit = {}) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.h_unsaved_title)
            .setMessage(R.string.h_unsaved_message)
            .setPositiveButton(R.string.save) { _, _ -> onSave() }
            .setNegativeButton(R.string.h_action_discard) { _, _ ->
                viewModel.hasUnsavedChanges = false
                onDiscard()
            }
            .setNeutralButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    override fun onAddLangClick(code: String, autotranslate: Boolean, skipTranslated: Boolean, skipSupport: Boolean) {
        val adapter = mainAdapter ?: return
        if (!autotranslate) {
            viewModel.addNewLang(code)
            return
        }
        TranslateTask(adapter.data, this).apply {
            setTargetLanguageCode(code)
            setSkipSupport(skipSupport)
            setSkipTranslated(skipTranslated)
            execute()
        }
    }

    override fun onTranslateSting(code: String) {
        // Single-string translation is handled by the string sheet itself.
    }

    override fun onTranslateClicked(item: TranslateItem, position: Int) {
        TranslateStringDialogFragment.newInstance(item, position).show(childFragmentManager, TranslateStringDialogFragment.TAG)
    }

    override fun onValueEdited(item: TranslateItem, position: Int) {
        viewModel.hasUnsavedChanges = true
    }

    override fun onNameCopied(name: String) {
        binding?.root?.showSnack(getString(R.string.string_name_copied, name), binding?.fabCluster)
    }

    /** Result of the string sheet: the edited translation of the row at [key]. */
    override fun onTranslateClicked(value: String, key: Int) {
        viewModel.hasUnsavedChanges = true
        mainAdapter?.setUpdateValue(value, key)
    }

    override fun onDeleteString(key: Int) {
        mainAdapter?.data?.getOrNull(key)?.let { viewModel.deleteString(it) }
    }

    override fun onItemClick(item: Int) {
        when (item) {
            R.id.open_with -> viewModel.currentLangFile?.let { file ->
                IntentUtils.openFileWithIntent(file)?.let(::startActivity)
            }
            R.id.add_new_string ->
                AddNewStringDialogFragment.newInstance().show(childFragmentManager, AddNewStringDialogFragment.TAG)
            R.id.add_new_language -> {
                val showSheet = {
                    AddLanguageDialogFragment.newInstance(false, false).show(childFragmentManager, AddLanguageDialogFragment.TAG)
                }
                if (viewModel.hasUnsavedChanges) {
                    promptUnsaved(onSave = { viewModel.saveAll(onSaved = showSheet) }, onDiscard = showSheet)
                } else {
                    showSheet()
                }
            }
            R.id.auto_translate_language ->
                AddLanguageDialogFragment.newInstance(true, false).show(childFragmentManager, AddLanguageDialogFragment.TAG)
            R.id.auto_translate_language_with -> pickDictionary()
            R.id.save_as_dictionary -> askDictionaryName()
        }
    }

    private fun pickDictionary() {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_translate_dictionary))
            .setSelectMode(FilePickerDialog.MODE_SINGLE)
            .setSelectType(FilePickerDialog.TYPE_FILE)
            .setExtensions(arrayOf("mtd"))
            .setRootDir(FileUtil.getInternalStorage().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        TranslateOptionsDialogFragment.newInstance(filePaths[0])
                            .show(childFragmentManager, TranslateOptionsDialogFragment.TAG)
                    }

                    override fun onCanceled() = Unit
                },
            )
            .show()
    }

    private fun askDictionaryName() {
        val input = DialogSingleInputBinding.inflate(layoutInflater)
        input.inputLayout.hint = getString(R.string.dialog_dictionary_name)
        input.inputEdit.setText(DEFAULT_DICTIONARY_NAME)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_save_as_dictionary)
            .setView(input.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.inputEdit.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                input.inputLayout.error = getString(R.string.cannot_be_empty)
            } else {
                viewModel.saveTranslationToDictionary(name, mainAdapter?.data.orEmpty())
                dialog.dismiss()
            }
        }
    }

    override fun onAddStringClicked(key: String, value: String) {
        viewModel.addNewString(TranslateItem(key, value))
    }

    /** Dictionary options confirmed: runs the dictionary translation over the visible strings. */
    override fun onOkClick(path: String?) {
        val helper = PreferenceHelper.getInstance(requireContext())
        TranslateDictionaryTask(this).apply {
            setDictionaryPath(path)
            setTranslateItems(mainAdapter?.data.orEmpty())
            setReverseDictionary(helper.isReverseDictionary)
            setSkipSupport(helper.isSkipSupportLines)
            setSkipTranslated(helper.isSkipTranslated)
            execute()
        }
    }

    fun showProgress() {
        val host = context ?: return
        hideProgress()
        val content = DialogTranslateProgressBinding.inflate(layoutInflater)
        progressContent = content
        progressDialog = MaterialAlertDialogBuilder(host)
            .setTitle(R.string.translating_run_title)
            .setView(content.root)
            .setCancelable(false)
            .show()
    }

    fun updateProgress(vararg values: Int?) {
        val percent = values.firstOrNull() ?: return
        val content = progressContent ?: return
        content.progressBar.apply {
            isIndeterminate = false
            max = 100
            setProgressCompat(percent.coerceIn(0, 100), true)
        }
        content.progressPercent.isVisible = true
        content.progressPercent.text = getString(R.string.h_progress_percent, percent)
    }

    fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressContent = null
    }

    override fun onDestroyView() {
        hideProgress()
        search = null
        mainAdapter = null
        searchAdapter = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "StringsFragment"
        private const val DEFAULT_DICTIONARY_NAME = "Default"
    }
}
