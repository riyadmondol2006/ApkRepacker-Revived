/*
 * Copyright (C) 2018 George Venios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.riyadm.apkrepacker.ui.filemanager.storage.operation

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Handler
import android.os.Looper
import com.riyadm.apkrepacker.ui.filemanager.FileFeedback
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.DeleteArguments
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils


class DeleteOperation(context: Context) : FileOperation<DeleteArguments>() {
    private val context: Context = context.applicationContext
    private val mainThreadHandler = Handler(context.mainLooper)
    private val uiContext = context
    private var dialog: AlertDialog? = null

    override fun operate(args: DeleteArguments): Boolean {
        var allSucceeded = true

        for (fh in args.getVictims()) {
            val tbd = fh.file
            val paths = FileUtils.getPathsUnder(tbd)

            val deleted = FileUtils.delete(tbd)
            allSucceeded = allSucceeded and deleted

            if (deleted) MediaScannerUtils.informPathsDeleted(context, paths)
        }
        return allSucceeded
    }

    override fun operateSaf(args: DeleteArguments): Boolean {
        var allSucceeded = true

        for (fh in args.getVictims()) {
            val tbd = DocumentFileUtils.findFile(context, fh.file)
            val paths = FileUtils.getPathsUnder(fh.file)

            val deleted = tbd != null && tbd.delete()
            allSucceeded = allSucceeded and deleted

            if (deleted) MediaScannerUtils.informPathsDeleted(context, paths)
        }
        return allSucceeded
    }

    override fun onStartOperation(args: DeleteArguments) {
        runOnUi {
            val body = LayoutInflater.from(uiContext).inflate(R.layout.dialog_files_progress, null)
            body.findViewById<TextView>(R.id.files_progress_message).setText(R.string.deleting)
            dialog = MaterialAlertDialogBuilder(uiContext)
                .setView(body)
                .setCancelable(false)
                .show()
        }
    }

    override fun onResult(success: Boolean, args: DeleteArguments) {
        runOnUi {
            if (success) {
                FileFeedback.show(context.getString(R.string.toast_files_deleted, args.getVictims().size))
                args.clear()
                args.getBaseFragment()?.refresh(context, args.target)
            } else {
                FileFeedback.show(context, R.string.toast_error_on_delete_file)
            }

            dialog?.dismiss()
        }
    }

    override fun onAccessDenied() {
        runOnUi { dialog?.dismiss() }
    }

    override fun onRequestingAccess() {
        runOnUi { dialog?.cancel() }
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }

    private fun runOnUi(runnable: Runnable) {
        if (Looper.myLooper() !== context.mainLooper) {
            mainThreadHandler.post(runnable)
        } else {
            runnable.run()
        }
    }
}
