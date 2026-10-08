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

package com.riyadm.apkrepacker.ui.filemanager.storage.access

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.DocumentFile.fromTreeUri
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils.safAwareDelete
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils.getExternalStorageRoot
import java.io.File
import java.util.Locale.ROOT

/**
 * Uses the Storage Access Framework to request and persist access permissions to external storage.
 */
internal class SafStorageAccessManager(context: Context) : StorageAccessManager {
    private val context: Context = context.applicationContext

    override fun hasWriteAccess(fileInStorage: File): Boolean {
        val grantedBefore = permissionGrantedForParentOf(fileInStorage)

        return grantedBefore || checkWriteAccess(fileInStorage)
    }

    override fun requestWriteAccess(fileInStorage: File,
                                    listener: StorageAccessManager.AccessPermissionListener) {

        listener.granted()
        /*Intent safPromptIntent = new Intent(context, SafPromptActivity.class);
        safPromptIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(safPromptIntent);*/
    }

    override fun isSafBased(): Boolean {
        return true
    }

    private fun permissionGrantedForParentOf(fileInStorage: File): Boolean {
        val permissions = context.contentResolver.persistedUriPermissions

        for (permission in permissions) {
            val storageRoot = getExternalStorageRoot(fileInStorage, context)
            val grantedDocFile = fromTreeUri(context, permission.uri)
            val grantedOnAncestor = DocumentFileUtils.areSameFile(storageRoot, grantedDocFile)
            if (permission.isWritePermission && grantedOnAncestor) return true
        }
        return false
    }

    private fun checkWriteAccess(fileInStorage: File): Boolean {
        val fileParent = fileInStorage.parentFile
        // Reached root, can't write
        if (fileParent == null) return false
        // Recur until we find a parent that exists
        if (!fileParent.exists()) return checkWriteAccess(fileParent)

        val tmpFile = generateDummyFileIn(fileParent)

        var writable = false
        if (FileUtils.isWritable(tmpFile)) writable = true

        val document: DocumentFile?
        if (!writable) {
            // Java said not writable, confirm with SAF
            document = DocumentFileUtils.createFile(context, tmpFile, "image/png")

            if (document != null) {
                writable = document.canWrite() && tmpFile.exists()
            }
        }

        // Cleanup
        safAwareDelete(context, tmpFile)
        return writable
    }

    private fun generateDummyFileIn(parent: File): File {
        var dummyFile: File
        var i = 0
        do {
            val fileName = String.format(ROOT, "WriteAccessCheck%d", i++)
            dummyFile = File(parent, fileName)
        } while (dummyFile.exists())
        return dummyFile
    }
}
