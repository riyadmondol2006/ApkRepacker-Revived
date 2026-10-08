package com.riyadm.apkrepacker.fragment

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.adapter.PatchAdapter
import com.riyadm.apkrepacker.adapter.PatchItem
import com.riyadm.apkrepacker.databinding.FragmentPatcherBinding
import com.riyadm.apkrepacker.filepicker.FilePickerDialog
import com.riyadm.apkrepacker.service.PatchService
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.utils.BatteryOptimizationHelper
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.patchengine.PatchInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Applies one or more `.zip` patches to the open project. The work runs in [PatchService] (a
 * foreground service); this screen binds to it and streams the log + progress, so patching no
 * longer blocks the UI and survives rotation/backgrounding. Before patches that edit code are
 * applied to a project with no smali, the user is asked whether to decompile it first.
 */
class PatcherFragment : Fragment(), OnBackPressedListener {

    private val patchAdapter = PatchAdapter(null)
    private var binding: FragmentPatcherBinding? = null

    private var service: PatchService? = null
    private var bound = false
    private var patchingStarted = false

    private val appContext: Context
        get() = requireContext().applicationContext

    private val projectDir: String
        get() = ProjectUtils.getProjectPath()

    /** API 33+: the progress notification needs POST_NOTIFICATIONS; asked once, on the first apply. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val emptyStateObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = updateEmptyState()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = updateEmptyState()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = updateEmptyState()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPatcherBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = binding ?: return

        ui.toolbar.setNavigationOnClickListener { FragmentUtils.remove(this) }
        ui.btnSelectPatch.setOnClickListener { selectPatch() }
        ui.startPatch.setOnClickListener { onApplyClicked() }

        setUpPatchList(ui)
        // The screen was recreated (rotation, theme change, process restart): bring the list back.
        if (patchAdapter.itemCount == 0) {
            savedInstanceState?.getStringArrayList(STATE_PATCHES)?.forEach { path ->
                if (File(path).isFile) addPatch(PatchItem(File(path).name, path))
            }
        }

        // A patch run started earlier (e.g. before a rotation) is still in the service: reconnect.
        if (patchingStarted || savedInstanceState?.getBoolean(STATE_RUNNING) == true) {
            patchingStarted = true
            bindService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RUNNING, patchingStarted)
        outState.putStringArrayList(STATE_PATCHES, ArrayList(patchAdapter.patchData.mapNotNull { it.mPath }))
    }

    private fun setUpPatchList(ui: FragmentPatcherBinding) {
        patchAdapter.onRemoveClickListener = { item -> patchAdapter.deleteItem(item) }
        patchAdapter.registerAdapterDataObserver(emptyStateObserver)
        ui.patchList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = patchAdapter
            applyExpressiveMotion()
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding?.patchEmpty?.isVisible = patchAdapter.itemCount == 0
    }

    private fun onApplyClicked() {
        if (patchAdapter.itemCount == 0) {
            binding?.let { Snackbar.make(it.startPatch, R.string.patcher_no_patches, Snackbar.LENGTH_SHORT).show() }
            return
        }
        // Ask for the permissions that keep the background run visible/alive (each asked once).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationHelper.shouldRequestPostNotifications(requireContext())
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        BatteryOptimizationHelper.maybeAsk(requireActivity())

        val paths = patchAdapter.patchData.mapNotNull { it.mPath }
        val project = projectDir
        viewLifecycleOwner.lifecycleScope.launch {
            val needsSmali = withContext(Dispatchers.IO) { PatchInspector.anyNeedsSmali(paths) }
            val hasSmali = withContext(Dispatchers.IO) { PatchInspector.hasSmali(project) }
            if (needsSmali && !hasSmali) askDecodeThenPatch(paths) else startPatching(paths, decodeSmali = false)
        }
    }

    /** The patch edits code but the project has no smali: let the user decide. */
    private fun askDecodeThenPatch(paths: List<String>) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.patcher_smali_dialog_title)
            .setMessage(R.string.patcher_smali_dialog_message)
            .setNegativeButton(R.string.cancel_button_label, null)
            .setNeutralButton(R.string.patcher_continue_anyway) { _, _ -> startPatching(paths, decodeSmali = false) }
            .setPositiveButton(R.string.patcher_decompile_and_apply) { _, _ -> startPatching(paths, decodeSmali = true) }
            .show()
    }

    private fun startPatching(paths: List<String>, decodeSmali: Boolean) {
        if (paths.isEmpty()) return
        patchingStarted = true
        binding?.startPatch?.isEnabled = false
        if (!bound) bindService()
        val intent = Intent(appContext, PatchService::class.java)
            .putExtra(PatchService.EXTRA_PROJECT_DIR, projectDir)
            .putStringArrayListExtra(PatchService.EXTRA_PATCH_PATHS, ArrayList(paths))
            .putExtra(PatchService.EXTRA_DECODE_SMALI, decodeSmali)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (e: IllegalStateException) {
            DLog.e(e)
            binding?.startPatch?.isEnabled = true
            binding?.let { Snackbar.make(it.startPatch, R.string.build_service_start_failed, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun bindService() {
        if (bound) return
        bound = appContext.bindService(Intent(appContext, PatchService::class.java), connection, 0)
    }

    private fun unbindService() {
        if (!bound) return
        bound = false
        service = null
        try {
            appContext.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Already unbound.
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = (binder as PatchService.LocalBinder).getService()
            service = connected
            binding?.let { observe(it, connected) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    private fun observe(ui: FragmentPatcherBinding, svc: PatchService) {
        val owner = viewLifecycleOwner
        svc.logLiveData.observe(owner) { log ->
            ui.logger.text = log
            ui.loggerScroll.post { ui.loggerScroll.fullScroll(View.FOCUS_DOWN) }
        }
        svc.patchRulesSize.observe(owner) { total -> ui.progressBarRules.setMaxCompat(total) }
        svc.patchCurrentRules.observe(owner) { current ->
            ui.progressBarRules.isIndeterminate = false
            ui.progressBarRules.setProgressCompat(current, true)
        }
        svc.patchSize.observe(owner) { total -> ui.progressBarPatches.setMaxCompat(total) }
        svc.patchCount.observe(owner) { done ->
            ui.progressBarPatches.isIndeterminate = false
            ui.progressBarPatches.setProgressCompat(done, true)
        }
        svc.done.observe(owner) { finished ->
            if (finished) {
                ui.startPatch.isEnabled = true
                patchingStarted = false
            }
        }
    }

    private fun LinearProgressIndicator.setMaxCompat(total: Int) {
        if (total > 0) max = total
    }

    private fun selectPatch() {
        FilePickerDialog(requireContext())
            .setTitleText(getString(R.string.select_patch))
            .setSelectMode(FilePickerDialog.MODE_MULTI)
            .setSelectType(FilePickerDialog.TYPE_FILE)
            .setExtensions(arrayOf("zip"))
            .setRootDir(FileUtil.getInternalStorage().absolutePath)
            .setBackCancelable(true)
            .setOutsideCancelable(true)
            .setDialogListener(
                getString(R.string.choose_button_label),
                getString(R.string.cancel_button_label),
                object : FilePickerDialog.FileDialogListener {
                    override fun onSelectedFilePaths(filePaths: Array<String>) {
                        filePaths.map(::File).forEach { patch ->
                            addPatch(PatchItem(patch.name, patch.absolutePath))
                        }
                    }

                    override fun onCanceled() {
                    }
                })
            .show()
    }

    /** Adds the row at once, then fills in author / rule-count / "needs code" off the main thread. */
    private fun addPatch(item: PatchItem) {
        patchAdapter.addItem(item)
        viewLifecycleOwner.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { PatchInspector.inspect(item.mPath) }
            item.info = info
            patchAdapter.refreshItem(item)
        }
    }

    override fun onBackPressed() {
        FragmentUtils.remove(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        patchAdapter.unregisterAdapterDataObserver(emptyStateObserver)
        binding?.patchList?.adapter = null
        binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService()
    }

    companion object {
        const val TAG = "PatcherFragment"
        private const val STATE_RUNNING = "patchingStarted"
        private const val STATE_PATCHES = "patchPaths"

        @JvmStatic
        fun newInstance(): PatcherFragment {
            return PatcherFragment()
        }
    }
}
