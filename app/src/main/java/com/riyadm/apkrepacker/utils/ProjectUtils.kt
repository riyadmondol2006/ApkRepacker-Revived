package com.riyadm.apkrepacker.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.apktool.ProjectMeta
import java.io.File

object ProjectUtils {
    private var projectPath: String? = null
    private var currentPath: String? = null

    /** Path of the project open in the editor ("" if none). */
    @JvmStatic
    fun getProjectPath(): String = projectPath ?: ""

    @JvmStatic
    fun setProjectPath(path: String?) {
        projectPath = path
    }

    @JvmStatic
    fun setCurrentPath(path: String?) {
        currentPath = path
    }

    @JvmStatic
    fun getCurrentPath(): String? = currentPath

    /**
     * Reads a value from a project's apktool.json. "versionName"/"versionCode" come from its
     * "VersionInfo" object, anything else is a top-level string.
     */
    @JvmStatic
    fun readJson(file: File, stringName: String): String? {
        val json = ProjectMeta.readFile(file) ?: return null
        return when (stringName) {
            "versionName", "versionCode" -> ProjectMeta.getString(json.optJSONObject("VersionInfo"), stringName)
            else -> ProjectMeta.getString(json, stringName)
        }
    }

    @JvmStatic
    fun getProjectIconDrawable(appIconBase64: String?, context: Context): Drawable? {
        val bitmap = FileUtil.decodeBase64(appIconBase64)
        val icon = if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
        return if (appIconBase64 != null) icon else ContextCompat.getDrawable(context, R.drawable.default_app_icon)
    }

    @JvmStatic
    fun getProjectName(): String = File(getProjectPath()).name
}
