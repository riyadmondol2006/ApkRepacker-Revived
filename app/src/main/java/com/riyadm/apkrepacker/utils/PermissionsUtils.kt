package com.riyadm.apkrepacker.utils

import com.jecelyin.common.utils.UIUtils

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.riyadm.apkrepacker.R
import java.io.File
import java.io.IOException

/**
 * Shared storage access.
 *
 * - API 23-29: READ/WRITE_EXTERNAL_STORAGE runtime permissions (API 29 keeps legacy storage
 *   through requestLegacyExternalStorage).
 * - API 30+: scoped storage is enforced, so the file manager needs MANAGE_EXTERNAL_STORAGE
 *   ("All files access"), which is granted on a system settings page, not by a dialog.
 *
 * App-specific storage (projects in Android/data/<pkg>/files, the app's data dir) is always
 * accessible, so projects keep working when access is denied.
 */
object PermissionsUtils {
    const val REQUEST_CODE_STORAGE_PERMISSIONS = 322

    private val LEGACY_STORAGE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** Whether the app can read and write all of shared storage. */
    @JvmStatic
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            LEGACY_STORAGE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /** Whether [path] is in storage that belongs to this app (always accessible). */
    @JvmStatic
    fun isAppSpecificPath(context: Context, path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val target = canonical(File(path))
        val roots = ArrayList<File>()
        roots.add(context.applicationInfo.dataDir.let { File(it) })
        // Android/data/<pkg> and Android/obb/<pkg> on every volume.
        context.getExternalFilesDirs(null).filterNotNull().mapNotNullTo(roots) { it.parentFile }
        context.obbDirs.filterNotNull().mapTo(roots) { it }
        return roots.any { root ->
            val r = canonical(root)
            target == r || target.startsWith(r + File.separator)
        }
    }

    /** Whether [path] can be browsed right now. */
    @JvmStatic
    fun canAccessPath(context: Context, path: String?): Boolean {
        return hasStorageAccess(context) || isAppSpecificPath(context, path)
    }

    private fun canonical(f: File): String {
        return try {
            f.canonicalPath
        } catch (e: IOException) {
            f.absolutePath
        }
    }

    /** The "All files access" settings page for this app. */
    @RequiresApi(Build.VERSION_CODES.R)
    @JvmStatic
    fun allFilesAccessSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.fromParts("package", context.packageName, null))
    }

    /**
     * Asks for storage access from a context that has no Activity Result launcher (dialogs,
     * views). API 30+: explains, then opens the settings page; screens re-check on resume.
     * API < 30: asks for the runtime permissions (result goes to onRequestPermissionsResult).
     */
    @JvmStatic
    fun requestStorageAccess(context: Context) {
        val activity = findActivity(context) ?: return
        if (hasStorageAccess(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showAllFilesAccessExplanation(activity) { openAllFilesAccessSettings(activity) }
        } else {
            ActivityCompat.requestPermissions(activity, LEGACY_STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE_PERMISSIONS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    internal fun openAllFilesAccessSettings(context: Context) {
        try {
            context.startActivity(allFilesAccessSettingsIntent(context))
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: ActivityNotFoundException) {
                UIUtils.toast(context, R.string.storage_access_settings_unavailable)
            }
        }
    }

    internal fun showAllFilesAccessExplanation(context: Context, onContinue: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.storage_access_title)
            .setMessage(R.string.storage_access_message)
            .setPositiveButton(R.string.storage_access_open_settings) { _, _ -> onContinue() }
            .setNegativeButton(R.string.storage_access_not_now, null)
            .show()
    }

    internal val legacyStoragePermissions: Array<String>
        get() = LEGACY_STORAGE_PERMISSIONS.clone()

    @JvmStatic
    fun findActivity(context: Context?): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

/**
 * Requests shared storage access for a fragment and reports the outcome.
 * Must be created while the fragment is being created (it registers Activity Result
 * launchers), e.g. as a property initializer.
 */
class StorageAccessRequester(
    private val fragment: Fragment,
    private val onResult: (granted: Boolean) -> Unit
) {
    private val permissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = PermissionsUtils.hasStorageAccess(fragment.requireContext())
            if (!granted && !canAskAgain()) {
                showDeniedForeverDialog()
            }
            onResult(granted)
        }

    private val settingsLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (fragment.isAdded) onResult(PermissionsUtils.hasStorageAccess(fragment.requireContext()))
        }

    /**
     * Asks for access, explaining first unless [explain] is false (the caller already did).
     * Calls back right away if access is already granted.
     */
    fun request(explain: Boolean = true) {
        val context = fragment.requireContext()
        if (PermissionsUtils.hasStorageAccess(context)) {
            onResult(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (explain) {
                PermissionsUtils.showAllFilesAccessExplanation(context) { launchSettings() }
            } else {
                launchSettings()
            }
        } else {
            permissionLauncher.launch(PermissionsUtils.legacyStoragePermissions)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchSettings() {
        val context = fragment.context ?: return
        try {
            settingsLauncher.launch(PermissionsUtils.allFilesAccessSettingsIntent(context))
        } catch (e: ActivityNotFoundException) {
            try {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: ActivityNotFoundException) {
                UIUtils.toast(context, R.string.storage_access_settings_unavailable)
            }
        }
    }

    private fun canAskAgain(): Boolean {
        val activity = fragment.activity ?: return false
        return PermissionsUtils.legacyStoragePermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    private fun showDeniedForeverDialog() {
        val context = fragment.context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.need_to_enable_read_storage_permissions)
            .setMessage(R.string.storage_access_denied_settings)
            .setPositiveButton(R.string.storage_access_open_settings) { _, _ ->
                AppUtils.gotoApplicationSettings(context, context.packageName)
            }
            .setNegativeButton(R.string.storage_access_not_now, null)
            .show()
    }
}
