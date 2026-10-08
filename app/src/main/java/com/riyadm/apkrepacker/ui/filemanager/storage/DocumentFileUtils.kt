package com.riyadm.apkrepacker.ui.filemanager.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.codeeditor.util.DLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.regex.Pattern

object DocumentFileUtils {
    @JvmStatic
    @Throws(NullPointerException::class, FileNotFoundException::class)
    fun outputStreamFor(outFile: DocumentFile, context: Context): OutputStream {
        val out = context.contentResolver.openOutputStream(outFile.uri)
                ?: throw NullPointerException("Could not open DocumentFile OutputStream")

        return out
    }

    /**
     * Very crude check.
     *
     * @return Whether filePath and documentFile represent the same file on disk.
     */
    @JvmStatic
    fun areSameFile(filePath: String?, documentFile: DocumentFile?): Boolean {
        if (filePath == null) return false
        val file = File(filePath)
        return file.lastModified() == documentFile!!.lastModified()
                && file.name == documentFile.name
    }

    /**
     * Delete a file. May be even on external SD card.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    @JvmStatic
    fun safAwareDelete(context: Context, file: File): Boolean {
        if (!file.exists()) return true
        var deleteSucceeded = FileUtils.delete(file)

        if (!deleteSucceeded) {
            val safFile = findFile(context, file)
            if (safFile != null) deleteSucceeded = safFile.delete()
        }

        return deleteSucceeded && !file.exists()
    }

    @JvmStatic
    fun findFile(context: Context, file: File): DocumentFile? {
        if (!file.exists()) {
            throw IllegalArgumentException(
                    "File must exist. Use createFile() or createDirectory() instead.")
        } else {
            return seekOrCreateTreeDocumentFile(context, file, null, false)
        }
    }

    @JvmStatic
    fun createFile(context: Context, file: File, mimeType: String?): DocumentFile? {
        if (file.exists()) {
            throw IllegalArgumentException(
                    "File must not exist. Use findFile() instead.")
        } else {
            return seekOrCreateTreeDocumentFile(context, file, mimeType, true)
        }
    }

    @JvmStatic
    fun createDirectory(context: Context, directory: File): DocumentFile? {
        if (directory.exists()) {
            throw IllegalArgumentException("Directory must not exist. Use findFile() instead.")
        } else {
            return seekOrCreateTreeDocumentFile(context, directory, null, true)
        }
    }

    /**
     * Get a DocumentFile corresponding to the given file. If the file doesn't exist, it is created.
     *
     * @param file     The file to get the DocumentFile representation of.
     * @param mimeType Only applies if shouldCreate is true. The mimeType of the file to create.
     * Null creates directory.
     * @return The DocumentFile representing the passed file. Null if the file or its path can't
     * be created, or found - depending on shouldCreate's value.
     */
    private fun seekOrCreateTreeDocumentFile(context: Context,
                                             file: File,
                                             mimeType: String?,
                                             shouldCreate: Boolean): DocumentFile? {
        val storageRoot = FileUtils.getExternalStorageRoot(file, context)
                ?: return null   // File is not on external storage

        var fileIsStorageRoot = false
        var filePathRelativeToRoot: String? = null
        try {
            val filePath = file.canonicalPath
            if (storageRoot != filePath) {
                filePathRelativeToRoot = filePath.substring(storageRoot.length + 1)
            } else {
                fileIsStorageRoot = true
            }
        } catch (e: IOException) {
            DLog.log("Could not get canonical path of File while getting DocumentFile")
            return null
        } catch (e: SecurityException) {
            fileIsStorageRoot = true
        }

        val docTreeUri = findStorageTreeUri(context, storageRoot)
                ?: return null // We don't have write permission for storageRoot

        // Walk the granted storage tree
        var docFile = DocumentFile.fromTreeUri(context, docTreeUri)
        if (fileIsStorageRoot) return docFile

        // Same semantics as java.lang.String.split("/")
        val filePathSegments = Pattern.compile("/").split(filePathRelativeToRoot!!)
        for (i in filePathSegments.indices) {
            val segment = filePathSegments[i]
            val isLastSegment = i == filePathSegments.size - 1
            var nextDocFile = docFile!!.findFile(segment)

            if (nextDocFile == null && shouldCreate) {
                val shouldCreateFile = isLastSegment && mimeType != null
                nextDocFile = if (shouldCreateFile) docFile.createFile(mimeType!!, segment)
                else docFile.createDirectory(segment)
            }

            if (nextDocFile == null) {
                // If shouldCreate = true, it means that current segment is not writable
                // Otherwise we couldn't find the file we were looking for
                return null
            } else {
                docFile = nextDocFile
            }
        }

        return docFile
    }

    private fun findStorageTreeUri(context: Context, storageRoot: String): Uri? {
        val permissions = context.contentResolver.persistedUriPermissions
        for (permission in permissions) {
            if (permission.isWritePermission) {
                val grantTree = DocumentFile.fromTreeUri(context, permission.uri)
                val storageRoots = FileUtils.getExtSdCardPaths(context)
                for (root in storageRoots) {
                    if (areSameFile(root, grantTree)) return grantTree!!.uri
                }
            }
        }
        return null
    }
}
