package com.riyadm.apkrepacker.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyadm.apkrepacker.BuildConfig
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.databinding.DialogProgressM3Binding
import com.riyadm.apkrepacker.utils.AppUtils
import com.riyadm.apkrepacker.utils.Constant
import com.riyadm.apkrepacker.utils.common.DLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** "Update available" on app open, "Check for updates" in About, and the download + install. */
object UpdateUi {

    /** Settings switch: look for a new version every time the app opens (on by default). */
    const val PREF_CHECK_ON_OPEN = "pref_check_updates"

    /** Set once the "join the Telegram channel" invitation was shown. */
    private const val PREF_CHANNEL_INVITED = "channel_invite_shown"

    /**
     * Run when the app opens. The very first open invites the user to the Telegram channel (once,
     * ever); every open after that quietly checks for a new version and, if there is one, shows it.
     */
    @JvmStatic
    fun onAppOpen(activity: FragmentActivity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!prefs.getBoolean(PREF_CHANNEL_INVITED, false)) {
            prefs.edit { putBoolean(PREF_CHANNEL_INVITED, true) }
            showChannelInvite(activity)
            return
        }
        if (!prefs.getBoolean(PREF_CHECK_ON_OPEN, true)) return
        activity.lifecycleScope.launch {
            val release = try {
                UpdateChecker.newerRelease()
            } catch (e: Exception) {
                // Offline or GitHub unreachable: nothing to tell on app open.
                DLog.w("Update", "Update check failed: ${e.message}")
                null
            } ?: return@launch
            if (!activity.isFinishing && !activity.isDestroyed) showAvailable(activity, release)
        }
    }

    /** "Check for updates" pressed: always answers, also when the app is up to date or offline. */
    @JvmStatic
    fun checkNow(fragment: Fragment) {
        val activity = fragment.activity ?: return
        val progress = progressDialog(activity, activity.getString(R.string.update_checking), determinate = false)
        val job = activity.lifecycleScope.launch {
            val result = runCatching { UpdateChecker.newerRelease() }
            progress.dismiss()
            if (activity.isFinishing || activity.isDestroyed) return@launch
            result.onSuccess { release ->
                if (release != null) {
                    showAvailable(activity, release)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.update_none_title)
                        .setMessage(activity.getString(R.string.update_none_message, BuildConfig.VERSION_NAME))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_failed_title)
                    .setMessage(activity.getString(R.string.update_check_failed, e.message ?: e.javaClass.simpleName))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
        progress.setOnCancelListener { job.cancel() }
    }

    /** The one-time invitation to the app's Telegram channel. */
    private fun showChannelInvite(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.ic_telegram)
            .setTitle(R.string.channel_invite_title)
            .setMessage(R.string.channel_invite_message)
            .setPositiveButton(R.string.channel_invite_join) { _, _ -> openUrl(activity, Constant.TELEGRAM_CHANNEL) }
            .setNegativeButton(R.string.channel_invite_later, null)
            .show()
    }

    private fun showAvailable(activity: FragmentActivity, release: UpdateChecker.Release) {
        val notes = release.notes.ifBlank { activity.getString(R.string.update_no_notes) }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.version))
            .setMessage(activity.getString(R.string.update_available_message, BuildConfig.VERSION_NAME, notes))
            .setPositiveButton(R.string.update_install) { _, _ ->
                if (release.apkUrl != null) download(activity, release) else openUrl(activity, release.pageUrl)
            }
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.update_release_page) { _, _ -> openUrl(activity, release.pageUrl) }
            .show()
    }

    /** Downloads the update with a progress dialog, then hands it to the system installer. */
    private fun download(activity: FragmentActivity, release: UpdateChecker.Release) {
        var job: Job? = null
        val body = DialogProgressM3Binding.inflate(LayoutInflater.from(activity))
        body.progressLoading.visibility = View.GONE
        body.progressDeterminate.visibility = View.VISIBLE
        body.progressDeterminate.isIndeterminate = true
        body.progressMessage.text = activity.getString(R.string.update_downloading, release.version)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_download_title)
            .setView(body.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> job?.cancel() }
            .show()
        job = activity.lifecycleScope.launch {
            try {
                val apk = UpdateChecker.download(activity, release) { done, total ->
                    activity.runOnUiThread {
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            body.progressDeterminate.isIndeterminate = false
                            body.progressDeterminate.setProgressCompat(percent, false)
                            body.progressMessage.text = activity.getString(
                                R.string.update_downloading_progress, release.version, done / 1_048_576.0, total / 1_048_576.0,
                            )
                        }
                    }
                }
                dialog.dismiss()
                AppUtils.installApk(activity, apk)
            } catch (e: CancellationException) {
                dialog.dismiss()
            } catch (e: Exception) {
                DLog.e("Update", e)
                dialog.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@launch
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_failed_title)
                    .setMessage(activity.getString(R.string.update_download_failed, e.message ?: e.javaClass.simpleName))
                    .setPositiveButton(R.string.update_release_page) { _, _ -> openUrl(activity, release.pageUrl) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun progressDialog(activity: Activity, message: String, determinate: Boolean): AlertDialog {
        val body = DialogProgressM3Binding.inflate(LayoutInflater.from(activity))
        body.progressLoading.visibility = if (determinate) View.GONE else View.VISIBLE
        body.progressDeterminate.visibility = if (determinate) View.VISIBLE else View.GONE
        body.progressMessage.text = message
        return MaterialAlertDialogBuilder(activity)
            .setView(body.root)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.cancel() }
            .show()
    }

    private fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            DLog.w("Update", "No app opens $url")
        }
    }
}
