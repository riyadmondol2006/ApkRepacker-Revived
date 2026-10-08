/*
 * Copyright (C) 2018 George Venios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.riyadm.apkrepacker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.CompressOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ExtractOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.FileOperationRunnerInjector
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CompressArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.ExtractArguments
import com.riyadm.apkrepacker.utils.common.DLog
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** Compresses / extracts files one request at a time and stops itself after the last one. */
class ZipService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executor.execute {
            try {
                onHandleIntent(intent)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun onHandleIntent(intent: Intent?) {
        val target = intent?.data?.path?.let(::File) ?: return
        val files = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_FILES, FileHolder::class.java) ?: return

        try {
            val runner = FileOperationRunnerInjector.operationRunner(this)
            when (intent.action) {
                ACTION_COMPRESS -> runner.run(CompressOperation(this), CompressArguments.compressArgs(target, files))
                ACTION_EXTRACT -> runner.run(ExtractOperation(this), ExtractArguments.extractArgs(target, files))
            }
        } catch (e: IOException) {
            DLog.e("ZipService", e)
        }
    }

    companion object {
        private const val ACTION_COMPRESS = "com.riyadm.apkrepacker.action.COMPRESS"
        private const val ACTION_EXTRACT = "com.riyadm.apkrepacker.action.EXTRACT"
        private const val EXTRA_FILES = "com.riyadm.apkrepacker.action.FILES"

        @JvmStatic
        fun extractTo(c: Context, tbe: FileHolder, extractTo: File) {
            extractTo(c, listOf(tbe), extractTo)
        }

        @JvmStatic
        fun compressTo(c: Context, tbc: FileHolder, compressTo: File) {
            compressTo(c, listOf(tbc), compressTo)
        }

        @JvmStatic
        fun extractTo(c: Context, tbe: List<FileHolder>, extractTo: File) {
            c.startService(request(c, ACTION_EXTRACT, tbe, extractTo))
        }

        @JvmStatic
        fun compressTo(c: Context, tbc: List<FileHolder>, compressTo: File) {
            c.startService(request(c, ACTION_COMPRESS, tbc, compressTo))
        }

        private fun request(c: Context, action: String, files: List<FileHolder>, target: File) =
            Intent(action)
                .setClassName(c, ZipService::class.java.name)
                .setData(Uri.fromFile(target))
                .putParcelableArrayListExtra(EXTRA_FILES, ArrayList(files))
    }
}
