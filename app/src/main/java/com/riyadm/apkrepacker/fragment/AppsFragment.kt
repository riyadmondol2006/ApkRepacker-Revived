package com.riyadm.apkrepacker.fragment

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ApktoolOptionsStore
import com.riyadm.apkrepacker.apktool.DecodeOptions
import com.riyadm.apkrepacker.apktool.ui.ApktoolUiPrefs
import com.riyadm.apkrepacker.databinding.FragmentAppsBinding
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.AppsOptionsItemDialogFragment
import com.riyadm.apkrepacker.fragment.dialogs.bottomsheet.DecompileOptionsDialogFragment
import com.riyadm.apkrepacker.ui.appslist.AppsAdapter
import com.riyadm.apkrepacker.ui.appslist.AppsViewModel
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.PackageMeta
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
        AppsOptionsItemDialogFragment.newInstance(packageMeta.label, packageMeta.packageName)
            .show(childFragmentManager, AppsOptionsItemDialogFragment.TAG)
    }

    override fun onAppsItemClick(item: Int?) {
        val context = requireContext()
        val app = current ?: return
        when (item) {
            R.id.decompile_app -> try {
                applicationInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
                appName = app.label
                confirmSplitThen(app) {
                    if (ApktoolUiPrefs.askDecodeOptions(context)) {
                        DecompileOptionsDialogFragment.newInstance()
                            .show(childFragmentManager, DecompileOptionsDialogFragment.TAG)
                    } else {
                        startDecompile(ApktoolOptionsStore.loadDecodeOptions(context))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in decompile installed app", e)
                showError(R.string.toast_error_in_decompile_installed_app)
            }
            R.id.simple_edit_apk -> try {
                val info = context.packageManager.getApplicationInfo(app.packageName, 0)
                FragmentUtils.replace(
                    SimpleEditorFragment.newInstance(info.publicSourceDir),
                    requireActivity().supportFragmentManager,
                    android.R.id.content,
                    SimpleEditorFragment.TAG,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in simple edit installed app", e)
                showError(R.string.toast_error_in_simple_edit_installed_app)
            }
            R.id.uninstall_app -> AppUtils.uninstallApp(context, app.packageName)
            R.id.goto_settings_app -> AppUtils.gotoApplicationSettings(context, app.packageName)
        }
    }

    /** Legacy quick actions (decompile all / resources only / dex only): the saved defaults with a preset mode. */
    override fun onModeItemClick(item: Int?) {
        val mode = DecompileOptionsDialogFragment.modeForLegacyItemId(item) ?: return
        val app = current
        val run = { startDecompile(ApktoolOptionsStore.loadDecodeOptions(requireContext()).copy(mode = mode)) }
        if (app != null) confirmSplitThen(app, run) else run()
    }

    /**
     * An app installed as split APKs decompiles differently (the splits are merged, see
     * SplitApks), so say so before starting; any other app goes straight through.
     */
    private fun confirmSplitThen(app: PackageMeta, proceed: () -> Unit) {
        if (!app.hasSplits) {
            proceed()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.split_warning_title)
            .setMessage(getString(R.string.split_warning_message, app.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.split_warning_continue) { _, _ -> proceed() }
            .show()
    }

    override fun onDecodeOptionsChosen(options: DecodeOptions) {
        startDecompile(options)
    }

    private fun startDecompile(options: DecodeOptions) {
        val source = applicationInfo?.publicSourceDir ?: return
        FragmentUtils.replace(
            DecompileFragment.newInstance(appName, source, true, options),
            requireActivity().supportFragmentManager,
            android.R.id.content,
            "DecompileFragment",
        )
    }

    private fun showError(message: Int) {
        binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        const val TAG = "AppsFragment"

        @JvmStatic
        fun newInstance(param1: String?, param2: String?): AppsFragment =
            AppsFragment().apply { arguments = Bundle() }
    }
}
