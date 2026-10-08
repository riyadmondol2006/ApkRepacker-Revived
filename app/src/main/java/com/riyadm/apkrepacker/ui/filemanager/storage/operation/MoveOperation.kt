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
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CopyArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.MoveArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui.OperationStatusDisplayer
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui.OperationStatusDisplayerInjector
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils
import java.io.File
import java.util.Collections

class MoveOperation(context: Context, statusDisplayer: OperationStatusDisplayer) : FileOperation<MoveArguments>() {
    private val context: Context = context.applicationContext
    private val statusDisplayer: OperationStatusDisplayer = statusDisplayer

    override fun operate(args: MoveArguments): Boolean {
        return NormalMover().move(args)
    }

    override fun operateSaf(args: MoveArguments): Boolean {
        return SafMover().move(args)
    }

    override fun onStartOperation(args: MoveArguments) {
    }

    override fun onResult(success: Boolean, args: MoveArguments) {
        if (success) {
            statusDisplayer.showMoveSuccess(id, args.target)
            //  BaseFilesFragment.refresh(context,args.getTarget().getParentFile());
            //  args.getBaseFragment().refresh(context,args.getTarget().getParentFile());
        } else {
            statusDisplayer.showMoveFailure(id, args.target)
        }
    }

    override fun onAccessDenied() {
    }

    override fun onRequestingAccess() {
        // clearNotification(id, context);
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }

    private abstract inner class Mover {
        fun move(args: MoveArguments): Boolean {
            var allSucceeded = true
            var fileIndex = 0

            var from: File
            var toFile: File
            val target = args.target
            val files = args.getFilesToMove()
            for (fh in files) {
                statusDisplayer.showMoveProgress(id, target, fh.file,
                        fileIndex++, files.size)

                from = fh.file.absoluteFile
                toFile = File(target, fh.name)
                val paths = FileUtils.getPathsUnder(from)

                val fileMoved = moveSingle(fh, toFile, args.getBaseFragment())

                if (fileMoved) {
                    MediaScannerUtils.informPathsDeleted(context, paths)
                    if (toFile.isDirectory) {
                        MediaScannerUtils.informFolderAdded(context, toFile)
                    } else {
                        MediaScannerUtils.informFileAdded(context, toFile)
                    }
                }

                allSucceeded = allSucceeded and fileMoved
            }

            return allSucceeded
        }

        protected abstract fun moveSingle(what: FileHolder, futureWhat: File, filesFragment: BaseFilesFragment?): Boolean
    }

    private inner class NormalMover : Mover() {
        override fun moveSingle(what: FileHolder, futureWhat: File, filesFragment: BaseFilesFragment?): Boolean {
            return what.file.renameTo(futureWhat)
        }
    }

    private inner class SafMover : Mover() {
        override fun moveSingle(what: FileHolder, futureWhat: File, filesFragment: BaseFilesFragment?): Boolean {
            val copySucceeded = CopyOperation(context, OperationStatusDisplayerInjector.noOpStatusDisplayer())
                    .operateSaf(CopyArguments.copyArgs(Collections.singletonList(what), futureWhat.parentFile!!, filesFragment))
            // Only delete if full tree was copied. Spare files are bad, disappearing files are worse
            return copySucceeded && DocumentFileUtils.safAwareDelete(context, what.file)
        }
    }
}
