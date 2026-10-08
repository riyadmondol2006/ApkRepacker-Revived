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

import androidx.annotation.WorkerThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ide.editor.task.SaveTask
import com.riyadm.apkrepacker.ide.file.ReadFileListener
import com.riyadm.apkrepacker.ide.file.SaveListener
import com.jecelyin.common.utils.DLog
import com.jecelyin.common.utils.StringUtils
import com.jecelyin.editor.v2.widget.EditorMessages
import com.jecelyin.editor.v2.widget.findActivity
import com.riyadm.apkrepacker.ide.editor.task.editorScope
import com.jecelyin.editor.v2.EditorPreferences
import com.jecelyin.editor.v2.io.FileReader
import com.jecelyin.editor.v2.io.LocalFileWriter

import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class Document internal constructor(
    private val mContext: Context,
    private val mEditorDelegate: EditorDelegate,
    currentFile: File?
) : ReadFileListener {
    private val mEditorPreferences: EditorPreferences = EditorPreferences.getInstance(mContext)
    private var mLineCount = 0
    private var mEncoding: String? = "UTF-8"
    private var mSourceMD5: ByteArray? = null
    private var mSourceLength = 0
    private val mModeName: String? = null
    private var mFile: File? = currentFile


    /** Loads belong to the editor's lifecycle; saves outlive it. */
    private val readScope: CoroutineScope
        get() = (mContext.findActivity() as? LifecycleOwner)?.lifecycleScope ?: editorScope

    internal fun onSaveInstanceState(ss: EditorDelegate.SavedState) {
        ss.modeName = mModeName
        ss.lineNumber = mLineCount
        ss.textMd5 = mSourceMD5
        ss.textLength = mSourceLength
        ss.encoding = mEncoding
        ss.file = mFile
    }

    internal fun onRestoreInstanceState(ss: EditorDelegate.SavedState) {

        if (ss.lineNumber > 0) {
            mLineCount = ss.lineNumber
        }
        mSourceMD5 = ss.textMd5
        mSourceLength = ss.textLength
        mEncoding = ss.encoding
        mFile = ss.file
    }

    internal fun loadFile(file: File, encodingName: String?) {
        if (!file.isFile || !file.exists()) {
            EditorMessages.error(mContext, mContext.getString(R.string.cannt_access_file, file.path))
            return
        }
        if (!file.canRead()) {
            EditorMessages.error(mContext, mContext.getString(R.string.cannt_read_file, file.path))
            return
        }
        mFile = file
        val reader = FileReader(mFile, encodingName)
        readScope.launch {
            onStart()
            val text = withContext(Dispatchers.IO) {
                if (reader.read()) onAsyncReaded(reader, true) else null
            }
            onDone(text, text != null)
        }
    }

    override fun onStart() {
        mEditorDelegate.onLoadStart()
    }

    override fun onAsyncReaded(fileReader: FileReader, ok: Boolean): String? {
        val text = fileReader.buffer!!

        mLineCount = fileReader.lineCount
        mEncoding = fileReader.encoding

        mSourceMD5 = md5(text)
        mSourceLength = text.length

        return text
    }

    override fun onDone(spannableStringBuilder: String?, ok: Boolean) {
        if (mEditorDelegate.editTextOrNull == null)
            return
        if (!ok) {
            mEditorDelegate.onLoadFinish()
            EditorMessages.error(mContext, mContext.getString(R.string.read_file_exception))
            return
        }

        mEditorDelegate.editText.setText(spannableStringBuilder)
        mEditorDelegate.onLoadFinish()
    }


    val modeName: String?
        get() = mModeName

    val file: File
        get() = mFile!!

    /** Like [file], but returns null instead of failing when no file is set. */
    internal val fileOrNull: File?
        get() = mFile

    val path: String
        get() = mFile!!.path

    val lineCount: Int
        get() = mLineCount

    val encoding: String?
        get() = mEncoding

    @WorkerThread
    @Throws(Exception::class)
    fun writeToFile(file: File?, encoding: String?) {
        val writer = LocalFileWriter(file!!, encoding)
        writer.writeToFile(mEditorDelegate.text)

        onSaveSuccess(file, encoding)
    }

    /**
     * Write current content to new file and set new file to edit
     *
     * @param file - file to write
     */
    internal fun saveInBackground(file: File?, encoding: String?, listener: SaveListener?) {
        val saveTask = SaveTask(file, encoding, this, listener)
        saveTask.execute()
    }

    private fun onSaveSuccess(newFile: File, encoding: String?) {
        mFile = newFile
        mEncoding = encoding
        mSourceMD5 = md5(mEditorDelegate.text)
        mSourceLength = mEditorDelegate.text.length
    }

    val isChanged: Boolean
        get() {

            if (mSourceMD5 == null) {
                return mEditorDelegate.text.length != 0
            }
            if (mSourceLength != mEditorDelegate.text.length) {
                return true
            }

            val curMD5 = md5(mEditorDelegate.text)

            return !StringUtils.isEqual(mSourceMD5, curMD5)
        }

    val md5: ByteArray?
        get() = mSourceMD5

    companion object {
        /**
         * Returns the md5sum for given string. Or dummy byte array on error
         * Suppress NoSuchAlgorithmException because MD5 algorithm always present in JRE
         *
         * @param charSequence Given string
         * @return md5 sum of given string
         */
        private fun md5(charSequence: CharSequence): ByteArray {
            try {
                val digest = MessageDigest.getInstance("MD5")
                val ba = ByteArray(2)
                var i = 0
                val n = charSequence.length
                while (i < n) {
                    val cp = charSequence[i].code
                    ba[0] = (cp and 0xff).toByte()
                    ba[1] = ((cp shr 8) and 0xff).toByte()
                    digest.update(ba)
                    i++
                }
                return digest.digest()
            } catch (e: NoSuchAlgorithmException) {
                DLog.e("Can't Calculate MD5 hash!", e)
                return charSequence.toString().toByteArray(Charset.defaultCharset())
            }
        }
    }
}
