package com.riyadm.apkrepacker.utils

import android.content.Context
import android.preference.PreferenceManager

@Suppress("DEPRECATION")
object PreferenceUtils {

    @JvmStatic
    fun getBoolean(context: Context?, key: String?, defaultValue: Boolean): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    @JvmStatic
    fun getInteger(context: Context?, key: String?, defaultValue: Int): Int {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(key, defaultValue)
    }

    @JvmStatic
    fun putInt(context: Context?, key: String?, value: Int) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPref.edit().putInt(key, value).apply()
    }
}
