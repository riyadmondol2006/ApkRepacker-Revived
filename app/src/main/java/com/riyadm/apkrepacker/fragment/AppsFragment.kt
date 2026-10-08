package com.riyadm.apkrepacker.fragment

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.antisplit.AntiSplitUi
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.ApktoolUiPrefs
import com.riyadm.apkrepacker.databinding.FragmentAppsBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.AppsOptionsItemDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.DecompileOptionsDialogFragment
import com.riyadm.apkrepacker.project.ProjectTransfer
import com.riyadm.apkrepacker.service.AntiSplitService
import com.riyadm.apkrepacker.ui.appslist.AppsAdapter
import com.riyadm.apkrepacker.ui.appslist.AppsViewModel
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.PackageMeta
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class AppsFragment : Fragment(), AppsAdapter.OnItemInteractionListener,
    AppsOptionsItemDialogFragment.ItemClickListener, DecompileOptionsDialogFragment.ItemClickListener {

    private val viewModel: AppsViewModel by viewModels()
    private var binding: FragmentAppsBinding? = null
    private var appsAdapter: AppsAdapter? = null

    private var query = ""
    private var current: PackageMeta? = null
    private var applicationInfo: ApplicationInfo? = null
    private var appName: String? = null

    /** The APK the next decompile uses (base APK, or the merged APK of a split app). */
    private var pendingApk: String? = null

    /** True when [pendingApk] was chosen through the AntiSplit question, so splits must not be merged again. */
    private var pendingFromSplit = false

    /** The package waiting for the "AntiSplit & save" file picker. */
    private var pendingSavePackage: String? = null

    /** Whether the pending save signs the APK. */
    private var pendingSaveSign = true

    /** AntiSplit save jobs started from this screen, whose results are shown here. */
    private val saveJobs = mutableSetOf<String>()

    private val saveApkLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
            startAntiSplitSave(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The system picker can outlive our process; keep what the pending action needs.
        savedInstanceState?.let { state ->
            pendingApk = state.getString(STATE_PENDING_APK)
            pendingFromSplit = state.getBoolean(STATE_PENDING_FROM_SPLIT)
            appName = state.getString(STATE_APP_NAME)
            pendingSavePackage = state.getString(STATE_PENDING_SAVE)
            pendingSaveSign = state.getBoolean(STATE_PENDING_SAVE_SIGN, true)
            state.getStringArrayList(STATE_SAVE_JOBS)?.let { saveJobs.addAll(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_APK, pendingApk)
        outState.putBoolean(STATE_PENDING_FROM_SPLIT, pendingFromSplit)
        outState.putString(STATE_APP_NAME, appName)
        outState.putString(STATE_PENDING_SAVE, pendingSavePackage)
        outState.putBoolean(STATE_PENDING_SAVE_SIGN, pendingSaveSign)
        outState.putStringArrayList(STATE_SAVE_JOBS, ArrayList(saveJobs))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentAppsBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return

        binding.collapsingToolbar.title = getString(R.string.installed_apps)
        setupList(binding)
        setupSearch(binding)
        setupFilters(binding)

        viewModel.packages.observe(viewLifecycleOwner) { packages ->
            appsAdapter?.setData(packages)
            binding.collapsingToolbar.subtitle =
                resources.getQuantityString(R.plurals.apps_count_plural, packages.size, packages.size)
            updateEmptyState(binding)
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.loadingView.visibility = if (it) View.VISIBLE else View.GONE
            updateEmptyState(binding)
        }
        filterPackages()
        setupAntiSplit()
    }

    /** Results of AntiSplit jobs started here: merged APKs to decompile or edit, and saved APKs. */
    private fun setupAntiSplit() {
        childFragmentManager.setFragmentResultListener(REQUEST_DECOMPILE, viewLifecycleOwner) { _, result ->
            val apk = result.getString(AntiSplitUi.RESULT_APK)
            if (result.getBoolean(AntiSplitUi.RESULT_OK) && apk != null) {
                continueDecompile(apk, result.getString(AntiSplitUi.RESULT_LABEL) ?: appName, fromSplit = true)
            } else {
                showMessage(result.getString(AntiSplitUi.RESULT_MESSAGE))
            }
        }
        childFragmentManager.setFragmentResultListener(REQUEST_EDIT, viewLifecycleOwner) { _, result ->
            val apk = result.getString(AntiSplitUi.RESULT_APK)
            if (result.getBoolean(AntiSplitUi.RESULT_OK) && apk != null) {
                openSimpleEditor(apk)
            } else {
                showMessage(result.getString(AntiSplitUi.RESULT_MESSAGE))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Subscribe first, then catch up on jobs that finished while the screen was away.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    AntiSplitService.results.collect(::onSaveFinished)
                }
                saveJobs.toList().forEach { id -> AntiSplitService.resultOf(id)?.let(::onSaveFinished) }
            }
        }
    }

    private fun onSaveFinished(result: AntiSplitService.Result) {
        if (saveJobs.remove(result.jobId)) showMessage((listOf(result.message) + result.warnings).joinToString("\n"))
    }

    private fun setupList(binding: FragmentAppsBinding) {
        val context = requireContext()
        val list = binding.appPackages
        val adapter = AppsAdapter(context).also { it.setInteractionListener(this) }
        appsAdapter = adapter
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.recycledViewPool.setMaxRecycledViews(0, 24)
        list.applyExpressiveMotion()
        FastScrollerBuilder(list)
            .setThumbDrawable(requireNotNull(ContextCompat.getDrawable(context, R.drawable.b_fastscroll_thumb)))
            .setTrackDrawable(requireNotNull(ContextCompat.getDrawable(context, R.drawable.b_fastscroll_track)))
            .build()
    }

    private fun setupSearch(binding: FragmentAppsBinding) {
        val toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.options_menu_apps)
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_apps_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(text: String?): Boolean {
                query = text.orEmpty()
                filterPackages()
                return true
            }
        })

        val backWhileSearching = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                searchItem.collapseActionView()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backWhileSearching)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                backWhileSearching.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                backWhileSearching.isEnabled = false
                query = ""
                filterPackages()
                return true
            }
        })
    }

    private fun setupFilters(binding: FragmentAppsBinding) {
        binding.chipFilterSplits.setOnCheckedChangeListener { _, _ -> filterPackages() }
        binding.chipFilterSystem.setOnCheckedChangeListener { _, _ -> filterPackages() }
    }

    private fun filterPackages() {
        val binding = binding ?: return
        viewModel.filter(query, binding.chipFilterSplits.isChecked, binding.chipFilterSystem.isChecked)
    }

    private fun updateEmptyState(binding: FragmentAppsBinding) {
        val empty = viewModel.loading.value != true && viewModel.packages.value.isNullOrEmpty()
        binding.appsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        binding = null
        appsAdapter = null
        super.onDestroyView()
    }

    override fun onBackupButtonClicked(packageMeta: PackageMeta) {
        current = packageMeta
        AppsOptionsItemDialogFragment.newInstance(packageMeta.label, packageMeta.packageName, packageMeta.hasSplits)
            .show(childFragmentManager, AppsOptionsItemDialogFragment.TAG)
    }

    override fun onAppsItemClick(item: Int?) {
        val context = requireContext()
        val app = current ?: return
        when (item) {
            R.id.decompile_app -> try {
                val info = context.packageManager.getApplicationInfo(app.packageName, 0)
                applicationInfo = info
                appName = app.label
                withChosenApk(app, REQUEST_DECOMPILE, info.publicSourceDir) { apk ->
                    continueDecompile(apk, app.label, fromSplit = app.hasSplits)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in decompile installed app", e)
                showError(R.string.toast_error_in_decompile_installed_app)
            }
            R.id.simple_edit_apk -> try {
                val info = context.packageManager.getApplicationInfo(app.packageName, 0)
                withChosenApk(app, REQUEST_EDIT, info.publicSourceDir, ::openSimpleEditor)
            } catch (e: Exception) {
                Log.e(TAG, "Error in simple edit installed app", e)
                showError(R.string.toast_error_in_simple_edit_installed_app)
            }
            R.id.antisplit_save -> AntiSplitUi.askSign(this) { sign ->
                pendingSavePackage = app.packageName
                pendingSaveSign = sign
                val name = ProjectTransfer.safeName(app.label ?: app.packageName)
                val version = app.versionName?.let { "_" + ProjectTransfer.safeName(it) }.orEmpty()
                saveApkLauncher.launch(AntiSplitUi.apkFileName("$name$version", sign))
            }
            R.id.uninstall_app -> AppUtils.uninstallApp(context, app.packageName)
            R.id.goto_settings_app -> AppUtils.gotoApplicationSettings(context, app.packageName)
        }
    }

    /**
     * Hands the APK to work on to [proceed]: the base APK for a normal app. A split app asks first;
     * "AntiSplit" merges it and the merged APK arrives later through [requestKey]'s listener (see
     * [setupAntiSplit]), "Base APK only" goes on with [baseApk] at once.
     */
    private fun withChosenApk(app: PackageMeta, requestKey: String, baseApk: String, proceed: (String) -> Unit) {
        if (!app.hasSplits) {
            proceed(baseApk)
            return
        }
        AntiSplitUi.askMergeOrBase(
            this,
            app.label,
            onAntiSplit = { AntiSplitUi.prepare(this, AntiSplitService.Source.Installed(app.packageName), requestKey, app.label) },
            onBaseOnly = { proceed(baseApk) },
        )
    }

    /** Remembers the APK and name to decompile, then asks for the options or starts with the saved ones. */
    private fun continueDecompile(apk: String, label: String?, fromSplit: Boolean) {
        val context = context ?: return
        pendingApk = apk
        pendingFromSplit = fromSplit
        appName = label
        if (ApktoolUiPrefs.askDecodeOptions(context)) {
            DecompileOptionsDialogFragment.newInstance()
                .show(childFragmentManager, DecompileOptionsDialogFragment.TAG)
        } else {
            startDecompile(ApktoolOptionsStore.loadDecodeOptions(context))
        }
    }

    private fun openSimpleEditor(apk: String) {
        FragmentUtils.replace(
            SimpleEditorFragment.newInstance(apk),
            requireActivity().supportFragmentManager,
            android.R.id.content,
            SimpleEditorFragment.TAG,
        )
    }

    private fun startAntiSplitSave(uri: Uri?) {
        val packageName = pendingSavePackage ?: return
        pendingSavePackage = null
        if (uri == null) return
        val jobId = AntiSplitService.save(requireContext(), AntiSplitService.Source.Installed(packageName), uri, pendingSaveSign)
        saveJobs += jobId
        showError(R.string.antisplit_started)
        // A job that could not even start has already reported its outcome.
        AntiSplitService.resultOf(jobId)?.let(::onSaveFinished)
    }

    /**
     * Legacy quick actions (decompile all / resources only / dex only): the saved defaults with a
     * preset mode. They are offered after the APK was chosen, so nothing is asked again.
     */
    override fun onModeItemClick(item: Int?) {
        val mode = DecompileOptionsDialogFragment.modeForLegacyItemId(item) ?: return
        startDecompile(ApktoolOptionsStore.loadDecodeOptions(requireContext()).copy(mode = mode))
    }

    override fun onDecodeOptionsChosen(options: DecodeOptions) {
        startDecompile(options)
    }

    /**
     * Decompiles the remembered APK. For a split app the merge-or-base choice was already made
     * (merged APK, or the base on purpose), so the decoder must not merge the splits again.
     */
    private fun startDecompile(options: DecodeOptions) {
        val source = pendingApk ?: applicationInfo?.publicSourceDir ?: return
        val effective = if (pendingFromSplit) options.copy(mergeSplits = false) else options
        FragmentUtils.replace(
            DecompileFragment.newInstance(appName, source, true, effective),
            requireActivity().supportFragmentManager,
            android.R.id.content,
            "DecompileFragment",
        )
    }

    private fun showError(message: Int) {
        binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showMessage(message: String?) {
        if (message.isNullOrBlank()) return
        binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        const val TAG = "AppsFragment"
        private const val REQUEST_DECOMPILE = "apps_antisplit_decompile"
        private const val REQUEST_EDIT = "apps_antisplit_edit"
        private const val STATE_PENDING_APK = "pending_apk"
        private const val STATE_PENDING_FROM_SPLIT = "pending_from_split"
        private const val STATE_APP_NAME = "app_name"
        private const val STATE_PENDING_SAVE = "pending_save_package"
        private const val STATE_PENDING_SAVE_SIGN = "pending_save_sign"
        private const val STATE_SAVE_JOBS = "save_jobs"

        @JvmStatic
        fun newInstance(param1: String?, param2: String?): AppsFragment =
            AppsFragment().apply { arguments = Bundle() }
    }
}
