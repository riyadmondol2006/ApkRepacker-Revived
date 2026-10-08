/*
 * Copyright (C) 2012 OpenIntents.org
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

package com.riyadm.apkrepacker.ui.filemanager.utils

import android.content.Context
import androidx.annotation.IntDef
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.service.CopyService
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import java.io.File

/**
 * This class helps simplify copying and moving of files and folders by providing
 * a simple interface and handling the actual operation transparently.
 */
class CopyHelper {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(COPY, CUT)
    private annotation class Operation

    private var mClipboard: MutableList<FileHolder>? = null

    @Operation
    private var mOperation: Int = 0
    private var mFilesFragment: BaseFilesFragment? = null

    fun getItemCount(): Int {
        return if (canPaste()) {
            mClipboard!!.size
        } else {
            0
        }
    }

    fun copy(tbc: MutableList<FileHolder>?) {
        mOperation = COPY
        mClipboard = tbc
    }

    fun copy(tbc: FileHolder) {
        val tbcl = ArrayList<FileHolder>()
        tbcl.add(tbc)
        copy(tbcl)
    }

    fun cut(tbc: MutableList<FileHolder>?) {
        mOperation = CUT
        mClipboard = tbc
    }

    fun cut(tbc: FileHolder) {
        val tbcl = ArrayList<FileHolder>()
        tbcl.add(tbc)
        cut(tbcl)
    }

    fun clear() {
        mClipboard!!.clear()
    }

    /**
     * Call this to check whether there are file references on the clipboard.
     */
    fun canPaste(): Boolean {
        return mClipboard != null && !mClipboard!!.isEmpty()
    }

    @Operation
    fun getOperationType(): Int {
        return mOperation
    }

    fun setFilesFragment(filesFragment: BaseFilesFragment?) {
        mFilesFragment = filesFragment
    }

    /**
     * Paste the copied/cut items.
     * @param copyTo Path to paste to.
     */
    fun paste(c: Context, copyTo: File) {
        // Quick check just to make sure. Normally this should never be the case as the path we get is not user-generated.
        if (!copyTo.isDirectory)
            return

        when (mOperation) {
            COPY -> {
                CopyService.setFilesFragment(mFilesFragment)
                CopyService.copyTo(c, mClipboard, copyTo)
                mClipboard!!.clear()
            }
            CUT -> {
                CopyService.setFilesFragment(mFilesFragment)
                CopyService.moveTo(c, mClipboard, copyTo)
                mClipboard!!.clear()
            }
            else -> {
            }
        }
    }

    companion object {
        const val COPY = 0

        @Suppress("WeakerAccess")
        const val CUT = 1
    }
}
