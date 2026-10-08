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
import com.riyadm.apkrepacker.ui.filemanager.FileFeedback
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.storage.access.StorageAccessManager
import java.io.IOException

class FileOperationRunner internal constructor(
        private val storageAccessManager: StorageAccessManager,
        contenxt: Context
) {
    private val context: Context = contenxt

    @Throws(IOException::class)
    fun <O : FileOperation<A>, A : FileOperation.Arguments> run(operation: O, args: A) {
        operation.onStartOperation(args)
        var success = operation.operate(args)
        val failedButNeedsAccess = !success && operation.needsWriteAccess()
        if (failedButNeedsAccess) {
            if (storageAccessManager.hasWriteAccess(args.target)) {
                if (storageAccessManager.isSafBased()) {
                    success = operation.operateSaf(args)
                }
                operation.onResult(success, args)
            } else {
                operation.onRequestingAccess()
                storageAccessManager.requestWriteAccess(args.target, object : StorageAccessManager.AccessPermissionListener {
                    override fun granted() {
                        try {
                            this@FileOperationRunner.run(operation, args)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }

                    override fun denied() {
                        operation.onAccessDenied()
                    }

                    override fun error() {
                        FileFeedback.show(context, R.string.toast_error_grant_permisson_sd_card)
                        // toastDisplayer.grantAccessWrongDirectory();
                        try {
                            this@FileOperationRunner.run(operation, args)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                })
            }
        } else {
            operation.onResult(success, args)
        }
    }
}
