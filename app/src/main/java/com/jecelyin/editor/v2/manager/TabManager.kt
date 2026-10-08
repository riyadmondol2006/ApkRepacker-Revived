/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.manager

import android.content.DialogInterface
import android.database.DataSetObserver
import androidx.viewpager.widget.ViewPager
import com.jecelyin.editor.v2.adapter.EditorAdapter
import com.jecelyin.editor.v2.adapter.TabAdapter
import com.jecelyin.editor.v2.common.TabCloseListener
import com.jecelyin.editor.v2.dialog.SaveConfirmDialog
import com.jecelyin.editor.v2.widget.EditorMessages
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.database.entity.RecentFileItem
import com.riyadm.apkrepacker.ide.editor.task.SaveAllTask
import com.riyadm.apkrepacker.ide.file.SaveListener
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.view.EditorView
import java.io.File

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class TabManager(activity: TextEditorActivity) : ViewPager.OnPageChangeListener {
    private val mainActivity: TextEditorActivity = activity
    private val tabAdapter: TabAdapter = TabAdapter()
    lateinit var editorAdapter: EditorAdapter
        private set

    init {
        tabAdapter.setOnTabClickListener(
            onClick = { position ->
                mainActivity.closeMenu()
                currentTab = position
            },
            onClose = { position -> closeTab(position) }
        )
        mainActivity.tabRecyclerView.adapter = tabAdapter

        initEditor()

        mainActivity.mTabPager.addOnPageChangeListener(this)
    }

    private fun initEditor() {
        editorAdapter = EditorAdapter(mainActivity)
        mainActivity.mTabPager.adapter = editorAdapter //优先，避免TabAdapter获取不到正确的CurrentItem

      /*  if (Pref.getInstance(mainActivity).isOpenLastFiles()) {
            ArrayList<DBHelper.RecentFileItem> recentFiles = DBHelper.getInstance(mainActivity).getRecentFiles(true);

            File f;
            for (DBHelper.RecentFileItem item : recentFiles) {
                f = new File(item.path);
                if (!f.isFile())
                    continue;
                editorAdapter.newEditor(false, f, item.offset, item.encoding);
                setCurrentTab(editorAdapter.getCount() - 1); //fixme: auto load file, otherwise click other tab will crash by search result
            }
            editorAdapter.notifyDataSetChanged();
            updateTabList();

            int lastTab = Pref.getInstance(mainActivity).getLastTab();
            setCurrentTab(lastTab);
        }*/

        editorAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                updateTabList()

             /*   if (!exitApp && editorAdapter.getCount() == 0) {
                   // newTab();
                }*/
            }
        })

        //if (editorAdapter.getCount() == 0)
        // editorAdapter.newEditor("test" /*mainActivity.getString(R.string.new_filename, editorAdapter.countNoFileEditor() + 1)*/, null);
    }

    fun newTab() {
        editorAdapter.newEditor("test"/*mainActivity.getString(R.string.new_filename, editorAdapter.getCount() + 1)*/, null)
        currentTab = editorAdapter.count - 1
    }

    fun newTab(content: CharSequence?): Boolean {
        editorAdapter.newEditor("test"/*mainActivity.getString(R.string.new_filename, editorAdapter.getCount() + 1)*/, content)
        currentTab = editorAdapter.count - 1
        return true
    }

    fun newTab(path: File): Boolean {
        return newTab(path, 0, "utf-8")
    }

    fun newTab(path: File, encoding: String?): Boolean {
        return newTab(path, 0, encoding)
    }

    fun newTab(path: File, offset: Int, encoding: String?): Boolean {
        val count = editorAdapter.count
        for (i in 0 until count) {
            val fragment = editorAdapter.getItem(i)!!
            if (fragment.path == null)
                continue
            if (fragment.path == path.path) {
                currentTab = i
                return false
            }
        }
        editorAdapter.newEditor(path, offset, encoding)
        currentTab = count
        return true
    }

    val tabCount: Int
        get() = editorAdapter.count

    var currentTab: Int
        get() = mainActivity.mTabPager.currentItem
        set(index) {
            mainActivity.mTabPager.currentItem = index
            tabAdapter.setCurrentTab(index)
            updateToolbar()
        }

    fun closeTab(position: Int) {
        editorAdapter.removeEditor(position, { path, _, _ ->
            JsonDatabase.getInstance(mainActivity).updateRecentFile(path, false)
            val currentTab = this.currentTab
            if (tabCount != 0) {
                this.currentTab = currentTab //设置title等等
            }
            //tabAdapter.setCurrentTab(currentTab);
        }, false)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
        tabAdapter.setCurrentTab(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    private fun updateTabList() {
        tabAdapter.setTabInfoList(editorAdapter.tabInfoList)
        mainActivity.showEmptyTabsHint(editorAdapter.count == 0)
    }

    fun updateEditorView(index: Int, editorView: EditorView) {
        editorAdapter.setEditorView(index, editorView)
    }

    fun onDocumentChanged() {
        DLog.d("TabManager", "DocumentChanged")
        updateTabList()
        updateToolbar()
    }

    private fun updateToolbar() {
        mainActivity.updateToolbarTitle(editorAdapter.getItem(currentTab))
    }

    fun onDestroy(): Boolean {
        val needSaveFiles = ArrayList<File>()
        val allEditor = editorAdapter.allEditor
        for (editorDelegate in allEditor) {
            val path = editorDelegate!!.path
            val encoding = editorDelegate.encoding
            val offset = editorDelegate.cursorOffset
            if (editorDelegate.isChanged) {
                needSaveFiles.add(editorDelegate.document.file)
            }
            val recentFileItem = RecentFileItem()
            recentFileItem.setPath(path)
            recentFileItem.setEncoding(encoding)
            recentFileItem.setOffset(offset)
            recentFileItem.setLastOpen(true)
            recentFileItem.setTime(System.currentTimeMillis())
            JsonDatabase.getInstance(mainActivity).updateRecentFile(path, encoding, offset)
        }

        if (needSaveFiles.isEmpty()) {
            return true
        } else {
            val fileName = StringBuilder("(")
            for (i in needSaveFiles.indices) {
                val needSaveFile = needSaveFiles[i]
                fileName.append(needSaveFile.name)
                if (i != needSaveFiles.size - 1) {
                    fileName.append(", ")
                }
            }
            fileName.append(")")

            val saveConfirmDialog = SaveConfirmDialog(mainActivity, fileName.toString()) { dialog, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val saveAllTask = SaveAllTask(mainActivity, object : SaveListener {
                        override fun onSavedSuccess() {
                            mainActivity.finish()
                        }

                        override fun onSaveFailed(e: Exception?) {
                            EditorMessages.error(mainActivity, mainActivity.getString(R.string.m3f_save_failed, e?.message.orEmpty()))
                        }
                    })
                    dialog.dismiss()
                    saveAllTask.execute()
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    dialog.cancel()
                    mainActivity.finish()
                } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                    dialog.cancel()
                }
            }
            saveConfirmDialog.show()
            return false
        }
    }

    fun closeAllUnchanged() {
        editorAdapter.removeAll(object : TabCloseListener {
            override fun onClose(path: String?, encoding: String?, offset: Int) {
                editorAdapter.removeAll(this, true)
            }
        }, true)
    }
}
