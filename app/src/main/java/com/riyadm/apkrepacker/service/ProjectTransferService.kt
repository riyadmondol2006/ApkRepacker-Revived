package com.riyadm.apkrepacker.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.project.ProjectTransfer
import com.riyadm.apkrepacker.project.TransferException
import com.riyadm.apkrepacker.project.TreeFs
import com.riyadm.apkrepacker.ui.preferences.PreferenceHelper
import com.riyadm.apkrepacker.utils.JobWakeLock
import com.riyadm.apkrepacker.utils.NotificationHelper
import com.riyadm.apkrepacker.utils.common.DLog
import com.riyadm.apkrepacker.viewmodel.projects.ProjectLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves projects to a ZIP / folder and loads them back, in the background: a dataSync foreground
 * service with an ongoing progress notification and a "finished" notification, so a big project
 * keeps copying with the screen off or the app closed. Jobs run one at a time. The outcome is also
 * emitted on [results] for the Projects screen's Snackbar.
 */
class ProjectTransferService : Service() {

    /** What happened; [message] is ready to show. */
    data class Result(val success: Boolean, val message: String)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val serial = Mutex()
    private val pending = AtomicInteger(0)
    private val doneIds = AtomicInteger(0)
    @Volatile
    private var inForeground = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Id of the newest start command; stopSelf(id) then never kills a job that arrived meanwhile. */
    private val latestStartId = AtomicInteger(0)
    private var wakeLock: JobWakeLock? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        wakeLock = JobWakeLock(this, "transfer")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId.set(startId)
        val action = intent?.action
        val uri = intent?.data
        val project = intent?.getStringExtra(EXTRA_PROJECT)
        val label = when (action) {
            ACTION_EXPORT_ZIP, ACTION_EXPORT_FOLDER -> getString(R.string.transfer_exporting)
            else -> getString(R.string.transfer_importing)
        }
        // Started with startForegroundService(): go foreground right away, every time.
        post(label, startForeground = true)

        if (action == null || uri == null) {
            if (pending.get() == 0) stopSelf(startId)
            return START_NOT_STICKY
        }

        pending.incrementAndGet()
        wakeLock?.acquire()
        scope.launch {
            try {
                val result = serial.withLock { run(action, uri, project, label) }
                publish(result)
            } finally {
                // Always, even if the job threw or was cancelled.
                wakeLock?.release()
                if (pending.decrementAndGet() == 0) {
                    // Re-check on the main thread, where onStartCommand runs: a start that raced in
                    // after the decrement bumped pending (or the start id) and keeps the service.
                    mainHandler.post {
                        if (pending.get() == 0) {
                            leaveForeground()
                            stopSelf(latestStartId.get())
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun run(action: String, uri: Uri, project: String?, label: String): Result {
        val progress = throttled(label)
        return try {
            when (action) {
                ACTION_EXPORT_ZIP -> {
                    val dir = File(requireNotNull(project))
                    try {
                        val out = contentResolver.openOutputStream(uri, "wt") ?: throw TransferException(R.string.transfer_error_open)
                        ProjectTransfer.exportZip(dir, out, progress)
                    } catch (e: Exception) {
                        // Don't leave a truncated ZIP behind.
                        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                        throw e
                    }
                    Result(true, getString(R.string.transfer_export_done, displayName(uri) ?: dir.name))
                }

                ACTION_EXPORT_FOLDER -> {
                    val dir = File(requireNotNull(project))
                    val folder = ProjectTransfer.exportFolder(dir, TreeFs.sink(this, uri), progress)
                    Result(true, getString(R.string.transfer_export_folder_done, folder.ifEmpty { ProjectTransfer.safeName(dir.name) }))
                }

                ACTION_IMPORT_ZIP -> {
                    val input = contentResolver.openInputStream(uri) ?: throw TransferException(R.string.transfer_error_open)
                    val imported = input.use {
                        ProjectTransfer.importZip(it, displayName(uri) ?: "project", PreferenceHelper.getInstance(this).projectsDir, progress)
                    }
                    importedResult(imported)
                }

                ACTION_IMPORT_FOLDER -> {
                    val imported = ProjectTransfer.importFolder(TreeFs.source(this, uri), PreferenceHelper.getInstance(this).projectsDir, progress)
                    importedResult(imported)
                }

                else -> Result(false, getString(R.string.transfer_failed, action))
            }
        } catch (e: TransferException) {
            Result(false, getString(R.string.transfer_failed, getString(e.resId, *e.args)))
        } catch (e: Exception) {
            DLog.e("ProjectTransfer", e)
            Result(false, getString(R.string.transfer_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun importedResult(imported: List<File>): Result {
        ProjectLoader.getInstance(applicationContext).loadProjects()
        return Result(true, resources.getQuantityString(R.plurals.transfer_import_done, imported.size, imported.size))
    }

    /** The system rate-limits notification updates, so report progress at most every ~400 ms. */
    private fun throttled(label: String): ProjectTransfer.Progress {
        var last = 0L
        return ProjectTransfer.Progress { done, total, name ->
            val now = System.currentTimeMillis()
            if (now - last >= 400 || (total in 1..done)) {
                last = now
                post(if (total > 0) getString(R.string.transfer_progress, done, total) else getString(R.string.transfer_progress_unknown, done), step = done, total = total, detail = name, title = label)
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // region notifications
    private fun buildNotification(title: String, text: String, step: Int, total: Int, detail: String?): Notification {
        val openApp = NotificationHelper.openAppIntent(this)
        return NotificationHelper.progressBuilder(this, NotificationHelper.CHANNEL_FILEOPS, R.drawable.ic_m3_notif_files)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (!detail.isNullOrEmpty()) setSubText(detail) }
            .setProgress(total, step, total <= 0)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun post(text: String, startForeground: Boolean = false, step: Int = 0, total: Int = 0, detail: String? = null, title: String = text) {
        val notification = buildNotification(title, text, step, total, detail)
        if (startForeground || !inForeground) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                )
                inForeground = true
            } catch (e: IllegalStateException) {
                // API 31+: not allowed to start from the background; keep running as a plain service.
                DLog.e("ProjectTransfer", e)
            }
        } else if (NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked meanwhile.
            }
        }
    }

    private fun publish(result: Result) {
        _results.tryEmit(result)
        if (!NotificationHelper.canPostNotifications(this)) return
        val icon = if (result.success) R.drawable.ic_m3_notif_done else R.drawable.ic_m3_notif_error
        val notification = NotificationHelper.completionBuilder(this, icon)
            .setContentTitle(getString(if (result.success) R.string.transfer_done_title else R.string.transfer_failed_title))
            .setContentText(result.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.message))
            .setContentIntent(NotificationHelper.openAppIntent(this))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_BASE + doneIds.getAndIncrement() % 50, notification)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }

    private fun leaveForeground() {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
    }

    /** Android 15+: dataSync services may run 6 hours a day; stop right away when told to. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job.cancel()
        publish(Result(false, getString(R.string.transfer_failed, getString(R.string.notification_build_timeout))))
        leaveForeground()
        stopSelf()
    }
    // endregion

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        wakeLock?.release()
        job.cancel()
    }

    companion object {
        const val ACTION_EXPORT_ZIP = "com.riyadm.apkrepacker.transfer.EXPORT_ZIP"
        const val ACTION_EXPORT_FOLDER = "com.riyadm.apkrepacker.transfer.EXPORT_FOLDER"
        const val ACTION_IMPORT_ZIP = "com.riyadm.apkrepacker.transfer.IMPORT_ZIP"
        const val ACTION_IMPORT_FOLDER = "com.riyadm.apkrepacker.transfer.IMPORT_FOLDER"
        private const val EXTRA_PROJECT = "project"

        private const val NOTIFICATION_ID = 5
        private const val DONE_NOTIFICATION_BASE = 600

        private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 8)

        /** Outcomes of finished jobs, for a Snackbar when the Projects screen is showing. */
        val results: SharedFlow<Result> = _results.asSharedFlow()

        /** Queues a job. [project] is the project folder for the export actions. */
        fun start(context: Context, action: String, uri: Uri, project: String? = null) {
            val intent = Intent(context, ProjectTransferService::class.java)
                .setAction(action)
                .setData(uri)
                .putExtra(EXTRA_PROJECT, project)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                DLog.e("ProjectTransfer", e)
                _results.tryEmit(Result(false, context.getString(R.string.transfer_failed, context.getString(R.string.build_service_start_failed))))
            }
        }
    }
}
