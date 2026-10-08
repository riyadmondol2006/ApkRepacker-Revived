package com.riyadm.apkrepacker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.databinding.FragmentFindListBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.FindFileOptionDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ReplaceInFileDialogFragment
import com.riyadm.apkrepacker.model.SearchFinder
import com.riyadm.apkrepacker.task.SearchFilesTask
import com.riyadm.apkrepacker.task.SearchStringsTask
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder
import com.riyadm.apkrepacker.ui.findresult.FoundStringsAdapter
import com.riyadm.apkrepacker.ui.findresult.ParentData
import com.riyadm.apkrepacker.ui.findresult.ParentViewHolder
import com.riyadm.apkrepacker.ui.findresult.files.SearchListAdapter
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.IntentUtils
import com.riyadm.apkrepacker.utils.StringUtils
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

/** Project-wide search: file names or text inside files, with a result list and bulk replace. */
class FindFragment : Fragment(),
    FindFileOptionDialogFragment.ItemClickListener,
    FileListViewHolder.OnItemClickListener,
    ParentViewHolder.ItemClickListener,
    ReplaceInFileDialogFragment.OnReplacedInterface,
    SearchSettingsFragment.ItemClickListener,
    OnBackPressedListener {

    private var binding: FragmentFindListBinding? = null

    private var mHolder: ParentViewHolder? = null
    private var searchText: String? = null
    private var stringFiles: ArrayList<String>? = ArrayList()
    private var selectedFile: File? = null
    private var filesAdapter: SearchListAdapter? = null
    private var stringsAdapter: FoundStringsAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFindListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.actionSearch.setOnClickListener { openSearchSettings() }
        b.actionFindReplace.setOnClickListener { openReplaceInFiles() }
        b.actionClear.setOnClickListener { clearResults() }

        b.findList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            applyExpressiveMotion()
            FastScrollerBuilder(this).useMd2Style().build()
        }
    }

    override fun onDestroyView() {
        binding = null
        filesAdapter = null
        stringsAdapter = null
        super.onDestroyView()
    }

    fun setResult(fileList: List<File>) {
        val b = binding ?: return
        b.actionFindReplace.springVisible(false)
        b.findCount(fileList.size)
        if (fileList.isEmpty()) {
            b.findList.adapter = null
            showEmpty(true)
            b.actionClear.springVisible(false)
            notifyNotFound()
            return
        }
        val adapter = SearchListAdapter(this).also { filesAdapter = it }
        stringsAdapter = null
        adapter.notifyDataUpdated(fileList.toMutableList())
        b.findList.adapter = adapter
        showEmpty(false)
        b.actionClear.springVisible(true)
    }

    fun setStringResult(parentList: List<ParentData>, files: ArrayList<String>?) {
        val b = binding ?: return
        b.findCount(parentList.size)
        if (parentList.isEmpty()) {
            b.findList.adapter = null
            showEmpty(true)
            b.actionClear.springVisible(false)
            b.actionFindReplace.springVisible(false)
            notifyNotFound()
            return
        }
        stringFiles = files
        val adapter = FoundStringsAdapter(requireContext(), this, parentList).also { stringsAdapter = it }
        filesAdapter = null
        b.findList.adapter = adapter
        showEmpty(false)
        b.actionClear.springVisible(true)
        b.actionFindReplace.springVisible(true)
    }

    /** Shows the indeterminate wait while a search task runs. */
    fun showProgress() {
        binding?.findLoading?.let { overlay ->
            if (!overlay.isVisible) overlay.springIn(fromScale = 0.96f)
        }
    }

    /** Searches are indeterminate; progress ticks from the tasks are ignored. */
    @Suppress("UNUSED_PARAMETER")
    fun updateProgress(vararg values: Int?) = Unit

    fun hideProgress() {
        binding?.findLoading?.let { overlay ->
            if (overlay.isVisible) overlay.springOut(toScale = 0.96f) { overlay.visibility = View.GONE }
        }
    }

    private fun FragmentFindListBinding.findCount(count: Int) {
        searchResultCount.text = getString(R.string.search_result, count, searchText)
    }

    private fun showEmpty(empty: Boolean) {
        binding?.findEmpty?.let { if (empty != it.isVisible) it.springVisible(empty) }
    }

    private fun notifyNotFound() {
        view?.let { Snackbar.make(it, R.string.find_not_found, Snackbar.LENGTH_SHORT).show() }
    }

    private fun View.springVisible(visible: Boolean) {
        if (visible) {
            if (!isVisible) springIn()
        } else if (isVisible) {
            springOut { visibility = View.GONE }
        }
    }

    private fun openSearchSettings() {
        val searchFragment = SearchSettingsFragment()
        searchFragment.setItemClickListener(this)
        FragmentUtils.add(searchFragment, childFragmentManager, R.id.fragment_container, SearchSettingsFragment.TAG)
    }

    private fun openReplaceInFiles() {
        ReplaceInFileDialogFragment.newInstance(searchText, stringFiles).also {
            it.setItemClickListener(this)
            it.show(parentFragmentManager, ReplaceInFileDialogFragment.TAG)
        }
    }

    private fun clearResults() {
        val b = binding ?: return
        filesAdapter?.clear()
        stringsAdapter?.clearData()
        filesAdapter = null
        stringsAdapter = null
        b.findList.adapter = null
        b.searchResultCount.text = ""
        b.actionClear.springVisible(false)
        b.actionFindReplace.springVisible(false)
        showEmpty(true)
    }

    override fun onFileItemClick(item: Int) {
        val file = selectedFile ?: return
        when (item) {
            R.id.open_with -> IntentUtils.openFileWithIntent(file)?.let { startActivity(it) }
            R.id.open_in_editor -> openInEditor(file)
            R.id.copy_path -> StringUtils.setClipboard(requireContext(), file.absolutePath, true)
            R.id.replace_in_file -> {
                ReplaceInFileDialogFragment.newInstance(searchText, file.absolutePath).also {
                    it.setItemClickListener(this)
                    it.show(parentFragmentManager, ReplaceInFileDialogFragment.TAG)
                }
            }
        }
    }

    override fun onTitleClick(file: String, id: Int, holder: ParentViewHolder) {
        selectedFile = File(file)
        mHolder = holder
        FindFileOptionDialogFragment.newInstance().also {
            it.setItemClickListener(this)
            it.setIsStringMode(true)
            it.show(parentFragmentManager, FindFileOptionDialogFragment.TAG)
        }
    }

    override fun onReplaced() {
        mHolder?.changeTextColor()
    }

    override fun onFileClick(item: FileHolder, position: Int) {
        val file = item.file
        when (FileUtil.FileType.getFileType(file)) {
            FileUtil.FileType.TXT,
            FileUtil.FileType.SMALI,
            FileUtil.FileType.JS,
            FileUtil.FileType.JSON,
            FileUtil.FileType.HTM,
            FileUtil.FileType.HTML,
            FileUtil.FileType.INI,
            FileUtil.FileType.XML -> openInEditor(file)
            else -> IntentUtils.openFileWithIntent(file)?.let { startActivity(it) }
        }
    }

    override fun onLongClick(item: FileHolder, position: Int) {
        selectedFile = item.file
        FindFileOptionDialogFragment.newInstance().also {
            it.setItemClickListener(this)
            it.show(childFragmentManager, FindFileOptionDialogFragment.TAG)
        }
    }

    private fun openInEditor(file: File) {
        startActivity(Intent(requireContext(), CodeEditorActivity::class.java).putExtra("filePath", file.absolutePath))
    }

    override fun onStartSearch(filesMode: Boolean, path: String?, searchText: String?, ext: ArrayList<String>?) {
        this.searchText = searchText
        if (filesMode) {
            SearchFilesTask(requireContext(), this, SearchFinder()).apply {
                setArguments(path, searchText, ArrayList(ext.orEmpty()))
                execute()
            }
        } else {
            SearchStringsTask(requireContext(), this).execute()
        }
    }

    override fun onBackPressed() {
        if (childFragmentManager.findFragmentByTag(SearchSettingsFragment.TAG) != null) {
            childFragmentManager.popBackStack()
        } else {
            requireActivity().finish()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): FindFragment = FindFragment()
    }
}
