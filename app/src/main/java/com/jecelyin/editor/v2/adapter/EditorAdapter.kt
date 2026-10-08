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
package com.jecelyin.editor.v2.adapter

import android.content.Context
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.jecelyin.editor.v2.common.ClusterCommand
import com.jecelyin.editor.v2.common.TabCloseListener
import com.jecelyin.editor.v2.common.TabInfo
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ide.editor.EditorDelegate
import com.riyadm.apkrepacker.ide.editor.IEditorDelegate
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.view.EditorView
import java.io.File

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class EditorAdapter(private val context: Context) : PagerAdapter() {
    private val list = ArrayList<EditorDelegate>(20)
    private var currentPosition = 0

    fun getView(position: Int, pager: ViewGroup?): View {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_code_editor, pager, false) as EditorView
        setEditorView(position, view)
        return view
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = getView(position, container)
        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        DLog.d("Editor Adapter", String.format("destroy view called, %d", position))
        container.removeView(`object` as View)
    }

    override fun getCount(): Int {
        return list.size
    }

    /**
     * @param file 一个路径或标题
     */
    fun newEditor(file: File?, offset: Int, encoding: String?) {
        newEditor(true, file, offset, encoding)
    }

    fun newEditor(notify: Boolean, file: File?, offset: Int, encoding: String?) {
        list.add(EditorDelegate(list.size, file, offset, encoding))
        if (notify)
            notifyDataSetChanged()
    }

    fun newEditor(title: String?, content: CharSequence?) {
        list.add(EditorDelegate(list.size, title, content))
        notifyDataSetChanged()
    }

    /**
     * 当View被创建或是内存不足重建时，如果不更新list的内容，就会链接到旧的View
     *
     * @param index
     * @param editorView
     */
    fun setEditorView(index: Int, editorView: EditorView) {
        if (index >= count) {
            return
        }
        val delegate: EditorDelegate? = list[index]
        delegate?.onCreate(editorView)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        super.setPrimaryItem(container, position, `object`)
        currentPosition = position
        setEditorView(position, `object` as EditorView)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    val currentEditorDelegate: EditorDelegate?
        get() {
            if (list.isEmpty() || currentPosition >= list.size)
                return null
            return list[currentPosition]
        }

    fun countNoFileEditor(): Int {
        var count = 0
        for (f in list) {
            if (f.path == null) {
                count++
            }
        }
        return count
    }

    val tabInfoList: Array<TabInfo?>
        get() {
            val size = list.size
            val arr = arrayOfNulls<TabInfo>(size)
            var f: EditorDelegate
            for (i in 0 until size) {
                f = list[i]
                arr[i] = TabInfo(f.title, f.path, f.isChanged)
            }

            return arr
        }

    val allEditor: ArrayList<IEditorDelegate?>
        get() {
            val delegates = ArrayList<IEditorDelegate?>()
            for (i in 0 until count) {
                delegates.add(getItem(i))
            }
            return delegates
        }

    fun removeEditor(position: Int, listener: TabCloseListener?, closeUnchanged: Boolean): Boolean {
        val delegate: EditorDelegate? = list[position]
        if (delegate == null) {
            //not init
            return false
        }
        val encoding = delegate.encoding
        val offset = delegate.cursorOffset
        val path = delegate.path

        if (closeUnchanged) {
            if (delegate.isChanged)
                return true
        }
        remove(position)
        listener?.onClose(path, encoding, offset)
        return true
    }

    fun remove(position: Int) {
        val delegate = list.removeAt(position)
        delegate.setRemoved()
        notifyDataSetChanged()
    }

    override fun getItemPosition(`object`: Any): Int {
        return if ((`object` as EditorView).isRemoved()) POSITION_NONE else POSITION_UNCHANGED
    }

    fun makeClusterCommand(): ClusterCommand {
        return ClusterCommand(ArrayList(list))
    }

    fun removeAll(tabCloseListener: TabCloseListener?, closeUnchanged: Boolean) {
        val position = list.size - 1
        if (position >= 0) {
            removeEditor(position, tabCloseListener, closeUnchanged)
        }
    }

    fun getItem(i: Int): EditorDelegate? {
        //TabManager调用时，可能程序已经退出，updateToolbar时就不需要做处理了
        if (i >= list.size)
            return null
        return list[i]
    }

    override fun saveState(): Parcelable {
        val ss = SavedState()
        val states = arrayOfNulls<EditorDelegate.SavedState>(list.size)
        for (i in list.size - 1 downTo 0) {
            states[i] = list[i].onSaveInstanceState() as EditorDelegate.SavedState?
        }
        ss.states = states
        return ss
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state !is SavedState)
            return
        val ss = state.states
        list.clear()
        for (s in ss!!) {
            list.add(EditorDelegate(s!!))
        }
        notifyDataSetChanged()
    }
}
