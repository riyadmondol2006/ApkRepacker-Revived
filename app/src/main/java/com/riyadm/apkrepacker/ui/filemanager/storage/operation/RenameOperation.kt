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
import com.riyadm.apkrepacker.ui.filemanager.FileFeedback
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.RenameArguments
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils


class RenameOperation(private val context: Context) : FileOperation<RenameArguments>() {
    private val affectedPaths: MutableList<String> = ArrayList()

    override fun operate(args: RenameArguments): Boolean {
        val from = args.getFileToRename()
        val dest = args.target

        return dest.exists() || from.renameTo(dest)
    }

    override fun operateSaf(args: RenameArguments): Boolean {
        val from = args.getFileToRename()
        val dest = args.target

        if (dest.exists()) {
            return true
        } else {
            val safFrom = DocumentFileUtils.findFile(context, from)
            return safFrom != null && safFrom.renameTo(args.target.name)
        }
    }

    override fun onStartOperation(args: RenameArguments) {
        val from = args.getFileToRename()
        if (from.isDirectory) {
            MediaScannerUtils.getPathsOfFolder(affectedPaths, from)
        } else {
            affectedPaths.add(from.absolutePath)
        }
    }

    override fun onResult(success: Boolean, args: RenameArguments) {
        if (success) {
            val dest = args.target
            // BaseFilesFragment.refresh(context,args.getTarget().getParentFile());
            args.getBaseFragment()?.refresh(context, args.target.parentFile)
            MediaScannerUtils.informPathsDeleted(context, affectedPaths)
            if (dest.isFile) {
                MediaScannerUtils.informFileAdded(context, dest)
            } else {
                MediaScannerUtils.informFolderAdded(context, dest)
            }
        } else {
            FileFeedback.show(context, R.string.toast_error_on_rename)
        }
    }

    override fun onAccessDenied() {
    }

    override fun onRequestingAccess() {
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }
}
