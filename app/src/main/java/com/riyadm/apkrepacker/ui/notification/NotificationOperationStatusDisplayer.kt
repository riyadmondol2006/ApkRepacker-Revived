package com.riyadm.apkrepacker.ui.notification

import android.app.Notification
import android.content.Context
import android.util.SparseLongArray
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.storage.operation.ui.OperationStatusDisplayer
import com.riyadm.apkrepacker.utils.NotificationHelper
import java.io.File
import java.lang.System.currentTimeMillis

/**
 * Copy / move progress and result as notifications: an ongoing, tinted progress notification per
 * operation, replaced by a dismissable result when the operation took long enough to notice.
 */
class NotificationOperationStatusDisplayer(context: Context) : OperationStatusDisplayer {

    private val context: Context = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(this.context)
    private val startTimes = SparseLongArray()

    override fun initChannels() {
        NotificationHelper.createChannels(context)
    }

    override fun showCopyProgress(operationId: Int, destDir: File, copying: File, progress: Int, max: Int) {
        show(
            operationId,
            progressNotification(
                context.getString(R.string.copying),
                context.getString(R.string.notif_copying_item, copying.name, destDir.absolutePath),
                copying, progress, max
            )
        )
    }

    override fun showCopySuccess(operationId: Int, destDir: File) {
        showSuccess(operationId, destDir, R.string.copied)
    }

    override fun showCopyFailure(operationId: Int, destDir: File) {
        showFailure(operationId, destDir, R.string.copy_error)
    }

    override fun showMoveProgress(operationId: Int, destDir: File, moving: File, progress: Int, max: Int) {
        show(
            operationId,
            progressNotification(
                context.getString(R.string.moving),
                context.getString(R.string.notif_moving_item, moving.name, destDir.absolutePath),
                moving, progress, max
            )
        )
    }

    override fun showMoveSuccess(operationId: Int, destDir: File) {
        showSuccess(operationId, destDir, R.string.moved)
    }

    override fun showMoveFailure(operationId: Int, destDir: File) {
        showFailure(operationId, destDir, R.string.move_error)
    }

    private fun showSuccess(operationId: Int, destDir: File, message: Int) {
        if (isLongOperation(operationId)) {
            show(operationId, resultNotification(destDir, context.getString(message), R.drawable.ic_m3_notif_done))
        } else {
            hide(operationId)
        }
        clearOperationTimer(operationId)
    }

    private fun showFailure(operationId: Int, destDir: File, message: Int) {
        show(operationId, resultNotification(destDir, context.getString(message), R.drawable.ic_m3_notif_error, failed = true))
        clearOperationTimer(operationId)
    }

    private fun progressNotification(title: String, longText: String, operatingOn: File, progress: Int, max: Int): Notification {
        return NotificationHelper.progressBuilder(context, CHANNEL_FILEOPS, R.drawable.ic_m3_notif_files)
            .setContentTitle(title)
            .setContentText(operatingOn.name)
            .setProgress(max, progress, false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
            .setTicker(title)
            .setContentIntent(NotificationHelper.openAppIntent(context))
            .build()
    }

    private fun resultNotification(
        destDir: File,
        message: String,
        @DrawableRes icon: Int,
        failed: Boolean = false
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_FILEOPS)
            .setSmallIcon(icon)
            .setColor(NotificationHelper.accentColor(context))
            .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(message)
            .setContentText(destDir.absolutePath)
            .setStyle(NotificationCompat.BigTextStyle().bigText(destDir.absolutePath))
            .setContentIntent(NotificationHelper.openAppIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTicker(message)
            .build()
    }

    private fun show(operationId: Int, notification: Notification) {
        if (NotificationHelper.canPostNotifications(context)) {
            try {
                notificationManager.notify(operationId, notification)
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS was revoked meanwhile; the operation itself keeps running.
            }
        }
        initOperationTimer(operationId)
    }

    private fun hide(operationId: Int) {
        notificationManager.cancel(operationId)
    }

    private fun isLongOperation(operationId: Int): Boolean {
        val now = currentTimeMillis()
        return now - startTimes.get(operationId, now) >= LONG_OPERATION_MIN_DURATION_MS
    }

    private fun initOperationTimer(operationId: Int) {
        if (startTimes.get(operationId) == 0L) {
            startTimes.put(operationId, currentTimeMillis())
        }
    }

    private fun clearOperationTimer(operationId: Int) {
        startTimes.delete(operationId)
    }

    companion object {
        const val CHANNEL_FILEOPS = NotificationHelper.CHANNEL_FILEOPS
        private const val LONG_OPERATION_MIN_DURATION_MS = 500
    }
}
