package com.riyadm.apkrepacker.utils.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.riyadm.apkrepacker.utils.AppUtils

object CommonUtil {

    /**
     * one shot vibrate
     * @param context your Context
     */
    @Suppress("DEPRECATION")
    @JvmStatic
    fun vibrate(context: Context) {
        if (AppUtils.apiIsAtLeast(Build.VERSION_CODES.O)) {
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?)!!.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?)!!.vibrate(25)
        }
    }
}
