package com.riyadm.apkrepacker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.AppEditorActivity
import com.riyadm.apkrepacker.activity.CodeEditorActivity
import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.databinding.FragmentFilesBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.ui.imageviewer.ImageViewerActivity
import com.riyadm.apkrepacker.ui.publicxml.PublicXmlParser
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.sdsmdg.harjot.vectormaster.VectorMasterDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The "Files" page of the project editor: browses the project folder and opens files in the editors. */
class FilesFragment : BaseFilesFragment(), OnBackPressedListener {

    private var projectPath: String? = null
    private var xmlParser: PublicXmlParser? = null

    override val projectMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectPath = arguments?.getString("prjPatch")
    }

    override fun initialPath(): File = File(projectPath ?: ProjectUtils.getProjectPath().orEmpty())

    override fun inflateScreen(inflater: LayoutInflater, container: ViewGroup?): Screen {
        val binding = FragmentFilesBinding.inflate(inflater, container, false)
        return Screen(binding.root, binding.filesPage, binding.pathBar, binding.selectionBar, binding.actionToolbar, binding.fabMenu)
    }

    override fun onDataApplied() {
        ProjectUtils.setCurrentPath(path)
        updateBreadcrumb()
    }

    override fun onBackPressed() {
        if (consumeBackForChrome()) return
        childFragmentManager.findFragmentByTag(ColorEditorFragment.TAG)?.let {
            childFragmentManager.popBackStack()
            return
        }
        val current = path.orEmpty()
        val root = projectPath.orEmpty()
        if (Utils.backWillExit(root, current)) {
            requireActivity().finish()
        } else {
            navigateTo(File(Utils.downDir(1, current)))
        }
    }

    override fun onFileClick(item: FileHolder, position: Int) {
        val adapter = fileAdapter ?: return
        if (position < 0) return
        if (adapter.anySelected()) {
            adapter.toggle(position)
            return
        }
        val file = item.file
        if (file.isDirectory) {
            if (file.canRead()) navigateTo(file) else snack(R.string.cannt_open_directory)
            return
        }
        openFile(file, adapter)
    }

    private fun openFile(file: File, adapter: com.riyadm.apkrepacker.ui.filemanager.FileAdapter) {
        when (FileUtil.FileType.getFileType(file)) {
            FileUtil.FileType.TXT,
            FileUtil.FileType.SMALI,
            FileUtil.FileType.JS,
            FileUtil.FileType.JSON,
            FileUtil.FileType.HTM,
            FileUtil.FileType.HTML,
            FileUtil.FileType.INI,
            FileUtil.FileType.XML -> openTextual(file, adapter)
            FileUtil.FileType.IMAGE -> showImage(file, adapter)
            else -> openWithSystem(file)
        }
    }

    private fun openTextual(file: File, adapter: com.riyadm.apkrepacker.ui.filemanager.FileAdapter) {
        val directory = File(path.orEmpty())
        val inResources = directory.name.startsWith("drawable") || directory.name.startsWith("mipmap")
        when {
            file.name.startsWith("colors") -> {
                childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ColorEditorFragment.newInstance(file.absolutePath), ColorEditorFragment.TAG)
                    .addToBackStack(null)
                    .commit()
            }
            inResources && VectorMasterDrawable(requireContext(), file).isVector -> showImage(file, adapter)
            else -> startActivity(
                Intent(activity, TextEditorActivity::class.java)
                    .putExtra("filePath", file.absolutePath)
                    .putExtra("currentDirectory", directory.absolutePath),
            )
        }
    }

    private fun showImage(file: File, adapter: com.riyadm.apkrepacker.ui.filemanager.FileAdapter) {
        ImageViewerActivity.setViewerData(context, adapter, file)
        startActivity(Intent(activity, ImageViewerActivity::class.java))
    }

    override fun onFabItemClick(id: Int) {
        when (id) {
            R.id.fab_search -> AppEditorActivity.getInstance()?.mViewPager?.setCurrentItem(SEARCH_PAGE)
            R.id.fab_copy_folder -> importFromStorage(
                title = R.string.select_directory,
                multiple = false,
                type = FilePickerDialog.TYPE_DIR,
            )
            R.id.fab_copy_file -> importFromStorage(
                title = R.string.select_file,
                multiple = true,
                type = FilePickerDialog.TYPE_FILE,
            )
            else -> super.onFabItemClick(id)
        }
    }

    /** Lets the user pick files or folders anywhere on storage and copies them into the current folder. */
    private fun importFromStorage(title: Int, multiple: Boolean, type: Int) {
        val destination = File(path.orEmpty())
        FilePickerDialog(requireContext())
            .setTitleText(getString(title))
            .setSelectMode(if (multiple) FilePickerDialog.MODE_MULTI else FilePickerDialog.MODE_SINGLE)
            .setSelectType(type)
            .setRootDir(FileUtil.getInternalStorage().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        copyInto(destination, filePaths)
                    }

                    override fun onCanceled() = Unit
                },
            )
            .show()
    }

    private fun copyInto(destination: File, sources: Array<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val failed = withContext(Dispatchers.IO) {
                sources.count { source ->
                    runCatching { FileUtil.copyFile(File(source), destination) }.isFailure
                }
            }
            if (failed > 0) snack(R.string.error)
            refresh()
        }
    }

    override fun onPrepareOverflow(menu: Menu, file: File) {
        menu.findItem(R.id.action_open_in_editor).isVisible = file.isFile
        menu.findItem(R.id.action_copy_id).isVisible =
            file.isFile && file.absolutePath.contains("$projectPath/res/")
    }

    override fun onOverflowItemClick(item: MenuItem, file: File): Boolean {
        when (item.itemId) {
            R.id.action_open_in_editor ->
                startActivity(Intent(activity, CodeEditorActivity::class.java).putExtra("filePath", file.absolutePath))
            R.id.action_copy_id -> copyResourceId(file)
            else -> return false
        }
        return true
    }

    private fun copyResourceId(file: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            val parser = xmlParser ?: withContext(Dispatchers.IO) {
                PublicXmlParser(File("$projectPath/res/values/public.xml"))
            }.also { xmlParser = it }
            parser.getIdByName(FileUtil.removeExtension(file.name))?.let { copyText(it) }
        }
    }

    private companion object {
        const val SEARCH_PAGE = 3
    }
}
