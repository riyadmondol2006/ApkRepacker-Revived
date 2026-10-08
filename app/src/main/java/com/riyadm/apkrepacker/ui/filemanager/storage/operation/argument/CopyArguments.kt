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

package com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument

import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.FileOperation
import java.io.File
import java.util.Collections

class CopyArguments private constructor(toCopy: List<FileHolder>, to: File, fragment: BaseFilesFragment?) : FileOperation.Arguments(to) {
    private val filesToCopy: List<FileHolder> = toCopy
    private val mFragment: BaseFilesFragment? = fragment

    fun getFilesToCopy(): List<FileHolder> {
        return Collections.unmodifiableList(filesToCopy)
    }

    fun getBaseFragment(): BaseFilesFragment? {
        return mFragment
    }

    companion object {
        @JvmStatic
        fun copyArgs(toCopy: List<FileHolder>, to: File, fragment: BaseFilesFragment?): CopyArguments {
            return CopyArguments(toCopy, to, fragment)
        }
    }
}
