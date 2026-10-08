package com.riyadm.apkrepacker.ui.projectview

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jecelyin.common.utils.IOUtils
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.FragmentFolderStructureBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.fragment.dialogs.CreateNewClass
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ProjectFileOptionDialog
import com.riyadm.apkrepacker.ui.filemanager.FileDialogs
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.projectview.treeview.adapter.RecyclerAdapter
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.FileChangeListener
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.ItemFileClickListener
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.common.DLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

/** The project's file tree (drawer of the code editors). */
class ProjectTreeStructureFragment :
    Fragment(R.layout.fragment_folder_structure),
    ItemFileClickListener,
    ProjectFileOptionDialog.ItemClickListener {

    private var binding: FragmentFolderStructureBinding? = null
    private var lastSelectedDir: File? = null
    private var parentListener: FileChangeListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parentListener = activity as? FileChangeListener
    }

    override fun onDetach() {
        parentListener = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentFolderStructureBinding.bind(view).also { binding = it }
        b.titleName.text = getString(R.string.project)
        b.projectName.text = ProjectUtils.getProjectName()
        b.popupMenu.setOnClickListener { loadFileSystem() }

        b.treeView.layoutManager = LinearLayoutManager(requireContext())
        b.treeView.applyExpressiveMotion()
        FastScrollerBuilder(b.treeView).useMd2Style().build()
        loadFileSystem()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun loadFileSystem() {
        val b = binding ?: return
        val adapter = RecyclerAdapter(requireContext()).also {
            it.setItemFileClickListener(this)
            it.setOnScrollToListener { position -> binding?.treeView?.scrollToPosition(position) }
        }
        b.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val roots = withContext(Dispatchers.IO) { adapter.getChildrenByPath(ProjectUtils.getProjectPath(), 0) }
            val current = binding ?: return@launch
            adapter.addAll(roots, 0)
            current.treeView.adapter = adapter
            current.progressBar.isVisible = false
        }
    }

    private fun treeAdapter() = binding?.treeView?.adapter as? RecyclerAdapter

    /** Refreshes the tree after [dir] changed: expanded folders re-read, the root reloads everything. */
    private fun onDirectoryChanged(dir: File?) {
        dir ?: return
        if (dir.absolutePath == ProjectUtils.getProjectPath()) loadFileSystem() else treeAdapter()?.reloadDirectory(dir.absolutePath)
    }

    override fun onFileClick(path: String) {
        parentListener?.doOpenFile(path)
    }

    override fun onFileLongClick(path: String) {
        val file = File(path)
        when {
            file.isFile -> showFileInfo(file)
            file.isDirectory -> {
                lastSelectedDir = file
                ProjectFileOptionDialog.newInstance().show(childFragmentManager, ProjectFileOptionDialog.TAG)
            }
        }
    }

    private fun showFileInfo(file: File) {
        val context = requireContext()
        val message = buildString {
            appendLine("${getString(R.string.tree_info_path)}: ${file.path}")
            appendLine("${getString(R.string.tree_info_size)}: ${Formatter.formatFileSize(context, file.length())}")
            append(
                "${getString(R.string.tree_info_modified)}: " +
                    DateUtils.formatDateTime(context, file.lastModified(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR),
            )
        }
        FileDialogs.info(context, file.name, message)
    }

    override fun onFileItemClick(item: Int?) {
        val dir = lastSelectedDir ?: return
        when (item) {
            R.id.create_class_file -> CreateNewClass(
                requireContext(),
                dir,
                object : CreateNewClass.OnFileCreatedListener {
                    override fun onCreateSuccess(file: File) {
                        parentListener?.onFileCreated(file)
                        onDirectoryChanged(dir)
                    }

                    override fun onCreateFailed(file: File, e: Exception) = Unit
                },
            ).show()
            R.id.create_xml_file -> FileDialogs.promptText(
                requireContext(),
                R.string.action_create_xml,
                validate = { name -> getString(R.string.tree_error_exists).takeIf { File(dir, xmlName(name)).exists() } },
            ) { name -> createXmlFile(xmlName(name), dir) }
            R.id.add_new_folder -> FileDialogs.promptText(
                requireContext(),
                R.string.action_create_new_folder,
                validate = { name -> getString(R.string.tree_error_exists).takeIf { File(dir, name).exists() } },
            ) { name -> createFolder(name, dir) }
            R.id.select_file -> selectFilesToCopy(dir)
        }
    }

    private fun xmlName(name: String) = if (name.endsWith(".xml")) name else "$name.xml"

    private fun createFolder(name: String, parent: File) {
        runCatching { FileUtil.createDirectory(parent, name) }
            .onSuccess {
                onDirectoryChanged(parent)
                showMessage(R.string.tree_folder_created)
            }
            .onFailure { e ->
                DLog.e(e)
                showMessage(R.string.toast_error_on_add_folder)
            }
    }

    private fun selectFilesToCopy(target: File) {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_directory))
            .setSelectMode(FilePickerDialog.MODE_MULTI)
            .setSelectType(FilePickerDialog.TYPE_ALL)
            .setRootDir(Environment.getExternalStorageDirectory().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        copyInto(target, filePaths)
                    }

                    override fun onCanceled() = Unit
                },
            )
            .show()
    }

    private fun copyInto(target: File, sources: Array<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) {
                sources.count { source ->
                    runCatching { FileUtil.copyFile(File(source), target) }
                        .onFailure { DLog.e(it) }
                        .isSuccess
                }
            }
            onDirectoryChanged(target)
            showMessage(getString(R.string.tree_items_added, copied))
        }
    }

    private fun createXmlFile(fileName: String, folder: File) {
        runCatching {
            val xmlFile = File(folder, fileName)
            xmlFile.parentFile?.mkdirs()
            IOUtils.writeFile(xmlFile, xmlTemplate(folder.name))
            xmlFile
        }.onSuccess { xmlFile ->
            parentListener?.onFileCreated(xmlFile)
            onDirectoryChanged(folder)
            showMessage(R.string.tree_file_created)
        }.onFailure { e ->
            DLog.e(e)
            showMessage(R.string.tree_error_create_file)
        }
    }

    /** Starter content matching the resource folder the file is created in. */
    private fun xmlTemplate(folderName: String): String {
        val header = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        return header + when {
            folderName.matches(Regex("^color(-v[0-9]+)?")) -> "<selector>\n</selector>"
            folderName.matches(Regex("^menu(-v[0-9]+)?")) ->
                "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n</menu>"
            folderName.matches(Regex("^values(-v[0-9]+)?")) -> "<resources>\n</resources>"
            folderName.matches(Regex("^layout(-v[0-9]+)?")) ->
                "<LinearLayout\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n</LinearLayout>"
            else -> ""
        }
    }

    private fun showMessage(message: Int) = showMessage(getString(message))

    private fun showMessage(message: String) {
        binding?.root?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    companion object {
        const val TAG = "ProjectTreeStructureFragment"
    }
}
