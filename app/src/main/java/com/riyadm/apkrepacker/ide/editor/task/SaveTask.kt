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

package com.riyadm.apkrepacker.ide.editor.task

import com.riyadm.apkrepacker.ide.editor.Document
import com.riyadm.apkrepacker.ide.file.SaveListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Scope for work that must finish even when the editor screen goes away, such as saving. */
internal val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Writes a document to disk off the main thread and reports back on it. */
class SaveTask(
    private val mFile: File?,
    private val mEncoding: String?,
    private val mDocument: Document,
    private val mListener: SaveListener?
) {
    fun execute() {
        editorScope.launch {
            val failure = withContext(Dispatchers.IO) {
                try {
                    mDocument.writeToFile(mFile, mEncoding)
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    e
                }
            }
            if (failure == null) mListener?.onSavedSuccess() else mListener?.onSaveFailed(failure)
        }
    }
}
