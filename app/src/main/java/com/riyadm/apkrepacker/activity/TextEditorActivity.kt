package com.riyadm.apkrepacker.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.jecelyin.editor.v2.EditorPreferences
import com.jecelyin.editor.v2.common.Command
import com.jecelyin.editor.v2.dialog.CharsetsDialog
import com.jecelyin.editor.v2.dialog.GotoLineDialog
import com.jecelyin.editor.v2.dialog.LangListDialog
import com.jecelyin.editor.v2.io.FileEncodingDetector
import com.jecelyin.editor.v2.manager.EditorPager
import com.jecelyin.editor.v2.manager.TabManager
import com.jecelyin.editor.v2.widget.EditorMessages
import com.jecelyin.editor.v2.widget.SymbolBarLayout
import com.jecelyin.editor.v2.widget.menu.MenuDef
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.databinding.ActivityCodeEditorBinding
import com.riyadm.apkrepacker.databinding.EditorFindPanelBinding
import com.riyadm.apkrepacker.ide.editor.EditorDelegate
import com.riyadm.apkrepacker.ide.editor.SearchPanel
import com.riyadm.apkrepacker.ide.editor.task.SaveAllTask
import com.riyadm.apkrepacker.ide.file.SaveListener
import com.riyadm.apkrepacker.task.Smali2JavaTask
import com.riyadm.apkrepacker.ui.motion.MotionSpring
import com.riyadm.apkrepacker.ui.motion.applyExpressiveMotion
import com.riyadm.apkrepacker.ui.motion.springIn
import com.riyadm.apkrepacker.ui.motion.springOut
import com.riyadm.apkrepacker.ui.motion.springTo
import com.riyadm.apkrepacker.ui.projectview.ProjectTreeStructureFragment
import com.riyadm.apkrepacker.ui.projectview.treeview.interfaces.FileChangeListener
import com.riyadm.apkrepacker.utils.ProjectUtils
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File

/**
 * The code editor screen: a toolbar with the file actions, the open files in the start drawer,
 * the project tree in the end drawer, a find bar and a floating bar of symbol keys.
 */
class TextEditorActivity : BaseActivity(), SharedPreferences.OnSharedPreferenceChangeListener, FileChangeListener {

    private lateinit var binding: ActivityCodeEditorBinding
    private lateinit var mEditorPreferences: EditorPreferences

    val mToolbar: MaterialToolbar get() = binding.toolbar
    val mTabPager: EditorPager get() = binding.tabPager
    val mProjectNavigationView: NavigationView get() = binding.rightNavigationView
    val mDrawerLayout: DrawerLayout get() = binding.drawerLayout
    val mTabRecyclerView: RecyclerView get() = binding.openTabsList
    val mSymbolBarLayout: SymbolBarLayout get() = binding.symbolBarLayout
    val tabRecyclerView: RecyclerView get() = binding.openTabsList

    /** The find bar's views; [SearchPanel] drives them. */
    val findPanelBinding: EditorFindPanelBinding get() = binding.findPanel

    val searchPanel: SearchPanel by lazy { SearchPanel(this).also { it.setActivity(this) } }

    var tabManager: TabManager? = null
        private set

    private var lastBackPress = 0L

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        try {
            super.onRestoreInstanceState(savedInstanceState)
        } catch (e: Exception) {
            DLog.d(e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        mEditorPreferences = EditorPreferences.getInstance(this)

        binding.drawerLayout.keepScreenOn = mEditorPreferences.isKeepScreenOn
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                runCatching { currentEditorDelegate?.editText?.clearFocus() }
                binding.drawerLayout.requestFocus()
                doCommand(Command(Command.CommandEnum.HIDE_SOFT_INPUT))
            }
        })

        binding.symbolBarLayout.setOnSymbolCharClickListener { _, text -> insertText(text) }
        mEditorPreferences.registerOnSharedPreferenceChangeListener(this)
        updateSymbolToolbar(animate = false)
        setScreenOrientation()

        initToolbar()
        initOpenFilesDrawer()
        binding.openTabsList.layoutManager = LinearLayoutManager(this)
        binding.openTabsList.applyExpressiveMotion()
        tabManager = TabManager(this)
        showEmptyTabsHint(true)

        if (savedInstanceState == null || supportFragmentManager.findFragmentByTag(ProjectTreeStructureFragment.TAG) == null) {
            supportFragmentManager.commit {
                replace(R.id.project_tree_container, ProjectTreeStructureFragment(), ProjectTreeStructureFragment.TAG)
            }
        }
        processIntent()
    }

    override fun onDestroy() {
        mEditorPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    // ---- toolbar and drawers ----------------------------------------------------------------

    @SuppressLint("RestrictedApi")
    private fun initToolbar() {
        binding.toolbar.inflateMenu(R.menu.code_editor_menu)
        // Lets the overflow menu show its icons next to the labels.
        (binding.toolbar.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        binding.toolbar.setOnMenuItemClickListener { item ->
            onMenuClick(item.itemId)
            true
        }
        syncCheckableMenuItems()
        setMenuStatus(R.id.action_save, MenuDef.STATUS_DISABLED)
        setMenuStatus(R.id.action_undo, MenuDef.STATUS_DISABLED)
        setMenuStatus(R.id.action_redo, MenuDef.STATUS_DISABLED)
    }

    private fun initOpenFilesDrawer() {
        binding.openFilesProject.text = ProjectUtils.getProjectName()
        binding.openFilesMenu.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                inflate(R.menu.m3f_tabs_menu)
                setOnMenuItemClickListener { item ->
                    onMenuClick(item.itemId)
                    true
                }
            }.show()
        }
    }

    private fun syncCheckableMenuItems() {
        binding.toolbar.menu.findItem(R.id.action_readonly)?.isChecked = mEditorPreferences.isReadOnly
        binding.toolbar.menu.findItem(R.id.action_hide_symbol_panel)?.isChecked = mEditorPreferences.isHidePanel
    }

    /** Called by the tab manager whenever the selected tab or its document changes. */
    fun updateToolbarTitle(delegate: EditorDelegate?) {
        if (delegate == null) {
            binding.toolbar.title = getString(R.string.code_editor)
            binding.toolbar.subtitle = null
            return
        }
        binding.toolbar.title = delegate.title.orEmpty() + if (delegate.isChanged) " •" else ""
        val encoding = delegate.encoding
        val language = runCatching { delegate.editText.lang }.getOrNull()
        binding.toolbar.subtitle = when {
            encoding != null && language != null -> getString(R.string.m3f_encoding_language, encoding, language)
            else -> encoding ?: language
        }
    }

    fun showEmptyTabsHint(empty: Boolean) {
        binding.openTabsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) updateToolbarTitle(null)
    }

    /** Enables or disables a toolbar action (save, undo, redo follow the document state). */
    fun setMenuStatus(@IdRes menuResId: Int, status: Int) {
        if (!this::binding.isInitialized) return
        binding.toolbar.menu.findItem(menuResId)?.isEnabled = status != MenuDef.STATUS_DISABLED
    }

    // ---- preferences --------------------------------------------------------------------------

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!this::binding.isInitialized) return
        when (key) { // key is null when the prefs are cleared (API 30+)
            EditorPreferences.KEY_ENABLE_HIGHLIGHT -> {
                val command = Command(Command.CommandEnum.HIGHLIGHT)
                command.`object` = if (mEditorPreferences.isHighlight) null else "None"
                doClusterCommand(command)
            }
            EditorPreferences.KEY_SCREEN_ORIENTATION -> setScreenOrientation()
            EditorPreferences.KEY_READ_ONLY,
            EditorPreferences.KEY_HIDE_SYMBOL_PANEL -> {
                updateSymbolToolbar(animate = true)
                syncCheckableMenuItems()
            }
        }
    }

    private fun setScreenOrientation() {
        requestedOrientation = when (mEditorPreferences.screenOrientation) {
            EditorPreferences.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            EditorPreferences.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** The symbol keys are hidden in read-only mode and when the user turned the panel off. */
    private fun updateSymbolToolbar(animate: Boolean) {
        val bar = binding.symbolToolbar
        val show = !mEditorPreferences.isReadOnly && !mEditorPreferences.isHidePanel
        val rise = resources.displayMetrics.density * 16f
        if (show) {
            if (bar.visibility == View.VISIBLE && bar.alpha == 1f) return
            if (animate) bar.springIn(fromScale = 0.9f, fromTranslationY = rise) else {
                bar.alpha = 1f
                bar.visibility = View.VISIBLE
            }
        } else if (bar.visibility == View.VISIBLE) {
            if (animate) {
                bar.springTo(androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, rise, MotionSpring.FastSpatial)
                bar.springOut(toScale = 0.9f) {
                    bar.visibility = View.GONE
                    bar.alpha = 1f
                    bar.scaleX = 1f
                    bar.scaleY = 1f
                    bar.translationY = 0f
                }
            } else {
                bar.visibility = View.GONE
            }
        }
    }

    // ---- intents ------------------------------------------------------------------------------

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent()
    }

    private fun processIntent() {
        val path = intent.getStringExtra("filePath")
        val offset = intent.getIntExtra("offset", 1)
        if (offset != 0) {
            openFile(path, FileEncodingDetector.DEFAULT_ENCODING, offset)
        } else {
            openFile(path, null, 0)
        }
    }

    // ---- menu ---------------------------------------------------------------------------------

    private fun onMenuClick(id: Int) {
        closeMenu()
        when (id) {
            R.id.action_explore -> binding.drawerLayout.openDrawer(GravityCompat.END)
            R.id.action_save -> doCommand(Command(Command.CommandEnum.SAVE))
            R.id.action_undo -> doCommand(Command(Command.CommandEnum.UNDO))
            R.id.action_redo -> doCommand(Command(Command.CommandEnum.REDO))
            R.id.action_find_replace -> doCommand(Command(Command.CommandEnum.FIND))
            R.id.action_goto_top -> doCommand(Command(Command.CommandEnum.GOTO_TOP))
            R.id.action_goto_end -> doCommand(Command(Command.CommandEnum.GOTO_END))
            R.id.action_goto_line -> currentEditorDelegate?.let { GotoLineDialog(this, it).show() }
            R.id.action_smali_java -> decompileSmali()
            R.id.action_highlight -> LangListDialog(this).show()
            R.id.action_readonly -> {
                mEditorPreferences.isReadOnly = !mEditorPreferences.isReadOnly
                doClusterCommand(Command(Command.CommandEnum.READONLY_MODE))
                syncCheckableMenuItems()
            }
            R.id.action_hide_symbol_panel -> {
                mEditorPreferences.isHidePanel = !mEditorPreferences.isHidePanel
                updateSymbolToolbar(animate = true)
                syncCheckableMenuItems()
            }
            R.id.action_encoding -> CharsetsDialog(this).show()
            R.id.action_info -> doCommand(Command(Command.CommandEnum.DOC_INFO))
            R.id.action_editor_setting -> EditorSettingsActivity.open(this, RC_SETTINGS)
            R.id.action_exit -> if (tabManager?.onDestroy() != false) finish()
            R.id.action_close_unchanged -> tabManager?.closeAllUnchanged()
            R.id.action_save_all -> SaveAllTask(this, object : SaveListener {
                override fun onSavedSuccess() = EditorMessages.show(this@TextEditorActivity, R.string.m3f_saved_all)

                override fun onSaveFailed(e: Exception?) =
                    EditorMessages.error(this@TextEditorActivity, getString(R.string.m3f_save_failed, e?.message.orEmpty()))
            }).execute()
        }
    }

    private fun decompileSmali() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.attention_title)
                .setMessage(R.string.jadx_doesnt_support_on_this_prone)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val path = currentEditorDelegate?.path ?: return
        if (path.endsWith(".smali", ignoreCase = true)) {
            EditorMessages.show(this, R.string.m3f_decompiling)
            Smali2JavaTask(this).execute(File(path))
        } else {
            EditorMessages.show(this, R.string.m3f_smali_only)
        }
    }

    fun closeMenu() {
        binding.drawerLayout.closeDrawers()
    }

    // ---- commands -----------------------------------------------------------------------------

    /** Runs [command] on every open editor. */
    fun doClusterCommand(command: Command?) {
        if (command == null) return
        tabManager?.editorAdapter?.allEditor?.forEach { it?.doCommand(command) }
    }

    /** Runs [command] on the current editor. */
    fun doCommand(command: Command) {
        currentEditorDelegate?.doCommand(command)
    }

    val currentEditorDelegate: EditorDelegate?
        get() = tabManager?.editorAdapter?.currentEditorDelegate

    val currentLang: String?
        get() = currentEditorDelegate?.let { runCatching { it.editText.lang }.getOrNull() }

    fun insertText(text: CharSequence?) {
        if (text == null) return
        val command = Command(Command.CommandEnum.INSERT_TEXT)
        command.`object` = text
        doCommand(command)
    }

    // ---- files --------------------------------------------------------------------------------

    fun openJavaText(content: CharSequence?, name: String?) {
        if (content.isNullOrEmpty()) return
        try {
            val file = File.createTempFile((name ?: "Decompiled").padEnd(3, '_'), ".java")
            file.writeText(content.toString(), Charsets.UTF_8)
            tabManager?.newTab(file)
        } catch (e: Exception) {
            e.printStackTrace()
            EditorMessages.error(this, e.message)
        }
    }

    fun openFile(file: String?, encoding: String?, offset: Int) {
        if (file.isNullOrEmpty()) return
        val f = File(file)
        if (!f.isFile) {
            EditorMessages.show(this, R.string.file_not_exists)
            return
        }
        if (tabManager?.newTab(f, offset, encoding) != true) return
        JsonDatabase.getInstance(this).addRecentFile(file, encoding)
    }

    override fun onFileDeleted(deleted: File) {}

    override fun onFileCreated(newFile: File) {}

    override fun doOpenFile(toEdit: String) {
        openFile(toEdit, null, 0)
    }

    // ---- back ---------------------------------------------------------------------------------

    /**
     * Back: close an open drawer or the find bar, else ask to save changed tabs, else (optionally
     * after a second press) leave. A callback because predictive back no longer calls onBackPressed().
     */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (closeDrawers()) return
            if (searchPanel.isShowing) {
                searchPanel.hide()
                return
            }
            if (tabManager == null || tabManager!!.onDestroy()) {
                val now = System.currentTimeMillis()
                if (now - lastBackPress > 2000 && mEditorPreferences.isConfirmExit) {
                    EditorMessages.show(this@TextEditorActivity, R.string.press_again_will_exit)
                    lastBackPress = now
                } else {
                    performDefaultBack(this)
                }
            }
        }
    }

    private fun closeDrawers(): Boolean {
        var closed = false
        for (gravity in intArrayOf(GravityCompat.START, GravityCompat.END)) {
            if (binding.drawerLayout.isDrawerOpen(gravity)) {
                binding.drawerLayout.closeDrawer(gravity)
                closed = true
            }
        }
        return closed
    }

    private companion object {
        const val RC_SETTINGS = 5
    }
}
