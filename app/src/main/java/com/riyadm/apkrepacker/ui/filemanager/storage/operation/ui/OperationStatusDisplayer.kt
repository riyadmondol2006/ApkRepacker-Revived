package com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui

import java.io.File

interface OperationStatusDisplayer {
    fun initChannels()

    fun showCopyProgress(operationId: Int, destDir: File, copying: File, progress: Int, max: Int)
    fun showCopySuccess(operationId: Int, destDir: File)
    fun showCopyFailure(operationId: Int, destDir: File)

    fun showMoveProgress(operationId: Int, destDir: File, moving: File, progress: Int, max: Int)
    fun showMoveSuccess(operationId: Int, destDir: File)
    fun showMoveFailure(operationId: Int, destDir: File)
}
