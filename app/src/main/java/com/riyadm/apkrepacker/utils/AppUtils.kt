package com.riyadm.apkrepacker.utils

import com.jecelyin.common.utils.UIUtils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.riyadm.apkrepacker.R
import java.io.File

object AppUtils {

    @JvmStatic
    fun checkAppInstalled(context: Context, packageName: String?): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName!!, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun apiIsAtLeast(sdkInt: Int): Boolean {
        return Build.VERSION.SDK_INT >= sdkInt
    }

    /** Opens the system uninstall dialog (needs REQUEST_DELETE_PACKAGES on API 28+). */
    @JvmStatic
    fun uninstallApp(context: Context, packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            UIUtils.toast(context, e.toString())
        }
    }

    /**
     * Hands [apk] to the system package installer through the app's content provider.
     * API 26+: installing needs the per-app "Install unknown apps" switch; when it is off the
     * user is sent to that settings page first and taps Install again afterwards.
     */
    @JvmStatic
    fun installApk(c: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !c.packageManager.canRequestPackageInstalls()) {
            requestInstallPermission(c)
            return
        }
        val data = FileProvider.getUriForFile(c, apk)
        @Suppress("DEPRECATION") // ACTION_INSTALL_PACKAGE still works; PackageInstaller sessions aren't needed for one APK.
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.setDataAndType(data, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (c !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            UIUtils.toast(c, e.toString())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestInstallPermission(c: Context) {
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", c.packageName, null))
        val activity = PermissionsUtils.findActivity(c)
        val open = {
            try {
                if (activity == null) settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                (activity ?: c).startActivity(settings)
            } catch (e: ActivityNotFoundException) {
                UIUtils.toast(c, e.toString())
            }
        }
        if (activity == null || activity.isFinishing) {
            open()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.install_unknown_sources_title)
            .setMessage(R.string.install_unknown_sources_message)
            .setPositiveButton(R.string.storage_access_open_settings) { _, _ -> open() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun gotoApplicationSettings(context: Context, packageName: String?) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", packageName, null)
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getVersionName(context: Context): String {
        return try {
            val manager = context.packageManager
            val info = manager.getPackageInfo(context.packageName, 0)
            val version = info.versionName
            val code = (if (apiIsAtLeast(Build.VERSION_CODES.P)) info.longVersionCode else info.versionCode.toLong()).toString()
            context.resources.getString(R.string.about_version, version, code)
        } catch (e: Exception) {
            e.printStackTrace()
            context.resources.getString(R.string.about_version, null, null)
        }
    }

    @JvmStatic
    fun getArchName(): String {
        for (androidArch in Build.SUPPORTED_ABIS) {
            when (androidArch) {
                "arm64-v8a" -> return androidArch

                "armeabi-v7a" -> return androidArch

                /*
                "x86_64" -> return androidArch

                "x86" -> return androidArch

                 */
            }
        }
        return "armeabi-v7a"
    }

    @JvmStatic
    fun getApkIcon(c: Context, n: String): Drawable? {
        val apkInfo = getApkInfo(c, n)
        return if (apkInfo != null)
            apkInfo[0] as Drawable?
        else
            null
    }

    @JvmStatic
    fun getApkPackage(context: Context, apk: String): String? {
        val apkInfo = getApkInfo(context, apk)
        return if (apkInfo != null)
            apkInfo[2] as String?
        else
            null
    }

    /**
     * Note: returns non-null; where the Java version returned null (unparsable APK) this throws
     * NullPointerException, which every existing caller would have hit on the null result anyway.
     */
    @JvmStatic
    fun getApkName(context: Context?, apk: String): String {
        val apkInfo = getApkInfo(context!!, apk)
        return if (apkInfo != null)
            apkInfo[1] as String
        else
            throw NullPointerException("Unable to read apk name: $apk")
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getApkInfo(c: Context, apk: String): Array<Any?>? {
        val res = arrayOfNulls<Any>(8)
        return try {
            val mf = Manifest(apk)
            val pm = c.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apk, PackageManager.GET_ACTIVITIES)
            if (packageInfo != null) {
                val appInfo = packageInfo.applicationInfo!!
                appInfo.sourceDir = apk
                appInfo.publicSourceDir = apk
                res[0] = appInfo.loadIcon(pm)
                res[1] = appInfo.loadLabel(pm)
                res[2] = packageInfo.packageName
                res[3] = packageInfo.versionName
                res[4] = packageInfo.versionCode
                if (apiIsAtLeast(Build.VERSION_CODES.N)) {
                    res[5] = appInfo.minSdkVersion
                } else {
                    res[5] = mf.getMinSdkVersion()//мне впадло писать метод
                }
                res[6] = appInfo.targetSdkVersion
                res[7] = packageInfo.installLocation
                res
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
