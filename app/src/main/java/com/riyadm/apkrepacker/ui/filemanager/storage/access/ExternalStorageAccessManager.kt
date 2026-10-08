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
import java.io.File

class ExternalStorageAccessManager(context: Context) : StorageAccessManager {
    private val delegate: StorageAccessManager = SafStorageAccessManager(context)

    override fun hasWriteAccess(fileInStorage: File): Boolean {
        return delegate.hasWriteAccess(fileInStorage)
    }

    override fun requestWriteAccess(fileInStorage: File, listener: StorageAccessManager.AccessPermissionListener) {
        delegate.requestWriteAccess(fileInStorage, listener)
    }

    override fun isSafBased(): Boolean {
        return delegate.isSafBased()
    }
}
