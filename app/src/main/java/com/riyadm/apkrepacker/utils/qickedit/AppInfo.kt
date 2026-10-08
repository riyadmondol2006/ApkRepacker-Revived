package com.riyadm.apkrepacker.utils.qickedit

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.io.File

class AppInfo {
    private var icon: Drawable? = null
    private var iconValue: String? = null
    private var label: CharSequence? = null
    private var pname: String? = null
    private var version: String? = null
    private var code = 0
    private var minSdk = 0
    private var targetSdk = 0
    private var pManager: PackageManager? = null
    private var pInfo: PackageInfo? = null
    private var valid: Boolean
    private var isSplitRequired = false

    constructor(ctx: Context, apk: File?) {
        this.valid = false
        init(ctx, apk!!.absolutePath)
    }

    constructor(ctx: Context, apk: String) {
        this.valid = false
        init(ctx, apk)
    }

    @Suppress("DEPRECATION")
    private fun init(ctx: Context, path: String) {
        try {
            val mf = ManifestAnalyser(path)
            this.minSdk = ManifestAnalyser.getMinSdkVersion()
            this.targetSdk = ManifestAnalyser.getTargetSdkVersion()
            this.isSplitRequired = ManifestAnalyser.isSplitRequired()
            this.pManager = ctx.packageManager
            this.pInfo = pManager!!.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
            if (pInfo != null) {
                val appInfo = pInfo!!.applicationInfo!!
                appInfo.sourceDir = path
                appInfo.publicSourceDir = path
                val resources = pManager!!.getResourcesForApplication(appInfo)
                this.icon = appInfo.loadIcon(pManager!!)
                this.iconValue = resources.getResourceName(appInfo.icon)
                this.label = appInfo.loadLabel(pManager!!)
                this.pname = pInfo!!.packageName
                this.version = pInfo!!.versionName
                this.code = pInfo!!.versionCode
            } else {
                this.label = ManifestAnalyser.getPackageLabel()
                this.pname = ManifestAnalyser.getPackageName()
                this.version = ManifestAnalyser.getVersionName()
                this.code = ManifestAnalyser.getVersionCode()
            }
            this.valid = true
        } catch (e: Exception) {
            e.printStackTrace()
            this.valid = false
        }
    }

    fun icon(): Drawable? {
        return icon
    }

    fun iconValue(): String? {
        return iconValue
    }

    fun label(): String {
        return label!!.toString()
    }

    fun pname(): String? {
        return pname
    }

    fun version(): String? {
        return version
    }

    fun code(): Int {
        return code
    }

    fun minSdk(): Int {
        return minSdk
    }

    fun targetSdk(): Int {
        return targetSdk
    }

    fun getPackageManager(): PackageManager? {
        return pManager
    }

    fun getPackageInfo(): PackageInfo? {
        return pInfo
    }

    fun isSplitRequired(): Boolean {
        return isSplitRequired
    }

    fun isValid(): Boolean {
        return valid
    }
}
