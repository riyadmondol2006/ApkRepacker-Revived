package com.riyadm.apkrepacker.filepicker

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.io.FileFilter

object Utility {

    /**
     * Root path of the first removable (SD card) or non-removable storage volume.
     * Public APIs only: StorageManager.getVolumeList()/StorageVolume.getPath() are non-SDK
     * interfaces, restricted for apps with a recent targetSdk.
     *
     * @param is_removable is external storage removable
     */
    @JvmStatic
    fun getExternalStoragePath(mContext: Context, is_removable: Boolean): String? {
        val storageManager = mContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return storageManager.storageVolumes
                .firstOrNull { it.isRemovable == is_removable && it.directory != null }
                ?.directory?.absolutePath
        }
        // API < 30: each volume's app-specific dir is <volume root>/Android/data/<pkg>/files.
        for (dir in mContext.getExternalFilesDirs(null).filterNotNull()) {
            val removable = try {
                Environment.isExternalStorageRemovable(dir)
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (removable != is_removable) continue
            val path = dir.absolutePath
            val idx = path.indexOf("/Android/data/")
            if (idx > 0) return path.substring(0, idx)
        }
        return null
    }

    /**
     * Appends the readable entries of [inter] that pass [filter] to [internalList] (which may
     * already hold the parent-folder row) and sorts it: directories first, then by name.
     * An unreadable directory leaves [internalList] as it was.
     */
    @JvmStatic
    fun prepareFileListEntries(internalList: ArrayList<FileListItem>, inter: File, filter: ExtensionFilter?): ArrayList<FileListItem> {
        val children = if (filter != null) inter.listFiles(filter as FileFilter) else inter.listFiles()
        children.orEmpty()
            .filter { it.canRead() }
            .mapTo(internalList) { FileListItem(it.name, it.absolutePath, it.lastModified(), it.isDirectory) }
        internalList.sort()
        return internalList
    }
}
