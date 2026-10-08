/*
 * Copyright 2018 Mr Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jecelyin.editor.v2.manager

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.database.ITabDatabase
import com.riyadm.apkrepacker.database.JsonDatabase
import com.riyadm.apkrepacker.database.entity.RecentFileItem

/**
 * Dialog listing the recently opened files.
 *
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
class RecentFilesManager(context: Context) {
    private val dbHelper: ITabDatabase = JsonDatabase.getInstance(context.applicationContext)
    private var onFileItemClickListener: OnFileItemClickListener? = null

    fun show(context: Context) {
        val files: List<RecentFileItem> = dbHelper.getRecentFiles(true).toList()
        val items = files.map { it.path.orEmpty() }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.recent_files)
            .setItems(items) { _, which ->
                val item = files[which]
                onFileItemClickListener?.onClick(item.path, item.encoding)
            }
            .setNeutralButton(R.string.clear_history) { dialog, _ ->
                dbHelper.clearRecentFiles()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    fun setOnFileItemClickListener(onFileItemClickListener: OnFileItemClickListener?) {
        this.onFileItemClickListener = onFileItemClickListener
    }

    fun interface OnFileItemClickListener {
        fun onClick(file: String?, encoding: String?)
    }
}
