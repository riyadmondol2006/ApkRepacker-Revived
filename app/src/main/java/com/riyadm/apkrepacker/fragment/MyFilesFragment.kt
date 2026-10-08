package com.riyadm.apkrepacker.fragment

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.antisplit.AntiSplit
import com.riyadm.apkrepacker.antisplit.AntiSplitUi
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.ApktoolUiPrefs
import com.riyadm.apkrepacker.apktool.ui.DecodeOptionsArgs
import com.riyadm.apkrepacker.databinding.FragmentFilelistSimpleBinding
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.fragment.dialogs.ProgressDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.ApkOptionsDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.DecompileOptionsDialogFragment
import com.riyadm.apkrepacker.project.ProjectTransfer
import com.riyadm.apkrepacker.service.AntiSplitService
import com.riyadm.apkrepacker.task.ImportFrameworkTask
import com.riyadm.apkrepacker.task.SignTask
import com.riyadm.apkrepacker.ui.filemanager.FileFeedback
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.utils.Utils
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.SignUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** "My Files": a general file manager with APK actions (decompile, edit, install, sign, ...). */
class MyFilesFragment : BaseFilesFragment(), ApkOptionsDialogFragment.ItemClickListener,
    DecompileOptionsDialogFragment.ItemClickListener, OnBackPressedListener {

    @JvmField
    var signedApk: File? = null

    private var binding: FragmentFilelistSimpleBinding? = null
    private var progressDialog: DialogFragment? = null
    private var signedMode = false

    /** The split-APK archive waiting for the save picker. */
    private var pendingSaveArchive: String? = null

    /** Whether the pending save signs the APK. */
    private var pendingSaveSign = true

    /** [selectedApk] came out of a split-APK archive, so the decompiler must not merge splits again. */
    private var noMergeSplits = false

    /** Save jobs started here whose outcome has not been shown yet. */
    private val saveJobs = ArrayList<String>()

    private val saveArchiveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(APK_MIME)) { uri ->
            val archive = pendingSaveArchive?.let(::File)
            pendingSaveArchive = null
            if (uri != null && archive != null) startArchiveSave(archive, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            pendingSaveArchive = state.getString(STATE_PENDING_SAVE)
            pendingSaveSign = state.getBoolean(STATE_PENDING_SAVE_SIGN, true)
            noMergeSplits = state.getBoolean(STATE_NO_MERGE)
            state.getStringArrayList(STATE_SAVE_JOBS)?.let { saveJobs.addAll(it) }
            if (selectedApk == null) selectedApk = state.getString(STATE_SELECTED_APK)?.let(::File)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_SAVE, pendingSaveArchive)
        outState.putBoolean(STATE_PENDING_SAVE_SIGN, pendingSaveSign)
        outState.putBoolean(STATE_NO_MERGE, noMergeSplits)
        outState.putStringArrayList(STATE_SAVE_JOBS, ArrayList(saveJobs))
        outState.putString(STATE_SELECTED_APK, selectedApk?.absolutePath)
    }

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
        childFragmentManager.setFragmentResultListener(KEY_ARCHIVE_DECOMPILE, viewLifecycleOwner) { _, result ->
            preparedApk(result)?.let { apk ->
                selectedApk = apk
                noMergeSplits = true
                decompileSelected()
            }
        }
        childFragmentManager.setFragmentResultListener(KEY_ARCHIVE_EDIT, viewLifecycleOwner) { _, result ->
            preparedApk(result)?.let { openSimpleEditor(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Subscribe first, then catch up on jobs that finished while the screen was away.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    AntiSplitService.results.collect { r -> if (r.jobId in saveJobs) onSaveFinished(r) }
                }
                saveJobs.toList().forEach { id -> AntiSplitService.resultOf(id)?.let(::onSaveFinished) }
            }
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
        val file = holder.file
        when (file.extension.lowercase(Locale.ROOT)) {
            "apk" -> {
                selectedApk = file
                noMergeSplits = false
                ApkOptionsDialogFragment.newInstance(subtitle = file.name).show(childFragmentManager, ApkOptionsDialogFragment.TAG)
            }
            in AntiSplit.ARCHIVE_EXTENSIONS -> showArchiveOptions(file)
            // A .zip is only an archive of splits when it holds several APKs; looking needs I/O.
            "zip" -> viewLifecycleOwner.lifecycleScope.launch {
                val isArchive = withContext(Dispatchers.IO) { AntiSplit.isSplitArchive(file) }
                if (!isAdded) return@launch
                if (isArchive) showArchiveOptions(file) else openWithSystem(file)
            }
            else -> openWithSystem(file)
        }
    }

    private fun openDir(holder: FileHolder) {
        // Avoid unnecessary attempts to load.
        if (holder.file.absolutePath == path) return
        navigateTo(holder.file)
    }

    // endregion

    // region Split-APK archives (.apks, .xapk, .apkm, .zip of APKs)

    private fun showArchiveOptions(archive: File) {
        // Label + icon, in the order the click handler below expects.
        val items = listOf(
            R.string.antisplit_archive_save to R.drawable.ic_save,
            R.string.antisplit_archive_decompile to R.drawable.ic_decompile,
            R.string.antisplit_archive_edit to R.drawable.ic_rename,
            R.string.antisplit_archive_base_decompile to R.drawable.ic_decompile,
            R.string.antisplit_archive_base_edit to R.drawable.ic_rename,
        )
        val builder = MaterialAlertDialogBuilder(requireContext())
        // Inflate with the dialog's themed context so the rows pick up its list paddings.
        val adapter = object : ArrayAdapter<Pair<Int, Int>>(builder.context, R.layout.item_archive_option, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_archive_option, parent, false)
                val (label, icon) = items[position]
                row.findViewById<TextView>(R.id.archive_option_label).setText(label)
                row.findViewById<ImageView>(R.id.archive_option_icon).setImageResource(icon)
                return row
            }
        }
        val dialog = builder
            .setTitle(getString(R.string.antisplit_archive_title, archive.name))
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> AntiSplitUi.askSign(this) { sign -> askSaveArchive(archive, sign) }
                    1 -> prepareArchive(archive, KEY_ARCHIVE_DECOMPILE, baseOnly = false)
                    2 -> prepareArchive(archive, KEY_ARCHIVE_EDIT, baseOnly = false)
                    3 -> prepareArchive(archive, KEY_ARCHIVE_DECOMPILE, baseOnly = true)
                    4 -> prepareArchive(archive, KEY_ARCHIVE_EDIT, baseOnly = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // The stock title is single-line, so a long archive name would cut off the "(split APK archive)" hint.
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            setSingleLine(false)
            maxLines = ARCHIVE_TITLE_MAX_LINES
        }
    }

    private fun askSaveArchive(archive: File, sign: Boolean) {
        pendingSaveArchive = archive.absolutePath
        pendingSaveSign = sign
        try {
            saveArchiveLauncher.launch(AntiSplitUi.apkFileName(ProjectTransfer.safeName(archive.nameWithoutExtension), sign))
        } catch (e: ActivityNotFoundException) {
            pendingSaveArchive = null
            snack(R.string.error)
        }
    }

    private fun startArchiveSave(archive: File, uri: Uri) {
        val context = context ?: return
        val jobId = AntiSplitService.save(context, AntiSplitService.Source.Archive(archive), uri, pendingSaveSign)
        saveJobs += jobId
        snack(R.string.antisplit_started)
        // A job that could not even start has already reported its outcome.
        AntiSplitService.resultOf(jobId)?.let(::onSaveFinished)
    }

    private fun onSaveFinished(result: AntiSplitService.Result) {
        if (!saveJobs.remove(result.jobId)) return
        val message = (listOf(result.message) + result.warnings).joinToString("\n")
        FileFeedback.show(message, Snackbar.LENGTH_LONG)
    }

    private fun prepareArchive(archive: File, requestKey: String, baseOnly: Boolean) {
        AntiSplitUi.prepare(
            this,
            AntiSplitService.Source.Archive(archive),
            requestKey,
            archive.nameWithoutExtension,
            baseOnly,
        )
    }

    /** The APK an [AntiSplitUi.prepare] result points at, or null after telling the user why not. */
    private fun preparedApk(result: Bundle): File? {
        val path = result.getString(AntiSplitUi.RESULT_APK)
        if (result.getBoolean(AntiSplitUi.RESULT_OK) && !path.isNullOrEmpty()) return File(path)
        val message = result.getString(AntiSplitUi.RESULT_MESSAGE)
        if (message.isNullOrBlank()) snack(R.string.error) else FileFeedback.show(message, Snackbar.LENGTH_LONG)
        return null
    }

    // endregion

    // region APK actions

    override fun onApkItemClick(item: Int?) {
        val apk = selectedApk ?: return
        when (item) {
            R.id.decompile_app -> decompileSelected()
            R.id.simple_edit_apk -> openSimpleEditor(apk)
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

    /** Decompiles [selectedApk]: asks for the options first when the user wants that. */
    private fun decompileSelected() {
        if (selectedApk == null) return
        if (ApktoolUiPrefs.askDecodeOptions(requireContext())) {
            DecompileOptionsDialogFragment.newInstance()
                .show(childFragmentManager, DecompileOptionsDialogFragment.TAG)
        } else {
            startDecompile(ApktoolOptionsStore.loadDecodeOptions(requireContext()))
        }
    }

    private fun openSimpleEditor(apk: File) {
        FragmentUtils.add(
            SimpleEditorFragment.newInstance(apk.absolutePath),
            parentFragmentManager,
            R.id.fragment_container,
            SimpleEditorFragment.TAG,
        )
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
        if (noMergeSplits) {
            val stored = ApktoolOptionsStore.loadDecodeOptions(requireContext())
            startDecompile(stored.copy(mode = DecodeOptionsArgs.modeFromLegacyAction(mode)))
        } else {
            openDecompiler(DecompileFragment.newInstance(apk.absolutePath, mode))
        }
    }

    override fun onDecodeOptionsChosen(options: DecodeOptions) {
        startDecompile(options)
    }

    private fun startDecompile(options: DecodeOptions) {
        val apk = selectedApk ?: return
        // An APK made from a split archive already holds its splits (or is meant to be the base alone).
        val effective = if (noMergeSplits) options.copy(mergeSplits = false) else options
        openDecompiler(DecompileFragment.newInstance(apk.absolutePath, effective))
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

        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val KEY_ARCHIVE_DECOMPILE = "myFiles.archiveDecompile"
        private const val KEY_ARCHIVE_EDIT = "myFiles.archiveEdit"
        private const val STATE_PENDING_SAVE = "pendingSaveArchive"
        private const val STATE_PENDING_SAVE_SIGN = "pendingSaveSign"
        private const val STATE_NO_MERGE = "noMergeSplits"
        private const val STATE_SAVE_JOBS = "saveJobs"
        private const val STATE_SELECTED_APK = "selectedApk"
        private const val ARCHIVE_TITLE_MAX_LINES = 3

        private var selectedApk: File? = null
    }
}
