package com.riyadm.apkrepacker.ui.filemanager.storage.operation

import android.content.Context
import com.riyadm.apkrepacker.ui.filemanager.storage.access.ExternalStorageAccessManager


object FileOperationRunnerInjector {

    /**
     * Builds a default instance of [FileOperationRunner].
     */
    @JvmStatic
    fun operationRunner(c: Context): FileOperationRunner {
        return FileOperationRunner(ExternalStorageAccessManager(c), c)
    }
}
