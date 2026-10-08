/*
 * Copyright (C) 2014 George Venios
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
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.riyadm.apkrepacker.task.base.CoroutinesAsyncTask
import java.io.File

object MediaScannerUtils {
    private val sLogScannerListener: MediaScannerConnection.OnScanCompletedListener =
            object : MediaScannerConnection.MediaScannerConnectionClient {
                override fun onMediaScannerConnected() {
                    //Logger.logV(Logger.TAG_MEDIASCANNER, "Scanner connected");
                }

                override fun onScanCompleted(path: String?, uri: Uri?) {
                    //Logger.logV(Logger.TAG_MEDIASCANNER, "Path: " + path + "\tUri: " + uri + " - scanned");
                }
            }

    /**
     * Request a MediaScanner scan for a single file.
     */
    @JvmStatic
    fun informFileAdded(c: Context, f: File?) {
        if (f == null)
            return

        MediaScannerConnection.scanFile(c.applicationContext, arrayOf(f.absolutePath), null,
                sLogScannerListener)
    }

    @JvmStatic
    fun informFolderAdded(c: Context, parentFile: File?) {
        if (parentFile == null)
            return

        val filePaths = ArrayList<String>()
        getPathsUnder(filePaths, parentFile)

        MediaScannerConnection.scanFile(c.applicationContext, filePaths.toTypedArray(), null,
                sLogScannerListener)
    }

    /**
     * Fills "paths" with the paths of all files contained in "from", recursively.
     * @param paths An initialized list instance.
     * @param from The root folder.
     */
    @JvmStatic
    fun getPathsOfFolder(paths: MutableList<String>, from: File?) {
        if (from == null)
            return

        getPathsUnder(paths, from)
    }

    private fun getPathsUnder(pathList: MutableList<String>, folder: File) {
        val files = folder.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    getPathsUnder(pathList, f)
                } else {
                    pathList.add(f.absolutePath)
                }
            }
        }
        pathList.add(folder.absolutePath)
    }

    @JvmStatic
    fun informFolderDeleted(c: Context, parentFile: File?) {
        val paths: MutableList<String> = ArrayList()
        getPathsOfFolder(paths, parentFile)
        informPathsDeleted(c, paths)
    }

    @JvmStatic
    fun informPathsDeleted(c: Context, paths: List<String>?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage: deleting other apps' MediaStore rows throws SecurityException.
            // Scanning a path that no longer exists removes its row instead.
            if (!paths.isNullOrEmpty()) {
                MediaScannerConnection.scanFile(c.applicationContext, paths.toTypedArray(), null, sLogScannerListener)
            }
            return
        }
        val params = DeleteTaskParams()
        params.context = c.applicationContext
        params.paths = paths

        DeleteFromMediaStoreAsyncTask().execute(params)
    }

    private fun getFileContentUri(context: Context, file: File?): Uri? {
        val filePath = file!!.absolutePath
        val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns._ID),
                MediaStore.Files.FileColumns.DATA + "=? ",
                arrayOf(filePath), null)
        if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            cursor.close()
            return MediaStore.Files.getContentUri("external", id.toLong())
        }

        return null
    }

    private class DeleteFromMediaStoreAsyncTask : CoroutinesAsyncTask<DeleteTaskParams, Void, Void?>() {
        override fun doInBackground(vararg params: DeleteTaskParams?): Void? {
            val context = params[0]!!.context!!.applicationContext
            val file = params[0]!!.file
            val paths = params[0]!!.paths

            if (paths == null) {
                safeDelete(context, file)
            } else {
                for (path in paths) {
                    safeDelete(context, File(path))
                }
            }

            return null
        }

        private fun safeDelete(context: Context, file: File?) {
            val uri = getFileContentUri(context, file)
            if (uri != null) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    // Not ours to delete; the next media scan cleans it up.
                }
            } else {
                // Logger.logV(Logger.TAG_MEDIASCANNER, "Error in removing file at " + file.getAbsolutePath() + " from MediaStore");
            }
        }

        override fun onPostExecute(result: Void?) {
            // Logger.logV(Logger.TAG_MEDIASCANNER, "Async removal of references from MediaStore complete");
        }
    }

    private class DeleteTaskParams {
        var context: Context? = null
        var file: File? = null
        var paths: List<String>? = null
    }
}
