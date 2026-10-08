package com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui

import android.content.Context
import com.riyadm.apkrepacker.ui.notification.NotificationOperationStatusDisplayer
import java.io.File

object OperationStatusDisplayerInjector {
    private val NO_OP_DISPLAYER: OperationStatusDisplayer = object : OperationStatusDisplayer {
        override fun initChannels() {
        }

        override fun showCopySuccess(operationId: Int, destDir: File) {
        }

        override fun showCopyFailure(operationId: Int, destDir: File) {
        }

        override fun showMoveProgress(operationId: Int, destDir: File, moving: File, progress: Int, max: Int) {
        }

        override fun showMoveSuccess(operationId: Int, destDir: File) {
        }

        override fun showMoveFailure(operationId: Int, destDir: File) {
        }

        override fun showCopyProgress(operationId: Int, destDir: File, copying: File, progress: Int, max: Int) {
        }
    }

    @JvmStatic
    fun operationStatusDisplayer(context: Context): OperationStatusDisplayer {
        return NotificationOperationStatusDisplayer(context)
    }

    @JvmStatic
    fun noOpStatusDisplayer(): OperationStatusDisplayer {
        return NO_OP_DISPLAYER
    }
}
