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
import com.riyadm.apkrepacker.fragment.MyFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.ExtractArguments
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils
import com.riyadm.codeeditor.util.DLog
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import com.riyadm.apkrepacker.utils.SafeZip
import java.util.zip.ZipFile


class ExtractOperation(private val context: Context) : FileOperation<ExtractArguments>() {

    override fun operate(args: ExtractArguments): Boolean {
        return NormalExtractor().extract(args)
    }

    override fun operateSaf(args: ExtractArguments): Boolean {
        return SafExtractor().extract(args)
    }

    override fun onStartOperation(args: ExtractArguments) {
    }

    override fun onResult(success: Boolean, args: ExtractArguments) {
        val to = args.target
        if (!success) DocumentFileUtils.safAwareDelete(context, to)

        MediaScannerUtils.informFileAdded(context, to)
        //Notifier.showExtractDoneNotification(success, id, to, context);
        //  BaseFilesFragment.refresh(context,args.getTarget().getParentFile());
        MyFilesFragment().refresh(context, args.target.parentFile)
    }

    override fun onAccessDenied() {
    }

    override fun onRequestingAccess() {
        // clearNotification(id, context);
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }

    private abstract inner class Extractor {
        fun extract(args: ExtractArguments): Boolean {
            val zipHolders = args.zipFiles
            val dstDirectory = args.target
            val zipFiles: List<ZipFile>
            try {
                zipFiles = fileHoldersToZipFiles(zipHolders)
            } catch (e: IOException) {
                DLog.log(e)
                return false
            }
            val fileCount = entriesIn(zipFiles)
            var extractedCount = 0

            for (zipFile in zipFiles) {
                val e = zipFile.entries()
                while (e.hasMoreElements()) {
                    val entry = e.nextElement() as ZipEntry

                    /*showExtractProgressNotification(extractedCount, fileCount,
                            getLastPathSegment(entry.getName()),
                            getLastPathSegment(zipFile.getName()),
                            id, context);*/

                    val extractSuccessful = extractEntry(zipFile, entry, dstDirectory)
                    if (!extractSuccessful) return false
                    extractedCount++
                }
            }

            return true
        }

        private fun extractEntry(zipFile: ZipFile, zipEntry: ZipEntry, outputDir: File): Boolean {
            // Entry names like "../x" would escape outputDir: skip them.
            val outputFile = SafeZip.resolve(outputDir, zipEntry.name) ?: return true
            if (zipEntry.isDirectory) {
                return createDir(outputFile)
            }
            if (!outputFile.parentFile!!.exists()) {
                val parentCreated = createDir(outputFile.parentFile!!)
                if (!parentCreated) return false
            }

            try {
                BufferedInputStream(zipFile.getInputStream(zipEntry)).use { inStream ->
                    BufferedOutputStream(outputStream(outputFile)).use { outStream ->
                        var len: Int
                        val buf = ByteArray(BUFFER_SIZE)
                        while (inStream.read(buf).also { len = it } > 0) {
                            outStream.write(buf, 0, len)
                        }
                        outputFile.setLastModified(zipEntry.time)
                    }
                }
            } catch (e: IOException) {
                DLog.log(e)
                return false
            }

            return true
        }

        @Throws(IOException::class)
        private fun fileHoldersToZipFiles(files: List<FileHolder>): List<ZipFile> {
            val zips: MutableList<ZipFile> = ArrayList(files.size)

            for (fh in files) {
                zips.add(ZipFile(fh.file))
            }

            return zips
        }

        private fun entriesIn(zipFiles: List<ZipFile>): Int {
            var count = 0
            for (z in zipFiles) count += z.size()
            return count
        }

        abstract fun createDir(dir: File): Boolean

        @Throws(FileNotFoundException::class)
        abstract fun outputStream(outputFile: File): OutputStream
    }

    private inner class NormalExtractor : Extractor() {
        override fun createDir(dir: File): Boolean {
            return dir.exists() || dir.mkdirs()
        }

        @Throws(FileNotFoundException::class)
        override fun outputStream(outputFile: File): OutputStream {
            return FileOutputStream(outputFile)
        }
    }

    private inner class SafExtractor : Extractor() {
        override fun createDir(dir: File): Boolean {
            return dir.exists() || DocumentFileUtils.createDirectory(context, dir) != null
        }

        @Throws(FileNotFoundException::class)
        override fun outputStream(outputFile: File): OutputStream {
            val toSaf = DocumentFileUtils.createFile(context, outputFile, "application/zip")
                    ?: throw NullPointerException("Could not create new zip archive via SAF")
            return DocumentFileUtils.outputStreamFor(toSaf, context)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }
}
