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
import androidx.documentfile.provider.DocumentFile
import com.riyadm.apkrepacker.fragment.MyFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CompressArguments
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.MediaScannerUtils
import com.riyadm.codeeditor.util.DLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class CompressOperation(private val context: Context) : FileOperation<CompressArguments>() {

    override fun operate(args: CompressArguments): Boolean {
        val to = args.target
        val outStream = outputStreamFor(to)
        return outStream != null && compressTo(outStream, args.toCompress, to)
    }

    override fun operateSaf(args: CompressArguments): Boolean {
        val to = args.target
        val toSaf = DocumentFileUtils.createFile(context, to, "application/zip")
        val outStream = outputStreamFor(toSaf)
        return outStream != null && compressTo(outStream, args.toCompress, to)
    }

    override fun onStartOperation(args: CompressArguments) {
    }

    override fun onResult(success: Boolean, args: CompressArguments) {
        val target = args.target
        if (!success) DocumentFileUtils.safAwareDelete(context, target)

        MediaScannerUtils.informFileAdded(context, target)
        //Notifier.showCompressDoneNotification(success, id, target, context);
        //BaseFilesFragment.refresh(context,target.getParentFile());
        MyFilesFragment().refresh(context, target.parentFile)
    }

    override fun onAccessDenied() {
    }

    override fun onRequestingAccess() {
        // clearNotification(id, context);
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }

    private fun outputStreamFor(toSaf: DocumentFile?): BufferedOutputStream? {
        if (toSaf == null) return null

        try {
            return BufferedOutputStream(DocumentFileUtils.outputStreamFor(toSaf, context))
        } catch (e: NullPointerException) {
            com.riyadm.apkrepacker.utils.common.DLog.e(e)
            return null
        } catch (e: FileNotFoundException) {
            com.riyadm.apkrepacker.utils.common.DLog.e(e)
            return null
        }
    }

    private fun outputStreamFor(to: File): BufferedOutputStream? {
        try {
            return BufferedOutputStream(FileOutputStream(to))
        } catch (e: FileNotFoundException) {
            DLog.log(e)
            return null
        }
    }

    private fun compressTo(outStream: BufferedOutputStream, toBeCompressed: List<FileHolder>,
                           targetArchive: File): Boolean {
        var filesCompressed = 0
        val fileCount = FileUtils.countFilesUnder(toBeCompressed)
        try {
            ZipOutputStream(BufferedOutputStream(outStream)).use { zipStream ->
                for (file in toBeCompressed) {
                    filesCompressed = compressCore(id, zipStream, file.file,
                            null, filesCompressed, fileCount, targetArchive)
                }
            }
        } catch (e: IOException) {
            DLog.log(e)
            return false
        }
        return true
    }

    /**
     * Recursively compress a File.
     *
     * @return How many files where compressed.
     */
    @Throws(IOException::class)
    private fun compressCore(notId: Int, zipStream: ZipOutputStream, toCompress: File, internalPath: String?,
                             filesCompressed: Int, fileCount: Int, zipFile: File): Int {
        var internalPath = internalPath
        var filesCompressed = filesCompressed
        if (internalPath == null) internalPath = ""

        //showCompressProgressNotification(filesCompressed, fileCount, notId, zipFile, toCompress, context);
        if (toCompress.isFile) {
            val buf = ByteArray(BUFFER_SIZE)
            var len: Int
            val `in` = FileInputStream(toCompress)

            // Create internal zip file entry.
            val entry: ZipEntry
            if (internalPath.length > 0) {
                entry = ZipEntry(internalPath + "/" + toCompress.name)
            } else {
                entry = ZipEntry(toCompress.name)
            }
            entry.time = toCompress.lastModified()
            zipStream.putNextEntry(entry)

            // Compress
            while (`in`.read(buf).also { len = it } > 0) {
                zipStream.write(buf, 0, len)
            }

            filesCompressed++
            zipStream.closeEntry()
            `in`.close()
        } else {
            if (toCompress.list()!!.size == 0) {
                zipStream.putNextEntry(ZipEntry(internalPath + "/" + toCompress.name + "/"))
                zipStream.closeEntry()
            } else {
                for (child in toCompress.listFiles()!!) {
                    filesCompressed = compressCore(notId, zipStream, child,
                            internalPath + "/" + toCompress.name,
                            filesCompressed, fileCount, zipFile)
                }
            }
        }

        return filesCompressed
    }

    private fun throwIfNull(o: Any?, msg: String) {
        if (o == null) throw NullPointerException(msg)
    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }
}
