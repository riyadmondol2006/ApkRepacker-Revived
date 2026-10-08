package com.riyadm.apkrepacker.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.riyadm.apkrepacker.R

/**
 * Notification channels, the shared Material 3 notification look, and the POST_NOTIFICATIONS
 * runtime permission (API 33+). Foreground services keep working without the permission; their
 * notification is just not shown in the shade.
 */
object NotificationHelper {
    /** Same ids as before, so users keep their channel settings. */
    const val CHANNEL_BUILD = "buildApkService"
    const val CHANNEL_FILEOPS = "com.riyadm.apkrepacker.channel.FILEOPETATION"
    const val CHANNEL_PATCH = "patchService"
    const val CHANNEL_DECOMPILE = "decompileService"

    /** A separate channel for the "finished" notifications, which may alert (the progress ones are silent). */
    const val CHANNEL_DONE = "operationDone"

    private const val KEY_ASKED_POST_NOTIFICATIONS = "asked_post_notifications_permission"

    @JvmStatic
    fun createChannels(context: Context) {
        val build = NotificationChannelCompat.Builder(CHANNEL_BUILD, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_build))
            .setSound(null, null)
            .setShowBadge(false)
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .build()
        val fileOps = NotificationChannelCompat.Builder(CHANNEL_FILEOPS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notif_title_operations))
            .setShowBadge(false)
            .build()
        val patch = NotificationChannelCompat.Builder(CHANNEL_PATCH, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_patch))
            .setSound(null, null)
            .setShowBadge(false)
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .build()
        val decompile = NotificationChannelCompat.Builder(CHANNEL_DECOMPILE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_decompile))
            .setSound(null, null)
            .setShowBadge(false)
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .build()
        val done = NotificationChannelCompat.Builder(CHANNEL_DONE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_done))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(listOf(build, fileOps, patch, decompile, done))
    }

    /** Theme primary, used by the system to tint the app name / icon / progress of a notification. */
    @JvmStatic
    fun accentColor(context: Context): Int =
        MaterialColors.getColor(context, R.attr.colorPrimary, NotificationCompat.COLOR_DEFAULT)

    /** Brings the app's existing task back to the front (no new activity instance). */
    @JvmStatic
    fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Ongoing, silent, tinted progress notification: the base of the build and file-operation
     * notifications. [smallIcon] must be a monochrome vector.
     */
    @JvmStatic
    fun progressBuilder(context: Context, channelId: String, @DrawableRes smallIcon: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setColor(accentColor(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

    /**
     * A one-shot "finished" notification (not ongoing, auto-cancels on tap, alerts once). Used for
     * the build/patch completion notices so the user is told the result when the app is in the
     * background. [smallIcon] must be a monochrome vector.
     */
    @JvmStatic
    fun completionBuilder(context: Context, @DrawableRes smallIcon: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(smallIcon)
            .setColor(accentColor(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

    /** Whether notifications can be posted (permission granted and not blocked by the user). */
    @JvmStatic
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** True once on API 33+ while the permission is missing: the caller should ask now. */
    @JvmStatic
    fun shouldRequestPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(KEY_ASKED_POST_NOTIFICATIONS, false)) return false
        prefs.edit().putBoolean(KEY_ASKED_POST_NOTIFICATIONS, true).apply()
        return true
    }
}
