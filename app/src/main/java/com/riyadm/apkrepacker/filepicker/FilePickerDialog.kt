package com.riyadm.apkrepacker.filepicker

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogFilePickerBinding
import com.riyadm.apkrepacker.databinding.ItemFilePickerChipBinding
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.utils.PermissionsUtils
import java.io.File

/**
 * Material 3 file/folder picker: a full-height sheet with a breadcrumb of path chips and
 * segmented list rows. Public API is unchanged from the dialog it replaces.
 */
class FilePickerDialog(hostContext: Context) : BottomSheetDialog(themedContext(hostContext)) {

    private val host: Context = hostContext
    private var binding: DialogFilePickerBinding? = null
    private lateinit var adapter: FileListAdapter
    private var filter: ExtensionFilter? = null
    private var listener: FileDialogListener? = null

    private var titleStr: String? = null
    private var selectBtnText: String? = null
    private var cancelBtnText: String? = null

    private var selectMode: Int = MODE_SINGLE
    private var selectType: Int = TYPE_FILE
    private var rootDir: File = File(DEFAULT_DIR)
    private var primaryDir: File = File(DEFAULT_DIR)
    private var extensions: Array<String>? = arrayOf("")

    private var curDir: File? = null
    private var sdRootActive = false
    private var backCancelable = true
    private var outsideCancelable = true
    private val positionMap = HashMap<String, Int>()

    /** Back goes up one directory; at the root it cancels (when back-cancelable). */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!navigateUp() && backCancelable) cancel()
        }
    }

    init {
        // The sheet's own back/outside handling would hide it from anywhere; navigation is ours.
        super.setCancelable(false)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val views = DialogFilePickerBinding.inflate(layoutInflater)
        binding = views
        setContentView(views.root)
        configureSheet()

        filter = ExtensionFilter(selectType, extensions)
        adapter = FileListAdapter(selectType, rowCallbacks)
        views.pickerList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FilePickerDialog.adapter
            applyExpressiveMotion()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val first = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return
                    curDir?.let { positionMap[it.absolutePath] = first }
                }
            })
        }

        renderTitle()
        views.pickerCancel.text = cancelBtnText ?: host.getString(R.string.cancel_button_label)
        views.pickerCancel.setOnClickListener {
            listener?.onCanceled()
            dismiss()
        }
        views.pickerSelect.setOnClickListener {
            listener?.onSelectedFilePaths(MarkedItemList.selectedPaths)
            dismiss()
        }
        setOnCancelListener { listener?.onCanceled() }

        views.pickerStorageToggle.visibility =
            if (Utility.getExternalStoragePath(host, true) != null) View.VISIBLE else View.GONE
        views.pickerStorageToggle.setOnClickListener { toggleStorage() }

        updateSelectButton()
        if (PermissionsUtils.canAccessPath(host, rootDir.absolutePath)) setUp()
    }

    override fun onStart() {
        super.onStart()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun configureSheet() {
        findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        behavior.apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isHideable = backCancelable || outsideCancelable
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private val rowCallbacks = object : FileListAdapter.Callbacks {
        override fun onOpenDirectory(item: FileListItem) {
            val dir = File(item.path)
            if (dir.canRead()) {
                showDirectory(dir)
            } else {
                snack(host.getString(R.string.error_dir_access))
            }
        }

        override fun onToggle(item: FileListItem, checked: Boolean): Boolean {
            val accepted = applySelection(item, checked)
            if (!accepted) snack(host.getString(R.string.toast_error_not_selectable))
            updateSelectButton()
            return accepted
        }

        override fun onNotSelectable() = snack(host.getString(R.string.toast_error_not_selectable))
    }

    /** Applies the MODE_SINGLE/MODE_MULTI rules; false if a parent folder is already selected. */
    private fun applySelection(item: FileListItem, checked: Boolean): Boolean {
        if (!checked) {
            MarkedItemList.removeSelectedItem(item.path)
            item.isMarked = false
            return true
        }
        if (selectMode == MODE_MULTI) {
            for (path in MarkedItemList.selectedPaths) {
                if (item.path.startsWith("$path/")) return false
                // The new item contains an already selected child: the child is subsumed.
                if (path.startsWith(item.path + "/")) MarkedItemList.removeSelectedItem(path)
            }
            MarkedItemList.addMultiItem(item)
        } else {
            MarkedItemList.addSingleFile(item)
        }
        item.isMarked = true
        return true
    }

    private fun setUp() {
        val start = when {
            primaryDir.isDirectory && primaryDir.absolutePath != rootDir.absolutePath &&
                primaryDir.absolutePath.contains(rootDir.absolutePath) -> primaryDir
            rootDir.exists() && rootDir.isDirectory -> rootDir
            else -> File(DEFAULT_DIR)
        }
        positionMap.clear()
        showDirectory(start)
    }

    /** Lists [dir]; when climbing out of a subdirectory its scroll position is restored. */
    private fun showDirectory(dir: File) {
        val views = binding ?: return
        val leaving = curDir
        curDir = dir

        val entries = ArrayList<FileListItem>()
        val parent = dir.parentFile
        if (dir.absolutePath != rootDir.absolutePath && parent != null) {
            entries.add(
                FileListItem(
                    host.getString(R.string.label_parent_dir), parent.absolutePath, dir.lastModified(),
                    isDirectory = true, isParentRow = true,
                ),
            )
        }
        adapter.submit(Utility.prepareFileListEntries(entries, dir, filter))
        renderBreadcrumb(dir)

        var position = 0
        if (leaving != null && leaving.absolutePath.startsWith(dir.absolutePath + "/")) {
            positionMap.remove(leaving.absolutePath)
            position = positionMap[dir.absolutePath] ?: 0
            if (position == 0) {
                position = entries.indexOfFirst { it.path == leaving.absolutePath }.coerceAtLeast(0)
            }
        }
        (views.pickerList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
    }

    /** One level up, unless already at the root. */
    private fun navigateUp(): Boolean {
        val dir = curDir ?: return false
        val parent = dir.parentFile
        if (parent == null || dir.absolutePath == rootDir.absolutePath || !parent.canRead()) return false
        showDirectory(parent)
        return true
    }

    private fun renderBreadcrumb(dir: File) {
        val views = binding ?: return
        val root = rootDir.absolutePath
        val underRoot = dir.absolutePath == root || dir.absolutePath.startsWith("$root/")
        val trail = ArrayList<File>()
        var node: File? = dir
        while (node != null) {
            trail.add(0, node)
            if (underRoot && node.absolutePath == root) break
            node = node.parentFile
        }

        val group = views.pickerPathChips
        group.removeAllViews()
        trail.forEachIndexed { index, segment ->
            val chip = ItemFilePickerChipBinding.inflate(layoutInflater, group, false).root as Chip
            val isFirst = index == 0 && underRoot
            val isCurrent = index == trail.lastIndex
            chip.text = when {
                isFirst -> host.getString(if (sdRootActive) R.string.pick_root_sd else R.string.pick_root_internal)
                else -> segment.name.ifEmpty { "/" }
            }
            if (isFirst) {
                chip.chipIcon = ResourcesCompat.getDrawable(
                    host.resources, if (sdRootActive) R.drawable.ic_sd_card else R.drawable.ic_phone_android, context.theme,
                )
                chip.isChipIconVisible = true
            }
            if (isCurrent) {
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondaryContainer),
                )
                chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSecondaryContainer))
                chip.chipStrokeWidth = 0f
            }
            chip.setOnClickListener { if (segment != curDir) showDirectory(segment) }
            group.addView(chip)
        }
        views.pickerPathScroll.post { views.pickerPathScroll.scrollTo(group.width, 0) }
    }

    private fun toggleStorage() {
        val views = binding ?: return
        if (!sdRootActive) {
            val sd = Utility.getExternalStoragePath(host, true) ?: return
            rootDir = File(sd)
            sdRootActive = true
            views.pickerStorageToggle.setIconResource(R.drawable.ic_phone_android)
            views.pickerStorageToggle.contentDescription = host.getString(R.string.pick_switch_to_internal)
        } else {
            rootDir = File(DEFAULT_DIR)
            sdRootActive = false
            views.pickerStorageToggle.setIconResource(R.drawable.ic_sd_card)
            views.pickerStorageToggle.contentDescription = host.getString(R.string.pick_switch_to_sd)
        }
        curDir = null
        setUp()
    }

    private fun renderTitle() {
        binding?.pickerTitle?.text = titleStr?.takeIf { it.isNotEmpty() } ?: host.getString(R.string.label_path)
    }

    private fun updateSelectButton() {
        val views = binding ?: return
        val base = selectBtnText ?: host.getString(R.string.choose_button_label)
        val count = MarkedItemList.fileCount
        views.pickerSelect.isEnabled = count > 0
        views.pickerSelect.text = if (count == 0) base else host.getString(R.string.pick_select_with_count, base, count)
    }

    private fun snack(message: CharSequence) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_SHORT).setAnchorView(views.pickerFooter).show()
    }

    // ---- public API (unchanged) ----

    fun setDialogListener(selectBtnText: CharSequence?, cancelBtnText: CharSequence?, listener: FileDialogListener?): FilePickerDialog {
        this.selectBtnText = selectBtnText?.toString()
        this.cancelBtnText = cancelBtnText?.toString()
        this.listener = listener
        return this
    }

    fun setTitleText(titleStr: CharSequence?): FilePickerDialog {
        this.titleStr = titleStr?.toString()
        renderTitle()
        return this
    }

    fun setSelectMode(selectMode: Int): FilePickerDialog {
        this.selectMode = selectMode
        return this
    }

    fun setSelectType(selectType: Int): FilePickerDialog {
        this.selectType = selectType
        return this
    }

    fun setRootDir(rootDir: String): FilePickerDialog {
        this.rootDir = File(rootDir)
        return this
    }

    fun setPrimaryDir(primaryDir: String): FilePickerDialog {
        this.primaryDir = File(primaryDir)
        return this
    }

    fun setExtensions(extensions: Array<String>?): FilePickerDialog {
        this.extensions = extensions
        return this
    }

    fun setBackCancelable(cancelable: Boolean): FilePickerDialog {
        backCancelable = cancelable
        return this
    }

    fun setOutsideCancelable(cancelable: Boolean): FilePickerDialog {
        outsideCancelable = cancelable
        return this
    }

    override fun show() {
        // The pickers browse shared storage: without access, ask for it instead of showing an
        // empty list (API 30+: "All files access" settings page).
        if (!PermissionsUtils.canAccessPath(host, rootDir.absolutePath)) {
            PermissionsUtils.requestStorageAccess(host)
            return
        }
        try {
            super.show()
        } catch (e: WindowManager.BadTokenException) {
            // Created from a non-activity context (e.g. the application context): nothing to attach to.
            Log.w(TAG, "File picker needs an activity context", e)
        }
    }

    override fun dismiss() {
        MarkedItemList.clearSelectionList()
        super.dismiss()
    }

    interface FileDialogListener {
        fun onSelectedFilePaths(filePaths: Array<String>)

        fun onCanceled()
    }

    companion object {
        private const val TAG = "FilePickerDialog"

        @JvmField
        val DEFAULT_DIR: String = Environment.getExternalStorageDirectory().absolutePath
        const val EXTERNAL_READ_PERMISSION_GRANT = 0x1a
        const val MODE_SINGLE = 0
        const val MODE_MULTI = 1
        const val TYPE_ALL = 0
        const val TYPE_FILE = 1
        const val TYPE_DIR = 2

        /** Sheets need a Material-themed context; the application context may be all we get. */
        private fun themedContext(context: Context): Context =
            if (PermissionsUtils.findActivity(context) != null) context
            else ContextThemeWrapper(context, R.style.Theme_Revived_Dark)
    }
}
