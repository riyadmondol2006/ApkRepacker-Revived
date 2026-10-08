package com.riyadm.apkrepacker.fragment.base

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.riyadm.apkrepacker.App
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.BaseFilesFragmentBinding
import com.riyadm.apkrepacker.databinding.LayoutFilesActionToolbarBinding
import com.riyadm.apkrepacker.databinding.LayoutFilesPathBarBinding
import com.riyadm.apkrepacker.databinding.LayoutFilesSelectionBarBinding
import com.riyadm.apkrepacker.recycler.OnItemSelectedListener
import com.riyadm.apkrepacker.ui.filemanager.FileAdapter
import com.riyadm.apkrepacker.ui.filemanager.FileDialogs
import com.riyadm.apkrepacker.ui.filemanager.FileFeedback
import com.riyadm.apkrepacker.ui.filemanager.PanelActions
import com.riyadm.apkrepacker.ui.filemanager.PathButtonAdapter
import com.riyadm.apkrepacker.ui.filemanager.holder.DirectoryHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.holder.FileListViewHolder
import com.riyadm.apkrepacker.ui.filemanager.misc.DirectoryScanner
import com.riyadm.apkrepacker.ui.filemanager.utils.CopyHelper
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.ui.widget.FabMenu
import com.riyadm.apkrepacker.utils.FileUtil
import com.riyadm.apkrepacker.utils.IntentUtils
import com.riyadm.apkrepacker.utils.PermissionsUtils
import com.riyadm.apkrepacker.utils.PreferenceUtils
import com.riyadm.apkrepacker.utils.StorageAccessRequester
import com.riyadm.apkrepacker.utils.StringUtils
import com.riyadm.apkrepacker.view.WaitingViewFlipper
import com.riyadm.apkrepacker.viewmodel.FilesFragmentViewModel
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

/**
 * Shared file browser: directory scanning, breadcrumb, multi-selection with a contextual bar and
 * floating action toolbar, clipboard (copy / cut / paste), and the expanding FAB menu.
 * Subclasses provide the screen layout and decide what tapping a file does.
 */
abstract class BaseFilesFragment : Fragment(), FileListViewHolder.OnItemClickListener, OnItemSelectedListener {

    /** The views every files screen layout must provide (via the shared sub-layouts). */
    class Screen(
        val root: View,
        val page: BaseFilesFragmentBinding,
        val pathBar: LayoutFilesPathBarBinding,
        val selectionBar: LayoutFilesSelectionBarBinding,
        val actionBar: LayoutFilesActionToolbarBinding,
        val fabMenu: FabMenu,
    )

    private val viewModel: FilesFragmentViewModel by viewModels()

    private var screen: Screen? = null
    private var mAdapter: FileAdapter? = null
    private var pathAdapter: PathButtonAdapter? = null
    private var mScanner: DirectoryScanner? = null
    private var fileObserver: FileObserver? = null

    private var mPath: String? = null
    private var mFilename: String? = null

    @JvmField
    var mDirsCount = 0

    @JvmField
    var mFilesCount = 0

    @JvmField
    var mSdCard: File? = null

    @JvmField
    var mFlag = false

    private var pasteMode = false
    private var barsShown = false
    private var askedForAccess = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val scannerHandler = Handler(Looper.getMainLooper()) { message ->
        onScannerMessage(message)
        true
    }
    private val observerRefresh = Runnable { if (view != null) refresh() }
    private val showLoadingPage = Runnable { showPage(WaitingViewFlipper.PAGE_INDEX_LOADING) }

    /** Shared storage access (API 30+: "All files access"); app-specific dirs work without it. */
    private val storageAccess = StorageAccessRequester(this) { granted ->
        if (granted && view != null) refresh()
    }

    protected val panelActions: PanelActions by lazy { PanelActions.getInstance(requireContext(), this) }

    // region Subclass contract

    protected abstract fun inflateScreen(inflater: LayoutInflater, container: ViewGroup?): Screen

    /** Whether the breadcrumb is limited to the project folders and thumbnails render vector XML. */
    protected open val projectMode: Boolean = false

    protected open fun initialPath(): File = FileUtil.getInternalStorage()

    /** Called when a selection starts or ends. */
    protected open fun onSelectionModeChanged(active: Boolean) {}

    /** Adjusts the overflow menu of a single selected [file] (items for plain files start visible). */
    protected open fun onPrepareOverflow(menu: Menu, file: File) {}

    protected open fun onOverflowItemClick(item: MenuItem, file: File): Boolean = false

    /** FAB menu entry tapped; the base class handles "new file" and "new folder". */
    protected open fun onFabItemClick(id: Int) {
        when (id) {
            R.id.fab_add_file -> panelActions.actionCreateFile(File(path.orEmpty()))
            R.id.fab_add_folder -> panelActions.actionCreateNewDirectory(File(path.orEmpty()))
        }
    }

    /** Use this callback to handle UI state when the new list data is ready and the UI has been refreshed. */
    protected open fun onDataApplied() {}

    protected open fun onFileClicked(holder: FileHolder?) {}

    // endregion

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflateScreen(inflater, container).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val s = screen ?: return
        mSdCard = FileUtil.getExternalStorage(requireContext())

        FileFeedback.attach(view) {
            when {
                s.actionBar.root.isVisible -> s.actionBar.root
                else -> s.fabMenu.anchorView
            }
        }

        setupList(s)
        setupPathBar(s)
        setupChrome(s)

        setPath(File(viewModel.path ?: initialPath().absolutePath))
        pathCheckAndFix()
        if (viewModel.files.isNotEmpty() && viewModel.displayedPath == mPath) {
            mAdapter?.submit(viewModel.files)
            showPage(WaitingViewFlipper.PAGE_INDEX_CONTENT, animate = false)
            s.page.empty.isVisible = viewModel.files.isEmpty()
        }
        refresh()
        renderChrome()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "All files access" settings page.
        val flipper = screen?.page?.flipper ?: return
        if (flipper.displayedChild == WaitingViewFlipper.PAGE_INDEX_PERMISSION_DENIED && hasPermissions()) {
            refresh()
        }
    }

    override fun onDestroyView() {
        stopScanner()
        stopWatching()
        uiHandler.removeCallbacksAndMessages(null)
        screen?.root?.let { FileFeedback.detach(it) }
        screen = null
        mAdapter = null
        pathAdapter = null
        barsShown = false
        super.onDestroyView()
    }

    // region View setup

    private fun setupList(s: Screen) {
        val adapter = FileAdapter().apply {
            setProjectMode(projectMode)
            setOnItemClickListener(this@BaseFilesFragment)
            setOnItemSelectedListener(this@BaseFilesFragment)
        }
        mAdapter = adapter
        s.page.fileList.apply {
            if (layoutManager == null) layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            applyExpressiveMotion()
        }
        FastScrollerBuilder(s.page.fileList).build()
        s.page.btnGrantStorageAccess.setOnClickListener { storageAccess.request() }
    }

    private fun setupPathBar(s: Screen) {
        val adapter = PathButtonAdapter()
        pathAdapter = adapter
        s.pathBar.pathScrollView.adapter = adapter
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                s.pathBar.pathScrollView.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
            }
        })
        adapter.setOnItemClickListener { position, _ -> navigateTo(adapter.getItem(position)) }
        s.pathBar.homeFolderApp.isVisible = projectMode
        s.pathBar.homeFolderApp.setOnClickListener { navigateTo(initialPath()) }
    }

    private fun setupChrome(s: Screen) {
        s.selectionBar.ibClearSelection.setOnClickListener {
            if (mAdapter?.anySelected() != true && pasteMode) discardClipboard() else mAdapter?.clearSelection()
        }
        s.selectionBar.actionSelectAll.setOnClickListener { mAdapter?.selectAll() }

        s.actionBar.actionCopy.setOnClickListener { stageClipboard(cut = false) }
        s.actionBar.actionCut.setOnClickListener { stageClipboard(cut = true) }
        s.actionBar.actionDelete.setOnClickListener { confirmDelete() }
        s.actionBar.actionRename.setOnClickListener { renameSelection() }
        s.actionBar.actionOverflow.setOnClickListener { showOverflow(it) }
        s.actionBar.actionPaste.setOnClickListener { pasteHere() }
        s.actionBar.actionNewFolder.setOnClickListener { panelActions.actionCreateNewDirectory(File(path.orEmpty())) }

        s.fabMenu.onItemClick = { onFabItemClick(it.id) }
    }

    // endregion

    // region Selection + clipboard chrome

    override fun onItemSelected() {
        renderChrome()
    }

    private fun renderChrome() {
        val s = screen ?: return
        val adapter = mAdapter ?: return
        val selecting = adapter.anySelected()
        val show = selecting || pasteMode
        val helper = copyHelper()

        s.actionBar.groupSelectionActions.isVisible = !pasteMode || selecting
        s.actionBar.groupPasteActions.isVisible = pasteMode && !selecting
        s.selectionBar.actionSelectAll.isVisible = selecting

        if (selecting) {
            val count = adapter.getSelectedItemCount()
            s.selectionBar.tvSelectionStatus.text = getString(R.string.selected, count)
            s.actionBar.actionOverflow.isEnabled = count == 1
            s.actionBar.actionOverflow.alpha = if (count == 1) 1f else DISABLED_ALPHA
        } else if (pasteMode && helper != null) {
            val plural = if (helper.getOperationType() == CopyHelper.COPY) R.plurals.menu_copy_items_to else R.plurals.menu_move_items_to
            s.selectionBar.tvSelectionStatus.text = resources.getQuantityString(plural, helper.getItemCount(), helper.getItemCount())
        }
        s.selectionBar.ibClearSelection.contentDescription =
            getString(if (selecting) R.string.files_action_clear_selection else R.string.files_action_cancel_paste)

        if (show != barsShown) {
            barsShown = show
            s.fabMenu.setMenuVisible(!show)
            s.selectionBar.root.animateBar(show, -BAR_SHIFT)
            s.actionBar.root.animateBar(show, BAR_SHIFT)
            onSelectionModeChanged(selecting)
        }
    }

    private fun View.animateBar(show: Boolean, fromTranslationY: Float) {
        if (show) {
            springIn(fromScale = 0.96f, fromTranslationY = fromTranslationY * resources.displayMetrics.density)
        } else {
            springOut(toScale = 0.96f) { if (!barsShown) visibility = View.GONE }
        }
    }

    private fun copyHelper(): CopyHelper? = App.get().getCopyHelper()

    private fun stageClipboard(cut: Boolean) {
        val adapter = mAdapter ?: return
        val helper = copyHelper() ?: return
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        helper.setFilesFragment(this)
        if (cut) helper.cut(items) else helper.copy(items)
        pasteMode = helper.canPaste()
        adapter.clearSelection()
        renderChrome()
    }

    private fun pasteHere() {
        val helper = copyHelper()
        if (helper != null && helper.canPaste()) helper.paste(requireContext(), File(path.orEmpty()))
        pasteMode = false
        renderChrome()
    }

    private fun discardClipboard() {
        runCatching { copyHelper()?.clear() }
        pasteMode = false
        renderChrome()
    }

    private fun confirmDelete() {
        val adapter = mAdapter ?: return
        val victims = adapter.getSelectedItems()
        if (victims.isEmpty()) return
        FileDialogs.confirm(requireContext(), R.string.confirm_delete, R.string.delete) {
            panelActions.actionDelete(*victims.toTypedArray())
            mAdapter?.clearSelection()
        }
    }

    private fun renameSelection() {
        val adapter = mAdapter ?: return
        when (adapter.getSelectedItemCount()) {
            0 -> Unit
            1 -> panelActions.actionRenameFile(adapter.getSelectedItems().first().file)
            else -> snack(R.string.files_rename_multiple_unsupported)
        }
    }

    private fun showOverflow(anchor: View) {
        val file = mAdapter?.getSelectedItems()?.singleOrNull()?.file ?: return
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.filemanager_project_menu)
        popup.menu.apply {
            findItem(R.id.action_open_with).isVisible = file.isFile
            findItem(R.id.action_share).isVisible = file.isFile
            findItem(R.id.action_open_in_editor).isVisible = false
            findItem(R.id.action_copy_id).isVisible = false
        }
        onPrepareOverflow(popup.menu, file)
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_with -> openWithSystem(file)
                R.id.action_share -> panelActions.actionShare(FileHolder(file, requireContext()))
                R.id.action_copy_path -> copyText(file.absolutePath)
                R.id.action_copy_name -> copyText(file.name)
                else -> if (!onOverflowItemClick(item, file)) return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    // endregion

    // region Helpers for subclasses

    protected fun snack(@StringRes message: Int) {
        context?.let { FileFeedback.show(it, message) }
    }

    protected fun snack(message: CharSequence) {
        FileFeedback.show(message)
    }

    protected fun copyText(text: String) {
        StringUtils.setClipboard(requireContext(), text, false)
        // Android 13+ confirms clipboard writes itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snack(R.string.files_copied_to_clipboard)
    }

    protected fun openWithSystem(file: File) {
        try {
            startActivity(IntentUtils.openFileWithIntent(file))
        } catch (e: ActivityNotFoundException) {
            snack(R.string.files_cant_open_file)
        }
    }

    /** Back for the shared chrome: closes the FAB menu, then leaves selection mode. */
    protected fun consumeBackForChrome(): Boolean {
        val menu = screen?.fabMenu
        if (menu?.isOpen == true) {
            menu.close()
            return true
        }
        if (mAdapter?.anySelected() == true) {
            mAdapter?.clearSelection()
            return true
        }
        return false
    }

    protected fun navigateTo(directory: File) {
        rememberScroll()
        setPath(directory)
        refresh()
    }

    // endregion

    // region Loading

    /** Reloads the current directory. */
    open fun refresh() {
        if (view == null) return
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        if (viewModel.displayedPath != mPath) {
            uiHandler.postDelayed(showLoadingPage, LOADING_DELAY_MS)
        }
        renewScanner().start()
    }

    /** Points this browser at [directory] and reloads it. Ignored while the view doesn't exist. */
    open fun refresh(c: Context?, directory: File?) {
        if (view == null || directory == null) return
        setPath(directory)
        refresh()
    }

    private fun hasPermissions(): Boolean {
        val context = context ?: return false
        return PermissionsUtils.canAccessPath(context, mPath)
    }

    /** Shows the "permission denied" page; the first time it also asks right away. */
    private fun requestPermissions() {
        showPage(WaitingViewFlipper.PAGE_INDEX_PERMISSION_DENIED)
        if (!askedForAccess) {
            askedForAccess = true
            storageAccess.request()
        }
    }

    private fun showPage(index: Int, animate: Boolean = true) {
        val flipper = screen?.page?.flipper ?: return
        if (flipper.displayedChild == index) return
        flipper.displayedChild = index
        if (animate) flipper.currentView?.springIn(fromScale = 0.96f)
    }

    /** Recreates the scanner for [path], cancelling the previous one so it can't load on top of the new list. */
    protected open fun renewScanner(): DirectoryScanner {
        stopScanner()
        val scanner = DirectoryScanner(
            File(mPath.orEmpty()),
            requireActivity(),
            scannerHandler,
            "",
            "*/*",
            false,
            false,
        )
        mScanner = scanner
        return scanner
    }

    private fun stopScanner() {
        mScanner?.cancel()
    }

    open fun isScannerRunning(): Boolean = mScanner?.let { it.isAlive && it.isRunning() } == true

    protected open fun hasScanner(): Boolean = mScanner != null

    private fun onScannerMessage(message: Message) {
        if (message.what != DirectoryScanner.MESSAGE_SHOW_DIRECTORY_CONTENTS) return
        val s = screen ?: return
        val contents = message.obj as? DirectoryHolder ?: return
        uiHandler.removeCallbacks(showLoadingPage)

        val files = ArrayList<FileHolder>().apply {
            addAll(contents.listSdCard.orEmpty())
            addAll(contents.listDir.orEmpty())
            addAll(contents.listFile.orEmpty())
        }
        mDirsCount = contents.listDir?.size ?: 0
        mFilesCount = contents.listFile?.size ?: 0

        val pathChanged = viewModel.displayedPath != mPath
        viewModel.files.clear()
        viewModel.files.addAll(files)
        viewModel.displayedPath = mPath

        mAdapter?.submit(files)
        s.page.empty.isVisible = files.isEmpty()
        showPage(WaitingViewFlipper.PAGE_INDEX_CONTENT)
        if (pathChanged) restoreScroll(s)
        onDataApplied()
    }

    private fun rememberScroll() {
        val key = mPath ?: return
        val manager = screen?.page?.fileList?.layoutManager as? LinearLayoutManager ?: return
        viewModel.scrollPositions[key] = manager.findFirstCompletelyVisibleItemPosition().coerceAtLeast(0)
    }

    private fun restoreScroll(s: Screen) {
        val position = mPath?.let { viewModel.scrollPositions.remove(it) } ?: 0
        val manager = s.page.fileList.layoutManager as? LinearLayoutManager ?: return
        s.page.fileList.post { manager.scrollToPositionWithOffset(position, 0) }
    }

    /** Remembers where the list is scrolled to; restoring happens automatically when a folder is shown again. */
    open fun savePosition(save: Boolean) {
        if (save) rememberScroll()
    }

    // endregion

    // region Path

    /** The currently displayed directory's absolute path. */
    val path: String?
        get() = mPath

    /** Sets the directory to show (doesn't reload; call [refresh]). */
    fun setPath(dir: File) {
        mPath = dir.absolutePath
        if (isAdded) viewModel.path = mPath
        if (view != null && dir.exists()) startWatching(dir)
    }

    private fun pathCheckAndFix() {
        val dir = File(mPath.orEmpty())
        // A file path (e.g. from an extra) falls back to its parent folder and remembers the name.
        if (!dir.isDirectory && dir.parentFile != null) {
            mFilename = dir.name
            setPath(dir.parentFile!!)
        }
    }

    open val filename: String?
        get() = mFilename

    open val fileAdapter: FileAdapter?
        get() = mAdapter

    /** Updates the breadcrumb; subclasses call this from [onDataApplied]. */
    protected fun updateBreadcrumb() {
        pathAdapter?.setPath(File(mPath.orEmpty()), projectMode)
    }

    // endregion

    // region Sorting / storage

    open fun actionSort() {
        val checked = PreferenceUtils.getInteger(requireContext(), "pref_sort", 0)
        val labels = arrayOf<CharSequence>(
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_size),
        )
        FileDialogs.chooseOne(requireContext(), R.string.sort_by, labels, checked) { which ->
            PreferenceUtils.putInt(requireContext(), "pref_sort", which)
            refresh()
        }
    }

    open fun actionGotoSDCard() {
        if (mFlag) {
            mFlag = false
            setPath(FileUtil.getInternalStorage())
            refresh()
        } else {
            mSdCard?.let {
                mFlag = true
                setPath(it)
                refresh()
            }
        }
    }

    // endregion

    // region File observer

    private fun startWatching(directory: File) {
        stopWatching()
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO
        // Rescans are debounced: copying or compressing floods the observer with events.
        val onChange = {
            uiHandler.removeCallbacks(observerRefresh)
            uiHandler.postDelayed(observerRefresh, OBSERVER_DEBOUNCE_MS)
        }
        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (event != IN_IGNORED) onChange()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (event != IN_IGNORED) onChange()
                }
            }
        }.also { it.startWatching() }
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    // endregion

    // Default behavior for a long press: toggle the row's selection.
    override fun onLongClick(item: FileHolder, position: Int) {
        mAdapter?.toggle(position)
    }

    private companion object {
        const val IN_IGNORED = 32768
        const val OBSERVER_DEBOUNCE_MS = 500L
        const val LOADING_DELAY_MS = 150L
        const val BAR_SHIFT = 24f
        const val DISABLED_ALPHA = 0.38f
    }
}
