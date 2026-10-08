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

import com.riyadm.apkrepacker.activity.TextEditorActivity
import com.riyadm.apkrepacker.ide.file.SaveListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saves every open document, then reports the first failure (if any) on the main thread. */
class SaveAllTask(
    private val editorActivity: TextEditorActivity,
    private val saveListener: SaveListener?
) {
    fun execute() {
        val editors = editorActivity.tabManager?.editorAdapter?.allEditor.orEmpty()
        editorScope.launch {
            val failure = withContext(Dispatchers.IO) {
                var first: Exception? = null
                for (editor in editors) {
                    try {
                        editor?.saveCurrentFile()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (first == null) first = e
                    }
                }
                first
            }
            editors.forEach { it?.onDocumentChanged() }
            if (failure == null) saveListener?.onSavedSuccess() else saveListener?.onSaveFailed(failure)
        }
    }
}
