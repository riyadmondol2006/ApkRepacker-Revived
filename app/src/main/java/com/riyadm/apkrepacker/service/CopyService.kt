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

package com.riyadm.apkrepacker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.fragment.base.BaseFilesFragment
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.CopyOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.FileOperationRunnerInjector
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.MoveOperation
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.CopyArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.argument.MoveArguments
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui.OperationStatusDisplayerInjector
import com.riyadm.apkrepacker.ui.filemanager.utils.FileUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors


/**
 * To use, call the copyTo and moveTo static methods with the appropriate parameters.
 *
 * Internal usage instructions:
 *
 * 1. Pass the files to be copied/moved as a list of FileHolders on EXTRA_FILES.
 * 2. Pass the path to copy/move to as the data string of the intent.
 * 3. Choose between copy or move by using ACTION_COPY or ACTION_MOVE respectively.
 *
 * Runs as a dataSync foreground service (it used to be an IntentService, which the system
 * stops about a minute after the app goes to the background, mid-copy). Requests are
 * handled one at a time, in order.
 */
class CopyService : Service() {

    private val mExecutor = Executors.newSingleThreadExecutor()
    private val mMainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        mExecutor.execute {
            try {
                onHandleIntent(intent)
            } catch (e: Exception) {
                DLog.e("CopyService", e)
            } finally {
                // Stops (and drops the notification) only after the last queued request.
                mMainHandler.post { stopSelf(startId) }
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        NotificationHelper.createChannels(this)
        val notification = NotificationHelper.progressBuilder(this, NotificationHelper.CHANNEL_FILEOPS, R.drawable.ic_m3_notif_files)
            .setContentTitle(getString(R.string.notification_copying))
            .setProgress(0, 0, true)
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .build()
        try {
            ServiceCompat.startForeground(
                this, FOREGROUND_NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (e: IllegalStateException) {
            // API 31+: not allowed right now (background / dataSync time limit); copy anyway.
            DLog.e("CopyService", e)
        }
    }

    /** Android 15+: the daily dataSync time limit is used up; stop right away. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        mExecutor.shutdownNow()
        stopSelf()
    }

    override fun onDestroy() {
        mExecutor.shutdown()
        super.onDestroy()
    }

    private fun onHandleIntent(intent: Intent?) {
        val destination = intent?.data?.path?.let(::File) ?: return
        val files = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_FILES, FileHolder::class.java)
            ?.takeIf { it.isNotEmpty() } ?: return

        val spaceLeft = when (intent.action) {
            ACTION_COPY -> spaceRemainingAfterCopy(files, destination).also { if (it > 0) copy(files, destination) }
            ACTION_MOVE -> spaceRemainingAfterMove(files, destination).also { if (it > 0) move(files, destination) }
            else -> return
        }

        if (spaceLeft > 0) {
            mFilesFragment?.let { fragment ->
                mMainHandler.post { fragment.refresh(this, destination) }
            }
        }
    }

    private fun copy(files: List<FileHolder>, to: File) {
        try {
            FileOperationRunnerInjector.operationRunner(this).run(
                CopyOperation(this, OperationStatusDisplayerInjector.operationStatusDisplayer(this)),
                CopyArguments.copyArgs(files, to, mFilesFragment)
            )
        } catch (e: IOException) {
            DLog.e("CopyService", e)
        }
    }

    private fun move(files: List<FileHolder>, to: File) {
        try {
            FileOperationRunnerInjector.operationRunner(this).run(
                MoveOperation(this, OperationStatusDisplayerInjector.operationStatusDisplayer(this)),
                MoveArguments.moveArgs(files, to, mFilesFragment)
            )
        } catch (e: IOException) {
            DLog.e("CopyService", e)
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val ACTION_COPY = "com.riyadm.apkrepacker.action.COPY"
        private const val ACTION_MOVE = "com.riyadm.apkrepacker.action.MOVE"
        private const val EXTRA_FILES = "com.riyadm.apkrepacker.action.FILES"
        private var mFilesFragment: BaseFilesFragment? = null

        @JvmStatic
        fun setFilesFragment(filesFragment: BaseFilesFragment?) {
            mFilesFragment = filesFragment
        }

        private fun spaceRemainingAfterCopy(of: List<FileHolder>, on: File): Long {
            var needed: Long = 0

            for (f in of) {
                needed += if (f.file.isDirectory) FileUtils.folderSize(f.file) else f.file.length()
            }

            return on.usableSpace - needed
        }

        private fun spaceRemainingAfterMove(of: List<FileHolder>, on: File): Long {
            var needed: Long = 0
            // We know all clipboard files are on the same directory.
            val onSameStorage = onSameStorage(of[0].file, on)

            for (f in of) {
                if (!onSameStorage) {
                    needed += if (f.file.isDirectory) FileUtils.folderSize(f.file) else f.file.length()
                }
            }

            return on.usableSpace - needed
        }

        /**
         * Bad but good enough for the common case. Someone, please write something better :)
         *
         * @return Whether these two files are on the same storage card/disk.
         * May return false positives but never false negatives.
         */
        private fun onSameStorage(file1: File, file2: File): Boolean {
            val fs1 = StatFs(file1.absolutePath)
            val fs2 = StatFs(file2.absolutePath)
            return fs1.availableBlocksLong == fs2.availableBlocksLong
                    && fs1.blockCountLong == fs2.blockCountLong
                    && fs1.freeBlocksLong == fs2.freeBlocksLong
                    && fs1.blockSizeLong == fs2.blockSizeLong
        }

        /** Started from the file manager UI, i.e. while the app is in the foreground. */
        private fun start(c: Context, i: Intent) {
            try {
                ContextCompat.startForegroundService(c, i)
            } catch (e: IllegalStateException) {
                // API 31+: ForegroundServiceStartNotAllowedException when not in the foreground.
                DLog.e("CopyService", e)
                Toast.makeText(c, e.toString(), Toast.LENGTH_LONG).show()
            }
        }

        @JvmStatic
        fun copyTo(c: Context, mClipboard: List<FileHolder>?, copyTo: File) {
            val i = Intent(ACTION_COPY)
            i.setClassName(c, CopyService::class.java.name)
            i.data = Uri.fromFile(copyTo)
            i.putParcelableArrayListExtra(
                EXTRA_FILES,
                if (mClipboard is ArrayList<*>)
                    mClipboard as ArrayList<FileHolder>
                else
                    ArrayList(mClipboard!!)
            )
            start(c, i)
        }

        @JvmStatic
        fun moveTo(c: Context, mClipboard: List<FileHolder>?, moveTo: File) {
            val i = Intent(ACTION_MOVE)
            i.setClassName(c, CopyService::class.java.name)
            i.data = Uri.fromFile(moveTo)
            i.putParcelableArrayListExtra(
                EXTRA_FILES, if (mClipboard is ArrayList<*>)
                    mClipboard as ArrayList<FileHolder>
                else
                    ArrayList(mClipboard!!)
            )
            start(c, i)
        }
    }
}
