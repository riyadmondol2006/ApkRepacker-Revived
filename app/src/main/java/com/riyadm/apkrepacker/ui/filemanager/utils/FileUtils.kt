/*
 * Copyright (C) 2018 George Venios
 * Copyright (C) 2007-2008 OpenIntents.org
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

package com.riyadm.apkrepacker.ui.filemanager.utils

import com.riyadm.apkrepacker.App
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.content.pm.ResolveInfo
import android.net.Uri
import android.net.Uri.fromFile
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.text.format.Formatter
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.utils.FileProvider
import com.riyadm.codeeditor.util.DLog
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Integer.MAX_VALUE
import java.util.Collections.unmodifiableList
import java.util.Locale

/**
 * @author Peli
 * @version 2009-07-03
 */
object FileUtils {
    const val NOMEDIA_FILE_NAME = ".nomedia"
    private const val EXTENSION_APK = "apk"

    /**
     * Gets the extension of a file name, like ".png" or ".jpg".
     *
     * @param path The file path or name
     * @return Extension including the dot("."); "" if there is no extension;
     * null if uri was null.
     */
    @JvmStatic
    fun getExtension(path: String): String {
        var ext = ""
        val name = File(path).name

        val i = name.lastIndexOf('.')

        if (i > 0 && i < name.length - 1) {
            ext = name.substring(i).lowercase(Locale.getDefault())
        }
        return ext
    }

    @JvmStatic
    fun getUri(fileHolder: FileHolder): Uri {
        return getUri(fileHolder.file.absolutePath)
    }

    /**
     * @deprecated Use getUri() instead. This will intentionally crash on and rafter API 24.
     */
    @Deprecated("Use getUri() instead. This will intentionally crash on and rafter API 24.")
    private fun getFileUri(fileHolder: FileHolder): Uri {
        if (Build.VERSION.SDK_INT >= N) {
            throw IllegalStateException("Tried to use File URI on a new Android version.")
        }
        return fromFile(fileHolder.file)
    }

    private fun getUri(filePath: String): Uri {
        var filePath = filePath
        if (filePath.startsWith("//")) {
            filePath = filePath.substring(2)
        }
        // The provider expects the Base64 path form (a raw path never resolved).
        return FileProvider.getUriForFile(App.get(), File(filePath))
    }

    /**
     * Convert Uri into File.
     *
     * @param uri Uri to convert.
     * @return The file pointed to by the uri.
     */
    @JvmStatic
    fun getFile(uri: Uri?): File? {
        if (uri != null) {
            val filepath = uri.path
            if (filepath != null) {
                return File(filepath)
            }
        }
        return null
    }

    /**
     * Returns the path only (without file name).
     *
     * @param file The file whose path to get.
     * @return The first directory up from file. If file.isdirectory returns the file.
     */
    @JvmStatic
    fun getPathWithoutFilename(file: File?): File? {
        if (file != null) {
            if (file.isDirectory) {
                // no file to be split off. Return everything
                return file
            } else {
                val filename = file.name
                val filepath = file.absolutePath

                // Construct path without file name.
                var pathWithoutName = filepath.substring(0, filepath.length - filename.length)
                if (pathWithoutName.endsWith("/")) {
                    pathWithoutName = pathWithoutName.substring(0, pathWithoutName.length - 1)
                }
                return File(pathWithoutName)
            }
        }
        return null
    }

    @JvmStatic
    fun formatSize(context: Context?, sizeInBytes: Long): String {
        return Formatter.formatFileSize(context, sizeInBytes)
    }

    @JvmStatic
    fun folderSize(directory: File): Long {
        var length: Long = 0
        val files = directory.listFiles()
        if (files != null)
            for (file in files)
                if (file.isFile)
                    length += file.length()
                else
                    length += folderSize(file)
        return length
    }

    /**
     * @param f File which needs to be checked.
     * @return True if the file is a zip archive.
     */
    @JvmStatic
    fun isZipArchive(f: File): Boolean {
        // Hacky but fast
        return f.isFile && getExtension(f.absolutePath) == ".zip"
    }

    /**
     * Recursively count all files in the `file`'s subtree.
     *
     * @param file The root of the tree to count.
     */
    @JvmStatic
    fun countFilesUnder(file: File): Int {
        var fileCount = 0
        if (!file.isDirectory) {
            fileCount++
        } else {
            if (file.list() != null) {
                for (f in file.listFiles()!!) {
                    fileCount += countFilesUnder(f)
                }
            }
        }

        return fileCount
    }

    @JvmStatic
    fun countFilesUnder(list: List<FileHolder>): Int {
        var fileCount = 0
        for (fh in list) {
            fileCount += countFilesUnder(fh.file)
        }

        return fileCount
    }

    /**
     * Native helper method, returns whether the current process has execute privilages.
     *
     * @param file File
     * @return returns True if the current process has execute permission.
     */
    @JvmStatic
    fun canExecute(file: File): Boolean {
        return file.canExecute()
    }

    /**
     * @param path     The path that the file is supposed to be in.
     * @param fileName Desired file name. This name will be modified to create a unique file if necessary.
     * @return A file name that is guaranteed to not exist yet. MAY RETURN NULL!
     */
    @JvmStatic
    fun createUniqueCopyName(context: Context, path: File?, fileName: String): File? {
        var fileName = fileName
        // Does that file exist?
        var file = File(path, fileName)

        if (!file.exists()) {
            // Nope - we can take that.
            return file
        }

        // Split file's name and extension to fix internationalization issue #307
        val extension = getExtension(file.path)
        val extStart = fileName.lastIndexOf(extension)
        if (extStart > 0) {
            fileName = fileName.substring(0, extStart)
        }

        // Try a simple "copy of".
        file = File(path, context.getString(R.string.copied_file_name, fileName) + extension)

        if (!file.exists()) {
            // Nope - we can take that.
            return file
        }

        var copyIndex = 2

        // Well, we gotta find a unique name at some point.
        while (copyIndex < MAX_VALUE) {
            val unqFile = context.getString(R.string.copied_file_name_2, copyIndex++, fileName) + extension
            file = File(path, unqFile)

            if (!file.exists()) return file
        }

        return null
    }

    /**
     * Attempts to open a file for viewing.
     *
     * @param fileholder The holder of the file to open.
     */
    @JvmStatic
    fun openFile(fileholder: FileHolder, c: Context) {
        val intent = getViewIntentFor(fileholder, c)
        /* if (EXTENSION_APK.equals(fileholder.getExtension())) {
             launchFileIntent(getInstallIntentFor(fileholder, c), intent, c);
         } else {*/
        launchFileIntent(intent, c)
        //   }
    }

    @JvmStatic
    fun getViewIntentFor(fileholder: FileHolder, c: Context?): Intent {
        val data = getUri(fileholder)
        val type = fileholder.mimeType

        val intent = Intent(ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_GRANT_READ_URI_PERMISSION
        intent.setDataAndType(data, type)
        return intent
    }

    @Suppress("DEPRECATION")
    private fun getInstallIntentFor(fileHolder: FileHolder, c: Context): Intent {
        val data = if (SDK_INT >= N) getUri(fileHolder) else getFileUri(fileHolder)
        val type = fileHolder.mimeType
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.setDataAndType(data, type)
        return intent
    }

    private fun launchFileIntent(intent: Intent, c: Context) {
        launchFileIntent(intent, null, c)
    }

    private fun launchFileIntent(intent: Intent, fallbackIntent: Intent?, c: Context) {
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val activities = c.packageManager.queryIntentActivities(intent, MATCH_DEFAULT_ONLY)
            if (activities.size == 0 || onlyActivityIsOurs(c, activities)) {
                launchFallbackOrToast(fallbackIntent, c)
            } else {
                c.startActivity(intent)
            }
        } catch (e: ActivityNotFoundException) {
            launchFallbackOrToast(fallbackIntent, c)
        } catch (e: SecurityException) {
            launchFallbackOrToast(fallbackIntent, c)
        }
    }

    private fun launchFallbackOrToast(fallbackIntent: Intent?, c: Context) {
        if (fallbackIntent != null) {
            launchFileIntent(fallbackIntent, c)
        } else {
            // makeText(c.getApplicationContext(), R.string.application_not_available, LENGTH_SHORT).show();
        }
    }

    @JvmStatic
    fun isValidDirectory(file: File): Boolean {
        return file.exists() && file.isDirectory
    }

    @JvmStatic
    fun isResolverActivity(resolveInfo: ResolveInfo?): Boolean {
        if (resolveInfo == null || resolveInfo.activityInfo == null) return false

        // Please kill me..
        return "android" == resolveInfo.activityInfo.packageName
                && "com.android.internal.app.ResolverActivity" == resolveInfo.activityInfo.name
    }

    private fun onlyActivityIsOurs(c: Context, activities: List<ResolveInfo>): Boolean {
        val dirPackage = c.applicationInfo.packageName
        val resolvedPackage = activities[0].activityInfo.packageName

        return activities.size == 1 && dirPackage == resolvedPackage
    }

    @JvmStatic
    fun getNameWithoutExtension(f: File): String {
        val fileName = f.name
        val extension = getExtension(fileName)
        return fileName.substring(0, fileName.length - extension.length)
    }

    /**
     * Delete a file or directory along with its children.
     *
     * @return Whether the operation succeeded.
     */
    @JvmStatic
    fun delete(fileOrDirectory: File): Boolean {
        var res = true

        // Delete children if directory
        val children = fileOrDirectory.listFiles()
        val hasChildren = children != null && children.size != 0
        if (hasChildren) {
            for (childFile in children!!) {
                if (childFile.isDirectory) {
                    res = res and delete(childFile)
                } else {
                    res = res and deleteFile(childFile)
                }
            }
        }

        // Delete the file itself
        res = res and deleteFile(fileOrDirectory)

        return res
    }

    @JvmStatic
    fun getFileName(file: File): String {
        return if (file.absolutePath == "/") {
            "/"
        } else {
            file.name
        }
    }

    @JvmStatic
    fun isSymlink(file: File): Boolean {
        // We should use NIO on >26 which should give more correct results.
        try {
            val canon: File
            if (file.parent == null) {
                canon = file
            } else {
                val canonDir = file.parentFile!!.canonicalFile
                canon = File(canonDir, file.name)
            }
            return canon.canonicalFile != canon.absoluteFile
        } catch (e: IOException) {
            DLog.log(e)
            return false
        }
    }

    @JvmStatic
    fun isWritable(file: File): Boolean {
        val fileJustCreated = !file.exists()

        // Check by opening a stream
        try {
            val output = FileOutputStream(file, true)
            try {
                output.close()
            } catch (ignored: IOException) {
            }
        } catch (ignored: FileNotFoundException) {
            return false
        }

        // If stream successful, check with Java
        val writable = file.canWrite()
        if (fileJustCreated) file.delete()
        return writable
    }

    /**
     * Determine if a file is on external sd card. (Kitkat or higher.)
     *
     * @return true If on external storage.
     */
    @JvmStatic
    fun isOnExternalStorage(file: File, context: Context): Boolean {
        return getExternalStorageRoot(file, context) != null
    }

    /**
     * @param file The file whose parent to look for.
     * @return A File representing the root of the external storage device that contains the file, otherwise null.
     */
    @JvmStatic
    fun getExternalStorageRoot(file: File, context: Context): String? {
        val filePath: String
        try {
            filePath = file.canonicalPath
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }

        val extSdPaths = getExtSdCardPaths(context)
        for (extSdPath in extSdPaths) {
            if (filePath.startsWith(extSdPath)) return extSdPath
        }
        return null
    }

    /**
     * Get a list of external SD card paths.
     *
     * @return A list of external SD card paths.
     */
    @JvmStatic
    fun getExtSdCardPaths(context: Context): List<String> {
        val externalStorageFilesDirs = context.getExternalFilesDirs(null)
        val primaryStorageFilesDir = context.getExternalFilesDir(null)
        val externalStorageRoots: MutableList<String> = ArrayList()
        for (extFilesDir in externalStorageFilesDirs) {
            if (extFilesDir != null && extFilesDir != primaryStorageFilesDir) {
                val rootPathEndIndex = extFilesDir.absolutePath.lastIndexOf("/Android/data")
                if (rootPathEndIndex < 0) {
                    DLog.log("Unexpected external storage directory.")
                } else {
                    var path = extFilesDir.absolutePath.substring(0, rootPathEndIndex)
                    try {
                        path = File(path).canonicalPath
                    } catch (e: IOException) {
                        DLog.log("Could not get canonical path for external storage. Using absolute.")
                    }
                    externalStorageRoots.add(path)
                }
            }
        }

        val rootsCount = externalStorageRoots.size
        return unmodifiableList(externalStorageRoots)
    }

    @JvmStatic
    fun getPathsUnder(file: File): List<String> {
        val paths: MutableList<String> = ArrayList()
        if (file.isDirectory) {
            MediaScannerUtils.getPathsOfFolder(paths, file)
        } else {
            paths.add(file.absolutePath)
        }
        return paths
    }

    private fun deleteFile(childFile: File): Boolean {
        return !childFile.exists() || childFile.delete()
    }
}
