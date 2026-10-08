package com.riyadm.apkrepacker.utils

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity

object ViewDeviceUtils {

    @JvmStatic
    fun toggledScreenOn(activity: Activity, enable: Boolean) {
        val window = activity.window
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @JvmStatic
    fun getScreenWidthDp(context: Context): Int = context.resources.configuration.screenWidthDp

    /** Hides the status bar and lets a swipe peek it back. */
    @JvmStatic
    fun setFullScreenMode(activity: ComponentActivity, fullScreenMode: Boolean) {
        EdgeToEdgeUtils.setStatusBarHidden(activity, fullScreenMode)
    }
}
