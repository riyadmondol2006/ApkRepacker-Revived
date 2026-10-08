package com.riyadm.apkrepacker.utils

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray

@Suppress("DEPRECATION")
object BundleUtils {

    private val sClassLoader: ClassLoader = BundleUtils::class.java.classLoader!!

    @JvmStatic
    fun <T : Parcelable> getParcelable(bundle: Bundle, key: String?): T? {
        return ensureClassLoader(bundle).getParcelable(key)
    }

    @JvmStatic
    fun getParcelableArray(bundle: Bundle, key: String?): Array<Parcelable>? {
        return ensureClassLoader(bundle).getParcelableArray(key)
    }

    @JvmStatic
    fun <T : Parcelable> getParcelableArrayList(bundle: Bundle, key: String?): ArrayList<T>? {
        return ensureClassLoader(bundle).getParcelableArrayList(key)
    }

    @JvmStatic
    fun <T : Parcelable> getSparseParcelableArray(bundle: Bundle, key: String?): SparseArray<T>? {
        return ensureClassLoader(bundle).getSparseParcelableArray(key)
    }

    private fun ensureClassLoader(bundle: Bundle): Bundle {
        bundle.classLoader = sClassLoader
        return bundle
    }

    @JvmStatic
    fun <T : Parcelable> getParcelableExtra(intent: Intent, key: String?): T? {
        return ensureClassLoader(intent).getParcelableExtra(key)
    }

    @JvmStatic
    fun getParcelableArrayExtra(intent: Intent, key: String?): Array<Parcelable>? {
        return ensureClassLoader(intent).getParcelableArrayExtra(key)
    }

    @JvmStatic
    fun <T : Parcelable> getParcelableArrayListExtra(intent: Intent, key: String?): ArrayList<T>? {
        return ensureClassLoader(intent).getParcelableArrayListExtra(key)
    }

    private fun ensureClassLoader(intent: Intent): Intent {
        intent.setExtrasClassLoader(sClassLoader)
        return intent
    }
}
