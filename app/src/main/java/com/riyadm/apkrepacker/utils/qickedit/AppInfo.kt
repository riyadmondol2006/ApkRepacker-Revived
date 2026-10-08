package com.riyadm.apkrepacker.utils.qickedit

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.TypedValue
import java.io.File

class AppInfo {
    private var icon: Drawable? = null
    private var iconValue: String? = null
    private var iconFiles: Map<String, String> = emptyMap()
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
                this.iconFiles = resolveIconFiles(resources, appInfo.icon)
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

    /** APK entry path -> [IconGenerate.mDens] density of every bitmap the icon resolves to. */
    fun iconFiles(): Map<String, String> {
        return iconFiles
    }

    /**
     * Asks the resource table which file serves the icon at each density, so obfuscated entry
     * names (res/a1.png) are found too. XML results (an adaptive icon) are skipped.
     */
    private fun resolveIconFiles(resources: Resources, id: Int): Map<String, String> {
        val dpis = intArrayOf(120, 160, 240, 320, 480, 640)
        val files = HashMap<String, String>()
        val value = TypedValue()
        for (dpi in dpis) {
            try {
                resources.getValueForDensity(id, dpi, value, true)
            } catch (e: Resources.NotFoundException) {
                continue
            }
            val path = value.string?.toString() ?: continue
            if (!path.startsWith("res/") || path.endsWith(".xml")) continue
            // The file's own density, which may differ from the one asked for; none/any -> largest.
            val index = dpis.indexOfFirst { value.density in 1..it }
            files[path] = IconGenerate.mDens[if (index >= 0) index else dpis.lastIndex]
        }
        return files
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
