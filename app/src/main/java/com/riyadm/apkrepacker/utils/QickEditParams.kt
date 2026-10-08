package com.riyadm.apkrepacker.utils

import android.graphics.Bitmap

object QickEditParams {
    private var oldpackage: String? = null
    private var newpackage: String? = null
    private var oldname: String? = null
    private var newname: String? = null
    private var installLocation = 0
    private var buildCode: String? = null
    private var buildNumber: String? = null
    private var minimumSdk = 0
    private var targetSdk = 0
    private var inRes = false
    private var inDex = false
    private var bitmap: Bitmap? = null
    private var iconName: String? = null

    @JvmStatic
    fun setOldName(old: String?) {
        oldname = old
    }

    @JvmStatic
    fun setInstallLocation(location: Int) {
        installLocation = location
    }

    @JvmStatic
    fun getOldPackage(): String? {
        return oldpackage
    }

    ////getters

    @JvmStatic
    fun setOldPackage(old: String?) {
        oldpackage = old
    }

    @JvmStatic
    fun getInstallLoacation(): Int {
        return installLocation
    }

    @JvmStatic
    fun getVersionCode(): String? {
        return buildCode
    }

    @JvmStatic
    fun setVersionCode(code: String?) {
        buildCode = code
    }

    @JvmStatic
    fun getVersionName(): String? {
        return buildNumber
    }

    @JvmStatic
    fun setVersionName(number: String?) {
        buildNumber = number
    }

    @JvmStatic
    fun getNewPackage(): String? {
        return newpackage
    }

    @JvmStatic
    fun setNewPackage(newPackage: String?) {
        newpackage = newPackage
    }

    @JvmStatic
    fun getNewname(): String? {
        return newname
    }

    @JvmStatic
    fun setNewname(newname1: String?) {
        newname = newname1
    }

    @JvmStatic
    fun getMinimumSdk(): Int {
        return minimumSdk
    }

    @JvmStatic
    fun setMinimumSdk(sdk: Int) {
        minimumSdk = sdk
    }

    @JvmStatic
    fun getTargetSdk(): Int {
        return targetSdk
    }

    @JvmStatic
    fun setTargetSdk(sdk: Int) {
        targetSdk = sdk
    }

    @JvmStatic
    fun isInRes(): Boolean {
        return inRes
    }

    @JvmStatic
    fun setInRes(res: Boolean) {
        inRes = res
    }

    @JvmStatic
    fun isInDex(): Boolean {
        return inDex
    }

    @JvmStatic
    fun setInDex(dex: Boolean) {
        inDex = dex
    }

    @JvmStatic
    fun getBitmap(): Bitmap? {
        return bitmap
    }

    @JvmStatic
    fun setBitmap(bit: Bitmap?) {
        bitmap = bit
    }

    @JvmStatic
    fun getIconName(): String? {
        if (iconName!! != "")
            return iconName
        return null
    }

    @JvmStatic
    fun setIconName(str: String?) {
        if (str!! != "")
            iconName = str
    }
}
