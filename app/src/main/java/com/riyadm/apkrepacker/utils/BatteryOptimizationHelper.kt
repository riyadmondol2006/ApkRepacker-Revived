package com.riyadm.apkrepacker.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.utils.common.DLog

/**
 * One-time prompt asking the user to exempt the app from battery optimization, so a long patch or
 * build the user started isn't throttled or killed while the app is in the background. The
 * foreground service keeps the work alive on its own in almost all cases; this just makes very long
 * runs more reliable. Asked at most once (unless the user later taps the retry path), like
 * [NotificationHelper.shouldRequestPostNotifications].
 */
object BatteryOptimizationHelper {

    private const val KEY_ASKED = "asked_ignore_battery_optimizations"

    @JvmStatic
    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True on API 23+ while not yet exempt and not asked before. */
    @JvmStatic
    fun shouldAsk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoring(context)) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getBoolean(KEY_ASKED, false)
    }

    /**
     * Shows the rationale dialog once and, on accept, opens the system prompt. Safe to call before
     * every long op: it no-ops once the user is exempt or has been asked.
     */
    @JvmStatic
    fun maybeAsk(activity: Activity) {
        if (!shouldAsk(activity)) return
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean(KEY_ASKED, true).apply()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setNegativeButton(R.string.battery_opt_not_now, null)
            .setPositiveButton(R.string.battery_opt_allow) { _, _ -> requestExemption(activity) }
            .show()
    }

    @Suppress("BatteryLife")
    private fun requestExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
            )
        } catch (e: Exception) {
            DLog.e("BatteryOpt", e)
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                DLog.e("BatteryOpt", e2)
            }
        }
    }
}
