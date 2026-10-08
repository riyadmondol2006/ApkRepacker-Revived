package com.riyadm.apkrepacker.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

/** Honors the system "remove animations" / animator duration scale = 0 setting. */
object MotionPreferences {

    @JvmStatic
    fun animationsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }
}
