package com.riyadm.patchengine

import android.content.Context
import java.io.File

class ProjectHelper {

    @JvmField
    var mProject: String? = null

    @JvmField
    var mDataPath: String? = null

    @JvmField
    var mApkPath: String? = null

    @JvmField
    var mApkPackage: String? = null

    @JvmField
    var mCache: File? = null

    @JvmField
    var mContext: Context? = null

    @JvmField
    var mSmaliCliced: Boolean = false

    fun getProjectPath(): String? {
        return mProject
    }

    fun getCacheDir(): File? {
        return mCache
    }

    fun getContext(): Context? {
        return mContext
    }

    fun smaliClicked(): Boolean {
        return mSmaliCliced
    }

    fun getAppDataPath(): String? {
        return mDataPath
    }

    fun getApkPath(): String? {
        return mApkPath
    }

    fun getApkPackage(): String? {
        return mApkPackage
    }
}
