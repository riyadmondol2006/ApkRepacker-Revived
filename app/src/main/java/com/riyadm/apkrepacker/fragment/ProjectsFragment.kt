package com.riyadm.apkrepacker.fragment

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.LayoutInflater
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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.AppEditorActivity
import com.riyadm.apkrepacker.databinding.FragmentProjectsBinding
import com.riyadm.apkrepacker.fragment.CompileFragment.Companion.newInstance
import com.riyadm.apkrepacker.project.ProjectTransfer
import com.riyadm.apkrepacker.service.ProjectTransferService
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.ui.projectlist.ProjectItem
import com.riyadm.apkrepacker.ui.projectlist.ProjectViewAdapter
import com.riyadm.apkrepacker.ui.projectlist.ProjectViewHolder
import com.riyadm.apkrepacker.utils.ClickGuard
import com.riyadm.apkrepacker.utils.FragmentUtils
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.PermissionsUtils
import com.riyadm.apkrepacker.utils.StorageAccessRequester
import com.riyadm.apkrepacker.viewmodel.ProjectsFragmentViewModel
import com.riyadm.apkrepacker.viewmodel.projects.ProjectLoader
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class ProjectsFragment : Fragment(), ProjectViewHolder.OnItemClickListener {

    private val viewModel: ProjectsFragmentViewModel by viewModels()
    private var binding: FragmentProjectsBinding? = null
    private var adapter: ProjectViewAdapter? = null

    private var allProjects: List<ProjectItem> = emptyList()
    private var query = ""
    private var loadedOnce = false

    /**
     * Storage access is only needed by the file manager (and to list old projects in
     * /sdcard/ApkRepacker/projects); projects in app storage work without it.
     */
    private val storageAccess = StorageAccessRequester(this) { granted ->
        if (granted) reloadForStorageAccess()
        // An export/import was waiting for the answer: carry on either way (the folder picker
        // works without All files access, it is just slower).
        afterStorageAccess?.invoke()
        afterStorageAccess = null
    }
    private var hadStorageAccess = false
    private var afterStorageAccess: (() -> Unit)? = null

    /** The project an export dialog is currently choosing a destination for. */
    private var pendingExportPath: String? = null

    private val clickGuard = ClickGuard()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The system picker can outlive our process; keep the chosen project so the export still proceeds.
        pendingExportPath = savedInstanceState?.getString(STATE_PENDING_EXPORT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_EXPORT, pendingExportPath)
    }

    private val exportZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            startExport(ProjectTransferService.ACTION_EXPORT_ZIP, uri)
        }
    private val exportFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            startExport(ProjectTransferService.ACTION_EXPORT_FOLDER, uri)
        }
    private val importZipLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            startImport(ProjectTransferService.ACTION_IMPORT_ZIP, uri)
        }
    private val importFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            startImport(ProjectTransferService.ACTION_IMPORT_FOLDER, uri)
        }

    /** API 33+: the transfer progress notification needs POST_NOTIFICATIONS; asked once. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentProjectsBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return
        binding.collapsingToolbar.title = getString(R.string.projects_title)
        setupSearch(binding)
        setupList(binding)
        setupFab(binding)
        setupRefresh(binding)
        requestStorageAccessOnce()

        viewModel.projects.observe(viewLifecycleOwner) { items ->
            allProjects = items.orEmpty()
            loadedOnce = true
            binding.swipeRefreshLayoutRecyclerView.isRefreshing = false
            binding.loadingIndicator.visibility = View.GONE
            showProjects()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ProjectTransferService.results.collect { result ->
                    this@ProjectsFragment.binding?.let {
                        Snackbar.make(it.root, result.message, Snackbar.LENGTH_LONG).setAnchorView(it.fabAddApp).show()
                    }
                }
            }
        }
        // The loader may already hold a value from an earlier visit.
        binding.loadingIndicator.visibility = if (viewModel.projects.value == null) View.VISIBLE else View.GONE
    }

    private fun setupSearch(binding: FragmentProjectsBinding) {
        val toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.options_menu_poject)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_zip -> {
                    withStorageAccess { importZipLauncher.launch(ZIP_MIME_TYPES) }
                    true
                }
                R.id.action_import_folder -> {
                    withStorageAccess { importFolderLauncher.launch(null) }
                    true
                }
                else -> false
            }
        }
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_projects_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(text: String?): Boolean {
                query = text.orEmpty().trim()
                showProjects()
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
                showProjects()
                return true
            }
        })
    }

    private fun setupList(binding: FragmentProjectsBinding) {
        val context = requireContext()
        val columns = when {
            resources.configuration.screenWidthDp >= 1000 -> 3
            resources.configuration.screenWidthDp >= 600 -> 2
            else -> 1
        }
        val list = binding.projectList
        list.layoutManager = if (columns == 1) {
            LinearLayoutManager(context)
        } else {
            StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)
        }
        val projectAdapter = ProjectViewAdapter(context).also {
            it.grouped = columns == 1
            it.setOnItemClickListener(this)
        }
        adapter = projectAdapter
        list.adapter = projectAdapter
        list.applyExpressiveMotion()
        FastScrollerBuilder(list)
            .setThumbDrawable(requireNotNull(ContextCompat.getDrawable(context, R.drawable.b_fastscroll_thumb)))
            .setTrackDrawable(requireNotNull(ContextCompat.getDrawable(context, R.drawable.b_fastscroll_track)))
            .build()
    }

    private fun setupFab(binding: FragmentProjectsBinding) {
        val fab = binding.fabAddApp
        fab.setOnClickListener {
            if (!clickGuard.allow()) return@setOnClickListener
            parentFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(android.R.id.content, MyFilesFragment())
                .commit()
        }
        binding.projectList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                when {
                    dy > SCROLL_SLOP -> fab.shrink()
                    dy < -SCROLL_SLOP || !recyclerView.canScrollVertically(-1) -> fab.extend()
                }
            }
        })
    }

    private fun setupRefresh(binding: FragmentProjectsBinding) {
        val refresh = binding.swipeRefreshLayoutRecyclerView
        refresh.setColorSchemeColors(
            MaterialColors.getColor(refresh, R.attr.colorPrimary),
        )
        refresh.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(refresh, com.google.android.material.R.attr.colorSurfaceContainerHigh),
        )
        refresh.setOnRefreshListener { viewModel.refresh() }
    }

    /** Offers storage access once, on the first start; projects load either way. */
    private fun requestStorageAccessOnce() {
        val context = requireContext()
        hadStorageAccess = PermissionsUtils.hasStorageAccess(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!hadStorageAccess && !prefs.getBoolean(KEY_ASKED_STORAGE_ACCESS, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE_ACCESS, true).apply()
            storageAccess.request()
        }
    }

    private fun showProjects() {
        val binding = binding ?: return
        val visible = if (query.isEmpty()) {
            allProjects
        } else {
            allProjects.filter {
                it.appName.orEmpty().contains(query, ignoreCase = true) ||
                    it.appPackage.orEmpty().contains(query, ignoreCase = true)
            }
        }
        adapter?.setData(visible)
        binding.collapsingToolbar.subtitle =
            resources.getQuantityString(R.plurals.projects_count_plural, visible.size, visible.size)

        val hasItems = visible.isNotEmpty()
        binding.projectList.visibility = if (hasItems) View.VISIBLE else View.INVISIBLE
        binding.emptyView.root.visibility = if (!hasItems && loadedOnce) View.VISIBLE else View.GONE
    }

    /** Access was just granted: old projects in shared storage are readable now. */
    private fun reloadForStorageAccess() {
        hadStorageAccess = true
        ProjectLoader.getInstance(requireContext()).loadProjects()
    }

    override fun onResume() {
        super.onResume()
        // Granted meanwhile (e.g. from the file manager, or in system settings).
        if (!hadStorageAccess && PermissionsUtils.hasStorageAccess(requireContext())) {
            reloadForStorageAccess()
        }
    }

    override fun onDestroyView() {
        binding = null
        adapter = null
        super.onDestroyView()
    }

    override fun onProjectClick(item: ProjectItem, position: Int) {
        if (!clickGuard.allow()) return
        val intent = Intent(requireContext(), AppEditorActivity::class.java)
            .putExtra("apkFileIcon", item.appIcon)
            .putExtra("apkFileName", item.appName)
            .putExtra("apkFilePackageName", item.appPackage)
            .putExtra("projectPatch", item.appProjectPath)
        startActivity(intent)
    }

    override fun onProjectMenuClick(view: View, item: ProjectItem, position: Int) {
        when (view.id) {
            R.id.action_build -> {
                if (PreferenceHelper.getInstance(requireContext()).isConfirmBuild) {
                    confirmBuild(item)
                } else {
                    showCompileFragment(item)
                }
            }
            R.id.action_about_project -> {
                FragmentUtils.add(AboutProjectFragment.newInstance(item), parentFragmentManager, android.R.id.content)
            }
            R.id.action_delete -> confirmDelete(item)
            R.id.action_export -> withStorageAccess { showExportDialog(item) }
        }
    }

    /**
     * Export/import go through the system file picker, which needs no permission, but with
     * All files access the app reads and writes the chosen folder directly (much faster for a
     * project of thousands of files). So offer it first; "continue without" is remembered until
     * the app restarts.
     */
    private fun withStorageAccess(action: () -> Unit) {
        val context = requireContext()
        if (PermissionsUtils.hasStorageAccess(context) || skippedStorageAccess) {
            action()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.transfer_access_title)
            .setMessage(R.string.transfer_access_message)
            .setPositiveButton(R.string.transfer_access_grant) { _, _ ->
                afterStorageAccess = action
                storageAccess.request(explain = false)
            }
            .setNegativeButton(R.string.transfer_access_continue) { _, _ ->
                skippedStorageAccess = true
                action()
            }
            .setOnCancelListener { /* back / tap outside: do nothing */ }
            .show()
    }

    private fun showExportDialog(item: ProjectItem) {
        val options = arrayOf(
            getString(R.string.transfer_export_as_zip) + "\n" + getString(R.string.transfer_export_as_zip_desc),
            getString(R.string.transfer_export_to_folder) + "\n" + getString(R.string.transfer_export_to_folder_desc),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.transfer_export_title))
            .setItems(options) { _, which ->
                askNotificationsOnce()
                pendingExportPath = item.appProjectPath
                if (which == 0) {
                    exportZipLauncher.launch(ProjectTransfer.safeName(item.appName ?: "project") + ".zip")
                } else {
                    exportFolderLauncher.launch(null)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startExport(action: String, uri: Uri?) {
        val projectPath = pendingExportPath
        pendingExportPath = null
        if (uri == null || projectPath == null) return
        ProjectTransferService.start(requireContext().applicationContext, action, uri, projectPath)
    }

    private fun startImport(action: String, uri: Uri?) {
        if (uri == null) return
        askNotificationsOnce()
        ProjectTransferService.start(requireContext().applicationContext, action, uri)
    }

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationHelper.shouldRequestPostNotifications(requireContext())
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Expanding/collapsing is animated by the row itself.
    override fun onProjectLongClick(item: ProjectItem, position: Int) = Unit

    private fun confirmBuild(item: ProjectItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_build_title)
            .setMessage(item.appName)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.app_build) { _, _ -> showCompileFragment(item) }
            .show()
    }

    private fun confirmDelete(item: ProjectItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_project_title)
            .setMessage(getString(R.string.delete_project_message, item.appName.orEmpty()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> deleteProject(item) }
            .show()
    }

    private fun deleteProject(item: ProjectItem) {
        // Drop the row right away; the loader rescans once the folder is gone.
        allProjects = allProjects.filterNot { it.appProjectPath == item.appProjectPath }
        showProjects()
        viewModel.deleteProject(item) { deleted ->
            val binding = binding ?: return@deleteProject
            if (deleted) {
                Snackbar.make(binding.root, R.string.project_deleted, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.fabAddApp)
                    .show()
            }
        }
    }

    private fun showCompileFragment(projectItem: ProjectItem) {
        FragmentUtils.replace(newInstance(projectItem.appProjectPath), parentFragmentManager, android.R.id.content)
    }

    companion object {
        const val TAG = "ProjectsFragment"
        private const val STATE_PENDING_EXPORT = "pending_export_path"
        private const val KEY_ASKED_STORAGE_ACCESS = "asked_storage_access_on_start"
        private const val SCROLL_SLOP = 6
        private val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

        /** The user chose "continue without" All files access; don't ask again until the app restarts. */
        private var skippedStorageAccess = false
    }
}
