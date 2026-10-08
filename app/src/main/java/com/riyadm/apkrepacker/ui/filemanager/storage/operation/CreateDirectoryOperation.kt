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
import com.riyadm.apkrepacker.ui.filemanager.storage.DocumentFileUtils
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CreateDirectoryArguments

class CreateDirectoryOperation(private val context: Context) : FileOperation<CreateDirectoryArguments>() {

    override fun operate(args: CreateDirectoryArguments): Boolean {
        val dest = args.target

        return dest.exists() || dest.mkdirs()
    }

    override fun operateSaf(args: CreateDirectoryArguments): Boolean {
        val dest = args.target

        return dest.exists() || DocumentFileUtils.createDirectory(context, dest) != null
    }

    override fun onStartOperation(args: CreateDirectoryArguments) {
    }

    override fun onResult(success: Boolean, args: CreateDirectoryArguments) {
        if (success) {
            FileFeedback.show(context, R.string.directory_created_sucess)
            args.getBaseFragment()?.refresh(context, args.target.parentFile)
        } else {
            FileFeedback.show(context, R.string.error)
        }
    }

    override fun onAccessDenied() {
    }

    override fun onRequestingAccess() {
    }

    override fun needsWriteAccess(): Boolean {
        return true
    }
}
