package com.riyadm.apkrepacker.apktool

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import brut.androlib.meta.ApkInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Level

/**
 * The app's per-project metadata file, `apktool.json` in the project root.
 *
 * apktool 3 keeps its own data in apktool.yml; this sidecar only holds what the app's project
 * list/editor shows (same keys as the apktool 2.4.1 fork wrote): apkFileIcon (base64 PNG),
 * apkFileName (app label), apkFilePackageName, apkFilePatch (source apk path), VersionInfo
 * {versionCode, versionName} and sdkInfo. apktool's builder ignores unknown files in the project
 * root, so it never ends up in the rebuilt apk.
 */
object ProjectMeta {
    const val FILE_NAME = "apktool.json"

    private const val MAX_ICON_SIZE = 192

    @JvmStatic
    fun file(projectDir: File): File = File(projectDir, FILE_NAME)

    /** Writes the sidecar for a project just decoded from [apk]. Never throws. */
    @JvmStatic
    fun write(context: Context, apk: File, projectDir: File, appName: String?, apkInfo: ApkInfo?) {
        try {
            val pm = context.packageManager
            val pkgInfo = archiveInfo(pm, apk)
            val json = JSONObject()
            json.put("version", ApktoolEngine.version)
            json.put("apkFileIcon", pkgInfo?.let { iconBase64(pm, it, apk) } ?: JSONObject.NULL)
            json.put("apkFileName", appName ?: label(pm, pkgInfo) ?: apk.name)
            json.put("apkFilePackageName", pkgInfo?.packageName ?: JSONObject.NULL)
            json.put("apkFilePatch", apk.absolutePath)

            val versionCode = apkInfo?.versionInfo?.versionCode?.takeIf { it >= 0 }?.toString()
                ?: pkgInfo?.let { versionCode(it).toString() }
            val versionName = apkInfo?.versionInfo?.versionName ?: pkgInfo?.versionName
            json.put("VersionInfo", JSONObject().apply {
                put("versionCode", versionCode ?: JSONObject.NULL)
                put("versionName", versionName ?: JSONObject.NULL)
            })

            val sdk = JSONObject()
            val appInfo = pkgInfo?.applicationInfo
            val minSdk = apkInfo?.sdkInfo?.minSdkVersion
                ?: if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion.toString() else null
            val targetSdk = apkInfo?.sdkInfo?.targetSdkVersion ?: appInfo?.targetSdkVersion?.toString()
            minSdk?.let { sdk.put("minSdkVersion", it) }
            targetSdk?.let { sdk.put("targetSdkVersion", it) }
            apkInfo?.sdkInfo?.maxSdkVersion?.let { sdk.put("maxSdkVersion", it) }
            json.put("sdkInfo", if (sdk.length() > 0) sdk else JSONObject.NULL)

            file(projectDir).writeText(json.toString(2))
        } catch (e: Exception) {
            android.util.Log.w("ProjectMeta", "Could not write $FILE_NAME", e)
        }
    }

    /** The sidecar as JSON, or null if missing/unreadable. */
    @JvmStatic
    fun read(projectDir: File): JSONObject? = readFile(file(projectDir))

    @JvmStatic
    fun readFile(file: File): JSONObject? = try {
        if (file.isFile) JSONObject(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    /** A top-level string value, or null if missing/JSON null. */
    @JvmStatic
    fun getString(json: JSONObject?, key: String): String? =
        if (json == null || json.isNull(key)) null else json.optString(key, null)

    /** minSdkVersion of a project from apktool.yml, falling back to the sidecar; null if unknown. */
    @JvmStatic
    fun minSdkVersion(projectDir: File): Int? {
        val fromYml = try {
            ApkInfo.load(projectDir).sdkInfo.minSdkVersion
        } catch (e: Exception) {
            null
        }
        val fromJson = read(projectDir)?.optJSONObject("sdkInfo")?.let { getString(it, "minSdkVersion") }
        for (value in listOf(fromYml, fromJson)) {
            if (value == null) continue
            try {
                return brut.androlib.meta.SdkInfo.parseSdkInt(value)
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    /**
     * Creates apktool.yml for a project decoded by the apktool 2.4.1 fork, from its apktool.json.
     * Best effort: such projects may still need to be decompiled again to build with aapt2.
     * @return false if there is no apktool.json to migrate from.
     */
    @JvmStatic
    @Throws(ApktoolException::class)
    fun migrateLegacyProject(projectDir: File, version: String, log: ApktoolLogListener?): Boolean {
        val json = read(projectDir) ?: return false
        log?.onLog(Level.WARNING, "Project was decompiled by an older apktool, converting $FILE_NAME to apktool.yml." +
                " If the build fails, decompile the apk again.")
        try {
            val info = ApkInfo()
            info.version = version
            val sourceApk = getString(json, "apkFilePatch")?.let { File(it).name }
            info.apkFileName = sourceApk?.takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains('\\') } ?: "base.apk"

            json.optJSONObject("UsesFramework")?.let { fw ->
                val ids: JSONArray? = fw.optJSONArray("ids")
                if (ids != null) {
                    for (i in 0 until ids.length()) info.usesFramework.ids.add(ids.getInt(i))
                }
                getString(fw, "tag")?.let { info.usesFramework.tag = it }
            }
            if (info.usesFramework.ids.isEmpty()) info.usesFramework.ids.add(1)

            json.optJSONObject("sdkInfo")?.let { sdk ->
                getString(sdk, "minSdkVersion")?.let { info.sdkInfo.minSdkVersion = it }
                getString(sdk, "targetSdkVersion")?.let { info.sdkInfo.targetSdkVersion = it }
                getString(sdk, "maxSdkVersion")?.let { info.sdkInfo.maxSdkVersion = it }
            }
            json.optJSONObject("VersionInfo")?.let { ver ->
                getString(ver, "versionCode")?.toIntOrNull()?.let { info.versionInfo.versionCode = it }
                getString(ver, "versionName")?.let { info.versionInfo.versionName = it }
            }
            json.optJSONObject("PackageInfo")?.let { pkg ->
                getString(pkg, "forcedPackageId")?.toIntOrNull()?.takeIf { it != 0x7f }?.let {
                    info.resourcesInfo.packageId = it
                }
                getString(pkg, "renameManifestPackage")?.let { info.resourcesInfo.packageName = it }
            }
            if (json.optBoolean("sparseResources", false)) {
                info.resourcesInfo.isSparseEntries = true
            }
            json.optJSONArray("doNotCompress")?.let { list ->
                for (i in 0 until list.length()) info.doNotCompress.add(list.getString(i))
            }
            info.save(projectDir)
            return true
        } catch (e: Exception) {
            throw ApktoolException("Could not convert the old project ($FILE_NAME): ${ApktoolEngine.describe(e)}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? = try {
        pm.getPackageArchiveInfo(apk.absolutePath, 0)?.also {
            it.applicationInfo?.sourceDir = apk.absolutePath
            it.applicationInfo?.publicSourceDir = apk.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    private fun label(pm: PackageManager, info: PackageInfo?): String? = try {
        info?.applicationInfo?.loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun iconBase64(pm: PackageManager, info: PackageInfo, apk: File): String? = try {
        val drawable = info.applicationInfo?.loadIcon(pm)
        drawable?.let { toBitmap(it) }?.let { bitmap ->
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Throwable) {
        android.util.Log.w("ProjectMeta", "Could not load icon of ${apk.path}", e)
        null
    }

    private fun toBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null &&
            drawable.bitmap.width <= MAX_ICON_SIZE && drawable.bitmap.height <= MAX_ICON_SIZE
        ) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(MAX_ICON_SIZE) ?: MAX_ICON_SIZE
        val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(MAX_ICON_SIZE) ?: MAX_ICON_SIZE
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
