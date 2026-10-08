package com.riyadm.apkrepacker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.ApktoolUiPrefs
import com.riyadm.apkrepacker.databinding.FragmentFilelistSimpleBinding
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.fragment.dialogs.ProgressDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ApkOptionsDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.DecompileOptionsDialogFragment
import com.riyadm.apkrepacker.task.ImportFrameworkTask
import com.riyadm.apkrepacker.task.SignTask
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.SignUtil
import java.io.File

/** "My Files": a general file manager with APK actions (decompile, edit, install, sign, ...). */
class MyFilesFragment : BaseFilesFragment(), ApkOptionsDialogFragment.ItemClickListener,
    DecompileOptionsDialogFragment.ItemClickListener, OnBackPressedListener {

    @JvmField
    var signedApk: File? = null

    private var binding: FragmentFilelistSimpleBinding? = null
    private var progressDialog: DialogFragment? = null
    private var signedMode = false

    override fun inflateScreen(inflater: LayoutInflater, container: ViewGroup?): Screen {
        val b = FragmentFilelistSimpleBinding.inflate(inflater, container, false)
        binding = b
        return Screen(b.root, b.filesPage, b.appBarFiles.pathBar, b.selectionBar, b.actionToolbar, b.fabMenu)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.appBarFiles?.filesToolbar?.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sd_card -> actionGotoSDCard()
                R.id.action_select_all -> fileAdapter?.selectAll()
                R.id.action_sort -> actionSort()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDataApplied() {
        val subtitle = when {
            mDirsCount > 0 && mFilesCount > 0 -> getString(
                R.string.files_subtitle_both,
                resources.getQuantityString(R.plurals.file_list_subtitle_directory_count_format, mDirsCount, mDirsCount),
                resources.getQuantityString(R.plurals.file_list_subtitle_file_count_format, mFilesCount, mFilesCount),
            )
            mFilesCount > 0 -> resources.getQuantityString(R.plurals.file_list_subtitle_file_count_format, mFilesCount, mFilesCount)
            mDirsCount > 0 -> resources.getQuantityString(R.plurals.file_list_subtitle_directory_count_format, mDirsCount, mDirsCount)
            else -> getString(R.string.this_folder_is_empty)
        }
        binding?.appBarFiles?.apply {
            filesCollapsing.subtitle = subtitle
            filesToolbar.menu.findItem(R.id.action_sd_card)?.apply {
                isVisible = mSdCard != null
                setTitle(if (mFlag) R.string.intenal_sd_card else R.string.action_sd_card)
            }
        }
        updateBreadcrumb()
    }

    override fun onSelectionModeChanged(active: Boolean) {
        // Fold the large header so the contextual bar sits exactly over the pinned toolbar.
        if (active) binding?.appBarFiles?.filesAppBar?.setExpanded(false, true)
    }

    override fun onPrepareOverflow(menu: Menu, file: File) {
        menu.findItem(R.id.action_open_in_editor).isVisible = false
        menu.findItem(R.id.action_copy_id).isVisible = false
    }

    // region Opening

    override fun onFileClick(item: FileHolder, position: Int) {
        val adapter = fileAdapter ?: return
        when {
            adapter.anySelected() -> adapter.toggle(position)
            !item.file.exists() -> Unit
            item.file.isDirectory -> openDir(item)
            item.file.isFile -> openFile(item)
        }
    }

    private fun openFile(holder: FileHolder) {
        if (holder.extension == "apk") {
            selectedApk = holder.file
            ApkOptionsDialogFragment.newInstance().show(childFragmentManager, ApkOptionsDialogFragment.TAG)
        } else {
            openWithSystem(holder.file)
        }
    }

    private fun openDir(holder: FileHolder) {
        // Avoid unnecessary attempts to load.
        if (holder.file.absolutePath == path) return
        navigateTo(holder.file)
    }

    // endregion

    // region APK actions

    override fun onApkItemClick(item: Int?) {
        val apk = selectedApk ?: return
        when (item) {
            R.id.decompile_app -> {
                if (ApktoolUiPrefs.askDecodeOptions(requireContext())) {
                    DecompileOptionsDialogFragment.newInstance()
                        .show(childFragmentManager, DecompileOptionsDialogFragment.TAG)
                } else {
                    startDecompile(ApktoolOptionsStore.loadDecodeOptions(requireContext()))
                }
            }
            R.id.simple_edit_apk -> FragmentUtils.add(
                SimpleEditorFragment.newInstance(apk.absolutePath),
                parentFragmentManager,
                R.id.fragment_container,
                SimpleEditorFragment.TAG,
            )
            R.id.install_app -> AppUtils.installApk(requireContext(), apk)
            R.id.sign_app -> {
                signedMode = true
                SignUtil.loadKey(requireContext()) { signTool ->
                    SignTask(requireContext(), this, signTool).execute(apk)
                }
            }
            R.id.set_as_framework_app -> ImportFrameworkTask(requireContext(), this).execute(apk)
            R.id.delete_item -> deleteApk(apk)
        }
    }

    private fun deleteApk(apk: File) {
        try {
            FileUtil.deleteFile(apk)
            snack(getString(R.string.toast_deleted_item, apk.name))
        } catch (e: Exception) {
            snack(R.string.error)
        }
    }

    override fun onModeItemClick(item: Int?) {
        val apk = selectedApk ?: return
        val mode = when (item) {
            R.id.decompile_all -> 3
            R.id.decompile_all_res -> 2
            R.id.decompile_all_dex -> 1
            else -> return
        }
        openDecompiler(DecompileFragment.newInstance(apk.absolutePath, mode))
    }

    override fun onDecodeOptionsChosen(options: DecodeOptions) {
        startDecompile(options)
    }

    private fun startDecompile(options: DecodeOptions) {
        val apk = selectedApk ?: return
        openDecompiler(DecompileFragment.newInstance(apk.absolutePath, options))
    }

    private fun openDecompiler(fragment: DecompileFragment) {
        FragmentUtils.add(fragment, parentFragmentManager, R.id.fragment_container, DecompileFragment.TAG)
    }

    // endregion

    // region Back

    override fun onBackPressed() {
        if (consumeBackForChrome()) return
        val current = path.orEmpty()
        val editor = parentFragmentManager.findFragmentByTag(SimpleEditorFragment.TAG)
        val decompile = parentFragmentManager.findFragmentByTag(DecompileFragment.TAG)
        val sdCard = mSdCard
        when {
            Utils.backWillExit(FileUtil.getInternalStorage().absolutePath, current) -> FragmentUtils.remove(this)
            sdCard != null && Utils.backWillExit(sdCard.absolutePath, current) -> FragmentUtils.remove(this)
            editor != null || decompile != null -> parentFragmentManager.popBackStack()
            else -> navigateTo(File(Utils.downDir(1, current)))
        }
    }

    // endregion

    // region Progress of sign / import-framework tasks

    fun showProgress() {
        val title = if (signedMode) R.string.dialog_sign else R.string.dialog_import
        val args = Bundle().apply {
            putString(ProgressDialogFragment.TITLE, getString(title))
            putString(ProgressDialogFragment.MESSAGE, getString(R.string.dialog_please_wait))
            putBoolean(ProgressDialogFragment.CANCELABLE, false)
        }
        progressDialog = ProgressDialogFragment.newInstance().also {
            it.arguments = args
            it.show(parentFragmentManager, ProgressDialogFragment.TAG)
        }
    }

    fun updateProgress(vararg values: Int?) {
        val value = values.firstOrNull() ?: return
        (parentFragmentManager.findFragmentByTag(ProgressDialogFragment.TAG) as? ProgressDialogFragment)
            ?.updateProgress(value)
    }

    fun hideProgress() {
        progressDialog?.dismissAllowingStateLoss()
        progressDialog = null
        if (signedMode) {
            selectedApk?.parentFile?.let { setPath(it) }
            snack(R.string.toast_sign_done)
            signedMode = false
        } else {
            snack(R.string.toast_import_framework_done)
        }
        refresh()
    }

    // endregion

    companion object {
        const val TAG = "MyFilesFragment"

        private var selectedApk: File? = null
    }
}
