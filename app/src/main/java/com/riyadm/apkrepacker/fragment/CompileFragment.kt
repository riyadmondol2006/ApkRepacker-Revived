package com.riyadm.apkrepacker.fragment

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.adapter.ErrorAdapter
import com.riyadm.apkrepacker.apktool.ui.ApktoolUiPrefs
import com.riyadm.apkrepacker.apktool.ui.BuildOptionsDialogFragment
import com.riyadm.apkrepacker.apktool.ui.copyToClipboard
import com.riyadm.apkrepacker.apktool.ui.springCardColor
import com.riyadm.apkrepacker.databinding.FragmentCompileBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.FileOptionsDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.FullLogDialogFragment
import com.riyadm.apkrepacker.service.BuildService
import com.riyadm.apkrepacker.ui.apkbuilder.BuildStepsAdapter
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.BatteryOptimizationHelper
import com.riyadm.apkrepacker.utils.FileProvider
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.TimeUtils
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.viewmodel.CompileFragmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.material.R as MaterialR

/**
 * Build screen. The build runs in [BuildService]; this screen binds to it and shows the status card,
 * the step list (checks spring in as steps finish) and, when the build ends, the tonal result card
 * with Install / Share / Show log actions. Build options come first (see [BuildOptionsDialogFragment]).
 */
class CompileFragment : Fragment(), ErrorAdapter.OnItemInteractionListener {

    private val viewModel: CompileFragmentViewModel by viewModels()
    private var binding: FragmentCompileBinding? = null
    private var errorAdapter = ErrorAdapter()
    private val stepsAdapter = BuildStepsAdapter()

    private var projectDir: String? = null
    private var service: BuildService? = null
    private var bound = false
    private var reconnectJob: Job? = null
    private var finished = false
    private var builtApk: File? = null
    private var builtPackage: String? = null
    private var stepRegex: Regex? = null

    private val appContext: Context
        get() = requireContext().applicationContext

    /** API 33+: the build progress notification needs POST_NOTIFICATIONS; asked once, on the first build. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectDir = arguments?.getString(ARG_PROJECT)
        stepRegex = buildStepRegex(getString(R.string.step))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SERVICE_RUNNING, bound)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentCompileBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        finished = false
        errorAdapter = ErrorAdapter().also { it.setItemInteractionListener(this) }

        b.progressTip.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        b.compileSteps.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stepsAdapter
            applyExpressiveMotion()
        }
        b.errorList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = errorAdapter
            applyExpressiveMotion()
        }
        stepsAdapter.submitList(viewModel.stepRows(finished = false))
        bindStaticActions(b)

        // The build options sheet comes first; the build itself reads ApktoolOptionsStore.
        childFragmentManager.setFragmentResultListener(BuildOptionsDialogFragment.REQUEST_KEY, viewLifecycleOwner) { _, result ->
            if (result.getBoolean(BuildOptionsDialogFragment.RESULT_CONFIRMED)) {
                startBuild()
            } else {
                FragmentUtils.remove(this)
            }
        }

        when {
            viewModel.buildStarted || savedInstanceState?.getBoolean(STATE_SERVICE_RUNNING) == true -> {
                // Rebuilt view of a running/finished build: just reconnect, never start a second build.
                viewModel.buildStarted = true
                service?.let { observe(b, it) } ?: run {
                    bindService()
                    watchReconnect(b)
                }
            }
            childFragmentManager.findFragmentByTag(BuildOptionsDialogFragment.TAG) != null -> Unit
            ApktoolUiPrefs.askBuildOptions(requireContext()) ->
                BuildOptionsDialogFragment.newInstance().show(childFragmentManager, BuildOptionsDialogFragment.TAG)
            else -> startBuild()
        }
    }

    override fun onResume() {
        super.onResume()
        // The app may have been installed or removed while the system installer was in front.
        if (finished) binding?.let { updateUninstallButton(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reconnectJob?.cancel()
        reconnectJob = null
        binding?.let {
            it.compileSteps.adapter = null
            it.errorList.adapter = null
        }
        binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService()
    }

    private fun bindStaticActions(b: FragmentCompileBinding) {
        b.btnShowLog.setOnClickListener {
            FullLogDialogFragment.newInstance().show(parentFragmentManager, FullLogDialogFragment.TAG)
        }
        b.btnCopyLog.setOnClickListener { it.copyToClipboard(errorAdapter.errorLines.joinToString("\n")) }
        b.btnInstall.setOnClickListener { builtApk?.let { apk -> AppUtils.installApk(requireContext(), apk) } }
        b.btnShare.setOnClickListener { builtApk?.let(::shareApk) }
        b.btnRemove.setOnClickListener { AppUtils.uninstallApp(requireContext(), builtPackage) }
        b.btnClose.setOnClickListener {
            unbindService()
            stopBuildService()
            FragmentUtils.remove(this)
        }
    }

    /**
     * Only reconnecting (no build is started here): if no service connects, e.g. after process
     * death, the build is gone. Say so instead of spinning forever; never start a new build.
     */
    private fun watchReconnect(b: FragmentCompileBinding) {
        reconnectJob?.cancel()
        reconnectJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!bound) {
                reconnectLost(b)
                return@launch
            }
            delay(RECONNECT_TIMEOUT_MS)
            if (service == null) reconnectLost(b)
        }
    }

    private fun reconnectLost(b: FragmentCompileBinding) {
        if (finished) return
        unbindService()
        val message = getString(R.string.build_interrupted)
        errorAdapter.updateMessage(message)
        showFailure(b, message)
    }

    private fun startBuild() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationHelper.shouldRequestPostNotifications(requireContext())
        ) {
            // The build doesn't wait for the answer: it runs either way.
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        BatteryOptimizationHelper.maybeAsk(requireActivity())
        viewModel.buildStarted = true
        if (!bound) bindService()
        startBuildService()
    }

    private fun startBuildService() {
        val intent = Intent(requireContext(), BuildService::class.java).putExtra("projectDir", projectDir)
        try {
            ContextCompat.startForegroundService(requireContext(), intent)
        } catch (e: IllegalStateException) {
            // API 31+: ForegroundServiceStartNotAllowedException if this runs while the app
            // is in the background (e.g. the view is recreated there).
            DLog.e(e)
            errorAdapter.updateMessage(getString(R.string.build_service_start_failed))
            binding?.let { showFailure(it, getString(R.string.build_service_start_failed)) }
        }
    }

    private fun stopBuildService() {
        appContext.stopService(Intent(appContext, BuildService::class.java))
    }

    private fun bindService() {
        if (bound) return
        bound = appContext.bindService(Intent(appContext, BuildService::class.java), connection, 0)
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
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val connected = (binder as BuildService.LocalBinder).getService()
            service = connected
            reconnectJob?.cancel()
            binding?.let { observe(it, connected) }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            service = null
        }
    }

    private fun observe(b: FragmentCompileBinding, svc: BuildService) {
        val owner = viewLifecycleOwner
        svc.falied.observe(owner) { errors -> errorAdapter.updateMessage(errors) }
        svc.stepInfo.observe(owner) { raw -> onStep(b, raw) }
        svc.time.observe(owner) { elapsed ->
            b.messageBuildFileTime.text = getString(R.string.app_compile_elapsed_time, TimeUtils().formatStopWatchTime(elapsed))
        }
        svc.success.observe(owner) { apk ->
            // null with no error = the service was reset for a new build, not a result.
            if (apk != null) showSuccess(b, apk)
            else if (!svc.falied.value.isNullOrEmpty()) showFailure(b, errorAdapter.errorLines.firstOrNull())
        }
    }

    private fun onStep(b: FragmentCompileBinding, raw: String?) {
        if (raw == null || finished) return
        val match = stepRegex?.matchEntire(raw)
        if (match != null) {
            val index = match.groupValues[1].toIntOrNull() ?: viewModel.stepIndex
            val total = match.groupValues[2].toIntOrNull() ?: viewModel.stepTotal
            viewModel.onStep(index, total, match.groupValues[3])
        } else {
            viewModel.onStep(viewModel.stepIndex + 1, viewModel.stepTotal, raw)
        }
        b.compileSubtitle.text = raw
        b.compileSubtitle.isVisible = true
        stepsAdapter.submitList(viewModel.stepRows(finished = false))
        val total = viewModel.stepTotal
        if (total > 0) {
            val progress = b.compileProgress
            if (progress.isIndeterminate) progress.isIndeterminate = false
            progress.max = total
            progress.setProgressCompat((viewModel.stepIndex - 1).coerceAtLeast(0), true)
        }
    }

    private fun showSuccess(b: FragmentCompileBinding, apk: File) {
        if (finished) return
        finished = true
        builtApk = apk
        applyResultColors(b, success = true)
        b.progressTip.setText(R.string.build_successful)
        b.compileSubtitle.text = apk.name
        b.compileSubtitle.isVisible = true
        val saved = getString(R.string.build_apk_saved_to, apk.absolutePath)
        // Signed with the test key because the user's own key wasn't found: say so right here.
        val signWarning = service?.signWarning?.value
        b.messageBuildFileSaved.text = if (signWarning != null) "$saved\n\n⚠ $signWarning" else saved
        b.messageBuildFileTime.isVisible = b.messageBuildFileTime.text.isNotEmpty()
        b.messageBuildFileSaved.isVisible = true
        showStatusIcon(b, success = true)

        stepsAdapter.submitList(viewModel.stepRows(finished = true))
        b.compileProgress.max = maxOf(viewModel.stepTotal, 1)
        b.compileProgress.setProgressCompat(b.compileProgress.max, true)
        revealActions(b, success = true)

        viewLifecycleOwner.lifecycleScope.launch {
            val pkg = withContext(Dispatchers.IO) { AppUtils.getApkPackage(appContext, apk.absolutePath) }
            builtPackage = pkg
            binding?.let { updateUninstallButton(it) }
        }
    }

    private fun showFailure(b: FragmentCompileBinding, firstError: String?) {
        if (finished) return
        finished = true
        applyResultColors(b, success = false)
        b.progressTip.setText(R.string.error_build_failed)
        b.compileSubtitle.text = firstError.orEmpty().ifBlank { getString(R.string.m3d_decompile_failed_hint) }
        b.compileSubtitle.isVisible = true
        showStatusIcon(b, success = false)

        b.compileStepsSection.springOut { b.compileStepsSection.visibility = View.GONE }
        b.compileErrorSection.springIn(delayMs = 80)
        revealActions(b, success = false)
    }

    private fun applyResultColors(b: FragmentCompileBinding, success: Boolean) {
        val container = MaterialColors.getColor(b.root, if (success) MaterialR.attr.colorPrimaryContainer else MaterialR.attr.colorErrorContainer)
        val onContainer = MaterialColors.getColor(b.root, if (success) MaterialR.attr.colorOnPrimaryContainer else MaterialR.attr.colorOnErrorContainer)
        b.compileStatusCard.springCardColor(container)
        listOf(b.progressTip, b.compileSubtitle, b.messageBuildFileTime, b.messageBuildFileSaved).forEach {
            it.setTextColor(onContainer)
        }
        b.compileResultIcon.imageTintList = ColorStateList.valueOf(onContainer)
    }

    private fun showStatusIcon(b: FragmentCompileBinding, success: Boolean) {
        b.compileResultIcon.setImageResource(if (success) R.drawable.ic_m3_check_circle else R.drawable.ic_m3_error_circle)
        b.compileResultIcon.contentDescription = getString(if (success) R.string.m3d_status_done else R.string.m3d_status_failed)
        b.compileResultIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        b.compileLoading.springOut { b.compileLoading.visibility = View.GONE }
        b.compileResultIcon.springIn(delayMs = 60)
        if (!success) b.compileProgress.springOut { b.compileProgress.visibility = View.GONE }
    }

    private fun revealActions(b: FragmentCompileBinding, success: Boolean) {
        b.compilePrimaryActions.isVisible = success
        b.btnCopyLog.isVisible = !success
        b.compileActions.springIn(delayMs = 120, fromScale = 0.96f)
    }

    private fun updateUninstallButton(b: FragmentCompileBinding) {
        val pkg = builtPackage
        b.btnRemove.isVisible = finished && pkg != null && AppUtils.checkAppInstalled(requireContext(), pkg)
    }

    private fun shareApk(apk: File) {
        val uri = FileProvider.getUriForFile(requireContext(), apk)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(apk.name, uri)
        startActivity(Intent.createChooser(send, getString(R.string.m3d_share_apk_title)))
    }

    override fun OnItemLongClick(message: String?) {
        message?.let { binding?.root?.copyToClipboard(it) }
    }

    override fun OnItemClicked(filePath: String?, lineNumber: Int) {
        FileOptionsDialogFragment.newInstance(filePath, lineNumber)
            .show(parentFragmentManager, FileOptionsDialogFragment.TAG)
    }

    companion object {
        private const val ARG_PROJECT = "project"
        private const val STATE_SERVICE_RUNNING = "serviceRunning"
        private const val RECONNECT_TIMEOUT_MS = 4000L

        @JvmStatic
        fun newInstance(param1: String?): CompileFragment = CompileFragment().apply {
            arguments = Bundle().apply { putString(ARG_PROJECT, param1) }
        }

        /** Turns the localized "Step %d/%d: %s" format of [R.string.step] into a regex (groups: index, total, text). */
        private fun buildStepRegex(format: String): Regex {
            val pattern = StringBuilder("^")
            var last = 0
            for (token in Regex("%(?:\\d+\\$)?[ds]").findAll(format)) {
                pattern.append(Regex.escape(format.substring(last, token.range.first)))
                pattern.append(if (token.value.endsWith("d")) "(\\d+)" else "(.*)")
                last = token.range.last + 1
            }
            pattern.append(Regex.escape(format.substring(last))).append('$')
            return Regex(pattern.toString(), RegexOption.DOT_MATCHES_ALL)
        }
    }
}
