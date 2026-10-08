/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riyadm.apkrepacker.ide.editor

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.view.View

import androidx.annotation.MainThread
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.jecelyin.editor.v2.common.OnVisibilityChangedListener
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.ide.editor.lexer.CssLexTask
import com.riyadm.apkrepacker.utils.common.ShareUtil

import com.riyadm.apkrepacker.ide.editor.lexer.CppLexTask
import com.riyadm.apkrepacker.ide.editor.lexer.HtmlLexTask
import com.riyadm.apkrepacker.ide.editor.lexer.JavaLexTask
import com.riyadm.apkrepacker.ide.editor.lexer.JsonLexTask
import com.riyadm.apkrepacker.ide.editor.lexer.LexerUtil
import com.riyadm.apkrepacker.ide.editor.lexer.SmaliLexTask
import com.riyadm.apkrepacker.ide.editor.lexer.XmlLexTask
import com.riyadm.apkrepacker.ide.editor.text.InputMethodManagerCompat
import com.riyadm.apkrepacker.ide.editor.view.HighlightEditorView
import com.riyadm.apkrepacker.ide.editor.view.IEditAreaView
import com.riyadm.apkrepacker.ide.file.SaveListener
import com.jecelyin.common.utils.DLog
import com.jecelyin.editor.v2.widget.EditorMessages
import com.jecelyin.editor.v2.EditorPreferences
import com.jecelyin.editor.v2.common.Command
import com.jecelyin.editor.v2.dialog.DocumentInfoDialog
import com.jecelyin.editor.v2.widget.menu.MenuDef
import com.riyadm.apkrepacker.view.EditorView
import com.riyadm.codeeditor.util.NonProgLexTask

import java.io.File
import java.util.Locale

class EditorDelegate : IEditorDelegate, HighlightEditorView.OnEditStateChangedListener, OnVisibilityChangedListener {
    private var mContext: Context? = null
    private var mDocument: Document? = null
    private lateinit var savedState: SavedState
    private var mOrientation = 0
    private var loaded = true
    private var mEditText: EditorView? = null

    constructor(ss: SavedState) {
        savedState = ss
    }

    constructor(index: Int, file: File?, offset: Int, encoding: String?) {
        savedState = SavedState()
        savedState.index = index
        savedState.file = file
        savedState.cursorOffset = offset
        savedState.encoding = encoding
        if (savedState.file != null) {
            savedState.title = savedState.file!!.name
        }
    }

    constructor(index: Int, title: String?, `object`: Parcelable?) {
        savedState = SavedState()
        savedState.index = index
        savedState.title = title
        savedState.editorState = `object`
    }

    constructor(index: Int, title: String?, content: CharSequence?) {
        savedState = SavedState()
        savedState.index = index
        savedState.title = title
        //    savedState.t = content;
    }

    constructor(file: File?, offset: Int, encoding: String?) {
        savedState = SavedState()
        savedState.encoding = encoding
        savedState.cursorOffset = offset
        setCurrentFileToEdit(file)
    }


    fun setRemoved() {
        if (mEditText == null)
            return
        mEditText!!.setRemoved()
    }

    private fun setCurrentFileToEdit(file: File?) {
        if (file != null) {
            savedState.file = file
            savedState.title = savedState.file!!.name
        }
    }

    internal fun onLoadStart() {
        loaded = false
        assert(mEditText != null)
        editText.setEnabled(false)
    }

    internal fun onLoadFinish() {
        assert(mEditText != null)
        editText.setEnabled(true)
        editText.setOnEditStateChangedListener(this)
        editText.post {
            editText.setLexTask(LexerUtil.createLexer(mDocument!!.file.name, mDocument!!.file))
            if (savedState.cursorOffset < editText.text.length && savedState.cursorOffset != -1) {
                editText.gotoLine(savedState.cursorOffset)
            }
        }

        onDocumentChanged()
        loaded = true

        val fileName = mDocument!!.file.path.replace(Regex("[^A-Za-z0-9_]"), "_")
        val historyData = mContext!!.getSharedPreferences(
            fileName, Context.MODE_PRIVATE
        )
        //getEditText().restoreEditHistory(historyData);
    }

    val context: Context?
        get() = mContext

    private val activity: TextEditorActivity
        get() = mContext as TextEditorActivity

    val title: String?
        get() = savedState.title

    override val path: String?
        get() = if (mDocument == null) (if (savedState.file == null) null else savedState.file!!.path) else mDocument!!.path

    override val encoding: String?
        get() = if (mDocument == null) null else mDocument!!.encoding

    val text: String
        get() = editText.text.toString()

    override val editText: IEditAreaView
        get() = mEditText!!.getEditText()!!

    /**
     * Same as [editText] but returns null (instead of failing) when the editor view has no
     * edit text yet.
     */
    internal val editTextOrNull: IEditAreaView?
        get() = mEditText!!.getEditText()

    fun onCreate(editorView: EditorView) {
        if (mDocument != null)
            return

        mContext = editorView.context
        mEditText = editorView

        mOrientation = mContext!!.resources.configuration.orientation

        mEditText!!.setVisibilityChangedListener(this)
        mDocument = Document(mContext!!, this, savedState.file)
        editText.setReadOnly(EditorPreferences.getInstance(mContext!!).isReadOnly)
        //getEditText().setCustomSelectionActionModeCallback(new EditorSelectionActionModeCallback());

        if (savedState.editorState != null)
            try {
                mDocument!!.onRestoreInstanceState(savedState)
                editText.onRestoreInstanceState(savedState.editorState)
            } catch (e: Exception) {
                //wrong state
                e.printStackTrace()
            }
        else if (savedState.file != null) {
            mDocument!!.loadFile(savedState.file!!, savedState.encoding)
        }

        onDocumentChanged()
    }

    fun onDestroy() {
/*
        String fileName = mDocument.getFile().getPath().replaceAll("[^A-Za-z0-9_]", "_");
        SharedPreferences historyData = mContext.getSharedPreferences(
                fileName, Context.MODE_PRIVATE);
        getEditText().saveHistory(historyData);

 */
        if (isChanged && EditorPreferences.getInstance(context!!).isAutoSave) {
            saveInBackground()
        }

    }

    override val isChanged: Boolean
        get() = mEditText != null && editText.isChanged

    val toolbarText: CharSequence
        get() {
            try {
                val encode = if (mDocument == null) "UTF-8" else mDocument!!.encoding
                val fileMode = if (mDocument == null || mDocument!!.modeName == null)
                    ""
                else
                    mDocument!!.modeName
                val title = this.title
                val changed = if (isChanged) "*" else ""
                val cursor = ""
                if (mEditText != null && cursorOffset >= 0) {
                    val cursorOffset = this.cursorOffset
                    //   int line = mDocument.getBuffer().getLineManager().getLineOfOffset(cursorOffset);
                    //   cursor += line + ":" + cursorOffset;
                }
                return String.format(
                    Locale.US, "%s%s  \t|\t  %s \t %s \t %s",
                    changed, title, encode, fileMode, cursor
                )
            } catch (e: Exception) {
                return ""
            }
        }
/*
    private void startSaveFileSelectorActivity() {
        if (mDocument != null) {
           // getActivity().startPickPathActivity(mDocument.getPath(), mDocument.getEncoding());
        }
    }


 */

    /**
     * Write out content of editor to file in background thread
     *
     * @param file     - File to write
     * @param encoding - file encoding
     */
    fun saveInBackground(file: File?, encoding: String?) {
        saveInBackground(file, encoding, notify = false)
    }

    private fun saveInBackground(file: File?, encoding: String?, notify: Boolean) {
        val context = mContext
        mDocument?.saveInBackground(file, encoding ?: mDocument!!.encoding,
            object : SaveListener {
                override fun onSavedSuccess() {
                    onDocumentChanged()
                    if (notify && context != null) EditorMessages.show(context, R.string.m3f_saved)
                }

                override fun onSaveFailed(e: Exception?) {
                    if (context != null) {
                        EditorMessages.error(context, context.getString(R.string.m3f_save_failed, e?.message.orEmpty()))
                    }
                }
            })
    }

    /**
     * Write current content of editor to file
     */
    @Throws(Exception::class)
    override fun saveCurrentFile() {
        if (mDocument!!.isChanged) {
            mDocument!!.writeToFile(mDocument!!.fileOrNull, mDocument!!.encoding)
        }
    }

    /**
     * Write out content of editor to file in background thread
     */
    override fun saveInBackground() {
        if (mDocument!!.isChanged) {
            saveInBackground(mDocument!!.fileOrNull, mDocument!!.encoding)
        } else {
            if (DLog.DEBUG) DLog.d(TAG, "saveInBackground: document not changed, no need to save")
        }
    }

    override val cursorOffset: Int
        get() {
            if (mEditText == null) {
                return -1
            }
            return editText.getSelectionEnd()
        }

    override fun doCommand(command: Command?): Boolean {
        if (mEditText == null)
            return false
        val readonly = EditorPreferences.getInstance(mContext!!).isReadOnly
        when (command!!.what!!) {
            Command.CommandEnum.HIDE_SOFT_INPUT -> InputMethodManagerCompat.hideSoftInput(editText as View)
            Command.CommandEnum.SHOW_SOFT_INPUT -> InputMethodManagerCompat.showSoftInput(editText as View)
            Command.CommandEnum.UNDO -> if (!readonly) {
                editText.undo()
            }
            Command.CommandEnum.REDO -> if (!readonly) {
                editText.redo()
            }
            Command.CommandEnum.CUT -> {
                if (!readonly) {
                    editText.doCut()
                    return readonly
                }
                // falls through to COPY
                editText.doCopy()
                return readonly
            }
            Command.CommandEnum.COPY -> {
                editText.doCopy()
                return readonly
            }
            Command.CommandEnum.PASTE -> {
                if (!readonly) {
                    editText.doPaste()
                    return readonly
                }
                // falls through to SELECT_ALL
                editText.selectAll()
                return readonly
            }
            Command.CommandEnum.SELECT_ALL -> {
                editText.selectAll()
                return readonly
            }
            Command.CommandEnum.DUPLICATION -> {
                if (readonly) {
                    // the original "if (!readonly) break;" falls through to GOTO_INDEX when read only
                    //  getEditText().duplicateSelection();
                    val col = command.args.getInt("col", -1)
                    val line = command.args.getInt("line", -1)
                    editText.gotoLine(line)
                }
            }
            Command.CommandEnum.GOTO_INDEX -> {
                val col = command.args.getInt("col", -1)
                val line = command.args.getInt("line", -1)
                editText.gotoLine(line)
            }
            Command.CommandEnum.GOTO_TOP -> editText.gotoTop()
            Command.CommandEnum.GOTO_END -> editText.gotoEnd()
            Command.CommandEnum.DOC_INFO -> {
                val documentInfoDialog = DocumentInfoDialog(mContext!!)
                documentInfoDialog.setDocument(mDocument)
                documentInfoDialog.setEditAreaView(editText)
                documentInfoDialog.setPath(mDocument!!.path)
                documentInfoDialog.show()
            }
            Command.CommandEnum.READONLY_MODE -> {
                val editorPreferences = EditorPreferences.getInstance(mContext!!)
                val readOnly = editorPreferences.isReadOnly
                editText.setReadOnly(readOnly)
            }
            Command.CommandEnum.SAVE -> if (!readonly && mDocument?.isChanged == true) {
                saveInBackground(mDocument!!.fileOrNull, mDocument!!.encoding, notify = true)
            }
            Command.CommandEnum.SAVE_AS -> {
                //startSaveFileSelectorActivity();
            }
            Command.CommandEnum.FIND -> {
                activity.searchPanel.initSearchPanel(this)
            }
            Command.CommandEnum.HIGHLIGHT -> {
                val scope = command.`object` as String?
                setMode(scope)
            }
            Command.CommandEnum.INSERT_TEXT -> if (!readonly) {
                editText.insert(command.`object` as CharSequence)
            }
            Command.CommandEnum.RELOAD_WITH_ENCODING -> reOpenWithEncoding(command.`object` as String?)
            Command.CommandEnum.REQUEST_FOCUS -> editText.requestFocus()
            Command.CommandEnum.SHARE_CODE -> shareCurrentContent()
            Command.CommandEnum.FORMAT_SOURCE -> {
                //formatSource();

            }
            Command.CommandEnum.REFRESH_THEME -> if (mEditText != null) {
                editText.setTheme(EditorPreferences.getInstance(mContext!!).editorTheme)
            }
            else -> {
            }
        }
        return true
    }

    private fun shareCurrentContent() {
        ShareUtil.shareText(mContext!!, editText.text.toString())
    }

    private fun reOpenWithEncoding(encoding: String?) {
        val file = mDocument!!.fileOrNull
        if (mDocument!!.isChanged) {
            MaterialAlertDialogBuilder(mContext!!)
                .setTitle(R.string.document_changed)
                .setMessage(R.string.give_up_document_changed_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ -> mDocument!!.loadFile(file!!, encoding) }
                .show()
            return
        }
        mDocument!!.loadFile(file!!, encoding)
    }

    /**
     * This method will be called when document changed file
     */
    @MainThread
    override fun onDocumentChanged() {
        DLog.d("EditorDelegate", "onDocumentChanged called")
        setCurrentFileToEdit(mDocument!!.fileOrNull)
        noticeMenuChanged()
    }


    private fun noticeMenuChanged() {
        //MainActivity mainActivity = (MainActivity) this.context;
        activity.setMenuStatus(R.id.action_save, if (isChanged) MenuDef.STATUS_NORMAL else MenuDef.STATUS_DISABLED)
        activity.setMenuStatus(R.id.action_undo, if (mEditText != null && editText.doCanUndo()) MenuDef.STATUS_NORMAL else MenuDef.STATUS_DISABLED)
        activity.setMenuStatus(R.id.action_redo, if (mEditText != null && editText.doCanRedo()) MenuDef.STATUS_NORMAL else MenuDef.STATUS_DISABLED)
        activity.tabManager!!.onDocumentChanged()
    }

    internal fun setMode(name: String?) {
        when (name) {
            // null: highlighting was switched back on, pick the lexer from the file again
            null -> editText.setLexTask(LexerUtil.createLexer(mDocument!!.file.name, mDocument!!.file))
            "C++" -> editText.setLexTask(CppLexTask())
            "Java" -> editText.setLexTask(JavaLexTask())
            "Smali" -> editText.setLexTask(SmaliLexTask())
            "Html" -> editText.setLexTask(HtmlLexTask())
            "Json" -> editText.setLexTask(JsonLexTask())
            "Xml" -> editText.setLexTask(XmlLexTask())
            "Css" -> editText.setLexTask(CssLexTask())
            "None" -> editText.setLexTask(NonProgLexTask.instance)
            else -> editText.setLexTask(NonProgLexTask.instance)
        }

    }
    fun onSaveInstanceState(): Parcelable {
        if (mDocument != null) {
            mDocument!!.onSaveInstanceState(savedState)
        }
        if (mEditText != null) {
            editText.setFreezesText(true)
        }

        if (!disableAutoSave && loaded && mDocument != null) {
            if (EditorPreferences.getInstance(mContext!!).isAutoSave) {
                val newOrientation = mContext!!.resources.configuration.orientation
                if (mOrientation != newOrientation) {
                    DLog.d("current is screen orientation, discard auto save!")
                    mOrientation = newOrientation
                } else {
                    try {
                        saveCurrentFile()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return savedState
    }

    override val document: Document
        get() = mDocument!!

    override fun onEditStateChanged() {
        onDocumentChanged()
    }

    override fun onVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE)
            return

        noticeMenuChanged()
    }


    class SavedState : Parcelable {
        @JvmField
        var cursorOffset = 0
        @JvmField
        var lineNumber = 0
        @JvmField
        var index = 0
        @JvmField
        var file: File? = null
        @JvmField
        var title: String? = null
        @JvmField
        var encoding: String? = null
        @JvmField
        var modeName: String? = null
        @JvmField
        var editorState: Parcelable? = null
        @JvmField
        var textMd5: ByteArray? = null
        @JvmField
        var textLength = 0

        internal constructor()

        @Suppress("DEPRECATION")
        internal constructor(`in`: Parcel) {
            this.index = `in`.readInt()
            this.cursorOffset = `in`.readInt()
            this.lineNumber = `in`.readInt()
            val file = `in`.readString()
            this.file = File(file!!)
            this.title = `in`.readString()
            this.encoding = `in`.readString()
            this.modeName = `in`.readString()
            val hasState = `in`.readInt()
            if (hasState == 1) {
                this.editorState = `in`.readParcelable(Parcelable::class.java.classLoader)
            }
            this.textMd5 = `in`.createByteArray()
            this.textLength = `in`.readInt()
        }


        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(this.index)
            dest.writeInt(this.cursorOffset)
            dest.writeInt(this.lineNumber)
            dest.writeString(this.file!!.path)
            dest.writeString(this.title)
            dest.writeString(this.encoding)
            dest.writeString(this.modeName)
            dest.writeInt(if (this.editorState == null) 0 else 1)
            if (this.editorState != null) {
                dest.writeParcelable(this.editorState, flags)
            }
            dest.writeByteArray(this.textMd5)
            dest.writeInt(textLength)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        const val KEY_CLUSTER = "is_cluster"
        private const val TAG = "EditorDelegate"
        private var disableAutoSave = false

        @JvmStatic
        fun setDisableAutoSave(b: Boolean) {
            disableAutoSave = b
        }
    }
}
