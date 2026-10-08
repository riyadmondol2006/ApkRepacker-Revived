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
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CopyArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui.OperationStatusDisplayer
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils
import com.riyadm.codeeditor.util.DLog
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


class CopyOperation(context: Context, statusDisplayer: OperationStatusDisplayer) : FileOperation<CopyArguments>() {
    private val context: Context = context.applicationContext
    private val statusDisplayer: OperationStatusDisplayer = statusDisplayer

    override fun operate(args: CopyArguments): Boolean {
        return NormalCopier(context, statusDisplayer, id).copy(args)
    }

    override fun operateSaf(args: CopyArguments): Boolean {
        return SafCopier(context, statusDisplayer, id).copy(args)
    }

    override fun onStartOperation(args: CopyArguments) {
    }

    override fun onResult(success: Boolean, args: CopyArguments) {
        if (success) {
            statusDisplayer.showCopySuccess(id, args.target)
            // BaseFilesFragment.refresh(context,target.getTarget());
            //target.getBaseFragment().refresh(context, target.getTarget());
        } else {
            statusDisplayer.showCopyFailure(id, args.target)
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

    private abstract inner class Copier(
            private val context: Context,
            private val statusDisplayer: OperationStatusDisplayer,
            private val operationId: Int
    ) {

        fun copy(args: CopyArguments): Boolean {
            val files = args.getFilesToCopy()
            val destDirectory = args.target

            val fileCount = FileUtils.countFilesUnder(files)
            var filesCopied = 0

            for (origin in files) {
                val dest = FileUtils.createUniqueCopyName(context, destDirectory, origin.name)
                if (dest != null) {
                    filesCopied = copyFileOrDirectory(
                            filesCopied, fileCount, origin.file, dest)

                    if (origin.file.isDirectory) {
                        MediaScannerUtils.informFolderAdded(context, dest)
                    } else {
                        MediaScannerUtils.informFileAdded(context, dest)
                    }
                }
            }

            return filesCopied == fileCount
        }

        /**
         * Recursively copy a folder.
         *
         * @param filesCopied Initial value of how many files have been copied.
         * @param oldFile     Folder to copy.
         * @param newFile     The dir to be created.
         * @return The new filesCopied count.
         */
        private fun copyFileOrDirectory(filesCopied: Int, fileCount: Int, oldFile: File, newFile: File): Int {
            var filesCopied = filesCopied
            if (oldFile.isDirectory) {
                filesCopied = copyDirectory(filesCopied, fileCount, oldFile, newFile)
            } else {
                filesCopied = copyFile(filesCopied, fileCount, oldFile, newFile)
            }

            return filesCopied
        }

        /**
         * Copy a file.
         *
         * @param filesCopied Initial value of how many files have been copied.
         * @param oldFile     File to copy.
         * @param newFile     The file to be created.
         * @return The new filesCopied count.
         */
        private fun copyFile(filesCopied: Int, fileCount: Int, oldFile: File, newFile: File): Int {
            statusDisplayer.showCopyProgress(operationId, newFile.parentFile!!, oldFile,
                    filesCopied, fileCount)

            try {
                FileInputStream(oldFile).use { input ->
                    outputStream(newFile).use { output ->
                        var len: Int
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (input.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }
            } catch (e: IOException) {
                com.riyadm.apkrepacker.utils.common.DLog.e(e)
                return filesCopied
            }
            return filesCopied + 1
        }

        private fun copyDirectory(filesCopied: Int, fileCount: Int, oldFile: File,
                                  newFile: File): Int {
            var filesCopied = filesCopied
            if (!newFile.exists()) mkDir(newFile)

            // list all the directory contents
            val files = oldFile.list()

            for (file in files!!) {
                // construct the src and dest file structure
                val srcFile = File(oldFile, file)
                val destFile = File(newFile, file)
                // recursive copy
                filesCopied = copyFileOrDirectory(filesCopied, fileCount, srcFile, destFile)
            }
            return filesCopied
        }

        @Throws(FileNotFoundException::class)
        protected abstract fun outputStream(newFile: File): OutputStream

        protected abstract fun mkDir(newFile: File): Boolean
    }

    private inner class NormalCopier(context: Context,
                                     statusDisplayer: OperationStatusDisplayer,
                                     operationId: Int) : Copier(context, statusDisplayer, operationId) {

        @Throws(FileNotFoundException::class)
        override fun outputStream(newFile: File): OutputStream {
            return FileOutputStream(newFile)
        }

        override fun mkDir(newFile: File): Boolean {
            return newFile.mkdir()
        }
    }

    private inner class SafCopier(context: Context,
                                  statusDisplayer: OperationStatusDisplayer,
                                  operationId: Int) : Copier(context, statusDisplayer, operationId) {
        private val context: Context = context

        @Throws(FileNotFoundException::class)
        override fun outputStream(newFile: File): OutputStream {
            var fileCreated = false
            try {
                // If target is accessible without SAF (in case of cross-media moves)
                fileCreated = newFile.createNewFile()
            } catch (e: IOException) {
                DLog.log(e)
            }

            if (fileCreated) {
                return FileOutputStream(newFile)
            } else {
                val toSaf = DocumentFileUtils.createFile(context, newFile, "*/*")
                        ?: throw FileNotFoundException()
                return DocumentFileUtils.outputStreamFor(toSaf, context)
            }
        }

        override fun mkDir(newDir: File): Boolean {
            return DocumentFileUtils.createDirectory(context, newDir) != null
        }
    }

    companion object {
        private const val COPY_BUFFER_SIZE = 32 * 1024
    }
}
